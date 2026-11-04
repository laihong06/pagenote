/**
 * 轻量 Markdown 渲染组件：标题、段落、代码块、有序/无序列表、引用、分割线，
 * 行内支持 **粗体**、*斜体*、`行内代码`、$…$ 行内公式；块级图片经 resolveImage 解析为
 * 沙箱路径渲染（外部 http(s) 图片占位提示，保持离线）；块级公式（$$ 与 ```math）与
 * 行内公式均交给 MathView（本地 KaTeX）渲染。不追求完整 CommonMark，复杂语法原样显示。
 */

import { MathView } from './MathView';

interface InlineSpan {
  text: string;
  bold: boolean;
  italic: boolean;
  code: boolean;
  math: boolean;
}

interface MdBlock {
  kind: string; // heading1..6 | paragraph | code | list | quote | hr | image | math
  text: string;
  spans: InlineSpan[];
  src: string;
  alt: string;
}

function textSpan(text: string): InlineSpan {
  return { text: text, bold: false, italic: false, code: false, math: false };
}

function parseInline(text: string): InlineSpan[] {
  const spans: InlineSpan[] = [];
  const pattern = /(\*\*[^*]+\*\*)|(\*[^*\n]+\*)|(`[^`\n]+`)|(\$\$[^$]+\$\$)|(\$[^$\n]+\$)/g;
  let last = 0;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(text)) !== null) {
    if (match.index > last) {
      spans.push(textSpan(text.substring(last, match.index)));
    }
    const raw = match[0];
    if (raw.startsWith('**')) {
      spans.push({ text: raw.substring(2, raw.length - 2), bold: true, italic: false, code: false, math: false });
    } else if (raw.startsWith('*')) {
      spans.push({ text: raw.substring(1, raw.length - 1), bold: false, italic: true, code: false, math: false });
    } else if (raw.startsWith('`')) {
      spans.push({ text: raw.substring(1, raw.length - 1), bold: false, italic: false, code: true, math: false });
    } else if (raw.startsWith('$$')) {
      spans.push({ text: raw.substring(2, raw.length - 2), bold: false, italic: false, code: false, math: true });
    } else {
      spans.push({ text: raw.substring(1, raw.length - 1), bold: false, italic: false, code: false, math: true });
    }
    last = match.index + raw.length;
  }
  if (last < text.length) {
    spans.push(textSpan(text.substring(last)));
  }
  if (spans.length === 0) {
    spans.push(textSpan(text));
  }
  return spans;
}

function plainBlock(kind: string, text: string, spans: InlineSpan[]): MdBlock {
  return { kind: kind, text: text, spans: spans, src: '', alt: '' };
}

