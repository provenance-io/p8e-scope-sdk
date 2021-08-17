package io.provenance.scope.sdk

import io.provenance.metadata.v1.MsgWriteRecordRequest
import io.provenance.metadata.v1.RecordInput
import io.provenance.metadata.v1.RecordInputStatus
import io.provenance.metadata.v1.RecordOutput
import io.provenance.scope.contract.proto.Envelopes
import io.provenance.scope.contract.proto.Envelopes.Envelope
import io.provenance.scope.util.toByteString

sealed class ExecutionResult
class SignedResult(val envelope: Envelope): ExecutionResult() {
    // todo: do we need to add a separate WriteSession message, or will just supplying the sessionId/process information on MsgWriteRecordReqeust create/update the appropriate session on chain?
    // yes, probably need the WriteSession as well as maybe a WriteScope first
    val messages = envelope.contract.considerationsList.map {
        MsgWriteRecordRequest.newBuilder()
            .apply {
                recordBuilder
                    .apply {
                        processBuilder
                            .setHash(envelope.contract.spec.dataLocation.ref.hash)
                            .setName(envelope.contract.spec.dataLocation.classname)
                            .setMethod(it.considerationName)
                    }
                    .setName(it.considerationName)
                    .setSessionId(envelope.executionUuid.value.toByteString())
//                    .setSpecificationId(envelope.contract.) // todo: where can we get a record spec id from? do we actually hash the spec proto? // MetadataAddress can construct this from contract spec uuid and record name
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
                // todo: add signer addresses (do signatures just end up on the transaction??? How will that work w/ multiparty?)
                // todo: set parties

            }
            .build()
    }
}
class FragmentResult(val input: Envelope, val result: Envelope)
