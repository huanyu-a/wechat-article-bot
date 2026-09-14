/**
 * 第二十九轮 A · **全库验收断言的分类 + 反例自检**（纯离线：不开浏览器、不连库、不打渲染 API）。
 *
 * 起因是第二十五~二十八轮反复撞上的那件事：**「只比两侧一致」型的判据，对「打开与再打开一致地丢」
 * 这种 bug 天然免疫**。这一支把那条方法论发现变成一次全库清点，逐条回答三个问题：
 *
 *   1. **属于哪一类？**
 *      · **甲｜只比两侧一致**——两侧出自同一份代码，只能证「自洽」，证不了「对」；
 *      · **乙｜与外部真值对照**——参照物是渲染服务产物 / 真实 DB / 独立实现；
 *      · **丙｜自声明期望值**——期望值写死在样本或脚本里（比甲可靠，但取决于期望值是谁写的、写对没有）；
 *      · **丁｜根本没有判据**——只落盘 / 只打印，从不判红。
 *   2. **能不能判红？**（只有 `process.exit` 非零、或内置「判为 FAIL」自检的才算「能判红」）
 *   3. **拿一个已知坏版本喂给它，它判了什么？判 PASS 的，就是松的。**
 *
 * ⚠️ 「已知坏版本」一律取自**仓库里真实存在过的产品代码/真实跑批产物**，不是现造的语义等价物；
 *    唯一现造的是给纯离线比较器当输入的两份「被改动过的量测」，用来证明**比较器不是恒真的**。
 *
 * ⚠️ 这一支**不改任何判据**，只审计。收紧的动作在各自的脚本里，见每行的「失效范围 / 本轮动作」。
 *
 * ⚠️ **第三十轮（工程项 E1~E3）**：九步链那 14 步的退出码补完之后，本表第 2~8 行的「能判红」由「否」变「是」，
 *    并且**不再用散文写「未做」**——每一行都真的去喂一份已知有问题的输入、只看退出码：
 *    浏览器套件跑它自己的 `--selftest`（不开浏览器），汇总层把产物**复制**到临时目录改坏一处再跑
 *    （`RENDER_VERIFY_PROBE_DIR` 就是为这件事开的口子，见 `paths.mjs`）。**真产物一个字节都不动**，
 *    所以这张表可以随时重跑自证。第 17 / 18 行（`round27_c_compare`、`r27-entry-paths`）同理。
 *    仍未接退出码的只剩第 16 / 19 / 20 行——按定位是**实验与尺子**，不是门。
 *
 * 用法：node tools/render-verify/round29_gate_audit.mjs [--json]
 * 退出码：0 = 每条断言的实际表现都**与它自己声明的相符**；1 = 有断言与声明不符（说明表该更新了）。
 */
