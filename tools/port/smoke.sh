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

rm -rf run/server/world
./gradlew runServer -PsmokeTest=all -q
./gradlew runServer -PsmokeTest=verify -q

if [ -z "${SKIP_CLIENT:-}" ]; then
    rm -rf run/client/saves/smoke
    cp -r run/server/world run/client/saves/smoke
    ./gradlew runClient -PsmokeClient=smoke -q
    ls -l run/client/screenshots/smoke-*.png
fi
echo "smoke: all runs passed"
