<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api } from '../api'
import {
  BadgeCheck, CircleAlert, Compass, Copy, Eye, Goal, Heading, Image, LayoutTemplate,
  Languages, ListEnd, LoaderCircle, MessageSquareQuote, PenLine, Pencil, Pilcrow, Plus,
  Shapes, Sigma, Trash2, X,
} from 'lucide-vue-next'
import { SKILL_DIMENSIONS, MARKFLOW_PRESETS, colorInputValue, dimensionClass, dimensionLabel, hexColor } from '../utils/skills'

const skills = ref([]), loading = ref(true), error = ref(''), notice = ref(''), saving = ref(false)
const filterDimension = ref(''), filterEnabled = ref('')
const showForm = ref(false), showPreview = ref(false), previewLoading = ref(false), previewPrompt = ref(''), previewIgnored = ref([])
const blank = () => ({
  id: null, name: '', dimension: 'WRITING', description: '', content: '', enabled: true, isBuiltin: false,
  engine: 'PROMPT', engineConfig: { accentMode: 'AUTO', accent: '#27ae60', dark: '#1e8449' },
})
const form = reactive(blank())
const CONTENT_LIMIT = 60000, DESCRIPTION_LIMIT = 500
const contentCount = computed(() => form.content.length)
const isLayoutForm = computed(() => form.dimension === 'LAYOUT')
// 维度在编辑态锁定（切换维度会改变字段语义），仅新建时允许选择
const dimensionLocked = computed(() => Boolean(form.id))
const markflowForm = computed(() => isLayoutForm.value && form.engine === 'MARKFLOW')
const accentInput = computed(() => colorInputValue(form.engineConfig.accent))
const darkInput = computed(() => colorInputValue(form.engineConfig.dark))
const accentInvalid = computed(() => markflowForm.value && form.engineConfig.accentMode === 'FIXED' && !hexColor(form.engineConfig.accent))
const darkInvalid = computed(() => markflowForm.value && form.engineConfig.accentMode === 'FIXED' && !hexColor(form.engineConfig.dark))
const duplicateCount = computed(() => skills.value.filter(s => s.dimension === form.dimension && String(s.id) !== String(form.id) && s.name.trim() === form.name.trim()).length)
const previewDisabled = computed(() => !form.name.trim() || !form.content.trim())
const engineOptions = [
  { value: 'PROMPT', label: '指令式（PROMPT）', desc: 'AI 直接输出带内联样式的 HTML，无需额外服务' },
  { value: 'MARKFLOW', label: '渲染式（MARKFLOW）', desc: 'AI 只写 Markdown，由 MarkFlow 渲染服务负责排版' },
]
const accentPresets = MARKFLOW_PRESETS

const dimensionIcons = {
  AUDIENCE: Goal, TOPIC: Compass, WRITING: PenLine, LANGUAGE: Languages, TITLE: Heading,
  OPENING: ListEnd, ENDING: Pilcrow, DIGEST: MessageSquareQuote, IMAGE: Image,
  LAYOUT: LayoutTemplate, FACT_CHECK: Sigma, OTHER: Shapes,
}
const engineLabel = value => ({ PROMPT: '指令式', MARKFLOW: '渲染式' }[value] || value)
const contentCountOf = skill => String(skill?.content || '').length

async function load() {
  loading.value = true
  try { skills.value = await api('/api/skills') } catch (e) { error.value = e.message } finally { loading.value = false }
}
const filteredSkills = computed(() => skills.value.filter(s =>
  (!filterDimension.value || s.dimension === filterDimension.value) &&
  (filterEnabled.value === '' || Boolean(s.enabled) === (filterEnabled.value === 'true'))))
const visibleDimensions = computed(() => [...new Set(filteredSkills.value.map(s => s.dimension))])

