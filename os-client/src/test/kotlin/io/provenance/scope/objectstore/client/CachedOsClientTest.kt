package io.provenance.scope.objectstore.client

import com.google.common.util.concurrent.Futures
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.provenance.scope.encryption.crypto.SignatureInputStream
import io.provenance.scope.encryption.domain.inputstream.DIMEInputStream
import io.provenance.scope.encryption.ecies.ProvenanceKeyGenerator
import io.provenance.scope.encryption.model.DirectKeyRef
import io.provenance.scope.objectstore.util.base64Decode
import io.provenance.scope.objectstore.util.toHex
import io.provenance.scope.util.NotFoundException

class CachedOsClientTest: WordSpec() {
    lateinit var osClient: OsClient
    val signingKeyRef = ProvenanceKeyGenerator.generateKeyPair().let { DirectKeyRef(it.public, it.private) }
    val encryptionKeyRef = ProvenanceKeyGenerator.generateKeyPair().let { DirectKeyRef(it.public, it.private) }

    override fun beforeTest(testCase: TestCase) {
        super.beforeTest(testCase)

        osClient = mockk<OsClient>()
    }

    init {
        "CachedOsClient" should {
            "throw exception when object signature not verified" {
                val signatureInputStream = mockk<SignatureInputStream>()
                every { signatureInputStream.readAllBytes() } returns ByteArray(0)
                every { signatureInputStream.verify() } returns false
                val dimeInputStream = mockk<DIMEInputStream>()
                every { dimeInputStream.getDecryptedPayload(any()) } returns signatureInputStream
                every { osClient.get(any(), any()) } returns Futures.immediateFuture(dimeInputStream)

                val cachedOsClient = CachedOsClient(osClient, 1, 1, 0)

                val exception = shouldThrow<NotFoundException> {
                    val ex = cachedOsClient.getJar("abc".base64Decode(), encryptionKeyRef).runCatching { get() }.exceptionOrNull()
                    ex shouldNotBe null
                    ex!!.cause shouldNotBe null
                    throw ex.cause!!
                }

                exception.message shouldContain "abc"
                exception.message shouldContain encryptionKeyRef.publicKey.toHex()
            }
        }
    }
}
