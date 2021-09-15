package io.provenance.p8e.testframework

import com.google.protobuf.ByteString
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import io.provenance.scope.contract.proto.*
import io.provenance.scope.encryption.ecies.ECUtils
import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.sdk.Session

class SessionBuilderTest : WordSpec({

    "SessionBuilder.Builder tests" should {
        mockkConstructor(Session.PermissionUpdater::class)
        every {anyConstructed<Session.PermissionUpdater>().saveConstructorArguments()} returns Unit
        every {anyConstructed<Session.PermissionUpdater>().saveProposedFacts(any())} returns Unit

        "Package Contract Single Record" {
            //Setting up single record test
            val osClient = createClientDummy(0)

            val builder = createSessionBuilderNoRecords(osClient)

            // Setting up dummy Record
            val dataLocation =
                Commons.Location.newBuilder().setClassname("io.provenance.scope.contract.proto.Contracts\$Record")
                    .setRef(builder.provenanceReference).build()
            val record = Contracts.Record.newBuilder().setDataLocation(dataLocation).setName("record2").build()
            builder.addProposedRecord("record2", record)

            // Create Session and run package contract for tests
            val session = builder.build()
            val envelopePopulatedRecord = session.packageContract(false)

            envelopePopulatedRecord.contract.invoker.signingPublicKey shouldBe PublicKeys.PublicKey.newBuilder()
                .setPublicKeyBytes(ByteString.copyFrom(ECUtils.convertPublicKeyToBytes(osClient.affiliate.signingKeyRef.publicKey)))
                .build()
            envelopePopulatedRecord.contract.invoker.encryptionPublicKey shouldBe PublicKeys.PublicKey.newBuilder()
                .setPublicKeyBytes(ByteString.copyFrom(ECUtils.convertPublicKeyToBytes(osClient.affiliate.encryptionKeyRef.publicKey)))
                .build()

            envelopePopulatedRecord.contract.considerationsCount shouldBe 1
            envelopePopulatedRecord.contract.considerationsList[0].considerationName shouldBe "record2"
            envelopePopulatedRecord.contract.considerationsList[0].inputsCount shouldBe 1
            envelopePopulatedRecord.contract.considerationsList[0].inputsList[0].classname shouldBe "io.provenance.scope.contract.proto.Contracts\$Record"
        }

        "Package Contract Existing Scope" {
            //Setting up single record test

            val osClient = createClientDummy(0)

            val scopeResponse = createExistingScope()

            val provenanceReference = Commons.ProvenanceReference.newBuilder().build()
            val dataLocation = Commons.Location.newBuilder().setClassname("record2").setRef(provenanceReference).build()
            val record = Contracts.Record.newBuilder().setDataLocation(dataLocation).setName("record2").build()

            val builder = createSessionBuilderNoRecords(osClient, scopeResponse.build())

            builder.addProposedRecord("record2", record)

            val session = builder.build()

            val envelopePopulatedRecord = session.packageContract(false)

            envelopePopulatedRecord.contract.invoker.signingPublicKey shouldBe PublicKeys.PublicKey.newBuilder()
                .setPublicKeyBytes(ByteString.copyFrom(ECUtils.convertPublicKeyToBytes(osClient.affiliate.signingKeyRef.publicKey)))
                .build()
            envelopePopulatedRecord.contract.invoker.encryptionPublicKey shouldBe PublicKeys.PublicKey.newBuilder()
                .setPublicKeyBytes(ByteString.copyFrom(ECUtils.convertPublicKeyToBytes(osClient.affiliate.encryptionKeyRef.publicKey)))
                .build()

            envelopePopulatedRecord.contract.considerationsCount shouldBe 1
            envelopePopulatedRecord.contract.considerationsList[0].considerationName shouldBe "record2"
            envelopePopulatedRecord.contract.considerationsList[0].inputsCount shouldBe 1
            envelopePopulatedRecord.contract.considerationsList[0].inputsList[0].classname shouldBe "io.provenance.scope.contract.proto.Contracts\$Record"

            envelopePopulatedRecord.contract.inputsCount shouldBe 1
            envelopePopulatedRecord.contract.inputsList[0].name shouldBe "record2"
            envelopePopulatedRecord.contract.inputsList[0].dataLocation.classname shouldBe "io.provenance.scope.contract.proto.Contracts\$Record"
        }

        "disallow adding a scope when sessions were not requested" {
            val osClient = createClientDummy(0)
            val scopeResponse = createExistingScope().apply {
                requestBuilder.setIncludeSessions(false)
            }

            val exception = shouldThrow<IllegalStateException> {
                createSessionBuilderNoRecords(osClient, scopeResponse.build())
            }
            exception.message shouldBe "Provided scope must include sessions"
        }

        "disallow adding a scope when records were not requested" {
            val osClient = createClientDummy(0)
            val scopeResponse = createExistingScope().apply {
                requestBuilder.setIncludeRecords(false)
            }

            val exception = shouldThrow<IllegalStateException> {
                createSessionBuilderNoRecords(osClient, scopeResponse.build())
            }
            exception.message shouldBe "Provided scope must include records"
        }

        "disallow setting data access keys that aren't on the existing scope" {
            val osClient = createClientDummy(0)

            val scopeResponse = createExistingScope().also { builder ->
                builder.scopeBuilder.scopeBuilder
                    .clearDataAccess()
            }

            val provenanceReference = Commons.ProvenanceReference.newBuilder().build()
            val dataLocation = Commons.Location.newBuilder().setClassname("record2").setRef(provenanceReference).build()
            val record = Contracts.Record.newBuilder().setDataLocation(dataLocation).setName("record2").build()

            val builder = createSessionBuilderNoRecords(osClient, scopeResponse.build())

            builder.addProposedRecord("record2", record)
            builder.dataAccessKeys.clear()
            builder.addDataAccessKey(localKeys[2].public)

            val session = builder.build()

            val exception = shouldThrow<IllegalStateException> {
                session.packageContract(false)
            }
            exception.message shouldContain localKeys[2].public.getAddress(false)
        }

        "disallow data access keys on the existing scope that are omitted in the proposed session" {
            val osClient = createClientDummy(0)

            val scopeResponse = createExistingScope().also { builder ->
                builder.scopeBuilder.scopeBuilder
                    .clearDataAccess()
                    .addDataAccess(localKeys[2].public.getAddress(false))
            }

            val provenanceReference = Commons.ProvenanceReference.newBuilder().build()
            val dataLocation = Commons.Location.newBuilder().setClassname("record2").setRef(provenanceReference).build()
            val record = Contracts.Record.newBuilder().setDataLocation(dataLocation).setName("record2").build()

            val builder = createSessionBuilderNoRecords(osClient, scopeResponse.build())

            builder.addProposedRecord("record2", record)
            builder.dataAccessKeys.clear()

            val session = builder.build()

            val exception = shouldThrow<IllegalStateException> {
                session.packageContract(false)
            }
            exception.message shouldContain localKeys[2].public.getAddress(false)
        }
    }
})
