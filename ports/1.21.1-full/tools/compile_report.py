#!/usr/bin/env python3
"""Run the real target compiler, retain failures, and group diagnostics (not coverage)."""
import argparse
import collections
import csv
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
KOTLIN = re.compile(r'^e:\s+(?:file://)?(.+?\.(?:kt|java)):(\d+):(\d+)\s+(.*)$')
KOTLIN_OLD = re.compile(r'^e:\s+(?:file://)?(.+?\.kt):\s*\((\d+),\s*(\d+)\):\s*(.*)$')
JAVA = re.compile(r'^(.+?\.java):(\d+):\s+error:\s+(.*)$')

def parse(text):
    records = []
    for line in text.splitlines():
        match = KOTLIN.match(line) or KOTLIN_OLD.match(line)
        if match:
            path, number, column, message = match.groups()
        else:
            match = JAVA.match(line)
            if not match:
                continue
            path, number, message = match.groups()
            column = '0'
        marker = '/src/'
        path = 'src/' + path.split(marker, 1)[1] if marker in path else path
        kind = ('unresolved-reference' if 'Unresolved reference' in message or 'does not exist' in message else
                'signature' if 'overrides nothing' in message or 'override' in message else
                'type-mismatch' if 'mismatch' in message or 'compatible' in message else 'other')
        records.append({'file': path, 'line': int(number), 'column': int(column), 'category': kind, 'message': message})
    return records

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--label', default='latest')
    parser.add_argument('--from-log', type=Path)
    parser.add_argument('--exit-code', type=int, default=1)
    args = parser.parse_args()
    if not re.fullmatch(r'[a-zA-Z0-9_-]+', args.label):
        raise SystemExit('Unsafe report label')
    dest = ROOT / 'build/migration' / args.label
    dest.mkdir(parents=True, exist_ok=True)
    if args.from_log:
        text = args.from_log.read_text(errors='replace')
        status = args.exit_code
    else:
        wrapper = 'gradlew.bat' if os.name == 'nt' else './gradlew'
        command = [wrapper, 'compileKotlin', 'compileJava', '--continue', '--no-daemon', '--console=plain', '--stacktrace']
        with (dest / 'compile.log').open('w', encoding='utf-8') as log:
            process = subprocess.Popen(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace')
            for line in process.stdout:
                print(line, end='', flush=True)
                log.write(line)
            status = process.wait()
        text = (dest / 'compile.log').read_text()
    records = parse(text)
    (dest / 'diagnostics.json').write_text(json.dumps(records, indent=2) + '\n')
    for filename, counts in [('by-file.csv', collections.Counter(x['file'] for x in records)),
                             ('by-message.csv', collections.Counter(x['message'] for x in records))]:
        with (dest / filename).open('w', newline='') as out:
            writer = csv.writer(out)
            writer.writerow(['key', 'diagnostics'])
            writer.writerows(counts.most_common())
    stage = 'COMPILE_PASS' if status == 0 else ('SOURCE_ERRORS' if records else 'BUILD_OR_ENVIRONMENT_FAILURE')
    summary = {'status': stage, 'process_exit_code': status, 'diagnostics': len(records),
               'files_with_diagnostics': len({x['file'] for x in records}),
               'categories': dict(collections.Counter(x['category'] for x in records)),
               'note': 'Diagnostics can cascade. Counts are neither unique bugs nor port completion percentages.'}
    (dest / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    (dest / 'SUMMARY.md').write_text('# Full-source compile report\n\n```json\n' + json.dumps(summary, indent=2) + '\n```\n\nAll original active sources remain included. Kotlin failure can prevent the dependent Java compilation; inspect the task log.\n')
    print(json.dumps(summary))
    return status

if __name__ == '__main__':
    sys.exit(main())
