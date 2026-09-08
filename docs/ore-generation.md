# Ore generation in the 1.21.1 port

ELN uses normal Overworld biome generation for **copper, lead, tungsten and cinnabar**. These are ELN's ore blocks; vanilla copper is separate. Each ore defaults to enabled. Generation adds ore only while new chunks are generated: updating the mod or changing a setting does **not** retrofit already explored chunks.

| Ore | Attempts per chunk | Feature vein size | Uniform origin Y range |
|---|---:|---:|---:|
| Copper | 30 | 8 | 0–80 |
| Lead | 8 | 6 | 0–24 |
| Tungsten | 6 | 6 | 0–32 |
| Cinnabar | 3 | 6 | 0–32 |

These retain the port's existing spawn numbers. Attempts and vein size are not guaranteed block counts: terrain, caves and vein overlap affect the result. A vein can extend a little beyond its origin's Y range. Both stone and deepslate are valid replacement materials, but there are not separate deepslate-textured ELN ore variants. This is not a new deep-underground distribution rebalance.

## Configuration

On the dedicated server (or the single-player installation), edit `config/eln/eln.json` and restart:

```json
{
  "worldgen": {
    "ores": {
      "copper": { "enabled": true },
      "lead": { "enabled": true },
      "tungsten": { "enabled": true },
      "cinnabar": { "enabled": true }
    }
  }
}
```

Merge this section into your existing file; do not replace unrelated settings. Use the names **without `_ore`** in config. Setting an ore to `false` prevents future generation, without removing existing blocks.

Previously the shipped biome modifiers read incorrect `_ore` config paths, while the cinnabar feature had zero attempts baked in. The registration/data now keep positive base generation rates and use the canonical public config switches at biome assembly. Cinnabar can therefore generate in newly explored terrain when enabled. Registry IDs and existing ores are unchanged.

## What CI tests

The old generic smoke test uses superflat and skips its optional ore count. It was **not evidence that natural ore generation worked**.

The dedicated `Natural ore generation contracts` jobs test the **exact packaged release JAR** in a clean Minecraft 1.21.1 / NeoForge installation, without launching Minecraft on the developer's PC:

- Two fixed normal-world seeds with default settings, plus all-disabled and mixed per-ore configurations.
- A census of 16 naturally generated Overworld chunks per initial world, scanning their full vertical extent. No ore blocks are manually placed, and no feature is directly invoked by the test.
- A named presence/absence and height-envelope assertion for **every registered ELN ore**, not just “some ore exists.” Newly added ore types require explicit coverage.
- Live biome attachment checks: enabled features appear exactly once in Overworld-tagged biomes and not in other biomes. [NeoForge's biome-modifier model](https://docs.neoforged.net/docs/1.21.1/worldgen/biomemodifier/) supplies this generation stage.
- Four normal Nether chunks and four End chunks with no ELN ores.
- A fresh server process loads the same saved world; per-ore counts, height ranges and hashes of all ore positions must match. It also generates another 16 distant Overworld chunks after restart.
- Packaged JSON/descriptor consistency tests, strict missing/skipped/duplicate-result rejection, JAR checksums and independent process identities.

Artifacts named `ore-worldgen-<profile>` include JSON/JUnit results, per-region counts and heights, position hashes, effective config and both server logs. A failed job blocks the rolling release. Failures preserve the synthetic world when available and capture thread dumps on runtime/shutdown timeout.

This is a deterministic regression sample, not a statistical guarantee about every seed, biome, custom dimension, terrain mod or datapack. ELN's default modifier targets `#minecraft:is_overworld` biomes; it does not promise compatibility with arbitrary replacement world generators. Back up worlds and explore **new chunks** when checking an update in-game.
