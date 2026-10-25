# Android Pen Ink Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan is explicitly decomposed into **parallel waves** (see Parallel Execution Map): tasks in the same wave touch disjoint files and may be dispatched to concurrent subagents; the orchestrator serializes review and commits.

**Goal:** Rebuild the Android pen canvas around a committed-ink raster cache, add display-only heuristic stroke prediction, and render freehand ink with midpoint quadratic smoothing — without changing any stored annotation data or the public `PenCanvasView` API.

**Architecture:** Today `PenCanvasView.onDraw` redraws every committed stroke every frame, so latency-critical live inking pays for all history. We extract a shared `PenStrokePainter`, add a view-pixel `PenStrokeCache` raster that holds committed ink (the Kotlin counterpart of the "page handle" in the Newskyer `PaintView` kernel), redraw only the active gesture plus a predicted tail on top, and smooth freehand rendering through a pure `PenStrokeSmoother`. A pure `PenPredictor` (a clean reimplementation of the Newskyer `PathPrediction` heuristics: nine-sample window, turn-angle buckets, deceleration and pressure-spike damping, refresh-rate boost) feeds the live tail; predicted points are never persisted.

**Tech Stack:** Kotlin, Android View/Canvas/Bitmap (software rendering), JUnit 4 JVM tests, AndroidJUnit4 instrumentation tests on the API 34 tablet AVD.

**Spec:** `android/docs/spec/features/pen-canvas.md` (the behavioral contract this plan must preserve and extend). External reference: `/Users/broccoli/Project/note/CODE_GUIDE.md` — Newskyer (touchnotes) decompiled source guide; `com/newskyer/paint/utils/PathPrediction.java` and `com/newskyer/paint/PaintView.java` are the reference implementations for prediction and the raster-page idea.

## Global Constraints

- The sole hardware target is Xiaomi Pad 5 Pro / Snapdragon 870 / Android 14 (API 34); the only routine emulator proxy is one API 34 tablet AVD.
- No annotation JSON schema change. `PenStroke` storage, `PenPageStore`, and the `annotations.json` contract are untouched; prediction and smoothing are **display-only**.
- The public `PenCanvasView` API (properties, callbacks, `PenCanvasTool`) must not change; `MainActivity` is not modified by any task.
- Existing instrumentation pixel tests (`HighlighterRenderingInstrumentedTest`, `PenCanvasViewInstrumentedTest`) must pass **unchanged** after every task that touches rendering.
- JVM tests run from `android/` with `./gradlew :feature-pen:testDebugUnitTest`; instrumentation tests with `./gradlew :feature-pen:connectedDebugAndroidTest` (AVD `Pixel_Tablet_API34` running).
- Every source push includes a root `CHANGELOG.md` Unreleased bilingual entry; local debug APKs are not public releases.
- Feature work happens on branch `codex/pen-ink-pipeline`; the eventual PR targets `beta`, never `main`.

## Review Focus

- The cache blit is pixel-identical to direct rendering for every stroke type, including translucent highlighter (one coat per gesture, overlap across gestures allowed).
- The predictor never leaks into storage: after `ACTION_UP`, the stored stroke ends at the last real sample.
- Smoothing preserves the first and last sample exactly and keeps single-dot and two-point behavior identical.
- All wave boundaries are file-disjoint; no task edits a file another task in the same wave edits.

## Parallel Execution Map

| Wave | Tasks (parallel within wave) | Files touched |
|---|---|---|
| 0 | Task 0 (preflight, orchestrator only) | git only |
| 1 | Task 1 painter extraction ∥ Task 2 predictor ∥ Task 3 smoother | `PenCanvasView.kt`+new `PenStrokePainter.kt` ∥ new `PenPredictor.kt` ∥ new `PenStrokeSmoother.kt` |
| 2 | Task 4 stroke cache ∥ Task 5 smoothed freehand | new `PenStrokeCache.kt` ∥ `PenStrokePainter.kt` |
| 3 | Task 6 cache-backed view | `PenCanvasView.kt` |
| 4 | Task 7 prediction wiring | `PenCanvasView.kt` + `PenStrokePainter.kt` (one new helper) |
| 5 | Task 8 spec + changelog + full verification | docs + `CHANGELOG.md` |

Within a wave, subagents implement and run their own tests but do **not** commit; the orchestrator reviews each task and commits them one by one, then dispatches the next wave.

---

### Task 0: Preflight — clean tree and branch

**Files:** none (git operations only).

**Interfaces:**
- Consumes: the working tree.
- Produces: branch `codex/pen-ink-pipeline` with a clean tree, ready for Task 1.

- [ ] **Step 1: Inspect the working tree**

Run: `cd /Users/broccoli/Project/pagenote && git status --short`
Expected: either clean, or only the four known pending highlighter files (`CHANGELOG.md`, `android/docs/spec/features/pen-canvas.md`, `android/feature-pen/src/main/java/io/pagenote/feature/pen/PenCanvasView.kt`, `android/feature-pen/src/androidTest/java/io/pagenote/feature/pen/HighlighterRenderingInstrumentedTest.kt`). If anything else is dirty, stop and surface it to the maintainer instead of proceeding.

- [ ] **Step 2: Commit the pending highlighter fix if present**

The highlighter per-gesture composition fix is complete and verified (its CHANGELOG entry and verification notes already exist in `pen-canvas.md`). It must be its own commit before the refactor starts so the refactor diff stays clean.

```bash
git add CHANGELOG.md android/docs/spec/features/pen-canvas.md \
  android/feature-pen/src/main/java/io/pagenote/feature/pen/PenCanvasView.kt \
  android/feature-pen/src/androidTest/java/io/pagenote/feature/pen/HighlighterRenderingInstrumentedTest.kt
git commit -m "fix(android): composite each highlighter gesture as one translucent layer"
```

If the tree was already clean, skip this step.

- [ ] **Step 3: Create the feature branch**

```bash
git checkout -b codex/pen-ink-pipeline
```

---

### Task 1 (Wave 1): Extract PenStrokePainter from PenCanvasView

**Files:**
- Create: `android/feature-pen/src/main/java/io/pagenote/feature/pen/PenStrokePainter.kt`
- Modify: `android/feature-pen/src/main/java/io/pagenote/feature/pen/PenCanvasView.kt`

**Interfaces:**
- Consumes: `PenStroke`, `PenPoint`, `PenCanvasGeometry` (existing).
- Produces (used by Tasks 4, 5, 6, 7):
  - `object PenStrokePainter` with `const val HIGHLIGHT_ALPHA: Int = 89`
  - `fun drawStroke(canvas: Canvas, stroke: PenStroke, geometry: PenCanvasGeometry)`
  - `fun drawLaser(canvas: Canvas, points: List<PenPoint>, color: String, width: Double, geometry: PenCanvasGeometry)`
  - (Task 5 adds smoothing inside `drawFreehand`; Task 7 adds `drawSegment`.)

This is a behavior-preserving extraction: every moved function body keeps its exact drawing operations, only gaining a `geometry` parameter. The test cycle is "existing suites pass unchanged before and after".

- [ ] **Step 1: Run the existing JVM suite to establish the baseline**

Run: `cd /Users/broccoli/Project/pagenote/android && ./gradlew :feature-pen:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (all existing tests pass).

- [ ] **Step 2: Create PenStrokePainter.kt with the moved code**

```kotlin
package io.pagenote.feature.pen

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Shared stroke rasterizer used by the live view and the committed-stroke
 * cache. All functions draw in document units; the caller has already
 * applied the page translate/scale to [canvas].
 */
object PenStrokePainter {
  const val HIGHLIGHT_ALPHA = 89

