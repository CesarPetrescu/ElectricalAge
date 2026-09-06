#!/usr/bin/env python3
"""One-time deterministic source extraction. Does not fetch anything or edit legacy sources.
CI supplies exact Git checkouts. Generated, human-readable sources are committed on the port branch.
"""
from pathlib import Path
import argparse, gzip, hashlib, json, shutil, struct, subprocess

REWIRED = '3a0088b384aa4111c3a7f984d4cbc5f4ff142ed9'
MDK = '70d335c962ee8a773b38fb0690c7e7f30d1bafa6'
ROOT = Path(__file__).resolve().parents[1]

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def put(relative, content):
    path = ROOT / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(content, bytes):
        path.write_bytes(content)
    else:
        path.write_text(content, encoding='utf-8')

def checked_checkout(path, expected):
    actual = subprocess.check_output(['git','-C',str(path),'rev-parse','HEAD'],text=True).strip()
    if actual != expected:
        raise SystemExit(f'Wrong source checkout: {actual}, expected {expected}')

def once(text, before, after):
    if text.count(before) != 1:
        raise SystemExit(f'Expected exactly one migration anchor: {before!r}')
    return text.replace(before, after)

def neutral(text):
    return (text.replace('import mods.eln.misc.INBTTReady;', 'import mods.eln.sim.persistence.StateSerializable;')
        .replace('import net.minecraft.nbt.NBTTagCompound;', 'import mods.eln.sim.persistence.StateData;')
        .replace('INBTTReady', 'StateSerializable').replace('NBTTagCompound', 'StateData')
        .replace('readFromNBT', 'readState').replace('writeToNBT', 'writeState')
        .replace('import mods.eln.misc.Utils;', 'import mods.eln.sim.support.SimLog;')
        .replace('Utils.println', 'SimLog.println').replace('Utils.print(', 'SimLog.print('))

def empty_structure():
    def utf(value):
        b=value.encode('utf-8'); return struct.pack('>H',len(b))+b
    def tag(kind,name,data): return bytes([kind])+utf(name)+data
    def list_tag(name,kind,entries):
        return tag(9,name,bytes([kind])+struct.pack('>i',len(entries))+b''.join(entries))
    root = tag(3,'DataVersion',struct.pack('>i',3955))
    root += list_tag('size',3,[struct.pack('>i',3)]*3)
    root += list_tag('palette',10,[tag(8,'Name',utf('minecraft:air'))+b'\0'])
    blocks=[]
    for x in range(3):
        for y in range(3):
            for z in range(3):
                blocks.append(list_tag('pos',3,[struct.pack('>i',v) for v in (x,y,z)])+tag(3,'state',struct.pack('>i',0))+b'\0')
    root += list_tag('blocks',10,blocks)+list_tag('entities',10,[])+b'\0'
    return gzip.compress(b'\x0a\0\0'+root,mtime=0)

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--legacy',type=Path,required=True)
    parser.add_argument('--mdk',type=Path,required=True)
    args=parser.parse_args()
    if (ROOT/'PROVENANCE.json').exists():
        print('Already materialized; preserving checked-in source edits.'); return
    checked_checkout(args.legacy,REWIRED); checked_checkout(args.mdk,MDK)
    src=args.legacy/'src/main/java'
    files=sorted((src/'mods/eln/sim/mna').rglob('*.java'))
    files += [src/p for p in ('mods/eln/sim/ElectricalLoad.java','mods/eln/sim/ElectricalConnection.java','mods/eln/misc/Profiler.java','mods/eln/misc/FunctionTable.java','mods/eln/misc/IFunction.java')]
    manifest=[]
    for path in files:
        relative=path.relative_to(src)
        text=path.read_text(encoding='utf-8')
        if path.name=='Matrix.java':
            text=(ROOT/'porting/seed/Matrix.java').read_text(encoding='utf-8')
        if path.name=='SubSystem.java':
            text=once(text,'Idata[s.getId()] = v;','Idata[s.getId()] += v; // Shared-node contributions must accumulate.')
            text=once(text,'root.systems.remove(this);','if (root != null) root.systems.remove(this);')
        destination=Path('sim-core/src/main/java')/relative
        put(destination,neutral(text))
        manifest.append({'from':str(relative),'input_sha256':sha(path),'to':str(destination),'output_sha256':sha(ROOT/destination)})
    for filename in ('gradlew','gradlew.bat','gradle/wrapper/gradle-wrapper.jar','gradle/wrapper/gradle-wrapper.properties'):
        put(filename,(args.mdk/filename).read_bytes())
    (ROOT/'gradlew').chmod(0o755)
    put('src/gametest/resources/data/eln/structure/empty.nbt',empty_structure())
    model=args.legacy/'src/main/resources/assets/eln/model/voltagesource'
    original=(model/'voltagesource.obj').read_text(encoding='utf-8')
    vertices=[tuple(map(float,line.split()[1:4])) for line in original.splitlines() if line.startswith('v ')]
    low=[min(v[i] for v in vertices) for i in range(3)]
    high=[max(v[i] for v in vertices) for i in range(3)]
    scale=.8/max(high[i]-low[i] for i in range(3))
    center=[(high[i]+low[i])/2 for i in range(3)]
    lines=[]
    for line in original.splitlines():
        if line.startswith('v '):
            v=list(map(float,line.split()[1:4]))
            v=[(v[0]-center[0])*scale+.5,(v[1]-low[1])*scale+.05,(v[2]-center[2])*scale+.5]
            line='v '+' '.join(f'{n:.9f}' for n in v)
        elif line.startswith('mtllib '): line='mtllib circuit_bench.mtl'
        lines.append(line)
    put('src/main/resources/assets/eln/models/block/circuit_bench.obj','\n'.join(lines)+'\n')
    mtl=(model/'voltagesource.mtl').read_text(encoding='utf-8')
    mtl='\n'.join('map_Kd #texture0' if line.startswith('map_Kd ') else line for line in mtl.splitlines())+'\n'
    put('src/main/resources/assets/eln/models/block/circuit_bench.mtl',mtl)
    put('src/main/resources/assets/eln/textures/block/circuit_bench.png',(model/'voltagesource.png').read_bytes())
    put('LICENSE-legacy.md',(args.legacy/'LICENSE.md').read_bytes())
    put('PROVENANCE.json',json.dumps({'rewired_commit':REWIRED,'mdk_commit':MDK,'audit_candidate_commit':'5d72a6c65ef72e112a0f4fdb91fb7c33df446ba7','scope':'Audited QR and RHS/lifecycle corrections retained; no claim that all 1.12 audit changes or all upstream features have been ported.','sources':manifest,'asset':{'source':'assets/eln/model/voltagesource','attribution':'Electrical Age team','license':'CC-BY-NC-SA-3.0','change':'Uniform fit and translation of OBJ vertices; material texture reference adapted for NeoForge; PNG unchanged.','obj_sha256':sha(model/'voltagesource.obj'),'texture_sha256':sha(model/'voltagesource.png'),'scale':scale}},indent=2)+'\n')
    print(f'Materialized {len(files)} inherited Java files, modern wrapper, structure and one OBJ asset.')

if __name__=='__main__': main()
