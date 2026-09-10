import copy
import unittest
from unittest.mock import Mock, patch
from pathlib import Path

from multiplayer_plan import plan, validate
from run_multiplayer import (
    ACTIVE_SERVER_SHUTDOWN_SECONDS,
    Runner,
    install_client,
    offline_uuid,
    run_installer,
    server_shutdown_in_progress,
)


ACTIVE_SERVER_SHUTDOWN_DUMP = '''
"Server thread" #42 prio=5 os_prio=0 cpu=66532ms elapsed=240s tid=0x1 RUNNABLE
   java.lang.Thread.State: RUNNABLE
        at net.minecraft.server.level.ChunkMap.processUnloads(ChunkMap.java:999)
        at net.minecraft.server.level.ChunkMap.saveAllChunks(ChunkMap.java:888)
        at net.minecraft.server.level.ServerChunkCache.close(ServerChunkCache.java:555)
        at net.minecraft.server.level.ServerLevel.close(ServerLevel.java:123)
        at net.minecraft.server.MinecraftServer.stopServer(MinecraftServer.java:777)
"Reference Handler" #9 daemon prio=10 os_prio=0 tid=0x2 RUNNABLE
'''


class MultiplayerGateTest(unittest.TestCase):
    def test_slow_shutdown_captures_diagnostics_without_ignoring_a_hang(self):
        import subprocess
        runner = Runner.__new__(Runner)
        process = Mock()
        runner.processes = {"server": process}
        runner.thread_dump = Mock()
        process.wait.side_effect = subprocess.TimeoutExpired("server", 20)
        with self.assertRaises(subprocess.TimeoutExpired):
            runner.wait_for_exit("server")
        runner.thread_dump.assert_called_once_with("server", "slow-shutdown")
        self.assertEqual(process.wait.call_count, 2)
        self.assertLessEqual(process.wait.call_args.kwargs["timeout"], 60)

    def test_shutdown_classifier_requires_the_actual_server_thread(self):
        self.assertTrue(server_shutdown_in_progress(ACTIVE_SERVER_SHUTDOWN_DUMP))
        worker_only = ACTIVE_SERVER_SHUTDOWN_DUMP.replace('"Server thread"', '"Worker-Main-1"', 1)
        self.assertFalse(server_shutdown_in_progress(worker_only))
        no_stop = ACTIVE_SERVER_SHUTDOWN_DUMP.replace("MinecraftServer.stopServer", "MinecraftServer.tickServer")
        self.assertFalse(server_shutdown_in_progress(no_stop))
        no_save_or_unload = ACTIVE_SERVER_SHUTDOWN_DUMP
        for method in ("ChunkMap.processUnloads", "ChunkMap.saveAllChunks", "ServerChunkCache.close", "ServerLevel.close"):
            no_save_or_unload = no_save_or_unload.replace(method, "SomeOtherWork.run")
        self.assertFalse(server_shutdown_in_progress(no_save_or_unload))

    def test_active_clean_server_shutdown_gets_bounded_extra_time(self):
        import subprocess
        runner = Runner.__new__(Runner)
        process = Mock()
        runner.processes = {"server": process}
        runner.thread_dump = Mock(return_value=ACTIVE_SERVER_SHUTDOWN_DUMP)
        process.wait.side_effect = [subprocess.TimeoutExpired("server", 20), 0]
        self.assertEqual(runner.wait_for_exit("server"), 0)
        self.assertEqual(process.wait.call_count, 2)
        extended = process.wait.call_args_list[1].kwargs["timeout"]
        self.assertGreater(extended, 180)
        self.assertLessEqual(extended, ACTIVE_SERVER_SHUTDOWN_SECONDS)
        runner.thread_dump.assert_called_once_with("server", "slow-shutdown")

    def test_active_clean_shutdown_still_fails_at_the_hard_deadline(self):
        import subprocess
        runner = Runner.__new__(Runner)
        process = Mock()
        runner.processes = {"server": process}
        runner.thread_dump = Mock(return_value=ACTIVE_SERVER_SHUTDOWN_DUMP)
        process.wait.side_effect = subprocess.TimeoutExpired("server", 20)
        with self.assertRaises(subprocess.TimeoutExpired):
            runner.wait_for_exit("server")
        self.assertEqual(process.wait.call_count, 2)
        extended = process.wait.call_args_list[1].kwargs["timeout"]
        self.assertGreater(extended, 180)
        self.assertLessEqual(extended, ACTIVE_SERVER_SHUTDOWN_SECONDS)
        self.assertEqual(runner.thread_dump.call_count, 2)
        runner.thread_dump.assert_any_call("server", "slow-shutdown")
        runner.thread_dump.assert_any_call("server", "shutdown-timeout")

    def test_normal_shutdown_preserves_the_process_exit_code(self):
        runner = Runner.__new__(Runner)
        runner.processes = {"server": Mock()}
        runner.processes["server"].wait.return_value = 7
        runner.thread_dump = Mock()
        self.assertEqual(runner.wait_for_exit("server"), 7)
        runner.thread_dump.assert_not_called()

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
        with patch("run_multiplayer.subprocess.run", side_effect=subprocess.CalledProcessError(1, "installer")) as run, patch("run_multiplayer.time.sleep"):
            with self.assertRaises(subprocess.CalledProcessError):
                install_client(launcher, "1.21.1", "21.1.249", Path("runtime"), "java", Path("installer.jar"), Mock())
        self.assertEqual(run.call_count, 3)
        self.assertEqual(launcher.install.install_minecraft_version.call_count, 1)

    def test_partial_installer_failure_retries_but_stops_on_success(self):
        import subprocess
        with patch("run_multiplayer.subprocess.run", side_effect=[subprocess.CalledProcessError(1, "installer"), None]) as run, patch("run_multiplayer.time.sleep"):
            run_installer("java", Path("installer.jar"), "--install-server", Path("server"), Mock())
        self.assertEqual(run.call_count, 2)

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
