package io.provenance.scope.contract.spec

import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import io.provenance.scope.proto.Util

/**
 * Provide basic functionality for agreement setup.
 */
abstract class P8eContract {
    val uuid = Util.UUID.newBuilder().setValue(UUID.randomUUID().toString()).build()
    val currentTime = AtomicReference<OffsetDateTime?>()

    protected fun getCurrentTime(): OffsetDateTime {
        return currentTime.get()
            ?: throw IllegalStateException("Current time wasn't set prior to contract construction.")
    }
}
