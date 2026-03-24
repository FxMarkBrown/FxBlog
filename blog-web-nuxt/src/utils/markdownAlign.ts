import type MarkdownIt from 'markdown-it'

const ALIGN_BLOCK_OPEN_PATTERN = /^:::\s*align-(left|right|center)\s*$/
const ALIGN_BLOCK_CLOSE_PATTERN = /^:::\s*$/

function renderAlignBody(md: MarkdownIt, content: string) {
  return `<div class="blog-align-scroll"><div class="blog-align-scroll-content">${md.render(content)}</div></div>`
}

/**
 * 为 Markdown 渲染器补充 `::: align-*` 容器语法。
 */
export function installMarkdownAlignPlugin(md: MarkdownIt) {
  md.block.ruler.before('fence', 'blog-align', (state, startLine, endLine, silent) => {
    const start = state.bMarks[startLine] + state.tShift[startLine]
    const max = state.eMarks[startLine]
    const firstLine = state.src.slice(start, max).trim()
    const alignMatch = firstLine.match(ALIGN_BLOCK_OPEN_PATTERN)

    if (!alignMatch) {
      return false
    }

    if (silent) {
      return true
    }

    let nextLine = startLine + 1
    while (nextLine < endLine) {
      const lineStart = state.bMarks[nextLine] + state.tShift[nextLine]
      const lineMax = state.eMarks[nextLine]
      const lineText = state.src.slice(lineStart, lineMax).trim()

      if (ALIGN_BLOCK_CLOSE_PATTERN.test(lineText)) {
        break
      }

      nextLine += 1
    }

    if (nextLine >= endLine) {
      return false
    }

    const direction = alignMatch[1]
    const openToken = state.push('blog_align_open', 'div', 1)
    openToken.block = true
    openToken.map = [startLine, nextLine + 1]
    openToken.attrSet('class', `blog-align-block is-${direction}`)

    const bodyToken = state.push('blog_align_body', '', 0)
    bodyToken.block = true
    bodyToken.content = state.getLines(startLine + 1, nextLine, 0, false)
    bodyToken.meta = {
      direction
    }

    const closeToken = state.push('blog_align_close', 'div', -1)
    closeToken.block = true

    state.line = nextLine + 1
    return true
  }, {
    alt: ['paragraph', 'reference', 'blockquote', 'list']
  })

  md.renderer.rules.blog_align_open = (tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options)
  md.renderer.rules.blog_align_close = (tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options)
  md.renderer.rules.blog_align_body = (tokens, idx) => renderAlignBody(md, tokens[idx].content)
}
