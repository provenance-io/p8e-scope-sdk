package io.provenance.p8e.testframework

import io.p8e.spec.P8eContract
import java.util.*

interface TestContract{
    var numParticipants: Int
    var maxFacts: Int
    val scopeUuid: UUID
    val factMap: HashMap<String, ByteArray>

//    fun execute()
//    fun waitForResult(): ContractResult
}