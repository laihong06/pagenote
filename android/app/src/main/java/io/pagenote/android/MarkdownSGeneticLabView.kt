package io.pagenote.android

import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import io.pagenote.feature.notes.MarkdownSGeneration
import io.pagenote.feature.notes.MarkdownSGeneticAlgorithm
import io.pagenote.feature.notes.MarkdownSIndividual
import io.pagenote.feature.notes.MarkdownSState
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** Android-owned, data-driven rendering of the desktop genetic-lab teaching template. */
internal class MarkdownSGeneticLabView(
  context: Context,
  private val sceneFactory: (Context, List<Int>, Double, Boolean, Boolean, (Int?, Double) -> Unit) -> MarkdownSChromosomeSceneView = ::MarkdownSChromosomeSceneView,
  private val onStateChange: (expected: MarkdownSState, next: MarkdownSState) -> Unit,
) : LinearLayout(context) {
  private var state: MarkdownSState? = null
  private var enabledControls = true
  private val pad = dp(16)
  private var chromosome3d: View? = null

  init {
    orientation = VERTICAL
    setPadding(pad, pad, pad, pad)
    background = GradientDrawable().apply {
      setColor(PAPER)
      setStroke(dp(1), PAPER_RULE)
      cornerRadius = dp(2).toFloat()
    }
  }

  fun bind(next: MarkdownSState, @Suppress("UNUSED_PARAMETER") textColor: Int, @Suppress("UNUSED_PARAMETER") surfaceColor: Int, enabled: Boolean) {
    if (state == next && enabledControls == enabled) return
    state = next
    enabledControls = enabled
    render()
  }

  fun dispose() {
    state = null
    disposeChromosome()
    chromosome3d = null
    removeAllViews()
  }

  private fun commit(next: MarkdownSState) {
    next.validate()
    val previous = state ?: return
    if (previous == next) return
    state = next
    render()
    onStateChange(previous, next)
  }

  private fun render() {
    val current = state ?: return
    val retainedScene = chromosome3d as? MarkdownSChromosomeSceneView
    if (retainedScene == null) {
      disposeChromosome()
      chromosome3d = null
      removeAllViews()
    } else {
      for (index in childCount - 1 downTo 0) {
        if (getChildAt(index) !== retainedScene) removeViewAt(index)
      }
    }
    setBackgroundColor(PAPER)

    addView(label("人工演化 · 原生 Markdown S", 11f, PAPER_ACCENT, letterSpacing = 0.16f))
    addView(title("一次改变，看见新的可能"))
    addView(label("直接点击基因，或调整采样位置。状态写入当前 Markdown，随笔记保存。", 14f, PAPER_MUTED))
    addChapterNavigation()

    addDivider()
    addChapter("01 · 编码与变异", "编码与变异")
    val chromosome = ChromosomeView(context, current.genes, enabledControls) { index ->
      val genes = current.genes.toMutableList()
      index.forEach { gene -> genes[gene] = 1 - genes[gene] }
      commit(current.copy(genes = genes, selectedIndividual = null))
    }.apply { tag = "markdowns_chromosome" }
    addView(chromosome, LayoutParams(LayoutParams.MATCH_PARENT, dp(132)))
    addView(label(current.genes.joinToString(""), 25f, PAPER_ACCENT, serif = true).apply {
      tag = "markdowns_gene_sequence"
      setPadding(0, dp(2), 0, dp(12))
    })

    val run = MarkdownSGeneticAlgorithm.run(current.seed ?: 20261102L, current.mutationRate ?: 0.06)
    val generation = (current.generation ?: 0).coerceIn(0, MarkdownSGeneticAlgorithm.GENERATIONS)
    val snapshot = run.history[generation]
    val selected = (current.selectedIndividual ?: 0).coerceIn(0, snapshot.population.lastIndex)
    val individuals = snapshot.population

    addDivider()
    addChapter("02 · 种群与个体", "种群与个体")
    val population = GridLayout(context).apply {
      columnCount = 6
      rowCount = 6
      contentDescription = "36 个遗传算法种群个体"
      background = surfaceBackground()
      setPadding(dp(10), dp(10), dp(10), dp(10))
    }
    individuals.forEachIndexed { index, item ->
      val selectedItem = index == selected
      val tile = TextView(context).apply {
        text = "${(index + 1).toString().padStart(2, '0')} · ${"%.1f".format(item.fitness)}"
        textSize = 11f
        typeface = android.graphics.Typeface.MONOSPACE
        gravity = Gravity.CENTER
        setTextColor(if (selectedItem) PAPER_LIGHT else PAPER_WHITE)
        background = tileBackground(item.fitness, selectedItem)
        isFocusable = true
        isClickable = enabledControls
        isEnabled = enabledControls
        contentDescription = "个体 ${index + 1}，适应度 ${"%.2f".format(item.fitness)}${if (selectedItem) "，当前选中" else ""}"
        tag = "markdowns_population_$index"
        setOnClickListener { commit(current.copy(selectedIndividual = index, genes = item.genes)) }
      }
      population.addView(tile, GridLayout.LayoutParams().apply {
        width = 0
        height = dp(43)
        columnSpec = GridLayout.spec(index % 6, 1f)
        rowSpec = GridLayout.spec(index / 6)
        setMargins(dp(3), dp(3), dp(3), dp(3))
      })
    }
    addView(population, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    addView(label("最佳适应度 ${"%.2f".format(snapshot.best.fitness)}  ·  平均 ${"%.2f".format(snapshot.average)}  ·  多样性 ${"%.1f".format(snapshot.diversity)}%", 13f, PAPER_MUTED).apply {
      setPadding(0, dp(7), 0, dp(10))
    })

    addDivider()
    addChapter("03 · 适应度地形", "适应度地形")
    addView(label("采样位置", 14f, PAPER_MUTED))
    val position = (current.position ?: 50.0).toInt().coerceIn(0, 100)
    addView(FitnessLandscapeView(context, current.genes, position) { nextPosition ->
      commit(current.copy(position = nextPosition.toDouble()))
    }, LayoutParams(LayoutParams.MATCH_PARENT, dp(190)))
    addView(slider(100, position, "采样位置") { value -> commit(current.copy(position = value.toDouble())) })
    val (sampleX, sampleY) = samplePoint(current.genes, position)
    val fitness = MarkdownSGeneticAlgorithm.landscape(sampleX, sampleY)
    addView(label("${"%.2f".format(fitness / 100)}", 25f, PAPER_ACCENT, serif = true).apply {
      tag = "markdowns_fitness_value"
      setPadding(0, 0, 0, dp(12))
    })

    addDivider()
    addChapter("04 · 选择轮盘", "选择轮盘")
    addView(FitnessWheelView(context, individuals, current.wheelAngle ?: 0.0, selected, enabledControls) { angle, picked ->
      commit(current.copy(wheelAngle = angle, selectedIndividual = picked, genes = individuals[picked].genes))
    }.apply { tag = "markdowns_fitness_wheel" }, LayoutParams(LayoutParams.MATCH_PARENT, dp(230)))
    addView(label("选中个体 ${selected + 1}  ·  适应度 ${"%.2f".format(individuals[selected].fitness)}", 14f, PAPER_ACCENT).apply {
      tag = "markdowns_selected_individual"
      setPadding(0, 0, 0, dp(12))
    })

    addDivider()
    addChapter("05 · 交叉切点", "基因与交叉")
    val cut = (current.cut ?: 6).coerceIn(1, 11)
    val first = individuals[selected].genes
    val second = individuals[(selected + 1) % individuals.size].genes
    val children = MarkdownSGeneticAlgorithm.crossover(first, second, cut)
    addView(CrossoverView(context, children.first, children.second, cut), LayoutParams(LayoutParams.MATCH_PARENT, dp(112)))
    addView(label("交叉切点：$cut  ·  子代 ${children.first.joinToString("")}", 14f, PAPER_ACCENT).apply {
      tag = "markdowns_crossover_value"
    })
    addView(slider(10, cut - 1, "交叉切点") { value -> commit(current.copy(cut = value + 1)) })
    addView(label("", 1f, PAPER_MUTED).apply { setPadding(0, 0, 0, dp(8)) })

    addDivider()
    addChapter("06 · 变异概率", "变异概率与演化")
    val rate = ((current.mutationRate ?: 0.06) * 100).toInt().coerceIn(0, 30)
    val probability = (1.0 - (1.0 - rate / 100.0).powInt(12)) * 100.0
    addView(slider(30, rate, "变异概率") { value -> commit(current.copy(mutationRate = value / 100.0)) })
    addView(label("$rate%  ·  至少变异一次的概率 ${"%.1f".format(probability)}%  ·  平均 ${"%.2f".format(12 * rate / 100.0)} 个位点变化。", 14f, PAPER_ACCENT).apply {
      tag = "markdowns_mutation_feedback"
      setPadding(0, dp(2), 0, dp(12))
    })

    addDivider()
    addChapter("07 · 演化记录", "演化记录")
    addView(label("迭代轨迹 · 数据图表", 17f, PAPER_INK, bold = true))
    addView(GenerationChartView(context, run.history, generation) { nextGeneration ->
      val item = run.history[nextGeneration].population[selected]
      commit(current.copy(generation = nextGeneration, genes = item.genes))
    }.apply { tag = "markdowns_generation_chart" }, LayoutParams(LayoutParams.MATCH_PARENT, dp(210)))
    addView(label("第 $generation / ${MarkdownSGeneticAlgorithm.GENERATIONS} 代  ·  最佳适应度 ${"%.2f".format(snapshot.best.fitness)}", 14f, PAPER_ACCENT).apply {
      tag = "markdowns_generation_value"
    })
    addView(slider(MarkdownSGeneticAlgorithm.GENERATIONS, generation, "演化世代") { value ->
      val item = run.history[value].population[selected]
      commit(current.copy(generation = value, genes = item.genes))
    })

    addDivider()
    addChapter("08 · 三维染色体", "三维染色体")
    addView(label("拖动旋转 · 点击位点改变基因", 14f, PAPER_MUTED))
    val onThreeChange: (Int?, Double) -> Unit = onThreeChange@{ index, angle ->
      val latest = state ?: return@onThreeChange
      if (index != null) {
        val genes = latest.genes.toMutableList().also { it[index] = 1 - it[index] }
        commit(latest.copy(genes = genes, angle = angle, selectedIndividual = null))
      } else {
        commit(latest.copy(angle = angle))
      }
    }
    val three: View = try {
      retainedScene?.also {
        it.bindState(current.genes, current.angle ?: 0.15, current.paused == false, enabledControls)
      } ?: sceneFactory(context, current.genes, current.angle ?: 0.15, current.paused == false, enabledControls, onThreeChange)
    } catch (error: Exception) {
      retainedScene?.let { it.dispose(); removeView(it) }
      addView(label("3D 渲染不可用，显示二维染色体示意。", 13f, PAPER_MUTED))
      NativeChromosomeView(context, current.genes, current.angle ?: 0.15, current.paused == false, enabledControls, onThreeChange)
    } catch (error: LinkageError) {
      retainedScene?.let { it.dispose(); removeView(it) }
      addView(label("3D 渲染组件不可用，显示二维染色体示意。", 13f, PAPER_MUTED))
      NativeChromosomeView(context, current.genes, current.angle ?: 0.15, current.paused == false, enabledControls, onThreeChange)
    }.apply { tag = "markdowns_three_scene" }
    chromosome3d = three
    if (three === retainedScene) bringChildToFront(three)
    else addView(three, LayoutParams(LayoutParams.MATCH_PARENT, dp(300)))
    addView(outlineButton(if (current.paused == false) "暂停旋转" else "继续旋转", "markdowns_rotation_toggle") {
      val angle = when (three) {
        is MarkdownSChromosomeSceneView -> three.rotationAngle()
        is NativeChromosomeView -> three.rotationAngle()
        else -> current.angle ?: 0.15
      }
      commit(current.copy(paused = current.paused == false, angle = angle))
    }, LayoutParams(LayoutParams.WRAP_CONTENT, dp(42)).apply { topMargin = dp(10) })
  }

  private fun disposeChromosome() {
    when (val view = chromosome3d) {
      is MarkdownSChromosomeSceneView -> view.dispose()
      is NativeChromosomeView -> view.dispose()
    }
  }

  private fun addChapterNavigation() {
    val titles = listOf("01 编码与变异", "02 种群与个体", "03 适应度地形", "04 选择轮盘", "05 交叉切点", "06 变异概率", "07 演化记录", "08 三维染色体")
    val strip = LinearLayout(context).apply {
      orientation = HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }
    titles.forEachIndexed { index, text ->
      strip.addView(outlineButton(text, "markdowns_chapter_${index + 1}") {
        val targetTag = listOf(
          "markdowns_section_编码与变异", "markdowns_section_种群与个体", "markdowns_section_适应度地形",
          "markdowns_section_选择轮盘", "markdowns_section_基因与交叉", "markdowns_section_变异概率与演化",
          "markdowns_section_演化记录", "markdowns_section_三维染色体",
        )[index]
        val target = findViewWithTag<View>(targetTag) ?: return@outlineButton
        post {
          var ancestor: ViewParent? = parent
          while (ancestor != null && ancestor !is android.widget.ScrollView) ancestor = ancestor.parent
          val scroll = ancestor as? android.widget.ScrollView ?: return@post
          val rect = android.graphics.Rect(0, 0, target.width, target.height)
          scroll.offsetDescendantRectToMyCoords(target, rect)
          scroll.smoothScrollTo(0, (rect.top - dp(8)).coerceAtLeast(0))
        }
      }, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(38)).apply {
        rightMargin = dp(6)
      })
    }
    val scroll = HorizontalScrollView(context).apply {
      isHorizontalScrollBarEnabled = false
      addView(strip)
    }
    addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, dp(46)))
  }

  private fun addChapter(titleText: String, viewTag: String) {
    addView(label(titleText, 18f, PAPER_INK, bold = true).apply {
      tag = "markdowns_section_$viewTag"
      setPadding(0, dp(11), 0, dp(8))
    })
  }

  private fun addDivider() {
    addView(View(context).apply { setBackgroundColor(PAPER_RULE) }, LayoutParams(LayoutParams.MATCH_PARENT, dp(1)).apply {
      topMargin = dp(8)
      bottomMargin = dp(5)
    })
  }

  private fun slider(maximum: Int, initial: Int, description: String, onChange: (Int) -> Unit) = SeekBar(context).apply {
    max = maximum
    progress = initial
    contentDescription = description
    tag = "markdowns_slider_$description"
    isEnabled = enabledControls
    progressTintList = android.content.res.ColorStateList.valueOf(PAPER_ACCENT)
    thumbTintList = android.content.res.ColorStateList.valueOf(PAPER_ACCENT)
    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) = Unit
      override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
      override fun onStopTrackingTouch(seekBar: SeekBar?) { onChange(seekBar?.progress ?: initial) }
    })
  }

  private fun label(
    value: String,
    size: Float,
    color: Int,
    bold: Boolean = false,
    serif: Boolean = false,
    letterSpacing: Float = 0f,
  ) = TextView(context).apply {
    text = value
    textSize = size
    setTextColor(color)
    this.letterSpacing = letterSpacing
    typeface = when {
      serif -> android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
      bold -> android.graphics.Typeface.DEFAULT_BOLD
      else -> android.graphics.Typeface.DEFAULT
    }
    gravity = Gravity.CENTER_VERTICAL
  }

  private fun title(value: String) = label(value, 27f, PAPER_INK, serif = true).apply {
    setPadding(0, dp(11), 0, dp(5))
  }

  private fun outlineButton(textValue: String, viewTag: String, action: () -> Unit) = Button(context).apply {
    text = textValue
    textSize = 11f
    isAllCaps = false
    minHeight = 0
    minimumHeight = 0
    minWidth = 0
    minimumWidth = 0
    setPadding(dp(9), 0, dp(9), 0)
    setTextColor(PAPER_ACCENT)
    background = GradientDrawable().apply {
      setColor(android.graphics.Color.TRANSPARENT)
      setStroke(dp(1), PAPER_ACCENT.withAlpha(75))
      cornerRadius = dp(4).toFloat()
    }
    stateListAnimator = null
    tag = viewTag
    isEnabled = enabledControls || viewTag.startsWith("markdowns_chapter_")
    setOnClickListener { action() }
  }

  private fun tileBackground(fitness: Double, selected: Boolean) = GradientDrawable().apply {
    val strength = (fitness / 101.0).coerceIn(0.0, 1.0).toFloat()
    val base = blend(PAPER_TEAL, PAPER_SURFACE, 0.48f - strength * 0.38f)
    setColor(if (selected) PAPER_ACCENT else base)
    cornerRadius = dp(3).toFloat()
  }

  private fun surfaceBackground() = GradientDrawable().apply {
    setColor(PAPER_SURFACE)
    setStroke(dp(1), PAPER_RULE)
    cornerRadius = dp(2).toFloat()
  }

  private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

  private fun samplePoint(genes: List<Int>, position: Int): Pair<Double, Double> {
    val (_, y) = MarkdownSGeneticAlgorithm.decode(genes)
    return position / 100.0 to y
  }

  companion object {
    private const val PAPER = 0xfff0ecdf.toInt()
    private const val PAPER_SURFACE = 0xfff7f3e8.toInt()
    private const val PAPER_LIGHT = 0xfffcf8ed.toInt()
    private const val PAPER_WHITE = 0xff426f70.toInt()
    private const val PAPER_INK = 0xff293028.toInt()
    private const val PAPER_MUTED = 0xff696c5e.toInt()
    private const val PAPER_ACCENT = 0xffa44c34.toInt()
    private const val PAPER_TEAL = 0xff376d70.toInt()
    private const val PAPER_RULE = 0x33353a2e
  }
}

