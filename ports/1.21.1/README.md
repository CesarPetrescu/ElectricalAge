# Electrical Age: Minecraft 1.21.1 port — first milestone

**Prototype, not the complete mod.** NeoForge 21.1.249, Java 21, ModDevGradle 2.0.146. The 1.12.2 branch and source are not modified by this port.

This milestone extracts the real Re-Wired MNA simulator into `sim-core` with a genuine platform-neutral persistence interface and logging boundary. It connects the core to a server-ticked circuit test bench using a modern BlockEntity, versioned NBT and vanilla block-entity synchronization. The original voltage-source OBJ artwork is reused through NeoForge's baked OBJ loader, not the obsolete GL11 renderer.

The bench is **not** a complete port of the old voltage-source machine: its bounded internal demonstration circuit is 10 V, 10 ohms, 1 F, stepped at 0.05 s. Empty-hand right-click toggles charging/discharging and displays measurements. Sneak-right-click explicitly resets the bench. Separate benches do not form a connected network. Creative tab or `/give @s eln:circuit_bench`; one simple crafting recipe is provided.

Build from this directory with `./gradlew build`, run `./gradlew runClient`, and run real in-game tests with `./gradlew runGameTestServer`. Ordinary dedicated servers require the operator to accept Minecraft's EULA in `run/server/eula.txt`; no legacy world should be used for this prototype.

`sim-core` has no Minecraft, Forge, LWJGL, Netty, Kotlin or stub dependency. It retains the audited current-accumulation and QR corrections. The unit tests exercise those actual extracted sources. GameTests use the real mapped Minecraft/NeoForge API. GameTest classes and templates are excluded from the distributable jar. CI checks this exclusion and the presence of the core and artwork.

On the first CI run only, `tools/bootstrap.py` materializes the inherited sources and assets from pinned checkouts and commits them to **port/1.21.1 only**. Subsequent runs preserve source edits. `PROVENANCE.json` records origins and hashes. The workflow SHA and the generated-source commit SHA can differ; CI saves the latter in its artifacts.

## Remaining architecture work

- Replace legacy damage/metadata IDs with explicit registry IDs, typed data components and a documented mapping table.
- Port SixNode placement, face topology, hit detection, covers and multipart collision shapes; then TransparentNode machinery.
- Build a per-server/per-level circuit manager with correct chunk unload/reload, graph edits, split/merge and bounded rebuild work.
- Replace GL11 display lists and immediate mode with baked static geometry plus BlockEntityRenderer/PoseStack/vertex-consumer animation for moving parts.
- Replace packet classes with bounded CustomPacketPayload/StreamCodec messages and server-side permissions; retain strict physical-side isolation.
- Port menus, inventory/fluid capabilities, energy bridges, tags, recipes, loot, world generation, translations and machine sounds.
- Port thermal/mechanical systems and modern age-series behavior in tested vertical slices, not a bulk source merge.
- Validate populated save/reload, multiplayer, capabilities and packaged client/server distributions, then assess any explicit legacy save converter.

No 1.7.10 or 1.12.2 world compatibility is claimed. No percentage-complete claim is made. The test bench is an engineering checkpoint, not survival/content parity.

## Provenance and licensing

Inherited code: Electrical Age contributors, LGPL v3 as declared by Re-Wired. Existing source notices are retained. Inherited voltage-source artwork: Electrical Age team, CC BY-NC-SA 3.0; geometry is uniformly fitted/translated and its material texture path adapted. The PNG remains unchanged. See `LICENSE-legacy.md` and `PROVENANCE.json`. New port code is supplied under LGPL-3.0-only.

Primary API references: NeoForge's versioned 1.21.1 Getting Started, Block Entities, Custom Model Loaders, Data Components, Payloads and Game Tests documentation. The MDK is pinned to 70d335c962ee8a773b38fb0690c7e7f30d1bafa6, rather than inferred from current-version tutorials.
