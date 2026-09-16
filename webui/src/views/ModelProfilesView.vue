<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'
import {
  BrainCircuit, Info, Layers, LoaderCircle, Pencil, Plus, Shield, ShieldCheck, Star, Trash2, X,
} from 'lucide-vue-next'

const auth = useAuthStore()
// 角色未知时按「非管理员」处理：宁可让管理员的按钮晚一个 /me 往返出现，
// 也不要让非管理员看到按钮、点开后才收到 403。
const isAdmin = computed(() => auth.user?.role === 'ADMIN')

const profiles = ref([]), agents = ref([])
const loading = ref(true), profilesError = ref(''), message = ref(''), error = ref('')
// 绑定用量只是锦上添花；取不到时不能把「未知」说成「没有」，故单独记一个不可用标志
const agentsReady = ref(false)
const profileSaving = ref(false), profileFormError = ref('')
const profileModal = reactive({ open: false, id: null, hasApiKey: false, apiKeyMasked: '未配置' })
const profileForm = reactive(blankProfile())

const PROVIDERS = [
  { value: 'OPENAI_COMPATIBLE', label: 'Chat Completions' },
  { value: 'OPENAI_RESPONSES', label: 'Responses / Codex' },
  { value: 'ANTHROPIC', label: 'Anthropic Messages' },
]
const providerLabel = value => (PROVIDERS.find(p => p.value === value) || {}).label || value
function blankProfile(){return {name:'',provider:'OPENAI_COMPATIBLE',baseUrl:'https://api.openai.com',modelName:'gpt-4.1-mini',imageModelName:'',apiKey:'',clearApiKey:false,enabled:true,temperature:0.7,maxTokens:4096}}

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

async function load(){
  loading.value = true
  profilesError.value = ''
  try {
    const list = await api('/api/llm-profiles')
    profiles.value = Array.isArray(list) ? list : []
  } catch (e) {
    profiles.value = []
    profilesError.value = e.message
  } finally { loading.value = false }
  // 用量信息只是锦上添花：取不到（非管理员 / 接口异常）不影响档案管理本身
  try {
    const list = await api('/api/agents')
    agents.value = Array.isArray(list) ? list : []
    agentsReady.value = true
  } catch { agents.value = []; agentsReady.value = false }
}

