#!/usr/bin/env python3
"""Add the mods.eln.misc.McBridge imports every Kotlin file needs.

Kotlin resolves extensions only when they are imported, so the bridge in
McBridge.kt is invisible until each user imports the specific symbols it calls.
Members always win over extensions, so importing a name a file also has as a
member is harmless.

Idempotent: re-run after adding a bridge symbol.
"""
import pathlib, re, subprocess, sys

SYMBOLS = [
    "xCoord", "yCoord", "zCoord",
    "getBlock", "getBlockMetadata", "getBlockState", "getBlockEntity",
    "setBlock", "setBlockToAir", "isBlockLoaded", "isEmptyBlock",
    "getIndirectPowerLevelTo", "markBlockForUpdate", "isReplaceable", "isNothing",
    # 1.21
    "rand", "isItemEqual", "writeToNBT", "stackFromNbt", "itemId", "itemById", "blockById",
    "tagCompound", "dimension", "hasTagCompound", "editTag",
]
PKG = "mods.eln.misc"

def needed(src: str) -> list:
    out = []
    for s in SYMBOLS:
        if re.search(r"[.\s(]" + s + r"\b", src) and f"import {PKG}.{s}\n" not in src:
            out.append(s)
    return out

def main(files):
    touched = 0
    for f in files:
        p = pathlib.Path(f)
        if p.suffix != ".kt":
            continue
        src = p.read_text(encoding="utf-8")
        m = re.match(r"^(?:@file:[^\n]*\n)*\s*package\s+([\w.]+)", src)
        if not m or m.group(1) == PKG:
            continue
        add = needed(src)
        if not add:
            continue
        imports = "".join(f"import {PKG}.{s}\n" for s in sorted(add))
        # after the last existing import, else right after the package line
        last = None
        for im in re.finditer(r"^import [^\n]*\n", src, re.M):
            last = im
        at = last.end() if last else m.end() + 1
        p.write_text(src[:at] + imports + src[at:], encoding="utf-8")
        touched += 1
    print(f"added bridge imports to {touched} files")

if __name__ == "__main__":
    files = sys.argv[1:] or subprocess.run(
        ["git", "ls-files", "src/main", "src/test"], capture_output=True, text=True
    ).stdout.split()
    main(files)
