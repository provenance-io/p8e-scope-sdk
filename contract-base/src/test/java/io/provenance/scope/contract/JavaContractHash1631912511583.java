package io.provenance.scope.contract;

import io.provenance.scope.contract.contracts.ContractHash;

import java.util.Map;
import java.util.HashMap;

public class JavaContractHash1631912511583 implements ContractHash {

    private final Map<String, Boolean> classes = new HashMap<String, Boolean>() {{
        put("io.provenance.scope.contract.TestJavaContracts$TestJavaContract", true);
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
        return "bfkvcj/TeXCrhUZ4TJedRP2iIWRggsIg2PZ6gaRCUlg=";
    }
}
