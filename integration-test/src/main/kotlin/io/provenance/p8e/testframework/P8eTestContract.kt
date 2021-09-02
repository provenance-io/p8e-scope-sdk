package io.provenance.p8e.testframework

import io.p8e.ContractManager
import io.p8e.exception.message
import io.p8e.proto.ContractScope
import io.p8e.proto.ContractSpecs
import io.p8e.spec.P8eContract
import io.p8e.util.toByteString
import io.p8e.util.toHex
import io.p8e.util.toUuidProv
import io.provenance.p8e.shared.extension.logger
import io.provenance.p8e.testframework.contracts.*
import io.provenance.p8e.testframework.proto.RandomBytes
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.collections.HashMap


private val OWNER_PRIVATE_KEY = System.getenv("OWNER_PRIVATE_KEY")
private val CUST_PRIVATE_KEY = System.getenv("CUST_PRIVATE_KEY")
private val OMNI_PRIVATE_KEY = System.getenv("OMNI_PRIVATE_KEY")
private val API_URL = System.getenv("API_URL")

class P8eTestContract constructor(contractBuilder: P8eTestContractBuilder): TestContract {

    //The type of contract to be executed, specified in the constructor of the Builder
    var type: Class<out P8eContract> = contractBuilder.contractType
    //Stores facts and their data
    override val factMap: HashMap<String, ByteArray> = contractBuilder.factMap
    val otherPartyMap: HashMap<String, ByteArray> = HashMap<String, ByteArray>()    //For facts executed by other party
    override var maxFacts = contractBuilder.maxFacts //Holds max number of facts in a contract
    val log = logger()

    companion object {
        val executionLatches = ConcurrentHashMap<UUID, CountDownLatch>()
        val log = logger()

        //This sets up a watcher for all contract types except Multi-Step contracts, because those need special watchers
        fun watchAll(cm: ContractManager) {
            log.info("in watch all for key = ${cm.keyPair.public.toHex()}")
            watch(cm, SinglePartyContractSmall::class.java)
            watch(cm, SinglePartyContractMedium::class.java)
            watch(cm, SinglePartyContractLarge::class.java)
            watch(cm, MultiPartyContractSmall::class.java)
            watch(cm, MultiPartyContractMedium::class.java)
            watch(cm, MultiPartyContractLarge::class.java)
        }
        //Creates a watcher for the given contract type for the given ContractManager
        fun watch(cm: ContractManager, type: Class<out P8eContract>) {
            cm.watchBuilder(type)
                .stepCompletion {
                    val latch = executionLatches.get(it.envelope.ref.groupUuid.toUuidProv())

                    if (latch != null) {
                        latch.countDown()

                        log.info("GROUP UUID: ${it.envelope.ref.groupUuid.value} remaining count downs: ${latch.count}")
                    }

                    true
                }
                .error {
                    val latch = executionLatches.get(it.event.envelope.ref.groupUuid.toUuidProv())

                    if (latch != null) {
                        latch.countDown()

                        log.info("ERROR GROUP UUID: ${it.event.envelope.ref.groupUuid.value} remaining count downs: ${latch.count}")
                    }

                    true
                }
                .executeRequests()
                .also { it.watch() }
        }

        //Universal contract managers used unless user specifies a specific CM they want, or the contract is multi-step
        var ownerCmComp: ContractManager = ContractManager.create(
            OWNER_PRIVATE_KEY,
            API_URL
        ).apply {
            watchAll(this)
        }
        var custCmComp: ContractManager = ContractManager.create(
            CUST_PRIVATE_KEY,
            API_URL
        ).apply {
            watchAll(this)
        }
        var omniCmComp: ContractManager = ContractManager.create(
            OMNI_PRIVATE_KEY,
            API_URL
        ).apply {
            watchAll(this)
        }
    }

    //TestContracts have 1 participant for single-party, 2 for multi-step, and 3 for multi-party contracts.
    override var numParticipants = contractBuilder.numParticipants    //Default 1 for single party

    var ownerCM: ContractManager? = contractBuilder.ownerCM   //If null will be set in init block
    var custCM: ContractManager? = contractBuilder.custCM      //Used for a second party
    var omniCM: ContractManager? = contractBuilder.omniCM     //Used for a third party

    init {
        //Set the contract's managers if multi-step and/or user did not provide a ContractManager in the builder.
        if(P8eContractInformationMap.getValue(type).isMultiStep){
            if(ownerCM == null) {
                ownerCM = ContractManager.create(
                    OWNER_PRIVATE_KEY,
                    API_URL
                )
            }
            if(custCM == null) {
                custCM = ContractManager.create(
                    CUST_PRIVATE_KEY,
                    API_URL
                )
            }
            if(omniCM == null) {
                omniCM = ContractManager.create(
                    OMNI_PRIVATE_KEY,
                    API_URL
                )
            }
            multiStepWatchAll(ownerCM!!, ContractSpecs.PartyType.OWNER)
            multiStepWatchAll(custCM!!, ContractSpecs.PartyType.CUSTODIAN)
            multiStepWatchAll(omniCM!!, ContractSpecs.PartyType.OMNIBUS)
        } else {
            if (contractBuilder.ownerCM == null) {
                ownerCM = ownerCmComp
            }
            if (contractBuilder.custCM == null) {
                custCM = custCmComp
            }
            if (contractBuilder.omniCM == null) {
                omniCM = omniCmComp
            }
        }
    }

