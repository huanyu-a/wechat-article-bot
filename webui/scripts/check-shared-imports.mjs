/**
 * 共享工具函数的导入自检。
 *
 * 背景：`vite build` 不会因为「用了未导入的标识符」而失败——它只在浏览器运行时抛
 * `ReferenceError`。ArticleEditorView 就曾用 `parseSkillIds` 却漏了 import，
 * 构建、单测全绿，但编辑器页每个请求都弹「parseSkillIds is not defined」。
 * 这里在构建前做一次静态扫描，把这类错误挡在构建阶段。
 *
 * 规则：src/utils/*.js 的导出若在某个源文件里被使用，该文件必须显式 import（或本地声明同名符号）。
 */
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const srcDir = resolve(dirname(fileURLToPath(import.meta.url)), '..', 'src')
const utilsDir = join(srcDir, 'utils')

const EXPORT_PATTERN = /^export\s+(?:const|function|class|let|var)\s+([A-Za-z_$][A-Za-z0-9_$]*)/gm
const IMPORT_PATTERN = /import\s*\{([^}]*)\}\s*from\s*['"]([^'"]+)['"]/g
const DECLARATION_PATTERN = /(?:^|\n)\s*(?:export\s+)?(?:const|let|var|function|class)\s+([A-Za-z_$][A-Za-z0-9_$]*)/g

function walk(dir) {
  return readdirSync(dir).flatMap(name => {
    const full = join(dir, name)
    if (statSync(full).isDirectory()) return walk(full)
    return /\.(vue|js|ts)$/.test(name) ? [full] : []
  })
}

/** 每个共享工具模块导出的名字 → 提供它的模块路径（用于错误提示）。 */
const sharedExports = new Map()
for (const file of readdirSync(utilsDir)) {
  if (!file.endsWith('.js')) continue
  const text = readFileSync(join(utilsDir, file), 'utf8')
  for (const match of text.matchAll(EXPORT_PATTERN)) {
    if (!sharedExports.has(match[1])) sharedExports.set(match[1], `../utils/${file.replace(/\.js$/, '')}`)
  }
}

const violations = []
for (const file of walk(srcDir)) {
  if (file.startsWith(utilsDir)) continue
  const text = readFileSync(file, 'utf8')

  const imported = new Set()
  for (const match of text.matchAll(IMPORT_PATTERN)) {
    for (const name of match[1].split(',')) {
      const clean = name.trim().split(/\s+as\s+/).pop()
      if (clean) imported.add(clean)
    }
  }

  const declared = new Set()
  for (const match of text.matchAll(DECLARATION_PATTERN)) declared.add(match[1])

  for (const [name, modulePath] of sharedExports) {
    if (imported.has(name) || declared.has(name)) continue
    if (!new RegExp(`\\b${name}\\b`).test(text)) continue
    violations.push({ file: relative(srcDir, file), name, modulePath })
  }
}

if (violations.length) {
  console.error('✗ 共享工具函数缺少 import（构建可通过，但运行时必然 ReferenceError）：')
  for (const { file, name, modulePath } of violations) {
    console.error(`  src/${file} 使用 ${name}，但未导入。修复：import { ${name} } from '${modulePath}'`)
  }
  process.exit(1)
}
console.log(`✓ 共享工具导入自检通过（${sharedExports.size} 个导出符号）`)
