# PR #6: staged high-voltage validation

This is an execution ledger, not a claim that unexecuted tests passed. Keep PR #6 in draft until the required gates are verified on its latest commit.

## Source identity

The feature branch is `fix/hv-converters-2026-09-09`. Every published test result must identify the checked-out commit (`git rev-parse HEAD`) and workflow run. A workbench archive is not evidence for the current PR unless its complete source identity matches.

## Gates

1. **Build and FML tests** — compile the complete mod and run the existing JUnit/FML suite. Preserve failing assertions. Investigate the two observed length-conservation failures (expected 1 m, observed 128 m); verify whether the workbench payload matches the PR before changing working code.
2. **HV runtime** — actual registered converters/cables at 800 V, 3.2 kV and 12.8 kV, loaded chains, source removal, reverse blocking, overload and selective insulation faults. Require non-empty machine-readable reports, not just a server reaching its title/startup state.
3. **Persistence and migration** — saved-world restart in a separate JVM, old descriptor identity preservation, spool length and mass conservation, persisted faults/settings/heat.
4. **Native client and GUI** — existing menu/orientation coverage plus actual interaction with the changed HV controls and manufacturing selection. Preserve screenshots and logs.
5. **Compatibility and multiplayer** — execute the existing standalone, Create and companion-mod matrices; require their runtime assertions.
6. **Performance and regression** — run the existing benchmark suite and inspect solver convergence/energy residuals. Record measurements; do not invent performance thresholds after seeing the results.

## Observed starting point

The workbench job `102289088981` in run `34294836639` reached 202 tests and reported two spool-length failures. That job extracted `/tmp/eln-hv-step07-src`; it did not by itself establish that the feature branch HEAD was tested. The current feature source already uses `stack.editTag` in `setRemainingLengthMeters`, so an additional copy-on-write change is not justified without a matching-source reproduction.

## Completion rules

- Distinguish passed, failed, running, skipped and not executed.
- Link complete logs and test artifacts to the exact commit.
- Re-run downstream gates after relevant fixes; a green older commit is not a green latest commit.
- Do not merge or force-push as part of verification.