    //UUID variables
    override val scopeUuid = contractBuilder.scopeUuid
    val executionUuid = UUID.randomUUID()
    val contract = ownerCM!!.newContract(
        contractClazz = type,
        scopeUuid = scopeUuid,
        executionUuid = executionUuid,
        invokerRole = ContractSpecs.PartyType.OWNER)
    val groupUuid = contract.envelope.ref.groupUuid.toUuidProv()

    //Add all the facts from the factMap into an executable contract and execute it
    fun execute(){
        //Satisify Participants for multi-party contracts if the contract is multi-party
        if(numParticipants > 1){    //Not single-party
            contract.satisfyParticipant(ContractSpecs.PartyType.CUSTODIAN, custCM!!.keyPair.public)
            if(!P8eContractInformationMap.getValue(type).isMultiStep){  //multi-party not multi-step
                contract.satisfyParticipant(ContractSpecs.PartyType.OMNIBUS, omniCM!!.keyPair.public)
            }
        }

        //Add all facts in factMap to an actual contract that can be executed if they are invokedBy OWNER
        //  Facts are invokedBy OWNER if the number in the name of the fact is in the first half of maxFacts
        for((key, value) in factMap){
            if(!P8eContractInformationMap.getValue(type).isMultiStep ||
                key.substring(4).toInt() <= maxFacts/2) {
                val dataProto = RandomBytes.Data.newBuilder().setData(value.toByteString()).build()
                contract.addProposedFact(key, dataProto)
            } else {
                otherPartyMap[key] = value
            }
        }

        //Execute the contract
        contract.execute().map {
            log.info("Accepted with scope $scopeUuid and execution $executionUuid")

            //The latch needs the number of participants
            executionLatches.put(groupUuid, CountDownLatch(numParticipants))
        }.mapLeft {
            log.error("Error with envelope ${it::class.java.name} ${it.message()}")
        }
    }

    //returns a ContractResult to get useful information after execution
    /*override*/ fun waitForResult(): ContractResult{
        val latch = executionLatches.getValue(this.groupUuid)

        // wait for contract execution to complete(60 seconds)
        val success = latch.await(240, TimeUnit.SECONDS)
        // allow ACK to close envelope
        Thread.sleep(2_000)

        val envelope = ownerCM!!.client.getContract(this.executionUuid)
        val result = if (success) {
            when (envelope.status) {
                ContractScope.Envelope.Status.COMPLETE -> ResultState.SUCCESS
                else -> ResultState.FAILED
            }
        } else {
            ResultState.FAILED
        }

        return ContractResult(
            result = result,
            envelope = envelope,
            scope = ownerCM!!.indexClient.findLatestScopeByUuid(this.scopeUuid)?.scope,
        )
    }

    //These two functions set up watchers for multi-step contracts since the companion CMs' watchers aren't configured for it
    fun multiStepWatchAll(cm: ContractManager, party: ContractSpecs.PartyType){
        multiStepWatch(cm, MultiPartyMultiStepContractSmall::class.java, party)
        multiStepWatch(cm, MultiPartyMultiStepContractMedium::class.java, party)
        multiStepWatch(cm, MultiPartyMultiStepContractLarge::class.java, party)
    }

    fun multiStepWatch(cm: ContractManager, type: Class<out P8eContract>, party: ContractSpecs.PartyType){
        cm.watchBuilder(type)
            .stepCompletion {
                val latch = executionLatches.get(it.envelope.ref.groupUuid.toUuidProv())

                if (latch != null) {
                    latch.countDown()

                    if(it.isCompleted()){
                        Companion.log.debug("in step completed")
                    } else {
                        Companion.log.debug("in step not completed")

                        if(party != ContractSpecs.PartyType.OWNER){
                            val contract = cm.loadContract(type, UUID.fromString(it.envelope.executionUuid.value))
                            for((key, value) in otherPartyMap) {
                                val dataProto = RandomBytes.Data.newBuilder().setData(value.toByteString()).build()
                                contract.addProposedFact(key, dataProto)
                            }
                            contract.execute()
                        }
                    }

                    Companion.log.info("GROUP UUID: ${it.envelope.ref.groupUuid.value} remaining count downs: ${latch.count}")
                }

                true
            }
            .error {
                val latch = executionLatches.get(it.event.envelope.ref.groupUuid.toUuidProv())

                if (latch != null) {
                    latch.countDown()

                    Companion.log.info("ERROR GROUP UUID: ${it.event.envelope.ref.groupUuid.value} remaining count downs: ${latch.count}")
                }

                true
            }
            .executeRequests()  //Needed for multi-party contracts, does not harm single-party
            .also { it.watch() }
    }
}