  private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
  }

  fun drawStroke(canvas: Canvas, stroke: PenStroke, geometry: PenCanvasGeometry) {
    val color = runCatching { Color.parseColor(stroke.color) }.getOrDefault(Color.BLACK)
    val points = stroke.points
    if (points.isEmpty()) return
    strokePaint.color = color
    strokePaint.alpha = Color.alpha(color)
    strokePaint.style = Paint.Style.STROKE
    strokePaint.strokeWidth = stroke.width.toFloat()

    when (stroke.type) {
      "rect" -> {
        if (points.size >= 2) {
          strokePaint.style = Paint.Style.STROKE
          canvas.drawRect(
            min(points.first().x, points.last().x),
            min(points.first().y, points.last().y),
            max(points.first().x, points.last().x),
            max(points.first().y, points.last().y),
            strokePaint,
          )
        }
      }
      "ellipse" -> {
        if (points.size >= 2) {
          canvas.drawOval(
            min(points.first().x, points.last().x),
            min(points.first().y, points.last().y),
            max(points.first().x, points.last().x),
            max(points.first().y, points.last().y),
            strokePaint,
          )
        }
      }
      "line", "arrow" -> {
        if (points.size >= 2) {
          val first = points.first()
          val last = points.last()
          canvas.drawLine(first.x, first.y, last.x, last.y, strokePaint)
          if (stroke.type == "arrow") drawArrowHead(canvas, first, last, strokePaint)
        }
      }
      "pen" -> drawFreehand(canvas, points, stroke.width.toFloat())
      "highlight" -> drawHighlight(canvas, points, stroke.width.toFloat(), geometry)
      else -> Unit
    }
  }

  fun drawHighlight(
    canvas: Canvas,
    points: List<PenPoint>,
    width: Float,
    geometry: PenCanvasGeometry,
  ) {
    val first = points.first()
    val bounds = RectF(first.x, first.y, first.x, first.y)
    points.forEach { bounds.union(it.x, it.y) }
    val padding = width / 2f + 1f / geometry.pixelsPerDocumentUnit
    bounds.inset(-padding, -padding)
    if (!bounds.intersect(RectF(canvas.clipBounds))) return

    // Composite once per gesture, never once per pressure segment or retrace.
    val save = canvas.saveLayerAlpha(bounds, HIGHLIGHT_ALPHA)
    try {
      drawFreehand(canvas, points, width)
    } finally {
      canvas.restoreToCount(save)
    }
  }

  fun drawFreehand(canvas: Canvas, points: List<PenPoint>, width: Float) {
    strokePaint.style = Paint.Style.STROKE
    strokePaint.alpha = 255
    if (points.size == 1) {
      strokePaint.style = Paint.Style.FILL
      canvas.drawCircle(points[0].x, points[0].y, width * points[0].pressure / 2f, strokePaint)
      return
    }
    for (index in 1 until points.size) {
      val previous = points[index - 1]
      val current = points[index]
      strokePaint.strokeWidth = width * (previous.pressure + current.pressure) / 2f
      val path = Path().apply {
        moveTo(previous.x, previous.y)
        lineTo(current.x, current.y)
      }
      canvas.drawPath(path, strokePaint)
    }
  }

  fun drawLaser(
    canvas: Canvas,
    points: List<PenPoint>,
    color: String,
    width: Double,
    geometry: PenCanvasGeometry,
  ) {
    val stroke = PenStroke("pen", color, width, points)
    drawStroke(canvas, stroke.copy(width = width * 2.5), geometry)
    drawStroke(canvas, stroke.copy(color = "#ffffff", width = width * 0.55), geometry)
  }

  private fun drawArrowHead(canvas: Canvas, start: PenPoint, end: PenPoint, paint: Paint) {
    val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble()).toFloat()
    val headLength = (paint.strokeWidth * 4f).coerceIn(12f, 40f)
    val spread = 0.55f
    val path = Path().apply {
      moveTo(end.x, end.y)
      lineTo(end.x - headLength * cos(angle - spread), end.y - headLength * sin(angle - spread))
      moveTo(end.x, end.y)
      lineTo(end.x - headLength * cos(angle + spread), end.y - headLength * sin(angle + spread))
    }
    canvas.drawPath(path, paint)
  }
}
```

- [ ] **Step 3: Point PenCanvasView at the painter**

In `PenCanvasView.kt`:
1. Delete the private `drawPaint` field and the `drawStroke`, `drawHighlight`, `drawFreehand`, `drawLaser`, `drawArrowHead` functions.
2. Delete `HIGHLIGHT_ALPHA` from the companion object (it now lives in `PenStrokePainter`).
3. Replace every call site:
   - `onDraw`: `internalStrokes.forEach { drawStroke(canvas, it) }` → `internalStrokes.forEach { PenStrokePainter.drawStroke(canvas, it, geometry) }`
   - `onDraw` laser trail: `drawLaser(canvas, laserTrail, laserColor, laserWidth)` → `PenStrokePainter.drawLaser(canvas, laserTrail, laserColor, laserWidth, geometry)`
   - `onDraw` active laser: `drawLaser(canvas, activePoints, activeColor, activeWidth)` → `PenStrokePainter.drawLaser(canvas, activePoints, activeColor, activeWidth, geometry)`
   - `onDraw` shape preview and freehand preview: `drawStroke(canvas, PenStroke(...))` → `PenStrokePainter.drawStroke(canvas, PenStroke(...), geometry)`
4. Remove now-unused imports (`Paint`, `Path`, `RectF`, `Color`, `atan2`, `cos`, `sin`); keep `Canvas`, `Color` only if still used (`drawSelection` uses `selectionPaint` — keep its `Paint` import).

- [ ] **Step 4: Run the JVM suite**

Run: `cd /Users/broccoli/Project/pagenote/android && ./gradlew :feature-pen:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run the full instrumentation suite (pixel-equivalence gate)**

Run: `cd /Users/broccoli/Project/pagenote/android && ./gradlew :feature-pen:connectedDebugAndroidTest`
Expected: BUILD SUCCESSFUL, all existing tests pass unchanged.

- [ ] **Step 6: Commit**

```bash
cd /Users/broccoli/Project/pagenote
git add android/feature-pen/src/main/java/io/pagenote/feature/pen/PenStrokePainter.kt \
  android/feature-pen/src/main/java/io/pagenote/feature/pen/PenCanvasView.kt
git commit -m "refactor(android): extract shared stroke painter"
```

---

### Task 2 (Wave 1): PenPredictor — display-only stroke extrapolation

**Files:**
- Create: `android/feature-pen/src/main/java/io/pagenote/feature/pen/PenPredictor.kt`
- Test: `android/feature-pen/src/test/java/io/pagenote/feature/pen/PenPredictorTest.kt`

**Interfaces:**
- Consumes: nothing (pure Kotlin, no Android dependency).
- Produces (used by Task 7):
  - `class PenPredictor(densityPx: Float = 1f, refreshRateHz: Float = 60f)`
  - `fun onDown()`, `fun onUp()` — both reset all state
  - `fun onMove(xPx: Float, yPx: Float, pressure: Float)` — one call per input sample, view-pixel coordinates, raw event pressure
  - `fun predict(): Prediction?` — `data class Prediction(val x: Float, val y: Float)` in view-pixel space; `null` before nine samples, when stationary, or after reset. Idempotent: repeated calls without new samples return the same value.

Design note: this is a clean reimplementation of the Newskyer `PathPrediction.onTouch2` heuristics (the decompiled control flow is not portable): nine-sample gate, turn-angle-bucketed extrapolation, deceleration damping, pressure-spike damping, high-refresh boost. Constants are deliberately explicit and tunable; on-device tuning happens after this plan lands.

- [ ] **Step 1: Write the failing tests**

