<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { useAuthStore } from '../stores/auth'
import {
  BrainCircuit, KeyRound, Layers, LayoutTemplate, LoaderCircle, Pencil, PlugZap, Plus, Save,
  Shield, ShieldCheck, Star, Trash2, X,
} from 'lucide-vue-next'

const router = useRouter()
const auth = useAuthStore()
const isAdmin = computed(() => (auth.user?.role ?? 'ADMIN') === 'ADMIN')
const loading=ref(true),saving=ref(false),message=ref(''),error=ref('')
const llm=reactive({provider:'OPENAI_COMPATIBLE',baseUrl:'https://api.openai.com',modelName:'gpt-4.1-mini',apiKey:'',hasApiKey:false,apiKeyMasked:'未配置',enabled:false,temperature:0.7,maxTokens:4096,clearApiKey:false,imageBaseUrl:'',imageModelName:'',imageApiKey:'',hasImageApiKey:false,imageApiKeyMasked:'复用 LLM API Key',clearImageApiKey:false})
const password=reactive({currentPassword:'',newPassword:'',confirmPassword:''})
const render=reactive({provider:'MARKFLOW',baseUrl:'',siteBaseUrl:'',syntaxCacheTtlSeconds:86400,enabled:false,token:'',tokenMasked:'未配置',envInjected:false,hasToken:false})
const renderSaving=ref(false),renderTesting=ref(false),renderTest=reactive({done:false,ok:false,message:''})

const profiles = ref([]), profilesLoading = ref(false), profileSaving = ref(false), profilesError = ref(''), profileFormError = ref('')
const profileModal = reactive({open:false, id:null, hasApiKey:false, apiKeyMasked:'未配置'})
const profileForm = reactive(blankProfile())
const PROVIDERS = [
  { value: 'OPENAI_COMPATIBLE', label: 'Chat Completions' },
  { value: 'OPENAI_RESPONSES', label: 'Responses / Codex' },
  { value: 'ANTHROPIC', label: 'Anthropic Messages' },
]
const providerLabel = value => (PROVIDERS.find(p => p.value === value) || {}).label || value
function blankProfile(){return {name:'',provider:'OPENAI_COMPATIBLE',baseUrl:'https://api.openai.com',modelName:'gpt-4.1-mini',apiKey:'',clearApiKey:false,enabled:true,temperature:0.7,maxTokens:4096}}

async function load(){try{Object.assign(llm,await api('/api/settings/llm'))}catch(e){error.value=e.message}finally{loading.value=false}}
async function loadRender(){try{const data=await api('/api/settings/render');Object.assign(render,{...data,token:'',hasToken:Boolean(data.tokenMasked&&data.tokenMasked!=='未配置'),envInjected:Boolean(data.envInjected)})}catch(e){error.value=e.message}}
async function saveLlm(){saving.value=true;error.value='';message.value='';try{const result=await api('/api/settings/llm',{method:'PUT',body:JSON.stringify(llm)});Object.assign(llm,result,{apiKey:'',clearApiKey:false,imageApiKey:'',clearImageApiKey:false});message.value='LLM 配置已保存，新的 AI 请求会立即使用该配置。'}catch(e){error.value=e.message}finally{saving.value=false}}
async function changePassword(){error.value='';message.value='';if(password.newPassword!==password.confirmPassword){error.value='两次输入的新密码不一致';return}try{await api('/api/auth/password',{method:'PUT',body:JSON.stringify(password)});localStorage.removeItem('wechat_bot_token');message.value='密码已修改，请重新登录。';setTimeout(()=>router.push('/login'),800)}catch(e){error.value=e.message}}
/** TTL 归一：空 → null（后端用默认值）；越界 → 抛错提示（后端 @Min(60) @Max(604800)） */
function renderTtlValue(){
  const raw=render.syntaxCacheTtlSeconds
  if(raw===''||raw===null||raw===undefined)return null
  const value=Math.round(Number(raw))
  if(!Number.isFinite(value)||value<60||value>604800)throw new Error('语法缓存 TTL 需在 60 到 604800 秒之间')
  return value
}
function renderPayload(){return {provider:render.provider,baseUrl:render.baseUrl,siteBaseUrl:render.siteBaseUrl,syntaxCacheTtlSeconds:renderTtlValue(),enabled:render.enabled,token:render.token}}
async function saveRender(){renderSaving.value=true;error.value='';message.value='';renderTest.done=false;try{const result=await api('/api/settings/render',{method:'PUT',body:JSON.stringify(renderPayload())});Object.assign(render,{...result,token:'',hasToken:Boolean(result.tokenMasked&&result.tokenMasked!=='未配置'),envInjected:Boolean(result.envInjected)});message.value='排版渲染服务配置已保存。'}catch(e){error.value=e.message}finally{renderSaving.value=false}}
async function testRender(){renderTesting.value=true;error.value='';renderTest.done=false;try{const result=await api('/api/settings/render/test',{method:'POST',body:JSON.stringify(renderPayload())});renderTest.done=true;renderTest.ok=true;renderTest.message=result?.message||`连接正常，语法指令 ${result?.syntaxChars ?? result?.syntaxCacheChars ?? '?'} 字符`}catch(e){renderTest.done=true;renderTest.ok=false;renderTest.message=e.message}finally{renderTesting.value=false}}