function open(skill) {
  Object.assign(form, blank(), skill || {})
  const config = { ...blank().engineConfig, ...(skill?.engineConfig || {}) }
  form.engineConfig = { accentMode: 'AUTO', ...config }
  form.engine = isLayoutForm.value ? (form.engine === 'MARKFLOW' ? 'MARKFLOW' : 'PROMPT') : 'PROMPT'
  error.value = ''
  showForm.value = true
}
async function toggleEnabled(skill, event) {
  event.stopPropagation()
  error.value = ''; notice.value = ''
  const target = !skill.enabled
  try {
    const updated = await api(`/api/skills/${skill.id}`, { method: 'PUT', body: JSON.stringify({ ...skill, enabled: target }) })
    Object.assign(skill, updated)
    notice.value = `技能「${skill.name}」已${skill.enabled ? '启用' : '停用'}`
  } catch (e) { error.value = e.message }
}
async function duplicate(skill) {
  error.value = ''; notice.value = ''
  try {
    await api(`/api/skills/${skill.id}/duplicate`, { method: 'POST' })
    notice.value = `已克隆技能「${skill.name}」`
    await load()
  } catch (e) { error.value = e.message }
}
async function remove(skill) {
  if (!confirm(`确定删除技能「${skill.name}」吗？该操作不可恢复。`)) return
  error.value = ''; notice.value = ''
  try {
    await api(`/api/skills/${skill.id}`, { method: 'DELETE' })
    notice.value = `技能「${skill.name}」已删除`
    await load()
  } catch (e) { error.value = e.message }
}
function buildPayload() {
  const payload = { name: form.name.trim(), dimension: form.dimension, description: form.description, content: form.content, enabled: form.enabled }
  if (form.dimension === 'LAYOUT') {
    payload.engine = form.engine
    if (form.engine === 'MARKFLOW') {
      const accentMode = form.engineConfig.accentMode === 'FIXED' ? 'FIXED' : 'AUTO'
      payload.engineConfig = accentMode === 'FIXED'
        ? { accentMode, accent: hexColor(form.engineConfig.accent), dark: hexColor(form.engineConfig.dark) }
        : { accentMode }
    }
  }
  return payload
}
async function save() {
  error.value = ''
  if (!form.name.trim()) { error.value = '请填写技能名称'; return }
  if (!form.content.trim()) { error.value = '请填写技能内容'; return }
  if (contentCount.value > CONTENT_LIMIT) { error.value = `技能内容不能超过 ${CONTENT_LIMIT} 字`; return }
  if (markflowForm.value && form.engineConfig.accentMode === 'FIXED' && (accentInvalid.value || darkInvalid.value)) {
    error.value = '固定主题色需填写 6 位十六进制颜色（例如 27ae60）'; return
  }
  saving.value = true
  try {
    await api(form.id ? `/api/skills/${form.id}` : '/api/skills', { method: form.id ? 'PUT' : 'POST', body: JSON.stringify(buildPayload()) })
    showForm.value = false
    notice.value = `技能「${form.name.trim()}」已${form.id ? '保存' : '创建'}`
    await load()
  } catch (e) { error.value = e.message } finally { saving.value = false }
}
function applyPreset(preset) {
  form.engineConfig.accentMode = 'FIXED'
  form.engineConfig.accent = preset.accent
  form.engineConfig.dark = preset.dark
}
watch(() => form.engineConfig.accentMode, mode => {
  if (mode === 'AUTO') { form.engineConfig.accent = ''; form.engineConfig.dark = '' }
  else {
    if (!hexColor(form.engineConfig.accent)) form.engineConfig.accent = '#27ae60'
    if (!hexColor(form.engineConfig.dark)) form.engineConfig.dark = '#1e8449'
  }
})
async function openPreview(skill) {
  showPreview.value = true; previewLoading.value = true; previewPrompt.value = ''; previewIgnored.value = []
  try {
    const result = await api('/api/skills/preview', { method: 'POST', body: JSON.stringify({ skillIds: [skill.id], scene: 'SCHEDULED' }) })
    previewPrompt.value = result?.prompt ?? result?.content ?? JSON.stringify(result, null, 2)
    previewIgnored.value = result?.ignoredLayoutSkills || []
  } catch (e) { previewPrompt.value = ''; error.value = e.message; showPreview.value = false } finally { previewLoading.value = false }
}
onMounted(load)
</script>

