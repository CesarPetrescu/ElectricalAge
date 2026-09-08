"""Check the committed cooler geometry, packed assets and classic ELN references."""
from pathlib import Path
import hashlib
import json
import math
import struct
import subprocess
import zlib

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'artwork/evaporative_cooler'
GAME = ROOT / 'src/main/resources/assets/eln/model/evaporativecooler'


def png(data):
    assert data[:8] == b'\x89PNG\r\n\x1a\n', 'Invalid PNG signature'
    offset=8; size=None; ended=False
    while offset<len(data):
        n=struct.unpack_from('>I',data,offset)[0]
        assert offset+n+12<=len(data), 'Truncated PNG chunk'
        chunk=data[offset+4:offset+8+n]
        assert zlib.crc32(chunk)&0xffffffff==struct.unpack_from('>I',data,offset+8+n)[0], 'PNG CRC mismatch'
        kind=chunk[:4]
        if kind==b'IHDR':size=struct.unpack_from('>II',chunk,4)
        offset+=12+n
        if kind==b'IEND':ended=True;break
    assert ended and size and offset==len(data), 'PNG incomplete'
    return size


def geometry(manifest):
    vertices=[];uvs=[];groups={};part=None
    for line in (GAME/'evaporativecooler.obj').read_text().splitlines():
        t=line.split()
        if not t:continue
        if t[0]=='v':
            v=tuple(map(float,t[1:4]));assert len(v)==3 and all(math.isfinite(x) for x in v)
            assert all(-.505<=x<=.505 for x in v), 'Outside block footprint'
            vertices.append(v)
        elif t[0]=='vt':
            uv=tuple(map(float,t[1:3]));assert all(math.isfinite(x) and 0<=x<=1 for x in uv)
            uvs.append(uv)
        elif t[0]=='o':part=t[1];groups[part]=[]
        elif t[0]=='f':
            assert len(t)==4 and part, 'ELN requires named, triangulated faces'
            pairs=[tuple(map(int,p.split('/'))) for p in t[1:]]
            assert all(len(p)==2 and 1<=p[0]<=len(vertices) and 1<=p[1]<=len(uvs) for p in pairs)
            face=[p[0]-1 for p in pairs];a,b,c=[vertices[i] for i in face]
            ab=[b[i]-a[i] for i in range(3)];ac=[c[i]-a[i] for i in range(3)]
            cross=[ab[1]*ac[2]-ab[2]*ac[1],ab[2]*ac[0]-ab[0]*ac[2],ab[0]*ac[1]-ab[1]*ac[0]]
            assert sum(x*x for x in cross)>1e-16, 'Degenerate triangle'
            groups[part].append(face)
    assert set(groups)=={'main','rotor','pad_dry','pad_wet','water'}
    counts={p:len(f) for p,f in groups.items()}
    assert counts==manifest['parts'] and sum(counts.values())==manifest['triangles']<=768
    assert len(vertices)==manifest['vertices']
    # Verify the entire swept fan envelope, not only its rest pose.
    pivot=manifest['rotor_pivot_mc'];assert pivot==[-.375,.125,0.0]
    rotor=[vertices[i] for i in {v for f in groups['rotor'] for v in f}]
    radius=max(math.hypot(v[1]-pivot[1],v[2]-pivot[2]) for v in rotor)
    assert radius<.34375*math.cos(math.pi/8), 'Blade intersects octagonal guard'
    assert pivot[1]-radius>-.1875, 'Blade intersects reservoir/base'
    assert max(v[0] for v in rotor)<-.32, 'Blade intersects rear support or fin bank'
    for angle in range(0,360,5):
        co=math.cos(math.radians(angle));si=math.sin(math.radians(angle))
        for x,y,z in rotor:
            yy=co*(y-pivot[1])-si*z+pivot[1];zz=si*(y-pivot[1])+co*z
            assert -.5<=yy<=.5 and -.5<=zz<=.5
    metadata=(GAME/'evaporativecooler.txt').read_text()
    assert 'originX -0.375' in metadata and 'originY 0.125' in metadata
    water=[vertices[i] for i in {v for f in groups['water'] for v in f}]
    assert abs(min(v[1] for v in water)-manifest['gauge_bottom_mc'])<1e-6
    renderer=(ROOT/'src/main/kotlin/mods/eln/transparentnode/evaporative/EvaporativeCoolerRender.kt').read_text()
    assert '-.4375' in renderer and 'draw(angle, 1f, 0f, 0f)' in renderer
    print('Geometry passed:',sum(counts.values()),'triangles; fan sweep, gauge anchor and five state groups')


def check():
    manifest=json.loads((ASSETS/'manifest.json').read_text())
    icon=ROOT/'src/main/resources/assets/eln/textures/blocks/evaporativecooler.png'
    pairs=[(GAME/'evaporativecooler.obj','obj_sha256'),(GAME/'atlas.png','atlas_sha256'),
           (ROOT/'tools/evaporative/build_model.py','generator_sha256'),(icon,'icon_sha256'),
           (ASSETS/'evaporativecooler.blend','blend_sha256'),(ASSETS/'evaporativecooler.glb','glb_sha256')]
    for path,key in pairs:assert hashlib.sha256(path.read_bytes()).hexdigest()==manifest[key],str(path)
    for relative,expected in manifest['reference_sha256'].items():
        assert hashlib.sha256((ROOT/relative).read_bytes()).hexdigest()==expected, 'Changed classic reference: '+relative
    assert png((GAME/'atlas.png').read_bytes())==(64,64)
    assert png(icon.read_bytes())==(32,32)
    geometry(manifest)
    data=(ASSETS/'evaporativecooler.glb').read_bytes()
    magic,version,total=struct.unpack_from('<4sII',data)
    assert magic==b'glTF' and version==2 and total==len(data), 'GLB header/length corrupted'
    offset=12;chunks=[]
    while offset<total:
        size,kind=struct.unpack_from('<II',data,offset)
        assert size%4==0 and offset+8+size<=total
        chunks.append((kind,data[offset+8:offset+8+size]));offset+=8+size
    assert offset==total and len(chunks)==2
    assert chunks[0][0]==0x4e4f534a and chunks[1][0]==0x004e4942
    scene=json.loads(chunks[0][1]);binary=chunks[1][1]
    assert scene['animations'] and scene['meshes']
    assert len(scene['buffers'])==1 and scene['buffers'][0]['byteLength']<=len(binary)
    for v in scene['bufferViews']:
        assert v['buffer']==0 and v.get('byteOffset',0)+v['byteLength']<=len(binary)
    for image in scene['images']:
        assert image['mimeType']=='image/png' and 'bufferView' in image
        v=scene['bufferViews'][image['bufferView']];start=v.get('byteOffset',0)
        assert png(binary[start:start+v['byteLength']])==(64,64)
    if (ROOT/'.git').exists():
        attrs=subprocess.check_output(['git','check-attr','text','--','artwork/evaporative_cooler/evaporativecooler.glb'],cwd=ROOT,text=True)
        assert attrs.strip().endswith(': unset'), 'GLB must be binary'
    print('Asset integrity passed: classic references unchanged, 64px diffuse, 32px icon, packed GLB and source/game hashes.')


if __name__=='__main__':check()
