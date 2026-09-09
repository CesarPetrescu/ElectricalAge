#!/usr/bin/env python3
"""Regression tests for launcher supervision, KFF registration, and signed APT scope."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest

from native_campaign_process import ClientFailure, crash_snapshot, wait_for_client

ROOT = Path(__file__).resolve().parents[1]


class StartupTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.crashes = self.root / 'crash-reports'
        self.crashes.mkdir()

    def process(self, code):
        p = subprocess.Popen([sys.executable, '-c', code])
        def cleanup():
            if p.poll() is None:
                p.kill()
            p.wait(timeout=5)
        self.addCleanup(cleanup)
        return p

    def test_successful_process(self):
        self.assertEqual(wait_for_client(self.process('pass'), self.crashes, {}, timeout=3, poll_interval=.01), 0)

    def test_nonzero_exit(self):
        with self.assertRaisesRegex(ClientFailure, 'code 7'):
            wait_for_client(self.process('raise SystemExit(7)'), self.crashes, {}, timeout=3, poll_interval=.01)

    def test_alive_error_screen_detected(self):
        path = self.crashes / 'crash-fml.txt'
        p = self.process(f"from pathlib import Path; import time; Path({str(path)!r}).write_text('Failure message: registration broken'); time.sleep(60)")
        start = time.monotonic()
        with self.assertRaisesRegex(ClientFailure, 'registration broken'):
            wait_for_client(p, self.crashes, {}, timeout=4, poll_interval=.01)
        self.assertIsNone(p.poll())  # This is the formerly 26-minute alive-error-UI case.
        self.assertLess(time.monotonic() - start, 3)

    def test_stale_crash_ignored(self):
        (self.crashes / 'old.txt').write_text('Old failure')
        self.assertEqual(wait_for_client(self.process('pass'), self.crashes, crash_snapshot(self.crashes), timeout=3, poll_interval=.01), 0)

    def test_rewritten_crash_detected(self):
        path = self.crashes / 'old.txt'
        path.write_text('Old failure')
        baseline = crash_snapshot(self.crashes)
        path.write_text('Failure message: new failure at reused path')
        with self.assertRaisesRegex(ClientFailure, 'new failure'):
            wait_for_client(self.process('import time; time.sleep(60)'), self.crashes, baseline, timeout=3, poll_interval=.01)

    def test_timeout_is_bounded_and_concise(self):
        with self.assertRaisesRegex(TimeoutError, 'exceeded 0.05s'):
            wait_for_client(self.process('import time; time.sleep(60)'), self.crashes, {}, timeout=.05, poll_interval=.01)

    def test_invalid_timeout(self):
        with self.assertRaises(ValueError):
            wait_for_client(self.process('pass'), self.crashes, {}, timeout=0)

    def test_object_subscribers_are_instance_methods(self):
        text = (ROOT / 'src/main/kotlin/mods/eln/devtest/NativeCampaignClient.kt').read_text()
        self.assertNotIn('@JvmStatic', text)
        for name in ('serverPre', 'serverPost', 'frame', 'tick'):
            self.assertIn(f'@SubscribeEvent fun {name}(', text)
        # This is only a source regression. CI also executes the real runData loader.

    def apt(self, packages, exit_code=0, source_exists=True):
        binpath = self.root / 'bin'
        binpath.mkdir(exist_ok=True)
        source = self.root / 'ubuntu.sources'
        if source_exists:
            source.write_text('Types: deb\nURIs: http://archive.ubuntu.com/ubuntu\nSuites: noble\nComponents: main\nSigned-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg\n')
        output = self.root / 'calls.jsonl'
        sudo = binpath / 'sudo'
        sudo.write_text('#!' + sys.executable + '\nimport json,os,sys\nwith open(os.environ["CALLS"],"a") as f: f.write(json.dumps(sys.argv[1:])+"\\n")\nraise SystemExit(int(os.environ["FAKE_EXIT"]))\n')
        sudo.chmod(0o755)
        env = {**os.environ, 'PATH': str(binpath) + os.pathsep + os.environ['PATH'],
               'ELN_CI_APT_SOURCE_FILE': str(source), 'CALLS': str(output), 'FAKE_EXIT': str(exit_code)}
        result = subprocess.run(['bash', str(ROOT / 'script/install_ci_packages.sh'), *packages], env=env, capture_output=True, text=True)
        calls = [json.loads(line) for line in output.read_text().splitlines()] if output.exists() else []
        return result.returncode, calls

    def test_apt_scopes_update_and_install_without_disabling_validation(self):
        code, calls = self.apt(['xvfb', 'libgl1-mesa-dri'])
        self.assertEqual(code, 0)
        self.assertEqual(len(calls), 2)
        for call in calls:
            self.assertIn('Dir::Etc::sourceparts=-', call)
            self.assertIn('Acquire::Retries=3', call)
            self.assertIn('APT::Update::Error-Mode=any', call)
            self.assertTrue(any(a.startswith('Dir::Etc::sourcelist=/') for a in call))
            self.assertNotIn('--allow-unauthenticated', call)
        self.assertEqual(calls[0][-1], 'update')
        self.assertEqual(calls[1][-2:], ['xvfb', 'libgl1-mesa-dri'])

    def test_apt_update_failure_is_not_ignored(self):
        code, calls = self.apt(['xvfb'], exit_code=100)
        self.assertEqual(code, 100)
        self.assertEqual(len(calls), 1)

    def test_missing_source_fails_closed(self):
        code, calls = self.apt(['xvfb'], source_exists=False)
        self.assertEqual(code, 2)
        self.assertEqual(calls, [])

    def test_invalid_package_cannot_inject_apt_options(self):
        code, calls = self.apt(['--allow-unauthenticated'])
        self.assertEqual(code, 2)
        self.assertEqual(calls, [])

    def test_empty_package_list_fails(self):
        code, calls = self.apt([])
        self.assertEqual(code, 2)
        self.assertEqual(calls, [])


if __name__ == '__main__':
    unittest.main(verbosity=2)
