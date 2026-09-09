#!/usr/bin/env python3
"""Run existing Kotlin assertion bodies without a JUnit engine in the offline harness.
Only @Test/import plumbing is removed. Full Gradle CI still runs the real original tests.
Minecraft bootstrap, benchmark, profiling and GUI tests are NOT executed by this adapter.
"""
from pathlib import Path
import json
import re
import sys

root = Path(__file__).resolve().parents[2]
out = Path(sys.argv[1])
out.mkdir(parents=True, exist_ok=True)
files = sorted((root / 'src/test/kotlin/mods/eln/sim/mna').rglob('*Test.kt'))
files += [root / 'src/test/kotlin/mods/eln/transparentnode/OneWayDcDcMathTest.kt']
files += sorted((root / 'src/test/kotlin/mods/eln/sixnode/electricalcable').glob('Wire*Test.kt'))
cases = []
excluded = []
for path in files:
    if 'Benchmark' in path.name or 'Profiling' in path.name:
        excluded.append(str(path.relative_to(root)))
        continue
    source = path.read_text()
    package = re.search(r'^package\s+([\w.]+)', source, re.M).group(1)
    classes = list(re.finditer(r'^class\s+(\w+)', source, re.M))
    methods = list(re.finditer(r'@Test\s+(?:public\s+)?fun\s+(\w+)\s*\(', source))
    if not methods:
        raise SystemExit(f'No explicit test methods found in {path}')
    for method in methods:
        cls = [c.group(1) for c in classes if c.start() < method.start()][-1]
        cases.append((f'{package}.{cls}', method.group(1)))
    source = source.replace('import kotlin.test.Test\n', '').replace('@Test', '')
    # These Java NBT methods are members in the 1.21 port; the extension import is unused here.
    source = source.replace('import mods.eln.misc.writeToNBT\n', '')
    (out / path.name).write_text(source)
body = ['fun main() {', '    var passed = 0', '    val failures = mutableListOf<String>()']
for cls, name in cases:
    body += [f'    try {{ {cls}().{name}(); passed++ }}',
             f'    catch (t: Throwable) {{ failures += "{cls}.{name}: ${{t.message}}" }}']
body += ['    failures.forEach(::println)',
         f'    println("Existing offline test methods: $passed/{len(cases)} passed")',
         '    check(failures.isEmpty()) { "Existing regression failures: ${failures.size}" }', '}']
(out / 'ExistingTestRunner.kt').write_text('\n'.join(body) + '\n')
(out / 'manifest.json').write_text(json.dumps({'cases': cases, 'excluded': excluded}, indent=2) + '\n')
print(f'Prepared {len(cases)} existing test methods; excluded {len(excluded)} benchmark/profiling files')
