from pathlib import Path
files = [
    Path("patches/src/main/kotlin/patches/youtube/captions/RoundedCaptions2120Patch.kt"),
    Path("patches/src/main/kotlin/patches/youtube/captions/RoundedCaptionsPatch.kt")
]
for path in files:
    source = path.read_text()
    lines = source.splitlines(keepends=True)
    positions = [i for i,line in enumerate(lines) if 'name = "Rounded YouTube Captions' in line]
    assert len(positions) == 1, (path, positions)
    lines[positions[0]] = '    name = null,\n'
    path.write_text(''.join(lines))
print("Only the standalone Ask add-on is public; unrelated captions hidden.")
