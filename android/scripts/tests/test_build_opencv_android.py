import sys
import tempfile
import unittest

from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPT_DIR))

from build_opencv_android import (
    build_config_source,
    exclude_camera_bridge_source,
    patch_aar_templates,
    validate_ndk,
)


class ExcludeCameraBridgeSourceTest(unittest.TestCase):
    def test_excludes_only_the_optional_camera_bridge_from_aar_sources(self):
        template = """sourceSets {
        main {
            java.srcDirs = ['src/main/java']
        }
    }
"""

        patched = exclude_camera_bridge_source(template)

        self.assertIn(
            "java.exclude 'org/opencv/android/NativeCameraView.java'",
            patched,
        )
        self.assertIn("java.srcDirs = ['src/main/java']", patched)

    def test_exclusion_is_idempotent(self):
        template = """sourceSets {
        main {
            java.srcDirs = ['src/main/java']
        }
    }
"""

        once = exclude_camera_bridge_source(template)

        self.assertEqual(once, exclude_camera_bridge_source(once))
        self.assertEqual(
            1,
            once.count("java.exclude 'org/opencv/android/NativeCameraView.java'"),
        )

    def test_fails_when_upstream_template_shape_changes(self):
        with self.assertRaisesRegex(ValueError, "source directory marker"):
            exclude_camera_bridge_source("sourceSets { main { java.srcDirs = ['java'] } }")

    def test_patches_intermediate_and_final_aar_templates(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory)
            intermediate = source / "modules/java/android_sdk/android_gradle_lib/build.gradle"
            final = source / "platforms/android/aar-template/OpenCV/build.gradle.template"
            intermediate.parent.mkdir(parents=True)
            final.parent.mkdir(parents=True)
            intermediate.write_text("java.srcDirs = ['src']  // upstream source\n", encoding="utf-8")
            final.write_text("java.srcDirs = ['src/main/java']\n", encoding="utf-8")

            patch_aar_templates(source)
            patch_aar_templates(source)

            self.assertEqual(
                1,
                intermediate.read_text(encoding="utf-8").count(
                    "java.exclude 'org/opencv/android/NativeCameraView.java'"
                ),
            )
            self.assertEqual(
                1,
                final.read_text(encoding="utf-8").count(
                    "java.exclude 'org/opencv/android/NativeCameraView.java'"
                ),
            )


class BuildConfigSourceTest(unittest.TestCase):
    def test_targets_all_four_app_abis_from_android_14(self):
        config = build_config_source()

        self.assertIn("ANDROID_NATIVE_API_LEVEL = 34", config)
        self.assertIn('"ANDROID_COMPILE_SDK_VERSION": 34', config)
        self.assertIn('"ANDROID_TARGET_SDK_VERSION": 34', config)
        for abi in ("armeabi-v7a", "arm64-v8a", "x86_64", "x86"):
            self.assertIn(f'"{abi}"', config)

    def test_arm64_uses_neon_baseline_without_cpu_dispatch(self):
        config = build_config_source()

        self.assertIn('CPU_BASELINE="NEON"', config)
        self.assertIn('"CPU_DISPATCH": ""', config)

    def test_native_module_set_is_bounded_to_ocr_requirements(self):
        config = build_config_source()

        self.assertIn('"BUILD_opencv_world": "OFF"', config)
        self.assertIn('"BUILD_TESTS": "OFF"', config)
        self.assertIn('"WITH_IPP": "OFF"', config)
        self.assertNotIn("videoio", config)
        self.assertNotIn("highgui", config)


class ValidateNdkTest(unittest.TestCase):
    def test_accepts_ndk_r28_or_newer(self):
        with tempfile.TemporaryDirectory() as directory:
            ndk = Path(directory)
            (ndk / "source.properties").write_text("Pkg.Revision = 28.2.13676358\n", encoding="utf-8")

            self.assertEqual(ndk, validate_ndk(ndk))

    def test_rejects_older_ndk_with_actionable_error(self):
        with tempfile.TemporaryDirectory() as directory:
            ndk = Path(directory)
            (ndk / "source.properties").write_text("Pkg.Revision = 27.2.12479018\n", encoding="utf-8")

            with self.assertRaisesRegex(RuntimeError, "NDK r28 or newer"):
                validate_ndk(ndk)


if __name__ == "__main__":
    unittest.main()
