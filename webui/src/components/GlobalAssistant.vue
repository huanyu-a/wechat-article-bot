<script setup>
/* 全局助手：非公开、非编辑器页面的右下角入口 + 右侧滑出面板。
 *
 * 为什么不在 /articles/:id 挂载（真正的判断在 App.vue）：那篇文章的正文真值在编辑器的 Tiptap
 * 文档里、还有自动保存在跑；全局助手的写工具直接改库。两个写者同时改同一篇 = 丢更新，
 * 乐观锁只能让其中一方报 409，救不回被后写覆盖的内容。
 *
 * 视觉上刻意沿用编辑器面板的类名（.ai-panel / .chat-message / .chat-composer / .tool-card），
 * 让用户觉得这是同一个助手换了作用域，而不是第二个产品。
 *
 * 刻意**不** import '../article-agent.css'：那是编辑器路由的懒加载 CSS 块，从入口静态 import
 * 会把它（含 .wechat-progress-* / .asset-picker-* 等整页样式）压进每个页面的 index CSS。
 * 需要复用的三条（.tool-card / .message-markdown / .typing-caret）在 assistant.css 里按同一
 * 观感、收在 .global-assistant 作用域内补齐——只作用于本面板。
 *
 * 已知代价（2026-11-11 实测）：marked + dompurify 原本只随 ArticleEditorView 的懒加载块加载，
 * 现在入口也 import 它们，index-*.js 从 121.61 kB 涨到 199.98 kB（gzip 47.41 → 73.65），
 * 同时编辑器块从 545.79 kB 降到 477.03 kB（两份共用）。面板要在任意页面可用，就必须在入口
 * 拿到这两个库；再拆一层 defineAsyncComponent 只会在首屏后多一次请求，不值得。
 * 若将来首屏预算紧张，优化点是「首次打开面板时再 import 渲染库」，不是退回整块懒加载。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { api, stream } from '../api'
import '../assistant.css'
import { Bot, Check, CircleAlert, Cpu, Info, LoaderCircle, RotateCcw, Send, Sparkles, User, X } from 'lucide-vue-next'

const route = useRoute()
const open = ref(false), loaded = ref(false), loading = ref(false), busy = ref(false)
const instruction = ref(''), error = ref(''), stateText = ref('')
const messages = ref([]), scroller = ref(), inputRef = ref()

/* 模型选择：列表只在首次打开面板时拉一次（GET /api/assistant/models），之后复用这个 ref。
   selectedId 是**用户选中**的档案（下拉框绑定值、乐观更新的落点），它完全可能指向一条此刻
   不可用的档案——「先建档案、后补 Key」的中间态就是这样；confirmedId 是服务端最后确认的值，
   改选失败时回退到它。currentId / currentLabel 是**此刻真正会用到**的档案及其标签（null 即
   没有可用档案），由服务端算好，前端不自己推兜底链。 */
const models = ref([]), selectedId = ref(null), confirmedId = ref(null), currentId = ref(null), currentLabel = ref('')
/* runningModel 来自 state 事件的 model：兜底链可能连 currentId 都绕过去，只有它是这一轮
   真正在跑的模型。 */
const runningModel = ref('')
const modelsLoaded = ref(false), modelsLoading = ref(false), modelSaving = ref(false)

/* 服务端把历史落在 assistant_message 表里，面板重新挂载（离开页面再回来）时靠这份历史恢复
   对话——所以组件卸载不用往别处缓存消息。 */
const pageTitle = computed(() => route.meta?.title || document.title || '当前页面')

/* 工具名 → 中文标签。覆盖 ToolRegistry 里 ASSISTANT_READ / ARTICLE_WRITE / TASK / MODEL
   四组全部 25 个工具；服务端后来加工具而这里没跟上时回落到原始名字——
   宁可露出一行英文工具名，也不能让这条工具记录在对话里消失。 */
