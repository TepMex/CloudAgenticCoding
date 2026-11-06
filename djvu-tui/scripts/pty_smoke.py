#!/usr/bin/env python3
"""Local headless terminal QA; Python standard library only.

Usage: python3 scripts/pty_smoke.py target/release/djvu-tui target/fixtures
Run make_fixtures first. No GUI, installed DjVu decoder, or font is used.
"""
import codecs
import fcntl
import os
from pathlib import Path
import re
import select
import signal
import struct
import subprocess
import sys
import termios
import tempfile
import time


class Screen:
    """The small VT subset emitted by Crossterm (all tested glyphs are width 1)."""
    def __init__(self, cols=160, rows=50):
        self.resize(cols, rows)
        self.decoder = codecs.getincrementaldecoder("utf-8")("replace")
        self.pending = ""
        self.last_char = " "

    def resize(self, cols, rows):
        self.cols, self.rows = cols, rows
        self.cells = [[" "] * cols for _ in range(rows)]
        self.x = self.y = 0

    def feed(self, data):
        self.pending += self.decoder.decode(data)
        while self.pending:
            if self.pending.startswith("\x1b["):
                match = re.match(r"\x1b\[([0-?]*)([ -/]*)([@-~])", self.pending)
                if not match:
                    return
                params, _, command = match.groups()
                numeric = params.lstrip("?<>=")
                numbers = [int(p or "0") for p in numeric.split(";")] if re.fullmatch(r"[0-9;]*", numeric) else [0]
                n = numbers[0] or 1
                if command in "Hf":
                    self.y = n - 1
                    self.x = (numbers[1] or 1) - 1 if len(numbers) > 1 else 0
                elif command == "G":
                    self.x = n - 1
                elif command == "d":
                    self.y = n - 1
                elif command in "ABCD":
                    if command == "A": self.y -= n
                    if command == "B": self.y += n
                    if command == "C": self.x += n
                    if command == "D": self.x -= n
                elif command == "J" and numbers[0] in (2, 3):
                    self.cells = [[" "] * self.cols for _ in range(self.rows)]
                elif command == "K" and 0 <= self.y < self.rows:
                    start = 0 if numbers[0] in (1, 2) else max(0, self.x)
                    end = self.cols if numbers[0] != 1 else min(self.cols, self.x + 1)
                    self.cells[self.y][start:end] = [" "] * (end - start)
                elif command == "X" and 0 <= self.y < self.rows:
                    end = min(self.cols, self.x + n)
                    self.cells[self.y][self.x:end] = [" "] * (end - self.x)
                elif command == "b":
                    for _ in range(n):
                        if 0 <= self.y < self.rows and 0 <= self.x < self.cols:
                            self.cells[self.y][self.x] = self.last_char
                        self.x += 1
                self.pending = self.pending[match.end():]
                continue
            if self.pending.startswith("\x1b"):
                if self.pending.startswith("\x1b]"):
                    end = re.search(r"\x07|\x1b\\", self.pending)
                    if not end:
                        return
                    self.pending = self.pending[end.end():]
                    continue
                if len(self.pending) >= 2 and self.pending[1] in "()*+":
                    if len(self.pending) < 3:
                        return
                    self.pending = self.pending[3:]
                    continue
                if len(self.pending) < 2:
                    return
                self.pending = self.pending[2:]
                continue
            ch, self.pending = self.pending[0], self.pending[1:]
            if ch == "\r": self.x = 0
            elif ch == "\n": self.y = min(self.rows - 1, self.y + 1)
            elif ch >= " ":
                if self.x >= self.cols:
                    self.x = 0
                    self.y = min(self.rows - 1, self.y + 1)
                if 0 <= self.x < self.cols and 0 <= self.y < self.rows:
                    self.cells[self.y][self.x] = ch
                self.last_char = ch
                self.x += 1

    def text(self):
        return "\n".join("".join(row) for row in self.cells)


