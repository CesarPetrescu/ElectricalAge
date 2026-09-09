#!/usr/bin/env python3
"""Actual packaged Minecraft client, one integrated server and a separate-JVM restart.
No assertion is retried. Installation/network retries never retry gameplay failures.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tempfile
import time
import urllib.request
import uuid
import zipfile
from run_multiplayer import MODS, download, install_client, offline_uuid
from native_campaign_plan import SUITES
from native_campaign_report import build_index, validate_phase
from check_client_assets import inspect as inspect_assets


def main():
    p=argparse.ArgumentParser();p.add_argument('--jar',type=Path,required=True)
    p.add_argument('--suite',choices=(*SUITES,'preflight'),required=True);p.add_argument('--world',type=Path)
    p.add_argument('--output',type=Path,default=Path('build/native-campaign'))
    args=p.parse_args();import minecraft_launcher_lib as launcher
    out=args.output.resolve();out.mkdir(parents=True,exist_ok=False)
    jar=args.jar.resolve();sha=hashlib.sha256(jar.read_bytes()).hexdigest();nonce=uuid.uuid4().hex
    java=str(Path(os.environ['JAVA_HOME'])/'bin/java')
    runtime=Path(os.environ.get('RUNNER_TEMP',tempfile.gettempdir()))/f'eln-native-{platform.system()}-{platform.machine()}'
    runtime.mkdir(parents=True,exist_ok=True)
    game=Path('run/native-campaign').resolve();game.mkdir(parents=True,exist_ok=False)
    (game/'mods').mkdir();shutil.copy2(jar,game/'mods'/jar.name)
    metadata={'schema':1,'system':platform.system(),'arch':platform.machine(),'suite':args.suite,'runId':nonce,'jarSha256':sha,
        'runnerImage':os.environ.get('ImageVersion'),'source':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
        'graphicsBackend':'Apple NSGL: software renderer allowed (not GPU acceleration)' if os.environ.get('ELN_APPLE_SOFTWARE_PREFIX') else 'system OpenGL',
        'status':'running','phases':{}}
    proc=None;started=time.monotonic()
    try:
        url,check=MODS['kff'];shutil.copy2(download(url,runtime/url.rsplit('/',1)[1],check),game/'mods')
        props=dict(line.strip().split('=',1) for line in Path('gradle.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
        props={k.strip():v.strip() for k,v in props.items()};neo,mc=props['neoVersion'],props['minecraftVersion']
        metadata.update(minecraft=mc,neoforge=neo)
        url=f'https://maven.neoforged.net/releases/net/neoforged/neoforge/{neo}/neoforge-{neo}-installer.jar'
        with urllib.request.urlopen(url+'.sha1',timeout=60) as r: check=r.read().decode().split()[0]
        installer=download(url,runtime/f'neoforge-{neo}-installer.jar',check)
        with (out/'install.log').open('w') as log: version=install_client(launcher,mc,neo,runtime,java,installer,log)
        if args.suite!='preflight':
            if not args.world: raise ValueError('--world is required')
            dest=game/'saves'/'campaign';dest.mkdir(parents=True)
            with zipfile.ZipFile(args.world) as z:
                for info in z.infolist():
                    if not (dest/info.filename).resolve().is_relative_to(dest): raise ValueError('Unsafe world archive')
                z.extractall(dest)
            if not (dest/'level.dat').is_file() or not (dest/'eln-contracts.json').is_file(): raise ValueError('Missing authoritative world/registry fixture')
            metadata['initialWorldSha256']=hashlib.sha256(args.world.read_bytes()).hexdigest()
        (game/'options.txt').write_text('onboardAccessibility:false\nnarrator:0\nrenderDistance:4\nsimulationDistance:4\nmaxFps:60\nenableVsync:false\npauseOnLostFocus:false\nguiScale:2\nsoundCategory_master:0.0\n')
        with (out/'java.txt').open('w') as log: subprocess.run([java,'-XshowSettings:properties','-version'],stdout=log,stderr=subprocess.STDOUT,check=True)
        retained=0
        for phase in (('preflight',) if args.suite=='preflight' else ('first','restart')):
            directory=out/phase;directory.mkdir()
            flags=['-Xms256m','-Xmx3G',f'-Deln.nativeGraphicsBackend={metadata["graphicsBackend"]}']
            if platform.system()=='Darwin': flags+=['-XstartOnFirstThread']
            if os.environ.get('ELN_APPLE_SOFTWARE_PREFIX'):
                prefix=Path(os.environ['ELN_APPLE_SOFTWARE_PREFIX']);flags+=[f'-Dorg.lwjgl.glfw.libname={prefix}/lib/libglfw.3.dylib']
            if phase=='preflight':
                control=directory/'control';control.mkdir()
                flags+=['-Deln.multiplayerTest=alpha',f'-Deln.multiplayerDirectory={control}',f'-Deln.multiplayerJarSha256={sha}','-Deln.multiplayerProfile=standalone']
            else:
                flags += [f'-Deln.nativeCampaign={args.suite}',f'-Deln.nativeCampaignDirectory={directory}',f'-Deln.nativeCampaignJarSha256={sha}',f'-Deln.nativeCampaignRunId={nonce}',f'-Deln.nativeCampaignRestart={str(phase=="restart").lower()}']
            command=launcher.command.get_minecraft_command(version,runtime,{'username':'ElnNativeQA','uuid':offline_uuid('ElnNativeQA'),'token':'offline-ci-local-only','executablePath':java,'jvmArguments':flags,'gameDirectory':str(game),'customResolution':True,'resolutionWidth':'1280','resolutionHeight':'800'})
            phase_start=time.monotonic()
            with (directory/'client.log').open('w') as log:
                proc=subprocess.Popen(command,cwd=game,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
                if phase=='preflight':
                    from PIL import Image
                    for action in ('boot','stop'):
                        command_file=control/'alpha-command.json';temp=command_file.with_suffix('.tmp')
                        temp.write_text(json.dumps({'role':'alpha','id':action,'action':action}));temp.replace(command_file)
                        result_path=control/f'alpha-{action}.json';deadline=time.monotonic()+240
                        while not result_path.exists():
                            if proc.poll() is not None: raise RuntimeError(f'Client exited before {action}: {proc.returncode}')
                            if time.monotonic()>deadline: raise TimeoutError(f'Missing native {action} report')
                            time.sleep(.2)
                        result=json.loads(result_path.read_text())
                        if result['status']!='passed' or result['pid']!=proc.pid or 'screenshotError' in result: raise ValueError(result)
                        with Image.open(control/f'alpha-{action}.png') as im: im.verify()
                    code=proc.wait(timeout=60)
                else:
                    try: code=proc.wait(timeout=1600)
                    except subprocess.TimeoutExpired:
                        with (directory/'threads.txt').open('w') as dump: subprocess.run([str(Path(java).with_name('jcmd')),str(proc.pid),'Thread.print'],stdout=dump,stderr=subprocess.STDOUT,timeout=20,check=False)
                        raise
                if code!=0: raise RuntimeError(f'{phase} client exited {code}')
            errors, known = inspect_assets((directory/'client.log').read_text(errors='replace'))
            (directory/'assets.json').write_text(json.dumps({'newAssetErrors':errors,'knownUnfixedWarnings':known},indent=2))
            if errors: raise ValueError(f'New real client asset errors: {errors}')
            if phase=='preflight': metadata['phases'][phase]={'seconds':time.monotonic()-phase_start,'pid':proc.pid,'status':'passed'}
            else:
                summary=validate_phase(directory,args.suite,nonce,sha,phase=='restart',retained)
                if summary['pid']!=proc.pid: raise ValueError('Report came from another process')
                summary['seconds']=time.monotonic()-phase_start;metadata['phases'][phase]=summary
                if phase=='first':
                    saved=game/'saves'/'campaign'/'native-campaign-state.json';shutil.copy2(saved,out/'retained-state.json')
                    retained=len(json.loads(saved.read_text()))
                    if retained<=0: raise ValueError('Empty saved-state verification')
                elif metadata['phases']['first']['pid']==proc.pid: raise ValueError('Restart reused the same JVM')
        metadata['status']='passed'
    except BaseException as e:
        metadata['status']='failed';metadata['error']=str(e);raise
    finally:
        if proc is not None and proc.poll() is None:
            proc.terminate()
            try: proc.wait(timeout=20)
            except subprocess.TimeoutExpired: proc.kill();proc.wait(timeout=20)
        for name in ('logs','crash-reports'):
            if (game/name).exists(): shutil.copytree(game/name,out/name,dirs_exist_ok=True)
        if (game/'saves'/'campaign').exists(): shutil.make_archive(str(out/'reproduction-world'),'zip',game/'saves'/'campaign')
        metadata['totalSeconds']=time.monotonic()-started
        (out/'runtime.json').write_text(json.dumps(metadata,indent=2));build_index(out)
        print(json.dumps({k:v for k,v in metadata.items() if k!='phases'},indent=2),flush=True)

if __name__=='__main__': main()
