package io.provenance.scope

import io.github.kawamuray.wasmtime.Instance
import io.github.kawamuray.wasmtime.Module
import io.github.kawamuray.wasmtime.Store
import io.github.kawamuray.wasmtime.Val
import java.nio.ByteBuffer
import java.nio.ByteOrder


open class WasmInstance(wasmBytes: ByteArray, val allocator: String = "p8e_allocate", val deallocator: String = "p8e_free") {
    private val store = Store.withoutData() // todo: what the heck is a store?
    private val engine = store.engine()
    private val module = Module.fromBinary(engine, wasmBytes)
    private val instance = Instance(store, module, emptyList())

    fun callFunByName(name: String, vararg args: Int): Int = instance.getFunc(store, name).get().call(store, args.map(Val::fromI32)).first().i32()

    fun callFunByName(name: String, vararg args: ByteArray): ByteArray = callFunByName(name, *args.map(::writeP8ePtr).toIntArray()).let(::readP8ePtr)

    fun readP8ePtr(ptr: Int): ByteArray {
        val memory = instance.getMemory(store, "memory")
        val memBuf = memory.get().buffer(store)
        val lengthBuf = ByteArray(4)
        memBuf.position(ptr)
        memBuf.get(lengthBuf, 0, 4)
        val length = ByteBuffer.wrap(lengthBuf).order(ByteOrder.LITTLE_ENDIAN).getInt(0)
        val valueBuf = ByteArray(length)
        memBuf.position(ptr + 4)
        memBuf.get(valueBuf, 0, length)
        return valueBuf
    }

    fun writeP8ePtr(data: ByteArray): Int {
        val ptr = callFunByName(allocator, data.size + 4)
        val memory = instance.getMemory(store, "memory")
        val memBuf = memory.get().buffer(store)
        memBuf.position(ptr)
        val lengthBuf = ByteArray(4)
        ByteBuffer.wrap(lengthBuf).order(ByteOrder.LITTLE_ENDIAN).putInt(data.size)
        memBuf.put(lengthBuf)
        memBuf.position(ptr + 4)
        memBuf.put(data)
        return ptr
    }
}
