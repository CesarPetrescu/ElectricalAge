# 1.21.1 port: requirements, decisions and acceptance gates

## Scope and source boundaries

The target is exactly Minecraft 1.21.1, not a moving latest version. Start with NeoForge 21.1.249, Java 21, ModDevGradle 2.0.146 and Gradle 9.2.1, pinned from the official 1.21.1 MDK at 70d335c962ee8a773b38fb0690c7e7f30d1bafa6. No Fabric or contemporary Forge compatibility is claimed for the resulting jar.

The 1.12.2 Re-Wired adaptation remains useful as a behavior and architecture reference, but is not a reusable 1.21.1 platform shell. Audited numerical fixes and regressions are carried into the extracted core. The remaining inventory/network/save fixes in the 1.12 audit must be deliberately carried into new adapters, not assumed present because a branch exists. Upstream age-series stable 1.24.8 and current main remain separate comparison targets; the newer main must not silently define a moving parity goal.

## Alternatives considered

1. NeoForge-only first: preserves a Forge-like extension model and supplies registries, block entities, transfer capabilities, baked OBJ support and GameTests. Lowest adapter burden for this project; one loader initially.
2. Fabric-first: possible, but requires another set of loader/platform bindings and different integration choices. The electrical core can still be reused; this is not the selected implementation.
3. Multi-loader immediately: maximizes potential reach but doubles initial lifecycle/render/integration validation before one port is dependable. Defer until the core boundary and first gameplay slices are stable.

Decision: one NeoForge module plus a Minecraft-independent simulation module. Do not rewrite electrical behavior into a generic FE counter. Energy compatibility belongs at an explicitly bounded conversion adapter.

## Major migration work

| Legacy area | 1.21.1 requirement | Acceptance gate |
|---|---|---|
| RFG, MCP and Java 8 build | ModDevGradle, official mappings, Java 21 and new mod metadata | Clean compile, core tests and packaged-jar validation |
| SharedItem damage/metadata identities | Explicit registry IDs; typed data components for variable item state | Complete old descriptor-to-new-ID manifest; no item identity depends on raw damage |
| SixNode/TransparentNode TileEntities | BlockEntity, registry-aware NBT, server-owned state, new tick and interaction hooks | All six orientations, selection/collision, placement/break and populated reload tests |
| GL11 immediate mode/display lists | Static baked OBJ geometry; moving parts rendered with PoseStack/vertex consumers/BER | Same artwork, correct pivots, lights, transparency and resource reload behavior |
| Old packets and channels | CustomPacketPayload/StreamCodec where vanilla synchronization is insufficient | Bounds, permissions, thread ownership, decode/encode and two-client tests |
| OreDictionary and imperative recipes | Tags, current recipe serializers/codecs, JSON/datagen, loot and worldgen | Every survival item reachable; no missing or invalid resources |
| Legacy inventory/fluid/energy interfaces | NeoForge capabilities, invalidation/caching and modern menus/screens | Sided transfer, partial output, shift-click, no-duplication and third-party tests |
| Global world/simulator managers | Explicit server/level ownership and chunk-aware circuit topology | Repeated split/merge/unload/reload without stale nodes or leaked state |
| Raw old save data | Versioned per-device schemas; optional explicit converter later | Reject/preserve unknown data; never silently load a legacy world as compatible |

Static objects can reuse their OBJ meshes. Dynamic machines still need their rendering code ported. NeoForge's OBJ loader does not recreate the old Java animation methods, pivots, halos or special lighting automatically.

The first module uses Java 21 bytecode. Minecraft-independent does not mean it can already be dropped into the old Java 8 build. Sharing the resulting library with 1.12.2 would require a separately designed compatible toolchain/API boundary.

## First implemented slice

A circuit test bench is an integration fixture, NOT the complete legacy voltage-source machine. It runs the inherited MNA simulator for a 10 V / 10 ohm / 1 F RC circuit at 0.05 s per simulation tick. It supports charging, discharging, live measurements, schema-checked NBT, client measurement snapshots and ownership cleanup/reinitialization. Its model uses the existing voltage-source artwork through the new OBJ path. Breaking/replacing it resets charge; it has no external cable, FE, item or fluid capability.

