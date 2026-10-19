# Android Reader OCR Selection Implementation Plan

> **For agentic workers:** Use the `superpowers:subagent-driven-development` workflow task by task. Each task has a failing-test-first step, verification command, and commit boundary.

**Goal:** Add transformed-page region selection to the Android PDF reader, persist offline OCR results in the opened pagenote project, and retain the existing manual full-page OCR action.

**Architecture:** Keep viewport geometry and prepared-manifest policy platform-independent where practical. Put SAF document access and Compose gestures in the Android app module. The app maps a drag to `NormalizedOcrRegion`, checks the project cache, then invokes the existing `OfflineOcrEngine` only on a miss.

**Tech Stack:** Kotlin, Jetpack Compose, Android Storage Access Framework, `PdfRenderer`, Kotlinx Serialization JSON, existing PP-OCRv6 ONNX engine.

**Spec:** `android/docs/spec/features/reader-ocr-selection.md`; contracts: `android/docs/spec/contracts/prepared-manifest.schema.json` and the existing `OcrResult` model.

## Global Constraints

- Keep Android SPECs and implementation plans under `android/docs/spec/`; do not move them to root `docs/`.
- The existing full-page OCR action remains explicit and unchanged; do not add automatic whole-document OCR or network OCR.
- Region coordinates use the original page's normalized 0..1000 coordinate space.
- Keep the displayed PDF bitmap separate from the bitmap passed to OCR; recycle OCR-owned bitmaps in `finally`.
- Persist entries only under `src/prepared/<page>-<key-prefix>/` and write `manifest.json` last as the completion marker.
- Cache only `ok` and `partial` results; report SAF/hash failures without claiming persistence.
- Keep `OcrResult`, the 16-megapixel render cap, and prepared-manifest version 2 compatible.
- Every pushed feature commit includes an Unreleased `CHANGELOG.md` entry; push only to `fork/codex/android-port`, with eventual PR target `beta`.

## Review Focus

- Non-square pages with letterbox margins under fit-width and zoom/pan: inverse mapping must not skew or select outside the PDF.
- Reverse drags and endpoints outside the page: normalize direction and clip the endpoint, but reject a start outside the page.
- SAF provider rejects directory/file creation or truncation: recognition still succeeds, no incomplete cache is treated as a hit, and the UI reports the result was not cached.
- Inference failure/cancellation or page changes while work is active: preserve the displayed page, recycle temporary images, and never associate a result with another page.

## File Map

- `android/app/src/main/java/io/pagenote/android/PdfViewportTransform.kt`: viewport bounds and pure screen-to-page selection mapping.
- `android/core-reader/src/main/java/io/pagenote/core/reader/PreparedManifest.kt`: complete version-2 manifest model and validation.
- `android/core-reader/src/main/java/io/pagenote/core/reader/PreparedManifestCodec.kt`: JSON representation of the prepared-manifest contract.
- `android/app/src/main/java/io/pagenote/android/PreparedDocumentAccess.kt`: fakeable SAF document-provider boundary.
- `android/app/src/main/java/io/pagenote/android/ProjectPreparedCache.kt`: SAF tree adapter and prepared-region cache lifecycle.
- `android/app/src/main/java/io/pagenote/android/PdfDocumentSession.kt` and `PdfSourceSnapshot.kt`: immutable renderer snapshot, source revision, and cleanup.
- `android/app/src/test/java/io/pagenote/android/PdfSourceSnapshotTest.kt`: exact-byte snapshot/hash regression.
- `android/app/src/test/java/io/pagenote/android/ReaderOcrSelectionTest.kt`: selection-state and cache-coordinator policy tests.
- `android/app/src/androidTest/java/io/pagenote/android/ReaderOcrSelectionTest.kt`: Compose action and pointer-routing tests.
- `android/feature-ocr/src/main/java/io/pagenote/feature/ocr/OcrResultJsonCodec.kt`: stable JSON round-trip for the existing `OcrResult` fields.
- `android/app/src/main/java/io/pagenote/android/ReaderOcrSelection.kt`: Compose selection overlay, action state, and input locking.
- `android/app/src/main/java/io/pagenote/android/MainActivity.kt`: wire toolbar actions, viewport selection, cache lookup, OCR, and result lifecycle.
- `android/app/src/main/res/values/strings.xml` and `values-zh-rCN/strings.xml`: localized selection, validation, progress, and cache warnings.