const TOOL_LABELS = {
  list_articles: '列出文章', read_article: '读取文章', read_article_blocks: '读取正文块',
  list_accounts: '列出公众号', list_skills: '列出技能', list_agents: '列出智能体',
  create_article: '新建文章', update_article_meta: '更新标题/摘要', insert_article_blocks: '插入内容块',
  replace_article_blocks: '改写内容块', delete_article_blocks: '删除内容块',
  list_schedule_tasks: '列定时任务', list_task_runs: '查看运行记录', create_schedule_task: '新建定时任务',
  update_schedule_task: '修改定时任务', set_schedule_task_enabled: '启停定时任务', run_schedule_task_now: '立即运行任务',
  list_llm_providers: '列模型供应商', list_llm_profiles: '列模型档案', discover_llm_models: '拉取可用模型',
  create_llm_provider: '新建供应商', update_llm_provider: '修改供应商', create_llm_profile: '新建模型档案',
  set_default_llm_profile: '设为默认档案', set_fallback_llm_profile: '设为兜底档案',
}
/* 会改动数据的工具（清单与方案 §4.3 的四组一致，止步于「草稿 / 配置」：没有发布、没有删除）。
   它们成功后要提示刷新，但**不自动刷新**：用户可能正在填一张表单，
   自动刷新会把未提交的输入冲掉，代价远大于多点一次刷新。 */
const WRITE_TOOLS = new Set([
  'create_article', 'update_article_meta', 'insert_article_blocks', 'replace_article_blocks', 'delete_article_blocks',
  'create_schedule_task', 'update_schedule_task', 'set_schedule_task_enabled', 'run_schedule_task_now',
  'create_llm_provider', 'update_llm_provider', 'create_llm_profile', 'set_default_llm_profile', 'set_fallback_llm_profile',
])
const WRITE_HINT = '改动已保存，页面数据可能需要刷新'

/* 与 ArticleEditorView 同一套渲染约定：gfm + breaks，再过 DOMPurify。
   marked 的选项是模块级全局状态，这里显式再设一次，免得将来编辑器改动顺带改变本面板。 */
marked.setOptions({ gfm: true, breaks: true })
function renderMarkdown(content) { return DOMPurify.sanitize(marked.parse(String(content || ''))) }
function toolLabel(name) { return TOOL_LABELS[name] || name || '工具调用' }

function scrollChat() { nextTick(() => { if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight }) }

/* 提示行紧贴在产生它的工具卡之后；连续多次写只留一条——同一句话连排三遍会被当成噪音，
   提示也就没人看了。分隔开的多张写卡各自带提示，因为它们之间夹着别的操作。 */
function pushWriteHint() {
  if (messages.value.at(-1)?.type === 'HINT') return
  messages.value.push({ type: 'HINT', id: `hint-${Date.now()}-${messages.value.length}` })
}

/* 历史按「新的在后」返回。整段对话的顺序是语义的一部分：服务端若哪天改成倒序，
   界面会把对话整段翻过来——只在首尾时间戳明确倒序时才纠正，同毫秒的多条保持服务端顺序。 */
function timeOf(row) { const value = Date.parse(row?.createdAt || ''); return Number.isFinite(value) ? value : 0 }
async function loadHistory() {
  if (loading.value) return
  loading.value = true; error.value = ''
  try {
    const rows = (await api('/api/assistant/messages?limit=50')) || []
    if (rows.length > 1 && timeOf(rows[0]) > timeOf(rows.at(-1))) rows.reverse()
    messages.value = rows.filter(row => row?.content).map(row => ({
      id: row.id, type: 'MESSAGE', role: String(row.role || 'ASSISTANT').toUpperCase(),
      content: row.content, status: row.status === 'FAILED' ? 'FAILED' : 'COMPLETED',
    }))
    loaded.value = true
  } catch (e) { error.value = e.message } finally { loading.value = false; scrollChat() }
}

