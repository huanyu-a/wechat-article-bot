<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'
import {
  BrainCircuit, ChevronDown, ChevronRight, ImageIcon, Info, Layers, ListChecks, LoaderCircle, Pencil, Plus,
  RefreshCw, Server, Shield, ShieldCheck, Sparkles, Star, Trash2, TriangleAlert, X,
} from 'lucide-vue-next'

const auth = useAuthStore()
// 角色未知时按「非管理员」处理：宁可让管理员的按钮晚一个 /me 往返出现，
// 也不要让非管理员看到按钮、点开后才收到 403。
const isAdmin = computed(() => auth.user?.role === 'ADMIN')

const providers = ref([]), profiles = ref([]), agents = ref([])
const loading = ref(true), providersError = ref(''), profilesError = ref('')
const message = ref(''), error = ref('')
// 绑定用量只是锦上添花；取不到时不能把「未知」说成「没有」，故单独记一个不可用标志
const agentsReady = ref(false)
const providerSaving = ref(false), providerFormError = ref('')
const providerModal = reactive({
  open: false, id: null, hasApiKey: false, apiKeyMasked: '未配置',
  // 模型发现（§2.2）：结果只存在弹窗 state 里，绝不落库、绝不在加载时自动探测
  discovering: false, discovered: null, discoverError: '',
})
const providerForm = reactive(blankProvider())
const profileSaving = ref(false), profileFormError = ref('')
const profileModal = reactive({ open: false, id: null, discovering: false, discovered: null, discoverError: '' })
// 自绘下拉（替换原生 datalist）：原生弹出层由浏览器绘制，样式完全不受控，与本页设计系统不匹配。
// 这里自己画列表，支持方向键/Enter/Esc/Tab 与鼠标，点外部关闭。
// typing：区分「用户正在这一格敲字」与「刚聚焦/程序回填」。只有前者才启用
// 「精确匹配唯一项 ⇒ 不再弹」的抑制，否则编辑已有档案时一聚焦就把候选吞掉（看不到可选模型）。
const modelSuggest = reactive({ open: false, active: -1, typing: false })
const modelSuggestRef = ref(null)
const profileForm = reactive(blankProfile())
// 展开是浏览态（看该供应商下挂了哪些模型），一次只展开一张；编辑仍然走 modal
const expandedKey = ref(null)

const PROVIDERS = [
  { value: 'OPENAI_COMPATIBLE', label: 'Chat Completions' },
  { value: 'OPENAI_RESPONSES', label: 'Responses / Codex' },
  { value: 'ANTHROPIC', label: 'Anthropic Messages' },
]
// 「用途」不是供应商的属性（用户 2026-11-10 否决）：一个网关通常同时供文本模型与图片模型，
// 所以图片通道由**模型卡片**上的「图片模型名称」驱动（契约 §4.3 两跳），供应商层不区分文本/图片。
const providerLabel = value => (PROVIDERS.find(p => p.value === value) || {}).label || value
function blankProvider(){return {name:'',provider:'OPENAI_COMPATIBLE',baseUrl:'https://api.openai.com',apiKey:'',clearApiKey:false,enabled:true}}
// 模型档案不再自带端点与密钥（已归供应商），因此这里只有 providerId 这一个归属字段。
// modelName 留空（F7）：预填 `gpt-4.1-mini` 会让 Anthropic 供应商下新建出 OpenAI 模型名，
// 由 placeholder 按协议提示 + required 拦住空提交。
// imageModelName 不再是表单字段（用户 2026-11-10 第二次修订：改成「这是图片模型」复选框）；
// 它仍随 payload 回传原值，只为兼容后端字段与 ①b 旧数据跳（见 profilePayload 注释）。
function blankProfile(){return {name:'',providerId:'',modelName:'',isImageModel:false,imageModelName:'',enabled:true,temperature:0.7,maxTokens:4096}}
/** 新建模型时「上游模型名称」的 placeholder 随供应商协议变化（F7）。 */
function modelNamePlaceholder(provider){
  const proto = provider?.provider
  if (proto === 'ANTHROPIC') return '例如：claude-sonnet-4-5'
  if (proto === 'OPENAI_RESPONSES') return '例如：gpt-5-codex'
  return '例如：gpt-4.1-mini'
}

const byIdAsc = (a, b) => Number(a.id) - Number(b.id)

/** 每个档案被多少个智能体指定为专属档案（用于回答「删掉它会影响谁」）。 */
const bindingCounts = computed(() => {
  const counts = new Map()
  for (const agent of agents.value) {
    const id = agent.llmProfileId
    if (id !== null && id !== undefined) counts.set(String(id), (counts.get(String(id)) || 0) + 1)
  }
  return counts
})
function bindingText(profile) {
  if (!agentsReady.value) return '智能体绑定情况暂不可用'
  const count = bindingCounts.value.get(String(profile.id)) || 0
  // 默认与兜底可以落在同一档案上（种子就是这样标记的），所以这里逐条拼接而不是二选一
  const parts = []
  if (count) parts.push(`${count} 个智能体已指定该档案`)
  if (profile.isDefault) parts.push(count ? '其余未绑定档案的智能体也用它' : '未被单独指定：所有未绑定档案的智能体都用它')
  if (profile.isFallback) parts.push('主用档案都不可用时由它接手')
  return parts.length ? parts.join('；') : '暂无智能体指定'
}

/**
 * 某个供应商下的模型档案（按 id 升序，与后端 failoverChain 的「其余已启用」跳同序）。
 * 注意档案的 `name` 是显示名、`modelName` 是上游模型名——两者是两件事，所以表单里分别叫
 * 「档案名称」与「上游模型名称」，卡片上「模型」那一行显示的是 `modelName`。
 */
const modelsOf = providerId => profiles.value
  .filter(profile => String(profile.providerId) === String(providerId))
  .sort(byIdAsc)

/**
 * 供应商下挂的模型数。**只统计 `provider_id` 等于它的档案**，不含旧版档案（`providerId` 为空、
 * 读自身旧列的那种）——所以它不能当「全部档案数」用。
 *
 * 显示口径与护栏口径必须分开（F4）：
 *   - 显示（行内「N 个模型」、展开区的卡片数）统一用**本地列表长度**，用户数得出来、点得到；
 *   - 删除护栏单独用 `Math.max(declared, local)`——后端也另有一道拒绝，护栏不能跟着放宽。
 * 两个数字不一致说明两次请求之间有并发改动，行内要提示刷新（见 `providerCountMismatch`）。
 */
function localModelCount(provider) {
  return modelsOf(provider?.id).length
}
function providerGuardCount(provider) {
  const local = localModelCount(provider)
  const declared = Number(provider?.modelCount)
  return Number.isFinite(declared) ? Math.max(declared, local) : local
}
/** 后端记录的条数与当前显示的条数不一致（并发改动），行内提示刷新。 */
function providerCountMismatch(provider) {
  const declared = Number(provider?.modelCount)
  return Number.isFinite(declared) && declared !== localModelCount(provider)
}

/**
 * 两级列表的分组结果。kind 三种：
 *   provider —— 正常供应商区块；
 *   dangling —— providerId 有值但供应商已不在列表里（被删）：运行时不可用，做成告警态而不是普通分组；
 *   legacy   —— providerId 为空（存量回退行）：运行时读档案自身的旧列，提示管理员归位。
 */
const groups = computed(() => {
  const list = []
  const rest = profiles.value
    .filter(profile => profile.providerId != null
      && !providers.value.some(provider => String(provider.id) === String(profile.providerId)))
    .sort(byIdAsc)
  const legacy = profiles.value.filter(profile => profile.providerId == null).sort(byIdAsc)

  // 数据完整性缺口放在最前面：它意味着这些档案在故障切换链上已经消失
  if (rest.length) {
    list.push({
      key: 'dangling', kind: 'dangling', name: '供应商已不存在的档案', models: rest,
      modelCount: rest.length, enabled: false, protocolLabel: '', baseUrl: '', apiKeyMasked: '',
    })
  }
  for (const provider of providers.value.slice().sort(byIdAsc)) {
    list.push({
      key: `provider-${provider.id}`, kind: 'provider', provider, name: provider.name,
      models: modelsOf(provider.id),
      // 显示口径用本地长度（F4）；护栏口径另算，见 providerGuardCount
      modelCount: localModelCount(provider),
      guardCount: providerGuardCount(provider),
      countMismatch: providerCountMismatch(provider),
      // ProviderView.enabled 是可空 Boolean：只有明确的 true 才算启用
      enabled: provider.enabled === true,
      protocolLabel: providerLabel(provider.provider),
      baseUrl: provider.baseUrl || '', apiKeyMasked: provider.apiKeyMasked || '未配置',
    })
  }
  if (legacy.length) {
    list.push({
      key: 'legacy', kind: 'legacy', name: '旧版档案（未指定供应商）', models: legacy,
      modelCount: legacy.length, enabled: true, protocolLabel: '', baseUrl: '', apiKeyMasked: '',
    })
  }
  return list
})

