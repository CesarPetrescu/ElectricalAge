# Complete-source Electrical Age migration to 1.21.1

This is a WORK-IN-PROGRESS source port, not the five-block prototype and not a playable release.

- Start with ALL code/resources from Re-Wired at `3a0088b384aa4111c3a7f984d4cbc5f4ff142ed9`.
- `src/` is the actual editable compiler input, not a reference-only archive.
- `reference/rewired-1.12.2/` is an untouched copy for comparison.
- The older maintained age-series source remains at the outer repository root.
- `../1.21.1/` is the previous isolated prototype. This build does not compile or include it.
- Only `Eln_old.java` is excluded, exactly as in Re-Wired's original build.
- Compilation failure remains a failing CI result. Never stub devices, remove sources, or package placeholders to make it pass.
- Compilation is the first gate, followed by content-registry parity, all asset loading, dedicated-server/client loading, save and machine behavior tests.

Build with Java 21:

```sh
cd ports/1.21.1-full
python3 tools/compile_report.py --label local
```

The full compiler log and grouped reports are in `build/migration/local/`.
The source import is a one-time operation, not something to rerun after editing.

This import begins with pristine Re-Wired, not the separately delivered 1.12 audit candidate. Existing audit patches must be reviewed and ported explicitly; their prior test counts do not validate this source tree. Nothing is claimed ported because it is merely copied.
