#!/usr/bin/env python3
"""Smoke-test helper: finds an on-screen element by its text or accessibility
description (UI Automator dump) and taps it, or waits for it.

usage: ui.py tap|wait|gone <regex> [timeout-seconds]
       ui.py texts
Exit code 1 when the element does not show up (or, for `gone`, does not leave).
A tap waits until the element is in the same place in two dumps in a row, so a
list still moving after a swipe is not tapped in the wrong place. `texts`
prints what the screen shows, for the report of a failed step.
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


SYSTEM_DIALOG = re.compile(r"isn't responding|não está respondendo|keeps stopping", re.I)


def dismiss_system_dialogs(root):
    """The CI emulator's own apps (the launcher) sometimes freeze and Android
    shows an "isn't responding" dialog over everything: answer "Wait"."""
    if not any(SYSTEM_DIALOG.search(n.get("text") or "") for n in root.iter("node")):
        return False
    for node in root.iter("node"):
        if re.fullmatch(r"Wait|Aguardar", node.get("text") or "", re.I):
            m = BOUNDS.match(node.get("bounds", ""))
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                subprocess.run(["adb", "shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2)], timeout=30)
                print("dismissed a system 'not responding' dialog")
                time.sleep(1)
                return True
    return False


def find(pattern):
    root = dump()
    if root is None:
        return None
    if dismiss_system_dialogs(root):
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


def texts():
    root = dump()
    if root is None:
        return "(no UI dump)"
    seen = []
    for node in root.iter("node"):
        for value in (node.get("text"), node.get("content-desc")):
            value = " ".join((value or "").split())
            if value and value not in seen:
                seen.append(value)
    return " | ".join(seen)


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "texts":
        try:
            print(texts())
        except subprocess.TimeoutExpired:
            print("(no UI dump)")
        return
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(2)
    cmd, pattern = sys.argv[1], sys.argv[2]
    timeout = float(sys.argv[3]) if len(sys.argv) > 3 else 10
    deadline = time.time() + timeout
    last = None
    while True:
        try:
            pos = find(pattern)
        except subprocess.TimeoutExpired:
            pos = None
        if cmd == "gone":
            if pos is None:
                print(f"gone: {pattern}")
                return
        elif pos is not None and (cmd != "tap" or pos == last):
            break
        if time.time() > deadline:
            if cmd == "tap" and pos is not None:
                break  # still moving at the deadline: tap where it is now
            print(f"{cmd}: timed out waiting for {pattern!r}")
            sys.exit(1)
        if pos is None:
            time.sleep(1)
        last = pos
    if cmd == "tap":
        subprocess.run(["adb", "shell", "input", "tap", str(pos[0]), str(pos[1])], timeout=30)
    print(f"{cmd}: {pattern!r} at {pos}")


if __name__ == "__main__":
    main()
