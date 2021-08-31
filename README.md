# P8e Scope Sdk

## Status

[![stability-alpha](https://img.shields.io/badge/stability-alpha-f4d03f.svg)](https://github.com/mkenney/software-guides/blob/master/STABILITY-BADGES.md#alpha)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.provenance.scope/sdk/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.provenance.scope/sdk)
[![Latest Release][release-badge]][release-latest]
[![Code Coverage][code-coverage-badge]][code-coverage-report]
[![License][license-badge]][license-url]
[![LOC][loc-badge]][loc-report]

[code-coverage-badge]: https://codecov.io/gh/provenance-io/p8e-scope-sdk/branch/main/graph/badge.svg
[code-coverage-report]: https://app.codecov.io/gh/provenance-io/p8e-scope-sdk

[release-badge]: https://img.shields.io/github/v/tag/provenance-io/p8e-scope-sdk.svg?sort=semver
[release-latest]: https://github.com/provenance-io/p8e-scope-sdk/releases/latest

[license-badge]: https://img.shields.io/github/license/provenance-io/p8e-scope-sdk.svg
[license-url]: https://github.com/provenance-io/p8e-scope-sdk/blob/main/LICENSE

[loc-badge]: https://tokei.rs/b1/github/provenance-io/p8e-scope-sdk
[loc-report]: https://github.com/provenance-io/p8e-scope-sdk

The libraries included here comprise a development kit that makes interacting with the [Provenance Blockchain](https://github.com/provenance-io/provenance)
[Metadata](https://docs.provenance.io/modules/metadata-module) module easier.

The components needed to make use of this include the following:
- Provenance node - Provides an API to submit transactions to and to read the event stream from.
- Object store - Stores encrypted DIME objects. More information can be found [here](https://github.com/provenance-io/object-store).

The quickest way to run these dependent services in a local environment is to use the docker-compose setup [here](https://github.com/provenance-io/p8e-scope-sdk/tree/main/dev-tools/compose).

## Provenance Scopes

See the docs on the [Provenance Metadata](https://docs.provenance.io/modules/metadata-module) module for background.
TODO (steve docs) add more information

## Contract Execution

One function that this sdk can perform is JVM based contract executions. A contract is a class that subclasses `P8eContract` and contains annotations. The annotations are used at runtime
to map the `P8eContract` to a [Provenance ContractSpec](https://github.com/provenance-io/provenance/blob/main/proto/provenance/metadata/v1/specification.proto#L61-L86).
A [gradle plugin](https://github.com/provenance-io/p8e-gradle-plugin) is provided to help develop contracts and to persist them to Provenance and Object Store.

## Examples

A collection of examples are maintained [here](https://github.com/provenance-io/p8e-scope-sdk/tree/main/examples).