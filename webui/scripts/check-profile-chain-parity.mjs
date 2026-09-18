/**
 * 前端档案链 ↔ 后端 `LlmProfileService.failoverChain` 一致性自检。
 *
 * 背景：AgentsView 的「配图模型」卡片会告诉用户「配图实际会用哪个模型」。这个结论来自
 * 前端自己走一遍档案链——`webui/src/utils/profiles.js` 是后端 `failoverChain` 的**手抄镜像**。
 * 抄错任何一跳，界面就会报出一个后端根本不会用的模型名，比不显示更糟。
 *
 * 为什么需要这个脚本：`vite build` 与 `check-shared-imports.mjs` 都只看「符号有没有导入」，
 * 不看**语义**是否一致；而 webui 没有测试框架（devDeps 只有 vite + plugin-vue）。
 * 曾经真实存在的漂移：后端 `defaultProfile()` 是 `findDefault() ?? findFirst()`，
 * 前端只写了 `find(p => p.isDefault)`，漏掉「无 is_default 时回落第一条」这一跳，
 * 于是「没设默认档案 + 兜底档案声明了图片模型」时前端报兜底档案、后端用第一条档案。
 *
 * 两道检查，缺一不可：
 *   A. 结构自检——后端源码里那 4 跳的顺序与 `findFirst()` 回落是否还在。
 *      若后端改了顺序，本脚本失败并提示重新核对，避免「前端悄悄过期」。
 *   B. 行为自检——把同一批档案喂给前端实现，断言链顺序与图片模型来源符合后端契约。
 */
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

import { imageChain, imageCarrier } from '../src/utils/profiles.js'

const webuiDir = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const servicePath = resolve(webuiDir, '..', 'src/main/java/ink/icoding/wechat/article/agent/LlmProfileService.java')

const problems = []
const fail = message => problems.push(message)

// ── A. 后端源码结构自检 ───────────────────────────────────────────────────────
// Docker 构建的 frontend 阶段只 COPY webui/（hermetic），后端源码不在场；那份产物只是
// 后续 backend 阶段打进去的静态资源，档案链语义以 backend 阶段编译的后端为准。
// 结构自检只在完整检出（开发机 / CI）上有意义：文件缺席时跳过 A，B 照跑。
let source = null
try {
  source = readFileSync(servicePath, 'utf8')
} catch (error) {
  if (error?.code !== 'ENOENT') throw error
  console.log('· 后端源码不在场（Docker frontend 阶段），跳过结构自检，仅跑行为用例')
}