/* ── 模型选择 ──────────────────────────────────────────────────────────────── */
/* 下拉框里**一直**列出全部档案（不可用的禁用并标「不可用」）：列表里凭空少一条，
   用户只会以为是界面漏了，而不是「档案在、只是现在用不了」。 */
function modelOptionLabel(item) {
  const base = `${item.name || '未命名档案'} · ${item.modelName || '未指定模型'}`
  return item.available === false ? `${base}（不可用）` : base
}

/* selectedId 与 currentId 是两件事：前者是用户的选择（可能指向不可用档案），后者是此刻真正
   生效的档案（null = 没有可用档案）。照抄服务端，不在这里做任何「兜底推理」。 */
function applyModelState(payload) {
  models.value = Array.isArray(payload?.items) ? payload.items : []
  selectedId.value = payload?.selectedId ?? null
  confirmedId.value = selectedId.value
  currentId.value = payload?.currentId ?? null
  currentLabel.value = payload?.currentLabel || ''
}

async function loadModels() {
  if (modelsLoaded.value || modelsLoading.value) return
  modelsLoading.value = true
  try {
    applyModelState(await api('/api/assistant/models'))
    modelsLoaded.value = true
  } catch (e) { error.value = e.message } finally { modelsLoading.value = false }
}

/* 选中即乐观更新（v-model 已经把 selectedId 改成新值），PUT 失败再回退到服务端确认过的值。
   profileId 传 null 是「清除选择、回到自动挑链」，所以这里绝不能把 null 当成「没选」跳过。 */
async function selectModel() {
  const nextId = selectedId.value
  const previousId = confirmedId.value
  if (nextId === previousId) return
  modelSaving.value = true
  try {
    applyModelState(await api('/api/assistant/model', { method: 'PUT', body: JSON.stringify({ profileId: nextId }) }))
    /* 换过模型后，上一轮那句「本次使用」说的是旧模型，留着会让下一轮看起来还在用它 */
    runningModel.value = ''
    error.value = ''
  } catch (e) {
    selectedId.value = previousId
    error.value = e.message
  } finally { modelSaving.value = false }
}

/* 有没有「现在能用」的档案：全部不可用时下拉框禁用，并给出可执行的下一步提示，
   而不是让用户去点一堆选不动的条目。 */
const hasUsableModel = computed(() => models.value.some(item => item.available !== false))
/* 服务端记住的 selectedId 不在列表里（档案刚被删 / 列表被裁剪）时，v-model 会落到
   selectedIndex -1、下拉框显示成空白。补一条只读选项，保证「选的是谁」始终有答案。 */
const selectedIdInList = computed(() => selectedId.value == null || models.value.some(item => item.id === selectedId.value))
/* 选中了却没被用上：档案此刻不可用（供应商停用 / 没配 key），后端临时走兜底链。这不是 bug，
   但用户会怀疑自己的选择没保存，所以界面必须主动说明，不能悄悄显示成「自动」。 */
const selectionIdle = computed(() => selectedId.value != null && selectedId.value !== currentId.value)
/* 界面上「真正在跑的是谁」：本轮有 state 报出的实际模型就用它，否则用生效档案的标签 */
const actualModel = computed(() => runningModel.value || currentLabel.value)
/* busy 期间禁选：一轮对话跑到一半换模型，前半段和后半段的产出会来自两个模型，
   而界面上只有一句「本次使用」，用户无从分辨哪段是谁写的。 */
const modelLocked = computed(() => busy.value || modelsLoading.value || modelSaving.value || !hasUsableModel.value)

async function openPanel() {
  open.value = true
  await nextTick()
  inputRef.value?.focus()
  /* 历史与模型列表互不依赖，并行拉取：首开面板少等一个 RTT，下拉框不必晚一步才出现。 */
  await Promise.all([loadModels(), loaded.value ? null : loadHistory()])
}
function close() { open.value = false }
function suggest(text) { instruction.value = text; inputRef.value?.focus() }

