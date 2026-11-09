import { display } from '@kit.ArkUI';

/**
 * onAreaChange 的 Area 尺寸单位消歧。
 * 实测（API 26 MatePad，2880px 窗口、密度 2）：1392px 宽的左栏回报 width=696——
 * 即 Area 数值是 vp 而非官方文档声称的 px；直接 px2vp 会再除一次密度导致全部几何减半
 * （连续滚动页高减半被 Fill 拉伸、批注 1000 比例系翻倍出界）。
 * 消歧：按"左栏占窗口宽度比例"（横屏约一半/竖屏约满宽）选更接近的解释。
 */
export function areaPairToPx(rawW: number, rawH: number, uiContext: UIContext): number[] {
  const density: number = uiContext.vp2px(1);
  let windowPx: number = 0;
  let landscape: boolean = true;
  try {
    const disp: display.Display = display.getDefaultDisplaySync();
    windowPx = disp.width;
    landscape = disp.width >= disp.height;
  } catch (e) {
    windowPx = 0;
  }
  const asVpW: number = rawW * density; // raw 解释为 vp → 真实 px
  const asPxW: number = rawW; // raw 解释为 px
  let treatAsVp: boolean = false;
  if (windowPx > 0 && density > 0) {
    // 左栏占窗口宽度比例：横屏约一半（减边距），竖屏上下堆叠约满宽
    const expectedPx: number = windowPx * (landscape ? 0.48 : 0.96);
    treatAsVp = Math.abs(asVpW - expectedPx) <= Math.abs(asPxW - expectedPx);
  }
  if (treatAsVp) {
    return [asVpW, rawH * density];
  }
  return [asPxW, rawH];
}
