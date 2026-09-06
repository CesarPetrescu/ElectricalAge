#!/usr/bin/env bash
# Called only after CI passes on main. The rolling tag is owned by this publisher.
set -euo pipefail

: "${GITHUB_REPOSITORY:?}"
: "${GITHUB_SHA:?}"
: "${GITHUB_RUN_ID:?}"
: "${GITHUB_SERVER_URL:?}"
: "${GH_TOKEN:?}"

current_main=$(gh api "repos/$GITHUB_REPOSITORY/git/ref/heads/main" --jq '.object.sha')
if [[ "$current_main" != "$GITHUB_SHA" ]]; then
    echo "Skipping release: main has advanced beyond this tested commit."
    exit 0
fi

# Use the artifact from the build job, without rebuilding or downloading another run.
mapfile -d '' jars < <(find release-input -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' -print0)
if [[ ${#jars[@]} -ne 1 ]]; then
    echo "Expected exactly one installable mod JAR; found ${#jars[@]}." >&2
    exit 1
fi
mkdir -p release-output
cp "${jars[0]}" release-output/ElectricalAge-1.21.1-latest.jar
(
    cd release-output
    sha256sum ElectricalAge-1.21.1-latest.jar > SHA256SUMS.txt
)

tag=latest-1.21.1
title='Latest 1.21.1 development build'
notes=release-output/notes.md
cat > "$notes" <<EOF
Automated development build for **Minecraft 1.21.1 / NeoForge 21.1.249 / Java 21**.
**Required dependency: [Kotlin for Forge 5.12.0 by thedarkcolour](https://modrinth.com/mod/kotlin-for-forge/versions)**, using the Minecraft 1.21.1 / NeoForge-compatible build. Download it separately and install its JAR alongside Electrical Age on both the client and dedicated server.

**KotlinLangForge by btwonion is a separate mod and is not required by Electrical Age.** You do not need both Kotlin mods for this port; KotlinLangForge does not replace its required Kotlin for Forge loader.

Use a fresh world; legacy saves are not migrated.

Source commit: [$GITHUB_SHA]($GITHUB_SERVER_URL/$GITHUB_REPOSITORY/commit/$GITHUB_SHA)
Passed build, unit tests, benchmarks, and server/restart/client smoke tests: [CI run]($GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID).

Download **ElectricalAge-1.21.1-latest.jar** below and place it in your mods folder.
SHA256SUMS.txt contains its checksum. Screenshots and test reports are attached to the CI run.
This rolling release is automatically updated by newer passing builds from main.
EOF

if gh release view "$tag" --repo "$GITHUB_REPOSITORY" >/dev/null 2>&1; then
    gh api --method PATCH "repos/$GITHUB_REPOSITORY/git/refs/tags/$tag" \
        -f "sha=$GITHUB_SHA" -F force=true --silent
    gh release upload "$tag" --repo "$GITHUB_REPOSITORY" --clobber \
        release-output/ElectricalAge-1.21.1-latest.jar release-output/SHA256SUMS.txt
    gh release edit "$tag" --repo "$GITHUB_REPOSITORY" \
        --title "$title" --notes-file "$notes" --prerelease=false --latest
else
    gh release create "$tag" --repo "$GITHUB_REPOSITORY" --target "$GITHUB_SHA" \
        --title "$title" --notes-file "$notes" --latest \
        release-output/ElectricalAge-1.21.1-latest.jar release-output/SHA256SUMS.txt
fi

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    printf '\nPublished [latest 1.21.1 development build](%s/%s/releases/tag/%s) from `%s`.\n' \
        "$GITHUB_SERVER_URL" "$GITHUB_REPOSITORY" "$tag" "$GITHUB_SHA" >> "$GITHUB_STEP_SUMMARY"
fi
