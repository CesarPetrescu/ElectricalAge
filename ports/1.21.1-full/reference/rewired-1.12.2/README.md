# Electrical Age: Re-Wired

[![CI Build](https://github.com/brambora69123/electrical-age-rewired/actions/workflows/ci.yml/badge.svg)](https://github.com/brambora69123/electrical-age-rewired/actions/workflows/ci.yml)

Electrical Age: Re-Wired is a 1.12.2 port of the mod Electrical Age, an attempt to bring it to more modern version of
Minecraft. But here's a brief description:<br>
Electrical Age is a Minecraft Mod offering the ability to perform large-scale in-game electrical simulations.

We don't have a Wiki yet, but you can join our Discord server!

<a href="https://discord.gg/USruYk7pjW">
<img src="https://img.shields.io/discord/1407039747397779566?style=for-the-badge" alt="Discord">
</a>

## Note on this port
Although we are already through the way with a lot of stuff, like the sim, it's largely unfinished. Expect bugs, missing
features and functionality.

## How to get started

**The newest Electrical Age is Minecraft 1.12.2 compatible only. Forge/CleanroomMC is needed. (works on both)**
<br>
Note: we recommend that you use [CleanroomMC](https://cleanroommc.com/) instead of Forge, it includes many improvements to forge, and
allows for newer Java versions, like Java 21-25, and uses LWJGL3 (improved input handling, performance, etc.).

### Dependencies

**Required:**
- [CoFH Core](https://www.curseforge.com/minecraft/mc-mods/cofh-core) - Required for energy integration

**Optional:**
- [Hwyla/WAILA](https://www.curseforge.com/minecraft/mc-mods/hwyla) - For tooltip information
- [ComputerCraft](https://www.curseforge.com/minecraft/mc-mods/computercraft) - For computer integration
- [OpenComputers](https://www.curseforge.com/minecraft/mc-mods/opencomputers) - For computer integration

1. Download the [last mod release](https://github.com/brambora69123/electrical-age-rewired/releases).
2. Install CoFH Core (and other optional mods as desired).
3. Launch Minecraft with your favorite launcher, or the official Minecraft launcher. PrismLauncher highly recommended.


tutorial worlds WIP

### Building from source

This option is primarily for developers. If you take it, make sure to join our Discord first; see the chat button above.

To build Electrical Age, you need to already have Git and the Java development kit installed. You should also have IDEA, which is what we recommend for working on it. You don't have to be running Linux, but it helps; you can typically install git and the JDK with your package manager, if they don't come preinstalled. Windows users are on your own.

Once the prerequisites are in place, run these commands:

```sh
git clone https://github.com/brambora69123/electrical-age-rewired.git
cd ElectricalAge
git checkout <branch you want to work on>  # Optional. The main development branch is also the default.
gradle setupDecompWorkspace
gradle runClient  # Test
```

For more information, see Discord and [CONTRIBUTING.md](CONTRIBUTING.md).

## Contributing

We appreciate any help from the community to improve the mod, but please follow the pull request and issue guidelines. You can find the basic guidelines whenever you open one. For more information, go [here](./CONTRIBUTING.md).

## ABOUT

Here are some highlighted features:

A better simulation
> Electrical simulation with resistive and capacitive effects. Behavior similar to those of real life objects.

Multiple electrical machines and components
> Furnaces, Solar panels, Wind turbines, Batteries, Capacitors, ...

Break the cube
> Cables, sensors, actuators, alarms, etc. can be placed on each face (outer and inner) of a cube, which allows a significant reduction of the consumed space by electrical installations.

Night-lighting revisited
> Lamps, switches, captors, ...

Small and big electrical consumers
> From lamps and electrical furnaces to miners and transporters...

Incredible tools
> XRay scanner, flashlight, portable mining drill...

Interoperability
> Old redstone circuits can be exploited with electrical <-> redstone converters.

Game lifetime/complexity extended
> A consequent list of new raw materials and items...

## CURRENT STATE

Electrical Age: Re-Wired is still in **pre-alpha**.
Use at your own risk and make map backups on a regular basis.

## MAIN DEVELOPERS

- **brambora69123** (Mod porting)

Special thanks to former developers:
- **Dolu1990** (Code guru, concepts, some 3D models)
- **Svein Ove Aas, aka. Baughn** (Code, some 3D models, concepts)
- **cm0x4D** (Sound engineer, code and 3D models/texturing, concepts)
- **lambdaShade** (3D models/texturing/graphics maestro, concepts, some sounds and lines of code)
- **metc** (Website/Wiki webmaster)

## MAIN CONTRIBUTORS

Code/models:

- **bloxgate** (some tweaks)
- **DrummerMC** (bug fix)
- **ltouroumov** (bug fix)
- **meelock** (typo fix)
- **Sukasa** (code enhancement)
- **DrummingFish** (GUI text parsing, cleaning/refactoring, some tweaks)
- **lolmegaxde1** (lots of work on the 1.10 port)

Languages:

- **bomdia** (it_IT)
- **KLsz, aneBlack and Ahtsm** (zh_CN)
- **dcbrwn** (ru_RU)
- **XxCoolGamesxX** (es_ES - deprecated)


Full list of contributors is [available here](https://github.com/brambora69123/electrical-age-rewired/graphs/contributors).

## LICENSE

The source code of this mod is licensed under the LGPL V3.0 licence. See http://www.gnu.org/copyleft/lesser.html for more information.

Most graphics and all 3D models are licensed under the Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-nc-sa/3.0/. These should all be attributed to the Electrical Age team, with the following exceptions:

- src/main/resources/assets/eln/textures/blocks/2x3solarpanel.png
  Designed by [Luis Prado](https://thenounproject.com/Luis/).
- src/main/resources/assets/eln/textures/blocks/scanner.png
  Designed by [Creative Stall](https://thenounproject.com/creativestall/).
- src/main/resources/assets/eln/textures/items/
Designed by Guillermo Guso from the Noun Project

Some graphics are public domain. These are:

- src/main/resources/assets/eln/textures/blocks/smallsolarpanel.png
- src/main/resources/assets/eln/textures/blocks/smallrotatingsolarpanel.png
- src/main/resources/assets/eln/textures/blocks/2x3rotatingsolarpanel.png

![logo](https://raw.githubusercontent.com/Electrical-Age/electrical-age.github.io/master/assets/favicon.ico)
