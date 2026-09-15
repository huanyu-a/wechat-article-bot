<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { Plus, Bot, Play, Clock3, MoreHorizontal, X, CheckCircle2, AlertCircle, AlertTriangle, Trash2, LoaderCircle, FileText, Layers, Network } from 'lucide-vue-next'
import CronBuilder from '../components/CronBuilder.vue'
import { parseSkillIds } from '../utils/skills'
import SkillPicker from '../components/SkillPicker.vue'
import { describeQuartzCron } from '../utils/cron'

const router=useRouter()
const tasks=ref([]),accounts=ref([]),assets=ref([]),allSkills=ref([]),error=ref(''),notice=ref(''),showForm=ref(false),showRuns=ref(false),runs=ref([]),selected=ref(null),running=ref(null)
const agents=ref([]),agentsFailed=ref(false)
const defaultInstruction=`每天检索并浏览过去24小时内值得关注的 AI 产品与行业动态，优先阅读官方公告和可靠媒体来源。选择一个最适合公众号读者的主题，核实关键事实后，整理成一篇观点清晰、结构完整、适合手机阅读的原创文章。正文末尾列出主要参考来源；需要时从素材库选择或生成合适的封面和正文配图。`

/** 执行模式（后端 /api/tasks/execution-modes 返回；接口未就绪时用本地兜底） */
const FALLBACK_MODES=[{key:'SINGLE',name:'单智能体'},{key:'PIPELINE',name:'流水线'},{key:'COORDINATOR',name:'协调者'}]
const executionModes=ref([...FALLBACK_MODES])
const MODE_DESC={
  SINGLE:'单个智能体自主完成调研、写作与配图，行为与原有任务一致',
  PIPELINE:'调研 → 写作 → 配图 → 审核 依次执行，审核不通过自动返工，成本可控',
  COORDINATOR:'由主编智能体规划并委托子智能体协作，最灵活但 token 消耗更高',
}
const MODE_ICONS={SINGLE:Bot,PIPELINE:Layers,COORDINATOR:Network}
const MODE_FALLBACK={SINGLE:'单智能体',PIPELINE:'流水线',COORDINATOR:'协调者'}
const modeIcon=key=>MODE_ICONS[key]||Bot
const modeDesc=key=>MODE_DESC[key]||''
function modeLabel(key){if(!key)return MODE_FALLBACK.SINGLE;const found=executionModes.value.find(mode=>mode.key===key);return found?found.name:(MODE_FALLBACK[key]||key)}

/** 运行状态显示名。SUCCESS_WITH_WARNINGS = 跑完了但有工具失败（典型是配图没进文章），
 *  必须与纯粹的 SUCCESS 有可见区别，否则「交付物缺图」会被看成完全成功。 */
const STATUS_LABELS={SUCCESS:'成功',SUCCESS_WITH_WARNINGS:'成功（有警告）',FAILED:'失败',RUNNING:'执行中'}
function statusLabel(key){return STATUS_LABELS[key]||key||''}

/** 四个阶段固定 key（后端 stage_agents 仅接受这四个），stage 用于过滤智能体列表 */
const STAGE_FIELDS=[
  {key:'research',stage:'RESEARCH',label:'调研',hint:'检索并核实资料，产出调研简报',skippable:false},
  {key:'writing',stage:'WRITING',label:'写作',hint:'基于调研简报撰写正文',skippable:false},
  {key:'illustration',stage:'ILLUSTRATION',label:'配图',hint:'选图、生图与封面设置',skippable:true},
  {key:'review',stage:'REVIEW',label:'审核',hint:'核对事实与排版，不通过则自动返工',skippable:true},
]
const stageAgentsOf=stage=>agents.value.filter(agent=>agent.stage===stage)
const blank=()=>({id:null,name:'',accountId:null,coverAssetId:null,cronExpression:'0 0 9 * * ?',timezone:'Asia/Shanghai',aiPrompt:defaultInstruction,outputMode:'LOCAL_DRAFT',skillIds:[],enabled:true,executionMode:'SINGLE',stageAgents:{research:'',writing:'',illustration:'',review:''},maxRevisionRounds:2})
const form=reactive(blank())
let runPoller=null
const outputLabel=value=>({LOCAL_DRAFT:'保存本地草稿',WECHAT_DRAFT:'同步微信草稿',AUTO_PUBLISH:'自动发布公众号'}[value]||value)
const fmt=value=>value?new Date(value).toLocaleString('zh-CN'):'尚未运行'

