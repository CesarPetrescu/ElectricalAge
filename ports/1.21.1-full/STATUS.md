# Full-source 1.21.1 migration: compiler checkpoint

**Date: 2026-09-06. Status: IN PROGRESS / DOES NOT COMPILE / NO PLAYABLE FULL-PORT JAR.**

This is the requested copy-all, compile, fix, recompile approach. It is not the five-block prototype. The complete original machine and item source is active compiler input, edited in place. Original names, descriptor strings, source paths, and artwork remain.

## Project and provenance

Repository: `CesarPetrescu/ElectricalAge`, branch `port/1.21.1-full-source`.
Open `ports/1.21.1-full/`, or open the root of the standalone migration project ZIP.

- `src/`: editable complete Re-Wired source and resources.
- `reference/rewired-1.12.2/`: unchanged original source and assets for side-by-side comparison, with SHA-256 manifest.
- `migrations/`: records of the applied transformations and reviewed followup patch. Do not reapply them.
- `tools/`: actual compiler runner, diagnostic grouping, preservation checks, and target-API inspection.
- `reports/`: historical compiler summaries.
- The maintained age-series 1.7 source remains at the outer repository root; it is not another source set in this modern build.
- `../1.21.1/` is the older prototype, also not an input or dependency of this build.

Re-Wired source pin: `3a0088b384aa4111c3a7f984d4cbc5f4ff142ed9`.
Target: Minecraft 1.21.1 / NeoForge 21.1.249 / Java 21 / Kotlin 2.2.21 / ModDevGradle 2.0.146 / Gradle 9.2.1.

## Real compiler iterations

| Pass | Compiled source commit | CI run | Kotlin diagnostics | Files with direct diagnostics |
|---|---|---:|---:|---:|
| Complete original-source import | e06b71e3d0448bacac191d2bff09f4be65870e56 | 34025599543 | 2816 | 71 |
| Shared type migration | 1bcf3d1c3a0a12e0412f48afa362d10154271ba7 | 34025935716 | 896 | 56 |
| NBT/inventory/member migration | 791a496a18d46e8e9095503e120e8e23a44ef5da | 34027211328 | 809 | 53 |
| Confirmed NBT receiver and entity calls | 45e35635f3e4644b3e7dac171b690b8bcc130fd7 | 34027813937 | 783 | 51 |

All four full compiles FAIL on source errors. The dependent full Java compile has not run because Kotlin compilation fails first. These are diagnostic counts, including cascades, NOT independent bugs, completed features, or a completion percentage. The last code-changing commit above is followed by reporting/CI changes only. The latest CI artifact records its own exact source commit and raw output.

A separate CI attempt (34026937889) stopped before compilation because the preservation guard detected binary import corruption. It is not counted as a compiler pass. Round 1's project artifact was empty due to a git-archive working-directory mistake; later exports corrected the invocation and validate archive contents. Do not use that first source artifact.

## What has actually changed

116 shared type mappings were applied across 517 original source files; all mapped destination classes exist in the real NeoForge compile classpath. These address moved/renamed types such as EntityPlayer, NBTTagCompound, IInventory, IBlockState, TileEntity and EnumFacing without fabricating old Minecraft classes. Import collisions are handled explicitly.

The next member pass edited 206 files, many already touched by the type pass. It migrates confirmed NBT receivers, inventory-contract declarations and callers, ResourceLocation factories, physical-client annotations and selected entity/client API calls. Logical network Side has NOT been conflated with physical Dist. The final ten-file patch corrects nullable/returned CompoundTag receivers in real batteries, tools, logic/analog chips and the fuel generator; it preserves saved keys and numerical behavior. The only removed SOAP import was unused. These changes do not make those whole devices playable yet.

Two actual migrated production files, INBTTReady.java and the existing empty-inventory sentinel FakeSideInventory.java, compile in an independent javac probe against the real target classpath. There are no Minecraft stub classes in this probe. It is a two-file API compile check, not full Java compilation, an inventory gameplay test, or a replacement for real machines.

