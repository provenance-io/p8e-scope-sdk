#Integration Tests

##What it does:
The integration tests allow someone to execute a contract with dummy data
and use various information from the results

##Important Notes:
The integration-test framework only supports single-party contracts currently

##Compatible Contracts
Any contract can be used as long as the records take in a Data proto as the input defined in `sdkContracts/protos/src/main/proto/p8e/testframework/contract.proto`.
Some contracts are provided in the sdkContracts directory

##The SdkTestContractBuilder
The SdkTestContractBuilder has one argument for it's constructor and that's the contract you want to use.
```aidl
SdkTestContractBuilder(SdkSinglePartyContractSmall::class.java)
```
Here, this returns a SdkTestContractBuilder that when built into a SdkTestContract and executes, it will execute a SdkSinglePartyContractSmall contract.

The Builder comes with many functions that return itself that allows you to customize various aspects of the contract

###The Builder's Functions
`.addProposedRecord(recordName: String, size: Int)` sets the value of the record recordName to random bytes sized size

`.setOwnerClient(client: Client)` sets the client that will be execute the contract for the OWNER party

`.setScopeUuid(uuid: UUID)` sets the scope uuid to be used if an existing scope is not provided

`.setScope(inputScope: ScopeRespose)` sets an existing scope where the contract will be executed

`.addAccessKey(key: PublicKey)` adds a key to the data access list of the scope when the contract is executed

##The SdkTestContract and Executing Contracts
The contract is created with the `SdkTestContractBuilder.build()` function.  To execute the contract, use `SdkTestContract.execute()`

`.execute()` returns a data class called `SdkContractResult`. 
This data class contains:

`result: ResultState` which is either `ResultState.SUCCESS` or `ResultState.FAILED` to indicate whether the contract completed or failed

`indexedResult: Map<String, Any>` which is the result of indexing the fields with the ProtoIndexer

`scope: ScopeResponse?` which is the scope that the contract was executed in

##Using Your Own Contracts
If a contract has records that take in the Data proto as input, the contract can be executed with this testframework.

To make these contracts compatible, you must place enter it into the `SdkContractInformationMap` in `Utils.kt`

The format of this map is the contract to SdkContractInformation.

The SdkContractInformation data class contains three things:

`maxRecords: Int` which is the number of records the contract has

`numParticipants: Int` which is the number of parties the contract has

`scopeSpec: Class<out P8eScopeSpecification>` which is the scope specification the contract is in

Here is an example of an item in SdkContractInformationMap
```aidl
SdkSinglePartyContractSmall::class.java to
            SdkContractInformation(2, 1, SdkSinglePartyTestScopeSpecification::class.java as Class<out P8eScopeSpecification>)
```

##Examples
There are examples of using this framework in `src/test/kotlin/io.provenance.p8e.test/SingleParty.kt`