const isExpanded = group => group.kind === 'dangling' || expandedKey.value === group.key
function toggleExpand(group){
  if (group.kind === 'dangling') return
  expandedKey.value = expandedKey.value === group.key ? null : group.key
}
/** 展开态落在第一个供应商上：单供应商是绝大多数情况，默认就能看到模型列表。 */
function ensureExpanded(){
  const keys = groups.value.filter(group => group.kind !== 'dangling').map(group => group.key)
  if (expandedKey.value && keys.includes(expandedKey.value)) return
  expandedKey.value = keys.length ? keys[0] : null
}

async function load(){
  loading.value = true
  providersError.value = ''; profilesError.value = ''
  // 两个接口各自记错误：把「取不到」渲染成「没有」会让管理员以为供应商被删了
  try {
    const list = await api('/api/llm-providers')
    // F11：成功但非数组（data:null 之类）也算失败——否则所有档案都会被判成「供应商已不存在」
    if (Array.isArray(list)) providers.value = list
    else { providers.value = []; providersError.value = '供应商接口返回了非列表数据，请刷新页面后重试' }
  } catch (e) {
    providers.value = []
    providersError.value = e.message
  }
  try {
    const list = await api('/api/llm-profiles')
    if (Array.isArray(list)) profiles.value = list
    else { profiles.value = []; profilesError.value = '模型档案接口返回了非列表数据，请刷新页面后重试' }
  } catch (e) {
    profiles.value = []
    profilesError.value = e.message
  }
  loading.value = false
  ensureExpanded()
  // 用量信息只是锦上添花：取不到（非管理员 / 接口异常）不影响档案管理本身
  try {
    const list = await api('/api/agents')
    agents.value = Array.isArray(list) ? list : []
    agentsReady.value = true
  } catch { agents.value = []; agentsReady.value = false }
}

// ── 焦点管理（F5）────────────────────────────────────────────────────────────
// 弹窗是 `position:fixed` 的全屏遮罩，但 DOM 上排在列表之后——不接管焦点的话，Tab 会在
// 被遮住的背景控件里走，键盘用户既看不到焦点也走不出弹窗。
let lastFocused = null
/** 打开弹窗：记下触发元素 → 等 DOM 出来 → focus 首个输入框。 */
function captureFocus(){
  lastFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null
}
async function focusFirstField(refName){
  await nextTick()
  const modal = document.querySelector('.modal-backdrop .modal-card')
  if (!modal) return
  const first = modal.querySelector('input:not([type=hidden]):not([disabled]), select, textarea')
  if (first) first.focus()
}
/** 关闭弹窗：焦点还原到触发按钮（触发元素已不在 DOM 时退回 body，不留悬空焦点）。 */
async function restoreFocus(){
  await nextTick()
  if (lastFocused && document.contains(lastFocused)) lastFocused.focus()
  else if (document.activeElement instanceof HTMLElement) document.activeElement.blur()
  lastFocused = null
}

// ── 模型发现（§2.2）──────────────────────────────────────────────────────────
// 后端：GET /api/llm-providers/{id}/models → { data: string[] }（失败时中文消息，不含密钥）。
// 前端约束：只在用户显式点击时发请求；失败不锁输入框，只给一行 small 提示。
async function discoverModels(providerId, target){
  if (providerId === null || providerId === undefined || providerId === '') return
  target.discovering = true; target.discoverError = ''
  try {
    const list = await api(`/api/llm-providers/${providerId}/models`)
    const names = Array.isArray(list) ? list.map(String).filter(Boolean) : []
    if (!names.length) { target.discovered = null; target.discoverError = '该供应商未返回任何模型' }
    else target.discovered = [...new Set(names)]
  } catch (e) {
    target.discovered = null
    target.discoverError = e.message
  } finally { target.discovering = false }
}
/** 供应商弹窗：新建（尚无 id）时禁用并提示「保存供应商后可获取模型列表」。 */
function discoverFromProviderModal(){
  if (providerModal.id) discoverModels(providerModal.id, providerModal)
}
/**
 * 模型弹窗下拉的候选（§2.2）：**只列「还能加的」，已经建过档案的模型名不出现。**
 *
 * 数据源两处：
 * ① 本次探测结果（`profileModal.discovered`，点「获取可用模型」才有，且每次开弹窗会清空）；
 * ② 该供应商下已有档案的 `modelName` —— 它们本身**已加过**，所以只用来喂 `taken` 排除集，
 *    不再贡献候选。
 *
 * 为什么要排除：同一供应商下「一个模型一条档案」，已经建好的模型再出现在列表里，
 * 用户点一下只会撞上「该模型已存在」的报错，列表等于在推荐一个走不通的操作。
 *
 * 编辑态例外：正在编辑的那条档案自己的模型名必须留在列表里，否则用户打开编辑想看一眼
 * 「原本是哪个模型」都看不到（它已经在库里，会被当成 taken 排掉）。
 */
const profileModelOptions = computed(() => {
  const taken = new Set()
  for (const profile of modelsOf(profileForm.providerId)) {
    const name = (profile.modelName || '').trim()
    // 编辑中的那条不算占位：它的模型名要能被自己看见、也能原样保留
    if (name && String(profile.id) !== String(profileModal.id)) taken.add(name)
  }
  const candidates = new Set()
  for (const name of (profileModal.discovered || [])) {
    const trimmed = (name || '').trim()
    if (trimmed && !taken.has(trimmed)) candidates.add(trimmed)
  }
  // 编辑态：把当前档案自己的模型名补回候选，保证它至少自己可见
  const own = (profileForm.modelName || '').trim()
  if (profileModal.id && own) candidates.add(own)
  return [...candidates].sort((a, b) => a.localeCompare(b))
})
/**
 * 下拉候选：**聚焦时给全部候选，只有用户真正打字后才按关键词过滤。**
 *
 * 为什么以 typing 为界而不是直接按值过滤：编辑已有档案时输入框里已经有值（例如 model-a），
 * 若拿它当关键词过滤，其他候选一个都不含这串字符 ⇒ 列表只剩 model-a 自己，
 * 「换一个模型」这个下拉的主要用途就没了。用户点进来首先是想看「有哪些可选」。
 */
const filteredModelOptions = computed(() => {
  const all = profileModelOptions.value
  if (!modelSuggest.typing) return all
  const keyword = (profileForm.modelName || '').trim().toLowerCase()
  if (!keyword) return all
  const matched = all.filter(name => name.toLowerCase().includes(keyword))
  // 已经把某个候选完整敲进去了 ⇒ 不必再弹（否则敲完想 Tab 走人，列表还挡着）
  if (matched.length === 1 && matched[0].toLowerCase() === keyword) return []
  return matched
})

function openModelSuggest(){
  // 只在有候选时才弹：候选为空（该供应商的模型都加过了、或还没探测过）就不弹空壳列表。
  //
  // 注意：**不能**在这里读 `filteredModelOptions.value` 来判断「有没有候选」。
  // 它在 `typing` 刚被改动的同一个同步块里返回的还是上一次缓存的值——
  // `onModelFocus` 把 typing 置 false 后立刻调本函数，此时 computed 尚未重算，
  // 读到的仍是上一次（typing=true、关键词非空）的过滤结果，于是「聚焦看不到全部候选」。
  // 所以这里只看未过滤的 `profileModelOptions`，它不含关键词逻辑，不受 typing 影响。
  const available = profileModelOptions.value
  modelSuggest.open = available.length > 0
  if (!modelSuggest.open || modelSuggest.active >= available.length) modelSuggest.active = -1
}

/** 聚焦：值可能是程序回填的，先清掉 typing 再开下拉，保证能看到全部候选。 */
function onModelFocus(){
  modelSuggest.typing = false
  modelSuggest.active = -1
  openModelSuggest()
}

/** 用户真正在这格输入：带上 typing 标记、重置高亮再开下拉。 */
function onModelInput(){
  modelSuggest.typing = true
  // 候选集随关键词变化，旧的下标可能指向已消失的项，甚至越界。每次输入都清掉高亮，
  // 让用户自己用方向键重新定位（也让 Enter 在没主动选时不至于选中一个没看过的模型）。
  modelSuggest.active = -1
  openModelSuggest()
}

