# EC-240 evaporative heat sink

Minecraft 1.21.1 / NeoForge. Registered as **240V Evaporative Heat Sink**, stable descriptor **4132** (group 64, sub-ID 36). Existing heatsink IDs and behavior are unchanged.

## Build and connect

Craft from a 240 V active thermal dissipator, a sponge, two copper thermal cables, an empty bucket and four iron ingots. The normal in-game guide shows the recipe.

- Copper side ports connect to the thermal network (for example a heat turbine's cold side).
- Front and rear lower terminals accept 240 V ELN power. Like the existing fan heatsinks, this device uses the mod's internally grounded single supply.
- Top and bottom accept standard NeoForge water fluid pipes. Only water is accepted. Both ports can fill and drain.
- Main-hand water buckets fill 1000 mB; empty buckets withdraw 1000 mB. Partial transfers do not consume a bucket. Ordinary right-click opens the controls. The inherited legacy block callback has no hand argument: offhand bucket use is not supported.
- The reservoir stores 4000 mB. A separate pre-paid wet film stores less than one additional mB. Breaking and replacing the machine preserves water, film and settings, but not stored heat.

## Controls and feedback

**Off** leaves passive cooling. **Dry** runs only the fan. **Auto** starts the fan at target +2 C and stops at target; wet assistance starts at target +5 C and stops at target +2 C. **Wet** requests continuous fan/pump operation subject to interlocks. Target range is 5..90 C; maximum fan speed is 10..100 percent. Redstone can be ignored, required high, or required low.

The native menu shows surface and intake temperatures, relative humidity, estimated wet-bulb temperature, water and consumption, electrical input and signed net heat rejection. Hover net cooling for the sensible, evaporative and electrical-heat components. A negative net value means the device is warming rather than cooling.

A rotating fan, wet/dry pad, reservoir gauge, status LED and cosmetic moisture particles reflect synchronized server state. Particles are not the simulation.

## Limits and failure behavior

Rated supply is 240 V. Maximum fan input is 120 W; pump input is 15 W. Maximum latent heat rejection is 8000 W, not a promise of 8000 W under every condition. The existing ELN voltage and temperature watchdogs protect the device; its maximum surface temperature is 160 C.

Wet operation requires outdoor intake and exhaust. Each straight path must pass through loaded, unobstructed cells to a sky-visible cell within eight blocks. Low floor cables below the aperture are allowed. Full blocks, water and unloaded chunks stop the path. An identified indoor room inhibits wet operation even if a path appears open. This intentionally does **not** simulate room moisture or ducts. Indoors, ordinary dry cooling still participates in the existing room thermal solver.

No water means dry fallback. No power or a blocked aperture means passive cooling. Low supply voltage below 75% of nominal inhibits the pump. Water spraying is disabled at air temperatures at or below freezing, surface temperatures at or below 1 C, surfaces at or above 95 C, unsupported climate samples, or ultrawarm dimensions. There is no automatic refilling from an adjacent infinite water source.

## Simulation and units

The exchanger is one thermal load: heat capacity 18000 J/K, connection resistance 0.001 K/W, passive conductance 6 W/K and a forced-air addition of up to 40 W/K. Actual solved electrical power governs fan speed; commanded fan input follows a cubic law. All motor/pump input is conservatively deposited as heat into the exchanger.

ELN thermal temperatures in this port are *ambient-relative deltas*. The evaporative calculations convert to actual Celsius; native Rp exchange already handles signed sensible heat and room coupling. The new process adds only motor heat and subtracts latent evaporation. It does not subtract ordinary convection twice or directly assign a target temperature.

The wetted-surface model uses an approximate Lewis relation:

```
m_dot = G / cp_air * max(0, w_saturated(surface) - w_inlet)
Q_latent = m_dot * L_v(surface)
water_mB = Q_latent * dt / (0.001 * L_v(surface))
Q_net = Q_sensible + Q_latent - P_electric
```

Air pressure is fixed at 101325 Pa. Saturation pressure follows the ASHRAE equations used by PsychroLib. Latent heat uses `2,501,000 - 2360*T` J/kg. The displayed wet-bulb temperature is the equilibrium of this approximate Lewis model, **not** a certified tower rating or an exact psychrometric wet bulb. Humidity matters via moisture ratio, not a blanket `(1-RH)` cooling multiplier. A hot surface can still evaporate into air at 100% RH.

A physical conversion of **1 mB = 1 gram of water** is an explicit modeling choice. Integer fluid API quantization is handled by withdrawing a whole mB before consuming its fractional film. Film remainder is persisted and clamped on load. The last available water strictly limits credited latent energy. No accumulator permits unpaid cooling.

Environmental/ventilation sampling occurs at 2 Hz, independently from the thermal solver frequency. Wet-bulb/root calculations are cached. A new block obstruction therefore takes at most one environmental interval to inhibit wet cooling; no chunks are loaded by the lookup.

## Asset sources

`tools/evaporative/build_model.py` generates the original model in Blender 5.2 LTS. The `.blend` source and animated `.glb` are in `artwork/evaporative_cooler/`; game assets are triangulated OBJ, MTL, atlas and pivot metadata in `assets/eln/model/evaporativecooler`. The reservoir, fan, pad and LED are separate parts. No external model, image or proprietary asset is used. Regenerate with:

```
python -m pip install bpy==5.2.0
OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=4 python tools/evaporative/build_model.py --root .
```

The generator validates bounds and a <16000-triangle budget and writes hashes in a manifest. Blender source is not included in the game JAR. Rendering uses CPU Cycles without denoising for portable headless execution.

## Verification commands

```
bash tools/evaporative/test-core.sh
./gradlew generateLangFiles runData build
```

The 26 pure regression cases are also exposed as JUnit dynamic tests. They exercise conservation, wet/dry behavior, saturated air, water exhaustion, fractional accounting, timestep sensitivity, invalid inputs, mode hysteresis and redstone/command limits. In-world validation additionally needs real fluid capabilities, native menu routing, source wiring, asset loading, persistence and failure behavior; see the final feature test reports rather than assuming numerical tests cover those.

## References

- PsychroLib API and ASHRAE equation notes: https://psychrometrics.github.io/psychrolib/api_docs.html
- ASHRAE cooling towers, sensible and latent heat transfer: https://handbook.ashrae.org/Handbooks/S20/SI/S20_Ch40/S20_ch40_si.aspx
- NeoForge 1.21.1 capabilities: https://docs.neoforged.net/docs/1.21.1/datastorage/capabilities/
- NeoForge 1.21.1 menus: https://docs.neoforged.net/docs/1.21.1/gui/menus/
