#!/usr/bin/env bash
# Show Re-Wired's 1.12.2 port diff for one class, resolved through tools/port/map.tsv
# (handles the Java<->Kotlin moves and the Coordonate/Coordinate-style renames).
#   tools/port/crib.sh mods/eln/node/six/SixNodeBlock
#   tools/port/crib.sh src/main/kotlin/mods/eln/node/six/SixNodeBlock.kt
set -u
MB=6ffe05384f0f00b2f571bb44fd55ad335920dbbe
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MAP="$ROOT/tools/port/map.tsv"
[ -f "$MAP" ] || { echo "run tools/port/build_map.py first" >&2; exit 1; }
q="${1:?usage: crib.sh <class-or-path>}"
q="${q#src/main/java/}"; q="${q#src/main/kotlin/}"; q="${q%.java}"; q="${q%.kt}"
row=$(awk -F'\t' -v k="$q" '$1==k' "$MAP")
[ -n "$row" ] || row=$(awk -F'\t' -v k="/${q##*/}" 'index($1,k)>0 && index($1,k)==length($1)-length(k)+1' "$MAP" | head -1)
[ -n "$row" ] || { echo "no map entry for $q" >&2; exit 2; }
IFS=$'\t' read -r key status mb up rw <<<"$row"
echo "### $key"
echo "### status:   $status"
echo "### base:     $mb"
echo "### upstream: $up"
echo "### rewired:  $rw"
echo
case "$status" in
  FRESH)  echo "# upstream-only class: no Re-Wired counterpart. Port by hand; see tools/port/rewrite.pl and PORT.md."; exit 0 ;;
  ASIS)   echo "# untouched by both sides: expected to compile unmodified." ;;
esac
[ "$rw" = "-" ] && exit 0
if [ "$mb" = "-" ]; then git -C "$ROOT" show "rw/main:$rw"
else git -C "$ROOT" diff "$MB:$mb" "rw/main:$rw"; fi