function closeModelSuggest(){
  modelSuggest.open = false
  modelSuggest.active = -1
}

/** 选中候选：写回输入框并收起。不做 trim 之外的加工，模型名原样保留。 */
function pickModelOption(name){
  profileForm.modelName = name
  closeModelSuggest()
}

/**
 * 输入框键盘导航。只在 open 时接管方向键/Enter/Esc，其余键保持原生输入行为。
 * Tab 不拦截：让用户照常跳到下一个字段（收起即可）。
 */
function onModelInputKeydown(event){
  if (event.key === 'Escape') {
    // 只在确实有下拉时拦截：否则 Esc 应继续冒泡去关弹窗（那是用户预期）。
    if (modelSuggest.open) { event.stopPropagation(); closeModelSuggest() }
    return
  }
  if (!modelSuggest.open) {
    if (event.key === 'ArrowDown' && filteredModelOptions.value.length) {
      event.preventDefault(); openModelSuggest(); modelSuggest.active = 0
    }
    return
  }
  const last = filteredModelOptions.value.length - 1
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    modelSuggest.active = modelSuggest.active >= last ? 0 : modelSuggest.active + 1
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    modelSuggest.active = modelSuggest.active <= 0 ? last : modelSuggest.active - 1
  } else if (event.key === 'Enter') {
    if (modelSuggest.active >= 0) { event.preventDefault(); pickModelOption(filteredModelOptions.value[modelSuggest.active]) }
    else closeModelSuggest()
  } else if (event.key === 'Tab') {
    closeModelSuggest()
  }
}

/** 点组件外关下拉。用 pointerdown 而非 click，避免与选项自身的 click 抢顺序。 */
function onDocumentPointerDown(event){
  if (!modelSuggest.open) return
  if (modelSuggestRef.value && !modelSuggestRef.value.contains(event.target)) closeModelSuggest()
}

onMounted(()=>{document.addEventListener('pointerdown', onDocumentPointerDown, true)})
onBeforeUnmount(()=>{document.removeEventListener('pointerdown', onDocumentPointerDown, true)})

function discoverFromProfileModal(){
  const providerId = profileForm.providerId
  if (providerId === '' || providerId === null || providerId === undefined) {
    profileModal.discoverError = '请先选择所属供应商'
    return
  }
  discoverModels(providerId, profileModal)
}

// ── 批量添加（多选）──────────────────────────────────────────────────────────
// 用户 2026-11-11：加 34 个模型就得重复 34 遍「新增档案 → 获取可用模型 → 选一个」，缺一个批量入口。
// 后端 POST /api/llm-profiles/batch 逐条建、逐条记账，所以这里的重点不是省请求，而是把两种
// 结果分开摊给用户：skipped 是幂等（已存在，不算出错），failed 必须逐条带名字与原话，
// 否则用户只看到「新增 0 个」，还得回去一条条点开核对是哪个模型、卡在哪。
const batchModal = reactive({
  open: false, providerId: '', discovering: false, discovered: null, discoverError: '',
  selected: [], submitting: false, formError: '',
})
const batchDialogRef = ref(null)

/**
 * 该供应商下已有档案的模型名。与单条弹窗的 `profileModelOptions` 用同一份 `modelsOf` 口径，
 * 不另算一套：这里没有「正在编辑的那条」要豁免，所以只做「取 modelName 去空白」。
 */
const batchTaken = computed(() => {
  const taken = new Set()
  for (const profile of modelsOf(batchModal.providerId)) {
    const name = (profile.modelName || '').trim()
    if (name) taken.add(name)
  }
  return taken
})
/** 探测结果 + 是否已添加：已添加的要标出来，不能让用户勾了才发现提交只得到 skipped。 */
const batchOptions = computed(() =>
  (batchModal.discovered || []).map(name => ({ name, taken: batchTaken.value.has(name) })))
/** 可勾选项：「全选」与提交都只认这一批，已添加的不再计入。 */
const batchSelectable = computed(() => batchOptions.value.filter(option => !option.taken))

/**
 * 打开批量添加。只有一个供应商时直接预选它（绝大多数部署就一个网关），有多个时留空——
 * 猜错供应商等于把模型建到另一套端点与密钥下，之后得逐条改回来。
 */
async function openBatch(){
  batchModal.providerId = providers.value.length === 1 ? providers.value[0].id : ''
  batchModal.discovering = false
  batchModal.discovered = null
  batchModal.discoverError = ''
  batchModal.selected = []
  batchModal.submitting = false
  batchModal.formError = ''
  batchModal.open = true
  captureFocus()
  await nextTick()
  // 焦点落在 dialog 自身（tabindex="-1"）：读屏会先播报对话框名称与用途，
  // 也不像聚焦 select 那样把供应商列表直接弹开。
  batchDialogRef.value?.focus()
}
async function closeBatch(){
  batchModal.open = false
  batchModal.formError = ''
  await restoreFocus()
}
/** 换供应商＝换一整套端点与密钥：旧的探测结果与勾选全部作废，免得把 A 家的模型名提交到 B 家。 */
function onBatchProviderChange(){
  batchModal.discovered = null
  batchModal.discoverError = ''
  batchModal.selected = []
  batchModal.formError = ''
}
async function discoverForBatch(){
  if (!batchModal.providerId) { batchModal.discoverError = '请先选择供应商'; return }
  await discoverModels(batchModal.providerId, batchModal)
  // 探测结果换了：把勾选收紧到「此刻仍可选」的范围——期间被别的管理员建过的模型不能继续挂着，
  // 否则提交上去只是凑一个 skipped。
  const selectable = new Set(batchSelectable.value.map(option => option.name))
  batchModal.selected = batchModal.selected.filter(name => selectable.has(name))
}
function toggleBatchModel(name){
  batchModal.selected = batchModal.selected.includes(name)
    ? batchModal.selected.filter(item => item !== name)
    : [...batchModal.selected, name]
}
/** 全选只勾「还能加的」：已添加的勾上再提交只会得到 skipped，是个假动作。 */
function selectAllBatch(){ batchModal.selected = batchSelectable.value.map(option => option.name) }
function clearBatchSelection(){ batchModal.selected = [] }

/** 提交按钮文案带选中数量：用户不用回头数勾了几个。 */
const batchSubmitLabel = computed(() => batchModal.submitting
  ? `提交中…（${batchModal.selected.length}）`
  : `添加选中（${batchModal.selected.length}）`)

/**
 * 提交批量添加。只要请求本身成功（哪怕有逐条失败）就关弹窗并刷新列表：后端的
 * created/skipped/failed 是一份完整账，播报到页面上比留在弹窗里更有用——用户接着就能在
 * 列表里看到新增的档案。失败项写进 `error`，与 summary 同时可见，不会被「新增 N 个」盖掉。
 */
async function submitBatch(){
  batchModal.formError = ''
  if (!batchModal.providerId) { batchModal.formError = '请先选择供应商'; return }
  const modelNames = [...batchModal.selected]
  if (!modelNames.length) { batchModal.formError = '请至少勾选一个模型'; return }
  // 后端单次上限 200（超出整批被拒）：提前拦住并说清办法，别让用户对着一条 400 猜。
  if (modelNames.length > 200) {
    batchModal.formError = `一次最多提交 200 个模型（当前已选 ${modelNames.length} 个），请分批添加`
    return
  }
  const providerId = batchModal.providerId
  batchModal.submitting = true
  error.value = ''; message.value = ''
  try {
    const data = await api('/api/llm-profiles/batch', {
      method: 'POST', body: JSON.stringify({ providerId: Number(providerId), modelNames }),
    })
    // createdCount 是后端口径；字段缺失时退回 created 长度，不把「拿到了名单却说 0 个」渲染给用户
    const created = Number(data?.createdCount ?? (Array.isArray(data?.created) ? data.created.length : 0))
    const skipped = Array.isArray(data?.skipped) ? data.skipped : []
    const failed = Array.isArray(data?.failed) ? data.failed : []
    await closeBatch()
    await load()
    // 刚加的模型挂在刚选的供应商下：顺手展开它，用户能立刻核对，不用自己再点一次折叠箭头。
    expandedKey.value = `provider-${providerId}`
    const summary = [`新增 ${created} 个`]
    if (skipped.length) summary.push(`已存在 ${skipped.length} 个`)
    message.value = `批量添加完成：${summary.join('，')}`
    // 失败要逐条给名字与原话：一句「部分失败」会把排查成本全推回给用户
    if (failed.length) {
      error.value = `${failed.length} 个模型未能创建：${failed.map(item => `${item.modelName}（${item.message}）`).join('；')}`
    }
  } catch (e) {
    batchModal.formError = e.message
  } finally {
    batchModal.submitting = false
  }
}

