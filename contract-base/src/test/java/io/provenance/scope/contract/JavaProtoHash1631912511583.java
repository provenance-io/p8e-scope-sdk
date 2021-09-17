package io.provenance.scope.contract;

import java.util.Map;
import java.util.HashMap;

import io.provenance.scope.contract.proto.ProtoHash;

public class JavaProtoHash1631912511583 implements ProtoHash {

    private final Map<String, Boolean> classes = new HashMap<String, Boolean>() {{
        put("io.provenance.scope.contract.proto.TestContractProtos$TestProto", true);
    }};

    @Override
    public Map<String, Boolean> getClasses() {
        return classes;
    }

    @Override
    public String getUuid() {
        return "1631912511583";
    }

    @Override
    public String getHash() {
        return "5+71R7IWzuDVAqeunYtBn0atXySPtXTb9xOGXckKoBo=";
    }
}
