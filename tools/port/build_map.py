#!/usr/bin/env python3
"""Build tools/port/map.tsv: the per-class correspondence between the merge-base
tree, upstream HEAD (age-series) and rw/main (Electrical Age: Re-Wired).

Columns: key  status  mb_path  upstream_path  rw_path
status is one of:
  REPLAY    both sides changed the file  -> apply Re-Wired's diff to upstream's version
  VERBATIM  only Re-Wired changed it     -> take Re-Wired's file
  NOCRIB    only upstream changed it     -> port by hand, patterns only
  ASIS      neither side changed it      -> should compile untouched
  FRESH     upstream-only class          -> port by hand, no crib
  GONE      upstream deleted it          -> ignore
"""
import subprocess, sys, difflib, os
from collections import defaultdict

MB = "6ffe05384f0f00b2f571bb44fd55ad335920dbbe"
REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

def ls(ref):
    out = subprocess.run(["git", "-C", REPO, "ls-tree", "-r", "--name-only", ref, "--", "src/main"],
                         capture_output=True, text=True, check=True).stdout.split("\n")
    return [p for p in out if p.endswith((".java", ".kt"))]

def key_of(path):
    p = path
    for pre in ("src/main/java/", "src/main/kotlin/"):
        if p.startswith(pre):
            p = p[len(pre):]
            break
    for ext in (".java", ".kt"):
        if p.endswith(ext):
            p = p[: -len(ext)]
    return p

def index(ref):
    d = {}
    for p in ls(ref):
        d[key_of(p)] = p
    return d

def blob(ref, path):
    r = subprocess.run(["git", "-C", REPO, "show", f"{ref}:{path}"], capture_output=True)
    return r.stdout if r.returncode == 0 else None

mb, up, rw = index(MB), index("HEAD"), index("rw/main")

# Fuzzy-match keys that exist on one side only: catches renames git's similarity
# index missed (Coordonate -> Coordinate, IDestructible -> IDestructable, ...).
def fuzzy(src_keys, dst_keys):
    """map src key -> dst key for near-identical basenames in the same package."""
    by_pkg = defaultdict(list)
    for k in dst_keys:
        by_pkg[k.rsplit("/", 1)[0] if "/" in k else ""].append(k)
    out = {}
    for k in src_keys:
        pkg = k.rsplit("/", 1)[0] if "/" in k else ""
        base = k.rsplit("/", 1)[-1]
        cands = by_pkg.get(pkg, [])
        names = [c.rsplit("/", 1)[-1] for c in cands]
        m = difflib.get_close_matches(base, names, n=1, cutoff=0.86)
        if m:
            out[k] = cands[names.index(m[0])]
    return out

mb_only_vs_up = fuzzy(set(mb) - set(up), set(up) - set(mb))
mb_only_vs_rw = fuzzy(set(mb) - set(rw), set(rw) - set(mb))
up_only_vs_rw = fuzzy(set(up) - set(rw), set(rw) - set(up))

rows = []
for k_up, p_up in sorted(up.items()):
    # find the merge-base ancestor of this upstream file
    k_mb = k_up if k_up in mb else None
    if k_mb is None:
        for src, dst in mb_only_vs_up.items():
            if dst == k_up:
                k_mb = src
                break
    p_mb = mb.get(k_mb) if k_mb else None

    # find Re-Wired's counterpart
    k_rw = None
    for cand in (k_up, k_mb):
        if cand and cand in rw:
            k_rw = cand
            break
    if k_rw is None:
        k_rw = up_only_vs_rw.get(k_up) or (mb_only_vs_rw.get(k_mb) if k_mb else None)
    p_rw = rw.get(k_rw) if k_rw else None

    if p_mb is None:
        status = "FRESH"
    else:
        b_mb = blob(MB, p_mb)
        up_changed = p_up != p_mb or blob("HEAD", p_up) != b_mb
        rw_changed = p_rw is not None and (p_rw != p_mb or blob("rw/main", p_rw) != b_mb)
        if up_changed and rw_changed:   status = "REPLAY"
        elif rw_changed:                status = "VERBATIM"
        elif up_changed:                status = "NOCRIB"
        else:                           status = "ASIS"
    rows.append((k_up, status, p_mb or "-", p_up, p_rw or "-"))

with open(os.path.join(REPO, "tools/port/map.tsv"), "w") as f:
    f.write("# key\tstatus\tmerge_base\tupstream\trewired\n")
    for r in rows:
        f.write("\t".join(r) + "\n")

counts = defaultdict(int)
for r in rows:
    counts[r[1]] += 1
for s in ("REPLAY", "VERBATIM", "NOCRIB", "ASIS", "FRESH"):
    print(f"{s:9s} {counts[s]:4d}")
print(f"{'TOTAL':9s} {len(rows):4d}")
