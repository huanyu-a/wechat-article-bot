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
import { spawn } from 'node:child_process'
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
  if (!version) { browser.kill(); throw new Error('浏览器 CDP 端口没起来') }

  const client = new CDP(version.webSocketDebuggerUrl)
  await client.open()
  return {
    browser, client, port, version,
    close() { try { client.close() } catch { /* 已经关了 */ } try { browser.kill() } catch { /* 已退出 */ } },
  }
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
