"""Fail-closed evidence gate and offline HTML index for genuine client framebuffers."""
from __future__ import annotations
import hashlib
import html
import json
from pathlib import Path
from PIL import Image, ImageStat
from native_campaign_plan import expected, gallery_id, SUITES


def read(path):
    return json.loads(Path(path).read_text())


def validate_phase(directory: Path, suite: str, nonce: str, sha: str, restart: bool, retained_count: int = 0):
    report = read(directory / 'report.json')
    for key, value in {'schema':1, 'suite':suite,'runId':nonce,'jarSha256':sha,'restart':restart,'complete':True}.items():
        if type(report.get(key)) is not type(value) or report.get(key) != value:
            raise ValueError(f'{directory}: wrong or missing {key}: {report.get(key)} != {value}')
    runtime = report['runtime']
    if runtime.get('jarSha256') != sha or runtime.get('runId') != nonce or runtime.get('pid',0)<=0:
        raise ValueError('Missing or stale actual-process identity')
    wanted = [f'restart-retained-{i}' for i in range(retained_count)] if restart else expected(suite)
    if not wanted or set(report['expected']) != set(wanted) or len(report['expected']) != len(wanted):
        raise ValueError(f'Incorrect functional plan: expected {wanted}, got {report["expected"]}')
    if restart:
        gallery = []
    else:
        coverage = read(directory / 'coverage.json')
        if coverage['jarSha256'] != sha or coverage['runId'] != nonce:
            raise ValueError('Stale registry coverage')
        all_entries = sorted(coverage['galleryAll'], key=gallery_id)
        if not all_entries or len({gallery_id(e) for e in all_entries}) != len(all_entries):
            raise ValueError('Empty or duplicate registry gallery')
        shard = SUITES.index(suite)
        gallery = [gallery_id(e) for i,e in enumerate(all_entries) if i%len(SUITES)==shard]
        if not gallery or set(coverage['assignedGallery']) != set(gallery) or len(coverage['assignedGallery']) != len(gallery):
            raise ValueError('Incorrect gallery partition')
        registered = {(r['id'],r['descriptor']) for r in coverage['registry']}
        placed = {(r['id'],r['descriptor']) for r in all_entries}
        if not registered or not registered.issubset(placed):
            raise ValueError(f'Registered components missing from fixture: {registered-placed}')
    if set(report['galleryExpected']) != set(gallery) or len(report['galleryExpected']) != len(gallery):
        raise ValueError('Incorrect declared visual coverage')
    results = report['results']
    ids = [r['id'] for r in results]
    if len(ids)!=len(set(ids)) or set(ids)!=set(wanted+gallery):
        raise ValueError(f'Missing/extra/duplicate results; missing={set(wanted+gallery)-set(ids)}, extra={set(ids)-set(wanted+gallery)}')
    captures = []
    files = set()
    for row in results:
        if row.get('status')!='passed' or not row.get('observation'):
            raise ValueError(f'Failed/empty assertion {row}')
        if row['kind'] != ('gallery' if row['id'] in gallery else 'functional'):
            raise ValueError(f'Mislabelled coverage type {row["id"]}')
        relative=Path(row['screenshot']); path=(directory/relative).resolve()
        if relative.is_absolute() or not path.is_relative_to(directory.resolve()) or path.suffix.lower()!='.png':
            raise ValueError('Unsafe screenshot path')
        if path in files: raise ValueError('One screenshot reused for different cases')
        files.add(path)
        if path.stat().st_size<=1024:
            raise ValueError(f'Empty screenshot: {path}')
        with Image.open(path) as im:
            im.load()
            if im.width<640 or im.height<400 or max(ImageStat.Stat(im.convert('RGB')).stddev)<1:
                raise ValueError(f'Blank or undersized framebuffer {path}')
            size=list(im.size)
        captures.append({'id':row['id'],'file':str(relative),'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'size':size})
    return {'functional':len(wanted),'gallery':len(gallery),'pid':runtime['pid'],'captures':captures,'runtime':runtime}


def build_index(root: Path):
    root.mkdir(parents=True,exist_ok=True)
    reports=[]
    for phase in ('first','restart'):
        path=root/phase/'report.json'
        if path.exists(): reports.append((phase,read(path)))
    cards=[]
    for phase, report in reports:
        for row in report.get('results',[]):
            image=f'{phase}/{row["screenshot"]}' if row.get('screenshot') else ''
            title=html.escape(row.get('title',row['id']))
            detail=html.escape(json.dumps(row.get('observation',row.get('detail',{})),indent=2))
            text=html.escape(' '.join(row.get('components',[])))
            kind=row.get('kind','runtime');status=row.get('status','unknown')
            cards.append(f'<article data-kind="{html.escape(kind)}" data-search="{html.escape(row["id"]+" "+title+" "+text).lower()}"><header><span>{phase} · {kind} · {status}</span><h2>{title}</h2><code>{html.escape(row["id"])}</code></header><a href="{html.escape(image)}"><img loading="lazy" src="{html.escape(image)}" alt="Actual Minecraft framebuffer: {title}"></a><p>{text}</p><details><summary>Measured assertions</summary><pre>{detail}</pre></details></article>')
    metadata=read(root/'runtime.json') if (root/'runtime.json').exists() else {'status':'Incomplete run'}
    note=html.escape(json.dumps(metadata,indent=2))
    content='''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>ELN native client evidence</title><style>
body{margin:0;background:#101820;color:#e6edf3;font:16px system-ui,sans-serif}main{max-width:1500px;margin:auto;padding:32px}h1{font-size:32px}p{line-height:1.55}nav{display:flex;gap:12px;position:sticky;top:0;background:#101820;padding:16px 0}input,select{padding:12px;font:inherit;background:#243442;color:white;border:1px solid #536577;border-radius:5px}input{flex:1}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(360px,1fr));gap:20px}article{background:#1b2934;border:1px solid #354552;border-radius:8px;overflow:hidden}article header,article p,article details{padding:16px}h2{font-size:19px;margin:8px 0}article img{display:block;width:100%;height:auto}span{font-size:12px;text-transform:uppercase;color:#b5cedb}code{overflow-wrap:anywhere}pre{white-space:pre-wrap;overflow-wrap:anywhere;font-size:12px}a{color:#91cfff}details{background:#17232d}article[hidden]{display:none}</style><main><h1>ElectricalAge · Native client evidence</h1><p>These are unmodified screenshots from the real Minecraft framebuffer. <b>Functional</b> cases include server assertions and client observations. <b>Gallery</b> captures prove registered component presence and expose its appearance for inspection; they do not prove every component's physics or visual quality.</p><p>First and restart are separate Java processes using the same saved synthetic world. Renderer, architecture and JAR identity are recorded below. The hosted M1 uses the actual Apple software OpenGL renderer when its VM has no GPU. That is not GPU acceleration. The small opt-in GLFW change and host graphics probe are included in the artifacts.</p>'''
    content+=f'<details><summary>Run identity and completion status</summary><pre>{note}</pre></details><nav><input id="search" placeholder="Filter by component, scenario or ID"><select id="kind"><option value="">All evidence</option><option>functional</option><option>gallery</option><option>runtime</option></select></nav><div class="grid">'+''.join(cards)+'</div></main>'
    content+='''<script>function filter(){const q=document.querySelector('#search').value.toLowerCase(),k=document.querySelector('#kind').value;for(const e of document.querySelectorAll('article'))e.hidden=!(e.dataset.search.includes(q)&&(!k||e.dataset.kind===k));}document.querySelector('#search').oninput=filter;document.querySelector('#kind').onchange=filter;</script></html>'''
    (root/'index.html').write_text(content)
