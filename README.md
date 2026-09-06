# Electrical Age — Minecraft 1.21.1 port branch

This branch is **port/1.21.1**. It contains a tested **NeoForge 1.21.1 prototype**, not a complete port of Electrical Age.

**The modern Gradle project is in [`ports/1.21.1/`](ports/1.21.1/). Do not run the legacy repository-root Gradle build when testing this port.**

```sh
cd ports/1.21.1
./gradlew :sim-core:test build
./gradlew runGameTestServer
./gradlew runClient
```

Target: Minecraft 1.21.1, NeoForge 21.1.249, Java 21. The initial implementation has a Minecraft-independent MNA simulation module and one circuit test bench with adapted original OBJ artwork, persistence and synchronization.

## Current verified milestone

- Tested source commit: `1170d34764119f6d03de12297d829a77ea91f34b`.
- [Final successful CI](https://github.com/CesarPetrescu/ElectricalAge/actions/runs/34019376906).
- 456 core JUnit cases, ten in-game GameTests and five asset-validator tests passed.
- Development client and independent packaged-JAR client passed, including four block orientations and the inventory model.
- The final unchanged core JUnit results came from Gradle cache; the previous green run executed them, and a fresh plain-Java numerical recheck also passed.

See the [validation record](porting/1.21.1-VALIDATION.md), [modern module README](ports/1.21.1/README.md), and [porting plan](ports/1.21.1/docs/PORTING-PLAN.md).

## Trying the prototype

Use a **new disposable NeoForge 1.21.1 profile and Java 21**, with the regular `eln-1.21.1-0.1.0-port.1.jar` from the CI artifact. Do not use the sources JAR or load a legacy world.

Obtain `/give @s eln:circuit_bench`, place it, and empty-hand right-click to switch the internal RC source and display V/A/J measurements. Sneak-right-click resets it. Separate benches do not connect to each other, and breaking/replacing one resets its charge.

## Remaining work

Connected source/cable/load networks, SixNode face-mounted components, full machines, inventories/menus/capabilities, animated machinery, thermal/mechanical systems, modern upstream parity, survival progression, optional integrations, populated-world restarts and multiplayer still need implementation or validation. No legacy-world conversion or full feature parity is claimed.

## Legacy source and attribution

The older root source is retained for reference, not compiled by the modern module. The previous root README is preserved as [README-legacy.md](README-legacy.md). The default and 1.12 baseline branches were not changed by this port work.

Inherited source and artwork retain their original notices and respective licenses. See the modern module's LICENSE-legacy.md, PROVENANCE.json and ASSET-MIGRATION.json.
