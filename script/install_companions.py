"""Install only hash-pinned 1.21.1 test mods into fresh, isolated run directories."""
import argparse
import hashlib
import json
import shutil
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def install(profile, directories):
    lock = json.loads((ROOT / "tools/port/companions.lock.json").read_text())
    if lock["minecraft"] != "1.21.1" or lock["loader"] != "neoforge":
        raise ValueError("Wrong Minecraft/loader lock")
    selected = lock["profiles"][profile]
    mods = [m for m in lock["mods"] if m["project"] in selected]
    if len(mods) != len(selected):
        raise ValueError("Missing/duplicate locked mod")
    for directory in directories:
        directory = directory.resolve()
        if not directory.is_relative_to(ROOT / "run") or directory.name != "mods":
            raise ValueError("Only test mods directories beneath run/ are allowed")
        directory.mkdir(parents=True, exist_ok=True)
        # Refuse to mix test profiles with a user's installed modpack. Never delete its files.
        unexpected = {p.name for p in directory.glob("*.jar")} - {m["filename"] for m in mods}
        if unexpected:
            raise ValueError(f"Unexpected mods in {directory}: {sorted(unexpected)}")
    cache = ROOT / "build/compat-cache"
    cache.mkdir(parents=True, exist_ok=True)
    for mod in mods:
        if Path(mod["filename"]).name != mod["filename"] or not mod["url"].startswith("https://"):
            raise ValueError("Unsafe locked file or URL")
        jar = cache / mod["filename"]
        if not jar.exists():
            request = urllib.request.Request(mod["url"], headers={"User-Agent": "ElectricalAge-compatibility-CI/1.0"})
            with urllib.request.urlopen(request, timeout=120) as response:
                data = response.read()
            if hashlib.sha512(data).hexdigest() != mod["sha512"]:
                raise ValueError(f"Download checksum mismatch: {mod['project']}")
            jar.write_bytes(data)
        if hashlib.sha512(jar.read_bytes()).hexdigest() != mod["sha512"]:
            raise ValueError(f"Cached checksum mismatch: {mod['project']}")
        for directory in directories:
            shutil.copy2(jar, directory / jar.name)
        print(f"Verified {mod['project']} {mod['version']}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("profile", choices=["fluids", "opencomputers", "combined"])
    parser.add_argument("directories", nargs="+", type=Path)
    args = parser.parse_args()
    install(args.profile, args.directories)
