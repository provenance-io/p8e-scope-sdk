package io.provenance.scope.sdk.extensions

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.provenance.scope.contract.proto.Contracts
import io.provenance.scope.contract.proto.Envelopes
import io.provenance.scope.contract.proto.Specifications.PartyType
import io.provenance.scope.encryption.ecies.ProvenanceKeyGenerator
import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.encryption.util.toPublicKey
import io.provenance.scope.proto.PK
import io.provenance.scope.sdk.toPublicKeyProto
import io.provenance.scope.util.ValueOwnerException

class EnvelopeExtensionsTest: WordSpec() {
    private fun generateSigner(): PK.SigningAndEncryptionPublicKeys = PK.SigningAndEncryptionPublicKeys.newBuilder()
        .setSigningPublicKey(ProvenanceKeyGenerator.generateKeyPair().public.toPublicKeyProto())
        .setEncryptionPublicKey(ProvenanceKeyGenerator.generateKeyPair().public.toPublicKeyProto())
        .build()

    private fun PK.SigningAndEncryptionPublicKeys.toRecital(partyType: PartyType): Contracts.Recital = Contracts.Recital.newBuilder()
        .setSignerRole(partyType)
        .setSigner(this)
        .build()

    init {
        "Envelope.getDefaultValueOwner" should {
            "Choose the invoker's address if the invoker is also a signer" {
                val invokerSigner = generateSigner()

                val envelope = Envelopes.Envelope.newBuilder()
                    .apply {
                        contractBuilder
                            .setInvoker(invokerSigner)
                            .addAllRecitals(listOf(invokerSigner.toRecital(PartyType.OWNER)))
                    }
                    .build()

                val valueOwnerAddress = envelope.getDefaultValueOwner(false)

                valueOwnerAddress shouldBe invokerSigner.signingPublicKey.toPublicKey().getAddress(false)
            }

            "Prefer the OWNER recital signer address over the ORIGINATOR if invoker is not a recital" {
                val invokerSigner = generateSigner()
                val ownerSigner = generateSigner()
                val originatorSigner = generateSigner()

                val envelope = Envelopes.Envelope.newBuilder()
                    .apply {
                        contractBuilder
                            .setInvoker(invokerSigner)
                            .addAllRecitals(listOf(
                                originatorSigner.toRecital(PartyType.ORIGINATOR),
                                ownerSigner.toRecital(PartyType.OWNER)
                            ))
                    }
                    .build()

                val valueOwnerAddress = envelope.getDefaultValueOwner(false)

                valueOwnerAddress shouldBe ownerSigner.signingPublicKey.toPublicKey().getAddress(false)
            }

            "Prefer the ORIGINATOR recital signer address over another non-OWNER recital if invoker is not a recital" {
                val invokerSigner = generateSigner()
                val custodianSigner = generateSigner()
                val originatorSigner = generateSigner()

                val envelope = Envelopes.Envelope.newBuilder()
                    .apply {
                        contractBuilder
                            .setInvoker(invokerSigner)
                            .addAllRecitals(listOf(
                                custodianSigner.toRecital(PartyType.CUSTODIAN),
                                originatorSigner.toRecital(PartyType.ORIGINATOR),
                            ))
                    }
                    .build()

                val valueOwnerAddress = envelope.getDefaultValueOwner(false)

                valueOwnerAddress shouldBe originatorSigner.signingPublicKey.toPublicKey().getAddress(false)
            }

            "Take the first recital signer address if invoker is not a signer and no OWNER/ORIGINATOR recital is present" {
                val invokerSigner = generateSigner()
                val custodianSigner = generateSigner()
                val affiliateSigner = generateSigner()

                val envelope = Envelopes.Envelope.newBuilder()
                    .apply {
                        contractBuilder
                            .setInvoker(invokerSigner)
                            .addAllRecitals(listOf(
                                custodianSigner.toRecital(PartyType.CUSTODIAN),
                                affiliateSigner.toRecital(PartyType.AFFILIATE),
                            ))
                    }
                    .build()

                val valueOwnerAddress = envelope.getDefaultValueOwner(false)

                valueOwnerAddress shouldBe custodianSigner.signingPublicKey.toPublicKey().getAddress(false)
            }

            "Throw a ValueOwnerException if no recitals are present" {
                val invokerSigner = generateSigner()

                val envelope = Envelopes.Envelope.newBuilder()
                    .apply {
                        contractBuilder
                            .setInvoker(invokerSigner)
                    }
                    .build()

                val exception = shouldThrow<ValueOwnerException> {
                    envelope.getDefaultValueOwner(false)
                }

                exception.message shouldBe "no suitable party found to be value owner"
            }
        }
    }
}
