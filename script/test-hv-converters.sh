#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p run/server build/smoke-artifacts
# Dev run directory only; never run this script against a valuable world.
printf 'eula=true\n' > run/server/eula.txt
cat > run/server/server.properties <<'PROPS'
online-mode=false
max-tick-time=-1
level-name=hv-converter-test-world
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:stone","height":63}],"biome":"minecraft:plains"}
PROPS
for mode in place restart; do
  timeout --signal=TERM --kill-after=30s 15m ./gradlew runServer "-PsmokeTest=hv-converters-$mode" --console=plain 2>&1 | tee "build/smoke-artifacts/hv-world-$mode.log"
done
python3 - <<'CHECK'
import json, pathlib
for mode in ('place','restart'):
    suite=f'hv-world-{mode}'
    p=pathlib.Path(f'build/smoke-artifacts/contracts/{suite}.json')
    d=json.loads(p.read_text())
    assert d['suite']==suite and d['complete'] and d['failures']==0,suite
    assert len(d['results'])>=20 and all(r['status']=='passed' for r in d['results']),d
    trace=pathlib.Path(f'build/smoke-artifacts/{suite}.csv')
    assert len(trace.read_text().splitlines())>20,trace
    print(suite,len(d['results']),'passed')
CHECK
