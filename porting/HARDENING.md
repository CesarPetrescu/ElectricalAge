# Re-Wired hardening work

This isolated harness builds brambora69123/electrical-age-rewired at 3a0088b384aa4111c3a7f984d4cbc5f4ff142ed9 plus the verified baseline patch and incremental hardening changes. The source in the repository root remains the user's 1.7.10 fork; CI checks out the 1.12.2 source separately. Do not mistake a harness branch for an in-place full source port.

Scope: fix defects in the September 5 user logs, audit inventories/networking/simulation/resources, add regression tests, run real build/server/client probes, rescan. No upstream default branches are changed. Full 1.24.8 feature parity is not claimed.

The unrelated 1.7.10 tests workflow is absent on this harness-only branch so these experiments do not launch irrelevant builds. The default branch is untouched.
