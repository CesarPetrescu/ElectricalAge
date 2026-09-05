#!/usr/bin/env python3
"""Check the OBJ subset consumed by ELN and its local material texture dependencies."""
import argparse
from pathlib import Path
import json


def audit(root):
    base=root/'src/main/resources/assets/eln/model'
    issues=[]; count={'models':0,'triangle_faces':0,'material_libraries':0,'material_texture_references':0}
    for path in sorted(base.rglob('*.obj')):
        count['models']+=1
        vertices=uvs=0; libraries=[]
        for line_number,line in enumerate(path.read_text().splitlines(),1):
            words=line.split()
            if not words: continue
            if words[0]=='v': vertices+=1
            elif words[0]=='vt': uvs+=1
            elif words[0]=='mtllib': libraries.extend(words[1:])
            elif words[0]=='f':
                if len(words)!=4:
                    issues.append(f'{path.relative_to(base)}:{line_number}: unsupported face arity {len(words)-1}')
                    continue
                count['triangle_faces']+=1
                try:
                    for ref in words[1:]:
                        fields=ref.split('/')
                        if not 1<=int(fields[0])<=vertices: raise ValueError('vertex index out of range')
                        if len(fields)>1 and fields[1] and not 1<=int(fields[1])<=uvs: raise ValueError('UV index out of range')
                except (ValueError,IndexError) as error:
                    issues.append(f'{path.relative_to(base)}:{line_number}: {error}')
        for name in libraries:
            mtl=path.parent/name
            count['material_libraries']+=1
            if not mtl.is_file(): issues.append(f'{path.relative_to(base)}: missing material library {name}'); continue
            for i,line in enumerate(mtl.read_text().splitlines(),1):
                words=line.split(maxsplit=1)
                if words and words[0]=='map_Kd':
                    count['material_texture_references']+=1
                    name=words[1] if len(words)>1 else ''
                    if not name or '\\' in name or ':' in name or not (mtl.parent/name).is_file():
                        issues.append(f'{mtl.relative_to(base)}:{i}: unresolved texture {name}')
    return {'statistics':count,'errors':issues,'passed':not issues,
            'scope':'OBJ triangle arity and positive vertex/UV indices; local MTL and diffuse texture references. Does not certify visual appearance.'}

if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('root',nargs='?',default='.');ap.add_argument('--json',dest='out');a=ap.parse_args()
    result=audit(Path(a.root));text=json.dumps(result,indent=2);print(text)
    if a.out: Path(a.out).write_text(text+'\n')
    raise SystemExit(not result['passed'])
