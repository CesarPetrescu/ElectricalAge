#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/offline
find sim-core/src/main/java -name '*.java' | sort > build/offline/sources.txt
printf '%s\n' sim-core/src/test/java/mods/eln/audit/NumericalChecks.java >> build/offline/sources.txt
javac --release 21 -d build/offline/classes @build/offline/sources.txt
java -cp build/offline/classes mods.eln.audit.NumericalChecks | tee build/offline/numerical.log
