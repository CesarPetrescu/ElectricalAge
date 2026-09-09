# PR9 follow-up: parallel limits, native charging and scaling

This supersedes the original draft's implementation checklist in `converter-floating-topology.md`. That file and its logs describe historical evidence, not the current remaining-defect status. Current executed results and artifact links are recorded in PR9's conversation and Checks tab. Do not infer a pass from this description of test coverage.

## Production corrections

- `RegulatedPowerProcess` is the actual modern converter controller used by the placed block and the circuit tests, not a replacement controller in a harness.
- Parallel current-limited buses receive bounded, connected-group Newton correction of the existing controller commands. Every accepted step still satisfies the original reverse-current, current-limit and power-balance tests. Trials never advance physical/thermal time.
- Finite-difference derivatives preserve the current piecewise limiter region: both directions and a shrinking stencil are tried near target/current-limit boundaries. The dynamic 110 A winding + 125 kW sampled charger-load regression reproduces a failure without that correction.
- DD matrix inverse row updates are limited to small changes, checked against the new matrix, and periodically discarded for full factorization. Full factorization is the fallback, not disabled.
- Metrics worker startup/sink replacement no longer synchronizes the normal producer path or lets an old worker generation adopt a new worker's running flag. The existing 1.75 performance-ratio bound remains unchanged. AB/BA paired measurements warm both modes instead of measuring every cold baseline before every warmed instrumented sample.

## Repeatable tests

```sh
./gradlew --no-daemon test
./gradlew --no-daemon benchmarkTest
ELN_ALLOW_DISPOSABLE_QA=1 bash script/test-converter-charger.sh /path/to/pinned/AutoPropulsion-Age
```

The native script refuses an existing world and requires the exact companion commit `daf12579815345470310aed469df3e54eb7c61d4`. It builds that unmodified mod and runs two independent Minecraft server JVMs against the same saved world. It never changes the companion source.

`ParallelConverterRuntimeTest` exercises the production controller, actual stamps and matrix: open/idle output, overload/recovery, changed/mismatched targets, missing supplies, output-current limiting, isolated returns, insertion order, history committed once, seeded changes, and the real 110 A winding rating with voltage-dependent sampled charger demand.

`ConverterChargerSmokeTest` places four production converters, utility cables, an actual ULTRA charger and an actual ELECTRIC_400 car. A fake player invokes normal placement and pairing APIs; electrical components, charger, vehicle, battery and energy integration are real. It checks charging, energy bounds, unplug/replug, actual chunk unload and reload, saved car energy, expired connection leases and re-pairing after a separate-JVM restart. There is no added Ground Cable, dummy load, creative charger supply or substitution of saved objects on restart.

## Capacity is not a numerical error

These native windings have a real 110 A primary limit. Four 300 V inputs provide only about 125 kW after conversion and winding loss; the car requests approximately 125 kW at the charger input. Resistance rises as windings heat, so insisting on 3200 V indefinitely at that loading is incorrect.

The within-capacity fixture uses 400 V inputs with the same windings and limits. It then deliberately drops all four supplies to 241 V. The test requires actual current limiting, no numerical fault latch, and recovery to normal charging after restoring 400 V without resetting converters. It does not require impossible full-power regulation when the supply is insufficient. A charger may pause when its input falls below its supported voltage envelope.

## Performance evidence and scope

The workflow measures 1, 4, 16 and 64 converter networks, including startup, steady p50/p95/p99, overload, recovery and reconnect. It also runs an identical ordinary-network control on base `bde23a17` and the PR revision. Reported JVM heap is total used heap, not per-network allocation.

A reviewed pre-limiter-boundary run at head `8922e0c6` (merge checkout `5721b334`) passed all ten benchmark tests. Its 64-converter fixture had a 258-state matrix, steady p95 5.73 ms, recovery 564 ms and reconnect 870 ms. A separate same-head scaling runner measured 8.05 ms steady p95 and 1250 ms reconnect. These are different measured runs, not interchangeable figures. They improve the local repeatedly-refactorized ~12-second transitions, but still demonstrate a noticeable large-network topology-change pause. This is not a guarantee that arbitrary huge worlds sustain 20 TPS.

Raw source identities, JUnit XML, voltage/current/energy CSV, native contract JSON, server logs, saved-world archive and companion jar SHA256 are uploaded by the read-only acceptance workflow. The first native failure is retained in Actions rather than rewritten as a pass. Its within-capacity assertion failed while real charging and energy conservation already worked; the explicit capacity and transition regressions above address that gap.

No main updates, force pushes, auto-merge or release are part of this work. Temporary source-transport workflows and binary parts are removed from the final PR tree.
