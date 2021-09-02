package io.provenance.p8e.testframework

import com.google.protobuf.Any
import cosmos.tx.v1beta1.TxOuterClass
import io.grpc.ManagedChannelBuilder
import io.p8e.util.toByteString
import io.provenance.metadata.v1.QueryGrpc
import io.provenance.metadata.v1.ScopeRequest
import io.provenance.scope.contract.spec.P8eContract as SdkContract
import io.provenance.metadata.v1.ScopeResponse
//import io.provenance.metadata.v1.Session
import io.provenance.p8e.shared.extension.logger
//import io.provenance.p8e.testframework.contracts.SdkSinglePartyTestScopeSpecification
import io.provenance.p8e.testframework.proto.RandomBytes
import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.sdk.*
import io.provenance.scope.util.toUuidProv
import java.util.*
import io.provenance.scope.contract.spec.P8eScopeSpecification
import sample.TransactionService
import java.util.concurrent.TimeUnit


class SdkTestContract constructor(contractBuilder: SdkTestContractBuilder): TestContract{
    var type: Class<out SdkContract> = contractBuilder.contractType
    override var numParticipants: Int = contractBuilder.numParticipants
    override var maxFacts: Int = contractBuilder.maxFacts
    override var scopeUuid: UUID = contractBuilder.scopeUuid
    override val factMap: HashMap<String, ByteArray> = contractBuilder.factMap
    val sharedClient: SharedClient = contractBuilder.sharedClient
    var ownerClient: Client = contractBuilder.ownerClient
    val scope: ScopeResponse? = contractBuilder.scope

    val log = logger()

    fun execute(): SdkContractResult{
        //Get scopeSpecUuid
        val scopeSpec = SdkContractInformationMap.getValue(type).scopeSpec

        //Make the session
        log.info("About to generate session builder...")
        val sessionBuilder = generateSessionBuilder(scopeSpec)
        log.info("Session builder generated.  Adding records...")
        //Go through the fact map and add all the records(facts)
        supplyRecords(sessionBuilder)

        log.info("About to execute contract...")
        //Call execute and store result somewhere that can be used in waitForResult()
        val result = ownerClient.execute(sessionBuilder.build())

//        log.info("result is: $result")
        log.info("result envelope is: ${(result as SignedResult).envelope}")

        //Submit to chain

        val pbChannel = ManagedChannelBuilder.forAddress("localhost", 9090)
            .usePlaintext()
            .build()
        val pbClient = QueryGrpc.newFutureStub(pbChannel)
        val transactionService = TransactionService("chain-local", pbChannel)

        var resultState: ResultState = ResultState.SUCCESS
        val indexedResult = mutableMapOf<String, kotlin.Any>()
        var scopeResponse: ScopeResponse? = null

        when (result) {
            is SignedResult -> {
                log.info("result is: ${result.messages}")
                val txBody = TxOuterClass.TxBody.newBuilder().addAllMessages(result.messages.map {
                    Any.pack(it, "")
                }).build()

                val accountInfo = transactionService.accountInfo(localKeys[0].public.getAddress(false))

                val estimate = transactionService.estimateTx(txBody, accountInfo.accountNumber, accountInfo.sequence, localKeys[0])

                val result = transactionService.batchTx(txBody, accountInfo.accountNumber, accountInfo.sequence, estimate, localKeys[0]).get()

                val hash = result.txResponse.txhash

                val done = try {
                    val txResult = transactionService.getTx(hash)
                    when (txResult.code) {
                        0 -> {
                            println("Transaction complete!")
                            //This is the ScopeResponse we want in the SdkContractResult
                            val updatedScope = pbClient.scope(
                                ScopeRequest.newBuilder()
                                .setScopeId(scopeUuid.toString())
                                .setIncludeRecords(true)
                                .setIncludeSessions(true)
                                .build()).get()
                            scopeResponse = updatedScope
                            println("scope = $updatedScope")

                            //This is the indexedResult we want in the SdkContractResult
                            val indexed = ProtoIndexer(sharedClient.osClient, false).indexFields(updatedScope, localKeys)
                            indexedResult.putAll(indexed)
                            println("indexed result = $indexed")
                        }
                        else -> {
                            resultState = ResultState.FAILED
                            println("Transaction error! ${txResult.code}")
                        }
                    }
                    true
                } catch (e: Exception) {
//                        println("Tx fetch error ${e.message}")
                    false
                }
            }
            else -> TODO("multiparty schtuff")
        }
        pbChannel.shutdown()
        //TODO: Wait until awaitTermination() is true?
        pbChannel.awaitTermination(60, TimeUnit.SECONDS)
        return SdkContractResult(resultState, indexedResult, scopeResponse)
    }

    fun generateSessionBuilder(scopeSpec: Class<out P8eScopeSpecification>): io.provenance.scope.sdk.Session.Builder{
        if(scope == null){
            log.info("No provided scope")
            return ownerClient.newSession(type, scopeSpec).setScopeUuid(scopeUuid)
        }
        else{
            log.info("Scope provided")
            scopeUuid = scope.scope.scopeIdInfo.scopeUuid.toUuidProv()    //Not needed?
            val session = scope.sessionsList.last().session
            return ownerClient.newSession(type, scope, session)
        }
    }

    fun supplyRecords(sessionBuilder: io.provenance.scope.sdk.Session.Builder){
        for((recordName, data) in factMap){
            log.info("factMap contents: $recordName : $data")
            sessionBuilder.addProposedRecord(recordName,
                RandomBytes.Data.newBuilder().setData(data.toByteString()).build())
        }
    }

}