The 446 selected inherited numerical cases exclude the nine old packet-target checks: those included 1.12-specific assumptions such as a 0..255 world-height range. They must not be treated as correct checks for a 1.21.1 world. Ten new pure-core bench tests and real GameTests cover the new adapters.

## Implementation sequence after the bench

### M1: real connected electrical network

Build a per-level manager around inherited RootSystem and connection/state types. Separate durable device parameters from reconstructible graph/matrix caches. Start with a voltage source, resistive cable, load and capacitor. Rebuild affected components only; never allocate a dense matrix for every world block. Use loaded chunks only and never force-load a target because of a packet.

Gate: source-load currents and voltage drops agree with analytical circuits, parallel components accumulate correctly, loop edits and repeated splits/merges remain stable, unloading either endpoint removes its contribution, and reload restores the stored charge without duplicating energy.

### M2: SixNode interaction and rendering

Retain the useful face-mounted container concept. Store parts with explicit descriptor identities/orientations rather than historical damage values. Implement union collision/selection shapes and reliable hit-to-part selection. Cache shapes and static baked geometry. Use dedicated moving-part render paths only where necessary.

Gate: six faces, rotations, support removal, covers, placement/replacement, stacked interactions, correct drops and two-player edits. Add screenshot/resource-reload tests for the chosen render approach.

### M3: machinery and control

Port complete vertical slices: descriptor, behavior, inventory/fluids, menu, screen, synchronization, recipe, model, sound and tooltip. Carry forward audited output-transaction and access-control behavior. Preserve the electrical solver rather than substituting RF/FE-only machinery.

Gate: machines work through save/reload and automation, output cannot partially commit, container actions cannot duplicate items, and remote or unauthorized packets cannot modify state.

### M4: thermal/mechanical and modern upstream parity

Bring thermal, steam, shafts, clutches, motors, converters, signal buses and other upstream features in dependency order with their own reference circuits and tests. Do not wholesale merge Java-to-Kotlin moves and 1.7 API calls over the modern shell. Re-evaluate solver failure propagation and ill-conditioned network behavior separately from the existing finite numerical tests.

### M5: survival and release hardening

Complete tags, crafting/processing recipes, world generation, loot, advancements/documentation, translations and supported integration versions. Profile large networks, chunk churn, long-lived worlds and multiplayer. Keep legacy world conversion an explicit, independently tested project rather than assuming vanilla data fixing will translate this mod's custom descriptors.

## CI and evidence rules

A successful build alone is not a gameplay claim. Record the exact code commit and jar hash. Require nonzero named JUnit and GameTest counts. Test development loading and the actual distributable jar separately: the packaged-client run uses an empty source set with no local mods and loads the jar from a disposable mods directory. The client checks real block and item baked geometry and verifies that development GameTest classes are absent from the packaged runtime. Preserve failed-run logs, then rerun after corrections.

The first client probe intentionally requires the title screen, not merely an OpenGL/version log. Headless CI must disable the first-launch onboarding dialog through its disposable options file, not weaken the readiness criterion. Normal developer run/client saves are not deleted by the smoke-test helper.

Ordinary dedicated server testing requires the operator's EULA acceptance. GameTestServer can validate in-game server behavior, but must not be described as an ordinary populated-world restart or two-client playtest. Neither headless title-screen probes nor a nonmissing mesh prove that all artwork looks correct to a human.

## Primary references

- https://docs.neoforged.net/docs/1.21.1/gettingstarted/
- https://docs.neoforged.net/docs/1.21.1/blockentities/
- https://docs.neoforged.net/docs/1.21.1/blockentities/ber/
- https://docs.neoforged.net/docs/1.21.1/items/datacomponents/
- https://docs.neoforged.net/docs/1.21.1/networking/payload/
- https://docs.neoforged.net/docs/1.21.1/resources/client/models/modelloaders/
- https://docs.neoforged.net/docs/1.21.1/resources/server/tags/
- https://docs.neoforged.net/docs/1.21.1/misc/gametest/
- https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle/tree/70d335c962ee8a773b38fb0690c7e7f30d1bafa6
- https://github.com/neoforged/ModDevGradle
