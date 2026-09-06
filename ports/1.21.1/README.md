# Electrical Age — 1.21.1 connected-circuit prototype

**Version 0.2.0-port.2. This is an early port, not the complete historical mod.**

Target: **Minecraft 1.21.1 / NeoForge 21.1.249 / Java 21**. ModDevGradle 2.0.146 and Gradle 9.2.1 are pinned. Repository: `CesarPetrescu/ElectricalAge`, branch `port/1.21.1`, module `ports/1.21.1/`. In the standalone project ZIP, this file and `build.gradle` are at the project root. Open **this** Gradle project, not the legacy repository-root build.

## What works in this milestone

Five registered blocks, real inherited MNA electrical equations, server-authoritative ticking, bounded connected circuits, saved capacitor history, fault latching, resource validation and real Minecraft GameTests. The circuit bench retains adapted original OBJ/PNG artwork. The four connected devices intentionally use simple full-cube prototype models and vanilla textures; they are not finished legacy machine or SixNode models.

| Block ID | Behavior |
|---|---|
| `eln:circuit_bench` | Independent internal 10 V / 10 ohm / 1 F RC integration fixture |
| `eln:voltage_source` | 10 V source with 1 ohm internal resistance; toggle on/off |
| `eln:resistive_wire` | Connects all six faces through a resistive center; 0.05 ohm per arm |
| `eln:resistive_load` | 10 ohm load; light level 12 when calculated power exceeds 0.1 W |
| `eln:capacitor` | 1 F capacitor; remembers differential voltage across topology rebuilds |

Source, load and capacitor have **two opposite terminals**: their facing side is positive, the opposite side negative. Other faces do not connect. The wire is six-way. A complete return path is required; independent circuits do not share a hidden ground. Turning a source off sets its EMF to zero while retaining its 1 ohm series resistance, so a capacitor can discharge through it. The timestep is 0.05 seconds per server tick, not wall-clock catch-up during lag.

Empty-hand right-click toggles a source, toggles the independent bench, or inspects other devices. Sneak-right-click resets the selected device and clears its fault latch. **Breaking/replacing a capacitor or bench resets charge**; item drops do not preserve it. Do not mix this JAR with the old Forge 1.12.2 build or open old ELN worlds.

## Build and run

Install a **JDK 21**, not just a JRE, and set `JAVA_HOME`/`PATH`. Import the directory containing this README as a Gradle project. The wrapper downloads Gradle and dependencies; the full build needs Internet access on first use.

Linux/macOS:
```sh
./gradlew :sim-core:cleanTest :sim-core:test build --no-build-cache
./gradlew runGameTestServer
./gradlew runClient
```

Windows PowerShell:
```powershell
.\gradlew.bat :sim-core:cleanTest :sim-core:test build --no-build-cache
.\gradlew.bat runGameTestServer
.\gradlew.bat runClient
```

The regular game JAR is `build/libs/eln-1.21.1-0.2.0-port.2.jar`. Do not install the sources JAR. Use a separate NeoForge 1.21.1 / Java 21 launcher profile and a disposable new world. `runServer` is also configured; its operator must read and accept Minecraft's EULA themselves in `run/server/eula.txt` before ordinary dedicated-server use.

For a dependency-free core check (does **not** compile the game adapter):
```sh
python3 tools/test_offline.py
python3 -m unittest discover -s tools -p 'test_*.py' -v
```
On Windows use `py -3.11` or another Python 3.11+ interpreter instead of `python3`. Offline Java tests use the actual core with no Minecraft stubs; use Gradle and GameTests for game integration.

## A reproducible circuit

Use a clear 3 x 3 area in a **new creative test world**. At one height, viewed from above, with east to the right and south downward:

```text
wire — SOURCE (facing east) — wire
 |                              |
wire            air            wire
 |                              |
wire — LOAD   (facing east) — wire
```

There are six wire blocks. Through each wire the path crosses two arms (0.1 ohm), giving total resistance `1 + 6*0.1 + 10 = 11.6 ohms`. Expect load current approximately **0.862069 A** and front-to-back load voltage **8.620690 V**. Removing a return-path wire should extinguish the load; replacing it should restore the current.

For exact coordinates, the following commands intentionally place eight blocks at Y=100 near the origin. Only run them in a disposable, clear test area:
```mcfunction
/setblock 1 100 0 eln:voltage_source[facing=east]
/setblock 1 100 2 eln:resistive_load[facing=east]
/setblock 0 100 0 eln:resistive_wire
/setblock 0 100 1 eln:resistive_wire
/setblock 0 100 2 eln:resistive_wire
/setblock 2 100 0 eln:resistive_wire
/setblock 2 100 1 eln:resistive_wire
/setblock 2 100 2 eln:resistive_wire
```
Replace the load with `eln:capacitor[facing=east]` to observe charging, then toggle the source to observe discharge. The original `circuit_bench` remains independent and cannot be connected to these devices.

## Safety boundaries and unfinished work

The prototype enforces **64 active connected devices per server level** and **128 MNA unknowns per connected island**. These limits are guards, not performance claims; a large connected graph can hit the matrix limit before reaching 64 blocks. Exceeding a limit or numerical fault freezes affected devices until reset. Only vanilla-ticking devices participate; no chunks are force-loaded. Capacitor voltage is retained while inactive, and offline simulation is not performed.

Ordinary populated-world disk restart, actual chunk load/unload transitions, two-client play, third-party automation and production modpack performance still need independent testing. Tests of lifecycle callbacks and compressed NBT are narrower than those guarantees. Full SixNode interactions, machine catalogue, thermal/mechanical simulation, survival progression, advanced rendering and legacy-world migration remain unported.

Read **HANDOFF.md** for implementation ownership, exact verification boundaries and next tasks. `docs/PORTING-PLAN.md` is the broader migration plan, not a completed-features list. The module includes a standalone `.github/workflows/ci.yml`; the parent repository runs `.github/workflows/eln-1.21.1.yml` instead. Both require actual tests and load the distributable JAR, not merely an IDE classpath.

## Provenance and licensing

Inherited code: Electrical Age contributors, LGPL v3 as declared by Re-Wired. Existing notices are retained. New port code is LGPL-3.0-only. The inherited voltage-source artwork is Electrical Age team artwork under CC BY-NC-SA 3.0, uniformly fitted/translated with adapted material paths; the PNG is unchanged. See LICENSE-legacy.md, PROVENANCE.json and ASSET-MIGRATION.json. `PROVENANCE.json` records the **initial extraction**, not current edited-file checksums. The historical extraction script is disabled to prevent it overwriting subsequent fixes; it is never needed for building.
