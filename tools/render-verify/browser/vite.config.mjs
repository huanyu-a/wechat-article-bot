import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import { ROOT, PROBE_DIST } from '../paths.mjs'

const here = dirname(fileURLToPath(import.meta.url))
const webui = resolve(ROOT, 'webui')

/**
 * 探针页面自己的 Vite 配置。
 *
 * 为什么不直接用 webui 的配置：那会把探针文件塞进 `webui/`（用户要求工作树里
 * 只有 target/probe 之外没有意外文件）。这里 root 指向探针目录，
 * 靠一条 alias 让 `@tiptap/*` 从 `webui/node_modules` 解析——
 * 传递依赖（`@tiptap/pm` → `prosemirror-*`）会从 webui 那边的真实路径自然向上找到。
 */
export default {
  root: here,
  base: './',
  build: {
    outDir: PROBE_DIST,
    emptyOutDir: true,
    // 四个入口：probe.html = 14 组「改前 / 改后」对照；all.html = 全量 79 样例核查；
    // r16.html = 用户标注 11 条（带逐条计算样式探针）；r48-flex-span.html = D48 定点探针
    rollupOptions: {
      input: {
        probe: resolve(here, 'probe.html'),
        all: resolve(here, 'probe_all.html'),
        r16: resolve(here, 'probe_r16.html'),
        r48: resolve(here, 'r48-flex-span.html'),
      },
    },
  },
  resolve: {
    alias: [{ find: /^@tiptap\/(.+)$/, replacement: resolve(webui, 'node_modules/@tiptap') + '/$1' }],
  },
  server: { fs: { allow: [ROOT] } },
}
