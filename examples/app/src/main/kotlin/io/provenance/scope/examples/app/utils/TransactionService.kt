package io.provenance.scope.examples.app.utils

import com.google.protobuf.Any
import cosmos.auth.v1beta1.Auth
import cosmos.auth.v1beta1.QueryOuterClass
import cosmos.base.v1beta1.CoinOuterClass
import cosmos.crypto.secp256k1.Keys
import cosmos.tx.signing.v1beta1.Signing
import cosmos.tx.v1beta1.ServiceOuterClass
import cosmos.tx.v1beta1.TxOuterClass
import io.grpc.ManagedChannel
import cosmos.auth.v1beta1.QueryGrpc
import cosmos.base.abci.v1beta1.Abci
import io.provenance.scope.encryption.crypto.SignerImpl
import io.provenance.scope.util.toByteString
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import java.security.KeyPair
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

private fun Double.roundUp(): Long = ceil(this).toLong()

data class GasEstimate(val estimate: Long, val feeAdjustment: Double? = DEFAULT_FEE_ADJUSTMENT) {
    companion object {
        private const val DEFAULT_FEE_ADJUSTMENT = 1.25
        private const val DEFAULT_GAS_PRICE = 1905.00
    }
    private val adjustment = feeAdjustment ?: DEFAULT_FEE_ADJUSTMENT

    val limit = (estimate * adjustment).roundUp()
    val fees = (limit * DEFAULT_GAS_PRICE).roundUp()
}

class TransactionService(
    private val chainId: String,
    private val channel: ManagedChannel,
) {

    private val accountService = QueryGrpc.newFutureStub(channel)
    private val txService = cosmos.tx.v1beta1.ServiceGrpc.newFutureStub(channel)

    fun accountInfo(bech32Address: String): Auth.BaseAccount = accountService
        .withDeadlineAfter(10, TimeUnit.SECONDS)
        .account(QueryOuterClass.QueryAccountRequest.newBuilder()
            .setAddress(bech32Address)
            .build()
        ).get().run { account.unpack(Auth.BaseAccount::class.java) }

    fun getTx(hash: String): Abci.TxResponse = txService
        .withDeadlineAfter(10, TimeUnit.SECONDS)
        .getTx(ServiceOuterClass.GetTxRequest.newBuilder().setHash(hash).build())
        .get()
        .txResponse

    fun signTx(body: TxOuterClass.TxBody, accountNumber: Long, sequenceNumber: Long, gasEstimate: GasEstimate = GasEstimate(0), signerImpl: SignerImpl): TxOuterClass.Tx {
        val signer = PbSigner.signerFor(signerImpl)
        val authInfo = TxOuterClass.AuthInfo.newBuilder()
            .setFee(
                TxOuterClass.Fee.newBuilder()
                    .addAllAmount(listOf(
                        CoinOuterClass.Coin.newBuilder()
                            .setDenom("nhash")
                            .setAmount((gasEstimate.fees).toString())
                            .build()
                    )).setGasLimit((gasEstimate.limit))
            )
            .addAllSignerInfos(listOf(
                TxOuterClass.SignerInfo.newBuilder()
                    .setPublicKey(
                        Keys.PubKey.newBuilder()
                            .setKey((signerImpl.getPublicKey() as BCECPublicKey).q.getEncoded(true).toByteString())
                            .build()
                            .let { Any.pack(it, "") }
                    )
                    .setModeInfo(
                        TxOuterClass.ModeInfo.newBuilder().setSingle(
                            TxOuterClass.ModeInfo.Single.newBuilder()
                                .setModeValue(Signing.SignMode.SIGN_MODE_DIRECT_VALUE)
                        ))
                    .setSequence(sequenceNumber)
                    .build()
            )).build()

        val signatures = TxOuterClass.SignDoc.newBuilder()
            .setBodyBytes(body.toByteString())
            .setAuthInfoBytes(authInfo.toByteString())
            .setChainId(chainId)
            .setAccountNumber(accountNumber)
            .build()
            .toByteArray()
            .let { signer(it) }
            .map { it.signature.toByteString() }

        return TxOuterClass.Tx.newBuilder()
            .setBody(body)
            .setAuthInfo(authInfo)
            .addAllSignatures(signatures)
            .build()
    }

    fun estimateTx(body: TxOuterClass.TxBody, accountNumber: Long, sequenceNumber: Long, signerImpl: SignerImpl): GasEstimate =
        signTx(body, accountNumber, sequenceNumber, signerImpl = signerImpl).let {
            txService.withDeadlineAfter(10, TimeUnit.SECONDS)
                .simulate(ServiceOuterClass.SimulateRequest.newBuilder()
                    .setTx(it)
                    .build()
                ).get()
        }.let { GasEstimate(it.gasInfo.gasUsed) }

    fun batchTxBlock(body: TxOuterClass.TxBody, accountNumber: Long, sequenceNumber: Long, estimate: GasEstimate, signerImpl: SignerImpl): ServiceOuterClass.BroadcastTxResponse =
        signTx(body, accountNumber, sequenceNumber, estimate, signerImpl).run {
            TxOuterClass.TxRaw.newBuilder()
                .setBodyBytes(body.toByteString())
                .setAuthInfoBytes(authInfo.toByteString())
                .addAllSignatures(signaturesList)
                .build()
        }.let {
            txService.withDeadlineAfter(20, TimeUnit.SECONDS)
                .broadcastTx(ServiceOuterClass.BroadcastTxRequest.newBuilder()
                    .setTxBytes(it.toByteString())
                    .setMode(ServiceOuterClass.BroadcastMode.BROADCAST_MODE_BLOCK)
                    .build()
                ).get()
        }
}
