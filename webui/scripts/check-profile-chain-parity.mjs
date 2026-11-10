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
import { existsSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

import { carrierModelName, imageChain, imageCarrier } from '../src/utils/profiles.js'

const webuiDir = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const backendSrcDir = resolve(webuiDir, '..', 'src/main/java')
const servicePath = resolve(backendSrcDir, 'ink/icoding/wechat/article/agent/LlmProfileService.java')

const problems = []
const fail = message => problems.push(message)

// ── A. 后端源码结构自检 ───────────────────────────────────────────────────────
// Docker 构建的 frontend 阶段只 COPY webui/（hermetic），后端源码不在场；那份产物只是
// 后续 backend 阶段打进去的静态资源，档案链语义以 backend 阶段编译的后端为准。
// 结构自检只在完整检出（开发机 / CI）上有意义：后端源码目录**整体**缺席时跳过 A，B 照跑。
//
// 判据用「`src/main/java` 目录是否存在」而不是「文件读不到」：Docker frontend 阶段是整棵树
// 都不在，而文件被改名/移动/误删时目录仍在——那种情况下静默跳过会让 A 段失去一次本该报警的
// 机会（F9）。目录在而目标文件不在 ⇒ 失败并指明期望路径。
let source = null
if (!existsSync(backendSrcDir)) {
  console.log('· 后端源码目录不在场（Docker frontend 阶段），跳过结构自检，仅跑行为用例')
} else {
  try {
    source = readFileSync(servicePath, 'utf8')
  } catch (error) {
    if (error?.code !== 'ENOENT') throw error
    fail(`后端源码目录存在，但定位不到 LlmProfileService.java（期望路径：${servicePath}）。`
      + '文件被改名或移动后结构自检不能静默跳过——请确认路径，或同步修改本脚本的 servicePath。')
  }
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

  // A3. addIfUsable 的判定体必须仍走 `runtime(profile)` + `available()`。
  // 为什么钉这条：跳序没变、但判定体退化成「只看档案自己的 enabled / 那一列密文」时，
  // 供应商停用、供应商没配 key、供应商被删（悬空）三种情况都会误判为可用——而 A1/A2 与
  // B 段（只喂前端实现）都看不见这个变化（复查报告 F2 的篡改实验 4 就是这样 PASS 的）。
  const usableBody = source.match(/private void addIfUsable\(([\s\S]*?)\n {4}\}/)
  if (!usableBody) {
    fail('在后端源码里定位不到 addIfUsable 的方法体——判定体自检无法进行，请同步修改本脚本。')
  } else {
    if (!/\bruntime\s*\(\s*profile\s*\)/.test(usableBody[1])) {
      fail('后端 addIfUsable 的判定体里没有调用 runtime(profile)：供应商停用/缺 key/被删三种情况'
        + '将不再被识别为不可用，而前端 usable() 仍按运行时口径判断——两处判据会漂移。')
    }
    if (!/\.available\s*\(\s*\)/.test(usableBody[1])) {
      fail('后端 addIfUsable 的判定体里没有调用 available()：端点/provider 是否为空的收紧口径'
        + '已失效，前端 usable() 的对应判据失去依据。')
    }
  }

  // A4. 运行时可用性的定义必须仍是「enabled + provider + baseUrl + apiKey 四项非空」。
  // 前端 usable() 的判据以 `runtimeAvailable` 为准，而它就是这段求值的结果；后端松掉任何一项，
  // 前端就会跟着多算进链。
  const availableBody = source.match(/public boolean available\(\)\s*\{([\s\S]*?)\n {8}\}/)
  if (!availableBody) {
    fail('在后端源码里定位不到 RuntimeProfile.available() 的方法体——判据自检无法进行。')
  } else {
    const missing = ['enabled', 'provider', 'baseUrl', 'apiKey']
      .filter(field => !new RegExp(`\\b${field}\\b`).test(availableBody[1]))
    if (missing.length) {
      fail(`后端 RuntimeProfile.available() 的判据里缺少 [${missing.join(', ')}]。`
        + '前端 profiles.js 的 usable() 以 runtimeAvailable 为准，请同步核对。')
    }
  }

  // A5. ProfileView 一旦下发 `runtimeAvailable`，就必须是 boolean 且由 `available()` 求值。
  // 为什么钉这条（契约 §3.2 F2 裁决 / §3.3 的「判据单点化」）：前端 usable() 已删掉自己那份
  // provider/baseUrl 复刻，改为读这个字段。后端若把它改名、改类型、或另写一套判据，前端会在
  // **毫不知情**的情况下退回「只看 enabled/hasApiKey」——正是 F2 的老路。
  //
  // 过渡期行为（刻意不分「尚未实现」与「已被删掉」，因为二者在源码上不可区分）：字段还没落地时
  // 只打印一行提示、不失败——后端本轮是并行改造，前端不能因为对方未合入就卡住构建；
  // 字段一旦出现，本检查立即转为强制（类型必须是 boolean，且 `available()` 必须在源码里被调用）。
  const hasRuntimeAvailable = /record ProfileView\([\s\S]*?\bruntimeAvailable\b/.test(source)
  if (!hasRuntimeAvailable) {
    console.log('· 后端 ProfileView 尚未下发 runtimeAvailable（本轮并行实现中）；'
      + '前端 usable() 暂按 enabled/hasApiKey/providerEnabled 三项兼容判据工作，字段落地后本检查自动收紧')
  } else {
    if (!/record ProfileView\([\s\S]*?\bboolean runtimeAvailable\b/.test(source)) {
      fail('后端 ProfileView 里有 runtimeAvailable，但类型不是 boolean——前端 usable() 依赖它，请核对字段声明。')
    }
    if (!/\.available\s*\(\s*\)/.test(source)) {
      fail('后端源码里找不到 `available()` 的调用点——runtimeAvailable 必须由它求值，不得另写判据。')
    }
  }
}

// ── B. 前端行为自检 ───────────────────────────────────────────────────────────
// 判据（契约 §3.2 F2 裁决 / §3.3）：后端 `ProfileView` 下发 `runtimeAvailable`——它就是
// `RuntimeProfile.available()` 的求值结果（`enabled && provider && baseUrl && apiKey`）。
// 前端 `usable()` 以它为准，其余三项只为兼容不带新字段的旧快照。夹具因此造三组：
//   · 带 runtimeAvailable=false（模拟后端判定「此刻进不了链」）；
//   · 不带 runtimeAvailable（旧快照），仍按 enabled/hasApiKey/providerEnabled 三项判；
//   · 带 runtimeAvailable=true 但其它字段照旧——验证它不会被三项以外的东西推翻。
const P = (id, over = {}) => ({
  id, name: `p${id}`, providerId: 1, providerEnabled: true, enabled: true, hasApiKey: true,
  isDefault: false, isFallback: false, imageModelName: null, isImageModel: false, modelName: `m${id}`, ...over,
})
/** 不带 providerEnabled 的档案：模拟老快照 / 未带上该字段的响应。 */
const legacyP = (id, over = {}) => {
  const profile = P(id, over)
  delete profile.providerEnabled
  return profile
}
/** 不带 runtimeAvailable 的档案：模拟本轮改造前的旧快照（判据退化为三项）。 */
const oldSnapshotP = (id, over = {}) => {
  const profile = P(id, over)
  delete profile.runtimeAvailable
  return profile
}
/** 存量回退行（`providerId` 为空、读档案自身旧列）。 */
const fallbackP = (id, over = {}) => oldSnapshotP(id, { providerId: null, providerName: null, ...over })
const ids = chain => chain.map(p => p.id)
const carrierOf = (profiles, bound) => {
  const carrier = imageCarrier(profiles, bound)
  return carrier ? carrierModelName(carrier) : null
}

// 图片载体夹具的两个来源（契约 §4.3）：勾选的档案（①，模型名取 modelName）与
// 遗留 imageModelName 的旧档案（①b，只读兼容）。下面绝大多数用例走 ①，
// 末尾几条专门钉 ①b 与两者的优先级。
const IMG = (id, over = {}) => P(id, { isImageModel: true, modelName: over.modelName ?? `img${id}`, ...over })
const LEGACY_IMG = (id, name, over = {}) => P(id, { imageModelName: name, ...over })

const cases = [
  {
    label: '无 is_default：回落第一条（不是兜底档案）',
    profiles: [IMG(1), IMG(2, { isFallback: true })],
    bound: null, chain: [1, 2], carrier: 'img1',
  },
  {
    label: '有 is_default：默认档案优先于兜底',
    profiles: [IMG(1, { isDefault: true }), IMG(2, { isFallback: true })],
    bound: null, chain: [1, 2], carrier: 'img1',
  },
  {
    label: '绑定档案是图片模型时胜出',
    profiles: [IMG(1), IMG(2, { isDefault: true })],
    bound: 1, chain: [1, 2], carrier: 'img1',
  },
  {
    label: '绑定档案被停用则跳过',
    profiles: [IMG(1, { enabled: false }), IMG(2, { isDefault: true })],
    bound: 1, chain: [2], carrier: 'img2',
  },
  {
    label: '第一条被停用：回落跳什么都不加，兜底顺延',
    profiles: [IMG(1, { enabled: false }), IMG(2)],
    bound: null, chain: [2], carrier: 'img2',
  },
  {
    label: '无任何标记：按 id 升序',
    profiles: [IMG(1), IMG(2)],
    bound: null, chain: [1, 2], carrier: 'img1',
  },
  {
    label: '只有兜底档案：默认跳取第一条',
    profiles: [IMG(3, { isFallback: true }), IMG(1)],
    bound: null, chain: [1, 3], carrier: 'img1',
  },
  {
    label: '没有 key 的档案不参与',
    profiles: [IMG(1, { hasApiKey: false }), IMG(2)],
    bound: null, chain: [2], carrier: 'img2',
  },
  {
    label: '绑定档案没有 key 则跳过',
    profiles: [IMG(1, { hasApiKey: false }), IMG(2)],
    bound: 1, chain: [2], carrier: 'img2',
  },
  {
    // 证明「供应商停用」是一条**独立于 key** 的判据：该行的 enabled 与 hasApiKey 都是 true，
    // 仅 providerEnabled=false（供应商停用/供应商被删）就必须整条掉出链，
    // 且第 2 跳（默认档案）此时什么都不加，由兜底档案顺延——与「第一条被停用」同款语义。
    label: '供应商被停用 ⇒ 该档案不在链上（key 仍在也不例外）',
    profiles: [
      IMG(1, { isDefault: true, providerEnabled: false, hasApiKey: true }),
      IMG(2, { isFallback: true }),
    ],
    bound: null, chain: [2], carrier: 'img2',
  },
  {
    // 契约 §5.2：hasApiKey 的语义是「该档案在运行时拿得到密钥」——供应商没配 key 时后端会
    // 折成 false。这条用例与上面那条各证明一半：供应商停用走 providerEnabled，
    // 供应商缺 key 走 hasApiKey，两者都不在链上。绑定跳同样要过滤，所以这里用 bound 命中它。
    label: '供应商缺 key（hasApiKey=false）⇒ 该档案不在链上',
    profiles: [IMG(1, { hasApiKey: false, providerEnabled: true }), IMG(2)],
    bound: 1, chain: [2], carrier: 'img2',
  },
  {
    label: 'providerEnabled 缺省（旧快照 / 存量回退行）视为 true，仍在链上',
    profiles: [legacyP(1, { isImageModel: true, modelName: 'L' }), P(2, { isDefault: true })],
    bound: null, chain: [2, 1], carrier: 'L',
  },
  {
    // 契约 §3.3 的核心用例：后端说「此刻进不了链」（脏 legacy 行 / 端点为空 / 供应商不可用），
    // 前端必须照办。这一行其它三项（enabled/hasApiKey/providerEnabled）全是 true——只在
    // runtimeAvailable 上体现不可用，正是旧判据的盲区（复查报告 F2 的反证实验）。
    label: 'runtimeAvailable=false ⇒ 不在链上（其余三项仍为 true 也不例外）',
    profiles: [IMG(1, { runtimeAvailable: false }), IMG(2)],
    bound: null, chain: [2], carrier: 'img2',
  },
  {
    // 绑定跳同样要过滤：否则界面会报出后端根本不会用的图片模型。
    label: '绑定的档案 runtimeAvailable=false ⇒ 跳过，兜底/其余顺延',
    profiles: [IMG(1, { runtimeAvailable: false }), IMG(2, { isFallback: true })],
    bound: 1, chain: [2], carrier: 'img2',
  },
  {
    // 缺省该字段（旧快照）⇒ 判据退化回三项，行为与改造前完全一致，不得凭空收紧。
    label: '缺 runtimeAvailable（旧快照）⇒ 仍按 enabled/hasApiKey/providerEnabled 三项判，在链上',
    profiles: [oldSnapshotP(1, { isImageModel: true, modelName: 'L' }), P(2, { isDefault: true })],
    bound: null, chain: [2, 1], carrier: 'L',
  },
  {
    // 显式 true 也不该改变任何东西：判据是 `!== false`，不是 `=== true`。
    label: 'runtimeAvailable=true ⇒ 与其他三项一致时在链上',
    profiles: [IMG(1, { runtimeAvailable: true }), IMG(2, { runtimeAvailable: true })],
    bound: null, chain: [1, 2], carrier: 'img1',
  },
  {
    // runtimeAvailable 不是万能钥匙：后端 available() 里也含 enabled，但旧快照路径下
    // enabled/hasApiKey 仍是独立判据，停用行即便 runtimeAvailable 缺省也不得入链。
    label: 'runtimeAvailable 缺省 + enabled=false ⇒ 不在链上',
    profiles: [IMG(1, { enabled: false, runtimeAvailable: true }), IMG(2)],
    bound: null, chain: [2], carrier: 'img2',
  },
  {
    label: '绑定 id 不在列表里则忽略',
    profiles: [IMG(1)],
    bound: 99, chain: [1], carrier: 'img1',
  },
  {
    label: '全链无人勾选图片模型 → null（回落全局设置）',
    profiles: [P(1), P(2, { isDefault: true })],
    bound: null, chain: [2, 1], carrier: null,
  },
  {
    label: '空列表',
    profiles: [], bound: null, chain: [], carrier: null,
  },

  // ── ① 勾选 vs ①b 旧字段（契约 §4.3 的两跳来源）─────────────────────────────
  {
    // ①b 是第二版界面留下的落点：界面不再提供该输入框，但存量数据必须继续生效。
    label: '链上没人勾选、但有档案遗留 imageModelName ⇒ ①b 旧数据跳生效',
    profiles: [LEGACY_IMG(1, 'X'), P(2, { isDefault: true })],
    bound: null, chain: [2, 1], carrier: 'X',
  },
  {
    // ① 永远先于 ①b：即使 ①b 的档案在链上更靠前，也必须用勾选的那条。
    label: '勾选(①) 优先于遗留 imageModelName(①b)，与链上顺序无关',
    profiles: [LEGACY_IMG(1, 'legacy'), IMG(2, { modelName: 'chosen' })],
    bound: null, chain: [1, 2], carrier: 'chosen',
  },
  {
    // 勾选的档案取**自己的 modelName**，不是遗留字段（同一行两者并存时证明取值来源）。
    label: '勾选的档案用 modelName 作为图片模型名，忽略同行遗留的 imageModelName',
    profiles: [{ ...IMG(1, { modelName: 'from-model-name' }), imageModelName: 'from-legacy-field' }],
    bound: null, chain: [1], carrier: 'from-model-name',
  },
  {
    // ①b 也不许绕过可用性判据：停用行即便有遗留字段也不得入链。
    label: '①b 的档案被停用 ⇒ 仍不在链上（旧字段不是特例）',
    profiles: [LEGACY_IMG(1, 'X', { enabled: false }), P(2, { isDefault: true })],
    bound: null, chain: [2], carrier: null,
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
