#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
ROOT="$PWD"
KOTLINC="${KOTLINC:-$(command -v kotlinc || true)}"
if [[ -z "$KOTLINC" ]]; then echo 'A local Kotlin compiler is required (tested with 1.9.0).' >&2; exit 2; fi
KOTLIN_HOME="${KOTLIN_HOME:-$(cd "$(dirname "$(readlink -f "$KOTLINC")")/.." && pwd)}"
LIB="${COMMONS_NUMBERS_JAR:-}"
if [[ -z "$LIB" ]]; then
    LIB="$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches" -path '*commons-numbers-core*1.2*' -name '*.jar' -print -quit 2>/dev/null || true)"
fi
if [[ ! -f "$LIB" ]]; then echo 'Set COMMONS_NUMBERS_JAR to commons-numbers-core-1.2.jar (also embedded in the baseline ELN mod JAR).' >&2; exit 2; fi
OUT="$ROOT/build/hv-offline"
mkdir -p "$OUT/classes" "$OUT/logs"
# Only platform logging and NBT storage are stubbed. Every MNA solver/component is repository source.
mapfile -t JAVA < <(find tools/hv/offline-support src/main/java/mods/eln/sim/mna -name '*.java' | sort)
javac -cp "$LIB" -d "$OUT/classes" "${JAVA[@]}" \
    src/main/java/mods/eln/sim/{ElectricalLoad,ElectricalConnection,ThermalLoad,IProcess}.java \
    src/main/java/mods/eln/sim/nbt/NbtThermalLoad.java \
    src/test/java/mods/eln/sim/mna/HvMnaRegression.java
mapfile -t PURE < <(find src/main/kotlin/mods/eln/sim/power -name '*.kt' | sort)
"$KOTLINC" "${PURE[@]}" \
    src/main/kotlin/mods/eln/transparentnode/{OneWayDcDcMath,OneWayDcDcProcess,WindingThermalLoad}.kt \
    src/main/kotlin/mods/eln/sixnode/electricalcable/{UtilityCableMaterial,WirePhysics,WireThermalPhysics,WireThermalLoad,HvCableSpecifications,CableInsulationState}.kt \
    src/main/kotlin/mods/eln/sim/process/heater/ElectricalHeatAccumulator.kt \
    src/main/kotlin/mods/eln/mechanical/ShaftElectricalMath.kt \
    src/test/kotlin/mods/eln/transparentnode/{HvConverterRegression,HvFeatureRegression}.kt \
    -classpath "$OUT/classes" -include-runtime -jvm-target 17 -d "$OUT/runtime-tests.jar"
CP="$OUT/runtime-tests.jar:$OUT/classes:$LIB"
java -cp "$CP" mods.eln.sim.mna.HvMnaRegression | tee "$OUT/logs/mna.log"
java -cp "$CP" mods.eln.transparentnode.HvConverterRegressionKt | tee "$OUT/logs/converters.log"
java -cp "$CP" mods.eln.transparentnode.HvFeatureRegressionKt | tee "$OUT/logs/features.log"
python3 tools/hv/prepare-existing-tests.py "$OUT/existing"
mapfile -t EXISTING < <(find "$OUT/existing" tools/hv/offline-support/kotlin -name '*.kt' | sort)
"$KOTLINC" "${EXISTING[@]}" -classpath "$CP:$KOTLIN_HOME/lib/kotlin-test.jar" \
    -Xfriend-paths="$OUT/runtime-tests.jar" -jvm-target 17 -d "$OUT/existing-tests.jar"
java -cp "$OUT/existing-tests.jar:$CP:$KOTLIN_HOME/lib/kotlin-test.jar" ExistingTestRunnerKt | tee "$OUT/logs/existing.log"
printf '%s\n' 'PASS: offline harness only; this is NOT a Minecraft/NeoForge build or playtest.' | tee "$OUT/logs/scope.log"
