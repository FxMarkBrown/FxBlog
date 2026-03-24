import { existsSync, readFileSync, readdirSync } from 'fs'
import path from 'path'

interface HtmlTransformPlugin {
  name: string
  transformIndexHtml?: (html: string) => string
}

let idPrefix = ''
const svgTitle = /<svg([^>+].*?)>/
const clearHeightWidth = /(width|height)="([^>+].*?)"/g
const hasViewBox = /(viewBox="[^>+].*?")/g
const clearReturn = /(\r)|(\n)/g

function findSvgFile(dir: string): string[] {
  const svgRes: string[] = []
  const dirents = readdirSync(dir, {
    withFileTypes: true
  })

  for (const dirent of dirents) {
    if (dirent.isDirectory()) {
      svgRes.push(...findSvgFile(path.join(dir, dirent.name)))
      continue
    }

    if (!dirent.name.endsWith('.svg')) {
      continue
    }

    const svg = readFileSync(path.join(dir, dirent.name))
      .toString()
      .replace(clearReturn, '')
      .replace(svgTitle, (_fullMatch: string, attrs: string) => {
        let width = '0'
        let height = '0'
        let content = attrs.replace(clearHeightWidth, (_attrMatch: string, attrName: string, attrValue: string) => {
          if (attrName === 'width') {
            width = attrValue
          } else if (attrName === 'height') {
            height = attrValue
          }
          return ''
        })
        if (!hasViewBox.test(attrs)) {
          content += `viewBox="0 0 ${width} ${height}"`
        }
        return `<symbol id="${idPrefix}-${dirent.name.replace('.svg', '')}" ${content}>`
      })
      .replace('</svg>', '</symbol>')
    svgRes.push(svg)
  }

  return svgRes
}

export const svgBuilder = (svgPath: string, prefix = 'icon'): HtmlTransformPlugin => {
  if (!svgPath || !existsSync(svgPath)) {
    return {
      name: 'svg-transform'
    }
  }

  idPrefix = prefix
  const res = findSvgFile(svgPath)

  return {
    name: 'svg-transform',
    transformIndexHtml(html: string) {
      return html.replace(
        '<body>',
        `
          <body>
            <svg xmlns="http://www.w3.org/2000/svg" style="position: absolute; width: 0; height: 0">
              ${res.join('')}
            </svg>
        `
      )
    }
  }
}
