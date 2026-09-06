#!/usr/bin/env python3
"""Real client startup probe. No server logs, fake success markers, or stale run directories."""
from pathlib import Path
import os, re, shutil, signal, subprocess, time
ROOT=Path(__file__).resolve().parents[1]
run=ROOT/'run/client'
if run.exists(): shutil.rmtree(run)
evidence=ROOT/'build/evidence';evidence.mkdir(parents=True,exist_ok=True)
log=evidence/'client.log'
env=dict(os.environ,LIBGL_ALWAYS_SOFTWARE='1',ALSOFT_DRIVERS='null')
ready=False
with log.open('w') as stream:
    process=subprocess.Popen(['xvfb-run','-a','-s','-screen 0 1280x720x24',str(ROOT/'gradlew'),'runClient','--no-daemon','--stacktrace'],cwd=ROOT,stdout=stream,stderr=subprocess.STDOUT,env=env,start_new_session=True)
    try:
        deadline=time.monotonic()+480
        while time.monotonic()<deadline and process.poll() is None:
            text=log.read_text(errors='replace')
            match=re.search(r'ELN_CLIENT_READY obj_quads=(\d+) item_quads=(\d+)',text)
            if match and all(int(n)>0 for n in match.groups()):
                time.sleep(8)
                ready=process.poll() is None
                break
            time.sleep(2)
    finally:
        if process.poll() is None:
            os.killpg(process.pid,signal.SIGTERM)
            try: process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid,signal.SIGKILL); process.wait(timeout=10)
text=log.read_text(errors='replace')
print(text[-14000:])
if not ready: raise SystemExit('Client did not reach the title screen with both real OBJ models baked')
if list(run.rglob('crash-*.txt')): raise SystemExit('Client generated a crash report')
for line in text.splitlines():
    if re.search(r'(?:missing|failed|unable|exception|error)',line,re.I) and re.search(r'eln:(?:block|item|models|textures)',line):
        raise SystemExit('ELN resource failure: '+line)
(evidence/'client-result.txt').write_text('PASS: real title screen; nonmissing block/item OBJ quads; alive after readiness. Intentional termination, not gameplay proof.\n')
print('CLIENT_PROBE_PASS')