---

### Task 1: Viewport Selection Geometry

**Files:**
- Modify: `android/app/src/main/java/io/pagenote/android/PdfViewportTransform.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt` (move `PdfViewportBounds` from its private local declaration to the shared geometry file)
- Test: `android/app/src/test/java/io/pagenote/android/PdfViewportTransformTest.kt`

**Interfaces:**
- Consumes: `PdfViewportTransform(scale, translationX, translationY)` and `NormalizedOcrRegion(left, top, right, bottom)`.
- Produces: `PdfViewportBounds(viewportWidthPx, viewportHeightPx, pageWidthPx, pageHeightPx)`, `PdfSelectionPoint(x, y)`, and `mapPdfSelectionToOcrRegion(start, end, bounds, transform): NormalizedOcrRegion?`.

- [x] **Step 1: Add failing tests** named `fitWidthLetterbox_mapsToFullPageCoordinates`, `zoomPan_mapsSelectionThroughInverseTransform`, `startInLetterbox_isRejected`, `dragEndOutsidePage_isClipped`, `reverseDrag_isNormalized`, and `invalidBounds_areRejected`. Pin a fitted non-square page (`1000x800` viewport, `600x800` page, drag `(300,200)` to `(700,600)` -> `(166,250,834,750)`) and a zoomed/panned page (scale `2`, translation `(50,-100)`, drag `(350,200)` to `(750,600)` -> `(333,437,667,688)`).
- [x] **Step 2: Run tests and verify RED.** Run `cd android && ./gradlew :app:testDebugUnitTest --tests io.pagenote.android.PdfViewportTransformTest --offline`; expect unresolved mapper symbols or failing new cases before implementation.
- [x] **Step 3: Implement the pure mapper.** Invert scale/translation around the measured viewport/page centers, reject starts outside the page, clip end coordinates, then floor left/top and ceil right/bottom into 0..1000 integer bounds.
- [x] **Step 4: Run focused and module tests.** The focused mapper tests and `:app:testDebugUnitTest` must pass.
- [x] **Step 5: Commit.** `feat(android): map transformed page selections`; include an Unreleased changelog line.

### Task 2: Prepared Manifest and OCR Result Codecs

