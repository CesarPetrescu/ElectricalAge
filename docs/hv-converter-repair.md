# HV wiring and converter repair — draft verification record

Base: `CesarPetrescu/ElectricalAge` at `4ff68e073b588e4711cd1575620994efc5acd33d`.
Work branch: `fix/hv-converters-20260909` (local source snapshot only).
Status: **implemented draft, partially verified; not release-ready or merged**.
No GitHub branch or PR was published by the authoring environment.

## Implemented changes

### Electrical solver and transfer lifecycle

Voltage sources have an actual open state: the disabled branch constrains auxiliary current to
zero, not terminal voltage to zero. The Thevenin probe saves/restores the source's commanded
voltage and enabled flag and does not write committed node/current states. Its driving-point
conductance comes from the actual linear MNA inverse response, avoiding cancellation in a small
finite-difference probe on a high-ratio circuit. Singular/nonfinite probes return an explicit
invalid result; older generic `solve` callers retain their zero fallback.

Reversible transformers retain fixed-ratio, signed-current coupling. The ordinary fixed
one-way converter now also enforces the fixed ratio under load instead of acting like a voltage
regulator referenced to the unloaded supply. One-way fixed/isolation devices block reverse
transfer and trip when protected limits are exceeded. Regulated buck/boost/buck-boost profiles
respect actual input/output current, voltage, source-sag and gain constraints. Runtime electronics
are ideal (efficiency 1); copper losses are electrical resistors, not a second efficiency penalty.

The root validates real split-port power/current residuals before committing a step. Coupled
iteration is bounded to 32 correction passes. A scaled depth-one Anderson accelerator handles
slowly converging high-ratio chains. Extrapolated commands are NEVER accepted directly: a fresh
controller solve must restore a topology-valid point, followed by power/current checks. Trial
solves do not advance capacitors, inductors, heating, or fault dose. A non-converging connected
converter group opens and retries after one simulated second; unrelated networks remain online.
This is not a general-purpose nonlinear circuit solver or a proof for arbitrary modpacks.

### Controls, construction, and windings

New variable devices start at manual ratio 1:1. One-way regulated variants also offer a manual
INTERNAL voltage target. It is intentionally labeled internal: external winding voltage drop is
not hidden or compensated. External logarithmic mapping has 1:1 at the bidirectional midpoint
and reaches 1/256..256. A buck cannot request gain >1 and a boost cannot request gain <1.

Missing saved control tags restore each original external linear formula, including the 50x
legacy one-way boost limit. Missing protection tags restore unprotected legacy behavior; newly
placed one-way devices enable protection. Nonfinite/out-of-range requests are rejected. Invalid
saved control profiles remain faulted across another save until explicitly reconfigured.
Server configuration packets additionally require matching node/element identity, a live nearby
non-spectator player, and the same world. This is not a claim of integration with third-party land
claim/permission mods.

Legacy linear profiles keep the equal-winding-length construction rule. V2 profiles use flexible
valid-winding construction; fixed-ratio devices retain their quantity-defined ratio. Construction
migration is currently tied to the mapping profile, not a separate magnetic-geometry version.
Actual turn count and mean turn length have NOT been implemented separately from legacy cable
amount. Multicore inserts use one effective electrical core, not all cores in parallel; advanced
series/parallel winding arrangements remain outside this patch.

Each winding now has an explicit resistor between external terminal and internal conversion
port. Utility winding resistance uses material, per-core area, actual cable length and absolute
temperature. Endpoint epsilon resistance remains numerical, not a modeled contact loss. Heating
accumulates accepted-step I²R joules and flushes pending energy on reconnect/save. The common
enthalpy model preserves latent heat and separates jacket damage from metal fusion.

An explicit gameplay coil-packing factor exposes 25% of equivalent straight-wire cooling.
Removed-coil heat remains in the winding assembly so inventory swaps do not erase energy. Heat
is not yet transported in the removed inventory item. Insulated winding voltage checks use the
inserted cable rating, capped by the legacy 120 kV assembly limit. Bare/old winding support keeps
the explicitly documented legacy 120 kV assumption; no detailed turn-to-turn dielectric geometry,
magnetic saturation, core-loss curve or switching ripple is claimed.

### Cables, faults, and survival acquisition

15 copper single-core variants use explicit IDs 2368..2382, in already reserved six-node group 37.
The existing 152 utility descriptors (2176..2327) retain their registration order, names and IDs.
A source-derived baseline manifest and real FML registry assertions cover the old identities.

| Conductor | Current gameplay rating | New insulation classes |
|---|---:|---|
| 12 AWG / 3.309 mm² | 20 A | 1, 5, 20, 40, 150 kV |
| 8 AWG / 8.366 mm² | 40 A | 1, 5, 20, 40, 150 kV |
| 2 AWG / 33.631 mm² | 100 A | 1, 5, 20, 40, 150 kV |

Insulation does not change copper resistivity or add fictitious current capacity. The wire
insulator exposes a shared server/client choice list; legacy output remains first. The new
classes cost 2/4/8/12/24 times the ordinary rubber budget for the SAME metal length. Recipe/wiki
examples are generated from that same cost helper. These are gameplay classes, not certified
real-world cable products. Their thermal jacket geometry remains the existing generic model.

