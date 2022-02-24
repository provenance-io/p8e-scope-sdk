package io.provenance.scope

import io.provenance.scope.contract.proto.TestContractProtos

fun testProto(value: String): TestContractProtos.TestProto = TestContractProtos.TestProto.newBuilder().setValue(value).build()
fun testProto2(value: String): TestContractProtos.TestProto2 = TestContractProtos.TestProto2.newBuilder().setValue(value).build()
