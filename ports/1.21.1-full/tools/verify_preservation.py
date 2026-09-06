#!/usr/bin/env python3
"""Static preservation checks, NOT gameplay or registration-parity tests."""
import hashlib
import json
from pathlib import Path
import re
import sys
ROOT = Path(__file__).resolve().parents[1]
def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
def check(root=ROOT):
    reference = root / 'reference/rewired-1.12.2'
    manifest = json.loads((reference / 'SNAPSHOT.json').read_text())
    errors = []
    source_count = 0
    resource_count = 0
    changed_source_count = 0
    for name, expected in manifest['files'].items():
        original = reference / name
        if not original.is_file() or digest(original) != expected:
            errors.append(f'Immutable reference changed or absent: {name}')
        if not name.startswith('src/'):
            continue
        active = root / name
        if not active.is_file():
            errors.append(f'Original input missing: {name}')
        elif name.startswith('src/main/resources/'):
            resource_count += 1
            if digest(active) != expected:
                errors.append(f'Resource differs without a reviewed migration record: {name}')
        elif active.suffix in ('.java', '.kt'):
            source_count += 1
            if digest(active) != expected:
                changed_source_count += 1
    literal = re.compile(r'"(?:\\.|[^"\\])*"')
    registration_files = ['src/main/java/mods/eln/init/Items.kt', 'src/main/java/mods/eln/init/Descriptors.kt']
    literals = 0
    for name in registration_files:
        if not (reference/name).exists():
            errors.append(f'Missing registration reference: {name}')
            continue
        before = literal.findall((reference/name).read_text())
        after = literal.findall((root/name).read_text()) if (root/name).is_file() else []
        literals += len(before)
        if before != after:
            errors.append(f'Original registration literals changed: {name}')
    return {'errors': errors, 'original_source_files': source_count,
            'changed_original_source_files': changed_source_count,
            'preserved_resource_files': resource_count,
            'registration_string_literals_preserved': literals,
            'scope': 'Static source/resource preservation only; NOT runnable content parity.'}
if __name__ == '__main__':
    result = check()
    print(json.dumps(result, indent=2))
    sys.exit(bool(result['errors']))
