export const SKILL_DIMENSIONS = [
  { key: 'AUDIENCE', label: '目标读者' },
  { key: 'TOPIC', label: '选题策略' },
  { key: 'WRITING', label: '写作风格' },
  { key: 'LANGUAGE', label: '语言习惯' },
  { key: 'TITLE', label: '标题风格' },
  { key: 'OPENING', label: '开头写法' },
  { key: 'ENDING', label: '结尾写法' },
  { key: 'DIGEST', label: '摘要风格' },
  { key: 'IMAGE', label: '图片风格' },
  { key: 'LAYOUT', label: '排版模板' },
  { key: 'FACT_CHECK', label: '事实核查' },
  { key: 'OTHER', label: '其他' },
]

const dimensionMap = new Map(SKILL_DIMENSIONS.map(d => [d.key, d.label]))

export function dimensionLabel(key) { return dimensionMap.get(key) || key || '未分类' }
export function dimensionClass(key) { return `d-${String(key || 'other').toLowerCase().replace(/_/g, '-')}` }

export const MARKFLOW_PRESETS = [
  { name: '翡翠绿', accent: '#27ae60', dark: '#1e8449' },
  { name: '科技蓝', accent: '#0984e3', dark: '#0769b5' },
  { name: '深藏蓝', accent: '#1e3a5f', dark: '#0f2744' },
  { name: '商务红', accent: '#e74c3c', dark: '#c0392b' },
  { name: '活力橙', accent: '#f39c12', dark: '#e67e22' },
  { name: '玫红', accent: '#e84393', dark: '#d63384' },
  { name: '纯黑', accent: '#000000', dark: '#1a1a1a' },
]

const HEX6 = /^[0-9a-fA-F]{6}$/
export function hexColor(value, fallback = '') {
  const raw = String(value ?? '').trim().replace('#', '')
  return HEX6.test(raw) ? `#${raw.toLowerCase()}` : fallback
}
export function colorInputValue(value, fallback = '#000000') {
  return hexColor(value, '') || fallback
}

/**
 * skillIds 契约容错：后端按「TEXT 存逗号分隔字符串」持久化，不同接口的返回形态不一致——
 * 账号接口返回数组（AccountView 已解析），任务/文章接口返回原始字符串。
 * 这里统一解析，避免前端 `Array.isArray` 判断失败把已保存的绑定清空（曾导致任务/文章技能选择丢失）。
 */
export function parseSkillIds(value) {
  if (Array.isArray(value)) return value.map(Number).filter(n => Number.isFinite(n) && n > 0)
  if (value === null || value === undefined || value === '') return []
  const text = String(value).trim()
  if (text.startsWith('[')) {
    try {
      const parsed = JSON.parse(text)
      return Array.isArray(parsed) ? parsed.map(Number).filter(n => Number.isFinite(n) && n > 0) : []
    } catch { return [] }
  }
  return text.split(',').map(v => Number(v.trim())).filter(n => Number.isFinite(n) && n > 0)
}