// ── 供应商 ───────────────────────────────────────────────────────────────────
function openProvider(provider){
  providerFormError.value = ''
  Object.assign(providerForm, blankProvider(), provider ? {...provider, apiKey: '', clearApiKey: false} : {})
  providerModal.open = true
  providerModal.id = provider ? provider.id : null
  providerModal.hasApiKey = Boolean(provider?.hasApiKey)
  providerModal.apiKeyMasked = provider?.apiKeyMasked || '未配置'
  providerModal.discovering = false
  providerModal.discovered = null
  providerModal.discoverError = ''
  captureFocus()
  focusFirstField('providerForm.name')
}
async function closeProvider(){ providerModal.open = false; providerFormError.value = ''; await restoreFocus() }
function providerPayload(){
  return {
    name: providerForm.name.trim(), provider: providerForm.provider, baseUrl: providerForm.baseUrl.trim(),
    apiKey: providerForm.apiKey, clearApiKey: Boolean(providerForm.clearApiKey), enabled: providerForm.enabled,
  }
}
async function saveProvider(){
  providerFormError.value = ''
  if (!providerForm.name.trim()) { providerFormError.value = '请填写供应商名称'; return }
  if (!providerForm.baseUrl.trim()) { providerFormError.value = '请填写 Base URL'; return }
  if (providerForm.enabled && !providerModal.hasApiKey && !providerForm.apiKey.trim()) { providerFormError.value = '启用供应商前必须填写 API Key'; return }
  if (providerForm.enabled && providerForm.clearApiKey && !providerForm.apiKey.trim()) { providerFormError.value = '删除已保存的 API Key 后供应商将无法启用，请先填写新 Key 或取消启用'; return }
  providerSaving.value = true
  try {
    await api(providerModal.id ? `/api/llm-providers/${providerModal.id}` : '/api/llm-providers',
      { method: providerModal.id ? 'PUT' : 'POST', body: JSON.stringify(providerPayload()) })
    closeProvider()
    message.value = `供应商「${providerForm.name.trim()}」已${providerModal.id ? '保存' : '创建'}`
    await load()
  } catch (e) { providerFormError.value = e.message } finally { providerSaving.value = false }
}
// 停用/启用走整条 PUT（后端把缺省字段当「清空」），因此必须原样带上名称、协议与 Base URL。
// F3：失败时显式回写 checkbox 的视觉状态——Vue 的 patchProps 只在值变化时写 DOM，
// 请求失败后 `provider.enabled` 没变，开关会停在用户点出来的位置，看起来已经切过去了。
async function toggleProvider(event, provider){
  error.value = ''; message.value = ''
  const input = event?.target
  try {
    await api(`/api/llm-providers/${provider.id}`, { method: 'PUT', body: JSON.stringify({
      name: provider.name, provider: provider.provider, baseUrl: provider.baseUrl,
      apiKey: '', clearApiKey: false, enabled: provider.enabled !== true,
    }) })
    message.value = `供应商「${provider.name}」已${provider.enabled === true ? '停用' : '启用'}`
    await load()
  } catch (e) {
    if (input) input.checked = Boolean(provider.enabled)
    error.value = e.message
  }
}
async function removeProvider(provider){
  const count = providerGuardCount(provider)
  // 护栏按钮用 aria-disabled 保持可聚焦（F8），因此点击要在这里拦住并播报原因，而不是沉默
  if (count > 0) {
    message.value = ''
    error.value = `供应商「${provider.name}」下仍有 ${count} 个模型，请先删除或把它们改挂到别的供应商`
    return
  }
  if (!confirm(`确定删除供应商「${provider.name}」吗？引用它的智能体将回落默认档案，该操作不可恢复。`)) return
  error.value = ''; message.value = ''
  try {
    await api(`/api/llm-providers/${provider.id}`, { method: 'DELETE' })
    message.value = `供应商「${provider.name}」已删除`
    await load()
  } catch (e) { error.value = e.message }
}

// ── 模型档案 ─────────────────────────────────────────────────────────────────
function openProfile(profile, presetProviderId){
  profileFormError.value = ''
  // null 归范（两处）：`isImageModel` 存量行是 NULL，`imageModelName` 也是 NULL。
  // 前者归成 false（复选框未勾选），后者归成 ''（它已不是表单字段，只作 payload 透传，
  // 见 profilePayload 的注释）。
  Object.assign(profileForm, blankProfile(),
    profile
      ? {...profile, isImageModel: profile.isImageModel === true, imageModelName: profile.imageModelName || ''}
      : { providerId: presetProviderId ?? '' })
  profileModal.open = true
  profileModal.id = profile ? profile.id : null
  profileModal.discovering = false
  profileModal.discovered = null
  profileModal.discoverError = ''
  captureFocus()
  focusFirstField('profileForm.name')
}
async function closeProfile(){ profileModal.open = false; profileFormError.value = ''; await restoreFocus() }
/** 模型表单顶部只读展示的所属供应商（编辑时随「所属供应商」下拉走）。 */
const currentProvider = computed(() =>
  providers.value.find(provider => String(provider.id) === String(profileForm.providerId)) || null)
function profilePayload(){
  const temperature = profileForm.temperature === '' || profileForm.temperature === null ? null : Number(profileForm.temperature)
  const maxTokens = profileForm.maxTokens === '' || profileForm.maxTokens === null ? null : Number(profileForm.maxTokens)
  // imageModelName 仍随 payload 回传，但**只透传原值、不由界面修改**（契约 §4.3 ①b：旧数据
  // 落点"只读不改"）。之所以不传空串：编辑一条已配好旧字段的档案（比如只改温度）会把
  // 它的图片模型抹掉，而存量库正是靠这个字段在 ①b 跳生效的——那是数据丢失，不是兼容。
  // null 归范：存量档案该列就是 NULL，直接 .trim() 会抛 TypeError（表现为"编辑任何存量档案都存不进去"）。
  return {
    name: profileForm.name.trim(),
    providerId: profileForm.providerId === '' || profileForm.providerId === null ? null : Number(profileForm.providerId),
    modelName: profileForm.modelName.trim(), imageModelName: (profileForm.imageModelName || '').trim(),
    // 契约 §4.3：这张档案本身是不是图片模型。缺省即 false（不用「字段缺失就按 TEXT」的静默兼容）。
    isImageModel: !!profileForm.isImageModel,
    enabled: profileForm.enabled, temperature, maxTokens,
  }
}
async function saveProfile(){
  profileFormError.value = ''
  if (!profileForm.name.trim()) { profileFormError.value = '请填写档案名称'; return }
  if (profileForm.providerId === '' || profileForm.providerId === null || profileForm.providerId === undefined) { profileFormError.value = '请选择所属供应商'; return }
  if (!profileForm.modelName.trim()) { profileFormError.value = '请填写模型名称'; return }
  const temperature = profileForm.temperature === '' || profileForm.temperature === null ? null : Number(profileForm.temperature)
  const maxTokens = profileForm.maxTokens === '' || profileForm.maxTokens === null ? null : Number(profileForm.maxTokens)
  if (temperature !== null && (Number.isNaN(temperature) || temperature < 0 || temperature > 2)) { profileFormError.value = 'temperature 需在 0 到 2 之间（可留空）'; return }
  if (maxTokens !== null && (Number.isNaN(maxTokens) || maxTokens < 256 || maxTokens > 32768)) { profileFormError.value = '最大输出 Token 需在 256 到 32768 之间（可留空）'; return }
  profileSaving.value = true
  try {
    await api(profileModal.id ? `/api/llm-profiles/${profileModal.id}` : '/api/llm-profiles',
      { method: profileModal.id ? 'PUT' : 'POST', body: JSON.stringify(profilePayload()) })
    closeProfile()
    message.value = `模型档案「${profileForm.name.trim()}」已${profileModal.id ? '保存' : '创建'}`
    await load()
  } catch (e) { profileFormError.value = e.message } finally { profileSaving.value = false }
}
async function setDefaultProfile(profile){
  // 按钮是 aria-disabled（保持可聚焦/可读 title），所以「已是默认」的判断要在这里做
  if (profile.isDefault) { message.value=''; error.value=`「${profile.name}」已经是默认档案`; return }
  error.value='';message.value=''
  try{await api(`/api/llm-profiles/${profile.id}/set-default`,{method:'POST'});message.value=`已将「${profile.name}」设为默认档案`;await load()}catch(e){error.value=e.message}
}
// 兜底档案：主用档案全部不可用时的最后安全网。与「默认」是两个独立标记，
// 后端 setFallback 会把其他档案的标记清掉（全局唯一）。
async function setFallbackProfile(profile){
  if (profile.isFallback) { message.value=''; error.value=`「${profile.name}」已经是兜底档案`; return }
  error.value='';message.value=''
  try{await api(`/api/llm-profiles/${profile.id}/set-fallback`,{method:'POST'});message.value=`已将「${profile.name}」设为兜底档案`;await load()}catch(e){error.value=e.message}
}
// 停用/启用走的是整条 PUT（后端 apply 会把缺省字段当「清空」），因此必须原样带上
// isImageModel（与 imageModelName），否则一次「点开关」就会把图片模型标志悄悄抹掉。
// F3：失败时显式回写 checkbox——Vue 的 patchProps 只在 vnode prop 变化时写 DOM，请求失败后
// `profile.enabled` 没变，开关会停在用户点出来的位置（看起来已切换，数据却没变）。
async function toggleProfile(event, profile){
  error.value='';message.value=''
  const input = event?.target
  try{
    await api(`/api/llm-profiles/${profile.id}`,{method:'PUT',body:JSON.stringify({name:profile.name,providerId:profile.providerId,modelName:profile.modelName,imageModelName:profile.imageModelName||'',isImageModel:profile.isImageModel===true,enabled:!profile.enabled,temperature:profile.temperature,maxTokens:profile.maxTokens})})
    message.value=`档案「${profile.name}」已${profile.enabled?'停用':'启用'}`;await load()
  }catch(e){
    if (input) input.checked = Boolean(profile.enabled)
    error.value=e.message
  }
}
async function removeProfile(profile){
  // F8：默认档案的删除按钮是 aria-disabled（保持可聚焦），点击要在这里拦住并说明原因
  if (profile.isDefault) {
    message.value=''
    error.value=`「${profile.name}」是默认档案，不可删除；请先把其他档案设为默认`
    return
  }
  const count = bindingCounts.value.get(String(profile.id)) || 0
  const suffix = !agentsReady.value
    ? '绑定情况暂不可用，引用它的智能体将回落默认档案。'
    : count ? `有 ${count} 个智能体指定了它，删除后将回落到默认档案。` : '引用它的智能体将回落默认档案。'
  if(!confirm(`确定删除模型档案「${profile.name}」吗？${suffix}`))return
  error.value='';message.value=''
  try{await api(`/api/llm-profiles/${profile.id}`,{method:'DELETE'});message.value=`模型档案「${profile.name}」已删除`;await load()}catch(e){error.value=e.message}
}

