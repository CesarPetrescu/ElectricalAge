# HV/DC-DC regression tools

The normal `./gradlew test` task discovers `HvRepairRegressionTest` (three methods wrapping the
actual MNA/converter/feature regression bodies) and `HvRegistryIntegrationTest` (two real
Minecraft registry/manufacturing tests). No Gradle or wrapper changes are required.

## Offline subset

```
COMMONS_NUMBERS_JAR=/path/to/commons-numbers-core-1.2.jar bash tools/hv/test-offline.sh
```

Requires Java 21 and a local Kotlin compiler; the executed offline run used Kotlin 1.9.0.
The dependency can be taken from your Gradle cache or extracted from the corresponding embedded
jar in a baseline ELN artifact. The full Minecraft build still uses the repository's Kotlin 2.4.0
and NeoForge configuration, unchanged.

The harness compiles repository MNA components, solver, power-transfer controllers, winding
enthalpy, wire physics, and persisted fault/control models. `offline-support` replaces only
Minecraft scalar NBT storage, logging, metrics publishing and profiler integration; it does not
replace circuit algebra or converter runtime. It is outside `src/main` and never enters the mod.
The NBT fixture is NOT Minecraft binary serialization or a real saved-world test.

Existing test assertion bodies are also run without a JUnit engine by a generated adapter.
Only `@Test` and framework/import plumbing are removed; benchmark/profiling classes are omitted,
just as in the ordinary correctness task. The generated manifest names every executed method.

Logs are under `build/hv-offline/logs`. The harness is not a mod build, GUI test, dedicated-server
test, client test, benchmark, or compatibility test. Do not report it as one.

`fixtures/legacy-utility-identities.tsv` was derived from the pinned source's registration loops,
not from a live baseline registry capture. The real FML test checks those 152 expected identities
against registered items, including item-stack lookup and registry keys.

`check-kotlin-syntax.kt` uses the locally installed Kotlin compiler PSI parser. It checks syntax
only. Some unrelated baseline files use newer syntax than Kotlin 1.9 accepts; scope that utility
to changed files. Syntax success is not type resolution against Minecraft.
