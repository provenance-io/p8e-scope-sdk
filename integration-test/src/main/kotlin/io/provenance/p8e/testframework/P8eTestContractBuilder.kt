package io.provenance.p8e.testframework

import io.p8e.ContractManager
import io.p8e.spec.P8eContract
import io.provenance.p8e.testframework.contracts.SinglePartyContractLarge
import io.provenance.scope.sdk.Client
import java.util.*
import kotlin.collections.HashMap


//Builder that constructs contracts
//NOTE: If the user wants to use a specific contract manager, they need their own watcher currently
class P8eTestContractBuilder(val contractType: Class<out P8eContract> = SinglePartyContractLarge::class.java): TestContractBuilder{
    //TODO: It seems kinda weird to have null CMs while the Sdk one has defaults
    //Contract takes care of contract managers unless user wants to specify a specific one
    //These pretty much have to be null because we want the Contract Managers to be shared across all the contracts
    var ownerCM: ContractManager? = null
    var custCM: ContractManager? = null
    var omniCM: ContractManager? = null

    val contractInfo = P8eContractInformationMap.getValue(contractType)
    override val factMap: HashMap<String, ByteArray> = HashMap<String, ByteArray>()
    override var scopeUuid = UUID.randomUUID()
    override var maxFacts = contractInfo.maxFacts
    override var numParticipants = contractInfo.numParticipants

    //Adjusts the value of numParticipants if the contract is multi-step(needs to be double the number of parties
    init{
        if(contractInfo.isMultiStep){
            numParticipants *= 2
        }
    }

    fun addProposedFact(name: String, numBytes: Int): P8eTestContractBuilder{
        if(name.substring(4).toInt() > maxFacts){
            throw IllegalArgumentException("Fact number too large for given ContractType")
        }
        factMap[name] = generateRandomBytes(numBytes)
        return this
    }

    //The following functions allow the user to modify different variable but return a builder to chain together calls.
    fun setScopeUuid(uuid: UUID): P8eTestContractBuilder{
        scopeUuid = uuid
        return this
    }

    fun setOwnerContractManager(contractManager: ContractManager): P8eTestContractBuilder{
        ownerCM = contractManager
        return this
    }

    fun setCustContractManager(contractManager: ContractManager): P8eTestContractBuilder{
        custCM = contractManager
        return this
    }

    fun setOmniContractManager(contractManager: ContractManager): P8eTestContractBuilder{
        omniCM = contractManager
        return this
    }

    //Returns an P8eTestContract
    fun build(): P8eTestContract{
        return P8eTestContract(this)
    }
}