private class ChromosomeView(
  context: Context,
  private val genes: List<Int>,
  private val enabled: Boolean,
  private val onGeneBrush: (List<Int>) -> Unit,
) : View(context) {
  private val density = resources.displayMetrics.density
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val bounds = RectF()
  private val brushed = linkedSetOf<Int>()
  private var brushStart = 0

  init {
    contentDescription = "12 位基因染色体，可点按或横向拖过多个位点切换"
    isFocusable = enabled
    isClickable = enabled
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val gap = 7f * density
    val side = 16f * density
    val totalGap = gap * (genes.size - 1)
    val cellWidth = ((width - 2 * side - totalGap) / genes.size).coerceAtLeast(8f * density)
    val height = minOf(this.height - 24f * density, 83f * density)
    val top = (this.height - height) / 2f
    genes.forEachIndexed { index, originalGene ->
      val gene = if (index in brushed) 1 - originalGene else originalGene
      val left = side + index * (cellWidth + gap)
      bounds.set(left, top, left + cellWidth, top + height)
      paint.color = if (gene == 1) 0xffa44c34.toInt() else 0xff376d70.toInt()
      canvas.drawRoundRect(bounds, cellWidth / 2, cellWidth / 2, paint)
      paint.color = 0xfffcf8ed.toInt()
      paint.textAlign = Paint.Align.CENTER
      paint.textSize = 23f * density
      paint.typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
      canvas.drawText(gene.toString(), bounds.centerX(), bounds.centerY() + 8f * density, paint)
    }
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (!enabled) return false
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        brushed.clear()
        brushStart = indexAt(event.x)
        brushed += brushStart
        invalidate()
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        for (historyIndex in 0 until event.historySize) brushTo(indexAt(event.getHistoricalX(historyIndex)))
        brushTo(indexAt(event.x))
        invalidate()
        return true
      }
      MotionEvent.ACTION_UP -> {
        brushTo(indexAt(event.x))
        onGeneBrush(brushed.toList())
        brushed.clear()
        performClick()
        return true
      }
      MotionEvent.ACTION_CANCEL -> {
        brushed.clear()
        invalidate()
        return true
      }
    }
    return false
  }

  private fun indexAt(x: Float): Int {
    val side = 16f * density
    val gap = 7f * density
    val cell = ((width - 2 * side - gap * (genes.size - 1)) / genes.size).coerceAtLeast(8f * density)
    return ((x - side) / (cell + gap)).toInt().coerceIn(0, genes.lastIndex)
  }

  private fun brushTo(index: Int) {
    for (gene in minOf(brushStart, index)..maxOf(brushStart, index)) brushed += gene
  }

  override fun performClick(): Boolean {
    super.performClick()
    return true
  }
}

