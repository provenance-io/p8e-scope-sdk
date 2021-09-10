package io.provenance.scope.sdk.testframework

import io.provenance.metadata.v1.ScopeResponse
import io.provenance.scope.sdk.testframework.contracts.SdkSinglePartyContractLarge
import io.provenance.scope.contract.proto.Specifications
import io.provenance.scope.contract.spec.P8eContract as SdkContract
import io.provenance.scope.encryption.model.DirectKeyRef
import io.provenance.scope.sdk.Affiliate
import io.provenance.scope.sdk.Client
import io.provenance.scope.sdk.ClientConfig
import io.provenance.scope.sdk.SharedClient
import java.net.URI
import java.util.*
import kotlin.collections.HashMap

//TODO: Change name of addProposedFact to addProposedRecord(and factMap too) since facts are now called records

class SdkTestContractBuilder(val contractType: Class<out SdkContract> = SdkSinglePartyContractLarge::class.java) {
    val recordMap: HashMap<String, ByteArray> = HashMap<String, ByteArray>()
    var scopeUuid: UUID = UUID.randomUUID()
    var maxRecords: Int = SdkContractInformationMap.getValue(contractType).maxRecords
    var numParticipants: Int = SdkContractInformationMap.getValue(contractType).numParticipants
    var scope: ScopeResponse? = null

    val sharedClient = SharedClient(
        ClientConfig(
            10_000_000,
            10_000_000,
            10_000_000,
            URI.create("grpc://localhost:5000"),
            5_000,
            mainNet = false)
    )

    var ownerClient: Client = Client(sharedClient,
        Affiliate(
            DirectKeyRef(localKeys[0].public, localKeys[0].private),    //Made from local, private keypair from localKeys list
            DirectKeyRef(localKeys[0].public, localKeys[0].private),
            Specifications.PartyType.OWNER
        )
    )
    //TODO: Once multi-party stuff is added to the sdk, will need clients for CUSTODIAN and OMNIBUS parties

    fun setOwnerClient(client: Client): SdkTestContractBuilder{
        ownerClient = client
        return this
    }

    fun setScopeUuid(uuid: UUID): SdkTestContractBuilder{
        scopeUuid = uuid
        return this
    }

    fun setScope(inputScope: ScopeResponse): SdkTestContractBuilder{
        scope = inputScope
        return this
    }

    fun addProposedRecord(name: String, numBytes: Int): SdkTestContractBuilder{
        if(name.substring(4).toInt() > maxRecords){
            throw IllegalArgumentException("Record number too large for given ContractType")
        }
        recordMap[name] = generateRandomBytes(numBytes)
        return this
    }

    fun build(): SdkTestContract{
        return SdkTestContract(this)
    }
}
