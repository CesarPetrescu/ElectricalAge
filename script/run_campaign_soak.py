#!/usr/bin/env python3
"""Bounded real-time packaged multiplayer soak; no tick acceleration or production patches."""
import argparse
import json
import math
import os
from pathlib import Path
import time
import traceback

from run_multiplayer import Runner
from multiplayer_plan import plan, validate


def command(role, name, action, **kwargs):
    return dict(role=role, id=name, action=action, **kwargs)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('profile', choices=('standalone', 'create'))
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--seconds', type=int, default=1200)
    parser.add_argument('--interval', type=int, default=30)
    args = parser.parse_args()
    if os.environ.get('GITHUB_ACTIONS') != 'true' or not 60 <= args.seconds <= 14400 or not 5 <= args.interval <= 120:
        raise SystemExit('GitHub Linux runner and bounded positive timing required')
    runner = Runner(args)
    baseline = plan(args.profile)
    baseline_keys = {(c['role'], c['id']) for group in baseline for c in group}
    extra = []
    samples = []
    epochs = []
    elapsed = 0.0
    error = None

    def group(commands):
        extra.extend((c['role'], c['id']) for c in commands)
        runner.run_group(commands)

    def one(role, name, action, **kwargs):
        group([command(role, name, action, **kwargs)])

    def both(name, action, **kwargs):
        group([command(role, name, action, **kwargs) for role in ('alpha', 'beta')])

    def stats():
        result = {}
        for role, process in runner.processes.items():
            if process.poll() is not None:
                raise RuntimeError(f'{role} exited during soak: {process.returncode}')
            proc = Path('/proc') / str(process.pid)
            fields = {}
            for line in (proc / 'status').read_text().splitlines():
                if line.startswith(('VmRSS:', 'VmHWM:', 'Threads:')):
                    key, value = line.split(':', 1)
                    fields[key] = int(value.split()[0])
            fields['pid'] = process.pid
            fields['open_fds'] = len(list((proc / 'fd').iterdir()))
            result[role] = fields
        return result

    def soak():
        nonlocal elapsed
        start = time.monotonic()
        count = math.ceil(args.seconds / args.interval)
        for index in range(count + 1):
            target = start + min(index * args.interval, args.seconds)
            while time.monotonic() < target:
                stats()
                time.sleep(min(1.0, max(0.01, target - time.monotonic())))
            prefix = f'soak-{index:04d}'
            voltage = 12 if index % 2 == 0 else 24
            one('alpha', prefix + '-set', 'source-set', voltage=voltage)
            one('server', prefix + '-circuit', 'circuit', voltage=voltage)
            both(prefix + '-sync', 'source', voltage=voltage)
            both(prefix + '-monitor', 'monitor')
            samples.append({'seconds': time.monotonic() - start, 'voltage': voltage, 'processes': stats()})
            (runner.output / 'soak-samples.json').write_text(json.dumps(samples, indent=2))
        elapsed = time.monotonic() - start
        if elapsed < args.seconds:
            raise AssertionError('Real-time interval was not completed')
        one('alpha', 'soak-restore-source', 'source-set', voltage=24)
        one('server', 'soak-restore-circuit', 'circuit', voltage=24)
        both('soak-restore-sync', 'source', voltage=24)
        for epoch in (1, 2):
            prefix = f'extra-restart-{epoch}'
            both(prefix + '-disconnect', 'disconnect')
            one('server', prefix + '-stop', 'stop')
            old_pid = runner.processes['server'].pid
            runner.start('server')
            one('server', prefix + '-boot', 'boot')
            boot = runner.results[-1]
            if boot['pid'] == old_pid or boot['observation'].get('jarSha256') != runner.sha:
                raise AssertionError('Restart did not use a fresh JVM with the same mod JAR')
            epochs.append({'before': old_pid, 'after': boot['pid']})
            both(prefix + '-join', 'join')
            one('server', prefix + '-home', 'home')
            both(prefix + '-sync', 'source', voltage=24)
            one('server', prefix + '-circuit', 'circuit', voltage=24)
            both(prefix + '-monitor', 'monitor')

    try:
        runner.setup()
        for role in ('server', 'alpha', 'beta'):
            runner.start(role)
        for commands in baseline:
            if commands[0]['id'] == 'close-clients':
                soak()
            runner.run_group(commands)
        validate([r for r in runner.results if (r['role'], r['id']) in baseline_keys], args.profile, runner.sha)
        actual = {(r['role'], r['id']): r for r in runner.results if (r['role'], r['id']) not in baseline_keys}
        if len(extra) != len(set(extra)) or set(actual) != set(extra) or any(r['status'] != 'passed' for r in actual.values()):
            raise AssertionError('Missing, duplicate or failed extra contracts')
        if len(samples) != math.ceil(args.seconds / args.interval) + 1 or len(epochs) != 2:
            raise AssertionError('Incomplete soak or restart campaign')
    except Exception:
        error = traceback.format_exc()
        print(error, flush=True)
    finally:
        (runner.output / 'soak-summary.json').write_text(json.dumps({
            'complete': error is None, 'error': error, 'requested_real_seconds': args.seconds,
            'observed_real_seconds': elapsed, 'samples': len(samples), 'extra_contracts': len(extra),
            'additional_restarts': epochs, 'jar_sha256': runner.sha,
            'scope': 'Small source/cable/resistive-load and monitor fixture with two real clients; Create adapter also present in Create profile. Not a survival playthrough or full-factory capacity test.'
        }, indent=2))
        runner.finish(error)
    if error:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
