"""`tools/render-verify/` 的路径约定（Python 侧）。

**为什么要有这个文件**：这套 harness 原先整份躺在 `target/probe/` 里，而 `target/` 被
`.gitignore` 命中，于是「换一台机器 / 换一个会话，证据和脚本一个都拿不到」。第十四轮把它们
搬进受版本控制的 `tools/render-verify/`，同时把**产物留在 gitignored 的 `target/probe/`**——
脚本是资产，产物是每次可重算的中间件。

三条路径，边界不许混：

| 常量 | 指向 | 在版本控制里？ | 放什么 |
| --- | --- | --- | --- |
| `RENDER_VERIFY` | `tools/render-verify/` | 是 | 脚本本体 |
| `SPEC` | `tools/render-verify/spec/` | 是 | **输入**：组件清单、匹配器、官方 guide、引擎包存档 |
| `OUT` | `target/probe/` | **否**（`.gitignore:2`） | **产物**：样例 HTML、截图、汇总 JSON/MD、日志 |

搬运时踩到的坑：原脚本大量用 `HERE = dirname(__file__)`，**输入和产物同在一个目录**。
搬完必须把两者拆开——凡是写的走 `OUT`，凡是读的、且属于「结论的定义基准」的走 `SPEC`。
脚本内一律不要出现裸的相对路径（如 `'target/probe/xxx'`），否则 cwd 一变就崩。
"""
import os

RENDER_VERIFY = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(RENDER_VERIFY))
SPEC = os.path.join(RENDER_VERIFY, 'spec')
OUT = os.path.join(ROOT, 'target', 'probe')

#: 渲染服务的调用令牌：**只从文件读，绝不允许进任何产物或提交**。
#: 路径由 `MARKFLOW_RENDER_TOKEN_FILE` 环境变量显式指定——不再回退到某个写死在仓库里的
#: 「约定路径」，那种做法本身就是泄露面。取不到就报错，让人显式提供。
TOKEN_FILE = os.environ.get('MARKFLOW_RENDER_TOKEN_FILE', '')

#: 渲染端点。默认是公网服务；设 `MARKFLOW_RENDER_URL` 环境变量可指向**自部署**实例
#: （2026-09-19 起：huanyu-a/MarkFlow 仓库 tools/render-server/render_server.mjs 可本地起，
#: 令牌就是部署者自己设的 `MARKFLOW_RENDER_TOKEN` 环境变量——公网令牌不可得时的正规替代）。
#: 不设环境变量时与历史行为逐字节一致。
RENDER_URL = os.environ.get('MARKFLOW_RENDER_URL') or 'https://www.bx9y.com.cn/__markflow_render'


def ensure_outdir(*parts):
    """在 `OUT` 下建（可能多层）目录并返回绝对路径——产物目录被 clean 掉是常态。"""
    path = os.path.join(OUT, *parts)
    os.makedirs(path, exist_ok=True)
    return path


def read_token():
    """读渲染令牌。路径必须由 `MARKFLOW_RENDER_TOKEN_FILE` 显式给出，取不到就大声报错。"""
    if not TOKEN_FILE:
        raise SystemExit(
            '未设置 MARKFLOW_RENDER_TOKEN_FILE：渲染令牌的位置不再写死在仓库里，'
            '请用环境变量显式指定令牌文件路径（值本身依旧不进任何产物）。'
        )
    with open(TOKEN_FILE, 'r', encoding='utf-8') as handle:
        return handle.read().strip()
