# Third-Party Notices

- PaddleOCR Android inference pipeline and PP-OCRv6 tiny model assets: Apache-2.0. The PaddleOCR license text is included in `THIRD_PARTY_LICENSE_PADDLEOCR.txt`; upstream Kotlin copyright/license headers are retained. Adapted source snapshot: `PaddlePaddle/PaddleOCR@dab3fe35379033fdcb2d0e9572fac0b36c9a9ebf`.
- PP-OCRv6 tiny detector and recognizer model cards declare Apache-2.0. Model source repositories and pinned revisions are documented in `../docs/spec/features/offline-ocr.md`.
- ONNX Runtime Android `1.21.1`: MIT. The release's license and third-party attributions are included in `THIRD_PARTY_LICENSE_ONNXRUNTIME.txt` and `THIRD_PARTY_NOTICES_ONNXRUNTIME.txt`.
- QuickBird OpenCV Android wrapper `4.5.3.0`: BSD-3-Clause; bundled OpenCV native library `4.5.3`: Apache-2.0. Their upstream license texts are included in `THIRD_PARTY_LICENSE_OPENCV_ANDROID.txt` and `THIRD_PARTY_LICENSE_OPENCV.txt`.
- Kotlin coroutines Android `1.9.0`: Apache-2.0, distributed under the Apache License 2.0.

This module bundles the PP-OCRv6 tiny detector (`inference.onnx`), recognizer (`inference.onnx`), and recognition config (`inference.yml`) as app assets. Their exact model revisions and SHA-256 digests are recorded in the Android OCR SPEC.