private class FitnessLandscapeView(
  context: Context,
  private val genes: List<Int>,
  private val position: Int,
  private val onPositionSelected: (Int) -> Unit,
) : View(context) {
  private val density = resources.displayMetrics.density
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val path = Path()

  init {
    contentDescription = "适应度地形曲线，点按可设置采样位置"
    setBackgroundColor(0xfff7f3e8.toInt())
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val left = 10f * density
    val right = width - 10f * density
    val top = 18f * density
    val bottom = height - 18f * density
    canvas.drawRect(left, top, right, bottom, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xfff7f3e8.toInt() })
    val y = MarkdownSGeneticAlgorithm.decode(genes).second
    fun curve(x: Double) = MarkdownSGeneticAlgorithm.landscape(x, y) / 100.0
    path.reset()
    for (index in 0..240) {
      val xValue = index / 240.0
      val x = left + (right - left) * xValue.toFloat()
      val yValue = bottom - (bottom - top) * curve(xValue).toFloat().coerceIn(0f, 1f)
      if (index == 0) path.moveTo(x, yValue) else path.lineTo(x, yValue)
    }
    paint.color = 0xff376d70.toInt()
    paint.strokeWidth = 3f * density
    paint.style = Paint.Style.STROKE
    paint.strokeCap = Paint.Cap.ROUND
    canvas.drawPath(path, paint)
    val x = position / 100f
    val px = left + (right - left) * x
    val py = bottom - (bottom - top) * curve(position / 100.0).toFloat().coerceIn(0f, 1f)
    paint.color = 0xffa44c34.toInt()
    paint.style = Paint.Style.FILL
    canvas.drawCircle(px, py, 7f * density, paint)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (event.actionMasked == MotionEvent.ACTION_UP) {
      onPositionSelected((((event.x / width.coerceAtLeast(1)) * 100).toInt()).coerceIn(0, 100))
      performClick()
      return true
    }
    return event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_CANCEL
  }

  override fun performClick(): Boolean {
    super.performClick()
    return true
  }
}

