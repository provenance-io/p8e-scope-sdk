# Examples

Examples are provided that demonstrate the functionality provided by the SDK. It's best
to look through all the examples before running. Some demonstrated functionality is built upon
previous examples so `app/src/main/kotlin/io/provenance/scope/examples/app/SimpleContract.kt` is
a good starting point.

## How to Run

### Build and Publish the SDK

NOTE: Run this at the root of this repository.

```
./gradlew clean build publishToMavenLocal -xsignMavenPublication --info
```

### Bootstrap Example Contracts

```
./gradlew p8eClean p8eCheck --info
./gradlew p8eBootstrap --info
```

### Run an Example

NOTE: Change the mainClass property to the desired example for execution.

```
./gradlew -PmainClass=io.provenance.scope.examples.app.UpdateAndIndexContractKt app:run
```

### TODO (steve docs) add examples of the following
- [ ] smart key example
- [x] single party execution with hydration
- [x] complex single party execution and then update single party execution with proto indexer and with hydration
- [x] multiple contract batching and sending to chain
- [ ] multi party execution
- [ ] multistep execution
- [ ] skipping execution and using client strictly to save objects and persist to provenance
- [x] once examples exist, make sure CI is building all examples to verify they stay up to date
