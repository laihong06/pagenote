#!/usr/bin/env python3
"""Build the project's pinned, 16 KB-compatible OpenCV Android AAR."""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


OPENCV_VERSION = "4.10.0"
OPENCV_COMMIT = "71d3237a093b60a27601c20e9ee6c3e52154e8b1"
OPENCV_URL = "https://github.com/opencv/opencv.git"
ARTIFACT_GROUP = "io.pagenote.thirdparty"
ARTIFACT_NAME = "opencv-android"
ARTIFACT_VERSION = "4.10.0-pagenote.1"
MODULES = "core,imgproc,imgcodecs,java"
CAMERA_EXCLUSION = "java.exclude 'org/opencv/android/NativeCameraView.java'"
ANDROID_TEMPLATE_MARKER = re.compile(
    r"(?m)^(?P<indent>\s*)java\.srcDirs = \['(?:src|src/main/java)'\].*$"
)


def build_config_source() -> str:
    return '''ANDROID_NATIVE_API_LEVEL = 34
cmake_common_vars = {
    "ANDROID_COMPILE_SDK_VERSION": 34,
    "ANDROID_TARGET_SDK_VERSION": 34,
    "ANDROID_MIN_SDK_VERSION": 34,
    "CPU_DISPATCH": "",
    "BUILD_opencv_world": "OFF",
    "BUILD_TESTS": "OFF",
    "INSTALL_TESTS": "OFF",
    "WITH_IPP": "OFF",
}
arm64_vars = dict(cmake_common_vars, CPU_BASELINE="NEON")
arm32_vars = dict(cmake_common_vars, ANDROID_ABI="armeabi-v7a with NEON")
ABIs = [
    ABI("2", "armeabi-v7a", None, ndk_api_level=34, cmake_vars=arm32_vars),
    ABI("3", "arm64-v8a", None, ndk_api_level=34, cmake_vars=arm64_vars),
    ABI("5", "x86_64", None, ndk_api_level=34, cmake_vars=cmake_common_vars),
    ABI("4", "x86", None, ndk_api_level=34, cmake_vars=cmake_common_vars),
]
'''


def exclude_camera_bridge_source(template: str) -> str:
    if CAMERA_EXCLUSION in template:
        return template
    matches = list(ANDROID_TEMPLATE_MARKER.finditer(template))
    if len(matches) != 1:
        raise ValueError("upstream AAR template source directory marker is missing or ambiguous")
    match = matches[0]
    exclusion = f"\n{match.group('indent')}{CAMERA_EXCLUSION}"
    return template[:match.end()] + exclusion + template[match.end():]


def patch_aar_templates(source_dir: Path) -> None:
    templates = (
        source_dir / "modules/java/android_sdk/android_gradle_lib/build.gradle",
        source_dir / "platforms/android/aar-template/OpenCV/build.gradle.template",
    )
    for template_path in templates:
        template = template_path.read_text(encoding="utf-8")
        patched = exclude_camera_bridge_source(template)
        if patched != template:
            template_path.write_text(patched, encoding="utf-8")


def java_major_version(java: Path) -> int:
    result = subprocess.run([str(java), "-version"], capture_output=True, text=True)
    version_text = result.stderr + result.stdout
    match = re.search(r'version "(?:1\.)?(\d+)', version_text)
    if result.returncode or not match:
        raise RuntimeError(f"could not determine Java version from {java}: {version_text.strip()}")
    return int(match.group(1))


def find_sdk() -> Path:
    configured = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if configured:
        return Path(configured).expanduser().resolve()
    if sys.platform == "darwin":
        return Path.home() / "Library/Android/sdk"
    if sys.platform == "win32" and os.environ.get("LOCALAPPDATA"):
        return Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk"
    return Path.home() / "Android/Sdk"


def version_key(path: Path) -> tuple[int | str, ...]:
    return tuple(int(part) if part.isdigit() else part for part in re.split(r"[.-]", path.name))


def validate_ndk(ndk: Path) -> Path:
    version_file = ndk / "source.properties"
    version_text = version_file.read_text(encoding="utf-8") if version_file.is_file() else ""
    match = re.search(r"Pkg.Revision\s*=\s*(\d+)", version_text)
    if not match or int(match.group(1)) < 28:
        raise RuntimeError(f"Android NDK r28 or newer is required: {ndk}")
    return ndk


def find_ndk(sdk_dir: Path) -> Path:
    explicit = os.environ.get("ANDROID_NDK_HOME") or os.environ.get("ANDROID_NDK_ROOT")
    if explicit:
        return validate_ndk(Path(explicit).expanduser().resolve())
    roots = sdk_dir / "ndk"
    choices = sorted((path for path in roots.iterdir() if path.is_dir()), key=version_key, reverse=True)
    if not choices:
        raise FileNotFoundError(f"no Android NDK found under {roots}")
    return validate_ndk(choices[0])


def find_cmake(sdk_dir: Path) -> Path:
    roots = sdk_dir / "cmake"
    choices = sorted(
        (path for path in roots.iterdir() if (path / "bin/cmake").is_file()),
        key=version_key,
        reverse=True,
    ) if roots.is_dir() else []
    if not choices:
        raise FileNotFoundError(f"no Android SDK CMake installation found under {roots}")
    return choices[0]


def ensure_java_17() -> Path:
    java_home = os.environ.get("JAVA_HOME")
    java = Path(java_home) / "bin/java" if java_home else Path(shutil.which("java") or "")
    if not java.is_file():
        raise FileNotFoundError("set JAVA_HOME to a JDK 17 installation")
    version = java_major_version(java)
    if version != 17:
        raise RuntimeError(f"JDK 17 is required by the pinned OpenCV Gradle wrapper; found Java {version} at {java}")
    return java


