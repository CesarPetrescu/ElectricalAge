"""Run isolated companion worlds; refuses to overwrite existing worlds or remove user data."""
import argparse
import os
import subprocess
import sys
from pathlib import Path
from install_companions import ROOT, install


def run(profile):
    game = ROOT / "run" / f"compat-{profile}"
    client = ROOT / "run" / f"compat-{profile}-client"
    if (game / "world").exists():
        raise ValueError(f"Previous world exists at {game / 'world'}; archive it before another fresh run")
    install(profile, [game / "mods", client / "mods"])
    (game / "eula.txt").write_text("eula=true\n")
    (game / "server.properties").write_text('online-mode=false\nmax-tick-time=-1\nlevel-type=minecraft\\:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:stone","height":63}],"biome":"minecraft:plains"}\n')
    (client / "options.txt").write_text("onboardAccessibility:false\nnarrator:0\n")
    logs = ROOT / "build/companion-artifacts" / profile
    logs.mkdir(parents=True, exist_ok=True)
    args = [str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")), "--no-daemon", "--console=plain", f"-PcompanionProfile={profile}"]
    if profile == "opencomputers":
        args.append("-PwithoutCc")
    if profile == "combined":
        args.append("-PwithCreate")
    for phase in ("place", "restart"):
        print(f"Launching {profile}/{phase}", flush=True)
        with (logs / f"{phase}.log").open("w", encoding="utf-8") as log:
            result = subprocess.run([*args, "runServer", f"-PsmokeTest=companions-{profile}-{phase}"], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, timeout=1500)
        if result.returncode:
            print((logs / f"{phase}.log").read_text(encoding="utf-8", errors="replace")[-16000:])
            raise SystemExit(result.returncode)
    subprocess.run([os.sys.executable, str(ROOT / "script/check_companions.py"), profile], cwd=ROOT, check=True)


if __name__ == "__main__":
    sys.stdout.reconfigure(errors="replace")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("profile", choices=["fluids", "opencomputers", "combined"])
    run(parser.parse_args().profile)
