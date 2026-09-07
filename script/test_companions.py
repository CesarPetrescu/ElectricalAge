import json
import tempfile
import unittest
from pathlib import Path
from check_companions import required, validate


class CompanionReportTests(unittest.TestCase):
    def test_missing_reports_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            for profile in ("fluids", "opencomputers", "combined"):
                self.assertTrue(validate(Path(directory), profile)[1])

    def test_required_checks_cannot_be_skipped_or_omitted(self):
        for profile in ("fluids", "opencomputers", "combined"):
            with tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                for phase in ("place", "restart"):
                    suite = f"companions-{profile}-{phase}"
                    data = {"suite": suite, "complete": True, "failures": 0, "results": [
                        {"id": i, "check": c, "status": "passed"} for i, c in sorted(required(profile, phase))]}
                    (root / f"{suite}.json").write_text(json.dumps(data))
                self.assertFalse(validate(root, profile)[1])
                path = root / f"companions-{profile}-restart.json"
                data = json.loads(path.read_text())
                original = json.dumps(data)
                for mutation in ("incomplete", "omitted", "skipped", "duplicate", "failed"):
                    data = json.loads(original)
                    if mutation == "incomplete": data["complete"] = False
                    elif mutation == "omitted": data["results"].pop()
                    elif mutation == "duplicate": data["results"].append(data["results"][0])
                    else: data["results"][0]["status"] = mutation
                    path.write_text(json.dumps(data))
                    self.assertTrue(validate(root, profile)[1], mutation)


if __name__ == "__main__":
    unittest.main()
