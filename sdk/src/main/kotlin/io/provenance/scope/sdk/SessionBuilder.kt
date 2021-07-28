package io.provenance.scope.sdk

import com.google.protobuf.Message
import io.provenance.metadata.v1.Session
import io.provenance.scope.contract.proto.Contracts.Contract
import io.provenance.scope.contract.proto.Envelopes.Envelope
import io.provenance.scope.contract.proto.Specifications
import io.provenance.scope.objectstore.util.sha512
import java.security.PublicKey
import java.util.*
import kotlin.collections.HashMap

class Session(
    val proposedSession: Session?,
    val participants: HashMap<Specifications.PartyType, PublicKey>,
    val proposedRecords: HashMap<String, Message>,
    val client: Client, // TODO (wyatt) should probably move this class into the client so we have access to the client
) {
    private constructor(builder: Builder) : this(builder.proposedSession, builder.participants, builder.proposedRecords, builder.client!!)

    class Builder {
        var proposedSession: Session? = null
        private set
        var proposedRecords: HashMap<String, Message> = HashMap()
        private set
        var participants: HashMap<Specifications.PartyType, PublicKey> = HashMap()
        private set
        var client: Client? = null

        fun build() = Session(this)

        // fun addProposedSession(session: Session) = apply {
        //     this.proposedSession = session
        // }

        // TODO (wyatt) pull validation logic from addProposedFact function
        fun addProposedRecord(name: String, record: Message) {

            // if valid add record to proposedRecords map
        }

        // TODO (wyatt) check if party is already present in the map and if so throw an exception - I think the existing
        // Contract.kt does this
        fun addParticipant(party: Specifications.PartyType, encryptionPublicKey: PublicKey) = apply {
            this.participants.put(party, encryptionPublicKey)
        }

    }

    // TODO add execute function - packages ContractScope.Envelope and calls execute on it
    private fun populateContract(): Contract {
        return Contract.getDefaultInstance()
    //     val builder = envelope.contract.toBuilder()

    //     builder.invoker = PK.SigningAndEncryptionPublicKeys.newBuilder()
    //         .setEncryptionPublicKey(contractManager.keyPair.public.toPublicKeyProto())
    //         .setSigningPublicKey(contractManager.keyPair.public.toPublicKeyProto())
    //         .build()

    //     // Copy the outputs from previous contract executions to the inputs list.
    //     spec.conditionSpecsList
    //         .filter { it.hasOutputSpec() }
    //         .map { it.outputSpec }
    //         .map { defSpec ->
    //             envelope.contract.conditionsList
    //                 .filter { it.hasResult() }
    //                 .filter { it.result.output.name == defSpec.spec.name }
    //                 .map { it.result.output }
    //                 .singleOrNull()
    //                 // TODO warn if more than one output with same name.
    //                 ?.let {
    //                     // Only add the output to the input list if it hasn't been previously defined.
    //                     if (builder.inputsList.find { fact -> fact.name == it.name } == null) {
    //                         builder.addInputs(
    //                             Fact.newBuilder()
    //                                 .setName(it.name)
    //                                 .setDataLocation(
    //                                     Location.newBuilder()
    //                                         .setClassname(it.classname)
    //                                         .setRef(
    //                                             ProvenanceReference.newBuilder()
    //                                                 .setHash(it.hash)
    //                                                 .setGroupUuid(envelope.ref.groupUuid)
    //                                                 .setScopeUuid(envelope.ref.scopeUuid)
    //                                                 .build()
    //                                         )
    //                                 )
    //                         )
    //                     }
    //                 }
    //         }

    //     spec.considerationSpecsList
    //         .filter { it.hasOutputSpec() }
    //         .map { it.outputSpec }
    //         .map { defSpec ->
    //             envelope.contract.considerationsList
    //                 .filter { it.hasResult() }
    //                 .filter { it.result.output.name == defSpec.spec.name }
    //                 .map { it.result.output }
    //                 .singleOrNull()
    //                 // TODO warn if more than one output with same name.
    //                 ?.let {
    //                     // Only add the output to the input list if it hasn't been previously defined.
    //                     if (builder.inputsList.find { fact -> fact.name == it.name } == null) {
    //                         builder.addInputs(
    //                             Fact.newBuilder()
    //                                 .setName(it.name)
    //                                 .setDataLocation(
    //                                     Location.newBuilder()
    //                                         .setClassname(it.classname)
    //                                         .setRef(
    //                                             ProvenanceReference.newBuilder()
    //                                                 .setHash(it.hash)
    //                                                 .setGroupUuid(envelope.ref.groupUuid)
    //                                                 .setScopeUuid(envelope.ref.scopeUuid)
    //                                                 .build()
    //                                         )
    //                                 )
    //                         )
    //                     }
    //                 }
    //         }

    //     // All facts should already be loaded to the system. No need to send them to POS.
    //     stagedFacts.forEach {
    //         val msg = it.value.second.second
    //         val ref = it.value.second.first.takeUnless { ref -> ref == ProvenanceReference.getDefaultInstance() }
    //             ?: ProvenanceReference.newBuilder().setHash(msg.toByteArray().base64Sha512()).build()

    //         builder.addInputs(
    //             Fact.newBuilder()
    //                 .setName(it.key)
    //                 .setDataLocation(
    //                     Location.newBuilder()
    //                         .setClassname(msg.javaClass.name)
    //                         .setRef(ref)
    //                 )
    //         )
    //     }

    //     stagedCrossScopeFacts.forEach { (factName, refMessage) ->
    //         val (ref, message) = refMessage

    //         builder.populateFact(
    //             Fact.newBuilder()
    //                 .setName(factName)
    //                 .setDataLocation(
    //                     Location.newBuilder()
    //                         .setClassname(message.javaClass.name)
    //                         .setRef(ref)
    //                 ).build()
    //         )
    //     }

    //     stagedCrossScopeCollectionFacts.forEach { (factName, collection) ->
    //         collection.forEach { (ref, message) ->
    //             builder.populateFact(
    //                 Fact.newBuilder()
    //                     .setName(factName)
    //                     .setDataLocation(
    //                         Location.newBuilder()
    //                             .setClassname(message.javaClass.name)
    //                             .setRef(ref)
    //                     ).build()
    //             )
    //         }
    //     }

    //     spec.considerationSpecsList
    //         .filter { it.inputSpecsList.find { it.type == PROPOSED } != null }
    //         .forEach { considerationSpec ->
    //             // Find the consideration impl for the spec.
    //             val consideration = builder.considerationsBuilderList
    //                 .filter { it.considerationName == considerationSpec.funcName }
    //                 .single()
    //                 .orThrowNotFound("Function not found for ${considerationSpec.funcName}")

    //             considerationSpec.inputSpecsList.forEach { defSpec ->
    //                 // Search the Function for an input that hasn't been previously satisfied
    //                 if (consideration.inputsList.find { it.name == defSpec.name } == null) {
    //                     stagedProposedFacts[defSpec.name]?.also {
    //                         consideration.addInputs(
    //                             ProposedFact.newBuilder()
    //                                 .setClassname(defSpec.resourceLocation.classname)
    //                                 .setHash(it.second.toByteArray().base64Sha512())
    //                                 .setName(defSpec.name)
    //                                 .build()
    //                         ).also {
    //                             // Clear any previous skip result if there is a proposed fact for the consideration.
    //                             if(it.result.resultValue == ExecutionResult.Result.SKIP_VALUE) {
    //                                 it.clearResult()
    //                             }
    //                         }

    //                         // Prepare for upload
    //                         if (!stagedProposedProtos.contains(it.second))
    //                             stagedProposedProtos.add(it.second)
    //                     }
    //                 }
    //             }
    //         }

    //     val scope = envelope.scope.takeUnless { it == Scope.getDefaultInstance() }
    //     if (scope != null) {
    //         scope.recordGroupList
    //             .flatMap { it.recordsList }
    //             .associateBy { it.resultName }
    //             .forEach { (factName, scopeFact) ->
    //                 builder.populateFact(
    //                     Fact.newBuilder()
    //                         .setName(factName)
    //                         .setDataLocation(
    //                             Location.newBuilder()
    //                                 .setClassname(scopeFact.classname)
    //                                 .setRef(
    //                                     ProvenanceReference.newBuilder()
    //                                         .setScopeUuid(scope.uuid)
    //                                         .setHash(scopeFact.resultHash)
    //                                 )
    //                         ).build()
    //                 )
    //             }
    //     }

    //     val formattedStagedRecitals = stagedRecital.map { (partyType, data) ->
    //         Recital.newBuilder()
    //             .setSignerRole(partyType)
    //             .also { recitalBuilder ->
    //                 when (data) {
    //                     is RecitalAddress -> recitalBuilder
    //                         .setAddress(data.address.toByteString())
    //                     is RecitalPublicKey -> recitalBuilder
    //                         .setSigner(
    //                             // Setting single key for both Signing and Encryption key fields, service will handle updating key fields.
    //                             PK.SigningAndEncryptionPublicKeys.newBuilder()
    //                                 .setSigningPublicKey(data.key.toPublicKeyProto())
    //                                 .setEncryptionPublicKey(data.key.toPublicKeyProto())
    //                                 .build()
    //                         )
    //                 } as Recital.Builder
    //             }
    //             .build()
    //     }

    //     builder.clearRecitals()
    //     if (scope != null) {
    //         builder.addAllRecitals(formattedStagedRecitals.plus(scope.partiesList).distinctBy { it.signerRole })
    //     } else {
    //         builder.addAllRecitals(formattedStagedRecitals)
    //     }

    //     builder.timesExecuted ++

    //     return builder.build()
    }

    private fun packageContract(): Envelope {
        val contract = populateContract()

        val permissionUpdater = PermissionUpdater(
            client,
            contract,
            emptySet(), // TODO this is audience list
        )

        // Build the envelope for this execution
        val envelope = Envelope.newBuilder()
            // TODO (wyatt) the proto Session should have execution uuid
            // .setExecutionUuid(this.stagedExecutionUuid)
            .setContract(contract)
            .also {
                // stagedPrevExecutionUuid?.run { builder.prevExecutionUuid = this }
                // stagedExpirationTime?.run { builder.expirationTime = toProtoTimestampProv() } ?: builder.clearExpirationTime()
                it.ref = it.refBuilder
                    // TODO (wyatt) add extension function for Message.base64Sha512()
                    .setHash(String(Base64.getEncoder().encode(contract.toByteArray().sha512())))
                    .build()
            }
            .clearSignatures()
            .build()

        permissionUpdater.saveConstructorArguments()

        // TODO (wyatt) the proto Session should have execution uuid
        // permissionUpdater.saveProposedFacts(this.stagedExecutionUuid.toUuidProv(), this.proposedRecords.values)

        return envelope
    }
}

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
            with (client) {
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
