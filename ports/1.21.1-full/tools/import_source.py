#!/usr/bin/env python3
"""One-time, non-overwriting import of the complete pinned Re-Wired tree."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

PIN = '3a0088b384aa4111c3a7f984d4cbc5f4ff142ed9'
ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('source', type=Path)
    parser.add_argument('--local-archive', action='store_true', help='Explicitly mark an archive import without Git verification')
    args = parser.parse_args()
    source = args.source.resolve()
    if (ROOT / 'reference/rewired-1.12.2/SNAPSHOT.json').exists():
        raise SystemExit('Already imported; refusing to overwrite migration edits.')
    if (ROOT / 'src').exists():
        raise SystemExit('Active src already exists; refusing to overwrite it.')
    revision = None
    if not args.local_archive:
        revision = subprocess.check_output(['git', '-C', str(source), 'rev-parse', 'HEAD'], text=True).strip()
        if revision != PIN:
            raise SystemExit(f'Expected pinned source {PIN}, got {revision}')
    reference = ROOT / 'reference/rewired-1.12.2'
    reference.mkdir(parents=True, exist_ok=True)
    shutil.copytree(source / 'src', reference / 'src')
    shutil.copytree(source / 'src', ROOT / 'src')
    for name in ('build.gradle', 'settings.gradle', 'gradle.properties', 'LICENSE.md', 'LICENSE', 'README.md', 'PORT_TODO.md', 'Tasks.org'):
        if (source / name).is_file():
            shutil.copy2(source / name, reference / name)
    old_wrapper = ROOT.parent / '1.21.1'
    for name in ('gradlew', 'gradlew.bat'):
        shutil.copy2(old_wrapper / name, ROOT / name)
    shutil.copytree(old_wrapper / 'gradle/wrapper', ROOT / 'gradle/wrapper')
    (ROOT / 'gradlew').chmod(0o755)
    files = {p.relative_to(reference).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
             for p in sorted(reference.rglob('*')) if p.is_file()}
    java = list((ROOT / 'src/main/java').rglob('*.java'))
    kotlin = list((ROOT / 'src/main/java').rglob('*.kt'))
    report = {'source': 'brambora69123/electrical-age-rewired', 'pinned_commit': PIN,
              'verified_git_commit': revision, 'files': files,
              'java_files_including_upstream_disabled': len(java), 'kotlin_files': len(kotlin),
              'upstream_disabled': ['src/main/java/mods/eln/Eln_old.java'],
              'audit_note': 'Pristine Re-Wired copy. Earlier audit patches are NOT silently assumed applied.'}
    (reference / 'SNAPSHOT.json').write_text(json.dumps(report, indent=2) + '\n')
    (ROOT / 'SOURCE-MANIFEST.json').write_text(json.dumps(report, indent=2) + '\n')
    print(f'Imported {len(java)} Java + {len(kotlin)} Kotlin files and ALL resources into active src and immutable reference.')

if __name__ == '__main__':
    main()
