import { AnnotationItem } from '../core/Annotations';

/**
 * 手写批注层：覆盖在文档/笔记之上的透明画布。
 * 坐标用桌面相同的 1000 比例系（相对层宽），保证跨端互通。
 * 支持契约内的工具类型：pen 自由笔迹、highlight 荧光（半透明）、line/rect/ellipse 拖拽形状、
 * text 文字批注（点击位置经 onTextTap 回调由父组件收集文字后落项）。
 * 笔迹变更通过 onChange 回调给父组件，由父组件统一保存。
 */
@Component
export struct InkCanvas {
  @Prop @Watch('onItemsChange') items: AnnotationItem[] = [];
  // 父组件运行期会切换/改色的参数必须是 @Prop，否则只在创建时赋值一次
  @Prop penColor: string = '#dc3545';
  @Prop penWidth: number = 3;
  @Prop drawingEnabled: boolean = false;
  @Prop tool: string = 'pen'; // pen | highlight | line | rect | ellipse | text | eraser
  // 禁止手指书写：开启后手指/鼠标只触发拖动平移（由 onPanDelta 回调给父组件），手写笔照常书写
  @Prop fingerLock: boolean = false;
  // 外部要求清空套索选区（切换工具/撤销/清除/删除所选时父组件递增此值）
  @Prop @Watch('clearSelection') selectionEpoch: number = 0;
  onChange: (items: AnnotationItem[]) => void = () => {};
  onTextTap: (x: number, y: number) => void = () => {};
  onPanDelta: (dx: number, dy: number) => void = () => {};
  onPanEnd: () => void = () => {};
  onSelectionChange: (indices: number[]) => void = () => {};

  private settings: RenderingContextSettings = new RenderingContextSettings(true);
  private canvas: CanvasRenderingContext2D = new CanvasRenderingContext2D(this.settings);
  private layerWidth: number = 1;
  private layerHeight: number = 1;
  private current: number[][] = [];
  private shapeStart: number[] | null = null;
  private shapeEnd: number[] | null = null;
  // 手写笔压感：一次笔迹中记录最大压力，抬笔时按压力调制线宽（不改变存储契约，width 仍是数值）
  private strokeIsPen: boolean = false;
  private strokeMaxPressure: number = 0;
  // 手锁拖动：上一次采样点（原始像素坐标）
  private panAnchor: number[] | null = null;
  // 橡皮本次拖动是否真的擦到过东西（决定抬笔是否触发落盘）
  private eraserTouched: boolean = false;
  // 是否有进行中的手势（多指守卫：第二根手指的 Down 不覆盖在飞手势，静态审查 L5）
  private gestureActive: boolean = false;
  // 套索：圈选矩形与选中笔迹索引（1000 比例系）
  private lassoStart: number[] | null = null;
  private lassoEnd: number[] | null = null;
  private selectedIdx: number[] = [];
  private selRect: number[] | null = null;
  private moveAnchor: number[] | null = null;

