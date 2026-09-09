# Hosted M1 native Minecraft component campaigns

## Scope

This suite launches the packaged ElectricalAge JAR in a real Minecraft 1.21.1
client on GitHub's `macos-15` ARM64/M1 runner. One client and its integrated
server run per shard, followed by another Java process opening the same saved
world. The seed world is created by the existing `smokeTest=all` server fixture.
Production solvers, machines, client renderers and menus are not mocked. Source,
JAR/world hashes, process IDs and a run nonce are validated before acceptance.

| Suite | Expected functional cases | Examples |
|---|---:|---|
| Power | 29 | Seven converter variants, loaded/open, two native GUI transactions, parallel overload/recovery, missing/unequal supplies, eight seeded load changes |
| Logic | 76 | Independent truth tables, Schmitt hysteresis, D/JK transitions, oscillator pulses, eight physically wired NOT gates |
| Mechanical | 6 | Loaded motor/shaft/joint/tachometer/flywheel/generator, coasting energy, split/reconnect, large motor/generator and ghost removal |
| Storage/thermal | 17 | Seven battery variants discharging/open, fuel-to-thermal-to-electric chain, thermal link break/reconnect |

These counts are the expected plan, not evidence of a passing run. Actions must
produce every named result and its genuine framebuffer PNG. Passing counts are
always associated with an exact run, revision and artifact.

Each entry in the registered fixture catalogue is assigned to exactly one M1
shard for server identity, client renderer presence and an individual screenshot.
`component-coverage.csv` distinguishes named functional cases from GALLERY ONLY.
A picture does not prove every configuration, recipe, inventory, UI or physical
behaviour. Existing Linux unit, world, native-menu, compatibility, multiplayer
and charger checks remain enabled. This does not exhaust every possible circuit.

## Hosted M1 graphics: measured limitation and narrow workaround

The first unmodified GLFW launch failed before the title screen with
`NSGL: Failed to find a suitable pixel format`. A real CGL probe reported
`Apple Software Renderer`, OpenGL 4.1 and `accelerated=0`. GLFW 3.4 requests
`NSOpenGLPFAAccelerated` unconditionally on Cocoa.

`script/build_macos_software_gl.sh` fetches upstream GLFW commit
`7b6aead9fb88b3623e3b3725ebb42670cbe4c579` and makes that one constraint optional
under `ELN_QA_APPLE_SOFTWARE_GL`. The macOS NSGL context/window/input path and
Apple OpenGL implementation remain in use. The JVM selects the compiled ARM64
library using `org.lwjgl.glfw.libname` and uses `-XstartOnFirstThread`.

This is software rendering on a hosted M1, NOT M1 GPU acceleration or a promise
of faster graphics than Linux. The graphics probe, renderer, upstream diff,
binary hash and dependencies accompany the results. No paid M2/xlarge or
self-hosted machine is selected. An earlier Mesa experiment never compiled and
was superseded; it is not the backend used by this suite.

## Actions

`ci.yml` calls the reusable `native-client-campaign.yml`; publication depends on
its result. The workflow builds one artifact, validates the report gate, creates
an actual registered world, and records immutable source/JAR/world hashes.

Four M1 jobs open separate copies of the world, execute their cases, record the
catalogue gallery and save non-default fixture state. Another JVM checks retained
identity, converter settings, logic state and battery energy bounds. A Linux job
runs the same power plan with the same JAR and initial world as a control. Apple
software GL and Linux llvmpipe are explicitly different renderer implementations.

The final job revalidates every artifact and creates the offline screenshot index.
Missing, duplicate, stale, skipped or failed results, omitted descriptors, unsafe
paths, absent/blank PNGs and incorrect hashes fail the gate. The expected case
catalogue is independent of the runtime's self-reported plan. No gameplay assertion
is automatically retried into a pass. Installer/network retries are separate.

Normal PR and main CI, the existing nightly schedule, and manual dispatch invoke
the suite. All permanent test jobs use read-only repository permissions.

## What the client actually does

An opt-in JVM-property-gated subscriber is inactive during ordinary gameplay.
Server fixture actions use real placement/configuration APIs and production
simulation. Declared creative sources/resistors are controllable boundary
conditions, not injected energy inside a tested converter or battery. Measurements
execute on the authoritative server thread.

The native converter and source GUI cases open the real menu, focus its actual
text field, send key/character events and press Enter, then verify the accepted
server setting and physical output. Other fixture construction uses server
placement APIs: it is not claimed as end-to-end client mouse placement. The
client verifies synchronized renderers and shaft animation, then captures its
actual framebuffer. Screenshots are inspection evidence, not golden-image or
artistic-quality certification.

New per-suite restart tests use different JVMs and the same saved world. Actual
chunk unload/reload and two-client networking remain in existing dedicated tests;
do not describe these new restart checks as active chunk-unload coverage.

## Evidence

Download `native-client-screenshot-index`, extract it, and open `index.html`.
The offline page filters captures by component, case ID, suite, runner and kind.
Each card links the unchanged PNG to measured observations. Included files:

- `summary.json`: exact identity, validation, counts, errors and timing.
- `component-coverage.csv` / `.json`: every descriptor and functional coverage gaps.
- `overview.jpg`: labelled montage of real frames; originals remain in each
  suite's `first/screenshots` and `restart/screenshots` directories.
- Per-suite traces, performance JSON, logs, crash/thread reports and a saved
  `reproduction-world.zip`.

Failure reports also have an index when possible, but remain failed. An existing
HTML/ZIP or zero client exit code alone is never a pass.

## Performance and reproduction

Client frame intervals at a 60 FPS cap and integrated-server Pre/Post timings are
recorded separately, alongside startup/total time and used JVM heap. They include
instrumentation, camera travel, screenshots and GUI waits. Heap is not per-network
allocation. A single hosted sample is not a stable CPU/GPU benchmark or a blanket
20 TPS guarantee.

The input artifact contains its manifest, JAR, initial world and source archive.
With Java 21, Python 3.12, `minecraft-launcher-lib==8.0` and `Pillow==11.3.0`:

```sh
python3 script/run_native_campaign.py --jar /path/to/ELN.jar \
  --world /path/to/seed-world.zip --suite power
```

Use a fresh checkout/work directory per run. On a hosted-like Mac without an
accelerated context, run the GLFW helper and export the variables written to
`GITHUB_ENV` before Java. A normal Mac with GPU should use its original runtime;
this opt-in helper is not installed into ELN or recommended to players.

Synthetic worlds and offline test usernames remain local to the runner. No player
credentials, release tokens or production worlds are used.
