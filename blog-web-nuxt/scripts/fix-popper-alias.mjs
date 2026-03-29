import {cpSync, existsSync, mkdirSync, rmSync} from 'node:fs'
import {dirname, resolve} from 'node:path'
import {fileURLToPath} from 'node:url'

/**
 * 修复 Nuxt 3 开始就存在的打包软链接问题
 * https://juejin.cn/post/7517596436180647986
 */

const scriptDir = dirname(fileURLToPath(import.meta.url))
const projectRoot = resolve(scriptDir, '..')
const serverNodeModules = resolve(projectRoot, '.output/server/node_modules')
const sourceDir = resolve(serverNodeModules, '@sxzz/popperjs-es')
const targetDir = resolve(serverNodeModules, '@popperjs/core')

if (!existsSync(sourceDir)) {
  console.warn(`[软链接修复] 包未找到: ${sourceDir}`)
  process.exit(0)
}

rmSync(targetDir, { recursive: true, force: true })
mkdirSync(dirname(targetDir), { recursive: true })
cpSync(sourceDir, targetDir, { recursive: true })

console.log(`[软链接修复] 成功复制 ${sourceDir} -> ${targetDir}`)