async function load(){
  try{[tasks.value,accounts.value,assets.value,allSkills.value]=await Promise.all([api('/api/tasks'),api('/api/accounts'),api('/api/assets'),api('/api/skills')])}catch(e){error.value=e.message}
  try{const modes=await api('/api/tasks/execution-modes');if(Array.isArray(modes)&&modes.length)executionModes.value=modes}catch{executionModes.value=[...FALLBACK_MODES]}
  try{agents.value=await api('/api/agents?enabled=true');agentsFailed.value=false}catch{agents.value=[];agentsFailed.value=true}
}
/** stageAgents 后端为 JSON 字符串；归一为四个固定 key 的本地对象（''=使用内置默认） */
function parseStageAgentsObject(value){
  if(!value)return {}
  if(typeof value==='string'){try{const parsed=JSON.parse(value);return parsed&&typeof parsed==='object'?parsed:{}}catch{return {}}}
  return typeof value==='object'?value:{}
}
function normalizeStageAgents(value){
  const source=parseStageAgentsObject(value),result={research:'',writing:'',illustration:'',review:''}
  for(const field of STAGE_FIELDS){
    const raw=source[field.key]
    if(raw===null||raw===undefined||raw==='')continue
    const num=Number(raw)
    if(Number.isFinite(num))result[field.key]=num
  }
  return result
}
function open(task){
  Object.assign(form,blank(),task||{})
  form.skillIds=parseSkillIds(form.skillIds)
  form.stageAgents=normalizeStageAgents(form.stageAgents)
  if(!form.executionMode)form.executionMode='SINGLE'
  const rounds=Number(form.maxRevisionRounds)
  form.maxRevisionRounds=Number.isFinite(rounds)&&rounds>=1&&rounds<=5?rounds:2
  showForm.value=true;error.value=''
}
function taskSkillNames(task){return parseSkillIds(task.skillIds).map(id=>skillNameOf(id)).filter(Boolean)}
function skillNameOf(id){return (allSkills.value.find(s=>String(s.id)===String(id))||{}).name||`技能 #${id}`}
/** 任务卡片上的阶段编排摘要（仅显示显式配置过的阶段） */
function taskStageNote(task){
  const map=parseStageAgentsObject(task.stageAgents)
  return STAGE_FIELDS.map(field=>{
    if(!Object.prototype.hasOwnProperty.call(map,field.key))return null
    const value=Number(map[field.key])
    if(value===0)return `${field.label}跳过`
    const agent=agents.value.find(item=>String(item.id)===String(value))
    return agent?`${field.label}·${agent.name}`:null
  }).filter(Boolean).join(' · ')
}
async function save(){
  try{
    const stageAgents={}
    for(const field of STAGE_FIELDS){
      const value=form.stageAgents?.[field.key]
      if(value===''||value===null||value===undefined)continue
      stageAgents[field.key]=Number(value)
    }
    const rounds=Number(form.maxRevisionRounds)
    const payload={
      ...form,
      skillIds:(form.skillIds||[]).map(Number),
      executionMode:form.executionMode||'SINGLE',
      stageAgents:Object.keys(stageAgents).length?stageAgents:null,
      maxRevisionRounds:Number.isFinite(rounds)&&rounds>=1&&rounds<=5?rounds:2,
    }
    await api(form.id?`/api/tasks/${form.id}`:'/api/tasks',{method:form.id?'PUT':'POST',body:JSON.stringify(payload)})
    showForm.value=false;notice.value='定时创作任务已保存';await load()
  }catch(e){error.value=e.message}
}
async function run(task){if(running.value)return;running.value=task.id;error.value='';notice.value='';try{const result=await api(`/api/tasks/${task.id}/run`,{method:'POST'});notice.value=`任务已在后台启动，运行记录 #${result.id}`;await load()}catch(e){error.value=e.message}finally{running.value=null}}