private class FitnessWheelView(
  context: Context,
  private val population: List<MarkdownSIndividual>,
  private val angle: Double,
  private val selected: Int,
  private val enabled: Boolean,
  private val onSelect: (angle: Double, selected: Int) -> Unit,
) : View(context) {
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val density = resources.displayMetrics.density
  private var dragging = false

  init {
    contentDescription = "适应度选择轮盘，可拖动或使用左右方向键选择个体"
    isFocusable = enabled
    isFocusableInTouchMode = enabled
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    setBackgroundColor(0xfff7f3e8.toInt())
    val cx = width / 2f
    val cy = height / 2f
    val radius = minOf(width, height) * 0.37f
    val total = population.sumOf { it.fitness }.takeIf { it > 0 } ?: population.size.toDouble()
    var start = angle.toFloat()
    val bounds = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
    population.forEachIndexed { index, item ->
      val weight = if (population.sumOf { it.fitness } > 0) item.fitness / total else 1.0 / population.size
      val sweep = (weight * 360.0).toFloat()
      paint.color = if (index % 2 == 0) 0xffa44c34.toInt() else 0xff376d70.toInt()
      canvas.drawArc(bounds, start, sweep, true, paint)
      paint.color = 0xfff0ecdf.toInt()
      paint.style = Paint.Style.STROKE
      paint.strokeWidth = 1f * density
      canvas.drawArc(bounds, start, sweep, true, paint)
      paint.style = Paint.Style.FILL
      start += sweep
    }
    paint.color = 0xfff0ecdf.toInt()
    canvas.drawCircle(cx, cy, radius * 0.34f, paint)
    paint.color = 0xff293028.toInt()
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = 11f * density
    paint.typeface = android.graphics.Typeface.MONOSPACE
    canvas.drawText("SELECT", cx, cy + 4f * density, paint)
    paint.color = 0xffa44c34.toInt()
    val marker = Path().apply {
      moveTo(cx, cy - radius - 5f * density)
      lineTo(cx - 9f * density, cy - radius - 22f * density)
      lineTo(cx + 9f * density, cy - radius - 22f * density)
      close()
    }
    canvas.drawPath(marker, paint)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (!enabled) return false
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> dragging = true
      MotionEvent.ACTION_MOVE -> if (dragging) invalidate()
      MotionEvent.ACTION_UP -> if (dragging) {
        dragging = false
        val selectedAngle = atan2(event.y - height / 2f, event.x - width / 2f)
        onSelect(selectedAngle.toDouble(), selectAtAngle(population, selectedAngle.toDouble()))
        performClick()
      }
      MotionEvent.ACTION_CANCEL -> dragging = false
    }
    return true
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    if (enabled && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
      val nextAngle = angle + if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -0.25 else 0.25
      onSelect(nextAngle, selectAtAngle(population, nextAngle))
      return true
    }
    return super.onKeyDown(keyCode, event)
  }

  override fun performClick(): Boolean {
    super.performClick()
    return true
  }

  @Suppress("unused") private val selectedForAccessibility = selected
}

