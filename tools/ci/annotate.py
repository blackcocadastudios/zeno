#!/usr/bin/env python3
"""Prints a file as GitHub Actions notices, so its content shows on the run's
page and in the check run's annotations (readable through the API without
downloading artifacts). GitHub cuts a message at 4 KB, so longer files go out
in numbered parts, split between lines.

usage: annotate.py <title> <file>
"""
import sys

PART_BYTES = 3_500
MAX_PARTS = 8  # GitHub shows up to 10 notices per step.


def parts(text, size=PART_BYTES):
    """Splits text into pieces of at most `size` UTF-8 bytes, between lines when possible."""
    chunk, used = [], 0
    for line in text.splitlines(keepends=True):
        while len(line.encode()) > size:  # a single very long line
            cut = len(line.encode()[:size].decode(errors="ignore"))
            if chunk:
                yield "".join(chunk)
                chunk, used = [], 0
            yield line[:cut]
            line = line[cut:]
        n = len(line.encode())
        if used + n > size and chunk:
            yield "".join(chunk)
            chunk, used = [], 0
        chunk.append(line)
        used += n
    if chunk:
        yield "".join(chunk)


def notice(title, message, file=None):
    escaped = message.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
    title = title.replace("%", "%25").replace(",", "%2C").replace(":", "%3A")
    where = f" file={file}," if file else " "
    print(f"::notice{where}title={title}::{escaped}")


def annotate(title, text, file=None):
    pieces = list(parts(text))[:MAX_PARTS]
    for i, piece in enumerate(pieces, 1):
        notice(title if len(pieces) == 1 else f"{title} ({i}/{len(pieces)})", piece, file)


def main():
    title, path = sys.argv[1], sys.argv[2]
    try:
        text = open(path, encoding="utf-8", errors="replace").read()
    except OSError:
        text = f"{path} was not written."
    annotate(title, text)


if __name__ == "__main__":
    main()
