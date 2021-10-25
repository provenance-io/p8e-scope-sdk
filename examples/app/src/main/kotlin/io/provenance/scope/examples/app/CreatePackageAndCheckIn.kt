package io.provenance.scope.examples.app

import io.grpc.ManagedChannelBuilder
import io.provenance.scope.contract.annotations.Record
import io.provenance.scope.contract.proto.Specifications.PartyType
import io.provenance.scope.encryption.ecies.ECUtils
import io.provenance.scope.encryption.model.DirectKeyRef
import io.provenance.scope.encryption.util.toJavaPrivateKey
import io.provenance.scope.examples.ShippingExample.ShippingPackage
import io.provenance.scope.examples.ShippingExample.Checkpoint
import io.provenance.scope.examples.ShippingExample.CheckpointList
import io.provenance.scope.examples.ShippingExample.Destination
import io.provenance.scope.examples.app.utils.SingleTx
import io.provenance.scope.examples.app.utils.TransactionService
import io.provenance.scope.examples.app.utils.getScope
import io.provenance.scope.examples.app.utils.persistBatchToProvenance
import io.provenance.scope.examples.contract.ShipPackage
import io.provenance.scope.examples.contract.AddCheckin
import io.provenance.scope.examples.contract.ShippingScopeSpecification
import io.provenance.scope.sdk.Affiliate
import io.provenance.scope.sdk.Client
import io.provenance.scope.sdk.ClientConfig
import io.provenance.scope.sdk.SharedClient
import io.provenance.scope.sdk.SignedResult
import io.provenance.scope.util.toByteString
import io.provenance.scope.util.toProtoTimestamp
import java.net.URI
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

// Example of hydrating a partial scope. It is best only to hydrate the exact fields you need as each
// field maps to one gRPC call.

data class ShippingScopeData(
    @Record("package") val shippingPackage: ShippingPackage
)

fun main(args: Array<String>) {
    println("Executing and updating a shipping scope!")

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
        partyType = PartyType.ORIGINATOR,
    )
    val sdk = Client(SharedClient(config = config), affiliate)

    // A UUID used to create and update this scope. This can be thought of as the unique primary key
    // referencing the scope we will be creating below.
    val scopeUuid = UUID.randomUUID()
    try {
        val session = sdk.newSession(ShipPackage::class.java, ShippingScopeSpecification::class.java)
            .setScopeUuid(scopeUuid)
            .also {
                it.addProposedRecord(
                    "package",
                    ShippingPackage.newBuilder()
                        .setUuid(scopeUuid.toString())
                        .setDestination(Destination.newBuilder()
                            .setUuid(UUID.randomUUID().toString())
                            .setName("123 fake")
                            .setZipcode("12345-3123")
                            .build()
                        )
                        .setCheckins(
                            CheckpointList.newBuilder()
                                .addCheckpoints(Checkpoint.newBuilder()
                                    .setUuid(UUID.randomUUID().toString())
                                    .setPackageUuid(UUID.randomUUID().toString())
                                    .setFacility("Shipping Origination - IL")
                                    .setCity("Chicago")
                                    .setCountry("USA")
                                    .build())
                                .build())
                        .build())
            }.build()

        // A single party contract will always return a batch of messages that can be persisted to Provenance.
        when (val result = sdk.execute(session)) {
            is SignedResult -> persistBatchToProvenance(transactionService, SingleTx(result), affiliate.signingKeyRef)
            else -> throw IllegalStateException("Must be a signed result since this is a single party contract.")
        }

        // Fetches the latest scope from Provenance and hydrates hashes from Object Store.
        val scopeResponse = getScope(channel, scopeUuid)
        val scope = sdk.hydrate(ShippingScopeData::class.java, scopeResponse)

        println("Shipping scope after initiation = $scope")

        val sessionTwo = sdk.newSession(AddCheckin::class.java, scopeResponse)
            .addProposedRecord(
                "checkpoint",
                Checkpoint.newBuilder()
                        .setUuid(UUID.randomUUID().toString())
                        .setPackageUuid(scopeUuid.toString())
                        .setFacility("Shipping Receiving 2 - CA")
                        .setCity("Fremont")
                        .setCountry("USA")
                        .build()
            ).build()

        // A single party contract will always return a batch of messages that can be persisted to Provenance.
        when (val result = sdk.execute(sessionTwo)) {
            is SignedResult -> persistBatchToProvenance(transactionService, SingleTx(result), affiliate.signingKeyRef)
            else -> throw IllegalStateException("Must be a signed result since this is a single party contract.")
        }

        // Fetches the latest scope from Provenance and hydrates hashes from Object Store.
        val scopeResponseTwo = getScope(channel, scopeUuid)
        val scopeTwo = sdk.hydrate(ShippingScopeData::class.java, scopeResponseTwo)
        println("Checkpoint list after adding a new checkpoint = ${scopeTwo}")

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
