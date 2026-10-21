#!/usr/bin/env python3
"""Smoke-test helper: finds an on-screen element by its text or accessibility
description (UI Automator dump) and taps it, or waits for it.

usage: ui.py tap|wait|gone <regex> [timeout-seconds]
Exit code 1 when the element does not show up (or, for `gone`, does not leave).
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def dump():
    subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/zeno-ui.xml"], capture_output=True, timeout=30)
    raw = subprocess.run(["adb", "exec-out", "cat", "/sdcard/zeno-ui.xml"], capture_output=True, timeout=30).stdout
    try:
        return ET.fromstring(raw)
    except ET.ParseError:
        return None


def find(pattern):
    root = dump()
    if root is None:
        return None
    rx = re.compile(pattern, re.I)
    for node in root.iter("node"):
        if rx.search(node.get("text") or "") or rx.search(node.get("content-desc") or ""):
            m = BOUNDS.match(node.get("bounds", ""))
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                if x2 > x1 and y2 > y1:
                    return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(2)
    cmd, pattern = sys.argv[1], sys.argv[2]
    timeout = float(sys.argv[3]) if len(sys.argv) > 3 else 10
    deadline = time.time() + timeout
    while True:
        try:
            pos = find(pattern)
        except subprocess.TimeoutExpired:
            pos = None
        if cmd == "gone":
            if pos is None:
                print(f"gone: {pattern}")
                return
        elif pos is not None:
            break
        if time.time() > deadline:
            print(f"{cmd}: timed out waiting for {pattern!r}")
            sys.exit(1)
        time.sleep(1)
    if cmd == "tap":
        subprocess.run(["adb", "shell", "input", "tap", str(pos[0]), str(pos[1])], timeout=30)
    print(f"{cmd}: {pattern!r} at {pos}")


if __name__ == "__main__":
    main()
