#!/usr/bin/env bash
# Disposable native test only. No modifications to the companion repository's source.
set -euo pipefail
cd "$(dirname "$0")/.."
test "${ELN_ALLOW_DISPOSABLE_QA:-}" = 1 || { echo 'Set ELN_ALLOW_DISPOSABLE_QA=1 only in a disposable checkout.' >&2; exit 2; }
COMPANION=$(realpath "${1:?Supply the pinned AutoPropulsion-Age checkout}")
PIN=daf12579815345470310aed469df3e54eb7c61d4
test "$(git -C "$COMPANION" rev-parse HEAD)" = "$PIN"
test ! -e run/server/world || { echo 'Refusing to overwrite an existing world.' >&2; exit 2; }
mkdir -p build/charger-acceptance run/server/mods
printf 'ElectricalAge %s\nAutoPropulsion-Age %s\n' "$(git rev-parse HEAD)" "$PIN" > build/charger-acceptance/source-identity.txt
(cd "$COMPANION" && ./gradlew --no-daemon build --stacktrace) 2>&1 | tee build/charger-acceptance/companion-build.log
python3 - "$COMPANION" <<'PY'
import pathlib,sys,hashlib,shutil
jars=[p for p in (pathlib.Path(sys.argv[1])/'build/libs').glob('*.jar') if not any(x in p.name for x in ['-sources','-javadoc','-dev'])]
assert len(jars)==1,jars
p=jars[0];shutil.copyfile(p,pathlib.Path('run/server/mods')/p.name)
pathlib.Path('build/charger-acceptance/companion-jar-sha256.txt').write_text(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n')
PY
printf 'eula=true\n' > run/server/eula.txt
cat > run/server/server.properties <<'PROPS'
online-mode=false
max-tick-time=60000
level-name=world
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:stone","height":63}],"biome":"minecraft:plains"}
view-distance=3
simulation-distance=3
PROPS
for mode in place restart; do
  timeout --signal=TERM --kill-after=30s 12m ./gradlew --no-daemon runServer "-PsmokeTest=converter-charger-$mode" --stacktrace 2>&1 | tee "build/charger-acceptance/$mode.log"
  python3 - "$mode" <<'PY'
import json,pathlib,sys
mode=sys.argv[1];suite='converter-charger-'+mode
p=pathlib.Path('build/smoke-artifacts/contracts')/(suite+'.json');d=json.loads(p.read_text())
assert d['complete'] is True and d['failures']==0,d
r=d['results'];assert r and all(x['status']=='passed' for x in r),d
names={x['check'] for x in r}
required={'actual-companion-mod-loaded','no-external-ground-or-dummy-load','initial-no-load-output-stays-regulated','initial-native-charger-draws-power','initial-real-battery-gains-energy','initial-energy-conserved-through-charger-and-battery','under-capacity-241v-supply-limits-without-numerical-latch','supply-recovered-native-charger-draws-power','supply-recovered-energy-conserved-through-charger-and-battery','replugged-native-charger-draws-power','unplugged-no-phantom-charging-or-large-bleeder','final-no-load-output-stays-regulated'}
if mode=='place':required|={'actual-chunk-unload-closes-native-charger-port','real-chunk-reload-preserves-car-and-expires-lease','chunk-reloaded-real-battery-gains-energy'}
else:required|={'separate-jvm-saved-car-energy-preserved','separate-jvm-connection-lease-expired','separate-jvm-converter-settings-and-windings-preserved'}
assert required<=names,required-names
trace=pathlib.Path('build/smoke-artifacts')/(suite+'.csv');assert len(trace.read_text().splitlines())>200
assert pathlib.Path('run/server/world/level.dat').is_file()
print(suite,len(r),'passed; actual native charger, battery, wiring and measured energy')
PY
  if [[ $mode == place ]]; then
    tar -czf build/charger-acceptance/world-before-restart.tar.gz -C run/server world
    sha256sum run/server/world/level.dat > build/charger-acceptance/saved-level.sha256
  fi
done
