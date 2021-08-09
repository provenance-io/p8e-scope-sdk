package io.provenance.scope.objectstore.client

import com.google.common.util.concurrent.SettableFuture
import io.grpc.stub.StreamObserver
import java.util.*
import java.util.concurrent.CountDownLatch

class SingleResponseObserver<T> : StreamObserver<T> {
    val finishLatch: CountDownLatch = CountDownLatch(1)
    var error: Throwable? = null
    private var item: T? = null

    fun get(): T = item ?: throw IllegalStateException("Attempting to get result before it was received")

    override fun onNext(item: T) {
        this.item = item
    }

    override fun onError(t: Throwable) {
        error = t
        finishLatch.countDown()
    }

    override fun onCompleted() {
        finishLatch.countDown()
    }
}

class SingleResponseFutureObserver<T> : StreamObserver<T> {
    val future: SettableFuture<T> = SettableFuture.create()

    override fun onNext(value: T) {
        future.set(value)
    }

    override fun onError(t: Throwable) {
        future.setException(t)
    }

    override fun onCompleted() {
        if (!future.isDone) {
            future.setException(IllegalStateException("Stream closed before any values received"))
        }
    }
}

class BufferedResponseFutureObserver<T> : StreamObserver<T> {
    private val buffer = mutableListOf<T>()

    val future: SettableFuture<Iterator<T>> = SettableFuture.create()

    override fun onNext(value: T) {
        buffer.add(value)
    }

    override fun onError(t: Throwable) {
        future.setException(t)
    }

    override fun onCompleted() {
        if (buffer.isEmpty()) {
            future.setException(IllegalStateException("Stream closed before any values received"))
        }

        future.set(buffer.iterator())
    }
}
