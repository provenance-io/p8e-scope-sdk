package io.provenance.scope.contract.spec

import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import io.provenance.scope.contract.proto.Utils

/**
 * Provide basic functionality for agreement setup.
 */
abstract class P8eContract {
    val uuid = Utils.UUID.newBuilder().setValue(UUID.randomUUID().toString()).build()
    val currentTime = AtomicReference<OffsetDateTime?>()

    // By invoking the consideration you are indicating your agreement with the consideration.
    fun impliedConsent() = Utils.BooleanResult.newBuilder().setValue(true).build()

    protected fun getCurrentTime(): OffsetDateTime {
        return currentTime.get()
            ?: throw IllegalStateException("Current time wasn't set prior to contract construction.")
    }
}
