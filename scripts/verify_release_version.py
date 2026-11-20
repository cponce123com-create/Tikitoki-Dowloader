#!/usr/bin/env python3
"""Validate the Android release tag and monotonically increasing versionCode."""

from __future__ import annotations

import os
import re
import subprocess
import sys
import tomllib
from pathlib import Path

TAG_PATTERN = re.compile(r"^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$")
GRADLE_VERSION_CODE_PATTERN = re.compile(r"^\s*versionCode\s*=\s*(\d+)\s*$", re.MULTILINE)
CATALOG_PATH = "gradle/libs.versions.toml"
APP_GRADLE_PATH = "app/build.gradle.kts"
MAX_ANDROID_VERSION_CODE = 2_100_000_000


def git_show(tag: str, path: str) -> str:
    return subprocess.check_output(
        ["git", "show", f"{tag}:{path}"],
        text=True,
        stderr=subprocess.DEVNULL,
    )


def version_code_for_tag(tag: str) -> int:
    """Read a release's versionCode, supporting both catalog and legacy DSL values."""
    try:
        catalog = tomllib.loads(git_show(tag, CATALOG_PATH))
        catalog_code = catalog.get("versions", {}).get("appVersionCode")
        if catalog_code is not None:
            return int(catalog_code)
    except (subprocess.CalledProcessError, tomllib.TOMLDecodeError, ValueError):
        pass

    try:
        build_file = git_show(tag, APP_GRADLE_PATH)
    except subprocess.CalledProcessError as error:
        raise ValueError(f"no se pudo leer versionCode desde el tag anterior {tag}") from error

    match = GRADLE_VERSION_CODE_PATTERN.search(build_file)
    if match is None:
        raise ValueError(f"no se pudo determinar versionCode del tag anterior {tag}")
    return int(match.group(1))


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> None:
    tag = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("RELEASE_TAG", "")
    tag_match = TAG_PATTERN.fullmatch(tag)
    if tag_match is None:
        fail(f"tag inválido: {tag!r}; se requiere vMAJOR.MINOR.PATCH sin sufijo")
    tag_version = tuple(int(part) for part in tag_match.groups())

    try:
        catalog = tomllib.loads(Path(CATALOG_PATH).read_text(encoding="utf-8"))
        versions = catalog["versions"]
        app_version = str(versions["appVersion"])
        app_version_code = int(versions["appVersionCode"])
    except (OSError, KeyError, tomllib.TOMLDecodeError, ValueError) as error:
        fail(f"no se pudo leer appVersion/appVersionCode de {CATALOG_PATH}: {error}")

    if TAG_PATTERN.fullmatch(f"v{app_version}") is None:
        fail(f"appVersion {app_version!r} no es MAJOR.MINOR.PATCH")
    if tag != f"v{app_version}":
        fail(f"el tag {tag} no coincide con appVersion {app_version}; actualiza el catálogo antes del release")
    if not 1 <= app_version_code <= MAX_ANDROID_VERSION_CODE:
        fail(f"appVersionCode debe estar entre 1 y {MAX_ANDROID_VERSION_CODE}")

    try:
        tags = subprocess.check_output(
            ["git", "tag", "--list", "v*"], text=True
        ).splitlines()
    except subprocess.CalledProcessError as error:
        fail(f"no se pudieron enumerar los tags locales: {error}")

    previous_versions: list[tuple[tuple[int, int, int], str]] = []
    for previous_tag in tags:
        match = TAG_PATTERN.fullmatch(previous_tag)
        if match is None or previous_tag == tag:
            continue
        previous_version = tuple(int(part) for part in match.groups())
        if previous_version < tag_version:
            previous_versions.append((previous_version, previous_tag))

    previous_codes = [(previous_tag, version_code_for_tag(previous_tag))
                      for _, previous_tag in previous_versions]
    if previous_codes:
        highest_previous_code = max(code for _, code in previous_codes)
        if app_version_code <= highest_previous_code:
            details = ", ".join(f"{previous_tag}={code}" for previous_tag, code in previous_codes)
            fail(
                f"appVersionCode {app_version_code} debe ser mayor que todos los releases anteriores "
                f"({details})"
            )

    print(
        f"Release version válida: tag={tag}, versionName={app_version}, "
        f"versionCode={app_version_code}; versionCode supera los tags anteriores."
    )


if __name__ == "__main__":
    main()
