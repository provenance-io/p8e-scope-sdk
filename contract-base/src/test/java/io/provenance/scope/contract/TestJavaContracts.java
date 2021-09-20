package io.provenance.scope.contract;

import io.provenance.scope.contract.annotations.Function;
import io.provenance.scope.contract.annotations.Input;
import io.provenance.scope.contract.annotations.Participants;
import io.provenance.scope.contract.annotations.Record;
import io.provenance.scope.contract.annotations.ScopeSpecification;
import io.provenance.scope.contract.annotations.ScopeSpecificationDefinition;
import io.provenance.scope.contract.proto.Specifications;
import io.provenance.scope.contract.proto.TestContractProtos;
import io.provenance.scope.contract.spec.P8eContract;
import io.provenance.scope.contract.spec.P8eScopeSpecification;

public class TestJavaContracts {
    @Participants(roles = {Specifications.PartyType.OWNER})
    public static class TestJavaContract extends P8eContract {
        @Function(invokedBy = Specifications.PartyType.OWNER)
        @Record(name = "testRecord")
        public TestContractProtos.TestProto testRecord() {
            return TestContractProtos.TestProto.newBuilder()
                    .setValue("testRecordJavaValue")
                    .build();
        }
    }
}
