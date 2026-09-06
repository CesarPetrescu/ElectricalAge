#!/usr/bin/env bash
# Exercise publishing decisions with a fake gh; never contacts GitHub.
set -euo pipefail
publisher="$(cd "$(dirname "$0")" && pwd)/publish-latest.sh"
test_dir=$(mktemp -d)
export GITHUB_REPOSITORY=CesarPetrescu/ElectricalAge
export GITHUB_SHA=0123456789012345678901234567890123456789
export GITHUB_RUN_ID=123 GITHUB_SERVER_URL=https://github.com GH_TOKEN=test-only

gh() {
    printf '%s\n' "$*" >> "$PWD/gh-calls.log"
    if [[ "$1 $2" == "api repos/$GITHUB_REPOSITORY/git/ref/heads/main" ]]; then
        printf '%s\n' "${TEST_MAIN_SHA:-$GITHUB_SHA}"
    elif [[ "$1 $2" == 'release view' ]]; then
        [[ "${TEST_RELEASE_EXISTS:-0}" == 1 ]]
    fi
}
export -f gh

for scenario in create update stale missing multiple; do
    mkdir -p "$test_dir/$scenario/release-input"
    (
        cd "$test_dir/$scenario"
        export TEST_RELEASE_EXISTS=0 TEST_MAIN_SHA="$GITHUB_SHA"
        case "$scenario" in
            update) export TEST_RELEASE_EXISTS=1 ;;
            stale) export TEST_MAIN_SHA=newer-commit ;;
        esac
        if [[ "$scenario" != missing ]]; then
            printf 'test artifact\n' > release-input/mod.jar
        fi
        if [[ "$scenario" == multiple ]]; then
            printf 'ambiguous artifact\n' > release-input/another.jar
        fi
        if [[ "$scenario" == missing || "$scenario" == multiple ]]; then
            if bash "$publisher"; then
                echo "Expected rejection of $scenario artifacts" >&2
                exit 1
            fi
            ! grep -q '^release ' gh-calls.log
        else
            bash "$publisher"
            if [[ "$scenario" == stale ]]; then
                ! grep -q '^release ' gh-calls.log
                [[ ! -d release-output ]]
            else
                cmp release-input/mod.jar release-output/ElectricalAge-1.21.1-latest.jar
                (cd release-output && sha256sum -c SHA256SUMS.txt)
                grep -q "$GITHUB_SHA" release-output/notes.md
                if [[ "$scenario" == create ]]; then
                    grep -q '^release create latest-1.21.1 ' gh-calls.log
                    ! grep -q 'api --method PATCH' gh-calls.log
                else
                    grep -q 'api --method PATCH.*git/refs/tags/latest-1.21.1' gh-calls.log
                    grep -q '^release upload latest-1.21.1 .*--clobber' gh-calls.log
                    grep -q '^release edit latest-1.21.1 ' gh-calls.log
                fi
            fi
        fi
        echo "PASS: $scenario"
    )
done
echo "Publishing checks passed. Fixtures: $test_dir"
