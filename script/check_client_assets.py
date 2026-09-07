"""Reject new ELN missing-model/texture diagnostics; keep existing debt visible."""
import json
import re
import sys
from pathlib import Path

# Narrow, reviewable debt list, not a wildcard exemption for dynamic ELN renderers.
KNOWN_MODELS = {
    "eln:item/conduitsingle": "Existing development-only conduit item lacks a baked fallback model.",
    "eln:item/conduit": "Existing conduit item lacks a baked fallback model; renderer migration pending.",
    "eln:item/isolation_transformer": "Existing isolation-transformer item lacks a baked fallback model; renderer migration pending.",
}


def inspect(log):
    failed, exempt = set(), set()
    for line in log.splitlines():
        if "eln:" not in line or not re.search(r"Unable to load model|Missing textures|Using missing texture|Exception loading", line, re.I):
            continue
        match = re.search(r"Unable to load model: '([^']+)'", line)
        if match and match[1] in KNOWN_MODELS:
            exempt.add(match[1])
        else:
            failed.add(line)
    return sorted(failed), {key: KNOWN_MODELS[key] for key in sorted(exempt)}


if __name__ == "__main__":
    log = Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace")
    if not log.strip():
        raise SystemExit("Missing client log content")
    failed, exempt = inspect(log)
    result = dict(new_asset_errors=failed, known_unfixed_asset_warnings=exempt)
    output = Path("build/smoke-artifacts/client-assets.json")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result, indent=2))
    raise SystemExit(bool(failed))
