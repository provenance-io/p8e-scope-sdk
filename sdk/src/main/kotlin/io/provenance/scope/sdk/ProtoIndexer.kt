package io.provenance.scope.sdk

import com.google.protobuf.ByteString
import com.google.protobuf.Descriptors.*
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType.*
import com.google.protobuf.Message
import io.provenance.metadata.v1.RecordWrapper
import io.provenance.metadata.v1.ScopeResponse
import io.provenance.metadata.v1.SessionWrapper
import io.provenance.scope.contract.proto.Specifications.ContractSpec
import io.provenance.scope.classloader.ClassLoaderCache
import io.provenance.scope.classloader.MemoryClassLoader
import io.provenance.scope.contract.proto.Commons.DefinitionSpec
import io.provenance.scope.contract.proto.IndexProto.Index
import io.provenance.scope.contract.proto.IndexProto.Index.Behavior
import io.provenance.scope.contract.proto.IndexProto.Index.Behavior.*
import io.provenance.scope.definition.DefinitionService
import io.provenance.scope.encryption.crypto.Pen
import io.provenance.scope.encryption.crypto.SignerImpl
import io.provenance.scope.encryption.model.DirectKeyRef
import io.provenance.scope.encryption.model.KeyRef
import io.provenance.scope.encryption.model.signer
import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.objectstore.client.CachedOsClient
import io.provenance.scope.objectstore.util.base64Decode
import io.provenance.scope.sdk.extensions.resultType
import io.provenance.scope.util.MetadataAddress
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.security.KeyPair

