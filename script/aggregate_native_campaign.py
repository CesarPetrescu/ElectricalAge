#!/usr/bin/env python3
"""Require all M1 shards and a same-artifact Linux control; index genuine screenshots."""
from __future__ import annotations
import argparse
import csv
import html
import json
from pathlib import Path
import shutil
from PIL import Image, ImageDraw
from native_campaign_plan import SUITES, gallery_id
from native_campaign_report import read, validate_phase


def aggregate(incoming: Path, output: Path, manifest: dict) -> dict:
    output.mkdir(parents=True,exist_ok=False)
    expected=[('m1',s) for s in SUITES]+[('linux','power')]
    summary={'schema':1,'source':manifest['source'],'jarSha256':manifest['jarSha256'],'initialWorldSha256':manifest['worldSha256'],
             'status':'failed','shards':{},'errors':[],'scope':'All registry entries receive presence/render captures; functional coverage is explicitly listed, not inferred from gallery screenshots.'}
    if (manifest.get('seedSmokePassed') is not True or manifest.get('seedProduction') is not True) or manifest.get('seedJarSha256') != manifest['jarSha256']:
        summary['errors'].append('Input world lacks an identical production-JAR seed receipt')
    cards=[];registry=None;all_gallery=None;covered=[];functional={};thumbs=[]
    for platform,suite in expected:
        key=f'{platform}-{suite}';source=incoming/f'native-evidence-{key}';dest=output/key
        try:
            if not source.is_dir(): raise ValueError(f'Missing artifact {source.name}')
            shutil.copytree(source,dest)
            meta=read(dest/'runtime.json')
            if meta.get('status')!='passed': raise ValueError(f'{key} did not complete: {meta.get("error",meta.get("status"))}')
            for k,v in [('source',manifest['source']),('jarSha256',manifest['jarSha256']),('initialWorldSha256',manifest['worldSha256']),('seedProduction',True),('seedJarSha256',manifest['jarSha256'])]:
                if meta.get(k)!=v: raise ValueError(f'{key}: mismatched {k}')
            if meta['suite']!=suite or (platform=='m1' and (meta['system']!='Darwin' or meta['arch']!='arm64')) or (platform=='linux' and meta['system']!='Linux'):
                raise ValueError('Wrong suite/platform/architecture')
            retained=len(read(dest/'retained-state.json'))
            first=validate_phase(dest/'first',suite,meta['runId'],meta['jarSha256'],False)
            restart=validate_phase(dest/'restart',suite,meta['runId'],meta['jarSha256'],True,retained)
            if first['pid']==restart['pid']: raise ValueError('Same JVM used for restart')
            for phase in ('first','restart'):
                assets=read(dest/phase/'assets.json')
                if assets.get('newAssetErrors')!=[]: raise ValueError(f'New asset errors in {phase}')
            coverage=read(dest/'first'/'coverage.json')
            if platform=='m1':
                current=sorted(coverage['registry'],key=lambda x:(x['id'],x['descriptor']))
                entries=sorted(coverage['galleryAll'],key=gallery_id)
                if registry is None: registry=current;all_gallery=entries
                if registry!=current or all_gallery!=entries: raise ValueError('Shards used different registry/world catalogues')
                covered+=coverage['assignedGallery']
                for case in read(dest/'first'/'report.json')['results']:
                    if case['kind']=='functional':
                        for component in case['components']:functional.setdefault(component,[]).append(f'{key}/{case["id"]}')
            summary['shards'][key]={'status':'passed','functional':first['functional'],'gallery':first['gallery'],'restart':restart['functional'],
                'firstPid':first['pid'],'restartPid':restart['pid'],'runtime':first['runtime'],
                'firstPerformance':read(dest/'first'/'performance.json'),'restartPerformance':read(dest/'restart'/'performance.json')}
        except Exception as error:
            summary['errors'].append(f'{key}: {error}');summary['shards'][key]={'status':'failed','detail':str(error)}
        # Retain available failure captures too. These never satisfy the gate.
        if dest.exists():
            for phase in ('first','restart'):
                report=dest/phase/'report.json'
                if not report.exists():continue
                data=read(report)
                for row in data.get('results',[]):
                    rel=row.get('screenshot','');image=dest/phase/rel
                    if not rel or not image.is_file() or not image.resolve().is_relative_to(dest.resolve()):continue
                    before=dest/phase/row.get('beforeScreenshot','')
                    before_ref=str(before.relative_to(output)) if row.get('beforeScreenshot') and before.is_file() else ''
                    before_html=f'<p>Before action</p><a href="{html.escape(before_ref)}"><img loading="lazy" src="{html.escape(before_ref)}" alt="Actual framebuffer before action"></a>' if before_ref else ''
                    ref=str(image.relative_to(output));identifier=f'{key}/{phase}/{row["id"]}'
                    title=row.get('title',row['id']);kind=row.get('kind','runtime');status=row.get('status','unknown')
                    details=json.dumps(row.get('observation',row.get('detail',{})),indent=2)
                    search=identifier+' '+title+' '+' '.join(row.get('components',[]))
                    cards.append(f'<article data-kind="{html.escape(kind)}" data-suite="{key}" data-search="{html.escape(search).lower()}"><header><span>{key} · {phase} · {kind} · {status}</span><h2>{html.escape(title)}</h2><code>{html.escape(row["id"])}</code></header>{before_html}<p>Observed result</p><a href="{html.escape(ref)}"><img loading="lazy" src="{html.escape(ref)}" alt="Actual Minecraft framebuffer"></a><details><summary>Measured observations</summary><pre>{html.escape(details)}</pre></details></article>')
                    if platform=='m1' and phase=='first' and kind=='functional' and len([x for x in thumbs if x[2]==key])<6:thumbs.append((image,title,key))
    if not registry or not all_gallery:
        summary['errors'].append('No complete registry inventory')
    elif len(covered)!=len(set(covered)) or set(covered)!=set(map(gallery_id,all_gallery)):
        summary['errors'].append('M1 gallery is missing or duplicates registered fixture entries')
    if registry:
        rows=[]
        for entry in registry:
            ident=entry['id'];cases=functional.get(ident,[])
            images=[gallery_id(e) for e in (all_gallery or []) if e['id']==ident and e['descriptor']==entry['descriptor']]
            rows.append({**entry,'functionalCaseCount':len(cases),'functionalCases':';'.join(cases),
                'functionalScope':'named cases only' if cases else 'GALLERY ONLY - functional coverage not claimed',
                'm1Gallery':'passed' if images and all(k in covered for k in images) else 'missing'})
        (output/'component-coverage.json').write_text(json.dumps(rows,indent=2))
        with (output/'component-coverage.csv').open('w',newline='') as f:
            writer=csv.DictWriter(f,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)
        summary['coverage']={'registeredDescriptors':len(rows),'functionalDescriptors':sum(bool(r['functionalCaseCount']) for r in rows),
            'galleryOnlyDescriptors':sum(not r['functionalCaseCount'] for r in rows),'galleryEntries':len(all_gallery or [])}
    if all(summary['shards'][k]['status']=='passed' for k in ('m1-power','linux-power')):
        m=summary['shards']['m1-power']['firstPerformance'];l=summary['shards']['linux-power']['firstPerformance']
        summary['powerComparison']={'m1Seconds':m['totalSeconds'],'linuxSeconds':l['totalSeconds'],
            'linuxOverM1WallRatio':l['totalSeconds']/max(m['totalSeconds'],1e-9),
            'note':'Same packaged artifact, seed world, functional plan and frame cap. Includes GUI waits and gallery travel; one runner sample, not a pure CPU/GPU benchmark. Actual GL renderers differ.'}
    if not summary['errors']:summary['status']='passed'
    summary['screenshotsIndexed']=len(cards)
    (output/'summary.json').write_text(json.dumps(summary,indent=2))
    if thumbs:
        # Overview is a labelled montage of actual frames; originals remain unchanged.
        width=320;cell_h=235;cols=4
        board=Image.new('RGB',(cols*width,((len(thumbs)+cols-1)//cols)*cell_h),(24,32,40));draw=ImageDraw.Draw(board)
        for i,(path,title,key) in enumerate(thumbs):
            x=(i%cols)*width;y=(i//cols)*cell_h
            with Image.open(path) as im:
                im=im.convert('RGB');im.thumbnail((width,190));board.paste(im,(x,y))
            draw.text((x+6,y+193),key,fill=(230,237,243));draw.text((x+6,y+211),title[:43],fill=(195,210,221))
        board.save(output/'overview.jpg',quality=88)
    status=html.escape(summary['status'].upper());detail=html.escape(json.dumps(summary,indent=2))
    style='''body{background:#101820;color:#e5edf3;margin:0;font:16px system-ui,sans-serif}main{max-width:1600px;padding:28px;margin:auto}p{line-height:1.5}nav{display:flex;gap:12px;padding:16px 0;position:sticky;top:0;background:#101820;z-index:1}input,select{padding:12px;background:#243442;color:white;font:inherit;border:1px solid #607485}input{flex:1}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(350px,1fr));gap:18px}article{border:1px solid #364a59;background:#1b2934;border-radius:6px;overflow:hidden}header,details{padding:16px}h2{font-size:18px}span{font-size:12px;text-transform:uppercase;color:#c0d7e4}img{display:block;width:100%}pre{white-space:pre-wrap;overflow-wrap:anywhere;font-size:12px}code{overflow-wrap:anywhere}a{color:#8fd0ff}[hidden]{display:none}'''
    menu=''.join(f'<option>{p}-{s}</option>' for p,s in expected)
    page=f'''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>ELN client test evidence</title><style>{style}</style><main><h1>ElectricalAge · M1 client campaign · {status}</h1><p>Actual Minecraft screenshots linked to named assertions, observed values, component IDs and source identity. <b>Gallery-only is not functional coverage.</b> Apple Software Renderer is not GPU acceleration. Both successful and failed captures are retained.</p><p><a href="component-coverage.csv">Component coverage CSV</a> · <a href="component-coverage.json">Coverage JSON</a> · <a href="summary.json">Validation and timing JSON</a> · <a href="overview.jpg">Labelled screenshot overview</a></p><details><summary>Exact revisions, results, limitations and timings</summary><pre>{detail}</pre></details><nav><input id="q" placeholder="Search a component or tested scenario"><select id="kind"><option value="">Every capture</option><option>functional</option><option>gallery</option><option>runtime</option></select><select id="suite"><option value="">All runners and suites</option>{menu}</select></nav><div class="grid">{''.join(cards)}</div></main><script>function update(){{const q=document.querySelector('#q').value.toLowerCase(),k=document.querySelector('#kind').value,s=document.querySelector('#suite').value;for(const e of document.querySelectorAll('article'))e.hidden=!(e.dataset.search.includes(q)&&(!k||e.dataset.kind===k)&&(!s||e.dataset.suite===s));}}for(const id of ['q','kind','suite'])document.getElementById(id).oninput=update;</script></html>'''
    (output/'index.html').write_text(page)
    return summary

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--incoming',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--manifest',type=Path,required=True);a=p.parse_args()
    result=aggregate(a.incoming,a.output,read(a.manifest));print(json.dumps(result,indent=2));raise SystemExit(result['status']!='passed')
