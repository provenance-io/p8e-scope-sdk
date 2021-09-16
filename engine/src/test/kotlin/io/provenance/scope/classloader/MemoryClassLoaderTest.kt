package io.provenance.scope.classloader

import io.kotest.core.spec.style.WordSpec
import io.provenance.scope.contract.TestContract
import io.provenance.scope.contract.proto.PublicKeys
import io.provenance.scope.contract.proto.Utils
import io.provenance.scope.contract.spec.P8eContract
import java.io.FileInputStream


class MemoryClassLoaderTest: WordSpec() {
    init {
        "MemoryClassLoader" should {
            "load P8eContract classes from child class loader" {
                val classLoader = TestContract::class
                    .java
                    .protectionDomain
                    .codeSource
                    .location
                    .path
                    .let { FileInputStream(it) }
                    .use {
                        MemoryClassLoader(
                            "some-hash",
                            it
                        )
                    }

                val clazz = classLoader.loadClass(TestContract::class.java.name)

                assert(P8eContract::class.java.isAssignableFrom(clazz)) { "loaded contract class does not inherit from P8eContract" }
                assert(TestContract::class.java.name == clazz.name) { "loaded contract class name was different than requested" }
                assert(TestContract::class.java != clazz) { "loaded contract class was the same as system contract class" }

                val publicKeyResultClass = clazz.declaredMethods.find { it.name == "testRecord" }?.returnType
                assert(publicKeyResultClass != null) { "testRecord method not found on TestContract" }
                assert(publicKeyResultClass?.name == PublicKeys.PublicKey::class.java.name) { "testRecord return type name does not match" }
                assert(publicKeyResultClass == PublicKeys.PublicKey::class.java) { "testRecord return type does not match" }

                val uuidClass = clazz.superclass.declaredFields.find { it.name == "uuid" }?.type
                assert(uuidClass != null) { "base P8eContract class uuid field not found" }
                assert(uuidClass?.name == Utils.UUID::class.java.name) { "base P8eContract uuid type name does not match" }
                assert(uuidClass == Utils.UUID::class.java) { "base P8eContract uuid type does not match" }
            }
        }
    }
}
