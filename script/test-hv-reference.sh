#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/hv-reference
# No Minecraft stubs: only the actual pure production math and a standalone copy of its assertion corpus.
sed '/class HighVoltageReferenceTest/,$d' src/test/kotlin/mods/eln/sim/power/HighVoltageReferenceTest.kt > build/hv-reference/Corpus.kt
printf '\nfun main() = runReferenceCorpus()\n' >> build/hv-reference/Corpus.kt
kotlinc src/main/kotlin/mods/eln/sim/power/{ConverterCore,ControlMapping,Insulation,PortSafety}.kt \
  src/test/kotlin/mods/eln/sim/power/WindingModel.kt build/hv-reference/Corpus.kt \
  -jvm-target 17 -include-runtime -d build/hv-reference/tests.jar
java -jar build/hv-reference/tests.jar
