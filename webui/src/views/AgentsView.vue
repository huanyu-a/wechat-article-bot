<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { api } from '../api'
import { parseSkillIds } from '../utils/skills'
import SkillPicker from '../components/SkillPicker.vue'
import {
  BadgeCheck, Bot, BrainCircuit, CircleAlert, Clock3, Copy, FileText, Image, LoaderCircle,
  Network, Pencil, PenLine, Plus, Search, ShieldCheck, Trash2, X,
} from 'lucide-vue-next'

const agents = ref([]), toolGroups = ref([]), profiles = ref([])
const loading = ref(true), saving = ref(false), error = ref(''), formError = ref(''), notice = ref('')
const profilesUnavailable = ref(false)
// 全局图片模型（系统设置里的「图片模型」）：档案没声明图片模型时配图就实际用它。
// 卡片上要显示的是「实际生效的那个模型」，因此必须拿到它——否则用户看到的仍是
// 「档案名」而不是「配图用的模型名」，正是这次要修的那个误会。
const globalImageModel = ref('')
const filterStage = ref(''), filterEnabled = ref('')
const showForm = ref(false)

const STAGES = [
  { key: 'EDITOR', label: '编辑', desc: '编辑器内的交互式协作编辑' },
  { key: 'SCHEDULED_SINGLE', label: '定时创作', desc: '定时任务中的单智能体自主创作' },
  { key: 'RESEARCH', label: '调研', desc: '信息检索与事实核实' },
  { key: 'WRITING', label: '写作', desc: '基于调研简报成稿' },
  { key: 'ILLUSTRATION', label: '配图', desc: '选图、生图与图片编辑' },
  { key: 'REVIEW', label: '审核', desc: '内容、事实与排版审核' },
  { key: 'COORDINATE', label: '协调', desc: '规划并委托子智能体协作' },
]
const stageIcons = {
  EDITOR: PenLine, SCHEDULED_SINGLE: Clock3, RESEARCH: Search, WRITING: FileText,
  ILLUSTRATION: Image, REVIEW: ShieldCheck, COORDINATE: Network,
}
const stageLabelOf = key => (STAGES.find(s => s.key === key) || {}).label || key || '未分类'

const blank = () => ({
  id: null, code: '', name: '', stage: 'SCHEDULED_SINGLE', persona: '', toolKeys: [], skillIds: [],
  llmProfileId: '', temperature: '', maxTokens: '', enabled: true, isBuiltin: false,
})
const form = reactive(blank())
const codeLocked = computed(() => Boolean(form.isBuiltin))
const personaCount = computed(() => String(form.persona || '').length)

/** toolKeys 后端为 JSON 数组字符串；容错解析。 */
function parseToolKeys(value) {
  if (Array.isArray(value)) return value.map(String)
  if (typeof value !== 'string' || !value.trim()) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed.map(String) : []
  } catch { return [] }
}

const groupNameOf = key => (toolGroups.value.find(g => g.key === key) || {}).name || key
const groupOf = key => toolGroups.value.find(g => g.key === key)
const profileNameOf = id => {
  if (!id) return '默认档案'
  const found = profiles.value.find(p => String(p.id) === String(id))
  if (found) return found.name
  return profilesUnavailable.value ? `指定档案 #${id}` : `档案 #${id}`
}
/**
 * 与后端 LlmProfileService.failoverChain 同口径的候选链：
 * 绑定档案 → 默认档案 → 兜底档案 → 其余已启用档案（按 id 升序），
 * 每环都要「已启用 + 有 key」（后端 addIfUsable 的判据），按 id 去重。
 *
 * <p>为什么要逐跳照抄而不是取「绑定 + 默认」两跳：这张卡片存在的唯一目的就是
 * 如实告诉用户「配图会用哪个模型」。少抄一跳（兜底/其余已启用）或漏掉可用性过滤，
 * 都会让界面报出一个后端根本不会用的模型名——那比不显示更糟。
 */