/* 每次请求带上用户所在页面，助手据此判断「这篇文章」指的是谁。articleId 恒为 null——
   面板不挂在编辑器页，但 JSON 里「缺键」与「null」是两种形状，后端不该为此多一条分支。 */
function requestContext() { return { path: route.fullPath, pageTitle: pageTitle.value, articleId: null } }

/* 工具卡在事件到达时才创建：服务端先发 tool.call、后发 tool.result，中间可能插着别的工具，
   所以按 callId 归一（不能假设「最后一张卡就是当前工具」）。 */
function ensureTool(data) {
  if (!data?.callId) return null
  let item = messages.value.find(entry => entry.type === 'TOOL' && entry.callId === data.callId)
  if (!item) {
    const name = data.name || data.tool || ''
    item = reactive({ type: 'TOOL', callId: data.callId, name, label: toolLabel(name), status: 'CALLING', detail: '正在执行…' })
    messages.value.push(item)
  }
  return item
}
function closeAssistant(turn) {
  if (!turn.assistant) return
  if (turn.assistant.status === 'RUNNING') turn.assistant.status = 'COMPLETED'
  turn.assistant = null
}
function appendAssistantDelta(turn, content) {
  if (!content) return
  if (!turn.assistant) { turn.assistant = reactive({ type: 'MESSAGE', role: 'ASSISTANT', content: '', status: 'RUNNING' }); messages.value.push(turn.assistant) }
  turn.assistant.content += content; turn.receivedDelta = true
}
function handleEvent(name, data, turn) {
  if (name === 'heartbeat') return
  if (name === 'state' && data.message) stateText.value = data.message
  /* state 里的 model 才是这一轮**实际**在跑的模型（兜底可能把它换掉），用它顶掉说明行里的
     currentLabel——否则那句「本次使用」只是在复述服务端算好的生效档案。 */
  if (name === 'state' && data.model) runningModel.value = data.model
  if (name === 'delta') appendAssistantDelta(turn, data.content || '')
  if (name === 'tool.call') {
    closeAssistant(turn)
    const item = ensureTool(data)
    if (item) { item.status = 'CALLING'; item.detail = '正在执行…' }
  }
  if (name === 'tool.result') {
    const item = ensureTool(data)
    if (item) {
      item.status = data.status === 'FAILED' ? 'FAILED' : 'COMPLETED'
      item.detail = data.message || (data.status === 'FAILED' ? '工具执行失败' : '工具执行完成')
      if (item.status === 'COMPLETED' && WRITE_TOOLS.has(item.name)) pushWriteHint()
    }
  }
  if (name === 'completed') {
    closeAssistant(turn)
    /* 模型只给了最终答复、没有流式 delta 时，completed 里的 message 就是唯一正文。 */
    if (!turn.receivedDelta && data.message) messages.value.push({ type: 'MESSAGE', role: 'ASSISTANT', content: data.message, status: 'COMPLETED' })
  }
  if (name === 'error') throw new Error(data.message || '助手处理失败')
  scrollChat()
}

async function send() {
  const text = instruction.value.trim()
  if (!text || busy.value) return
  error.value = ''; stateText.value = ''; runningModel.value = ''; busy.value = true
  messages.value.push({ type: 'MESSAGE', role: 'USER', content: text, status: 'COMPLETED' })
  instruction.value = ''; scrollChat()
  const turn = { assistant: null, receivedDelta: false }
  /* 带上用户选中的档案：服务端仍会按兜底链自己决定真正用谁，profileId 只是首选。
     没选（自动挑链）时不传这个键（缺键 ≠ null，后端不必多一条分支）。 */
  const body = { instruction: text, context: requestContext() }
  if (selectedId.value != null) body.profileId = selectedId.value
  try {
    await stream('/api/assistant/chat', body, (name, data) => handleEvent(name, data, turn))
    closeAssistant(turn)
  } catch (e) {
    /* 半截回答留在原气泡里并标红：用户能看到「说到哪儿断了」，比整段消失有用。
       一个字都没有的空气泡则丢掉，避免留下一条无内容的红框。 */
    const bubble = turn.assistant
    if (bubble && bubble.content) bubble.status = 'FAILED'
    closeAssistant(turn)
    if (bubble && !bubble.content) messages.value = messages.value.filter(item => item !== bubble)
    error.value = e.message
  } finally { busy.value = false; stateText.value = ''; scrollChat() }
}