/** 执行日志按「【前缀】」逐行着色 */
function logKind(tag){
  if(tag.startsWith('委托'))return 'delegate'
  return {调研:'research',写作:'writing',配图:'illustration',审核:'review',协调:'coordinate'}[tag]||'other'
}
function parseLog(text){
  if(!text)return []
  return String(text).split('\n').filter(line=>line.trim()).map(line=>{
    const match=line.match(/^【([^】]+)】\s*(.*)$/)
    if(!match)return {tag:'',text:line,kind:'other'}
    return {tag:match[1],text:match[2],kind:logKind(match[1])}
  })
}
/** stagesSummary 为 JSON 字符串（实际写入的是工作区摘要），解析失败静默忽略 */
function summaryChips(value){
  if(!value)return []
  let parsed=value
  if(typeof value==='string'){try{parsed=JSON.parse(value)}catch{return []}}
  if(!parsed||typeof parsed!=='object')return []
  const chips=[]
  // 阶段耗时（Phase 5）：一次 1801 秒的运行里「时间花在哪一阶段」是首要问题，
  // 此前只能展开执行日志逐行看时间戳反推，因此把它提到卡片上直接可见。
  if(Array.isArray(parsed.stages)){
    for(const stage of parsed.stages){
      if(!stage||!stage.stage)continue
      const seconds=Number(stage.seconds)
      const calls=Number(stage.toolCalls)
      let text=Number.isFinite(seconds)?`${stageLabel(stage.stage)} ${seconds}s`:stageLabel(stage.stage)
      if(Number.isFinite(calls)&&calls>0)text+=` / ${calls} 次`
      chips.push(text)
    }
  }
  if(Number.isFinite(Number(parsed.revisionRound))&&Number(parsed.revisionRound)>0)chips.push(`返工 ${Number(parsed.revisionRound)} 轮`)
  if(Number.isFinite(Number(parsed.researchNotesRounds)))chips.push(`调研简报 ${Number(parsed.researchNotesRounds)} 份`)
  if(Number.isFinite(Number(parsed.reviewRounds)))chips.push(`审核 ${Number(parsed.reviewRounds)} 轮`)
  if(typeof parsed.saved==='boolean')chips.push(parsed.saved?'草稿已保存':'草稿未落盘')
  // 档案切换：整轮仍可能成功，但换过模型说明主用档案当时不可用，是排查与效率对照的关键信息
  if(Array.isArray(parsed.profilesUsed)&&parsed.profilesUsed.length>1)chips.push(`模型档案 ${parsed.profilesUsed.join(' → ')}`)
  return chips
}
/** 阶段名的中文短标签；DELEGATE_* 是 COORDINATOR 的每次委托，保留「第几次委托」的区分 */
function stageLabel(stage){
  const fixed={RESEARCH:'调研',WRITING:'写作',ILLUSTRATION:'配图',REVIEW:'审核',COORDINATE:'协调',SCHEDULED_SINGLE:'创作'}
  if(fixed[stage])return fixed[stage]
  if(stage.startsWith('DELEGATE_'))return `委托·${fixed[stage.slice(9)]||stage.slice(9)}`
  return stage
}
function decorate(run){run.logLines=parseLog(run.executionLog);run.stageChips=summaryChips(run.stagesSummary);return run}
function stopRunPolling(){if(runPoller){clearInterval(runPoller);runPoller=null}}
async function refreshRuns(){
  if(!selected.value)return
  try{
    const list=await api(`/api/tasks/${selected.value.id}/runs`)
    runs.value=(Array.isArray(list)?list:[]).map(decorate)
    if(!runs.value.some(item=>item.status==='RUNNING'))stopRunPolling()
  }catch(e){error.value=e.message;stopRunPolling()}
}
async function history(task){stopRunPolling();selected.value=task;showRuns.value=true;await refreshRuns();if(runs.value.some(item=>item.status==='RUNNING'))runPoller=setInterval(refreshRuns,3000)}
function closeHistory(){showRuns.value=false;stopRunPolling()}
async function remove(task){if(!confirm(`确定删除任务“${task.name}”吗？`))return;try{await api(`/api/tasks/${task.id}`,{method:'DELETE'});await load()}catch(e){error.value=e.message}}
onMounted(load)
onBeforeUnmount(stopRunPolling)
</script>

