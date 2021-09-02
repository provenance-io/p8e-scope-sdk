package io.provenance.scope.examples.app

import com.google.protobuf.Any
import cosmos.tx.v1beta1.TxOuterClass.TxBody
import io.grpc.Channel
import io.provenance.metadata.v1.QueryGrpc
import io.provenance.metadata.v1.ScopeRequest
import io.provenance.metadata.v1.ScopeResponse
import io.provenance.scope.encryption.model.DirectKeyRef
import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.sdk.SignedResult
import java.security.KeyPair
import java.util.UUID
import java.util.concurrent.TimeUnit

fun DirectKeyRef.keyPair(): KeyPair = KeyPair(this.publicKey, this.privateKey)

class ProvenanceTxException(message: String) : Exception(message)

fun persistBatchToProvenance(transactionService: TransactionService, result: SignedResult, keyRef: DirectKeyRef) {
    val txBody = TxBody.newBuilder().addAllMessages(result.messages.map { Any.pack(it, "") }).build()
    val accountInfo = transactionService.accountInfo(keyRef.publicKey.getAddress(false))
    val estimate = transactionService.estimateTx(txBody, accountInfo.accountNumber, accountInfo.sequence, keyRef.keyPair())
    val result = transactionService.batchTxBlock(txBody, accountInfo.accountNumber, accountInfo.sequence, estimate, keyRef.keyPair())

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
