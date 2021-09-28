package io.provenance.scope.examples.app.utils

import com.google.protobuf.Any
import cosmos.tx.v1beta1.TxOuterClass.TxBody
import io.grpc.Channel
import io.provenance.metadata.v1.QueryGrpc
import io.provenance.metadata.v1.ScopeRequest
import io.provenance.metadata.v1.ScopeResponse
import io.provenance.scope.encryption.crypto.SignerImpl
import io.provenance.scope.encryption.model.KeyRef
import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.sdk.SignedResult
import java.util.UUID
import java.util.concurrent.TimeUnit

class ProvenanceTxException(message: String) : Exception(message)

sealed class ProvenanceTx
class SingleTx(val value: SignedResult) : ProvenanceTx()
class BatchTx(val value: Collection<SignedResult>) : ProvenanceTx()

fun persistBatchToProvenance(transactionService: TransactionService, tx: ProvenanceTx, keyRef: KeyRef) {
    val messages = when (tx) {
        is SingleTx -> tx.value.messages
        is BatchTx -> tx.value.flatMap { it.messages }
    }
    val txBody = TxBody.newBuilder().addAllMessages(messages.map { Any.pack(it, "") }).build()
    val accountInfo = transactionService.accountInfo(keyRef.publicKey.getAddress(false))
    val signer = keyRef.signer().apply { hashType = SignerImpl.Companion.HashType.SHA256 }

    val estimate = transactionService.estimateTx(txBody, accountInfo.accountNumber, accountInfo.sequence, signer)
    val result = transactionService.batchTxBlock(txBody, accountInfo.accountNumber, accountInfo.sequence, estimate, signer)

    if (result.txResponse.code != 0) {
        throw ProvenanceTxException(result.txResponse.toString())
    }
}

fun getScope(channel: Channel, uuid: UUID): ScopeResponse {
    val metadataClient = QueryGrpc.newBlockingStub(channel).withDeadlineAfter(10, TimeUnit.SECONDS)

    return metadataClient.scope(
        ScopeRequest.newBuilder()
        .setIncludeRecords(true)
        .setIncludeSessions(true)
        .setScopeId(uuid.toString())
        .build()
    )
}
