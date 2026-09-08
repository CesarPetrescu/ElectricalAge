#!/usr/bin/env python3
"""Test the packaged mod's natural ore generation. Minecraft runs only on GitHub Linux."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import urllib.request

from run_multiplayer import MODS, download, run_installer

ORES = ("copper", "lead", "tungsten", "cinnabar")
PROFILES = {"default": 20260908, "default-alt": 8675309, "disabled": 20260908, "mixed": 8675309}


def enabled(profile, ore):
    return profile in ("default", "default-alt") or (profile == "mixed" and ore in ("lead", "cinnabar"))


def expected_checks(phase):
    keys = {("runtime", "packaged-dedicated-seed-profile"), ("registry", "all-registered-ores-covered")}
    for ore in ORES:
        keys.update((f"eln:{ore}_ore", check) for check in ("configuration", "biome-attachment"))
    areas = ["overworld", "nether", "end"] + (["fresh-after-restart"] if phase == "restart" else [])
    for area in areas:
        keys.add((area, "normal-terrain-scanned"))
        keys.update((f"eln:{ore}_ore", f"{area}/count-and-height") for ore in ORES)
        if phase == "restart" and area != "fresh-after-restart":
            keys.add((area, "persisted-ore-positions"))
    return keys


def validate(output, profile, sha):
    pids = []
    for phase in ("generate", "restart"):
        report = json.loads((output / f"ore-{phase}.json").read_text())
        assert report["suite"] == f"ore-{phase}" and report["complete"] is True and report["failures"] == 0
        results = report["results"]
        keys = [(r["id"], r["check"]) for r in results]
        assert len(keys) == len(set(keys)) and set(keys) == expected_checks(phase), (phase, "missing/duplicate/unexpected checks")
        assert all(r["status"] == "passed" for r in results), phase
        runtime = json.loads((output / f"runtime-{phase}.json").read_text())
        assert runtime["profile"] == profile and runtime["seed"] == PROFILES[profile]
        assert runtime["jarSha256"] == sha and runtime["production"] is True and runtime["dedicated"] is True
        assert isinstance(runtime["pid"], int) and runtime["pid"] > 0
        pids.append(runtime["pid"])
        census = json.loads((output / f"census-{phase}.json").read_text())
        areas = {"overworld", "nether", "end"} | ({"fresh-after-restart"} if phase == "restart" else set())
        assert set(census) == areas
        for area, data in census.items():
            assert data["chunks"] == (4 if area in ("nether", "end") else 16)
            assert data["terrain"] > 1000 and data["biomes"]
            assert set(data["ores"]) == {f"eln:{ore}_ore" for ore in ORES}
            for ore in ORES:
                sample = data["ores"][f"eln:{ore}_ore"]
                should_generate = area not in ("nether", "end") and enabled(profile, ore)
                assert (sample["count"] > 0) if should_generate else (sample["count"] == 0), (area, ore, sample)
                assert len(sample["positionsSha256"]) == 64
        if phase == "restart":
            before = json.loads((output / "census-generate.json").read_text())
            for area in before:
                assert census[area]["ores"] == before[area]["ores"], (area, "saved ore positions changed")
    assert pids[0] != pids[1], "No independent server restart"


def launch(java, neo, folder, output, flags, phase):
    with (output / f"server-{phase}.log").open("w") as log:
        process = subprocess.Popen([java, "-Xms256m", "-Xmx3G", "-XX:ActiveProcessorCount=2", *flags,
            f"-Deln.oreWorldgenTest={phase}", f"@libraries/net/neoforged/neoforge/{neo}/unix_args.txt", "nogui"],
            cwd=folder, stdout=log, stderr=subprocess.STDOUT)
        deadline = time.monotonic() + 900
        stopping = False
        try:
            while process.poll() is None:
                report_path = output / f"ore-{phase}.json"
                if not stopping and report_path.exists():
                    try:
                        if json.loads(report_path.read_text())["complete"]:
                            stopping = True
                            deadline = min(deadline, time.monotonic() + 60)
                    except json.JSONDecodeError:
                        pass  # The probe can be halfway through writing its progress report.
                if time.monotonic() >= deadline:
                    with (output / f"threads-{phase}.txt").open("w") as dump:
                        subprocess.run([str(Path(java).with_name("jcmd")), str(process.pid), "Thread.print"],
                            stdout=dump, stderr=subprocess.STDOUT, timeout=15, check=False)
                    raise TimeoutError(f"Server {phase} failed to complete or shut down")
                time.sleep(1)
            if process.returncode != 0:
                raise RuntimeError(f"Server {phase} exited {process.returncode}")
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=15)
            for directory in ("logs", "crash-reports"):
                if (folder / directory).exists():
                    shutil.copytree(folder / directory, output / f"{phase}-{directory}", dirs_exist_ok=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("profile", choices=PROFILES)
    parser.add_argument("--jar", type=Path, required=True)
    args = parser.parse_args()
    if os.environ.get("GITHUB_ACTIONS") != "true" or os.name != "posix":
        raise SystemExit("Minecraft ore tests run only on GitHub Linux runners, never the user's PC")
    jar = args.jar.resolve()
    sha = hashlib.sha256(jar.read_bytes()).hexdigest()
    output = Path("build/ore-worldgen-artifacts").resolve()
    output.mkdir(parents=True, exist_ok=False)
    root = Path(tempfile.mkdtemp(prefix="eln-ore-", dir=os.environ["RUNNER_TEMP"]))
    server = root / "server"
    server.mkdir()
    runtime = Path(os.environ["RUNNER_TEMP"]) / "eln-ore-runtime"
    runtime.mkdir(exist_ok=True)
    java = str(Path(os.environ["JAVA_HOME"]) / "bin/java")
    properties = dict(line.split("=", 1) for line in Path("gradle.properties").read_text().splitlines() if "=" in line and not line.startswith("#"))
    neo = properties["neoVersion"].strip()
    try:
        url = f"https://maven.neoforged.net/releases/net/neoforged/neoforge/{neo}/neoforge-{neo}-installer.jar"
        with urllib.request.urlopen(url + ".sha1", timeout=60) as response:
            installer_sha = response.read().decode().strip().split()[0]
        installer = download(url, runtime / "installer.jar", installer_sha)
        with (output / "installer.log").open("w") as log:
            run_installer(java, installer, "--install-server", runtime, log)
        (server / "libraries").symlink_to(runtime / "libraries", target_is_directory=True)
        (server / "mods").mkdir()
        shutil.copy2(jar, server / "mods" / jar.name)
        url, kff_sha = MODS["kff"]
        shutil.copy2(download(url, runtime / "kotlinforforge.jar", kff_sha), server / "mods/kotlinforforge.jar")
        (server / "eula.txt").write_text("eula=true\n")
        (server / "server.properties").write_text(f"server-ip=127.0.0.1\nserver-port=25565\nonline-mode=false\nlevel-type=minecraft:normal\nlevel-seed={PROFILES[args.profile]}\nview-distance=2\nsimulation-distance=2\nmax-tick-time=120000\nspawn-protection=0\n")
        config = server / "config/eln/eln.json"
        config.parent.mkdir(parents=True)
        settings = {"analytics": {"enabled": False}, "updates": {"versionCheck": {"enabled": False}}}
        if args.profile not in ("default", "default-alt"):
            settings["worldgen"] = {"ores": {ore: {"enabled": enabled(args.profile, ore)} for ore in ORES}}
        config.write_text(json.dumps(settings, indent=2))
        flags = [f"-Deln.oreWorldgenOutput={output}", f"-Deln.oreWorldgenProfile={args.profile}",
            f"-Deln.oreWorldgenSeed={PROFILES[args.profile]}", f"-Deln.oreWorldgenJarSha256={sha}"]
        launch(java, neo, server, output, flags, "generate")
        assert (server / "world/level.dat").is_file(), "No saved world to restart"
        launch(java, neo, server, output, flags, "restart")
        validate(output, args.profile, sha)
        print(f"PASS {args.profile}: {sum(len(expected_checks(p)) for p in ('generate', 'restart'))} named checks; JAR {sha}")
        if os.environ.get("GITHUB_STEP_SUMMARY"):
            with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
                summary.write(f"## Natural ore generation: {args.profile}\n\nSeed `{PROFILES[args.profile]}`; packaged JAR `{sha}`. Both independent server processes passed.\n\n")
                for phase in ("generate", "restart"):
                    census = json.loads((output / f"census-{phase}.json").read_text())
                    summary.write(f"### {phase}\n\n| Region | Copper | Lead | Tungsten | Cinnabar |\n|---|---:|---:|---:|---:|\n")
                    for area, row in census.items():
                        summary.write(f"| {area} | " + " | ".join(str(row["ores"][f"eln:{ore}_ore"]["count"]) for ore in ORES) + " |\n")
                    summary.write("\n")
    except Exception:
        if (server / "world").exists():
            shutil.make_archive(str(output / "failed-world"), "zip", server, "world")
        raise
    finally:
        if config_path := next(iter(server.glob("config/eln/eln.json")), None):
            shutil.copy2(config_path, output / "effective-config.json")


if __name__ == "__main__":
    main()