function onKeydown(event){
  if (event.key !== 'Escape') return
  // 下拉开着时，Esc 只收起下拉（用户本意是「关掉这个列表」），不该顺手把整个弹窗也关了——
  // 那样刚敲了一半的模型名会连弹窗一起丢。输入框自身的 keydown 已处理过并默认阻止冒泡之外的情况，
  // 这里再兜一层：只要下拉还开着就直接返回。
  if (modelSuggest.open) { closeModelSuggest(); return }
  if (profileModal.open) closeProfile()
  else if (providerModal.open) closeProvider()
  else if (batchModal.open) closeBatch()
}
/**
 * F5：弹窗打开时把背景设为 `inert`——遮罩是 `position:fixed` 的全屏层，但 DOM 上排在列表之后，
 * 不 inert 的话 Tab 会继续走进被遮住的控件（焦点不可见）。
 * 只 inert 弹窗之外的兄弟节点，弹窗自身留在可交互区；三个弹窗（供应商 / 模型档案 / 批量添加）不会同时打开。
 */
const anyModalOpen = computed(() => providerModal.open || profileModal.open || batchModal.open)
function setBackgroundInert(active){
  const shell = document.querySelector('.page-content')
  if (!shell) return
  for (const child of shell.children) {
    if (child.classList.contains('modal-backdrop')) continue
    if (active) child.setAttribute('inert', '')
    else child.removeAttribute('inert')
  }
}
watch(anyModalOpen, active => setBackgroundInert(active))
onMounted(()=>{document.addEventListener('keydown',onKeydown);load()})
onBeforeUnmount(()=>{document.removeEventListener('keydown',onKeydown);setBackgroundInert(false)})
</script>

