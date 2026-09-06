#!/usr/bin/env python3
"""Require both the exact named regressions and the GameTest framework success summary."""
from pathlib import Path
import json
import re
import sys

EXPECTED = frozenset('''bench_fault_latch network_invalid_nbt future_schema vanilla_packet_codec
network_fault_persistence component_nbt_adapter bench_packet_rotation lifecycle_callbacks
bench_strict_boolean recipe_loaded network_nbt_packet malformed_state bench_chunk_callback
server_charge network_split_merge independent_instances nbt_roundtrip network_capacitor_rebuild
server_discharge network_source_toggle compressed_nbt_disk_roundtrip'''.split())

def validate(text: str) -> dict:
    names = set(re.findall(r'^ELN_GAMETEST_PASS ([a-z0-9_]+)\s*$', text, re.MULTILINE))
    if names != EXPECTED:
        raise ValueError(f'GameTest markers differ: missing={sorted(EXPECTED-names)}, unexpected={sorted(names-EXPECTED)}')
    summaries = re.findall(r'All (\d+) required tests passed', text)
    complete = re.findall(r'(\d+) GAME TESTS COMPLETE', text)
    if not summaries or int(summaries[-1]) != len(EXPECTED) or not complete or int(complete[-1]) != len(EXPECTED):
        raise ValueError('Missing or inconsistent real GameTest completion summary')
    if 'BUILD SUCCESSFUL' not in text or 'BUILD FAILED' in text:
        raise ValueError('Gradle did not exit successfully')
    return {'required_gametests': len(EXPECTED), 'cases': sorted(names)}

def main() -> None:
    root = Path(__file__).resolve().parents[1]
    path = Path(sys.argv[1]) if len(sys.argv)>1 else root/'build/evidence/gametest.log'
    result = validate(path.read_text(encoding='utf-8',errors='replace'))
    destination = root/'build/evidence/gametest-verification.json'
    destination.parent.mkdir(parents=True,exist_ok=True)
    destination.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(result,indent=2))

if __name__ == '__main__':
    main()
