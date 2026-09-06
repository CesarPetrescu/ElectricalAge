# Porting Electrical Age to Minecraft 1.21.1 (NeoForge)

Branch `port/1.21.1`, based on `port/1.12.2` (which is upstream `main` @ `6a8cd0df` plus the 1.12.2
port). The 1.12.2 branch is the base because it already did the API modernisation that 1.21 also
needs (BlockPos, ItemStack.EMPTY, capabilities, deferred registration, lowercase assets, model
JSON) and because its `McBridge`/boundary-class discipline is what keeps this port tractable.
`PORT.md` is the 1.12.2 document; the layers it describes are reused here where they still apply.

## Where it stands

Phases 0 to 3 are done and Jade from phase 4; CC:Tweaked and the biome data are open. What that
means, verifiably:

    ./gradlew portStatus        454 java + 424 kotlin files included of 893 (the 15 left out are listed below)
    ./gradlew test              303 tests, through NeoForge's JUnit launcher (the mod is constructed first)
    ./gradlew runData           recipes, tags, loot, ore worldgen, ~600 item models -> src/generated/resources
    ./gradlew runServer -PsmokeTest=place    a 50 V source, cables, a 100 Ohm resistor, a ground and a 48 V
                                            macerator placed through the item-use path; every meter reads 500 mA;
                                            a 120 V source, an MV cable and a classic lamp socket with a bulb:
                                            the socket's block light (13) reaches the light engine and the
                                            projected light block carries the same level; /eln ls, version and
                                            a zone command run on the console
    ./gradlew runServer -PsmokeTest=verify   the same readings, light included, after a restart against the saved world
    ./gradlew runServer -PsmokeTest=all      every six-node and transparent-node descriptor placed on a grid
                                            (388 of 401 take; the rest want a wall, a ceiling or water), 414 nodes
                                            alive after 80 ticks, no exception
    DISPLAY=:99 ./gradlew runClient -PsmokeClient=smoke   joins a copy of that world: the circuit, the macerator,
                                            the lit lamp by day and by night, the items (hotbar, hand in first and
                                            third person, floor, creative tab), the resistor GUI, the macerator's
                                            container GUI and the Jade overlay all draw; docs/port/smoke-*-1.21.png

`tools/port/headless.md` has the X server recipe, the world copy and the greps that matter.

## Fixed decisions

