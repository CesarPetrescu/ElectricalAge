# Re-Wired as a crib: what was matched, what was done differently, what is wrong there

Every file ported on `port/1.12.2` was checked against `brambora69123/electrical-age-rewired`
(`rw/main`, Jan-2019 snapshot base) with `tools/port/crib.sh <Class>`. Three outcomes per file:
**matched** (same approach, sometimes literally the same lines), **improved** (Re-Wired's version
compiles but is wrong or incomplete, so this branch does something else), or **no crib** (upstream
code newer than Re-Wired's base: 154 classes, e.g. the Falstad importer, room thermal manager, biome
climate, utility cables, MQTT, railroad).

## Matched

| Area | What was taken from Re-Wired |
|---|---|
| `NodeBlock`/`NodeBlockEntity` shape | `IBlockState`/`BlockPos` signatures, `ITickable`, `getUpdatePacket` returning null, `neighborChanged`. |
| Inventories | `ItemStack.EMPTY`-filled arrays, `isEmpty`, `removeStackFromSlot`, `getSlotsForFace(EnumFacing)`. |
| `ReplicatorPopProcess` | Bounded scan (`y < world.height - 2`), `doMobSpawning` game rule, air + air-above + light ≤ 6 headroom test. |
| `ElectricalPickaxe` | `state.material` set for the 3×3 break, `pos.add(a, b, c)`. |
| Ore scanner chunk read | `ExtendedBlockStorage.get(x, y, z)` with the same 12-bit id + 4-bit meta key. |
| Waila accessor proxy | Same idea: re-target `getPosition`/`getMOP` at the real node behind a ghost block. |
| Byte packet protocol | Kept, as planned (`GenericPacket`-style payloads over `SPacketCustomPayload`). |
| `TransparentNodeDescriptor.checkCanPlace` | `isOpaqueCube(state)` on the neighbour state. |

## Improved (Re-Wired's version compiles but is wrong or incomplete)

1. **Fuel generator cannot be fuelled.** `FuelGenerator.onBlockActivated`: the whole bucket branch is
   commented out (`TODO(1.10): Filling with fuel`). Here: `FluidUtil.getFluidContained` +
   `IFluidHandlerItem.drain(1000 mB)`, same one-bucket-at-a-time rule, tank fluid persisted.
2. **RF/energy bridge speaks a dead API.** `EnergyConverterElnToOtherEntity` keeps cofh
   `IEnergyProvider` from a vendored 1.7-era `cofh/api` tree (`src/main/java/cofh/…`) and comments
   the IC2 bridge out entirely. No 1.12 machine consumes cofh RF. Here: Forge Energy
   (`CapabilityEnergy` / `IEnergyStorage`, extract-only), IC2 `IEnergySource` with the 1.12
   `IEnergyAcceptor` signature, and the push firewall asks the neighbour for the face that touches
   the converter (upstream passed the un-inverted direction, which cofh handlers ignored but
   capability lookups do not).
3. **Cross-mod ids never match.** `Other.modIdIc2 = "IC2"`, `"OpenComputers"`, `"ComputerCraft"`,
   `"Waila"`. 1.12 mod ids are lower-case and `Loader.isModLoaded` is an exact map lookup, so every
   `@Optional.Interface`/`@Optional.Method` is stripped and every `Other.*Loaded` flag is false.
   `FMLInterModComms.sendMessage("Waila", …)` is likewise never delivered (exact-id queue), so the
   Waila integration is dead code. Here: `ic2`, `opencomputers`, `computercraft`, `waila`
   (verified against `ic2.api.info.Info.MOD_ID`, `li.cil.oc.api.API.ID_OWNER`, the CC-Tweaked and
   Hwyla `mcmod.info`).
4. **Node updates stop 64 blocks away.** `Utils`/`NodeBase` broadcast with
   `elnNetwork.sendToAllAround(…, 64)`. 1.7.10 sent to every player *watching the chunk*
   (`PlayerManager.isPlayerWatchingChunk`). Here: `PlayerChunkMap.isPlayerWatchingChunk` over
   `playerList.players`, so render distance decides, as before.