function parseBlocks(markdown: string): MdBlock[] {
  const blocks: MdBlock[] = [];
  const lines = markdown.split('\n');
  let inCode = false;
  let codeLang = '';
  let codeBuffer: string[] = [];
  let inMath = false;
  let mathBuffer: string[] = [];
  let paraBuffer: string[] = [];

  const flushPara = (): void => {
    if (paraBuffer.length > 0) {
      // 行内图片无法混入 ArkUI Text，占位显示 alt
      const text = paraBuffer.join('\n').replace(/!\[([^\]]*)\]\([^)]*\)/g, '[图：$1]');
      blocks.push(plainBlock('paragraph', text, parseInline(text)));
      paraBuffer = [];
    }
  };

  const flushFence = (): void => {
    const body = codeBuffer.join('\n');
    if (codeLang === 'math' || codeLang === 'latex') {
      blocks.push(plainBlock('math', body, []));
    } else {
      blocks.push(plainBlock('code', body, []));
    }
    codeBuffer = [];
    codeLang = '';
  };

  for (const line of lines) {
    const fence = /^\s*```(\w*)/.exec(line);
    if (fence !== null) {
      if (inCode) {
        flushFence();
        inCode = false;
      } else if (!inMath) {
        flushPara();
        inCode = true;
        codeLang = (fence[1] ?? '').toLowerCase();
      }
      continue;
    }
    if (inCode) {
      codeBuffer.push(line);
      continue;
    }
    const trimmed = line.trim();
    if (trimmed === '$$') {
      if (inMath) {
        blocks.push(plainBlock('math', mathBuffer.join('\n'), []));
        mathBuffer = [];
        inMath = false;
      } else {
        flushPara();
        inMath = true;
      }
      continue;
    }
    if (inMath) {
      mathBuffer.push(line);
      continue;
    }
    if (trimmed.length === 0) {
      flushPara();
      continue;
    }
    const image = /^!\[([^\]]*)\]\(([^)\s]+)(?:\s+"[^"]*")?\)$/.exec(trimmed);
    if (image !== null) {
      flushPara();
      blocks.push({ kind: 'image', text: '', spans: [], src: image[2], alt: image[1] });
      continue;
    }
    const heading = /^(#{1,6})\s+(.*)$/.exec(trimmed);
    if (heading !== null) {
      flushPara();
      blocks.push(plainBlock(`heading${heading[1].length}`, heading[2], parseInline(heading[2])));
      continue;
    }
    if (/^(-{3,}|\*{3,})$/.test(trimmed)) {
      flushPara();
      blocks.push(plainBlock('hr', '', []));
      continue;
    }
    if (/^([-*+]|\d+\.)\s+/.test(trimmed)) {
      flushPara();
      const itemText = trimmed.replace(/^([-*+]|\d+\.)\s+/, '');
      const marker = /^([-*+]|\d+\.)/.exec(trimmed);
      const prefix = marker !== null ? marker[1] : '-';
      blocks.push(plainBlock('list', `${prefix} ${itemText}`, parseInline(`${prefix} ${itemText}`)));
      continue;
    }
    if (trimmed.startsWith('>')) {
      flushPara();
      const quoteText = trimmed.replace(/^>\s?/, '');
      blocks.push(plainBlock('quote', quoteText, parseInline(quoteText)));
      continue;
    }
    paraBuffer.push(trimmed);
  }
  if (inCode) {
    flushFence();
  }
  if (inMath) {
    blocks.push(plainBlock('math', mathBuffer.join('\n'), []));
  }
  flushPara();
  return blocks;
}

@Component
export struct MarkdownView {
  content: string = '';
  /** 把笔记里的相对图片路径（如 assets/x.png）解析为可加载的沙箱绝对路径；返回空串表示不可渲染。 */
  resolveImage: (src: string) => string = () => '';
  @State private blocks: MdBlock[] = [];

  aboutToAppear(): void {
    this.blocks = parseBlocks(this.content);
  }

  private imagePath(src: string): string {
    if (src.startsWith('http://') || src.startsWith('https://') || src.startsWith('data:')) {
      return '';
    }
    return this.resolveImage(src);
  }

  private hasMath(block: MdBlock): boolean {
    return block.spans.some((span: InlineSpan): boolean => span.math);
  }

  build() {
    Scroll() {
      Column() {
        ForEach(this.blocks, (block: MdBlock) => {
          if (block.kind === 'image') {
            if (this.imagePath(block.src).length > 0) {
              Column() {
                Image(this.imagePath(block.src))
                  .width('100%')
                  .constraintSize({ maxHeight: 320 })
                  .objectFit(ImageFit.Contain)
                if (block.alt.length > 0) {
                  Text(block.alt)
                    .fontSize(12)
                    .fontColor('#999999')
                    .margin({ top: 4 })
                }
              }
              .width('100%')
              .margin({ top: 8, bottom: 8 })
            } else {
              Text(`[图片${block.alt.length > 0 ? `：${block.alt}` : ''}] ${block.src}`)
                .fontSize(13)
                .fontColor('#999999')
                .width('100%')
                .margin({ top: 4, bottom: 4 })
            }
          } else if (block.kind === 'math') {
            MathView({ latex: block.text })
              .width('100%')
              .margin({ top: 4, bottom: 4 })
          } else if (block.kind === 'code') {
            Text(block.text)
              .fontFamily('monospace')
              .fontSize(13)
              .backgroundColor('#F2F3F5')
              .borderRadius(6)
              .padding(10)
              .width('100%')
              .margin({ top: 4, bottom: 4 })
          } else if (block.kind === 'hr') {
            Divider()
              .margin({ top: 8, bottom: 8 })
          } else if (block.kind === 'quote') {
            Text() {
              ForEach(block.spans, (span: InlineSpan) => {
                Span(span.text)
                  .fontWeight(span.bold ? FontWeight.Bold : FontWeight.Normal)
                  .fontStyle(span.italic ? FontStyle.Italic : FontStyle.Normal)
                  .fontFamily(span.code ? 'monospace' : '')
              })
            }
            .fontColor('#555555')
            .backgroundColor('#F7F7F7')
            .border({ width: { left: 3 }, color: '#2263EB' })
            .padding(8)
            .width('100%')
            .margin({ top: 4, bottom: 4 })
          } else if ((block.kind === 'paragraph' || block.kind === 'list') && this.hasMath(block)) {
            // 含行内公式的段落：Text 无法混入公式组件，用 Flex 自动换行混排
            Flex({ wrap: FlexWrap.Wrap, alignItems: ItemAlign.Center }) {
              ForEach(block.spans, (span: InlineSpan) => {
                if (span.math) {
                  MathView({ latex: span.text, displayMode: false })
                } else {
                  Text(span.text)
                    .fontWeight(span.bold ? FontWeight.Bold : FontWeight.Normal)
                    .fontStyle(span.italic ? FontStyle.Italic : FontStyle.Normal)
                    .fontFamily(span.code ? 'monospace' : '')
                    .fontColor(span.code ? '#C7254E' : '#222222')
                }
              }, (span: InlineSpan, index: number) => `${index}-${span.text.length}-${span.math}`)
            }
            .width('100%')
            .margin({ top: 4, bottom: 4 })
          } else {
            Text() {
              ForEach(block.spans, (span: InlineSpan) => {
                Span(span.text)
                  .fontWeight(span.bold ? FontWeight.Bold : FontWeight.Normal)
                  .fontStyle(span.italic ? FontStyle.Italic : FontStyle.Normal)
                  .fontFamily(span.code ? 'monospace' : '')
                  .fontColor(span.code ? '#C7254E' : '#222222')
              })
            }
            .fontSize(this.headingSize(block.kind))
            .fontWeight(block.kind.startsWith('heading') ? FontWeight.Bold : FontWeight.Normal)
            .lineHeight(22)
            .width('100%')
            .margin({ top: block.kind.startsWith('heading') ? 12 : 4, bottom: 4 })
          }
        }, (block: MdBlock, index: number) => `${index}-${block.kind}-${block.text.length}`)
      }
      .width('100%')
      .alignItems(HorizontalAlign.Start)
      .padding({ left: 12, right: 12, top: 8, bottom: 24 })
    }
    .width('100%')
    .height('100%')
    .align(Alignment.TopStart)
  }

  private headingSize(kind: string): number {
    switch (kind) {
      case 'heading1': return 24;
      case 'heading2': return 21;
      case 'heading3': return 18;
      case 'heading4': return 16;
      case 'heading5': return 15;
      case 'heading6': return 14;
      default: return 15;
    }
  }
}