<template>
  <section class="page-content">
    <div class="section-heading">
      <div>
        <span class="eyebrow">MODEL PROFILES</span>
        <h2>模型档案</h2>
        <p>端点与密钥按供应商填写，一个供应商下挂多个模型。切换顺序：智能体绑定档案 → 默认档案 → 兜底档案 → 其余已启用档案。</p>
      </div>
      <!-- 批量添加与「新增供应商」并列。.section-heading 是 space-between，直接塞两个按钮会被
           分到标题两侧，所以套一层现成的 flex 行 .llm-discover-row（gap 8）；本页没有 <style> 块，
           按约定不新增样式。用户 2026-11-11：一次只能加一个模型，34 个模型点了 34 遍。 -->
      <div v-if="isAdmin" class="llm-discover-row">
        <button class="secondary-button" @click="openBatch()"><ListChecks :size="16"/>批量添加</button>
        <button class="primary-button" @click="openProvider()"><Plus :size="16"/>新增供应商</button>
      </div>
    </div>

    <div v-if="error" class="alert error" role="alert">{{error}}</div>
    <div v-if="message" class="alert success-alert" role="status">{{message}}</div>

    <div class="profile-legend">
      <Info :size="15"/>
      <span><strong>供应商</strong>：Base URL 与 API Key 属于供应商，同供应商下的模型共用一份密钥；停用供应商会同时停用它下面的全部模型。</span>
      <span><strong>默认档案</strong>：未被单独指定档案的智能体都用它（全局唯一）。</span>
      <span><strong>兜底档案</strong>：绑定档案与默认档案都不可用时才启用，应选最稳、最便宜的通道（全局唯一）。</span>
      <span><strong>图片模型</strong>：勾选「这是一个图片模型」的档案本身就是图片模型——配图时用它所属供应商的端点与密钥，模型名就是它的「上游模型名称」；链上没有勾选的档案时，回落旧的全局图片配置。</span>
      <span><strong>同一供应商同时供文本与图片</strong>：在它下面建两条档案即可——一条写正文（如 <em>deepseek-chat</em>，不勾选），另一条勾选「这是一个图片模型」供配图（如模型名填 <em>gpt-image-1</em>）。两条档案共用供应商的那一份端点与密钥，不需要建两个供应商。</span>
    </div>
    <div class="skill-engine-alert">
      <ShieldCheck :size="15"/>模型档案与供应商仅管理员可管理；停用供应商、或停用/删除处于故障切换链上的档案，会让整轮创作退回到更慢的通道。
    </div>

    <div v-if="loading" class="panel empty-state"><LoaderCircle class="spin" :size="20"/>正在加载模型档案…</div>
    <div v-else-if="providersError" class="panel empty-state" role="alert">
      <Server :size="26"/><strong>无法读取供应商列表</strong>
      <p>{{ providersError }}（供应商与模型档案仅管理员可管理，非管理员可忽略此项）</p>
    </div>
    <div v-else-if="profilesError" class="panel empty-state" role="alert">
      <Layers :size="26"/><strong>无法读取模型档案</strong>
      <p>{{ profilesError }}（模型档案仅管理员可管理，非管理员可忽略此项）</p>
    </div>
    <div v-else-if="!groups.length" class="profile-grid">
      <button v-if="isAdmin" class="add-account-card profile-add-card" @click="openProvider()"><span><Plus/></span><strong>新增供应商</strong><p>先填 Base URL 与 API Key，再在它下面添加模型</p></button>
      <div v-if="!isAdmin" class="empty-state"><Server :size="26"/><strong>暂无供应商与模型档案</strong><p>供应商与模型档案仅管理员可管理。</p></div>
    </div>
    <div v-else class="provider-list">
      <!-- 供应商区块：行内是供应商自身的信息与操作，展开后是它下挂的模型档案。
           展开区有 id，供 caret 的 aria-controls 指向（F5）。 -->
      <article v-for="group in groups" :key="group.key" class="profile-item provider-item"
        :class="{ off: group.kind === 'provider' && !group.enabled, dangling: group.kind === 'dangling', legacy: group.kind === 'legacy' }">
        <header class="provider-head">
          <button v-if="group.kind !== 'dangling'" type="button" class="provider-caret"
            :aria-expanded="isExpanded(group)" :aria-controls="`provider-models-${group.key}`"
            :aria-label="`${isExpanded(group) ? '收起' : '展开'}供应商 ${group.name} 的模型列表`"
            @click="toggleExpand(group)">
            <ChevronDown v-if="isExpanded(group)" :size="16"/><ChevronRight v-else :size="16"/>
          </button>
          <span v-else class="provider-caret static" aria-hidden="true"><TriangleAlert :size="16"/></span>
          <div class="profile-titles">
            <strong>{{ group.name }}</strong>
            <span v-if="group.protocolLabel" class="provider-protocol">{{ group.protocolLabel }}</span>
            <span v-if="group.kind === 'provider' && !group.enabled" class="status-pill">已停用</span>
            <span v-if="group.kind === 'dangling'" class="status-pill failed">供应商已不存在</span>
            <span v-if="group.kind === 'legacy'" class="status-pill">未指定供应商</span>
          </div>
          <!-- 计数用本地列表长度（F4）：用户数得出来；与后端记录不一致时单独提示 -->
          <span class="agent-group-count">{{ group.modelCount }} 个模型</span>
          <label v-if="isAdmin && group.kind === 'provider'" class="skill-switch" :title="group.enabled ? '点击停用' : '点击启用'">
            <input type="checkbox" :checked="group.enabled" :aria-label="`${group.enabled ? '停用' : '启用'}供应商 ${group.name}`" @change="toggleProvider($event, group.provider)">
            <i></i>
          </label>
          <span v-if="isAdmin && group.kind === 'provider'" class="row-actions">
            <!-- 常驻「新增模型」入口（契约 §2.1）：折叠/空供应商时也可见，否则用户以为加不了模型 -->
            <button type="button" class="icon-button" :title="`为供应商 ${group.name} 新增模型`"
              :aria-label="`为供应商 ${group.name} 新增模型`" @click="openProfile(null, group.provider.id)"><Plus :size="16"/></button>
            <button type="button" class="icon-button" title="编辑供应商" aria-label="编辑供应商" @click="openProvider(group.provider)"><Pencil :size="16"/></button>
            <!-- 护栏用 aria-disabled 而非 disabled：按钮保持可聚焦（键盘可达），点击播报原因（F8） -->
            <button type="button" class="icon-button danger-ghost" :aria-disabled="group.guardCount > 0 ? 'true' : 'false'"
              :title="group.guardCount > 0 ? `该供应商下仍有 ${group.guardCount} 个模型，请先删除或改挂到别的供应商` : '删除供应商'"
              :aria-label="group.guardCount > 0 ? `该供应商下仍有 ${group.guardCount} 个模型，不可删除` : '删除供应商'"
              @click="removeProvider(group.provider)"><Trash2 :size="16"/></button>
          </span>
        </header>

        <dl v-if="group.kind === 'provider'" class="provider-meta">
          <div><dt>Base URL</dt><dd :title="group.baseUrl">{{ group.baseUrl }}</dd></div>
          <div><dt>API Key</dt><dd>{{ group.apiKeyMasked }}</dd></div>
        </dl>
        <p v-if="group.countMismatch" class="provider-hint alert-hint" role="status">
          <TriangleAlert :size="13"/>后端记录 {{ group.guardCount }} 条、当前显示 {{ group.modelCount }} 条，请刷新页面后重试。
        </p>
        <p v-if="group.kind === 'legacy'" class="provider-hint">
          <Info :size="13"/>这些档案还是「一条档案一份端点与密钥」的老形态，运行时读档案自身的旧列。建议编辑它们指定供应商，端点和密钥就能由供应商统一维护。
        </p>
        <p v-else-if="group.kind === 'dangling'" class="provider-hint alert-hint" role="alert">
          <TriangleAlert :size="13"/>这些档案的供应商已不存在，运行时不可用（不会进入故障切换链）。请编辑它们指定新的供应商，或直接删除。
        </p>

        <div v-if="isExpanded(group)" :id="`provider-models-${group.key}`" class="provider-models">
          <div v-if="group.models.length" class="provider-model-grid">
            <article v-for="profile in group.models" :key="profile.id" class="profile-item profile-model-card" :class="{ off: !profile.enabled }">
              <header>
                <div class="profile-titles">
                  <strong>{{ profile.name }}</strong>
                  <span v-if="profile.isDefault" class="profile-default-badge"><Star :size="11"/>默认</span>
                  <span v-if="profile.isFallback" class="profile-fallback-badge"><Shield :size="11"/>兜底</span>
                  <span v-if="!profile.enabled" class="status-pill">已停用</span>
                  <!-- 悬空档案的 providerName 为空：措辞必须与外层分组标题（供应商已不存在）一致，
                       不能和「供应商被停用」混成一句话，否则同一张卡上两处说法打架 -->
                  <span v-else-if="profile.providerEnabled === false && !profile.providerName" class="status-pill failed">供应商已不存在</span>
                  <span v-else-if="profile.providerEnabled === false" class="status-pill">供应商已停用</span>
                  <!-- F6：旧版档案（未指定供应商）自身没 key 时，不能说「供应商未配置密钥」 -->
                  <span v-else-if="!profile.hasApiKey && group.kind === 'legacy'" class="status-pill">该档案未配置密钥</span>
                  <span v-else-if="!profile.hasApiKey" class="status-pill">供应商未配置密钥</span>
                  <!-- 图片模型徽标（契约 §4.3）：只在勾选「这是图片模型」时出现，卡片保持干净。
                       文案只写「图片」，title 补全语义让鼠标/读屏都能问出「这张档案本身是图片模型」。 -->
                  <span v-if="profile.isImageModel === true" class="status-pill image-model-pill"
                    title="这是一张图片模型档案：配图时用它所属供应商的端点与密钥，模型名即上面的「模型」">图片</span>
                </div>
                <!-- F3：悬空/旧版档案的启停必然 400，不渲染开关，改成一行引导 -->
                <label v-if="isAdmin && group.kind === 'provider'" class="skill-switch" :title="profile.enabled?'点击停用':'点击启用'">
                  <input type="checkbox" :checked="profile.enabled" :aria-label="`${profile.enabled?'停用':'启用'}模型档案 ${profile.name}`" @change="toggleProfile($event, profile)">
                  <i></i>
                </label>
                <span v-else-if="isAdmin" class="profile-hint-inline">先归位供应商后才能启停</span>
              </header>
              <dl>
                <div><dt>模型</dt><dd>{{ profile.modelName }}</dd></div>
                <div v-if="group.kind === 'legacy'"><dt>API Key</dt><dd>{{ profile.apiKeyMasked }}</dd></div>
                <div><dt>温度 / 上限</dt><dd>{{ profile.temperature ?? '—' }} · {{ profile.maxTokens ?? '—' }}</dd></div>
              </dl>
              <p class="profile-usage"><BrainCircuit :size="12"/>{{ bindingText(profile) }}</p>
              <footer v-if="isAdmin">
                <!-- §1：窄卡片下降级为图标按钮（CSS container query 隐藏 .btn-label），
                     title/aria-label 必须保留原语义。
                     已处于该状态的按钮用 aria-disabled 而非 disabled：图标形态下文字标签被隐藏，
                     若再 disabled 就既不可聚焦、也不弹 title（Chrome 的 disabled 元素不派发鼠标事件），
                     用户只能看到「一个点不动的星星」，不知道为什么。 -->
                <button type="button" class="secondary-button" :aria-disabled="profile.isDefault ? 'true' : 'false'"
                  :title="profile.isDefault?'已是默认档案':'设为默认档案'" :aria-label="profile.isDefault?'已是默认档案':'设为默认档案'"
                  @click="setDefaultProfile(profile)"><Star :size="14"/><span class="btn-label">{{ profile.isDefault?'默认档案':'设为默认' }}</span></button>
                <button type="button" class="secondary-button" :aria-disabled="profile.isFallback ? 'true' : 'false'"
                  :title="profile.isFallback?'已是兜底档案':'主用档案全部不可用时自动启用它（设为兜底档案）'" :aria-label="profile.isFallback?'已是兜底档案':'设为兜底档案'"
                  @click="setFallbackProfile(profile)"><Shield :size="14"/><span class="btn-label">{{ profile.isFallback?'兜底档案':'设为兜底' }}</span></button>
                <button type="button" class="icon-button" title="编辑模型档案" aria-label="编辑模型档案" @click="openProfile(profile)"><Pencil :size="16"/></button>
                <!-- 同上：默认档案不可删除用 aria-disabled，保留可聚焦与原因播报 -->
                <button type="button" class="icon-button danger-ghost" :aria-disabled="profile.isDefault ? 'true' : 'false'"
                  :title="profile.isDefault?'默认档案不可删除':'删除档案'" :aria-label="profile.isDefault?'默认档案不可删除':'删除档案'"
                  @click="removeProfile(profile)"><Trash2 :size="16"/></button>
              </footer>
            </article>
          </div>
          <p v-else class="provider-hint">
            <Info :size="13"/>{{ group.kind === 'dangling' ? '没有可显示的模型档案。' : '该供应商下还没有模型，点下面的「新增模型」为它添加一个。' }}
          </p>
          <button v-if="isAdmin && group.kind === 'provider'" type="button" class="secondary-button provider-add-model" @click="openProfile(null, group.provider.id)"><Plus :size="14"/>新增模型</button>
        </div>
      </article>
    </div>

    <div v-if="providerModal.open" class="modal-backdrop" @click.self="closeProvider">
      <form class="modal-card profile-form-modal" role="dialog" aria-modal="true" aria-label="供应商配置" @submit.prevent="saveProvider">
        <header>
          <div><span class="eyebrow">{{ providerModal.id ? 'EDIT PROVIDER' : 'NEW PROVIDER' }}</span><h3>{{ providerModal.id ? '编辑供应商' : '新增供应商' }}</h3></div>
          <button type="button" class="icon-button" title="关闭" aria-label="关闭供应商表单" @click="closeProvider"><X :size="19"/></button>
        </header>
        <div v-if="providerFormError" class="alert error" role="alert">{{ providerFormError }}</div>
        <div class="form-grid">
          <label>供应商名称<input v-model="providerForm.name" required maxlength="100" placeholder="例如：nexus 网关"></label>
          <label>API 协议<select v-model="providerForm.provider"><option v-for="provider in PROVIDERS" :key="provider.value" :value="provider.value">{{ provider.label }}</option></select><small>Codex 模型选择 Responses；Claude 原生接口选择 Anthropic</small></label>
          <label class="full">Base URL<input v-model="providerForm.baseUrl" required placeholder="https://api.openai.com"><small>只填写服务根地址，不要包含 /v1、/responses、/chat/completions 或 /messages</small></label>
          <label class="full">API Key<input v-model="providerForm.apiKey" type="password" autocomplete="new-password" :placeholder="providerModal.hasApiKey?'已保存，留空表示不修改':'请输入 API Key'"><small v-if="providerModal.hasApiKey">当前状态：{{ providerModal.apiKeyMasked }}；留空表示不修改。</small><small v-else>启用供应商前必须配置 API Key。同一供应商下的全部模型共用这一份密钥，使用系统密钥 AES-GCM 加密保存。</small></label>
          <!-- 模型发现（契约 §2.2）：新建未保存时禁用并说明原因；失败只提示，不回滚已填内容 -->
          <div class="full llm-discover-row">
            <button type="button" class="secondary-button" :disabled="!providerModal.id || providerModal.discovering"
              :title="providerModal.id ? '用已保存的端点与密钥拉取模型列表' : '保存供应商后可获取模型列表'"
              @click="discoverFromProviderModal">
              <LoaderCircle v-if="providerModal.discovering" class="spin" :size="15"/><RefreshCw v-else :size="15"/>获取可用模型
            </button>
            <span v-if="!providerModal.id" class="llm-discover-status">保存供应商后可获取模型列表</span>
            <span v-else-if="providerModal.discoverError" class="llm-discover-status is-error">{{ providerModal.discoverError }}</span>
            <span v-else-if="providerModal.discovered" class="llm-discover-status is-ok">已获取 {{ providerModal.discovered.length }} 个模型</span>
          </div>
          <label v-if="providerModal.hasApiKey" class="checkbox full danger"><input v-model="providerForm.clearApiKey" type="checkbox">删除已保存的 API Key</label>
          <label class="checkbox full"><input v-model="providerForm.enabled" type="checkbox">启用该供应商</label>
        </div>
        <div class="form-actions">
          <button type="button" class="secondary-button" @click="closeProvider">取消</button>
          <button class="primary-button" :disabled="providerSaving"><LoaderCircle v-if="providerSaving" class="spin" :size="15"/>{{ providerSaving?'保存中…':'保存供应商' }}</button>
        </div>
      </form>
    </div>

    <div v-if="profileModal.open" class="modal-backdrop" @click.self="closeProfile">
      <form class="modal-card profile-form-modal" role="dialog" aria-modal="true" aria-label="模型档案配置" @submit.prevent="saveProfile">
        <header>
          <div><span class="eyebrow">{{ profileModal.id ? 'EDIT PROFILE' : 'NEW PROFILE' }}</span><h3>{{ profileModal.id ? '编辑模型档案' : '新增模型档案' }}</h3></div>
          <button type="button" class="icon-button" title="关闭" aria-label="关闭模型档案表单" @click="closeProfile"><X :size="19"/></button>
        </header>
        <div v-if="profileFormError" class="alert error" role="alert">{{ profileFormError }}</div>
        <div class="agent-section supplier-readonly">
          <span class="agent-section-title"><Server :size="13"/>所属供应商</span>
          <p class="agent-section-hint"><strong>{{ currentProvider ? currentProvider.name : '未选择供应商' }}</strong><template v-if="currentProvider"> · {{ currentProvider.baseUrl }}</template></p>
          <p class="agent-section-hint">Base URL 与 API Key 由供应商统一承载并加密保存，模型档案只声明用哪个模型。</p>
          <p v-if="currentProvider && currentProvider.enabled !== true" class="agent-section-hint warn">该供应商当前已停用，它下面的全部模型都不会进入故障切换链。</p>
          <p v-else-if="currentProvider && !currentProvider.hasApiKey" class="agent-section-hint warn">该供应商尚未配置 API Key，它下面的模型即使启用也不会进入故障切换链。</p>
        </div>
        <div class="form-grid">
          <label>档案名称<input v-model="profileForm.name" required maxlength="100" placeholder="例如：廉价快速模型"></label>
          <label v-if="profileModal.id">所属供应商
            <select v-model="profileForm.providerId" required>
              <option v-for="provider in providers" :key="provider.id" :value="provider.id">{{ provider.name }}{{ provider.enabled === true ? '' : '（已停用）' }}</option>
            </select>
            <small>改选后该模型的端点与密钥随新供应商生效。</small>
          </label>
          <!-- §2.2：自绘 combobox。可自由输入，也可从「本次探测结果 / 该供应商下已有档案」里选。
               数据源优先级：本次探测结果优先，其次该供应商下已有档案的 modelName 去重集。
               绝不在页面加载时自动探测（只在用户点「获取可用模型」时发请求）。 -->
          <label class="full">上游模型名称
            <!-- 自绘下拉，不用原生 datalist：后者的弹出层由浏览器绘制，样式不受页面 CSS 控制，
                 会以系统默认样式浮在设计系统之上（用户 2026-11-10 反馈「这个下拉跟当前 ui 不匹配」）。 -->
            <div class="model-combobox" ref="modelSuggestRef">
              <input v-model="profileForm.modelName" required autocomplete="off"
                role="combobox" aria-autocomplete="list"
                :aria-expanded="modelSuggest.open ? 'true' : 'false'"
                :aria-controls="'profile-model-options'"
                :placeholder="modelNamePlaceholder(currentProvider)"
                @focus="onModelFocus" @input="onModelInput" @keydown="onModelInputKeydown">
              <button v-if="profileForm.modelName" type="button" class="model-combobox-clear"
                aria-label="清空上游模型名称" title="清空" @click="profileForm.modelName=''; closeModelSuggest()">
                <X :size="14"/>
              </button>
              <ul v-if="modelSuggest.open" id="profile-model-options" class="model-combobox-list" role="listbox">
                <li v-for="(name, index) in filteredModelOptions" :key="name" role="option"
                  :aria-selected="modelSuggest.active === index ? 'true' : 'false'"
                  :class="{ active: modelSuggest.active === index }"
                  @mouseenter="modelSuggest.active = index"
                  @mousedown.prevent="pickModelOption(name)">
                  {{ name }}
                </li>
              </ul>
            </div>
            <small>供应商实际接受的那个模型名，例如 deepseek-flash。点「获取可用模型」后可从下拉里选
              （列表只显示还没添加过的），也可以直接输入。</small>
          </label>
          <!-- 探测按钮不能嵌在 <label> 里：label 内嵌按钮是无效嵌套，点按钮会被 label 转成「聚焦输入框」，
               键盘用户按 Enter 也可能触发两次。这里单独成行。 -->
          <div class="full llm-discover-row">
            <button type="button" class="secondary-button" :disabled="profileModal.discovering || !profileForm.providerId"
              :title="profileForm.providerId ? '用该供应商的端点与密钥拉取模型列表' : '请先选择所属供应商'"
              @click="discoverFromProfileModal">
              <LoaderCircle v-if="profileModal.discovering" class="spin" :size="15"/><RefreshCw v-else :size="15"/>获取可用模型
            </button>
            <span v-if="profileModal.discoverError" class="llm-discover-status is-error">{{ profileModal.discoverError }}</span>
            <span v-else-if="profileModal.discovered" class="llm-discover-status is-ok">
              <template v-if="profileModelOptions.length">已获取 {{ profileModal.discovered.length }} 个模型，{{ profileModal.discovered.length - profileModelOptions.length }} 个已添加，下拉里还有 {{ profileModelOptions.length }} 个可选</template>
              <template v-else>已获取 {{ profileModal.discovered.length }} 个模型，但都添加过了，没有可选的</template>
            </span>
            <span v-else class="llm-discover-status">用该供应商已保存的端点与密钥拉取；失败不影响手工输入与保存</span>
          </div>
          <label class="checkbox full image-model-check">
            <input v-model="profileForm.isImageModel" type="checkbox">这是一个图片模型
            <small>勾选后，这张档案本身就是图片模型：配图时用它所属供应商的端点与密钥，模型名就是上面的「上游模型名称」。同一个供应商通常同时提供文本与图片模型，在它下面建两条档案即可——一条写正文，另一条勾选这里供配图，共用一份端点与密钥。</small>
          </label>
          <label>Temperature<input v-model="profileForm.temperature" type="number" min="0" max="2" step="0.1"><small>待 agent4j 支持后接线，当前仅保存不生效</small></label>
          <label>最大输出 Token<input v-model="profileForm.maxTokens" type="number" min="256" max="32768"><small>待 agent4j 支持后接线，当前仅保存不生效</small></label>
          <label class="checkbox full"><input v-model="profileForm.enabled" type="checkbox">启用该档案</label>
        </div>
        <div class="form-actions">
          <button type="button" class="secondary-button" @click="closeProfile">取消</button>
          <button class="primary-button" :disabled="profileSaving"><LoaderCircle v-if="profileSaving" class="spin" :size="15"/>{{ profileSaving?'保存中…':'保存档案' }}</button>
        </div>
      </form>
    </div>

    <!-- 批量添加（多选）：选供应商 → 获取可用模型 → 勾选 → 一次提交。
         后端逐条建、逐条记账，所以「已存在」与「失败」是两种结果，界面必须分开呈现。
         只沿用本页已有的弹窗/表单类，不新增样式（本文件没有 <style> 块）。 -->
    <div v-if="batchModal.open" class="modal-backdrop" @click.self="closeBatch">
      <form ref="batchDialogRef" class="modal-card wide" role="dialog" aria-modal="true" aria-label="批量添加模型档案" tabindex="-1" @submit.prevent="submitBatch">
        <header>
          <div><span class="eyebrow">BULK ADD</span><h3>批量添加模型档案</h3></div>
          <button type="button" class="icon-button" title="关闭" aria-label="关闭批量添加" :disabled="batchModal.submitting" @click="closeBatch"><X :size="19"/></button>
        </header>
        <p class="agent-section-hint">一次为同一个供应商勾选多个模型名：提交后每个模型各建一条档案（档案名称就是模型名）。</p>
        <div v-if="batchModal.formError" class="alert error" role="alert">{{ batchModal.formError }}</div>
        <div v-if="!providers.length" class="alert error" role="status">
          还没有供应商。模型必须挂在供应商下——请先关闭本窗口，点「新增供应商」填好 Base URL 与 API Key，再回来批量添加。
        </div>
        <div class="form-grid">
          <label class="full">供应商
            <select v-model="batchModal.providerId" :disabled="!providers.length || batchModal.submitting" @change="onBatchProviderChange">
              <option value="">请选择供应商</option>
              <option v-for="provider in providers" :key="provider.id" :value="provider.id">{{ provider.name }}{{ provider.enabled === true ? '' : '（已停用）' }}</option>
            </select>
            <small>与单条新增共用同一份供应商列表；停用的供应商也能添加模型，但它的模型不会进入故障切换链。</small>
          </label>
          <!-- 探测按钮不放进 <label>：label 内嵌按钮是无效嵌套，点按钮会被 label 转成「聚焦输入框」。 -->
          <div class="full llm-discover-row">
            <button type="button" class="secondary-button" :disabled="!batchModal.providerId || batchModal.discovering || batchModal.submitting"
              :title="batchModal.providerId ? '用该供应商的端点与密钥拉取模型列表' : '请先选择供应商'" @click="discoverForBatch">
              <LoaderCircle v-if="batchModal.discovering" class="spin" :size="15"/><RefreshCw v-else :size="15"/>获取可用模型
            </button>
            <span v-if="batchModal.discoverError" class="llm-discover-status is-error">{{ batchModal.discoverError }}</span>
            <span v-else-if="batchModal.discovered" class="llm-discover-status is-ok">
              <template v-if="batchSelectable.length">已获取 {{ batchModal.discovered.length }} 个模型，{{ batchModal.discovered.length - batchSelectable.length }} 个已添加，还有 {{ batchSelectable.length }} 个可选</template>
              <template v-else>已获取 {{ batchModal.discovered.length }} 个模型，但它们都添加过了</template>
            </span>
            <span v-else class="llm-discover-status">用供应商已保存的端点与密钥拉取；失败不影响已经勾好的内容</span>
          </div>
          <!-- 按 AgentsView 的多选卡片写法复用 .agent-section + .tool-group-grid/.tool-group-option，
               不新增样式；已添加的照样列出来，只是标「已添加」并禁用——眼不见为净会让用户以为
               供应商那边漏了模型，实际上是自己建过档案了。 -->
          <div class="full agent-section">
            <span class="agent-section-title">选择要添加的模型</span>
            <p class="agent-section-hint">该供应商下已有的模型会标成「已添加」且不可勾选——同一供应商下一个模型只能有一条档案，再提交只会被后端跳过。</p>
            <div v-if="batchOptions.length" class="tool-group-grid" role="group" aria-label="可批量添加的模型">
              <label v-for="option in batchOptions" :key="option.name" class="tool-group-option"
                :class="{ active: !option.taken && batchModal.selected.includes(option.name) }">
                <input type="checkbox" :checked="batchModal.selected.includes(option.name)"
                  :disabled="option.taken || batchModal.submitting"
                  :aria-label="`模型 ${option.name}${option.taken ? '（已添加，不可重复添加）' : ''}`"
                  @change="toggleBatchModel(option.name)">
                <span class="tool-group-copy"><strong>{{ option.name }}<span v-if="option.taken" class="status-pill">已添加</span></strong></span>
              </label>
            </div>
            <p v-else-if="batchModal.discovered" class="agent-section-hint">该供应商返回的模型都已经建过档案了，没有可以批量添加的。</p>
            <p v-else-if="batchModal.discoverError" class="agent-section-hint">没有拿到可勾选的模型列表：{{ batchModal.discoverError }}（可以重试，或直接关闭窗口）</p>
            <p v-else class="agent-section-hint">还没有探测结果：先选供应商，再点上面的「获取可用模型」。</p>
          </div>
          <div class="full llm-discover-row">
            <button type="button" class="secondary-button" :disabled="!batchSelectable.length || batchModal.submitting" @click="selectAllBatch">全选</button>
            <button type="button" class="secondary-button" :disabled="!batchModal.selected.length || batchModal.submitting" @click="clearBatchSelection">取消全选</button>
            <span class="llm-discover-status">已选 {{ batchModal.selected.length }} 个；全选只作用于还没添加过的模型</span>
          </div>
        </div>
        <div class="form-actions">
          <button type="button" class="secondary-button" :disabled="batchModal.submitting" @click="closeBatch">取消</button>
          <button class="primary-button" :disabled="batchModal.submitting || !batchModal.selected.length">
            <LoaderCircle v-if="batchModal.submitting" class="spin" :size="15"/>{{ batchSubmitLabel }}
          </button>
        </div>
      </form>
    </div>
  </section>
</template>