5. **Scanner misses capability storage and can divide by zero.** `Scanner` tests `IFluidHandler`/
   `IInventory` interfaces only (every 1.12 tank/chest that is capability-only is invisible), divides
   by the tank count without an empty guard, and dropped the HBM special case. Here: `ISidedFluidHandler`
   → `CapabilityFluidHandler` (`tankProperties`, empty-guarded) → HBM → `CapabilityItemHandler`
   → `ISidedInventory` → `IInventory`.
6. **Replicator spawn egg spawns nothing; free iron.** `ReplicatorEntity`: `ItemStack(spawn_egg, 1, id)`
   (meta-based eggs died in 1.9), a free iron-dust drop when the drop list is empty, and the villager
   target's `checkSight` flag flipped. Here: `ItemMonsterPlacer.applyEntityIdToItemStack` with the
   registered `EntityList` key, drop list as upstream, upstream's AI flags.
7. **Tree-capitation axe.** `ElectricalAxe` passes `null` as the tile entity to `harvestBlock`,
   uses the 2019 energy check and dropped the `isBlockLoaded` guard. Here: the 1.12
   `onBlockHarvested` → `removedByPlayer` → `onPlayerDestroy` → `harvestBlock(te)` sequence with
   upstream's energy accounting.
8. **Replicator population cap hard-coded to 100** and the unloaded-chunk guard dropped. Here: config
   cap and `isBlockLoaded` break, with Re-Wired's bounded loop kept (see "Matched").
9. **Translations client-only, keys changed.** `I18N.tr` translates on the client only (server chat
   and Waila text stay raw), prefixes every key with `eln.`, and switches `%1$` placeholders to
   `%s`, invalidating all six shipped language files. Here: `net.minecraft.util.text.translation.I18n`
   (side-neutral, dedicated server injects `en_us.lang`), keys and placeholders unchanged. Its lang
   file is still `en_US.lang`, which a 1.12 client never loads (the lookup is a lowercased
   `ResourceLocation` against case-sensitive jar entries) — renamed here.
10. **Recipes: none.** Both `addRecipe` helpers are commented out in `Eln_old.java`; nothing feeds
    `ForgeRegistries.RECIPES`. Here: the two helpers register `ShapedOreRecipe`/`ShapelessOreRecipe`
    under `eln:<output>_<meta>_<n>`, all 449 recipes.
11. **Fluids: none.** No `FluidRegistration`/`BlockElnFluid`; hot and cold water do not exist. Here:
    `Fluid` with sprites + universal bucket (`FluidRegistry.addBucketForFluid`).
12. **Achievements dropped.** Here: two advancements with `impossible` criteria granted from the same
    server-side triggers.
13. **Sounds.** `SoundClient` builds unregistered `SoundEvent`s — fine for its client-side play path
    (same here), but Re-Wired has no `RegistryEvent.Register<SoundEvent>` at all, so any server-side
    `world.playSound` with an Eln sound sends registry id -1 and NPEs the client. Registering the
    `sounds.json` keys is a phase-2 item on this branch.
14. **`LoopedSound`** re-implements `createAccessor`/`getSound` by copying `PositionedSound`. Here it
    extends `PositionedSound` and only keeps the live-coordinate and tickable parts.
15. **Kotlin stdlib embedded flat and unrelocated** (`embed("kotlin-stdlib")`). Two mods doing this
    with different Kotlin versions collide at class-load. Here: shaded and relocated to
    `mods.eln.shaded.kotlin`.
16. **Rendering.** No `TileEntityItemStackRenderer` binding (3D items render as missing model), and
    six-node camouflage is disabled. On this branch these are phase 3; the `IItemRenderer` shim keeps
    the ~250 `renderItem` bodies intact so one TEISR can drive them.

## Not flaws, but different choices

- Re-Wired ports on the same `stable_39` mappings and already uses `setTranslationKey`; nothing to
  reconcile there.
- Re-Wired rewrote `Eln.java` into `Eln.kt` and kept `Eln_old.java` beside it. This branch keeps
  upstream's `Eln.java` (still to be ported, phase 1 Java) so upstream diffs keep applying.

## Numbers

    Kotlin  4100 → 0 errors, 425/425 files (43 commits on top of 6a8cd0df)
    Java    199 errors at first javac (446 files), next

Everything above that says "here" is in the commit messages of `port/1.12.2`; grep them for
"Re-Wired".
