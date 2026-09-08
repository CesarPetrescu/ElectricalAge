import copy
import unittest
from unittest.mock import Mock, patch
from pathlib import Path

from multiplayer_plan import plan, validate
from run_multiplayer import offline_uuid, install_client


class MultiplayerGateTest(unittest.TestCase):
    def test_pinned_installer_does_not_query_loader_version_listing(self):
        launcher, log = Mock(), Mock()
        runtime, installer = Path("runtime"), Path("installer.jar")
        launcher.vanilla_launcher.do_vanilla_launcher_profiles_exists.return_value = False
        with patch("run_multiplayer.subprocess.run") as run:
            self.assertEqual(install_client(launcher, "1.21.1", "21.1.249", runtime, "java", installer, log), "neoforge-21.1.249")
        launcher.mod_loader.get_mod_loader.assert_not_called()
        self.assertEqual([c.args[0] for c in launcher.install.install_minecraft_version.call_args_list], ["1.21.1", "neoforge-21.1.249"])
        launcher.vanilla_launcher.create_empty_vanilla_launcher_profiles_file.assert_called_once_with(runtime)
        self.assertEqual(run.call_args.args[0], ["java", "-jar", str(installer), "--install-client", str(runtime)])
        self.assertTrue(run.call_args.kwargs["check"])

    def test_failed_installer_cannot_be_reported_as_installed(self):
        import subprocess
        launcher = Mock()
        with patch("run_multiplayer.subprocess.run", side_effect=subprocess.CalledProcessError(1, "installer")):
            with self.assertRaises(subprocess.CalledProcessError):
                install_client(launcher, "1.21.1", "21.1.249", Path("runtime"), "java", Path("installer.jar"), Mock())
        self.assertEqual(launcher.install.install_minecraft_version.call_count, 1)

    def reports(self, profile="standalone"):
        results = []
        for group in plan(profile):
            for c in group:
                observation = {}
                if c["action"] == "boot":
                    observation = {"jarSha256": "abc", "create": profile == "create", "dedicated": True, "integratedServer": False}
                if c["action"] == "join":
                    name = "ElnAlpha" if c["role"] == "alpha" else "ElnBeta"
                    observation = {"uuid": offline_uuid(name), "name": name, "integratedServer": False}
                if c["action"] == "inventory":
                    observation = {"prints": 1 if c["role"] == "alpha" else 0}
                pid = {"server": 10, "alpha": 20, "beta": 30}[c["role"]]
                if c["id"] == "restarted-packaged-server":
                    pid = 40
                results.append({"role": c["role"], "id": c["id"], "action": c["action"], "status": "passed", "pid": pid, "observation": observation})
        return results

    def test_complete_profiles(self):
        for profile in ("standalone", "create"):
            results = self.reports(profile)
            self.assertEqual(validate(results, profile, "abc"), len(results))

    def test_missing_empty_and_duplicate_results_fail(self):
        results = self.reports()
        for invalid in ([], results[:-1], results + [results[0]]):
            with self.assertRaises(ValueError):
                validate(invalid, "standalone", "abc")

    def test_failed_skipped_and_wrong_action_fail(self):
        for field, value in (("status", "failed"), ("status", "skipped"), ("action", "wrong")):
            results = self.reports()
            results[0][field] = value
            with self.assertRaises(ValueError):
                validate(results, "standalone", "abc")

    def test_wrong_jar_profile_shared_jvm_or_integrated_server_fail(self):
        for field, value in (("jarSha256", "wrong"), ("create", True), ("dedicated", False)):
            results = self.reports()
            results[0]["observation"][field] = value
            with self.assertRaises(ValueError):
                validate(results, "standalone", "abc")
        results = self.reports()
        results[1]["pid"] = results[0]["pid"]
        with self.assertRaises(ValueError):
            validate(results, "standalone", "abc")
        results = self.reports()
        results[1]["observation"]["integratedServer"] = True
        with self.assertRaises(ValueError):
            validate(results, "standalone", "abc")

    def test_duplicate_client_inventory_fails(self):
        results = self.reports()
        next(r for r in results if r["id"] == "inventory-after-transfer" and r["role"] == "beta")["observation"]["prints"] = 1
        with self.assertRaises(ValueError):
            validate(results, "standalone", "abc")

    def test_no_restart_fails(self):
        results = self.reports()
        next(r for r in results if r["id"] == "restarted-packaged-server")["pid"] = 10
        with self.assertRaises(ValueError):
            validate(results, "standalone", "abc")

    def test_duplicate_player_identity_fails(self):
        results = self.reports()
        a = next(r for r in results if r["id"] == "first-join")
        b = next(r for r in results if r["id"] == "late-join")
        b["observation"] = copy.deepcopy(a["observation"])
        with self.assertRaises(ValueError):
            validate(results, "standalone", "abc")

    def test_offline_players_are_distinct_and_stable(self):
        self.assertNotEqual(offline_uuid("ElnAlpha"), offline_uuid("ElnBeta"))
        self.assertEqual(offline_uuid("ElnAlpha"), offline_uuid("ElnAlpha"))

    def test_create_checks_cannot_be_satisfied_by_standalone(self):
        with self.assertRaises(ValueError):
            validate(self.reports(), "create", "abc")


if __name__ == "__main__":
    unittest.main()
