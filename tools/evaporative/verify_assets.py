"""Check committed model integrity without requiring Blender or a graphical display."""
from pathlib import Path
import hashlib
import json
import struct
import subprocess

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "artwork/evaporative_cooler"
GAME = ROOT / "src/main/resources/assets/eln/model/evaporativecooler"


def check() -> None:
    manifest = json.loads((ASSETS / "manifest.json").read_text())
    for path, key in [(GAME / "evaporativecooler.obj", "obj_sha256"),
                      (GAME / "atlas.png", "atlas_sha256"),
                      (ROOT / "tools/evaporative/build_model.py", "generator_sha256")]:
        assert hashlib.sha256(path.read_bytes()).hexdigest() == manifest[key], str(path)
    data = (ASSETS / "evaporativecooler.glb").read_bytes()
    magic, version, total = struct.unpack_from("<4sII", data)
    assert magic == b"glTF" and version == 2 and total == len(data), "GLB header/length corrupted"
    offset = 12
    chunks = []
    while offset < total:
        size, kind = struct.unpack_from("<II", data, offset)
        assert size % 4 == 0 and offset + 8 + size <= total, "Invalid GLB chunk"
        chunks.append((kind, data[offset + 8:offset + 8 + size]))
        offset += 8 + size
    assert offset == total and len(chunks) == 2
    assert chunks[0][0] == 0x4E4F534A and chunks[1][0] == 0x004E4942
    scene = json.loads(chunks[0][1])
    binary = chunks[1][1]
    assert scene["animations"] and scene["meshes"], "Missing model or animation"
    assert len(scene["buffers"]) == 1 and scene["buffers"][0]["byteLength"] <= len(binary)
    for view in scene["bufferViews"]:
        assert view["buffer"] == 0
        assert view.get("byteOffset", 0) + view["byteLength"] <= len(binary)
    for image in scene["images"]:
        assert "bufferView" in image and image["mimeType"] == "image/png", "Texture must be packed"
        view = scene["bufferViews"][image["bufferView"]]
        start = view.get("byteOffset", 0)
        assert binary[start:start + 8] == b"\x89PNG\r\n\x1a\n", "Packed PNG signature corrupted"
    if (ROOT / ".git").exists():
        attrs = subprocess.check_output(["git", "check-attr", "text", "--", "artwork/evaporative_cooler/evaporativecooler.glb"], cwd=ROOT, text=True)
        assert attrs.strip().endswith(": unset"), "GLB must be marked binary in .gitattributes"
    print(f"Asset integrity passed: {manifest['triangles']} triangles, {len(scene['animations'])} animation(s), packed PNG, valid GLB chunks and source/game hashes.")


if __name__ == "__main__":
    check()
