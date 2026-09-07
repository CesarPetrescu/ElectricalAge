"""Require the selected companion profile's named assertions in both JVM launches."""
import argparse
import json
import os
from pathlib import Path


def required(profile, phase):
    checks = {(profile, "setup"), ("eln", "loaded"), ("jade", "loaded")}
    if profile != "opencomputers":
        checks |= {(m, "loaded") for m in ("pneumaticcraft", "railcraft", "immersiveengineering", "pipez")}
        checks |= {("pipez", "pneumaticcraft-tank-to-eln-engine"), ("pipez", "configured-source" if phase == "place" else "persisted-tank-and-extraction")}
        if phase == "place":
            light = ("pneumaticcraft:gasoline", "pneumaticcraft:kerosene", "pneumaticcraft:lpg", "pneumaticcraft:ethanol", "immersiveengineering:ethanol")
            engines = [(e, f) for e in ("Gas Turbine", "Radial Motor") for f in light]
            engines += [(e, "railcraft:steam") for e in ("Steam Turbine", "Large Steam Turbine")]
            engines += [("Large Gas Turbine", "pneumaticcraft:gasoline")]
            checks |= {(f"{e}/{f}", "capability-and-mechanical-work") for e, f in engines}
            checks |= {(f"Fuel Heat Furnace/{f}", "accept-and-persist-fuel") for f in ("immersiveengineering:biodiesel", "pneumaticcraft:biodiesel", "pneumaticcraft:diesel", "railcraft:creosote")}
    if profile != "fluids":
        checks |= {("opencomputers", "loaded"), ("opencomputers", "native-component")}
    if profile == "opencomputers":
        checks.add(("computercraft", "absent-native-isolation"))
    if profile == "combined":
        checks |= {("create", "loaded"), ("computercraft", "loaded"), ("computercraft", "probe-alongside-opencomputers")}
    return checks


def validate(directory, profile):
    errors, rows = [], []
    for phase in ("place", "restart"):
        suite = f"companions-{profile}-{phase}"
        try:
            report = json.loads((directory / f"{suite}.json").read_text(encoding="utf-8"))
            if report.get("complete") is not True or report.get("suite") != suite:
                raise ValueError("wrong suite or incomplete run")
            seen = set()
            for result in report["results"]:
                key = result["id"], result["check"]
                if key in seen:
                    raise ValueError(f"duplicate assertion {key}")
                seen.add(key)
                if result["status"] != "passed":
                    errors.append(f"{suite}: {key}: {result['status']} {result.get('detail', '')}")
                rows.append(f"| {phase} | {key[0]} | {key[1]} | {result['status']} |")
            missing = required(profile, phase) - seen
            if missing or report.get("failures") != 0:
                raise ValueError(f"missing={sorted(missing)}, failures={report.get('failures')}")
        except (OSError, ValueError, KeyError, TypeError) as error:
            errors.append(f"{suite}: {error}")
    summary = "\n".join([f"## Companion compatibility: {profile}", "", "| Launch | Subject | Assertion | Result |", "|---|---|---|---|", *rows, "", *[f"- ERROR: {e}" for e in errors], ""])
    return summary, errors


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("profile", choices=["fluids", "opencomputers", "combined"])
    parser.add_argument("--directory", type=Path, default=Path("build/smoke-artifacts/contracts"))
    args = parser.parse_args()
    summary, errors = validate(args.directory, args.profile)
    print(summary)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as f:
            f.write(summary)
    raise SystemExit(bool(errors))
