#!/usr/bin/env bash
# Minecraft 1.11+ lowercases every ResourceLocation path, so an asset whose path
# contains capitals can never be found again. Rename the files, then lowercase the
# keys in sounds.json. String literals in code are handled by Obj3DFolder/Obj3D,
# which lowercase their lookups.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
root=src/main/resources/assets/eln
n=0
# Deepest paths first so parent directory renames do not invalidate child paths.
while IFS= read -r p; do
    d=$(dirname "$p"); b=$(basename "$p")
    lb=$(printf '%s' "$b" | tr '[:upper:]' '[:lower:]')
    [ "$b" = "$lb" ] && continue
    # lang files keep their canonical en_US.lang spelling; the 1.12 loader lowercases them itself
    case "$d" in */lang) continue;; esac
    if [ -e "$d/$lb" ] && [ "$d/$b" != "$d/$lb" ]; then
        echo "collision, skipping: $p" >&2; continue
    fi
    git mv -f "$d/$b" "$d/$lb"
    n=$((n+1))
done < <(find "$root" -depth -mindepth 1 | sort -r)
echo "renamed $n paths"
