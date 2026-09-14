/**
 * 极简 CDP（Chrome DevTools Protocol）客户端——用系统已装的 Chrome，零新增依赖。
 *
 * 为什么不用 playwright/puppeteer：仓库里（`webui/package.json` 与全局 npm）都没有这两个包，
 * 第五轮用户已经明确「不要为了这个任务引入新的重量级依赖」。而 Windows 上 Chrome 是现成的
 * （`C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe`），CDP 本身只是
 * 「HTTP 拿一个 WebSocket 地址 + 在上面收发 JSON」，Node 24 自带全局 `WebSocket` 与 `fetch`，够用。
 *
 * 只用到这里需要的几个域：Target / Page / Runtime / Emulation / DOM。
 */
import { spawn, spawnSync } from 'node:child_process'
import { mkdtempSync, existsSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const CHROME_CANDIDATES = [
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
]

export function findBrowser() {
  for (const candidate of CHROME_CANDIDATES) if (existsSync(candidate)) return candidate
  throw new Error('没找到 Chrome/Edge，本机浏览器路径需要补进 CHROME_CANDIDATES')
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

/** 起一个 headless 浏览器，返回 { browser, port, close() }。 */
export async function launchBrowser({ port = 9333, headless = true, extraArgs = [] } = {}) {
  await sweepLeftovers()
  const executable = findBrowser()
  const profile = mkdtempSync(join(tmpdir(), 'probe-chrome-'))
  const args = [
    `--remote-debugging-port=${port}`,
    `--user-data-dir=${profile}`,
    '--no-first-run', '--no-default-browser-check', '--disable-extensions',
    '--disable-background-networking', '--disable-sync', '--mute-audio',
    '--hide-scrollbars',
    ...(headless ? ['--headless=new', '--disable-gpu'] : []),
    ...extraArgs,
    'about:blank',
  ]
  const browser = spawn(executable, args, { stdio: 'ignore', windowsHide: true })

  // 等 CDP 端口起来；真浏览器冷启动大约 1~3 秒
  let version = null
  for (let attempt = 0; attempt < 60; attempt += 1) {
    try {
      const response = await fetch(`http://127.0.0.1:${port}/json/version`)
      if (response.ok) { version = await response.json(); break }
    } catch { /* 还没起来 */ }
    await sleep(250)
  }
  if (!version) { killTree(browser, profile); throw new Error('浏览器 CDP 端口没起来') }

  const client = new CDP(version.webSocketDebuggerUrl)
  await client.open()
  return {
    browser, client, port, version,
    close() { try { client.close() } catch { /* 已经关了 */ } killTree(browser, profile) },
  }
}

/** 本支专用 profile 的前缀。`mkdtempSync(join(tmpdir(), 'probe-chrome-'))` 造出来的目录全带它。 */
const PROFILE_PREFIX = 'probe-chrome-'

/**
 * 「是不是本支的探针浏览器」的 PowerShell 判定片段。
 *
 * ⚠️ 第二十九轮实测：**只用「命令行里带前缀」判定是错的**——发出这条查询/清理命令的
 * PowerShell 自己，命令行里也嵌着这个前缀（脚本字符串原样进命令行），于是**自己数自己**：
 * 数出来永远留 1 个（那 1 个就是正在执行查询的 powershell.exe 本身），清理脚本还会
 * `Stop-Process` 打到自己、半路把自己干掉，剩下的进程反而清不完。
 * 所以必须同时限定**可执行名**是浏览器（chrome.exe / msedge.exe / crashpad_handler.exe）。
 * 副作用是这条判定也不再依赖「前缀不能出现在别的进程命令行里」这个假设。
 */
function probeMatcher(varName) {
  return "$_.Name -in @('chrome.exe','msedge.exe','crashpad_handler.exe') "
    + '-and $_.CommandLine -and $_.CommandLine.Contains(' + varName + ')'
}

/**
 * 关掉本次启动的浏览器进程。
 *
 * ⚠️ 第二十八轮实测的教训：Windows 上 `browser.kill()`（只杀 spawn 返回的那一个 pid）**杀不干净**——
 * 一轮跑下来在机器上攒出 **1828 个 `chrome.exe`**（都带 `--user-data-dir=…\probe-chrome-*`，
 * 确认过没有一个是用户自己的浏览器），把机器压到 `Page.loadEventFired` 直接超时，
 * 后面几次量出来的都是环境噪声。
 *
 * 为什么不能按 pid 杀：Chrome 的启动器进程会**把浏览器进程另起一个再自己退出**——
 * 实测 spawn 拿到的 pid（88476）在 `close()` 那一刻就已经「没有这个进程」，
 * 所以 `taskkill /PID` 和 `browser.kill()` 都打空，真正的浏览器进程成了孤儿。
 *
 * 稳定可用的标识只有一个：本次启动专用的 `--user-data-dir`（`mkdtemp` 出来，全机唯一）。
 * 进程的完整命令行里带着它，按它匹配来杀，跑完进程数回到 0。非 Windows 上仍走 `kill()`。
 */
function killTree(browser, profile) {
  if (process.platform === 'win32' && profile) {
    // PowerShell 里单引号字符串不做转义，Windows 路径可以直接放；用 Contains 而不是 -like，
    // 免得路径里的字符被当成通配符。
    const script = "$p='" + profile + "'; "
      + 'Get-CimInstance Win32_Process | '
      + 'Where-Object { ' + probeMatcher('$p') + ' } | '
      + 'ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }'
    try {
      spawnSync('powershell', ['-NoProfile', '-NonInteractive', '-Command', script],
        { stdio: 'ignore', windowsHide: true })
      return
    } catch { /* 退回到 kill */ }
  }
  try { browser && browser.kill() } catch { /* 已退出 */ }
}

/**
 * 数一数机器上还有几个「本支的」浏览器进程（命令行里带 `probe-chrome-`，且可执行名是浏览器）。
 * 不返回命令行内容是为了不把一屏路径灌进日志。
 */
function probeChromeProcesses() {
  if (process.platform !== 'win32') return []
  // 单引号字符串在 PowerShell 里不做转义，这里没有用户输入，前缀是常量。
  const script = 'Get-CimInstance Win32_Process | '
    + 'Where-Object { ' + probeMatcher("'" + PROFILE_PREFIX + "'") + ' } | '
    + 'Select-Object -ExpandProperty ProcessId'
  try {
    const out = spawnSync('powershell', ['-NoProfile', '-NonInteractive', '-Command', script],
      { encoding: 'utf8', windowsHide: true, timeout: 60000 })
    return String(out.stdout || '').split(/\r?\n/).map((line) => line.trim()).filter(Boolean)
  } catch { return [] }
}

/** 只扫一次：`launchBrowser()` 可能在同一条链里被调好几次（每支脚本各自起一次）。 */
let swept = false

/**
 * **启动前自检**：开浏览器之前先看机器上有没有上一轮留下的探针浏览器，有就报告并清掉。
 *
 * 为什么要有这一步：第二十八轮实测过 **1828 个 `chrome.exe`** 堆积（全是本支的 profile），
 * 把机器压到 `Page.loadEventFired` 直接超时。当时是事后 `killTree` 修的；但「事后」管不了
 * **上一次异常退出留下的**（脚本被 Ctrl-C、进程被杀、Node 崩掉——`close()` 根本没跑到）。
 * 于是在这里补一道**事前**的：残留不清干净，后面的量都不可信，宁可在启动那一刻就喊出来。
 *
 * 只匹配本支专用的 `probe-chrome-` 前缀，**绝不碰用户自己的浏览器**。
 * 用 `RENDER_VERIFY_NO_SWEEP=1` 可以跳过（给「就是要看残留」的排查场景留口子）。
 * 非 Windows 上是空操作（`probeChromeProcesses()` 直接返回空表）。
 */
export async function sweepLeftovers() {
  if (swept) return null
  swept = true
  if (process.env.RENDER_VERIFY_NO_SWEEP === '1') return null
  const before = probeChromeProcesses()
  if (before.length === 0) return { 残留: 0, 已清理: 0 }
  console.log('⚠️ 启动前自检：机器上有 ' + before.length + ' 个上一轮留下的探针浏览器'
    + '（完整命令行里带 `' + PROFILE_PREFIX + '` 的 chrome/msedge，不是用户自己的浏览器）'
    + '——先把它们清掉再继续，否则量出来的是环境噪声。')
  const script = 'Get-CimInstance Win32_Process | '
    + 'Where-Object { ' + probeMatcher("'" + PROFILE_PREFIX + "'") + ' } | '
    + 'ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }'
  // 实测（第二十九轮自检）：Chrome 是「启动器 + 一串子进程」，一条 Stop-Process 管道打下去，
  // 先死的父进程可能还在被回收，所以**反复收**，最多 6 轮，到 0 或不再减少为止。
  let after = before
  let previous = before.length
  for (let round = 1; round <= 6; round += 1) {
    try {
      spawnSync('powershell', ['-NoProfile', '-NonInteractive', '-Command', script],
        { stdio: 'ignore', windowsHide: true, timeout: 120000 })
    } catch { /* 清不掉也把数报出来，不吞 */ }
    await sleep(700)
    after = probeChromeProcesses()
    if (after.length === 0) break
    if (after.length >= previous) break  // 一轮没动静就不再耗时间
    previous = after.length
  }
  console.log('   清理完成：' + before.length + ' → ' + after.length + ' 个'
    + (after.length ? '（还有剩，后面的量请自行判断可信度）' : ' ✅'))
  return { 残留: before.length, 已清理: before.length - after.length }
}

export class CDP {
  constructor(url) {
    this.url = url
    this.sequence = 0
    this.pending = new Map()
    this.listeners = new Map()
  }

  open() {
    return new Promise((resolve, reject) => {
      this.socket = new WebSocket(this.url)
      this.socket.addEventListener('open', () => resolve())
      this.socket.addEventListener('error', (event) => reject(new Error('CDP WebSocket 出错: ' + (event.message || 'unknown'))))
      this.socket.addEventListener('message', (event) => {
        let message
        try { message = JSON.parse(event.data) } catch { return }
        if (message.id !== undefined) {
          const entry = this.pending.get(message.id)
          if (!entry) return
          this.pending.delete(message.id)
          if (message.error) entry.reject(new Error(entry.method + ' -> ' + JSON.stringify(message.error)))
          else entry.resolve(message.result)
          return
        }
        const key = message.method
        for (const listener of this.listeners.get(key) || []) listener(message)
      })
    })
  }

  /** 发一条命令；`sessionId` 走扁平模式（flatten），不需要再嵌一层 Target.sendMessageToTarget。 */
  send(method, params = {}, sessionId) {
    const id = ++this.sequence
    const payload = { id, method, params }
    if (sessionId) payload.sessionId = sessionId
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject, method })
      this.socket.send(JSON.stringify(payload))
    })
  }

  /** 等一个事件（只等一次）。 */
  once(method, timeoutMs = 30000) {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('等 ' + method + ' 超时')), timeoutMs)
      const listener = (message) => {
        clearTimeout(timer)
        const list = this.listeners.get(method) || []
        this.listeners.set(method, list.filter((item) => item !== listener))
        resolve(message.params)
      }
      this.listeners.set(method, [...(this.listeners.get(method) || []), listener])
    })
  }

  close() { try { this.socket?.close() } catch { /* 已关 */ } }
}

