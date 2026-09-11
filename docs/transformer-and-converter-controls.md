# Transformer and converter controls

## Three different inputs, not three simultaneous gains

The power input supplies energy. The small 0–50 V signal connector is an optional control command, not the power supply. The number in the GUI is used only in the selected manual control mode.

| Selected mode | What controls conversion | Meaning of the number | Signal connector |
| --- | --- | --- | --- |
| Signal (0–50 V) | The existing versioned signal-to-gain mapping | Hidden; unused | Active |
| Ratio (x) | A manual voltage-gain request | x2 requests nominal Vout = 2 × Vin; x0.5 requests half | Ignored |
| Target (V) | A manual internal output-voltage target | 200 requests a 200 V internal target | Ignored |

Example, before winding drop and current/power limits: at 100 V input, ratio x2 requests 200 V. Changing input to 150 V changes that request to 300 V. A target of 200 V remains a 200 V request when input changes. It may be unattainable because of the converter's topology, voltage rating, source resistance, gain range or current/power limit.

The existing V2 signal mapping uses x = clamp(Vsignal / 50, 0, 1). Boost gain is 256^x; buck gain is 256^(x−1); variable and buck-boost gain is 256^(2x−1). Thus an unconnected/zero signal on a boost requests x1 intentionally. The middle signal on a variable/buck-boost requests x1. V1 signal curves are different and are preserved for saved worlds.

Only one mode is active. The code must not evaluate an inactive signal just to produce a diagnostic. Invalid active controls open the converter branches; a zero voltage target turns them off rather than becoming a small gain or a short circuit.

## Ratio and wire length by device

| Device | Ratio policy retained | Wire-length policy |
| --- | --- | --- |
| Modern fixed DC/DC | Secondary winding amount / primary amount, limited to 1/256…256 | Utility spools use remaining meters, native cables use item count as winding units; actual winding resistance and heat remain |
| One-way fixed DC/DC | Same winding-amount ratio; reverse power blocked | Same physical winding model |
| One-way isolation | Intentional x1 with separate references | Wire affects resistance/heat, not nominal x1 ratio |
| Boost | Electronically requested gain, protected V2 range x1…x256 | Windings contribute copper resistance/heat, not another gain multiplier |
| Buck | Electronically requested gain, protected V2 range x1/256…x1 | Same |
| Buck-boost | Electronically requested gain, protected V2 range x1/256…x256 | Same |
| Variable DC/DC | Reversible controlled-ratio abstraction, not a simulated PWM topology | Shared real winding resistors; do not silently multiply its manual gain by a winding-length ratio |
| Grid transformer | Fixed x0.25 nominal conversion | External span length still changes resistance/drop/heating |
| Legacy DC/DC | Old native-cable item-count ratio, limited to x1/16…x16; legacy core loss factor retained | Length-based spools are incompatible with this historical model and are now rejected rather than silently counted as one turn |

A 10 m primary and 20 m secondary therefore produce nominal x2 in a modern fixed DC/DC. A 20 m / 20 m pair and a 100 m / 100 m pair both have nominal x1, but the longer pair has more copper resistance. The length of a cable outside the transformer changes loaded voltage, not transformer turns ratio.

Meters are still a gameplay proxy for turns. This patch does not introduce winding-layer geometry, AC switching, magnetizing current, core saturation or a frequency-dependent core-loss simulation.

## What the readings mean

For one-way converters, Waila now distinguishes requested gain or internal voltage target from the measured terminal ratio. It no longer displays a placeholder x1 as though it were the measured gain in voltage-target mode. It also says when the signal is ignored in a manual mode.

A voltage target is internal, before the output winding and external cable drops. It is not remote sensing at a distant load. The reversible variable converter derives a ratio from the primary Thevenin/no-load voltage; it is not a newly added load-regulating power-electronics controller. Its loaded output can sag. The protected one-way regulator also respects current, power and topology limits. Neither guarantees the number typed will appear exactly at the load.

## Compatibility and safety

Existing V1 signal mappings are unchanged. Voltage-target operation requires the explicit V2 upgrade; this upgrade selects a safe manual ratio and leaves the device off until deliberately enabled. Unsupported/future versions, invalid modes and invalid numeric values cannot be re-enabled with an enable packet. Truncated packets cannot partly overwrite accepted settings.

Legacy DC/DC still supports its original native power-cable stacks and retains its core-factor resistance and ratio range. Empty slots, signal cables and utility spools cannot form a valid legacy winding. Spools already inserted in old saves are not consumed or deleted: the converter is non-operational, and those spools can be removed for a modern DC/DC. Its active source branches now open for invalid/incomplete construction. Duplicate source registration during initialization has also been removed.

The core tooltip now identifies its coefficient as a legacy cable-loss factor. Modern winding copper resistance remains derived from material, per-conductor cross-section, length and temperature. Multiplying that resistivity by a magnetic-core quality factor would invent a different physical model, so this patch does not do it.

A multicore spool represents one active conductor in this winding model. Thermal mass/cooling now uses the same per-conductor area as electrical resistance rather than borrowing unused cores as a free heat sink. This is still a simplified thermal geometry, not a detailed coupled cable-bundle heat model.

## Regression coverage and validation

- `DcDcControlTest`: 18 cases for mode isolation, existing mappings, bounds, persistence/synchronization, malformed packets and explicit legacy upgrade.
- `ConverterControlRuntimeRegressionTest`: 8 production-MNA cases for invalid callbacks/targets, open-circuit shutdown, recovery, manual modes with an inactive invalid signal, fixed ratios, power balance and series-wire drop.
- `OneWayDcDcInvalidInputTest`: 4 numerical cases for invalid gain/rating/network inputs and unchanged valid legacy transfer behavior.
- `WindingThermalBindingRegressionTest`: 3 source-binding/production-thermal-math guards for per-core geometry and length scaling. This is not a native winding-placement test.
- `LegacyWindingCompatibilityBindingTest`: 3 source-binding guards for shared slot/server validation, retained legacy policy and one-time population-gated source registration. The open-source behavior itself is exercised by the MNA tests.

Run the complete project build/test and the existing acceptance, migration, topology and native-control workflows on the exact PR head. The source-binding checks above do not substitute for native-client or saved-world testing. Do not interpret a queued/skipped workflow as a pass.

Local evidence before full CI: the 18 control cases passed in a JVM harness using the production control class and mapping with a map-backed Minecraft NBT stand-in. That run did not compile or launch the full mod, FML, a game client, or the production MNA tests. Check the PR's actual workflow results for those gates.