const imageCarrier = id => {
  const usable = profiles.value
    .filter(p => p.enabled && p.hasApiKey)
    .slice()
    .sort((a, b) => Number(a.id) - Number(b.id))
  const chain = []
  const seen = new Set()
  const push = profile => {
    if (!profile || profile.id == null || seen.has(String(profile.id))) return
    seen.add(String(profile.id))
    chain.push(profile)
  }
  if (id) push(usable.find(p => String(p.id) === String(id)))
  push(usable.find(p => p.isDefault))
  push(usable.find(p => p.isFallback))
  usable.forEach(push)
  return chain.find(p => (p.imageModelName || '').trim())
}
/**
 * 配图实际生效的图片模型：档案链上第一个声明了 imageModelName 的档案，都没有则用全局设置。
 *
 * <p>返回空串表示「哪儿都没配图片模型」，此时后端会在生图时抛出可读的业务异常。
 */
const imageModelOf = id => (imageCarrier(id) || {}).imageModelName || globalImageModel.value
/** 图片模型来自档案（true）还是全局设置（false）——卡片上据此区分措辞。 */
const imageModelFromProfile = id => Boolean(imageCarrier(id))
const toolKeysOf = agent => parseToolKeys(agent.toolKeys)
/** 只有具备 MEDIA 工具组（能生图/修图）的智能体才值得提图片模型，否则是噪音。 */
const usesImage = agent => toolKeysOf(agent).includes('MEDIA')