function openProfile(profile){
  profileFormError.value=''
  // imageModelName 显式归范成 ''：后端对存量档案返回 null，而 v-model 绑 null 的
  // 输入框与随后的 .trim() 都不可靠（见 profilePayload 的注释）。
  Object.assign(profileForm,blankProfile(),profile?{...profile,apiKey:'',clearApiKey:false,imageModelName:profile.imageModelName||''}:{})
  profileModal.open=true;profileModal.id=profile?profile.id:null;profileModal.hasApiKey=Boolean(profile?.hasApiKey);profileModal.apiKeyMasked=profile?.apiKeyMasked||'未配置'
}
function closeProfile(){profileModal.open=false;profileFormError.value=''}
function profilePayload(){
  const temperature=profileForm.temperature===''||profileForm.temperature===null?null:Number(profileForm.temperature)
  const maxTokens=profileForm.maxTokens===''||profileForm.maxTokens===null?null:Number(profileForm.maxTokens)
  // imageModelName 必须做 null 归范：存量档案该列就是 NULL，而 Object.assign 会把
  // blankProfile() 的 '' 覆盖成 null（null 是会覆盖的，不是被跳过的），
  // 直接 .trim() 会抛 TypeError —— 表现为「编辑任何存量档案都存不进去」。
  return {name:profileForm.name.trim(),provider:profileForm.provider,baseUrl:profileForm.baseUrl.trim(),modelName:profileForm.modelName.trim(),imageModelName:(profileForm.imageModelName||'').trim(),apiKey:profileForm.apiKey,clearApiKey:Boolean(profileForm.clearApiKey),enabled:profileForm.enabled,temperature,maxTokens}
}
async function saveProfile(){
  profileFormError.value=''
  if(!profileForm.name.trim()){profileFormError.value='请填写档案名称';return}
  if(!profileForm.baseUrl.trim()){profileFormError.value='请填写 Base URL';return}
  if(!profileForm.modelName.trim()){profileFormError.value='请填写模型名称';return}
  if(profileForm.enabled&&!profileModal.hasApiKey&&!profileForm.apiKey.trim()){profileFormError.value='启用模型档案前必须填写 API Key';return}
  if(profileForm.enabled&&profileForm.clearApiKey&&!profileForm.apiKey.trim()){profileFormError.value='删除已保存的 API Key 后档案将无法启用，请先填写新 Key 或取消启用';return}
  const temperature=profileForm.temperature===''||profileForm.temperature===null?null:Number(profileForm.temperature)
  const maxTokens=profileForm.maxTokens===''||profileForm.maxTokens===null?null:Number(profileForm.maxTokens)
  if(temperature!==null&&(Number.isNaN(temperature)||temperature<0||temperature>2)){profileFormError.value='temperature 需在 0 到 2 之间（可留空）';return}
  if(maxTokens!==null&&(Number.isNaN(maxTokens)||maxTokens<256||maxTokens>32768)){profileFormError.value='最大输出 Token 需在 256 到 32768 之间（可留空）';return}
  profileSaving.value=true
  try{
    await api(profileModal.id?`/api/llm-profiles/${profileModal.id}`:'/api/llm-profiles',{method:profileModal.id?'PUT':'POST',body:JSON.stringify(profilePayload())})
    closeProfile();message.value=`模型档案「${profileForm.name.trim()}」已${profileModal.id?'保存':'创建'}`;await load()
  }catch(e){profileFormError.value=e.message}finally{profileSaving.value=false}
}
async function setDefaultProfile(profile){
  error.value='';message.value=''
  try{await api(`/api/llm-profiles/${profile.id}/set-default`,{method:'POST'});message.value=`已将「${profile.name}」设为默认档案`;await load()}catch(e){error.value=e.message}
}
// 兜底档案：主用档案全部不可用时的最后安全网。与「默认」是两个独立标记，
// 后端 setFallback 会把其他档案的标记清掉（全局唯一）。
async function setFallbackProfile(profile){
  error.value='';message.value=''
  try{await api(`/api/llm-profiles/${profile.id}/set-fallback`,{method:'POST'});message.value=`已将「${profile.name}」设为兜底档案`;await load()}catch(e){error.value=e.message}
}
// 停用/启用走的是整条 PUT（后端 apply 会把缺省字段当「清空」），因此必须原样带上 imageModelName，
// 否则一次「点开关」就会把已配好的图片模型悄悄抹掉。
async function toggleProfile(profile){
  error.value='';message.value=''
  try{
    await api(`/api/llm-profiles/${profile.id}`,{method:'PUT',body:JSON.stringify({name:profile.name,provider:profile.provider,baseUrl:profile.baseUrl,modelName:profile.modelName,imageModelName:profile.imageModelName||'',apiKey:'',clearApiKey:false,enabled:!profile.enabled,temperature:profile.temperature,maxTokens:profile.maxTokens})})
    message.value=`档案「${profile.name}」已${profile.enabled?'停用':'启用'}`;await load()
  }catch(e){error.value=e.message}
}
async function removeProfile(profile){
  const count = bindingCounts.value.get(String(profile.id)) || 0
  const suffix = !agentsReady.value
    ? '绑定情况暂不可用，引用它的智能体将回落默认档案。'
    : count ? `有 ${count} 个智能体指定了它，删除后将回落到默认档案。` : '引用它的智能体将回落默认档案。'
  if(!confirm(`确定删除模型档案「${profile.name}」吗？${suffix}`))return
  error.value='';message.value=''
  try{await api(`/api/llm-profiles/${profile.id}`,{method:'DELETE'});message.value=`模型档案「${profile.name}」已删除`;await load()}catch(e){error.value=e.message}
}
function onKeydown(event){if(event.key==='Escape'&&profileModal.open)closeProfile()}
onMounted(()=>{document.addEventListener('keydown',onKeydown);load()})
onBeforeUnmount(()=>document.removeEventListener('keydown',onKeydown))
</script>

