#!/usr/bin/env python3
"""Writes the LaTeX sources of the workflow documentation set (edition 5.4.0) next to this folder.

Usage: python3 generator/generate.py [--only NAME-SUBSTRING]
The generated .tex files are the documents of record; regenerate after changing the service contracts, then run
./build-docs.sh. Hand edits to generated .tex files are overwritten by the next generation."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import render_services  # noqa: E402

OUT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def all_files():
    out = {}
    out.update(render_services.files())
    try:
        import render_systems
        out.update(render_systems.files())
    except ImportError:
        pass
    try:
        import render_sos
        out.update(render_sos.files())
    except ImportError:
        pass
    return out


def main():
    only = sys.argv[2] if len(sys.argv) > 2 and sys.argv[1] == "--only" else None
    for name, text in sorted(all_files().items()):
        if only and only not in name:
            continue
        bad = [c for c in text if ord(c) > 127]
        if bad:
            raise SystemExit("%s contains non-ASCII characters: %r" % (name, sorted(set(bad))))
        with open(os.path.join(OUT, name), "w") as f:
            f.write(text)
        print(name)


if __name__ == "__main__":
    main()
