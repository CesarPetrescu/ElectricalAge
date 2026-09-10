"""Bounded supervision of the real client; never retry a failed gameplay run."""
from __future__ import annotations

import subprocess
import time
from pathlib import Path
from typing import Mapping


class ClientFailure(RuntimeError):
    """An observed crash or non-zero exit, not a missing test result."""


def crash_snapshot(directory: Path) -> dict[Path, tuple[int, int]]:
    return {p: (p.stat().st_mtime_ns, p.stat().st_size)
            for p in directory.glob('*.txt') if p.is_file()}


def crash_reason(path: Path) -> str:
    # Keep the original file separately; do not dump the entire launch command.
    text = path.read_text(errors='replace')
    lines = text.splitlines()
    for index, line in enumerate(lines):
        if 'Failure message:' in line or 'Expected @SubscribeEvent' in line:
            return '\n'.join(lines[index:index + 5]).strip()[:1600]
    return '\n'.join(lines[:18])[:1600]


def wait_for_client(process: subprocess.Popen, crash_directory: Path,
                    baseline: Mapping[Path, tuple[int, int]], *,
                    timeout: float = 1600, poll_interval: float = 0.5) -> int:
    """Fail promptly on a newly written crash, including an alive FML error UI.

    Reports left by an earlier phase are ignored unless changed. The caller owns
    process termination and evidence collection, including a failure screenshot.
    """
    if timeout <= 0 or poll_interval <= 0:
        raise ValueError('Timeout and polling interval must be positive')
    deadline = time.monotonic() + timeout
    while True:
        for path, signature in crash_snapshot(crash_directory).items():
            if baseline.get(path) != signature and signature[1] > 0:
                raise ClientFailure(f'Minecraft crash report {path.name}:\n{crash_reason(path)}')
        code = process.poll()
        if code is not None:
            if code != 0:
                raise ClientFailure(f'Client exited with code {code}; see client.log')
            return code
        if time.monotonic() >= deadline:
            raise TimeoutError(f'Client exceeded {timeout:g}s without completing; pid={process.pid}')
        time.sleep(min(poll_interval, max(0, deadline - time.monotonic())))