/** 开一个页面并 attach，返回带 sessionId 的会话封装。 */
export async function openPage(client, url = 'about:blank') {
  const { targetId } = await client.send('Target.createTarget', { url })
  const { sessionId } = await client.send('Target.attachToTarget', { targetId, flatten: true })
  await client.send('Page.enable', {}, sessionId)
  await client.send('Runtime.enable', {}, sessionId)
  await client.send('DOM.enable', {}, sessionId)
  return {
    targetId,
    sessionId,
    send: (method, params) => client.send(method, params, sessionId),
    navigate: async (target) => {
      const loaded = client.once('Page.loadEventFired', 45000)
      await client.send('Page.navigate', { url: target }, sessionId)
      await loaded
    },
    /** 在页面里跑一段表达式并把结果以 JSON 形式取回（不是 console 字符串）。 */
    evaluate: async (expression, { awaitPromise = true } = {}) => {
      const result = await client.send('Runtime.evaluate', {
        expression, awaitPromise, returnByValue: true, userGesture: true,
      }, sessionId)
      if (result.exceptionDetails) {
        throw new Error('页面里报错: ' + (result.exceptionDetails.exception?.description
          || result.exceptionDetails.text))
      }
      return result.result.value
    },
    screenshot: async (filePath, { fullPage = true } = {}) => {
      const params = { format: 'png', captureBeyondViewport: fullPage }
      if (fullPage) {
        const metrics = await client.send('Page.getLayoutMetrics', {}, sessionId)
        const size = metrics.cssContentSize || metrics.contentSize
        params.clip = { x: 0, y: 0, width: Math.ceil(size.width), height: Math.ceil(size.height), scale: 1 }
      }
      const shot = await client.send('Page.captureScreenshot', params, sessionId)
      const { writeFileSync } = await import('node:fs')
      writeFileSync(filePath, Buffer.from(shot.data, 'base64'))
      return filePath
    },
    close: () => client.send('Target.closeTarget', { targetId }),
  }
}
