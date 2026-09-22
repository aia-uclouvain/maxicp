#!/usr/bin/env python3
"""
check_source_links.py
---------------------
Scans every .rst file under userguide/source/ and verifies that every
GitHub source link of the form

    https://github.com/aia-uclouvain/maxicp/blob/main/<path>   (file)
    https://github.com/aia-uclouvain/maxicp/tree/main/<path>   (directory)

actually resolves to an existing file or directory inside the repository root.

Usage (run from repo root):
    python userguide/check_source_links.py

Exit code:
    0  – all links are valid
    1  – one or more links are broken
"""

import os
import re
import sys

# --- configuration -----------------------------------------------------------

# Repository root = directory that contains this script's parent (userguide/)
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT   = os.path.dirname(SCRIPT_DIR)

# Directory tree to scan for .rst files
RST_ROOT = os.path.join(SCRIPT_DIR, "source")

GITHUB_BASE = "https://github.com/aia-uclouvain/maxicp/"

# Matches both /blob/main/<path> and /tree/main/<path>
LINK_RE = re.compile(
    re.escape(GITHUB_BASE) + r"(blob|tree)/main/([^\s>`'\"]+)"
)

# --- helpers -----------------------------------------------------------------

def iter_rst_files(root):
    for dirpath, _dirs, filenames in os.walk(root):
        for fname in filenames:
            if fname.endswith(".rst"):
                yield os.path.join(dirpath, fname)


def check_links(rst_file):
    """Return a list of (line_no, kind, rel_path, full_url) for broken links."""
    broken = []
    with open(rst_file, encoding="utf-8") as fh:
        for lineno, line in enumerate(fh, start=1):
            for m in LINK_RE.finditer(line):
                kind     = m.group(1)          # "blob" or "tree"
                rel_path = m.group(2).rstrip(")>`'\"`.,;")
                abs_path = os.path.join(REPO_ROOT, rel_path)
                full_url = GITHUB_BASE + kind + "/main/" + rel_path
                if kind == "blob" and not os.path.isfile(abs_path):
                    broken.append((lineno, "file", rel_path, full_url))
                elif kind == "tree" and not os.path.isdir(abs_path):
                    broken.append((lineno, "dir ", rel_path, full_url))
    return broken

# --- main --------------------------------------------------------------------

def main():
    all_broken = []
    total      = 0

    for rst_file in sorted(iter_rst_files(RST_ROOT)):
        broken = check_links(rst_file)
        rel_rst = os.path.relpath(rst_file, REPO_ROOT)
        with open(rst_file, encoding="utf-8") as fh:
            total += len(LINK_RE.findall(fh.read()))
        for lineno, kind, path, url in broken:
            all_broken.append((rel_rst, lineno, kind, path, url))

    if all_broken:
        print(f"ERROR: {len(all_broken)} broken source link(s) found:\n")
        for rst, lineno, kind, path, url in all_broken:
            print(f"  {rst}:{lineno}")
            print(f"    URL  : {url}")
            print(f"    {kind} : {path}  (not found in repo)")
            print()
        sys.exit(1)
    else:
        print(f"OK – all {total} source link(s) are valid.")
        sys.exit(0)


if __name__ == "__main__":
    main()

