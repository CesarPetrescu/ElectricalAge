# Electrical Age — Minecraft 1.21.1 connected-circuit port

This is the isolated **port/1.21.1** branch. The modern project lives in **[ports/1.21.1/](ports/1.21.1/)**. The repository-root sources/build and original artwork are retained as legacy references; **do not run the root Gradle build for this port**.

Target: Minecraft **1.21.1**, NeoForge **21.1.249**, Java **21**. Current prototype version: **0.2.0-port.2**.

## Implemented scope

Five prototype blocks: an independent circuit bench plus a connected voltage source, resistive wire, resistive load and capacitor. The source/wire/load/capacitor network uses inherited MNA electrical equations and requires a physical return path. Source/load/capacitor have opposite positive/negative terminals; wire connects all six faces resistively. Source toggling, live wire removal/reconnection, capacitor history, fault-preserving saved state, recipes, drops and models are implemented for this bounded slice.

This is **not the full historical mod**: SixNode interactions, the machine catalogue, thermal/mechanical systems, external automation, production-scale graphs and old-world migration remain unfinished. Current guards limit connected devices to 64 per server level and 128 MNA unknowns per island. Breaking devices resets item-carried charge.

## Build / continue development

```sh
cd ports/1.21.1
./gradlew :sim-core:cleanTest :sim-core:test build --no-build-cache
./gradlew runGameTestServer
./gradlew runClient
```

On Windows, use `.\gradlew.bat` in the same directory. Read the [module README](ports/1.21.1/README.md) for a reproducible closed-loop circuit and installation details, and [HANDOFF.md](ports/1.21.1/HANDOFF.md) for ownership, verification boundaries and next tasks. The module's standalone ZIP can be opened directly as a Gradle project and includes a standalone CI workflow.

Use a **new disposable NeoForge 1.21.1 / Java 21 profile**, the regular JAR (not the sources JAR), and a new world. Do not install alongside the old ELN JAR or open a legacy ELN world.

## Validation records

The `ELN 1.21.1 port` Actions workflow builds the real mapped target, executes fresh core regressions, requires the exact named Minecraft GameTests plus their framework summary, checks resources/packaging, and launches both a development client and an independent client loading the actual JAR. Its artifacts record the exact source commit and checksums. Read test scope in the handoff; boot/model validation is not multiplayer or populated-world restart proof.

[First-milestone validation](porting/1.21.1-VALIDATION.md) is retained as a **historical** record for the earlier bench-only version; it does not validate later changes.

## Credits / licensing

Original Electrical Age source and art attribution remains in [LICENSE.md](LICENSE.md), [docs/credits.md](docs/credits.md), and the modern module's LICENSE-legacy.md, PROVENANCE.json and ASSET-MIGRATION.json. The inherited artwork was not relicensed. The default legacy branch and the 1.12 baseline branch are separate and were not changed by this modern-port work.
