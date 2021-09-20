package io.provenance.scope.examples.app

import io.grpc.ManagedChannelBuilder
import io.provenance.scope.contract.proto.Specifications.PartyType
import io.provenance.scope.encryption.ecies.ECUtils
import io.provenance.scope.encryption.model.DirectKeyRef
import io.provenance.scope.encryption.util.toJavaPrivateKey
import io.provenance.scope.examples.SimpleExample.ExampleName
import io.provenance.scope.examples.app.utils.BatchTx
import io.provenance.scope.examples.app.utils.TransactionService
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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

fun createRandomExampleContract(sdk: Client): SignedResult {
    val scopeUuid = UUID.randomUUID()
    val session = sdk.newSession(SimpleExampleContract::class.java, SimpleExampleScopeSpecification::class.java)
        .setScopeUuid(scopeUuid)
        .addProposedRecord("name", ExampleName.newBuilder().setFirstName(scopeUuid.toString()).build())
        .build()

    return when (val result = sdk.execute(session)) {
        is SignedResult -> result
        else -> throw IllegalStateException("Must be a signed result since this is a single party contract.")
    }
}

// This example demonstrates how a background thread can be used to batch records to persist to Provenance. This
// solution results in an increase in throughput and is a good strategy to use when higher throughput is required.
fun main(args: Array<String>) {
    println("Executing batch contract example!")

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
    val queue = LinkedBlockingQueue<SignedResult>(10)

    thread(start = true, isDaemon = true) {
        val batch = mutableListOf<SignedResult>()

        while (true) {
            while (batch.size < 10) {
                batch.add(queue.take())
            }

            println("Sending batch of 10 scopes to Provenance!")
            persistBatchToProvenance(transactionService, BatchTx(batch), affiliate.signingKeyRef)
            println("Batch completed!")
            batch.clear()
        }
    }

    try {
        repeat(70) {
            queue.put(createRandomExampleContract(sdk))
            println("Queued a contract!")
        }

        println("Completed queuing all contracts!")
        var timeoutSecs = 0
        val timeout = 20
        while (queue.isNotEmpty() && timeoutSecs < timeout) {
            Thread.sleep(1_000)
            timeoutSecs += 1
        }

        if (timeoutSecs >= timeout) {
            println("Could not finish last batch before timeout!")
        }
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
