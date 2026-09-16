<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import {
  BrainCircuit, KeyRound, LayoutTemplate, LoaderCircle, PlugZap, Save, ShieldCheck,
} from 'lucide-vue-next'

const router = useRouter()
const loading=ref(true),saving=ref(false),message=ref(''),error=ref('')
const llm=reactive({provider:'OPENAI_COMPATIBLE',baseUrl:'https://api.openai.com',modelName:'gpt-4.1-mini',apiKey:'',hasApiKey:false,apiKeyMasked:'未配置',enabled:false,temperature:0.7,maxTokens:4096,clearApiKey:false,imageBaseUrl:'',imageModelName:'',imageApiKey:'',hasImageApiKey:false,imageApiKeyMasked:'复用 LLM API Key',clearImageApiKey:false})
const password=reactive({currentPassword:'',newPassword:'',confirmPassword:''})
const render=reactive({provider:'MARKFLOW',baseUrl:'',siteBaseUrl:'',syntaxCacheTtlSeconds:86400,enabled:false,token:'',tokenMasked:'未配置',envInjected:false,hasToken:false})
const renderSaving=ref(false),renderTesting=ref(false),renderTest=reactive({done:false,ok:false,message:''})

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

onMounted(()=>{load();loadRender()})
</script>

<template>
  <section class="page-content settings-page">
    <div class="section-heading"><div><span class="eyebrow">SYSTEM SETTINGS</span><h2>系统设置</h2><p>集中管理 AI 服务和账号安全，敏感密钥只以加密形式保存在数据库中。</p></div></div>
    <div v-if="error" class="alert error">{{error}}</div><div v-if="message" class="alert success-alert">{{message}}</div>
    <div v-if="loading" class="panel empty-state">正在加载配置…</div>
    <div v-else class="settings-grid">
      <form class="panel settings-card" @submit.prevent="saveLlm">
        <header><span class="settings-icon"><BrainCircuit/></span><div><h3>LLM 服务</h3><p>文章对话编辑与定时任务共用这套运行配置。</p></div></header>
        <p class="settings-note">此为默认档案，其他模型档案（含兜底）在「模型档案」菜单里单独管理。</p>
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
  </section>
</template>