async function loadProfiles(){profilesLoading.value=true;profilesError.value='';try{const list=await api('/api/llm-profiles');profiles.value=Array.isArray(list)?list:[]}catch(e){profiles.value=[];profilesError.value=e.message}finally{profilesLoading.value=false}}
function openProfile(profile){
  profileFormError.value=''
  Object.assign(profileForm,blankProfile(),profile?{...profile,apiKey:'',clearApiKey:false}:{})
  profileModal.open=true;profileModal.id=profile?profile.id:null;profileModal.hasApiKey=Boolean(profile?.hasApiKey);profileModal.apiKeyMasked=profile?.apiKeyMasked||'未配置'
}
function closeProfile(){profileModal.open=false;profileFormError.value=''}
function profilePayload(){
  const temperature=profileForm.temperature===''||profileForm.temperature===null?null:Number(profileForm.temperature)
  const maxTokens=profileForm.maxTokens===''||profileForm.maxTokens===null?null:Number(profileForm.maxTokens)
  return {name:profileForm.name.trim(),provider:profileForm.provider,baseUrl:profileForm.baseUrl.trim(),modelName:profileForm.modelName.trim(),apiKey:profileForm.apiKey,clearApiKey:Boolean(profileForm.clearApiKey),enabled:profileForm.enabled,temperature,maxTokens}
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
    closeProfile();message.value=`模型档案「${profileForm.name.trim()}」已${profileModal.id?'保存':'创建'}`;await loadProfiles()
  }catch(e){profileFormError.value=e.message}finally{profileSaving.value=false}
}
async function setDefaultProfile(profile){
  error.value='';message.value=''
  try{await api(`/api/llm-profiles/${profile.id}/set-default`,{method:'POST'});message.value=`已将「${profile.name}」设为默认档案`;await loadProfiles()}catch(e){error.value=e.message}
}
// 兜底档案：主用档案全部不可用时的最后安全网。与「默认」是两个独立标记，
// 后端 setFallback 会把其他档案的标记清掉（全局唯一）。
async function setFallbackProfile(profile){
  error.value='';message.value=''
  try{await api(`/api/llm-profiles/${profile.id}/set-fallback`,{method:'POST'});message.value=`已将「${profile.name}」设为兜底档案`;await loadProfiles()}catch(e){error.value=e.message}
}
async function toggleProfile(profile){
  error.value='';message.value=''
  try{
    await api(`/api/llm-profiles/${profile.id}`,{method:'PUT',body:JSON.stringify({name:profile.name,provider:profile.provider,baseUrl:profile.baseUrl,modelName:profile.modelName,apiKey:'',clearApiKey:false,enabled:!profile.enabled,temperature:profile.temperature,maxTokens:profile.maxTokens})})
    message.value=`档案「${profile.name}」已${profile.enabled?'停用':'启用'}`;await loadProfiles()
  }catch(e){error.value=e.message}
}
async function removeProfile(profile){
  if(!confirm(`确定删除模型档案「${profile.name}」吗？引用它的智能体将回落默认档案。`))return
  error.value='';message.value=''
  try{await api(`/api/llm-profiles/${profile.id}`,{method:'DELETE'});message.value=`模型档案「${profile.name}」已删除`;await loadProfiles()}catch(e){error.value=e.message}
}
function onKeydown(event){if(event.key==='Escape'&&profileModal.open)closeProfile()}
onMounted(()=>{document.addEventListener('keydown',onKeydown);load();loadRender();loadProfiles()})
onBeforeUnmount(()=>document.removeEventListener('keydown',onKeydown))
</script>

