package io.provenance.scope.sdk.proxy

import com.google.protobuf.Message
import io.provenance.scope.contract.proto.Contracts
import io.provenance.scope.contract.proto.Contracts.ExecutionResult.Result.*
import io.provenance.scope.contract.proto.Envelopes.Envelope
import io.provenance.scope.encryption.model.KeyRef
import io.provenance.scope.objectstore.client.CachedOsClient
import io.provenance.scope.objectstore.util.base64Decode

class Contract(
    val envelope: Envelope,
    private val osClient: CachedOsClient,
    private val encryptionKeyRef: KeyRef,
) {
    @Suppress("UNCHECKED_CAST")
    fun <T: Message> getProposedRecord(
        clazz: Class<T>,
        proposedRecordName: String,
    ): T? = envelope.contract
        .considerationsList
        .flatMap { it.inputsList }
        .find { it.classname == clazz.name && it.name == proposedRecordName }
        ?.let { osClient.getRecord(it.classname, it.hash.base64Decode(), encryptionKeyRef).get() as T }

    @Suppress("UNCHECKED_CAST")
    fun <T: Message> getResult(
        clazz: Class<T>,
        resultName: String,
    ): T? = findResult(clazz, resultName)
        ?.takeIf { it.result.result == PASS }
        ?.result?.output
        ?.let { osClient.getRecord(it.classname, it.hash.base64Decode(), encryptionKeyRef).get() as T }

    fun <T: Message> hasResult(
        clazz: Class<T>,
        resultName: String
    ): Boolean = findResult(clazz, resultName)
        ?.takeIf { listOf(PASS, FAIL).contains(it.result.result) } != null

    fun <T: Message> findResult(
        clazz: Class<T>,
        resultName: String,
    ): Contracts.ConsiderationProto?  = envelope.contract
        .considerationsList
        .find { it.result.output.classname == clazz.name && it.result.output.name == resultName }
}