async function reset() {
  if (busy.value) return
  if (messages.value.length && !confirm('开始新对话？智能体会忘掉当前上下文（已保存的历史消息不受影响）。')) return
  error.value = ''
  try {
    await api('/api/assistant/reset', { method: 'POST', body: JSON.stringify({}) })
    /* 清完只清本地列表、把 loaded 置回 true：reset 清的是会话上下文，不动历史消息表。
       若允许这里重新拉历史，刚点的「新对话」会被旧对话立刻填满，等于按钮没生效。
       模型选择不属于会话上下文，reset 不动它；只把上一轮那句「本次使用」一起收掉。 */
    messages.value = []; runningModel.value = ''; loaded.value = true
    scrollChat(); inputRef.value?.focus()
  } catch (e) { error.value = e.message }
}

/* Escape 收面板挂在 window 上而不是面板元素上：用户点过消息区、焦点跑出输入框后，
   元素级 keydown 收不到按键，面板就「关不掉」了。 */
function onKeydown(event) { if (event.key === 'Escape' && open.value) open.value = false }
onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <!-- 只有收起态才渲染入口按钮：打开时它正好被 380px 的面板压住，
       一个被盖住、却仍在 Tab 序列里的按钮，比没有按钮更糟。 -->
  <button v-if="!open" class="open-chat global-assistant-fab" aria-label="打开全局助手" title="项目级助手：处理文章、定时任务与模型档案" @click="openPanel">
    <Sparkles :size="17" /><span>项目助手</span>
  </button>
  <Transition name="global-assistant">
    <aside v-if="open" class="ai-panel global-assistant-panel global-assistant" role="dialog" aria-modal="false" aria-labelledby="global-assistant-title">
      <header>
        <div><span class="ai-avatar"><Sparkles :size="17" /></span><div><strong id="global-assistant-title">墨舟全局助手</strong><small><i></i> 项目级 · 服务端操作</small></div></div>
        <div class="global-assistant-actions">
          <button class="icon-button" aria-label="新对话" title="新对话（清空智能体上下文）" :disabled="busy" @click="reset"><RotateCcw :size="15" /></button>
          <button class="icon-button" aria-label="关闭助手" title="关闭助手（Esc）" @click="close"><X :size="16" /></button>
        </div>
      </header>
      <div class="ai-context"><Bot :size="15" /><span>当前页面：{{ pageTitle }}</span><Check :size="14" /></div>
      <!-- 模型选择行：紧贴上下文条。面板里最该「一眼可见」的两件事就是当前在哪一页、在用哪个
           模型，所以排在一起。下拉框绑的是「我选的」（selectedId），下面一行说的是「实际用的」
           （currentLabel / 本轮 state 报出的 model）——选中一条此刻不可用的档案时两者会不一致，
           那正是用户需要知道的，不能悄悄显示成「自动」。 -->
      <div class="global-assistant-model">
        <label class="global-assistant-model-label" for="global-assistant-model-select"><Cpu :size="13" /><span>模型</span></label>
        <select
          id="global-assistant-model-select"
          v-model="selectedId"
          class="global-assistant-model-select"
          aria-label="选择助手使用的模型"
          :title="currentLabel || undefined"
          :disabled="modelLocked"
          @change="selectModel"
        >
          <option :value="null">自动选择（推荐）</option>
          <option v-if="!selectedIdInList" :value="selectedId" disabled>已选档案 #{{ selectedId }}</option>
          <option v-for="item in models" :key="item.id" :value="item.id" :disabled="item.available === false">{{ modelOptionLabel(item) }}</option>
        </select>
        <span v-if="runningModel" class="global-assistant-model-note global-assistant-model-running">本次使用：{{ runningModel }}</span>
        <span v-else-if="currentLabel" class="global-assistant-model-note">当前使用：{{ currentLabel }}</span>
        <span v-else-if="modelsLoading" class="global-assistant-model-note global-assistant-model-empty">正在读取模型档案…</span>
        <span v-else-if="modelsLoaded" class="global-assistant-model-note global-assistant-model-empty">未配置可用模型，请到「模型档案」页添加</span>
        <span v-if="selectionIdle && actualModel" class="global-assistant-model-note global-assistant-model-stale">已选中的档案当前不可用，暂用 {{ actualModel }}</span>
      </div>
      <div ref="scroller" class="chat-messages global-assistant-messages" role="log" aria-live="polite" aria-label="助手对话">
        <div v-if="loading" class="global-assistant-loading"><LoaderCircle class="spin" :size="16" />正在加载历史对话…</div>
        <div v-else-if="!messages.length" class="ai-welcome">
          <span><Sparkles /></span><strong>想让我做什么？</strong>
          <p>我能读文章、改标题摘要与内容块，能建和改定时任务，也能看模型档案。写操作会落库，发布和删除得你自己来。</p>
          <button @click="suggest('现在有哪些文章？挑一篇值得优化的给我看看')">盘点现有文章</button>
          <button @click="suggest('列出所有定时任务，说明哪些没在正常运行')">检查定时任务</button>
          <button @click="suggest('现在的模型档案里，默认和兜底分别是哪个？')">查看模型档案</button>
        </div>
        <template v-for="(item, index) in messages" :key="item.callId || item.id || index">
          <div v-if="item.type === 'MESSAGE'" class="chat-message" :class="[String(item.role).toLowerCase(), item.status === 'FAILED' ? 'failed' : '']">
            <span class="message-avatar"><User v-if="item.role === 'USER'" :size="15" /><Sparkles v-else :size="15" /></span>
            <div><div class="message-markdown" v-html="renderMarkdown(item.content)"></div><span v-if="item.status === 'RUNNING'" class="typing-caret"></span></div>
          </div>
          <div v-else-if="item.type === 'TOOL'" class="tool-card" :class="item.status.toLowerCase()">
            <span class="tool-state"><Check v-if="item.status === 'COMPLETED'" :size="14" /><X v-else-if="item.status === 'FAILED'" :size="14" /><LoaderCircle v-else class="spin" :size="14" /></span>
            <div><strong>{{ item.label }}</strong><small>{{ item.detail }}</small></div>
          </div>
          <div v-else class="global-assistant-write-hint"><Info :size="13" /><span>{{ WRITE_HINT }}</span></div>
        </template>
      </div>
      <div v-if="error" class="global-assistant-error"><CircleAlert :size="15" /><span>{{ error }}</span><button aria-label="关闭错误提示" @click="error = ''"><X :size="13" /></button></div>
      <form class="chat-composer" @submit.prevent="send">
        <textarea ref="inputRef" v-model="instruction" rows="3" :disabled="busy" placeholder="让助手做事：改文章、建定时任务、查模型档案…" @keydown.enter.meta.prevent="send" @keydown.enter.ctrl.prevent="send"></textarea>
        <div>
          <span class="global-assistant-status"><LoaderCircle v-if="busy" class="spin" :size="12" /><em>{{ busy ? (stateText || '正在处理…') : '⌘ / Ctrl + ↵ 发送' }}</em></span>
          <button :disabled="busy || !instruction.trim()" :aria-label="busy ? '正在回复，请稍候' : '发送'" :title="busy ? '正在回复，请稍候' : '发送（⌘ / Ctrl + ↵）'"><LoaderCircle v-if="busy" class="spin" :size="17" /><Send v-else :size="17" /></button>
        </div>
      </form>
    </aside>
  </Transition>
</template>