#!/usr/bin/env python3
"""Clean packaged-JAR multiplayer test; Minecraft is launched only on GitHub's Linux runner."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import traceback
import urllib.request
import uuid
import xml.etree.ElementTree as ET

from multiplayer_plan import plan, validate

MODS = {
    "kff": ("https://cdn.modrinth.com/data/ordsPcFz/versions/uhJhCT7X/kotlinforforge-5.12.0-all.jar", "d10d062caf1aad9aec82d7852f2fee1781735c1f"),
    "create": ("https://cdn.modrinth.com/data/LNytGWDc/versions/UjX6dr61/create-1.21.1-6.0.10.jar", "0e97e49837bed766e6f28a4c95b04885d6acc353"),
}


def download(url, path, sha1):
    if path.exists() and hashlib.sha1(path.read_bytes()).hexdigest() == sha1:
        return path
    path.parent.mkdir(parents=True, exist_ok=True)
    for attempt in range(3):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "ElectricalAge-Multiplayer-CI/1.0"})
            with urllib.request.urlopen(request, timeout=90) as response:
                data = response.read()
            if hashlib.sha1(data).hexdigest() != sha1:
                raise ValueError(f"Checksum mismatch: {url}")
            path.write_bytes(data)
            return path
        except Exception:
            if attempt == 2:
                raise
            time.sleep(2)


def offline_uuid(name):
    digest = hashlib.md5(f"OfflinePlayer:{name}".encode()).digest()
    return str(uuid.UUID(bytes=digest, version=3))


def install_client(launcher, mc, neo, runtime, java, installer, log):
    """Use the pinned official installer, without an unrelated live loader-version listing."""
    callback = {"setStatus": lambda text: print(text, flush=True)}
    launcher.install.install_minecraft_version(mc, runtime, callback=callback)
    if not launcher.vanilla_launcher.do_vanilla_launcher_profiles_exists(runtime):
        launcher.vanilla_launcher.create_empty_vanilla_launcher_profiles_file(runtime)
    subprocess.run([java, "-jar", str(installer), "--install-client", str(runtime)],
        cwd=runtime, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=900)
    version = f"neoforge-{neo}"
    launcher.install.install_minecraft_version(version, runtime, callback=callback)
    return version


class Runner:
    def __init__(self, args):
        self.args = args
        self.jar = args.jar.resolve()
        self.sha = hashlib.sha256(self.jar.read_bytes()).hexdigest()
        self.output = Path("build/multiplayer-artifacts").resolve()
        self.output.mkdir(parents=True, exist_ok=False)
        self.control = self.output / "control"
        self.control.mkdir()
        self.root = Path(tempfile.mkdtemp(prefix="eln-multiplayer-", dir=os.environ["RUNNER_TEMP"]))
        self.processes = {}
        self.logs = []
        self.results = []
        self.server_epoch = 0

    def setup(self):
        import minecraft_launcher_lib as launcher
        self.launcher = launcher
        self.java = str(Path(os.environ["JAVA_HOME"]) / "bin/java")
        self.runtime = Path(os.environ["RUNNER_TEMP"]) / "eln-multiplayer-runtime"
        self.runtime.mkdir(exist_ok=True)
        # This is the official installer path, not Gradle's development launch classpath.
        properties = dict(line.split("=", 1) for line in Path("gradle.properties").read_text().splitlines() if "=" in line and not line.startswith("#"))
        properties = {k.strip(): v.strip() for k, v in properties.items()}
        mc, neo = properties["minecraftVersion"], properties["neoVersion"]
        self.neo = neo
        print(f"Installing Minecraft {mc} / NeoForge {neo}", flush=True)
        installer_url = f"https://maven.neoforged.net/releases/net/neoforged/neoforge/{neo}/neoforge-{neo}-installer.jar"
        with urllib.request.urlopen(installer_url + ".sha1", timeout=60) as response:
            sha1 = response.read().decode().strip().split()[0]
        installer = download(installer_url, self.runtime / "installer.jar", sha1)
        with (self.output / "client-install.log").open("w") as log:
            self.version = install_client(launcher, mc, neo, self.runtime, self.java, installer, log)
        server = self.root / "server"
        server.mkdir()
        with (self.output / "server-install.log").open("w") as log:
            subprocess.run([self.java, "-jar", str(installer), "--install-server", str(server)], cwd=server, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=900)
        (server / "eula.txt").write_text("eula=true\n")
        (server / "server.properties").write_text(
            "server-ip=127.0.0.1\nserver-port=25565\nonline-mode=false\nenforce-secure-profile=false\n"
            "max-tick-time=120000\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\n"
            "level-type=minecraft\\:flat\ngenerator-settings={\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},{\"block\":\"minecraft:stone\",\"height\":63}],\"biome\":\"minecraft:plains\"}\n"
            "gamemode=creative\nforce-gamemode=true\ndifficulty=peaceful\nallow-flight=true\n")
        mods = [self.jar]
        for key in (["kff", "create"] if self.args.profile == "create" else ["kff"]):
            url, digest = MODS[key]
            mods.append(download(url, self.runtime / url.rsplit("/", 1)[1], digest))
        for role in ("server", "alpha", "beta"):
            folder = self.root / role
            (folder / "mods").mkdir(parents=True, exist_ok=True)
            for mod in mods:
                shutil.copy2(mod, folder / "mods" / mod.name)
            if role != "server":
                (folder / "options.txt").write_text("onboardAccessibility:false\nnarrator:0\nrenderDistance:3\nsimulationDistance:3\nmaxFps:20\n"
                    "graphicsMode:0\nparticles:2\nmipmapLevels:0\nenableVsync:false\npauseOnLostFocus:false\nguiScale:2\nsoundCategory_master:0.0\n")
        (self.output / "runtime.json").write_text(json.dumps({"minecraft": mc, "neoforge": neo, "profile": self.args.profile,
            "jar": self.jar.name, "sha256": self.sha, "mods": [{"file": p.name, "sha256": hashlib.sha256(p.read_bytes()).hexdigest()} for p in mods]}, indent=2))

    def start(self, role):
        folder = self.root / role
        flags = ["-Xms256m", "-Xmx2G", "-XX:ActiveProcessorCount=2", f"-Deln.multiplayerTest={role}",
            f"-Deln.multiplayerDirectory={self.control}", f"-Deln.multiplayerJarSha256={self.sha}", f"-Deln.multiplayerProfile={self.args.profile}"]
        if role == "server":
            self.server_epoch += 1
            command = [self.java, *flags, f"@libraries/net/neoforged/neoforge/{self.neo}/unix_args.txt", "nogui"]
            log_name = f"server-{self.server_epoch}"
        else:
            name = "ElnAlpha" if role == "alpha" else "ElnBeta"
            command = self.launcher.command.get_minecraft_command(self.version, self.runtime, {
                "username": name, "uuid": offline_uuid(name), "token": "offline-ci-loopback", "executablePath": self.java,
                "jvmArguments": flags, "gameDirectory": str(folder), "customResolution": True,
                "resolutionWidth": "960", "resolutionHeight": "640"})
            log_name = role
        log = (self.output / f"{log_name}.log").open("w")
        self.logs.append(log)
        self.processes[role] = subprocess.Popen(command, cwd=folder, stdout=log, stderr=subprocess.STDOUT,
            env={**os.environ, "LIBGL_ALWAYS_SOFTWARE": "1", "LP_NUM_THREADS": "2", "ALSOFT_DRIVERS": "null"})
        print(f"Started {role} pid={self.processes[role].pid}", flush=True)

    def issue(self, command):
        path = self.control / f"{command['role']}-command.json"
        temporary = path.with_suffix(".tmp")
        temporary.write_text(json.dumps(command))
        temporary.replace(path)

    def run_group(self, group):
        for command in group:
            if command["id"] == "restarted-packaged-server":
                self.start("server")
            self.issue(command)
        deadline = time.monotonic() + (420 if group[0]["action"] == "boot" else 180)
        pending = list(group)
        while pending:
            for command in pending[:]:
                role, name = command["role"], command["id"]
                path = self.control / f"{role}-{name}.json"
                if path.exists():
                    result = json.loads(path.read_text())
                    if result.get("id") != name or result.get("role") != role or result.get("pid") != self.processes[role].pid:
                        raise ValueError(f"Stale or mismatched report: {path}")
                    self.results.append(result)
                    if result["status"] != "passed":
                        raise AssertionError(f"{role}/{name}: {result.get('detail')}")
                    pending.remove(command)
                    print(f"PASS {role}/{name}", flush=True)
                    if command["action"] == "stop":
                        code = self.processes[role].wait(timeout=60)
                        if code != 0:
                            raise RuntimeError(f"{role} shutdown failed: {code}")
                elif self.processes[role].poll() is not None:
                    raise RuntimeError(f"{role} exited before {name}: {self.processes[role].returncode}")
            if time.monotonic() > deadline:
                raise TimeoutError(f"Missing results: {pending}")
            if pending:
                time.sleep(.2)

    def finish(self, error):
        for process in self.processes.values():
            if process.poll() is None:
                process.terminate()
        for process in self.processes.values():
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)
        for log in self.logs:
            log.close()
        for role in ("server", "alpha", "beta"):
            for sub in ("logs", "crash-reports"):
                path = self.root / role / sub
                if path.exists():
                    shutil.copytree(path, self.output / role / sub, dirs_exist_ok=True)
        report = {"complete": error is None, "profile": self.args.profile, "jarSha256": self.sha, "error": error, "results": self.results}
        (self.output / "report.json").write_text(json.dumps(report, indent=2))
        suite = ET.Element("testsuite", name="packaged-multiplayer-" + self.args.profile)
        actual = {(r["role"], r["id"]): r for r in self.results}
        for group in plan(self.args.profile):
            for c in group:
                result = actual.get((c["role"], c["id"]))
                case = ET.SubElement(suite, "testcase", classname=c["role"], name=c["id"], time=str(result.get("seconds", 0) if result else 0))
                if not result or result["status"] != "passed":
                    ET.SubElement(case, "failure", message=result.get("detail", "") if result else "Not executed / missing result")
        if error:
            ET.SubElement(ET.SubElement(suite, "testcase", classname="driver", name="complete-run"), "failure", message=error)
        ET.ElementTree(suite).write(self.output / "junit.xml", encoding="utf-8", xml_declaration=True)
        if os.environ.get("GITHUB_STEP_SUMMARY"):
            with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
                summary.write(f"## Packaged multiplayer: {self.args.profile}\n\n{'PASS' if error is None else 'FAIL'} — {len(self.results)} reported contracts.\n\nJAR SHA-256: `{self.sha}`\n\n")
                if error:
                    summary.write(f"```text\n{error[-5000:]}\n```\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("profile", choices=["standalone", "create"])
    parser.add_argument("--jar", type=Path, required=True)
    args = parser.parse_args()
    if os.environ.get("GITHUB_ACTIONS") != "true" or os.name != "posix":
        raise SystemExit("Minecraft runtime tests are restricted to GitHub's Linux runners; local unit tests are safe to run separately.")
    runner = Runner(args)
    error = None
    try:
        runner.setup()
        for role in ("server", "alpha", "beta"):
            runner.start(role)
        for group in plan(args.profile):
            runner.run_group(group)
        validate(runner.results, args.profile, runner.sha)
    except Exception:
        error = traceback.format_exc()
        print(error, flush=True)
    finally:
        runner.finish(error)
    if error:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