<template>
  <section class="page-content">
    <div class="section-heading">
      <div>
        <span class="eyebrow">MODEL PROFILES</span>
        <h2>模型档案</h2>
        <p>为不同智能体准备独立的模型与密钥。切换顺序：智能体绑定档案 → 默认档案 → 兜底档案 → 其余已启用档案。</p>
      </div>
      <button v-if="isAdmin" class="primary-button" @click="openProfile()"><Plus :size="16"/>新增档案</button>
    </div>

    <div v-if="error" class="alert error">{{error}}</div>
    <div v-if="message" class="alert success-alert">{{message}}</div>

    <div class="profile-legend">
      <Info :size="15"/>
      <span><strong>默认档案</strong>：未被单独指定档案的智能体都用它（全局唯一）。</span>
      <span><strong>兜底档案</strong>：绑定档案与默认档案都不可用时才启用，应选最稳、最便宜的通道（全局唯一）。</span>
      <span><strong>图片模型</strong>：留空表示该档案只负责文本模型，配图沿用「系统设置」里的图片模型。</span>
    </div>
    <div class="skill-engine-alert">
      <ShieldCheck :size="15"/>模型档案仅管理员可管理；停用或删除处于故障切换链上的档案，会让整轮创作退回到更慢的通道。
    </div>

    <div v-if="loading" class="panel empty-state"><LoaderCircle class="spin" :size="20"/>正在加载模型档案…</div>
    <div v-else-if="profilesError" class="panel empty-state" role="alert">
      <Layers :size="26"/><strong>无法读取模型档案</strong>
      <p>{{ profilesError }}（模型档案仅管理员可管理，非管理员可忽略此项）</p>
    </div>
    <div v-else class="profile-grid">
      <article v-for="profile in profiles" :key="profile.id" class="profile-item" :class="{off:!profile.enabled}">
        <header>
          <div class="profile-titles">
            <strong>{{ profile.name }}</strong>
            <span v-if="profile.isDefault" class="profile-default-badge"><Star :size="11"/>默认</span>
            <span v-if="profile.isFallback" class="profile-fallback-badge"><Shield :size="11"/>兜底</span>
            <span v-if="!profile.enabled" class="status-pill">已停用</span>
          </div>
          <label v-if="isAdmin" class="skill-switch" :title="profile.enabled?'点击停用':'点击启用'">
            <input type="checkbox" :checked="profile.enabled" :aria-label="`${profile.enabled?'停用':'启用'}模型档案 ${profile.name}`" @change="toggleProfile(profile)">
            <i></i>
          </label>
        </header>
        <p class="profile-provider">{{ providerLabel(profile.provider) }}</p>
        <dl>
          <div><dt>模型</dt><dd>{{ profile.modelName }}</dd></div>
          <div><dt>图片模型</dt><dd :title="profile.imageModelName || ''">{{ profile.imageModelName || '跟随全局图片设置' }}</dd></div>
          <div><dt>Base URL</dt><dd :title="profile.baseUrl">{{ profile.baseUrl }}</dd></div>
          <div><dt>API Key</dt><dd>{{ profile.apiKeyMasked }}</dd></div>
          <div><dt>温度 / 上限</dt><dd>{{ profile.temperature ?? '—' }} · {{ profile.maxTokens ?? '—' }}</dd></div>
        </dl>
        <p class="profile-usage"><BrainCircuit :size="12"/>{{ bindingText(profile) }}</p>
        <footer v-if="isAdmin">
          <button class="secondary-button" :disabled="profile.isDefault" :title="profile.isDefault?'已是默认档案':''" @click="setDefaultProfile(profile)"><Star :size="14"/>{{ profile.isDefault?'默认档案':'设为默认' }}</button>
          <button class="secondary-button" :disabled="profile.isFallback" :title="profile.isFallback?'已是兜底档案':'主用档案全部不可用时自动启用它'" @click="setFallbackProfile(profile)"><Shield :size="14"/>{{ profile.isFallback?'兜底档案':'设为兜底' }}</button>
          <button class="icon-button" title="编辑" aria-label="编辑模型档案" @click="openProfile(profile)"><Pencil :size="16"/></button>
          <button class="icon-button danger-ghost" :title="profile.isDefault?'默认档案不可删除':'删除档案'" :aria-label="profile.isDefault?'默认档案不可删除':'删除档案'" :disabled="profile.isDefault" @click="removeProfile(profile)"><Trash2 :size="16"/></button>
        </footer>
      </article>
      <button v-if="isAdmin" class="add-account-card profile-add-card" @click="openProfile()"><span><Plus/></span><strong>新增模型档案</strong><p>为不同智能体准备独立的模型与密钥</p></button>
      <div v-if="!profiles.length && !isAdmin" class="empty-state"><Layers :size="26"/><strong>暂无模型档案</strong><p>模型档案仅管理员可管理。</p></div>
    </div>

    <div v-if="profileModal.open" class="modal-backdrop" @click.self="closeProfile">
      <form class="modal-card profile-form-modal" role="dialog" aria-modal="true" aria-label="模型档案配置" @submit.prevent="saveProfile">
        <header>
          <div><span class="eyebrow">{{ profileModal.id ? 'EDIT PROFILE' : 'NEW PROFILE' }}</span><h3>{{ profileModal.id ? '编辑模型档案' : '新增模型档案' }}</h3></div>
          <button type="button" class="icon-button" title="关闭" @click="closeProfile"><X :size="19"/></button>
        </header>
        <div v-if="profileFormError" class="alert error" role="alert">{{ profileFormError }}</div>
        <div class="form-grid">
          <label>档案名称<input v-model="profileForm.name" required maxlength="100" placeholder="例如：廉价快速模型"></label>
          <label>服务类型<select v-model="profileForm.provider"><option v-for="provider in PROVIDERS" :key="provider.value" :value="provider.value">{{ provider.label }}</option></select></label>
          <label class="full">Base URL<input v-model="profileForm.baseUrl" required placeholder="https://api.openai.com"><small>只填写服务根地址，不要包含 /v1、/responses、/chat/completions 或 /messages</small></label>
          <label class="full">模型名称<input v-model="profileForm.modelName" required placeholder="gpt-4.1-mini"></label>
          <label class="full">图片模型名称<input v-model="profileForm.imageModelName" placeholder="留空则跟随全局图片设置"><small>配图师等生成图片的智能体使用。留空表示该档案不指定，图片仍走「系统设置」里的图片模型。</small></label>
          <label class="full">API Key<input v-model="profileForm.apiKey" type="password" autocomplete="new-password" :placeholder="profileModal.hasApiKey?'已保存，留空表示不修改':'请输入 API Key'"><small v-if="profileModal.hasApiKey">当前状态：{{ profileModal.apiKeyMasked }}；留空表示不修改。</small><small v-else>启用档案前必须配置 API Key，密钥使用系统密钥 AES-GCM 加密保存。</small></label>
          <label v-if="profileModal.hasApiKey" class="checkbox full danger"><input v-model="profileForm.clearApiKey" type="checkbox">删除已保存的 API Key</label>
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
  </section>
</template>