```kotlin
package io.pagenote.feature.pen

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PenPredictorTest {
  private val density = 2f
  private val refresh = 120f

  @Test
  fun predictsNothingBeforeNineMoves() {
    val predictor = PenPredictor(density, refresh)
    predictor.onDown()
    repeat(8) { index -> predictor.onMove(100f + index * 30f, 200f, 1f) }
    assertNull(predictor.predict())
  }

  @Test
  fun straightFastStrokeExtrapolatesForwardAlongTheLine() {
    val predictor = PenPredictor(density, refresh)
    predictor.onDown()
    repeat(9) { index -> predictor.onMove(100f + index * 30f, 200f, 1f) }
    val lastX = 100f + 8 * 30f
    val prediction = predictor.predict()
    assertNotNull(prediction)
    assertEquals(200f, prediction!!.y, 0.01f)
    assertTrue(
      "expected forward extrapolation beyond $lastX, was ${prediction.x}",
      prediction.x > lastX,
    )
    assertTrue(
      "extrapolation must stay within the ${20f * density}px cap, was ${prediction.x - lastX}",
      prediction.x <= lastX + 20f * density,
    )
  }

  @Test
  fun sharpTurnExtrapolatesLessThanAStraightStrokeAndFollowsTheNewHeading() {
    val straight = PenPredictor(density, refresh)
    straight.onDown()
    repeat(9) { index -> straight.onMove(100f + index * 30f, 200f, 1f) }
    val straightDistance = straight.predict()!!.x - (100f + 8 * 30f)

    // Eight samples rightward, then one sample straight down: the corner sits
    // on the second-to-last sample, so the last heading turns 90 degrees.
    val turning = PenPredictor(density, refresh)
    turning.onDown()
    repeat(8) { index -> turning.onMove(100f + index * 30f, 200f, 1f) }
    turning.onMove(100f + 7 * 30f, 230f, 1f)
    val lastX = 100f + 7 * 30f
    val lastY = 230f
    val turned = turning.predict()!!
    val turnedDistance = hypot(turned.x - lastX, turned.y - lastY)
    assertTrue(
      "turn distance $turnedDistance should be below straight distance $straightDistance",
      turnedDistance < straightDistance,
    )
    assertTrue("prediction should follow the new downward heading", turned.y > lastY)
  }

  @Test
  fun stationaryTailPredictsNothing() {
    val predictor = PenPredictor(density, refresh)
    predictor.onDown()
    repeat(9) { index -> predictor.onMove(100f + index * 30f, 200f, 1f) }
    predictor.onMove(100f + 8 * 30f, 200f, 1f)
    assertNull(predictor.predict())
  }

  @Test
  fun deceleratingStrokeExtrapolatesLessThanConstantSpeed() {
    fun extrapolation(lastStep: Float): Float {
      val predictor = PenPredictor(density, refresh)
      predictor.onDown()
      repeat(8) { index -> predictor.onMove(100f + index * 30f, 200f, 1f) }
      predictor.onMove(100f + 7 * 30f + lastStep, 200f, 1f)
      val prediction = predictor.predict()!!
      return prediction.x - (100f + 7 * 30f + lastStep)
    }
    // The last sample's step is exactly lastStep (10 slows down from the
    // previous 30px step, 30 keeps constant speed), so only the 10px case
    // falls below 70% of its previous step and triggers DECELERATION_DAMP.
    // The doubled bound fails if the damping ever becomes a no-op.
    assertTrue(extrapolation(30f) > extrapolation(10f) * 2f)
  }

  @Test
  fun pressureSpikeDampsExtrapolation() {
    fun extrapolation(pressures: List<Float>): Float {
      val predictor = PenPredictor(density, refresh)
      predictor.onDown()
      pressures.forEachIndexed { index, pressure ->
        predictor.onMove(100f + index * 30f, 200f, pressure)
      }
      val prediction = predictor.predict()!!
      return prediction.x - (100f + (pressures.size - 1) * 30f)
    }
    val steady = extrapolation(List(9) { 0.4f })
    val spiking = extrapolation(List(8) { 0.4f } + 0.9f)
    assertTrue("spike $spiking should be damped below steady $steady", spiking < steady)
  }

  @Test
  fun upClearsTheWindowAndPredictIsIdempotent() {
    val predictor = PenPredictor(density, refresh)
    predictor.onDown()
    repeat(9) { index -> predictor.onMove(100f + index * 30f, 200f, 1f) }
    val first = predictor.predict()
    val second = predictor.predict()
    assertNotNull(first)
    assertEquals(first!!.x, second!!.x, 0.001f)
    assertEquals(first.y, second.y, 0.001f)
    predictor.onUp()
    assertNull(predictor.predict())
  }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd /Users/broccoli/Project/pagenote/android && ./gradlew :feature-pen:testDebugUnitTest`
Expected: COMPILATION ERROR — `PenPredictor` is unresolved.

- [ ] **Step 3: Implement PenPredictor**

```kotlin
package io.pagenote.feature.pen

import kotlin.math.acos
import kotlin.math.hypot

/**
 * Display-only stroke extrapolation in view-pixel space, reimplemented from
 * the Newskyer PathPrediction heuristics: a nine-sample window gates
 * prediction, the recent turn angle buckets the extrapolation distance,
 * deceleration and pressure spikes damp it, and high refresh rates lengthen
 * it. Predicted points are never written to PenStroke storage; the owner
 * maps them back through PenCanvasGeometry when drawing the live tail.
 *
 * [predict] is a pure function of the current window: calling it repeatedly
 * without new samples returns the same prediction.
 */
class PenPredictor(
  private val densityPx: Float = 1f,
  private val refreshRateHz: Float = 60f,
) {
  data class Prediction(val x: Float, val y: Float)

  private data class Sample(val x: Float, val y: Float, val pressure: Float)

  private val window = ArrayDeque<Sample>()

  fun onDown() = reset()

  fun onUp() = reset()

  fun onMove(xPx: Float, yPx: Float, pressure: Float) {
    window.addLast(Sample(xPx, yPx, pressure))
    while (window.size > WINDOW_SIZE) window.removeFirst()
  }

  fun predict(): Prediction? {
    if (window.size < WINDOW_SIZE) return null
    val a = window[window.size - 3]
    val b = window[window.size - 2]
    val c = window[window.lastIndex]
    val stepPx = hypot(c.x - b.x, c.y - b.y)
    if (!stepPx.isFinite() || stepPx < MIN_STEP_DP * densityPx) return null
    val previousStepPx = hypot(b.x - a.x, b.y - a.y)

    var distancePx = baseExtrapolationDp(turnAngleDegrees(a, b, c)) * densityPx *
      speedFactor(stepPx)
    if (previousStepPx > 0f && stepPx < previousStepPx * DECELERATION_RATIO) {
      distancePx *= DECELERATION_DAMP
    }
    if (b.pressure > MIN_PRESSURE_FOR_RATIO && c.pressure / b.pressure >= PRESSURE_SPIKE_RATIO) {
      distancePx *= PRESSURE_DAMP
    }
    if (refreshRateHz > HIGH_REFRESH_HZ) distancePx *= HIGH_REFRESH_BOOST
    distancePx = distancePx.coerceAtMost(MAX_EXTRAPOLATION_DP * densityPx)

    val dx = (c.x - b.x) / stepPx
    val dy = (c.y - b.y) / stepPx
    return Prediction(c.x + dx * distancePx, c.y + dy * distancePx)
  }

  fun reset() {
    window.clear()
  }

  /** 0° means a perfectly straight continuation; 90° is a right-angle turn. */
  private fun turnAngleDegrees(a: Sample, b: Sample, c: Sample): Float {
    val v1x = b.x - a.x
    val v1y = b.y - a.y
    val v2x = c.x - b.x
    val v2y = c.y - b.y
    val v1 = hypot(v1x, v1y)
    val v2 = hypot(v2x, v2y)
    if (v1 <= 0f || v2 <= 0f) return 0f
    val cos = ((v1x * v2x) + (v1y * v2y)) / (v1 * v2)
    val angle = Math.toDegrees(acos(cos.coerceIn(-1.0, 1.0)).toDouble()).toFloat()
    return angle
  }

  private fun speedFactor(stepPx: Float): Float =
    (stepPx / (SPEED_REFERENCE_DP * densityPx)).coerceIn(MIN_SPEED_FACTOR, MAX_SPEED_FACTOR)

  private fun baseExtrapolationDp(turnAngleDegrees: Float): Float = when {
    turnAngleDegrees < 2f -> 5.0f
    turnAngleDegrees < 4f -> 4.0f
    turnAngleDegrees < 12f -> 2.5f
    turnAngleDegrees < 18f -> 1.5f
    turnAngleDegrees < 22f -> 1.0f
    else -> 0.4f
  }

  private companion object {
    const val WINDOW_SIZE = 9
    const val MIN_STEP_DP = 0.75f
    const val SPEED_REFERENCE_DP = 15f
    const val MIN_SPEED_FACTOR = 0.5f
    const val MAX_SPEED_FACTOR = 2.5f
    const val DECELERATION_RATIO = 0.7f
    const val DECELERATION_DAMP = 0.4f
    const val MIN_PRESSURE_FOR_RATIO = 0.1f
    const val PRESSURE_SPIKE_RATIO = 1.25f
    const val PRESSURE_DAMP = 0.5f
    const val HIGH_REFRESH_HZ = 90f
    const val HIGH_REFRESH_BOOST = 1.4f
    const val MAX_EXTRAPOLATION_DP = 20f
  }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd /Users/broccoli/Project/pagenote/android && ./gradlew :feature-pen:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, `PenPredictorTest` all green.

- [ ] **Step 5: Commit**

```bash
cd /Users/broccoli/Project/pagenote
git add android/feature-pen/src/main/java/io/pagenote/feature/pen/PenPredictor.kt \
  android/feature-pen/src/test/java/io/pagenote/feature/pen/PenPredictorTest.kt
