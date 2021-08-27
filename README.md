```
                            ______  _____  _____
                            | ___ \|  _  ||  ___|
                            | |_/ / \ V / | |__
                            |  __/  / _ \ |  __|
                            | |    | |_| || |___
                            \_|    \_____/\____/

```
## Status

[![stability-alpha](https://img.shields.io/badge/stability-alpha-f4d03f.svg)](https://github.com/mkenney/software-guides/blob/master/STABILITY-BADGES.md#alpha)


# P8E-SDK — Provenance Contract Execution Environment Library

The Provenance Contact Execution Environment (nicknamed “p8e”) is an optional layer on top of the Provenance Blockchain
to allow single and multi-party client-side contract execution while preserving data privacy.
Provenance client-side contracts take encrypted data from the user (client) and transform the information into
encrypted data in the user’s own private object store with object hashes recorded on the blockchain.

Further documentation is provided [here](https://docs.provenance.io/p8e/overview).

## Provenance Blockchain

All of the contract memorialization artifacts are stored within the [Provenance](https://github.com/provenance-io/provenance)
open source blockchain. Submitted contract memorialization requests are evaluated against the known global provenance state.
Chain of custody and control is enforced for all state transitions to ensure provenance of data is maintained.

## Links
- [docs](https://docs.provenance.io/)
- [provenance github](https://github.com/provenance-io/provenance)
- [p8e-gradle-plugin github](https://github.com/provenance-io/p8e-gradle-plugin)
- [object-store github](https://github.com/provenance-io/object-store)