<template>
  <section class="page-content">
    <div class="section-heading"><div><span class="eyebrow">AUTONOMOUS AGENTS</span><h2>让智能体按时完成一篇文章</h2><p>按计划启动完整创作 Agent，自主检索、阅读、整理、配图并交付文章。</p></div><button class="primary-button" @click="open()"><Plus :size="17" />新建任务</button></div>
    <div v-if="error" class="alert error">{{error}}</div><div v-if="notice" class="alert task-notice">{{notice}}</div>
    <div class="task-grid">
      <article v-for="task in tasks" :key="task.id" class="task-card">
        <header><span class="task-icon"><Bot :size="20" /></span><div class="task-toggle" :class="{on:task.enabled}"><i></i>{{task.enabled?'运行中':'已停用'}}</div></header>
        <h3>{{task.name}}</h3>
        <div class="task-mode-row">
          <span class="mode-badge" :class="`mode-${(task.executionMode||'SINGLE').toLowerCase()}`"><component :is="modeIcon(task.executionMode||'SINGLE')" :size="11" />{{modeLabel(task.executionMode||'SINGLE')}}</span>
          <span v-if="taskStageNote(task)" class="task-mode-note">{{taskStageNote(task)}}</span>
        </div>
        <p class="task-source">{{task.aiPrompt||'尚未填写创作要求'}}</p>
        <div class="task-schedule"><Clock3 :size="16" /><div><small>{{task.timezone}}</small><strong>{{describeQuartzCron(task.cronExpression)}}</strong><code>{{task.cronExpression}}</code></div></div>
        <dl><div><dt>目标公众号</dt><dd>{{task.accountName||'不指定公众号'}}</dd></div><div><dt>完成动作</dt><dd>{{outputLabel(task.outputMode)}}</dd></div><div><dt>下次执行</dt><dd>{{fmt(task.nextRunAt)}}</dd></div></dl>
        <div v-if="taskSkillNames(task).length" class="task-skills-row"><dt class="task-skills-label">创作技能</dt><div class="capability-row"><span v-for="name in taskSkillNames(task)" :key="name" class="skill-tag mini">{{ name }}</span></div></div>
        <footer><button class="secondary-button" :disabled="running===task.id" @click="run(task)"><Play :size="15" />{{running===task.id?'启动中…':'立即执行'}}</button><button class="text-button" @click="history(task)">运行记录</button><button class="icon-button" aria-label="编辑任务" @click="open(task)"><MoreHorizontal :size="17" /></button><button class="icon-button danger-ghost" aria-label="删除任务" @click="remove(task)"><Trash2 :size="16" /></button></footer>
      </article>
      <button v-if="!tasks.length" class="add-account-card" @click="open()"><span><Plus /></span><strong>创建第一个定时创作 Agent</strong><p>告诉它何时运行，以及每次需要完成什么文章</p></button>
    </div>

    <div v-if="showForm" class="modal-backdrop" @click.self="showForm=false">
      <form class="modal-card schedule-modal" @submit.prevent="save">
        <header><div><span class="eyebrow">SCHEDULED AGENT</span><h3>{{form.id?'编辑定时创作任务':'新建定时创作任务'}}</h3></div><button type="button" class="icon-button" aria-label="关闭" @click="showForm=false"><X :size="19" /></button></header>
        <div class="form-grid">
          <label>任务名称<input v-model="form.name" required placeholder="例如：每日 AI 行业头条"></label>
          <label>目标公众号<select v-model="form.accountId"><option :value="null">不指定，仅保存本地</option><option v-for="account in accounts" :key="account.id" :value="account.id">{{account.name}}</option></select></label>
          <label>执行时区<input v-model="form.timezone" required placeholder="Asia/Shanghai"><small>所有日期和时间均按此时区解释。</small></label>
          <span></span>
          <div class="full schedule-builder-field"><span>执行计划</span><CronBuilder v-model="form.cronExpression" /></div>

          <div class="full schedule-builder-field">
            <span id="execution-mode-label">执行模式</span>
            <div class="exec-mode-grid" role="radiogroup" aria-labelledby="execution-mode-label">
              <label v-for="mode in executionModes" :key="mode.key" class="exec-mode-card" :class="{active:form.executionMode===mode.key}">
                <input v-model="form.executionMode" type="radio" name="executionMode" :value="mode.key">
                <span class="exec-mode-icon"><component :is="modeIcon(mode.key)" :size="16" /></span>
                <span class="exec-mode-copy"><strong>{{mode.name}}<i v-if="mode.key==='PIPELINE'" class="exec-mode-recommend">推荐</i></strong><small>{{modeDesc(mode.key)}}</small></span>
              </label>
            </div>
          </div>

          <div v-if="form.executionMode!=='SINGLE'" class="full schedule-builder-field stage-orchestration">
            <div class="stage-orch-head"><Layers :size="15" /><div><strong>阶段编排</strong><small>为每个阶段指定智能体；未指定时使用内置默认智能体。</small></div></div>
            <div v-if="agentsFailed" class="stage-orch-warn" role="status"><AlertCircle :size="14" />智能体列表加载失败，各阶段将使用内置默认智能体。</div>
            <div class="stage-orch-grid">
              <label v-for="field in STAGE_FIELDS" :key="field.key">
                <span>{{field.label}}<i v-if="field.skippable" class="stage-skip-hint">可跳过</i></span>
                <select v-model="form.stageAgents[field.key]">
                  <option value="">使用内置默认</option>
                  <option v-for="agent in stageAgentsOf(field.stage)" :key="agent.id" :value="agent.id">{{agent.name}}</option>
                  <option v-if="field.skippable" :value="0">跳过此阶段</option>
                </select>
                <small>{{field.hint}}</small>
              </label>
            </div>
            <label class="stage-rounds">返工轮次上限<input v-model.number="form.maxRevisionRounds" type="number" min="1" max="5" step="1"><small>审核不通过时最多自动返工的次数（1-5，默认 2）。</small></label>
          </div>

          <label>完成后的动作<select v-model="form.outputMode"><option value="LOCAL_DRAFT">保存为本地草稿</option><option value="WECHAT_DRAFT">同步到微信公众号草稿箱</option><option value="AUTO_PUBLISH">自动发布到公众号</option></select></label>
          <label>默认封面（可选）<select v-model="form.coverAssetId"><option :value="null">由 Agent 自行选择或生成</option><option v-for="asset in assets" :key="asset.id" :value="asset.id">{{asset.originalName}}</option></select></label>
          <label class="full">每次执行的完整要求<textarea v-model="form.aiPrompt" required rows="10" placeholder="描述要关注的领域、时间范围、资料要求、读者、文章风格、结构、配图和事实核验要求。"></textarea><small>Agent 会据此自主使用网页搜索、内容浏览、素材库和图片工具，并且每次只创作一篇新文章。</small></label>
          <div class="full schedule-builder-field"><span>创作技能（可选）</span><SkillPicker v-model="form.skillIds" enabled-only placeholder="不使用创作技能，由 Agent 自由发挥" /></div>
          <label class="checkbox full"><input v-model="form.enabled" type="checkbox">保存后启用 Quartz 调度</label>
        </div>
        <div class="form-actions"><button type="button" class="secondary-button" @click="showForm=false">取消</button><button class="primary-button">保存任务</button></div>
      </form>
    </div>

    <div v-if="showRuns" class="modal-backdrop" @click.self="closeHistory">
      <div class="modal-card wide">
        <header><div><span class="eyebrow">AGENT RUNS</span><h3>{{selected?.name}}</h3></div><button class="icon-button" aria-label="关闭" @click="closeHistory"><X :size="19" /></button></header>
        <div class="run-list">
          <div v-for="run in runs" :key="run.id">
            <CheckCircle2 v-if="run.status==='SUCCESS'" class="success-text" />
            <AlertTriangle v-else-if="run.status==='SUCCESS_WITH_WARNINGS'" class="warn-text" />
            <LoaderCircle v-else-if="run.status==='RUNNING'" class="spin" />
            <AlertCircle v-else class="danger-text" />
            <div>
              <strong>{{statusLabel(run.status)}} · {{run.triggerType}}<span v-if="run.mode" class="mode-badge" :class="`mode-${run.mode.toLowerCase()}`">{{modeLabel(run.mode)}}</span></strong>
              <p>{{run.message||'智能体正在执行研究与创作…'}}</p>
              <small>工具调用 {{run.toolCallCount||0}} 次<span v-if="run.articleId"> · 文章 #{{run.articleId}}</span></small>
              <div v-if="run.stageChips.length" class="run-stage-chips"><span v-for="chip in run.stageChips" :key="chip">{{chip}}</span></div>
              <details v-if="run.logLines.length" class="run-log">
                <summary>查看分阶段执行日志（{{run.logLines.length}} 行）</summary>
                <div class="run-log-lines">
                  <p v-for="(line,index) in run.logLines" :key="index" :class="`log-${line.kind}`"><b v-if="line.tag">【{{line.tag}}】</b>{{line.text}}</p>
                </div>
              </details>
            </div>
            <div class="run-actions"><button v-if="run.articleId" class="text-button" @click="router.push(`/articles/${run.articleId}`)"><FileText :size="14" />查看文章</button><time>{{fmt(run.startedAt)}}</time></div>
          </div>
          <div v-if="!runs.length" class="empty-state">暂无运行记录</div>
        </div>
      </div>
    </div>
  </section>
</template>
