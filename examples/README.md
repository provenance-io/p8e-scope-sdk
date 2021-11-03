# Examples

Examples are provided that demonstrate the functionality provided by the SDK. It's best
to look through all the examples before running. Some demonstrated functionality is built upon
previous examples so `app/src/main/kotlin/io/provenance/scope/examples/app/SimpleContract.kt` is
a good starting point.

## How to Run

### Build and Publish the SDK

NOTE: Run this at the root of this repository. This will publish the SDK jars into the local maven repository.

```
./gradlew clean build publishToMavenLocal -xsignMavenPublication --info
```

### Bootstrap Example Contracts

If you are running using the dev-tools docker compose setup, first run the following to set up the proper environment variables.

```
source ../dev-tools/compose/host.env
```

and then to bootstrap the contracts

```
./gradlew p8eClean p8eCheck --info
./gradlew p8eBootstrap --info
```

### Run an Example

NOTE: Change the mainClass property to the desired example for execution.

```
./gradlew -PmainClass=io.provenance.scope.examples.app.SimpleContractKt app:run
```

### Example Descriptions

SimpleContract.kt
 - Simple execution to introduce Provenance scopes and P8eContract basics.
DataAccess.kt
 - A single party contract that includes additional data access addresses. The data access list can be thought
 of as a READ ONLY party on the scope. The data access list will be audience members on all records persisted
 to object-store.
SmartKeyHsm.kt
 - Mimics the simple execution example but makes use of a third party HSM (Smart Key) for cryptography operations.
 - The following environment variables will have to be set that are associated with your Smart Key account.

 ```bash
 SMART_KEY_API_KEY, SMART_KEY_PUBLIC_KEY, SMART_KEY_UUID
 ```

UpdateAndIndexContract.kt
 - Demonstrates updating records on a Provenance scope and the concept of the P8e index protobuf descriptor.
BatchSendContracts.kt
 - Demonstrates higher throughput Provenance writes by batching and sending transactions in a background process.
CreatePackageAndCheckin.kt
 - Demonstrates creating a shipping package object and another contract adding "checkpoints" to the package as a simple example of a supply chain checkin. 

### TODO (steve docs) add examples of the following
- [x] smart key example
- [x] single party execution with hydration
- [x] data access example
- [x] complex single party execution and then update single party execution with proto indexer and with hydration
- [x] multiple contract batching and sending to chain
- [ ] change scope ownership
- [ ] multi party execution
- [ ] multistep execution
- [ ] skipping execution and using client strictly to save objects and persist to provenance
- [x] once examples exist, make sure CI is building all examples to verify they stay up to date
