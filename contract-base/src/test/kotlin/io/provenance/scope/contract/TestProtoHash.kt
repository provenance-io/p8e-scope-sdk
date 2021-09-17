package io.provenance.scope.contract

import io.provenance.scope.contract.proto.ProtoHash

class TestProtoHash : ProtoHash {

    private val classes = mapOf("io.provenance.scope.contract.proto.TestContractProtos\$TestProto" to true)

    override fun getClasses(): Map<String, Boolean> {
        return classes
    }

    override fun getUuid(): String {
        return "123456789"
    }

    override fun getHash(): String {
        return "M8PWxG2TFfO0YzL3sDW/l9kX325y+3v+5liGcjZoi4Q="
    }
}
