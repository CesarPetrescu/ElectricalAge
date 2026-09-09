#!/usr/bin/env python3
"""Verify the pinned legacy allocation region, not a pretend Minecraft registry run."""
from pathlib import Path
import hashlib
import json
import re

root = Path(__file__).resolve().parents[2]
meta = json.loads((root / 'tools/hv/fixtures/baseline.json').read_text())
source = (root / 'src/main/kotlin/mods/eln/registration/SixNodeRegistration.kt').read_text()
body = source[source.index('        val singles = listOf('):source.index('        // New identities live')].rstrip()
assert hashlib.sha256(body.encode()).hexdigest() == meta['catalogue_body_sha256'], 'Legacy allocation/specification region changed'
rows = [x.split('\t') for x in (root / 'tools/hv/fixtures/legacy-utility-identities.tsv').read_text().splitlines() if x and not x.startswith('#')]
assert len(rows) == 152
assert [int(r[0]) for r in rows] == list(range(2176, 2328))
assert len({r[1] for r in rows}) == 152 and len({r[2] for r in rows}) == 152
hv = (root / 'src/main/kotlin/mods/eln/sixnode/electricalcable/HvCableSpecifications.kt').read_text()
ids = [int(x) for x in re.findall(r'HvCableSpec\((\d+),', hv)]
assert ids == list(range(2368, 2383))
assert not set(ids) & {int(r[0]) for r in rows}
print('Static migration check: unchanged legacy allocation region, 152 identities; 15 unique appended HV IDs')
