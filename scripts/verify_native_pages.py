#!/usr/bin/env python3
"""Reject Android 64-bit ELF load segments incompatible with 16 KiB pages."""

import io
import struct
import sys
import zipfile


def check_elf(data, path):
    if data[:4] != b"\x7fELF":
        return []
    if data[4] != 2 or data[5] != 1:
        raise ValueError(f"{path}: se esperaba un ELF de 64 bits little-endian")
    table = struct.unpack_from("<Q", data, 32)[0]
    entry_size, entries = struct.unpack_from("<HH", data, 54)
    errors = []
    for index in range(entries):
        kind, _, offset, address, _, _, _, alignment = struct.unpack_from(
            "<IIQQQQQQ", data, table + index * entry_size
        )
        if kind == 1 and (alignment < 16384 or (offset - address) % 16384):
            errors.append(f"{path}: segmento LOAD {index} incompatible (0x{alignment:x})")
    return errors


def check_archive(archive, label, inside_abi=False):
    errors = []
    for name in archive.namelist():
        if not inside_abi and not name.startswith(("lib/arm64-v8a/", "lib/x86_64/")):
            continue
        if not name.endswith(".so"):
            continue
        data = archive.read(name)
        path = f"{label}!/{name}"
        if data.startswith(b"PK\x03\x04"):
            with zipfile.ZipFile(io.BytesIO(data)) as nested:
                errors.extend(check_archive(nested, path, inside_abi=True))
        else:
            errors.extend(check_elf(data, path))
    return errors


def main():
    errors = []
    for apk in sys.argv[1:]:
        with zipfile.ZipFile(apk) as archive:
            errors.extend(check_archive(archive, apk))
    for error in errors:
        print(error, file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
