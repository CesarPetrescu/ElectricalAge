import json
import tempfile
import unittest
from pathlib import Path
from check_block_contracts import REQUIRED, CLIENT_REQUIRED, summarize
from check_client_assets import inspect


class ReportsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name)

    def write(self, suites=REQUIRED, **changes):
        for suite in suites:
            data = dict(suite=suite, complete=True, failures=0,
                        results=[dict(id="eln:sample", check="place", status="passed", detail="")])
            data.update(changes)
            (self.path / f"{suite}.json").write_text(json.dumps(data), encoding="utf-8")

    def test_missing(self):
        self.assertEqual(len(summarize(self.path)[1]), len(REQUIRED))

    def test_valid(self):
        self.write()
        text, errors = summarize(self.path)
        self.assertFalse(errors)
        self.assertIn("eln:sample", text)

    def test_wire_reports_are_required(self):
        self.assertIn("wire-behavior", REQUIRED)
        self.assertIn("wire-behavior-restart", REQUIRED)
        self.assertIn("wire-thermal", REQUIRED)
        self.assertIn("wire-thermal-restart", REQUIRED)
        self.write(suites=tuple(s for s in REQUIRED if not s.startswith("wire-")))
        self.assertEqual(len(summarize(self.path)[1]), 4)

    def test_incomplete_empty_failed_and_unknown(self):
        for changes in [dict(complete=False), dict(results=[]), dict(failures=1),
                        dict(results=[dict(id="eln:x", check="p", status="unknown")]),
                        dict(results=[dict(id="eln:x", check="p", status="skipped", detail="")]),
                        dict(failures=1, results=[dict(id="eln:x", check="p", status="failed", detail="absent")])]:
            with self.subTest(changes=changes):
                self.write(**changes)
                self.assertTrue(summarize(self.path)[1])

    def test_duplicate(self):
        row = dict(id="eln:x", check="place", status="passed", detail="")
        self.write(results=[row, row])
        self.assertTrue(summarize(self.path)[1])

    def test_monitor_and_meter_contracts_cannot_be_missing(self):
        required = {"monitor-print", "monitor-print-restart", "circuit-diagnostics", "circuit-diagnostics-restart"}
        self.assertTrue(required.issubset(REQUIRED))
        self.assertIn("monitor-print-client", CLIENT_REQUIRED)
        self.write(suites=tuple(s for s in REQUIRED if s not in required))
        self.assertEqual(len(summarize(self.path)[1]), len(required))

    def test_client_reports_are_required_and_must_finish(self):
        suites = REQUIRED + CLIENT_REQUIRED
        self.write()
        self.assertEqual(len(summarize(self.path, suites)[1]), len(CLIENT_REQUIRED))
        self.write(suites=CLIENT_REQUIRED)
        self.assertFalse(summarize(self.path, suites)[1])
        self.write(suites=("wiki-client",), complete=False)
        self.assertEqual(len(summarize(self.path, suites)[1]), 1)

    def test_asset_gate(self):
        bad, known = inspect("Unable to load model: 'eln:item/conduit'\nUnable to load model: 'eln:item/new_machine'\nMissing textures in model eln:block/new_machine")
        self.assertEqual(len(bad), 2)
        self.assertEqual(set(known), {"eln:item/conduit"})
        self.assertFalse(inspect("normal startup")[0])
        self.assertEqual(len(inspect("Failed to load texture: eln:textures/blocks/missing.png")[0]), 1)


if __name__ == "__main__":
    unittest.main()
