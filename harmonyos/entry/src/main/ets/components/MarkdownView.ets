/**
 * 轻量 Markdown 渲染组件（基础预览）：标题、段落、代码块、有序/无序列表、引用、分割线，
 * 行内支持 **粗体**、*斜体*、`行内代码`。不追求完整 CommonMark，复杂语法原样显示。
 * 桌面的完整渲染管线（公式、mermaid 等）不在本组件范围内。
 */

interface InlineSpan {
  text: string;
  bold: boolean;
  italic: boolean;
  code: boolean;
}

interface MdBlock {
  kind: string; // heading1..6 | paragraph | code | list | quote | hr
  text: string;
  spans: InlineSpan[];
}

function parseInline(text: string): InlineSpan[] {
  const spans: InlineSpan[] = [];
  const pattern = /(\*\*[^*]+\*\*)|(\*[^*\n]+\*)|(`[^`\n]+`)/g;
  let last = 0;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(text)) !== null) {
    if (match.index > last) {
      spans.push({ text: text.substring(last, match.index), bold: false, italic: false, code: false });
    }
    const raw = match[0];
    if (raw.startsWith('**')) {
      spans.push({ text: raw.substring(2, raw.length - 2), bold: true, italic: false, code: false });
    } else if (raw.startsWith('*')) {
      spans.push({ text: raw.substring(1, raw.length - 1), bold: false, italic: true, code: false });
    } else {
      spans.push({ text: raw.substring(1, raw.length - 1), bold: false, italic: false, code: true });
    }
    last = match.index + raw.length;
  }
  if (last < text.length) {
    spans.push({ text: text.substring(last), bold: false, italic: false, code: false });
  }
  if (spans.length === 0) {
    spans.push({ text: text, bold: false, italic: false, code: false });
  }
  return spans;
}

function parseBlocks(markdown: string): MdBlock[] {
  const blocks: MdBlock[] = [];
  const lines = markdown.split('\n');
  let inCode = false;
  let codeBuffer: string[] = [];
  let paraBuffer: string[] = [];

  const flushPara = (): void => {
    if (paraBuffer.length > 0) {
      const text = paraBuffer.join('\n');
      blocks.push({ kind: 'paragraph', text: text, spans: parseInline(text) });
      paraBuffer = [];
    }
  };

  for (const line of lines) {
    if (line.trimStart().startsWith('```')) {
      if (inCode) {
        blocks.push({ kind: 'code', text: codeBuffer.join('\n'), spans: [] });
        codeBuffer = [];
        inCode = false;
      } else {
        flushPara();
        inCode = true;
      }
      continue;
    }
    if (inCode) {
      codeBuffer.push(line);
      continue;
    }
    const trimmed = line.trim();
    if (trimmed.length === 0) {
      flushPara();
      continue;
    }
    const heading = /^(#{1,6})\s+(.*)$/.exec(trimmed);
    if (heading !== null) {
      flushPara();
      blocks.push({ kind: `heading${heading[1].length}`, text: heading[2], spans: parseInline(heading[2]) });
      continue;
    }
    if (/^(-{3,}|\*{3,})$/.test(trimmed)) {
      flushPara();
      blocks.push({ kind: 'hr', text: '', spans: [] });
      continue;
    }
    if (/^([-*+]|\d+\.)\s+/.test(trimmed)) {
      flushPara();
      const itemText = trimmed.replace(/^([-*+]|\d+\.)\s+/, '');
      const marker = /^([-*+]|\d+\.)/.exec(trimmed);
      const prefix = marker !== null ? marker[1] : '-';
      blocks.push({ kind: 'list', text: `${prefix} ${itemText}`, spans: parseInline(`${prefix} ${itemText}`) });
      continue;
    }
    if (trimmed.startsWith('>')) {
      flushPara();
      const quoteText = trimmed.replace(/^>\s?/, '');
      blocks.push({ kind: 'quote', text: quoteText, spans: parseInline(quoteText) });
      continue;
    }
    paraBuffer.push(trimmed);
  }
  if (inCode) {
    blocks.push({ kind: 'code', text: codeBuffer.join('\n'), spans: [] });
  }
  flushPara();
  return blocks;
}

@Component
export struct MarkdownView {
  content: string = '';
  @State private blocks: MdBlock[] = [];

  aboutToAppear(): void {
    this.blocks = parseBlocks(this.content);
  }

  build() {
    Scroll() {
      Column() {
        ForEach(this.blocks, (block: MdBlock) => {
          if (block.kind === 'code') {
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
