package io.provenance.p8e.testframework

import java.util.*
import kotlin.collections.HashMap

interface TestContractBuilder {
    val factMap: HashMap<String, ByteArray>
    var scopeUuid: UUID
    var maxFacts: Int
    var numParticipants: Int
}
