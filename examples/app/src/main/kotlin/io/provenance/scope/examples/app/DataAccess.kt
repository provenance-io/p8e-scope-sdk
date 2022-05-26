package io.provenance.scope.examples.app

import io.grpc.ManagedChannelBuilder
import io.provenance.scope.contract.annotations.Record
import io.provenance.scope.contract.proto.Specifications.PartyType
import io.provenance.scope.encryption.ecies.ECUtils
import io.provenance.scope.encryption.model.DirectKeyRef
import io.provenance.scope.encryption.util.toJavaPrivateKey
import io.provenance.scope.examples.SimpleExample.ExampleName
import io.provenance.scope.examples.app.utils.SingleTx
import io.provenance.scope.examples.app.utils.TransactionService
import io.provenance.scope.examples.app.utils.getScope
import io.provenance.scope.examples.app.utils.persistBatchToProvenance
import io.provenance.scope.examples.contract.SimpleExampleContract
import io.provenance.scope.examples.contract.SimpleExampleScopeSpecification
import io.provenance.scope.sdk.Affiliate
import io.provenance.scope.sdk.Client
import io.provenance.scope.sdk.ClientConfig
import io.provenance.scope.sdk.SharedClient
import io.provenance.scope.sdk.SignedResult
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ExampleNameDataAccess(
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

    // Affiliate and Client are light weight representations and creating a lot of them in a multi-tenant environment
    // is fine. Here we are bringing up a second Affiliate so that we can hydrate the scope later in the example.
    val encryptionPrivateKey2 = System.getenv("PRIVATE_KEY_2").toJavaPrivateKey()
    val signingPrivateKey2 = System.getenv("PRIVATE_KEY_2").toJavaPrivateKey()
    val affiliate2 = Affiliate(
        encryptionKeyRef = DirectKeyRef(ECUtils.toPublicKey(encryptionPrivateKey2)!!, encryptionPrivateKey2),
        signingKeyRef = DirectKeyRef(ECUtils.toPublicKey(signingPrivateKey2)!!, signingPrivateKey2),
        partyType = PartyType.OWNER,
    )
    val sdk2 = Client(SharedClient(config = config), affiliate2)

    // A UUID used to create and update this scope. This can be thought of as the unique primary key
    // referencing the scope we will be creating below.
    val scopeUuid = UUID.randomUUID()

    try {
        // Creates and executes a new session on a new scope that contains a single record.
        val session = sdk.newSession(SimpleExampleContract::class.java, SimpleExampleScopeSpecification::class.java)
            .setScopeUuid(scopeUuid)
            // In this example we happen to have the whole affiliate, but all that is needed here is the PublicKey
            // and in a real setting it's more than likely that you'd only have the PublicKey itself.
            .addDataAccessKey(affiliate2.encryptionKeyRef.publicKey)
            .addProposedRecord(
                "name",
                ExampleName.newBuilder()
                    .setFirstName("Cosmo")
                    .setLastName("Kramer")
                    .build()
            )
            .build()

        // A single party contract will always return a batch of messages that can be persisted to Provenance.
        when (val result = sdk.execute(session)) {
            is SignedResult -> persistBatchToProvenance(transactionService, SingleTx(result), affiliate.signingKeyRef)
            else -> throw IllegalStateException("Must be a signed result since this is a single party contract.")
        }

        // Fetches the latest scope from Provenance and hydrates hashes from Object Store. In this case,
        // we can hydrate the data based on "sdk2" which is the affiliate that was added to the scope as
        // a data access member.
        val scopeResponse = getScope(channel, scopeUuid)
        val scope = sdk2.hydrate(ExampleNameDataAccess::class.java, scopeResponse)
        println("ExampleName record data hydrated as the data access (read only) member = $scope")
    } catch (e: Exception) {
        println(e.printStackTrace())
    } finally {
        // Cleanly shuts down both the sdk client and the Provenance grpc channel.
        channel.shutdown()
        if (!channel.awaitTermination(10, TimeUnit.SECONDS)) {
            println("Could not shutdown Provenance managed channel cleanly!")
        }

        sdk.close()
        if (!sdk.awaitTermination(10, TimeUnit.SECONDS)) {
            println("Could not shutdown sdk managed channel cleanly!")
        }
    }
}