Four original binary authoring files were damaged by inherited Git text normalization during import (one .blend1 and three .m_p files). The original pinned bytes were restored to BOTH active and reference copies, checked against the pre-existing hashes, and protected with scoped .gitattributes. The checks were not weakened to hide the mismatch.

## Preservation, not parity

- All 753 original Java/Kotlin files remain: 669 Java and 84 Kotlin.
- 752 are intended active inputs. Only Eln_old.java remains excluded, as it already was in Re-Wired.
- 526 original source files differ from the reference after the migration passes.
- All 1629 resource files remain byte-identical to the pinned original.
- 545 string literals in Items.kt and Descriptors.kt retain their original sequence. This is NOT a count of registered items.
- 25 Python tool tests cover mapping, diagnostic parsing and preservation guards. They are NOT mod gameplay tests.
- The earlier prototype's numerical tests and green CI do NOT validate this full-source branch.

## Current blockers and next work

Start with shared dependencies, not hundreds of isolated symptom edits. The latest raw reports identify:

1. Eln.kt, ElnContent.kt, ModBlock.kt: real NeoForge lifecycle, registries, BlockEntity types/tickers and initialization sequencing. Obsolete lifecycle events do not become correct by a type rename.
2. SharedItem and the electrical items: old ISpecialArmor dependency, metadata subtypes, armor/tool hooks and ItemStack tag access. Implement the modern item/data-component contract while preserving each original descriptor and behavior. One missing shared superclass currently creates many cascaded errors.
3. Config.kt and persistence: replace removed Forge config APIs, complete BlockEntity/SavedData serialization, and preserve schema/keys deliberately. Verify old ItemStack serialization separately from CompoundTag setter renames.
4. Packets: migrate IMessage/MessageContext/ByteBufUtils to real modern payload codecs, threading and target authorization. Do not add dummy legacy network types.
5. GUI and rendering: modern menu/screen signatures, render buffers, model loading and animations. GL11 display lists and immediate rendering cannot be treated as fixed by imports. Original resources are preserved but not yet all mapped into the new renderer.
6. Waila, ComputerCraft, OpenComputers, IC2 and other optional integration sources remain present. Their obsolete APIs need explicit compatible adapters or documented target-support decisions; they have not been silently excluded.

Once Kotlin passes, run the FULL Java compilation and work through its errors; its remaining error count is not yet known. Then require an original catalogue manifest, actual registration parity, all model/texture loads, dedicated-server and client tests, crafting/GUI/automation tests, charged populated-world restart and chunk transitions. Compilation alone cannot certify original gameplay.

The previous 1.12 audit patches are NOT assumed applied to this pristine-based branch. They must be reviewed and transplanted explicitly. No default branch, old 1.12 baseline branch, or prior 1.21.1 prototype branch was modified for this migration.

## Continue locally

Use JDK 21, Python 3 and the checked-in Gradle wrapper. The first Gradle invocation needs network access to download dependencies.

```sh
python3 tools/verify_preservation.py
python3 -m unittest discover -s tools -p 'test_*.py' -v
python3 tools/compile_report.py --label local
```

On Windows use `py -3` in place of `python3`; the compiler runner selects gradlew.bat automatically. Inspect `build/migration/local/compile.log`, `diagnostics.json`, `by-file.csv`, `by-message.csv`, and `SUMMARY.md`. Generate the file ledger with:

```sh
python3 tools/source_status.py build/migration/local/diagnostics.json
```

Edit real `src/main/java/mods/eln/...` files and compare against `reference/rewired-1.12.2/src/...`. Do not rerun the historical import/mapping scripts with --apply or reapply migrations/003-followup-api.patch. Their transformations are already present.

The final routine workflow is read-only and does not rewrite code. It keeps compiler failures red while uploading complete source and diagnostics. No JAR is distributed from this checkpoint because the full port does not compile. The releaseParityGate task is an explicit manual guard, not evidence of completeness.
