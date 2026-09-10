# Native campaign correctness follow-up

Base: `9d59429356b67c7b9952124f9c283231b85d7ef5` (merged PR #10).

This follow-up corrects the test fixtures. It does not disable gameplay hazards,
change shaft speeds/energy directly, increase electrical ratings, waive failed
assertions, or count development-only components as production passes.

## Corrected contracts

* **Production registry:** the official NeoForge installer starts a packaged
  server containing the exact client JAR. Its receipt contains the SHA-256,
  production flag, process ID and full descriptor catalogue. The launcher separately
  requires a zero exit and the smoke success marker before publishing the seed.
  Each client verifies the registry receipt before setup.
  The six production converter types are required explicitly; an additional or
  missing type fails coverage review. The development-only Isolation Transformer
  is recorded as outside this profile, not silently skipped or declared passing.
* **Logic:** 0/5 V sources use `Eln.SVU`. Every requested gate input must be
  observed at that voltage before its output can pass. Schmitt hysteresis uses
  0/4/2/0 V. Flip-flop cases deliberately preserve state between clock edges;
  each independent fixture starts with fresh sources/gates. The report validator
  independently checks input voltages and the named truth-table bit pattern.
* **Battery:** external current is measured at the actual load resistor. Internal
  current is checked against terminal voltage / the descriptor's self-discharge
  resistance. A read-only electrical-step observer integrates terminal energy.
  The existing energy meter's 50-bin quadrature and measured aging are accounted
  for explicitly (1 J + 5% integral + aging allowance); this is not an exact
  calorimetric verification of every thermal loss channel.
* **Rigid shafts:** unsafe replacement must place then destroy the stationary
  flywheel, as existing production mechanics specify. A 0 V absorbing source and
  a declared generator resistor brake the surviving networks through the normal
  electrical simulation. Only after both measured speeds are below 20 rad/s is
  safe reinsertion attempted. Orientation, live identity and shaft membership
  are recorded. Motor power is restored only after the connection survives.
* **Clutch:** install while stopped, spin one side with engagement off, then
  apply 1 V to the iron plate. Require slipping/transfer/wear, then synchronized
  speeds with distinct network objects. The coal plate separately demonstrates
  destruction on engagement above its 5 rad/s mismatch condition. Shaft merge
  thresholds and plate physics are unchanged.
* **Restart:** coordinate-based rigid membership, generator output, clutch plate,
  wear, lock state, input, battery state and converter settings are checked in a
  separate JVM. Intentionally destroyed components are removed from retained
  fixture expectations, never from the production registry gallery.

## Evidence

Each functional case has a **before action** and **observed result** framebuffer.
These are direct Minecraft screenshots; no generated images satisfy the gate.
An unsafe insertion can be destroyed before the next rendered frame. Its actual
server-side placement is recorded in the trace; physics is not paused to stage a
picture of a pending destruction. The before/result pair shows the real visible
outcome. Gallery frames remain separate from functional proof.

The independent catalogue contains 27 power, 76 logic, 15 mechanical and 17
storage/thermal first-process cases, plus the production registry gallery and
retained-state cases after restart. These are **planned counts**, not a success
claim. Consult the Actions run on the exact PR head for actual completion.

Fast gates: `python3 script/test_native_campaign_correctness.py`,
`python3 script/test_native_campaign_report.py`, and
`python3 script/test_native_campaign_startup.py`.
`NativeCampaignOraclesTest` runs in the real NeoForge/JUnit suite.

Full verification uses the reusable `native-client-campaign.yml` workflow:
packaged seed -> hosted M1 shards and same-JAR Linux control -> separate-JVM
restart -> strict aggregate result and screenshot validation. Existing tests
and release prerequisites remain enabled. Do not merge a failed or partial run.

## Additional defect exposed by the actual M1 run

Run 34453471681 on head 8074390a passed the rigid unsafe/safe replacement cases
and iron clutch synchronization. The coal clutch was correctly destroyed, but
its two surviving sides became one network and transmitted power through air.
The retained trace first has `clutchPresent=false, distinctNetworks=false` at
server tick 1665. This is not an intended mismatch hazard or an assertion waiver.

`ShaftNetwork.disconnectShaft` now explicitly excludes the owner being detached
from rebuild traversal, even while its world/node entry remains resolvable in
its destruction callback. Traversal also follows the current port only, rather
than crossing every neighbour of a two-port element and bypassing its internal
connectivity rule. Four graph regression tests exercise the real node lookup
and real rebuild code with inert owners; the real coal-clutch world case remains
required. No speed-mismatch threshold, clutch friction, or hazard is changed.

The same run's power suite passed all twelve converter load/open tests and the
converter GUI edit, then exposed a fixture hit-position error for the flat
source GUI. Camera aim and interaction now use the actual client block-outline
ray hit rather than a fabricated full-cube centre hit.

On that intermediate revision the complete M1 logic shard passed 76 functional,
107 gallery and 20 restart checks; storage/thermal passed 17 functional, 107
gallery and 9 restart checks. Those are historical results, not validation of
a later head. Final review must use the final head's own artifacts.
