#!/usr/bin/env python3
"""Serves the smoke test's pages (tools/ci/pages). Forms posted to a page get
that page back, so a sign-in form can be sent like on a real site.

usage: serve_pages.py <port> <directory>
"""
import functools
import http.server
import sys


class Handler(http.server.SimpleHTTPRequestHandler):
    def do_POST(self):
        self.rfile.read(int(self.headers.get("Content-Length", 0)))
        self.path = self.path.split("?", 1)[0]
        return self.do_GET()

    def log_message(self, format, *args):
        sys.stderr.write("%s %s\n" % (self.command, self.path))


def main():
    port, directory = int(sys.argv[1]), sys.argv[2]
    handler = functools.partial(Handler, directory=directory)
    http.server.ThreadingHTTPServer(("127.0.0.1", port), handler).serve_forever()


if __name__ == "__main__":
    main()