class Session:
    def __init__(self, binary, path, cols=160, rows=50):
        self.master, self.slave = os.openpty()
        self.screen = Screen(cols, rows)
        self.resize(cols, rows, notify=False)
        self.raw = bytearray()
        env = dict(os.environ, TERM="xterm-256color", LANG="en_US.UTF-8", DJVU_TUI_TIMINGS="1")

        def controlling_terminal():
            os.setsid()
            fcntl.ioctl(self.slave, termios.TIOCSCTTY, 0)

        notify_read, notify_write = os.pipe()
        # Keep the session leader alive until after checking termios. macOS
        # revokes the slave when its session leader exits, even if we hold it.
        wrapper = """
import os, subprocess, sys, termios
original = termios.tcgetattr(0)
child = subprocess.Popen(sys.argv[2:])
os.write(int(sys.argv[1]), str(child.pid).encode())
os.close(int(sys.argv[1]))
code = child.wait()
restored = termios.tcgetattr(0) == original
print(f'\\r\\nPTY_EXIT={code} PTY_RESTORED={int(restored)}', flush=True)
sys.exit(0 if code == 0 and restored else 1)
"""
        self.process = subprocess.Popen([sys.executable, "-c", wrapper, str(notify_write), str(binary), str(path)], stdin=self.slave,
                                        stdout=self.slave, stderr=self.slave, env=env,
                                        preexec_fn=controlling_terminal, pass_fds=(notify_write,))
        os.close(notify_write)
        self.child_pid = int(os.read(notify_read, 64))
        os.close(notify_read)

    def resize(self, cols, rows, notify=True):
        fcntl.ioctl(self.slave, termios.TIOCSWINSZ, struct.pack("HHHH", rows, cols, 0, 0))
        self.screen.resize(cols, rows)
        if notify:
            os.kill(self.child_pid, signal.SIGWINCH)

    def pump(self, duration=0.1):
        deadline = time.monotonic() + duration
        while time.monotonic() < deadline:
            ready, _, _ = select.select([self.master], [], [], min(0.01, max(0, deadline - time.monotonic())))
            if ready:
                data = os.read(self.master, 65536)
                self.raw.extend(data)
                self.screen.feed(data)

    def wait(self, predicate, timeout=5):
        start = time.monotonic()
        while time.monotonic() - start < timeout:
            self.pump(0.01)
            if predicate(self.screen.text()):
                return (time.monotonic() - start) * 1000
        raise AssertionError("Timed out waiting for screen:\n" + self.screen.text())

    def key(self, data):
        os.write(self.master, data)
        self.pump()

    def close(self, key=b"q", sig=None):
        try:
            if sig:
                os.kill(self.child_pid, sig)
            else:
                os.write(self.master, key)
            deadline = time.monotonic() + 5
            while self.process.poll() is None and time.monotonic() < deadline:
                self.pump(0.02)
            self.pump()
            assert self.process.poll() == 0, "Viewer failed to exit cleanly: " + self.raw.decode(errors="replace")[-1500:]
            assert b"PTY_EXIT=0 PTY_RESTORED=1" in self.raw, "Terminal attributes were not restored"
            assert b"\x1b[?1049l" in self.raw, "Alternate screen was not left"
            assert b"\x1b[?25h" in self.raw, "Cursor was not restored"
        finally:
            if self.process.poll() is None:
                os.killpg(self.process.pid, signal.SIGKILL)
                self.process.wait()
            os.close(self.master)
            os.close(self.slave)