private class CrossoverView(context: Context, private val first: List<Int>, private val second: List<Int>, private val cut: Int) : View(context) {
  private val density = resources.displayMetrics.density
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val rect = RectF()

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    setBackgroundColor(0xfff7f3e8.toInt())
    val side = 10f * density
    val gap = 4f * density
    val cell = (width - side * 2 - gap * 11) / 12f
    val rowHeight = (height - 22f * density) / 2f
    listOf(first, second).forEachIndexed { row, genes ->
      genes.forEachIndexed { index, gene ->
        val left = side + index * (cell + gap)
        val top = 7f * density + row * rowHeight
        rect.set(left, top, left + cell, top + rowHeight - 5f * density)
        paint.color = if (index < cut) 0xffa44c34.toInt() else 0xff376d70.toInt()
        canvas.drawRoundRect(rect, 5f * density, 5f * density, paint)
        paint.color = 0xfffcf8ed.toInt()
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 14f * density
        canvas.drawText(gene.toString(), rect.centerX(), rect.centerY() + 5f * density, paint)
      }
    }
    contentDescription = "交叉结果，切点 $cut，子代 ${first.joinToString("")}"
  }
}

private class GenerationChartView(
  context: Context,
  private val history: List<MarkdownSGeneration>,
  private val selectedGeneration: Int,
  private val onSelectGeneration: (Int) -> Unit,
) : View(context) {
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val density = resources.displayMetrics.density
  private val path = Path()

  init { contentDescription = "四十代遗传算法最佳适应度折线图，可点按选择世代"; isClickable = true }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    setBackgroundColor(0xfff7f3e8.toInt())
    val left = 22f * density
    val top = 16f * density
    val right = width - 12f * density
    val bottom = height - 26f * density
    paint.color = 0xff696c5e.toInt()
    paint.strokeWidth = 1f * density
    canvas.drawLine(left, bottom, right, bottom, paint)
    canvas.drawLine(left, top, left, bottom, paint)
    path.reset()
    history.forEachIndexed { index, item ->
      val x = left + (right - left) * index / (history.size - 1)
      val y = bottom - (bottom - top) * (item.best.fitness / 105.0).toFloat()
      if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    paint.color = 0xff376d70.toInt()
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3f * density
    paint.strokeCap = Paint.Cap.ROUND
    canvas.drawPath(path, paint)
    val selected = selectedGeneration.coerceIn(0, history.lastIndex)
    val x = left + (right - left) * selected / (history.size - 1)
    val y = bottom - (bottom - top) * (history[selected].best.fitness / 105.0).toFloat()
    paint.style = Paint.Style.FILL
    paint.color = 0xffa44c34.toInt()
    canvas.drawCircle(x, y, 6f * density, paint)
    paint.color = 0xff293028.toInt()
    paint.textSize = 10f * density
    paint.typeface = android.graphics.Typeface.MONOSPACE
    canvas.drawText("最佳适应度 / 代数", left, height - 5f * density, paint)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (event.actionMasked == MotionEvent.ACTION_UP) {
      val left = 22f * density
      val right = width - 12f * density
      val generation = (((event.x - left) / (right - left).coerceAtLeast(1f)) * history.lastIndex).toInt().coerceIn(0, history.lastIndex)
      onSelectGeneration(generation)
      performClick()
      return true
    }
    return event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_CANCEL
  }

  override fun performClick(): Boolean { super.performClick(); return true }
}

