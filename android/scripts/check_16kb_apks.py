#!/usr/bin/env python3
"""Verify 16 KB ZIP and ELF alignment for Android APKs."""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import zipfile
from pathlib import Path
from tempfile import TemporaryDirectory


ABI_LIBRARY = re.compile(r"^lib/(arm64-v8a|x86_64)/[^/]+\.so$")
MINIMUM_ELF_ALIGNMENT_POWER = 14


def version_key(path: Path) -> tuple[int | str, ...]:
    return tuple(
        int(part) if part.isdigit() else part
        for part in re.split(r"[.-]", path.name)
    )


def sdk_root(android_dir: Path) -> Path:
    for variable in ("ANDROID_SDK_ROOT", "ANDROID_HOME"):
        value = os.environ.get(variable)
        if value:
            return Path(value).expanduser()

    local_properties = android_dir / "local.properties"
    if local_properties.is_file():
        for line in local_properties.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                value = line.partition("=")[2].replace("\\:", ":").replace("\\\\", "\\")
                return Path(value).expanduser()

    if sys.platform == "darwin":
        return Path.home() / "Library/Android/sdk"
    if sys.platform == "win32":
        local_app_data = os.environ.get("LOCALAPPDATA")
        if local_app_data:
            return Path(local_app_data) / "Android/Sdk"
    return Path.home() / "Android/Sdk"


def find_llvm_objdump(android_dir: Path) -> Path:
    explicit_ndk = os.environ.get("ANDROID_NDK_HOME") or os.environ.get("ANDROID_NDK_ROOT")
    sdk = sdk_root(android_dir)
    ndk_roots = [Path(explicit_ndk).expanduser()] if explicit_ndk else []
    ndk_dir = sdk / "ndk"
    if ndk_dir.is_dir():
        installed_ndks = (path for path in ndk_dir.iterdir() if path.is_dir())
        ndk_roots.extend(sorted(installed_ndks, key=version_key, reverse=True))

    host_tags = {
        "darwin": ("darwin-arm64", "darwin-x86_64"),
        "linux": ("linux-x86_64", "linux-aarch64"),
        "win32": ("windows-x86_64",),
    }.get(sys.platform, ())
    for ndk in ndk_roots:
        for host in host_tags:
            candidate = ndk / "toolchains/llvm/prebuilt" / host / "bin/llvm-objdump"
            if candidate.is_file():
                return candidate
            candidate_exe = candidate.with_suffix(".exe")
            if candidate_exe.is_file():
                return candidate_exe
    raise FileNotFoundError("Could not find NDK llvm-objdump; set ANDROID_NDK_HOME or pass --llvm-objdump")


def find_zipalign(android_dir: Path) -> Path:
    explicit = os.environ.get("ZIPALIGN")
    if explicit:
        return Path(explicit).expanduser()

    build_tools = sdk_root(android_dir) / "build-tools"
    installed_build_tools = (path for path in build_tools.iterdir() if path.is_dir())
    versions = sorted(installed_build_tools, key=version_key, reverse=True) if build_tools.is_dir() else []
    for version in versions:
        for name in ("zipalign", "zipalign.exe"):
            candidate = version / name
            if candidate.is_file():
                return candidate
    raise FileNotFoundError("Could not find Android SDK zipalign; set ZIPALIGN or pass --zipalign")


def load_segment_powers(objdump: Path, library: Path) -> list[int]:
    result = subprocess.run(
        [str(objdump), "-p", str(library)],
        check=True,
        capture_output=True,
        text=True,
    )
    powers: list[int] = []
    for line in result.stdout.splitlines():
        if not re.match(r"\s*LOAD\b", line):
            continue
        power_match = re.search(r"\balign\s+2\*\*(\d+)", line, re.IGNORECASE)
        if power_match:
            powers.append(int(power_match.group(1)))
            continue
        byte_match = re.search(r"\bAlign\s+(0x[0-9a-f]+|\d+)\b", line, re.IGNORECASE)
        if byte_match:
            alignment = int(byte_match.group(1), 0)
            if alignment <= 0 or alignment & (alignment - 1):
                raise ValueError(f"non-power-of-two LOAD alignment in {library.name}: {alignment}")
            powers.append(alignment.bit_length() - 1)
            continue
        raise ValueError(f"could not parse LOAD alignment in {library.name}: {line.strip()}")
    if not powers:
        raise ValueError(f"no ELF LOAD segments found in {library.name}")
    return powers