def main():
    binary, fixtures = (Path(p).resolve() for p in sys.argv[1:3])
    book = Session(binary, fixtures / "shared.djvu")
    try:
        first_ms = book.wait(lambda s: "ready" in s and "1/6" in s)
        assert any("\u2801" <= c <= "\u28ff" for c in book.screen.text())
        book.key(b" ")
        book.wait(lambda s: "2–3/6" in s and "ready" in s)
        book.key(b"g3\r++")
        book.wait(lambda s: "3/6" in s and "156%" in s and "ready" in s)
        book.key(b"\x1b[B\x1b[Crd")
        book.wait(lambda s: "braille+fill+dither" in s and "ready" in s)
        book.key(b"r")
        book.wait(lambda s: "blocks+dither" in s and "ready" in s)
        book.key(b"r")
        book.wait(lambda s: "mosaic (experimental)+dither" in s and "ready" in s)
        book.key(b"r")
        book.wait(lambda s: "braille+dither" in s and "ready" in s)
        book.key(b"0")
        book.resize(60, 24)
        book.wait(lambda s: "3/6" in s and "ready" in s)
        book.resize(160, 50)
        book.wait(lambda s: "2–3/6" in s and "ready" in s)
        book.key(b"?")
        book.wait(lambda s: "Reading" in s)
        book.key(b"?o")
        book.wait(lambda s: "bilevel.djvu" in s and "Enter open" in s)
        # Several opens/changes/resizes exercise replacement of in-flight work.
        book.key(b"\r++ro\x1b[B\r++r")
        book.resize(90, 30)
        book.wait(lambda s: "ready" in s and "colour.djvu" in s)
        book.key(b"o")
        book.wait(lambda s: "Enter open" in s)
        book.key(b"\x1b[H\r")
        book.wait(lambda s: "ready" in s and "bilevel.djvu" in s)
        # A transport may own its own status row (tmux includes a clock).
        trim = 2 if os.environ.get("DJVU_TUI_TRANSPORT") else 1
        before = book.screen.text().splitlines()[:-trim]
        book.key(b"i")
        after = book.screen.text().splitlines()[:-trim]
        assert after == before, "Polarity changed content: " + repr([(i, a, b) for i, (a, b) in enumerate(zip(before, after)) if a != b][:3])
        book.resize(1, 1)
        book.pump()
        book.resize(100, 40)
        book.wait(lambda s: "ready" in s)
    finally:
        book.close()
    print(f"PASS navigation, rendering, resize, replacement, polarity, tiny terminal; first screen {first_ms:.1f} ms")

    for name, text in [("indirect.djvu", "Indirect DjVu"), ("missing.djvu", "No such file")]:
        session = Session(binary, fixtures / name)
        try:
            session.wait(lambda s: text in s)
            session.key(b"o\x1b[H\r")
            session.wait(lambda s: "ready" in s and "bilevel.djvu" in s)
        finally:
            session.close()
    print("PASS error messages and recovery")

    with tempfile.TemporaryDirectory(prefix="djvu-tui-qa-") as directory:
        directory = Path(directory)
        broken = directory / "broken.djvu"
        broken.write_bytes(b"not a djvu file")
        session = Session(binary, broken)
        try:
            session.wait(lambda s: "Cannot open DjVu" in s)
        finally:
            session.close()
        if os.geteuid() != 0:
            denied = directory / "denied"
            denied.mkdir(mode=0)
            try:
                session = Session(binary, denied)
                try:
                    session.wait(lambda s: "Permission denied" in s)
                finally:
                    session.close()
            finally:
                denied.chmod(0o700)
    print("PASS malformed file and permission errors (permission case requires non-root)")

    exits = [(b"\x03", None)] if os.environ.get("DJVU_TUI_TRANSPORT") else [(b"\x03", None), (None, signal.SIGINT), (None, signal.SIGTERM)]
    for key, sig in exits:
        session = Session(binary, fixtures / "large.djvu")
        if os.environ.get("DJVU_TUI_TRANSPORT"):
            # SSH needs time to connect and put the local terminal into raw
            # mode; before that, Ctrl-C interrupts the SSH connection itself.
            session.wait(lambda s: "ready" in s)
        else:
            # A signal before main() runs would test the OS default handler,
            # not restoration of the application's raw terminal session.
            session.wait(lambda s: "loading" in s or "Loading" in s or "ready" in s)
        session.close(key=key, sig=sig)
    print("PASS exit and terminal restoration: " + ("Ctrl-C over transport" if os.environ.get("DJVU_TUI_TRANSPORT") else "Ctrl-C, SIGINT, SIGTERM"))


if __name__ == "__main__":
    main()
