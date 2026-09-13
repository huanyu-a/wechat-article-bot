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
export const OUT = join(ROOT, 'target', 'probe')

/** 浏览器套件的产物目录：截图、`*_result.json`、`*_summary.{md,json}`、探针 dist 都在这。 */
export const BROWSER_OUT = join(OUT, 'browser')

/** 探针前端（`probe.html` / `probe_all.html`）单独用 vite 打的 dist，与 `webui/dist` 无关。 */
export const PROBE_DIST = join(BROWSER_OUT, 'dist')

/** 真实应用的编辑器产物。`run-article.mjs` / `verify-live-app.mjs` 用它起静态服务。 */
export const WEBUI_DIST = join(ROOT, 'webui', 'dist')

/** 渲染令牌文件：只读，不进产物。 */
export const TOKEN_FILE = join(process.env.USERPROFILE || process.env.HOME || '', '.zcode', 'secrets', 'markflow-render-token')

export const API = 'http://127.0.0.1:8081'
