#!/usr/bin/env python3
"""Rebuild the five WebP libraries in the ARM64 FFmpeg payload for 16 KiB pages."""

import argparse
import hashlib
import io
import shutil
import subprocess
import tarfile
import tempfile
import urllib.request
import zipfile
from pathlib import Path

from verify_native_pages import check_archive

WEBP_VERSION = "1.6.0"
WEBP_URL = f"https://codeload.github.com/webmproject/libwebp/tar.gz/refs/tags/v{WEBP_VERSION}"
WEBP_SHA256 = "93a852c2b3efafee3723efd4636de855b46f9fe1efddd607e1f42f60fc8f2136"
ABI = "arm64-v8a"
PAYLOAD = "libffmpeg.zip.so"
LIBRARIES = ("libsharpyuv.so", "libwebp.so", "libwebpdecoder.so", "libwebpdemux.so", "libwebpmux.so")


def build_webp(source, build, ndk):
    disabled = ("ANIM_UTILS", "CWEBP", "DWEBP", "GIF2WEBP", "IMG2WEBP", "VWEBP", "WEBPINFO", "WEBPMUX", "EXTRAS")
    subprocess.run([
        "cmake", "-S", str(source), "-B", str(build), "-G", "Ninja",
        f"-DCMAKE_TOOLCHAIN_FILE={ndk / 'build/cmake/android.toolchain.cmake'}",
        f"-DANDROID_ABI={ABI}", "-DANDROID_PLATFORM=android-24",
        "-DCMAKE_BUILD_TYPE=Release", "-DBUILD_SHARED_LIBS=ON",
        "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384",
        "-DWEBP_ENABLE_SWAP_16BIT_CSP=ON",
        *(f"-DWEBP_BUILD_{tool}=OFF" for tool in disabled),
    ], check=True)
    subprocess.run(["cmake", "--build", str(build), "--parallel", "2"], check=True)


def replace_payload(original, build, output):
    replacements = {f"usr/lib/{name}": build / name for name in LIBRARIES}
    with zipfile.ZipFile(io.BytesIO(original)) as source, zipfile.ZipFile(output, "w") as target:
        if not replacements.keys() <= set(source.namelist()):
            raise ValueError("El AAR no contiene las cinco bibliotecas WebP esperadas")
        for entry in source.infolist():
            replacement = replacements.get(entry.filename)
            if replacement:
                target.writestr(entry, replacement.read_bytes())
            else:
                with source.open(entry) as incoming, target.open(entry, "w") as outgoing:
                    shutil.copyfileobj(incoming, outgoing)
    if output.stat().st_size == len(original):
        raise ValueError("El nuevo payload debe activar la reextracción al actualizar Baja.pe")
    with zipfile.ZipFile(output) as archive:
        errors = check_archive(archive, str(output), inside_abi=True)
    if errors:
        raise ValueError("\n".join(errors))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--aar", type=Path, required=True)
    parser.add_argument("--ndk", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    with tempfile.TemporaryDirectory(prefix="bajape-webp-") as directory:
        work = Path(directory)
        with urllib.request.urlopen(WEBP_URL, timeout=60) as response:
            source = response.read()
        if hashlib.sha256(source).hexdigest() != WEBP_SHA256:
            raise ValueError("El SHA-256 del código fuente de libwebp no coincide")
        with tarfile.open(fileobj=io.BytesIO(source), mode="r:gz") as archive:
            archive.extractall(work, filter="data")
        build = work / "build"
        build_webp(work / f"libwebp-{WEBP_VERSION}", build, args.ndk.resolve())
        with zipfile.ZipFile(args.aar) as aar:
            original = aar.read(f"jni/{ABI}/{PAYLOAD}")
        output = work / PAYLOAD
        replace_payload(original, build, output)
        destination = args.output / ABI / PAYLOAD
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(output, destination)


if __name__ == "__main__":
    main()
