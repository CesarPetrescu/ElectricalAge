# Developer handoff — connected 1.21.1 port

## Entry point

Work in this standalone directory (`ports/1.21.1/` in the parent repository). Java 21, Minecraft 1.21.1, NeoForge 21.1.249, ModDevGradle 2.0.146, Gradle 9.2.1. Start with README.md and the source files below. Do not build the legacy root or rerun the historical bootstrap.

The handoff ZIP's accompanying verification record supplies the exact tested commit, CI run, hashes and final executed test counts. This document describes implementation and acceptance gates; it is not a substitute for those results.

## Architecture / where to edit

| Path | Responsibility |
|---|---|
| `sim-core/.../sim/mna` | Inherited solver, components and numerical routines; no game dependencies |
| `sim-core/.../sim/network/CircuitNetwork.java` | Bounded netlist, independent electrical islands, differential readings and latched faults |
| `sim-core/.../sim/network/GridTopology.java` | Six-direction ports, finite-resistance wire arms, internal source resistance and explicit return paths |
| `sim-core/.../sim/bench/RcCircuit.java` | Independent bench fixture, not a world graph |
| `src/main/java/mods/eln/modern/ElectricalAgeModern.java` | Registry entries and creative tab |
| `.../modern/network/LevelCircuitManager.java` | Server-thread-only per-level graph ownership, tick heartbeats, graph rebuild and publishing |
| `.../modern/network/CircuitDeviceBlock*.java` | Connected devices, interaction, vanilla tick participation, saved history and synchronization |
| `.../modern/CircuitBenchBlock*.java` | Independent bench and versioned fault-preserving persistence |
| `.../modern/CompoundStateData.java` | Real modern NBT adapter for core persistence |
| `.../modern/client/ClientValidation.java` | Explicit opt-in CI assertions; checks bench OBJ and all connected models |
| `src/gametest` | Actual mapped Minecraft/NeoForge tests; excluded from regular and sources JARs |
| `tools/verify.py`, `tools/client_smoke.py` | Fail-closed package/test and development/packaged-client probes |

## Changes since first milestone

Singular or nonfinite solves no longer silently overwrite previous voltages; publication waits for a complete finite solution. Component removal detaches private states and callbacks. PowerSource's duplicate RHS registration was removed (its configured 3 V previously solved as 6 V). Capacitance history is differential, so rebuilding a graph or changing the arbitrary reference does not erase charge. Capacitor current is now measured instead of returning an unconditional zero.

Added bounded connected source/wire/load/capacitor devices, live topology rebuilds and explicit two-terminal return paths. Independent numerical islands isolate a failed solve. A level-wide topology construction failure can freeze all currently participating devices in that rebuild; this is deliberate fail-closed behavior, not optimal per-island recovery.

Saved bench schema 2 includes runtime fault state and reads valid schema 1. Connected device schema 1 validates type, kind, finite differential voltage and strict booleans. Unsupported/corrupt device data is retained until explicit reset, but not sent to clients. Component-level voltage/current restoration validates the whole record before mutation. Removed entities cannot toggle/reset/advance. Interaction checks vanilla build/interact permissions; third-party claim integrations are not claimed.

Resource checks cover four new items, recipes, loot tables and 48 facing/light block states. Version metadata comes from Gradle rather than a second hardcoded string. Offline tests are cross-platform Python + JDK 21. The historical generator is disabled. Main CI executes fresh tests with the Gradle build cache disabled and preserves failure evidence.

## Verify before changing behavior

```sh
python3 tools/test_offline.py
python3 -m unittest discover -s tools -p 'test_*.py' -v
./gradlew :sim-core:cleanTest :sim-core:test build --no-build-cache
python3 tools/verify.py
./gradlew runGameTestServer
```

