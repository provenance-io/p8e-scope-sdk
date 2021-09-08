package io.provenance.scope.examples.app

import io.grpc.ManagedChannelBuilder
import io.provenance.scope.contract.annotations.Record
import io.provenance.scope.contract.proto.Specifications.PartyType
import io.provenance.scope.encryption.ecies.ECUtils
import io.provenance.scope.encryption.model.DirectKeyRef
import io.provenance.scope.encryption.util.toJavaPrivateKey
import io.provenance.scope.examples.LoanExample.CreditReport
import io.provenance.scope.examples.LoanExample.Document
import io.provenance.scope.examples.LoanExample.DocumentList
import io.provenance.scope.examples.LoanExample.Income
import io.provenance.scope.examples.LoanExample.Lien
import io.provenance.scope.examples.LoanExample.Loan
import io.provenance.scope.examples.LoanExample.Servicing
import io.provenance.scope.examples.LoanExample.UnderwritingPacket
import io.provenance.scope.examples.contract.AddLoanDocument
import io.provenance.scope.examples.contract.AddLoanServicing
import io.provenance.scope.examples.contract.LoanOnboard
import io.provenance.scope.examples.contract.LoanScopeSpecification
import io.provenance.scope.sdk.Affiliate
import io.provenance.scope.sdk.Client
import io.provenance.scope.sdk.ClientConfig
import io.provenance.scope.sdk.SharedClient
import io.provenance.scope.sdk.SignedResult
import io.provenance.scope.util.toByteString
import io.provenance.scope.util.toProtoTimestamp
import java.net.URI
import java.security.KeyPair
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

// Example of hydrating a partial scope. It is best only to hydrate the exact fields you need as each
// field maps to one gRPC call.
data class LoanServicingData(
    @Record("servicing") val servicing: Servicing,
)

data class LoanScopeData(
    @Record("credit_report") val creditReport: CreditReport,
    @Record("documents") val documents: DocumentList,
    @Record("income") val icome: Income,
    @Record("loan") val loan: Loan,
    @Record("underwriting_packet") val underwritingPacket: UnderwritingPacket,
    @Record("lien") val lien: Lien,
)