import { readFileSync, writeFileSync, existsSync, mkdtempSync, mkdirSync, cpSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { resolve, join } from 'node:path'
import { spawnSync } from 'node:child_process'
import { ROOT, BROWSER_OUT } from './paths.mjs'

const read = (name) => JSON.parse(readFileSync(resolve(BROWSER_OUT, name), 'utf8'))
const has = (name) => existsSync(resolve(BROWSER_OUT, name))
const runNode = (script, args = []) => spawnSync(process.execPath, [resolve(ROOT, script), ...args],
  { encoding: 'utf8', cwd: ROOT, timeout: 300000 })
const runPy = (script, args = []) => spawnSync('python', [resolve(ROOT, script), ...args],
  { encoding: 'utf8', cwd: ROOT, timeout: 300000 })

/**
 * 「闸是活的」的**可复跑证据**（第三十轮 E1 补，取代原先的散文式「未做」）。
 *
 * 做法：把该支要读的产物**复制**到临时目录 → 按 `改坏` 改坏一处 → 用 `RENDER_VERIFY_PROBE_DIR`
 * 把这一支指过去跑（这个环境变量就是为这件事开的口子，见 `paths.mjs`）→ **只看退出码**。
 *
 * 结果口径（与这张表的 `结果` 列一致）：
 *   `FAIL` = 退出码非零，坏输入被抓住了；`PASS` = 退出码 0，**闸写松了**；`未做` = 产物缺失。
 * 只在临时目录里改，**真产物一个字节都不动**，所以这个自检可以随时重跑。
 */
function 喂坏输入({ 复制 = [], 改坏, 脚本, 参数 = [] }) {
  for (const 项 of 复制) {
    if (!existsSync(resolve(ROOT, 'target/probe', 项))) {
      return { 结果: '未做', 证据: '缺产物 `target/probe/' + 项 + '`（先跑对应那一轮）' }
    }
  }
  const 目录 = mkdtempSync(join(tmpdir(), 'r30-gate-'))
  try {
    for (const 项 of 复制) cpSync(resolve(ROOT, 'target/probe', 项), resolve(目录, 项), { recursive: true })
    const 说明 = 改坏(目录)
    const 跑 = spawnSync(process.execPath, [resolve(ROOT, 脚本), ...参数],
      { encoding: 'utf8', cwd: ROOT, timeout: 300000, env: { ...process.env, RENDER_VERIFY_PROBE_DIR: 目录 } })
    // ⚠️ **先排掉「根本没跑起来」**（第三十一轮复查补）：`spawnSync` 起不来 / 被信号打死时
    // `status` 是 `null`，而 `null === 0` 为假 —— 原写法会把它当成「判红」，白送一条「闸是活的」。
    // 这与本表存在的理由（把假绿挑出来）恰好相反，所以这一格必须单列，绝不能算抓住。
    if (跑.error || 跑.status === null) {
      return { 结果: '未做', 码: 跑.status,
        证据: '**这一支没跑起来**（' + (跑.error ? 跑.error.code || 跑.error.message : '被信号终结 signal=' + 跑.signal)
          + '），**不构成「能判红」的证据**' }
    }
    const 判红行 = (跑.stdout || '').split('\n').filter((行) => 行.includes('❌')).slice(0, 2).join(' / ')
    return {
      结果: 跑.status === 0 ? 'PASS' : 'FAIL',
      码: 跑.status,
      证据: '喂「' + 说明 + '」→ 退出码 **' + 跑.status + '**'
        + (跑.status === 0 ? '（**没抓住**）' : '（判红）') + (判红行 ? ' · ' + 判红行.trim() : ''),
    }
  } finally {
    // 这份副本是整份 `target/probe` 里若干目录的拷贝，不删就是**每跑一次本表在临时目录里堆十几份**
    // （第三十轮实测：一次审计留下 28 个 `r30-gate-*`）。真产物本来就没动，副本用完即弃。
    rmSync(目录, { recursive: true, force: true })
  }
}

/** 临时目录里的 JSON 读写（`喂坏输入` 的 `改坏` 回调用）。 */
const 读JSON = (目录, 相对) => JSON.parse(readFileSync(resolve(目录, 相对), 'utf8'))
const 写JSON = (目录, 相对, 值) => writeFileSync(resolve(目录, 相对), JSON.stringify(值), 'utf8')

/** 「把某条样例的编辑器侧可见文字改得与参照侧不一致」——用于 9a~9d 这一族。 */
const 改样例文字 = (目录, 相对) => {
  const 档 = 读JSON(目录, 相对)
  const 样例 = 档.samples[0]
  样例.after.text = String(样例.after.text) + '自检造坏输入'
  样例.after.textLength = 样例.after.text.length
  写JSON(目录, 相对, 档)
  return 相对 + ' 第 1 例（' + 样例.id + '）的编辑器侧可见文字末位加 6 个字'
}

/** 各支浏览器脚本的 `--selftest`：内置坏输入，退出码 0 = 坏输入判红且好输入不误报。 */
function 跑浏览器自检(脚本们) {
  const 明细 = []
  let 没跑起来 = 0
  for (const 项 of 脚本们) {
    // 允许写成 `'<脚本>'` 或 `['<脚本>', ...参数]`（`run-set-browser` 要用 alt / registry 各跑一遍）。
    const [脚本, ...参数] = Array.isArray(项) ? 项 : [项]
    const 跑 = spawnSync(process.execPath, [resolve(ROOT, 脚本), ...参数, '--selftest'],
      { encoding: 'utf8', cwd: ROOT, timeout: 300000 })
    const 摘要 = (跑.stdout || '').split('\n').map((行) => 行.trim())
      .find((行) => 行.startsWith('→')) || ''
    // 同上：`status === null`（起不来 / 被信号打死）**不是**「闸抓住了坏输入」，单列。
    const 起没起 = 跑.error || 跑.status === null
    if (起没起) 没跑起来++
    明细.push('`' + 脚本.split('/').pop() + (参数.length ? ' ' + 参数.join(' ') : '') + '` '
      + (起没起 ? '⚠️ 没跑起来（' + (跑.error ? 跑.error.code || 'error' : 'signal=' + 跑.signal) + '）'
        : 跑.status === 0 ? '✅' : '❌ 退出码 ' + 跑.status)
      + (摘要 ? '（' + 摘要.replace('→ ', '') + '）' : ''))
  }
  return {
    结果: 没跑起来 ? '未做' : (明细.some((行) => 行.includes('❌')) ? 'PASS' : 'FAIL'),
    证据: 明细.join('；'),
  }
}

/** 造一份「已知坏版本」的 79 样例结构量测（改 3 处），证明比较器能判红。 */
function 造坏样例() {
  const payload = read('r26_samples_after.json')
  const ids = Object.keys(payload.samples)
  payload.samples[ids[0]].blocks[0].首字符x = (payload.samples[ids[0]].blocks[0].首字符x ?? 0) + 7
  payload.samples[ids[1]].htmlChars = (payload.samples[ids[1]].htmlChars ?? 0) + 3
  payload.samples[ids[2]].textLength = (payload.samples[ids[2]].textLength ?? 0) + 1
  writeFileSync(resolve(BROWSER_OUT, 'r26_samples_r29bogus.json'), JSON.stringify(payload, null, 1), 'utf8')
}

/** 造一份「已知坏版本」的 #38 症状量测（改 1 个叶子值），证明比较器能判红。 */
function 造坏症状() {
  const payload = read('r24_article38_r27-after_result.json')
  payload.editor.items[0].value.count = (payload.editor.items[0].value.count ?? 0) + 1
  writeFileSync(resolve(BROWSER_OUT, 'r24_article38_r29bogus_result.json'), JSON.stringify(payload, null, 1), 'utf8')
}

const 缺 = (name) => ({ 结果: '未做', 证据: '缺产物 ' + name + '（先跑对应那一轮）' })
const 未做 = (理由) => ({ 结果: '未做', 证据: 理由 })

// ===========================================================================
// 断言清单。
//   结果 = 这条闸在**已知坏版本**上判了什么：'FAIL'（抓住了）/ 'PASS'（没抓住 = 松的）/ '未做'
//   期望 = 它**应当**判什么：'FAIL'（该抓）/ 'PASS'（已声明的盲区，抓不住是已知的）
//   相符 = 结果 === 期望（'未做' 一律算相符，但会在表里标出来）
// ===========================================================================
const CHECKS = [
  // ------------------------------------------------------------------
  // 一、九步链里的步骤 —— 也就是「今天的绿灯」的直接来源
  // ------------------------------------------------------------------
  {
    断言: '1/9 vite build（探针 dist 构建）', 类别: '构建', 在九步链: true, 有退出码: true,
    已知坏版本: '（构建失败本身即坏版本）', 期望: '未做',
    自检: async () => 未做('构建错误会让 vite 返回非零。**这是九步链里唯一真正能判红的步骤。**'),
    失效范围: '只拦构建失败；对「构建成功但行为错」零效力。',
  },
  {
    断言: '2/9 run-all-browser · 3/9 run-combo · 4/9 run-set alt · 5/9 run-set registry · 6/9 run-article · 8/9 run-r16',
    类别: '丁｜收集器', 在九步链: true, 有退出码: true,
    已知坏版本: '各支 `--selftest` 内置的坏输入（少一条样例 / 有稿件没挂载 / 残留公式源码 / 有图没加载全 …）',
    期望: 'FAIL',
    自检: async () => 跑浏览器自检([
      'tools/render-verify/browser/run-all-browser.mjs',
      'tools/render-verify/browser/run-combo-browser.mjs',
      // run-set-browser 不传集合时默认只跑 alt（10 条），registry（76 条）**必须显式点名**，
      // 否则这一支的自检只覆盖了十分之一不到的集合（第三十轮实测）。
      ['tools/render-verify/browser/run-set-browser.mjs', 'alt'],
      ['tools/render-verify/browser/run-set-browser.mjs', 'registry'],
      'tools/render-verify/browser/run-article.mjs',
      'tools/render-verify/browser/run-r16-browser.mjs',
    ]),
    失效范围: '判的是「收集完整 ＋ 编辑器层 fail ＋ 各支自己的既定不变量」，**不判上游层**——'
      + '`not-rendered` / `nested-unsupported` / `silently-lost` 是已确立的引擎结论，计入会让这道闸永远红着。 '
      + '⚠️ **6/9 当前是真红**（`EXIT=1`）：`#16` / `#35` 正文里引用的 13 个外链图（`robocopmao.github.io`）'
      + '**13/13 全部返回 404**（站点根 `/` 仍是 200，`/r-markdown/` 页面在，图没了），属外部图床失效，'
      + '不是本项目缺陷——**保留红灯，不豁免**。',
  },
  {
    断言: '7/9 verify-live-app（43/44 活体取证）', 类别: '乙（活体前端）', 在九步链: true, 有退出码: true,
    已知坏版本: '`--selftest` 内置的坏输入（结果集为空 / `ready=false` / 可见文字里残留公式源码 / 指纹三项不成立）',
    期望: 'FAIL',
    自检: async () => 跑浏览器自检(['tools/render-verify/browser/verify-live-app.mjs']),
    失效范围: '判的是「应用实际对外提供的那份前端」有没有画出来（这一支与 6/9 量的是**两份不同构建**，'
      + '见手册 §3.4 的说明）；chunk 指纹只判「是不是带修复的那一份」，不判渲染质量。',
  },
  {
    断言: '9a summarize-all（79 样例 pass / na / fail / unverified）', 类别: '乙', 在九步链: true, 有退出码: true,
    已知坏版本: '临时目录里把 `browser/all_result.json` 第 1 例的编辑器侧可见文字改得与参照侧不一致',
    期望: 'FAIL',
    自检: async () => 喂坏输入({
      复制: ['component_matrix.json', 'browser/all_result.json'],
      改坏: (目录) => 改样例文字(目录, 'browser/all_result.json'),
      脚本: 'tools/render-verify/browser/summarize-all.mjs',
    }),
    失效范围: '参照栏 = 渲染服务真实产物（外部真值），判据不盲；现在 `fail` 数会接到退出码上。'
      + '`na` 仍带内置复验（`check(ref)` 不成立即判 `unverified`，不是悄悄算 na）。'
      + '⚠️ **第三十二轮加「分母锚」**：应有条数不取 `rows.length`（那是遍历产物生成的，少几条就少几条），'
      + '而取 `component_matrix.json` 的样例数——产物被截成 0/1 条时判红（反事实对照已验：剥掉那一句即由红转绿）。',
  },
  {
    断言: '9b summarize-combos（17 组合）· 9g summarize-r16（11 条）', 类别: '乙', 在九步链: true, 有退出码: true,
    已知坏版本: '9b：`combo_result.json` 第 1 例改字；9g：`r16/r16.json` 少一条用例（外加 `--selftest` 的 6 条）',
    期望: 'FAIL',
    自检: async () => {
      const 乙 = await 喂坏输入({
        复制: ['component_matrix.json', 'combos', 'browser/combo_result.json'],
        改坏: (目录) => 改样例文字(目录, 'browser/combo_result.json'),
        脚本: 'tools/render-verify/browser/summarize-combos.mjs',
      })
      const 庚 = await 喂坏输入({
        复制: ['r16', 'browser/r16_result.json'],
        改坏: (目录) => {
          const 档 = 读JSON(目录, 'r16/r16.json')
          const 删掉 = 档.cases.pop()
          写JSON(目录, 'r16/r16.json', 档)
          return 'r16/r16.json 删掉一条用例（' + 删掉.id + '），浏览器产物仍是 11 条'
        },
        脚本: 'tools/render-verify/browser/summarize-r16.mjs',
      })
      return {
        结果: 乙.结果 === 'FAIL' && 庚.结果 === 'FAIL' ? 'FAIL' : 'PASS',
        证据: '9b ' + 乙.证据 + ' ‖ 9g ' + 庚.证据,
      }
    },
    失效范围: '⚠️ **9g 的用例数不读它自己那份产物**——第二十九轮的自检当场抓到第一版写的是 '
      + '`backend.cases.length`（`rows` 就是遍历它生成的，恒真，删一条用例照样判绿）。'
      + '现在 11 是**域常量**（用户逐字标注的 11 条），另拿 `r16_result.json` 的 `samples.length` 交叉对账。'
      + '9b 只看编辑器层的 `fail`，上游 `silently-lost` 不计入（同 6/9 的理由）。'
      + '⚠️ **第三十二轮给这两支都加了「分母锚」**：9b 锚 `combos/combos.json` 的 `cases`、9g 仍锚域常量 11；'
      + '产物被截成 0/1 条时判红（反事实对照已验）。'
      + '⚠️ **第三十四轮又抓到 9g 第二处恒真式**：原先只有一个**总**上限（`<= 152`），'
      + '于是「甲例差异涨 20、乙例降 20」总数不变、闸照样判绿——而这两件事的含义完全相反。'
      + '现在改成**逐例上限**（`每例上限`，总和即总上限），并补了第 6 条自检用例'
      + '「总数不变、差异在两条用例之间搬家」（实测：只钉总数时这一条判绿，钉到例之后判红）。'
      + '本次基线变更本身（`r16-09-table-card` 5 → 25，其余 10 例一字未动）的逐条归因写在 '
      + '`summarize-r16.mjs` 文件头：那 20 条全在表格这一例、方向朝产品真值，'
      + '来源是本支参照栏被 `.ProseMirror` 兜底样式污染（`probe_r16.js:57`），修法把编辑器栏摘出来之后'
      + '原本被「两边一起脏」抹平的差如实显形。',
  },
  {
    断言: '9c / 9d summarize-alt（等上游替代 10 · 注册表全族 76）', 类别: '乙 + 丙', 在九步链: true, 有退出码: true,
    已知坏版本: '9c：`alt_result.json` 第 1 例改字；9d：把 `registry_result.json` 第 1 例的参照侧**伪造成好产物**'
      + '（`chars>0`、可见文字无字面 `:::`），编辑器侧文字多 1 字——不伪造的话这一族全是 `na`，没有可判对象',
    期望: 'FAIL',
    自检: async () => {
      const 丙 = await 喂坏输入({
        复制: ['alt', 'browser/alt_result.json'],
        改坏: (目录) => 改样例文字(目录, 'browser/alt_result.json'),
        脚本: 'tools/render-verify/browser/summarize-alt.mjs', 参数: ['alt'],
      })
      const 丁 = await 喂坏输入({
        复制: ['registry', 'browser/registry_result.json'],
        改坏: (目录) => {
          const 档 = 读JSON(目录, 'browser/registry_result.json')
          const 样例 = 档.samples[0]
          样例.reference.html = '<p>参照侧好产物</p>'
          样例.reference.text = '参照侧好产物'
          样例.reference.textLength = 6
          样例.reference.chars = 100
          样例.after.text = 样例.reference.text + 'X'
          样例.after.textLength = 样例.after.text.length
          写JSON(目录, 'browser/registry_result.json', 档)
          return 'registry_result.json 第 1 例（' + 样例.id + '）参照侧伪造成好产物＋编辑器侧多 1 字'
        },
        脚本: 'tools/render-verify/browser/summarize-alt.mjs', 参数: ['registry'],
      })
      return {
        结果: 丙.结果 === 'FAIL' && 丁.结果 === 'FAIL' ? 'FAIL' : 'PASS',
        证据: '9c ' + 丙.证据 + ' ‖ 9d ' + 丁.证据,
      }
    },
    失效范围: '编辑器层的参照 = 渲染服务产物（乙）；后端层的 `missingMust` / `missingHtml` / `forbidRaw` / colors '
      + '是**写死在用例生成脚本里**的期望值（丙），若写错判据会跟着错。'
      + '⚠️ 9d 的 76 组**正常输入下全是 `na`**（参照侧本身是坏产物），所以它的红灯只能靠「参照侧被伪造成好产物」'
      + '这类输入来证伪——这正是上面喂的那一份。'
      + '⚠️ **第三十二轮给这两支都加了「分母锚」**：应有条数取 `<SET>/<SET>.json` 的 `cases`（另一个脚本写的用例清单），'
      + '产物被截成 0/1 条时判红（反事实对照已验：剥掉那一句即由红转绿）。',
  },
  {
    断言: '9e round10_component_paths（组件能力对照表终稿）', 类别: '丁｜聚合器', 在九步链: true, 有退出码: true,
    已知坏版本: '临时目录里从 `component_matrix.json` 删掉一条样例（清单少一条、真浏览器实测仍 79 行）',
    期望: 'FAIL',
    自检: async () => 喂坏输入({
      复制: ['component_matrix.json', 'round10_registry_closure.json', 'round10_article_coverage.json',
        'browser/all_summary.json', 'browser/registry_summary.json', 'browser/alt_summary.json',
        'browser/articles_result.json'],
      改坏: (目录) => {
        const 档 = 读JSON(目录, 'component_matrix.json')
        const 行 = 档.rows || 档
        const 删掉 = 行.pop()
        写JSON(目录, 'component_matrix.json', 档)
        return 'component_matrix.json 删掉一条样例（' + 删掉.id + '），all_summary.json 仍是 79 行'
      },
      脚本: 'tools/render-verify/round10_component_paths.mjs',
    }),
    失效范围: '逐行判定直接取自 all_summary / registry_summary / alt_summary 的原始行，自己不另判；'
      + '⚠️ 第二十九轮的自检抓到第一版判据是**恒真式**（「表 A 认领数 + 表 B 行数 == 样例总数」，'
      + '两边都是从同一个 `matrixRows` 数出来的互补两半，删一条样例两边一起少）。'
      + '现在改成**换一个来源对账**：清单（`component_matrix.json`）对实测（`all_summary.json`）。',
  },
  {
    断言: '9f round11_crosscheck（换一条通道 + 3 个已知支持对照组）', 类别: '乙 + 对照', 在九步链: true, 有退出码: true,
    已知坏版本: '`--selftest` 内置的坏输入（存疑组 / 对照组假阳性 / 退化输入）——本支要打真实渲染 API，'
      + '没有「换成一份坏产物」这种喂法，断言只能落在 `judge()` 这个纯函数上',
    期望: 'FAIL',
    自检: async () => {
      const 跑 = runPy('tools/render-verify/gen/round11_crosscheck.py', ['--selftest'])
      return {
        结果: 跑.status === 0 ? 'FAIL' : 'PASS',
        证据: '`--selftest` 退出码 **' + 跑.status + '**' + (跑.status === 0 ? '（4 条用例：好输入不误报、'
          + '存疑组 / 对照组假阳性 / 退化输入逐条判红）' : '（**没抓住**）'),
      }
    },
    失效范围: '对照组（`:::breaking` / `:::callout type="tip"` / `<badge … />`）用来证明检测器不是「一律判未渲染」'
      + '——**这是全库最好的一个反例设计**。它每次真打渲染 API，结论只对「现在这个引擎版本」成立。'
      + '⚠️ **第三十二轮实测的缺口（待拍板，未改）**：`target/probe/round11_live_bundle.js` 不在时，'
      + '本支照旧 `EXIT=0`，但产物里「通道 B2」整节**静默消失**（88 行 → 75 行，`summary` 字段一个不缺）——'
      + '那是「38 个 `layout-*` 只有注册条目」这条结论的第二来源。详见 `known-issues-handoff.md` §3.33①/⑤ D1。',
  },

  // ------------------------------------------------------------------
  // 二、链外、真正能判红的闸 —— 逐条喂已知坏版本
  // ------------------------------------------------------------------
  {
    断言: 'r26-leading-ws-samples --compare（79 样例改前/改后逐 id 逐项相同）',
    类别: '甲｜只比两侧一致', 在九步链: false, 有退出码: true,
    已知坏版本: '现造一份「被改动过」的结构量测 r26_samples_r29bogus.json（3 处改动）', 期望: 'FAIL',
    自检: async () => {
       if (!has('r26_samples_after.json')) return 缺('r26_samples_after.json')
       造坏样例()
       const out = runNode('tools/render-verify/browser/r26-leading-ws-samples.mjs', ['--compare', 'after', 'r29bogus'])
       const line = (out.stdout || '').split('\n').find((text) => /逐条不同/.test(text)) || ''
       return { 结果: out.status === 4 ? 'FAIL' : 'PASS', 证据: '比较器 exit=' + out.status + ' · ' + line.trim() }
     },
    失效范围: '**对「改前改后一致地错」零效力**——它的用途本来就是**不变量**（证修法不改动 79 个样例的可观测行为），'
      + '这一点它做得到，且比较器确实能判红。「改前本来就错」这一格由 r26-leading-ws-effect / r28 判据③ 负责。',
    修紧: '**本轮动作：不改判据，在脚本头部把盲区写死**（「本支属甲类，对『改前改后一致地错』零效力，'
      + '那一格由 r26-leading-ws-effect 判据① 与 r28 判据③ 负责」）。'
      + '不改判据的理由：这条闸量的是**不变量**，改成「与真值对照」就换了它要量的东西，'
      + '而那个职责已经有人在做——再写一条只会让两处结论可能打架。',
  },
  {
    断言: 'r26-leading-ws-effect 判据①②③（段首空白：打开在位 / 出口带着 / 重开还在）',
    类别: '丙｜自声明期望值', 在九步链: false, 有退出码: true,
    已知坏版本: 'target/probe/r26/before-dist 的真跑存档 r26_effect_before.json', 期望: 'FAIL',
    自检: async () => {
       if (!has('r26_effect_before.json')) return 缺('r26_effect_before.json')
       const out = runNode('tools/render-verify/browser/r26-leading-ws-effect.mjs', ['--selftest'])
       const line = (out.stdout || '').split('\n').filter((text) => /抓住坏版本/.test(text)).map((t) => t.trim()).join(' / ')
       return { 结果: out.status === 0 ? 'FAIL' : 'PASS', 证据: '--selftest exit=' + out.status + ' · ' + line }
     },
    修紧: '**本轮修紧**：原判据② `saved.includes(inputLeading.replace(/[ \\t]/g,""))` 的入参替换完是**空串**，'
      + '`String.includes("")` 恒真 ⇒ 这条判据**不可能失败**；原判据③只比「灌回后 == 打开后」，两边同源 ⇒ **甲类盲区**。'
      + '收紧后同一批存档离线复判：坏版本 ①/②/③ 各 **1/7**（只有全角空格 A5 存活），当前包与修复包各 **7/7**。',
    失效范围: '修紧后：三条都能抓住「打开就丢段首空白」这一类。仍不覆盖的是**段首空白以外的**量（列宽、行高、颜色…），'
      + '那些不在这支的七条语料里。',
  },
  {
    断言: 'r26-table-colwidth-exit 判据①②（窄列表格列宽的保存出口）',
    类别: '乙 + 丙 + 内置自检', 在九步链: false, 有退出码: true,
    已知坏版本: '第二十五轮存档的旧编辑器出口（两列都塌成 min-width:25px）', 期望: 'FAIL',
    自检: async () => {
       if (!has('r26_colwidth_exit.json')) return 缺('r26_colwidth_exit.json')
       const payload = read('r26_colwidth_exit.json')
       const st = payload.selfTest
       if (!st) return 未做('产物里没有 selfTest 段')
       return { 结果: st.判为FAIL ? 'FAIL' : 'PASS',
         证据: '内置自检：存档出口 ' + JSON.stringify(st.旧编辑器出口) + ' 套同一套规则 → 判为FAIL=' + st.判为FAIL }
     },
    失效范围: '**这是全库唯一一条把「反例自检」写进脚本本体、并以它作为闸的完整性的闸**，'
      + '可作模板。仍然只管列宽，不管其他量。',
  },
  {
    断言: 'r25-save-exit-roundtrip 判据①（保存出口灌回后与保存那一刻逐条相同）',
    类别: '甲｜只比两侧一致', 在九步链: false, 有退出码: true,
    已知坏版本: 'target/probe/r26/before-dist（第二十六轮修复前的整包前端，真跑存档）', 期望: 'PASS',
    自检: async () => {
       const file = 'r25_roundtrip_setcontent_all_r29blindcheck_result.json'
       if (!has(file)) return 缺(file)
       const payload = read(file)
       const v = payload.verdict
       // 本轮新立的 `--selftest`（离线、与主流程共用同一把尺子）也要跑一遍：
       // 它证明的是「尺子另一头是灵的」——盲区照报，但别把「盲」误当成「瞎」。
       const st = runNode('tools/render-verify/browser/r25-save-exit-roundtrip.mjs', ['--selftest'])
       const st线 = (st.stdout || '').split('\n').find((text) => /反例自检通过|反例自检\*\*不通过/.test(text)) || ''
       return { 结果: v.differences.length ? 'FAIL' : 'PASS',
         证据: 'bundle=' + String(payload.bundle).split(/[\\/]/).pop() + '（修复前）· 逐条 '
           + v.identical + '/' + v.compared + ' 条完全相同（真正量到数的 ' + v.coveredAtSave + ' 条）· 差异 ' + v.differences.length + ' 条'
           + ' · 整段 DOM 重排 style 后 identical=' + payload.domDigest.normalizedIdentical
           + ' ‖ 新立 --selftest exit=' + st.status + '（' + st线.trim() + '）' }
     },
    失效范围: '**对「打开时就一致地丢」型 bug 无效，本轮实测坐实**：修复前的整包前端上判据① **11/11 全过、差异 0 条、exit 0**。'
      + '抓这类 bug 的是 r26-leading-ws-effect 判据① 与 r28 判据③。',
    修紧: '**本轮动作**：① 不改判据（改了就不是在量「所见 = 所存」这个不变量），② 把盲区写进脚本头部，'
      + '③ **新立 `--selftest`（离线，与主流程共用同一把尺子）**并已跑通 EXIT=0：'
      + '自比差异 0 条（不误报）／把「重灌后」的 `r16-01-changelog.容器边框宽` 从 1 改成 2 → 差异恰好 1 条且 id 对得上（判红）／'
      + '并打印该存档的真实结论（11/11、差异 0）作为盲区实据。'
      + '为了让自检走真判据，`diffItems` / `diffBlocks` 已从主流程提成文件顶部的纯函数，主流程改为调用同一份。',
  },
  {
    断言: 'r28-roundtrip-gate 判据①②（往返前后逐叶子值相同 / 二次往返稳定）',
    类别: '甲｜只比两侧一致', 在九步链: false, 有退出码: true,
    已知坏版本: 'target/probe/r26/before-dist 的真跑存档 r28_roundtrip_round26-before.json', 期望: 'PASS',
    自检: async () => {
       const file = 'r28_roundtrip_round26-before.json'
       if (!has(file)) return 缺(file)
       const t = read(file)
       const 叶 = t.samples.reduce((sum, s) => sum + (s.判据一_叶子差异 || []).length, 0)
       const 块 = t.samples.reduce((sum, s) => sum + (s.判据一_块差异 || []).length, 0)
       const 二 = t.samples.reduce((sum, s) => sum + (s.判据二_叶子差异 || []).length + (s.判据二_块差异 || []).length, 0)
       return { 结果: (叶 + 块 + 二) ? 'FAIL' : 'PASS',
         证据: '修复前 bundle 上：判据① 叶子差异 ' + 叶 + ' 处、块差异 ' + 块 + ' 处；判据② 差异 ' + 二 + ' 处' }
     },
    失效范围: '判据①② 对「两边一致地丢」无效（本轮实测：修复前 0 处差异）。'
      + '**但这条闸整体不盲**——判据③（入口期望值）在同一份产物上报 4 处失败、整闸 exit 1。',
  },
  {
    断言: 'r28-roundtrip-gate 判据③（每条样本自己声明要保住的量）', 类别: '丙｜自声明期望值',
    在九步链: false, 有退出码: true,
    已知坏版本: '同上（修复前 bundle 的真跑存档）+ 内置离线自检①（第二十六轮之前的存档，缩进 0px）', 期望: 'FAIL',
    自检: async () => {
       const file = 'r28_roundtrip_round26-before.json'
       if (!has(file)) return 缺(file)
       const t = read(file)
       const 失败 = t.samples.reduce((sum, s) => sum + (s.判据三_不成立 || []).length, 0)
       const st = t.selfTest || {}
       return { 结果: 失败 ? 'FAIL' : 'PASS',
         证据: '修复前 bundle 上判据③ 报 ' + 失败 + ' 处不成立（' + (t.failures || []).map((f) => f.项 + '@' + f.id).join('、') + '）'
           + ' · 内置离线自检① 判为FAIL=' + st.判为FAIL }
     },
    失效范围: '只在**样本声明了**期望值的那几个量上有效（当前 6 条样本：段首空格/制表符缩进、表格列宽、checklist、quote-card 的盒）。',
  },
  {
    断言: 'r16-compare-probe-vs-live（探针页 vs 真实应用界面，70 组）',
    类别: '甲｜只比两侧一致', 在九步链: false, 有退出码: true,
    已知坏版本: '① **值级差异**（`--selftest` 现造：改掉一个叶子的量测 / 第二十二轮**异宽**量测）——'
      + '本支该抓这一类；② **同源一致**（`_before_fix_*` 与 `_after_fix_*` 两对真实存档，各出自同一份前端的同一次构建）'
      + '——本支声明抓不住的盲区', 期望: 'FAIL',
    自检: async () => {
      /**
       * ⚠️ **第三十四轮订正：反例必须是「同一代」的两份产物。**
       *
       * 本行原先的喂法是「**新**探针页 `r16_result.json` + 第二十二轮以前的真实界面量测」。
       * 那是**跨代混喂**：第三十四轮改的是编辑器扩展（新增表格标记类），探针页随之重建，
       * 而存档里的真实界面量测还是旧前端量出来的。两边本来就不是同一份代码，比出来必然有差
       * ——实测 2 组（`步内文字 lineHeight` 15 条、`步骤表结构 parent table.mf-preserved`），
       * 这与「这把尺子抓不抓得住 bug」毫无关系，纯粹是喂法的产物。
       * 本支的定位是甲类「只比两侧一致」，它要的是**同源**；跨代输入恰恰破坏了这个前提。
       *
       * 订正后取自同一批存档、两侧同代：
       *   - `_before_fix_*` 一对（修复前那一代）；`_after_fix_*` 一对（修复后那一代）——两对都**期望 PASS**。
       * 真正证明「这把尺子不是恒绿」的是随后的 `--selftest`（三条：不误报 / 异宽判红 / 值级差异判红），
       * 它现造坏输入、用同一把尺子，不依赖任何历史存档的年代是否对得上。
       *
       * 用 `RENDER_VERIFY_PROBE_DIR` 把两份**复制**到临时目录再跑：真产物一个字节不动。
       */
      for (const name of ['_before_fix_r16_result.json', '_before_fix_r16_live_widthmatch_result.json',
        '_after_fix_r16_result.json', '_after_fix_r16_live_widthmatch_result.json']) if (!has(name)) return 缺(name)
      const 同代 = (探针, 界面) => {
        const 目录 = mkdtempSync(join(tmpdir(), 'r34-samegen-'))
        try {
          // 两份都要落在 `<目录>/browser/`——`BROWSER_OUT = OUT + '/browser'`，本支读的就是那里。
          mkdirSync(resolve(目录, 'browser'), { recursive: true })
          writeFileSync(resolve(目录, 'browser', 'r16_result.json'), readFileSync(resolve(BROWSER_OUT, 探针)))
          cpSync(resolve(BROWSER_OUT, 界面), resolve(目录, 'browser', 界面))
          const 跑 = spawnSync(process.execPath,
            [resolve(ROOT, 'tools/render-verify/browser/r16-compare-probe-vs-live.mjs'), 界面],
            { encoding: 'utf8', cwd: ROOT, timeout: 300000, env: { ...process.env, RENDER_VERIFY_PROBE_DIR: 目录 } })
          const 行 = (跑.stdout || '').split('\n').find((text) => /有差异/.test(text)) || ''
          return { 码: 跑.status, 行: 行.trim(),
            错: 跑.status === 3 ? (跑.stderr || '').split('\n').filter((text) => text.trim()).slice(0, 2).join(' / ') : '' }
        } finally { rmSync(目录, { recursive: true, force: true }) }
      }
      const 前 = 同代('_before_fix_r16_result.json', '_before_fix_r16_live_widthmatch_result.json')
      const 后 = 同代('_after_fix_r16_result.json', '_after_fix_r16_live_widthmatch_result.json')
      const st = runNode('tools/render-verify/browser/r16-compare-probe-vs-live.mjs', ['--selftest'])
      const st线 = (st.stdout || '').split('\n').find((text) => /反例自检通过|反例自检\*\*不通过/.test(text)) || ''
      /**
       * ⚠️ **本行的结果取自 `--selftest`，不取自那两喂。**
       *
       * 表头对「结果」列的定义是 `FAIL`（抓住了）/ `PASS`（没抓住 = 松的）。「没抓住」这一列在本表里
       * 是**给已知盲区留的**（第 12 / 13 行就是这么用的，它们的 `期望` 也写 `PASS`）——本支同样有已知盲区
       * （上面「失效范围」第一句），所以它那两喂按定义只能落 `PASS`，与本表语义一致。
       * 但把本行整条标成「没抓住」是**误导**：`--selftest` 三条（不误报 / 异宽判红 / 值级差异判红）
       * 全部通过，即它对**值级差异**确实会判红，并不瞎。所以结果取 `--selftest`，
       * 而那两喂的真实读数一并写进证据里（可复核）。
       *
       * **这不是为了把本行凑绿**：把 `--selftest` 拿掉、或它任一判红用例不再成立，本行立刻回到
       * `PASS`（判松），`期望` 也随之对不上、整张表判红。也就是说这一格仍然是**能被证伪**的，
       * 只是被证伪的对象从「它抓不住同源一致的错」（结构性做不到，写在失效范围里）换成了
       * 「它抓不抓得住该抓的值级差异」（做得到，且当场可重跑）。
       */
      const 自检过 = st.status === 0
      return { 结果: 自检过 ? 'FAIL' : 'PASS',
        证据: '`--selftest` exit=' + st.status + '（' + st线.trim() + '）'
          + ' ‖ **同源对照**（本支的已知盲区，按定义判不出坏）'
          + '：修复前那一对 exit=' + 前.码 + ' · ' + 前.行 + (前.错 ? '（' + 前.错 + '）' : '')
          + '；修复后那一对 exit=' + 后.码 + ' · ' + 后.行 + (后.错 ? '（' + 后.错 + '）' : '') }
    },
    失效范围: '**对「两条路径一起错」无效，本轮实测坐实**：换第二十二轮修复前的真实界面量测，**70 组里 0 组有差异、exit 0**。'
      + '它量的是「探针页与真实界面是否同源一致」，两边用的是同一份前端。'
      + '真正管「值对不对」的是 `summarize-r16.mjs`（参照栏 = 渲染服务产物，乙类）。'
      + '⚠️ **第三十四轮实测到第二类盲区**：它经不起**跨代**输入——探针页与真实界面量测若出自不同次前端构建，'
      + '差异条数只反映「这两次构建之间代码变了」，不反映任何缺陷。本支的输入必须成对取自同一代（见自检里的注释）。',
    修紧: '**本轮动作**：① 不改判据（改了就不是在量「两条入口是否同源」），② 把盲区写进脚本头部，'
      + '③ **新立 `--selftest`** 并已跑通 EXIT=0 —— 它 spawn 本脚本自己、用的就是同一把尺子，三条一起判：'
      + '①当前包（同宽）exit 0 不误报／②第二十二轮**异宽**量测 exit 1（13 组差异，尺子对成片平移会判红）／'
      + '③现造改掉一个叶子的量测 exit 1（`r16-01-changelog/外层容器.count 7→8`，值级差异会判红）。'
      + ' ⚠️ **第三十四轮订正喂法**：原先那一喂是跨代混喂（新探针页 + 旧真实界面量测），它报出的 FAIL 是**喂法的产物**，'
      + '不是闸的表现；已改成同代成对喂（`_before_fix_*` / `_after_fix_*` 各一对，均期望 PASS），'
      + '「能判红」仍由 `--selftest` 的三条负责。',
  },
  {
    断言: 'r16-width-equiv-test（第 9 条余下那条高度差的双向证伪）', 类别: '丙｜对照实验',
    在九步链: false, 有退出码: false,
    已知坏版本: '（本身就是一次证伪实验，不是闸）', 期望: '未做',
    自检: async () => 未做('它把「两栏内容盒差 38px」这个假设**双向**验了一遍（同宽 / 异宽各一次），'
      + '结论是那 1 条高度差属测量假象。没有退出码，是论证材料不是闸。'),
    失效范围: '不适用（实验，不是门）。',
  },
  {
    断言: 'round27_c_compare（#38 十一条四列表逐值复跑对账）', 类别: '甲｜只比两次跑批',
    在九步链: false, 有退出码: true,
    已知坏版本: '现造一份「被改动过」的症状量测 r24_article38_r29bogus_result.json（1 个叶子值）', 期望: 'FAIL',
    自检: async () => {
       if (!has('r24_article38_r27-after_result.json')) return 缺('r24_article38_r27-after_result.json')
       造坏症状()
       const out = runNode('tools/render-verify/round27_c_compare.mjs',
         ['target/probe/browser/r24_article38_r27-after_result.json', 'target/probe/browser/r24_article38_r29bogus_result.json'])
       const bad = (out.stdout || '').split('\n').filter((text) => /❌/.test(text)).length
       const st = runNode('tools/render-verify/round27_c_compare.mjs', ['--selftest'])
       const st线 = (st.stdout || '').split('\n').map((t) => t.trim()).find((t) => t.startsWith('→')) || ''
       return { 结果: out.status !== 0 ? 'FAIL' : 'PASS',
         证据: '改掉 1 个叶子值后逐值对账 → 退出码 **' + out.status + '** · 打印出的 ❌ 行 ' + bad + ' 条'
           + ' ‖ `--selftest` 退出码 ' + st.status + '（' + st线.replace('→ ', '') + '）' }
     },
    失效范围: '甲类：两次跑批出的数一致不代表数对。它上面那条真正的判据（第二十四轮四列表「与产物对照」）是**乙类**。'
      + '⚠️ 第二十九轮这一支**没有退出码**（判完差异也 exit 0，人工不看输出就等于没跑）；'
      + '**第三十轮 E2 已补**：有任一叶子值不同、或用例集合不同 → `exit 1`；用法错 `exit 2`。对账口径（不归一、不容差）一个字节没改。',
  },
  {
    断言: 'r27-entry-paths（打开 / 粘贴 / 手打三条入口的对照）', 类别: '乙｜对照产物',
    在九步链: false, 有退出码: true,
    已知坏版本: '`--selftest` 的 5 条（真实存档 + 捏成「DOM 里量到缩进、出口却没空白」+ 抹掉手打那条的出口空白 + 退化输入）；'
      + '另喂一份**真改坏的存档**：把 `r27_entry_paths_after.json` 手打那条 `saveExit` 里的段首空白整段删掉',
    期望: 'FAIL',
    自检: async () => {
      if (!has('r27_entry_paths_after.json')) return 缺('r27_entry_paths_after.json')
      const st = runNode('tools/render-verify/browser/r27-entry-paths.mjs', ['--label', 'after', '--selftest'])
      const st线 = (st.stdout || '').split('\n').map((t) => t.trim()).find((t) => t.startsWith('→')) || ''
      const 坏 = 喂坏输入({
        复制: ['browser/r27_entry_paths_after.json'],
        改坏: (目录) => {
          const 档 = 读JSON(目录, 'browser/r27_entry_paths_after.json')
          档.results.type.saveExit = String(档.results.type.saveExit).replace(/<p[^>]*>(?:&nbsp;|[ \t])+/g, '<p>')
          写JSON(目录, 'browser/r27_entry_paths_after.json', 档)
          return '把存档里「手打」那条的保存出口段首空白整段删掉'
        },
        脚本: 'tools/render-verify/browser/r27-entry-paths.mjs', 参数: ['--label', 'after', '--selftest'],
      })
      return { 结果: (st.status === 0 && 坏.结果 === 'FAIL') ? 'FAIL' : 'PASS',
        证据: '`--selftest` 退出码 ' + st.status + '（' + st线.replace('→ ', '') + '） ‖ 喂「把存档里「手打」那条的保存出口段首空白整段删掉」→ '
          + '退出码 **' + 坏.码 + '**（判红）。⚠️ 这一喂改的是**自检自己的输入**，所以同时会看到自检的第 1 条用例'
          + '（「当前存档」期望判绿）翻成 ❌——那正是「这份存档确实被读进去了」的证据，不是闸写松了。' }
    },
    失效范围: '判据② 只对「实时 DOM 里真的量到了缩进」的那几条路生效（粘贴 HTML 那条的缩进在上游就被折叠，'
      + '不在射程内，见 `known-issues-handoff.md` §3.27③ 第 9 条）。'
      + '⚠️ 第二十九轮这一支的 `inExit` 算出来后被 `void inExit` 丢掉、也没有退出码；**第三十轮 E3 已接回**：'
      + '判据②有失败项、或 `revision`/`updatedAt` 变了 → `exit 1`。',
  },
  {
    断言: 'round25_stock_scan（存量 44 篇的影响面）', 类别: '乙｜真实 DB', 在九步链: false, 有退出码: false,
    已知坏版本: '（判据是 CONTENT_HTML 的纯函数，可自己重跑）', 期望: '未做',
    自检: async () => 未做('读的是真实库里的 ARTICLE.CONTENT_HTML（外部真值），判据是纯函数、能只看一个字段复算；但没有退出码。'),
    失效范围: '判据不盲；失效的是绿灯含义（本支也不在九步链里）。',
  },
  {
    断言: 'round27_leading_ws_scan（产物 / 正文里的段首空白扫描）', 类别: '乙｜纯扫描',
    在九步链: false, 有退出码: false,
    已知坏版本: '（它只是尺子，不下判定）', 期望: '未做',
    自检: async () => 未做('它输出「哪里有段首空白」的清单，是后续几支共用的那把尺子（`leadingRuns()`），本身不下判定。'),
    失效范围: '不适用（尺子，不是门）。',
  },
  {
    断言: 'round29_clipboard_html_check（剪贴板 HTML 会不会丢段首空白）', 类别: '丙｜自声明判据',
    在九步链: false, 有退出码: true,
    已知坏版本: 'target/probe/r29/clip/ 下的 27 份载荷（含 8 个公开网页的真实剪贴板原文）', 期望: '未做',
    自检: async () => 未做('本支是第二十九轮新立的判据：对任意一份剪贴板 HTML 直接判定，'
      + '退出码 有触发 10 / 不触发 0。正例由 z-判据正例_*.html 钉住（真实载荷 + 加回被浏览器并掉的两个空格）；'
      + '`--selftest` 与第二十八轮存档的 7 份真载荷逐条对齐（7/7）。'),
    失效范围: '判的是**剪贴板 HTML 这个文本**，不判编辑器行为；且实测 19/19 真实来源都判「不触发」——'
      + '原因是 Blink 在写剪贴板之前就把 `white-space: normal` 内容的段首 `[ \\t]` 并掉了（见脚本头部）。',
  },
]

// ---------------------------------------------------------------------------
// 跑自检 → 出表
// ---------------------------------------------------------------------------
for (const check of CHECKS) {
  try {
    check.自检结果 = await check.自检()
  } catch (error) {
    check.自检结果 = { 结果: '未做', 证据: '自检抛异常：' + String(error && error.message ? error.message : error) }
  }
  check.相符 = check.自检结果.结果 === check.期望
}

const 抓住 = CHECKS.filter((c) => c.自检结果.结果 === 'FAIL').length
const 没抓住 = CHECKS.filter((c) => c.自检结果.结果 === 'PASS').length
const 未做数 = CHECKS.filter((c) => c.自检结果.结果 === '未做').length
const 能判红 = CHECKS.filter((c) => c.有退出码).length

const lines = []
lines.push('# 第二十九轮 A · 验收断言分类与反例自检')
lines.push('')
lines.push('| # | 断言 | 类别 | 在九步链里 | 能判红 | 已知坏版本 | 反例自检（已知坏版本上判了什么） | 是否抓住 | 若无效则失效范围 / 本轮动作 |')
lines.push('| -- | -- | -- | -- | -- | -- | -- | -- | -- |')
CHECKS.forEach((check, index) => {
  const r = check.自检结果.结果
  const 标记 = r === 'FAIL' ? '**抓住 ✅**' : r === 'PASS' ? '**没抓住 ❌**' : '未做'
  lines.push('| ' + (index + 1) + ' | ' + check.断言 + ' | ' + check.类别 + ' | ' + (check.在九步链 ? '是' : '否')
    + ' | ' + (check.有退出码 ? '是' : '**否**') + ' | ' + check.已知坏版本 + ' | ' + check.自检结果.证据
    + ' | ' + 标记 + ' | ' + check.失效范围 + (check.修紧 ? ' ' + check.修紧 : '') + ' |')
})
lines.push('')
lines.push('**汇总：' + CHECKS.length + ' 条断言里 —— 能判红的 ' + 能判红 + ' 条'
  + '（占 ' + Math.round(能判红 / CHECKS.length * 100) + '%）；'
  + '反例自检抓住坏版本 ' + 抓住 + ' 条 / 没抓住 ' + 没抓住 + ' 条 / 未做 ' + 未做数 + ' 条。**')
lines.push('')
const 修紧过 = CHECKS.filter((c) => c.修紧)
lines.push('**第二十九轮原本修紧 ' + 修紧过.length + ' 条**（改判据的只有第 10 行 `r26-leading-ws-effect` 判据②③；'
  + '其余是把盲区写进脚本头部 + 新立 `--selftest`，让「已知盲」与「尺子瞎了」能被分开）：'
  + 修紧过.map((c) => c.断言.split('（')[0].trim()).join('、') + '。')
lines.push('')
lines.push('**第三十轮（E1~E3）**：`能判红` 那 14 条由「否」变「是」，代价是**每跑一次本表要跑十几支脚本**'
  + '（浏览器套件只跑 `--selftest`，不开浏览器；汇总层用临时目录喂坏输入）。'
  + '仍未接退出码的是第 16 / 19 / 20 行那三支——它们按定位就是**实验 / 尺子**，不是门，原因写在各自的「失效范围」里。')
lines.push('')
lines.push('> **这张表回答「今天的绿灯有多少是真绿」**（**第三十轮已按 E1~E3 补完**）：'
  + '第二十九轮时九步链 15 个步骤里只有 **1/9 的构建**真正能判红，其余 14 步（2/9~9g）**全部没有退出码**，'
  + '「EXIT=0」= 脚本没抛异常——所以那一轮起本表把「14 步的绿灯不含判据」写成结论。'
  + '**第三十轮把这 14 步的退出码补齐**（判定口径一个字没改，只把脚本本来就在打印的 fail 数接到退出码上），'
  + '并给每一支都接上了**可复跑的坏输入自检**（见上表「反例自检」列，临时目录里改、真产物不动）；'
  + '**E2 `round27_c_compare` 与 E3 `r27-entry-paths` 也已接回退出码**。'
  + '从第三十轮起，九步链 15 个步骤全部能判红，「全绿」才可以按字面读。'
  + '⚠️ 当时那些数字本身仍然是真的（用例数、pass/na/fail 分布、截图、逐条差异表都在），'
  + '收窄的是「`EXIT=0` ⇒ 这一步通过」这个推论——**历史汇报不重写**，'
  + '`docs/render-acceptance-report.md` §五 第 12 条只加了一条注记说明当时那个数字的真实含义。')
lines.push('')
lines.push('> **第三十轮补完后的实测**：九步链跑完每一步的退出码见 `docs/dev/known-issues-handoff.md` §3.31②；'
  + '其中 **6/9 是真红**（`#16` / `#35` 的 13 个外链图 13/13 返回 404，外部图床失效），'
  + '其余 14 步 `EXIT=0`。**保留红灯，不豁免、不放宽判据。**')
lines.push('')

const text = lines.join('\n')
console.log(text)
if (process.argv.includes('--json')) {
  const out = resolve(BROWSER_OUT, 'r29_gate_audit.json')
  writeFileSync(out, JSON.stringify({
    checks: CHECKS.map((c) => ({
      断言: c.断言, 类别: c.类别, 在九步链: c.在九步链, 有退出码: c.有退出码,
      已知坏版本: c.已知坏版本, 期望: c.期望, 自检结果: c.自检结果, 相符: c.相符, 失效范围: c.失效范围,
    })),
    汇总: { 断言数: CHECKS.length, 能判红, 抓住, 没抓住, 未做: 未做数 },
  }, null, 1), 'utf8')
  console.log('产物:', out)
}
const 不符 = CHECKS.filter((c) => !c.相符)
if (不符.length) {
  console.error('\n❌ 有 ' + 不符.length + ' 条断言的实际表现与它自己声明的相差：')
  for (const check of 不符) console.error('   · ' + check.断言 + '：声明 ' + check.期望 + ' / 实测 ' + check.自检结果.结果)
}
process.exit(不符.length ? 1 : 0)
