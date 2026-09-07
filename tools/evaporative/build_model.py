#!/usr/bin/env python3
"""Build the editable EC-240 asset with Blender 5.2 LTS (bpy), then export ELN OBJ.

Run with `python build_model.py --root . --renders build/evaporative-renders`, or
`blender --background --python build_model.py -- --root .`. No downloaded assets.
ELN's loader needs triangulated faces, global indices, map_Kd and named parts.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import math
import sys
from pathlib import Path
import bpy
from mathutils import Vector


def arguments():
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else sys.argv[1:]
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--renders', type=Path)
    parser.add_argument('--no-render', action='store_true')
    return parser.parse_args(argv)


def mc(v):
    """Minecraft Y-up to Blender Z-up, right-handed in both systems."""
    return Vector((v[0], -v[2], v[1]))


def build(root: Path, renders: Path, render: bool):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    out = root / 'src/main/resources/assets/eln/model/evaporativecooler'
    source = root / 'artwork/evaporative_cooler'
    out.mkdir(parents=True, exist_ok=True)
    source.mkdir(parents=True, exist_ok=True)
    renders.mkdir(parents=True, exist_ok=True)
    scene = bpy.context.scene
    scene.unit_settings.system = 'METRIC'
    scene.unit_settings.scale_length = 1
    scene.render.engine = 'CYCLES'
    scene.cycles.device = 'CPU'
    scene.cycles.samples = 64
    scene.cycles.use_denoising = False
    scene.render.threads_mode = 'FIXED'
    scene.render.threads = 4
    scene.render.resolution_x = 1100
    scene.render.resolution_y = 1100
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = 'PNG'
    scene.render.film_transparent = False
    scene.view_settings.view_transform = 'AgX'
    scene.world = bpy.data.worlds.new('Workshop atmosphere')
    scene.world.use_nodes = True
    scene.world.node_tree.nodes['Background'].inputs[0].default_value = (.14, .18, .23, 1)
    scene.world.node_tree.nodes['Background'].inputs[1].default_value = .4

    # 16 swatches in a small shared atlas; values are sRGB and become packed pixels.
    swatches = [
        (41, 52, 62), (104, 129, 142), (184, 196, 199), (190, 110, 61),
        (24, 29, 35), (37, 157, 183), (191, 153, 90), (81, 111, 96),
        (18, 100, 143), (84, 218, 183), (242, 174, 56), (234, 237, 229),
        (87, 99, 109), (107, 66, 43), (25, 43, 57), (20, 31, 41),
    ]
    import numpy as np
    size = 256
    pixels = np.zeros((size, size, 4), dtype=np.float32)
    rng = np.random.default_rng(240)
    for tile, col in enumerate(swatches):
        tx, ty = (tile % 4) * 64, (tile // 4) * 64
        noise = rng.uniform(-.012, .012, (64, 64, 1))
        block = np.clip(np.array(col)[None, None, :] / 255.0 + noise, 0, 1)
        if tile in (1, 2, 3):
            block[::4] *= .96
        pixels[ty:ty+64, tx:tx+64, :3] = block
        pixels[ty:ty+64, tx:tx+64, 3] = 1
    image = bpy.data.images.new('ec240_atlas', width=size, height=size, alpha=False)
    image.pixels.foreach_set(pixels.flatten())
    image.filepath_raw = str(out / 'atlas.png')
    image.file_format = 'PNG'
    image.save()
    image.pack()
    mats = []
    for i in range(16):
        m = bpy.data.materials.new(f'EC240_{i:02d}')
        m.use_nodes = True
        bsdf = m.node_tree.nodes.get('Principled BSDF')
        tex = m.node_tree.nodes.new('ShaderNodeTexImage')
        tex.image = image
        tex.interpolation = 'Closest'
        m.node_tree.links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
        bsdf.inputs['Metallic'].default_value = .65 if i in (1, 2, 3, 12) else .08
        bsdf.inputs['Roughness'].default_value = .35 if i in (2, 3) else .62
        mats.append(m)
    objects = []

    def finish(obj, material, part='main', bevel=0):
        obj['eln_part'] = part
        obj.data.materials.append(mats[material])
        if bevel:
            modifier = obj.modifiers.new('Machined edge', 'BEVEL')
            modifier.width = bevel
            modifier.segments = 1
            bpy.context.view_layer.objects.active = obj
            bpy.ops.object.modifier_apply(modifier=modifier.name)
        uv = obj.data.uv_layers.active or obj.data.uv_layers.new(name='EC240 atlas')
        u, v = (material % 4 + .5) / 4, (material // 4 + .5) / 4
        for poly in obj.data.polygons:
            for k, loop_idx in enumerate(poly.loop_indices):
                # Small region avoids bleeding between atlas cells.
                uv.data[loop_idx].uv = (u + (.06 if k % 2 else -.06), v + (.06 if k // 2 else -.06))
        objects.append(obj)
        if part in ('pad_dry', 'led_off', 'led_warning'):
            obj.hide_render = True
        return obj

    def box(name, p, d, material, part='main', bevel=.006):
        bpy.ops.mesh.primitive_cube_add(size=1, location=mc(p))
        obj = bpy.context.object
        obj.name = name
        obj.dimensions = (d[0], d[2], d[1])
        bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
        return finish(obj, material, part, bevel)

    def cylinder(name, a, b, r, material, part='main', n=12):
        a, b = mc(a), mc(b)
        delta = b - a
        bpy.ops.mesh.primitive_cylinder_add(vertices=n, radius=r, depth=delta.length, location=(a+b)/2)
        obj = bpy.context.object
        obj.name = name
        obj.rotation_euler = delta.to_track_quat('Z', 'Y').to_euler()
        return finish(obj, material, part)

    def mesh(name, vertices, faces, material, part='main'):
        data = bpy.data.meshes.new(name)
        data.from_pydata([mc(p) for p in vertices], [], faces)
        data.update()
        obj = bpy.data.objects.new(name, data)
        scene.collection.objects.link(obj)
        return finish(obj, material, part)

    def ring(name, x, y, z, outer, inner, depth, material, part='main', n=32):
        vertices = []
        for xx, r in ((x-depth/2, outer), (x-depth/2, inner), (x+depth/2, outer), (x+depth/2, inner)):
            for k in range(n):
                a = 2 * math.pi * k / n
                vertices.append((xx, y + math.cos(a)*r, z + math.sin(a)*r))
        faces = []
        for k in range(n):
            j = (k+1) % n
            faces.extend([(k,j,n+j,n+k), (2*n+k,3*n+k,3*n+j,2*n+j), (k,2*n+k,2*n+j,j), (n+k,n+j,3*n+j,3*n+k)])
        return mesh(name, vertices, faces, material, part)

    # Structural frame, reservoir and isolation feet.
    box('Reservoir welded steel shell', (0,-.315,0), (.82,.27,.80), 0, bevel=.016)
    box('Reservoir top lip', (0,-.168,0), (.86,.033,.84), 2)
    for x in (-.32,.32):
        for z in (-.31,.31):
            box('Rubber isolation foot', (x,-.465,z), (.13,.07,.13), 4, bevel=.01)
    for x in (-.37,.37):
        for z in (-.37,.37):
            box('Corner extrusion', (x,.132,z), (.055,.62,.055), 1)
    box('Roof', (0,.458,0), (.86,.043,.84), 0, bevel=.012)
    for z in (-.405,.405):
        box('Upper silver trim', (0,.40,z), (.81,.028,.025), 2)
        box('Lower silver trim', (0,-.11,z), (.81,.026,.025), 2)
        # Side louvers retain genuine openings onto the copper core.
        for j in range(6):
            slat = box('Side intake louver', (.12,-.052+j*.072,z), (.39,.024,.023), 1, bevel=.002)
            slat.rotation_euler[0] = math.radians(22 if z > 0 else -22)
    # Copper thermal circuit, exposed in the side cut-outs.
    for z in (-.23,.23):
        cylinder('Vertical copper header', (.105,-.11,z), (.105,.36,z), .029, 3)
    for j in range(9):
        y = -.076 + j*.048
        cylinder('Cross-flow copper tube', (.105,y,-.23), (.105,y,.23), .012, 3, n=10)
    for j in range(13):
        z = -.25+j*.0417
        box('Copper exchanger fin', (.10,.125,z), (.145,.475,.006), 3, bevel=0)
    for z in (-.45,.45):
        sign = 1 if z > 0 else -1
        cylinder('Thermal connection stub', (.0,-.16,z-sign*.08), (.0,-.16,sign*.5), .052, 3)
        cylinder('Thermal compression nut', (0,-.16,z-sign*.022), (0,-.16,z+sign*.016), .071, 2, n=6)
    # Rear wetted media, separate dry and wet variants.
    for part, material in (('pad_dry',6), ('pad_wet',7)):
        box('Wetted cellulose block '+part, (.323,.128,0), (.06,.50,.64), material, part, .002)
        for j in range(13):
            z=-.29+j*.048
            cylinder('Media channel '+part, (.361,-.102,z), (.361,.352,z), .006, material, part, 6)
    for j in range(8):
        cylinder('Rear pad retaining bar', (.367,-.115,-.28+j*.08), (.367,.375,-.28+j*.08), .005, 12, n=6)
    # Fan, bearing, angled blades and guard. Pivot matches the accompanying .txt.
    pivot=(-.326,.124,0)
    ring('Fan bellmouth', -.332,.124,0,.292,.266,.074,2)
    ring('Fan black gasket', -.372,.124,0,.300,.286,.013,4)
    cylinder('Fan rotor hub', (-.367,.124,0), (-.291,.124,0), .063, 4, 'rotor',20)
    cylinder('Fan hub cover', (-.382,.124,0), (-.366,.124,0), .039, 5, 'rotor',16)
    for i in range(6):
        a = i*math.pi/3
        local=[(.065,-.019,-.020),(.222,-.066,-.010),(.257,.029,.028),(.124,.057,.018)]
        verts=[]
        for thickness in (-.005,.005):
            for radius,tangent,xshift in local:
                y=.124+radius*math.cos(a)-tangent*math.sin(a)
                z=radius*math.sin(a)+tangent*math.cos(a)
                verts.append((-.326+xshift+thickness,y,z))
        faces=[(0,1,2,3),(7,6,5,4),(0,4,5,1),(1,5,6,2),(2,6,7,3),(3,7,4,0)]
        mesh(f'Swept fan blade {i+1}',verts,faces,12,'rotor')
    for r in (.108,.194,.271):
        ring('Concentric wire safety guard',-.409,.124,0,r+.003,r-.003,.006,1,n=40)
    for i in range(8):
        a=i*math.pi/4
        cylinder('Radial safety guard',(-.412,.124,0),(-.412,.124+.280*math.cos(a),.280*math.sin(a)),.003,1,n=6)
    for y in (-.123,.371):
        for z in (-.247,.247):
            cylinder('Fan fastener',(-.399,y,z),(-.415,y,z),.013,2,n=6)
    # Water distributor and pump, connected by a visible riser.
    cylinder('Water riser',(.285,-.26,-.28),(.285,.407,-.28),.018,5)
    cylinder('Distribution manifold',(.281,.405,-.31),(.281,.405,.31),.027,2)
    for j in range(7):
        z=-.27+j*.09
        cylinder('Water distribution nozzle',(.282,.390,z),(.315,.358,z),.009,5,n=8)
    cylinder('Circulation pump body',(.23,-.27,-.17),(.23,-.27,.05),.053,4)
    cylinder('Pump blue end cap',(.23,-.27,-.20),(.23,-.27,-.175),.06,5)
    cylinder('Top water fill connector',(0,.457,0),(0,.5,0),.055,5,n=12)
    # External service points and tank gauge.
    box('Power terminal enclosure',(-.437,-.303,-.245),(.06,.11,.17),4)
    for z in (-.276,-.216):
        cylinder('Power terminal',(-.450,-.30,z),(-.49,-.30,z),.019,10,n=8)
    box('Sight gauge dark recess',(-.266,-.303,.411),(.109,.216,.015),4,bevel=.004)
    box('Water gauge liquid',(-.266,-.310,.421),(.075,.180,.006),8,'water',0)
    for j in range(5):
        box('Sight gauge mark',(-.330,-.385+j*.044,.424),(.025,.004,.009),11,bevel=0)
    box('Front identification plate',(-.435,-.300,.123),(.022,.123,.23),14)
    # Text is mesh geometry so it survives every interchange format without font dependencies.
    def lettering(text, pos, size, material):
        curve=bpy.data.curves.new('EC240 lettering','FONT')
        curve.body=text; curve.size=size; curve.align_x='CENTER'; curve.extrude=.00025; curve.resolution_u=2
        obj=bpy.data.objects.new(text,curve); scene.collection.objects.link(obj)
        obj.location=mc(pos)
        # Text lies in the Y/Z plane, reading from the front (-X).
        obj.rotation_euler=(math.pi/2,0,-math.pi/2)
        bpy.context.view_layer.objects.active=obj
        bpy.ops.object.select_all(action='DESELECT'); obj.select_set(True)
        bpy.ops.object.convert(target='MESH')
        return finish(bpy.context.object,material)
    lettering('EC-240',(-.450,-.292,.122),.029,11)
    lettering('EVAP',(-.450,-.332,.122),.018,5)
    for part,mat in (('led_off',4),('led_ready',9),('led_warning',10)):
        cylinder('Controller status lamp '+part,(-.450,-.246,.212),(-.46,-.246,.212),.012,mat,part,10)
    for z in (-.40,.40):
        for x in (-.33,.33):
            cylinder('Reservoir access fastener',(x,-.30,z),(x,-.30,z+math.copysign(.008,z)),.009,2,n=6)

    # Animation in the editable source; exported ELN .txt uses the same pivot.
    rotor_root=bpy.data.objects.new('Fan axis - animate X',None)
    scene.collection.objects.link(rotor_root)
    rotor_root.location=mc(pivot)
    bpy.context.view_layer.update()
    for obj in objects:
        if obj['eln_part']=='rotor':
            matrix=obj.matrix_world.copy(); obj.parent=rotor_root; obj.matrix_world=matrix
    rotor_root.rotation_euler.x=0
    rotor_root.keyframe_insert('rotation_euler',frame=1)
    rotor_root.rotation_euler.x=2*math.pi
    rotor_root.keyframe_insert('rotation_euler',frame=61)
    scene.frame_start=1; scene.frame_end=60; scene.render.fps=30; scene.frame_set(1)
    bpy.context.view_layer.update()

    # Export ELN's constrained OBJ dialect. Materials use one shared atlas to minimize binds.
    lines=['# EC-240 evaporative heat sink; generated by Blender '+bpy.app.version_string,'mtllib evaporativecooler.mtl']
    vertex_count=0; uv_count=0; triangles=0; bounds=[]; part_stats={}
    deps=bpy.context.evaluated_depsgraph_get()
    for part in sorted({o['eln_part'] for o in objects}):
        lines.append('o '+part)
        part_tri=0
        for obj in [o for o in objects if o['eln_part']==part]:
            evaluated=obj.evaluated_get(deps)
            data=evaluated.to_mesh()
            data.calc_loop_triangles()
            offset=vertex_count
            for vertex in data.vertices:
                v=evaluated.matrix_world@vertex.co
                xyz=(v.x,v.z,-v.y)
                bounds.append(xyz)
                lines.append('v %.6f %.6f %.6f'%xyz)
            vertex_count+=len(data.vertices)
            uv_offset=uv_count
            for uv in data.uv_layers.active.data:
                lines.append('vt %.6f %.6f'%(uv.uv.x,uv.uv.y))
            uv_count+=len(data.uv_layers.active.data)
            lines.append('usemtl atlas')
            for triangle in data.loop_triangles:
                indices=[f'{offset+vi+1}/{uv_offset+li+1}' for vi,li in zip(triangle.vertices,triangle.loops)]
                lines.append('f '+' '.join(indices)); triangles+=1; part_tri+=1
            evaluated.to_mesh_clear()
        part_stats[part]=part_tri
    (out/'evaporativecooler.obj').write_text('\n'.join(lines)+'\n')
    (out/'evaporativecooler.mtl').write_text('newmtl atlas\nKd 1 1 1\nd 1\nillum 1\nmap_Kd atlas.png\n')
    (out/'evaporativecooler.txt').write_text('o rotor\nf originX -0.326\nf originY 0.124\nf originZ 0.0\n')
    mins=[min(v[i] for v in bounds) for i in range(3)]
    maxs=[max(v[i] for v in bounds) for i in range(3)]
    assert all(-.505<=x<=.505 for x in mins+maxs),(mins,maxs)
    assert triangles<16000,triangles

    # Portable glTF interchange and editable Blender source, not bundled into the game JAR.
    bpy.ops.object.select_all(action='DESELECT')
    for obj in objects:
        if obj['eln_part'] not in ('pad_dry','led_off','led_warning'): obj.select_set(True)
    rotor_root.select_set(True)
    bpy.ops.export_scene.gltf(filepath=str(source/'evaporativecooler.glb'),use_selection=True,export_format='GLB',export_animations=True)

    # Separate studio collection; none of this is part of the Minecraft export.
    bpy.ops.mesh.primitive_plane_add(size=200,location=(0,0,-.501))
    floor=bpy.context.object; floor.name='STUDIO - ground (not exported)'
    m=bpy.data.materials.new('Studio slate'); m.diffuse_color=(.055,.07,.09,1); floor.data.materials.append(m)
    def area(name,position,power,size):
        data=bpy.data.lights.new(name,'AREA'); data.energy=power; data.shape='DISK'; data.size=size
        obj=bpy.data.objects.new(name,data); scene.collection.objects.link(obj); obj.location=position
        obj.rotation_euler=(Vector((0,0,.0))-obj.location).to_track_quat('-Z','Y').to_euler()
    area('STUDIO key',(-3,-4,5),500,4)
    area('STUDIO rim',(3,1,3),650,3)
    area('STUDIO fill',(-1,3,2),220,3)
    camera_data=bpy.data.cameras.new('Presentation camera'); camera=bpy.data.objects.new('Presentation camera',camera_data)
    scene.collection.objects.link(camera); scene.camera=camera
    camera_data.type='ORTHO'; camera_data.ortho_scale=1.65
    def view(position):
        camera.location=position
        camera.rotation_euler=(Vector((0,0,.015))-camera.location).to_track_quat('-Z','Y').to_euler()
    view((-3.2,-4,2.5))
    bpy.ops.wm.save_as_mainfile(filepath=str(source/'evaporativecooler.blend'))
    if render:
        scene.render.filepath=str(renders/'evaporative-cooler-front.png'); bpy.ops.render.render(write_still=True)
        view((3.2,-4,2.4))
        scene.render.filepath=str(renders/'evaporative-cooler-rear.png'); bpy.ops.render.render(write_still=True)
        # Actual model render used as the game's inventory sprite, with transparent surroundings.
        view((-3.2,-4,2.5)); floor.hide_render=True; scene.render.film_transparent=True
        scene.render.resolution_x=128; scene.render.resolution_y=128; camera_data.ortho_scale=1.55
        icon=root/'src/main/resources/assets/eln/textures/blocks/evaporativecooler.png'
        icon.parent.mkdir(parents=True,exist_ok=True)
        scene.render.filepath=str(icon); bpy.ops.render.render(write_still=True)
    manifest={'blender':bpy.app.version_string,'triangles':triangles,'vertices':vertex_count,'parts':part_stats,
              'bounds_min':mins,'bounds_max':maxs,'rotor_pivot_mc':list(pivot),
              'generator_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              'obj_sha256':hashlib.sha256((out/'evaporativecooler.obj').read_bytes()).hexdigest(),
              'atlas_sha256':hashlib.sha256((out/'atlas.png').read_bytes()).hexdigest()}
    (source/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps(manifest,indent=2))

if __name__=='__main__':
    args=arguments(); build(args.root.resolve(),(args.renders or args.root/'build/evaporative-renders').resolve(),not args.no_render)
