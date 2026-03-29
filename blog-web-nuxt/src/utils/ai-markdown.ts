const MARKDOWN_BLOCK_PLACEHOLDER_PREFIX = '__AI_MD_BLOCK__'
const MARKDOWN_PROTECTED_BLOCK_PATTERN = /(```[\s\S]*?```|~~~[\s\S]*?~~~|\$\$[\s\S]*?\$\$)/g
const MARKDOWN_INLINE_LIST_START = '(?:\\*\\*|__|`|[A-Za-z0-9\\u4e00-\\u9fa5])'
const MARKDOWN_CHAINED_LIST_START = '(?:\\*\\*|__|`|[A-Za-z\\u4e00-\\u9fa5])'
const MARKDOWN_IMPLICIT_TITLE_SUFFIX = '(?:概述|简介|总结|分析|说明|指南|入门|基础|原理|对比|笔记|实践|方法|流程|综述)'

export function normalizeMarkdownContent(content: string, aggressive = false) {
  if (!content) {
    return ''
  }
  const normalizedInput = String(content)
    .replace(/\r\n?/g, '\n')
    .replace(/^[\u200B\u200C\u200D\u200E\u200F\uFEFF]+/g, '')
  const protectedBlocks: string[] = []
  let normalized = normalizedInput.replace(MARKDOWN_PROTECTED_BLOCK_PATTERN, (block) => {
    const placeholder = `${MARKDOWN_BLOCK_PLACEHOLDER_PREFIX}${protectedBlocks.length}__`
    protectedBlocks.push(block)
    return placeholder
  })
  normalized = normalized
    .replace(/\u00a0/g, ' ')
    .replace(/[\u200B\u200C\u200D\u200E\u200F\uFEFF]/g, '')
    .replace(/[ \t]+\n/g, '\n')
  if (aggressive) {
    normalized = repairCollapsedMarkdownBlocks(normalized)
  }
  normalized = normalized
    .split('\n')
    .map((line) => normalizeMarkdownLine(line))
    .join('\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()

  return normalized.replace(new RegExp(`${MARKDOWN_BLOCK_PLACEHOLDER_PREFIX}(\\d+)__`, 'g'), (_, index) => protectedBlocks[Number(index)] || '')
}

function normalizeMarkdownLine(line: string) {
  if (!line || !line.trim()) {
    return ''
  }
  return line
    .replace(/^([一二三四五六七八九十]+、\s*[^\n]+)$/g, '## $1')
    .replace(/^(\s*#{1,6})([^#\s])/g, '$1 $2')
    .replace(/^(\s*(?:>\s*)+)([^>\s])/g, (_, prefix, text) => `${String(prefix).replace(/>\s*/g, '> ').trimEnd()} ${text}`)
    .replace(/^(\s*[-*+])(\S)/g, '$1 $2')
    .replace(/^(\s*\d+\.)(\S)/g, '$1 $2')
    .replace(/[ \t]+$/g, '')
}

function repairCollapsedMarkdownBlocks(content: string) {
  if (!content) {
    return ''
  }
  const blockStartPattern = '(?:#{1,6}\\s+|>\\s+|[-*+]\\s+|\\d+\\.\\s+)'
  const implicitTitlePattern = new RegExp(`(^|\\n)(?!#{1,6}\\s|[-*+]\\s|\\d+\\.\\s|>\\s)([A-Za-z0-9\\u4e00-\\u9fa5《》【】()（）·]{2,24}?${MARKDOWN_IMPLICIT_TITLE_SUFFIX})(?=[A-Za-z0-9\\u4e00-\\u9fa5])`, 'g')
  const unorderedListPattern = new RegExp(`\\s+(?=[-*+]\\s+${MARKDOWN_INLINE_LIST_START})`, 'g')
  const orderedListPattern = new RegExp(`\\s+(?=\\d+\\.\\s+${MARKDOWN_INLINE_LIST_START})`, 'g')
  const chainedDashPattern = new RegExp(`([^\\n\\s\\d])\\s*[—-]{1,3}\\s*(?=${MARKDOWN_CHAINED_LIST_START})`, 'g')
  let repaired = content
    .replace(implicitTitlePattern, (_, prefix, title) => `${prefix}## ${title}\n`)
    .replace(new RegExp(`([：:。！？!?])\\s*(?=${blockStartPattern})`, 'g'), '$1\n')
    .replace(/([A-Za-z\u4e00-\u9fa5）】》])(?=\d+\.\s+)/g, '$1\n')
    .replace(/([A-Za-z0-9\u4e00-\u9fa5）】》])(?=#{1,6}\s+)/g, '$1\n')
    .replace(new RegExp(`([^\n])\s+(?=>\s+|#{1,6}\s+)`, 'g'), '$1\n')

  const normalizedLines = repaired.split('\n').flatMap((line) => {
    if (!line || !line.trim()) {
      return ['']
    }
    let currentLine = line
    if (/^\s*(?:#{1,6}\s+|[一二三四五六七八九十]+、|\d+\.\s+|[-*+]\s+)/.test(currentLine)) {
      currentLine = currentLine.replace(chainedDashPattern, '$1\n- ')
    }
    if (/^\s*[-*+]\s+/.test(currentLine)) {
      currentLine = currentLine.replace(unorderedListPattern, '\n')
    }
    if (/^\s*\d+\.\s+/.test(currentLine)) {
      currentLine = currentLine.replace(orderedListPattern, '\n')
    }
    if (/^\s*(?:>\s*)+/.test(currentLine)) {
      currentLine = currentLine.replace(/\s+(?=>\s+)/g, '\n')
      currentLine = currentLine.replace(new RegExp(`(>\\s+[^\\n]+?[：:])\\s*(?=>\\s+[-*+]\\s+)`, 'g'), '$1\n')
    }
    return currentLine.split('\n')
  })
  return normalizedLines.join('\n')
}
