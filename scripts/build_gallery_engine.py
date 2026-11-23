#!/usr/bin/env python3
"""Build a reproducible gallery-dl zipapp from a pinned pip target directory.

Install the versions documented in docs/PHOTO_ENGINE.md into the target first.
"""
import pathlib
import sys
import zipfile

MODULES = ("gallery_dl", "requests", "urllib3", "certifi", "charset_normalizer", "idna")


def main():
    if len(sys.argv) != 3:
        raise SystemExit("usage: build_gallery_engine.py PIP_TARGET OUTPUT")
    site = pathlib.Path(sys.argv[1])
    output = pathlib.Path(sys.argv[2])
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        def add(name, content):
            info = zipfile.ZipInfo(name, (2020, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            archive.writestr(info, content)

        add("__main__.py", b"import gallery_dl\nraise SystemExit(gallery_dl.main())\n")
        for path in sorted(site.rglob("*")):
            relative = path.relative_to(site)
            root = relative.parts[0]
            included = root in MODULES or any(root.startswith(name + "-") and root.endswith(".dist-info") for name in MODULES)
            if path.is_file() and included and "__pycache__" not in relative.parts and path.suffix not in (".so", ".pyc"):
                add(relative.as_posix(), path.read_bytes())


if __name__ == "__main__":
    main()
