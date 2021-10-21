package io.provenance.scope.sdk

import com.google.protobuf.*
import com.google.protobuf.Any
import io.provenance.metadata.v1.ScopeResponse
import io.provenance.scope.contract.proto.*
import io.provenance.scope.contract.proto.Commons.DefinitionSpec.Type.PROPOSED
import io.provenance.scope.contract.proto.Contracts.Contract
import io.provenance.scope.contract.proto.Envelopes.Envelope
import io.provenance.scope.encryption.ecies.ECUtils
import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.sdk.ContractSpecMapper.newContract
import io.provenance.scope.sdk.ContractSpecMapper.orThrowNotFound
import io.provenance.scope.sdk.extensions.resultHash
import io.provenance.scope.sdk.extensions.uuid
import io.provenance.scope.util.toProtoUuid
import io.provenance.scope.objectstore.util.base64EncodeString
import io.provenance.scope.objectstore.util.sha256
import io.provenance.scope.objectstore.util.toPublicKey
import io.provenance.scope.proto.PK
import io.provenance.scope.proto.Util
import io.provenance.scope.sdk.extensions.sessionUuid
import io.provenance.scope.sdk.extensions.validateRecordsRequested
import io.provenance.scope.sdk.extensions.validateSessionsRequested
import io.provenance.scope.util.toUuid
import org.slf4j.LoggerFactory
import java.security.PublicKey
import java.util.*
import java.util.UUID
import java.util.UUID.randomUUID

/**
 * A Session containing details about the participants, proposed records, new/existing scope, and various other metadata
 * about a session to be executed and memorialized to the blockchain
 *
 * @property [participants] the roles/public keys of affiliates participating in this session
 * @property [proposedRecords] the records proposed by the invoking party of this session execution
 * @property [client] a [Client] object to be used for invoking affiliate details and saving objects to Object Store
 * @property [contractSpec] the [ContractSpec][Specifications.ContractSpec] of the contract being executed in this session
 * @property [provenanceReference] the location (by object store hash) of the contract code being executed in this session
 * @property [scope] the existing scope that this session is being run against, if one exists
 * @property [executionUuid] a client-side reference uuid for identifying a particular execution within a session
 * @property [scopeUuid] the uuid of the scope this session is being run against, whether new or existing
 * @property [sessionUuid] the uuid of the session
 * @property [scopeSpecUuid] the uuid of the scope specification associated with the new/existing scope
 * @property [dataAccessKeys] a list of public keys for non-participants with which to share data
 */
