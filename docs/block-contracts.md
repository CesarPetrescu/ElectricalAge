# Per-block test coverage

`tools/port/smoke.sh` runs the strict block contracts in both standalone and Create-enabled CI jobs. This is the first increment of the broader behavior-testing plan, not a claim that every machine is fully tested.

## Required on every push

- Discover every registered six-node and transparent-node descriptor by its registry ID. Exercise supported mounting faces/player-view orientations with appropriate floor, ceiling, wall or log support. Account for rotated placement offsets.
- Assert the exact descriptor ID and a block entity, not only a successful interaction or the absence of an exception.
- Remove each descriptor and assert that its node, root block and local multiblock ghost cells disappear.
- Keep one representative placement per descriptor and check its identity after ticking and after a real dedicated-server restart.
- Enumerate native ELN blocks separately. Place ordinary BlockItems; explicitly exempt descriptor hosts and transient lamp/fluid helpers. A new non-placeable native block without an exemption fails.
- Retain the existing hand-written electrical, lamp, cable-inventory, mechanical, multiblock, GUI and Create scenarios. The Create suite checks braking after both an RPM reduction and a gear reduction on all six output directions.

The older overview grid remains for visual inspection, but its permissive placement count is **not** the gate: `BlockContracts` is.

Reports are `build/smoke-artifacts/contracts/*.json` and JUnit XML. GitHub's job summary lists each block/descriptor ID with its passed, failed and skipped counts. A missing, empty, malformed, incomplete or failing required report fails CI. Every skipped check includes a reason. Reports and logs upload even when a run fails. Each launched Gradle/game process has a 25-minute timeout; the smoke job has a 90-minute limit.

Local sequence (start from a fresh disposable smoke world, never an actual play world):

```sh
./gradlew runServer -PsmokeTest=all
./gradlew runServer -PsmokeTest=verify
python3 script/check_block_contracts.py
```

Add `-PwithCreate` to test the integration. `-PsmokeTest=place` still runs only the smaller hand-written scenarios; it does not satisfy the CI contract-report gate. World identity fixtures are recorded in `eln-contracts.json` inside the test save.

## Extended runs

The nightly run at 02:23 UTC, or a manual CI run with **extended** selected, additionally replays the pre-break snapshot through two more dedicated-server restarts and captures a named screenshot for every retained block/descriptor. Client screenshots use fixed camera offsets, daylight and clear weather. The gallery verifies that each PNG was written; images are review evidence, **not** proof of visual correctness. They are available in the normal smoke artifacts as `smoke-block-<registry-id>.png`.

The repeated restart checks replay a saved snapshot because the existing behavior verifier intentionally breaks shafts. They are not a chunk-unload stress test. Scheduling and the existing standalone/Create matrix use [GitHub workflow syntax](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax). Nightly runs never publish a release. Push/manual release publishing still requires build, benchmarks and both smoke jobs.

## Deliberately still untested

The report marks per-descriptor behavior, exact survival drops/inventory contents and settings/chunk-only reloads as pending. Generic removal does not exercise survival loot. Restart **identity** does not prove inventory or setting preservation. Native block placement currently uses a single mounting face; adapter orientations have separate six-direction tests.

Next increments should introduce explicit family fixtures and expected outputs: wire conduction/overload, signal inputs, powered recipes with exact item counts, battery energy conservation and multiblock invalidation. Add settings/inventory snapshots, actual chunk-unload/reload tests and multiple-player synchronization. [NeoForge 1.21.1 GameTest](https://docs.neoforged.net/docs/1.21.1/misc/gametest/) can host isolated generated cases as those fixtures are added; the current suite retains the existing server harness and does not alter the Gradle setup.

Visual regression still needs reviewed golden screenshots and a pinned rendering environment; do not automatically approve newly captured images as baselines. The adapter JSON has a separate unit test rejecting overlapping coplanar outward faces to catch its reported z-fighting mechanically. A clean installed-game launch of the packaged release JAR also remains separate work: current smoke runs launch the development runtime, while publication uses the build job's JAR.

Client model/texture diagnostics naming ELN resources fail the asset-log gate. Three pre-existing missing baked item models (`conduit`, development-only `conduitsingle`, `isolation_transformer`) are explicitly recorded as unfixed debt in `client-assets.json`; they are not reported as fixed or visually correct. The log gate is not an exhaustive render validator.
