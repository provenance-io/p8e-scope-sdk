package io.provenance.scope.sdk

import com.google.protobuf.*
import io.provenance.metadata.v1.*
import io.provenance.metadata.v1.Session
import io.provenance.metadata.v1.p8e.Fact
import io.provenance.metadata.v1.p8e.Location
import io.provenance.metadata.v1.p8e.ProvenanceReference
import io.provenance.metadata.v1.p8e.PublicKey
import io.provenance.scope.contract.proto.*
import io.provenance.scope.contract.proto.Commons.DefinitionSpec.Type.PROPOSED
import io.provenance.scope.contract.proto.Contracts.Contract
import io.provenance.scope.contract.proto.Envelopes.Envelope
import io.provenance.scope.contract.proto.Utils.UUID
import io.provenance.scope.encryption.ecies.ECUtils
import io.provenance.scope.objectstore.util.sha512
import io.provenance.scope.sdk.ContractSpecMapper.orThrowNotFound
import io.provenance.scope.sdk.extensions.resultHash
import io.provenance.scope.sdk.extensions.uuid
import java.util.*

class Session(
    val proposedSession: Session?,
    val participants: HashMap<Specifications.PartyType, PublicKeys.PublicKey>,
    val proposedRecords: HashMap<String, Message>,
    val client: Client, // TODO (wyatt) should probably move this class into the client so we have access to the
    val spec: Specifications.ContractSpec,
    val affiliate: Affiliate,
    val scope: ScopeResponse?,
    val stagedProposedProtos: MutableList<Message> = mutableListOf(),
) {
    private constructor(builder: Builder) : this(
        builder.proposedSession,
        builder.participants,
        builder.proposedRecords,
        builder.client!!,
        builder.spec,
        builder.affiliate!!,
        builder.scope
    )

    // TODO perhaps add scope
    class Builder(val spec: Specifications.ContractSpec, val scope: ScopeResponse?) {
        var proposedSession: Session? = null
            private set
        var proposedRecords: HashMap<String, Message> = HashMap()
            private set
        var participants: HashMap<Specifications.PartyType, PublicKeys.PublicKey> = HashMap()
            private set
        var client: Client? = null

        var affiliate: Affiliate? = null

        fun build() = Session(this)

        // fun addProposedSession(session: Session) = apply {
        //     this.proposedSession = session
        // }

        fun addProposedRecord(name: String, record: Message) {
            val proposedSpec = listOf(
                spec.conditionSpecsList
                    .flatMap { it.inputSpecsList }
                    .filter { it.type == PROPOSED },
                spec.functionSpecsList
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

        fun addParticipant(party: Specifications.PartyType, encryptionPublicKey: PublicKeys.PublicKey) = apply {
            val recitalSpec = spec.partiesInvolvedList
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

    //    // TODO add execute function - packages ContractScope.Envelope and calls execute on it
    private fun populateContract(): Contract {
//        return Contract.getDefaultInstance()
        val envelope = Envelope.getDefaultInstance()
        val builder = Contract.getDefaultInstance().toBuilder()

        builder.invoker = PublicKeys.SigningAndEncryptionPublicKeys.newBuilder()
            // TODO Where can these be retrieved? Try passing in affiliate
            .setEncryptionPublicKey(affiliate.encryptionKeyRef.publicKey.toPublicKeyProtoOS())
            .setSigningPublicKey(affiliate.signingKeyRef.publicKey.toPublicKeyProtoOS())
            .build()

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

        spec.functionSpecsList
            .filter { it.hasOutputSpec() }
            .map { it.outputSpec }
            .map { defSpec ->
                envelope.contract.considerationsList
                    .filter { it.hasResult() }
                    .filter { it.result.output.name == defSpec.spec.name }
                    .map { it.result.output }
                    .singleOrNull()
                    // TODO warn if more than one output with same name.
                    ?.let {
                        // Only add the output to the input list if it hasn't been previously defined.
                        if (builder.inputsList.find { fact -> fact.name == it.name } == null) {
                            builder.addInputs(
                                Contracts.Record.newBuilder()
                                    .setName(it.name)
                                    .setDataLocation(
                                        Commons.Location.newBuilder()
                                            .setClassname(it.classname)
                                            .setRef(
                                                Commons.ProvenanceReference.newBuilder()
                                                    .setHash(it.hash)
                                                    // TODO where can these be retrieved
                                                    .setGroupUuid(
                                                        UUID.newBuilder()
                                                            .setValueBytes(envelope.ref.groupUuid.valueBytes).build()
                                                    )
                                                    .setScopeUuid(
                                                        UUID.newBuilder()
                                                            .setValueBytes(envelope.ref.scopeUuid.valueBytes).build()
                                                    )
                                                    .build()
                                            )
                                    )
                            )
                        }
                    }
            }

        // All facts should already be loaded to the system. No need to send them to POS.
        proposedRecords.map { (name, record) ->
            val ref = record.takeUnless { ref -> ref == Commons.ProvenanceReference.getDefaultInstance() }
                ?: Commons.ProvenanceReference.newBuilder().setHash(record.toByteArray().base64Sha512()).build()

            builder.addInputs(
                Contracts.Record.newBuilder()
                    .setName(name)
                    .setDataLocation(
                        Commons.Location.newBuilder()
                            .setClassname(record.javaClass.name)
                        // TODO figure out how to get the provenance ref
//                            .setRef(ref)
                    )
            )
        }

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

        spec.functionSpecsList
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
                                    .setHash(it.toByteArray().base64Sha512())
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

        // TODO (Steve/Pierce) This is probably wrong. I took a stab at it.
//        val scope = envelope.scope.takeUnless { it == Scope.getDefaultInstance() }
        if (scope != null) {
            scope.recordsList
                .associateBy { it.record.name }
                .forEach { (factName, scopeFact) ->
                    builder.populateFact(
                        Fact.newBuilder()
                            .setName(factName)
                            .setDataLocation(
                                Location.newBuilder()
                                    .setClassname(scopeFact.record.name)
                                    .setRef(
                                        ProvenanceReference.newBuilder()
                                            .setScopeUuid(io.provenance.metadata.v1.p8e.UUID.newBuilder()
                                                .setValue(scope.uuid()))
                                            .setHash(scopeFact.record.resultHash().base64Sha512())
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
                            PublicKeys.SigningAndEncryptionPublicKeys.newBuilder()
                                .setSigningPublicKey(publicKey)
                                .setEncryptionPublicKey(publicKey)
                                .build()
                        )
                }
                .build()
        }

        builder.clearRecitals()
        //TODO How should partieslist be handled? There doesn't seem to be an equivalent in the scope being used
        if (scope != null) {
            builder.addAllRecitals(
                formattedStagedRecitals.plus(scope.partiesList).distinctBy { recital -> recital.signerRole })
        } else {
            builder.addAllRecitals(formattedStagedRecitals)
        }
        builder.addAllRecitals(formattedStagedRecitals)

        builder.timesExecuted++

        return builder.build()
    }


    private fun packageContract(): Envelope {
        val contract = populateContract()
        val executionUuid = UUID.newBuilder().setValueBytes(proposedSession?.sessionId).build()

        val permissionUpdater = PermissionUpdater(
            client,
            contract,
            emptySet(), // TODO this is audience list
        )
        // Build the envelope for this execution
        val envelope = Envelope.newBuilder()
            .setExecutionUuid(executionUuid)
            .setContract(contract)
            .also {
                // stagedPrevExecutionUuid?.run { builder.prevExecutionUuid = this }
                // stagedExpirationTime?.run { builder.expirationTime = toProtoTimestampProv() } ?: builder.clearExpirationTime()
                it.ref = it.refBuilder
                    .setHash(String(contract.base64Sha512()))
                    .build()
            }
            .clearSignatures()
            .build()

        permissionUpdater.saveConstructorArguments()

        permissionUpdater.saveProposedFacts(executionUuid, this.proposedRecords.values)

        return envelope
    }

    private fun Contract.Builder.populateFact(fact: Fact) {
        inputsBuilderList.firstOrNull {
            isMatchingFact(it, fact.name)
        }?.apply {
            dataLocation = Commons.Location.newBuilder()
                .setClassname(fact.dataLocation.classname)
                .setRef(
                    Commons.ProvenanceReference.newBuilder()
                        .setGroupUuid(
                            UUID.newBuilder().setValueBytes(fact.dataLocation.ref.groupUuid.valueBytes).build()
                        )
                        .setHash(fact.dataLocation.ref.hash)
                        .setScopeUuid(
                            UUID.newBuilder().setValueBytes(fact.dataLocation.ref.scopeUuid.valueBytes).build()
                        )
                        .setName(fact.dataLocation.ref.name)
                        .build()
                )

                .build()
        }
    }

    private fun isMatchingFact(inputFact: Contracts.Record.Builder, factName: String): Boolean {
        return inputFact.name == factName && inputFact.dataLocation.ref == Commons.ProvenanceReference.getDefaultInstance()
    }

    fun ByteArray.base64String() = String(Base64.getEncoder().encode(this))
    fun ByteArray.base64Sha512() = this.sha512().base64String()
    fun Message.base64Sha512() = Base64.getEncoder().encode(this.toByteArray().sha512())
    fun PublicKey.toPublicKeyProto(): PublicKeys.PublicKey =
        PublicKeys.PublicKey.newBuilder()
            .setCurve(PublicKeys.KeyCurve.SECP256K1)
            .setType(PublicKeys.KeyType.ELLIPTIC)
            .setPublicKeyBytes(this.toByteString())
            .setCompressed(false)
            .build()

    fun java.security.PublicKey.toPublicKeyProtoOS(): PublicKeys.PublicKey =
        PublicKeys.PublicKey.newBuilder()
            .setPublicKeyBytes(ECUtils.convertPublicKeyToBytes(this).toByteString())
            .build()

    fun ByteArray.toByteString() = ByteString.copyFrom(this)

    class PermissionUpdater(
        private val client: Client,
        private val contract: Contract,
        private val audience: Set<PublicKey>
    ) {

        fun saveConstructorArguments() {
            // TODO (later) this can be optimized by checking the recitals and record groups and determining what subset, if any,
            // of input facts need to be fetched and stored in order only save the objects that are needed by some of
            // the recitals
            contract.inputsList.map { fact ->
                with(client) {
                    // val obj = this.loadObject(fact.dataLocation.ref.hash)
                    // val inputStream = ByteArrayInputStream(obj)

                    // this.storeObject(inputStream, audience)
                }
            }
        }

        // TODO (steve) for later convert to async with ListenableFutures
        fun saveProposedFacts(stagedExecutionUuid: UUID, stagedProposedProtos: Collection<Message>) {
            stagedProposedProtos.map {
                // client.saveProto(it, stagedExecutionUuid, audience)
            }
        }
    }
}