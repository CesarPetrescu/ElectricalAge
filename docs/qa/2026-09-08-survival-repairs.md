# Survival/runtime audit follow-up

Baseline: `a3b9b046c08abfd8b8979e9093b7f830e7ee0568`. PR #5 preserves the opt-in campaign and its original failing runs. Repair verification is in progress; original audit results are not relabeled as passing evidence.

## Repairs

- Wire Roller/Insulator machine items retain exact remaining metal/material and insulation buffers plus selected settings. Inventory still drops separately. Work-in-progress is reset, not copied, and consumed material is never refunded. Existing world-save keys and registry IDs are unchanged.
- Wire Snips now require two iron ingots and two rubber. Polarized generator/motor recipes upgrade the existing Generator/Shaft Motor with two corresponding-tier cables and two rubber, retaining the parent progression requirements.
- Tachometer controls are recreated on every screen initialization; typed values survive resizing. Native checks exercise Validate and require synchronized range values after resizing.
- All logger variants have a 256 x 232 compact layout at small viewports, with the graph, configuration controls, paper/output slots and player inventory repositioned together. The normal 176 x 286 layout remains on larger screens. Native checks click Configuration/Back at both sizes.
- Overhead gantry support detection includes the missing north neighbor.
- The electrical furnace's functional thermal-isolator slot is now accessible beside its heater/regulator. Auto Miner's unused scanner slot retains its legacy inventory index but is explicitly inactive and rejects insertion/pickup; breaking still returns stored inventory.
- `machines.heatFurnace.consumeFuel` had inverted semantics: the documented/default `true` supplied free heat. It now consumes fuel when true and permits fuel-free heating only when false. This is a behavior correction: installations relying on the bug need fuel or an explicit server setting of false. Four isolated native cases cover true/false with/without coal; naturally acquired production independently requires paid fuel consumption.

## Required checks

Main/PR CI now also requires survival material/crafting, acquisition, all advertised GUI shards, directional overhead rendering, and the natural-resource fuel-powered production campaign. Release publishing waits for these jobs as well as the existing suites.

The three acquisition recipes include packaged JSON and recipe-book unlocks. CI runs data generation and compares those files against their Kotlin declarations, so adding a declaration without shipping its data cannot silently pass again.

Survival fixtures use explicitly seeded ingredients for deterministic regression tests. The separate natural-resource campaign starts empty and pays ingredients from harvested resources, but uses automated travel, placement and menu interactions; it is not a human-equivalent survival playthrough. Fractional item-state roundtrips use synthetic values; three repeated ordinary survival breaks also check those values, single machine drops, and the absence of copied inventories.

## Shutdown investigation

Original QA run `34241605315` again stalled during shutdown. Both preserved thread dumps show a busy `ChunkMap.scheduleUnload` / `processUnloads` loop rather than a sleeping save operation. Its cause is not yet assigned. A successful repeat is not proof of remediation. Shutdown deadlines and failure captures remain enabled; no forced-success exit or extended timeout has been introduced.

The original two-hour campaign runs the pre-repair JAR and must not be described as endurance evidence for these changes. Fresh repair-run IDs, failures and release status are recorded in PR #5.
