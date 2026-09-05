#!/usr/bin/env python3
"""Reproducible seventh pass: brush tooltips and loadable OBJ/MTL assets."""
from pathlib import Path
import json, re, shutil, sys
root = Path(sys.argv[1]).resolve()
b = root / 'src/main/java/mods/eln'
p = b / 'item/BrushDescriptor.kt'
s = p.read_text()
s = s.replace('import net.minecraft.client.Minecraft\n', '')
s = s.replace('        val creative = Minecraft.getMinecraft().player.capabilities.isCreativeMode\n', '', 1)
s = s.replace('return if (!creative && color == 15 && life == 0)', 'return if (color == 15 && life == 0)')
s = s.replace('val creative = Minecraft.getMinecraft().player.capabilities.isCreativeMode', 'val creative = entityPlayer?.capabilities?.isCreativeMode == true', 1)
s = s.replace('name.lowercase()', 'name.lowercase(java.util.Locale.ROOT)')
assert 'Minecraft.getMinecraft()' not in s[:s.index('// TODO')]
p.write_text(s)
p = root / 'src/test/java/mods/eln/generic/CallbackRegressionTest.java'
s = p.read_text(); ix = s.rfind('}')
s = s[:ix] + '''    @Test public void brushTooltipsDoNotRequireMinecraftOrAPlayer() {
        mods.eln.item.BrushDescriptor descriptor = new mods.eln.item.BrushDescriptor("White Brush");
        ItemStack stack = new ItemStack(net.minecraft.init.Items.STICK, 1, 15);
        java.util.List<Object> lines = new java.util.ArrayList<>();
        assertDoesNotThrow(() -> descriptor.addInformation(stack, null, lines, false));
        assertFalse(lines.isEmpty());
        descriptor.setLife(stack, 0);
        assertEquals("Empty White Brush", descriptor.getName(stack));
    }
''' + s[ix:]; p.write_text(s)
assets = root / 'src/main/resources/assets/eln'
models = assets / 'model'
repairs = {'triangulated_quads': 0, 'loose_edges': 0, 'degenerate_quads': 0, 'models_changed': [], 'material_texture_fixes': []}
for p in sorted(models.rglob('*.obj')):
    vertices = []; out = []; changed = False
    for line in p.read_text().splitlines():
        w = line.split()
        if not w: out.append(line); continue
        if w[0] == 'v': vertices.append(tuple(map(float,w[1:4])))
        if w[0] != 'f' or len(w) == 4: out.append(line); continue
        if len(w) == 3:
            refs = [x.split('/')[0] for x in w[1:]]
            out.append('l '+' '.join(refs)); repairs['loose_edges'] += 1; changed = True; continue
        assert len(w) == 5, (p, line)
        refs = w[1:]; points = [vertices[int(t.split('/')[0])-1] for t in refs]
        normal = [sum((points[i][(k+1)%3]-points[(i+1)%4][(k+1)%3])*(points[i][(k+2)%3]+points[(i+1)%4][(k+2)%3]) for i in range(4)) for k in range(3)]
        if max(map(abs,normal)) < 1e-14:
            out.append('# Ignored zero-area exported quad: '+line); repairs['degenerate_quads'] += 1
        else:
            axis = max(range(3),key=lambda k:abs(normal[k])); dims=[k for k in range(3) if k != axis]
            projected=[(v[dims[0]],v[dims[1]]) for v in points]
            crosses=[]
            for i in range(4):
                a,c,d=projected[i],projected[(i+1)%4],projected[(i+2)%4]
                crosses.append((c[0]-a[0])*(d[1]-c[1])-(c[1]-a[1])*(d[0]-c[0]))
            assert all(v >= -1e-12 for v in crosses) or all(v <= 1e-12 for v in crosses), ('concave quad',p,line)
            out.extend(['f '+' '.join([refs[0],refs[1],refs[2]]), 'f '+' '.join([refs[0],refs[2],refs[3]])])
            repairs['triangulated_quads'] += 1
        changed=True
    if changed:
        p.write_text('\n'.join(out)+'\n'); repairs['models_changed'].append(str(p.relative_to(models)))
assert (repairs['triangulated_quads'],repairs['loose_edges'],repairs['degenerate_quads']) == (90,68,1), repairs
shutil.copyfile(assets/'textures/items/incandescentcarbonlamp.png',models/'sconcelamp/incandescentcarbonlamp.png')
shutil.copyfile(models/'batterychargera/batterychargera.png',models/'batterychargerb/batterychargera.png')
for rel in ['sconcelamp/sconcelamp.mtl','batterychargerb/batterychargerb.mtl','batterybig/batterybighv.mtl','distributionboard/distributionboard.mtl']:
    p=models/rel; out=[]
    for line in p.read_text().splitlines():
        w=line.split(maxsplit=1)
        if w and w[0]=='map_Kd':
            old=w[1]; new=old.replace('\\','/').rsplit('/',1)[-1]
            if rel.startswith('sconcelamp/') and new=='incandescentlampcarbon.png': new='incandescentcarbonlamp.png'
            if rel.startswith('distributionboard/') and new=='reflection.png': new='glass.png'
            assert (p.parent/new).is_file(),(p,new)
            if new!=old: repairs['material_texture_fixes'].append({'material':rel,'old':old,'new':new})
            line='map_Kd '+new
        out.append(line)
    p.write_text('\n'.join(out)+'\n')
assert len(repairs['material_texture_fixes'])==5
out=root/'audit-results';out.mkdir(exist_ok=True)
(out/'asset-repairs.json').write_text(json.dumps(repairs,indent=2)+'\n')
print(json.dumps(repairs,indent=2))
# Keep a separate packaged-runtime probe; the validation fixture is a dev-only jar.
p = root / 'tools/smoke_test.py'
s = p.read_text()
s = s.replace("    run_dir = root / 'run'", "    run_dir = root / 'run' / 'obfuscated' if args.obfuscated else root / 'run'\n    if args.obfuscated and args.validation:\n        raise ValueError('The dev validation fixture is not an obfuscated mod')")
s = s.replace("    before_crashes = set(run_dir.rglob('crash-*.txt'))", "    if args.obfuscated:\n        command[1] = 'runObfServer' if args.kind == 'server' else 'runObfClient'\n    before_crashes = set(run_dir.rglob('crash-*.txt'))")
s = s.replace("'validation_fixture_enabled': args.validation,", "'validation_fixture_enabled': args.validation, 'obfuscated_runtime': args.obfuscated,")
s = s.replace("    parser.add_argument('--validation', action='store_true')", "    parser.add_argument('--validation', action='store_true')\n    parser.add_argument('--obfuscated', action='store_true')")
p.write_text(s)
