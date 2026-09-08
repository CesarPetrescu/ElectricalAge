import json
from pathlib import Path
import tempfile
import unittest
from run_ore_worldgen import ORES, PROFILES, enabled, expected_checks, validate, read_properties


class OreGateTest(unittest.TestCase):
    def test_pinned_versions_accept_property_whitespace(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "versions.properties"
            path.write_text("  # ignored = comment\n  neoVersion    = 21.1.249 \nminecraftVersion = 1.21.1\n")
            self.assertEqual(read_properties(path), {"neoVersion": "21.1.249", "minecraftVersion": "1.21.1"})
        actual = read_properties(Path(__file__).resolve().parents[1] / "gradle.properties")
        self.assertTrue(actual["neoVersion"])
        self.assertEqual(actual["minecraftVersion"], "1.21.1")

    def fixture(self, profile="default"):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        directory = Path(temporary.name)
        for index, phase in enumerate(("generate", "restart")):
            self.write(directory, f"ore-{phase}", {"suite": f"ore-{phase}", "complete": True, "failures": 0,
                "results": [{"id": i, "check": c, "status": "passed"} for i, c in expected_checks(phase)]})
            self.write(directory, f"runtime-{phase}", {"pid": index + 100, "jarSha256": "a" * 64,
                "seed": PROFILES[profile], "profile": profile, "dedicated": True, "production": True})
            census = {}
            for area in ["overworld", "nether", "end"] + (["fresh-after-restart"] if index else []):
                census[area] = {"chunks": 4 if area in ("nether", "end") else 16, "terrain": 10000,
                    "biomes": ["minecraft:plains"], "ores": {f"eln:{o}_ore": {"count": 10 if area not in ("nether", "end") and enabled(profile, o) else 0,
                        "positionsSha256": "b" * 64} for o in ORES}}
            self.write(directory, f"census-{phase}", census)
        return directory

    def write(self, directory, name, value):
        (directory / f"{name}.json").write_text(json.dumps(value))

    def reject(self, name, mutate, profile="default"):
        directory = self.fixture(profile)
        data = json.loads((directory / f"{name}.json").read_text())
        mutate(data)
        self.write(directory, name, data)
        with self.assertRaises((AssertionError, KeyError)):
            validate(directory, profile, "a" * 64)

    def test_complete_profiles(self):
        for profile in PROFILES:
            validate(self.fixture(profile), profile, "a" * 64)

    def test_missing_contract(self):
        self.reject("ore-generate", lambda d: d["results"].pop())

    def test_duplicate_contract(self):
        self.reject("ore-generate", lambda d: d["results"].append(d["results"][0]))

    def test_skipped_or_failed_or_incomplete_never_passes(self):
        for status in ("skipped", "failed"):
            self.reject("ore-restart", lambda d: d["results"][0].update(status=status))
        self.reject("ore-restart", lambda d: d.update(complete=False))

    def test_absent_enabled_ore(self):
        self.reject("census-generate", lambda d: d["overworld"]["ores"]["eln:cinnabar_ore"].update(count=0))

    def test_disabled_ore_or_wrong_dimension(self):
        self.reject("census-generate", lambda d: d["overworld"]["ores"]["eln:copper_ore"].update(count=1), "disabled")
        self.reject("census-generate", lambda d: d["nether"]["ores"]["eln:copper_ore"].update(count=1))

    def test_fresh_chunks_cannot_be_missing(self):
        self.reject("census-restart", lambda d: d.pop("fresh-after-restart"))

    def test_insufficient_terrain_or_missing_ore_inventory(self):
        self.reject("census-generate", lambda d: d["overworld"].update(terrain=0))
        self.reject("census-generate", lambda d: d["overworld"]["ores"].pop("eln:lead_ore"))

    def test_saved_ore_positions_must_match(self):
        self.reject("census-restart", lambda d: d["overworld"]["ores"]["eln:lead_ore"].update(positionsSha256="c" * 64))

    def test_actual_packaged_jar_and_independent_restart(self):
        for key, value in [("jarSha256", "c" * 64), ("production", False), ("dedicated", False), ("seed", 1), ("pid", 100)]:
            self.reject("runtime-restart", lambda d: d.update({key: value}))


if __name__ == "__main__":
    unittest.main()
