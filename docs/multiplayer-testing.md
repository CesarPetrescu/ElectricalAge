# Multiplayer: support, evidence and remaining coverage

Electrical Age runs its simulation on the server and sends machine state to clients. Install the **same ELN build, Minecraft 1.21.1, NeoForge 21.1.249 and Kotlin for Forge 5.12.0** on the server and each client. Optional gameplay integrations such as Create must also match.

This is still a development port. Passing these checks is useful evidence, not a promise that every block, modpack or large server is bug-free. Back up existing worlds and try a disposable world with two players before moving a survival server.

## What the dedicated multiplayer job proves

The multiplayer work also fixed floor/volumetric monitor selection: a hit on the cube's top or side must select the descriptor mounted on its bottom. Previously that could select an empty face and silently fail to open the monitor. Interaction, pick-block and breaking now share the same body-selection rule.

`Packaged multiplayer contracts` runs twice: **standalone** and **with Create 6.0.10**. Each job installs a clean NeoForge runtime, copies the exact `mod-jar` build artifact into three separate game directories, then launches:

- One dedicated server JVM.
- Two independent Minecraft client JVMs, with distinct player names and UUIDs.
- A second dedicated server JVM against the saved world for the restart checks.

Every process checks that it loaded the expected ELN JAR's SHA-256. Clients assert that they have no integrated server. No development classes or Gradle `runClient`/`runServer` classpaths are used for this job. The loopback-only CI server uses offline authentication for synthetic test identities; **do not copy that setting to a public server**.

| Contract | Assertions |
|---|---|
| Independent controls | Alpha's wrench press does not affect Beta; release is independent; disconnect/reconnect clears a held key. Production key packets and the interaction consumer are exercised. |
| Working electrical circuit | A source, two cables, a 12-ohm load and ground conduct; a client's source-setting packet changes the actual server voltage/current and both clients' rendered source state. |
| Initial and late state | The second player joins after a setting change; both receive the right descriptor, cable connections and monitor samples/settings. |
| Shared monitor | Both players open the same real menu; a real Print-button click consumes exactly one paper. Repeated requests cannot overwrite/duplicate the print. Concurrent vanilla output transfers leave exactly one print across player inventories. |
| Create adapter | With both menus open, one player disengages, the other selects a different gear, and both menus and block entities agree. The live Create motor drives the ELN output to the selected speed. |
| Dimension travel | Both clients travel to the Nether and return; ELN source/monitor state synchronizes again. |
| Chunk reload | Both players move away; the test waits for actual server **and** client chunk unload, then returns and verifies the circuit and client state. It does not force-load the fixture to pretend a reload occurred. |
| Server restart | Settings, monitor data and the single printed item survive a fresh dedicated-server process; client state and the Create gear/speed are checked again. |

The two headless windows cannot both own native keyboard focus. The isolation test therefore sends the same production key payload each client normally sends. It does not claim to test physical keyboard or operating-system focus events. The focus-loss cleanup also releases a held modifier when opening a GUI or leaving the window.

## Reports and release gate

Download `multiplayer-standalone` and `multiplayer-create` from the CI run. Each contains:

- `report.json` and `junit.xml`, with a named result for each client/server contract.
- The tested JAR/dependency checksums in `runtime.json`.
- Per-JVM logs, crash reports, and screenshots at client checkpoints.

Missing results, failures, skipped results, duplicate identities, a wrong JAR, an integrated-server substitute or a missing real restart fail the job. Client inventory totals are checked independently against the expected single print. Screenshots are evidence for review, not a substitute for state assertions.

The rolling release waits for **both multiplayer profiles and all existing required jobs**. A failed run does not replace the last passing release.

The orchestration entry point is `script/run_multiplayer.py`; its process/file actions run only on GitHub's Linux runners. The small validator tests can run locally with `python script/test_multiplayer.py` without launching Minecraft. Existing broad block tests still use dedicated-server smoke tests plus a separate **single-player copy** for their client gallery; those are not mislabeled as multiplayer tests.

## What still needs more testing

- This is representative cross-client coverage, **not a behavior test for every descriptor**. Batteries, every machine GUI, wireless networks, cooler UI, computer mods and all lamp variants need their own two-player scenarios.
- Custom/datapack dimension identifiers are not covered by the vanilla Nether/Overworld trip. The legacy integer dimension mapping needs a dedicated review before claiming arbitrary dimension support.
- Internet latency, packet loss, many-player load, long-running worlds, permissions/claim mods and client-only rendering mods are not covered.
- Screenshots are not approved pixel-diff baselines; appearance still needs human review.

For a manual acceptance test, use two clients on a disposable dedicated server: build a loaded generator/battery circuit, change the same machine's settings from both clients, move items simultaneously, disconnect one player, leave/revisit the chunks, then restart the server. Check that both displays agree and that no items, energy or connections disappear or duplicate. Include the build hash, mod list, both client logs and the server log in a bug report.
