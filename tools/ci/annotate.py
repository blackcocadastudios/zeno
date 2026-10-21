#!/usr/bin/env python3
"""Prints a file as a GitHub Actions notice, so its content shows on the run's
page and in the check run's annotations (readable through the API without
downloading artifacts).

usage: annotate.py <title> <file>
"""
import sys

LIMIT = 60_000  # GitHub truncates longer annotation messages.


def main():
    title, path = sys.argv[1], sys.argv[2]
    try:
        text = open(path, encoding="utf-8", errors="replace").read()
    except OSError:
        text = f"{path} was not written."
    text = text[:LIMIT]
    escaped = text.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
    title = title.replace("%", "%25").replace(",", "%2C").replace(":", "%3A")
    print(f"::notice title={title}::{escaped}")


if __name__ == "__main__":
    main()
