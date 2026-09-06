#!/usr/bin/env python3
"""Compile two actual migrated production interfaces against the REAL target.
This small diagnostic probe does not replace or bypass full-source compilation.
"""
from pathlib import Path
import subprocess
import sys
ROOT = Path(__file__).resolve().parents[1]
classes = ['src/main/java/mods/eln/misc/INBTTReady.java', 'src/main/java/mods/eln/misc/FakeSideInventory.java']
classpath = (ROOT/'build/migration/api/classpath.txt').read_text().strip()
out = ROOT/'build/migration/api-check-classes'
out.mkdir(parents=True, exist_ok=True)
command = ['javac', '--release', '21', '-proc:none', '-sourcepath', str(out), '-classpath', classpath, '-d', str(out)] + classes
result = subprocess.run(command, cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
(ROOT/'build/migration/api-check.log').write_text(result.stdout + '\nexit_code=' + str(result.returncode) + '\n')
print(result.stdout)
print('Selected production files compiled:', classes, 'exit code:',result.returncode)
sys.exit(result.returncode)
