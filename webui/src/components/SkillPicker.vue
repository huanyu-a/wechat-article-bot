<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { api } from '../api'
import { AlertTriangle, ChevronDown, LayoutTemplate, LoaderCircle, Search, Shapes, X } from 'lucide-vue-next'
import { SKILL_DIMENSIONS, dimensionClass, dimensionLabel } from '../utils/skills'

const props = defineProps({
  // v-model：选中的技能 id 数组
  modelValue: { type: Array, default: () => [] },
  // 仅列出启用的技能（定时任务 / 公众号默认 / 编辑器场景）
  enabledOnly: { type: Boolean, default: false },
  // 场景标识：保留以兼容既有调用方（EDITOR 曾用于置灰 MARKFLOW 技能）。
  // 第④期起编辑器已支持 render_markflow 渲染式排版，该 prop 不再改变行为。
  scene: { type: String, default: '' },
  // 超出该数量时在收起态显示 +N 徽标
  maxVisible: { type: Number, default: 4 },
  placeholder: { type: String, default: '选择创作技能' },
  emptyText: { type: String, default: '还没有可用技能，先到「技能库」新建' },
})
const emit = defineEmits(['update:modelValue', 'change'])

const skills = ref([]), loading = ref(true), error = ref('')
const open = ref(false), keyword = ref(''), pickerRef = ref(null)

const selectedIds = computed(() => (props.modelValue || []).map(Number))
const selectedSkills = computed(() => selectedIds.value.map(id => skills.value.find(s => Number(s.id) === id)).filter(Boolean))
const shownSkills = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return skills.value.filter(s => !kw || s.name.toLowerCase().includes(kw) || dimensionLabel(s.dimension).includes(kw))
})
const grouped = computed(() => SKILL_DIMENSIONS
  .map(d => ({ ...d, items: shownSkills.value.filter(s => s.dimension === d.key) }))
  .filter(g => g.items.length))
const isMarkflow = skill => skill?.dimension === 'LAYOUT' && skill?.engine === 'MARKFLOW'
const layoutCount = computed(() => selectedSkills.value.filter(s => s.dimension === 'LAYOUT').length)
const overflowCount = computed(() => Math.max(0, selectedSkills.value.length - props.maxVisible))

function toggle(skill, event) {
  if (event) event.stopPropagation()
  const id = Number(skill.id)
  const next = selectedIds.value.includes(id) ? selectedIds.value.filter(v => v !== id) : [...selectedIds.value, id]
  emit('update:modelValue', next)
  emit('change', next)
}
function remove(id, event) { if (event) event.stopPropagation(); toggle({ id, engine: 'PROMPT' }) }
function close() { open.value = false }
function onOuterClick(event) { if (pickerRef.value && !pickerRef.value.contains(event.target)) close() }
onMounted(async () => {
  document.addEventListener('click', onOuterClick)
  try {
    const list = await api(`/api/skills${props.enabledOnly ? '?enabled=true' : ''}`)
    skills.value = Array.isArray(list) ? list : []
  } catch (e) { error.value = e.message } finally { loading.value = false }
})
onBeforeUnmount(() => document.removeEventListener('click', onOuterClick))
</script>

<template>
  <div ref="pickerRef" class="skill-picker">
    <div class="skill-picker-field" :class="{ open }" role="button" tabindex="0"
         :aria-label="placeholder" @click="open = !open" @keydown.enter.prevent="open = !open" @keydown.space.prevent="open = !open" @keydown.esc="close">
      <span v-if="!selectedSkills.length" class="skill-picker-placeholder">{{ placeholder }}</span>
      <span v-else class="skill-picker-selection">
        <span v-for="skill in selectedSkills.slice(0, maxVisible)" :key="skill.id" class="skill-tag" :class="dimensionClass(skill.dimension)">
          {{ skill.name }}
          <i v-if="skill.dimension === 'LAYOUT'" class="skill-tag-badge" title="排版技能">排版</i>
          <button type="button" class="skill-tag-remove" :title="`移除 ${skill.name}`" @click.stop="remove(skill.id, $event)"><X :size="11" /></button>
        </span>
        <span v-if="overflowCount" class="skill-tag more">+{{ overflowCount }}</span>
      </span>
      <LoaderCircle v-if="loading" class="spin skill-picker-icon" :size="15" />
      <ChevronDown v-else class="skill-picker-icon" :size="15" />
    </div>
    <p v-if="layoutCount > 1" class="skill-picker-warning" role="alert"><AlertTriangle :size="13" />多个排版技能仅第一个生效</p>
    <div v-if="open" class="skill-picker-pop">
      <div class="skill-picker-search"><Search :size="14" /><input v-model="keyword" type="text" placeholder="搜索技能名称或维度"></div>
      <div v-if="error" class="alert error">{{ error }}</div>
      <div v-if="loading" class="skill-picker-empty"><LoaderCircle class="spin" :size="18" />正在加载技能…</div>
      <template v-else>
        <div v-if="!grouped.length" class="skill-picker-empty"><Shapes :size="18" />{{ skills.length ? '没有匹配的技能' : emptyText }}</div>
        <div v-for="group in grouped" v-else :key="group.key" class="skill-picker-group">
          <span class="skill-picker-group-label">{{ group.label }}</span>
          <button v-for="skill in group.items" :key="skill.id" type="button" class="skill-option"
                  :class="{ active: selectedIds.includes(Number(skill.id)) }"
                  :title="skill.description || skill.name"
                  @click="toggle(skill)">
            <span class="skill-option-name">
              {{ skill.name }}
              <i v-if="skill.dimension === 'LAYOUT'" class="skill-tag-badge" title="排版技能"><LayoutTemplate :size="9" />排版</i>
              <i v-if="isMarkflow(skill)" class="skill-tag-badge markflow">渲染式</i>
            </span>
            <span v-if="selectedIds.includes(Number(skill.id))" class="skill-option-check">✓</span>
          </button>
        </div>
      </template>
    </div>
  </div>
</template>
