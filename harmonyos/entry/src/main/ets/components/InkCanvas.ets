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
  @Prop tool: string = 'pen'; // pen | highlight | line | rect | ellipse | text
  onChange: (items: AnnotationItem[]) => void = () => {};
  onTextTap: (x: number, y: number) => void = () => {};

  @State private redrawTick: number = 0;
  private settings: RenderingContextSettings = new RenderingContextSettings(true);
  private canvas: CanvasRenderingContext2D = new CanvasRenderingContext2D(this.settings);
  private layerWidth: number = 1;
  private layerHeight: number = 1;
  private current: number[][] = [];
  private shapeStart: number[] | null = null;
  private shapeEnd: number[] | null = null;

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
        if (event.type === TouchType.Down) {
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
          const item: AnnotationItem = {
            type: this.tool === 'highlight' ? 'highlight' : 'pen',
            color: this.penColor, width: this.penWidth, points: this.current
          };
          this.items = [...this.items, item];
          this.repaint();
        } else if (event.type === TouchType.Move) {
          if (this.isShapeTool() && this.shapeStart !== null) {
            this.shapeEnd = [x, y];
            this.repaint();
            return;
          }
          if (this.current.length > 0) {
            this.current.push([x, y]);
            this.repaint();
          }
        } else if (event.type === TouchType.Up) {
          if (this.isShapeTool()) {
            this.commitShape();
            this.repaint();
            return;
          }
          if (this.current.length > 0) {
            this.current = [];
            this.onChange(this.items);
          }
        }
      })
  }

  aboutToAppear(): void {
    this.repaint();
  }
}
