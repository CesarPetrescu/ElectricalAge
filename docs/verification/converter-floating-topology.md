# Converter floating/private topology repair — draft verification

Base commit: `bde23a17ac80f3af96ed266c0dd115169aaf4901`.

## Scope and cause

The reported 3.2 kV converter bus/vehicle charger failure is not proof of a
missing physical ground. The inspected AutoPropulsion-Age adapter uses
`ElectricalIntegration.createGroundedResistorSink`, so its load has an internal
return. This ElectricalAge-only patch addresses reproduced solver-level faults;
it does not modify AutoPropulsion-Age or claim an end-to-end vehicle test.

* Accumulate MNA component stamps in double-double precision before inversion.
  Converting an already rounded double matrix to DD could not recover Kirchhoff
  cancellation in open, low-resistance wire networks.
* Probe the affine source-current response directly from the matrix inverse,
  instead of subtracting two large, nearly equal trial currents. Reject an
  injected current into an open port rather than silently discarding it.
* Solve physically connected switchable-converter port networks without the
  private/size partition voltage proxies. Ordinary networks retain their existing
  partition policy. Electrically isolated ports are not joined by this change.
* Seed voltage-regulated converters' trial commands together before sequential
  loaded calculations, to avoid making the first parallel converter carry the
  whole load during startup. Acceptance, current limits, reverse-flow checks and
  the existing energy-balance tolerance remain in force before physical commit.

No hidden grounding resistor, artificial load, or weakened energy tolerance is
introduced. The exact-network path removes the size partition on these networks;
its performance on large worlds is an explicit review requirement.

## Evidence included, and what it actually establishes

The raw logs below were retained from the preceding local investigation on this
base plus these source changes. They are **not** new GitHub Actions results.

| Evidence | Observed result | Limitations |
| --- | --- | --- |
| [Baseline test bodies](converter-floating-topology/new-unit-against-baseline.log) | 5 of 9 new regression test bodies failed against the unchanged solver. | Standalone local runner, not the NeoForge/JUnit launcher. |
| [Patched test bodies](converter-floating-topology/new-unit-bodies.log) | All 9 test bodies passed. | Production matrix/stamps/probes; not a Minecraft world. |
| [Private-load/shared-bus harness](converter-floating-topology/private-load-after-exact.log) | Single and four-converter, public/private, idle and within-capacity cases operated. | Actual controller with lightweight stand-ins for Minecraft container fields; not the external charger/vehicle. |

`ConverterTopologyRegressionTest` includes 100 seeded irregular open wire trees
at 0 V, +3.2 kV and -3.2 kV, a 1e12-ohm idle load, affine probe precision,
unbalanced injected current, conflicting ideal sources, isolated capacitor
history, private API-style load boundaries, unchanged ordinary partitioning,
and disconnect/reconnect lifecycle checks. There are nine test methods; the
100 networks are cases inside one of them, not 100 separate JUnit tests.

## Reproduction

Run with Java 21 and the repository's Gradle wrapper:

```sh
./gradlew --no-daemon test --tests 'mods.eln.sim.power.ConverterTopologyRegressionTest'
./gradlew --no-daemon build
./gradlew --no-daemon benchmarkTest
bash script/test-hv-converters.sh
```

The new `Converter topology regression` workflow runs the first command under
the repository's real test configuration, requires a JUnit XML report containing
at least nine tests with zero failures/errors/skips, and uploads reports, logs,
and the checked-out revision. Existing CI and HV world/restart workflows remain
unchanged. A green focused regression job does not establish vehicle integration
or satisfactory large-network performance.

Publication-session checks: all six uploaded code/test blobs are checked against
the local Git blob hashes; `git diff --check` passed. A new local Gradle attempt
stopped before compilation because `services.gradle.org` could not resolve
(`UnknownHostException`). This is an environment blocker, not a test pass.
Consult this PR's current Checks tab for actual Actions status.

## Merge blockers / remaining acceptance

- [ ] Fix and regress the retained four-converter overload case: at a 10-ohm load,
  both public and private cases still latch `NON_CONVERGENT` instead of settling
  at a reduced, current-limited bus voltage. Do not relabel this as a pass.
- [ ] Promote the controller/shared-bus harness into automated production-runtime
  tests, including within-capacity, overloaded, mismatched and changing setpoints.
- [ ] Run the full NeoForge build, existing unit suite, benchmarks, and in-world
  converter/restart tests on the PR revision; inspect failures and artifacts.
- [ ] Measure exact-network inversion/runtime/memory on large converter-connected
  networks; review whether a more scalable equivalent-network approach is needed.
- [ ] Reproduce the actual AutoPropulsion-Age charger/vehicle configuration without
  an added GND cable; confirm input power, delivered joules and battery increase,
  including connect/disconnect, chunk unload and saved-world restart.

Keep this PR in draft until those requirements are addressed. No release or merge
is requested by this patch.