| Topic | Decision |
| --- | --- |
| Loader | **NeoForge 21.1** (gradle.properties: `neoVersion`). Forge 1.21 has no Kotlin language provider; the 1.21.1 ecosystem (Jade, JEI, CC:Tweaked, FE consumers) is NeoForge. |
| Build | ModDevGradle 2.0.x on Gradle 9.2 / JDK 21, Mojmap + Parchment. `tools/port/env.sh` sets JAVA_HOME. Plain library jars (semver4j, commons-math3, commons-numbers) are `jarJar` in the shipped jar and `additionalRuntimeClasspath` for dev runs; 1.21.1 dev runs see nothing else. |
| Kotlin | **Kotlin for Forge 5.x** provides the runtime (`modLoader="kotlinforforge"`); no shading, no relocation. The Kotlin Gradle plugin is pinned to the exact stdlib version KFF bundles (5.12.0 -> Kotlin 2.4.0). KFF registers Kotlin `object`s as instances, so `@SubscribeEvent` handlers on objects must not be `@JvmStatic`. |
| Mod entry | `Eln.java` stays a Java `@Mod` class; KFF's container constructs it with `(IEventBus, ModContainer)` injection. What used to be preInit is the constructor (`registerContent`), init is `FMLCommonSetupEvent`, postInit is `FMLLoadCompleteEvent` (recipes declared there). |
| Compile set | An explicit include list (`tools/port/include-1.21.txt`, one Ant pattern per line) drives what compiles. `./gradlew portStatus` is the metric. |
| The Flattening | Every descriptor is its own registered `Item` (`DescriptorItem`/`DescriptorBlockItem`), named by the same `registryName()` sanitizer as 1.12.2. The families (`GenericItemUsingDamage`, `GenericItemBlockUsingDamage`) are no longer `Item`s; they register their descriptors' items and keep `getDescriptor(stack)` and the legacy-id table (`descriptor.parentItemDamage`) so call sites survive. Node families (`SixNodeItem`, `TransparentNodeItem`) register one `Placer` per descriptor that routes vanilla's `useOn` into the family's 1.7.10 placement flow. Ores are one block per descriptor. Two descriptors of one family may share a name (1.7.10 never needed them distinct): the second gets its legacy id suffixed (`eln:power_inductor_80`); the dev-only single-node conduit is `eln:conduitsingle`, the hidden legacy switch is "Legacy Signal Switch". |
| Registration timing | NeoForge freezes the registries outside `RegisterEvent`, and an `Item`/`Block` cannot even be constructed while frozen (intrusive holder). Descriptors are constructed eagerly and in order; registry objects are staged in `ElnRegistry` as factories and created inside their event (blocks, items, block entity types, menus, tabs, armor materials, entity types, fluid types, fluids, biome modifier serializers); anything needing an `ItemStack` at construction time goes through `ElnRegistry.afterItems`. Ghost groups resolve `Eln.ghostBlock` when plotted, not when declared. |
| Item NBT | Since 1.20.5 a stack's tag is a copy (`CUSTOM_DATA` component): `ItemStack.tagCompound` returns a copy, `editTag {}` / descriptor `updateNbt {}` write it back. |
| Lang | The `.lang` files stay the source (generated from `tr()` call sites). `generateLangJson` converts them to `en_us.json` etc. at build time, keys byte-for-byte. Items keep the 1.7.10 key shape: `DescriptorItem.getDescriptionId()` returns `Copper_Dust.name`. |
| Data | Recipes, tags, loot tables, worldgen and models are data since 1.13 and are **generated** (`mods.eln.datagen`, `./gradlew runData`) from the mod's own declarations, and committed. `CraftingRecipes.kt` keeps its 1.7.10 shape and records into `RecipeBook`; ore-dictionary names become conventional `c:` item tags (`OreDict.tagFor`: `ingotCopper` -> `c:ingots/copper`, `plankWood` -> `minecraft:planks`, the AE2 names to AE2's 1.21 tags); the metadata items the recipes named (wool and dye colours, charcoal, the spruce sapling) are `LegacyItems`. Machine recipes declared on dictionary names are expanded from the tags on every `TagsUpdatedEvent`, both sides. |
| Worldgen | The ore veins are configured/placed features from the `OreDescriptor` spawn numbers (`spawnRate` veins per chunk, uniform between the two heights, the mean vein size, stone and deepslate targets). Whether a vein generates was an `Eln.cfg` switch at registration; a data pack cannot read the config, so the `eln:ores` biome modifier (`ElnOreBiomeModifier`) reads `worldgen.ores.<ore>.enabled` when biomes are assembled. Overworld only, as before. |
| Item models | Generated: `item/generated` with the icon and the voltage-level background as `layer0` for plain items; `builtin/entity` plus display transforms for node items, which `NodeItemRenderer` (a `BlockEntityWithoutLevelRenderer`) draws through the descriptor's own 1.7.10 `renderItem` body - the flat sprite (16-pixel, y-down space) in the inventory, the OBJ model in hand and on the ground. Descriptors that asked for 1.7.10's INVENTORY_BLOCK helper, or whose sprite the asset tree never had, draw their model in the inventory with a block's GUI transform. The families' per-type hand transforms were tuned for the 1.7.10 hand space and are not used; the display transforms in the model are. |
| Textures | Kept under `textures/items/`, `textures/blocks/`, `textures/voltages/` (1.12 layout). 1.19.3+ only stitches the directories listed in `atlases/blocks.json`, so `assets/minecraft/atlases/blocks.json` adds those three sources. Resource paths are `[a-z0-9/._-]`: the registrations still name sprites the 1.7.10 way and are lowercased where they are built (cable sprites, model textures), `festive items` is `festive_items`, `(noswing)` is `_noswing`. |
| Rendering | A fixed-function OpenGL emulator (`mods.eln.client.gl`: `GL11`, `FixedFunction`, display lists) on top of 1.21's `PoseStack`/`VertexConsumer`/`RenderSystem`, so the ~150 `draw()` bodies keep their shape. Frame-level `FixedFunction.begin(pose, buffers, light, overlay)`/`beginGui(graphics)`/`finish()`; `SixNodeRender`/`TransparentNodeRender` are `BlockEntityRenderer`s wrapping it. Node blocks have no JSON model (invisible blockstate); the slab/box shapes come from the node. |
| Hit sides | 1.7.10's ray trace named the block face an element sits on; 1.21's names the face of the element's slab that was hit (a floor element's top is UP). `SixNodeBlock.elementSide` maps a hit back to the element whose slab holds it, for activation and breaking. |
| Node light | A node's light is live data, not a state property. The server's light engine runs off the main thread (where `Level.getBlockEntity` is null) and lights chunks straight from disk before their entities exist, so the node blocks declare `hasDynamicLightEmission` and read the chunk's `AuxiliaryLightManager`; the node writes its light there when it changes, the client writes what the publish frame carries, and a node's block entity re-syncs the record when its chunk loads (nodes simulate while unloaded). The record is saved and sent with the chunk. The invisible light block a lamp projects keeps its level in a state property (`light`). |
| Node lighting when drawn | 1.7.10's `NodeBlock` set `useNeighborBrightness`: a tile entity was lit by the brightest of its six neighbours. The dispatcher lights a block entity with its own position's light, which inside a machine that blocks light is next to none, so the two node renderers take the neighbour maximum themselves (`NodeRenderSupport.neighbourLight`). |
| Node state to clients | The node's publish frame rides in the block entity's chunk-sync tag (`getUpdateTag`/`handleUpdateTag`, `PublishSync`), the way 1.7.10's description packet delivered it; later changes go over the mod's channel (`ElnNetwork.RawPayload`, the unchanged byte protocol) to the players watching the chunk. |
| GUIs | One `MenuType`; the four numbers of the 1.7.10 gui handler travel in the menu's buffer. Element screens build their own container (the 1.7.10 design): `BasicContainer` reads the vanilla-assigned id from `GuiHandler.pendingContainerId`. Container-less screens open through the byte protocol as before. |
| Capabilities | `RegisterCapabilitiesEvent`: item handlers on the transparent node entities (1.7.10's ISidedInventory), the side-aware fluid handler through a per-side adapter (`SidedFluidAdapter`), the energy exporter's `IEnergyStorage`. IC2 and OpenComputers have no 1.21 releases; their exporters are gone. |
| Fluids | `hot_water`/`cold_water` are a `FluidType`, a still/flowing `BaseFlowingFluid` pair, a `LiquidBlock` and a bucket item; sprites and tint through `IClientFluidTypeExtensions`. |
| Damage, advancements, saves | Damage types are data (`data/eln/damage_type/`), advancements are data (`data/eln/advancement/`), the per-dimension node/ghost saves are `SavedData` factories; the `electricalAgeWorld<dim>.dat` file format is unchanged. |
| Overlay | Jade (`maven.modrinth:jade`, optional at runtime): `ElnJadePlugin` registers block component and icon providers for the six-node, transparent-node and ghost blocks. The data still travels the mod's own way (`WailaCache` request packets); the providers only read the cache, as the 1.12 Hwyla providers did. A six-node's tooltip and icon are the element under the cursor (`SixNodeBlock.elementSide`); a ghost block answers for the machine it belongs to. |
| Console | `/eln <command> [args]` is one Brigadier literal with a greedy argument; the sub-commands parse their own words, permissions are decided per sub-command, tab completion comes from each command's list. |
| Tests | ModDevGradle `unitTest` hooks the test task onto NeoForge's JUnit launcher: the mod is constructed and the registries bootstrapped before any test runs (plain `Bootstrap.bootStrap()` cannot work under NeoForge). JUnit 4 tests run on the platform through the vintage engine; tests that read repository files resolve them against `eln.projectDir`. |
| World compat | None (fresh worlds only); nothing from 1.7.10/1.12.2 saves is migrated. |

## Behaviour that changed on purpose

- **MNA right-hand side accumulates.** `SubSystem.addToI` upstream *assigned*, so two current stamps on one
  state (a series string of Norton-modelled solar panels, a capacitor beside an inter-system delay) kept
  whichever component a `HashSet` iterated last; results depended on identity hash codes, and upstream's
  own `panelsInSeriesDoNotCollapseToShortCircuitCurrent` only passed on JDK 17 by luck. It accumulates now,
  and `RootSystem`'s staging sets are insertion-ordered so a world simulates the same way every run.
- **Deferred ore-dictionary stacks resolve to their own descriptor.** Every `addToOre("dustCopper")
  { element.newItemStack() }` in `ItemRegistration` captured the function's reused `var`, so all 93 deferred
  stacks resolved to whichever descriptor came last (copper ingots smelted from cinnabar dust). The
  suppliers take the descriptor as a parameter (`stackOf`).
- Ore blocks need a pickaxe (`mineable/pickaxe`, `needs_stone_tool`); 1.7.10 set no harvest level.
- A fresh world no longer prints two `NoSuchFileException` traces per dimension while the mod looks for
  a save it has never written.
- The ore scanner's default factors name the flattened ores and the deepslate variants; the 1.7.10
  `Eln:Eln.Ore:<meta>` spelling in an old config still resolves.
- Analytics use the JDK HTTP client (Minecraft no longer ships Apache HttpClient); same requests.
- **Empty slots are `ItemStack.EMPTY`.** `Container.getItem` has not returned null since 1.11, so the
  1.7.10 null checks on slot contents never fired: lamp sockets and floodlights cast EMPTY to a lamp
  descriptor, hubs looked up the cable of an empty slot, the wire machines and the fabricator took an
  empty output slot for a full one, inventory insertion never found a free slot. All test `isEmpty` now.
- **Element inventories save their items.** `ItemStack.save(provider, prefix)` returns a copy since
  1.20.5; the `writeToNBT(tag)` bridge returned it and every caller ignored it, so a saved slot carried
  only its index and every machine came back empty after a restart. The bridge merges into the tag
  (`InventoryNbtRoundTripTest`).

## Left out (the 15 files) and open items

- `transparentnode/computercraftio/**`, `simplenode/computerprobe/**`, `energyconverter/*Ic2*`, `*Oc*`:
  ComputerCraft/OpenComputers peripherals and IC2/OC energy exporters; CC:Tweaked is a phase-4 candidate,
  IC2 and OC do not exist on 1.21.
- `simplenode/DeviceProbe.kt`, `simplenode/test/**`, `entity/ReplicatorModel.kt`, `node/NodeBlockItem.kt`,
  `generic/GenericItemBlock.java`: upstream scaffolding nothing registers or references.
- `biomes.json` still keys the 1.7.10 biome names: 18 of the 68 profiles match 1.21 ids, the other 50
  biomes fall back to Plains (the startup audit lists them). A data update, not code.
- The in-hand and on-ground display transforms of node items are vanilla's block transforms
  (`smoke-hand-*-1.21.png`: a machine reads as a block in hand and on the floor, a cable as a rod);
  the 1.7.10 per-family hand tweaks are not reproduced.
- Of the 401 placeable descriptors, `-PsmokeTest=all` cannot place 13 on a flat grid (the wall-mounted
  sensors and sockets, the suspended lamp sockets, the water turbine, the radial motor, the string
  lights, the auto miner's ghost footprint); nothing is known to be wrong with them.
- The IC2-era `Eln.cfg` dictionary names (`runtime.dictionary.*`) still drive which `c:` tag a recipe
  wants; nothing on 1.21 fills `c:dusts/eln_tungsten` but this mod.
- Sound: no audio device in the headless runs, so the looped sounds are untested past construction.

## Phases

0. Toolchain and the flattening pattern end-to-end: MDG build, KFF, `Eln` entry, `ElnRegistry`,
   families + per-descriptor items, ores as blocks, datagen, lang JSON, server boot, headless
   client to the title screen. **Done.**
1. The rename pass over the whole tree, the include list grown to everything but the integrations,
   the sim core and its tests running under the FML launcher. **Done** (`f075868f`).
2. Content registration, recipes/tags/worldgen through datagen, config, SavedData, networking,
   entities, sounds, fluids, capabilities, the console -> the dedicated server runs a circuit and
   keeps it across a restart. **Done** (`a2383fba`, `e3657966`, `e3e79e85`, `63c90815`).
3. Rendering: the emulator, the block entity renderers, the item renderer, the GUI layer, the node
   light and the light block, the renderers' lighting. **Done** (`4caf850d`, `5fb41035`, `ba2ed33c`,
   `c0bfc2f7`, `37c4936a`).
4. Jade **done** (`ea4440f5`). CC:Tweaked, the biome data, a GameTest wrapper around the smoke test remain.