<template>
  <section class="page-content">
    <div class="section-heading"><div><span class="eyebrow">SKILL LIBRARY</span><h2>技能库</h2><p>沉淀给智能体的写作能力：目标读者、选题策略、排版模板……在任务与编辑器中随时组合使用。</p></div><button class="primary-button" @click="open()"><Plus :size="17" />新建技能</button></div>
    <div v-if="error" class="alert error">{{ error }}</div><div v-if="notice" class="alert task-notice">{{ notice }}</div>
    <div class="filter-bar">
      <div class="skill-dimension-tabs">
        <button :class="{ active: filterDimension === '' }" @click="filterDimension = ''">全部</button>
        <button v-for="d in SKILL_DIMENSIONS" :key="d.key" :class="{ active: filterDimension === d.key }" @click="filterDimension = d.key">{{ d.label }}</button>
      </div>
      <span class="filter-spacer"></span>
      <select v-model="filterEnabled" class="compact-select">
        <option value="">全部状态</option><option value="true">仅启用</option><option value="false">仅停用</option>
      </select>
      <span class="filter-count">{{ filteredSkills.length }} 项</span>
    </div>
    <div v-if="loading" class="panel empty-state"><LoaderCircle class="spin" :size="22" />正在加载技能…</div>
    <div v-else-if="!filteredSkills.length" class="panel empty-state">
      <Shapes :size="30" /><strong>这里还没有技能</strong><p>新建第一个技能，把你的写作经验变成可复用的能力。</p><button class="primary-button" @click="open()"><Plus :size="15" />新建技能</button>
    </div>
    <div v-else class="skill-grid">
      <article v-for="skill in filteredSkills" :key="skill.id" class="skill-card" :class="{ off: !skill.enabled }">
        <header>
          <span class="skill-card-icon"><component :is="dimensionIcons[skill.dimension] || Shapes" :size="19" /></span>
          <div class="skill-card-titles">
            <h3>{{ skill.name }}</h3>
            <div class="skill-card-badges">
              <span class="skill-tag" :class="dimensionClass(skill.dimension)">{{ dimensionLabel(skill.dimension) }}</span>
              <span v-if="skill.isBuiltin" class="builtin-badge"><BadgeCheck :size="12" />内置</span>
            </div>
          </div>
          <label class="skill-switch" :title="skill.enabled ? '点击停用' : '点击启用'">
            <input type="checkbox" :checked="skill.enabled" @change="toggleEnabled(skill, $event)">
            <i></i>
          </label>
        </header>
        <p class="skill-desc">{{ skill.description || '暂无描述' }}</p>
        <div v-if="skill.dimension === 'LAYOUT'" class="skill-engine-row">
          <LayoutTemplate :size="13" />
          <span>{{ engineLabel(skill.engine) }}排版引擎</span>
          <em v-if="skill.engine === 'MARKFLOW'">由 MarkFlow 渲染服务排版</em>
        </div>
        <footer>
          <span class="skill-meta">{{ contentCountOf(skill) }} 字</span>
          <span class="row-actions">
            <button class="icon-button" title="预览注入 Prompt" @click="openPreview(skill)"><Eye :size="17" /></button>
            <button class="icon-button" title="编辑" @click="open(skill)"><Pencil :size="16" /></button>
            <button class="icon-button" title="克隆" @click="duplicate(skill)"><Copy :size="16" /></button>
            <button class="icon-button danger-ghost" title="内置技能不可删除" :disabled="skill.isBuiltin" @click="remove(skill)"><Trash2 :size="16" /></button>
          </span>
        </footer>
      </article>
    </div>

    <div v-if="showForm" class="modal-backdrop" @click.self="showForm = false">
      <form class="modal-card skill-form-modal" @submit.prevent="save">
        <header>
          <div><span class="eyebrow">{{ form.id ? 'EDIT SKILL' : 'NEW SKILL' }}</span><h3>{{ form.id ? '编辑技能' : '新建技能' }}</h3></div>
          <button type="button" class="icon-button" @click="showForm = false"><X :size="19" /></button>
        </header>
        <div v-if="form.id && form.isBuiltin" class="skill-engine-alert"><CircleAlert :size="15" />这是内置技能：内容会随系统版本更新被重新注入，你的修改将在下次升级时被覆盖。想长期自定义请先「克隆」为副本再修改。</div>
        <div v-if="markflowForm" class="skill-engine-alert"><CircleAlert :size="15" />渲染式排版需先在「系统设置 → 排版渲染服务」完成 MarkFlow 服务配置，否则定时创作会回退到指令式排版。</div>
        <div class="form-grid">
          <label>技能名称<input v-model="form.name" required maxlength="64" placeholder="例如：科技圈资深主编口吻"></label>
          <label>所属维度<select v-model="form.dimension" :disabled="dimensionLocked"><option v-for="d in SKILL_DIMENSIONS" :key="d.key" :value="d.key">{{ d.label }}</option></select></label>
          <label class="full">技能描述<textarea v-model="form.description" rows="2" maxlength="500" placeholder="一句话说明这个技能的用途，方便在挑选时识别。"></textarea><small>{{ form.description.length }} / {{ DESCRIPTION_LIMIT }} 字</small></label>
          <label class="full">技能内容<textarea v-model="form.content" rows="12" required class="skill-content-input" placeholder="写下完整的指令内容：口吻、结构、约束、示例等，智能体会把这段内容注入到创作 Prompt 中。"></textarea><small :class="{ over: contentCount > CONTENT_LIMIT }">当前 {{ contentCount }} 字 · 上限 {{ CONTENT_LIMIT }} 字（注入时不包含引导语与协议部分）</small></label>
          <div v-if="duplicateCount" class="full skill-duplicate-warning"><CircleAlert :size="14" />同维度下已有 {{ duplicateCount }} 个同名技能，建议先克隆或改名，避免重复注入。</div>
          <template v-if="isLayoutForm">
            <div class="full skill-engine-section">
              <span class="skill-engine-title">排版引擎</span>
              <div class="skill-engine-options">
                <label v-for="option in engineOptions" :key="option.value" class="skill-engine-option" :class="{ active: form.engine === option.value }">
                  <input v-model="form.engine" type="radio" :value="option.value" name="engine">
                  <span class="skill-engine-copy"><strong>{{ option.label }}</strong><small>{{ option.desc }}</small></span>
                </label>
              </div>
              <template v-if="form.engine === 'MARKFLOW'">
                <div class="skill-accent-block">
                  <div class="skill-accent-head">
                    <span class="skill-accent-title">主题色模式</span>
                    <p class="skill-accent-hint">{{ form.engineConfig.accentMode === 'FIXED'
                      ? '所有文章统一套用下方配色，AI 不再自行选择主题色。'
                      : 'AI 会根据文章内容在翡翠绿 / 科技蓝 / 深藏蓝 / 商务红 / 活力橙 / 玫红 / 纯黑之中自动选择主题色。' }}</p>
                  </div>
                  <div class="skill-accent-modes" role="radiogroup" aria-label="主题色模式">
                    <label class="skill-accent-mode" :class="{ active: form.engineConfig.accentMode === 'AUTO' }">
                      <input v-model="form.engineConfig.accentMode" type="radio" value="AUTO" name="accentMode">
                      <span class="skill-accent-copy"><strong>AUTO<i class="skill-accent-tag">推荐</i></strong><small>AI 按内容自选主题色</small></span>
                    </label>
                    <label class="skill-accent-mode" :class="{ active: form.engineConfig.accentMode === 'FIXED' }">
                      <input v-model="form.engineConfig.accentMode" type="radio" value="FIXED" name="accentMode">
                      <span class="skill-accent-copy"><strong>FIXED</strong><small>固定使用同一套主题色</small></span>
                    </label>
                  </div>
                  <div v-if="form.engineConfig.accentMode === 'FIXED'" class="skill-accent-fixed">
                    <div class="skill-color-row">
                      <span class="skill-color-name">主题色 accent</span>
                      <input type="color" :value="accentInput" @input="form.engineConfig.accent = $event.target.value">
                      <input class="skill-color-hex" v-model="form.engineConfig.accent" :class="{ invalid: accentInvalid }" maxlength="7" placeholder="#27ae60">
                      <span class="skill-swatch-pair" aria-hidden="true"><i :style="{ background: accentInput }"></i><i :style="{ background: darkInput }"></i></span>
                    </div>
                    <div class="skill-color-row">
                      <span class="skill-color-name">深色 dark</span>
                      <input type="color" :value="darkInput" @input="form.engineConfig.dark = $event.target.value">
                      <input class="skill-color-hex" v-model="form.engineConfig.dark" :class="{ invalid: darkInvalid }" maxlength="7" placeholder="#1e8449">
                      <span class="skill-color-name muted">正文标题与强调色</span>
                    </div>
                    <div class="skill-preset-row">
                      <button v-for="preset in accentPresets" :key="preset.name" type="button" class="skill-preset-chip" :class="{ active: hexColor(form.engineConfig.accent) === preset.accent && hexColor(form.engineConfig.dark) === preset.dark }" @click="applyPreset(preset)">
                        <i :style="{ background: preset.accent }"></i>{{ preset.name }}
                      </button>
                    </div>
                  </div>
                </div>
              </template>
            </div>
          </template>
        </div>
        <div class="form-actions">
          <button type="button" class="secondary-button" :disabled="previewDisabled || previewLoading" @click="openPreview(form)"><Eye :size="15" />预览注入效果</button>
          <span class="skill-form-spacer"></span>
          <button type="button" class="secondary-button" @click="showForm = false">取消</button>
          <button class="primary-button" :disabled="saving"><LoaderCircle v-if="saving" class="spin" :size="15" />{{ saving ? '保存中…' : '保存技能' }}</button>
        </div>
      </form>
    </div>

    <div v-if="showPreview" class="modal-backdrop" @click.self="showPreview = false">
      <div class="modal-card skill-preview-modal">
        <header><div><span class="eyebrow">PROMPT PREVIEW</span><h3>技能注入预览</h3></div><button type="button" class="icon-button" @click="showPreview = false"><X :size="19" /></button></header>
        <div v-if="previewLoading" class="panel empty-state"><LoaderCircle class="spin" :size="20" />正在生成预览…</div>
        <template v-else>
          <div v-if="previewIgnored.length" class="skill-ignored-tip"><CircleAlert :size="14" />已忽略的排版技能：{{ previewIgnored.map(s => s.name || s).join('、') }}（渲染式排版技能仅定时创作链路生效，多条排版技能仅第一条参与注入）</div>
          <pre class="skill-preview-prompt">{{ previewPrompt }}</pre>
        </template>
      </div>
    </div>
  </section>
</template>
