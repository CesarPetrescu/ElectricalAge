#!/usr/bin/env python3
"""Cross-platform Java 21 numerical checks; no Gradle, network, JUnit or game stubs.
These checks do NOT compile the NeoForge adapter. Use Gradle/CI for that gate.
"""
import pathlib, shutil, subprocess, sys
ROOT = pathlib.Path(__file__).resolve().parents[1]
CLASSES = ('NumericalChecks', 'HardeningChecks', 'NetworkChecks')
def main():
    for tool in ('javac', 'java'):
        if not shutil.which(tool):
            raise SystemExit(f'{tool} not on PATH; install a JDK 21, not only a JRE')
    output = ROOT / 'build/offline'
    output.mkdir(parents=True, exist_ok=True)
    sources = sorted((ROOT / 'sim-core/src/main/java').rglob('*.java'))
    sources += [ROOT / f'sim-core/src/test/java/mods/eln/audit/{name}.java' for name in CLASSES]
    argfile = output / 'sources.txt'
    argfile.write_text('\n'.join('"'+p.as_posix()+'"' for p in sources)+'\n', encoding='utf-8')
    with (output / 'compile.log').open('w', encoding='utf-8') as log:
        result = subprocess.run(['javac','--release','21','-d',str(output/'classes'),'@'+str(argfile)], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    if result.returncode:
        print((output/'compile.log').read_text()); raise SystemExit(result.returncode)
    for name in CLASSES:
        result = subprocess.run(['java','-cp',str(output/'classes'),'mods.eln.audit.'+name], cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        (output/(name+'.log')).write_text(result.stdout, encoding='utf-8')
        print(result.stdout, end='')
        if result.returncode: raise SystemExit(result.returncode)
if __name__ == '__main__': main()