if (source !== null) {
  // A1. defaultProfile() 必须保留「无 is_default 时回落第一条」这一跳
  if (!/findDefault\(\)[\s\S]{0,200}?mapper\.findFirst\(\)/.test(source)) {
    fail('后端 defaultProfile() 里找不到「findDefault() 为空则回落 findFirst()」——'
      + '前端 profiles.js 的第 2 跳（ordered[0]）可能已过期，请重新核对。')
  }

  // A2. failoverChain 的 4 跳必须仍是「绑定 → 默认 → 兜底 → 其余已启用」
  const chainBody = source.match(/failoverChain\(LlmProfile bound\)\s*\{([\s\S]*?)\n {4}\}/)
  if (!chainBody) {
    fail('在后端源码里定位不到 failoverChain(LlmProfile bound) 的方法体，结构自检无法进行。')
  } else {
    // 只取「第三个实参」的方法名（`defaultProfile()` 与 `profile` 都归一成裸名）
    const hops = [...chainBody[1].matchAll(/addIfUsable\(chain, seen, ([A-Za-z_$][A-Za-z0-9_$]*)/g)]
      .map(m => m[1].trim())
    const expected = ['bound', 'defaultProfile', 'fallbackProfile', 'profile']
    if (JSON.stringify(hops) !== JSON.stringify(expected)) {
      fail(`后端 failoverChain 的跳序变了：期望 [${expected.join(', ')}]，实际 [${hops.join(', ')}]。`
        + '前端 profiles.js 必须同步调整。')
    }
  }
}

// ── B. 前端行为自检 ───────────────────────────────────────────────────────────
const P = (id, over = {}) => ({
  id, name: `p${id}`, enabled: true, hasApiKey: true,
  isDefault: false, isFallback: false, imageModelName: null, ...over,
})
const ids = chain => chain.map(p => p.id)
const carrierOf = (profiles, bound) => {
  const carrier = imageCarrier(profiles, bound)
  return carrier ? carrier.imageModelName : null
}

const cases = [
  {
    label: '无 is_default：回落第一条（不是兜底档案）',
    profiles: [P(1, { imageModelName: 'X' }), P(2, { isFallback: true, imageModelName: 'Y' })],
    bound: null, chain: [1, 2], carrier: 'X',
  },
  {
    label: '有 is_default：默认档案优先于兜底',
    profiles: [P(1, { isDefault: true, imageModelName: 'X' }), P(2, { isFallback: true, imageModelName: 'Y' })],
    bound: null, chain: [1, 2], carrier: 'X',
  },
  {
    label: '绑定档案声明图片模型时胜出',
    profiles: [P(1, { imageModelName: 'X' }), P(2, { isDefault: true, imageModelName: 'Y' })],
    bound: 1, chain: [1, 2], carrier: 'X',
  },
  {
    label: '绑定档案被停用则跳过',
    profiles: [P(1, { enabled: false, imageModelName: 'X' }), P(2, { isDefault: true, imageModelName: 'Y' })],
    bound: 1, chain: [2], carrier: 'Y',
  },
  {
    label: '第一条被停用：回落跳什么都不加，兜底顺延',
    profiles: [P(1, { enabled: false, imageModelName: 'X' }), P(2, { imageModelName: 'Y' })],
    bound: null, chain: [2], carrier: 'Y',
  },
  {
    label: '无任何标记：按 id 升序',
    profiles: [P(1, { imageModelName: 'X' }), P(2, { imageModelName: 'Y' })],
    bound: null, chain: [1, 2], carrier: 'X',
  },
  {
    label: '只有兜底档案：默认跳取第一条',
    profiles: [P(3, { isFallback: true, imageModelName: 'Z' }), P(1, { imageModelName: 'X' })],
    bound: null, chain: [1, 3], carrier: 'X',
  },
  {
    label: '没有 key 的档案不参与',
    profiles: [P(1, { hasApiKey: false, imageModelName: 'X' }), P(2, { imageModelName: 'Y' })],
    bound: null, chain: [2], carrier: 'Y',
  },
  {
    label: '绑定档案没有 key 则跳过',
    profiles: [P(1, { hasApiKey: false, imageModelName: 'X' }), P(2, { imageModelName: 'Y' })],
    bound: 1, chain: [2], carrier: 'Y',
  },
  {
    label: '绑定 id 不在列表里则忽略',
    profiles: [P(1, { imageModelName: 'X' })],
    bound: 99, chain: [1], carrier: 'X',
  },
  {
    label: '全链无人声明图片模型 → null（回落全局设置）',
    profiles: [P(1), P(2, { isDefault: true })],
    bound: null, chain: [2, 1], carrier: null,
  },
  {
    label: '空列表',
    profiles: [], bound: null, chain: [], carrier: null,
  },
]

for (const { label, profiles, bound, chain, carrier } of cases) {
  const actualChain = ids(imageChain(profiles, bound))
  if (JSON.stringify(actualChain) !== JSON.stringify(chain)) {
    fail(`[${label}] 链顺序不符：期望 [${chain}]，实际 [${actualChain}]`)
  }
  const actualCarrier = carrierOf(profiles, bound)
  if (actualCarrier !== carrier) {
    fail(`[${label}] 图片模型来源不符：期望 ${carrier}，实际 ${actualCarrier}`)
  }
}

if (problems.length) {
  console.error('✗ 前端档案链与后端 failoverChain 不一致：')
  for (const problem of problems) console.error(`  - ${problem}`)
  process.exit(1)
}
console.log(`✓ 档案链一致性自检通过（后端 4 跳结构 + ${cases.length} 个行为用例）`)
