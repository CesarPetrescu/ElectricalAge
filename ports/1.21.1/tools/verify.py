#!/usr/bin/env python3
"""Fail closed on absent tests, accidentally bundled game/test classes or missing port assets."""
from pathlib import Path
import hashlib, json, struct, xml.etree.ElementTree as ET, zipfile, tomllib
from validate_resources import validate
ROOT=Path(__file__).resolve().parents[1]

def require(value,message):
    if not value: raise SystemExit(message)

def main():
    reports=list((ROOT/'sim-core/build/test-results/test').glob('TEST-*.xml'))
    require(reports,'No real core test results found')
    total=0
    for path in reports:
        suite=ET.parse(path).getroot()
        require(suite.tag=='testsuite','Unexpected JUnit report shape')
        total+=int(suite.get('tests','0'))
        require(all(int(suite.get(k,'0'))==0 for k in ('failures','errors','skipped')),f'Unsuccessful tests: {path}')
    require(total>=517,f'Test count dropped: {total}, expected at least 517')
    validate(ROOT/'src/main/resources')
    for source in (ROOT/'sim-core/src/main/java').rglob('*.java'):
        text=source.read_text(encoding='utf-8')
        require(not any(token in text for token in ('import net.minecraft.','import net.minecraftforge.','import net.neoforged.','import org.lwjgl.','import io.netty.')),f'Platform dependency in pure core: {source}')
    for resource in (ROOT/'src').rglob('*.json'):
        json.loads(resource.read_text(encoding='utf-8'))
    provenance=json.loads((ROOT/'PROVENANCE.json').read_text())
    texture=ROOT/'src/main/resources/assets/eln/textures/block/circuit_bench.png'
    require(hashlib.sha256(texture.read_bytes()).hexdigest()==provenance['asset']['texture_sha256'],'Inherited texture changed unexpectedly')
    jars=[p for p in (ROOT/'build/libs').glob('*.jar') if not p.name.endswith('-sources.jar')]
    require(len(jars)==1,f'Expected exactly one distributable jar, found {jars}')
    with zipfile.ZipFile(jars[0]) as archive:
        names=set(archive.namelist())
        for path in ('META-INF/neoforge.mods.toml','mods/eln/modern/ElectricalAgeModern.class','mods/eln/modern/CircuitBenchBlockEntity.class','mods/eln/sim/mna/SubSystem.class','mods/eln/sim/bench/RcCircuit.class','assets/eln/models/block/circuit_bench.obj','assets/eln/models/block/circuit_bench.mtl','assets/eln/textures/block/circuit_bench.png','data/eln/recipe/circuit_bench.json'):
            require(path in names,f'Missing jar entry: {path}')
        metadata=tomllib.loads(archive.read('META-INF/neoforge.mods.toml').decode('utf-8'))
        version=next(line.split('=',1)[1] for line in (ROOT/'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
        require(metadata['mods'][0]['version']==version,'Packaged version does not match project')
        for path in ('mods/eln/sim/network/CircuitNetwork.class','mods/eln/modern/network/LevelCircuitManager.class','mods/eln/modern/network/CircuitDeviceBlockEntity.class'):
            require(path in names, f'Missing connected-network implementation: {path}')
        for name in ('voltage_source','resistive_wire','resistive_load','capacitor'):
            for path in (f'assets/eln/blockstates/{name}.json',f'assets/eln/models/item/{name}.json',f'data/eln/recipe/{name}.json',f'data/eln/loot_table/blocks/{name}.json'):
                require(path in names, f'Missing network resource: {path}')
        require(not any(n.startswith(('net/minecraft/','net/neoforged/','org/junit/','mods/eln/audit/','mods/eln/modern/gametest/','data/eln/structure/')) for n in names),'Game or test implementation leaked into distributable')
        for name in names:
            if name.endswith('.class'):
                content=archive.read(name)
                require(content[:4]==b'\xca\xfe\xba\xbe' and struct.unpack('>H',content[6:8])[0]==65,f'Not Java 21 bytecode: {name}')
    result={'core_junit_cases':total,'core_has_no_game_imports':True,'packaging_verified':True,'jar':jars[0].name,'jar_sha256':hashlib.sha256(jars[0].read_bytes()).hexdigest()}
    (ROOT/'build/evidence').mkdir(parents=True,exist_ok=True)
    (ROOT/'build/evidence/verification.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2))

if __name__=='__main__': main()
