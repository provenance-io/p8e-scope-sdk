package io.provenance.scope.examples.app

import io.grpc.ManagedChannelBuilder
import io.provenance.scope.contract.annotations.Record
import io.provenance.scope.contract.proto.Specifications.PartyType
import io.provenance.scope.encryption.ecies.ECUtils
import io.provenance.scope.encryption.model.DirectKeyRef
import io.provenance.scope.encryption.util.toJavaPrivateKey
import io.provenance.scope.examples.SimpleExample.ExampleName
import io.provenance.scope.examples.contract.SimpleExampeContract
import io.provenance.scope.examples.contract.SimpleExampleScopeSpecification
import io.provenance.scope.sdk.Affiliate
import io.provenance.scope.sdk.Client
import io.provenance.scope.sdk.ClientConfig
import io.provenance.scope.sdk.SharedClient
import io.provenance.scope.sdk.SignedResult
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ExampleNameHydrate(
    @Record("name") val name: ExampleName,
)

fun main(args: Array<String>) {
    println("Executing simple contract example!")

    // Creates Provenance grpc client. This is used for fetching Provenance account information, as well as
    // simulating and broadcasting TXs.
    val provenanceUri = URI(System.getenv("PROVENANCE_GRPC_URL"))
    val channel = ManagedChannelBuilder
        .forAddress(provenanceUri.host, provenanceUri.port)
        .also {
            if (provenanceUri.scheme.endsWith("s")) {
                it.useTransportSecurity()
            } else {
                it.usePlaintext()
            }
        }
        .usePlaintext()
        .build()
    val transactionService = TransactionService(System.getenv("CHAIN_ID"), channel)

    // Creates a P8e scope client that will be used for contract execution.
    val encryptionPrivateKey = System.getenv("ENCRYPTION_PRIVATE_KEY").toJavaPrivateKey()
    val signingPrivateKey = System.getenv("SIGNING_PRIVATE_KEY").toJavaPrivateKey()
    val config = ClientConfig(
        cacheJarSizeInBytes = 0L,
        cacheSpecSizeInBytes = 0L,
        cacheRecordSizeInBytes = 0L,

        osGrpcUrl = URI(System.getenv("OS_GRPC_URL")),
        osGrpcDeadlineMs = 30 * 1_000L,

        mainNet = false,
    )
    val affiliate = Affiliate(
        encryptionKeyRef = DirectKeyRef(ECUtils.toPublicKey(encryptionPrivateKey)!!, encryptionPrivateKey),
        signingKeyRef = DirectKeyRef(ECUtils.toPublicKey(signingPrivateKey)!!, signingPrivateKey),
        partyType = PartyType.OWNER,
    )
    val sdk = Client(SharedClient(config = config), affiliate)

    // A UUID used to create and update this scope. This can be thought of as the unique primary key
    // referencing the scope we will be creating below.
    val scopeUuid = UUID.randomUUID()

    try {
        // Creates and executes a new session on a new scope that contains a single record.
        val session = sdk.newSession(SimpleExampeContract::class.java, SimpleExampleScopeSpecification::class.java)
            .setScopeUuid(scopeUuid)
            .addProposedRecord(
                "name",
                ExampleName.newBuilder()
                    .setFirstName("Jerry")
                    .setLastName("Seinfeld")
                    .build()
            )
            .build()

        // A single party contract will always return a batch of messages that can be persisted to Provenance.
        when (val result = sdk.execute(session)) {
            is SignedResult -> persistBatchToProvenance(transactionService, result, affiliate.signingKeyRef as DirectKeyRef)
            else -> throw IllegalStateException("Must be a signed result since this is a single party contract.")
        }

        // Fetches the latest scope from Provenance and hydrates hashes from Object Store.
        val scopeResponse = getScope(channel, scopeUuid)
        val scope = sdk.hydrate(ExampleNameHydrate::class.java, scopeResponse)
        println("ExampleName record data = $scope")
    } catch (e: Exception) {
        println(e.printStackTrace())
    } finally {
        // Cleanly shuts down both the sdk client and the Provenance grpc channel.
        channel.shutdown()
        if (!channel.awaitTermination(10, TimeUnit.SECONDS)) {
            println("Could not shutdown Provenance managed channel cleanly!")
        }

        sdk.inner.close()
        if (!sdk.inner.awaitTermination(10, TimeUnit.SECONDS)) {
            println("Could not shutdown sdk managed channel cleanly!")
        }
    }
}