class ProtoIndexer(
    private val osClient: CachedOsClient,
    private val mainNet: Boolean,
    private val affiliate: Affiliate,
    private val definitionServiceFactory: (CachedOsClient, MemoryClassLoader) -> DefinitionService =
        { osClient, memoryClassLoader -> DefinitionService(osClient, memoryClassLoader) },
) {
    private val indexDescriptor = Index.getDefaultInstance().descriptorForType.file.findExtensionByName("index")
    private val messageIndexDescriptor = Index.getDefaultInstance().descriptorForType.file.findExtensionByName("message_index")
    private val affiliateAddress = affiliate.encryptionKeyRef.publicKey.getAddress(mainNet)

    fun indexFields(scope: ScopeResponse): Map<String, Any> {
        // find all record groups where there's at least one party member that's an affiliate on this p8e instance
        val sessionMap: Map<ByteString, SessionWrapper> = makeSessionIdMap(scope.sessionsList)

        return scope.recordsList
            //Filter to just the records that we have addresses for from the partiesList
            .filter { recordWrapper ->
                val sessionWrapper = sessionMap.getValue(recordWrapper.record.sessionId)
                sessionWrapper.session.partiesList.any { it.address == affiliateAddress }
            }
            //Get list of record_name - Map pairs
            .map { recordWrapper ->
                recordNameToIndexFields(recordWrapper, sessionMap)
            }
            //Filter out null maps and then convert list to a map
            .filter { it.second != null }
            .map { it.first to it.second!! }
            .toMap()
    }

    //Helper function that makes a Pair of a record's name to a map
    private fun recordNameToIndexFields(recordWrapper: RecordWrapper, sessionMap: Map<ByteString, SessionWrapper>): Pair<String, Map<String, Any>?>{
        val sessionWrapper = sessionMap.getValue(recordWrapper.record.sessionId)

        // Need a reference of the signer that is used to verify signatures.
        val signer = affiliate.encryptionKeyRef.signer()

        // Try to re-use MemoryClassLoader if possible for caching reasons
        val spec = osClient.getRecord(
            ContractSpec::class.java.name,
            MetadataAddress.fromBech32(sessionWrapper.contractSpecIdInfo.contractSpecAddr).bytes.sliceArray(1 until 17), // todo: is there a better way to do this?
            affiliate.encryptionKeyRef,
        ).get() as ContractSpec

        val classLoaderKey =
            "${spec.definition.resourceLocation.ref.hash}-${spec.functionSpecsList.first().outputSpec.spec.resourceLocation.ref.hash}"
        val memoryClassLoader = ClassLoaderCache.classLoaderCache.get(classLoaderKey) {
            MemoryClassLoader("", ByteArrayInputStream(ByteArray(0)))
        }

        val definitionService = definitionServiceFactory(osClient, memoryClassLoader)
        loadAllJars(affiliate.encryptionKeyRef, definitionService, spec, signer)

        return recordWrapper.record.name to indexFields(
            definitionService,
            affiliate.encryptionKeyRef,
            recordWrapper,
            signer,
            spec = spec
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: Message> indexFields(
        definitionService: DefinitionService,
        encryptionKeyRef: KeyRef,
        t: T,
        signer: SignerImpl,
        indexParent: Boolean? = null,
        spec: ContractSpec? = null
    ): Map<String, Any>? {
        val message = when(t) {
            is RecordWrapper ->
                if (t.record.outputsList.first().hash.isEmpty()) {
                    return mapOf()
                } else {
                    definitionService.forThread {
                        osClient.getRecord(
                            t.record.resultType(),
                            t.record.outputsList.first().hash.base64Decode(),
                            encryptionKeyRef,
                        ).get()
                    }
                }
            else -> t
        }

        val messageBehavior = message.descriptorForType.getIndex(messageIndexDescriptor)
        return message.descriptorForType
            .fields
            .map { fieldDescriptor ->
                val doIndex = indexField(
                    indexParent,
                    fieldDescriptor.getIndex(indexDescriptor)?.index,
                    messageBehavior?.index
                )

                when {
                    fieldDescriptor.isRepeated -> {
                        val list = (message.getField(fieldDescriptor) as List<Any>)
                        val resultList = mutableListOf<Any>()
                        for (i in 0 until list.size) {
                            getValue(
                                definitionService,
                                encryptionKeyRef,
                                fieldDescriptor,
                                doIndex,
                                list[i],
                                signer
                            )?.let(resultList::add)
                        }
                        fieldDescriptor.jsonName to resultList.takeIf { it.isNotEmpty() }
                    }
                    fieldDescriptor.isMapField -> // Protobuf only allows Strings in the key field
                        fieldDescriptor.jsonName to (message.getField(fieldDescriptor) as Map<String, *>).mapValues { value ->
                            getValue(
                                definitionService,
                                encryptionKeyRef,
                                fieldDescriptor,
                                doIndex,
                                message.getField(fieldDescriptor),
                                signer
                            )
                        }.takeIf { it.entries.any { it.value != null } }
                    else -> fieldDescriptor.jsonName to getValue(
                        definitionService,
                        encryptionKeyRef,
                        fieldDescriptor,
                        doIndex,
                        message.getField(fieldDescriptor),
                        signer
                    )
                }
            }.filter { it.second != null }
            .takeIf { it.any { it.second != null }}
            ?.map { it.first to it.second!! }
            ?.toMap()
    }

    private fun getValue(
        definitionService: DefinitionService,
        encryptionKeyRef: KeyRef,
        fieldDescriptor: FieldDescriptor,
        doIndex: Boolean,
        value: Any,
        signer: SignerImpl
    ): Any? {
        // Get value for primitive types, on MESSAGE recurse, else empty list
        return when (fieldDescriptor.javaType) {
            INT,
            LONG,
            FLOAT,
            DOUBLE,
            BOOLEAN,
            STRING,
            BYTE_STRING -> if (doIndex) { value } else { null }
            ENUM -> if (doIndex) { (value as EnumValueDescriptor).name } else { null }
            MESSAGE -> {
                indexFields(
                    definitionService,
                    encryptionKeyRef,
                    value as Message,
                    signer,
                    doIndex
                )
            }
            else -> throw IllegalStateException("Unknown protobuf type ${fieldDescriptor.javaType}")
        }
    }

    private fun indexField(
        indexParent: Boolean?,
        fieldBehavior: Behavior?,
        messageBehavior: Behavior?
    ): Boolean {
        return when (fieldBehavior ?: messageBehavior) {
            ALWAYS -> true

            NEVER -> false

            INDEX_DEFER_PARENT -> {
                // Index if parent is true or unset (null)
                when (indexParent) {
                    null,
                    true -> true
                    false -> false
                }
            }

            UNRECOGNIZED,
            null,
            NOT_SET,
            NO_INDEX_DEFER_PARENT -> {
                // Don't index if parent is false or unset (null)
                when (indexParent) {
                    true -> true

                    null,
                    false -> false
                }
            }
        }
    }

    private fun loadAllJars(
        encryptionKeyRef: KeyRef,
        definitionService: DefinitionService,
        spec: ContractSpec,
        signer: SignerImpl
    ) {
        mutableListOf(spec.definition)
            .apply {
                addAll(spec.inputSpecsList)
                addAll(
                    spec.conditionSpecsList
                        .flatMap { listOf(it.outputSpec.spec) }
                )
                addAll(
                    spec.functionSpecsList
                        .flatMap { listOf(it.outputSpec.spec) }
                )
            }.forEach {
                definitionService.addJar(
                    encryptionKeyRef,
                    DefinitionSpec.parseFrom(it.toByteArray()),
                    signer.getPublicKey()
                )
            }
    }

    //Creates a map of session Id's to their session for easy lookup of a session from a record
    private fun makeSessionIdMap(sessionArray: List<SessionWrapper>): Map<ByteString, SessionWrapper> =
        sessionArray.associateBy { it.session.sessionId }
}

fun FieldDescriptor.getIndex(
    extensionDescriptor: FieldDescriptor
): Index? {
    // return options.getField(extensionDescriptor) as? Index
    // Pierce Trey - 03/10/2021 replaced the above line with the below to handle old versions of p8e-contract
    // that use the io.provenance.util namespaced Index class (while we only have access to the new io.p8e.util namespace)
    // which caused all fields to get skipped for indexing.
    // todo: change back to the old line once p8e-contract is converted to use the new index extension and
    // the old Index message/extension have been removed from provenance-corelib
    return options.getField(extensionDescriptor)?.let {
        try {
            Index.parseFrom((it as Message).toByteArray())
        } catch (t: Throwable) {
            LoggerFactory.getLogger(this::class.java).error("FieldDescriptor.getIndex failed to parse index extension with error", t)
            null
        }
    }
}

fun Descriptor.getIndex(
    extensionDescriptor: FieldDescriptor
): Index? {
    // return options.getField(extensionDescriptor) as? Index
    // Pierce Trey - 03/10/2021 replaced the above line with the below to handle old versions of p8e-contract
    // that use the io.provenance.util namespaced Index class (while we only have access to the new io.p8e.util namespace)
    // which caused all fields to get skipped for indexing.
    // todo: change back to the old line once p8e-contract is converted to use the new index extension and
    // the old Index message/extension have been removed from provenance-corelib
    return options.getField(extensionDescriptor)?.let {
        try {
            Index.parseFrom((it as Message).toByteArray())
        } catch (t: Throwable) {
            LoggerFactory.getLogger(this::class.java).error("Descriptor.getIndex failed to parse index extension with error", t)
            null
        }
    }
}
