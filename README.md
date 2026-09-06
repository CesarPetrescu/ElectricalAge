# Electrical Age — Minecraft 1.21.1

[![CI](https://github.com/CesarPetrescu/ElectricalAge/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/CesarPetrescu/ElectricalAge/actions/workflows/ci.yml)

Electrical Age brings electrical simulation, wiring, lighting, and industrial machines to Minecraft. This repository contains CesarPetrescu's **Minecraft 1.21.1 / NeoForge port** on `main`, based on the [Age Series Electrical Age project](https://github.com/age-series/ElectricalAge).

The port includes generators and turbines, shaft networks, large motors and generators, batteries, meters, lamps, and processing machines, with optional Jade and CC: Tweaked integrations. See the [port status and known differences](PORT-1.21.md) for implemented features, excluded legacy integrations, and verification evidence.

## Download and install

**Required dependency: [Kotlin for Forge by thedarkcolour](https://modrinth.com/mod/kotlin-for-forge/versions), version 5.12.0 for Minecraft 1.21.1 / NeoForge.** Install it alongside Electrical Age on both the client and dedicated server. Electrical Age's JAR does not include this dependency.

**KotlinLangForge by btwonion is a different mod and is not required by Electrical Age.** It does not replace the `kotlinforforge` loader this port declares. You do not need both Kotlin mods for Electrical Age; other mods in your pack may have their own requirements.

Get the [latest tested 1.21.1 development build](https://github.com/CesarPetrescu/ElectricalAge/releases/tag/latest-1.21.1), or [download the mod JAR directly](https://github.com/CesarPetrescu/ElectricalAge/releases/download/latest-1.21.1/ElectricalAge-1.21.1-latest.jar).

1. Create a **Minecraft 1.21.1** instance with **NeoForge 21.1.249** and **Java 21**.
2. Download **[Kotlin for Forge 5.12.0 by thedarkcolour](https://modrinth.com/mod/kotlin-for-forge/versions)**, using its Minecraft 1.21.1 / NeoForge-compatible build, and put its JAR in the instance's `mods` folder.
3. Put `ElectricalAge-1.21.1-latest.jar` in the same `mods` folder. Install it and Kotlin for Forge on both the client and dedicated server when playing multiplayer.
4. Start with a fresh world. Saves from the 1.7.10 and 1.12.2 versions are not migrated by this port.

These are development prereleases. Keep backups of worlds used for testing. Jade and CC: Tweaked are optional; use their Minecraft 1.21.1 / NeoForge builds if you want their integrations. Development dependency versions are recorded in [gradle.properties](gradle.properties).

## Build and test

Use JDK 21 and the included Gradle wrapper. On Linux/macOS:

```sh
./gradlew build
./gradlew benchmarkTest
```

On Windows PowerShell:

```powershell
.\gradlew.bat build
.\gradlew.bat benchmarkTest
```

`build` compiles the mod, runs the unit tests through NeoForge's JUnit launcher, and produces JARs in `build/libs/`. `benchmarkTest` runs the separate benchmark and profiling suite.

The [headless test guide](tools/port/headless.md) explains the Linux server/restart/client smoke suite (`tools/port/smoke.sh`), including its software-rendered screenshots. See [docs/port](docs/port) for saved visual evidence.

## Automated builds and releases

[GitHub Actions](https://github.com/CesarPetrescu/ElectricalAge/actions/workflows/ci.yml) checks pushes to `main` and `port/**`, tags, pull requests, and manual runs:

- **Build and unit tests:** compile the mod and upload the JAR and test reports.
- **MNA benchmarks:** run benchmark tests and upload statistics and reports.
- **In-world smoke tests:** place machines and circuits, verify a saved-world restart, run the client, and upload screenshots and logs.
- **Publish latest mod:** after all three jobs succeed on the current `main` commit, update the `latest-1.21.1` prerelease with the tested JAR and its SHA-256 checksum. Pull requests and other branches do not publish releases.

The release notes identify the exact source commit and CI run. The download URL stays the same as newer passing builds replace it. Failed checks leave the previous release available. Individual build artifacts can also be downloaded from an Actions run; those artifacts may exist before the full suite finishes.

To run the pipeline manually, open **Actions → CI → Run workflow** and select `main`. Publishing uses GitHub's automatic workflow token; no personal access token is needed.

## Credits and license

This port builds on Electrical Age and the work of the Age Series maintainers and contributors. See [credits](docs/credits.md) and the [upstream contributor history](https://github.com/age-series/ElectricalAge/graphs/contributors).

Source and asset licensing is described in [LICENSE.md](LICENSE.md), with additional notices alongside individual assets.
