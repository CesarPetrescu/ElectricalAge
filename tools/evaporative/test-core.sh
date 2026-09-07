#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p build/evaporative-core
javac -d build/evaporative-core src/main/java/mods/eln/transparentnode/evaporative/*.java \
  src/test/java/mods/eln/transparentnode/evaporative/EvaporationRegression.java
java -cp build/evaporative-core mods.eln.transparentnode.evaporative.EvaporationRegression
