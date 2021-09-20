package io.provenance.scope.util

fun <T> ClassLoader.forThread(fn: () -> T): T {
    val current = Thread.currentThread().contextClassLoader
    try {
        Thread.currentThread().contextClassLoader = this
        return fn()
    } finally {
        Thread.currentThread().contextClassLoader = current
    }
}