A new dielectric failure selects and persists the worst core pair or core-to-ground stress.
It adds one finite 10-ohm gameplay fault resistor rather than instantly joining all conductors.
Its actual I²R loss enters wire heating. Example: +800/-800 V cores stress a 1 kV cable by 1.6 kV.
A 25% overload accumulates one second of simulated exposure; 50% overload trips immediately.
Those are declared gameplay timing assumptions, not calibrated dielectric-aging measurements.
Faults remain visible in Jade and the cable rendering after voltage falls. Legacy saved `bound`
cables stay damaged; thermal melting can still merge cores as a separate physical transition.

HV-rated multicores, detailed overhead clearances, dedicated terminal/switch/fuse HV variants,
and changing every existing device rating are NOT part of this draft. Use appropriately rated
components throughout; a 5 kV cable does not upgrade a 600 V machine.

## Executed verification

The environment recovered the exact baseline source archive and existing CI jar via GitHub
Actions artifacts. Baseline run 34264096765 is source/dependency provenance ONLY; its successful
CI does not validate these changes. Local Git history starts at a snapshot commit, not the
original upstream history.

| Suite | Executed result | Scope |
|---|---|---|
| Native MNA regression | 2,130 assertions passed | Includes 1,000 seeded reversible-transformer networks, open-source/capacitor behavior, non-mutating probes, invalid/singular inputs and lifecycle |
| Actual one-way runtime + RootSystem | 941 assertions passed | Fixed/boost/buck/buck-boost, 250 seeded regulated networks, limits, explicit winding terminal losses, 50→3200→200 chain, precharged output, supply loss, independent isolated references and source-free loops |
| Feature regression | 1,124 assertions passed | Mapping/control wire protocol, scalar-NBT model round trips, enthalpy/thermal changes, selective real-MNA fault branch, 800 V at 1 kV rating, catalogue definitions |
| Existing assertion methods | 126/126 passed | Original MNA/one-way-math/wire assertion bodies via offline adapter; not JUnit engine execution; two benchmark/profiling classes excluded |
| Changed Kotlin syntax | Passed | Local Kotlin 1.9 parser only; not Minecraft type checking |
| Old allocation-loop comparison | Passed | Source-derived 152-entry identity manifest; not live registry execution |

New tests are also wired into normal Gradle/FML discovery through `HvRepairRegressionTest` and
`HvRegistryIntegrationTest`. The two registry/manufacturing integration methods were WRITTEN,
not executed in this environment. Offline NBT support is a scalar map, not a Minecraft saved
world or serialized tag-file implementation. The offline harness compiles actual repository
solver/controller/physics source; only platform logging/metrics/NBT integration is substituted.

### Failures found and fixed while testing

A finite-difference Thevenin probe initially failed the strict power residual at a high step-down
ratio when stored node voltage was stale. The probe now uses the linear MNA response directly.
A 50→3200→200 chain initially exceeded the bounded ordinary fixed-point iteration budget. The
trial-only Anderson step resolves the tested chain without weakening the acceptance tolerance.
Code review corrected cable faults to reconnect their owning six-node, fixed a GUI constructor
call mismatch, and made controls-only config copying recompute state even without inventory edits.

### Blocked / not executed

`bash gradlew test --no-daemon` fails downloading Gradle 9.2.1 with
`java.net.UnknownHostException: services.gradle.org`. Therefore the FULL mod build, Kotlin 2.4
Minecraft type check, FML/JUnit run, translation generation, live registry test, server/client
playtest, GUI resize test, world save migration, companion-mod run and TPS benchmark remain
unverified. No runnable replacement mod jar is supplied or claimed.

The GitHub connector exposed read-only actions and the container had no authenticated GitHub
CLI. No push, PR creation, or new CI dispatch occurred. A publishing helper in the delivery bundle
performs clean/base checks, applies the patch, requires successful full build/translation/test,
then commits/pushes a new branch and opens a DRAFT PR from an authenticated development checkout.

## Required merge gates

Run `./gradlew generateLangFiles test build`, review/commit generated translations, and require
normal CI including benchmarks, real server/client smoke, multiplayer and companion profiles.
Do not skip old world/registry or manufacturing conservation failures.

In a disposable world, exercise manual controls, both signal profiles, +800/-800 V multicore
faults, rated 800/3200/12800/30000/120000 V lines, source disconnect/capacitor discharge, overload,
all orientations, config-copy access, cold/hot inventory changes, chunk unload and JVM restart.
Confirm native menu layout at supported GUI scales and that new wire choices are craftable.
Repeat powered/unpowered multi-converter networks with real sources/machines and inspect both
power residuals and performance. Investigate unsupported truly floating isolated islands: this
patch does not introduce a universal floating-reference/gauge solver.

Back up worlds and update server and client together: converter/cable telemetry packet layouts
have changed. Existing circuit behavior can change because winding copper losses are now modeled
rather than being fixed arbitrary resistances. Identity preservation does not promise identical
losses or temperatures in old installations. Keep this work DRAFT until all gates pass.
