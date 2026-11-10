package io.pagenote.android

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import io.github.sceneview.node.GeometryNode
import com.google.android.filament.EntityManager
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/** Native Filament rendering adapter for the shared Markdown S genetic-lab state. */
internal class MarkdownSChromosomeSceneView(
  context: Context,
  genes: List<Int>,
  initialAngle: Double,
  animate: Boolean,
  private var enabled: Boolean,
  private val onCommit: (geneIndex: Int?, angle: Double) -> Unit,
) : SceneView(context, isOpaque = true) {
  // SceneView 2.3.0 otherwise sets FLAG_KEEP_SCREEN_ON on the containing Activity.
  override val activity: androidx.activity.ComponentActivity? get() = null
  private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
  private val root = Node(engine).apply { name = "markdowns_chromosome_root" }
  private val basePairMaterials = listOf(
    materialLoader.createColorInstance(GENE_ZERO, metallic = 0.12f, roughness = 0.34f),
    materialLoader.createColorInstance(GENE_ONE, metallic = 0.12f, roughness = 0.34f),
  )
  private val backboneMaterials = listOf(
    materialLoader.createColorInstance(BACKBONE_A, metallic = 0.1f, roughness = 0.42f),
    materialLoader.createColorInstance(BACKBONE_B, metallic = 0.1f, roughness = 0.42f),
  )
  private var angle = initialAngle.toFloat()
  private var downX = 0f
  private var downY = 0f
  private var lastX = 0f
  private var lastRawY = 0f
  private var moved = false
  private var verticalGesture = false
  private var disposed = false
  private var animator: ValueAnimator? = null
  private var autoRotate = animate
  private var boundAngle = initialAngle
  private var boundGenes = genes.toList()
  private val visibleBounds = Rect()
  private var sceneVisible = false
  private var lifecycleActive = false
  private val animationLifecycle = object : DefaultLifecycleObserver {
    override fun onResume(owner: LifecycleOwner) {
      lifecycleActive = true
      sceneVisible = false
    }

    override fun onPause(owner: LifecycleOwner) {
      lifecycleActive = false
      sceneVisible = false
      animator?.cancel()
      animator = null
    }
  }

  init {
    require(genes.size == GENE_COUNT && genes.all { it == 0 || it == 1 })
    cameraManipulator = null
    cameraNode.position = Position(0f, 0f, 5.1f)
    addChildNode(root)
    buildHelix(genes)
    root.rotation = Rotation(0f, Math.toDegrees(angle.toDouble()).toFloat(), 0f)
    contentDescription = "三维染色体，可拖动旋转，轻触基因切换"
    onTouchEvent = ::handleSceneTouch
  }

  fun rotationAngle(): Double = angle.toDouble()

  fun bindState(genes: List<Int>, initialAngle: Double, animate: Boolean, enabled: Boolean) {
    check(!disposed)
    require(genes.size == GENE_COUNT && genes.all { it == 0 || it == 1 })
    this.enabled = enabled
    if (boundGenes != genes) {
      root.childNodes.filterIsInstance<GeometryNode>().forEach { node ->
        val index = geneIndex(node.name)
        if (index != null) node.materialInstance = basePairMaterials[genes[index]]
      }
      boundGenes = genes.toList()
    }
    if (boundAngle != initialAngle) {
      boundAngle = initialAngle
      angle = initialAngle.toFloat()
      root.rotation = Rotation(0f, Math.toDegrees(angle.toDouble()).toFloat(), 0f)
    }
    if (autoRotate != animate) {
      autoRotate = animate
      animator?.cancel()
      animator = null
      if (animate && sceneVisible && ValueAnimator.areAnimatorsEnabled()) startRotation()
    }
  }

  fun dispose() = destroy()

  override fun destroy() {
    if (disposed) return
    disposed = true
    lifecycle?.removeObserver(animationLifecycle)
    animator?.cancel()
    animator = null
    removeChildNode(root)
    root.childNodes.toList().forEach(::destroyNodeTree)
    engine.destroyEntity(root.entity)
    EntityManager.get().destroy(root.entity)
    super.destroy()
  }

  override fun onDetachedFromWindow() {
    dispose()
    super.onDetachedFromWindow()
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    lifecycle?.addObserver(animationLifecycle)
  }

  override fun onFrame(frameTimeNanos: Long) {
    val visible = lifecycleActive && isShown && windowVisibility == VISIBLE && getGlobalVisibleRect(visibleBounds)
    if (visible != sceneVisible) {
      sceneVisible = visible
      if (visible && autoRotate && ValueAnimator.areAnimatorsEnabled()) {
        startRotation()
      } else if (!visible) {
        animator?.cancel()
        animator = null
      }
    }
    if (visible) super.onFrame(frameTimeNanos)
  }

  private fun buildHelix(genes: List<Int>) {
    val materialA = backboneMaterials[0]
    val materialB = backboneMaterials[1]
    val pointsA = (0 until GENE_COUNT).map { helixPoint(it, strand = 0) }
    val pointsB = (0 until GENE_COUNT).map { helixPoint(it, strand = 1) }

    addBackbone(pointsA, materialA, "strand_a")
    addBackbone(pointsB, materialB, "strand_b")
    genes.forEachIndexed { index, gene ->
      val pointA = pointsA[index]
      val pointB = pointsB[index]
      val material = basePairMaterials[gene]
      val rung = cylinderBetween(pointA, pointB, radius = 0.034f, material = material).apply {
        name = "markdowns_gene_$index"
        isTouchable = true
      }
      root.addChildNode(rung)
      root.addChildNode(sphereAt(pointA, material, "markdowns_base_a_$index"))
      root.addChildNode(sphereAt(pointB, material, "markdowns_base_b_$index"))
    }
  }

  private fun addBackbone(points: List<Position>, material: com.google.android.filament.MaterialInstance, name: String) {
    for (index in 0 until points.lastIndex) {
      root.addChildNode(cylinderBetween(points[index], points[index + 1], 0.026f, material).apply {
        this.name = "markdowns_${name}_segment_$index"
        isTouchable = false
      })
      root.addChildNode(sphereAt(points[index], material, "markdowns_${name}_bead_$index").apply {
        isTouchable = false
      })
    }
    root.addChildNode(sphereAt(points.last(), material, "markdowns_${name}_bead_${GENE_COUNT - 1}").apply {
      isTouchable = false
    })
  }

  private fun helixPoint(index: Int, strand: Int): Position {
    val t = index / (GENE_COUNT - 1f)
    val phase = t * (2f * PI.toFloat() * 2f)
    val direction = if (strand == 0) 1f else -1f
    return Position(
      direction * kotlin.math.cos(phase) * 0.55f,
      1.3f - t * 2.6f,
      direction * kotlin.math.sin(phase) * 0.38f,
    )
  }

  private fun sphereAt(
    point: Position,
    material: com.google.android.filament.MaterialInstance,
    name: String,
  ) = SphereNode(engine = engine, radius = 0.078f, materialInstance = material).apply {
    this.name = name
    position = point
  }

  private fun cylinderBetween(
    start: Position,
    end: Position,
    radius: Float,
    material: com.google.android.filament.MaterialInstance,
  ) = CylinderNode(
    engine = engine,
    radius = radius,
    height = distance(start, end),
    materialInstance = material,
  ).apply {
    position = Position((start.x + end.x) / 2f, (start.y + end.y) / 2f, (start.z + end.z) / 2f)
    val dx = end.x - start.x
    val dy = end.y - start.y
    val dz = end.z - start.z
    val horizontal = sqrt(dx * dx + dy * dy)
    rotation = Rotation(
      Math.toDegrees(atan2(dz, horizontal).toDouble()).toFloat(),
      0f,
      Math.toDegrees(-atan2(dx, dy).toDouble()).toFloat(),
    )
  }

  private fun distance(start: Position, end: Position): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val dz = end.z - start.z
    return sqrt(dx * dx + dy * dy + dz * dz)
  }

  private fun startRotation() {
    if (disposed || !sceneVisible || animator != null) return
    val start = angle
    animator = ValueAnimator.ofFloat(start, start + 2f * PI.toFloat()).apply {
      duration = 12_000L
      repeatCount = ValueAnimator.INFINITE
      interpolator = LinearInterpolator()
      addUpdateListener {
        angle = it.animatedValue as Float
        root.rotation = Rotation(0f, Math.toDegrees(angle.toDouble()).toFloat(), 0f)
      }
      start()
    }
  }

  private fun handleSceneTouch(event: MotionEvent, hit: io.github.sceneview.collision.HitResult?): Boolean {
    if (!enabled || disposed) return false
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        downX = event.x
        downY = event.rawY
        lastRawY = event.rawY
        lastX = event.x
        moved = false
        verticalGesture = false
        parent?.requestDisallowInterceptTouchEvent(true)
        animator?.cancel()
        animator = null
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        val dx = kotlin.math.abs(event.x - downX)
        val dy = kotlin.math.abs(event.rawY - downY)
        if (!moved && dy > touchSlop && dy > dx) {
          verticalGesture = true
        }
        if (verticalGesture) {
          var ancestor = parent
          while (ancestor != null && ancestor !is android.widget.ScrollView) ancestor = ancestor.parent
          (ancestor as? android.widget.ScrollView)?.scrollBy(0, (lastRawY - event.rawY).toInt())
          lastRawY = event.rawY
          return true
        }
        if (!moved && (dx > touchSlop || dy > touchSlop)) {
          moved = true
        }
        if (moved) {
          angle += (event.x - lastX) / width.coerceAtLeast(1) * 2f * PI.toFloat()
          lastX = event.x
          root.rotation = Rotation(0f, Math.toDegrees(angle.toDouble()).toFloat(), 0f)
        }
        return true
      }
      MotionEvent.ACTION_UP -> {
        parent?.requestDisallowInterceptTouchEvent(false)
        if (verticalGesture) {
          if (autoRotate) startRotation()
          return true
        }
        if (!moved) {
          val index = geneIndex(hit?.node?.name)
          if (index == null) {
            if (autoRotate) startRotation()
          } else {
            val committedAngle = angle.toDouble()
            post { if (!disposed) onCommit(index, committedAngle) }
          }
        } else {
          val committedAngle = angle.toDouble()
          post { if (!disposed) onCommit(null, committedAngle) }
          if (animator == null && autoRotate) startRotation()
        }
        return true
      }
      MotionEvent.ACTION_CANCEL -> {
        parent?.requestDisallowInterceptTouchEvent(false)
        if (autoRotate) startRotation()
        return true
      }
    }
    return false
  }

  private fun destroyNodeTree(node: Node) {
    node.childNodes.toList().forEach(::destroyNodeTree)
    engine.destroyEntity(node.entity)
    if (node is GeometryNode) {
      engine.destroyVertexBuffer(node.geometry.vertexBuffer)
      engine.destroyIndexBuffer(node.geometry.indexBuffer)
    }
    EntityManager.get().destroy(node.entity)
  }

  private fun geneIndex(name: String?): Int? = name
    ?.let { value -> listOf(GENE_NODE_PREFIX, "markdowns_base_a_", "markdowns_base_b_").firstOrNull(value::startsWith)?.let(value::removePrefix) }
    ?.toIntOrNull()
    ?.takeIf { it in 0 until GENE_COUNT }

  companion object {
    const val GENE_COUNT = 12
    const val GENE_NODE_PREFIX = "markdowns_gene_"
    private const val GENE_ZERO = 0xff376d70.toInt()
    private const val GENE_ONE = 0xffa44c34.toInt()
    private const val BACKBONE_A = 0xff92ada1.toInt()
    private const val BACKBONE_B = 0xffc2a879.toInt()
  }
}
