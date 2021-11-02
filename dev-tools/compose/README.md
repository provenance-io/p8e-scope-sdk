# Docker Compose Environment

## Overview

This compose environment allows for an simple way to get started with developing against the
Provenance metadata module

The genesis file seeds large hash allocations on the following mnemonics.

```bash
"stable payment cliff fault abuse clinic bus belt film then forward world goose bring picnic rich special brush basic lamp window coral worry change"
"present pulse retreat vault snap buyer purse lift casual horse canal silent quick arrest wedding win slide cool bicycle pride display unhappy assume inside"
"genuine kitten liar plunge swim host way stove space room boring interest rose artist into marine mushroom minimum tip unfair nose plunge nest glory"
```

and the corresponding Java EC private keys for the above mnemonics as hex.

```bash
0A2100EF4A9391903BFE252CB240DA6695BC5F680A74A8E16BEBA003833DFE9B18C147
0A2100CBEDAA6241122CB6B5BD2A3E1FFDD8694C0AEC16E80A0CC72B6256C56090F6FA
0A21009B6CFD2525DD7EA500A3F2665047319A3D2A3F1D62177D686DF98713D8E52BDB
```

## Usage

`./host.env` contains environment variables useful to source on your host. It contains default
variables that can be used for bootstrapping Provenance contract specifications.

## Start/Stop Applications

Typical [docker-compose](https://docs.docker.com/compose/) commands can be used to manage the applications.

```
docker-compose up -d
docker-compose stop

or for testing multiparty contracts (WIP)

docker compose --profile multi-party up -d
docker compose --profile multi-party down -v
```

## Using the provenanced CLI

A wrapper script is provided at `./bin/provenanced`.

Example usage:

```
./bin/provenanced query metadata scopespec all
```

## Resetting Data Volumes

`docker-compose down -v` can be used to wipe all the stateful volumes. This will wipe all Object Store data
and reset the Provenance blockchain to block 0.
