#!/usr/bin/env python3
"""Prove solver tests detect the old overwrite bug, then always restore the fixed source."""
from pathlib import Path
import argparse,json,subprocess,shutil
from xml.etree import ElementTree as ET
p=argparse.ArgumentParser();p.add_argument('--version',required=True);a=p.parse_args()
source=Path('src/main/java/mods/eln/sim/mna/SubSystem.java');before=source.read_bytes();text=before.decode()
assert text.count('Idata[s.getId()] += v;')==1
out=Path('audit-results');out.mkdir(exist_ok=True)
reports=Path('build/test-results/test')
if reports.exists(): shutil.rmtree(reports)
try:
    source.write_text(text.replace('Idata[s.getId()] += v;','Idata[s.getId()] = v;'))
    with (out/'mutation-test.log').open('w') as stream:
        result=subprocess.run(['./gradlew','test','--tests','mods.eln.sim.mna.MnaRegressionTest','--no-daemon','--stacktrace','-PportVersion='+a.version],stdout=stream,stderr=subprocess.STDOUT)
    xml=reports/'TEST-mods.eln.sim.mna.MnaRegressionTest.xml'
    assert xml.is_file(),'Mutation run produced no test report (build failure is not a detected bug)'
    tree=ET.parse(xml).getroot()
    failed={c.get('name').removesuffix('()') for c in tree.findall('testcase') if c.find('failure') is not None}
    expected={'parallelCurrentContributionsAccumulate','currentContributionsCancelAndGroundIsIgnored','rhsIsResetBetweenSteps','parallelCapacitorsEqualTheirCombinedCapacitance','capacitorInsertionOrderDoesNotChangeCircuit'}
    report={'mutation':'replace additive RHS stamping with legacy overwrite','process_returncode':result.returncode,'failing_tests':sorted(failed),'expected_failures_observed':expected<=failed,'passed':result.returncode!=0 and expected<=failed and tree.get('errors')=='0'}
    shutil.copyfile(xml,out/'mutation-test.xml')
    (out/'mutation-test.json').write_text(json.dumps(report,indent=2)+'\n')
    assert report['passed'],report
finally:
    source.write_bytes(before)
    assert source.read_bytes()==before
print(json.dumps(report,indent=2))