class Session(
    val participants: HashMap<Specifications.PartyType, PK.PublicKey>,
    val proposedRecords: HashMap<String, Message>,
    val client: Client, // TODO (wyatt) should probably move this class into the client so we have access to the
    val contractSpec: Specifications.ContractSpec,
    val provenanceReference: Commons.ProvenanceReference,
    val scope: ScopeResponse?,
    val executionUuid: UUID,
    val scopeUuid: UUID,
    val sessionUuid: UUID,
    val scopeSpecUuid: UUID,
    val dataAccessKeys: Set<PublicKey>,
) {
    /**
     * protos of proposed records to save to object store
     */
    private val stagedProposedProtos: MutableList<Message> = mutableListOf()

    private constructor(builder: Builder) : this(
        builder.participants,
        builder.proposedRecords,
        builder.client!!,
        builder.contractSpec!!,
        builder.provenanceReference!!,
        builder.scope,
        builder.executionUuid!!,
        builder.scopeUuid,
        builder.sessionUuid,
        builder.scopeSpecUuid,
        builder.dataAccessKeys.toSet(),
    )

    /**
     * A builder to set various properties on in order to produce a fully-populated [Session] object
     *
     * @property [scopeSpecUuid] the uuid of the scope specification associated with the new/existing scope
     */
    class Builder(val scopeSpecUuid: UUID) {
        /** The records proposed by the invoking party of this session execution */
        var proposedRecords: HashMap<String, Message> = HashMap()
            private set
        /** The roles/public keys of affiliates participating in this session */
        var participants: HashMap<Specifications.PartyType, PK.PublicKey> = HashMap()
            private set
        /** A [Client] object to be used for invoking affiliate details and saving objects to Object Store */
        var client: Client? = null
        /** The [ContractSpec][Specifications.ContractSpec] of the contract being executed in this session */
        var contractSpec: Specifications.ContractSpec? = null
        /** The location (by object store hash) of the contract code being executed in this session */
        var provenanceReference: Commons.ProvenanceReference? = null
        /** The uuid of the scope this session is being run against, whether new or existing */
        var scopeUuid: UUID = randomUUID()
            private set
        /** The existing scope that this session is being run against, if one exists */
        var scope: ScopeResponse? = null
            private set
        /** A client-side reference uuid for identifying a particular execution within a session */
        var executionUuid: UUID = randomUUID()
        /** The uuid of the session */
        var sessionUuid: UUID = randomUUID()
        /** A list of public keys for non-participants with which to share data */
        val dataAccessKeys: MutableList<PublicKey> = mutableListOf()

        /**
         * Build the resultant [Session] object from the current state of this [Builder]
         */
        fun build() = Session(this)

        /** Set the [executionUuid] property
         * @return this
         */
        fun setExecutionUuid(uuid: UUID) = apply {
            executionUuid = uuid
        }

        /** Set the [sessionUuid] property
         * @return this
         */
        fun setSessionUuid(sessionUuid: UUID) = apply {
            this.sessionUuid = sessionUuid
        }

        /** Set the [contractSpec] property
         * @return this
         */
        fun setContractSpec(contractSpec: Specifications.ContractSpec) = apply {
            this.contractSpec = contractSpec
        }

        /** Set the [provReference] property
         * @return this
         */
        fun setProvenanceReference(provReference: Commons.ProvenanceReference) = apply {
            provenanceReference = provReference
        }

        /** Set the [client] property
         * @return this
         */
        fun setClient(client: Client) = apply {
            this.client = client
        }

        /** Set the [scope] property, also sets the [scopeUuid] property accordingly
         * @return this
         */
        fun setScope(scopeResponse: ScopeResponse) = apply {
            scopeResponse.validateSessionsRequested()
                .validateRecordsRequested()
            scope = scopeResponse
            scopeUuid = scopeResponse.scope.scopeIdInfo.scopeUuid.toUuid()
        }

        /** Set the [scopeUuid] property
         * @throws [IllegalStateException] if the [scope] property is set
         * @return this
         */
        fun setScopeUuid(scopeUUID: UUID) = apply {
            if (scope != null) {
                throw IllegalStateException("Scope UUID cannot be set once the scope is already set")
            }
            this.scopeUuid = scopeUUID
        }

        /** Add the provided [Collection] of [PublicKey]s to the [dataAccessKeys] list for sharing contract data with these keys
         * @param [keys] the keys to add for data access
         * @return this
         */
        fun addDataAccessKeys(keys: Collection<PublicKey>) = apply {
            dataAccessKeys.addAll(keys)
        }

        /** Add a single [PublicKey] to the [dataAccessKeys] list for sharing contract data with this key
         * @param [key] the key to add for data access
         * @return this
         */
        fun addDataAccessKey(key: PublicKey) = apply {
            dataAccessKeys.add(key)
        }

        /**
         * Add a proposed record for this session execution
         * @param [name] the name of the proposed record to set
         * @param [record] the proto message to propose as a value for this record
         *
         * @return this
         *
         * @throws [NotFoundException][io.provenance.scope.sdk.ContractSpecMapper.NotFoundException] if an input with the provided [name] cannot be found
         * @throws [IllegalArgumentException] if the provided [record]'s type does not match the type of the input with the provided [name]
         *
         * @see [io.provenance.scope.contract.annotations.Input]
         */
        fun addProposedRecord(name: String, record: Message) = apply {
            val proposedSpec = listOf(
                contractSpec!!.conditionSpecsList
                    .flatMap { it.inputSpecsList }
                    .filter { it.type == PROPOSED },
                contractSpec!!.functionSpecsList
                    .flatMap { it.inputSpecsList }
                    .filter { it.type == PROPOSED }
            )
                .flatten()
                .firstOrNull { it.name == name }
                .orThrowNotFound("Can't find the proposed fact for $name")

            require(proposedSpec.resourceLocation.classname == record.defaultInstanceForType.javaClass.name)
            { "Invalid proto message supplied for $name. Expected: ${proposedSpec.resourceLocation.classname} Received: ${record.defaultInstanceForType.javaClass.name}" }

            proposedRecords[name] = record
        }

        /**
         * Add a participant to this session
         * @param [party] the [PartyType][Specifications.PartyType] role of this participant in this session
         * @param [encryptionPublicKey] the encryption public key of this party
         *
         * @return this
         *
         * @throws [NotFoundException][io.provenance.scope.sdk.ContractSpecMapper.NotFoundException] if no party of the
         * provided [party] type is found on the [contractSpec]
         * @throws [ContractDefinitionException][ContractSpecMapper.ContractDefinitionException] if a party of the
         * provided [party] type already exists in the [participants] list
         */
        fun addParticipant(party: Specifications.PartyType, encryptionPublicKey: PK.PublicKey) = apply {
            val recitalSpec = contractSpec!!.partiesInvolvedList
                .filter { it == party }
                .firstOrNull()
                .orThrowNotFound("Can't find participant for party type ${party}")

            if (participants.get(party) == null) {
                participants[party] = encryptionPublicKey
            } else {
                throw ContractSpecMapper.ContractDefinitionException("Participant for party type $party already exists in the participant list.")
            }
        }
    }

    private fun populateContract(): Contract {
//        val envelope = Envelope.getDefaultInstance()
        val builder = contractSpec.newContract()
            .setDefinition(contractSpec.definition)

        builder.invoker = PK.SigningAndEncryptionPublicKeys.newBuilder()
            .setEncryptionPublicKey(
                PK.PublicKey.newBuilder()
                    .setPublicKeyBytes(ByteString.copyFrom(ECUtils.convertPublicKeyToBytes(client.affiliate.encryptionKeyRef.publicKey)))
                    .build()
            ).setSigningPublicKey(
                PK.PublicKey.newBuilder()
                    .setPublicKeyBytes(ByteString.copyFrom(ECUtils.convertPublicKeyToBytes(client.affiliate.signingKeyRef.publicKey)))
                    .build()
            ).build()

        // Copy the outputs from previous contract executions to the inputs list.
//             spec.conditionSpecsList
//                 .filter { it.hasOutputSpec() }
//                 .map { it.outputSpec }
//                 .map { defSpec ->
//                     envelope.contract.conditionsList
//                         .filter { it.hasResult() }
//                         .filter { it.result.output.name == defSpec.spec.name }
//                         .map { it.result.output }
//                         .singleOrNull()
//                         // TODO warn if more than one output with same name.
//                         ?.let {
//                             // Only add the output to the input list if it hasn't been previously defined.
//                             if (builder.inputsList.find { fact -> fact.name == it.name } == null) {
//                                 builder.addInputs(
//                                     Fact.newBuilder()
//                                         .setName(it.name)
//                                         .setDataLocation(
//                                             Location.newBuilder()
//                                                 .setClassname(it.classname)
//                                                 .setRef(
//                                                     ProvenanceReference.newBuilder()
//                                                         .setHash(it.hash)
//                                                         .setGroupUuid(envelope.ref.groupUuid)
//                                                         .setScopeUuid(envelope.ref.scopeUuid)
//                                                         .build()
//                                                 )
//                                         )
//                                 )
//                             }
//                         }
//                 }
//        builder.addConsiderations(
//            Contracts.ConsiderationProto.newBuilder()
//                .addAllInputs(proposedRecords)
//                .build()
//        )

//        contractSpec.functionSpecsList
//            .filter { it.hasOutputSpec() }
//            .map { it.outputSpec }
//            .map { defSpec ->
//                envelope.contract.considerationsList
//                    .filter { it.hasResult() }
//                    .filter { it.result.output.name == defSpec.spec.name }
//                    .map { it.result.output }
//                    .singleOrNull()
//                    // TODO warn if more than one output with same name.
//                    ?.let {
//                        // Only add the output to the input list if it hasn't been previously defined.
//                        if (builder.inputsList.find { fact -> fact.name == it.name } == null) {
//                            builder.addInputs(
//                                Contracts.Record.newBuilder()
//                                    .setName(it.name)
//                                    .setDataLocation(
//                                        Commons.Location.newBuilder()
//                                            .setClassname(it.classname)
//                                            .setRef(
//                                                Commons.ProvenanceReference.newBuilder()
//                                                    .setHash(it.hash)
//                                                    // TODO where can these be retrieved
//                                                    .setSessionUuid(
//                                                        Util.UUID.newBuilder()
//                                                            .setValueBytes(envelope.ref.sessionUuid.valueBytes).build()
//                                                    )
//                                                    .setScopeUuid(
//                                                        Util.UUID.newBuilder()
//                                                            .setValueBytes(envelope.ref.scopeUuid.valueBytes).build()
//                                                    )
//                                                    .build()
//                                            )
//                                    )
//                            )
//                        }
//                    }
//            }

//             stagedCrossScopeFacts.forEach { (factName, refMessage) ->
//                 val (ref, message) = refMessage
//
//                 builder.populateFact(
//                     Fact.newBuilder()
//                         .setName(factName)
//                         .setDataLocation(
//                             Location.newBuilder()
//                                 .setClassname(message.javaClass.name)
//                                 .setRef(ref)
//                         ).build()
//                 )
//             }
//
//             stagedCrossScopeCollectionFacts.forEach { (factName, collection) ->
//                 collection.forEach { (ref, message) ->
//                     builder.populateFact(
//                         Fact.newBuilder()
//                             .setName(factName)
//                             .setDataLocation(
//                                 Location.newBuilder()
//                                     .setClassname(message.javaClass.name)
//                                     .setRef(ref)
//                             ).build()
//                     )
//                 }
//             }

        contractSpec.functionSpecsList
            .filter { it.inputSpecsList.find { it.type == PROPOSED } != null }
            .forEach { considerationSpec ->
                // Find the consideration impl for the spec.
                val consideration = builder.considerationsBuilderList
                    .filter { it.considerationName == considerationSpec.funcName }
                    .single()
                    .orThrowNotFound("Function not found for ${considerationSpec.funcName}")

                considerationSpec.inputSpecsList.forEach { defSpec ->
                    // Search the Function for an input that hasn't been previously satisfied
                    if (consideration.inputsList.find { it.name == defSpec.name } == null) {
                        proposedRecords[defSpec.name]?.also {
                            consideration.addInputs(
                                Contracts.ProposedRecord.newBuilder()
                                    .setClassname(defSpec.resourceLocation.classname)
                                    .setHash(it.toByteArray().sha256().base64EncodeString())
                                    .setName(defSpec.name)
                                    .build()
                            ).also {
                                // Clear any previous skip result if there is a proposed fact for the consideration.
                                if (it.result.resultValue == Contracts.ExecutionResult.Result.SKIP_VALUE) {
                                    it.clearResult()
                                }
                            }

                            // Prepare for upload
                            // TODO idk what these are. Add this to the variables that need to be filled.
                            if (!stagedProposedProtos.contains(it))
                                stagedProposedProtos.add(it)
                        }
                    }
                }
            }

        if (scope != null) {
            scope.recordsList
                .associateBy { it.record.name }
                .forEach { (recordName, scopeRecord) ->
                    builder.populateRecord(
                        Contracts.Record.newBuilder()
                            .setName(recordName)
                            .setDataLocation(
                                Commons.Location.newBuilder()
                                    .setClassname(scopeRecord.record.process.name)
                                    .setRef(
                                        Commons.ProvenanceReference.newBuilder()
                                            .setScopeUuid(scope.uuid().toProtoUuid())
                                            .setSessionUuid(scopeRecord.record.sessionUuid().toProtoUuid())
                                            .setHash(
                                                scopeRecord.record.resultHash().base64EncodeString()
                                            ) // todo: address for multiple outputs
                                    )
                            ).build()
                    )
                }
        }

        val formattedStagedRecitals = participants.map { (partyType, publicKey) ->
            Contracts.Recital.newBuilder()
                .setSignerRole(partyType)
                .also { recitalBuilder ->
                    recitalBuilder
                        .setSigner(
                            // Setting single key for both Signing and Encryption key fields, service will handle updating key fields.
                            PK.SigningAndEncryptionPublicKeys.newBuilder()
                                .setSigningPublicKey(publicKey)
                                .setEncryptionPublicKey(publicKey)
                                .build()
                        )
                }
                .build()
        }

        builder.clearRecitals()
        //TODO How should partieslist be handled? There doesn't seem to be an equivalent in the scope being used
//        if (scope != null) {
//            builder.addAllRecitals(
//                formattedStagedRecitals.plus(scope.partiesList).distinctBy { recital -> recital.signerRole })
//        } else {
//            builder.addAllRecitals(formattedStagedRecitals)
//        }
        builder.addAllRecitals(formattedStagedRecitals)

        builder.timesExecuted++

        return builder.build()
    }

     fun packageContract(mainNet: Boolean): Envelope {
         if (scope != null) {
             val sessionDataAccessAddresses = dataAccessKeys.map { it.getAddress(mainNet) }.toSet()

             sessionDataAccessAddresses.forEach { key ->
                 if (!scope.scope.scope.dataAccessList.contains(key)) {
                     throw IllegalStateException("$key was added with data access in this session but does not have access in the existing scope.")
                 }
             }

             scope.scope.scope.dataAccessList.forEach { key ->
                 if (!sessionDataAccessAddresses.contains(key)) {
                     throw IllegalStateException("$key has data access in the existing scope but was not added to the data access list in this session.")
                 }
             }
         }

        val contract = populateContract()
        //TODO Different executionID
        val permissionUpdater = PermissionUpdater(
            client,
            contract,
            contract.toAudience(scope) + dataAccessKeys,
        )
        // Build the envelope for this execution
        val envelope = Envelope.newBuilder()
            .setExecutionUuid(executionUuid.toProtoUuid())
            .setScopeSpecUuid(scopeSpecUuid.toProtoUuid())
            .setNewScope(scope == null)
            .setNewSession(scope?.sessionsList?.find { it.sessionIdInfo.sessionUuid.toUuid() == sessionUuid } == null)
            .addAllDataAccess(dataAccessKeys.map { it.toPublicKeyProto() })
            .setMainNet(mainNet)
            .setContract(contract)
            .apply {
                // stagedPrevExecutionUuid?.run { builder.prevExecutionUuid = this }
                // stagedExpirationTime?.run { builder.expirationTime = toProtoTimestamp() } ?: builder.clearExpirationTime()
                refBuilder
                    .setScopeUuid(scopeUuid.toProtoUuid())
                    .setSessionUuid(sessionUuid.toProtoUuid())
                    .setHash(contract.toByteArray().sha256().base64EncodeString())
                    .build()

                if (this@Session.scope != null) {
                    setScope(Any.pack(this@Session.scope, ""))
                }
            }
            .clearSignatures()
            .build()

        permissionUpdater.saveConstructorArguments()

        permissionUpdater.saveProposedFacts(this.proposedRecords.values)

        return envelope
    }

    private fun Contract.Builder.populateRecord(record: Contracts.Record) {
        inputsBuilderList.firstOrNull {
            isMatchingRecord(it, record.name)
        }?.apply {
            dataLocation = Commons.Location.newBuilder()
                .setClassname(record.dataLocation.classname)
                .setRef(
                    Commons.ProvenanceReference.newBuilder()
                        .setSessionUuid(
                            Util.UUID.newBuilder().setValueBytes(record.dataLocation.ref.sessionUuid.valueBytes).build()
                        )
                        .setHash(record.dataLocation.ref.hash)
                        .setScopeUuid(
                            Util.UUID.newBuilder().setValueBytes(record.dataLocation.ref.scopeUuid.valueBytes).build()
                        )
                        .setName(record.dataLocation.ref.name)
                        .build()
                )

                .build()
        }
    }

    private fun Contract.toAudience(scope: ScopeResponse?): Set<PublicKey> = recitalsList.map {
        it.signer.encryptionPublicKey.toPublicKey()
    }.toSet()
//    .plus(scope.scope.scope.ownersList.map { // todo: add scope owners to list, need AffiliateRepository
//        it.address
//    })

    private fun isMatchingRecord(inputRecord: Contracts.Record.Builder, recordName: String): Boolean {
        return inputRecord.name == recordName && inputRecord.dataLocation.ref == Commons.ProvenanceReference.getDefaultInstance()
    }

    class PermissionUpdater(
        private val client: Client,
        private val contract: Contract,
        private val audience: Set<java.security.PublicKey>
    ) {
        private val log = LoggerFactory.getLogger(this::class.java);
        fun saveConstructorArguments() {
            // TODO (later) this can be optimized by checking the recitals and record groups and determining what subset, if any,
            // of input facts need to be fetched and stored in order only save the objects that are needed by some of
            // the recitals
//            contract.inputsList.map { record ->
//                with(client) {
////                     val obj = this.loadObject(record.dataLocation.ref.hash)
////                     val inputStream = ByteArrayInputStream(obj)
////
////                     this.storeObject(inputStream, audience)
//                }
//            }
        }

        // TODO (steve) for later convert to async with ListenableFutures
        fun saveProposedFacts(stagedProposedProtos: Collection<Message>) {
            log.debug("Persisting ${stagedProposedProtos.size} record(s) to object store")
            stagedProposedProtos.map {
                client.inner.osClient.putRecord(
                    it,
                    client.affiliate.signingKeyRef,
                    client.affiliate.encryptionKeyRef,
                    audience
                )
            }.map { it.get() } // TODO is this the best way to await N items?
        }
    }
}