<template>
  <section class="page-content settings-page">
    <div class="section-heading"><div><span class="eyebrow">SYSTEM SETTINGS</span><h2>系统设置</h2><p>集中管理 AI 服务和账号安全，敏感密钥只以加密形式保存在数据库中。</p></div></div>
    <div v-if="error" class="alert error">{{error}}</div><div v-if="message" class="alert success-alert">{{message}}</div>
    <div v-if="loading" class="panel empty-state">正在加载配置…</div>
    <div v-else class="settings-grid">
      <form class="panel settings-card" @submit.prevent="saveLlm">
        <header><span class="settings-icon"><BrainCircuit/></span><div><h3>LLM 服务</h3><p>文章对话编辑与定时任务共用这套运行配置。</p></div></header>
        <p class="settings-note">此为默认档案，可在智能体中指定其他档案。</p>
        <div class="form-grid">
          <label>服务类型<select v-model="llm.provider"><option value="OPENAI_COMPATIBLE">Chat Completions</option><option value="OPENAI_RESPONSES">Responses / Codex</option><option value="ANTHROPIC">Anthropic Messages</option></select><small>Codex 模型选择 Responses；Claude 原生接口选择 Anthropic</small></label>
          <label>模型名称<input v-model="llm.modelName" required placeholder="gpt-4.1-mini"></label>
          <label class="full">Base URL<input v-model="llm.baseUrl" required placeholder="https://api.openai.com"><small>只填写服务根地址，不要包含 /v1、/responses、/chat/completions 或 /messages</small></label>
          <label class="full">API Key<input v-model="llm.apiKey" type="password" :placeholder="llm.hasApiKey?'已保存，留空表示不修改':'请输入 API Key'"><small>当前状态：{{llm.apiKeyMasked}}</small></label>
          <label>Temperature<input v-model.number="llm.temperature" type="number" min="0" max="2" step="0.1"><small>待 agent4j 支持后接线，当前仅保存不生效</small></label>
          <label>最大输出 Token<input v-model.number="llm.maxTokens" type="number" min="256" max="32768"><small>待 agent4j 支持后接线，当前仅保存不生效</small></label>
          <label>图片模型<input v-model="llm.imageModelName" placeholder="gpt-image-1"><small>留空则禁用 AI 画图和图片编辑</small></label>
          <label>图片服务 Base URL<input v-model="llm.imageBaseUrl" placeholder="留空则复用 LLM Base URL"></label>
          <label class="full">图片服务 API Key<input v-model="llm.imageApiKey" type="password" :placeholder="llm.hasImageApiKey?'已单独保存，留空表示不修改':'留空则复用 LLM API Key'"><small>当前状态：{{llm.imageApiKeyMasked}}</small></label>
          <label class="checkbox full"><input v-model="llm.enabled" type="checkbox">启用 AI 服务</label>
          <label v-if="llm.hasApiKey" class="checkbox full danger"><input v-model="llm.clearApiKey" type="checkbox">删除已保存的 API Key</label>
          <label v-if="llm.hasImageApiKey" class="checkbox full danger"><input v-model="llm.clearImageApiKey" type="checkbox">删除单独保存的图片 API Key并改为复用</label>
        </div>
        <footer><span><ShieldCheck :size="15"/>API Key 使用系统密钥 AES-GCM 加密</span><button class="primary-button" :disabled="saving"><Save :size="16"/>{{saving?'保存中…':'保存配置'}}</button></footer>
      </form>
      <form class="panel settings-card render-card" @submit.prevent="saveRender">
        <header><span class="settings-icon"><LayoutTemplate/></span><div><h3>排版渲染服务</h3><p>接入 MarkFlow 渲染服务，渲染式排版技能（MARKFLOW）依赖此项服务。</p></div></header>
        <div class="form-grid">
          <label class="full">渲染服务 Base URL<input v-model="render.baseUrl" placeholder="https://render.example.com"><small>MarkFlow 渲染服务的根地址，例如内网部署地址</small></label>
          <label class="full">渲染令牌 Token<input v-model="render.token" type="password" :placeholder="render.hasToken?'已配置（点击修改可覆盖）':'请输入渲染服务访问令牌'" autocomplete="new-password"><small>当前状态：{{ render.tokenMasked || '未配置' }}</small><span v-if="render.envInjected" class="render-env-badge">已由环境变量 MARKFLOW_RENDER_TOKEN 注入</span></label>
          <label class="full">本站公网地址（Site Base URL）<input v-model="render.siteBaseUrl" placeholder="https://your-site.com"><small>本站公网地址，用于把文章图片 /uploads/ 相对路径转为渲染服务可访问的绝对直链</small></label>
          <label>语法缓存 TTL（秒）<input v-model.number="render.syntaxCacheTtlSeconds" type="number" min="60" step="60"><small>渲染语法指令的缓存时长，最短 60 秒</small></label>
          <label class="checkbox"><input v-model="render.enabled" type="checkbox">启用渲染服务</label>
        </div>
        <div v-if="renderTest.done" class="alert" :class="renderTest.ok?'success-alert':'error'" role="status">{{ renderTest.message }}</div>
        <footer>
          <span><PlugZap :size="15"/>测试当前已保存的配置连通性</span>
          <div class="render-actions">
            <button type="button" class="secondary-button" :disabled="renderTesting||renderSaving" @click="testRender"><LoaderCircle v-if="renderTesting" class="spin" :size="15"/><PlugZap v-else :size="15"/>{{ renderTesting?'测试中…':'测试连接' }}</button>
            <button class="primary-button" :disabled="renderSaving||renderTesting"><LoaderCircle v-if="renderSaving" class="spin" :size="15"/><Save v-else :size="15"/>{{ renderSaving?'保存中…':'保存配置' }}</button>
          </div>
        </footer>
      </form>
      <form class="panel settings-card password-card" @submit.prevent="changePassword">
        <header><span class="settings-icon amber"><KeyRound/></span><div><h3>修改登录密码</h3><p>修改后所有已登录设备都会退出。</p></div></header>
        <div class="form-grid one-column">
          <label>当前密码<input v-model="password.currentPassword" type="password" required autocomplete="current-password"></label>
          <label>新密码<input v-model="password.newPassword" type="password" required minlength="8" autocomplete="new-password"></label>
          <label>确认新密码<input v-model="password.confirmPassword" type="password" required minlength="8" autocomplete="new-password"></label>
        </div>
        <footer><span>新密码至少 8 位</span><button class="secondary-button">修改密码</button></footer>
      </form>
    </div>

    <section v-if="!loading" class="panel settings-card profile-card">
      <header>
        <span class="settings-icon"><Layers/></span>
        <div><h3>模型档案</h3><p>智能体可指定专属档案；未指定时使用默认档案。仅管理员可管理。</p></div>
        <button v-if="isAdmin" class="primary-button profile-add" @click="openProfile()"><Plus :size="16"/>新增档案</button>
      </header>
      <div v-if="profilesLoading" class="empty-state"><LoaderCircle class="spin" :size="20"/>正在加载模型档案…</div>
      <div v-else-if="profilesError" class="alert error" role="alert">{{ profilesError }}（模型档案仅管理员可管理，非管理员可忽略此项）</div>
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
            <div><dt>Base URL</dt><dd :title="profile.baseUrl">{{ profile.baseUrl }}</dd></div>
            <div><dt>API Key</dt><dd>{{ profile.apiKeyMasked }}</dd></div>
            <div><dt>温度 / 上限</dt><dd>{{ profile.temperature ?? '—' }} · {{ profile.maxTokens ?? '—' }}</dd></div>
          </dl>
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
    </section>

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
