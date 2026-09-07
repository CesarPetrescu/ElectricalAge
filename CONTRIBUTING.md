# Contributing to the Minecraft 1.21.1 port

Thanks for helping improve Electrical Age. This guide is for **CesarPetrescu/ElectricalAge**, targeting **Minecraft 1.21.1 / NeoForge** on `main`, not the legacy upstream build.

## Report a bug or propose a feature

Use [this repository's issues](https://github.com/CesarPetrescu/ElectricalAge/issues). For a bug, include:

- The Electrical Age release/commit, Minecraft, NeoForge and Java versions.
- Kotlin for Forge and any relevant optional integration versions.
- Clear reproduction steps, expected behavior and actual behavior.
- Whether it happens in a fresh test world and with only the required dependencies.
- Relevant `logs/latest.log`, crash reports and screenshots. Remove private information or credentials before uploading logs.

For larger changes, discuss the design before implementation. A small reproduction world or regression test is especially useful. Keep an original world backup; don't experiment on your only copy.

## Set up a working copy

Install **Git and a full JDK 21**, set `JAVA_HOME`, and fork this repository on GitHub. Clone **your fork**, then create a branch from its up-to-date `main`:

```sh
git clone --branch main https://github.com/YOUR-USERNAME/ElectricalAge.git
cd ElectricalAge
git switch -c fix/short-description
```

Replace `YOUR-USERNAME` with your GitHub username. Use the included wrapper:

```sh
./gradlew build
./gradlew runClient
```

On Windows PowerShell, use `.\gradlew.bat build` and `.\gradlew.bat runClient`. For Create integration work, use `runClient -PwithCreate`. Development directories are `run/client/` and `run/server/`.

**Do not run `setupDecompWorkspace`: it belongs to the old build and is not part of this port.** See the [README](README.md#build-from-source) for the full command reference and dependency information.

## Code and content guidelines

- Prefer Kotlin for new code. Kotlin belongs in `src/main/kotlin`; Java belongs in `src/main/java`.
- Keep registry names and existing descriptor IDs/sub-IDs stable. They are saved in worlds; do not renumber unrelated content.
- Follow nearby code conventions and keep unrelated formatting/refactors out of a focused fix.
- The port uses explicit source/test include lists in `tools/port/include-1.21*.txt`. Check that new files are actually compiled, and use `portStatus` when investigating coverage.
- Keep optional integrations optional. Verify that ELN still starts without Create or other integrations affected by your change.
- Discuss Gradle configuration, wrapper or dependency-version changes before changing them.
- Put technical documentation under `docs/`. When changing generated resources, run the relevant generator and review its output.

## Tests and visual changes

Run `build` for code changes and `benchmarkTest` for changes affecting simulation performance. Add a regression test that fails without the fix whenever practical.

For in-world behavior, use the [headless smoke guide](tools/port/headless.md) and [block-contract coverage guide](docs/block-contracts.md). These tests require disposable worlds. Do not interpret a skipped contract as a passing test, or an existing screenshot as a behavioral assertion.

For models and GUIs, include before/after screenshots and check relevant orientations, connections and controls. Test adapter changes both with and without Create. Baseline images need review; do not silently replace a visual baseline just to make a comparison pass.

## Translations

Localization sources live in [`src/main/resources/assets/eln/lang`](src/main/resources/assets/eln/lang). To add new user-facing text:

1. Wrap complete phrases in `tr()` and item/block names in `TR_NAME()`.
2. Use positional placeholders such as `%1$` and `%2$` for runtime values. Avoid concatenating translated sentence fragments or interpolating values into translation keys.
3. Run `./gradlew generateLangFiles` (Windows: `.\gradlew.bat generateLangFiles`).
4. Review the generated keys. Preserve placeholders in translations so languages can reorder values correctly.

Edit existing language files directly when fixing or completing translations; use the generator to create new keys. The build converts `.lang` sources into the JSON resources Minecraft loads.

## Pull request checklist

- [ ] Target **`CesarPetrescu/ElectricalAge:main`** and link the relevant issue or discussion.
- [ ] Explain the user-visible change and why it is needed.
- [ ] List the tests/commands run and their results; distinguish failures, skips and untested cases.
- [ ] Include screenshots or reproduction steps for visual/gameplay changes.
- [ ] Preserve registry IDs and document any intentional compatibility impact.
- [ ] Update documentation and generated translation/data files where relevant.
- [ ] Do not commit build output, private credentials, personal configuration or test worlds.
- [ ] Credit asset sources and respect [the code and asset licenses](LICENSE.md).

CI runs on pull requests and does not publish releases from them. Maintainer review and passing checks are both important; a green placement test is not proof that every behavior of a block is correct.
