# P8e Scope Sdk

The libraries included here comprise the development kit that makes interacting with the [Provenance Blockchain](https://github.com/provenance-io/provenance)
[Metadata](https://docs.provenance.io/modules/metadata-module) module.

The components needed to make use of this include the following:
- Provenance node - Provides an API to submit transactions to and to read the event stream from.
- Object store - Stores encrypted DIME objects. More information can be found [here](https://github.com/provenance-io/object-store).

The easiest way to run these dependent services in a local environment is to use the docker-compose setup [here](https://github.com/provenance-io/p8e-scope-sdk/tree/main/dev-tools/compose).

## Provenance Scopes

See the docs on the [Provenance Metadata](https://docs.provenance.io/modules/metadata-module) for background.
TODO (steve docs) add more information

## Contract Execution

One function that this sdk can perform is JVM based contract executions. A contract is a class that subclasses `P8eContract` and contains annotations. The annotations are used at runtime
to map the `P8eContract` to a [Provenance ContractSpec](https://github.com/provenance-io/provenance/blob/main/proto/provenance/metadata/v1/specification.proto#L61-L86).
A [gradle plugin](https://github.com/provenance-io/p8e-gradle-plugin) is provided to help develop contracts and to persist them to Provenance and Object Store.

## Examples

A collection of examples are maintained [here](https://github.com/provenance-io/p8e-scope-sdk/tree/main/examples).
