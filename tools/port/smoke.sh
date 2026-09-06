#!/usr/bin/env bash
# The whole smoke suite, in order, stopping at the first failed run (each run exits 1 on a
# failed check): every descriptor placed and a circuit, a lamp and the computer probe
# verified; the same after a restart; the client's screenshots of that world.
#
#     tools/port/smoke.sh            # needs an X server on DISPLAY for the client run (see headless.md)
#     SKIP_CLIENT=1 tools/port/smoke.sh
set -euo pipefail
cd "$(dirname "$0")/../.."
source tools/port/env.sh >/dev/null 2>&1 || true

mkdir -p run/client/saves build/smoke-artifacts
gradle_args=()
if [ "${WITH_CREATE:-0}" = 1 ]; then gradle_args+=(-PwithCreate); fi

rm -rf run/server/world
./gradlew runServer "${gradle_args[@]}" -PsmokeTest=all -q 2>&1 | tee build/smoke-artifacts/server-place.log
if [ "${WITH_CREATE:-0}" = 1 ]; then
    ./gradlew runServer -PwithCreate -PcreateSmoke=place -q 2>&1 | tee build/smoke-artifacts/create-place.log
fi
# the client's copy is taken now: the restart run ends by breaking shafts
rm -rf run/client/saves/smoke
cp -r run/server/world run/client/saves/smoke
./gradlew runServer "${gradle_args[@]}" -PsmokeTest=verify -q 2>&1 | tee build/smoke-artifacts/server-restart.log
if [ "${WITH_CREATE:-0}" = 1 ]; then
    ./gradlew runServer -PwithCreate -PcreateSmoke=verify -q 2>&1 | tee build/smoke-artifacts/create-restart.log
fi

if [ -z "${SKIP_CLIENT:-}" ]; then
    ./gradlew runClient "${gradle_args[@]}" -PsmokeClient=smoke -q 2>&1 | tee build/smoke-artifacts/client.log
    ls -l run/client/screenshots/smoke-*.png
fi
echo "smoke: all runs passed"
