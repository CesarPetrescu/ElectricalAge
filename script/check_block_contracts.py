"""Fail closed on absent, incomplete or failing block reports; publish honest coverage."""
import argparse
import json
import os
from collections import Counter, defaultdict
from pathlib import Path

REQUIRED = ("blocks-place", "blocks-settled", "blocks-restart", "power-behavior", "power-behavior-restart")
CLIENT_REQUIRED = ("power-client-leds", "wiki-client", "lighting-gallery")


def summarize(directory, suites=REQUIRED):
    errors, rows = [], []
    by_block = defaultdict(Counter)
    for suite in suites:
        try:
            report = json.loads((directory / f"{suite}.json").read_text(encoding="utf-8"))
            if report.get("suite") != suite or report.get("complete") is not True:
                raise ValueError("wrong suite or incomplete run")
            results = report["results"]
            if not results or not any(r["status"] == "passed" for r in results):
                raise ValueError("empty report or no passing assertions")
            seen = set()
            counts = Counter()
            for r in results:
                key = (r["id"], r["check"])
                if key in seen or r["status"] not in ("passed", "failed", "skipped"):
                    raise ValueError(f"duplicate/invalid result: {key}")
                seen.add(key)
                if r["status"] == "skipped" and not r.get("detail", "").strip():
                    raise ValueError(f"unexplained exemption: {key}")
                counts[r["status"]] += 1
                by_block[r["id"]][r["status"]] += 1
                if r["status"] == "failed":
                    errors.append(f"{suite}: {key}: {r.get('detail', '')}")
            if report.get("failures") != counts["failed"]:
                raise ValueError("failure count mismatch")
            rows.append(f"| {suite} | {counts['passed']} | {counts['failed']} | {counts['skipped']} |")
        except (OSError, ValueError, KeyError, TypeError) as error:
            errors.append(f"{suite}: {error}")
    summary = ["## Block contracts", "", "Skipped means **untested**, not passed.", "",
               "| Suite | Passed | Failed | Skipped |", "|---|---:|---:|---:|", *rows, "",
               "Placement/removal and restart identity do not prove exact drops, saved settings, inventory conservation or family behavior.", ""]
    if errors:
        summary += ["### Failures", "", *[f"- {e}" for e in errors], ""]
    summary += ["<details><summary>Per-block / descriptor results</summary>", "",
                "| ID | Passed | Failed | Untested |", "|---|---:|---:|---:|"]
    summary += [f"| `{key}` | {c['passed']} | {c['failed']} | {c['skipped']} |" for key, c in sorted(by_block.items())]
    summary += ["", "</details>", ""]
    return "\n".join(summary), errors


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", nargs="?", type=Path, default=Path("build/smoke-artifacts/contracts"))
    parser.add_argument("--client", action="store_true", help="Also require completed rendered-client contracts")
    args = parser.parse_args()
    summary, errors = summarize(args.directory, REQUIRED + CLIENT_REQUIRED if args.client else REQUIRED)
    args.directory.mkdir(parents=True, exist_ok=True)
    (args.directory / "summary.md").write_text(summary, encoding="utf-8")
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as output:
            output.write(summary)
    print("Block contract reports:", "FAILED" if errors else "passed")
    for error in errors:
        print(error)
    raise SystemExit(bool(errors))
