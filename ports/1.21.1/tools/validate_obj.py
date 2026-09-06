#!/usr/bin/env python3
"""Strict check for the currently exported bench mesh, not a generic OBJ importer."""
from pathlib import Path
import math
ROOT=Path(__file__).resolve().parents[1]

def validate(text):
    counts={'v':0,'vt':0,'vn':0}; faces=0;objects=[]
    for number,line in enumerate(text.splitlines(),1):
        words=line.split()
        if not words or words[0].startswith('#'):continue
        kind=words[0]
        if kind in counts:
            values=list(map(float,words[1:]))
            if not values or not all(math.isfinite(v) for v in values): raise ValueError(f'Nonfinite vertex at {number}')
            counts[kind]+=1
        elif kind=='o': objects.append(words[1])
        elif kind=='f':
            if len(words)!=4: raise ValueError(f'Expected triangle at {number}')
            for corner in words[1:]:
                indices=corner.split('/')
                if len(indices)!=3 or not all(indices): raise ValueError(f'Missing explicit vertex/UV/normal at {number}')
                for index,field in zip(map(int,indices),('v','vt','vn')):
                    if not 1<=index<=counts[field]: raise ValueError(f'Invalid {field} index at {number}')
            faces+=1
    if objects!=['main'] or counts!={'v':8,'vt':15,'vn':6} or faces!=12: raise ValueError('Unexpected bench mesh, including possible helper objects')
    return {'triangles':faces,**counts}

if __name__=='__main__':
    print(validate((ROOT/'src/main/resources/assets/eln/models/block/circuit_bench.obj').read_text()))