/** Canvas projection of the desktop's helix illustration; rotation and genes remain real state. */
private class NativeChromosomeView(
  context: Context,
  private val genes: List<Int>,
  initialAngle: Double,
  private val animate: Boolean,
  private val enabled: Boolean,
  private val onCommit: (geneIndex: Int?, angle: Double) -> Unit,
) : View(context) {
  private val density = resources.displayMetrics.density
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val genePoints = mutableListOf<Pair<Float, Float>>()
  private var angle = initialAngle.toFloat()
  private var downX = 0f
  private var lastX = 0f
  private var moved = false
  private var animator: ValueAnimator? = null

  init {
    setBackgroundColor(0xfff7f3e8.toInt())
    contentDescription = "三维染色体示意，可拖动旋转，轻触基因切换"
    isFocusable = enabled
    if (animate && ValueAnimator.areAnimatorsEnabled()) {
      animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 5000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
          angle += (it.animatedFraction - previousFraction) * (2f * PI.toFloat())
          previousFraction = it.animatedFraction
          invalidate()
        }
        addListener(object : AnimatorListenerAdapter() {
          override fun onAnimationRepeat(animation: Animator) {
            previousFraction = 0f
          }
        })
        start()
      }
    }
  }

  private var previousFraction = 0f

  fun rotationAngle(): Double = angle.toDouble()

  fun dispose() {
    animator?.cancel()
    animator = null
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    genePoints.clear()
    val cx = width / 2f
    val centerY = height / 2f
    val amplitude = minOf(width * 0.27f, 92f * density)
    val top = 24f * density
    val bottom = height - 24f * density
    val count = genes.size
    val pointsA = ArrayList<Pair<Float, Float>>(count)
    val pointsB = ArrayList<Pair<Float, Float>>(count)
    for (index in 0 until count) {
      val t = index / (count - 1.0)
      val phase = t * PI * 3.4 + angle
      val depth = cos(phase).toFloat()
      val y = top + (bottom - top) * t.toFloat()
      val xA = cx + sin(phase).toFloat() * amplitude
      val xB = cx - sin(phase).toFloat() * amplitude
      pointsA += xA to y
      pointsB += xB to y
      genePoints += (if (depth > 0) xA else xB) to y
    }
    val rungPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      strokeWidth = 5f * density
      strokeCap = Paint.Cap.ROUND
    }
    for (index in 0 until count) {
      val phase = index / (count - 1.0) * PI * 3.4 + angle
      val depth = cos(phase).toFloat()
      val a = pointsA[index]
      val b = pointsB[index]
      rungPaint.color = if (genes[index] == 1) 0xffa44c34.toInt() else 0xff376d70.toInt()
      rungPaint.alpha = (110 + 130 * ((depth + 1f) / 2f)).toInt().coerceIn(0, 255)
      canvas.drawLine(a.first, a.second, b.first, b.second, rungPaint)
      paint.color = if (genes[index] == 1) 0xffa44c34.toInt() else 0xff376d70.toInt()
      paint.alpha = rungPaint.alpha
      canvas.drawCircle(a.first, a.second, (8f + depth * 2f) * density, paint)
      paint.color = if (genes[index] == 1) 0xffa44c34.toInt() else 0xff376d70.toInt()
      paint.alpha = rungPaint.alpha
      canvas.drawCircle(b.first, b.second, (8f + depth * 2f) * density, paint)
      paint.color = 0xff293028.toInt()
      paint.alpha = 255
      paint.textAlign = Paint.Align.CENTER
      paint.textSize = 10f * density
      canvas.drawText(genes[index].toString(), if (depth > 0) a.first else b.first, a.second + 4f * density, paint)
    }
    val helix = Path()
    for (index in 0 until count) {
      val point = pointsA[index]
      if (index == 0) helix.moveTo(point.first, point.second) else helix.lineTo(point.first, point.second)
    }
    paint.color = 0xffd9dcc5.toInt()
    paint.alpha = 210
    paint.strokeWidth = 8f * density
    paint.style = Paint.Style.STROKE
    canvas.drawPath(helix, paint)
    paint.style = Paint.Style.FILL
    paint.alpha = 255
    contentDescription = "染色体旋转角 ${"%.2f".format(angle)}，12 个基因，可拖动旋转或轻触位点编辑"
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (!enabled) return false
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        downX = event.x
        lastX = event.x
        moved = false
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        if (kotlin.math.abs(event.x - downX) > dp(4)) moved = true
        if (moved) {
          angle += (event.x - lastX) / width.coerceAtLeast(1) * 2f
          lastX = event.x
          invalidate()
        }
        return true
      }
      MotionEvent.ACTION_UP -> {
        if (!moved) {
          val index = genePoints.indices.minByOrNull { index ->
            val (x, y) = genePoints[index]
            (x - event.x) * (x - event.x) + (y - event.y) * (y - event.y)
          }
          onCommit(index, angle.toDouble())
        } else onCommit(null, angle.toDouble())
        performClick()
        return true
      }
      MotionEvent.ACTION_CANCEL -> return true
    }
    return false
  }

  override fun performClick(): Boolean { super.performClick(); return true }
  private fun dp(value: Int) = value * resources.displayMetrics.density
}

private fun selectAtAngle(population: List<MarkdownSIndividual>, angle: Double): Int {
  val total = population.sumOf { it.fitness }
  val normalized = (((-PI / 2 - angle) % (2 * PI)) + 2 * PI) % (2 * PI)
  var threshold = normalized / (2 * PI) * if (total > 0) total else population.size.toDouble()
  population.forEachIndexed { index, individual ->
    threshold -= if (total > 0) individual.fitness else 1.0
    if (threshold <= 0) return index
  }
  return population.lastIndex
}

private fun blend(from: Int, to: Int, amount: Float): Int {
  val t = amount.coerceIn(0f, 1f)
  fun channel(shift: Int) = (((from shr shift) and 0xff) * (1 - t) + ((to shr shift) and 0xff) * t).toInt()
  return android.graphics.Color.argb(channel(24), channel(16), channel(8), channel(0))
}

private fun Int.withAlpha(alpha: Int): Int = (this and 0x00ffffff) or (alpha.coerceIn(0, 255) shl 24)

private fun Double.powInt(power: Int): Double {
  var result = 1.0
  repeat(power) { result *= this }
  return result
}