def checkout_source(source_dir: Path) -> None:
    if not source_dir.exists():
        source_dir.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(
            ["git", "clone", "--depth", "1", "--branch", OPENCV_VERSION, OPENCV_URL, str(source_dir)],
            check=True,
        )
    commit = subprocess.run(
        ["git", "-C", str(source_dir), "rev-parse", "HEAD"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()
    if commit != OPENCV_COMMIT:
        raise RuntimeError(f"expected OpenCV {OPENCV_COMMIT}, found {commit} in {source_dir}")


def publish_local_artifact(source_dir: Path, repo_dir: Path, provenance_dir: Path) -> Path:
    built_aar = (
        source_dir
        / "platforms/android/outputs/maven_repo/org/opencv/opencv"
        / OPENCV_VERSION
        / f"opencv-{OPENCV_VERSION}.aar"
    )
    if not built_aar.is_file():
        raise FileNotFoundError(f"OpenCV AAR build output not found: {built_aar}")

    artifact_dir = repo_dir / Path(*ARTIFACT_GROUP.split(".")) / ARTIFACT_NAME / ARTIFACT_VERSION
    artifact_dir.mkdir(parents=True, exist_ok=True)
    artifact_path = artifact_dir / f"{ARTIFACT_NAME}-{ARTIFACT_VERSION}.aar"
    shutil.copy2(built_aar, artifact_path)

    pom = ET.Element("project", xmlns="http://maven.apache.org/POM/4.0.0")
    ET.SubElement(pom, "modelVersion").text = "4.0.0"
    ET.SubElement(pom, "groupId").text = ARTIFACT_GROUP
    ET.SubElement(pom, "artifactId").text = ARTIFACT_NAME
    ET.SubElement(pom, "version").text = ARTIFACT_VERSION
    ET.SubElement(pom, "packaging").text = "aar"
    ET.SubElement(pom, "name").text = "Pagenote OpenCV Android runtime"
    ET.ElementTree(pom).write(
        artifact_dir / f"{ARTIFACT_NAME}-{ARTIFACT_VERSION}.pom",
        encoding="utf-8",
        xml_declaration=True,
    )

    provenance_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source_dir / "LICENSE", provenance_dir / "LICENSE")
    return artifact_path


def build(args: argparse.Namespace) -> Path:
    java = ensure_java_17()
    sdk_dir = args.android_sdk.resolve()
    ndk_dir = validate_ndk(args.android_ndk.resolve()) if args.android_ndk else find_ndk(sdk_dir)
    cmake_dir = find_cmake(sdk_dir)
    work_dir = args.work_dir.resolve()
    source_dir = args.source_dir.resolve() if args.source_dir else work_dir / "opencv-source"
    output_dir = args.maven_repo.resolve()
    provenance_dir = args.provenance_dir.resolve()

    checkout_source(source_dir)
    patch_aar_templates(source_dir)
    work_dir.mkdir(parents=True, exist_ok=True)
    config_path = work_dir / "pagenote-android.config.py"
    config_path.write_text(build_config_source(), encoding="utf-8")

    env = os.environ.copy()
    env.update({
        "JAVA_HOME": str(java.parent.parent),
        "ANDROID_SDK": str(sdk_dir),
        "ANDROID_HOME": str(sdk_dir),
        "ANDROID_SDK_ROOT": str(sdk_dir),
        "ANDROID_NDK": str(ndk_dir),
        "ANDROID_NDK_HOME": str(ndk_dir),
        "ANDROID_NDK_ROOT": str(ndk_dir),
    })
    env["PATH"] = str(java.parent) + os.pathsep + env.get("PATH", "")

    sdk_build_dir = work_dir / "sdk-build"
    build_sdk = source_dir / "platforms/android/build_sdk.py"
    subprocess.run(
        [
            sys.executable, str(build_sdk), str(sdk_build_dir), str(source_dir),
            "--ndk_path", str(ndk_dir), "--sdk_path", str(sdk_dir),
            "--config", str(config_path), "--use_android_buildtools",
            "--modules_list", MODULES, "--no_samples_build", "--no_kotlin",
            "--no_ccache", "--no_media_ndk", "--disable", "TBB", "--disable", "OPENCL",
            "--disable", "IPP",
        ],
        check=True,
        env=env,
    )

    build_aar = source_dir / "platforms/android/build_java_shared_aar.py"
    subprocess.run(
        [
            sys.executable, str(build_aar), str(sdk_build_dir / "OpenCV-android-sdk"),
            "--android_compile_sdk", "34", "--android_min_sdk", "34",
            "--android_target_sdk", "34", "--java_version", "1_8",
            "--ndk_location", str(ndk_dir), "--cmake_location", str(cmake_dir),
        ],
        check=True,
        cwd=source_dir / "platforms/android",
        env=env,
    )
    artifact = publish_local_artifact(source_dir, output_dir, provenance_dir)
    print(f"Built {artifact}")
    return artifact


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    android_dir = Path(__file__).resolve().parents[1]
    parser.add_argument("--android-sdk", type=Path, default=find_sdk())
    parser.add_argument("--android-ndk", type=Path)
    parser.add_argument("--work-dir", type=Path, default=android_dir / "build/opencv-native")
    parser.add_argument("--source-dir", type=Path, help="existing checkout of the pinned OpenCV commit")
    parser.add_argument("--maven-repo", type=Path, default=android_dir / "third-party/opencv-maven")
    parser.add_argument("--provenance-dir", type=Path, default=android_dir / "third-party/opencv")
    args = parser.parse_args()
    try:
        build(args)
    except (FileNotFoundError, RuntimeError, subprocess.CalledProcessError, OSError, ValueError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
