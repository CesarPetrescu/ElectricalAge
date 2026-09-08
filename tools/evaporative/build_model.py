#!/usr/bin/env python3
"""Rebuild the evaporative dissipator from ELN's existing heatsink/fan assets.

Uses Blender's Python module (bpy 5.2). All game geometry is flat shaded, on the
same coarse grid as the original cooler; the shared diffuse atlas is 64x64.
--no-render skips presentation renders, not the game icon. No network access.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import math
import sys
from pathlib import Path
import bpy
import numpy as np
from mathutils import Vector

PIVOT = (-0.375, 0.125, 0.0)
GAUGE_BOTTOM = -0.4375
PARTS = {'main', 'rotor', 'pad_dry', 'pad_wet', 'water'}


def arguments():
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else sys.argv[1:]
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--renders', type=Path)
    parser.add_argument('--no-render', action='store_true')
    return parser.parse_args(argv)


def mc(v):
    """Right-handed Minecraft Y-up to Blender Z-up."""
    return Vector((v[0], -v[2], v[1]))


def read_obj(path):
    """Read only the textured, named game parts (not the old reference cube)."""
    vertices, uvs, groups = [], [], {}
    group = None
    for line in path.read_text().splitlines():
        t = line.split()
        if not t:
            continue
        if t[0] == 'v':
            vertices.append(tuple(map(float, t[1:4])))
        elif t[0] == 'vt':
            uvs.append(tuple(map(float, t[1:3])))
        elif t[0] == 'o':
            group = t[1]
            groups[group] = []
        elif t[0] == 'f' and group is not None:
            face = [p.split('/') for p in t[1:]]
            if all(len(p) > 1 and p[1] for p in face):
                groups[group].append([(int(p[0])-1, int(p[1])-1) for p in face])
    return vertices, uvs, groups


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def build(root: Path, renders: Path, presentation: bool):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    game_root = root / 'src/main/resources/assets/eln/model'
    out = game_root / 'evaporativecooler'
    art = root / 'artwork/evaporative_cooler'
    for p in (out, art, renders):
        p.mkdir(parents=True, exist_ok=True)
    scene = bpy.context.scene
    scene.unit_settings.system = 'METRIC'
    scene.render.engine = 'CYCLES'
    scene.cycles.device = 'CPU'
    scene.cycles.samples = 32
    scene.cycles.use_denoising = False
    scene.render.threads_mode = 'FIXED'
    scene.render.threads = 4
    scene.render.image_settings.file_format = 'PNG'
    scene.view_settings.view_transform = 'Standard'
    scene.world = bpy.data.worlds.new('Neutral reference lighting')
    scene.world.use_nodes = True
    scene.world.node_tree.nodes['Background'].inputs[0].default_value = (.5,.5,.5,1)
    scene.world.node_tree.nodes['Background'].inputs[1].default_value = .6

    # Preserve the existing 240 V cooler's exact pixels in the lower-left tile.
    # Additional 16px tiles supply only pad, water, copper and plain metal.
    reference = game_root / '200vactivethermaldissipatora/tex.png'
    passive_texture = game_root / 'passivethermaldissipatora/tex.png'
    old_image = bpy.data.images.load(str(passive_texture))
    active_image = bpy.data.images.load(str(reference))
    pixels = np.ones((64,64,4), dtype=np.float32)
    pixels[:,:,:3] = .18
    pixels[:32,:32] = np.array(old_image.pixels[:],dtype=np.float32).reshape(32,32,4)
    pixels[:32,32:] = np.array(active_image.pixels[:],dtype=np.float32).reshape(32,32,4)
    colors = {'metal':(83,83,83), 'light':(118,118,118), 'dark':(41,41,41),
              'blue':(48,72,121), 'dry':(118,101,73), 'wet':(69,79,64),
              'water':(54,87,139), 'copper':(144,91,52)}
    tiles = {}
    for i,(name,col) in enumerate(colors.items()):
        x,y = (i%4)*16,32+(i//4)*16
        tiles[name] = (x,y)
        for py in range(16):
            for px in range(16):
                # Deliberate pixel treatment, not PBR/noise or baked studio light.
                v = ((px*13+py*7)%5-2) / 255.0
                if name in ('dry','wet') and (px+py)%4==0:
                    v -= .055
                if name=='water' and py%5==0:
                    v += .045
                pixels[y+py,x+px,:3] = np.clip(np.array(col)/255.0+v,0,1)
    atlas = bpy.data.images.new('ELN cooler diffuse',width=64,height=64,alpha=False)
    atlas.pixels.foreach_set(pixels.ravel())
    atlas.filepath_raw=str(out/'atlas.png'); atlas.file_format='PNG'; atlas.save(); atlas.pack()

    def material(image, name):
        m=bpy.data.materials.new(name); m.use_nodes=True
        bsdf=m.node_tree.nodes['Principled BSDF']
        bsdf.inputs['Metallic'].default_value=0
        bsdf.inputs['Roughness'].default_value=1
        bsdf.inputs['Specular IOR Level'].default_value=0
        tex=m.node_tree.nodes.new('ShaderNodeTexImage');tex.image=image;tex.interpolation='Closest'
        m.node_tree.links.new(tex.outputs['Color'],bsdf.inputs['Base Color'])
        return m
    mat=material(atlas,'ELN diffuse only')
    objects=[]

    def mesh(name, verts, faces, uvfaces, part, m=mat, record=True):
        data=bpy.data.meshes.new(name)
        data.from_pydata([mc(v) for v in verts],[],faces);data.update()
        obj=bpy.data.objects.new(name,data);scene.collection.objects.link(obj)
        data.materials.append(m);uv=data.uv_layers.new(name='Diffuse')
        for poly,coords in zip(data.polygons,uvfaces):
            poly.use_smooth=False
            for li,xy in zip(poly.loop_indices,coords):uv.data[li].uv=xy
        obj['eln_part']=part
        if record:objects.append(obj)
        if part=='pad_dry':obj.hide_render=True
        return obj

    def box(name, pos, dims, tile, part='main'):
        x,y,z=pos;a,b,c=(d/2 for d in dims)
        verts=[(x-a,y-b,z-c),(x+a,y-b,z-c),(x+a,y+b,z-c),(x-a,y+b,z-c),
               (x-a,y-b,z+c),(x+a,y-b,z+c),(x+a,y+b,z+c),(x-a,y+b,z+c)]
        faces=[(0,3,2,1),(4,5,6,7),(0,4,7,3),(1,2,6,5),(0,1,5,4),(3,7,6,2)]
        tx,ty=tiles[tile]
        u,v=(tx+.5)/64,(ty+.5)/64;du=dv=15/64
        return mesh(name,verts,faces,[[(u,v),(u+du,v),(u+du,v+dv),(u,v+dv)]]*6,part)

    def reuse(path, group, name, part, transform=lambda v,i:v, m=mat, uvscale=.5, uoffset=0.0, record=True):
        verts,uvs,groups=read_obj(path)
        source=groups[group];ids=sorted({vi for f in source for vi,_ in f});remap={v:i for i,v in enumerate(ids)}
        faces=[[remap[vi] for vi,_ in f] for f in source]
        uvfaces=[[(uoffset+uvs[ti][0]*uvscale,uvs[ti][1]*uvscale) for _,ti in f] for f in source]
        return mesh(name,[transform(verts[i],i) for i in ids],faces,uvfaces,part,m,record)

    # Original passive base and six broad fins. Only fin length changes to make
    # physical room for the front fan and rear pad; the base/UVs are unchanged.
    passive=game_root/'passivethermaldissipatora/passivethermaldissipatora.obj'
    active=game_root/'200vactivethermaldissipatora/200vactivethermaldissipatora.obj'
    def shorten_fins(v,i):
        return v if i<8 else (.03125+v[0]*.4375,v[1],v[2])
    reuse(passive,'main','Classic heatsink base and six fins','main',shorten_fins)
    # Reuse the real four-bladed ELN fan. It faces front/back because that is the
    # installed cooler's existing ventilation direction. Ports/builds don't move.
    def fan_transform(v,i):
        return (PIVOT[0]-(v[1]-.3125)*.6,PIVOT[1]+v[0]*.6,v[2]*.6)
    reuse(active,'rot','Classic four-bladed fan','rotor',fan_transform,uoffset=.5)

    # A simple octagonal fan surround, no cabinet, roof, logo or fine grille.
    verts=[]
    for x,r in ((-.4375,.375),(-.4375,.34375),(-.40625,.375),(-.40625,.34375)):
        for j in range(8):
            a=(j+.5)*math.pi/4
            verts.append((x,PIVOT[1]+math.cos(a)*r,math.sin(a)*r))
    faces=[]
    for j in range(8):
        k=(j+1)%8
        faces.extend([(j,k,8+k,8+j),(16+j,24+j,24+k,16+k),(j,16+j,16+k,k),(8+j,8+k,24+k,24+j)])
    u,v=tiles['metal'];quad=[((u+.5)/64,(v+.5)/64),((u+15.5)/64,(v+.5)/64),((u+15.5)/64,(v+15.5)/64),((u+.5)/64,(v+15.5)/64)]
    mesh('Eight-sided fan surround',verts,faces,[quad]*len(faces),'main')
    box('Fan support',(-.265625,-.03125,0),(.0625,.3125,.09375),'blue')
    box('Fan axle',(-.296875,.125,0),(.09375,.0625,.0625),'dark')
    # Thin tray rim uses the existing base as its reservoir rather than growing
    # a second enclosure. Two simple side pads mark the native heat connections.
    for z in (-.46875,.46875):
        box('Reservoir rim',(0,-.171875,z),(1,.03125,.0625),'metal')
        box('Thermal connection',(0,-.34375,math.copysign(.501,z)),(.1875,.1875,.002),'copper')
    for x in (-.46875,.46875):
        box('Reservoir rim',(x,-.171875,0),(.0625,.03125,.875),'metal')
        box('240 V terminal',(math.copysign(.501,x),-.34375,-.28125),(.002,.125,.125),'blue')
    for part,tile in [('pad_dry','dry'),('pad_wet','wet')]:
        box('Evaporative media '+part,(.34375,.09375,0),(.0625,.5,.8125),tile,part)
    for z in (-.4375,.4375):box('Pad retaining edge',(.34375,.09375,z),(.09375,.5625,.0625),'dark')
    box('Water distributor',(.34375,.375,0),(.09375,.0625,.9375),'metal')
    box('Water feed',(.34375,.0625,.40625),(.0625,.8125,.0625),'blue')
    box('Top water port',(.34375,.46875,.40625),(.125,.0625,.125),'blue')
    box('Pump block',(.3125,-.125,-.34375),(.125,.125,.125),'dark')
    # Recessed side sight strip. The dynamic liquid remains on the same plane
    # and scales from GAUGE_BOTTOM, shared with the renderer's anchor.
    box('Water sight recess',(-.28125,-.34375,.501),(.09375,.21875,.002),'dark')
    box('Water sight strip',(-.28125,-.34375,.503),(.0625,.1875,.001),'water','water')

    rotor_root=bpy.data.objects.new('Fan animation pivot',None);scene.collection.objects.link(rotor_root)
    rotor_root.location=mc(PIVOT);bpy.context.view_layer.update()
    for obj in objects:
        if obj['eln_part']=='rotor':
            transform=obj.matrix_world.copy();obj.parent=rotor_root;obj.matrix_world=transform
    # Quarter-turn keys avoid quaternion shortest-path loss of a full turn.
    for frame,angle in [(1,0),(16,math.pi/2),(31,math.pi),(46,3*math.pi/2),(61,2*math.pi)]:
        rotor_root.rotation_euler.x=angle;rotor_root.keyframe_insert('rotation_euler',frame=frame)
    scene.frame_start=1;scene.frame_end=60;scene.render.fps=30;scene.frame_set(1)
    bpy.context.view_layer.update()

    # ELN's OBJ dialect: global indices, triangles, named groups, one diffuse.
    lines=['# ELN evaporative dissipator: adapted classic heatsink/fan','mtllib evaporativecooler.mtl']
    nv=nt=0;bounds=[];counts={}
    for part in sorted(PARTS):
        lines.append('o '+part);counts[part]=0
        for obj in (o for o in objects if o['eln_part']==part):
            data=obj.data;data.calc_loop_triangles()
            for vertex in data.vertices:
                v=obj.matrix_world@vertex.co;xyz=(v.x,v.z,-v.y);bounds.append(xyz)
                lines.append('v %.6f %.6f %.6f'%xyz)
            for uv in data.uv_layers.active.data:lines.append('vt %.6f %.6f'%tuple(uv.uv))
            lines.append('usemtl atlas')
            for t in data.loop_triangles:
                lines.append('f '+' '.join(f'{nv+vi+1}/{nt+li+1}' for vi,li in zip(t.vertices,t.loops)))
                counts[part]+=1
            nv+=len(data.vertices);nt+=len(data.uv_layers.active.data)
    (out/'evaporativecooler.obj').write_text('\n'.join(lines)+'\n')
    (out/'evaporativecooler.mtl').write_text('newmtl atlas\nKd 1 1 1\nd 1\nillum 1\nmap_Kd atlas.png\n')
    (out/'evaporativecooler.txt').write_text('o rotor\nf originX -0.375\nf originY 0.125\nf originZ 0.0\n')
    mins=[min(v[i] for v in bounds) for i in range(3)];maxs=[max(v[i] for v in bounds) for i in range(3)]
    assert all(-.505<=v<=.505 for v in mins+maxs),(mins,maxs)
    assert sum(counts.values())<=768,counts
    bpy.ops.object.select_all(action='DESELECT')
    for obj in objects:
        if obj['eln_part']!='pad_dry':obj.select_set(True)
    rotor_root.select_set(True)
    bpy.ops.export_scene.gltf(filepath=str(art/'evaporativecooler.glb'),use_selection=True,export_format='GLB',export_animations=True)

    def area(name,p,power,size):
        data=bpy.data.lights.new(name,'AREA');data.energy=power;data.size=size
        obj=bpy.data.objects.new(name,data);scene.collection.objects.link(obj);obj.location=p
        obj.rotation_euler=(-obj.location).to_track_quat('-Z','Y').to_euler()
    area('Reference key',(-4,-5,6),500,5);area('Reference fill',(4,2,4),300,4)
    camera_data=bpy.data.cameras.new('Reference camera');camera=bpy.data.objects.new('Reference camera',camera_data)
    scene.collection.objects.link(camera);scene.camera=camera;camera_data.type='ORTHO'
    def view(pos,scale):
        camera.location=pos;camera.rotation_euler=(-camera.location).to_track_quat('-Z','Y').to_euler();camera_data.ortho_scale=scale
    view((-4,-6,4),1.7)
    scene.render.resolution_x=900;scene.render.resolution_y=900;scene.render.resolution_percentage=100
    scene.render.film_transparent=True
    bpy.ops.wm.save_as_mainfile(filepath=str(art/'evaporativecooler.blend'))
    if presentation:
        for name,pos in [('front',(-4,-6,4)),('rear',(4,-6,4))]:
            view(pos,1.7);scene.render.filepath=str(renders/f'evaporative-cooler-{name}.png');bpy.ops.render.render(write_still=True)
    # Actual 32px game icon, matching the existing heatsinks' sprite resolution.
    view((-4,-6,4),1.58);scene.render.resolution_x=scene.render.resolution_y=32
    icon=root/'src/main/resources/assets/eln/textures/blocks/evaporativecooler.png'
    scene.render.filepath=str(icon);bpy.ops.render.render(write_still=True)

    refs=[passive,active,reference,passive_texture]
    manifest={'blender':bpy.app.version_string,'design':'classic-eln-family-v2','triangles':sum(counts.values()),
              'vertices':nv,'parts':counts,'bounds_min':mins,'bounds_max':maxs,'rotor_pivot_mc':list(PIVOT),
              'gauge_bottom_mc':GAUGE_BOTTOM,'atlas_size':[64,64],'icon_size':[32,32],
              'reference_sha256':{str(p.relative_to(root)):digest(p) for p in refs},
              'generator_sha256':digest(Path(__file__)),'obj_sha256':digest(out/'evaporativecooler.obj'),
              'atlas_sha256':digest(out/'atlas.png'),'icon_sha256':digest(icon),
              'blend_sha256':digest(art/'evaporativecooler.blend'),'glb_sha256':digest(art/'evaporativecooler.glb')}
    (art/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps(manifest,indent=2))

    if presentation:
        # Review source meshes at identical scale/light, not stylized mock-ups.
        view((-4,-6,4),5.25);scene.render.resolution_x=1680;scene.render.resolution_y=660
        right=camera.rotation_euler.to_matrix()@Vector((1,0,0))
        shift=right*1.7
        for obj in objects:
            if obj.parent is None:obj.location+=shift
        rotor_root.location+=shift
        for index,folder in enumerate(['passivethermaldissipatora','200vactivethermaldissipatora']):
            img=bpy.data.images.load(str(game_root/folder/'tex.png'));m=material(img,folder)
            for part in ['main']+(['rot'] if index==1 else []):
                obj=reuse(game_root/folder/(folder+'.obj'),part,folder+' '+part,'reference',m=m,uvscale=1,record=False)
                obj.location+=right*((index-1)*1.7)
        scene.render.filepath=str(renders/'cooler-family-comparison.png');bpy.ops.render.render(write_still=True)


if __name__=='__main__':
    args=arguments()
    build(args.root.resolve(),(args.renders or args.root/'build/evaporative-renders').resolve(),not args.no_render)
