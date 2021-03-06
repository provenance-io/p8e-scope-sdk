package io.provenance.scope.sdk

import com.google.protobuf.Message
import io.provenance.metadata.v1.MsgAddScopeDataAccessRequest
import io.provenance.metadata.v1.MsgWriteRecordRequest
import io.provenance.metadata.v1.MsgWriteScopeRequest
import io.provenance.metadata.v1.MsgWriteSessionRequest
import io.provenance.metadata.v1.Party
import io.provenance.metadata.v1.RecordInput
import io.provenance.metadata.v1.RecordInputStatus
import io.provenance.metadata.v1.RecordOutput
import io.provenance.metadata.v1.ScopeResponse
import io.provenance.scope.contract.proto.Contracts
import io.provenance.scope.contract.proto.Envelopes.EnvelopeState
import io.provenance.scope.encryption.ecies.ECUtils
import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.objectstore.util.base64Decode
import io.provenance.scope.objectstore.util.toPublicKey
import io.provenance.scope.objectstore.util.toUuid
import io.provenance.scope.sdk.extensions.getDefaultValueOwner
import io.provenance.scope.util.MetadataAddress
import io.provenance.scope.util.toByteString
import io.provenance.scope.util.toUuid

/** The result of contract execution */
sealed class ExecutionResult

/**
 * A result indicating a contract execution that has been signed by all participants and is ready for memorialization to chain
 * @property [envelopeState] the resultant [EnvelopeState] from contract execution
 * @property [messages] a list of Provenance messages to package into a transaction for memorialization to chain
 */
class SignedResult(val envelopeState: EnvelopeState) : ExecutionResult() {
    private val mainNet = envelopeState.result.mainNet
    private val signers = listOf(envelopeState.result.contract.invoker.signingPublicKey.publicKeyBytes.toByteArray().let { ECUtils.convertBytesToPublicKey(it).getAddress(mainNet) })
    private val parties = envelopeState.result.contract.recitalsList.map { Party.newBuilder()
        .setRoleValue(it.signerRoleValue)
        .setAddress(ECUtils.convertBytesToPublicKey(it.signer.signingPublicKey.publicKeyBytes.toByteArray()).getAddress(mainNet))
        .build()
    }

    private val sessionId = MetadataAddress.forSession(envelopeState.result.ref.scopeUuid.toUuid(), envelopeState.result.ref.sessionUuid.toUuid()).bytes.toByteString()
    private val contractSpecId = envelopeState.result.contract.spec.dataLocation.ref.hash.base64Decode().toUuid().let { uuid -> MetadataAddress.forContractSpecification(uuid) }

    /** @suppress */
    val executionInfo = mutableListOf<Triple<String, String, String>>()
    val messages: List<Message> = mutableListOf<Message>().apply {

        if (envelopeState.result.newScope) {
            val msgWriteScopeRequest = MsgWriteScopeRequest.newBuilder()
                .apply {
                    scopeBuilder.setScopeId(MetadataAddress.forScope(envelopeState.result.ref.scopeUuid.toUuid()).bytes.toByteString())
                        .setSpecificationId(MetadataAddress.forScopeSpecification(envelopeState.result.scopeSpecUuid.toUuid()).bytes.toByteString())
                        .addAllOwners(parties)
                        .addAllDataAccess(envelopeState.result.dataAccessList.map { it.toPublicKey().getAddress(mainNet) })
                        .setValueOwnerAddress(envelopeState.result.getDefaultValueOwner(mainNet))
                }.addAllSigners(signers)
                .build()
            executionInfo.add(
                Triple<String, String, String>(
                    envelopeState.result.ref.scopeUuid.value,
                    msgWriteScopeRequest::class.java.name,
                    "ScopeID: "
                )
            )
            add(
                msgWriteScopeRequest
            )
        } else {
            val scope = envelopeState.result.scope.unpack(ScopeResponse::class.java)
            envelopeState.result.dataAccessList
                .map { it.toPublicKey().getAddress(mainNet) }
                .filter { address -> !scope.scope.scope.dataAccessList.contains(address) && address != scope.scope.scope.valueOwnerAddress }
                .takeIf { it.isNotEmpty() }?.let { addresses ->
                    add(
                        MsgAddScopeDataAccessRequest.newBuilder()
                            .setScopeId(MetadataAddress.forScope(envelopeState.result.ref.scopeUuid.toUuid()).bytes.toByteString())
                            .addAllDataAccess(addresses)
                            .addAllSigners(signers)
                            .build()
                    )
                }
        }

        if (envelopeState.result.newSession) {
            val msgWriteSessionRequest = MsgWriteSessionRequest.newBuilder()
                .apply {
                    sessionBuilder.setSessionId(sessionId)
                        .setSpecificationId(contractSpecId.bytes.toByteString())
                        .addAllParties(parties)
                        .setName(envelopeState.result.contract.definition.resourceLocation.classname)
                }.addAllSigners(signers)
                .build()
            executionInfo.add(
                Triple<String, String, String>(
                    envelopeState.result.ref.sessionUuid.value,
                    msgWriteSessionRequest::class.java.name,
                    "Session ID: "
                )
            )
            add(
                msgWriteSessionRequest
            )
        }
    } + envelopeState.result.contract.considerationsList
        .filter { it.result.result == Contracts.ExecutionResult.Result.PASS }
        .map {
            val specId =
                MetadataAddress.forRecordSpecification(contractSpecId.getPrimaryUuid(), it.considerationName).toString()
            val msgWriteRecordRequest = MsgWriteRecordRequest.newBuilder()
                .apply {
                    recordBuilder
                        .apply {
                            processBuilder
                                .setHash(envelopeState.result.contract.definition.resourceLocation.ref.hash)
                                .setName(it.result.output.classname)
                                .setMethod(it.considerationName)
                        }
                        .setName(it.result.output.name)
                        .setSessionId(sessionId)
                        // todo: specificationId seems to be optional, but setting this actually breaks updating a record from a different contract, as record spec id changes, might be some bugs on chain side for this
                        //                    .setSpecificationId(MetadataAddress.forRecordSpecification(contractSpecId.getPrimaryUuid(), it.considerationName).bytes.toByteString())
                        .addAllInputs(it.inputsList.map { input ->
                            RecordInput.newBuilder()
                                .setName(input.name)
                                .setTypeName(input.classname)
                                .setHash(input.hash) // todo: setRecordId instead of hash in the case of an @Record annotated input (need to determine how to determine this)
                                .setStatus(RecordInputStatus.RECORD_INPUT_STATUS_PROPOSED) // todo: RECORD_INPUT_STATUS_RECORD if this is an @Record annotated input (related to above hash/record_id value above)
                                .build()
                        }).addOutputs(
                            RecordOutput.newBuilder()
                                .setHash(it.result.output.hash)
                                .setStatusValue(it.result.resultValue)
                                .build()
                        )

                }.addAllSigners(signers)
                .addAllParties(parties)
                .build()
            executionInfo.add(
                Triple<String, String, String>(
                    specId,
                    msgWriteRecordRequest::class.java.name,
                    "Record ID: "
                )
            )

            msgWriteRecordRequest
        }
}

/**
 * A result indicating that the contract execution is only partially signed and needs to be persisted and
 * mailed to other participants for signing
 *
 * @property [envelopeState] the resultant [EnvelopeState] from contract execution
 */
class FragmentResult(val envelopeState: EnvelopeState) : ExecutionResult()
