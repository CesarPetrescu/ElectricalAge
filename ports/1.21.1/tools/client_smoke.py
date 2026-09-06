#!/usr/bin/env python3
"""Real development or packaged client probe; only dedicated disposable smoke directories are cleared."""
from pathlib import Path
import argparse, hashlib, os, re, shutil, signal, subprocess, time
ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--packaged',action='store_true');args=parser.parse_args()
name='packaged-smoke-client' if args.packaged else 'smoke-client'
run=ROOT/'run'/name
if run.is_symlink() or not run.resolve().is_relative_to(ROOT): raise SystemExit('Unsafe smoke directory')
if run.exists(): shutil.rmtree(run)
run.mkdir(parents=True)
# Do not get stuck at the first-launch accessibility onboarding dialog.
(run/'options.txt').write_text('onboardAccessibility:false\nnarrator:0\nfullscreen:false\nrenderDistance:2\nsimulationDistance:5\n')
evidence=ROOT/'build/evidence';evidence.mkdir(parents=True,exist_ok=True)
kind='packaged' if args.packaged else 'dev'
log=evidence/f'client-{kind}.log'
if args.packaged:
    jars=[p for p in (ROOT/'build/libs').glob('*.jar') if not p.name.endswith('-sources.jar')]
    if len(jars)!=1: raise SystemExit('Expected one distributable jar')
    (run/'mods').mkdir();shutil.copy2(jars[0],run/'mods'/jars[0].name)
    (evidence/'packaged-client-input-sha256.txt').write_text(hashlib.sha256(jars[0].read_bytes()).hexdigest()+'  '+jars[0].name+'\n')
env=dict(os.environ,LIBGL_ALWAYS_SOFTWARE='1',ALSOFT_DRIVERS='null')
ready=False
task='runPackagedClient' if args.packaged else 'runSmokeClient'
with log.open('w') as stream:
    process=subprocess.Popen(['xvfb-run','-a','-s','-screen 0 1280x720x24',str(ROOT/'gradlew'),task,'--no-daemon','--stacktrace'],cwd=ROOT,stdout=stream,stderr=subprocess.STDOUT,env=env,start_new_session=True)
    try:
        deadline=time.monotonic()+360
        while time.monotonic()<deadline and process.poll() is None:
            text=log.read_text(errors='replace')
            match=re.search(r'ELN_CLIENT_READY obj_quads=(\d+) item_quads=(\d+)',text)
            packaged_ok=not args.packaged or 'ELN_PACKAGED_RUNTIME_OK origin=' in text
            if match and all(int(n)>0 for n in match.groups()) and packaged_ok:
                time.sleep(8);ready=process.poll() is None;break
            time.sleep(2)
    finally:
        if process.poll() is None:
            os.killpg(process.pid,signal.SIGTERM)
            try: process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid,signal.SIGKILL);process.wait(timeout=10)
        # Some Gradle launchers detach the game JVM. Only terminate Java processes
        # whose exact working directory is this disposable probe directory.
        for entry in Path('/proc').iterdir():
            if not entry.name.isdigit(): continue
            try:
                if (entry/'cwd').resolve(strict=True)==run.resolve() and (entry/'comm').read_text().strip()=='java':
                    os.kill(int(entry.name),signal.SIGTERM)
            except (OSError,ProcessLookupError): pass
text=log.read_text(errors='replace');print(text[-14000:])
if not ready: raise SystemExit('Client did not reach title screen with both real OBJ models baked')
if list(run.rglob('crash-*.txt')): raise SystemExit('Client generated a crash report')
for line in text.splitlines():
    if re.search(r'(?:missing|failed|unable|exception|error)',line,re.I) and re.search(r'eln:(?:block|item|models|textures)|circuit_bench',line):
        raise SystemExit('ELN resource failure: '+line)
(evidence/f'client-{kind}-result.txt').write_text(f'PASS: {kind} client; title screen; nonmissing block/item OBJ quads; alive after readiness. Intentional termination, not gameplay proof.\n')
print(f'CLIENT_PROBE_PASS {kind}')
