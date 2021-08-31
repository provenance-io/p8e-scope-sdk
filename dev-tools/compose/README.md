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

and the following Java EC private keys as hex.

```bash
0A2071E487C3FB00642F1863D57749F32D94F13892FA68D02049F7EA9E8FC58D6E63
0A2077170DEDCB6CFEDCE5A19BC8F0CD408A254F1E48A7350FC2E9019F50AE52524F
0A203CE1967EF504559302CB027A52CB36E5BF6EDC2D8CAFEFF86CA2AAF2817C929F
```

## Usage

`./host.env` contains environment variables useful to source on your host. It contains default
variables that can be used for bootstrapping Provenance contract specifications.

## Start/Stop Applications

Typical [docker-compose](https://docs.docker.com/compose/) commands can be used to manage the applications.

## Using the provenanced CLI

A wrapper script is provided at `./bin/provenanced`.

Example usage:

```
./bin/provenanced query metadata scopespec all
```

## Resetting Data Volumes

`docker-compose down -v` can be used to wipe all the stateful volumes. This will wipe all Object Store data
and reset the Provenance blockchain to block 0.
