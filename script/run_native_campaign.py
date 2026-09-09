#!/usr/bin/env python3
"""Launch the actual packaged ELN client. Never substitutes a synthetic screenshot."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tempfile
import time
import urllib.request

from run_multiplayer import MODS, download, install_client, offline_uuid


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--suite', default='preflight', choices=['preflight'])
    args = parser.parse_args()
    import minecraft_launcher_lib as launcher
    from PIL import Image

    out = Path('build/native-campaign').resolve()
    out.mkdir(parents=True, exist_ok=False)
    control = out / 'control'
    control.mkdir()
    jar = args.jar.resolve()
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java')
    runtime = Path(os.environ.get('RUNNER_TEMP', tempfile.gettempdir())) / f'eln-native-{platform.system()}-{platform.machine()}'
    runtime.mkdir(parents=True, exist_ok=True)
    game = out / 'game'
    (game / 'mods').mkdir(parents=True)
    shutil.copy2(jar, game / 'mods' / jar.name)
    url, sha1 = MODS['kff']
    shutil.copy2(download(url, runtime / url.rsplit('/', 1)[1], sha1), game / 'mods')
    props = dict(line.strip().split('=', 1) for line in Path('gradle.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
    props = {k.strip(): v.strip() for k, v in props.items()}
    neo, mc = props['neoVersion'], props['minecraftVersion']
    url = f'https://maven.neoforged.net/releases/net/neoforged/neoforge/{neo}/neoforge-{neo}-installer.jar'
    with urllib.request.urlopen(url + '.sha1', timeout=60) as r:
        sha1 = r.read().decode().split()[0]
    installer = download(url, runtime / f'neoforge-{neo}-installer.jar', sha1)
    with (out / 'install.log').open('w') as log:
        version = install_client(launcher, mc, neo, runtime, java, installer, log)
    (game / 'options.txt').write_text('onboardAccessibility:false\nnarrator:0\nrenderDistance:4\nsimulationDistance:4\nmaxFps:60\nenableVsync:false\npauseOnLostFocus:false\nguiScale:2\nsoundCategory_master:0.0\n')
    flags = ['-Xms256m', '-Xmx3G', '-Deln.multiplayerTest=alpha', f'-Deln.multiplayerDirectory={control}', f'-Deln.multiplayerJarSha256={digest}', '-Deln.multiplayerProfile=standalone']
    if platform.system() == 'Darwin':
        flags.append('-XstartOnFirstThread')
    command = launcher.command.get_minecraft_command(version, runtime, {'username': 'ElnNativeQA', 'uuid': offline_uuid('ElnNativeQA'), 'token': 'offline-ci-local-only', 'executablePath': java, 'jvmArguments': flags, 'gameDirectory': str(game), 'customResolution': True, 'resolutionWidth': '1280', 'resolutionHeight': '800'})
    hardware = {'system': platform.system(), 'arch': platform.machine(), 'minecraft': mc, 'neoforge': neo, 'jarSha256': digest, 'runnerImage': os.environ.get('ImageVersion'), 'source': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()}
    (out / 'runtime.json').write_text(json.dumps(hardware, indent=2))
    subprocess.run([java, '-XshowSettings:properties', '-version'], stdout=(out / 'java.txt').open('w'), stderr=subprocess.STDOUT, check=True)
    started = time.monotonic()
    proc = None
    try:
        with (out / 'client.log').open('w') as log:
            proc = subprocess.Popen(command, cwd=game, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            for action in ('boot', 'stop'):
                path = control / 'alpha-command.json'
                temp = path.with_suffix('.tmp')
                temp.write_text(json.dumps({'role': 'alpha', 'id': action, 'action': action}))
                temp.replace(path)
                result_path = control / f'alpha-{action}.json'
                deadline = time.monotonic() + 300
                while not result_path.exists():
                    if proc.poll() is not None:
                        raise RuntimeError(f'Client exited {proc.returncode} before {action}; inspect client.log')
                    if time.monotonic() > deadline:
                        subprocess.run([str(Path(java).with_name('jcmd')), str(proc.pid), 'Thread.print'], stdout=(out / 'threads.txt').open('w'), stderr=subprocess.STDOUT, timeout=15, check=False)
                        raise TimeoutError(f'Missing real {action} report')
                    time.sleep(.2)
                result = json.loads(result_path.read_text())
                assert result['status'] == 'passed' and result['pid'] == proc.pid, result
                assert 'screenshotError' not in result, result
                with Image.open(control / f'alpha-{action}.png') as image:
                    image.verify()
            assert proc.wait(timeout=60) == 0
        hardware['clientSeconds'] = time.monotonic() - started
        hardware['status'] = 'passed'
    except BaseException as error:
        hardware['status'] = 'failed'
        hardware['error'] = str(error)
        raise
    finally:
        (out / 'runtime.json').write_text(json.dumps(hardware, indent=2))
        if proc is not None and proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(timeout=15)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait(timeout=15)
        print(json.dumps(hardware, indent=2), flush=True)


if __name__ == '__main__':
    main()
