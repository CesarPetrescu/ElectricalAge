# Companion compatibility (Minecraft 1.21.1 / NeoForge)

The compatibility suite runs the profiles below in GitHub Actions and blocks release publishing
on failures. Check the named assertions for the exact commit you are installing; a dependency
listed in a fuel configuration is not itself a compatibility result.

## Reproducible profiles

`tools/port/companions.lock.json` pins download URLs and SHA-512 checksums. Test mods are not
bundled in Electrical Age. Downloads go into isolated `run/compat-*` directories; the installer
refuses unexpected JARs and the runner refuses to overwrite an existing world.

| Profile | Added mods | Assertions |
|---|---|---|
| `fluids` | PneumaticCraft 8.2.23, Railcraft Reborn 1.2.10, Immersive Engineering 12.4.2-194, Pipez 1.2.31 | Matching fuel acceptance, simulation without tank mutation, turbine blade requirement, mechanical output, Pipez transfer from a PneumaticCraft tank, fuel/settings after restart |
| `opencomputers` | OpenComputers: Rebooted 1.9.4-3, ScalableCatsForce 3.3.3-build-15 | Native Adapter discovery and component callbacks, six-side settings, invalid arguments, persisted output/wireless settings; CC must be absent |
| `combined` | Both profiles, plus the project's pinned Create and CC: Tweaked | All the above, with both computer APIs available and no duplicate OC bridge |

Jade is supplied by the existing development runtime. Its client overlay and Create's mechanical
adapter behavior continue to be covered by the main smoke workflow; these new companion jobs
are dedicated-server tests, not visual tests of every external mod.

JSON and JUnit reports contain named assertions. Missing, incomplete, skipped or failing required
checks fail the report gate. The main CI workflow requires companion checks before publishing.

## OpenComputers API

The native component is `eln_probe`. Put an OpenComputers **Adapter** beside an ELN **Computer
Probe** and connect the Adapter to your computer's network. CC: Tweaked is not required.

```lua
local component = require("component")
local probe = component.eln_probe
print(probe.version())
probe.signalSetDir("XP", "out")
probe.signalSetOut("XP", 0.625)
print(probe.signalGetOut("XP"))
probe.wirelessSet("generator_enable", 1)
```

Sides are `XN`, `XP`, `YN`, `YP`, `ZN`, `ZP` (case-insensitive). Values must be finite numbers
between 0 and 1. All callbacks run on the Minecraft server thread.

Methods: `signalSetDir`, `signalGetDir`, `signalSetOut`, `signalGetOut`, `signalGetIn`,
`wirelessSet`, `wirelessGet`, `wirelessRemove`, `wirelessRemoveAll`, `version`.
`wirelessGet(channel[, aggregation])` accepts `bigger` (default) or `smaller` and returns
`nil, reason` when unavailable. Wireless discovery uses ELN's normal short-lived range cache.

This integration targets **OC: Rebooted**, not OC2 or every independent OpenComputers fork.
The pinned release needs ScalableCatsForce; newer development builds may bundle Scala instead.

## Running locally

Use a full JDK 21 (`JAVA_HOME`) and Python 3.10 or newer, then run from the repository root:

```sh
python script/test_companions.py
python script/run_companions.py fluids
python script/run_companions.py opencomputers
python script/run_companions.py combined
```

Run profiles sequentially; each dedicated server uses the normal Minecraft server port.
The runner downloads and verifies the locked JARs, prepares an isolated flat test world and
accepts the Minecraft EULA for that automated test server. It starts the server twice and
checks both reports. Archive an existing `run/compat-PROFILE/world` before repeating a fresh
test; the runner never deletes it. Logs are under `build/companion-artifacts`, with JSON/JUnit
results under `build/smoke-artifacts/contracts`.

For manual inspection after installation, use `./gradlew runClient -PcompanionProfile=combined -PwithCreate`
(Windows uses `.\gradlew.bat`). For OC-only inspection use
`-PcompanionProfile=opencomputers -PwithoutCc`. The regular development worlds are unchanged.

## What these tests do not prove

- Full OpenOS boot, a player-written Lua program, robot behavior or multiplayer GUI synchronization.
- Complete production chains inside the companion mods (oil refining or assembling a Railcraft boiler).
- Visual quality or client compatibility of every companion block.
- A clean launcher installation of the packaged release JAR; these scenarios use NeoForge's development server runs.
- Direct Mekanism chemical steam compatibility. ELN accepts standard fluid capabilities, not chemicals.
- FE importing. ELN's existing converter exports energy to FE; it does not accept FE into ELN.

Fuel aliases now accept matching registry paths from every installed namespace, so installing two
ethanol or biodiesel providers does not silently exclude the second one. Steam-turbine inputs
remain steam fluids; radial motors and gas turbines use configured light fuels/gases, not diesel
or biodiesel by default. Those heavy fuels belong in the fuel heat furnace.

## Regressions covered by this suite

- Turbine blades resolve through the port's individually registered items, not the removed metadata item type.
- An engine's internal signal pull-down does not count as an external throttle connection.
- Simulated fluid fills leave persisted state unchanged, including the cached fuel energy value.
- Computer Probe modes and output values are restored before connecting its simulation components.
- Wireless probe reads use the owning node's coordinates without recursively calling the transmitter getter.
- Installing multiple providers of ethanol or biodiesel keeps all matching fuels accepted.