  private normRect(a: number[], b: number[]): number[] {
    return [Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[0], b[0]), Math.max(a[1], b[1])];
  }

  private inRect(x: number, y: number, r: number[]): boolean {
    return x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3];
  }

  /** 复位所有进行中的手势中间态（静态审查 M2：切工具/系统 Cancel 时防串扰与幽灵图形）。 */
  private resetGestureStates(): void {
    this.current = [];
    this.shapeStart = null;
    this.shapeEnd = null;
    this.panAnchor = null;
    this.moveAnchor = null;
    this.eraserTouched = false;
    this.strokeIsPen = false;
    this.strokeMaxPressure = 0;
    this.gestureActive = false;
  }

  private clearSelection(): void {
    this.lassoStart = null;
    this.lassoEnd = null;
    this.selectedIdx = [];
    this.selRect = null;
    this.resetGestureStates();
    this.repaint();
    this.onSelectionChange([]);
  }

  private onItemsChange(): void {
    this.repaint();
  }

  private scaleX(): number {
    return 1000 / Math.max(1, this.layerWidth);
  }

  private toCanvasX(x: number): number {
    return x / this.scaleX();
  }

  private toCanvasY(y: number): number {
    // y 比例系数与 x 相同（桌面以宽度为基准），保证纵横不变形
    return y / this.scaleX();
  }

  private repaint(): void {
    const ctx = this.canvas;
    ctx.clearRect(0, 0, this.layerWidth, this.layerHeight);
    for (const item of this.items) {
      this.drawItem(ctx, item);
    }
    // 拖拽中的形状实时预览
    if (this.shapeStart !== null && this.shapeEnd !== null) {
      this.drawShape(ctx, this.tool, this.penColor, this.penWidth, this.shapeStart, this.shapeEnd);
    }
    // 套索圈选/选中框
    if (this.lassoStart !== null && this.lassoEnd !== null) {
      this.drawDashedRect(ctx, this.normRect(this.lassoStart, this.lassoEnd));
    } else if (this.selRect !== null) {
      this.drawDashedRect(ctx, this.selRect);
    }
    ctx.globalAlpha = 1.0;
  }

  private drawItem(ctx: CanvasRenderingContext2D, item: AnnotationItem): void {
    if (item.type === 'line' || item.type === 'rect' || item.type === 'ellipse') {
      if (item.points.length >= 2) {
        this.drawShape(ctx, item.type, item.color, item.width,
          item.points[0], item.points[item.points.length - 1]);
      }
      return;
    }
    if (item.type === 'text') {
      const text = item.text ?? '';
      if (item.points.length >= 1 && text.length > 0) {
        ctx.fillStyle = item.color;
        ctx.font = `${Math.max(12, item.width * 5) / this.scaleX()}px sans-serif`;
        ctx.fillText(text, this.toCanvasX(item.points[0][0]), this.toCanvasY(item.points[0][1]));
      }
      return;
    }
    // pen / highlight 自由笔迹
    ctx.strokeStyle = item.color;
    ctx.lineWidth = Math.max(1, item.width / this.scaleX());
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.globalAlpha = item.type === 'highlight' ? 0.35 : 1.0;
    ctx.beginPath();
    const pts = item.points;
    if (pts.length === 1) {
      const x = this.toCanvasX(pts[0][0]);
      const y = this.toCanvasY(pts[0][1]);
      ctx.moveTo(x, y);
      ctx.lineTo(x + 0.1, y + 0.1);
    } else {
      for (let i = 0; i < pts.length; i++) {
        const x = this.toCanvasX(pts[i][0]);
        const y = this.toCanvasY(pts[i][1]);
        if (i === 0) {
          ctx.moveTo(x, y);
        } else {
          ctx.lineTo(x, y);
        }
      }
    }
    ctx.stroke();
    ctx.globalAlpha = 1.0;
  }

  private drawShape(ctx: CanvasRenderingContext2D, type: string, color: string, width: number,
    start: number[], end: number[]): void {
    const x1 = this.toCanvasX(start[0]);
    const y1 = this.toCanvasY(start[1]);
    const x2 = this.toCanvasX(end[0]);
    const y2 = this.toCanvasY(end[1]);
    ctx.strokeStyle = color;
    ctx.lineWidth = Math.max(1, width / this.scaleX());
    ctx.lineCap = 'round';
    ctx.globalAlpha = 1.0;
    ctx.beginPath();
    if (type === 'line') {
      ctx.moveTo(x1, y1);
      ctx.lineTo(x2, y2);
    } else if (type === 'rect') {
      ctx.strokeRect(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1));
    } else if (type === 'ellipse') {
      ctx.ellipse((x1 + x2) / 2, (y1 + y2) / 2, Math.abs(x2 - x1) / 2, Math.abs(y2 - y1) / 2,
        0, 0, Math.PI * 2);
    }
    ctx.stroke();
  }

  private isShapeTool(): boolean {
    return this.tool === 'line' || this.tool === 'rect' || this.tool === 'ellipse';
  }

  private drawDashedRect(ctx: CanvasRenderingContext2D, r: number[]): void {
    ctx.strokeStyle = '#2263EB';
    ctx.lineWidth = Math.max(1, 2 / this.scaleX());
    ctx.globalAlpha = 1.0;
    ctx.setLineDash([10, 6]);
    ctx.strokeRect(this.toCanvasX(r[0]), this.toCanvasY(r[1]),
      this.toCanvasX(r[2]) - this.toCanvasX(r[0]), this.toCanvasY(r[3]) - this.toCanvasY(r[1]));
    ctx.setLineDash([]);
  }

  /** 擦除判定：笔迹任一点落在 (x,y) 半径 r（1000 比例系）内即擦除整笔。 */
  private itemNear(item: AnnotationItem, x: number, y: number, r: number): boolean {
    for (const p of item.points) {
      const dx = p[0] - x;
      const dy = p[1] - y;
      if (dx * dx + dy * dy <= r * r) {
        return true;
      }
    }
    return false;
  }

  /** 压感橡皮：半径随压力缩放（基准 30，1000 比例系≈40px），拖动经过即擦除并实时重绘。 */
  private eraseAt(x: number, y: number, pressure: number): void {
    const r = 30 * (0.5 + Math.min(1.2, Math.max(0, pressure)));
    const next = this.items.filter((item: AnnotationItem): boolean => !this.itemNear(item, x, y, r));
    if (next.length !== this.items.length) {
      this.items = next; // @Watch 触发重绘，实时反馈；父组件 inkItems 在抬笔 onChange 时才同步
      this.eraserTouched = true;
    }
  }

  /** 绘制笔迹的一段（增量路径用，样式与 drawItem 的 pen/highlight 一致）。 */
  private strokeSegment(x1: number, y1: number, x2: number, y2: number): void {
    const ctx = this.canvas;
    ctx.strokeStyle = this.penColor;
    ctx.lineWidth = Math.max(1, this.penWidth / this.scaleX());
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.globalAlpha = this.tool === 'highlight' ? 0.35 : 1.0;
    ctx.beginPath();
    ctx.moveTo(this.toCanvasX(x1), this.toCanvasY(y1));
    ctx.lineTo(this.toCanvasX(x2), this.toCanvasY(y2));
    ctx.stroke();
    ctx.globalAlpha = 1.0;
  }

  private commitShape(): void {
    if (this.shapeStart === null || this.shapeEnd === null) {
      return;
    }
    const item: AnnotationItem = {
      type: this.tool, color: this.penColor, width: this.penWidth,
      points: [this.shapeStart, this.shapeEnd]
    };
    this.items = [...this.items, item];
    this.shapeStart = null;
    this.shapeEnd = null;
    this.onChange(this.items);
  }

  build() {
    Canvas(this.canvas)
      .width('100%')
      .height('100%')
      .hitTestBehavior(this.drawingEnabled ? HitTestMode.Default : HitTestMode.Transparent)
      .onReady(() => {
        this.repaint();
      })
      .onAreaChange((oldValue: Area, newValue: Area) => {
        this.layerWidth = Number(newValue.width);
        this.layerHeight = Number(newValue.height);
        this.repaint();
      })
      .onTouch((event: TouchEvent) => {
        if (!this.drawingEnabled) {
          return;
        }
        if (event.touches.length === 0) {
          return;
        }
        const touch = event.touches[0];
        const x = Math.max(0, (touch.x) * this.scaleX());
        const y = Math.max(0, (touch.y) * this.scaleX());
        // 系统手势抢占/掌根误触导致的 Cancel：与切工具同等复位，不留半截状态（静态审查 L4）
        if (event.type === TouchType.Cancel) {
          this.resetGestureStates();
          this.repaint();
          return;
        }
        // 多指守卫：已有在飞手势时，新手指的 Down 不覆盖锚点（静态审查 L5）
        if (event.type === TouchType.Down && this.gestureActive) {
          return;
        }
        // 手锁：手指/鼠标不书写，只拖动平移（画布命中无法按输入源分流，平移由回调驱动内容偏移）
        if (this.fingerLock && (event.sourceTool === SourceTool.Finger || event.sourceTool === SourceTool.MOUSE)) {
          if (event.type === TouchType.Down) {
            this.gestureActive = true;
            this.panAnchor = [touch.x, touch.y];
          } else if (event.type === TouchType.Move && this.panAnchor !== null) {
            this.onPanDelta(touch.x - this.panAnchor[0], touch.y - this.panAnchor[1]);
            this.panAnchor = [touch.x, touch.y];
          } else if (event.type === TouchType.Up) {
            this.gestureActive = false;
            this.panAnchor = null;
            this.onPanEnd();
          }
          return;
        }
        // 套索：圈选笔迹 → 框内拖动移动 → 框外点击取消；选中索引经 onSelectionChange 报给父组件
        if (this.tool === 'lasso') {
          if (event.type === TouchType.Down) {
            this.gestureActive = true;
            if (this.selRect !== null && this.inRect(x, y, this.selRect)) {
              this.moveAnchor = [x, y];
            } else {
              this.selectedIdx = [];
              this.selRect = null;
              this.onSelectionChange([]);
              this.lassoStart = [x, y];
              this.lassoEnd = [x, y];
              this.repaint();
            }
          } else if (event.type === TouchType.Move) {
            if (this.moveAnchor !== null) {
              const dx = x - this.moveAnchor[0];
              const dy = y - this.moveAnchor[1];
              this.moveAnchor = [x, y];
              for (const idx of this.selectedIdx) {
                const item = this.items[idx];
                if (item !== undefined) {
                  item.points = item.points.map((p: number[]): number[] => [p[0] + dx, p[1] + dy]);
                }
              }
              if (this.selRect !== null) {
                this.selRect = [this.selRect[0] + dx, this.selRect[1] + dy,
                  this.selRect[2] + dx, this.selRect[3] + dy];
              }
              this.repaint();
            } else if (this.lassoStart !== null) {
              this.lassoEnd = [x, y];
              this.repaint();
            }
          } else if (event.type === TouchType.Up) {
            this.gestureActive = false;
            if (this.moveAnchor !== null) {
              this.moveAnchor = null;
              this.items = [...this.items];
              this.onChange(this.items);
            } else if (this.lassoStart !== null && this.lassoEnd !== null) {
              const rect = this.normRect(this.lassoStart, this.lassoEnd);
              const picked: number[] = [];
              for (let i = 0; i < this.items.length; i++) {
                const item = this.items[i];
                if (item !== undefined
                  && item.points.some((p: number[]): boolean => this.inRect(p[0], p[1], rect))) {
                  picked.push(i);
                }
              }
              this.lassoStart = null;
              this.lassoEnd = null;
              this.selectedIdx = picked;
              this.selRect = picked.length > 0 ? rect : null;
              this.repaint();
              this.onSelectionChange(picked);
            }
          }
          return;
        }
        // 压感橡皮：拖动经过笔迹即擦除，抬笔一次同步落盘
        if (this.tool === 'eraser') {
          if (event.type === TouchType.Down || event.type === TouchType.Move) {
            this.gestureActive = true;
            this.eraseAt(x, y, touch.pressure ?? 0.7);
          } else if (event.type === TouchType.Up) {
            this.gestureActive = false;
            if (this.eraserTouched) {
              this.eraserTouched = false;
              this.onChange(this.items);
            }
          }
          return;
        }
        if (event.type === TouchType.Down) {
          this.gestureActive = true;
          this.strokeIsPen = event.sourceTool === SourceTool.Pen;
          this.strokeMaxPressure = touch.pressure ?? 0;
          if (this.tool === 'text') {
            this.onTextTap(x, y);
            return;
          }
          if (this.isShapeTool()) {
            this.shapeStart = [x, y];
            this.shapeEnd = [x, y];
            return;
          }
          this.current = [[x, y]];
          // 增量画首点（单笔迹过程不全量 repaint，抬笔时才入 items 触发一次重绘）
          this.strokeSegment(x, y, x + 0.1, y + 0.1);
        } else if (event.type === TouchType.Move) {
          if (this.isShapeTool() && this.shapeStart !== null) {
            this.shapeEnd = [x, y];
            this.repaint();
            return;
          }
          if (this.current.length > 0) {
            if (this.strokeIsPen) {
              const p = touch.pressure ?? 0;
              if (p > this.strokeMaxPressure) {
                this.strokeMaxPressure = p;
              }
            }
            const prev = this.current[this.current.length - 1];
            this.current.push([x, y]);
            this.strokeSegment(prev[0], prev[1], x, y);
          }
        } else if (event.type === TouchType.Up) {
          this.gestureActive = false;
          if (this.isShapeTool()) {
            this.commitShape();
            this.repaint();
            return;
          }
          if (this.current.length > 0) {
            // 手写笔：按本次笔迹最大压力调制线宽（0.6x~1.6x），手指/鼠标不调制
            let width = this.penWidth;
            if (this.strokeIsPen && this.strokeMaxPressure > 0) {
              const factor = 0.6 + this.strokeMaxPressure;
              width = Math.min(this.penWidth * 1.6, Math.max(1, Math.round(this.penWidth * factor)));
            }
            const item: AnnotationItem = {
              type: this.tool === 'highlight' ? 'highlight' : 'pen',
              color: this.penColor, width: width, points: this.current
            };
            this.items = [...this.items, item];
            this.current = [];
            this.strokeIsPen = false;
            this.strokeMaxPressure = 0;
            // items 重赋值经 @Watch 触发一次全量重绘（压感调制后的线宽在这步对齐）
            this.onChange(this.items);
          }
        }
      })
  }

  aboutToAppear(): void {
    this.repaint();
  }
}
