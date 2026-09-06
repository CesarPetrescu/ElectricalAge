#!/usr/bin/env python3
"""Repair known binary import defects; wrong original hashes abort the repair."""
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
PIN='3a0088b384aa4111c3a7f984d4cbc5f4ff142ed9'
FILES=[
 'src/main/resources/assets/eln/model-to-be-integrated/Downlink2/Downlink2.blend1',
 'src/main/resources/assets/eln/sounds/source/heat_turbine_200v.m_p',
 'src/main/resources/assets/eln/sounds/source/heat_turbine_50v.m_p',
 'src/main/resources/assets/eln/sounds/source/water_turbine.m_p',
]
def sha(data):return hashlib.sha256(data).hexdigest()
def restore(root):
    root=Path(root)
    marker=root/'migrations/000-binary-import-repair.json'
    if marker.exists():return
    reference=root/'reference/rewired-1.12.2'
    expected=json.loads((reference/'SNAPSHOT.json').read_text())['files']
    broken=[name for name in FILES if any(sha((prefix/name).read_bytes()) != expected[name] for prefix in (root,reference))]
    records=[]
    if broken:
        with tempfile.TemporaryDirectory(prefix='eln-binary-original-') as temp:
            subprocess.run(['git','init',temp],check=True)
            subprocess.run(['git','-C',temp,'fetch','--depth=1','https://github.com/brambora69123/electrical-age-rewired.git',PIN],check=True)
            subprocess.run(['git','-C',temp,'checkout','--detach',PIN],check=True)
            for name in broken:
                data=(Path(temp)/name).read_bytes()
                if sha(data)!=expected[name]:
                    raise RuntimeError('Original bytes did not match pinned manifest: '+name)
                for prefix in (root,reference):
                    target=prefix/name
                    records.append({'path':target.relative_to(root).as_posix(),'before':sha(target.read_bytes()),'after':sha(data)})
                    target.write_bytes(data)
    marker.parent.mkdir(parents=True,exist_ok=True)
    marker.write_text(json.dumps(records,indent=2)+'\n')
    print('Repaired binary import paths:',len(records))
if __name__=='__main__':restore(Path(__file__).resolve().parents[1])