The core has 517 JUnit cases: 446 inherited numerical, 41 hardening, 20 connected-network and 10 RC fixture tests. The plain offline runner executes 507 of these, without JUnit. There are 15 Python validator tests and 21 Minecraft GameTests. Counts are thresholds, not a coverage percentage. Never turn a missing, skipped or failing test result into a green gate.

Linux CI additionally runs `python3 tools/client_smoke.py` and `python3 tools/client_smoke.py --packaged` under Xvfb/Mesa. They use **only their disposable smoke directories**, intentionally terminate the client after readiness, and do not prove ordinary interactive gameplay. Packaged mode checks that the loaded mod originates in the regular JAR and development GameTest classes are absent.

## Known limits / do not misrepresent

- Five prototype blocks only. No full legacy machine parity, SixNode, thermal/mechanical systems, inventories, external energy/fluid capabilities, automation integrations or legacy-world conversion.
- Full-cube wire/device graphics are temporary. The original OBJ bench is static; old GL11 animation was not ported wholesale.
- Maximum 64 active devices/level, 128 unknowns/island, 1024 core branches. The wire's unconnected arms also consume unknowns. Improve graph reduction before increasing limits blindly.
- Voltage bound is +/-1000 V for connected device state. Source/positive/negative semantics and fixed component values are defined in GridTopology, not user-configurable yet.
- Breaking/moving a device loses item-carried charge. Unloaded/non-ticking capacitor history is frozen; no offline catch-up is simulated.
- Topology is compared each tick and rebuilt when membership/orientation changes. This is a small-network prototype, not a performance-tuned large graph service. No GPU solver.
- The old public mutable SubSystem collections and some legacy solver utilities still exist. Guarded entry points and tested paths are not a proof of arbitrary addon/API misuse safety.
- Core island faults expose a generic fault latch, not a complete numerical diagnostic UI. Unsupported data remains on disk until reset; a reset intentionally discards that evidence.
- Vanilla build/interact permission checks are not a claim of support for every claim/protection mod. No custom C2S configuration protocol exists yet.
- Manual chunk lifecycle callback tests are not actual chunk boundary tests. Compressed NBT disk serialization is not a normal populated-world stop/start. Neither is two-client multiplayer.
- CI verifies a real packaged JAR through a separate ModDevGradle/NeoForge launch. Windows, Modrinth and ordinary packaged dedicated-server installations still need testing.

## Recommended next work, with acceptance criteria

1. **Persistence and chunk integration:** populated ordinary server stop/start, capacitor continuation across real chunk boundaries, unload/load at both ends, two clients editing a circuit, dimension changes. Assert no force-load, stale matrix references, duplicate ticks or charge resets. Preserve the last good snapshot on failure.
2. **Network scalability:** reduce dangling wire unknowns, update only affected islands, add explicit component budgets/configuration and diagnostics. Benchmark rather than merely raising the prototype ceiling.
3. **SixNode vertical slice:** six-face placement, ray-to-part hit selection, rotation/mirror, valid support, removal/drop, covers and collision shapes. Connect one source/cable/load family through this without breaking numerical tests.
4. **Real machine vertical slices:** server behavior, inventory/fluid contracts, menus, packets with authorization, persistence, recipe, model/animation and sound together. Then thermal/mechanical systems and selected upstream parity.
5. **Survival and integrations:** data generation, recipes/tags/ore worldgen, documentation and an explicit supported-mod matrix. Treat save migration and Fabric support as separate projects.

## Handoff hygiene

Keep changes on `port/1.21.1` or a child branch. The default legacy branch and 1.12 baseline were not updated by this work. The source ZIP is a complete modern project, not the full historical Git repository. To obtain history, fetch the remote branch. To use the ZIP as a new repository, initialize Git at the directory containing this file; its standalone CI workflow is already at `.github/workflows/ci.yml`.

All source and assets required by the current prototype are checked in. Runtime libraries are fetched by Gradle; the game itself is not redistributed in the ZIP. Test logs and an audit report are delivered separately from the clean project.
