import sys
import unittest
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPT_DIR))

from check_16kb_apks import is_arm64_opencv_library, rdvl_instruction_lines


class RdvlInstructionLinesTest(unittest.TestCase):
    def test_reports_sve_rdvl_instructions(self):
        disassembly = """
        00001000: 04bf5020 rdvl x0, #1
        00001004: d65f03c0 ret
        """

        self.assertEqual(
            ["00001000: 04bf5020 rdvl x0, #1"],
            rdvl_instruction_lines(disassembly),
        )

    def test_does_not_report_neon_or_other_sve_instructions(self):
        disassembly = """
        00002000: 4e25cc71 fmla v17.4s, v3.4s, v5.4s
        00002004: 04bf5020 addvl x0, x0, #1
        """

        self.assertEqual([], rdvl_instruction_lines(disassembly))


class Arm64OpenCvLibraryTest(unittest.TestCase):
    def test_selects_every_arm64_opencv_shared_library(self):
        self.assertTrue(is_arm64_opencv_library("lib/arm64-v8a/libopencv_java4.so"))
        self.assertTrue(is_arm64_opencv_library("lib/arm64-v8a/libopencv_core.so"))
        self.assertFalse(is_arm64_opencv_library("lib/arm64-v8a/libonnxruntime.so"))
        self.assertFalse(is_arm64_opencv_library("lib/x86_64/libopencv_java4.so"))


if __name__ == "__main__":
    unittest.main()
