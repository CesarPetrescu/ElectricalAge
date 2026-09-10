#!/usr/bin/env python3
"""Create and attest the native fixture world using the production JAR, never a dev server."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import urllib.request
from run_multiplayer import MODS, download, run_installer


def validate_receipt(receipt, jar_sha):
    if (receipt.get('schema') != 1 or receipt.get('production') is not True
            or receipt.get('registryVerified') is not True
            or receipt.get('jarSha256') != jar_sha or receipt.get('pid', 0) <= 0):
        raise ValueError('Missing, failed or non-production seed receipt')
    registry = receipt.get('registry', [])
    if not registry or len(registry) != len(set(registry)):
        raise ValueError('Empty or duplicated seed registry')
    if any('isolation_transformer#' in entry for entry in registry):
        raise ValueError('Development-only component in production seed')


def validate_smoke_log(text):
    if 'SMOKE all checks passed, stopping server' not in text or 'check(s) FAILED, stopping server' in text:
        raise ValueError('Packaged seed smoke suite did not report success')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--output', type=Path, default=Path('build/native-input'))
    args = parser.parse_args()
    jar = args.jar.resolve()
    sha = hashlib.sha256(jar.read_bytes()).hexdigest()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    evidence = Path('build/native-seed').resolve()
    evidence.mkdir(parents=True, exist_ok=False)
    runtime = Path(os.environ.get('RUNNER_TEMP', tempfile.gettempdir())) / 'eln-native-production-server'
    runtime.mkdir(parents=True, exist_ok=True)
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java')
    props = dict(line.strip().split('=', 1) for line in Path('gradle.properties').read_text().splitlines()
                 if '=' in line and not line.startswith('#'))
    neo = props['neoVersion'].strip()
    url = f'https://maven.neoforged.net/releases/net/neoforged/neoforge/{neo}/neoforge-{neo}-installer.jar'
    with urllib.request.urlopen(url + '.sha1', timeout=60) as response:
        checksum = response.read().decode().split()[0]
    installer = download(url, runtime / f'neoforge-{neo}-installer.jar', checksum)
    server = Path('run/native-seed').resolve()
    server.mkdir(parents=True, exist_ok=False)
    with (evidence / 'installer.log').open('w') as log:
        run_installer(java, installer, '--install-server', server, log)
    mods = server / 'mods'
    mods.mkdir(exist_ok=True)
    shutil.copy2(jar, mods / jar.name)
    url, checksum = MODS['kff']
    shutil.copy2(download(url, runtime / url.rsplit('/', 1)[1], checksum), mods)
    (server / 'eula.txt').write_text('eula=true\n')
    (server / 'server.properties').write_text(
        'server-ip=127.0.0.1\nserver-port=25569\nonline-mode=false\nenforce-secure-profile=false\n'
        'max-tick-time=120000\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\n'
        'level-type=minecraft\\:flat\n'
        'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},'
        '{"block":"minecraft:stone","height":63}],"biome":"minecraft:plains"}\n'
        'gamemode=creative\ndifficulty=peaceful\nallow-flight=true\n')
    command = [java, '-Xms256m', '-Xmx3G', '-Deln.smokeTest=all', f'-Deln.nativeSeedJarSha256={sha}',
               f'@libraries/net/neoforged/neoforge/{neo}/unix_args.txt', 'nogui']
    try:
        with (evidence / 'server.log').open('w') as log:
            subprocess.run(command, cwd=server, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=900)
        validate_smoke_log((evidence / 'server.log').read_text(errors='replace'))
        world = server / 'world'
        receipt = json.loads((world / 'native-production-seed.json').read_text())
        validate_receipt(receipt, sha)
        assert (world / 'level.dat').is_file()
        assert json.loads((world / 'eln-contracts.json').read_text())
        shutil.copy2(jar, output / jar.name)
        shutil.make_archive(str(output / 'seed-world'), 'zip', world)
        source = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
        manifest = {'source': source, 'jar': jar.name, 'jarSha256': sha, 'seedProduction': True,
                    'seedJarSha256': receipt['jarSha256'], 'seedSmokePassed': True,
                    'worldSha256': hashlib.sha256((output / 'seed-world.zip').read_bytes()).hexdigest()}
        (output / 'input-manifest.json').write_text(json.dumps(manifest, indent=2))
        shutil.copy2(world / 'native-production-seed.json', evidence)
        subprocess.run(['git', 'archive', '--format=zip', f'--output={output / "source.zip"}', 'HEAD'], check=True)
    finally:
        for name in ('logs', 'crash-reports'):
            if (server / name).exists():
                shutil.copytree(server / name, evidence / name, dirs_exist_ok=True)
        if (server / 'world').exists():
            shutil.make_archive(str(evidence / 'world'), 'zip', server / 'world')


if __name__ == '__main__':
    main()
