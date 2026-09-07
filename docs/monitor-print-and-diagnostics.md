# Monitor printouts and circuit diagnostics (1.21.1)

## Read a monitor print

1. Connect a signal source or sensor to the monitor's signal input. Configure the sample period, units and display range.
2. Wait for samples. Pause if you want to freeze the trace before printing.
3. Insert vanilla paper in the input slot and leave the output slot empty. **Print** consumes one sheet and creates one printout.
4. Take the paper and right-click in air to open its chart. Hover over the trace for the recorded value and age. Latest is on the right; the left label is the age of the oldest sample.

The print is an independent snapshot, including units, range and sample period. Resetting/reconfiguring the monitor does not change existing paper. Empty paper explicitly says there are no recorded samples. One sample is shown as a point. Different GUI scales use the same native, clipped chart layout.

The inventory/held/dropped item uses Minecraft's visible paper model; the detailed graph is in the reader, not a miniature live graph on the held item. Old print NBT remains readable. No registered IDs changed.

### Fixed porting defects

- The generated item model used `eln:items/empty-texture`, while the old descriptor's custom renderer was never connected to the 1.21 renderer. The item was invisible. Both the committed model and its generator now use a real paper texture.
- No use action existed to open a chart. A read-only reader now consumes the item-use action even though the stack does not change.
- Loading history appended to existing samples. It now replaces history and keeps the newest samples when loading a longer history into a smaller buffer.
- Pausing with an expired sampling timer and no pending samples could divide by zero. Paused monitors can print without sampling.
- The server now checks for actual paper, not merely any nonempty input stack.

## Two-probe meter

Both Multimeter and AllMeter retain their normal right-click readings.

- **Sneak-click a connector:** select reference probe A.
- **Sneak-click another connector:** read `V(B) - V(A)` with both terminals sampled at that moment. A stays selected for comparisons with other B points.
- **Sneak-use in air:** clear A.

Aim near the intended connector on the selected face. The meter also labels each terminal's voltage relative to simulation ground, terminal B's current and its serial connection losses. It does not change the circuit or install a measurement resistor.

A 12 V floating source can have A = -6 V and B = +6 V: the difference is +12 V. Reversing the probes changes the sign. Batteries' normal meter readout now includes their actual terminal difference and both ground-referenced voltages.

Limits are explicit: loaded locations, same dimension, up to 64 blocks between probes. A missing/replaced terminal invalidates the saved selection. Independent solver sections produce a reference warning. Multicore cables require a single-core breakout because this UI does not yet select individual cores. Almost-zero current is a troubleshooting hint, **not proof of a missing return**. There is no automatic whole-circuit topology diagnosis in this increment.

## In-game exercises

Press **P**. The contents page now links to four scrollable exercises:

- Measuring between terminals.
- Understanding a floating 12 V battery.
- Recording and printing a monitor chart.
- Checking a simple powered load, including the creative 12 V / 12 ohm example and an open-return comparison.

The pages show relevant items, steps, expected readings and caveats. They do not place blocks or modify the player's world.

## GitHub verification

The regular standalone and Create smoke jobs now require `monitor-print`, `monitor-print-restart`, `circuit-diagnostics`, `circuit-diagnostics-restart` and `monitor-print-client` reports, in addition to the existing gates.

- Monitor descriptors: real print packet/process, one-paper transaction, occupied-output protection, wrong/missing input, paused printing, immutable snapshots and item component serialization.
- A placed monitor retains its printed output across a real dedicated-server world save/restart.
- Diagnostics: real battery terminal routing, floating-source MNA readings, fresh values after A selection, invalid selection, ordinary meter behavior, and a 12 V / 12 ohm circuit contract. These are controlled simulation fixtures, not a complete player-built tutorial playthrough.
- Client: item has visible baked geometry/paper texture, item use opens the reader, trace pixels render at GUI scales 1/2/3, an empty page has no stale trace, and screenshots are saved. Wiki contracts check lesson links, item lookup and scrolling.
- Unit tests: bounded history, repeated loads, immutable chart data, old/malformed metadata, reversed/flat ranges and voltage differences.

Minecraft runs only in the GitHub smoke jobs for this work. Local validation is compilation and language generation.