def rdvl_instruction_lines(disassembly: str) -> list[str]:
    return [
        line.strip()
        for line in disassembly.splitlines()
        if re.search(r"\brdvl\b", line, re.IGNORECASE)
    ]


def is_arm64_opencv_library(name: str) -> bool:
    return name.startswith("lib/arm64-v8a/libopencv_") and name.endswith(".so")


def verify_opencv_instruction_baseline(objdump: Path, library: Path) -> list[str]:
    result = subprocess.run(
        [str(objdump), "-d", "--mattr=+sve", str(library)],
        check=True,
        capture_output=True,
        text=True,
    )
    return rdvl_instruction_lines(result.stdout)


def verify_apk(apk: Path, objdump: Path, zipalign: Path) -> list[str]:
    errors: list[str] = []
    try:
        with zipfile.ZipFile(apk) as archive:
            libraries = [name for name in archive.namelist() if ABI_LIBRARY.fullmatch(name)]
            with TemporaryDirectory(prefix="pagenote-16kb-") as temporary_dir:
                for index, name in enumerate(libraries):
                    library = Path(temporary_dir) / f"{index}-{Path(name).name}"
                    library.write_bytes(archive.read(name))
                    try:
                        powers = load_segment_powers(objdump, library)
                    except (OSError, ValueError, subprocess.CalledProcessError) as error:
                        errors.append(f"{apk}: {name}: {error}")
                        continue
                    if any(power < MINIMUM_ELF_ALIGNMENT_POWER for power in powers):
                        details = ", ".join(f"2**{power}" for power in powers)
                        errors.append(f"{apk}: {name}: unaligned LOAD segments ({details})")
                    else:
                        print(f"ALIGNED {apk}: {name} LOAD segments >= 2**{MINIMUM_ELF_ALIGNMENT_POWER}")
                    if is_arm64_opencv_library(name):
                        try:
                            rdvl_lines = verify_opencv_instruction_baseline(objdump, library)
                        except (OSError, subprocess.CalledProcessError) as error:
                            errors.append(f"{apk}: {name}: could not verify ARM64 instruction baseline: {error}")
                        else:
                            if rdvl_lines:
                                errors.append(
                                    f"{apk}: {name}: contains unsupported RDVL instruction(s): "
                                    f"{rdvl_lines[0]}"
                                )
                            else:
                                print(f"SAFE {apk}: {name} contains no RDVL instruction")
    except (OSError, zipfile.BadZipFile) as error:
        return [f"{apk}: unable to inspect APK: {error}"]

    if not libraries:
        print(f"INFO {apk}: no arm64-v8a or x86_64 shared libraries")

    try:
        aligned = subprocess.run(
            [str(zipalign), "-c", "-P", "16", "4", str(apk)],
            capture_output=True,
            text=True,
        )
    except OSError as error:
        errors.append(f"{apk}: could not run zipalign: {error}")
        return errors
    if aligned.returncode:
        detail = (aligned.stdout + aligned.stderr).strip()
        errors.append(f"{apk}: 16 KB ZIP alignment failed{': ' + detail if detail else ''}")
    else:
        print(f"ALIGNED {apk}: 16 KB ZIP alignment")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--llvm-objdump", type=Path, help="NDK llvm-objdump executable")
    parser.add_argument("--zipalign", type=Path, help="Android SDK zipalign executable")
    parser.add_argument("apk", nargs="+", type=Path, help="APK files to check")
    args = parser.parse_args()
    android_dir = Path(__file__).resolve().parents[1]
    try:
        objdump = args.llvm_objdump or find_llvm_objdump(android_dir)
        zipalign = args.zipalign or find_zipalign(android_dir)
    except FileNotFoundError as error:
        parser.error(str(error))

    errors: list[str] = []
    for apk in args.apk:
        errors.extend(verify_apk(apk, objdump, zipalign))
    for error in errors:
        print(f"ERROR {error}", file=sys.stderr)
    if errors:
        return 1
    print(f"Verified {len(args.apk)} APK(s) for 16 KB compatibility.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
