<p align="center">
  <img src="src/main/resources/assets/eln/logo.png" alt="Electrical Age — large-scale electrical simulation" width="600">
</p>

<h1 align="center">Electrical Age · Minecraft 1.21.1</h1>

<p align="center">
  Build circuits. Generate power. Drive machinery.<br>
  A community NeoForge port of the <a href="https://github.com/age-series/ElectricalAge">Age Series Electrical Age project</a>, maintained here on <code>main</code>.
</p>

<p align="center">
  <a href="https://github.com/CesarPetrescu/ElectricalAge/actions/workflows/ci.yml"><img src="https://github.com/CesarPetrescu/ElectricalAge/actions/workflows/ci.yml/badge.svg?branch=main" alt="CI status"></a>
  <img src="https://img.shields.io/badge/Minecraft-1.21.1-62B47A" alt="Minecraft 1.21.1">
  <img src="https://img.shields.io/badge/Loader-NeoForge-E89C54" alt="NeoForge">
  <img src="https://img.shields.io/badge/Java-21-669CC4" alt="Java 21">
</p>

<p align="center">
  <a href="https://github.com/CesarPetrescu/ElectricalAge/releases/tag/latest-1.21.1"><strong>Download the mod</strong></a> ·
  <a href="#installation">Installation</a> ·
  <a href="#build-from-source">Build from source</a> ·
  <a href="#contributing">Contribute</a> ·
  <a href="https://github.com/CesarPetrescu/ElectricalAge/issues">Report a bug</a>
</p>

> [!WARNING]
> **This is a development port, not a stable release.** Back up your worlds and start with a fresh test world. Saves from Electrical Age 1.7.10 and 1.12.2 are not migrated. This JAR targets **Minecraft 1.21.1 + NeoForge**, not Fabric or legacy Forge.

## What you can build

Electrical Age brings electrical simulation, wiring, lighting and industrial machinery to Minecraft:

- **Power networks:** cables, batteries, generators, turbines, transformers and meters.
- **Mechanical systems:** motors, generators, shafts, flywheels and large machines.
- **Automation:** processing machines, signal wiring and optional computer control.
- **Lighting:** lamps, sockets and floodlights for your builds. See the [bulb, wiring and floodlight guide](docs/lighting-tests.md).
- **Create integration:** optional 4 kW and 16 kW shaft adapters with gearing, load-dependent stress, overload protection and controlled braking.

The creative inventory has nine categories: **Wires & Cables**, **Signals & Control**, **Power**, **Mechanics**, **Processing**, **Lighting**, **Materials**, **Tools & Armor**, and **Creative Only**. Ground Cable belongs in Wires & Cables.

See the [Create adapter guide](docs/create-shaft-adapters.md) for connections on all three axes, gear selection and signal inputs. For worked circuits, explore the [electrical examples](docs/examples/README.md).

**In-game recipes and help:** press **P** to open the [Electrical Age guide](docs/in-game-guide.md). Search items, scroll through recipes and click ingredients to follow their crafting chains.

## Installation

### 1. Create the right Minecraft instance