git commit -m "feat(android): add display-only stroke predictor"
```

---

### Task 3 (Wave 1): PenStrokeSmoother — midpoint quadratic smoothing

**Files:**
- Create: `android/feature-pen/src/main/java/io/pagenote/feature/pen/PenStrokeSmoother.kt`
- Test: `android/feature-pen/src/test/java/io/pagenote/feature/pen/PenStrokeSmootherTest.kt`

**Interfaces:**
- Consumes: `PenPoint` (existing).
- Produces (used by Task 5):
  - `object PenStrokeSmoother`
  - `data class Segment(startX, startY, controlX, controlY, endX, endY, width: Float)`
  - `fun segments(points: List<PenPoint>, baseWidth: Float): List<Segment>` — empty for fewer than three points; the chain starts at `points.first()`, ends at `points.last()`, and consecutive segments share endpoints.

Render-time only: stored `PenStroke.points` always keep raw samples.

- [ ] **Step 1: Write the failing tests**

```kotlin
package io.pagenote.feature.pen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PenStrokeSmootherTest {
  @Test
  fun fewerThanThreePointsProduceNoSegments() {
    assertTrue(PenStrokeSmoother.segments(emptyList(), 10f).isEmpty())
    assertTrue(PenStrokeSmoother.segments(listOf(PenPoint(0f, 0f, 1f)), 10f).isEmpty())
    assertTrue(
      PenStrokeSmoother.segments(
        listOf(PenPoint(0f, 0f, 1f), PenPoint(10f, 10f, 1f)), 10f,
      ).isEmpty(),
    )
  }

  @Test
  fun threeCollinearPointsKeepTheLineAndUseMidpointEnds() {
    val segments = PenStrokeSmoother.segments(
      listOf(PenPoint(0f, 0f, 1f), PenPoint(50f, 50f, 1f), PenPoint(100f, 100f, 1f)),
      10f,
    )
    assertEquals(2, segments.size)
    val first = segments[0]
    assertEquals(0f, first.startX, 0.001f)
    assertEquals(0f, first.startY, 0.001f)
    assertEquals(50f, first.controlX, 0.001f)
    assertEquals(50f, first.controlY, 0.001f)
    assertEquals(75f, first.endX, 0.001f)
    assertEquals(75f, first.endY, 0.001f)
    val second = segments[1]
    assertEquals(first.endX, second.startX, 0.001f)
    assertEquals(first.endY, second.startY, 0.001f)
    assertEquals(100f, second.endX, 0.001f)
    assertEquals(100f, second.endY, 0.001f)
  }

  @Test
  fun chainStartsAtTheFirstSampleAndEndsAtTheLast() {
    val points = listOf(
      PenPoint(0f, 0f, 1f), PenPoint(30f, 10f, 0.8f), PenPoint(60f, 0f, 1f),
      PenPoint(90f, 10f, 0.6f), PenPoint(120f, 0f, 1f),
    )
    val segments = PenStrokeSmoother.segments(points, 10f)
    assertEquals(points.size - 1, segments.size)
    assertEquals(points.first().x, segments.first().startX, 0.001f)
    assertEquals(points.first().y, segments.first().startY, 0.001f)
    assertEquals(points.last().x, segments.last().endX, 0.001f)
    assertEquals(points.last().y, segments.last().endY, 0.001f)
    segments.zipWithNext().forEach { (left, right) ->
      assertEquals(left.endX, right.startX, 0.001f)
      assertEquals(left.endY, right.startY, 0.001f)
    }
    // Every control point is an original sample.
    points.drop(1).dropLast(1).forEachIndexed { index, point ->
      assertEquals(point.x, segments[index].controlX, 0.001f)
      assertEquals(point.y, segments[index].controlY, 0.001f)
    }
  }

  @Test
  fun segmentWidthAveragesThePressuresOfItsTwoSamples() {
    val segments = PenStrokeSmoother.segments(
      listOf(PenPoint(0f, 0f, 1f), PenPoint(50f, 0f, 0.5f), PenPoint(100f, 0f, 0.2f)),
      10f,
    )
    // Asymmetric endpoint pressures pin the (control, next) / (previous, last)
    // pairing: the (p0, p1) reading would yield 10f * (1f + 0.5f) / 2f instead.
    assertEquals(10f * (0.5f + 0.2f) / 2f, segments[0].width, 0.001f)
    assertEquals(10f * (0.5f + 0.2f) / 2f, segments[1].width, 0.001f)
  }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd /Users/broccoli/Project/pagenote/android && ./gradlew :feature-pen:testDebugUnitTest`
Expected: COMPILATION ERROR — `PenStrokeSmoother` is unresolved.

- [ ] **Step 3: Implement PenStrokeSmoother**

```kotlin
package io.pagenote.feature.pen

/**
 * Render-time midpoint quadratic smoothing for freehand ink: samples become
 * control points, segment boundaries sit on sample midpoints, and the chain
 * still starts and ends on real samples. Storage always keeps raw points.
 */
object PenStrokeSmoother {

  data class Segment(
    val startX: Float,
    val startY: Float,
    val controlX: Float,
    val controlY: Float,
    val endX: Float,
    val endY: Float,
    val width: Float,
  )

  fun segments(points: List<PenPoint>, baseWidth: Float): List<Segment> {
    if (points.size < 3) return emptyList()
    val result = ArrayList<Segment>(points.size - 1)
    var startX = points.first().x
    var startY = points.first().y
    for (index in 1 until points.size - 1) {
      val control = points[index]
      val next = points[index + 1]
      val endX = (control.x + next.x) / 2f
      val endY = (control.y + next.y) / 2f
      result.add(
        Segment(
          startX = startX,
          startY = startY,
          controlX = control.x,
          controlY = control.y,
          endX = endX,
          endY = endY,
          width = baseWidth * (control.pressure + next.pressure) / 2f,
        ),
      )
      startX = endX
      startY = endY
    }
    val previous = points[points.size - 2]
    val last = points.last()
    result.add(
      Segment(
        startX = startX,
        startY = startY,
        controlX = previous.x,
        controlY = previous.y,
        endX = last.x,
        endY = last.y,
        width = baseWidth * (previous.pressure + last.pressure) / 2f,
      ),
    )
    return result
  }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd /Users/broccoli/Project/pagenote/android && ./gradlew :feature-pen:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, `PenStrokeSmootherTest` all green.

- [ ] **Step 5: Commit**

```bash
cd /Users/broccoli/Project/pagenote
git add android/feature-pen/src/main/java/io/pagenote/feature/pen/PenStrokeSmoother.kt \
  android/feature-pen/src/test/java/io/pagenote/feature/pen/PenStrokeSmootherTest.kt
git commit -m "feat(android): add midpoint stroke smoother"
```

---

### Task 4 (Wave 2): PenStrokeCache — committed-ink raster

**Files:**
- Create: `android/feature-pen/src/main/java/io/pagenote/feature/pen/PenStrokeCache.kt`
- Test: `android/feature-pen/src/androidTest/java/io/pagenote/feature/pen/PenStrokeCacheInstrumentedTest.kt`

**Interfaces:**
- Consumes: `PenStrokePainter.drawStroke` (Task 1), `PenCanvasGeometry` (existing).
- Produces (used by Task 6):
  - `class PenStrokeCache`
  - `fun setStrokes(strokes: List<PenStroke>)` — full replace; no-op when the list is equal to the cached one
  - `fun appendStroke(stroke: PenStroke, geometry: PenCanvasGeometry)` — fast path draws one stroke onto the current raster; falls back to a dirty full rebuild when the raster is stale
  - `fun drawInto(target: Canvas, geometry: PenCanvasGeometry, widthPx: Int, heightPx: Int)` — ensures the raster matches size/geometry/content, then blits it at (0, 0)
  - `fun clear()` — recycles the bitmap

The raster is the Kotlin counterpart of the Newskyer kernel's page handle: committed ink lives in one view-pixel `ARGB_8888` bitmap, and rebuilds are lazy (only when size, geometry key, or content changed). Zoom/pan changes the geometry key every frame, so those frames pay a full re-render — identical to today's cost, and inking frames (the latency-critical path) become a single blit.

- [ ] **Step 1: Write the failing instrumentation tests**

```kotlin
package io.pagenote.feature.pen

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PenStrokeCacheInstrumentedTest {
  private val size = 500

  private fun strokes(): List<PenStroke> = listOf(
    PenStroke(
      "pen", "#dc3545", 8.0,
      listOf(PenPoint(100f, 100f, 1f), PenPoint(400f, 300f, 0.5f), PenPoint(700f, 100f, 1f)),
    ),
    PenStroke(
      "highlight", "#ffff00", 40.0,
      listOf(PenPoint(100f, 500f, 1f), PenPoint(700f, 500f, 1f), PenPoint(100f, 500f, 1f)),
    ),
    PenStroke("line", "#000000", 4.0, listOf(PenPoint(100f, 700f, 1f), PenPoint(700f, 900f, 1f))),
    PenStroke("arrow", "#000000", 4.0, listOf(PenPoint(700f, 700f, 1f), PenPoint(100f, 900f, 1f))),
    PenStroke("rect", "#000000", 4.0, listOf(PenPoint(800f, 100f, 1f), PenPoint(950f, 300f, 1f))),
    PenStroke(
      "ellipse", "#000000", 4.0, listOf(PenPoint(820f, 500f, 1f), PenPoint(950f, 700f, 1f)),
    ),
  )

  private fun renderDirect(strokes: List<PenStroke>, geometry: PenCanvasGeometry): Bitmap =
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
      val canvas = Canvas(bitmap)
      val save = canvas.save()
      canvas.translate(geometry.pageOriginX, geometry.pageOriginY)
      canvas.scale(geometry.pixelsPerDocumentUnit, geometry.pixelsPerDocumentUnit)
      strokes.forEach { PenStrokePainter.drawStroke(canvas, it, geometry) }
      canvas.restoreToCount(save)
    }

  private fun renderCached(
    strokes: List<PenStroke>,
    geometry: PenCanvasGeometry,
    configure: (PenStrokeCache) -> Unit = { it.setStrokes(strokes) },
  ): Bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
    val cache = PenStrokeCache()
    configure(cache)
    cache.drawInto(Canvas(bitmap), geometry, size, size)
  }

  private fun assertSamePixels(expected: Bitmap, actual: Bitmap) {
    val expectedPixels = IntArray(size * size)
    val actualPixels = IntArray(size * size)
    expected.getPixels(expectedPixels, 0, size, 0, 0, size, size)
    actual.getPixels(actualPixels, 0, size, 0, 0, size, size)
    assertArrayEquals(expectedPixels, actualPixels)
  }

  @Test
  fun cacheBlitMatchesDirectRenderingForEveryStrokeType() {
    val geometry = PenCanvasGeometry(size.toFloat(), size.toFloat())
    assertSamePixels(renderDirect(strokes(), geometry), renderCached(strokes(), geometry))
  }

  @Test
  fun appendStrokeMatchesTheEquivalentFullReplace() {
    val geometry = PenCanvasGeometry(size.toFloat(), size.toFloat())
    val list = strokes()
    val appended = renderCached(list, geometry) { cache ->
      cache.setStrokes(list.dropLast(1))
      cache.appendStroke(list.last(), geometry)
    }
    assertSamePixels(renderDirect(list, geometry), appended)
  }

  @Test
  fun transformChangeRebuildsWithTheNewGeometry() {
    val identity = PenCanvasGeometry(size.toFloat(), size.toFloat())
    val zoomed = PenCanvasGeometry(
      size.toFloat(), size.toFloat(),
      PenCanvasPageTransform(pageWidthPx = 400f, pageHeightPx = 400f, scale = 1f),
    )
    val list = strokes()
    // Prime the raster under the identity geometry, then draw under a
    // different page transform: the key mismatch must force a full rebuild.
    val cached = renderCached(list, zoomed) { cache ->
      cache.setStrokes(list)
      cache.drawInto(
        Canvas(Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)),
        identity, size, size,
      )
    }
    assertSamePixels(renderDirect(list, zoomed), cached)
  }

  @Test
  fun removingAStrokeThroughSetStrokesRemovesItsInk() {
    val geometry = PenCanvasGeometry(size.toFloat(), size.toFloat())
    val list = strokes()
    val cached = renderCached(list.dropLast(1), geometry) { cache ->
      cache.setStrokes(list)
      cache.setStrokes(list.dropLast(1))
    }
    assertSamePixels(renderDirect(list.dropLast(1), geometry), cached)
  }

  @Test
  fun strokesFullyOutsideTheViewportAreSkippedWithoutChangingPixels() {
    val geometry = PenCanvasGeometry(size.toFloat(), size.toFloat())
    val inside = strokes()
    val withOutside = inside + PenStroke(
      "pen", "#000000", 8.0,
      listOf(PenPoint(-500f, -500f, 1f), PenPoint(-400f, -400f, 1f)),
    )
    assertSamePixels(
      renderDirect(inside, geometry),
      renderCached(withOutside, geometry),
    )
  }
}
```

Note: these tests only need a software `Canvas`; they still run as instrumentation tests because `Bitmap`/`Canvas` require Android. All tests run on the instrumentation thread; no UI thread is needed because no `View` is involved.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd /Users/broccoli/Project/pagenote/android && ./gradlew :feature-pen:connectedDebugAndroidTest`
Expected: COMPILATION ERROR — `PenStrokeCache` is unresolved.

- [ ] **Step 3: Implement PenStrokeCache**

```kotlin
package io.pagenote.feature.pen

import android.graphics.Bitmap
import android.graphics.Canvas

/**
 * View-pixel raster of committed strokes — the Kotlin counterpart of the
 * page handle in the Newskyer PaintView kernel. Committed ink renders once
 * into this layer; the owner draws only live ink on top per frame.
 * Rebuilds are lazy: they happen when the view size, the geometry key, or
 * the committed list changes, and skip strokes outside the viewport.
 */
class PenStrokeCache {
  private var bitmap: Bitmap? = null
  private var rasterCanvas: Canvas? = null
  private var committed: List<PenStroke> = emptyList()
  private var key: CacheKey? = null
  private var dirty = false

  fun setStrokes(strokes: List<PenStroke>) {
    if (strokes != committed) {
      committed = strokes
      dirty = true
    }
  }

  fun appendStroke(stroke: PenStroke, geometry: PenCanvasGeometry) {
    committed = committed + stroke
    val canvas = rasterCanvas
    if (canvas == null || dirty || key != CacheKey.from(geometry)) {
      dirty = true
      return
    }
    drawStrokeInto(canvas, stroke, geometry)
  }

  fun drawInto(target: Canvas, geometry: PenCanvasGeometry, widthPx: Int, heightPx: Int) {
    if (widthPx <= 0 || heightPx <= 0 || !geometry.isValid) return
    val cacheKey = CacheKey.from(geometry)
    val current = bitmap
    if (current == null || current.width != widthPx || current.height != heightPx ||
      cacheKey != key || dirty
    ) {
      rebuild(widthPx, heightPx, geometry, cacheKey)
    }
    bitmap?.let { target.drawBitmap(it, 0f, 0f, null) }
  }

  fun clear() {
    bitmap?.recycle()
    bitmap = null
    rasterCanvas = null
    committed = emptyList()
    key = null
    dirty = false
  }

  private fun rebuild(
    widthPx: Int,
    heightPx: Int,
    geometry: PenCanvasGeometry,
    cacheKey: CacheKey,
  ) {
    bitmap?.recycle()
    val newBitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val newCanvas = Canvas(newBitmap)
    committed.forEach { stroke ->
      if (intersectsViewport(stroke, geometry, widthPx, heightPx)) {
        drawStrokeInto(newCanvas, stroke, geometry)
      }
    }
    bitmap = newBitmap
    rasterCanvas = newCanvas
    key = cacheKey
    dirty = false
  }

  private fun drawStrokeInto(target: Canvas, stroke: PenStroke, geometry: PenCanvasGeometry) {
    val save = target.save()
    target.translate(geometry.pageOriginX, geometry.pageOriginY)
    target.scale(geometry.pixelsPerDocumentUnit, geometry.pixelsPerDocumentUnit)
    PenStrokePainter.drawStroke(target, stroke, geometry)
    target.restoreToCount(save)
  }

  private fun intersectsViewport(
    stroke: PenStroke,
    geometry: PenCanvasGeometry,
    widthPx: Int,
    heightPx: Int,
  ): Boolean {
    val points = stroke.points
    if (points.isEmpty() || stroke.type !in RASTER_TYPES) return false
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    val pad = stroke.width.toFloat() + 1f
    val left = geometry.pageOriginX + (minX - pad) * geometry.pixelsPerDocumentUnit
    val top = geometry.pageOriginY + (minY - pad) * geometry.pixelsPerDocumentUnit
    val right = geometry.pageOriginX + (maxX + pad) * geometry.pixelsPerDocumentUnit
    val bottom = geometry.pageOriginY + (maxY + pad) * geometry.pixelsPerDocumentUnit
    return right >= 0f && bottom >= 0f && left <= widthPx && top <= heightPx
  }

  private data class CacheKey(
    val pageOriginX: Float,
    val pageOriginY: Float,
    val pixelsPerDocumentUnit: Float,
  ) {
    companion object {
      fun from(geometry: PenCanvasGeometry) = CacheKey(
        pageOriginX = geometry.pageOriginX,
        pageOriginY = geometry.pageOriginY,
        pixelsPerDocumentUnit = geometry.pixelsPerDocumentUnit,
      )
    }
  }

  private companion object {
    val RASTER_TYPES = setOf("pen", "highlight", "line", "arrow", "rect", "ellipse")
  }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd /Users/broccoli/Project/pagenote/android && ./gradlew :feature-pen:connectedDebugAndroidTest`
Expected: BUILD SUCCESSFUL, `PenStrokeCacheInstrumentedTest` all green, existing suites still green.

- [ ] **Step 5: Commit**

```bash
cd /Users/broccoli/Project/pagenote
git add android/feature-pen/src/main/java/io/pagenote/feature/pen/PenStrokeCache.kt \
  android/feature-pen/src/androidTest/java/io/pagenote/feature/pen/PenStrokeCacheInstrumentedTest.kt
git commit -m "feat(android): add committed-stroke raster cache"
```

---

### Task 5 (Wave 2): Smoothed freehand rendering in the painter

**Files:**
- Modify: `android/feature-pen/src/main/java/io/pagenote/feature/pen/PenStrokePainter.kt`

**Interfaces:**
- Consumes: `PenStrokeSmoother.segments` (Task 3).
- Produces: `PenStrokePainter.drawFreehand` now renders 3+ point ink through smoothed quadratic segments; 1-point dots and 2-point lines are unchanged in appearance.

- [ ] **Step 1: Replace drawFreehand with the smoothed version**

In `PenStrokePainter.kt`, replace `drawFreehand` and add a scratch path (the object is confined to the UI thread):

```kotlin
  private val scratchPath = Path()

  fun drawFreehand(canvas: Canvas, points: List<PenPoint>, width: Float) {
    strokePaint.style = Paint.Style.STROKE
    strokePaint.alpha = 255
    if (points.size == 1) {
      strokePaint.style = Paint.Style.FILL
      canvas.drawCircle(points[0].x, points[0].y, width * points[0].pressure / 2f, strokePaint)
      return
    }
    if (points.size == 2) {
      val previous = points[0]
      val current = points[1]
      strokePaint.strokeWidth = width * (previous.pressure + current.pressure) / 2f
      canvas.drawLine(previous.x, previous.y, current.x, current.y, strokePaint)
      return
    }
    PenStrokeSmoother.segments(points, width).forEach { segment ->
      strokePaint.strokeWidth = segment.width
      scratchPath.rewind()
      scratchPath.moveTo(segment.startX, segment.startY)
      scratchPath.quadTo(segment.controlX, segment.controlY, segment.endX, segment.endY)
      canvas.drawPath(scratchPath, strokePaint)
    }
  }
```

- [ ] **Step 2: Run the JVM suite**

Run: `cd /Users/broccoli/Project/pagenote/android && ./gradlew :feature-pen:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run the instrumentation pixel suites**

Run: `cd /Users/broccoli/Project/pagenote/android && ./gradlew :feature-pen:connectedDebugAndroidTest`
Expected: all existing tests pass. The highlighter pixel tests use collinear or single-dot geometry where midpoint smoothing is a no-op, so their assertions hold; if an exact-pixel expectation shifts because of smoothing, update that expectation in the same commit with a comment stating the smoothed geometry.

- [ ] **Step 4: Commit**

```bash
cd /Users/broccoli/Project/pagenote
git add android/feature-pen/src/main/java/io/pagenote/feature/pen/PenStrokePainter.kt
git commit -m "feat(android): render freehand ink with midpoint smoothing"
```

---

### Task 6 (Wave 3): Cache-backed PenCanvasView

**Files:**
- Modify: `android/feature-pen/src/main/java/io/pagenote/feature/pen/PenCanvasView.kt`

**Interfaces:**
- Consumes: `PenStrokeCache` (Task 4), `PenStrokePainter` (Tasks 1+5).
- Produces: unchanged public API. `onDraw` becomes: blit the cache, then draw only the live overlay (active gesture, laser, selection, shape preview) in a transformed block.

The cache is fed exclusively at the three `internalStrokes` mutation sites (`strokes` setter, `eraseAt`, `finishStroke`) — never per frame, so per-frame cost drops from O(total points) to one bitmap blit plus the active gesture.

- [ ] **Step 1: Add the cache field and feed the mutation sites**

In `PenCanvasView.kt`:

1. Add the field next to `internalStrokes`:

```kotlin
  private val strokeCache = PenStrokeCache()
```

2. `strokes` setter:

```kotlin
  var strokes: List<PenStroke>
    get() = internalStrokes
    set(value) {
      internalStrokes = value.toList()
      strokeCache.setStrokes(internalStrokes)
      invalidate()
    }
```

3. `eraseAt` — after removing the stroke:

```kotlin
  private fun eraseAt(xPx: Float, yPx: Float) {
    val index = geometry()
      .hitTest(internalStrokes, xPx, yPx, eraserRadiusPx) ?: return
    internalStrokes = internalStrokes.toMutableList().also { it.removeAt(index) }
    strokeCache.setStrokes(internalStrokes)
    onStrokeErased?.invoke(index)
    invalidate()
  }
```

4. `finishStroke` — after the non-consumed append:

```kotlin
    if (!consumed) {
      internalStrokes = internalStrokes + stroke
      strokeCache.appendStroke(stroke, geometry())
      onStrokeAdded?.invoke(stroke)
    }
```

5. `onDetachedFromWindow` — add `strokeCache.clear()` before `super.onDetachedFromWindow()`.

- [ ] **Step 2: Rewrite onDraw around the cache**

```kotlin
  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    if (width <= 0 || height <= 0) return
    val geometry = geometry()
    if (!geometry.isValid) return
    strokeCache.drawInto(canvas, geometry, width, height)
    val save = canvas.save()
    canvas.translate(geometry.pageOriginX, geometry.pageOriginY)
    canvas.scale(geometry.pixelsPerDocumentUnit, geometry.pixelsPerDocumentUnit)
    if (laserTrail.isNotEmpty()) {
      PenStrokePainter.drawLaser(canvas, laserTrail, laserColor, laserWidth, geometry)
    }
    selectedIndex?.let { index -> internalStrokes.getOrNull(index)?.let { drawSelection(canvas, it) } }
    gestureTool?.takeIf { it != PenCanvasTool.ERASER }?.let { tool ->
      if (activePoints.isNotEmpty()) {
        val preview = shapePreview
        if (tool == PenCanvasTool.LASER) {
          PenStrokePainter.drawLaser(canvas, activePoints, activeColor, activeWidth, geometry)
        } else if (preview != null) {
          PenStrokePainter.drawStroke(
            canvas,
            PenStroke(preview.type, activeColor, activeWidth, listOf(preview.first, preview.second)),
            geometry,
          )
        } else {
          PenStrokePainter.drawStroke(
            canvas,
            PenStroke(
              type = tool.persistedType ?: "pen",
              color = activeColor,
              width = activeWidth,
              points = activePoints.toList(),
            ),
            geometry,
          )
        }
      }
    }
    canvas.restoreToCount(save)
  }
```

- [ ] **Step 3: Run the JVM suite**

Run: `cd /Users/broccoli/Project/pagenote/android && ./gradlew :feature-pen:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run the full instrumentation suite — the behavior-preservation gate**

Run: `cd /Users/broccoli/Project/pagenote/android && ./gradlew :feature-pen:connectedDebugAndroidTest`
Expected: BUILD SUCCESSFUL with every existing test passing **unchanged**. The 1:1 unfiltered blit of a software-rendered raster is pixel-identical to drawing the same ops directly, including the translucent highlighter layer.

- [ ] **Step 5: Commit**

```bash
cd /Users/broccoli/Project/pagenote
git add android/feature-pen/src/main/java/io/pagenote/feature/pen/PenCanvasView.kt
git commit -m "perf(android): draw committed ink from the raster cache"
```

---

### Task 7 (Wave 4): Live prediction wiring

**Files:**
- Modify: `android/feature-pen/src/main/java/io/pagenote/feature/pen/PenStrokePainter.kt` (one new helper)
- Modify: `android/feature-pen/src/main/java/io/pagenote/feature/pen/PenCanvasView.kt`
- Test: `android/feature-pen/src/androidTest/java/io/pagenote/feature/pen/PenPredictionInstrumentedTest.kt`

**Interfaces:**
- Consumes: `PenPredictor` (Task 2), cache-backed onDraw (Task 6).
- Produces: the predicted tail is drawn only while a pen/ballpoint/highlighter gesture is active with no shape preview; it is clamped through the same `toDocumentPoint` pipeline as real input and never persisted.

- [ ] **Step 1: Write the failing instrumentation tests**

```kotlin
package io.pagenote.feature.pen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PenPredictionInstrumentedTest {
  private val size = 500

  @Test
  fun liveTailExtendsBeyondTheLastRealSample() = onUi {
    val view = createCanvas().apply {
      selectedTool = PenCanvasTool.PEN
      penColor = "#000000"
      penWidth = 20.0
    }
    touch(view, MotionEvent.ACTION_DOWN, 60f, 200f, 2L)
    repeat(9) { index ->
      touch(view, MotionEvent.ACTION_MOVE, 60f + (index + 1) * 30f, 200f, 3L + index)
    }
    val lastRealViewX = 60f + 9 * 30f
    val halfStrokePx = 20f / 2f * 0.5f
    withBitmap(view) { bitmap ->
      val row = IntArray(size)
      bitmap.getPixels(row, 0, size, 0, 200, size, 1)
      val rightmost = row.indices.filter { Color.alpha(row[it]) > 0 }.max()
      assertTrue(
        "ink should extend beyond the round cap at ${lastRealViewX + halfStrokePx} but reached $rightmost",
        rightmost > lastRealViewX + halfStrokePx,
      )
    }
    touch(view, MotionEvent.ACTION_UP, lastRealViewX, 200f, 20L)
    val stroke = view.strokes.single()
    assertEquals(10, stroke.points.size)
    assertEquals(660f, stroke.points.last().x, 0.001f)
  }

  @Test
  fun shortGesturesDoNotDrawAPredictedTail() = onUi {
    val view = createCanvas().apply {
      selectedTool = PenCanvasTool.PEN
      penColor = "#000000"
      penWidth = 20.0
    }
    touch(view, MotionEvent.ACTION_DOWN, 60f, 200f, 2L)
    repeat(3) { index ->
      touch(view, MotionEvent.ACTION_MOVE, 60f + (index + 1) * 30f, 200f, 3L + index)
    }
    val lastRealViewX = 60f + 3 * 30f
    val halfStrokePx = 20f / 2f * 0.5f
    withBitmap(view) { bitmap ->
      val row = IntArray(size)
      bitmap.getPixels(row, 0, size, 0, 200, size, 1)
      val rightmost = row.indices.filter { Color.alpha(row[it]) > 0 }.max()
      assertTrue(
        "no predicted tail expected, ink reached $rightmost past $lastRealViewX",
        rightmost <= lastRealViewX + halfStrokePx + 1,
      )
    }
    touch(view, MotionEvent.ACTION_UP, lastRealViewX, 200f, 20L)
  }

  private fun createCanvas() = PenCanvasView(
    InstrumentationRegistry.getInstrumentation().targetContext,
  ).apply {
    val spec = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
    measure(spec, spec)
    layout(0, 0, size, size)
  }

  private fun withBitmap(view: PenCanvasView, check: (Bitmap) -> Unit) {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    try {
      view.draw(Canvas(bitmap))
      check(bitmap)
    } finally {
      bitmap.recycle()
    }
  }

  private fun touch(view: PenCanvasView, action: Int, x: Float, y: Float, time: Long) {
    val pointer = MotionEvent.PointerProperties().apply {
      id = 0
      toolType = MotionEvent.TOOL_TYPE_STYLUS
    }
    val coordinates = MotionEvent.PointerCoords().apply {
      this.x = x
      this.y = y
      pressure = 1f
      size = 0.1f
    }
    val event = MotionEvent.obtain(
      1L, time, action, 1, arrayOf(pointer), arrayOf(coordinates),
      0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0,
    )
    try {
      view.dispatchTouchEvent(event)
    } finally {
      event.recycle()
    }
  }

  private fun onUi(action: () -> Unit) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
  }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd /Users/broccoli/Project/pagenote/android && ./gradlew :feature-pen:connectedDebugAndroidTest`
Expected: `liveTailExtendsBeyondTheLastRealSample` FAILS (ink stops at the round cap) — `PenPredictor` is not wired into the view yet. If it fails to compile first, that also counts as the failing state.

- [ ] **Step 3: Add the segment helper to PenStrokePainter**

```kotlin
  fun drawSegment(canvas: Canvas, from: PenPoint, to: PenPoint, color: String, width: Float) {
    val parsed = runCatching { Color.parseColor(color) }.getOrDefault(Color.BLACK)
    strokePaint.color = parsed
    strokePaint.alpha = Color.alpha(parsed)
    strokePaint.style = Paint.Style.STROKE
    strokePaint.strokeWidth = width * (from.pressure + to.pressure) / 2f
    canvas.drawLine(from.x, from.y, to.x, to.y, strokePaint)
  }
```

- [ ] **Step 4: Wire the predictor into PenCanvasView**

1. Add the field (density and refresh rate come from the display; both fall back safely):

```kotlin
  private val predictor = PenPredictor(
    densityPx = resources.displayMetrics.density,
    refreshRateHz = context.display?.refreshRate ?: 60f,
  )
```

2. `beginGesture` — first statement after `requestUnbufferedDispatch(event)`:

```kotlin
    predictor.onDown()
```

3. `ACTION_MOVE` ink branch — feed every sample before mapping it:

```kotlin
        } else {
          for (history in 0 until event.historySize) {
            val hx = event.getHistoricalX(index, history)
            val hy = event.getHistoricalY(index, history)
            val hp = event.getHistoricalPressure(index, history)
            predictor.onMove(hx, hy, hp)
            addPoint(hx, hy, hp)
          }
          val x = event.getX(index)
          val y = event.getY(index)
          val p = event.getPressure(index)
          predictor.onMove(x, y, p)
          addPoint(x, y, p)
          updateShapeRecognition(x, y)
        }
```

4. `resetGesture` — add `predictor.onUp()` as the first statement (covers UP, POINTER_UP, and CANCEL).

5. `onDraw` — inside the transformed block, right after the active-gesture preview block (before `canvas.restoreToCount(save)`):

```kotlin
    predictedDocumentPoint()?.let { predicted ->
      activePoints.lastOrNull()?.let { last ->
        PenStrokePainter.drawSegment(canvas, last, predicted, activeColor, activeWidth.toFloat())
      }
    }
```

6. Add the helper:

```kotlin
  private fun predictedDocumentPoint(): PenPoint? {
    if (shapePreview != null) return null
    val tool = gestureTool ?: return null
    if (tool != PenCanvasTool.PEN && tool != PenCanvasTool.BALLPOINT &&
      tool != PenCanvasTool.HIGHLIGHTER
    ) {
      return null
    }
    if (activePointerId == INVALID_POINTER || activePoints.isEmpty()) return null
    val prediction = predictor.predict() ?: return null
    return geometry().toDocumentPoint(
      prediction.x,
      prediction.y,
      activePoints.last().pressure,
    )
  }
```

- [ ] **Step 5: Run the JVM suite and the full instrumentation suite**

Run: `cd /Users/broccoli/Project/pagenote/android && ./gradlew :feature-pen:testDebugUnitTest :feature-pen:connectedDebugAndroidTest`
Expected: BUILD SUCCESSFUL, `PenPredictionInstrumentedTest` green, all existing suites still green.

- [ ] **Step 6: Commit**

```bash
cd /Users/broccoli/Project/pagenote
git add android/feature-pen/src/main/java/io/pagenote/feature/pen/PenStrokePainter.kt \
  android/feature-pen/src/main/java/io/pagenote/feature/pen/PenCanvasView.kt \
  android/feature-pen/src/androidTest/java/io/pagenote/feature/pen/PenPredictionInstrumentedTest.kt
git commit -m "feat(android): extrapolate live ink with the pen predictor"
```

---

### Task 8 (Wave 5): Spec, changelog, and full verification

**Files:**
- Modify: `android/docs/spec/features/pen-canvas.md`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: everything landed in Tasks 1–7.
- Produces: documented contract + changelog; the branch is ready for PR review against `beta`.

- [ ] **Step 1: Extend the pen-canvas spec**

Append to `android/docs/spec/features/pen-canvas.md`:

```markdown
## Rendering Pipeline

- Committed strokes render once into a view-pixel raster (`PenStrokeCache`)
  that is blitted each frame; the live overlay draws only the active gesture,
  laser trail, shape preview, and selection. Rebuilds happen only when the
  view size, page transform, or committed list changes, and skip strokes
  outside the viewport. The blit is pixel-identical to direct rendering for
  every supported stroke type, including one-coat-per-gesture highlighter.
- Freehand ink with three or more points renders through midpoint quadratic
  smoothing (`PenStrokeSmoother`): samples become control points, the chain
  starts and ends on real samples, and per-segment width keeps the stored
  pressure modulation. One-point dots and two-point lines are unchanged.
  Stored `PenStroke.points` always keep raw samples.

## Live Prediction

- While a pen, ballpoint, or highlighter gesture is active and no shape
  preview is showing, `PenPredictor` extrapolates a display-only tail from
  the last nine input samples: turn angle buckets the extrapolation,
  deceleration and pressure spikes damp it, and refresh rates above 90 Hz
  lengthen it. The predicted point is mapped through the same
  `toDocumentPoint` clamping as real input and is never written to storage;
  the stored stroke always ends at the last real sample.
- Prediction is display-only and best-effort: it must not change stroke
  geometry, persistence, erasing, selection, or shape recognition.

## Verification addendum

- Add the date, the AVD used (`Pixel_Tablet_API34`), the JVM and
  instrumentation counts, and the explicit limits: pixel checks use
  bitmap-backed software canvases and synthetic events; physical-device
  latency and prediction quality still require real stylus testing.
```

Adapt the wording to the actual verification results of this run (see Step 3).

- [ ] **Step 2: Add the CHANGELOG entry**

Append to the Unreleased section of `CHANGELOG.md` (match the existing bilingual style, one English sentence then the Chinese translation):

```markdown
- Rebuild the Android pen canvas rendering pipeline: committed ink renders once into a view-pixel raster blitted each frame, freehand ink renders through midpoint quadratic smoothing, and a display-only predictor extrapolates the live tail from the last nine samples with turn, speed, pressure, and refresh-rate damping. Stored annotations, the public canvas API, and desktop behavior are unchanged; verified with new JVM/instrumentation suites plus all existing pixel regressions on the API 34 tablet AVD. Local debug build only, no public release. / 重构安卓手写渲染管线：已提交笔迹渲染进逐帧位图缓存的视图像素层，自由笔迹改为中点二次贝塞尔平滑，新增仅用于显示的落笔预测（九点窗口，按转角、速度、压感和刷新率调节）。存储的批注数据、画布公开接口与桌面行为均不变；新增 JVM/仪器测试并通过全部既有像素回归（API 34 平板 AVD）。仅本地 debug 构建，不发布公开版本。
```

- [ ] **Step 3: Run the full verification set**

Run:
```bash
cd /Users/broccoli/Project/pagenote/android
./gradlew :feature-pen:testDebugUnitTest
./gradlew :feature-pen:connectedDebugAndroidTest
./gradlew :app:assembleDebug
```
Expected: all three BUILD SUCCESSFUL. Record the test counts for the spec addendum and changelog. If the app module fails to compile, the public API constraint was violated — fix before committing.

- [ ] **Step 4: Commit**

```bash
cd /Users/broccoli/Project/pagenote
git add android/docs/spec/features/pen-canvas.md CHANGELOG.md
git commit -m "docs(android): record ink pipeline spec and changelog"
```
