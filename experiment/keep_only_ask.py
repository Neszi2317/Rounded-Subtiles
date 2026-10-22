#!/usr/bin/env python3
"""Keep only Ask in Morphe's public patch list, preserving dependency execution."""
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])
patch_dir = root / "patches/src/main/kotlin"
target = patch_dir / "app/morphe/patches/youtube/interaction/ask/AskFlyoutMenuPatch.kt"
assert target.is_file(), "AskFlyoutMenuPatch.kt not present"
start_re = re.compile(r"\b(?:bytecodePatch|rawResourcePatch|resourcePatch)\s*\(\s*name\s*=\s*")

def argument_end(source, start):
    depth = []
    quote = None
    escaped = False
    i = start
    while i < len(source):
        ch = source[i]
        if quote:
            if quote == '"""':
                if source.startswith('"""', i):
                    quote = None
                    i += 3
                    continue
            elif escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == quote:
                quote = None
            i += 1
            continue
        if source.startswith('"""', i):
            quote = '"""'
            i += 3
            continue
        if ch in ("'", '"'):
            quote = ch
            i += 1
            continue
        if source.startswith("//", i):
            endline = source.find("\n", i)
            i = len(source) if endline < 0 else endline + 1
            continue
        if source.startswith("/*", i):
            endcomment = source.find("*/", i + 2)
            i = len(source) if endcomment < 0 else endcomment + 2
            continue
        if ch in "([{":
            depth.append(ch)
        elif ch in ")]}":
            if not depth:
                raise ValueError("Unexpected closing delimiter in name argument")
            depth.pop()
        elif ch == "," and not depth:
            return i
        i += 1
    raise ValueError("Cannot parse name argument to its comma")

changed = []
for path in patch_dir.rglob("*.kt"):
    if path == target:
        continue
    source = path.read_text()
    for match in reversed(list(start_re.finditer(source))):
        arg_start = match.end()
        arg_end = argument_end(source, arg_start)
        before = source[arg_start:arg_end].strip()
        if before == "null":
            continue
        source = source[:arg_start] + "null" + source[arg_end:]
        changed.append((str(path.relative_to(patch_dir)), before[:80]))
    if any(row[0] == str(path.relative_to(patch_dir)) for row in changed):
        path.write_text(source)

print(f"Masked {len(changed)} named unrelated patch declarations")
print(f"Target kept: {target.name}")
if len(changed) < 75:
    raise RuntimeError("Unexpectedly few named patches, refusing to produce misleading isolated MPP")
