# Evaporative cooler: ELN-family redesign

Based on merged `main` commit `6a4046f20db037b98ab0283cc254c3dc3978dd35`.

## Visual direction

This is an evolution of the passive and active thermal dissipators, not a new cabinet-style machine. The generator adapts the passive cooler's actual base/fins and the active cooler's actual four-bladed fan, preserving their original diffuse texture pixels. It adds only a tray rim, simple media pad/distributor, water feed, side level strip and small colour-coded connection patches.

The fan faces front/back to preserve the merged evaporative cooler's existing ventilation paths. It is intentionally not top-facing like the older active cooler: changing the physical ports or ventilation checks would break existing installations and is outside this presentation-only change.

- Asset geometry: **5,932 -> 444 triangles**, including both pad states.
- Main diffuse atlas: **256x256 -> 64x64**.
- Inventory sprite: **128x128 -> 32x32**, matching the older dissipators.
- Flat-shaded chunky geometry; no roof, cabinet, logo text, micro-plumbing or LED assembly.
- Editable `.blend`, animated `.glb`, game OBJ/atlas/pivot and icon are generated together.

## Interface

The compact **248x228** native menu uses the exact grey bevel and corner sprites used by `GuiHelper`. The frame drawing is shared, rather than duplicating or inventing another theme. Native buttons retain keyboard focus/narration and the validated menu protocol.

The main page shows temperature, net cooling, electrical input, water, status, modes, target, fan limit and redstone control. **Details** retains intake temperature, humidity, estimated wet bulb, water consumption, sensible/latent heat, motor input, voltage, actual fan speed and ventilation/wet-state diagnostics. Long status text has a full hover tooltip. Connection help remains available without tiny scaled footer text.

## Compatibility boundary

No thermal constants, water consumption, controls, electrical loading, ventilation rules, item ID 4132, recipe, menu data layout, network packet layout or saved NBT format change. Existing passive/active game assets remain byte-identical. The obsolete status field is still consumed by the new renderer to retain the packet layout. All model-state changes are client-side.

## Review and tests

`python tools/evaporative/verify_assets.py` checks all source/game hashes, original reference hashes, PNG CRCs/dimensions, GLB chunk/buffer/texture integrity, model part names and budgets, triangle degeneracy, UV indices, the complete fan sweep and the reservoir gauge anchor. It checks that moving fan blades clear the base, guard and rear support.

The existing 26 numerical/control/water regression cases and full mod unit suite remain enabled. The dedicated feature workflow exercises real power/fluid ports, buckets and failure cases, then restarts the saved world in another JVM. Native client checks include the fan and held-item rendering, menu packet round trips, Details/Back navigation, GUI scales 1/2/3, a resized mouse hitbox, all three actual in-world cooler models and four orientations/water levels.

Presentation comparisons are actual Blender renders of the referenced meshes under the same camera scale and lighting, not generated concept illustrations. In-game screenshots are captured by the opt-in native-client test.