async function load() {
  loading.value = true
  try {
    const [agentList, groups] = await Promise.all([api('/api/agents'), api('/api/agents/tool-groups')])
    agents.value = Array.isArray(agentList) ? agentList : []
    toolGroups.value = Array.isArray(groups) ? groups : []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
/** 模型档案接口仅 ADMIN 可读；非管理员静默降级为「默认档案」。 */
async function loadProfiles() {
  try {
    const list = await api('/api/llm-profiles')
    profiles.value = Array.isArray(list) ? list : []
    profilesUnavailable.value = false
  } catch {
    profiles.value = []
    profilesUnavailable.value = true
  }
}
/** 全局图片模型（系统设置）：同样仅 ADMIN 可读，取不到就不显示配图模型这一行。 */
async function loadGlobalImageModel() {
  try {
    const config = await api('/api/settings/llm')
    globalImageModel.value = (config?.imageModelName || '').trim()
  } catch { globalImageModel.value = '' }
}

const filteredAgents = computed(() => agents.value.filter(agent =>
  (!filterStage.value || agent.stage === filterStage.value) &&
  (filterEnabled.value === '' || Boolean(agent.enabled) === (filterEnabled.value === 'true'))))
const groupedAgents = computed(() => STAGES
  .map(stage => ({ ...stage, items: filteredAgents.value.filter(a => a.stage === stage.key) }))
  .filter(group => group.items.length))

function open(agent) {
  error.value = ''; formError.value = ''
  if (!agent) { Object.assign(form, blank()); showForm.value = true; return }
  Object.assign(form, blank(), {
    ...agent,
    toolKeys: parseToolKeys(agent.toolKeys),
    skillIds: parseSkillIds(agent.skillIds),
    llmProfileId: agent.llmProfileId ?? '',
    temperature: agent.temperature ?? '',
    maxTokens: agent.maxTokens ?? '',
  })
  showForm.value = true
}
function agentPayload(agent, overrides = {}) {
  return {
    code: agent.code, name: agent.name, stage: agent.stage, persona: agent.persona,
    toolKeys: parseToolKeys(agent.toolKeys), skillIds: parseSkillIds(agent.skillIds),
    llmProfileId: agent.llmProfileId ?? null,
    temperature: agent.temperature ?? null, maxTokens: agent.maxTokens ?? null,
    enabled: agent.enabled, ...overrides,
  }
}
async function toggleEnabled(agent) {
  error.value = ''; notice.value = ''
  const target = !agent.enabled
  try {
    const updated = await api(`/api/agents/${agent.id}`, { method: 'PUT', body: JSON.stringify(agentPayload(agent, { enabled: target })) })
    Object.assign(agent, updated)
    notice.value = `智能体「${agent.name}」已${agent.enabled ? '启用' : '停用'}`
  } catch (e) { error.value = e.message }
}
async function duplicate(agent) {
  error.value = ''; notice.value = ''
  try {
    await api(`/api/agents/${agent.id}/duplicate`, { method: 'POST' })
    notice.value = `已克隆智能体「${agent.name}」`
    await load()
  } catch (e) { error.value = e.message }
}
async function remove(agent) {
  if (!confirm(`确定删除智能体「${agent.name}」吗？该操作不可恢复。`)) return
  error.value = ''; notice.value = ''
  try {
    await api(`/api/agents/${agent.id}`, { method: 'DELETE' })
    notice.value = `智能体「${agent.name}」已删除`
    await load()
  } catch (e) { error.value = e.message }
}
function toggleToolGroup(key) {
  form.toolKeys = form.toolKeys.includes(key) ? form.toolKeys.filter(k => k !== key) : [...form.toolKeys, key]
}
function buildPayload() {
  const temperature = form.temperature === '' || form.temperature === null ? null : Number(form.temperature)
  const maxTokens = form.maxTokens === '' || form.maxTokens === null ? null : Number(form.maxTokens)
  return {
    code: form.code.trim(), name: form.name.trim(), stage: form.stage, persona: form.persona,
    toolKeys: [...form.toolKeys], skillIds: (form.skillIds || []).map(Number),
    llmProfileId: form.llmProfileId === '' || form.llmProfileId === null ? null : Number(form.llmProfileId),
    temperature, maxTokens, enabled: form.enabled,
  }
}
async function save() {
  formError.value = ''
  if (!form.name.trim()) { formError.value = '请填写智能体名称'; return }
  if (!codeLocked.value && !/^[a-z0-9_]{3,50}$/.test(form.code.trim())) {
    formError.value = '智能体标识只能使用小写字母、数字和下划线（3-50 位）'; return
  }
  if (!form.stage) { formError.value = '请选择智能体阶段'; return }
  if (!form.persona.trim()) { formError.value = '请填写人设'; return }
  if (!form.toolKeys.length) { formError.value = '请至少勾选一个工具组，否则该智能体无法产出任何结果'; return }
  const temperature = form.temperature === '' || form.temperature === null ? null : Number(form.temperature)
  const maxTokens = form.maxTokens === '' || form.maxTokens === null ? null : Number(form.maxTokens)
  if (temperature !== null && (Number.isNaN(temperature) || temperature < 0 || temperature > 2)) {
    formError.value = 'temperature 需在 0 到 2 之间（可留空）'; return
  }
  if (maxTokens !== null && (Number.isNaN(maxTokens) || maxTokens < 256 || maxTokens > 32768)) {
    formError.value = '最大输出 Token 需在 256 到 32768 之间（可留空）'; return
  }
  saving.value = true
  try {
    await api(form.id ? `/api/agents/${form.id}` : '/api/agents', {
      method: form.id ? 'PUT' : 'POST', body: JSON.stringify(buildPayload()),
    })
    showForm.value = false
    notice.value = `智能体「${form.name.trim()}」已${form.id ? '保存' : '创建'}`
    await load()
  } catch (e) { formError.value = e.message } finally { saving.value = false }
}
function onKeydown(event) {
  if (event.key === 'Escape' && showForm.value) showForm.value = false
}
onMounted(() => { document.addEventListener('keydown', onKeydown); load(); loadProfiles(); loadGlobalImageModel() })
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>

<template>
  <section class="page-content">
    <div class="section-heading">
      <div><span class="eyebrow">AGENT DEFINITIONS</span><h2>智能体</h2><p>为每条创作链路配置人设、可用工具组与默认技能，并指定专属模型档案。</p></div>
      <button class="primary-button" @click="open()"><Plus :size="17" />新建智能体</button>
    </div>
    <div v-if="error" class="alert error">{{ error }}</div>
    <div v-if="notice" class="alert task-notice">{{ notice }}</div>
    <div class="skill-engine-alert"><CircleAlert :size="15" />temperature / maxTokens 覆盖项当前不生效：agent4j 2.3.3 的 LLMModel.create 仅接受 4 个参数，暂未接线，待 agent4j 支持后生效。</div>
    <div class="filter-bar">
      <div class="skill-dimension-tabs" role="group" aria-label="按阶段筛选智能体">
        <button :class="{ active: filterStage === '' }" :aria-pressed="filterStage === ''" @click="filterStage = ''">全部</button>
        <button v-for="stage in STAGES" :key="stage.key" :class="{ active: filterStage === stage.key }" :aria-pressed="filterStage === stage.key" @click="filterStage = stage.key">{{ stage.label }}</button>
      </div>
      <span class="filter-spacer"></span>
      <select v-model="filterEnabled" class="compact-select" aria-label="按启用状态筛选">
        <option value="">全部状态</option><option value="true">仅启用</option><option value="false">仅停用</option>
      </select>
      <span class="filter-count">{{ filteredAgents.length }} 个</span>
    </div>

    <div v-if="loading" class="panel empty-state"><LoaderCircle class="spin" :size="22" />正在加载智能体…</div>
    <div v-else-if="!filteredAgents.length" class="panel empty-state">
      <Bot :size="30" />
      <strong>{{ agents.length ? '没有符合筛选条件的智能体' : '这里还没有智能体' }}</strong>
      <p>{{ agents.length ? '换一个阶段或状态筛选试试。' : '新建一个智能体，把技能、工具与模型档案组合成可复用的角色。' }}</p>
      <button v-if="!agents.length" class="primary-button" @click="open()"><Plus :size="15" />新建智能体</button>
    </div>
    <template v-else>
      <section v-for="group in groupedAgents" :key="group.key" class="agent-group">
        <div class="agent-group-head">
          <span class="agent-group-icon"><component :is="stageIcons[group.key] || Bot" :size="16" /></span>
          <h3>{{ group.label }}</h3>
          <span class="agent-group-count">{{ group.items.length }} 个 · {{ group.desc }}</span>
        </div>
        <div class="skill-grid">
          <article v-for="agent in group.items" :key="agent.id" class="skill-card" :class="{ off: !agent.enabled }">
            <header>
              <span class="skill-card-icon"><component :is="stageIcons[agent.stage] || Bot" :size="19" /></span>
              <div class="skill-card-titles">
                <h3>{{ agent.name }}</h3>
                <div class="skill-card-badges">
                  <span v-if="agent.isBuiltin" class="builtin-badge"><BadgeCheck :size="12" />内置</span>
                  <span class="agent-code">{{ agent.code }}</span>
                </div>
              </div>
              <label class="skill-switch" :title="agent.enabled ? '点击停用' : '点击启用'">
                <input type="checkbox" :checked="agent.enabled" :aria-label="`${agent.enabled ? '停用' : '启用'}智能体 ${agent.name}`" @change="toggleEnabled(agent)">
                <i></i>
              </label>
            </header>
            <p class="skill-desc">{{ agent.persona || '暂无人生设定' }}</p>
            <div v-if="toolKeysOf(agent).length" class="agent-tool-row">
              <span v-for="key in toolKeysOf(agent)" :key="key" class="agent-tool-tag" :class="{ browser: (groupOf(key) || {}).browserSide }">{{ groupNameOf(key) }}</span>
            </div>
            <div v-else class="agent-tool-row"><span class="agent-tool-tag empty">未配置工具组</span></div>
            <div class="agent-meta-line"><BrainCircuit :size="13" />{{ profileNameOf(agent.llmProfileId) }}</div>
            <div v-if="usesImage(agent) && imageModelOf(agent.llmProfileId)" class="agent-meta-line"><Image :size="13" />配图模型：{{ imageModelOf(agent.llmProfileId) }}<span class="agent-meta-hint">{{ imageModelFromProfile(agent.llmProfileId) ? '（来自档案）' : '（来自系统设置）' }}</span></div>
            <footer>
              <span class="skill-meta">{{ stageLabelOf(agent.stage) }}</span>
              <span class="row-actions">
                <button class="icon-button" title="编辑" aria-label="编辑智能体" @click="open(agent)"><Pencil :size="16" /></button>
                <button class="icon-button" title="克隆为自定义智能体" aria-label="克隆智能体" @click="duplicate(agent)"><Copy :size="16" /></button>
                <button class="icon-button danger-ghost" :title="agent.isBuiltin ? '内置智能体不可删除' : '删除智能体'" :aria-label="agent.isBuiltin ? '内置智能体不可删除' : '删除智能体'" :disabled="agent.isBuiltin" @click="remove(agent)"><Trash2 :size="16" /></button>
              </span>
            </footer>
          </article>
        </div>
      </section>
    </template>

    <div v-if="showForm" class="modal-backdrop" @click.self="showForm = false">
      <form class="modal-card skill-form-modal agent-form-modal" role="dialog" aria-modal="true" aria-label="智能体配置" @submit.prevent="save">
        <header>
          <div><span class="eyebrow">{{ form.id ? 'EDIT AGENT' : 'NEW AGENT' }}</span><h3>{{ form.id ? '编辑智能体' : '新建智能体' }}</h3></div>
          <button type="button" class="icon-button" title="关闭" @click="showForm = false"><X :size="19" /></button>
        </header>
        <div v-if="formError" class="alert error" role="alert">{{ formError }}</div>
        <div class="form-grid">
          <label>智能体名称<input v-model="form.name" required maxlength="100" placeholder="例如：科技频道调研员"></label>
          <label>智能体标识
            <input v-model="form.code" :disabled="codeLocked" :required="!codeLocked" maxlength="50" placeholder="小写字母 / 数字 / 下划线">
            <small v-if="codeLocked">内置标识不可修改</small>
            <small v-else>仅小写字母、数字与下划线，3-50 位；创建后用于链路装配。</small>
          </label>
          <label>所属阶段
            <select v-model="form.stage" :disabled="codeLocked">
              <option v-for="stage in STAGES" :key="stage.key" :value="stage.key">{{ stage.label }}</option>
            </select>
            <small v-if="codeLocked">内置阶段不可修改</small>
            <small v-else>{{ (STAGES.find(s => s.key === form.stage) || {}).desc }}</small>
          </label>
          <label>模型档案
            <select v-model="form.llmProfileId">
              <option value="">使用默认档案</option>
              <option v-for="profile in profiles" :key="profile.id" :value="profile.id">{{ profile.name }}{{ profile.isDefault ? '（默认）' : '' }}</option>
            </select>
            <small v-if="profilesUnavailable">当前账号无模型档案读取权限，仅能使用默认档案。</small>
            <small v-else>留空即使用系统默认档案。</small>
          </label>
          <label class="full">人设
            <span class="agent-persona-note">核心工具协议由系统内置保障，修改人设不会影响工具调用规则。</span>
            <textarea v-model="form.persona" required rows="9" placeholder="描述这个智能体的定位、工作方式与输出要求。工具调用规则由系统协议统一保障，无需在此重复。"></textarea>
            <small>{{ personaCount }} 字 · 建议聚焦角色定位与产出要求</small>
          </label>
          <div class="full agent-section">
            <span class="agent-section-title">可用工具组</span>
            <p class="agent-section-hint">勾选后该智能体可调用对应工具；未勾选的工具组不会出现在它的工具列表中。</p>
            <div class="tool-group-grid" role="group" aria-label="可用工具组">
              <label v-for="group in toolGroups" :key="group.key" class="tool-group-option" :class="{ active: form.toolKeys.includes(group.key) }">
                <input type="checkbox" :checked="form.toolKeys.includes(group.key)" :aria-label="`工具组 ${group.name}`" @change="toggleToolGroup(group.key)">
                <span class="tool-group-copy">
                  <strong>{{ group.name }}<i v-if="group.browserSide" class="tool-group-browser">需在编辑器页面执行</i></strong>
                  <small>{{ group.description }}</small>
                  <em>{{ (group.tools || []).join('、') }}</em>
                </span>
              </label>
            </div>
            <p v-if="!toolGroups.length" class="agent-section-hint">工具组清单加载失败，请刷新页面后重试。</p>
          </div>
          <div class="full agent-section">
            <span class="agent-section-title">默认技能</span>
            <SkillPicker v-model="form.skillIds" enabled-only placeholder="不绑定默认技能，由智能体按任务自由发挥" />
          </div>
          <label>temperature
            <input v-model="form.temperature" type="number" min="0" max="2" step="0.1" placeholder="留空则使用档案值">
            <small class="agent-pending-note">待 agent4j 支持后接线，当前仅保存不生效</small>
          </label>
          <label>最大输出 Token
            <input v-model="form.maxTokens" type="number" min="256" max="32768" placeholder="留空则使用档案值">
            <small class="agent-pending-note">待 agent4j 支持后接线，当前仅保存不生效</small>
          </label>
          <label class="checkbox full"><input v-model="form.enabled" type="checkbox">启用该智能体</label>
        </div>
        <div class="form-actions">
          <button type="button" class="secondary-button" @click="showForm = false">取消</button>
          <button class="primary-button" :disabled="saving"><LoaderCircle v-if="saving" class="spin" :size="15" />{{ saving ? '保存中…' : '保存智能体' }}</button>
        </div>
      </form>
    </div>
  </section>
</template>
