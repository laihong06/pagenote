import { AnnotationItem } from '../core/Annotations';

/**
 * 手写批注层：覆盖在文档/笔记之上的透明画布。
 * 坐标用桌面相同的 1000 比例系（相对层宽），保证跨端互通。
 * 笔迹变更通过 onChange 回调给父组件，由父组件统一保存。
 */
@Component
export struct InkCanvas {
  @Prop @Watch('onItemsChange') items: AnnotationItem[] = [];
  penColor: string = '#dc3545';
  penWidth: number = 3;
  drawingEnabled: boolean = false;
  onChange: (items: AnnotationItem[]) => void = () => {};

  @State private redrawTick: number = 0;
  private settings: RenderingContextSettings = new RenderingContextSettings(true);
  private canvas: CanvasRenderingContext2D = new CanvasRenderingContext2D(this.settings);
  private layerWidth: number = 1;
  private layerHeight: number = 1;
  private current: number[][] = [];

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
    }
    ctx.globalAlpha = 1.0;
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
        const touch = event.touches[0];
        const x = Math.max(0, (touch.x) * this.scaleX());
        const y = Math.max(0, (touch.y) * this.scaleX());
        if (event.type === TouchType.Down) {
          this.current = [[x, y]];
          const item: AnnotationItem = {
            type: 'pen', color: this.penColor, width: this.penWidth, points: this.current
          };
          this.items = [...this.items, item];
          this.repaint();
        } else if (event.type === TouchType.Move) {
          if (this.current.length > 0) {
            this.current.push([x, y]);
            this.repaint();
          }
        } else if (event.type === TouchType.Up) {
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