Use **Minecraft 1.21.1**, **[NeoForge 21.1.249](https://neoforged.net/)** and **Java 21**. These are the versions targeted by this repository. In a modpack launcher, select the Minecraft version first, then NeoForge; with the standard launcher, install the matching NeoForge profile.

### 2. Download the mod and its required dependency

| Mod | Required? | Version used by this port | Purpose |
|---|---|---|---|
| [Electrical Age](https://github.com/CesarPetrescu/ElectricalAge/releases/tag/latest-1.21.1) | **Yes** | Latest passing 1.21.1 development build | The mod itself |
| [Kotlin for Forge — thedarkcolour](https://modrinth.com/mod/kotlin-for-forge/versions) | **Yes** | **5.12.0**, Minecraft 1.21.1 / NeoForge build | Required Kotlin language loader and runtime |
| [Create](https://modrinth.com/mod/create/versions) | Optional | **6.0.10**, Minecraft 1.21.1 / NeoForge build | Enables the Create shaft adapters and their recipes |
| [Jade](https://modrinth.com/mod/jade/versions) | Optional | **15.10.6+neoforge** | Block names and measurement overlays |
| [CC: Tweaked](https://modrinth.com/mod/cc-tweaked/versions) | Optional | **1.120.2**, Minecraft 1.21.1 / NeoForge build | Computer Probe integration |
| [OpenComputers: Rebooted](https://www.curseforge.com/minecraft/mc-mods/opencomputers-rebooted) | Optional | **1.9.4-3** + **ScalableCatsForce 3.3.3-build-15** | Native `eln_probe` component through an OC Adapter; CC is not needed |

Always select each dependency's **Minecraft 1.21.1 / NeoForge-compatible file**. Installing an unrelated newer version is not the same as installing the matching build. ELN works without Create, Jade or CC: Tweaked.

OpenComputers is optional too. Its pinned release additionally requires [ScalableCatsForce](https://www.curseforge.com/minecraft/mc-mods/scalable-cats-force), a Scala loader, **not** a replacement for Kotlin for Forge. Install both OC dependencies on clients and servers when using that integration.

> [!IMPORTANT]
> **Kotlin for Forge by thedarkcolour is required and is not bundled in the ELN JAR.**
> **KotlinLangForge by btwonion is a different mod and is not required by Electrical Age.** It does not replace this port's `kotlinforforge` loader. You do not need both Kotlin mods for ELN; other mods in your pack may have their own requirements.

**[Download ElectricalAge-1.21.1-latest.jar directly](https://github.com/CesarPetrescu/ElectricalAge/releases/download/latest-1.21.1/ElectricalAge-1.21.1-latest.jar)** — or open the [release page](https://github.com/CesarPetrescu/ElectricalAge/releases/tag/latest-1.21.1) for its source commit, passing CI run and SHA-256 checksum.

### 3. Put the JARs in `mods` and launch

Open your instance's folder and put **Electrical Age + Kotlin for Forge** in its `mods` directory. Add any optional integrations you want alongside them. Download the actual mod JAR, **not** GitHub's “Source code” ZIP or a `-sources.jar`.

Launch the NeoForge instance, confirm Electrical Age appears in the Mods list, and create a fresh world. For multiplayer, install Electrical Age and Kotlin for Forge on **both the client and dedicated server**; keep gameplay mod versions in sync. Back up worlds before replacing an older ELN JAR, and avoid leaving two ELN versions in the same folder.

<details>
<summary><strong>Installation troubleshooting</strong></summary>

- **Missing `kotlinforforge` / Kotlin loader:** install the required Kotlin for Forge file above, not KotlinLangForge.
- **Incompatible loader or Minecraft version:** check that the instance and every downloaded file target 1.21.1 / NeoForge.
- **No Create adapters:** install the matching Create build, restart Minecraft, and check **ELN — Mechanics**.
- **Crash after an update:** keep your backup, note the exact mod versions, and attach the relevant `logs/latest.log` and crash report to a [bug report](https://github.com/CesarPetrescu/ElectricalAge/issues). Do not delete your world to diagnose a crash.

</details>

## Companion mods and fuels

| Companion | How it connects to Electrical Age |
|---|---|
| [PneumaticCraft: Repressurized](https://modrinth.com/mod/pneumaticcraft-repressurized) | Gasoline, kerosene, LPG and ethanol for gas turbines/radial motors; diesel and biodiesel for the fuel heat furnace |
| [Railcraft Reborn](https://modrinth.com/mod/railcraft-reborn) | Standard steam fluid for steam turbines; creosote for the fuel heat furnace |
| [Immersive Engineering](https://modrinth.com/mod/immersiveengineering) | Ethanol for gas turbines/radial motors; biodiesel for the fuel heat furnace |
| [Pipez](https://modrinth.com/mod/pipez) | Fluid pipes feeding ELN's NeoForge fluid ports |

These are optional companions, not bundled dependencies. See the [compatibility guide](docs/companion-compatibility.md) for pinned versions, native OpenComputers examples, test coverage and limitations. Fuel names in a configuration file alone are **not** proof that an old mod has a 1.21.1 release. Mekanism chemical steam is not a standard fluid and has no direct ELN bridge here.

## Build from source

You need **Git**, an internet connection and a **full JDK 21**. A game launcher's Java runtime/JRE alone is not enough to compile. Set `JAVA_HOME` to the JDK installation directory and use the included Gradle wrapper; a separate Gradle installation is unnecessary.

```sh
git clone --branch main https://github.com/CesarPetrescu/ElectricalAge.git
cd ElectricalAge
```

**Windows — PowerShell**

```powershell
java -version
javac -version
.\gradlew.bat --version
.\gradlew.bat build
```

**Linux / macOS**

```sh
java -version
javac -version
./gradlew --version
./gradlew build
```

Check that the compiler and Gradle JVM use **Java 21**. The first build downloads Gradle, Minecraft tooling and dependencies, so it takes longer than later builds.

The compiled mod is **`build/libs/ElectricalAge-3.0.0-port.jar`**. `build` also runs unit tests. Install that JAR like the release JAR, including Kotlin for Forge separately. The rolling release renames the published artifact to `ElectricalAge-1.21.1-latest.jar`.

### Useful development commands

Run these from the repository root. On Windows, replace `./gradlew` with `.\gradlew.bat`.

| Command | What it does |
|---|---|
| `./gradlew runClient` | Launch a development client; Gradle supplies its development dependencies |
| `./gradlew runClient -PwithCreate` | Launch with Create enabled for adapter development |
| `./gradlew runServer` | Launch a development dedicated server; review and accept the Minecraft EULA in `run/server/eula.txt` yourself |
| `./gradlew test` | Run the unit tests through NeoForge's test launcher |
| `./gradlew benchmarkTest` | Run the separate simulation benchmark suite |
| `./gradlew generateLangFiles` | Regenerate translation keys after adding translatable strings |
| `./gradlew runData` | Regenerate recipes, tags, loot, models and worldgen data; review the resulting diff |
| `./gradlew portStatus` | Show which source files the 1.21.1 build includes |

Development files live in `run/client/` and `run/server/`. Create is added to development runs with `-PwithCreate`; that flag is **not** needed to compile adapter support into the distributable JAR.

## Testing and automated releases

[GitHub Actions](https://github.com/CesarPetrescu/ElectricalAge/actions/workflows/ci.yml) runs build/unit tests, simulation benchmarks and separate **standalone / Create-enabled** game jobs.

Three additional **companion compatibility** jobs load the actual pinned mods: **fluids**, **OpenComputers without CC**, and **combined**. Named assertions verify fuel behavior, pipe transfer and computer callbacks, followed by a separate-JVM restart. Missing or skipped required checks fail CI, and release publishing waits for these jobs too.

| When | What runs |
|---|---|
| Pushes and pull requests | Build, unit tests, benchmarks, strict descriptor placement/removal contracts, saved-world restart checks and targeted client/GUI checks |
| Nightly or manual **extended** run | The above, plus two additional restart replays and a named per-block screenshot gallery |
| Passing eligible `main` build | Publish the build artifact and checksum to the rolling `latest-1.21.1` release |

Per-block results are available as **JSON, JUnit XML and a GitHub job summary**. Logs and screenshots upload even on failure. A missing or failing required contract report blocks publication; skipped checks remain visibly untested.

**Coverage is not complete.** Generic placement does not prove every machine's behavior, inventory conservation or visual correctness. See [what is tested and what remains](docs/block-contracts.md), and the [Linux headless testing guide](tools/port/headless.md) for running the full suite locally. Only use disposable test worlds: smoke runs intentionally replace test saves and break test machines.

The rolling download URL stays the same as newer passing builds replace it. Failed checks leave the previous release available. Actions artifacts can appear before the whole run passes, so **use the release page for the latest fully gated build**. Nightly, extended, pull-request and non-`main` runs do not publish releases.

## Contributing

Bug reports, regression tests, translations, documentation, models and code improvements are welcome.

1. **Report or discuss the change** in [this repository's issue tracker](https://github.com/CesarPetrescu/ElectricalAge/issues), especially for large features.
2. **Fork this repository**, branch from `main`, and keep the change focused.
3. **Add tests and verify your changes.** Run `build`; include in-game screenshots for model/GUI work and relevant smoke checks for gameplay changes.
4. **Open a pull request against `CesarPetrescu/ElectricalAge:main`**, explaining the change, how you tested it and any remaining limitations.

Read the [contribution guide](CONTRIBUTING.md) for setup, translation rules, registry-ID safety and a PR checklist. Report bugs in **this port** here rather than sending port-specific issues to the upstream project.

## Documentation and credits

- [Create shaft adapters](docs/create-shaft-adapters.md) — connections, gearing, braking and controls.
- [Generators, motors and batteries](docs/power-machines.md) — polarity, return paths, reverse operation, charge limits and regression tests.
- [Test coverage](docs/block-contracts.md) — per-block reports, nightly runs and known gaps.
- [Circuit examples](docs/examples/README.md) — worked electrical examples.
- [Porting notes](PORT-1.21.md) — historical migration decisions and known differences.
- [Credits](docs/credits.md) — the people and projects behind Electrical Age.

This port builds on the work of the **Age Series Electrical Age maintainers and contributors**. Source code is licensed under **LGPL v3.0**; graphics and models have separate **CC BY-NC-SA 3.0** terms and asset-specific exceptions. See [LICENSE.md](LICENSE.md) and the notices alongside individual assets.
- [Wire production and resistance](docs/wire-production.md) — roller/insulator/combiner recipes, multicore cables, spool lengths and electrical properties.
