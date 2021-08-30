package io.provenance.scope.sdk

import com.google.protobuf.Message
import io.provenance.metadata.v1.MsgWriteRecordRequest
import io.provenance.metadata.v1.MsgWriteScopeRequest
import io.provenance.metadata.v1.MsgWriteSessionRequest
import io.provenance.metadata.v1.Party
import io.provenance.metadata.v1.RecordInput
import io.provenance.metadata.v1.RecordInputStatus
import io.provenance.metadata.v1.RecordOutput
import io.provenance.scope.contract.proto.Contracts
import io.provenance.scope.contract.proto.Envelopes.Envelope
import io.provenance.scope.encryption.ecies.ECUtils
import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.objectstore.util.base64Decode
import io.provenance.scope.objectstore.util.toUuid
import io.provenance.scope.util.MetadataAddress
import io.provenance.scope.util.toByteString
import java.util.UUID

sealed class ExecutionResult
class SignedResult(session: Session, val envelope: Envelope, private val mainNet: Boolean): ExecutionResult() {
    private val signers = envelope.signaturesList.map { ECUtils.convertBytesToPublicKey(it.signer.signingPublicKey.publicKeyBytes.toByteArray()).getAddress(mainNet) }  // todo: correct address/pk?
    private val parties = envelope.contract.recitalsList.map { Party.newBuilder()
        .setRoleValue(it.signerRoleValue)
        .setAddress(ECUtils.convertBytesToPublicKey(it.signer.signingPublicKey.publicKeyBytes.toByteArray()).getAddress(mainNet)) // todo: correct address/pk?
        .build()
    }

    private val sessionId = MetadataAddress.forSession(session.scopeUuid, session.sessionUuid).bytes.toByteString()
    private val contractSpecId = envelope.contract.spec.dataLocation.ref.hash.base64Decode().toUuid().let { uuid -> MetadataAddress.forContractSpecification(uuid) }

    val messages: List<Message> = mutableListOf<Message>().apply {
            if (session.scope == null) {
                add(
                    MsgWriteScopeRequest.newBuilder()
                        .apply {
                            scopeBuilder.setScopeId(MetadataAddress.forScope(session.scopeUuid).bytes.toByteString())
                                .setSpecificationId(MetadataAddress.forScopeSpecification(session.scopeSpecUuid).bytes.toByteString())
                                .addAllOwners(parties)
                        }.addAllSigners(signers)
                        .build()
                )
            }

            if (session.proposedSession == null) {
                add(
                    MsgWriteSessionRequest.newBuilder()
                        .apply {
                            sessionBuilder.setSessionId(sessionId)
                                .setSpecificationId(contractSpecId.bytes.toByteString())
                                .addAllParties(parties)
                                .setName(envelope.contract.definition.resourceLocation.classname)
                        }.addAllSigners(signers)
                        .build()
                )
            }
        } + envelope.contract.considerationsList
            .filter { it.result.result == Contracts.ExecutionResult.Result.PASS }
            .map {
                MsgWriteRecordRequest.newBuilder()
                    .apply {
                        recordBuilder
                            .apply {
                                processBuilder
                                    .setHash(envelope.contract.definition.resourceLocation.ref.hash)
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
                            }).addOutputs(RecordOutput.newBuilder()
                                .setHash(it.result.output.hash)
                                .setStatusValue(it.result.resultValue)
                                .build()
                            )

                    }.addAllSigners(signers)
                    .addAllParties(parties)
                    .build()
            }
}
class FragmentResult(val input: Envelope, val result: Envelope): ExecutionResult()
