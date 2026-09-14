/**
 * `tools/render-verify/` 的路径约定（Node 侧）。与 `paths.py` 一一对应，改一边必须改另一边。
 *
 * 边界：**脚本与输入进版本控制（`tools/render-verify/`），产物留在 gitignored 的
 * `target/probe/`**。浏览器套件原先用 `PROBE = resolve(HERE, '..')` 指回 `target/probe`，
 * 搬迁后 `HERE` 变成 `tools/render-verify/browser`，所以统一改成显式引用本文件。
 *
 * 搬迁的硬约束：**产物落点必须与搬之前逐字节相同**，否则历史各轮的截图路径、汇总数字、
 * 以及 `docs/dev/render-verification.md` 里的预期值全部要重写——这一层不值得为「好看」付代价。
 */
import { dirname, resolve, join } from 'node:path'
import { fileURLToPath } from 'node:url'

export const RENDER_VERIFY = dirname(fileURLToPath(import.meta.url))
export const ROOT = resolve(RENDER_VERIFY, '..', '..')
export const SPEC = join(RENDER_VERIFY, 'spec')

/**
 * 产物根目录（默认 `target/probe/`）。
 *
 * ⚠️ **第二十九轮 E1 给「闸的自检」开的口子**：设了环境变量 `RENDER_VERIFY_PROBE_DIR` 时，
 * **整套 Node 脚本的读写都改到那个目录**（`BROWSER_OUT`、`PROBE_DIST` 随之改变）。
 *
 * 用途只有一个：把产物**复制**一份到临时目录、在里面改坏一个叶子，
 * 再让某个汇总脚本去读它——这是「喂一份已知有问题的输入、确认闸真的判红」的唯一干净做法，
 * 不必去动真产物、也就没有「跑一半被中断、真产物留成半坏的」这种风险。
 *
 * ⚠️ **浏览器驱动器（`run-*` / `verify-live-app`）不要用它**：那几支要读 `PROBE_DIST` 里的探针
 * dist（还有真实应用的 `webui/dist`），指到别处只会得到一堆「找不到文件」。
 * 设了就会在启动时**大声喊一句**，免得有人误以为自己在读真产物。
 */
export const OUT = process.env.RENDER_VERIFY_PROBE_DIR
  ? resolve(process.env.RENDER_VERIFY_PROBE_DIR)
  : join(ROOT, 'target', 'probe')

if (process.env.RENDER_VERIFY_PROBE_DIR) {
  console.log('⚠️  RENDER_VERIFY_PROBE_DIR 生效：产物根目录改到 ' + OUT
    + '\n    （这是给「闸的自检」用的临时目录，不是真产物；要读真产物请 unset 这个变量）')
}

/** 浏览器套件的产物目录：截图、`*_result.json`、`*_summary.{md,json}`、探针 dist 都在这。 */
export const BROWSER_OUT = join(OUT, 'browser')

/** 探针前端（`probe.html` / `probe_all.html`）单独用 vite 打的 dist，与 `webui/dist` 无关。 */
export const PROBE_DIST = join(BROWSER_OUT, 'dist')

/** 真实应用的编辑器产物。`run-article.mjs` / `verify-live-app.mjs` 用它起静态服务。 */
export const WEBUI_DIST = join(ROOT, 'webui', 'dist')

/** 渲染令牌文件：只读，不进产物。 */
export const TOKEN_FILE = join(process.env.USERPROFILE || process.env.HOME || '', '.zcode', 'secrets', 'markflow-render-token')

export const API = 'http://127.0.0.1:8081'
