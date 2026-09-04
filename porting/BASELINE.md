# Electrical Age: Re-Wired — 1.12.2 Baseline Validation

**Validation date:** 2026-09-04  
**Validated source:** `brambora69123/electrical-age-rewired`  
**Exact source commit:** `3a0088b384aa4111c3a7f984d4cbc5f4ff142ed9`  
**Exact source tree:** `e68a60b1f2cddf32fd68c41084ff84b4b7f5ea9b`  
**Uploaded ZIP SHA-256:** `e1146618257b4a5d14d1cf45c35f2ca79c07a64edb44e69709e1e2672d1e1fc2`

## Scope

This establishes a reproducible baseline before any `age-series/ElectricalAge` 1.24.8 feature-port work. The uploaded `electrical-age-rewired-main.zip` was compared with the published repository tree and found to represent the same source snapshot. The source was then built and runtime-smoke-tested in an isolated GitHub Actions branch without modifying the user's default branch.

## Environment

| Component | Version |
|---|---|
| Runner | GitHub Actions `ubuntu-24.04` |
| Gradle launcher JDK | Temurin OpenJDK `25.0.4.1` |
| Minecraft runtime/compiler toolchain | Azul OpenJDK `8.0.504` |
| Gradle wrapper | `9.4.0` |
| Minecraft | `1.12.2` |
| Forge | `14.23.5.2847` |
| MCP mappings | `stable_39` |
| LWJGL used by Minecraft | `2.9.4` |

## Result matrix

| Check | Unmodified Re-Wired | With baseline patch |
|---|---:|---:|
| `setupDecompWorkspace` | PASS | PASS |
| `clean build` | PASS | PASS |
| Unit tests | NONE PRESENT | NONE PRESENT |
| Dedicated-server startup | **FAIL** | **PASS** |
| Client startup | Not attributable to source in initial CI probe | **PASS** — Forge and ELN initialized under Xvfb/Mesa llvmpipe |

## Defect found in the unmodified baseline

The dedicated server crashed during `FMLServerAboutToStartEvent`:

```text
java.lang.NoClassDefFoundError: net/minecraft/client/entity/EntityPlayerSP
  at mods.eln.Eln.clearSimulatorState(Eln.kt:308)
Caused by: RuntimeException: Attempted to load class
  net/minecraft/client/entity/EntityPlayerSP for invalid side SERVER
```

`Eln.clearSimulatorState()` directly accessed `NodeBlockEntity.clientList`. Loading `NodeBlockEntity` on a dedicated server pulled in its client-only `Minecraft`/`EntityPlayerSP` references.

## Minimal baseline patch

Patch: `0001-fix-dedicated-server-client-state.patch`  
Patch SHA-256: `cc8ceaa188b8499b79fff041e8ad06fcdd3db9ea894e7166714df0983c9042d6`

The patch changes four files, with 16 insertions and 2 deletions:

1. Adds a no-op `CommonProxy.clearClientState()` for dedicated servers.
2. Implements `ClientProxy.clearClientState()` to clear `NodeBlockEntity.clientList` only on a physical client.
3. Replaces the direct common-side access in `Eln.clearSimulatorState()` with `proxy?.clearClientState()`.
4. Marks `NodeBlockEntity.getEntity(BlockPos)` as client-only.

After applying it, the dedicated server completes initialization and reports:

```text
Done (...s)! For help, type "help" or "?"
Electrical Age server started
```

## Client-probe note

An early client probe failed before any Electrical Age code loaded because the headless runner lacked `xrandr`; LWJGL 2 therefore reported zero available display modes. The final probe installs `x11-xserver-utils`, runs Xvfb with software OpenGL, clears stale server logs, and validates client initialization independently.

## Known baseline problems not fixed here

- There are no test sources: Gradle reports `test NO-SOURCE`.
- Ore Dictionary entries are registered before their items enter the Forge registry, producing repeated “broken ore dictionary registration” warnings.
- First-world startup prints full `NoSuchFileException` traces for expected missing ELN save and backup files before continuing.
- Forge 1.12's scanner reports the multi-release Kotlin stdlib `module-info.class` as unreadable and ignores that entry.
- Several 1.12/Minecraft and Kotlin APIs are deprecated.
- The build uses Gradle features that will not be compatible with Gradle 10 without cleanup.
- This automated baseline proves compilation, dedicated-server boot, and client initialization only. It does not prove machine placement, GUI behavior, save/reload behavior, recipe completeness, ore generation, or full simulation correctness.

## CI records

- Build-only unmodified baseline: GitHub Actions run `33849625931` — PASS.
- Unmodified dedicated-server probe: run `33850241838` — server FAIL, defect above.
- Patched server validation: run `33850808141` — server PASS.
- Independent pre-`xrandr` client probe: run `33851322943` — CI display environment FAIL before mod initialization.
- Final patched build/server/client validation: run `33852038078` — PASS.

## Final build artifacts

| File | Size | SHA-256 |
|---|---:|---|
| `eln-3a0088b-dirty.jar` | 12,019,544 bytes | `884408bd78e0f279f02eacb3813a2dd96ffb2795b87d5db601f2dd8143ec2e28` |
| `eln-3a0088b-dirty-dev.jar` | 11,952,435 bytes | `2b56f779c7304abc689e86d708a11a21bac671bd52755a43da455065e5f11efd` |
| `eln-3a0088b-dirty-sources.jar` | 9,135,065 bytes | `7be7058d2d4bbb1a313323ee28a2651670f18c46080f4f4f5df9631a59a62890` |

The `dirty` suffix is expected: the workflow applies the uncommitted baseline patch to the exact upstream source before building. The client process is intentionally terminated after Forge, textures, sound, and ELN complete initialization; therefore its captured Gradle log ends with a non-zero `runClient` exit caused by the smoke-test shutdown, not an initialization crash. No final client crash report was generated.

## Next engineering step

The next change should not be a bulk 1.24.8 merge. Enable the test source set and port a narrow group of pure MNA/solver tests from `age-series/ElectricalAge`, keeping the verified 1.12.2 platform layer intact.
