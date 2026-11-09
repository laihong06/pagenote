/**
 * 图标按钮：华为系统符号（SymbolGlyph + $r('sys.symbol.*')）替代文字按钮。
 * 灰底常态 / 彩底激活态；label 仅作无障碍朗读文本，不显示；showDot 用于脏标记角标。
 * 符号名以华为 HarmonyOS Symbol 官方图标库为准（developer.huawei.com/consumer/cn/design/harmonyos-symbol）。
 */
@Component
export struct IconButton {
  symbol: Resource = $r('sys.symbol.plus');
  label: string = '';
  // 父组件运行期切换的参数必须是 @Prop，否则只在创建时赋值一次（激活态/禁用态/角标会冻结）
  @Prop active: boolean = false;
  @Prop activeColor: string = '#2263EB';
  @Prop showDot: boolean = false;
  @Prop enabledState: boolean = true;
  onTap: () => void = () => {};
  @State private hovering: boolean = false;

  build() {
    Stack({ alignContent: Alignment.TopEnd }) {
      Button() {
        SymbolGlyph(this.symbol)
          .fontSize(18)
          .fontColor([this.active ? '#FFFFFF' : '#333333'])
      }
      .type(ButtonType.Normal)
      .width(36)
      .height(30)
      .borderRadius(8)
      .backgroundColor(this.active ? this.activeColor : '#EEEEEE')
      .opacity(this.enabledState ? 1.0 : 0.4)
      .enabled(this.enabledState)
      .accessibilityText(this.label)
      .onClick(() => {
        this.onTap();
      })
      .onHover((isHover: boolean) => {
        this.hovering = isHover;
      })

      if (this.showDot) {
        Stack()
          .width(8)
          .height(8)
          .borderRadius(4)
          .backgroundColor('#FF8800')
          .offset({ x: -3, y: 3 })
      }

      // 悬停文字提示：不用 bindPopup（会被点击连带触发），自定义气泡只在鼠标/手写笔悬停时浮现；
      // 图标按钮都在各面板顶排，气泡向下浮出避免被窗口/弹层上沿裁掉
      if (this.hovering && this.label.length > 0) {
        Text(this.label)
          .fontSize(11)
          .fontColor('#FFFFFF')
          .backgroundColor('#CC333333')
          .borderRadius(4)
          .padding({ left: 6, right: 6, top: 2, bottom: 2 })
          .offset({ x: -9, y: 34 })
          .hitTestBehavior(HitTestMode.None)
          .transition(TransitionEffect.OPACITY.animation({ duration: 150 }))
      }
    }
  }
}