fun main(args: Array<String>) {
    println("Executing and updating a loan scope!")

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
        partyType = PartyType.ORIGINATOR,
    )
    val sdk = Client(SharedClient(config = config), affiliate)

    // A UUID used to create and update this scope. This can be thought of as the unique primary key
    // referencing the scope we will be creating below.
    val scopeUuid = UUID.randomUUID()

    try {
        val session = sdk.newSession(LoanOnboard::class.java, LoanScopeSpecification::class.java)
            .setScopeUuid(scopeUuid)
            .also {
                it.addProposedRecord(
                    "credit_report",
                    CreditReport.newBuilder()
                        .setUuid(UUID.randomUUID().toString())
                        .setPartyUuid(UUID.randomUUID().toString())
                        .setPullType("SOFT")
                        .setInquiry(OffsetDateTime.now().toProtoTimestamp())
                        .setExpiration(OffsetDateTime.now().toProtoTimestamp())
                        .build()
                )
                it.addProposedRecord(
                    "documents",
                    DocumentList.newBuilder()
                        .addDocuments(Document.newBuilder()
                            .setUuid(UUID.randomUUID().toString())
                            .setName("disclosure.pdf")
                            .setLocation("/assets/docs/$scopeUuid/${UUID.randomUUID()}")
                            .setRawDoc(ByteArray(100).toByteString())
                            .setChecksum("0")
                            .build()
                        )
                        .addDocuments(Document.newBuilder()
                            .setUuid(UUID.randomUUID().toString())
                            .setName("inspection_waiver.pdf")
                            .setLocation("/assets/docs/$scopeUuid/${UUID.randomUUID()}")
                            .setRawDoc(ByteArray(300).toByteString())
                            .setChecksum("0")
                            .build()
                        )
                        .build()
                )
                it.addProposedRecord(
                    "income",
                    Income.newBuilder()
                        .setUuid(UUID.randomUUID().toString())
                        .addPlaidItemUuids(UUID.randomUUID().toString())
                        .setVendor("chase")
                        .setTransactionHistoryLength(36)
                        .build()
                )
                it.addProposedRecord(
                    "loan",
                    Loan.newBuilder()
                        .setUuid(scopeUuid.toString())
                        .setOriginatorUuid(UUID.randomUUID().toString())
                        .setLoanNumber("1")
                        .setOriginatorName("FIGURE")
                        .setAmount("300000.00")
                        .setTermInMonths(360)
                        .setInterestRate("0.03")
                        .setOriginationFee("1500.00")
                        .setPrimaryResidence(true)
                        .build()
                )
                it.addProposedRecord(
                    "underwriting_packet",
                    UnderwritingPacket.newBuilder()
                        .setUuid(UUID.randomUUID().toString())
                        .setProductUuid(UUID.randomUUID().toString())
                        .setOriginatorUuid(UUID.randomUUID().toString())
                        .setVersion(1)
                        .setCreditScore(725)
                        .setFicoScore(732)
                        .setOriginationState("NY")
                        .setCustomerAge("40")
                        .setYearsOfExperience(20)
                        .setJudgements(2)
                        .setTaxLiens(1)
                        .setBankruptcy(0)
                        .build()
                )
                it.addProposedRecord(
                    "lien",
                    Lien.newBuilder()
                        .setLienPosition(1)
                        .setOriginalBalance("300000.00")
                        .setCurrentBalance("225000.00")
                        .setLender("FIGURE")
                        .setMonthlyPayment("1504.26")
                        .setTerm(360)
                        .setIntRate("0.035")
                        .setPrimaryLien(true)
                        .build()
                )
            }.build()

        // A single party contract will always return a batch of messages that can be persisted to Provenance.
        when (val result = sdk.execute(session)) {
            is SignedResult -> persistBatchToProvenance(transactionService, result, affiliate.signingKeyRef as DirectKeyRef)
            else -> throw IllegalStateException("Must be a signed result since this is a single party contract.")
        }

        // Fetches the latest scope from Provenance and hydrates hashes from Object Store.
        val scopeResponse = getScope(channel, scopeUuid)
        val scope = sdk.hydrate(LoanScopeData::class.java, scopeResponse)
        println("Loan scope after onboard = $scope")

        val sessionTwo = sdk.newSession(AddLoanDocument::class.java, scopeResponse)
            .addProposedRecord(
                "documents",
                DocumentList.newBuilder()
                    .addDocuments(Document.newBuilder()
                        .setUuid(UUID.randomUUID().toString())
                        .setName("updated_disclosure.pdf")
                        .setLocation("/assets/docs/$scopeUuid/${UUID.randomUUID()}")
                        .setRawDoc(ByteArray(150).toByteString())
                        .setChecksum("0")
                        .build()
                    )
                    .build()
            ).build()

        // A single party contract will always return a batch of messages that can be persisted to Provenance.
        when (val result = sdk.execute(sessionTwo)) {
            is SignedResult -> persistBatchToProvenance(transactionService, result, affiliate.signingKeyRef as DirectKeyRef)
            else -> throw IllegalStateException("Must be a signed result since this is a single party contract.")
        }

        // Fetches the latest scope from Provenance and hydrates hashes from Object Store.
        val scopeResponseTwo = getScope(channel, scopeUuid)
        val scopeTwo = sdk.hydrate(LoanScopeData::class.java, scopeResponseTwo)
        println("Document list after adding a new document = ${scopeTwo.documents}")

        val sessionThree = sdk.newSession(AddLoanServicing::class.java, scopeResponseTwo)
            .addProposedRecord(
                "servicing",
                Servicing.newBuilder()
                    .setUuid(UUID.randomUUID().toString())
                    .setServicerName("FIGURE")
                    .setServiceOwnLoans(true)
                    .addDocuments(Document.newBuilder()
                        .setUuid(UUID.randomUUID().toString())
                        .setName("servicing_agreement.pdf")
                        .setLocation("/assets/docs/$scopeUuid/${UUID.randomUUID()}")
                        .setRawDoc(ByteArray(50).toByteString())
                        .setChecksum("0")
                        .build()
                    )
                    .build()
            ).build()

        // A single party contract will always return a batch of messages that can be persisted to Provenance.
        when (val result = sdk.execute(sessionThree)) {
            is SignedResult -> persistBatchToProvenance(transactionService, result, affiliate.signingKeyRef as DirectKeyRef)
            else -> throw IllegalStateException("Must be a signed result since this is a single party contract.")
        }

        // Fetches the latest scope from Provenance and hydrates hashes from Object Store.
        val scopeResponseThree = getScope(channel, scopeUuid)
        val scopeThree = sdk.hydrate(LoanServicingData::class.java, scopeResponseThree)
        println("New Servicing record = ${scopeThree.servicing}")

        // The proto indexer provides a way to filter the full scope down to a JSON representation
        // that can be stored efficiently in a downstream system for lookups.
        val result = sdk.indexer.indexFields(scopeResponseThree)
        println("Proto indexer result = $result")
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