**Files:**
- Modify: `android/core-reader/build.gradle.kts`
- Modify: `android/core-reader/src/main/java/io/pagenote/core/reader/PreparedManifest.kt`
- Create: `android/core-reader/src/main/java/io/pagenote/core/reader/PreparedManifestCodec.kt`
- Modify: `android/feature-ocr/build.gradle.kts`
- Create: `android/feature-ocr/src/main/java/io/pagenote/feature/ocr/OcrResultJsonCodec.kt`
- Test: `android/core-reader/src/test/java/io/pagenote/core/reader/PreparedManifestCodecTest.kt`
- Test: `android/feature-ocr/src/test/java/io/pagenote/feature/ocr/OcrResultJsonCodecTest.kt`
- Test: `android/app/src/test/java/io/pagenote/android/PreparedManifestApiVisibilityTest.kt` (verify the app can consume the manifest's public JSON model through the core-reader module boundary)

**Interfaces:**
- Consumes: `PageRegion`, `PreparedCache.VERSION`, and the existing `OcrResult`/`OcrBlock` models.
- Produces: `PreparedManifestCodec.encode(manifest): String`, `PreparedManifestCodec.decode(json): PreparedManifest`, and `OcrResultJsonCodec.encode(result): String` / `decode(json): OcrResult`.
- `PreparedManifest` carries `originalPreserved: Boolean`, the five non-negative character/block/table counts from the schema, `ocr: JsonObject`, `files: List<String>`, and `visuals: JsonArray`, in addition to its existing identity, region, dimensions, scale, warnings, and visual-review fields.

- [x] **Step 1: Add failing tests** named `versionTwoManifest_roundTripsEveryContractField`, `manifestRejectsInvalidIdentityRegionAndCounts`, `ocrResultRoundTripsCoordinatesWarningsAndOwnership`, and `unknownManifestFieldsAreIgnored`. Round-trip all schema fields for a version-2 PDF region entry; reject invalid page/revision/region/counts; preserve all `OcrResult` doubles, status, warnings and `imagePreserved`. Add an app-module consumer test that constructs a manifest with a `JsonObject` and reads its JSON fields, proving the core-reader public dependency is visible transitively.
- [x] **Step 2: Run tests and verify RED.** Run the two focused Gradle test tasks; expect missing codec symbols or missing manifest fields.
- [x] **Step 3: Implement the complete version-2 model and explicit JSON codecs.** Add the existing project version `org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3` as an `api` dependency of `core-reader`, because its public manifest model exposes `JsonObject` and `JsonArray` (no serialization compiler plugin; codecs build JSON values explicitly); keep `ocr` and `visuals` JSON data extensible, validate required manifest fields and safe project-relative file names, and make `PreparedManifestStore.isHit(cached, expected)` compare document id, page, source revision, region, cache key, and version.
- [x] **Step 4: Run focused and module tests.** `:core-reader:testDebugUnitTest :feature-ocr:testDebugUnitTest :app:testDebugUnitTest` pass.
- [x] **Step 5: Commit.** `feat(android): model prepared OCR manifests`; include an Unreleased changelog line.

### Task 3: SAF Project Region Cache

**Files:**
- Create: `android/app/src/main/java/io/pagenote/android/PreparedDocumentAccess.kt`
- Create: `android/app/src/main/java/io/pagenote/android/ProjectPreparedCache.kt`
- Test: `android/app/src/test/java/io/pagenote/android/ProjectPreparedCacheTest.kt`

**Interfaces:**
- Consumes: Task 2 codecs and manifest policy; `ProjectDocumentOpener`'s `sourceDirectoryUri`; PNG bytes and an `OcrResult`.
- Produces: `PreparedRegionCacheStore.read(srcDirectoryUri, documentId, page, sourceRevision, region, engine): PreparedOcrCacheEntry?` and `write(srcDirectoryUri, manifest, result, pagePng, regionPng): PreparedOcrCacheWriteResult`.
- `PreparedOcrCacheWriteResult` distinguishes `Saved` from `NotSaved(reason)`; cache I/O failure must not discard an otherwise valid recognition result.
- `PreparedDocumentAccess` exposes `findChild`, `createDirectory`, `createFile`, `delete`, `openInput`, and `openOutput`; production uses `ContentResolver`/`DocumentsContract`, tests use an in-memory fake. `PreparedImageValidator` is injected so local tests can reject malformed images and the Android adapter validates with `BitmapFactory`.

- [x] **Step 1: Add failing tests** named `emptyTreeIsMiss`, `savedEntryRoundTripsResultAndImages`, `changedDocumentPageSourceRegionEngineOrVersionIsMiss`, `malformedManifestOrMissingFileIsMiss`, `invalidImageOrResultIsMiss`, `writeFailureReturnsNotSaved`, and `manifestIsRequiredAsCompletionMarker`, using an in-memory `PreparedDocumentAccess` and injected image validator.
- [x] **Step 2: Run tests and verify RED.** Run `cd android && ./gradlew :app:testDebugUnitTest --tests io.pagenote.android.ProjectPreparedCacheTest --offline`; first run failed on missing cache APIs; the later focused run passed all seven tests.
- [x] **Step 3: Implement SAF-backed storage.** Create/find `src/prepared/<page>-<key-prefix>` through `DocumentsContract`; read and validate every required file; encode `manifest.json`, `page.png`, `region.png`, `ocr.txt`, and `blocks.json`; remove an old completion manifest before replacement and write the new manifest last. Add the `ContentResolver` adapter and fakeable document-access interface. Query failure is distinct from confirmed absence, and saved manifest warnings include both caller and OCR warnings.
- [x] **Step 4: Run cache, codec, and app tests.** `:app:testDebugUnitTest :core-reader:testDebugUnitTest :feature-ocr:testDebugUnitTest :app:assembleDebug --offline` passed.
- [x] **Step 5: Commit.** `feat(android): cache region OCR in SAF projects`; include an Unreleased changelog line.
- [x] **Step 6: Close review findings.** Add `childDiscoveryFailureStopsReplacement` and `savedManifestIncludesCallerAndOcrWarnings`; distinguish SAF enumeration errors from confirmed absence and persist the de-duplicated union of both warning sources. Focused tests, the full JVM suite, Android-test compilation, APK assembly, and all-ABI 16 KB checks pass; commit includes an Unreleased changelog entry.

### Task 4: Reader Selection and OCR Integration

**Files:**
- Create: `android/app/src/main/java/io/pagenote/android/ReaderOcrSelection.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-zh-rCN/strings.xml`
- Test: `android/app/src/test/java/io/pagenote/android/ReaderOcrSelectionTest.kt`
- Test: `android/app/src/androidTest/java/io/pagenote/android/ReaderOcrSelectionTest.kt`
- Test: `android/app/src/test/java/io/pagenote/android/PdfSourceSnapshotTest.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/PdfDocumentSession.kt`
- Create: `android/app/src/main/java/io/pagenote/android/PdfSourceSnapshot.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/ProjectPreparedCache.kt`

**Interfaces:**
- Consumes: Task 1 mapper, Task 3 cache store, the existing `OfflineOcrEngine.recognize(bitmap, region)` and `OcrResultDialog`.
- Produces: a native selection overlay with `Region OCR`, `Recognize`, and `Cancel` actions; result lifecycle that preserves page/source identity and restores normal input after result dismissal.
- Cache access is consumed through a `PreparedRegionCacheStore` interface; `ProjectPreparedCache` remains the production SAF implementation.

- [x] **Step 1: Add failing interaction/policy tests.** Reuse the existing Compose UI test dependency and add instrumentation cases named `regionActionEntersSelectionAndCancelRestoresInput`, `dragSelectsRegionWithoutAddingPenStrokeOrZoom`, and `recognizeUsesCurrentSelectionAndResultDismissalRestoresInput`. Add JVM cases for `sourceSnapshotHashMatchesCopiedBytes`, cache-hit inference bypass, write-failure warning preservation, and source-hash failure bypassing cache. Stylus event injection remains device acceptance.
- [x] **Step 2: Run tests and verify RED.** The focused unit tests failed on the missing snapshot/state/coordinator APIs, and Android-test compilation failed on the absent selection composable and viewport parameters.
- [x] **Step 3: Implement Compose integration.** Copy and hash the SAF PDF to an app-cache snapshot on `Dispatchers.IO`, open `PdfRenderer` on those exact bytes, and delete the snapshot on close; if copying fails, retain direct-SAF rendering and disable caching for that renderer. Add touch/stylus rectangle selection on the displayed transformed page; route selection gestures before the pen canvas; render a separate full-page OCR bitmap and region PNG; check cache before inference through `PreparedRegionCacheStore`; lock page/input while selecting or showing/running OCR; keep the existing full-page action unchanged; localize all new controls and warnings.
- [x] **Step 4: Run Android verification.** `./gradlew testDebugUnitTest :feature-ocr:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin :app:assembleDebug --offline --rerun-tasks` passed; all four ABI APKs passed `scripts/check_16kb_apks.py`. No Android device or AVD is currently available, so device-only interaction and startup acceptance remains pending under task 12.
- [x] **Step 5: Commit.** `feat(android): add reader region OCR selection`; include an Unreleased changelog line and mark the implemented 9.2 subtask in `android/docs/spec/tasks.md`. Device-only stylus and startup acceptance remains pending under task 12.

**Dependencies:** Task 1 and Task 2 are independent. Task 3 depends on Task 2. Task 4 depends on Tasks 1–3.

**Review Focus:** Explicitly inspect the four input/failure classes listed in the feature SPEC's test plan; device-only acceptance remains a separate completion requirement and must not be claimed when not run.
