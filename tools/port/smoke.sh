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
# Never let a stale report from a previous invocation satisfy this run's gate.
rm -f build/smoke-artifacts/contracts/blocks-{place,settled,restart}.{json,xml}
rm -f build/smoke-artifacts/contracts/power-{behavior,behavior-restart,client-leds}.{json,xml}
rm -f build/smoke-artifacts/contracts/wire-{behavior,behavior-restart,thermal,thermal-restart}.{json,xml}
rm -f build/smoke-artifacts/contracts/{wiki-client,lighting-gallery}.{json,xml}
run_gradle() {
    # Bound each process separately so a stuck shutdown cannot consume the whole job.
    timeout --signal=TERM --kill-after=30s 25m ./gradlew "$@"
}
gradle_args=()
if [ "${WITH_CREATE:-0}" = 1 ]; then gradle_args+=(-PwithCreate); fi

rm -rf run/server/world
run_gradle runServer "${gradle_args[@]}" -PsmokeTest=all -q 2>&1 | tee build/smoke-artifacts/server-place.log
if [ "${WITH_CREATE:-0}" = 1 ]; then
    run_gradle runServer -PwithCreate -PcreateSmoke=place -q 2>&1 | tee build/smoke-artifacts/create-place.log
fi
# the client's copy is taken now: the restart run ends by breaking shafts
rm -rf run/client/saves/smoke
cp -r run/server/world run/client/saves/smoke
run_gradle runServer "${gradle_args[@]}" -PsmokeTest=verify -q 2>&1 | tee build/smoke-artifacts/server-restart.log
if [ "${WITH_CREATE:-0}" = 1 ]; then
    run_gradle runServer -PwithCreate -PcreateSmoke=verify -q 2>&1 | tee build/smoke-artifacts/create-restart.log
fi
python3 script/check_block_contracts.py

if [ "${ELN_FULL_GALLERY:-0}" = 1 ]; then
    # verify deliberately destroys shafts; replay the pre-break snapshot for each extra restart.
    for pass in 2 3; do
        rm -rf run/server/world
        cp -r run/client/saves/smoke run/server/world
        run_gradle runServer "${gradle_args[@]}" -PsmokeTest=verify -q 2>&1 | tee "build/smoke-artifacts/server-restart-$pass.log"
        python3 script/check_block_contracts.py
        cp build/smoke-artifacts/contracts/blocks-restart.json "build/smoke-artifacts/contracts/blocks-restart-$pass.json"
    done
fi

if [ -z "${SKIP_CLIENT:-}" ]; then
    run_gradle runClient "${gradle_args[@]}" -PsmokeClient=smoke -q 2>&1 | tee build/smoke-artifacts/client.log
    python3 script/check_client_assets.py run/client/logs/latest.log
    python3 script/check_block_contracts.py --client
    ls -l run/client/screenshots/smoke-*.png
fi
echo "smoke: all runs passed"
