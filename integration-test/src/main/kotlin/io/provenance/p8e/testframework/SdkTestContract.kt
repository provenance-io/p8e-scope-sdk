package io.provenance.scope.sdk.testframework

import com.google.protobuf.Any
import cosmos.tx.v1beta1.TxOuterClass
import io.grpc.ManagedChannelBuilder
//import io.p8e.util.toByteString
import io.provenance.metadata.v1.QueryGrpc
import io.provenance.metadata.v1.ScopeRequest
import io.provenance.scope.contract.spec.P8eContract as SdkContract
import io.provenance.metadata.v1.ScopeResponse
//import io.provenance.p8e.shared.extension.logger
import io.provenance.scope.sdk.testframework.proto.RandomBytes
import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.sdk.*
import io.provenance.scope.util.toUuid
import java.security.PublicKey
import java.util.*
import io.provenance.scope.contract.spec.P8eScopeSpecification
import sample.TransactionService
import java.util.concurrent.TimeUnit


class SdkTestContract constructor(contractBuilder: SdkTestContractBuilder){
    var type: Class<out SdkContract> = contractBuilder.contractType
    var numParticipants: Int = contractBuilder.numParticipants
    var maxRecords: Int = contractBuilder.maxRecords
    var scopeUuid: UUID = contractBuilder.scopeUuid
    val recordMap: HashMap<String, ByteArray> = contractBuilder.recordMap
    val sharedClient: SharedClient = contractBuilder.sharedClient
    var ownerClient: Client = contractBuilder.ownerClient
    val scope: ScopeResponse? = contractBuilder.scope
    var dataAccessKeys: MutableList<PublicKey> = contractBuilder.dataAccessKeys
//    val log = logger()

    companion object{
        val pbChannel = ManagedChannelBuilder.forAddress("localhost", 9090)
            .usePlaintext()
            .build()
    }

    fun execute(): SdkContractResult{
        //Get scopeSpecUuid
        val scopeSpec = SdkContractInformationMap.getValue(type).scopeSpec


        //Make the session and add all data access keys
        val sessionBuilder = generateSessionBuilder(scopeSpec)
        sessionBuilder.addDataAccessKeys(dataAccessKeys)

        //Go through the record map and add all the records(records)
        supplyRecords(sessionBuilder)

        //Call execute and store result somewhere that can be used in waitForResult()
        val result = ownerClient.execute(sessionBuilder.build())

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
                            val indexed = ownerClient.indexer.indexFields(updatedScope)
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
                    false
                }
            }
            else -> TODO("multiparty schtuff")
        }
        //TODO: Figure out how to properly shut down the channel so tests aren't flooded
//        pbChannel.shutdown()
//        pbChannel.awaitTermination(240, TimeUnit.SECONDS)
//        ownerClient.inner.osClient.osClient.close()
        return SdkContractResult(resultState, indexedResult, scopeResponse)
    }

    fun generateSessionBuilder(scopeSpec: Class<out P8eScopeSpecification>): Session.Builder{
        if(scope == null){
            return ownerClient.newSession(type, scopeSpec).setScopeUuid(scopeUuid)
        }
        else{
            scopeUuid = scope.scope.scopeIdInfo.scopeUuid.toUuid()
            return ownerClient.newSession(type, scope)
        }
    }

    fun supplyRecords(sessionBuilder: Session.Builder){
        for((recordName, data) in recordMap){
            sessionBuilder.addProposedRecord(recordName,
                RandomBytes.Data.newBuilder().setData(data.toByteString()).build())
        }
    }

}