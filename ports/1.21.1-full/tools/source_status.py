#!/usr/bin/env python3
"""Produce a conservative file-level migration ledger from actual diagnostics.
No diagnostic at a file is NOT proof of successful compilation or gameplay.
"""
import argparse
from collections import Counter
import csv
import hashlib
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('diagnostics', type=Path)
    parser.add_argument('--output', type=Path, default=ROOT / 'build/migration/source-status.csv')
    args = parser.parse_args()
    entries = json.loads(args.diagnostics.read_text())
    counts = Counter(entry['file'] for entry in entries)
    manifest = json.loads((ROOT / 'reference/rewired-1.12.2/SNAPSHOT.json').read_text())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    columns = ['file', 'language', 'present', 'changed_from_reference', 'diagnostic_count',
               'compile_status', 'runtime_status', 'reference_sha256', 'active_sha256']
    rows = []
    for name, baseline_hash in sorted(manifest['files'].items()):
        if not name.startswith('src/') or Path(name).suffix not in ('.java', '.kt'):
            continue
        path = ROOT / name
        current_hash = hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else ''
        if name.endswith('/Eln_old.java'):
            status = 'UPSTREAM_ALREADY_DISABLED'
        elif not path.is_file():
            status = 'MISSING_SOURCE_ERROR'
        elif name.endswith('.java'):
            status = 'FULL_JAVA_COMPILATION_NOT_REACHED'
        elif counts[name]:
            status = 'KOTLIN_DIAGNOSTICS_PRESENT'
        else:
            status = 'NO_DIRECT_DIAGNOSTIC_NOT_VERIFIED'
        rows.append(dict(zip(columns, [name, 'Kotlin' if name.endswith('.kt') else 'Java', path.is_file(),
                                      current_hash != baseline_hash, counts[name], status,
                                      'NOT_VALIDATED', baseline_hash, current_hash])))
    with args.output.open('w', newline='', encoding='utf-8') as output:
        writer = csv.DictWriter(output, fieldnames=columns)
        writer.writeheader()
        writer.writerows(rows)
    print(f'Wrote {len(rows)} original-source records. No runtime-parity claim: {args.output}')

if __name__ == '__main__':
    main()
