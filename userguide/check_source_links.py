#!/usr/bin/env python3
"""
check_source_links.py
---------------------
Scans every .rst file under userguide/source/ and verifies that every
GitHub source-file link of the form

    https://github.com/aia-uclouvain/maxicp/blob/main/<path>

actually resolves to an existing file inside the repository root.

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

# Prefix that all source-file links must share
GITHUB_BLOB_PREFIX = "https://github.com/aia-uclouvain/maxicp/blob/main/"

# Regex: captures the path portion after the prefix inside RST link syntax
LINK_RE = re.compile(
    re.escape(GITHUB_BLOB_PREFIX) + r"([^\s>`'\"]+)"
)

# --- helpers -----------------------------------------------------------------

def iter_rst_files(root):
    for dirpath, _dirs, filenames in os.walk(root):
        for fname in filenames:
            if fname.endswith(".rst"):
                yield os.path.join(dirpath, fname)


def check_links(rst_file):
    """Return a list of (line_no, path, full_url) tuples for broken links."""
    broken = []
    with open(rst_file, encoding="utf-8") as fh:
        for lineno, line in enumerate(fh, start=1):
            for m in LINK_RE.finditer(line):
                rel_path = m.group(1)
                # Remove any trailing punctuation that might follow the URL
                rel_path = rel_path.rstrip(")>'\"`.,;")
                abs_path = os.path.join(REPO_ROOT, rel_path)
                if not os.path.isfile(abs_path):
                    broken.append((lineno, rel_path, GITHUB_BLOB_PREFIX + rel_path))
    return broken

# --- main --------------------------------------------------------------------

def main():
    all_broken = []

    for rst_file in sorted(iter_rst_files(RST_ROOT)):
        broken = check_links(rst_file)
        if broken:
            rel_rst = os.path.relpath(rst_file, REPO_ROOT)
            for lineno, path, url in broken:
                all_broken.append((rel_rst, lineno, path, url))

    if all_broken:
        print(f"ERROR: {len(all_broken)} broken source link(s) found:\n")
        for rst, lineno, path, url in all_broken:
            print(f"  {rst}:{lineno}")
            print(f"    URL  : {url}")
            print(f"    File : {path}  (not found in repo)")
            print()
        sys.exit(1)
    else:
        n = sum(
            len(LINK_RE.findall(open(f, encoding="utf-8").read()))
            for f in iter_rst_files(RST_ROOT)
        )
        print(f"OK – all {n} source link(s) are valid.")
        sys.exit(0)


if __name__ == "__main__":
    main()

