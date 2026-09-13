const __vite__mapDeps=(i,m=__vite__mapDeps,d=(m.f||(m.f=["assets/mermaid.core-YpAYt7oI.js","assets/react-vendor-9kzEeTmH.js","assets/purify.es-BnINGy_Y.js","assets/aliyun-oss-sdk-DKk5yfpl.js","assets/_commonjs-dynamic-modules-TDtrdbi3.js","assets/cos-js-sdk-v5-E1QkH1Uc.js","assets/ArticleMode-CnNbpX5i.js","assets/engine-vendor-BkSFcEUu.js","assets/ModeLayout-DrothPfy.js","assets/codemirror-vendor-8ufXWxjV.js","assets/ComponentAttrEditor-DQlD4DtW.js","assets/DocumentMode-Btp15Yh8.js","assets/FontSelect-BC78JFpE.js","assets/useExportAction-DFZlTz2P.js","assets/CardMode-CnbRJrKs.js","assets/zipDownload-DJrmkIyt.js","assets/HtmlMode-B6FSZu4o.js"])))=>i.map(i=>d[i]);
var Er=Object.defineProperty;var _r=(e,t,n)=>t in e?Er(e,t,{enumerable:!0,configurable:!0,writable:!0,value:n}):e[t]=n;var bn=(e,t,n)=>_r(e,typeof t!="symbol"?t+"":t,n);import{a as Wt,R as Re,r as v,j as s}from"./react-vendor-9kzEeTmH.js";import{k as Cr,H as fe}from"./engine-vendor-BkSFcEUu.js";(function(){const t=document.createElement("link").relList;if(t&&t.supports&&t.supports("modulepreload"))return;for(const r of document.querySelectorAll('link[rel="modulepreload"]'))i(r);new MutationObserver(r=>{for(const o of r)if(o.type==="childList")for(const a of o.addedNodes)a.tagName==="LINK"&&a.rel==="modulepreload"&&i(a)}).observe(document,{childList:!0,subtree:!0});function n(r){const o={};return r.integrity&&(o.integrity=r.integrity),r.referrerPolicy&&(o.referrerPolicy=r.referrerPolicy),r.crossOrigin==="use-credentials"?o.credentials="include":r.crossOrigin==="anonymous"?o.credentials="omit":o.credentials="same-origin",o}function i(r){if(r.ep)return;r.ep=!0;const o=n(r);fetch(r.href,o)}})();var oi,yn=Wt;oi=yn.createRoot,yn.hydrateRoot;const Tr="modulepreload",zr=function(e){return"/markflow/"+e},vn={},$e=function(t,n,i){let r=Promise.resolve();if(n&&n.length>0){document.getElementsByTagName("link");const a=document.querySelector("meta[property=csp-nonce]"),l=(a==null?void 0:a.nonce)||(a==null?void 0:a.getAttribute("nonce"));r=Promise.allSettled(n.map(d=>{if(d=zr(d),d in vn)return;vn[d]=!0;const c=d.endsWith(".css"),p=c?'[rel="stylesheet"]':"";if(document.querySelector(`link[href="${d}"]${p}`))return;const f=document.createElement("link");if(f.rel=c?"stylesheet":Tr,c||(f.as="script"),f.crossOrigin="",f.href=d,l&&f.setAttribute("nonce",l),document.head.appendChild(f),c)return new Promise((g,x)=>{f.addEventListener("load",g),f.addEventListener("error",()=>x(new Error(`Unable to preload CSS for ${d}`)))})}))}function o(a){const l=new Event("vite:preloadError",{cancelable:!0});if(l.payload=a,window.dispatchEvent(l),!l.defaultPrevented)throw a}return r.then(a=>{for(const l of a||[])l.status==="rejected"&&o(l.reason);return t().catch(o)})},wn=e=>{let t;const n=new Set,i=(c,p)=>{const f=typeof c=="function"?c(t):c;if(!Object.is(f,t)){const g=t;t=p??(typeof f!="object"||f===null)?f:Object.assign({},t,f),n.forEach(x=>x(t,g))}},r=()=>t,l={setState:i,getState:r,getInitialState:()=>d,subscribe:c=>(n.add(c),()=>n.delete(c))},d=t=e(i,r,l);return l},Ar=e=>e?wn(e):wn,Mr=e=>e;function Ir(e,t=Mr){const n=Re.useSyncExternalStore(e.subscribe,Re.useCallback(()=>t(e.getState()),[e,t]),Re.useCallback(()=>t(e.getInitialState()),[e,t]));return Re.useDebugValue(n),n}const Lr=e=>{const t=Ar(e),n=i=>Ir(t,i);return Object.assign(n,t),n},ai=e=>Lr;function Gt(e,t){let n;try{n=e()}catch{return}return{getItem:r=>{var o;const a=d=>d===null?null:JSON.parse(d,void 0),l=(o=n.getItem(r))!=null?o:null;return l instanceof Promise?l.then(a):a(l)},setItem:(r,o)=>n.setItem(r,JSON.stringify(o,void 0)),removeItem:r=>n.removeItem(r)}}const Ot=e=>t=>{try{const n=e(t);return n instanceof Promise?n:{then(i){return Ot(i)(n)},catch(i){return this}}}catch(n){return{then(i){return this},catch(i){return Ot(i)(n)}}}},Rr=(e,t)=>(n,i,r)=>{let o={storage:Gt(()=>window.localStorage),partialize:y=>y,version:0,merge:(y,j)=>({...j,...y}),...t},a=!1,l=0;const d=new Set,c=new Set;let p=o.storage;if(!p)return e((...y)=>{console.warn(`[zustand persist middleware] Unable to update item '${o.name}', the given storage is currently unavailable.`),n(...y)},i,r);const f=()=>{const y=o.partialize({...i()});return p.setItem(o.name,{state:y,version:o.version})},g=r.setState;r.setState=(y,j)=>(g(y,j),f());const x=e((...y)=>(n(...y),f()),i,r);r.getInitialState=()=>x;let h;const u=()=>{var y,j;if(!p)return;const w=++l;a=!1,d.forEach(b=>{var $;return b(($=i())!=null?$:x)});const N=((j=o.onRehydrateStorage)==null?void 0:j.call(o,(y=i())!=null?y:x))||void 0;return Ot(p.getItem.bind(p))(o.name).then(b=>{if(b)if(typeof b.version=="number"&&b.version!==o.version){if(o.migrate){const $=o.migrate(b.state,b.version);return $ instanceof Promise?$.then(S=>[!0,S]):[!0,$]}console.error("State loaded from storage couldn't be migrated since no migrate function was provided")}else return[!1,b.state];return[!1,void 0]}).then(b=>{var $;if(w!==l)return;const[S,C]=b;if(h=o.merge(C,($=i())!=null?$:x),n(h,!0),S)return f()}).then(()=>{w===l&&(N==null||N(i(),void 0),h=i(),a=!0,c.forEach(b=>b(h)))}).catch(b=>{w===l&&(N==null||N(void 0,b))})};return r.persist={setOptions:y=>{o={...o,...y},y.storage&&(p=y.storage)},clearStorage:()=>{p==null||p.removeItem(o.name)},getOptions:()=>o,rehydrate:()=>u(),hasHydrated:()=>a,onHydrate:y=>(d.add(y),()=>{d.delete(y)}),onFinishHydration:y=>(c.add(y),()=>{c.delete(y)})},o.skipHydration||u(),h||x},li=Rr,ht=[{accent:"#6c5ce7",dark:"#5a4bd1"},{accent:"#667eea",dark:"#536DFE"},{accent:"#e74c3c",dark:"#c0392b"},{accent:"#27ae60",dark:"#1e8449"},{accent:"#f39c12",dark:"#e67e22"},{accent:"#e84393",dark:"#d63384"},{accent:"#00b894",dark:"#00a381"},{accent:"#0984e3",dark:"#0769b5"},{accent:"#fd79a8",dark:"#e84393"},{accent:"#a29bfe",dark:"#6c5ce7"},{accent:"#888888",dark:"#666666"},{accent:"#000000",dark:"#1a1a1a"},{accent:"#1e3a5f",dark:"#0f2744"},{accent:"#722f37",dark:"#5a252c"},{accent:"#556B2F",dark:"#3d4f1f"}];function Or(e){const t=parseInt(e.slice(1,3),16),n=parseInt(e.slice(3,5),16),i=parseInt(e.slice(5,7),16);return`${t},${n},${i}`}function qe(e,t){return{accent:e,dark:t,light:e+"26",border:e+"33",rgb:Or(e)}}const D={white:"#ffffff",gray50:"#fafafe",gray100:"#f8fafc",gray200:"#eeeeee",gray250:"#e5e7eb",gray300:"#e2e8f0",gray350:"#dddddd",gray400:"#cbd5e1",gray500:"#94a3b8",gray600:"#64748b",gray700:"#475569",gray750:"#374151",gray800:"#334155",gray850:"#1e293b",gray900:"#1f2937",gray950:"#111827",gray1000:"#1a1a1a"},U={surface:D.white,borderDefault:D.gray300,inkStrong:D.gray900,ink:D.gray700,inkMuted:D.gray600,inkFaint:D.gray500,textPrimary:D.gray950,textTertiary:D.gray800,textQuaternary:D.gray750,errorBg:"#fef2f2",errorBorder:"#dc5050",errorText:"#781e1e"},E={"2xs":"10px",xs:"11px",sm:"12px",base:"13px",md:"14px",lg:"15px",xl:"16px","2xl":"17px","3xl":"18px","4xl":"20px","5xl":"22px","6xl":"24px","7xl":"28px","8xl":"30px","9xl":"34px","10xl":"40px","11xl":"48px","12xl":"60px"},m={0:"0px",1:"4px",2:"6px",3:"8px",4:"10px",5:"12px",6:"14px",7:"16px",8:"18px",9:"20px",10:"24px",11:"28px",12:"30px",13:"32px"},R={sm:"4px",md:"6px",lg:"8px",xl:"10px","2xl":"12px","3xl":"14px","4xl":"16px",full:"999px"},Ke={card:"rgba(15,23,42,0.05) 0px 10px 24px",cardHover:"rgba(15,23,42,0.08) 0px 12px 30px",float:"rgba(15,23,42,0.16) 0px 12px 24px"},X={tight:"1.2",snug:"1.35",normal:"1.4",relaxed:"1.5",loose:"1.6",looser:"1.7",loosest:"1.8",document:"1.85"},A={normal:"400",medium:"500",semibold:"600",bold:"700",extrabold:"800",black:"900"},K={tighter:"-1px",tight:"-0.5px",normal:"0",wide:"0.3px",wider:"0.5px",widest:"1px",xl:"1.2px","2xl":"2px","3xl":"2.4px","4xl":"2.8px","5xl":"3px"};function Ge(e){const t=(e==null?void 0:e.spacingMultiplier)??1;return{headingSizes:(e==null?void 0:e.headingSizes)??{1:E["6xl"],2:E["4xl"],3:E["2xl"],4:E.lg,5:E.base,6:E.sm},headingWeight:(e==null?void 0:e.headingWeight)??A.bold,headingColor:(e==null?void 0:e.headingColor)??"textPrimary",bodyFontSize:(e==null?void 0:e.bodyFontSize)??E.xl,bodyLineHeight:(e==null?void 0:e.bodyLineHeight)??X.document,quote:{borderRadius:(e==null?void 0:e.blockborderRadius)??R.lg,bg:(e==null?void 0:e.blockBg)??"transparent",border:(e==null?void 0:e.blockBorder)??"currentColor",accentText:(e==null?void 0:e.blockAccentText)??!1},radiusMap:(e==null?void 0:e.radiusMap)??{sm:R.sm,md:R.md,lg:R.lg,xl:R.xl,"2xl":R["2xl"],"3xl":R["3xl"],"4xl":R["4xl"]},spacingMultiplier:t}}const Dr=[{id:"minimal",name:"极简",description:"克制的留白与灰阶，适合长文阅读"},{id:"business",name:"商务",description:"稳重的蓝灰调，报告与提案首选"},{id:"tech",name:"科技",description:"高对比冷色调，代码感与现代感兼备"},{id:"editorial",name:"文艺编辑",description:"杂志级排版，衬线与暖色调"},{id:"warm",name:"温暖",description:"亲和力强的暖色调，生活与人文内容"},{id:"dark",name:"暗色",description:"深色基底，视觉冲击与沉浸感"}],ci=[{id:"default",name:"默认",category:"minimal",accent:"#27ae60",dark:"#1e8449",headingScale:1,headingWeight:700,headingColor:"textPrimary",bodySize:"15",spacingScale:1,radiusLevel:"default",quoteStyle:"border"},{id:"notion",name:"Notion",category:"minimal",accent:"#000000",dark:"#1a1a1a",headingScale:1,headingWeight:700,headingColor:"textPrimary",bodySize:"15",spacingScale:1,radiusLevel:"sharp",quoteStyle:"border"},{id:"stripe",name:"Stripe",category:"minimal",accent:"#635bff",dark:"#4f46e5",headingScale:1.15,headingWeight:700,headingColor:"textPrimary",bodySize:"16",spacingScale:1,radiusLevel:"default",quoteStyle:"bg"},{id:"linear",name:"Linear",category:"minimal",accent:"#5e6ad2",dark:"#4a55c7",headingScale:1,headingWeight:600,headingColor:"textPrimary",bodySize:"14",spacingScale:.8,radiusLevel:"sharp",quoteStyle:"border"},{id:"apple",name:"Apple",category:"minimal",accent:"#1d1d1f",dark:"#000000",headingScale:1.3,headingWeight:700,headingColor:"textPrimary",bodySize:"16",spacingScale:1.2,radiusLevel:"round",quoteStyle:"bg"},{id:"vercel",name:"Vercel",category:"minimal",accent:"#000000",dark:"#111111",headingScale:1.15,headingWeight:800,headingColor:"textPrimary",bodySize:"15",spacingScale:1,radiusLevel:"default",quoteStyle:"border"},{id:"figma",name:"Figma",category:"minimal",accent:"#7c3aed",dark:"#5b21b6",headingScale:1.15,headingWeight:700,headingColor:"textPrimary",bodySize:"15",spacingScale:1,radiusLevel:"default",quoteStyle:"border-bg"},{id:"github",name:"GitHub",category:"minimal",accent:"#0969da",dark:"#0550ae",headingScale:1,headingWeight:600,headingColor:"textPrimary",bodySize:"15",spacingScale:1,radiusLevel:"sharp",quoteStyle:"border"},{id:"bytedance",name:"字节跳动",category:"business",accent:"#3350ff",dark:"#1e3afa",headingScale:1.15,headingWeight:800,headingColor:"accent",bodySize:"15",spacingScale:1,radiusLevel:"default",quoteStyle:"border"},{id:"bytedance-pro",name:"BytePro",category:"business",accent:"#2563eb",dark:"#1d4ed8",headingScale:1,headingWeight:700,headingColor:"textPrimary",bodySize:"15",spacingScale:1,radiusLevel:"default",quoteStyle:"border-bg"},{id:"nike",name:"Nike",category:"business",accent:"#f97316",dark:"#ea580c",headingScale:1.3,headingWeight:900,headingColor:"textPrimary",bodySize:"16",spacingScale:1.2,radiusLevel:"round",quoteStyle:"bg"},{id:"cocacola",name:"Coca-Cola",category:"business",accent:"#dc2626",dark:"#b91c1c",headingScale:1.15,headingWeight:800,headingColor:"textPrimary",bodySize:"15",spacingScale:1,radiusLevel:"round",quoteStyle:"border"},{id:"spotify",name:"Spotify",category:"business",accent:"#1db954",dark:"#169c46",headingScale:1.3,headingWeight:800,headingColor:"accent",bodySize:"16",spacingScale:1.2,radiusLevel:"round",quoteStyle:"bg"},{id:"airbnb",name:"Airbnb",category:"business",accent:"#ff5a5f",dark:"#e04854",headingScale:1.15,headingWeight:700,headingColor:"textPrimary",bodySize:"16",spacingScale:1,radiusLevel:"default",quoteStyle:"bg"},{id:"salesforce",name:"Salesforce",category:"business",accent:"#00a1e0",dark:"#0084bd",headingScale:1,headingWeight:700,headingColor:"textPrimary",bodySize:"15",spacingScale:1,radiusLevel:"default",quoteStyle:"border"},{id:"goldman",name:"Goldman",category:"business",accent:"#1e3a5f",dark:"#0f2744",headingScale:1,headingWeight:700,headingColor:"textPrimary",bodySize:"15",spacingScale:1,radiusLevel:"sharp",quoteStyle:"border"},{id:"sspai",name:"少数派",category:"tech",accent:"#d63333",dark:"#b82a2a",headingScale:1.15,headingWeight:800,headingColor:"accent",bodySize:"15",spacingScale:1,radiusLevel:"default",quoteStyle:"border"},{id:"sspai-blue",name:"SSPai Blue",category:"tech",accent:"#2f6fed",dark:"#1d4fb8",headingScale:1,headingWeight:700,headingColor:"textPrimary",bodySize:"15",spacingScale:1,radiusLevel:"default",quoteStyle:"border-bg"},{id:"claude",name:"Claude",category:"tech",accent:"#cc785c",dark:"#a8604a",headingScale:1,headingWeight:700,headingColor:"textPrimary",bodySize:"15",spacingScale:1,radiusLevel:"default",quoteStyle:"border-bg"},{id:"openai",name:"OpenAI",category:"tech",accent:"#10a37f",dark:"#0d8c6d",headingScale:1,headingWeight:600,headingColor:"textPrimary",bodySize:"15",spacingScale:1,radiusLevel:"sharp",quoteStyle:"border"},{id:"dashboard",name:"Dashboard",category:"tech",accent:"#06b6d4",dark:"#0891b2",headingScale:1.15,headingWeight:700,headingColor:"accent",bodySize:"14",spacingScale:.8,radiusLevel:"sharp",quoteStyle:"border"},{id:"nvidia",name:"NVIDIA",category:"tech",accent:"#76b900",dark:"#659f00",headingScale:1.15,headingWeight:800,headingColor:"accent",bodySize:"15",spacingScale:1,radiusLevel:"sharp",quoteStyle:"border"},{id:"react",name:"React",category:"tech",accent:"#61dafb",dark:"#4fb8d9",headingScale:1,headingWeight:700,headingColor:"textPrimary",bodySize:"15",spacingScale:1,radiusLevel:"default",quoteStyle:"bg"},{id:"tailwind",name:"Tailwind",category:"tech",accent:"#06b6d4",dark:"#0891b2",headingScale:1,headingWeight:600,headingColor:"textPrimary",bodySize:"15",spacingScale:1,radiusLevel:"sharp",quoteStyle:"border"},{id:"elegant-gold",name:"优雅金",category:"editorial",accent:"#b8860b",dark:"#8b6508",headingScale:1.15,headingWeight:700,headingColor:"accent",bodySize:"16",spacingScale:1.2,radiusLevel:"round",quoteStyle:"border-bg"},{id:"elegant-green",name:"优雅绿",category:"editorial",accent:"#556b2f",dark:"#3d4f1f",headingScale:1.15,headingWeight:700,headingColor:"accent",bodySize:"16",spacingScale:1.2,radiusLevel:"round",quoteStyle:"border-bg"},{id:"elegant-purple",name:"优雅紫",category:"editorial",accent:"#722f37",dark:"#5a252c",headingScale:1.15,headingWeight:700,headingColor:"accent",bodySize:"16",spacingScale:1.2,radiusLevel:"round",quoteStyle:"border-bg"},{id:"clean-grey",name:"冷淡灰",category:"editorial",accent:"#64748b",dark:"#475569",headingScale:1,headingWeight:600,headingColor:"textPrimary",bodySize:"15",spacingScale:1,radiusLevel:"sharp",quoteStyle:"border"},{id:"wechat-native",name:"微信原生",category:"editorial",accent:"#07c160",dark:"#06ad56",headingScale:1,headingWeight:700,headingColor:"textPrimary",bodySize:"16",spacingScale:1,radiusLevel:"default",quoteStyle:"bg"},{id:"markdown",name:"Markdown",category:"editorial",accent:"#555555",dark:"#333333",headingScale:1,headingWeight:700,headingColor:"textPrimary",bodySize:"15",spacingScale:1,radiusLevel:"sharp",quoteStyle:"border"},{id:"zhihu",name:"知乎蓝",category:"editorial",accent:"#0066ff",dark:"#0052cc",headingScale:1,headingWeight:600,headingColor:"textPrimary",bodySize:"15",spacingScale:1,radiusLevel:"default",quoteStyle:"border"},{id:"juejin",name:"掘金蓝",category:"editorial",accent:"#1e80ff",dark:"#006fe6",headingScale:1,headingWeight:700,headingColor:"textPrimary",bodySize:"15",spacingScale:1,radiusLevel:"default",quoteStyle:"border"},{id:"coral-pink",name:"珊瑚粉",category:"warm",accent:"#ff6b6b",dark:"#e05555",headingScale:1.15,headingWeight:700,headingColor:"textPrimary",bodySize:"16",spacingScale:1,radiusLevel:"round",quoteStyle:"bg"},{id:"warm-yellow",name:"暖阳黄",category:"warm",accent:"#e67e22",dark:"#d35400",headingScale:1.15,headingWeight:700,headingColor:"textPrimary",bodySize:"16",spacingScale:1,radiusLevel:"round",quoteStyle:"bg"},{id:"soft-purple",name:"柔光紫",category:"warm",accent:"#a29bfe",dark:"#6c5ce7",headingScale:1.15,headingWeight:700,headingColor:"accent",bodySize:"16",spacingScale:1.2,radiusLevel:"round",quoteStyle:"bg"},{id:"vercel-gradient",name:"渐变橙",category:"warm",accent:"#f59e0b",dark:"#d97706",headingScale:1.3,headingWeight:800,headingColor:"textPrimary",bodySize:"16",spacingScale:1.2,radiusLevel:"round",quoteStyle:"bg"},{id:"lavender",name:"薰衣草",category:"warm",accent:"#b794f4",dark:"#9f7aea",headingScale:1.15,headingWeight:700,headingColor:"accent",bodySize:"16",spacingScale:1.2,radiusLevel:"round",quoteStyle:"bg"},{id:"matcha",name:"抹茶",category:"warm",accent:"#80b918",dark:"#6a9b14",headingScale:1,headingWeight:700,headingColor:"textPrimary",bodySize:"16",spacingScale:1,radiusLevel:"round",quoteStyle:"bg"},{id:"peach",name:"蜜桃",category:"warm",accent:"#fd79a8",dark:"#e84393",headingScale:1.15,headingWeight:700,headingColor:"textPrimary",bodySize:"16",spacingScale:1,radiusLevel:"round",quoteStyle:"bg"},{id:"sunset",name:"落日橙",category:"warm",accent:"#ff7849",dark:"#e56a3b",headingScale:1.15,headingWeight:800,headingColor:"accent",bodySize:"16",spacingScale:1,radiusLevel:"round",quoteStyle:"border-bg"},{id:"midnight",name:"午夜蓝",category:"dark",accent:"#3b82f6",dark:"#2563eb",headingScale:1.15,headingWeight:700,headingColor:"accent",bodySize:"15",spacingScale:1,radiusLevel:"default",quoteStyle:"border"},{id:"dark-purple",name:"暗夜紫",category:"dark",accent:"#8b5cf6",dark:"#7c3aed",headingScale:1.15,headingWeight:700,headingColor:"accent",bodySize:"15",spacingScale:1,radiusLevel:"default",quoteStyle:"border"},{id:"neon-green",name:"霓虹绿",category:"dark",accent:"#22c55e",dark:"#16a34a",headingScale:1.15,headingWeight:800,headingColor:"accent",bodySize:"15",spacingScale:1,radiusLevel:"default",quoteStyle:"border"},{id:"cyber-pink",name:"赛博粉",category:"dark",accent:"#ec4899",dark:"#db2777",headingScale:1.15,headingWeight:800,headingColor:"accent",bodySize:"15",spacingScale:1,radiusLevel:"default",quoteStyle:"border"},{id:"github-dark",name:"GitHub Dark",category:"dark",accent:"#58a6ff",dark:"#1f6feb",headingScale:1,headingWeight:600,headingColor:"accent",bodySize:"15",spacingScale:1,radiusLevel:"sharp",quoteStyle:"border"},{id:"discord",name:"Discord",category:"dark",accent:"#5865f2",dark:"#4752c4",headingScale:1,headingWeight:700,headingColor:"accent",bodySize:"15",spacingScale:1,radiusLevel:"default",quoteStyle:"bg"},{id:"amber-dark",name:"琥珀暗",category:"dark",accent:"#f59e0b",dark:"#d97706",headingScale:1.15,headingWeight:800,headingColor:"accent",bodySize:"15",spacingScale:1,radiusLevel:"default",quoteStyle:"border"},{id:"red-dark",name:"暗夜红",category:"dark",accent:"#ef4444",dark:"#dc2626",headingScale:1.15,headingWeight:800,headingColor:"accent",bodySize:"15",spacingScale:1,radiusLevel:"default",quoteStyle:"border"},{id:"moyu-green",name:"摸鱼绿",category:"tech",accent:"#059669",dark:"#047857",headingScale:1,headingWeight:700,headingColor:"accent",bodySize:"15",spacingScale:1,radiusLevel:"default",quoteStyle:"border"},{id:"graphite-minimal",name:"石墨极简",category:"minimal",accent:"#52525B",dark:"#3f3f46",headingScale:1,headingWeight:600,headingColor:"textPrimary",bodySize:"15",spacingScale:1,radiusLevel:"sharp",quoteStyle:"border"},{id:"zen-whitespace",name:"留白禅意",category:"minimal",accent:"#4A5D52",dark:"#3b4a42",headingScale:1,headingWeight:600,headingColor:"textPrimary",bodySize:"15",spacingScale:1,radiusLevel:"default",quoteStyle:"border"},{id:"olive-journal",name:"橄榄手记",category:"editorial",accent:"#1e1f23",dark:"#17181b",headingScale:1.15,headingWeight:700,headingColor:"accent",bodySize:"16",spacingScale:1.2,radiusLevel:"default",quoteStyle:"border"}],di=new Map(ci.map(e=>[e.id,e]));function Dt(e){return di.get(e)}function et(){return di.get("default")}const Pr={1:24,2:20,3:17,4:15,5:13,6:12},Fr={sharp:{sm:"2px",md:"3px",lg:"4px",xl:"5px","2xl":"6px","3xl":"8px","4xl":"8px"},default:{sm:"4px",md:"6px",lg:"8px",xl:"10px","2xl":"12px","3xl":"14px","4xl":"16px"},round:{sm:"6px",md:"10px",lg:"14px",xl:"18px","2xl":"22px","3xl":"26px","4xl":"32px"}};function Et(e){const t={};for(const l of[1,2,3,4,5,6])t[l]=`${Math.round(Pr[l]*e.headingScale)}px`;const n=Fr[e.radiusLevel];let i=n.lg,r="transparent",o="currentColor",a=!1;switch(e.quoteStyle){case"border":o="currentColor",r="transparent",i=n.lg;break;case"bg":r="currentColor",o="transparent",i=n.lg;break;case"border-bg":r="currentColor",o="currentColor",i=n.lg,a=!0;break}return{headingSizes:t,headingWeight:`${e.headingWeight}`,headingColor:e.headingColor,bodyFontSize:`${e.bodySize}px`,bodyLineHeight:e.bodySize==="16"?"1.85":"1.8",blockborderRadius:i,blockBg:r,blockBorder:o,blockAccentText:a,radiusMap:n,spacingMultiplier:e.spacingScale}}function V(e){return String(e).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;").replace(/'/g,"&#39;")}function _(e){const t=fi(String(e));return t.includes(`
`)?t.split(`
`).map(n=>`<span leaf="">${n.trim()}</span>`).join("<br>"):`<span leaf="">${t}</span>`}const pi="\\u4e00-\\u9fff\\u3040-\\u30ff\\u3400-\\u4dbf\\uf900-\\ufaff",Br=new RegExp(`([${pi}])([A-Za-z0-9])`,"g"),Hr=new RegExp(`([A-Za-z0-9])([${pi}])`,"g");function fi(e){return e&&e.replace(Br,"$1 $2").replace(Hr,"$1 $2")}function Ur(e,t){const n=parseInt(e.slice(1,3),16),i=parseInt(e.slice(3,5),16),r=parseInt(e.slice(5,7),16),o=Math.round(n+(255-n)*t),a=Math.round(i+(255-i)*t),l=Math.round(r+(255-r)*t);return"#"+((1<<24)+(o<<16)+(a<<8)+l).toString(16).slice(1)}function gi(e,t=.06){if(/^rgba?\(/.test(e))return e.replace(/rgba?\(([^)]+)\)/,(n,i)=>{const r=i.split(",").map(o=>o.trim());return r.length>=3?`rgba(${r[0]},${r[1]},${r[2]},${t})`:e});if(e.startsWith("#")){const n=e.length===4?"#"+e[1]+e[1]+e[2]+e[2]+e[3]+e[3]:e,i=parseInt(n.slice(1,3),16),r=parseInt(n.slice(3,5),16),o=parseInt(n.slice(5,7),16);return`rgba(${i},${r},${o},${t})`}if(typeof document<"u"){const n=document.createElement("canvas").getContext("2d");if(n){n.fillStyle=e;const i=n.fillStyle;if(i.startsWith("#")){const r=parseInt(i.slice(1,3),16),o=parseInt(i.slice(3,5),16),a=parseInt(i.slice(5,7),16);return`rgba(${r},${o},${a},${t})`}}}return`color-mix(in srgb, ${e} ${Math.round(t*100)}%, transparent)`}function dt(e,t="src"){if(!e)return e;const n=e.trim();if(n.startsWith("/")||n.startsWith("#")||n.startsWith(".")||t==="href"&&(/^mailto:/i.test(n)||/^tel:/i.test(n)))return n;try{const i=new URL(n).protocol.toLowerCase();if(i==="http:"||i==="https:")return n;if(t==="src"){if(i==="data:")return/^data:image\//.test(n)||/^data:font\//.test(n)?n:"about:blank";if(i==="img:")return n}}catch{if(!n.includes(":"))return n}return"about:blank"}function be(e){const t={};return e.replace(/([\w-]+)="([^"]*)"|([\w-]+)=([\w-]+)|([\w-]+)/g,(n,i,r,o,a,l)=>(i?t[i]=r:o?t[o]=a:l&&(t[l]="true"),"")),t}const kn="",$n="";function Sn(e,t){try{return Cr.renderToString(e.trim(),{displayMode:t,throwOnError:!1,output:"html"})}catch{return t?`$$${e}$$`:`$${e}$`}}function qr(e){const t={inline:new Map,block:new Map};let n=0,i=0,r=e.replace(/\$\$([\s\S]+?)\$\$/g,(o,a)=>{const l=`${kn}B${n++}${$n}`,d=Sn(a,!0);return t.block.set(l,`<section style="text-align:center;margin:18px 0;overflow-x:auto">${d}</section>`),l});return r=r.replace(new RegExp("\\$(?!\\s)([\\s\\S]+?)(?<!\\s)\\$(?!\\$)","g"),(o,a)=>{const l=`${kn}I${i++}${$n}`;return t.inline.set(l,Sn(a,!1)),l}),{text:r,store:t}}function Wr(e,t){let n=e;for(const[i,r]of t.block)n=n.split(`<p>${i}</p>`).join(r).split(i).join(r);for(const[i,r]of t.inline)n=n.split(i).join(r);return n}function Gr(e){const t=e.regex,n={},i={begin:/\$\{/,end:/\}/,contains:["self",{begin:/:-/,contains:[n]}]};Object.assign(n,{className:"variable",variants:[{begin:t.concat(/\$[\w\d#@][\w\d_]*/,"(?![\\w\\d])(?![$])")},i]});const r={className:"subst",begin:/\$\(/,end:/\)/,contains:[e.BACKSLASH_ESCAPE]},o=e.inherit(e.COMMENT(),{match:[/(^|\s)/,/#.*$/],scope:{2:"comment"}}),a={begin:/<<-?\s*(?=\w+)/,starts:{contains:[e.END_SAME_AS_BEGIN({begin:/(\w+)/,end:/(\w+)/,className:"string"})]}},l={className:"string",begin:/"/,end:/"/,contains:[e.BACKSLASH_ESCAPE,n,r]};r.contains.push(l);const d={match:/\\"/},c={className:"string",begin:/'/,end:/'/},p={match:/\\'/},f={begin:/\$?\(\(/,end:/\)\)/,contains:[{begin:/\d+#[0-9a-f]+/,className:"number"},e.NUMBER_MODE,n]},g=["fish","bash","zsh","sh","csh","ksh","tcsh","dash","scsh"],x=e.SHEBANG({binary:`(${g.join("|")})`,relevance:10}),h={className:"function",begin:/\w[\w\d_]*\s*\(\s*\)\s*\{/,returnBegin:!0,contains:[e.inherit(e.TITLE_MODE,{begin:/\w[\w\d_]*/})],relevance:0},u=["if","then","else","elif","fi","time","for","while","until","in","do","done","case","esac","coproc","function","select"],y=["true","false"],j={match:/(\/[a-z._-]+)+/},w=["break","cd","continue","eval","exec","exit","export","getopts","hash","pwd","readonly","return","shift","test","times","trap","umask","unset"],N=["alias","bind","builtin","caller","command","declare","echo","enable","help","let","local","logout","mapfile","printf","read","readarray","source","sudo","type","typeset","ulimit","unalias"],b=["autoload","bg","bindkey","bye","cap","chdir","clone","comparguments","compcall","compctl","compdescribe","compfiles","compgroups","compquote","comptags","comptry","compvalues","dirs","disable","disown","echotc","echoti","emulate","fc","fg","float","functions","getcap","getln","history","integer","jobs","kill","limit","log","noglob","popd","print","pushd","pushln","rehash","sched","setcap","setopt","stat","suspend","ttyctl","unfunction","unhash","unlimit","unsetopt","vared","wait","whence","where","which","zcompile","zformat","zftp","zle","zmodload","zparseopts","zprof","zpty","zregexparse","zsocket","zstyle","ztcp"],$=["chcon","chgrp","chown","chmod","cp","dd","df","dir","dircolors","ln","ls","mkdir","mkfifo","mknod","mktemp","mv","realpath","rm","rmdir","shred","sync","touch","truncate","vdir","b2sum","base32","base64","cat","cksum","comm","csplit","cut","expand","fmt","fold","head","join","md5sum","nl","numfmt","od","paste","ptx","pr","sha1sum","sha224sum","sha256sum","sha384sum","sha512sum","shuf","sort","split","sum","tac","tail","tr","tsort","unexpand","uniq","wc","arch","basename","chroot","date","dirname","du","echo","env","expr","factor","groups","hostid","id","link","logname","nice","nohup","nproc","pathchk","pinky","printenv","printf","pwd","readlink","runcon","seq","sleep","stat","stdbuf","stty","tee","test","timeout","tty","uname","unlink","uptime","users","who","whoami","yes"];return{name:"Bash",aliases:["sh","zsh"],keywords:{$pattern:/\b[a-z][a-z0-9._-]+\b/,keyword:u,literal:y,built_in:[...w,...N,"set","shopt",...b,...$]},contains:[x,e.SHEBANG(),h,f,o,a,j,l,d,c,p,n]}}function Kr(e){const t=e.regex,n=e.COMMENT("//","$",{contains:[{begin:/\\\n/}]}),i="decltype\\(auto\\)",r="[a-zA-Z_]\\w*::",a="(?!struct)("+i+"|"+t.optional(r)+"[a-zA-Z_]\\w*"+t.optional("<[^<>]+>")+")",l={className:"type",begin:"\\b[a-z\\d_]*_t\\b"},c={className:"string",variants:[{begin:'(u8?|U|L)?"',end:'"',illegal:"\\n",contains:[e.BACKSLASH_ESCAPE]},{begin:"(u8?|U|L)?'("+"\\\\(x[0-9A-Fa-f]{2}|u[0-9A-Fa-f]{4,8}|[0-7]{3}|\\S)"+"|.)",end:"'",illegal:"."},e.END_SAME_AS_BEGIN({begin:/(?:u8?|U|L)?R"([^()\\ ]{0,16})\(/,end:/\)([^()\\ ]{0,16})"/})]},p={className:"number",variants:[{begin:"[+-]?(?:(?:[0-9](?:'?[0-9])*\\.(?:[0-9](?:'?[0-9])*)?|\\.[0-9](?:'?[0-9])*)(?:[Ee][+-]?[0-9](?:'?[0-9])*)?|[0-9](?:'?[0-9])*[Ee][+-]?[0-9](?:'?[0-9])*|0[Xx](?:[0-9A-Fa-f](?:'?[0-9A-Fa-f])*(?:\\.(?:[0-9A-Fa-f](?:'?[0-9A-Fa-f])*)?)?|\\.[0-9A-Fa-f](?:'?[0-9A-Fa-f])*)[Pp][+-]?[0-9](?:'?[0-9])*)(?:[Ff](?:16|32|64|128)?|(BF|bf)16|[Ll]|)"},{begin:"[+-]?\\b(?:0[Bb][01](?:'?[01])*|0[Xx][0-9A-Fa-f](?:'?[0-9A-Fa-f])*|0(?:'?[0-7])*|[1-9](?:'?[0-9])*)(?:[Uu](?:LL?|ll?)|[Uu][Zz]?|(?:LL?|ll?)[Uu]?|[Zz][Uu]|)"}],relevance:0},f={className:"meta",begin:/#\s*[a-z]+\b/,end:/$/,keywords:{keyword:"if else elif endif define undef warning error line pragma _Pragma ifdef ifndef include"},contains:[{begin:/\\\n/,relevance:0},e.inherit(c,{className:"string"}),{className:"string",begin:/<.*?>/},n,e.C_BLOCK_COMMENT_MODE]},g={className:"title",begin:t.optional(r)+e.IDENT_RE,relevance:0},x=t.optional(r)+e.IDENT_RE+"\\s*\\(",h=["alignas","alignof","and","and_eq","asm","atomic_cancel","atomic_commit","atomic_noexcept","auto","bitand","bitor","break","case","catch","class","co_await","co_return","co_yield","compl","concept","const_cast|10","consteval","constexpr","constinit","continue","decltype","default","delete","do","dynamic_cast|10","else","enum","explicit","export","extern","false","final","for","friend","goto","if","import","inline","module","mutable","namespace","new","noexcept","not","not_eq","nullptr","operator","or","or_eq","override","private","protected","public","reflexpr","register","reinterpret_cast|10","requires","return","sizeof","static_assert","static_cast|10","struct","switch","synchronized","template","this","thread_local","throw","transaction_safe","transaction_safe_dynamic","true","try","typedef","typeid","typename","union","using","virtual","volatile","while","xor","xor_eq"],u=["bool","char","char16_t","char32_t","char8_t","double","float","int","long","short","void","wchar_t","unsigned","signed","const","static"],y=["any","auto_ptr","barrier","binary_semaphore","bitset","complex","condition_variable","condition_variable_any","counting_semaphore","deque","false_type","flat_map","flat_set","future","imaginary","initializer_list","istringstream","jthread","latch","lock_guard","multimap","multiset","mutex","optional","ostringstream","packaged_task","pair","promise","priority_queue","queue","recursive_mutex","recursive_timed_mutex","scoped_lock","set","shared_future","shared_lock","shared_mutex","shared_timed_mutex","shared_ptr","stack","string_view","stringstream","timed_mutex","thread","true_type","tuple","unique_lock","unique_ptr","unordered_map","unordered_multimap","unordered_multiset","unordered_set","variant","vector","weak_ptr","wstring","wstring_view"],j=["abort","abs","acos","apply","as_const","asin","atan","atan2","calloc","ceil","cerr","cin","clog","cos","cosh","cout","declval","endl","exchange","exit","exp","fabs","floor","fmod","forward","fprintf","fputs","free","frexp","fscanf","future","invoke","isalnum","isalpha","iscntrl","isdigit","isgraph","islower","isprint","ispunct","isspace","isupper","isxdigit","labs","launder","ldexp","log","log10","make_pair","make_shared","make_shared_for_overwrite","make_tuple","make_unique","malloc","memchr","memcmp","memcpy","memset","modf","move","pow","printf","putchar","puts","realloc","scanf","sin","sinh","snprintf","sprintf","sqrt","sscanf","std","stderr","stdin","stdout","strcat","strchr","strcmp","strcpy","strcspn","strlen","strncat","strncmp","strncpy","strpbrk","strrchr","strspn","strstr","swap","tan","tanh","terminate","to_underlying","tolower","toupper","vfprintf","visit","vprintf","vsprintf"],b={type:u,keyword:h,literal:["NULL","false","nullopt","nullptr","true"],built_in:["_Pragma"],_type_hints:y},$={className:"function.dispatch",relevance:0,keywords:{_hint:j},begin:t.concat(/\b/,/(?!decltype)/,/(?!if)/,/(?!for)/,/(?!switch)/,/(?!while)/,e.IDENT_RE,t.lookahead(/(<[^<>]+>|)\s*\(/))},S=[$,f,l,n,e.C_BLOCK_COMMENT_MODE,p,c],C={variants:[{begin:/=/,end:/;/},{begin:/\(/,end:/\)/},{beginKeywords:"new throw return else",end:/;/}],keywords:b,contains:S.concat([{begin:/\(/,end:/\)/,keywords:b,contains:S.concat(["self"]),relevance:0}]),relevance:0},O={className:"function",begin:"("+a+"[\\*&\\s]+)+"+x,returnBegin:!0,end:/[{;=]/,excludeEnd:!0,keywords:b,illegal:/[^\w\s\*&:<>.]/,contains:[{begin:i,keywords:b,relevance:0},{begin:x,returnBegin:!0,contains:[g],relevance:0},{begin:/::/,relevance:0},{begin:/:/,endsWithParent:!0,contains:[c,p]},{relevance:0,match:/,/},{className:"params",begin:/\(/,end:/\)/,keywords:b,relevance:0,contains:[n,e.C_BLOCK_COMMENT_MODE,c,p,l,{begin:/\(/,end:/\)/,keywords:b,relevance:0,contains:["self",n,e.C_BLOCK_COMMENT_MODE,c,p,l]}]},l,n,e.C_BLOCK_COMMENT_MODE,f]};return{name:"C++",aliases:["cc","c++","h++","hpp","hh","hxx","cxx"],keywords:b,illegal:"</",classNameAliases:{"function.dispatch":"built_in"},contains:[].concat(C,O,$,S,[f,{begin:"\\b(deque|list|queue|priority_queue|pair|stack|vector|map|set|bitset|multiset|multimap|unordered_map|unordered_set|unordered_multiset|unordered_multimap|array|tuple|optional|variant|function|flat_map|flat_set)\\s*<(?!<)",end:">",keywords:b,contains:["self",l]},{begin:e.IDENT_RE+"::",keywords:b},{match:[/\b(?:enum(?:\s+(?:class|struct))?|class|struct|union)/,/\s+/,/\w+/],className:{1:"keyword",3:"title.class"}}])}}const Vr=e=>({IMPORTANT:{scope:"meta",begin:"!important"},BLOCK_COMMENT:e.C_BLOCK_COMMENT_MODE,HEXCOLOR:{scope:"number",begin:/#(([0-9a-fA-F]{3,4})|(([0-9a-fA-F]{2}){3,4}))\b/},FUNCTION_DISPATCH:{className:"built_in",begin:/[\w-]+(?=\()/},ATTRIBUTE_SELECTOR_MODE:{scope:"selector-attr",begin:/\[/,end:/\]/,illegal:"$",contains:[e.APOS_STRING_MODE,e.QUOTE_STRING_MODE]},CSS_NUMBER_MODE:{scope:"number",begin:e.NUMBER_RE+"(%|em|ex|ch|rem|vw|vh|vmin|vmax|cm|mm|in|pt|pc|px|deg|grad|rad|turn|s|ms|Hz|kHz|dpi|dpcm|dppx)?",relevance:0},CSS_VARIABLE:{className:"attr",begin:/--[A-Za-z_][A-Za-z0-9_-]*/}}),Yr=["a","abbr","address","article","aside","audio","b","blockquote","body","button","canvas","caption","cite","code","dd","del","details","dfn","div","dl","dt","em","fieldset","figcaption","figure","footer","form","h1","h2","h3","h4","h5","h6","header","hgroup","html","i","iframe","img","input","ins","kbd","label","legend","li","main","mark","menu","nav","object","ol","optgroup","option","p","picture","q","quote","samp","section","select","source","span","strong","summary","sup","table","tbody","td","textarea","tfoot","th","thead","time","tr","ul","var","video"],Xr=["defs","g","marker","mask","pattern","svg","switch","symbol","feBlend","feColorMatrix","feComponentTransfer","feComposite","feConvolveMatrix","feDiffuseLighting","feDisplacementMap","feFlood","feGaussianBlur","feImage","feMerge","feMorphology","feOffset","feSpecularLighting","feTile","feTurbulence","linearGradient","radialGradient","stop","circle","ellipse","image","line","path","polygon","polyline","rect","text","use","textPath","tspan","foreignObject","clipPath"],Zr=[...Yr,...Xr],Jr=["any-hover","any-pointer","aspect-ratio","color","color-gamut","color-index","device-aspect-ratio","device-height","device-width","display-mode","forced-colors","grid","height","hover","inverted-colors","monochrome","orientation","overflow-block","overflow-inline","pointer","prefers-color-scheme","prefers-contrast","prefers-reduced-motion","prefers-reduced-transparency","resolution","scan","scripting","update","width","min-width","max-width","min-height","max-height"].sort().reverse(),Qr=["active","any-link","blank","checked","current","default","defined","dir","disabled","drop","empty","enabled","first","first-child","first-of-type","fullscreen","future","focus","focus-visible","focus-within","has","host","host-context","hover","indeterminate","in-range","invalid","is","lang","last-child","last-of-type","left","link","local-link","not","nth-child","nth-col","nth-last-child","nth-last-col","nth-last-of-type","nth-of-type","only-child","only-of-type","optional","out-of-range","past","placeholder-shown","read-only","read-write","required","right","root","scope","target","target-within","user-invalid","valid","visited","where"].sort().reverse(),es=["after","backdrop","before","cue","cue-region","first-letter","first-line","grammar-error","marker","part","placeholder","selection","slotted","spelling-error"].sort().reverse(),ts=["accent-color","align-content","align-items","align-self","alignment-baseline","all","anchor-name","animation","animation-composition","animation-delay","animation-direction","animation-duration","animation-fill-mode","animation-iteration-count","animation-name","animation-play-state","animation-range","animation-range-end","animation-range-start","animation-timeline","animation-timing-function","appearance","aspect-ratio","backdrop-filter","backface-visibility","background","background-attachment","background-blend-mode","background-clip","background-color","background-image","background-origin","background-position","background-position-x","background-position-y","background-repeat","background-size","baseline-shift","block-size","border","border-block","border-block-color","border-block-end","border-block-end-color","border-block-end-style","border-block-end-width","border-block-start","border-block-start-color","border-block-start-style","border-block-start-width","border-block-style","border-block-width","border-bottom","border-bottom-color","border-bottom-left-radius","border-bottom-right-radius","border-bottom-style","border-bottom-width","border-collapse","border-color","border-end-end-radius","border-end-start-radius","border-image","border-image-outset","border-image-repeat","border-image-slice","border-image-source","border-image-width","border-inline","border-inline-color","border-inline-end","border-inline-end-color","border-inline-end-style","border-inline-end-width","border-inline-start","border-inline-start-color","border-inline-start-style","border-inline-start-width","border-inline-style","border-inline-width","border-left","border-left-color","border-left-style","border-left-width","border-radius","border-right","border-right-color","border-right-style","border-right-width","border-spacing","border-start-end-radius","border-start-start-radius","border-style","border-top","border-top-color","border-top-left-radius","border-top-right-radius","border-top-style","border-top-width","border-width","bottom","box-align","box-decoration-break","box-direction","box-flex","box-flex-group","box-lines","box-ordinal-group","box-orient","box-pack","box-shadow","box-sizing","break-after","break-before","break-inside","caption-side","caret-color","clear","clip","clip-path","clip-rule","color","color-interpolation","color-interpolation-filters","color-profile","color-rendering","color-scheme","column-count","column-fill","column-gap","column-rule","column-rule-color","column-rule-style","column-rule-width","column-span","column-width","columns","contain","contain-intrinsic-block-size","contain-intrinsic-height","contain-intrinsic-inline-size","contain-intrinsic-size","contain-intrinsic-width","container","container-name","container-type","content","content-visibility","counter-increment","counter-reset","counter-set","cue","cue-after","cue-before","cursor","cx","cy","direction","display","dominant-baseline","empty-cells","enable-background","field-sizing","fill","fill-opacity","fill-rule","filter","flex","flex-basis","flex-direction","flex-flow","flex-grow","flex-shrink","flex-wrap","float","flood-color","flood-opacity","flow","font","font-display","font-family","font-feature-settings","font-kerning","font-language-override","font-optical-sizing","font-palette","font-size","font-size-adjust","font-smooth","font-smoothing","font-stretch","font-style","font-synthesis","font-synthesis-position","font-synthesis-small-caps","font-synthesis-style","font-synthesis-weight","font-variant","font-variant-alternates","font-variant-caps","font-variant-east-asian","font-variant-emoji","font-variant-ligatures","font-variant-numeric","font-variant-position","font-variation-settings","font-weight","forced-color-adjust","gap","glyph-orientation-horizontal","glyph-orientation-vertical","grid","grid-area","grid-auto-columns","grid-auto-flow","grid-auto-rows","grid-column","grid-column-end","grid-column-start","grid-gap","grid-row","grid-row-end","grid-row-start","grid-template","grid-template-areas","grid-template-columns","grid-template-rows","hanging-punctuation","height","hyphenate-character","hyphenate-limit-chars","hyphens","icon","image-orientation","image-rendering","image-resolution","ime-mode","initial-letter","initial-letter-align","inline-size","inset","inset-area","inset-block","inset-block-end","inset-block-start","inset-inline","inset-inline-end","inset-inline-start","isolation","justify-content","justify-items","justify-self","kerning","left","letter-spacing","lighting-color","line-break","line-height","line-height-step","list-style","list-style-image","list-style-position","list-style-type","margin","margin-block","margin-block-end","margin-block-start","margin-bottom","margin-inline","margin-inline-end","margin-inline-start","margin-left","margin-right","margin-top","margin-trim","marker","marker-end","marker-mid","marker-start","marks","mask","mask-border","mask-border-mode","mask-border-outset","mask-border-repeat","mask-border-slice","mask-border-source","mask-border-width","mask-clip","mask-composite","mask-image","mask-mode","mask-origin","mask-position","mask-repeat","mask-size","mask-type","masonry-auto-flow","math-depth","math-shift","math-style","max-block-size","max-height","max-inline-size","max-width","min-block-size","min-height","min-inline-size","min-width","mix-blend-mode","nav-down","nav-index","nav-left","nav-right","nav-up","none","normal","object-fit","object-position","offset","offset-anchor","offset-distance","offset-path","offset-position","offset-rotate","opacity","order","orphans","outline","outline-color","outline-offset","outline-style","outline-width","overflow","overflow-anchor","overflow-block","overflow-clip-margin","overflow-inline","overflow-wrap","overflow-x","overflow-y","overlay","overscroll-behavior","overscroll-behavior-block","overscroll-behavior-inline","overscroll-behavior-x","overscroll-behavior-y","padding","padding-block","padding-block-end","padding-block-start","padding-bottom","padding-inline","padding-inline-end","padding-inline-start","padding-left","padding-right","padding-top","page","page-break-after","page-break-before","page-break-inside","paint-order","pause","pause-after","pause-before","perspective","perspective-origin","place-content","place-items","place-self","pointer-events","position","position-anchor","position-visibility","print-color-adjust","quotes","r","resize","rest","rest-after","rest-before","right","rotate","row-gap","ruby-align","ruby-position","scale","scroll-behavior","scroll-margin","scroll-margin-block","scroll-margin-block-end","scroll-margin-block-start","scroll-margin-bottom","scroll-margin-inline","scroll-margin-inline-end","scroll-margin-inline-start","scroll-margin-left","scroll-margin-right","scroll-margin-top","scroll-padding","scroll-padding-block","scroll-padding-block-end","scroll-padding-block-start","scroll-padding-bottom","scroll-padding-inline","scroll-padding-inline-end","scroll-padding-inline-start","scroll-padding-left","scroll-padding-right","scroll-padding-top","scroll-snap-align","scroll-snap-stop","scroll-snap-type","scroll-timeline","scroll-timeline-axis","scroll-timeline-name","scrollbar-color","scrollbar-gutter","scrollbar-width","shape-image-threshold","shape-margin","shape-outside","shape-rendering","speak","speak-as","src","stop-color","stop-opacity","stroke","stroke-dasharray","stroke-dashoffset","stroke-linecap","stroke-linejoin","stroke-miterlimit","stroke-opacity","stroke-width","tab-size","table-layout","text-align","text-align-all","text-align-last","text-anchor","text-combine-upright","text-decoration","text-decoration-color","text-decoration-line","text-decoration-skip","text-decoration-skip-ink","text-decoration-style","text-decoration-thickness","text-emphasis","text-emphasis-color","text-emphasis-position","text-emphasis-style","text-indent","text-justify","text-orientation","text-overflow","text-rendering","text-shadow","text-size-adjust","text-transform","text-underline-offset","text-underline-position","text-wrap","text-wrap-mode","text-wrap-style","timeline-scope","top","touch-action","transform","transform-box","transform-origin","transform-style","transition","transition-behavior","transition-delay","transition-duration","transition-property","transition-timing-function","translate","unicode-bidi","user-modify","user-select","vector-effect","vertical-align","view-timeline","view-timeline-axis","view-timeline-inset","view-timeline-name","view-transition-name","visibility","voice-balance","voice-duration","voice-family","voice-pitch","voice-range","voice-rate","voice-stress","voice-volume","white-space","white-space-collapse","widows","width","will-change","word-break","word-spacing","word-wrap","writing-mode","x","y","z-index","zoom"].sort().reverse();function ns(e){const t=e.regex,n=Vr(e),i={begin:/-(webkit|moz|ms|o)-(?=[a-z])/},r="and or not only",o=/@-?\w[\w]*(-\w+)*/,a="[a-zA-Z-][a-zA-Z0-9_-]*",l=[e.APOS_STRING_MODE,e.QUOTE_STRING_MODE];return{name:"CSS",case_insensitive:!0,illegal:/[=|'\$]/,keywords:{keyframePosition:"from to"},classNameAliases:{keyframePosition:"selector-tag"},contains:[n.BLOCK_COMMENT,i,n.CSS_NUMBER_MODE,{className:"selector-id",begin:/#[A-Za-z0-9_-]+/,relevance:0},{className:"selector-class",begin:"\\."+a,relevance:0},n.ATTRIBUTE_SELECTOR_MODE,{className:"selector-pseudo",variants:[{begin:":("+Qr.join("|")+")"},{begin:":(:)?("+es.join("|")+")"}]},n.CSS_VARIABLE,{className:"attribute",begin:"\\b("+ts.join("|")+")\\b"},{begin:/:/,end:/[;}{]/,contains:[n.BLOCK_COMMENT,n.HEXCOLOR,n.IMPORTANT,n.CSS_NUMBER_MODE,...l,{begin:/(url|data-uri)\(/,end:/\)/,relevance:0,keywords:{built_in:"url data-uri"},contains:[...l,{className:"string",begin:/[^)]/,endsWithParent:!0,excludeEnd:!0}]},n.FUNCTION_DISPATCH]},{begin:t.lookahead(/@/),end:"[{;]",relevance:0,illegal:/:/,contains:[{className:"keyword",begin:o},{begin:/\s/,endsWithParent:!0,excludeEnd:!0,relevance:0,keywords:{$pattern:/[a-z-]+/,keyword:r,attribute:Jr.join(" ")},contains:[{begin:/[a-z-]+(?=:)/,className:"attribute"},...l,n.CSS_NUMBER_MODE]}]},{className:"selector-tag",begin:"\\b("+Zr.join("|")+")\\b"}]}}function is(e){const o={keyword:["break","case","chan","const","continue","default","defer","else","fallthrough","for","func","go","goto","if","import","interface","map","package","range","return","select","struct","switch","type","var"],type:["bool","byte","complex64","complex128","error","float32","float64","int8","int16","int32","int64","string","uint8","uint16","uint32","uint64","int","uint","uintptr","rune"],literal:["true","false","iota","nil"],built_in:["append","cap","close","complex","copy","imag","len","make","new","panic","print","println","real","recover","delete"]};return{name:"Go",aliases:["golang"],keywords:o,illegal:"</",contains:[e.C_LINE_COMMENT_MODE,e.C_BLOCK_COMMENT_MODE,{className:"string",variants:[e.QUOTE_STRING_MODE,e.APOS_STRING_MODE,{begin:"`",end:"`"}]},{className:"number",variants:[{match:/-?\b0[xX]\.[a-fA-F0-9](_?[a-fA-F0-9])*[pP][+-]?\d(_?\d)*i?/,relevance:0},{match:/-?\b0[xX](_?[a-fA-F0-9])+((\.([a-fA-F0-9](_?[a-fA-F0-9])*)?)?[pP][+-]?\d(_?\d)*)?i?/,relevance:0},{match:/-?\b0[oO](_?[0-7])*i?/,relevance:0},{match:/-?\.\d(_?\d)*([eE][+-]?\d(_?\d)*)?i?/,relevance:0},{match:/-?\b\d(_?\d)*(\.(\d(_?\d)*)?)?([eE][+-]?\d(_?\d)*)?i?/,relevance:0}]},{begin:/:=/},{className:"function",beginKeywords:"func",end:"\\s*(\\{|$)",excludeEnd:!0,contains:[e.TITLE_MODE,{className:"params",begin:/\(/,end:/\)/,endsParent:!0,keywords:o,illegal:/["']/}]}]}}var Ie="[0-9](_*[0-9])*",tt=`\\.(${Ie})`,nt="[0-9a-fA-F](_*[0-9a-fA-F])*",jn={className:"number",variants:[{begin:`(\\b(${Ie})((${tt})|\\.)?|(${tt}))[eE][+-]?(${Ie})[fFdD]?\\b`},{begin:`\\b(${Ie})((${tt})[fFdD]?\\b|\\.([fFdD]\\b)?)`},{begin:`(${tt})[fFdD]?\\b`},{begin:`\\b(${Ie})[fFdD]\\b`},{begin:`\\b0[xX]((${nt})\\.?|(${nt})?\\.(${nt}))[pP][+-]?(${Ie})[fFdD]?\\b`},{begin:"\\b(0|[1-9](_*[0-9])*)[lL]?\\b"},{begin:`\\b0[xX](${nt})[lL]?\\b`},{begin:"\\b0(_*[0-7])*[lL]?\\b"},{begin:"\\b0[bB][01](_*[01])*[lL]?\\b"}],relevance:0};function xi(e,t,n){return n===-1?"":e.replace(t,i=>xi(e,t,n-1))}function rs(e){const t=e.regex,n="[À-ʸa-zA-Z_$][À-ʸa-zA-Z_$0-9]*",i=n+xi("(?:<"+n+"~~~(?:\\s*,\\s*"+n+"~~~)*>)?",/~~~/g,2),d={keyword:["synchronized","abstract","private","var","static","if","const ","for","while","strictfp","finally","protected","import","native","final","void","enum","else","break","transient","catch","instanceof","volatile","case","assert","package","default","public","try","switch","continue","throws","protected","public","private","module","requires","exports","do","sealed","yield","permits","goto","when"],literal:["false","true","null"],type:["char","boolean","long","float","int","byte","short","double"],built_in:["super","this"]},c={className:"meta",begin:"@"+n,contains:[{begin:/\(/,end:/\)/,contains:["self"]}]},p={className:"params",begin:/\(/,end:/\)/,keywords:d,relevance:0,contains:[e.C_BLOCK_COMMENT_MODE],endsParent:!0};return{name:"Java",aliases:["jsp"],keywords:d,illegal:/<\/|#/,contains:[e.COMMENT("/\\*\\*","\\*/",{relevance:0,contains:[{begin:/\w+@/,relevance:0},{className:"doctag",begin:"@[A-Za-z]+"}]}),{begin:/import java\.[a-z]+\./,keywords:"import",relevance:2},e.C_LINE_COMMENT_MODE,e.C_BLOCK_COMMENT_MODE,{begin:/"""/,end:/"""/,className:"string",contains:[e.BACKSLASH_ESCAPE]},e.APOS_STRING_MODE,e.QUOTE_STRING_MODE,{match:[/\b(?:class|interface|enum|extends|implements|new)/,/\s+/,n],className:{1:"keyword",3:"title.class"}},{match:/non-sealed/,scope:"keyword"},{begin:[t.concat(/(?!else)/,n),/\s+/,n,/\s+/,/=(?!=)/],className:{1:"type",3:"variable",5:"operator"}},{begin:[/record/,/\s+/,n],className:{1:"keyword",3:"title.class"},contains:[p,e.C_LINE_COMMENT_MODE,e.C_BLOCK_COMMENT_MODE]},{beginKeywords:"new throw return else",relevance:0},{begin:["(?:"+i+"\\s+)",e.UNDERSCORE_IDENT_RE,/\s*(?=\()/],className:{2:"title.function"},keywords:d,contains:[{className:"params",begin:/\(/,end:/\)/,keywords:d,relevance:0,contains:[c,e.APOS_STRING_MODE,e.QUOTE_STRING_MODE,jn,e.C_BLOCK_COMMENT_MODE]},e.C_LINE_COMMENT_MODE,e.C_BLOCK_COMMENT_MODE]},jn,c]}}const Nn="[A-Za-z$_][0-9A-Za-z$_]*",ss=["as","in","of","if","for","while","finally","var","new","function","do","return","void","else","break","catch","instanceof","with","throw","case","default","try","switch","continue","typeof","delete","let","yield","const","class","debugger","async","await","static","import","from","export","extends","using"],os=["true","false","null","undefined","NaN","Infinity"],ui=["Object","Function","Boolean","Symbol","Math","Date","Number","BigInt","String","RegExp","Array","Float32Array","Float64Array","Int8Array","Uint8Array","Uint8ClampedArray","Int16Array","Int32Array","Uint16Array","Uint32Array","BigInt64Array","BigUint64Array","Set","Map","WeakSet","WeakMap","ArrayBuffer","SharedArrayBuffer","Atomics","DataView","JSON","Promise","Generator","GeneratorFunction","AsyncFunction","Reflect","Proxy","Intl","WebAssembly"],mi=["Error","EvalError","InternalError","RangeError","ReferenceError","SyntaxError","TypeError","URIError"],hi=["setInterval","setTimeout","clearInterval","clearTimeout","require","exports","eval","isFinite","isNaN","parseFloat","parseInt","decodeURI","decodeURIComponent","encodeURI","encodeURIComponent","escape","unescape"],as=["arguments","this","super","console","window","document","localStorage","sessionStorage","module","global"],ls=[].concat(hi,ui,mi);function cs(e){const t=e.regex,n=(M,{after:q})=>{const ce="</"+M[0].slice(1);return M.input.indexOf(ce,q)!==-1},i=Nn,r={begin:"<>",end:"</>"},o=/<[A-Za-z0-9\\._:-]+\s*\/>/,a={begin:/<[A-Za-z0-9\\._:-]+/,end:/\/[A-Za-z0-9\\._:-]+>|\/>/,isTrulyOpeningTag:(M,q)=>{const ce=M[0].length+M.index,H=M.input[ce];if(H==="<"||H===","){q.ignoreMatch();return}H===">"&&(n(M,{after:ce})||q.ignoreMatch());let re;const de=M.input.substring(ce);if(re=de.match(/^\s*=/)){q.ignoreMatch();return}if((re=de.match(/^\s+extends\s+/))&&re.index===0){q.ignoreMatch();return}}},l={$pattern:Nn,keyword:ss,literal:os,built_in:ls,"variable.language":as},d="[0-9](_?[0-9])*",c=`\\.(${d})`,p="0|[1-9](_?[0-9])*|0[0-7]*[89][0-9]*",f={className:"number",variants:[{begin:`(\\b(${p})((${c})|\\.)?|(${c}))[eE][+-]?(${d})\\b`},{begin:`\\b(${p})\\b((${c})\\b|\\.)?|(${c})\\b`},{begin:"\\b(0|[1-9](_?[0-9])*)n\\b"},{begin:"\\b0[xX][0-9a-fA-F](_?[0-9a-fA-F])*n?\\b"},{begin:"\\b0[bB][0-1](_?[0-1])*n?\\b"},{begin:"\\b0[oO][0-7](_?[0-7])*n?\\b"},{begin:"\\b0[0-7]+n?\\b"}],relevance:0},g={className:"subst",begin:"\\$\\{",end:"\\}",keywords:l,contains:[]},x={begin:".?html`",end:"",starts:{end:"`",returnEnd:!1,contains:[e.BACKSLASH_ESCAPE,g],subLanguage:"xml"}},h={begin:".?css`",end:"",starts:{end:"`",returnEnd:!1,contains:[e.BACKSLASH_ESCAPE,g],subLanguage:"css"}},u={begin:".?gql`",end:"",starts:{end:"`",returnEnd:!1,contains:[e.BACKSLASH_ESCAPE,g],subLanguage:"graphql"}},y={className:"string",begin:"`",end:"`",contains:[e.BACKSLASH_ESCAPE,g]},w={className:"comment",variants:[e.COMMENT(/\/\*\*(?!\/)/,"\\*/",{relevance:0,contains:[{begin:"(?=@[A-Za-z]+)",relevance:0,contains:[{className:"doctag",begin:"@[A-Za-z]+"},{className:"type",begin:"\\{",end:"\\}",excludeEnd:!0,excludeBegin:!0,relevance:0},{className:"variable",begin:i+"(?=\\s*(-)|$)",endsParent:!0,relevance:0},{begin:/(?=[^\n])\s/,relevance:0}]}]}),e.C_BLOCK_COMMENT_MODE,e.C_LINE_COMMENT_MODE]},N=[e.APOS_STRING_MODE,e.QUOTE_STRING_MODE,x,h,u,y,{match:/\$\d+/},f];g.contains=N.concat({begin:/\{/,end:/\}/,keywords:l,contains:["self"].concat(N)});const b=[].concat(w,g.contains),$=b.concat([{begin:/(\s*)\(/,end:/\)/,keywords:l,contains:["self"].concat(b)}]),S={className:"params",begin:/(\s*)\(/,end:/\)/,excludeBegin:!0,excludeEnd:!0,keywords:l,contains:$},C={variants:[{match:[/class/,/\s+/,i,/\s+/,/extends/,/\s+/,t.concat(i,"(",t.concat(/\./,i),")*")],scope:{1:"keyword",3:"title.class",5:"keyword",7:"title.class.inherited"}},{match:[/class/,/\s+/,i],scope:{1:"keyword",3:"title.class"}}]},O={relevance:0,match:t.either(/\bJSON/,/\b[A-Z][a-z]+([A-Z][a-z]*|\d)*/,/\b[A-Z]{2,}([A-Z][a-z]+|\d)+([A-Z][a-z]*)*/,/\b[A-Z]{2,}[a-z]+([A-Z][a-z]+|\d)*([A-Z][a-z]*)*/),className:"title.class",keywords:{_:[...ui,...mi]}},F={label:"use_strict",className:"meta",relevance:10,begin:/^\s*['"]use (strict|asm)['"]/},Q={variants:[{match:[/function/,/\s+/,i,/(?=\s*\()/]},{match:[/function/,/\s*(?=\()/]}],className:{1:"keyword",3:"title.function"},label:"func.def",contains:[S],illegal:/%/},W={relevance:0,match:/\b[A-Z][A-Z_0-9]+\b/,className:"variable.constant"};function Z(M){return t.concat("(?!",M.join("|"),")")}const I={match:t.concat(/\b/,Z([...hi,"super","import"].map(M=>`${M}\\s*\\(`)),i,t.lookahead(/\s*\(/)),className:"title.function",relevance:0},L={begin:t.concat(/\./,t.lookahead(t.concat(i,/(?![0-9A-Za-z$_(])/))),end:i,excludeBegin:!0,keywords:"prototype",className:"property",relevance:0},B={match:[/get|set/,/\s+/,i,/(?=\()/],className:{1:"keyword",3:"title.function"},contains:[{begin:/\(\)/},S]},G="(\\([^()]*(\\([^()]*(\\([^()]*\\)[^()]*)*\\)[^()]*)*\\)|"+e.UNDERSCORE_IDENT_RE+")\\s*=>",se={match:[/const|var|let/,/\s+/,i,/\s*/,/=\s*/,/(async\s*)?/,t.lookahead(G)],keywords:"async",className:{1:"keyword",3:"title.function"},contains:[S]};return{name:"JavaScript",aliases:["js","jsx","mjs","cjs"],keywords:l,exports:{PARAMS_CONTAINS:$,CLASS_REFERENCE:O},illegal:/#(?![$_A-z])/,contains:[e.SHEBANG({label:"shebang",binary:"node",relevance:5}),F,e.APOS_STRING_MODE,e.QUOTE_STRING_MODE,x,h,u,y,w,{match:/\$\d+/},f,O,{scope:"attr",match:i+t.lookahead(":"),relevance:0},se,{begin:"("+e.RE_STARTERS_RE+"|\\b(case|return|throw)\\b)\\s*",keywords:"return throw case",relevance:0,contains:[w,e.REGEXP_MODE,{className:"function",begin:G,returnBegin:!0,end:"\\s*=>",contains:[{className:"params",variants:[{begin:e.UNDERSCORE_IDENT_RE,relevance:0},{className:null,begin:/\(\s*\)/,skip:!0},{begin:/(\s*)\(/,end:/\)/,excludeBegin:!0,excludeEnd:!0,keywords:l,contains:$}]}]},{begin:/,/,relevance:0},{match:/\s+/,relevance:0},{variants:[{begin:r.begin,end:r.end},{match:o},{begin:a.begin,"on:begin":a.isTrulyOpeningTag,end:a.end}],subLanguage:"xml",contains:[{begin:a.begin,end:a.end,skip:!0,contains:["self"]}]}]},Q,{beginKeywords:"while if switch catch for"},{begin:"\\b(?!function)"+e.UNDERSCORE_IDENT_RE+"\\([^()]*(\\([^()]*(\\([^()]*\\)[^()]*)*\\)[^()]*)*\\)\\s*\\{",returnBegin:!0,label:"func.def",contains:[S,e.inherit(e.TITLE_MODE,{begin:i,className:"title.function"})]},{match:/\.\.\./,relevance:0},L,{match:"\\$"+i,relevance:0},{match:[/\bconstructor(?=\s*\()/],className:{1:"title.function"},contains:[S]},I,W,C,B,{match:/\$[(.]/}]}}function ds(e){const t={className:"attr",begin:/"(\\.|[^\\"\r\n])*"(?=\s*:)/,relevance:1.01},n={match:/[{}[\],:]/,className:"punctuation",relevance:0},i=["true","false","null"],r={scope:"literal",beginKeywords:i.join(" ")};return{name:"JSON",aliases:["jsonc"],keywords:{literal:i},contains:[t,n,e.QUOTE_STRING_MODE,r,e.C_NUMBER_MODE,e.C_LINE_COMMENT_MODE,e.C_BLOCK_COMMENT_MODE],illegal:"\\S"}}function ps(e){const t=e.regex,n={begin:/<\/?[A-Za-z_]/,end:">",subLanguage:"xml",relevance:0},i={begin:"^[-\\*]{3,}",end:"$"},r={className:"code",variants:[{begin:"(`{3,})[^`](.|\\n)*?\\1`*[ ]*"},{begin:"(~{3,})[^~](.|\\n)*?\\1~*[ ]*"},{begin:"```",end:"```+[ ]*$"},{begin:"~~~",end:"~~~+[ ]*$"},{begin:"`.+?`"},{begin:"(?=^( {4}|\\t))",contains:[{begin:"^( {4}|\\t)",end:"(\\n)$"}],relevance:0}]},o={className:"bullet",begin:"^[ 	]*([*+-]|(\\d+\\.))(?=\\s+)",end:"\\s+",excludeEnd:!0},a={begin:/^\[[^\n]+\]:/,returnBegin:!0,contains:[{className:"symbol",begin:/\[/,end:/\]/,excludeBegin:!0,excludeEnd:!0},{className:"link",begin:/:\s*/,end:/$/,excludeBegin:!0}]},l=/[A-Za-z][A-Za-z0-9+.-]*/,d={variants:[{begin:/\[.+?\]\[.*?\]/,relevance:0},{begin:/\[.+?\]\(((data|javascript|mailto):|(?:http|ftp)s?:\/\/).*?\)/,relevance:2},{begin:t.concat(/\[.+?\]\(/,l,/:\/\/.*?\)/),relevance:2},{begin:/\[.+?\]\([./?&#].*?\)/,relevance:1},{begin:/\[.*?\]\(.*?\)/,relevance:0}],returnBegin:!0,contains:[{match:/\[(?=\])/},{className:"string",relevance:0,begin:"\\[",end:"\\]",excludeBegin:!0,returnEnd:!0},{className:"link",relevance:0,begin:"\\]\\(",end:"\\)",excludeBegin:!0,excludeEnd:!0},{className:"symbol",relevance:0,begin:"\\]\\[",end:"\\]",excludeBegin:!0,excludeEnd:!0}]},c={className:"strong",contains:[],variants:[{begin:/_{2}(?!\s)/,end:/_{2}/},{begin:/\*{2}(?!\s)/,end:/\*{2}/}]},p={className:"emphasis",contains:[],variants:[{begin:/\*(?![*\s])/,end:/\*/},{begin:/_(?![_\s])/,end:/_/,relevance:0}]},f=e.inherit(c,{contains:[]}),g=e.inherit(p,{contains:[]});c.contains.push(g),p.contains.push(f);let x=[n,d];return[c,p,f,g].forEach(j=>{j.contains=j.contains.concat(x)}),x=x.concat(c,p),{name:"Markdown",aliases:["md","mkdown","mkd"],contains:[{className:"section",variants:[{begin:"^#{1,6}",end:"$",contains:x},{begin:"(?=^.+?\\n[=-]{2,}$)",contains:[{begin:"^[=-]*$"},{begin:"^",end:"\\n",contains:x}]}]},n,o,c,p,{className:"quote",begin:"^>\\s+",contains:x,end:"$"},r,i,d,a,{scope:"literal",match:/&([a-zA-Z0-9]+|#[0-9]{1,7}|#[Xx][0-9a-fA-F]{1,6});/}]}}function fs(e){const t=e.regex,n=new RegExp("[\\p{XID_Start}_]\\p{XID_Continue}*","u"),i=["and","as","assert","async","await","break","case","class","continue","def","del","elif","else","except","finally","for","from","global","if","import","in","is","lambda","match","nonlocal|10","not","or","pass","raise","return","try","while","with","yield"],l={$pattern:/[A-Za-z]\w+|__\w+__/,keyword:i,built_in:["__import__","abs","all","any","ascii","bin","bool","breakpoint","bytearray","bytes","callable","chr","classmethod","compile","complex","delattr","dict","dir","divmod","enumerate","eval","exec","filter","float","format","frozenset","getattr","globals","hasattr","hash","help","hex","id","input","int","isinstance","issubclass","iter","len","list","locals","map","max","memoryview","min","next","object","oct","open","ord","pow","print","property","range","repr","reversed","round","set","setattr","slice","sorted","staticmethod","str","sum","super","tuple","type","vars","zip"],literal:["__debug__","Ellipsis","False","None","NotImplemented","True"],type:["Any","Callable","Coroutine","Dict","List","Literal","Generic","Optional","Sequence","Set","Tuple","Type","Union"]},d={className:"meta",begin:/^(>>>|\.\.\.) /},c={className:"subst",begin:/\{/,end:/\}/,keywords:l,illegal:/#/},p={begin:/\{\{/,relevance:0},f={className:"string",contains:[e.BACKSLASH_ESCAPE],variants:[{begin:/([uU]|[bB]|[rR]|[bB][rR]|[rR][bB])?'''/,end:/'''/,contains:[e.BACKSLASH_ESCAPE,d],relevance:10},{begin:/([uU]|[bB]|[rR]|[bB][rR]|[rR][bB])?"""/,end:/"""/,contains:[e.BACKSLASH_ESCAPE,d],relevance:10},{begin:/([fF][rR]|[rR][fF]|[fF])'''/,end:/'''/,contains:[e.BACKSLASH_ESCAPE,d,p,c]},{begin:/([fF][rR]|[rR][fF]|[fF])"""/,end:/"""/,contains:[e.BACKSLASH_ESCAPE,d,p,c]},{begin:/([uU]|[rR])'/,end:/'/,relevance:10},{begin:/([uU]|[rR])"/,end:/"/,relevance:10},{begin:/([bB]|[bB][rR]|[rR][bB])'/,end:/'/},{begin:/([bB]|[bB][rR]|[rR][bB])"/,end:/"/},{begin:/([fF][rR]|[rR][fF]|[fF])'/,end:/'/,contains:[e.BACKSLASH_ESCAPE,p,c]},{begin:/([fF][rR]|[rR][fF]|[fF])"/,end:/"/,contains:[e.BACKSLASH_ESCAPE,p,c]},e.APOS_STRING_MODE,e.QUOTE_STRING_MODE]},g="[0-9](_?[0-9])*",x=`(\\b(${g}))?\\.(${g})|\\b(${g})\\.`,h=`\\b|${i.join("|")}`,u={className:"number",relevance:0,variants:[{begin:`(\\b(${g})|(${x}))[eE][+-]?(${g})[jJ]?(?=${h})`},{begin:`(${x})[jJ]?`},{begin:`\\b([1-9](_?[0-9])*|0+(_?0)*)[lLjJ]?(?=${h})`},{begin:`\\b0[bB](_?[01])+[lL]?(?=${h})`},{begin:`\\b0[oO](_?[0-7])+[lL]?(?=${h})`},{begin:`\\b0[xX](_?[0-9a-fA-F])+[lL]?(?=${h})`},{begin:`\\b(${g})[jJ](?=${h})`}]},y={className:"comment",begin:t.lookahead(/# type:/),end:/$/,keywords:l,contains:[{begin:/# type:/},{begin:/#/,end:/\b\B/,endsWithParent:!0}]},j={className:"params",variants:[{className:"",begin:/\(\s*\)/,skip:!0},{begin:/\(/,end:/\)/,excludeBegin:!0,excludeEnd:!0,keywords:l,contains:["self",d,u,f,e.HASH_COMMENT_MODE]}]};return c.contains=[f,u,d],{name:"Python",aliases:["py","gyp","ipython"],unicodeRegex:!0,keywords:l,illegal:/(<\/|\?)|=>/,contains:[d,u,{scope:"variable.language",match:/\bself\b/},{beginKeywords:"if",relevance:0},{match:/\bor\b/,scope:"keyword"},f,y,e.HASH_COMMENT_MODE,{match:[/\bdef/,/\s+/,n],scope:{1:"keyword",3:"title.function"},contains:[j]},{variants:[{match:[/\bclass/,/\s+/,n,/\s*/,/\(\s*/,n,/\s*\)/]},{match:[/\bclass/,/\s+/,n]}],scope:{1:"keyword",3:"title.class",6:"title.class.inherited"}},{className:"meta",begin:/^[\t ]*@/,end:/(?=#)|$/,contains:[u,j,f]}]}}function gs(e){const t=e.regex,n=/(r#)?/,i=t.concat(n,e.UNDERSCORE_IDENT_RE),r=t.concat(n,e.IDENT_RE),o={className:"title.function.invoke",relevance:0,begin:t.concat(/\b/,/(?!let|for|while|if|else|match\b)/,r,t.lookahead(/\s*\(/))},a="([ui](8|16|32|64|128|size)|f(32|64))?",l=["abstract","as","async","await","become","box","break","const","continue","crate","do","dyn","else","enum","extern","false","final","fn","for","if","impl","in","let","loop","macro","match","mod","move","mut","override","priv","pub","ref","return","self","Self","static","struct","super","trait","true","try","type","typeof","union","unsafe","unsized","use","virtual","where","while","yield"],d=["true","false","Some","None","Ok","Err"],c=["drop ","Copy","Send","Sized","Sync","Drop","Fn","FnMut","FnOnce","ToOwned","Clone","Debug","PartialEq","PartialOrd","Eq","Ord","AsRef","AsMut","Into","From","Default","Iterator","Extend","IntoIterator","DoubleEndedIterator","ExactSizeIterator","SliceConcatExt","ToString","assert!","assert_eq!","bitflags!","bytes!","cfg!","col!","concat!","concat_idents!","debug_assert!","debug_assert_eq!","env!","eprintln!","panic!","file!","format!","format_args!","include_bytes!","include_str!","line!","local_data_key!","module_path!","option_env!","print!","println!","select!","stringify!","try!","unimplemented!","unreachable!","vec!","write!","writeln!","macro_rules!","assert_ne!","debug_assert_ne!"],p=["i8","i16","i32","i64","i128","isize","u8","u16","u32","u64","u128","usize","f32","f64","str","char","bool","Box","Option","Result","String","Vec"];return{name:"Rust",aliases:["rs"],keywords:{$pattern:e.IDENT_RE+"!?",type:p,keyword:l,literal:d,built_in:c},illegal:"</",contains:[e.C_LINE_COMMENT_MODE,e.COMMENT("/\\*","\\*/",{contains:["self"]}),e.inherit(e.QUOTE_STRING_MODE,{begin:/b?"/,illegal:null}),{className:"symbol",begin:/'[a-zA-Z_][a-zA-Z0-9_]*(?!')/},{scope:"string",variants:[{begin:/b?r(#*)"(.|\n)*?"\1(?!#)/},{begin:/b?'/,end:/'/,contains:[{scope:"char.escape",match:/\\('|\w|x\w{2}|u\w{4}|U\w{8})/}]}]},{className:"number",variants:[{begin:"\\b0b([01_]+)"+a},{begin:"\\b0o([0-7_]+)"+a},{begin:"\\b0x([A-Fa-f0-9_]+)"+a},{begin:"\\b(\\d[\\d_]*(\\.[0-9_]+)?([eE][+-]?[0-9_]+)?)"+a}],relevance:0},{begin:[/fn/,/\s+/,i],className:{1:"keyword",3:"title.function"}},{className:"meta",begin:"#!?\\[",end:"\\]",contains:[{className:"string",begin:/"/,end:/"/,contains:[e.BACKSLASH_ESCAPE]}]},{begin:[/let/,/\s+/,/(?:mut\s+)?/,i],className:{1:"keyword",3:"keyword",4:"variable"}},{begin:[/for/,/\s+/,i,/\s+/,/in/],className:{1:"keyword",3:"variable",5:"keyword"}},{begin:[/type/,/\s+/,i],className:{1:"keyword",3:"title.class"}},{begin:[/(?:trait|enum|struct|union|impl|for)/,/\s+/,i],className:{1:"keyword",3:"title.class"}},{begin:e.IDENT_RE+"::",keywords:{keyword:"Self",built_in:c,type:p}},{className:"punctuation",begin:"->"},o]}}function xs(e){const t=e.regex,n=e.COMMENT("--","$"),i={scope:"string",variants:[{begin:/'/,end:/'/,contains:[{match:/''/}]}]},r={begin:/"/,end:/"/,contains:[{match:/""/}]},o=["true","false","unknown"],a=["double precision","large object","with timezone","without timezone"],l=["bigint","binary","blob","boolean","char","character","clob","date","dec","decfloat","decimal","float","int","integer","interval","nchar","nclob","national","numeric","real","row","smallint","time","timestamp","varchar","varying","varbinary"],d=["add","asc","collation","desc","final","first","last","view"],c=["abs","acos","all","allocate","alter","and","any","are","array","array_agg","array_max_cardinality","as","asensitive","asin","asymmetric","at","atan","atomic","authorization","avg","begin","begin_frame","begin_partition","between","bigint","binary","blob","boolean","both","by","call","called","cardinality","cascaded","case","cast","ceil","ceiling","char","char_length","character","character_length","check","classifier","clob","close","coalesce","collate","collect","column","commit","condition","connect","constraint","contains","convert","copy","corr","corresponding","cos","cosh","count","covar_pop","covar_samp","create","cross","cube","cume_dist","current","current_catalog","current_date","current_default_transform_group","current_path","current_role","current_row","current_schema","current_time","current_timestamp","current_path","current_role","current_transform_group_for_type","current_user","cursor","cycle","date","day","deallocate","dec","decimal","decfloat","declare","default","define","delete","dense_rank","deref","describe","deterministic","disconnect","distinct","double","drop","dynamic","each","element","else","empty","end","end_frame","end_partition","end-exec","equals","escape","every","except","exec","execute","exists","exp","external","extract","false","fetch","filter","first_value","float","floor","for","foreign","frame_row","free","from","full","function","fusion","get","global","grant","group","grouping","groups","having","hold","hour","identity","in","indicator","initial","inner","inout","insensitive","insert","int","integer","intersect","intersection","interval","into","is","join","json_array","json_arrayagg","json_exists","json_object","json_objectagg","json_query","json_table","json_table_primitive","json_value","lag","language","large","last_value","lateral","lead","leading","left","like","like_regex","listagg","ln","local","localtime","localtimestamp","log","log10","lower","match","match_number","match_recognize","matches","max","member","merge","method","min","minute","mod","modifies","module","month","multiset","national","natural","nchar","nclob","new","no","none","normalize","not","nth_value","ntile","null","nullif","numeric","octet_length","occurrences_regex","of","offset","old","omit","on","one","only","open","or","order","out","outer","over","overlaps","overlay","parameter","partition","pattern","per","percent","percent_rank","percentile_cont","percentile_disc","period","portion","position","position_regex","power","precedes","precision","prepare","primary","procedure","ptf","range","rank","reads","real","recursive","ref","references","referencing","regr_avgx","regr_avgy","regr_count","regr_intercept","regr_r2","regr_slope","regr_sxx","regr_sxy","regr_syy","release","result","return","returns","revoke","right","rollback","rollup","row","row_number","rows","running","savepoint","scope","scroll","search","second","seek","select","sensitive","session_user","set","show","similar","sin","sinh","skip","smallint","some","specific","specifictype","sql","sqlexception","sqlstate","sqlwarning","sqrt","start","static","stddev_pop","stddev_samp","submultiset","subset","substring","substring_regex","succeeds","sum","symmetric","system","system_time","system_user","table","tablesample","tan","tanh","then","time","timestamp","timezone_hour","timezone_minute","to","trailing","translate","translate_regex","translation","treat","trigger","trim","trim_array","true","truncate","uescape","union","unique","unknown","unnest","update","upper","user","using","value","values","value_of","var_pop","var_samp","varbinary","varchar","varying","versioning","when","whenever","where","width_bucket","window","with","within","without","year"],p=["abs","acos","array_agg","asin","atan","avg","cast","ceil","ceiling","coalesce","corr","cos","cosh","count","covar_pop","covar_samp","cume_dist","dense_rank","deref","element","exp","extract","first_value","floor","json_array","json_arrayagg","json_exists","json_object","json_objectagg","json_query","json_table","json_table_primitive","json_value","lag","last_value","lead","listagg","ln","log","log10","lower","max","min","mod","nth_value","ntile","nullif","percent_rank","percentile_cont","percentile_disc","position","position_regex","power","rank","regr_avgx","regr_avgy","regr_count","regr_intercept","regr_r2","regr_slope","regr_sxx","regr_sxy","regr_syy","row_number","sin","sinh","sqrt","stddev_pop","stddev_samp","substring","substring_regex","sum","tan","tanh","translate","translate_regex","treat","trim","trim_array","unnest","upper","value_of","var_pop","var_samp","width_bucket"],f=["current_catalog","current_date","current_default_transform_group","current_path","current_role","current_schema","current_transform_group_for_type","current_user","session_user","system_time","system_user","current_time","localtime","current_timestamp","localtimestamp"],g=["create table","insert into","primary key","foreign key","not null","alter table","add constraint","grouping sets","on overflow","character set","respect nulls","ignore nulls","nulls first","nulls last","depth first","breadth first"],x=p,h=[...c,...d].filter($=>!p.includes($)),u={scope:"variable",match:/@[a-z0-9][a-z0-9_]*/},y={scope:"operator",match:/[-+*/=%^~]|&&?|\|\|?|!=?|<(?:=>?|<|>)?|>[>=]?/,relevance:0},j={match:t.concat(/\b/,t.either(...x),/\s*\(/),relevance:0,keywords:{built_in:x}};function w($){return t.concat(/\b/,t.either(...$.map(S=>S.replace(/\s+/,"\\s+"))),/\b/)}const N={scope:"keyword",match:w(g),relevance:0};function b($,{exceptions:S,when:C}={}){const O=C;return S=S||[],$.map(F=>F.match(/\|\d+$/)||S.includes(F)?F:O(F)?`${F}|0`:F)}return{name:"SQL",case_insensitive:!0,illegal:/[{}]|<\//,keywords:{$pattern:/\b[\w\.]+/,keyword:b(h,{when:$=>$.length<3}),literal:o,type:l,built_in:f},contains:[{scope:"type",match:w(a)},N,j,u,i,r,e.C_NUMBER_MODE,e.C_BLOCK_COMMENT_MODE,n,y]}}const pt="[A-Za-z$_][0-9A-Za-z$_]*",bi=["as","in","of","if","for","while","finally","var","new","function","do","return","void","else","break","catch","instanceof","with","throw","case","default","try","switch","continue","typeof","delete","let","yield","const","class","debugger","async","await","static","import","from","export","extends","using"],yi=["true","false","null","undefined","NaN","Infinity"],vi=["Object","Function","Boolean","Symbol","Math","Date","Number","BigInt","String","RegExp","Array","Float32Array","Float64Array","Int8Array","Uint8Array","Uint8ClampedArray","Int16Array","Int32Array","Uint16Array","Uint32Array","BigInt64Array","BigUint64Array","Set","Map","WeakSet","WeakMap","ArrayBuffer","SharedArrayBuffer","Atomics","DataView","JSON","Promise","Generator","GeneratorFunction","AsyncFunction","Reflect","Proxy","Intl","WebAssembly"],wi=["Error","EvalError","InternalError","RangeError","ReferenceError","SyntaxError","TypeError","URIError"],ki=["setInterval","setTimeout","clearInterval","clearTimeout","require","exports","eval","isFinite","isNaN","parseFloat","parseInt","decodeURI","decodeURIComponent","encodeURI","encodeURIComponent","escape","unescape"],$i=["arguments","this","super","console","window","document","localStorage","sessionStorage","module","global"],Si=[].concat(ki,vi,wi);function us(e){const t=e.regex,n=(M,{after:q})=>{const ce="</"+M[0].slice(1);return M.input.indexOf(ce,q)!==-1},i=pt,r={begin:"<>",end:"</>"},o=/<[A-Za-z0-9\\._:-]+\s*\/>/,a={begin:/<[A-Za-z0-9\\._:-]+/,end:/\/[A-Za-z0-9\\._:-]+>|\/>/,isTrulyOpeningTag:(M,q)=>{const ce=M[0].length+M.index,H=M.input[ce];if(H==="<"||H===","){q.ignoreMatch();return}H===">"&&(n(M,{after:ce})||q.ignoreMatch());let re;const de=M.input.substring(ce);if(re=de.match(/^\s*=/)){q.ignoreMatch();return}if((re=de.match(/^\s+extends\s+/))&&re.index===0){q.ignoreMatch();return}}},l={$pattern:pt,keyword:bi,literal:yi,built_in:Si,"variable.language":$i},d="[0-9](_?[0-9])*",c=`\\.(${d})`,p="0|[1-9](_?[0-9])*|0[0-7]*[89][0-9]*",f={className:"number",variants:[{begin:`(\\b(${p})((${c})|\\.)?|(${c}))[eE][+-]?(${d})\\b`},{begin:`\\b(${p})\\b((${c})\\b|\\.)?|(${c})\\b`},{begin:"\\b(0|[1-9](_?[0-9])*)n\\b"},{begin:"\\b0[xX][0-9a-fA-F](_?[0-9a-fA-F])*n?\\b"},{begin:"\\b0[bB][0-1](_?[0-1])*n?\\b"},{begin:"\\b0[oO][0-7](_?[0-7])*n?\\b"},{begin:"\\b0[0-7]+n?\\b"}],relevance:0},g={className:"subst",begin:"\\$\\{",end:"\\}",keywords:l,contains:[]},x={begin:".?html`",end:"",starts:{end:"`",returnEnd:!1,contains:[e.BACKSLASH_ESCAPE,g],subLanguage:"xml"}},h={begin:".?css`",end:"",starts:{end:"`",returnEnd:!1,contains:[e.BACKSLASH_ESCAPE,g],subLanguage:"css"}},u={begin:".?gql`",end:"",starts:{end:"`",returnEnd:!1,contains:[e.BACKSLASH_ESCAPE,g],subLanguage:"graphql"}},y={className:"string",begin:"`",end:"`",contains:[e.BACKSLASH_ESCAPE,g]},w={className:"comment",variants:[e.COMMENT(/\/\*\*(?!\/)/,"\\*/",{relevance:0,contains:[{begin:"(?=@[A-Za-z]+)",relevance:0,contains:[{className:"doctag",begin:"@[A-Za-z]+"},{className:"type",begin:"\\{",end:"\\}",excludeEnd:!0,excludeBegin:!0,relevance:0},{className:"variable",begin:i+"(?=\\s*(-)|$)",endsParent:!0,relevance:0},{begin:/(?=[^\n])\s/,relevance:0}]}]}),e.C_BLOCK_COMMENT_MODE,e.C_LINE_COMMENT_MODE]},N=[e.APOS_STRING_MODE,e.QUOTE_STRING_MODE,x,h,u,y,{match:/\$\d+/},f];g.contains=N.concat({begin:/\{/,end:/\}/,keywords:l,contains:["self"].concat(N)});const b=[].concat(w,g.contains),$=b.concat([{begin:/(\s*)\(/,end:/\)/,keywords:l,contains:["self"].concat(b)}]),S={className:"params",begin:/(\s*)\(/,end:/\)/,excludeBegin:!0,excludeEnd:!0,keywords:l,contains:$},C={variants:[{match:[/class/,/\s+/,i,/\s+/,/extends/,/\s+/,t.concat(i,"(",t.concat(/\./,i),")*")],scope:{1:"keyword",3:"title.class",5:"keyword",7:"title.class.inherited"}},{match:[/class/,/\s+/,i],scope:{1:"keyword",3:"title.class"}}]},O={relevance:0,match:t.either(/\bJSON/,/\b[A-Z][a-z]+([A-Z][a-z]*|\d)*/,/\b[A-Z]{2,}([A-Z][a-z]+|\d)+([A-Z][a-z]*)*/,/\b[A-Z]{2,}[a-z]+([A-Z][a-z]+|\d)*([A-Z][a-z]*)*/),className:"title.class",keywords:{_:[...vi,...wi]}},F={label:"use_strict",className:"meta",relevance:10,begin:/^\s*['"]use (strict|asm)['"]/},Q={variants:[{match:[/function/,/\s+/,i,/(?=\s*\()/]},{match:[/function/,/\s*(?=\()/]}],className:{1:"keyword",3:"title.function"},label:"func.def",contains:[S],illegal:/%/},W={relevance:0,match:/\b[A-Z][A-Z_0-9]+\b/,className:"variable.constant"};function Z(M){return t.concat("(?!",M.join("|"),")")}const I={match:t.concat(/\b/,Z([...ki,"super","import"].map(M=>`${M}\\s*\\(`)),i,t.lookahead(/\s*\(/)),className:"title.function",relevance:0},L={begin:t.concat(/\./,t.lookahead(t.concat(i,/(?![0-9A-Za-z$_(])/))),end:i,excludeBegin:!0,keywords:"prototype",className:"property",relevance:0},B={match:[/get|set/,/\s+/,i,/(?=\()/],className:{1:"keyword",3:"title.function"},contains:[{begin:/\(\)/},S]},G="(\\([^()]*(\\([^()]*(\\([^()]*\\)[^()]*)*\\)[^()]*)*\\)|"+e.UNDERSCORE_IDENT_RE+")\\s*=>",se={match:[/const|var|let/,/\s+/,i,/\s*/,/=\s*/,/(async\s*)?/,t.lookahead(G)],keywords:"async",className:{1:"keyword",3:"title.function"},contains:[S]};return{name:"JavaScript",aliases:["js","jsx","mjs","cjs"],keywords:l,exports:{PARAMS_CONTAINS:$,CLASS_REFERENCE:O},illegal:/#(?![$_A-z])/,contains:[e.SHEBANG({label:"shebang",binary:"node",relevance:5}),F,e.APOS_STRING_MODE,e.QUOTE_STRING_MODE,x,h,u,y,w,{match:/\$\d+/},f,O,{scope:"attr",match:i+t.lookahead(":"),relevance:0},se,{begin:"("+e.RE_STARTERS_RE+"|\\b(case|return|throw)\\b)\\s*",keywords:"return throw case",relevance:0,contains:[w,e.REGEXP_MODE,{className:"function",begin:G,returnBegin:!0,end:"\\s*=>",contains:[{className:"params",variants:[{begin:e.UNDERSCORE_IDENT_RE,relevance:0},{className:null,begin:/\(\s*\)/,skip:!0},{begin:/(\s*)\(/,end:/\)/,excludeBegin:!0,excludeEnd:!0,keywords:l,contains:$}]}]},{begin:/,/,relevance:0},{match:/\s+/,relevance:0},{variants:[{begin:r.begin,end:r.end},{match:o},{begin:a.begin,"on:begin":a.isTrulyOpeningTag,end:a.end}],subLanguage:"xml",contains:[{begin:a.begin,end:a.end,skip:!0,contains:["self"]}]}]},Q,{beginKeywords:"while if switch catch for"},{begin:"\\b(?!function)"+e.UNDERSCORE_IDENT_RE+"\\([^()]*(\\([^()]*(\\([^()]*\\)[^()]*)*\\)[^()]*)*\\)\\s*\\{",returnBegin:!0,label:"func.def",contains:[S,e.inherit(e.TITLE_MODE,{begin:i,className:"title.function"})]},{match:/\.\.\./,relevance:0},L,{match:"\\$"+i,relevance:0},{match:[/\bconstructor(?=\s*\()/],className:{1:"title.function"},contains:[S]},I,W,C,B,{match:/\$[(.]/}]}}function ms(e){const t=e.regex,n=us(e),i=pt,r=["any","void","number","boolean","string","object","never","symbol","bigint","unknown"],o={begin:[/namespace/,/\s+/,e.IDENT_RE],beginScope:{1:"keyword",3:"title.class"}},a={beginKeywords:"interface",end:/\{/,excludeEnd:!0,keywords:{keyword:"interface extends",built_in:r},contains:[n.exports.CLASS_REFERENCE]},l={className:"meta",relevance:10,begin:/^\s*['"]use strict['"]/},d=["type","interface","public","private","protected","implements","declare","abstract","readonly","enum","override","satisfies"],c={$pattern:pt,keyword:bi.concat(d),literal:yi,built_in:Si.concat(r),"variable.language":$i},p={className:"meta",begin:"@"+i},f=(u,y,j)=>{const w=u.contains.findIndex(N=>N.label===y);if(w===-1)throw new Error("can not find mode to replace");u.contains.splice(w,1,j)};Object.assign(n.keywords,c),n.exports.PARAMS_CONTAINS.push(p);const g=n.contains.find(u=>u.scope==="attr"),x=Object.assign({},g,{match:t.concat(i,t.lookahead(/\s*\?:/))});n.exports.PARAMS_CONTAINS.push([n.exports.CLASS_REFERENCE,g,x]),n.contains=n.contains.concat([p,o,a,x]),f(n,"shebang",e.SHEBANG()),f(n,"use_strict",l);const h=n.contains.find(u=>u.label==="func.def");return h.relevance=0,Object.assign(n,{name:"TypeScript",aliases:["ts","tsx","mts","cts"]}),n}function hs(e){const t=e.regex,n=t.concat(/[\p{L}_]/u,t.optional(/[\p{L}0-9_.-]*:/u),/[\p{L}0-9_.-]*/u),i=/[\p{L}0-9._:-]+/u,r={className:"symbol",begin:/&[a-z]+;|&#[0-9]+;|&#x[a-f0-9]+;/},o={begin:/\s/,contains:[{className:"keyword",begin:/#?[a-z_][a-z1-9_-]+/,illegal:/\n/}]},a=e.inherit(o,{begin:/\(/,end:/\)/}),l=e.inherit(e.APOS_STRING_MODE,{className:"string"}),d=e.inherit(e.QUOTE_STRING_MODE,{className:"string"}),c={endsWithParent:!0,illegal:/</,relevance:0,contains:[{className:"attr",begin:i,relevance:0},{begin:/=\s*/,relevance:0,contains:[{className:"string",endsParent:!0,variants:[{begin:/"/,end:/"/,contains:[r]},{begin:/'/,end:/'/,contains:[r]},{begin:/[^\s"'=<>`]+/}]}]}]};return{name:"HTML, XML",aliases:["html","xhtml","rss","atom","xjb","xsd","xsl","plist","wsf","svg"],case_insensitive:!0,unicodeRegex:!0,contains:[{className:"meta",begin:/<![a-z]/,end:/>/,relevance:10,contains:[o,d,l,a,{begin:/\[/,end:/\]/,contains:[{className:"meta",begin:/<![a-z]/,end:/>/,contains:[o,a,d,l]}]}]},e.COMMENT(/<!--/,/-->/,{relevance:10}),{begin:/<!\[CDATA\[/,end:/\]\]>/,relevance:10},r,{className:"meta",end:/\?>/,variants:[{begin:/<\?xml/,relevance:10,contains:[d]},{begin:/<\?[a-z][a-z0-9]+/}]},{className:"tag",begin:/<style(?=\s|>)/,end:/>/,keywords:{name:"style"},contains:[c],starts:{end:/<\/style>/,returnEnd:!0,subLanguage:["css","xml"]}},{className:"tag",begin:/<script(?=\s|>)/,end:/>/,keywords:{name:"script"},contains:[c],starts:{end:/<\/script>/,returnEnd:!0,subLanguage:["javascript","handlebars","xml"]}},{className:"tag",begin:/<>|<\/>/},{className:"tag",begin:t.concat(/</,t.lookahead(t.concat(n,t.either(/\/>/,/>/,/\s/)))),end:/\/?>/,contains:[{className:"name",begin:n,relevance:0,starts:c}]},{className:"tag",begin:t.concat(/<\//,t.lookahead(t.concat(n,/>/))),contains:[{className:"name",begin:n,relevance:0},{begin:/>/,relevance:0,endsParent:!0}]}]}}const bs="#1e1e2e",ys="#cdd6f4";fe.registerLanguage("bash",Gr);fe.registerLanguage("cpp",Kr);fe.registerLanguage("css",ns);fe.registerLanguage("go",is);fe.registerLanguage("java",rs);fe.registerLanguage("javascript",cs);fe.registerLanguage("json",ds);fe.registerLanguage("markdown",ps);fe.registerLanguage("python",fs);fe.registerLanguage("rust",gs);fe.registerLanguage("sql",xs);fe.registerLanguage("typescript",ms);fe.registerLanguage("xml",hs);const vs={c:"cpp","c++":"cpp",html:"xml",js:"javascript",md:"markdown",py:"python",sh:"bash",shell:"bash",ts:"typescript"},En={keyword:"#c678dd",built_in:"#56b6c2",type:"#e5c07b",literal:"#56b6c2",number:"#d19a66",string:"#98c379",regexp:"#98c379",comment:"#7f848e",doctag:"#7f848e",meta:"#7f848e",title:"#61afef",attr:"#d19a66",attribute:"#d19a66",variable:"#e06c75",tag:"#e06c75",name:"#e06c75",params:"#abb2bf",property:"#e06c75",operator:"#56b6c2",symbol:"#56b6c2",selector:"#e06c75",bullet:"#61afef",link:"#98c379",quote:"#98c379",addition:"#98c379",deletion:"#e06c75",section:"#61afef",function:"#61afef"};function ws(e,t){let n;const i=vs[t]??t;try{n=i&&fe.getLanguage(i)?fe.highlight(e,{language:i}).value:fe.highlightAuto(e).value}catch{n=V(e)}return n.replace(/class="hljs-([a-z_]+)[^"]*"/g,(r,o)=>En[o]?`style="color:${En[o]}"`:"")}const _n=/\s*\/\/\s*\[!code\s+(highlight|\+\+|--|error|warning|focus|word:(.+?))\]\s*$/;function ks(e){const t=e.split(`
`),n=new Map,i=[];for(let r=0;r<t.length;r++){const o=t[r],a=o.match(_n);if(a){const l={};a[1]==="highlight"?l.highlight=!0:a[1]==="++"?l.add=!0:a[1]==="--"?l.remove=!0:a[1]==="error"?l.error=!0:a[1]==="warning"?l.warning=!0:a[1]==="focus"?l.focus=!0:a[1].startsWith("word:")&&(l.word=a[1].slice(5)),n.set(r,l),i.push(o.replace(_n,""))}else i.push(o)}return{cleanCode:i.join(`
`),annotations:n}}const je="display:inline-block;width:calc(100% + 3.5em);margin-left:-1.75em;padding:0 0.75em;border-left:3px solid transparent;",Te={highlight:`${je}background:rgba(255,235,59,0.14);border-left-color:#f59e0b;`,add:`${je}background:rgba(46,160,67,0.14);border-left-color:#2ea043;`,remove:`${je}background:rgba(248,81,73,0.14);border-left-color:#f85149;`,error:`${je}background:rgba(248,81,73,0.14);border-left-color:#f85149;color:#ffa198;`,warning:`${je}background:rgba(210,153,34,0.14);border-left-color:#d29922;`,focus:`${je}background:rgba(56,139,253,0.1);border-left-color:#388bfd;`};function $s(e,t){if(t.size===0)return e;const n=Array.from(t.values()).some(o=>o.focus),i=e.split(`
`),r=[];for(let o=0;o<i.length;o++){const a=t.get(o);if(!a){n?r.push(`<span style="opacity:0.4">${i[o]}</span>`):r.push(i[o]);continue}let l=je;a.error?l=Te.error:a.warning?l=Te.warning:a.add?l=Te.add:a.remove?l=Te.remove:a.highlight?l=Te.highlight:a.focus&&(l=Te.focus);let d=i[o];if(a.word){const c=a.word.replace(/[.*+?^${}()|[\]\\]/g,"\\$&");d=d.replace(new RegExp(`(${c})`,"gi"),'<span style="background:rgba(255,235,59,0.3);border-radius:2px;padding:0 2px">$1</span>')}r.push(`<span style="${l}">${d}</span>`)}return r.join(`
`)}function Ee(e,t="text",n){const r=(t.trim()||"text").replace(/\{[^}]*\}/,"").replace(/\s+line-numbers\s*$/,"").trim()||"text",{cleanCode:o,annotations:a}=ks(e.trimEnd()),l=ws(o,r),d=$s(l,a),c=(n==null?void 0:n.lineNumbers)||/\bline-numbers\b/.test(t),p=n==null?void 0:n.maxHeight;let f="";if(c){const x=o.split(`
`).length;f=`<span class="code-line-numbers" aria-hidden="true">${Array.from({length:x},(u,y)=>`<span class="code-line-num">${y+1}</span>`).join(`
`)}</span>`}const g=["margin:0px","white-space:pre-wrap","overflow-wrap:anywhere","word-break:break-word","font-family:SFMono-Regular,Consolas,Monaco,monospace",c?"padding-left:3.5em":"",p?`max-height:${p};overflow-y:auto`:""].filter(Boolean).join(";");return`<section data-block="code" style="background:${bs};color:${ys};padding:${m[6]} ${m[7]};border-radius:${R.lg};overflow:hidden;margin:${m[6]} 0px;font-size:${E.sm};line-height:${X.relaxed}"><pre data-lang="${V(r)}" style="${g}">${f}<code style="background:none;color:inherit;padding:0;font-size:inherit;font-family:inherit;white-space:inherit;overflow-wrap:inherit;word-break:inherit">${d||"&nbsp;"}</code></pre></section>`}const ji="",Ni="",Ei="",_i="";function Ss(e){const t={entries:[]};let n=e.replace(/^(`{3,})([^\n]*)\n?([\s\S]*?)\n?\1\s*$/gm,(i,r,o,a)=>{i.includes(`
`)||(a=o,o="");const l=o.trim().split(/\s+/)[0];if(l==="mermaid")return i;const d=t.entries.length;return t.entries.push({type:"block",code:a,lang:l||"text"}),`
${ji}B${d}${Ni}
`});return n=n.replace(/`([^`\n]+)`/g,(i,r)=>{const o=t.entries.length;return t.entries.push({type:"inline",code:r}),`${Ei}I${o}${_i}`}),{text:n,store:t}}function js(e,t){let n=e;for(let i=0;i<t.entries.length;i++){const r=t.entries[i],o=r.type==="block"?`${ji}B${i}${Ni}`:`${Ei}I${i}${_i}`;let a;r.type==="block"?a=Ee(r.code,r.lang):a=`<code style="background:#f0f0f5;padding:2px 6px;border-radius:4px;font-size:13px;font-family:SF Mono,Consolas,monospace;color:#e83e8c">${V(r.code)}</code>`,r.type==="block"&&(n=n.split(`<p>${o}</p>`).join(a)),n=n.split(o).join(a)}return n}let it=null;function Ns(){return it||(it=$e(()=>import("./mermaid.core-YpAYt7oI.js").then(e=>e.ca),__vite__mapDeps([0,1,2])).then(e=>{const t=e.default;return t.initialize({startOnLoad:!1,theme:"neutral",securityLevel:"strict",htmlLabels:!1,flowchart:{useMaxWidth:!1}}),t}),it)}async function Es(e,t,n=!0){const i=await Ns(),r=document.createElement("div");r.style.cssText=`position:absolute;left:-9999px;top:0;width:${t}px;visibility:hidden`,document.body.appendChild(r);try{const o=`m2v-mermaid-${Math.random().toString(36).slice(2,10)}`,{svg:a}=await i.render(o,e,r);let l=a;return n&&(l=l.replace(/(<svg\b[^>]*?)\s+width="[^"]*"/gi,"$1").replace(/(<svg\b[^>]*?)\s+height="[^"]*"/gi,"$1")),{svg:l}}catch(o){return{svg:"",error:(o==null?void 0:o.message)||"图表渲染失败"}}finally{r.remove()}}const bt="m2v-secret-vault",_s=25e4,Cs=16,Ci=12,Ti=1,_t=/^[A-Za-z0-9+/]*={0,2}$/;function Ct(e){let t="";for(const n of e)t+=String.fromCharCode(n);return btoa(t)}function lt(e){const t=atob(e),n=new Uint8Array(t.length);for(let i=0;i<t.length;i++)n[i]=t.charCodeAt(i);return n}async function zi(e,t){const n=new TextEncoder,i=await crypto.subtle.importKey("raw",n.encode(e),"PBKDF2",!1,["deriveKey"]);return crypto.subtle.deriveKey({name:"PBKDF2",salt:t,iterations:_s,hash:"SHA-256"},i,{name:"AES-GCM",length:256},!1,["encrypt","decrypt"])}function Ai(){return typeof window>"u"?!1:window.isSecureContext}function Kt(){return Ai()&&typeof crypto<"u"&&!!crypto.subtle&&typeof localStorage<"u"}function Mi(){return typeof localStorage<"u"&&!!localStorage.getItem(bt)}function Ts(){typeof localStorage<"u"&&localStorage.removeItem(bt)}function zs(e){if(typeof e!="object"||e===null)return!1;const t=e;if(t.v!==Ti||typeof t.salt!="string"||!_t.test(t.salt)||typeof t.iv!="string"||!_t.test(t.iv)||typeof t.data!="string"||!_t.test(t.data))return!1;try{if(lt(t.iv).length!==Ci)return!1}catch{return!1}return!0}async function As(e,t){if(!Kt())throw new Error("当前环境不支持加密存储（需 HTTPS 或 localhost）");const n=new TextEncoder,i=crypto.getRandomValues(new Uint8Array(Cs)),r=crypto.getRandomValues(new Uint8Array(Ci)),o=await zi(t,i),a=await crypto.subtle.encrypt({name:"AES-GCM",iv:r},o,n.encode(JSON.stringify(e))),l={v:Ti,salt:Ct(i),iv:Ct(r),data:Ct(new Uint8Array(a))};localStorage.setItem(bt,JSON.stringify(l))}async function Ms(e){if(!Kt())throw new Error("当前环境不支持加密存储（需 HTTPS 或 localhost）");const t=localStorage.getItem(bt);if(!t)throw new Error("没有已保存的加密密钥");let n;try{n=JSON.parse(t)}catch{throw new Error("加密密钥数据已损坏（JSON 解析失败）")}if(!zs(n))throw new Error("加密密钥数据已损坏或格式不兼容");const i=n,r=lt(i.salt),o=lt(i.iv),a=await zi(e,r),l=await crypto.subtle.decrypt({name:"AES-GCM",iv:o},a,lt(i.data));return JSON.parse(new TextDecoder().decode(l))}function Is(e){const t=e.length;if(t===0)return{level:"weak",label:"口令为空"};const n=/[a-z]/.test(e),i=/[A-Z]/.test(e),r=/\d/.test(e),o=/[^a-zA-Z0-9]/.test(e),a=[n,i,r,o].filter(Boolean).length;return t<6||/^(123456|password|qwerty|111111|000000|abc123)$/i.test(e)?{level:"weak",label:"口令极弱，极易被爆破"}:t<8&&a<=2?{level:"weak",label:"口令较弱"}:t>=8&&a>=2&&a<4?{level:"fair",label:"口令强度一般"}:t>=8&&a>=3||t>=12?{level:"strong",label:"口令强度良好"}:{level:"fair",label:"口令强度一般"}}const Ls="m2v-images-db",Pe="images",Rs=64,Os=10*1024*1024,Ds=new Set(["image/jpeg","image/png","image/webp"]);let rt=null;function Ii(){return rt||(rt=new Promise((e,t)=>{if(typeof indexedDB>"u"){t(new Error("IndexedDB is not supported in this environment"));return}const n=indexedDB.open(Ls,1);n.onupgradeneeded=()=>{const i=n.result;i.objectStoreNames.contains(Pe)||i.createObjectStore(Pe)},n.onsuccess=()=>e(n.result),n.onerror=()=>t(n.error)}),rt)}async function Ps(e,t){const n=await Ii();return new Promise((i,r)=>{const l=n.transaction(Pe,"readwrite").objectStore(Pe).put(t,e);l.onsuccess=()=>i(),l.onerror=()=>r(l.error)})}async function Li(e){try{const t=await Ii();return await new Promise((n,i)=>{const a=t.transaction(Pe,"readonly").objectStore(Pe).get(e);a.onsuccess=()=>n(a.result||null),a.onerror=()=>i(a.error)})}catch(t){return console.error("IndexedDB error:",t),null}}function Vt(e){const t=e.toLowerCase()==="image/jpg"?"image/jpeg":e.toLowerCase();return Ds.has(t)?t:null}function Pt(e){const t=Vt(e);return t==="image/jpeg"?"jpg":t==="image/png"?"png":t==="image/webp"?"webp":null}async function Fs(e){const t=new Uint8Array(await e.slice(0,16).arrayBuffer());return t[0]===255&&t[1]===216&&t[2]===255?"image/jpeg":t[0]===137&&t[1]===80&&t[2]===78&&t[3]===71&&t[4]===13&&t[5]===10&&t[6]===26&&t[7]===10?"image/png":t[0]===82&&t[1]===73&&t[2]===70&&t[3]===70&&t[8]===87&&t[9]===69&&t[10]===66&&t[11]===80?"image/webp":null}async function Bs(e,t=Os){if(!e.type.startsWith("image/"))throw new Error("请选择图片文件");if(e.size>t)throw new Error(`图片不能超过 ${Math.round(t/1024/1024)}MB`);const n=Vt(e.type),i=await Fs(e);if(!i)throw new Error("不支持的图片格式，仅支持 JPG、PNG、WebP");if(n&&n!==i)throw new Error("图片文件类型与文件头不一致");return i}function Hs(e,t){const n=Pt(t.type)||Pt(e.type)||"jpg",i=e.name.replace(/\.[^.]+$/,"")||"image";return new File([t],`${i}.${n}`,{type:t.type})}function Us(e,t=1600,n=.7,i){const r=i||Vt(e.type)||"image/jpeg";return new Promise((o,a)=>{const l=new FileReader;l.onload=d=>{var p;const c=new Image;c.onload=()=>{const f=document.createElement("canvas");let g=c.width,x=c.height;g>t&&(x=Math.round(x*t/g),g=t),f.width=g,f.height=x;const h=f.getContext("2d");if(!h){a(new Error("Failed to get canvas context"));return}h.drawImage(c,0,0,g,x),f.toBlob(u=>{u?o(u):a(new Error("Canvas compression failed"))},r,r==="image/png"?void 0:n)},c.onerror=()=>a(new Error("Failed to load image")),c.src=(p=d.target)==null?void 0:p.result},l.onerror=()=>a(new Error("Failed to read file")),l.readAsDataURL(e)})}const he={},ve=new Map;function Ri(e){return he[e]}function qs(){const e=new Map;for(const[t,n]of Object.entries(he))e.set(n,t);return e}function ft(e){e.startsWith("blob:")&&URL.revokeObjectURL(e)}function Oi(e){const t=he[e];if(t)return ve.delete(e),ve.set(e,t),t}function Ws(e,t){const n=he[e];if(n===t)return ve.delete(e),ve.set(e,t),t;for(n&&ft(n),he[e]=t,ve.delete(e),ve.set(e,t);ve.size>Rs;){const i=ve.entries().next().value;if(!i)break;const[r,o]=i;ve.delete(r),delete he[r],ft(o)}return t}function Gs(e){const t=he[e];t&&(ft(t),delete he[e]),ve.delete(e)}function lf(){for(const e of Object.values(he))ft(e);for(const e of Object.keys(he))delete he[e];ve.clear()}function Ks(e){const t=new Set(e);for(const n of Object.keys(he))t.has(n)||Gs(n)}function Vs(e){return Array.from(e.matchAll(/!\[[^\]]*\]\((img:\/\/img_[a-zA-Z0-9_-]+)\)/g),t=>t[1].replace("img://",""))}async function Di(e){const t=Oi(e);if(t)return t;const n=await Li(e);return n?Ws(e,URL.createObjectURL(n)):""}async function cf(e){const t=Array.from(new Set(Vs(e)));Ks(t);const n=t.map(async i=>{he[i]?Oi(i):await Di(i)});n.length>0&&await Promise.all(n)}async function Ys(e){const t=new FormData;t.append("reqtype","fileupload"),t.append("fileToUpload",e);const n=await fetch("https://catbox.moe/user/api.php",{method:"POST",body:t}),r=(await n.text()).trim();if(!n.ok||!/^https?:\/\//i.test(r))throw new Error(r||`Catbox upload failed（HTTP ${n.status}）`);return r}async function Xs(e){const t=await e.arrayBuffer(),n=new Uint8Array(t);let i="";const r=32768;for(let l=0;l<n.length;l+=r)i+=String.fromCharCode(...n.subarray(l,l+r));const o=await fetch("/__markflow_image_host/catbox",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({filename:e.name,mime:e.type||"image/jpeg",data:btoa(i)})}),a=await o.text();if(!o.ok)throw new Error(a||`Catbox proxy upload failed（HTTP ${o.status}）`);return a.trim()}async function Zs(e){try{return await Ys(e)}catch{return Xs(e)}}async function Js(e,t){var a,l;const n=new FormData;n.append("smfile",e);const r=await(await fetch("https://sm.ms/api/v2/upload",{method:"POST",headers:{Authorization:t},body:n})).json();let o;if(r.success)o=(a=r.data)==null?void 0:a.url;else if(r.code==="image_repeated")o=typeof r.images=="string"?r.images:((l=r.data)==null?void 0:l.url)||r.data;else throw new Error(r.message||"Sm.ms upload failed");if(typeof o!="string"||!o)throw new Error("Sm.ms 返回的图片地址无效");return o}async function Qs(e,t){const n=(await $e(async()=>{const{default:a}=await import("./aliyun-oss-sdk-DKk5yfpl.js").then(l=>l.a);return{default:a}},__vite__mapDeps([3,1,4]))).default,i=new n({region:t.region,accessKeyId:t.accessKeyId,accessKeySecret:t.accessKeySecret,bucket:t.bucket,secure:!0}),r=Pi(e);return(await i.put(r,e)).url}async function eo(e,t){const n=(await $e(async()=>{const{default:o}=await import("./cos-js-sdk-v5-E1QkH1Uc.js").then(a=>a.c);return{default:o}},__vite__mapDeps([5,1]))).default,i=new n({SecretId:t.SecretId,SecretKey:t.SecretKey}),r=Pi(e);return new Promise((o,a)=>{i.putObject({Bucket:t.Bucket,Region:t.Region,Key:r,Body:e},(l,d)=>{if(l)a(l);else{const c=d.Location.startsWith("http")?d.Location:`https://${d.Location}`;o(c)}})})}function to(e){return new Promise((t,n)=>{const i=new FileReader;i.onloadend=()=>t(i.result),i.onerror=n,i.readAsDataURL(e)})}function Pi(e){const t=Pt(e.type)||e.name.split(".").pop()||"jpg";return`m2v-${Date.now()}-${Math.random().toString(36).substring(2,8)}.${t}`}function Tt(e){return Mi()?`${e}已加密保存，请先在「设置」中输入口令解锁后再上传`:`请先在「设置」中配置${e}`}async function no(e,t){var a,l,d,c,p;const n=await Bs(e),i=await Us(e,1600,.7,n),r=Hs(e,i);if(t.activeType==="catbox")return Zs(r);if(t.activeType==="smms"){if(!((a=t.smms)!=null&&a.token))throw new Error(Tt("Sm.ms Token"));return Js(r,t.smms.token)}if(t.activeType==="oss"){if(!((l=t.oss)!=null&&l.accessKeyId)||!((d=t.oss)!=null&&d.accessKeySecret))throw new Error(Tt("OSS 密钥"));return Qs(r,t.oss)}if(t.activeType==="cos"){if(!((c=t.cos)!=null&&c.SecretId)||!((p=t.cos)!=null&&p.SecretKey))throw new Error(Tt("COS 密钥"));return eo(r,t.cos)}const o=`img_${Date.now()}`;return await Ps(o,i),await Di(o),`img://${o}`}function te(e,t,n){{const o=[],a=l=>`${o.push(l)-1}`;e=e.replace(/`[^`]+`/g,a).replace(/!\[[^\]]*\]\([^)]+\)(?:\[[^\]]+\])?/g,a).replace(/\]\([^)]+\)/g,a),e=fi(e),e=e.replace(/\uE000(\d+)\uE001/g,(l,d)=>o[Number(d)])}e=e.replace(/__FN_(\d+)__\|([^|]+)\|/g,(o,a,l)=>`<span style="color:${t.accent};text-decoration:underline;text-decoration-style:dashed;text-underline-offset:3px;cursor:pointer">${_(l)}</span><sup style="color:${t.accent};font-size:0.75em;font-weight:${A.semibold}">[${parseInt(a)+1}]</sup>`),e=e.replace(/__FN_(\d+)__/g,(o,a)=>`<sup style="color:${t.accent};font-weight:${A.semibold};cursor:pointer">[${parseInt(a)+1}]</sup>`);const i=[];e=e.replace(/`([^`]+)`/g,(o,a)=>{const l=i.length;return i.push(`<code style="background:${D.gray100};padding:${m[0]} ${m[2]};border-radius:${R.sm};font-size:${E.base};font-family:SF Mono,Consolas,monospace;color:#e83e8c">${_(a)}</code>`),`\0CODE_${l}\0`});const r={info:{bg:"#e3f2fd",fg:"#1565c0",border:"#90caf9"},tip:{bg:"#e8f5e9",fg:"#2e7d32",border:"#a5d6a7"},warning:{bg:"#fff3e0",fg:"#e65100",border:"#ffcc80"},danger:{bg:"#fce4ec",fg:"#c62828",border:"#ef9a9a"}};return e=e.replace(/<Badge\s([^>]*)\s*\/?>/gi,(o,a)=>{const l=a.match(/type="([^"]*)"/i),d=a.match(/text="([^"]*)"/i),c=l?l[1]:"info",p=d?d[1]:c,f=r[c.toLowerCase()]||r.info;return`<span style="display:inline-block;padding:0 ${m[2]};margin:0 ${m[1]};border-radius:${R.sm};font-size:${E.xs};font-weight:${A.semibold};background:${f.bg};color:${f.fg};border:1px solid ${f.border};line-height:1.6;vertical-align:middle">${_(p)}</span>`}),e=e.replace(/<Icon\s+name="([^"]+)"[^/]*?(?:size="([^"]*)")?\s*\/?>/gi,(o,a,l)=>{const d=l||"1em";return`<img src="${dt(`https://api.iconify.design/${encodeURIComponent(a)}.svg`,"src")}" alt="${V(a)}" style="width:${d};height:${d};vertical-align:-0.125em;display:inline-block">`}),n&&(e=e.replace(new RegExp("(?<!\\$)(?<!\\d)\\$(?!\\d)([^$]+?)\\$(?!\\$|[\\w])","g"),(o,a)=>{const l=n.get(`i:${a}`);return l||`<code style="font-style:italic;background:${D.gray100};padding:1px 4px;border-radius:${R.sm}">${V(a)}</code>`})),e=e.replace(/==([^=]+)==/g,(o,a)=>`<span style="background:linear-gradient(120deg,rgba(${t.rgb},0.1) 0%,rgba(${t.rgb},0.16) 100%);padding:0px ${m[2]};border-radius:${R.sm};font-weight:${A.bold};color:${t.accent}">${_(a)}</span>`),e=e.replace(/!!([^!]+)!!/g,(o,a)=>`<span style="display:inline-block;padding:0 ${m[2]};border-radius:20px;font-size:${E.md};font-weight:${A.semibold};background:${t.light};color:${t.accent};border:1px solid ${t.border}">${_(a)}</span>`),e=e.replace(/\^\^([^^]+)\^\^/g,(o,a)=>`<strong style="color:${t.accent}">${_(a)}</strong>`),e=e.replace(/::([^:]+)::/g,(o,a)=>`<span style="color:${Ur(t.accent,.15)};font-weight:${A.bold}">${_(a)}</span>`),e=e.replace(/__([^_]+)__/g,(o,a)=>`<span style="text-decoration:underline;text-decoration-color:${t.accent};text-underline-offset:3px">${_(a)}</span>`),e=e.replace(/~~([^~]+)~~/g,(o,a)=>`<del style="color:${D.gray500}">${_(a)}</del>`),e=e.replace(/~([^~]+)~/g,(o,a)=>`<sub>${_(a)}</sub>`),e=e.replace(/\^([^^]+)\^/g,(o,a)=>`<sup>${_(a)}</sup>`),e=e.replace(/\*\*([^*]+)\*\*/g,(o,a)=>`<strong style="font-weight:${A.extrabold};color:${U.textPrimary}">${_(a)}</strong>`),e=e.replace(/\*([^*]+)\*/g,(o,a)=>`<em>${_(a)}</em>`),e=e.replace(/!\[([^\]]*)\]\(([^)]+)\)(?:\[([^\]]+)\])?/g,(o,a,l,d)=>{let c=l;if(l.startsWith("img://")){const f=l.replace("img://","");c=Ri(f)||l}const p=dt(c,"src");if(d){const f=d.split(/\s+/),g=f[0]||"100%",x=f[1]||"250px";return`<img src="${V(p)}" alt="${V(a)}" style="width:${g};max-height:${x};border-radius:${R.md};display:block">`}return`<img src="${V(p)}" alt="${V(a)}" style="max-width:100%;border-radius:${R.md};display:block">`}),e=e.replace(/\x00CODE_(\d+)\x00/g,(o,a)=>i[parseInt(a)]||o),e=e.replace(/\n{2,}/g,`
`).replace(/[ \t]*\n[ \t]*/g,"<br>"),e}const io={red:"#e74c3c",orange:"#f39c12",yellow:"#f1c40f",green:"#27ae60",blue:"#3498db",purple:"#9b59b6",pink:"#e91e8a",cyan:"#00bcd4",teal:"#009688",indigo:"#3f51b5",gray:"#9e9e9e",grey:"#9e9e9e",black:"#222222",white:"#ffffff",crimson:"#dc143c",coral:"#ff6f61",tomato:"#ff6347",salmon:"#fa8072",gold:"#ffd700",lime:"#32cd32",navy:"#1a237e",maroon:"#800000",olive:"#808000",aqua:"#00ffff",fuchsia:"#ff00ff",silver:"#c0c0c0",skyblue:"#87ceeb",plum:"#dda0dd",sienna:"#a0522d",chocolate:"#d2691e",wheat:"#f5deb3",tan:"#d2b48c",violet:"#ee82ee",peach:"#ffdab9",mint:"#98ff98",lavender:"#e6e6fa",beige:"#f5f5dc"};function Ve(e){if(!e)return"";if(e.startsWith("#"))return e;const t=e.match(/rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/);if(t){const n=parseInt(t[1]).toString(16).padStart(2,"0"),i=parseInt(t[2]).toString(16).padStart(2,"0"),r=parseInt(t[3]).toString(16).padStart(2,"0");return`#${n}${i}${r}`}return io[e.toLowerCase()]||e}function Ft(e,t=.12){const n=Ve(e);if(n.startsWith("#")&&n.length===7){const i=parseInt(n.slice(1,3),16),r=parseInt(n.slice(3,5),16),o=parseInt(n.slice(5,7),16);return`rgba(${i},${r},${o},${t})`}return`rgba(0,0,0,${t})`}function ro(e,t=.15){const n=Ve(e);if(n.startsWith("#")&&n.length===7){const i=Math.round(parseInt(n.slice(1,3),16)*(1-t)),r=Math.round(parseInt(n.slice(3,5),16)*(1-t)),o=Math.round(parseInt(n.slice(5,7),16)*(1-t));return`#${Math.min(i,255).toString(16).padStart(2,"0")}${Math.min(r,255).toString(16).padStart(2,"0")}${Math.min(o,255).toString(16).padStart(2,"0")}`}return n}const Fi={id:"CTA_DA01",name:"行动号召",tag:"cta",attrs:[{key:"label",label:"标签",required:!1,default:"",description:"顶部引导标签，如 GET STARTED"},{key:"title",label:"标题",required:!1,default:"",description:"主标题文字，用于号召行动"},{key:"action",label:"按钮文字",required:!1,default:"",description:"按钮上显示的文字，如「立即复制下方代码」"},{key:"color",label:"自定义颜色",required:!1,default:"",description:"自定义颜色，填入 CSS 颜色值覆盖默认主题色，使用颜色单词或十六进制颜色值"},{key:"light",label:"浅色背景",required:!1,default:"",description:"设置为任意值启用浅色背景模式"}],example:'<cta label="GET STARTED" title="准备好用模块化排版改造你的下一篇文章了吗？" action="打开组件库 → 挑选模块 → 开始创作"></cta>',render(e,t,n){const i=Ve(e.color||n.accent);if(e.light){const a=`${i}0f`;let l=`<section style="margin:${m[10]} 0px;padding:${m[13]} ${m[9]};background:${a};border-radius:${R["4xl"]};text-align:center">`;return e.label&&(l+=`<p style="margin:0px 0px ${m[3]};font-size:${E.xs};letter-spacing:${K["5xl"]};font-weight:${A.bold};color:${i}">${_(e.label)}</p>`),e.title&&(l+=`<p style="margin:0px 0px ${m[7]};font-size:${E["4xl"]};font-weight:${A.extrabold};line-height:${X.normal};color:${U.textPrimary}">${_(e.title)}</p>`),e.action&&(l+=`<span style="display:inline-block;padding:${m[5]} ${m[9]};background:${i};border-radius:${R.lg};font-weight:${A.bold};letter-spacing:${K.widest};color:${U.surface}">${_(e.action)}</span>`),t.trim()&&(l+=`<section style="margin-top:${m[7]};font-size:${E.md};color:${U.ink};line-height:${X.looser}">${_(t.trim())}</section>`),l+="</section>",l}const r=ro(i);let o=`<section style="margin:${m[10]} 0px;padding:${m[13]} ${m[9]};background:linear-gradient(135deg,${i},${r});border-radius:${R["4xl"]};text-align:center;color:${U.surface}">`;return e.label&&(o+=`<p style="margin:0px 0px ${m[3]};font-size:${E.xs};letter-spacing:${K["5xl"]};font-weight:${A.bold};opacity:0.8">${_(e.label)}</p>`),e.title&&(o+=`<p style="margin:0px 0px ${m[7]};font-size:${E["4xl"]};font-weight:${A.extrabold};line-height:${X.normal}">${_(e.title)}</p>`),e.action&&(o+=`<span style="display:inline-block;padding:${m[5]} ${m[9]};background:rgba(255,255,255,0.2);border-radius:${R.lg};font-weight:${A.bold};letter-spacing:${K.widest};backdrop-filter:blur(4px)">${_(e.action)}</span>`),t.trim()&&(o+=`<section style="margin-top:${m[7]};font-size:${E.md};opacity:0.85;line-height:${X.looser}">${_(t.trim())}</section>`),o+="</section>",o}},Bi={id:"Badges_DA01",name:"彩色标签徽章",tag:"badges",attrs:[{key:"type",label:"风格色调",required:!1,default:"accent",options:["accent","green","yellow","dark"]},{key:"color",label:"文字颜色",required:!1,default:""},{key:"bg",label:"背景颜色",required:!1,default:""}],example:'<badges type="accent">模块化排版|48套主题|长图文|公众号|知识分享|AI排版</badges>',render(e,t,n){const i=t.split("|").map(f=>f.trim()).filter(Boolean),r=e.type||"accent",o={green:{bg:"#e8f5e9",color:"#2e7d32",border:"#a5d6a7"},yellow:{bg:"#fff9c4",color:"#f57f17",border:"#fff176"},dark:{bg:"#263238",color:"#eceff1",border:"#455a64"},accent:{bg:n.accent+"18",color:n.accent,border:n.accent+"50"}},a=o[r]||o.accent,l=e.color||a.color,d=e.bg||a.bg,c=e.bg?e.bg+"50":a.border;let p=`<section style="display:flex;gap:${m[3]};flex-wrap:wrap;margin:${m[6]} 0px;align-items:center">`;return i.forEach(f=>{p+=`<span style="display:inline-flex;align-items:center;gap:${m[1]};padding:${m[1]} ${m[5]};border-radius:${R.full};font-size:${E.base};font-weight:${A.semibold};background:${d};color:${l};border:1px solid ${c};line-height:${X.loosest};white-space:nowrap">${_(f)}</span>`}),p+="</section>",p}};function so(e,t,n){const r=(t?t.replace(/---[\s\S]*?---\s*/,"").replace(/[#*`>[\]!|_~=-]/g,"").replace(/\s+/g,""):"").length,o=Math.max(1,Math.ceil(r/400));let a=`<section style="margin:0px 0px ${m[12]};${Ke.card};border-radius:${R["3xl"]};border:1px solid ${D.gray250};overflow:hidden;background:linear-gradient(135deg,${D.gray100} 0%,rgb(238,244,251) 100%)">`;return a+=`<section style="padding:${m[9]};background:${U.surface}eb">`,a+='<section class="tableWrapper"><table style="border:0px;border-collapse:collapse;table-layout:fixed;min-width:115px;width:100%;margin-bottom:0"><colgroup><col><col style="width:90px;"></colgroup><tbody><tr>',a+='<td valign="top" align="left" style="vertical-align:top;border:0px;padding:0px;text-align:left">',e.badge&&(a+=`<p style="margin:0px;padding:0px 0px ${m[4]};font-size:${E["2xs"]};color:${n.accent};letter-spacing:${K["3xl"]};text-transform:uppercase;font-weight:${A.extrabold}">${_(e.badge)}</p>`),e.title&&(a+=`<p style="margin:0px;font-size:${E["7xl"]};font-weight:${A.black};color:${U.textPrimary};line-height:${X.tight};letter-spacing:${K.tight};word-break:break-all">${_(e.title)}</p>`),e.subtitle&&(a+=`<p style="margin:0px;padding:${m[4]} 0px 0px;font-size:${E.md};color:${U.ink};line-height:${X.looser};font-weight:${A.normal};text-align:justify;letter-spacing:${K.wide}">${_(e.subtitle)}</p>`),e.chips&&(a+=`<section style="margin:0px;padding:${m[4]} 0px 0px;font-size:0px;line-height:${X.loosest}">`,e.chips.split("|").forEach(l=>{a+=`<span style="display:inline-block;margin:0px ${m[3]} 0px 0px;font-size:${E["2xs"]};color:#576B95;font-weight:${A.bold};letter-spacing:0.02em;white-space:nowrap">${_("#"+l.trim())}</span>`}),a+="</section>"),a+="</td>",a+='<td data-colwidth="90" width="90" valign="top" align="right" style="vertical-align:top;border:0px;padding:0px;text-align:right">',a+=`<p style="margin:0px 0px ${m[3]};font-size:${E["2xs"]};line-height:${X.normal};color:${U.inkFaint};font-weight:${A.extrabold};letter-spacing:${K.wide}">${_("预计阅读(分)")}</p>`,a+=`<section style="display:inline-block;width:64px;height:64px;line-height:64px;text-align:center;border-radius:${R.xl};background-color:${n.accent};${Ke.float}"><span style="font-size:${E["8xl"]};line-height:64px;color:${U.surface};font-weight:${A.black};letter-spacing:${K.tighter}">${_(o)}</span></section>`,a+=`<p style="margin:${m[3]} 0px 0px;font-size:${E["2xs"]};color:${U.inkFaint};font-weight:${A.bold};letter-spacing:${K.wide}">${_("共 "+r+" 字")}</p>`,a+="</td>",a+="</tr></tbody></table></section>",a+="</section></section>",a}function oo(e,t,n){let i=t;const r=be(e[i]);for(i++;i<e.length&&!/^:::\s*$/.test(e[i]);)i++;if(i>=e.length)return null;i++;let o=`<section style="margin:${m[10]} 0px;padding:${m[13]} ${m[9]};background:linear-gradient(135deg,${n.accent},${n.dark});border-radius:${R["4xl"]};text-align:center;color:${U.surface}">`;return r.label&&(o+=`<p style="margin:0px 0px ${m[3]};font-size:${E.xs};letter-spacing:${K["5xl"]};font-weight:${A.bold};opacity:0.8">${_(r.label)}</p>`),r.title&&(o+=`<p style="margin:0px 0px ${m[7]};font-size:${E["4xl"]};font-weight:${A.extrabold};line-height:${X.normal}">${_(r.title)}</p>`),r.button&&(o+=`<span style="display:inline-block;padding:${m[5]} ${m[9]};background:rgba(255,255,255,0.2);border-radius:${R.lg};font-weight:${A.bold};letter-spacing:${K.widest};backdrop-filter:blur(4px)">${_(r.button)}</span>`),o+="</section>",{html:o,next:i}}function ao(e,t,n){let i=t;const r=e[i].match(/<cta\s*(.*)>/),o=r&&r[1]?be(r[1]):{};i++;let a="";for(;i<e.length&&!/^<\/cta>/.test(e[i]);)a+=e[i]+`
`,i++;return i>=e.length?null:(i++,{html:Fi.render(o,a,n),next:i})}function lo(e,t,n){const i=be(e[t]);let r=`<section style="margin:24px 0px;padding:32px 24px;background:linear-gradient(135deg,${n.accent},${n.dark});border-radius:16px;text-align:center;color:rgb(255,255,255)">`;return i.label&&(r+=`<p style="margin:0px 0px 8px;font-size:11px;letter-spacing:3px;font-weight:700;opacity:0.8">${_(i.label)}</p>`),i.title&&(r+=`<p style="margin:0px 0px 16px;font-size:20px;font-weight:800;line-height:1.4">${_(i.title)}</p>`),i.button&&(r+=`<span style="display:inline-block;padding:12px 32px;background:rgba(255,255,255,0.2);border-radius:8px;font-weight:700;letter-spacing:1px;backdrop-filter:blur(4px)">${_(i.button)}</span>`),r+="</section>",{html:r,next:t+1}}function co(e,t,n){let i=t;const r=e[i].match(/>\s*\[!?(TIP|NOTE|INFO|WARNING|CAUTION|IMPORTANT)\]\s*(.*)/i),o=r?r[1].toUpperCase():"NOTE",a=r?r[2]:"";i++;let l="";for(;i<e.length&&/^>/.test(e[i]);)l+=e[i].replace(/^>\s?/,"")+`
`,i++;const d={TIP:"💡",NOTE:"📝",INFO:"ℹ️",WARNING:"⚠️",CAUTION:"🚨",IMPORTANT:"❗"},c={TIP:"#f0fdf4",NOTE:"#eff6ff",INFO:"#f0f9ff",WARNING:"#fffbea",CAUTION:"#fef2f2",IMPORTANT:"#f5f3ff"},p={TIP:"#16a34a",NOTE:"#2563eb",INFO:"#0ea5e9",WARNING:"#ea580c",CAUTION:"#dc2626",IMPORTANT:"#7c3aed"},f=c[o]||"#f0f4fa",g=p[o]||n.accent;let x=`<section style="margin:${m[7]} 0px;padding:${m[7]} ${m[6]};background:${f};border-left:4px solid ${g};border-radius:0px ${R.xl} ${R.xl} 0px">`;return a&&(x+=`<p style="margin:0px 0px ${m[2]};font-size:${E.xl};font-weight:${A.bold};color:${g}">${V((d[o]||"")+" "+a)}</p>`),l.trim()&&(x+=`<section style="font-size:${E.xl};color:${D.gray700};line-height:${X.looser};letter-spacing:${K.wider};text-align:justify">${te(l.trim(),n)}</section>`),x+="</section>",{html:x,next:i}}function po(e,t){let n=t;const i=[],r=/!\[([^\]]*)\]\(([^)]+)\)/g;let o;for(;(o=r.exec(e[n]))!==null;)i.push({alt:o[1],src:o[2]});n++;let a=`<section style="white-space:nowrap;overflow-x:auto;margin:${m[5]} 0px;padding:${m[1]} 0px">`;return i.forEach(l=>{a+=`<img src="${V(l.src)}" alt="${V(l.alt)}" style="display:inline-block;vertical-align:top;max-height:200px;border-radius:${R.lg};margin-right:${m[3]}">`}),a+="</section>",{html:a,next:n}}const ue={card:`margin:0px 0px ${m[12]};${Ke.card};border-radius:${R["3xl"]};border:1px solid ${D.gray250};overflow:hidden;background:linear-gradient(135deg,${D.gray100} 0%,rgb(238,244,251) 100%)`,body:`padding:${m[9]};background:${U.surface}eb`,badge:e=>`margin:0px;padding:0px 0px ${m[4]};font-size:${E["2xs"]};color:${e};letter-spacing:${K["3xl"]};text-transform:uppercase;font-weight:${A.extrabold}`,title:`margin:0px;font-size:${E["7xl"]};font-weight:${A.black};color:${U.textPrimary};line-height:${X.tight};letter-spacing:${K.tight};word-break:break-all`,subtitle:`margin:0px;padding:${m[4]} 0px 0px;font-size:${E.md};color:${U.ink};line-height:${X.looser};font-weight:${A.normal};text-align:justify;letter-spacing:${K.wide}`,chips:`margin:0px;padding:${m[4]} 0px 0px;font-size:0px;line-height:${X.loosest}`,chip:`display:inline-block;margin:0px ${m[3]} 0px 0px;font-size:${E["2xs"]};color:#576B95;font-weight:${A.bold};letter-spacing:0.02em;white-space:nowrap`,statLabel:`margin:0px 0px ${m[3]};font-size:${E["2xs"]};line-height:${X.normal};color:${U.inkFaint};font-weight:${A.extrabold};letter-spacing:${K.wide}`,statNum:e=>`display:inline-block;width:64px;height:64px;line-height:64px;text-align:center;border-radius:${R.xl};background-color:${e};`+Ke.float,numText:`font-size:${E["8xl"]};line-height:64px;color:${U.surface};font-weight:${A.black};letter-spacing:${K.tighter}`,charCount:`margin:${m[3]} 0px 0px;font-size:${E["2xs"]};color:${U.inkFaint};font-weight:${A.bold};letter-spacing:${K.wide}`,tdLeft:"vertical-align:top;border:0px;padding:0px;text-align:left",tdRight:"vertical-align:top;border:0px;padding:0px;text-align:right",table:"border:0px;border-collapse:collapse;table-layout:fixed;min-width:115px;width:100%;margin-bottom:0"};function fo(e){const n=e.replace(/<title[\s\S]*?<\/title>\s*/,"").replace(/[#*`>[\]!|_~=-]/g,"").replace(/\s+/g,"").length;return{chars:n,minutes:Math.max(1,Math.ceil(n/400))}}function go(e){return e.split("|").map(t=>`<span style="${ue.chip}">${_("#"+t.trim())}</span>`).join("")}const Hi={id:"Title_DA01",name:"标题卡片",tag:"title",attrs:[{key:"type",label:"样式类型",required:!1,default:"DA01",options:["DA01","DA02"]},{key:"label",label:"标签",required:!1,default:""},{key:"subtitle",label:"副标题",required:!1,default:""},{key:"chips",label:"关键词（|分隔）",required:!1,default:""},{key:"color",label:"自定义颜色",required:!1,default:""}],example:'<title type="DA01" label="GUIDE" subtitle="涵盖标题卡片、步骤流程、时间线、对比卡片、代码块、提示框等全部 61 个排版组件，每个组件均提供可复制的语法模板与属性说明。" chips="公众号排版|长图文|组件化|知识分享">MarkFlow 排版组件完全指南</title>',render(e,t,n,...i){const r=i[0]||"",{chars:o,minutes:a}=fo(r),l=e.color||n.accent;return`
      <section style="${ue.card}">
        <section style="${ue.body}">
          <section class="tableWrapper">
            <table style="${ue.table}">
              <colgroup><col><col style="width:90px;"></colgroup>
              <tbody><tr>
                <td valign="top" align="left" style="${ue.tdLeft}">
                  ${e.label?`<p style="${ue.badge(l)}">${_(e.label)}</p>`:""}
                  ${t?`<p style="${ue.title}">${_(t)}</p>`:""}
                  ${e.subtitle?`<p style="${ue.subtitle}">${_(e.subtitle)}</p>`:""}
                  ${e.chips?`<section style="${ue.chips}">${go(e.chips)}</section>`:""}
                </td>
                <td data-colwidth="90" width="90" valign="top" align="right" style="${ue.tdRight}">
                  <p style="${ue.statLabel}">${_("预计阅读(分)")}</p>
                  <section style="${ue.statNum(l)}">
                    <span style="${ue.numText}">${_(a)}</span>
                  </section>
                  <p style="${ue.charCount}">${_(`共 ${o} 字`)}</p>
                </td>
              </tr></tbody>
            </table>
          </section>
        </section>
      </section>`}},we={card:"margin:0px 0px 30px;box-shadow:rgba(15,23,42,0.05) 0px 10px 24px;border-radius:14px;border:1px solid rgba(229,231,235,0.9);overflow:hidden;background:linear-gradient(135deg,rgb(248,250,252) 0%,rgb(238,244,251) 100%)",body:"padding:20px;background:rgba(255,255,255,0.92)",badgeRow:"margin:0px 0px 10px;white-space:nowrap",badge:e=>`margin:0px;padding:0px;font-size:10px;color:${e};letter-spacing:2.4px;text-transform:uppercase;font-weight:800;white-space:nowrap`,stat:"margin:0px 0px 0px 12px;display:inline-block;font-size:10px;color:rgb(148,163,184);font-weight:700;letter-spacing:0.3px;line-height:1;white-space:nowrap",statNum:e=>`font-size:12px;font-weight:900;color:${e}`,title:"margin:0px;font-size:28px;font-weight:900;color:rgb(17,24,39);line-height:1.2;letter-spacing:-0.5px;word-break:break-all",subtitle:"margin:0px;padding:10px 0px 0px;font-size:14px;color:rgb(71,85,105);line-height:1.7;font-weight:400;text-align:justify;letter-spacing:0.3px",chips:"margin:0px;padding:10px 0px 0px;font-size:0px;line-height:1.8",chip:"display:inline-block;margin:0px 8px 0px 0px;font-size:10px;color:#576B95;font-weight:700;letter-spacing:0.02em;white-space:nowrap"};function xo(e){const n=e.replace(/<title[\s\S]*?<\/title>\s*/,"").replace(/[#*`>[\]!|_~=-]/g,"").replace(/\s+/g,"").length;return{chars:n,minutes:Math.max(1,Math.ceil(n/400))}}function uo(e){return e.split("|").map(t=>`<span style="${we.chip}">${_("#"+t.trim())}</span>`).join("")}const Ui={id:"Title_DA02",name:"标题卡片",tag:"title",attrs:[{key:"type",label:"样式类型",required:!1,default:"DA02",options:["DA01","DA02"]},{key:"label",label:"标签",required:!1,default:""},{key:"subtitle",label:"副标题",required:!1,default:""},{key:"chips",label:"关键词（|分隔）",required:!1,default:""},{key:"color",label:"自定义颜色",required:!1,default:""}],example:'<title type="DA02" label="UPDATE" subtitle="v2.0 新增段落标题、步骤流程、时间线、对比卡片、提示框等 12 个高级排版组件，主题系统扩展至 52 套专业配色方案。" chips="新组件|52套主题|性能优化|公众号适配">MarkFlow v2.0 版本更新说明</title>',render(e,t,n,...i){const r=i[0]||"",{chars:o,minutes:a}=xo(r),l=e.color||n.accent,d=e.label?`<span style="${we.badge(l)}">${_(e.label)}</span>`:"",c=`<span style="${we.stat}">${_("预计阅读 ")}<span style="${we.statNum(l)}">${_(a)}</span>${_(` 分钟 · 共 ${o} 字`)}</span>`;return`
      <section style="${we.card}">
        <section style="${we.body}">
          <section style="${we.badgeRow}">
            ${d}
            ${c}
          </section>
          ${t?`<p style="${we.title}">${_(t)}</p>`:""}
          ${e.subtitle?`<p style="${we.subtitle}">${_(e.subtitle)}</p>`:""}
          ${e.chips?`<section style="${we.chips}">${uo(e.chips)}</section>`:""}
        </section>
      </section>`}},qi={id:"PTitle_DA01",name:"段落标题",tag:"p-title",attrs:[{key:"number",label:"序号",required:!1,default:"",description:"序号数字，如 01 / 02 / 03"},{key:"title",label:"标题文字",required:!1,default:"",description:"标题文字"},{key:"subtitle",label:"副标题",required:!1,default:"",description:"副标题，如英文翻译或补充说明"},{key:"color",label:"标题颜色",required:!1,default:"",description:"标题文字颜色，使用颜色单词或十六进制颜色值"},{key:"num-color",label:"序号颜色",required:!1,default:"",description:"序号数字的颜色，使用颜色单词或十六进制颜色值"},{key:"subtitle-color",label:"副标题颜色",required:!1,default:"",description:"副标题文字颜色，使用颜色单词或十六进制颜色值"},{key:"level",label:"层级",required:!1,default:"1",options:["1","2","3","4"],description:"标题层级：1 最大（对应 H1），4 最小（对应 H4）"},{key:"size",label:"尺寸（level=1）",required:!1,default:"normal",options:["normal","medium","small"],description:"仅 level=1 时生效：normal（默认）/ medium（中等）/ small（缩小）"},{key:"prefix",label:"前缀图标",required:!1,default:"",description:"标题前的图标，如 🚀、⚡、🔥，留空则不显示"},{key:"suffix",label:"后缀图标",required:!1,default:"",description:"标题后的图标，如 ✅、💡、→，留空则不显示"},{key:"hide",label:"隐藏元素（level=1）",required:!1,default:"",options:["","num","line"],description:"隐藏指定元素：num（隐藏序号）/ line（隐藏章节线及横线），留空则全部显示"}],example:'<p-title number="01" title="它解决什么问题" subtitle="ONE SOURCE · MULTI OUTPUT" level="1" size="normal"></p-title>',render(e,t,n){const i=e.number||"",r=e.title||t,o=e.subtitle,a=parseInt(e.level||"1",10),l=n.accent,d=e.color||U.textPrimary,c=e["num-color"]||l,p=e["subtitle-color"]||l,f=i!=="",g=e.prefix||"",x=e.suffix||"",h=g!=="",u=x!=="",y=e.hide||"";if(a===1){const S=e.size||"normal";let C,O,F,Q,W,Z,I,L;S==="small"?(C=E["10xl"],O=E["4xl"],F="-40px",Q="34px",W="34px",Z=E["2xs"],I="8px",L=`${m[11]} 0px ${m[7]}`):S==="medium"?(C=E["11xl"],O=E["6xl"],F="-48px",Q="40px",W="40px",Z=E["2xs"],I=E["2xs"],L=`${m[12]} 0px ${m[9]}`):(C=E["12xl"],O=E["8xl"],F="-60px",Q="50px",W="50px",Z=E.xs,I=E["2xs"],L=`${m[13]} 0px ${m[10]}`);const B=f&&y!=="num"?`<strong style="display:block;font-size:${C};line-height:1;color:${c};letter-spacing:${K.tighter};white-space:nowrap;opacity:0.25"><span leaf="">${V(i)}</span></strong>`:"",G=f&&y!=="num"?`<strong style="display:block;font-size:${O};font-weight:${A.black};color:${d};line-height:1.26;letter-spacing:${K.tight};margin-top:${F};margin-left:${Q}"><span leaf="">${h?g+" ":""}${V(r)}${u?" "+x:""}</span></strong>`:`<strong style="display:block;font-size:${O};font-weight:${A.black};color:${d};line-height:1.26;letter-spacing:${K.tight}"><span leaf="">${h?g+" ":""}${V(r)}${u?" "+x:""}</span></strong>`,se=o?`<span style="display:block;margin-left:${f&&y!=="num"?W:"0"};font-size:${Z};color:${p};font-weight:${A.bold};text-transform:uppercase;letter-spacing:1.6px"><span leaf="">${_(o)}</span></span>`:"",M=f&&y!=="line"?`<section style="display:flex;align-items:center;margin:0;padding-bottom:${m[5]}"><span style="font-size:${I};font-weight:${A.extrabold};color:${U.inkFaint};letter-spacing:2.6px;text-transform:uppercase;white-space:nowrap"><span leaf="">CHAPTER ${i}</span></span><section style="flex:1;border-top:1px solid ${D.gray250};margin:0 0 0 ${m[5]};height:0"></section></section>`:"";return`
<section style="margin:${L}">
  <section style="clear:both">
    ${M}
    <section style="margin:0">
      ${B}
      ${G}
      ${se}
    </section>
  </section>
</section>`}const j=(S,C)=>f?['<span style="',"display:inline-flex;align-items:center;justify-content:center;",`width:${S};height:${S};min-width:${S};`,"border-radius:50%;",`background:${c};color:#fff;`,`font-weight:${A.black};font-size:${C};`,`letter-spacing:${K.tight};`,"flex-shrink:0;line-height:1;",`box-shadow:0 2px 8px ${c}33;`,`"><span leaf="">${i}</span></span>`].join(""):"",w=(S,C,O,F)=>['<span style="',"display:inline-block;",`background:linear-gradient(135deg,${c}0c,${c}06);`,`padding:${F};border-radius:8px;`,`font-size:${C};font-weight:${O};color:${d};`,`line-height:1.4;letter-spacing:${K.tight};`,`"><span leaf="">${S}</span></span>`].join(""),N=(S,C,O,F,Q,W,Z,I)=>{const L=j(C,O),B=w(S,F,Q,W);return L?`<section style="display:flex;align-items:center;gap:${Z};margin:${I} 0 0 0">${L}${B}</section>`:`<section style="margin:${I} 0 0 0">${B}</section>`},b=(S,C,O,F,Q)=>{if(!o)return"";const W=f?`calc(${S} + ${C})`:"0";return`<p style="margin:${Q} 0 0 ${W};font-size:${O};color:${p};font-weight:${A.semibold};text-transform:uppercase;letter-spacing:${F}"><span leaf="">${_(o)}</span></p>`};if(a===2){const S=`${h?g+" ":""}${_(r)}${u?" "+x:""}`;return`
<section style="margin:${m[12]} 0px ${m[9]};padding-left:0px">
  ${N(S,"34px",E.base,E["2xl"],A.extrabold,`${m[2]} ${m[5]}`,m[4],"0")}
  ${b("34px",m[4],E.xs,"1.6px","-12px")}
</section>`}if(a===3){const S=`${h?g+" ":""}${_(r)}${u?" "+x:""}`;return`
<section style="margin:${m[11]} 0px ${m[7]};padding-left:20px">
  ${N(S,"28px",E.sm,E.xl,A.bold,`${m[1]} ${m[4]}`,m[3],"0")}
  ${b("28px",m[3],E["2xs"],"1.4px","-10px")}
</section>`}const $=`${h?g+" ":""}${_(r)}${u?" "+x:""}`;return`
<section style="margin:${m[10]} 0px ${m[5]};padding-left:40px">
  ${N($,"24px",E.xs,E.base,A.semibold,`${m[1]} ${m[3]}`,m[3],"0")}
  ${b("24px",m[3],E["2xs"],"1.4px","-8px")}
</section>`}};function Wi(e){const t={};for(const n of e.split(`
`)){const i=n.trim();if(!i)continue;const r=i.indexOf(":");if(r<0)continue;const o=i.slice(0,r).trim(),a=i.slice(r+1).trim();o&&(t[o]=a)}return t}function Gi(e){const t=[];for(const n of e.split(`
`)){let i=n.trim();if(!i)continue;i=i.replace(/^-\s+/,"");const r=i.split("|").map(o=>o.trim()).filter((o,a,l)=>!(a===l.length-1&&o===""));r.length>0&&t.push(r)}return t}function _e(e){const t=e.trim();if(!t)return null;try{return JSON.parse(t)}catch{return null}}function Ye(e){const t=e.trim();if(!t)return null;try{const n=JSON.parse(t);return Array.isArray(n)?n:null}catch{return null}}function Ki(e){var n;return(((n=e[e.length-1])==null?void 0:n.toLowerCase())??"")==="accent"}function Vi(e){var n;const t=((n=e[e.length-1])==null?void 0:n.toLowerCase())??"";return t==="accent"||t==="default"?e.slice(0,-1):e}function ge(e,t="fields"){return{fields:t==="fields"?Wi(e):{},rows:t==="rows"?Gi(e):[],json:t==="json_object"?_e(e):t==="json_array"?Ye(e):null,markdown:t==="markdown"?e:""}}function mo(e,t){var c;const n=e.spec;if(!n.example)return"";const i=n.example.split(`
`),r=i[0].match(/^:::\s*\S+\s*(.*)/),o=(c=r==null?void 0:r[1])!=null&&c.trim()?be(r[1]):{},a=[];for(let p=1;p<i.length&&!/^:::\s*$/.test(i[p].trim());p++)a.push(i[p]);const l=a.join(`
`).trim(),d=ge(l,n.bodyFormat);return e.render?e.render(o,l,d,t):""}function ye(e){const t=new RegExp(`^:::\\s*${e.spec.name}\\b`);return{name:`unified-${e.spec.name}`,priority:20,match:n=>t.test(n),render:(n,i,r,o)=>{var g;const a=i.match(/^:::\s*\S+\s*(.*)/),l=(g=a==null?void 0:a[1])!=null&&g.trim()?be(a[1]):{},d=[];let c=o+1;const p=80;for(;c<r.length&&!/^:::\s*$/.test(r[c]);)if(d.push(r[c]),c++,c-o>p){const x=d.join(`
`).trim();return{html:e.render(l,x,ge(x,e.spec.bodyFormat),n.t),next:c,warning:`模块未闭合，已扫描 ${p} 行`}}if(c>=r.length)return null;const f=d.join(`
`).trim();try{return{html:e.render(l,f,ge(f,e.spec.bodyFormat),n.t),next:c+1}}catch{return null}}}}const Yt={spec:{name:"breaking",label:"突发卡片",bodyFormat:"markdown",example:`:::breaking badge="重磅发布" title="MarkFlow v2.0 功能全集上线" subtitle="52 套专业主题 + 61 个排版组件，支持公众号、A4 文档、小红书卡片、自由画布四种输出模式" chips="模块化排版|52套主题|多场景导出|免费使用"
这个组件适合放在文章开头，用一句话告诉读者：这篇文章能给你什么。
:::`,fields:[{name:"badge",required:!1,description:"标签"},{name:"title",required:!1,description:"标题"},{name:"subtitle",required:!1,description:"副标题"},{name:"chips",required:!1,description:"关键词（|分隔）"},{name:"color",required:!1,description:"自定义颜色"}]},render(e,t,n,i){const r=e.color||i.accent;function o(c,p){if(/^#[0-9a-fA-F]{3,8}$/.test(c)){const f=c.length===4?"#"+c[1]+c[1]+c[2]+c[2]+c[3]+c[3]:c.slice(0,7),g=Math.round(p*255).toString(16).padStart(2,"0");return f+g}if(typeof document<"u"){const f=document.createElement("div");f.style.color=c,document.body.appendChild(f);const g=getComputedStyle(f).color;document.body.removeChild(f);const x=g.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/);if(x)return`rgba(${x[1]},${x[2]},${x[3]},${p})`}return c}const a=e.color?o(r,.15):i.light,l=e.color?o(r,.2):i.border;let d=`<section style="margin:${m[10]} 0px;padding:${m[12]} ${m[9]};background:radial-gradient(circle 60px at 92% 30px,${a} 96%,transparent 100%),linear-gradient(135deg,${a},rgba(255,255,255,0.8));border:1px solid ${l};border-radius:${R["4xl"]}">`;return e.badge&&(d+=`<span style="display:inline-block;padding:${m[1]} ${m[5]};background:${r};color:${U.surface};border-radius:${R.md};font-size:${E.xs};font-weight:${A.bold};letter-spacing:${K.widest};margin-bottom:${m[5]}">${_(e.badge)}</span>`),e.title&&(d+=`<p style="margin:0px 0px ${m[3]};font-size:${E["5xl"]};font-weight:${A.extrabold};color:${D.gray1000};line-height:${X.normal}">${_(e.title)}</p>`),e.subtitle&&(d+=`<p style="margin:0px 0px ${m[5]};font-size:${E.md};color:${D.gray600}">${_(e.subtitle)}</p>`),e.chips&&(d+=`<section style="display:flex;gap:${m[3]};flex-wrap:wrap;margin-bottom:${m[5]}">`,e.chips.split("|").forEach(c=>{d+=`<span style="display:inline-block;padding:${m[1]} ${m[5]};border-radius:${R["2xl"]};font-size:${E.xs};font-weight:${A.semibold};background:rgba(255,255,255,0.8);color:${r};border:1px solid ${l}">${_("#"+c.trim())}</span>`}),d+="</section>"),n.markdown.trim()&&(d+=`<section style="font-size:${E.md};color:${D.gray700};line-height:${X.loosest};margin-top:${m[3]}">${te(n.markdown,i)}</section>`),d+="</section>",d},renderLegacy(e,t,n){return this.render(e,t,ge(t,this.spec.bodyFormat),n)}},ho=ye(Yt),Xt={spec:{name:"steps-horizontal",label:"横向步骤",bodyFormat:"rows",example:`:::steps-horizontal label="HOW IT WORKS" title="从零到发布只需 5 步" hint="按顺序完成即可" active="2" color="#2563eb"
- 写作 | 在编辑器中用 Markdown 完成正文和标题层级
- 增强 | 从组件库挑选合适的排版模块，替换字段内容
- 预览 | 右侧实时查看渲染效果，同步调整移动端显示
- 导出 | 一键复制富文本到公众号，或导出长图/PDF
- 发布 | 粘贴到公众号后台，封面和合集设置后即可发布
:::`,fields:[{name:"label",required:!1,description:"顶部标签"},{name:"title",required:!1,description:"标题"},{name:"hint",required:!1,description:"提示文字"},{name:"active",required:!1,description:"强调控制（数字/all/none），默认 1"},{name:"color",required:!1,description:"自定义颜色"}]},render(e,t,n,i){const r=(e.active||"1").toLowerCase().trim(),o=parseInt(r,10),a=e.color||i.accent,l=n.rows.map(p=>({name:p[0]||"",desc:p[1]||""})),d=p=>r==="all"?!0:r==="none"?!1:p+1===o;let c=`<section style="margin:0px 0px ${m[10]};padding:${m[9]};background:${D.gray50};border-radius:${R["2xl"]};border:1px solid ${D.gray200}">`;return e.label&&(c+=`<p style="margin:0px 0px ${m[1]};font-size:${E["2xs"]};color:${D.gray500};letter-spacing:${K["2xl"]};font-weight:${A.bold}">${_(e.label)}</p>`),e.title&&(c+=`<p style="margin:0px 0px ${m[1]};font-size:${E["3xl"]};font-weight:${A.extrabold};color:${D.gray1000}">${_(e.title)}</p>`),e.hint&&(c+=`<p style="margin:0px 0px ${m[7]};font-size:${E.sm};color:${D.gray500}">${_(e.hint)}</p>`),c+='<section style="overflow-x:auto;-webkit-overflow-scrolling:touch">',c+=`<table border="0" cellpadding="0" cellspacing="12" style="margin:0;border-collapse:separate;border-spacing:12px 0;border:none;min-width:${l.length*120}px"><tr>`,l.forEach((p,f)=>{const g=d(f),x=g?"2px":"1px",h=g?a:D.gray200,u=g?gi(a):U.surface;c+=`<td style="vertical-align:top;padding:${m[7]} ${m[6]};background:${u};border-radius:${R.xl};border:${x} solid ${h};text-align:center;width:${Math.floor(100/l.length)}%">`,c+=`<p style="margin:0px 0px ${m[1]};font-size:${E["5xl"]};font-weight:${A.black};color:${a}">${_(f+1)}</p>`,c+=`<p style="margin:0px 0px ${m[0]};font-size:${E.base};font-weight:${A.bold};color:${U.textTertiary}">${_(p.name)}</p>`,c+=`<p style="margin:0px;font-size:${E.xs};color:${D.gray500}">${_(p.desc)}</p>`,c+="</td>"}),c+="</tr></table></section>",c+="</section>",c},renderLegacy(e,t,n){return this.render(e,t,ge(t,this.spec.bodyFormat),n)}},bo=ye(Xt),Zt={spec:{name:"steps-vertical",label:"竖向步骤",bodyFormat:"rows",example:`:::steps-vertical label="VERTICAL STEPS" title="竖向步骤流" hint="上下滑动查看" active="2"
- 注册账号 | 填写基本信息完成注册
- 实名认证 | 上传证件完成身份验证
- 开始使用 | 选择功能模块开始体验
:::`,fields:[{name:"label",required:!1,description:"顶部标签"},{name:"title",required:!1,description:"标题"},{name:"hint",required:!1,description:"提示文字"},{name:"active",required:!1,description:"强调控制（数字/all/none），默认 1"},{name:"color",required:!1,description:"自定义颜色"}]},render(e,t,n,i){const r=(e.active||"1").toLowerCase().trim(),o=parseInt(r,10),a=e.color||i.accent,l=n.rows.map(p=>({name:p[0]||"",desc:p[1]||""})),d=p=>r==="all"?!0:r==="none"?!1:p+1===o;let c='<section style="margin:0px 0px 24px;padding:20px;background:rgb(250,251,254);border-radius:12px;border:1px solid rgb(238,238,238)">';return e.label&&(c+=`<p style="margin:0px 0px 4px;font-size:10px;color:rgb(153,153,153);letter-spacing:2px;font-weight:700">${_(e.label)}</p>`),e.title&&(c+=`<p style="margin:0px 0px 4px;font-size:18px;font-weight:800;color:rgb(26,26,26)">${_(e.title)}</p>`),e.hint&&(c+=`<p style="margin:0px 0px 16px;font-size:12px;color:rgb(153,153,153)">${_(e.hint)}</p>`),l.forEach((p,f)=>{const g=d(f),x=g?"2px":"1px",h=g?a:"rgb(238,238,238)",u=g?gi(a):"rgb(255,255,255)",y=f<l.length-1?"margin-bottom:12px;":"";c+=`<section style="${y}padding:16px;background:${u};border-radius:10px;border:${x} solid ${h}">`,c+=`<section style="display:inline-block;vertical-align:top;width:32px;margin-right:12px"><section style="width:32px;height:32px;border-radius:50%;background:${g?a:"rgb(238,238,238)"};text-align:center;line-height:32px"><span style="font-size:14px;font-weight:900;color:${g?"#fff":"rgb(153,153,153)"}">${_(f+1)}</span></section></section>`,c+='<section style="display:inline-block;vertical-align:top;padding-top:4px">',c+=`<p style="margin:0px 0px 2px;font-size:14px;font-weight:700;color:rgb(51,65,85)">${_(p.name)}</p>`,c+=`<p style="margin:0px;font-size:12px;color:rgb(153,153,153)">${_(p.desc)}</p>`,c+="</section>",c+="</section>"}),c+="</section>",c},renderLegacy(e,t,n){return this.render(e,t,ge(t,this.spec.bodyFormat),n)}},yo=ye(Zt);function vo(e){const t=[],n=e.split(`
`).filter(i=>i.trim());for(const i of n){const r=i.match(/^-\s*\[([^\]]+)\]\s*(.+)$/);r&&t.push({label:r[1].trim(),text:r[2].trim()})}return t}const yt={spec:{name:"case-flow",label:"标签条目",bodyFormat:"markdown",example:`:::case-flow
- [案例 01] 从零搭建个人知识库，三周后效率翻倍
- [案例 02] 用 AI 辅助写周报，每周省出两小时
- [步骤三] 坚持早起 100 天，人生发生了什么变化
:::`,fields:[{name:"color",required:!1,description:"标签背景色（默认使用主题色）"}]},render(e,t,n,i){const r=n.markdown||t,o=Ve(e.color||i.accent),a=vo(r);if(a.length===0)return"";const l=Ft(o,.12);return`
      <section style="margin:20px 0;">
        ${a.map(c=>`
      <section style="display:flex;align-items:center;gap:16px;padding:20px;margin-bottom:12px;border:1px solid rgba(0,0,0,0.06);border-radius:12px;background:#fff;">
        <span style="flex-shrink:0;white-space:nowrap;background:${l};color:${o};font-size:13px;font-weight:600;padding:6px 14px;border-radius:8px;letter-spacing:0.5px;">${V(c.label)}</span>
        <span style="flex:1;font-size:15px;line-height:1.6;color:#333;">${te(c.text,i)}</span>
      </section>
    `).join("")}
      </section>
    `},renderLegacy(e,t,n){return this.render(e,t,ge(t,this.spec.bodyFormat),n)}},wo=ye(yt),Yi={id:"Statement_DA01",name:"居中强调语",tag:"statement",attrs:[{key:"color",label:"文字颜色",required:!1,default:""}],example:"<statement>好排版不是让文章变好看，而是让读者在 3 秒内决定「这篇文章值得读」。</statement>",render(e,t,n){return`<section style="margin:20px 0px"><p style="text-align:center;font-size:18px;font-weight:700;color:${e.color||"rgb(51,65,85)"};line-height:1.6">${_(t)}</p></section>`}},Jt={id:"Lead_DA01",name:"引导文字",tag:"lead",attrs:[{key:"color",label:"边框颜色",required:!1,default:""},{key:"text-color",label:"文字颜色",required:!1,default:""},{key:"bg",label:"背景颜色",required:!1,default:""},{key:"round",label:"圆角",required:!1,default:""}],example:"<lead>在开始之前，先聊一个背景：过去三年，内容创作者的平均产出量增长了 4 倍，但读者的平均阅读完成率却下降了 28%。问题不在内容质量——而在「信息呈现」的方式没有跟上读者注意力的变化。这篇指南将带你用模块化排版，把每一篇文章都变成读者愿意读完的样子。</lead>",render(e,t,n){const i=e.color||n.accent,r=e["text-color"]||"rgb(85,85,85)";return`<section style="${[e.bg?`background:${e.bg};`:"",e.round?"border-radius:8px;overflow:clip;":""].join("")}"><p style="font-size:16px;color:${r};line-height:1.8;letter-spacing:0.5px;text-align:justify;padding:16px;border-left:3px solid ${i};margin:14px 0px;overflow-wrap:break-word;word-break:break-word">${_(t)}</p></section>`}},Xi={id:"Engage_DA01",name:"底部引导卡片",tag:"engage-label",attrs:[{key:"title",label:"标题文字",required:!1,default:""},{key:"label",label:"底部小字",required:!1,default:"THANKS FOR READING"}],example:'<engage-label title="如果这篇文章帮你节省了排版时间，欢迎点赞、转发给需要的朋友，或在评论区留下你的使用心得！" label="THANKS FOR READING"></engage-label>',render(e,t,n){let i=`<section style="margin:${m[11]} 0px ${m[11]};width:100%;max-width:677px;box-sizing:border-box;overflow:hidden;text-align:center;padding:${m[9]};border-radius:${R["3xl"]};background:rgba(${n.rgb},0.05);border:1px dashed ${D.gray250}">`;return e.title&&(i+=`<p style="margin:0px 0px ${m[8]};font-size:${E.xl};font-weight:${A.extrabold};color:${D.gray850};line-height:${X.loose}">${_(e.title)}</p>`),i+=`<section style="margin:0px 0px ${m[6]};text-align:center;font-size:0px;line-height:1;color:${n.accent}">`,i+=`<span style="display:inline-block;margin:0px ${m[3]};width:28px;height:28px;vertical-align:middle"><svg viewBox="0 0 24 24" width="28" height="28" style="display:block" xmlns="http://www.w3.org/2000/svg"><path d="M1 21h4V9H1v12zm22-11c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L14.17 1 7.59 7.59C7.22 7.95 7 8.45 7 9v10c0 1.1.9 2 2 2h9c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73v-2z" fill="currentColor"></path></svg></span>`,i+=`<span style="display:inline-block;margin:0px ${m[3]};width:32px;height:32px;vertical-align:middle"><svg viewBox="0 0 24 24" width="32" height="32" style="display:block" xmlns="http://www.w3.org/2000/svg"><path d="M21 12l-7-7v4C7 10 4 15 3 20c2.5-3.5 6-5.1 11-5.1V19l7-7z" fill="currentColor"></path></svg></span>`,i+=`<span style="display:inline-block;margin:0px ${m[3]};width:28px;height:28px;vertical-align:middle"><svg viewBox="0 0 24 24" width="28" height="28" style="display:block" xmlns="http://www.w3.org/2000/svg"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" fill="currentColor"></path></svg></span>`,i+="</section>",i+=`<p style="margin:0px 0px ${m[1]};font-size:${E["2xs"]};color:${n.accent};font-weight:${A.extrabold};letter-spacing:${K["2xl"]};text-transform:uppercase">${_("点赞 · 转发 · 推荐")}</p>`,i+=`<p style="font-size:${E["2xs"]};color:${D.gray400};letter-spacing:${K.widest};margin:0px">${_(e.label||"THANKS FOR READING")}</p>`,i+="</section>",i}},Zi={id:"Engage_DA02",tag:"engage-card",name:"底部引导卡片",icon:"💬",example:'<engage-card title="感谢你阅读到这里！" subtitle="如果觉得有用，点个赞告诉我们——你的反馈是我们持续更新的动力 💚"></engage-card>',attrs:[{key:"title",label:"主标题文字",required:!1,default:"感谢你的阅读与支持！"},{key:"subtitle",label:"副标题文字",required:!1,default:"喜欢就互动一下吧～ 💚"},{key:"color",label:"主题色",required:!1,default:"red|green|yellow",options:["green","red","yellow","blue","purple","orange","pink","teal","gray","其他十六进制颜色"]}],render(e,t,...n){const i=e.title||"感谢你的阅读与支持！",r=e.subtitle||"喜欢就互动一下吧～ 💚",o=ko(e.color);return`
<section style="margin:24px 0;padding:0;position:relative;">
    <section style="background:linear-gradient(135deg,${o[0].bgLight} 0%,${o[1].bgLight} 50%,${o[2].bgLight} 100%);border-radius:16px;padding:24px 16px 20px;position:relative;overflow:hidden;border:1px dashed rgba(229,231,235,0.9);">

    <!-- 标题区域 -->
    <section style="text-align:center;margin-bottom:20px;">
      <section style="font-size:18px;font-weight:700;color:#333;letter-spacing:1px;line-height:1.5;">${i}</section>
      <section style="font-size:13px;color:#888;margin-top:4px;">${r}</section>
    </section>

        <!-- 三列图标区域 -->
    <section style="display:flex;justify-content:center;align-items:flex-start;gap:0;">

      <!-- 点赞 -->
      <section style="flex:1;text-align:center;padding:0 6px;">
        <section style="width:48px;height:48px;border-radius:50%;background:#fff;margin:0 auto 10px;display:flex;align-items:center;justify-content:center;box-shadow:0 4px 16px ${o[0].glow};">
          <svg viewBox="0 0 24 24" width="24" height="24" fill="${o[0].icon}" xmlns="http://www.w3.org/2000/svg"><path d="M1 21h4V9H1v12zm22-11c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L14.17 1 7.59 7.59C7.22 7.95 7 8.45 7 9v10c0 1.1.9 2 2 2h9c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73v-2z"/></svg>
        </section>
        <section style="font-size:15px;font-weight:700;color:${o[0].icon};margin-bottom:3px;letter-spacing:2px;">
          <span style="opacity:0.5;">·</span> 点赞 <span style="opacity:0.5;">·</span>
        </section>
        <section style="font-size:11px;color:#999;margin-bottom:6px;">喜欢就点个赞吧</section>
        <section style="width:24px;height:2px;border-radius:2px;background:${o[0].icon};margin:0 auto;opacity:0.5;"></section>
      </section>

      <!-- 转发（中间列，左右虚线边框做分隔） -->
      <section style="flex:1;text-align:center;padding:0 6px;border-left:1px dashed #e0e0e0;border-right:1px dashed #e0e0e0;">
        <section style="width:48px;height:48px;border-radius:50%;background:#fff;margin:0 auto 10px;display:flex;align-items:center;justify-content:center;box-shadow:0 4px 16px ${o[1].glow};">
          <svg viewBox="0 0 24 24" width="28" height="28" fill="${o[1].icon}" xmlns="http://www.w3.org/2000/svg"><path d="M21 12l-7-7v4C7 10 4 15 3 20c2.5-3.5 6-5.1 11-5.1V19l7-7z"/></svg>
        </section>
        <section style="font-size:15px;font-weight:700;color:${o[1].icon};margin-bottom:3px;letter-spacing:2px;">
          <span style="opacity:0.5;">·</span> 转发 <span style="opacity:0.5;">·</span>
        </section>
        <section style="font-size:11px;color:#999;margin-bottom:6px;">分享给更多朋友</section>
        <section style="width:24px;height:2px;border-radius:2px;background:${o[1].icon};margin:0 auto;opacity:0.5;"></section>
      </section>

      <!-- 推荐 -->
      <section style="flex:1;text-align:center;padding:0 6px;">
        <section style="width:48px;height:48px;border-radius:50%;background:#fff;margin:0 auto 10px;display:flex;align-items:center;justify-content:center;box-shadow:0 4px 16px ${o[2].glow};">
          <svg viewBox="0 0 24 24" width="24" height="24" fill="${o[2].icon}" xmlns="http://www.w3.org/2000/svg"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>
        </section>
        <section style="font-size:15px;font-weight:700;color:${o[2].icon};margin-bottom:3px;letter-spacing:2px;">
          <span style="opacity:0.5;">·</span> 推荐 <span style="opacity:0.5;">·</span>
        </section>
        <section style="font-size:11px;color:#999;margin-bottom:6px;">推荐给身边的人</section>
        <section style="width:24px;height:2px;border-radius:2px;background:${o[2].icon};margin:0 auto;opacity:0.5;"></section>
      </section>

    </section>
  </section>
</section>`}};function zt(e,t){e=e.replace("#",""),e.length===3&&(e=e[0]+e[0]+e[1]+e[1]+e[2]+e[2]);const n=parseInt(e.substring(0,2),16),i=parseInt(e.substring(2,4),16),r=parseInt(e.substring(4,6),16);return`rgba(${n},${i},${r},${t})`}function Cn(e){const t={red:"#e8636f",green:"#5fa55a",yellow:"#f3c885",blue:"#5b8dd9",purple:"#9b6fc3",orange:"#e8943a",pink:"#e87ba4",teal:"#4db8a0",gray:"#888888"},n=e.trim().toLowerCase();return t[n]?t[n]:/^#?[0-9a-f]{3,6}$/i.test(e.trim())?e.trim().startsWith("#")?e.trim():"#"+e.trim():null}function ze(e){return{icon:e,bg:zt(e,.1),glow:zt(e,.15),bgLight:zt(e,.05)}}function ko(e){const t=[ze("#e8636f"),ze("#5fa55a"),ze("#f3c885")];if(!e)return t;const n=e.split("|").map(r=>r.trim());if(n.length===1){const r=Cn(n[0]);if(!r)return t;const o=ze(r);return[o,o,o]}const i=n.slice(0,3).map(r=>{const o=Cn(r);return ze(o||"#888888")});for(;i.length<3;)i.push(i[i.length-1]);return i}const Qt={id:"Img_DA01",name:"图片",tag:"img",attrs:[{key:"src",label:"图片地址",required:!0,default:"",description:"图片地址，支持 http(s) URL、base64 数据"},{key:"alt",label:"替代文本",required:!1,default:"",description:"图片无法加载时显示的替代文本"},{key:"width",label:"宽度",required:!1,default:"100%",description:"图片容器宽度，如 100% / 600px"},{key:"height",label:"高度",required:!1,default:"auto",description:"图片容器高度，如 auto / 400px"},{key:"radius",label:"圆角",required:!1,default:"8px",description:"图片圆角大小，如 8px / 12px / 50% / 10px 20px / 10px 20px 10px 20px"},{key:"fit",label:"裁切方式",required:!1,default:"cover",options:["fill","contain","cover","none","scale-down"],description:"CSS object-fit 裁切方式：fill 拉伸 / contain 完整显示 / cover 裁剪 / none 原始尺寸 / scale-down 缩小"},{key:"align",label:"容器对齐",required:!1,default:"left",options:["left","center","right"],description:"图片容器水平对齐方式（固定宽度时生效）：left 居左 / center 居中 / right 居右"},{key:"left",label:"X轴偏移",required:!1,default:"",description:"图片 x 轴偏移效果，如 10px / -20px / 50%"},{key:"top",label:"Y轴偏移",required:!1,default:"",description:"图片 y 轴偏移效果，如 10px / -20px / 50%"}],example:'<img src="https://robocopmao.github.io/r-markdown/banner4.webp" alt="模块化排版引擎架构示意图：Markdown 解析层 → 模块匹配层 → 主题令牌注入 → 内联样式 HTML 输出" width="100%" height="auto" radius="8px" fit="cover" align="left" left="10px" top="5px" />',render(e,t,n,i="24px"){const r=e.src||"",o=e.alt||"",a=e.width||"100%",l=e.height||"auto",d=e.radius||"8px",c=e.fit||"cover",p=e.align||"left",f={left:`${i} 0px`,center:`${i} auto`,right:`${i} 0px ${i} auto`},g=f[p]||f.left,x=e.left||"",h=e.top||"",u=["width:100%",`object-fit:${c}`,"display:block",x?`margin-left:${x}`:"",h?`margin-top:${h}`:""].filter(Boolean).join(";");return`<section style="margin:${g};width:${a};height:${l};overflow:hidden;border-radius:${d}"><img src="${r}" alt="${o}" style="${u}" /></section>`}};function $o(e){const t=[];for(const n of e){if(n.length<3)continue;const i=n[0].replace(/^-\s*/,""),r=n[1],o=n[2],a=(n[3]||"").trim();let l=null,d=null;if(a){const c=a.match(/^!\[(.*?)\]\((.*?)\)\[(\S+)\s+(\S+)\]$/);if(c)l={alt:c[1],src:c[2],width:c[3],height:c[4]};else{const p=a.match(/^<img\s+(.*?)\s*\/?\s*>$/i);p&&(d=be(p[1]))}}t.push({date:i,title:r,desc:o,image:l,customImgAttrs:d})}return t}const en={spec:{name:"timeline",label:"时间线",bodyFormat:"rows",example:`:::timeline
- 2024年01月 | 项目启动 | 完成团队组建和需求分析 | ![新版](https://robocopmao.github.io/r-markdown/banner4.webp)[100% 120px]
- 2024年06月 | 一期上线 | 核心功能发布，用户突破1万
- 2025年01月 | 二期迭代 | 新增AI辅助功能，用户突破10万 | ![二期](https://robocopmao.github.io/r-markdown/banner4.webp)[100% 120px]
:::`,fields:[{name:"color",required:!1,description:"自定义颜色"}]},render(e,t,n,i){const r=Ve(e.color||i.accent),o=$o(n.rows);if(o.length===0)return"";const a=Ft(r,.15),l=Ft(r,.3);return`
      <section style="margin:24px 0;padding:24px;background:linear-gradient(135deg, rgba(255,255,255,0.8), rgba(248,250,252,0.6));border:1px solid rgba(0,0,0,0.06);border-radius:16px;">
        ${o.map((c,p)=>{const f=p===o.length-1;let g="";if(c.customImgAttrs)g=Qt.render(c.customImgAttrs,"",i,"12px");else if(c.image){const u=["border-radius:12px","display:block","margin-top:12px"];c.image.width&&u.push(`width:${c.image.width}`),c.image.height&&u.push(`height:${c.image.height}`,"object-fit:cover"),g=`<img src="${c.image.src}" alt="${c.image.alt}" style="${u.join(";")};" />`}const x=`<section style="float:left;width:12px;height:12px;border-radius:50%;background:${r};box-shadow:0 0 0 4px ${a};margin:5px 0 0 4px;"></section>`,h=f?"border-left:2px solid transparent;":`border-left:2px solid ${l};`;return`
        <section style="margin-bottom:${f?"0":"32px"};overflow:hidden;">
          ${x}
          <section style="margin-left:9px;${h}padding-left:18px;">
            <section style="margin:0 0 6px;font-size:13px;font-weight:700;color:${r};letter-spacing:0.5px;">${_(c.date)}</section>
            <section style="margin:0 0 6px;font-size:17px;font-weight:800;color:rgb(17,24,39);line-height:1.4;">${_(c.title)}</section>
            <section style="margin:0;font-size:14px;color:rgb(100,116,139);line-height:1.6;">${_(c.desc)}</section>
            ${g}
          </section>
        </section>`}).join("")}
      </section>`},renderLegacy(e,t,n){return this.render(e,t,ge(t,this.spec.bodyFormat),n)}},So=ye(en);function vt(e,t,n,i,r=""){return`<foreignObject x="${t}" y="0" width="${n}" height="${i}"${r}><img xmlns="http://www.w3.org/1999/xhtml" src="${e}" width="${n}" height="${i}" style="display:block;object-fit:cover"/></foreignObject>`}function wt(e,t,n){return`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${e} ${t}" width="100%" style="max-width:${e}px;display:block;margin:28px auto;border-radius:8px;overflow:hidden">
  ${n}
</svg>`}function jo(e,t,n,i,r){const a=(t+1)*n,l=.5;let d="";for(let g=0;g<t+1;g++)d+=vt(e[g%t],g*i,i,r);const c=[],p=[];for(let g=0;g<t;g++){const x=((g+1)*n-l)/a,h=(g+1)*n/a;g===0&&(c.push(0),p.push("0 0")),c.push(+x.toFixed(4)),p.push(`${-g*i} 0`),c.push(+h.toFixed(4)),p.push(`${-(g+1)*i} 0`)}const f=(a-l)/a;return c.push(+f.toFixed(4)),p.push(`${-t*i} 0`),c.push(.999),p.push(`${-t*i} 0`),c.push(1),p.push("0 0"),wt(i,r,`<g>
    <animateTransform attributeName="transform" type="translate" values="${p.join(";")}" keyTimes="${c.join(";")}" dur="${a}s" repeatCount="indefinite"/>
    ${d}
  </g>`)}function No(e,t,n,i,r){const a=(2*t-1)*n;let l="";for(let f=0;f<t;f++)l+=vt(e[f],f*i,i,r);const d=[0],c=["0 0"];let p=0;for(let f=0;f<t-1;f++)p+=n-.5,d.push(+(p/a).toFixed(4)),c.push(`${-f*i} 0`),p+=.5,d.push(+(p/a).toFixed(4)),c.push(`${-(f+1)*i} 0`);p+=n,d.push(+(p/a).toFixed(4)),c.push(`${-(t-1)*i} 0`);for(let f=t-2;f>=0;f--)p+=.5,d.push(+(p/a).toFixed(4)),c.push(`${-f*i} 0`),p+=n-.5,d.push(+(p/a).toFixed(4)),c.push(`${-f*i} 0`);return d[d.length-1]<.999&&(d.push(1),c.push("0 0")),wt(i,r,`<g>
    <animateTransform attributeName="transform" type="translate" values="${c.join(";")}" keyTimes="${d.join(";")}" dur="${a}s" repeatCount="indefinite"/>
    ${l}
  </g>`)}function Eo(e,t,n,i,r){const l=t*n+.6;let d="";for(let f=0;f<t;f++)d+=vt(e[f],f*i,i,r);const c=[0],p=["0 0"];for(let f=0;f<t-1;f++){const g=((f+1)*n-.5)/l,x=(f+1)*n/l;f===0?(c.push(+g.toFixed(4)),p.push("0 0")):(c.push(+g.toFixed(4)),p.push(`${-f*i} 0`)),c.push(+x.toFixed(4)),p.push(`${-(f+1)*i} 0`)}return c.push(+(t*n/l).toFixed(4)),p.push(`${-(t-1)*i} 0`),c.push(1),p.push("0 0"),wt(i,r,`<g>
    <animateTransform attributeName="transform" type="translate" values="${p.join(";")}" keyTimes="${c.join(";")}" dur="${l}s" repeatCount="indefinite"/>
    ${d}
  </g>`)}function _o(e,t,n,i,r){const a=t*n;let l="";for(let d=0;d<t;d++){let c;if(d===0){const p=+((n-.5)/a).toFixed(4),f=+(n/a).toFixed(4);c=` <animate attributeName="opacity" values="1;1;0;0" keyTimes="0;${p};${f};1" dur="${a}s" repeatCount="indefinite"/>`}else if(d===t-1){const p=+(d*n/a).toFixed(4),f=+((d*n+.5)/a).toFixed(4),g=+(((d+1)*n-.5)/a).toFixed(4);c=` <animate attributeName="opacity" values="0;0;1;1;0" keyTimes="0;${p};${f};${g};1" dur="${a}s" repeatCount="indefinite"/>`}else{const p=+(d*n/a).toFixed(4),f=+((d*n+.5)/a).toFixed(4),g=+(((d+1)*n-.5)/a).toFixed(4),x=+((d+1)*n/a).toFixed(4);c=` <animate attributeName="opacity" values="0;0;1;1;0;0" keyTimes="0;${p};${f};${g};${x};1" dur="${a}s" repeatCount="indefinite"/>`}l+=`<g>${c}
    ${vt(e[d],0,i,r)}
  </g>`}return wt(i,r,l)}const tn={spec:{name:"slider",label:"轮播图",bodyFormat:"fields",example:`:::slider images="https://robocopmao.github.io/r-markdown/banner4.webp,https://robocopmao.github.io/r-markdown/banner4.webp" interval="3" width="600" height="200" type="1"
:::`,fields:[{name:"images",required:!0,description:"图片URL列表（逗号分隔）"},{name:"interval",required:!1,description:"每张显示时长（秒），最小2秒，默认3"},{name:"width",required:!1,description:"视图宽度，默认600"},{name:"height",required:!1,description:"视图高度，默认200"},{name:"type",required:!1,description:"轮播类型：1循环 2来回 3滚回 4淡入淡出，默认1"}]},render(e,t,n,i){const r=e.images||"",o=Math.max(2,parseInt(e.interval||"3",10)||0),a=parseInt(e.width||"600",10)||0,l=parseInt(e.height||"200",10)||0,d=parseInt(e.type||"1",10)||1;if(!r)return'<section style="margin:28px 0;width:100%;text-align:center;padding:20px;background:rgba(0,0,0,0.05);border-radius:8px;color:#999;font-size:14px">请提供图片URL列表</section>';const c=r.split(",").map(f=>f.trim()).filter(Boolean).slice(0,5),p=c.length;if(p===0)return'<section style="margin:28px 0;width:100%;text-align:center;padding:20px;background:rgba(0,0,0,0.05);border-radius:8px;color:#999;font-size:14px">请提供图片URL列表</section>';if(p===1)return`<section style="margin:28px 0;width:100%;text-align:center"><img src="${c[0]}" width="${a}" height="${l}" style="max-width:100%;height:auto;border-radius:8px" /></section>`;switch(d){case 2:return No(c,p,o,a,l);case 3:return Eo(c,p,o,a,l);case 4:return _o(c,p,o,a,l);default:return jo(c,p,o,a,l)}},renderLegacy(e,t,n){return this.render(e,t,ge(t,this.spec.bodyFormat),n)}},Co=ye(tn),nn={spec:{name:"gov-header",label:"公文头部",bodyFormat:"fields",example:`:::gov-header issuer="XX市人民政府办公厅" doc-no="市政发〔2026〕第1号" classification="绝密" urgency="特急" signer="张三"
:::`,fields:[{name:"issuer",required:!0,description:"发文机关名称"},{name:"doc-no",required:!1,description:"发文字号"},{name:"classification",required:!1,description:"密级"},{name:"urgency",required:!1,description:"紧急程度"},{name:"signer",required:!1,description:"签发人（上行文）"}]},render(e,t,n,i){const r=V(e.issuer||""),o=V(e["doc-no"]||e.docNo||""),a=V(e.classification||""),l=V(e.urgency||""),d=V(e.signer||""),c="#c0202c",p=[],f=[];a&&f.push(`<span style="font-size:16pt;font-weight:bold;color:${c};font-family:'STSong','SimSun',serif">${a}★保密期限</span>`),l&&f.push(`<span style="font-size:16pt;font-weight:bold;color:${c};font-family:'STSong','SimSun',serif">${l}</span>`);const g=d?`<div style="position:absolute;top:0;right:0;font-size:16pt;color:#000;font-family:'FangSong','STFangsong',serif">签发人：${d}</div>`:"",x=f.length>0?`<div style="position:absolute;top:0;left:0;display:flex;flex-direction:column;gap:4px">${f.join("")}</div>`:"",h=r?`<div style="text-align:center;font-size:36pt;font-weight:bold;color:${c};font-family:'STSong','SimSun',serif;letter-spacing:6px;line-height:1.4;margin-top:24px">${r}</div>`:"",u=o?`<div style="text-align:center;font-size:16pt;color:#000;font-family:'FangSong','STFangsong',serif;margin-top:8px">${o}</div>`:"",y=`<div style="height:4px;background:${c};margin-top:12px;border:none"></div>`;return p.push('<section class="gov-header" data-block="gov-header" style="position:relative;margin-bottom:24px;break-inside:avoid">'),p.push(x),p.push(g),p.push(h),p.push(u),p.push(y),p.push("</section>"),p.join("")},renderLegacy(e,t,n){return this.render(e,t,ge(t,this.spec.bodyFormat),n)}},To=ye(nn),Tn="",zn="";function zo(e,t){if(!e)return"";const n=[];let i=e.replace(/```(\w*)\n([\s\S]*?)```/g,(r,o,a)=>{const l=n.length;return n.push(Ee(a,o||"")),`${Tn}CB${l}${zn}`});return i=te(i,t),i=i.replace(new RegExp(`${Tn}CB(\\d+)${zn}`,"g"),(r,o)=>n[parseInt(o)]||""),i=i.replace(/\n{2,}/g,`
`).replace(/[ \t]*\n[ \t]*/g,"<br>"),i}const rn={spec:{name:"align",label:"对齐容器",bodyFormat:"markdown",example:`:::align align="center"
这段文字将在页面中居中对齐显示，
适合用于引用语、诗歌或强调内容。
:::`,fields:[{name:"align",required:!1,description:"对齐方向（center/right/left），默认 center"}]},render(e,t,n,i){return n.markdown.trim()?`<section style="text-align:${e.align||"center"};margin:${m[5]} 0px">${zo(n.markdown,i)}</section>`:""},renderLegacy(e,t,n){return this.render(e,t,ge(t,this.spec.bodyFormat),n)}},Ao=ye(rn);function An(e){let t=e.trim();return t.startsWith("|")&&(t=t.substring(1)),t.endsWith("|")&&(t=t.substring(0,t.length-1)),t.split("|").map(n=>n.trim())}function Mn(e,t,n){var j,w;const i=e.style||"default",r=t.trim().split(`
`);let o="",a="",l=0;const d=(j=r[0])==null?void 0:j.match(/^:{3,4}\s*table\b\s*(.*)/);d?(o=((w=d[1])==null?void 0:w.trim())||"",l=1):r[0]&&!r[0].includes("|")&&(o=r[0].trim(),l=1);const c=[];for(let N=l;N<r.length;N++){const b=r[N].trim();if(!/^:{3,4}\s*$/.test(b)){if(b.includes("|")){if(/^[|\s\-:]+$/.test(b))continue;c.push(b)}else if(b&&c.length>=2){a=b;break}}}if(c.length<2)return'<p style="color:#999">表格至少需要表头行和一行数据</p>';const p=An(c[0]),f=c.slice(1).map(An),g=Math.max(p.length,...f.map(N=>N.length),2),x=i==="striped",h=i==="card";let u="";o&&(u+=`<section style="margin-bottom:${m[3]};padding:${m[3]} 0;text-align:center"><span style="display:inline-flex;align-items:center;gap:6px;font-size:${E.sm};font-weight:${A.semibold};color:${n.accent}"><span style="display:inline-block;width:3px;height:14px;border-radius:2px;background:${n.accent}"></span>${V(o)}</span></section>`);const y=h?"box-shadow:0 4px 16px rgba(0,0,0,0.08),0 1px 4px rgba(0,0,0,0.04)":"box-shadow:0 1px 3px rgba(0,0,0,0.06),0 1px 2px rgba(0,0,0,0.04)";u+=`<section style="margin:${m[7]} 0px;background:#fff;border-radius:${R["2xl"]};overflow:hidden;${y}">`,u+='<section style="overflow-x:auto"><table style="border-collapse:collapse;width:100%">',u+="<thead><tr>",p.forEach((N,b)=>{const $=b===0,S=b===p.length-1;u+=`<th style="vertical-align:top;padding:13px ${m[6]};text-align:left;font-size:13px;font-weight:${A.semibold};color:#fff;background:${n.accent};${$?`border-radius:${R["2xl"]} 0 0 0`:""}${S?`border-radius:0 ${R["2xl"]} 0 0`:""};letter-spacing:0.3px">${te(N,n)||"&nbsp;"}</th>`});for(let N=p.length;N<g;N++)u+=`<th style="vertical-align:top;padding:13px ${m[6]};text-align:left;font-size:13px;font-weight:${A.semibold};color:#fff;background:${n.accent};border-radius:0 ${R["2xl"]} 0 0;letter-spacing:0.3px">&nbsp;</th>`;return u+="</tr></thead>",u+="<tbody>",f.forEach((N,b)=>{const $=b===f.length-1;u+="<tr>",N.forEach((S,C)=>{const O=C===0,F=C===N.length-1,Q=$&&F,W=$&&O,Z=$&&!a?"border-bottom:none":`border-bottom:1px solid ${D.gray100}`,I=W&&!a?`border-radius:0 0 0 ${R["2xl"]}`:Q&&!a?`border-radius:0 0 ${R["2xl"]} 0`:"",L=x&&b%2===1?`background:${D.gray50}`:"background:#fff";u+=`<td style="vertical-align:top;padding:11px ${m[6]};text-align:left;font-size:13px;color:#475569;${Z};${I};${L}">${te(S,n)||"&nbsp;"}</td>`});for(let S=N.length;S<g;S++)u+=`<td style="vertical-align:top;padding:11px ${m[6]};text-align:left;font-size:13px;color:#475569;${$&&!a?"border-bottom:none":`border-bottom:1px solid ${D.gray100}`}">&nbsp;</td>`;u+="</tr>"}),u+="</tbody>",a&&(u+=`<tfoot><tr><td colspan="${g}" style="padding:9px ${m[6]};text-align:center;font-size:11px;color:#94a3b8;background:linear-gradient(180deg,${D.gray50} 0%,#f1f5f9 100%);border-top:1px solid ${D.gray200};border-radius:0 0 ${R["2xl"]} ${R["2xl"]}">${V(a)}</td></tr></tfoot>`),u+="</table></section></section>",u}const sn={spec:{name:"table",label:"表格",bodyFormat:"markdown",example:`:::table style="card" title="四种输出模式对比"
| 输出方式 | 适合场景 | 输出格式 | 特点 |
|----------|----------|----------|------|
| 复制富文本 | 公众号、知乎、语雀 | HTML 内联样式 | 保留完整排版，粘贴即用 |
| 导出长图 | 知识星球、社群传播 | PNG 长图 | 整篇内容一张图，方便转发 |
| A4 文档 | 正式报告、打印交付 | PDF | 自动分页，支持页码页眉 |
| 自由画布 | 网页 PPT、品牌页面 | HTML 源码 | 高度视觉化，可嵌入任意网页 |
:::
数据来源：MarkFlow 使用统计（2026 年 6 月）`,fields:[{name:"style",required:!1,description:"表格风格（default/striped/card）"},{name:"title",required:!1,description:"标题"}]},render(e,t,n,i){return Mn(e,n.markdown,i)},renderLegacy(e,t,n){return Mn(e,t,n)}},Mo=ye(sn);function Io(e,t){return{fields:t==="fields"?Wi(e):{},rows:t==="rows"?Gi(e):[],json:t==="json_object"?_e(e):t==="json_array"?Ye(e):null,markdown:t==="markdown"?e:""}}function ne(e,t){const n=new RegExp(`^:::\\s*${e.name}(?![-\\w])`);return{name:`layout-${e.name}`,priority:20,match:i=>n.test(i),render:(i,r,o,a)=>{const l=Lo(o,a);if(!l)return null;const d=Io(l.body,e.bodyFormat);return{html:t(d,i,l.body),next:l.next,warning:l.warning}}}}function Lo(e,t){const n=[];let i=t+1;const r=80;for(;i<e.length&&!/^:::\s*$/.test(e[i]);)if(n.push(e[i]),i++,i-t>r)return{body:n.join(`
`).trim(),next:i,warning:`模块未闭合，已扫描 ${r} 行`};return i>=e.length?null:{body:n.join(`
`).trim(),next:i+1}}function Xe(e,t){return`<p style="margin:0px 0px 8px;font-size:11px;letter-spacing:2.4px;text-transform:uppercase;font-weight:800;color:${t};line-height:1.4">${k(e)}</p>`}function Ce(e,t){const n=(t==null?void 0:t.color)??"#1a1a1a",i=(t==null?void 0:t.size)??"22px",r=(t==null?void 0:t.weight)??"800",o=(t==null?void 0:t.align)??"left";return`<p style="margin:0px 0px 12px;font-size:${i};font-weight:${r};color:${n};line-height:1.35;letter-spacing:-0.5px;text-align:${o};word-break:break-word">${k(e)}</p>`}function on(e,t){const n=(t==null?void 0:t.color)??"#64748b",i=(t==null?void 0:t.size)??"14px",r=(t==null?void 0:t.align)??"left";return`<p style="margin:0px;font-size:${i};color:${n};line-height:1.7;letter-spacing:0.3px;text-align:${r};text-align:justify">${k(e)}</p>`}function k(e){return e.replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;")}function Ro(e,t){const n=e.fields,i=t.t.accent;let r=`<section style="margin:0px 0px 32px;padding:36px 28px;background:linear-gradient(135deg,${i}10 0%,${i}05 100%);border-radius:16px;border:1px solid ${i}22;text-align:center;position:relative;overflow:hidden">`;return r+=`<section style="position:absolute;top:-30px;right:-30px;width:120px;height:120px;background:${i}0d;border-radius:50%"></section>`,n.label&&(r+=Xe(k(n.label),i)),n.title&&(r+=Ce(k(n.title),{color:"#1a1a1a",size:"28px",weight:"900",align:"center"})),n.subtitle&&(r+=on(k(n.subtitle),{color:"#64748b",size:"15px",align:"center"})),n.cta_text&&(r+=`<p style="margin:20px 0px 0px;font-size:13px;font-weight:700;letter-spacing:1.2px;color:${i};text-transform:uppercase">${k(n.cta_text)}</p>`),r+="</section>",r}const Oo={spec:{name:"hero",category:"opening",serves:["attention","readability"],bodyFormat:"fields",label:"开篇主视觉",fields:[{name:"label",required:!0,description:"标签/徽章文字"},{name:"title",required:!0,description:"主标题"},{name:"subtitle",required:!1,description:"副标题"},{name:"cta_text",required:!1,description:"引导文案"}]},renderer:ne({name:"hero",bodyFormat:"fields"},Ro)};function Do(e,t,n){const i=e.rows,r=t.t.accent;let o='<section style="margin:0px 0px 28px;padding:24px 20px;background:#fafafe;border-radius:14px;border:1px solid #e2e8f0">';return o+=`<p style="margin:0px 0px 14px;font-size:12px;letter-spacing:2.8px;text-transform:uppercase;font-weight:800;color:${r};line-height:1.4">READING PATH</p>`,o+='<p style="margin:0px 0px 18px;font-size:16px;font-weight:700;color:#1a1a1a">阅读导航</p>',o+='<section style="display:flex;flex-direction:column;gap:14px">',i.forEach((a,l)=>{const d=a.length>=3?a.slice(0,3):a,c=d[0]??String(l+1).padStart(2,"0"),p=d[1]??"",f=d[2]??"";o+='<section style="display:flex;align-items:flex-start;gap:14px">',o+=`<span style="display:inline-flex;align-items:center;justify-content:center;flex-shrink:0;width:32px;height:32px;border-radius:50%;background:${r}15;color:${r};font-size:13px;font-weight:800;letter-spacing:0.5px">${k(c)}</span>`,o+='<section style="flex:1;min-width:0">',o+=`<p style="margin:0px 0px 2px;font-size:15px;font-weight:700;color:#1a1a1a;line-height:1.4">${k(p)}</p>`,f&&(o+=`<p style="margin:0px;font-size:13px;color:#94a3b8;line-height:1.5">${k(f)}</p>`),o+="</section></section>"}),o+="</section></section>",o}const Po={spec:{name:"toc",category:"opening",serves:["readability"],bodyFormat:"rows",label:"阅读导航"},renderer:ne({name:"toc",bodyFormat:"rows"},Do)};function Fo(e,t){const n=e.rows,i=t.t.accent;let r='<section style="margin:0px 0px 28px;display:grid;grid-template-columns:repeat(2,1fr);gap:14px">';return n.forEach(o=>{var u;const a=(u=o[o.length-1])==null?void 0:u.toLowerCase(),l=a==="accent",d=a==="accent"||a==="default"?o.slice(0,-1):o,c=d[0]??"",p=d[1]??"",f=d[2]??"",g=l?i:"#f8fafc",x=l?"#ffffff":"#1a1a1a",h=l?"rgba(255,255,255,0.85)":"#64748b";r+=`<section style="padding:18px 16px;background:${g};border-radius:14px;border:1px solid ${l?i:"#e2e8f0"};position:relative;overflow:hidden">`,r+=`<p style="margin:0px 0px 4px;font-size:11px;letter-spacing:2px;font-weight:800;color:${l?"rgba(255,255,255,0.85)":i};text-transform:uppercase;line-height:1.4">${k(c)}</p>`,p&&(r+=`<p style="margin:0px 0px 8px;font-size:16px;font-weight:800;color:${x};line-height:1.3;letter-spacing:-0.3px">${k(p)}</p>`),f&&(r+=`<p style="margin:0px;font-size:13px;color:${h};line-height:1.6">${k(f)}</p>`),r+="</section>"}),r+="</section>",r}const Bo={spec:{name:"cards",category:"opening",serves:["attention"],bodyFormat:"rows",label:"开篇卡片矩阵"},renderer:ne({name:"cards",bodyFormat:"rows"},Fo)};function Ho(e,t){const n=e.fields,i=t.t.accent;let r='<section style="margin:36px 0px 24px;text-align:center">';return n.label&&(r+=Xe(k(n.label),i)),n.title&&(r+=Ce(k(n.title),{color:"#1a1a1a",size:"26px",weight:"900",align:"center"})),n.body&&(r+=`<section style="margin-top:14px;font-size:15px;color:#64748b;line-height:1.8;letter-spacing:0.3px;text-align:justify">${te(n.body,t.t)}</section>`),r+=`<section style="margin:20px auto 0px;width:48px;height:3px;border-radius:2px;background:${i}"></section>`,r+="</section>",r}const Uo={spec:{name:"part",category:"opening",serves:["readability"],bodyFormat:"fields",label:"章节分隔",fields:[{name:"label",required:!0,description:"标签/徽章文字"},{name:"title",required:!0,description:"主标题"},{name:"body",required:!1,description:"正文内容"}]},renderer:ne({name:"part",bodyFormat:"fields"},Ho)};function qo(e,t){const n=e.fields,i=t.t.accent;let r='<section style="margin:0px 0px 28px">';return n.label&&(r+=Xe(k(n.label),i)),n.title&&(r+=Ce(k(n.title),{color:"#1a1a1a",size:"24px",weight:"800"})),r+="</section>",r}const Wo={spec:{name:"label-title",category:"opening",serves:["attention"],bodyFormat:"fields",label:"标签标题"},renderer:ne({name:"label-title",bodyFormat:"fields"},qo)};function Go(e,t){const n=e.rows,i=t.t.accent;let r='<section style="margin:0px 0px 28px;display:flex;flex-wrap:wrap;gap:14px">';return n.forEach(o=>{const a=Vi(o),l=Ki(o),d=a[0]??"",c=a[1]??"",p=a[2]??"",f=l?i:"#f8fafc",g=l?i:"#e2e8f0",x=l?"#ffffff":i,h=l?"rgba(255,255,255,0.85)":"#64748b",u=l?"rgba(255,255,255,0.75)":"#94a3b8";r+=`<section style="flex:1 1 calc(25% - 14px);min-width:140px;padding:18px 16px;background:${f};border-radius:14px;border:1px solid ${g};position:relative;overflow:hidden">`,d&&(r+=`<p style="margin:0px 0px 8px;font-size:11px;letter-spacing:2px;font-weight:700;color:${h};text-transform:uppercase;line-height:1.4">${k(d)}</p>`),c&&(r+=`<p style="margin:0px 0px 6px;font-size:32px;font-weight:900;color:${x};line-height:1.1">${k(c)}</p>`),p&&(r+=`<p style="margin:0px;font-size:13px;color:${u};line-height:1.5">${k(p)}</p>`),r+="</section>"}),r+="</section>",r}const Ko={spec:{name:"metrics",category:"infographic",serves:["attention"],bodyFormat:"rows",label:"指标卡片"},renderer:ne({name:"metrics",bodyFormat:"rows"},Go)};function Vo(e,t){const n=e.fields,i=(n.type||"data").toLowerCase(),r=t.t.accent,o=t.t.light,a=n.value??"",l=n.label??"",d=n.note??"";if(i==="quote"){let p=`<section style="margin:0px 0px 28px;padding:22px 20px;background:${o};border-left:4px solid ${r};border-radius:0px 14px 14px 0px;position:relative;overflow:hidden">`;return a&&(p+=`<p style="margin:0px 0px 10px;font-size:17px;font-weight:700;color:#1a1a1a;line-height:1.6;letter-spacing:-0.2px">${k(a)}</p>`),l&&(p+=`<p style="margin:0px;font-size:12px;color:#64748b;line-height:1.5;letter-spacing:0.3px">— ${k(l)}</p>`),p+="</section>",p}if(i==="fact"){let p='<section style="margin:0px 0px 28px;padding:22px 20px;background:#fffbe6;border-radius:14px;border:1px solid #fde68a;position:relative;overflow:hidden">';return a&&(p+=`<p style="margin:0px 0px 8px;font-size:16px;font-weight:700;color:#1a1a1a;line-height:1.6"><span style="margin-right:8px">📌</span>${k(a)}</p>`),d&&(p+=`<p style="margin:0px;font-size:13px;color:#92400e;line-height:1.6">${k(d)}</p>`),p+="</section>",p}let c=`<section style="margin:0px 0px 28px;padding:30px 20px;background:linear-gradient(135deg,${o} 0%,#ffffff 100%);border-radius:14px;border:1px solid ${r}22;text-align:center">`;return l&&(c+=`<p style="margin:0px 0px 6px;font-size:11px;letter-spacing:2.4px;font-weight:700;color:${r};text-transform:uppercase;line-height:1.4">${k(l)}</p>`),a&&(c+=`<p style="margin:0px;font-size:36px;font-weight:900;color:${r};line-height:1.15;letter-spacing:-1px">${k(a)}</p>`),d&&(c+=`<p style="margin:12px 0px 0px;font-size:13px;color:#64748b;line-height:1.6">${k(d)}</p>`),c+="</section>",c}const Yo={spec:{name:"infographic",category:"infographic",serves:["attention"],bodyFormat:"fields",label:"信息图"},renderer:ne({name:"infographic",bodyFormat:"fields"},Vo)};function Xo(e,t){const n=e.rows,i=t.t.accent,r=t.t.light;let o='<section style="margin:0px 0px 28px;display:flex;flex-direction:column;gap:12px">';return n.forEach(a=>{const l=Vi(a),d=Ki(a),c=l[0]??"",p=l[1]??"",f=l[2]??"",g=d?i:"#e2e8f0",x=d?i:"#ffffff",h=d?"#ffffff":"#1a1a1a",u=d?"rgba(255,255,255,0.85)":"#475569",y=d?"#ffffff":"#475569",j=d?"rgba(255,255,255,0.18)":r;o+=`<section style="display:grid;grid-template-columns:120px 1fr 1fr;border:1px solid ${g};border-radius:14px;overflow:hidden;background:${x}">`,o+=`<p style="margin:0px;padding:14px 14px;font-size:13px;font-weight:800;color:${h};border-right:1px solid ${g};line-height:1.5">${k(c)}</p>`,o+=`<p style="margin:0px;padding:14px 14px;font-size:13px;color:${u};border-right:1px solid ${g};line-height:1.6">${k(p)}</p>`,o+=`<p style="margin:0px;padding:14px 14px;font-size:13px;font-weight:${d?"700":"400"};color:${y};background:${j};line-height:1.6">${k(f)}</p>`,o+="</section>"}),o+="</section>",o}const Zo={spec:{name:"compare",category:"infographic",serves:["readability"],bodyFormat:"rows",label:"双栏对比"},renderer:ne({name:"compare",bodyFormat:"rows"},Xo)};function Jo(e,t){const n=e.rows,i=t.t.accent;let r='<section style="margin:0px 0px 28px;display:flex;flex-wrap:nowrap;gap:0px;overflow-x:auto;padding:12px 0px 8px">';return n.forEach((o,a)=>{const l=(o[0]??String(a+1)).trim(),d=(o[1]??"").trim(),c=(o[2]??"").trim(),p=a===n.length-1;r+='<section style="flex:1 0 150px;display:flex;flex-direction:column;align-items:flex-start;text-align:left;position:relative;padding:0px 14px">',r+=`<section style="width:38px;height:38px;border-radius:50%;background:${i};color:#ffffff;font-size:15px;font-weight:800;display:flex;align-items:center;justify-content:center;margin-bottom:14px;box-shadow:0 2px 6px ${i}44;z-index:1;position:relative">${k(l)}</section>`,p||(r+=`<section style="position:absolute;top:18px;left:52px;right:-14px;height:2px;background:linear-gradient(90deg,${i}66,${i}22);z-index:0"></section>`),d&&(r+=`<p style="margin:0px 0px 6px;font-size:14px;font-weight:700;color:#1a1a1a;line-height:1.35">${k(d)}</p>`),c&&(r+=`<p style="margin:0px;font-size:12px;color:#64748b;line-height:1.6">${k(c)}</p>`),r+="</section>"}),r+="</section>",r}const Qo={spec:{name:"steps",category:"infographic",serves:["readability"],bodyFormat:"rows",label:"步骤卡片"},renderer:ne({name:"steps",bodyFormat:"rows"},Jo)};function ea(e,t){const n=e.fields,i=t.t.accent;let r=`<section style="margin:0px 0px 28px;padding:28px 24px;background:${i}08;border-left:4px solid ${i};border-radius:0px 14px 14px 0px;text-align:center">`;return n.label&&(r+=Xe(k(n.label),i)),n.title&&(r+=Ce(k(n.title),{color:t.t.dark,size:"28px",weight:"900",align:"center"})),n.body&&(r+=`<section style="margin:12px auto 0px;max-width:520px;font-size:15px;color:#475569;line-height:1.8;letter-spacing:0.3px;text-align:justify">${te(n.body,t.t)}</section>`),n.note&&(r+=`<p style="margin:16px 0px 0px;font-size:13px;color:#94a3b8;line-height:1.6;font-style:italic">${k(n.note)}</p>`),r+="</section>",r}const ta={spec:{name:"verdict",category:"judgment",serves:["memorability"],bodyFormat:"fields",label:"判断强调卡片"},renderer:ne({name:"verdict",bodyFormat:"fields"},ea)};function na(e,t){const n=e.rows,i=t.t.accent;let r='<section style="margin:0px 0px 28px;display:flex;flex-direction:column;gap:10px">';return n.forEach(o=>{var x;const a=(x=o[0])==null?void 0:x.trim().toLowerCase(),l=o.slice(1).join("|").trim(),d=a==="fit",c=d?"✓":"✗",p=d?i:"#dc2626",f=d?`${i}12`:"#fef2f2",g=d?`${i}44`:"#fecaca";r+=`<section style="display:flex;align-items:flex-start;gap:12px;padding:14px 16px;background:${f};border:1px solid ${g};border-radius:12px">`,r+=`<span style="flex-shrink:0;width:24px;height:24px;display:inline-flex;align-items:center;justify-content:center;border-radius:50%;background:#fff;color:${p};font-size:14px;font-weight:900;border:1.5px solid ${p}">${c}</span>`,r+=`<p style="margin:0px;font-size:14px;color:#334155;line-height:1.7;letter-spacing:0.3px">${k(l)}</p>`,r+="</section>"}),r+="</section>",r}const ia={spec:{name:"audience-fit",category:"judgment",serves:["readability"],bodyFormat:"rows",label:"受众匹配"},renderer:ne({name:"audience-fit",bodyFormat:"rows"},na)};function ra(e,t){const n=e.rows,i=t.t.accent;let r='<section style="margin:0px 0px 28px;display:grid;grid-template-columns:1fr 1fr;gap:14px">';return n.forEach(o=>{var u;const a=(u=o[0])==null?void 0:u.trim().toLowerCase(),l=o.slice(1).join("|").trim(),d=a==="myth",c=d?"误解":"事实",p=d?"🚫":"✓",f=d?"#fef2f2":"#f0fdf4",g=d?"#fecaca":"#bbf7d0",x=d?"#dc2626":i,h=d?"#7f1d1d":"#14532d";r+=`<section style="padding:18px 16px;background:${f};border:1px solid ${g};border-radius:14px;position:relative;overflow:hidden">`,r+=`<p style="margin:0px 0px 10px;font-size:11px;letter-spacing:2.4px;font-weight:800;color:${x};text-transform:uppercase;line-height:1.4">${p} ${k(c)}</p>`,r+=`<p style="margin:0px;font-size:14px;color:${h};line-height:1.7;letter-spacing:0.3px">${k(l)}</p>`,r+="</section>"}),r+="</section>",r}const sa={spec:{name:"myth-fact",category:"judgment",serves:["memorability"],bodyFormat:"rows",label:"辟谣卡片"},renderer:ne({name:"myth-fact",bodyFormat:"rows"},ra)};function oa(e,t){const n=e.fields,i=t.t.accent;let r='<section style="margin:36px 0px 32px;padding:48px 32px;background:#f8fafc;border-radius:16px;text-align:center;border:1px solid #e2e8f0">';return n.label&&(r+=Xe(k(n.label),i)),n.title&&(r+=Ce(k(n.title),{color:t.t.dark,size:"32px",weight:"900",align:"center"})),r+=`<section style="margin:24px auto 0px;width:48px;height:3px;border-radius:2px;background:${i}"></section>`,r+="</section>",r}const aa={spec:{name:"manifesto",category:"judgment",serves:["memorability"],bodyFormat:"fields",label:"宣言式大标题",fields:[{name:"label",required:!0,description:"标签/徽章文字"},{name:"title",required:!0,description:"主标题"}]},renderer:ne({name:"manifesto",bodyFormat:"fields"},oa)};function la(e,t){const n=e.fields,i=t.t.accent;let r='<section style="margin:0px 0px 28px;padding:20px 24px;display:flex;align-items:center;justify-content:center;gap:16px;background:#f8fafc;border-radius:14px;border:1px solid #e2e8f0">';return n.from&&(r+=`<p style="margin:0px;font-size:14px;color:#94a3b8;font-weight:600;letter-spacing:0.3px">${k(n.from)}</p>`),r+=`<span style="flex-shrink:0;font-size:16px;color:${i};font-weight:900">→</span>`,n.to&&(r+=`<p style="margin:0px;padding:6px 14px;font-size:14px;color:#fff;font-weight:700;letter-spacing:0.3px;background:${i};border-radius:8px">${k(n.to)}</p>`),r+="</section>",r}const ca={spec:{name:"bridge",category:"judgment",serves:["readability"],bodyFormat:"fields",label:"转场卡片"},renderer:ne({name:"bridge",bodyFormat:"fields"},la)};function da(e,t){const n=e.rows,i=t.t.accent;let r=`<section style="margin:0px 0px 28px;padding:24px 28px 24px 36px;background:${i}08;border-left:4px solid ${i};border-radius:0px 14px 14px 0px;position:relative">`;return r+=`<span style="position:absolute;top:8px;left:12px;font-size:56px;line-height:1;font-weight:900;color:${i}22;font-family:Georgia,serif">"</span>`,n.forEach(o=>{const a=o[0]??"",l=o[1]??"",d=o[2]??"";if(a&&(r+=`<p style="margin:0px 0px 12px;font-size:16px;color:#1a1a1a;line-height:1.8;letter-spacing:0.3px;font-style:italic;text-align:justify;padding-left:28px">${k(a)}</p>`),l||d){const c=[d,l].filter(Boolean).join(" · ");r+=`<p style="margin:0px;font-size:13px;color:#64748b;line-height:1.6;text-align:right;padding-left:28px">— ${k(c)}</p>`}}),r+="</section>",r}const pa={spec:{name:"quote",category:"evidence",serves:["memorability"],bodyFormat:"rows",label:"引用强调"},renderer:ne({name:"quote",bodyFormat:"rows"},da)};function fa(e,t){const n=e.fields,i=e.rows,r=t.t.accent;let o='<section style="margin:0px 0px 28px">';return n.title&&(o+=Ce(k(n.title),{color:"#1a1a1a",size:"18px",weight:"800"})),n.src&&(o+='<section style="position:relative;display:inline-block;max-width:100%;margin:0px 0px 12px;border-radius:12px;overflow:hidden">',o+=`<img src="${k(n.src)}" alt="${k(n.title||"")}" style="max-width:100%;display:block;border-radius:12px">`,i.forEach((a,l)=>{const d=a[0]??`${l+1}`,c=a[1]??"50",p=a[2]??"50",f=a[3]??"",g=a[4]??"",x=Math.max(0,Math.min(100,parseFloat(c)||0)),h=Math.max(0,Math.min(100,parseFloat(p)||0)),u=f||d;o+=`<span style="position:absolute;left:${x}%;top:${h}%;transform:translate(-50%,-50%);width:28px;height:28px;border-radius:50%;background:${r};color:#fff;font-size:13px;font-weight:800;display:flex;align-items:center;justify-content:center;box-shadow:0 2px 8px ${r}44;line-height:1" title="${k(g)}">${k(u)}</span>`}),o+="</section>"),i.length>0&&(o+='<section style="display:flex;flex-direction:column;gap:8px">',i.forEach((a,l)=>{const d=a[0]??`${l+1}`,c=a[3]??"",p=a[4]??"",f=c||d;o+='<section style="display:flex;align-items:flex-start;gap:10px;padding:8px 12px;background:#f8fafc;border-radius:10px">',o+=`<span style="flex-shrink:0;width:24px;height:24px;border-radius:50%;background:${r}15;color:${r};font-size:12px;font-weight:800;display:flex;align-items:center;justify-content:center;line-height:1">${k(d)}</span>`,o+='<section style="flex:1;min-width:0">',o+=`<p style="margin:0px;font-size:14px;font-weight:700;color:#1a1a1a;line-height:1.4">${k(f)}</p>`,p&&(o+=`<p style="margin:4px 0px 0px;font-size:13px;color:#64748b;line-height:1.6">${k(p)}</p>`),o+="</section>",o+="</section>"}),o+="</section>"),n.note&&(o+=`<p style="margin:12px 0px 0px;font-size:13px;color:#94a3b8;line-height:1.6;font-style:italic">${k(n.note)}</p>`),o+="</section>",o}const ga={spec:{name:"image-annotate",category:"evidence",serves:["readability"],bodyFormat:"fields",label:"图片标注"},renderer:ne({name:"image-annotate",bodyFormat:"fields"},fa)};function xa(e,t){var c,p;const n=e.fields,i=t.t.accent,r=(c=n.before)==null?void 0:c.trim(),o=(p=n.after)==null?void 0:p.trim(),a=(n.label_before||"Before").trim(),l=(n.label_after||"After").trim();let d='<section style="margin:0px 0px 28px">';return d+='<section style="display:grid;grid-template-columns:1fr 1fr;gap:12px">',d+='<section style="position:relative;border-radius:12px;overflow:hidden;border:1px solid #e2e8f0">',r&&(d+='<section style="position:absolute;top:12px;left:12px;z-index:2">',d+=`<span style="display:inline-block;padding:5px 14px;background:${i};color:#fff;font-size:12px;font-weight:700;border-radius:999px;letter-spacing:0.5px">${k(a)}</span>`,d+="</section>",d+=`<img src="${k(r)}" alt="${k(a)}" style="max-width:100%;display:block;border-radius:12px">`),d+="</section>",d+='<section style="position:relative;border-radius:12px;overflow:hidden;border:1px solid #e2e8f0">',o&&(d+='<section style="position:absolute;top:12px;left:12px;z-index:2">',d+=`<span style="display:inline-block;padding:5px 14px;background:${i};color:#fff;font-size:12px;font-weight:700;border-radius:999px;letter-spacing:0.5px">${k(l)}</span>`,d+="</section>",d+=`<img src="${k(o)}" alt="${k(l)}" style="max-width:100%;display:block;border-radius:12px">`),d+="</section>",d+="</section>",d+="</section>",d}const ua={spec:{name:"image-compare",category:"evidence",serves:["readability"],bodyFormat:"fields",label:"图片对比"},renderer:ne({name:"image-compare",bodyFormat:"fields"},xa)};function ma(e,t){const n=e.rows,i=t.t.accent;let r='<section style="margin:0px 0px 28px">';return n.forEach((o,a)=>{const l=o[0]??`${a+1}`,d=o[1]??"",c=o[2]??"";r+=`<section style="display:flex;gap:16px;margin-bottom:${c?"16px":"12px"};align-items:flex-start">`,r+='<section style="flex-shrink:0;display:flex;flex-direction:column;align-items:center">',r+=`<span style="width:32px;height:32px;border-radius:50%;background:${i};color:#fff;font-size:14px;font-weight:800;display:flex;align-items:center;justify-content:center;line-height:1">${k(l)}</span>`,a<n.length-1&&(r+=`<span style="width:2px;height:24px;background:${i}33;margin-top:4px"></span>`),r+="</section>",r+='<section style="flex:1;min-width:0;padding-top:4px">',d&&(r+=`<p style="margin:0px;font-size:15px;color:#1a1a1a;font-weight:700;line-height:1.5">${k(d)}</p>`),c&&(r+='<section style="margin-top:10px;border-radius:10px;overflow:hidden;border:1px solid #e2e8f0">',r+=`<img src="${k(c)}" alt="${k(d||l)}" style="max-width:100%;display:block;border-radius:10px">`,r+="</section>"),r+="</section>",r+="</section>"}),r+="</section>",r}const ha={spec:{name:"image-steps",category:"evidence",serves:["readability"],bodyFormat:"rows",label:"图文步骤"},renderer:ne({name:"image-steps",bodyFormat:"rows"},ma)};function ba(e,t){var o;const n=e.fields,i=(o=n.src)==null?void 0:o.trim();let r='<section style="margin:0px 0px 28px">';return n.title&&(r+=Ce(k(n.title),{color:"#1a1a1a",size:"18px",weight:"800"})),r+='<section style="display:flex;gap:20px;align-items:flex-start">',i&&(r+='<section style="flex:0 0 40%;max-width:280px;border-radius:12px;overflow:hidden;border:1px solid #e2e8f0">',r+=`<img src="${k(i)}" alt="${k(n.title||"")}" style="width:100%;display:block;border-radius:12px">`,r+="</section>"),r+='<section style="flex:1;min-width:0">',n.body&&(r+=`<section style="font-size:15px;color:#475569;line-height:1.8;letter-spacing:0.3px;text-align:justify">${te(n.body,t.t)}</section>`),r+="</section>",r+="</section>",r+="</section>",r}const ya={spec:{name:"image-text",category:"evidence",serves:["readability"],bodyFormat:"fields",label:"图文混排"},renderer:ne({name:"image-text",bodyFormat:"fields"},ba)};function va(e,t){const n=e.rows,i=t.t.accent;let r='<section style="margin:0px 0px 28px">';return n.forEach(o=>{const a=o[0]??"",l=o[1]??"";r+='<section style="margin-bottom:16px;padding:18px 20px;background:#fff;border:1px solid #e2e8f0;border-radius:14px">',r+='<section style="display:flex;align-items:flex-start;gap:12px;margin-bottom:10px">',r+=`<span style="flex-shrink:0;width:28px;height:28px;border-radius:8px;background:${i};color:#fff;font-size:14px;font-weight:800;display:flex;align-items:center;justify-content:center;line-height:1">Q</span>`,r+=`<p style="margin:0px;font-size:16px;font-weight:700;color:#1a1a1a;line-height:1.5;letter-spacing:-0.3px">${k(a)}</p>`,r+="</section>",l&&(r+=`<p style="margin:0px 0px 0px 40px;font-size:14px;color:#64748b;line-height:1.7;letter-spacing:0.3px;text-align:justify">${k(l)}</p>`),r+="</section>"}),r+="</section>",r}const wa={spec:{name:"faq",category:"conversion",serves:["conversion"],bodyFormat:"rows",label:"问答列表"},renderer:ne({name:"faq",bodyFormat:"rows"},va)};function ka(e,t){const n=e.rows;let i='<section style="margin:0px 0px 28px;padding:20px 22px;background:#fff;border:1px solid #e2e8f0;border-radius:14px">';return n.forEach(r=>{var x;const o=((x=r[r.length-1])==null?void 0:x.toLowerCase())??"",a=o==="done"||o==="todo"||o==="na",l=a?o:"todo",d=r[0]??"";let c,p,f,g;l==="done"?(c="✓",p="#16a34a",f="#94a3b8",g="line-through"):l==="na"?(c="—",p="#94a3b8",f="#94a3b8",g="none"):(c="",p="#cbd5e1",f="#1a1a1a",g="none"),i+='<section style="display:flex;align-items:center;gap:12px;padding:10px 0px;border-bottom:1px solid #f1f5f9">',l==="todo"?i+=`<span style="flex-shrink:0;width:20px;height:20px;border-radius:6px;border:2px solid ${p};background:#fff"></span>`:i+=`<span style="flex-shrink:0;width:20px;height:20px;border-radius:6px;background:${l==="done"?"#dcfce7":"#f1f5f9"};color:${p};font-size:13px;font-weight:800;display:flex;align-items:center;justify-content:center;line-height:1">${c}</span>`,i+=`<p style="margin:0px;font-size:15px;color:${f};line-height:1.5;text-decoration:${g}">${k(d)}</p>`,i+="</section>"}),i+="</section>",i}const $a={spec:{name:"checklist",category:"conversion",serves:["conversion"],bodyFormat:"rows",label:"任务清单"},renderer:ne({name:"checklist",bodyFormat:"rows"},ka)};function Sa(e,t){const n=e.rows,i=t.t.accent;let r='<section style="margin:0px 0px 28px;display:grid;grid-template-columns:repeat(2,1fr);gap:14px">';return n.forEach(o=>{const a=o[0]??"",l=o[1]??"",d=o[2]??"";r+='<section style="padding:20px 18px;background:#fff;border:1px solid #e2e8f0;border-radius:14px;position:relative;overflow:hidden">',r+=`<section style="position:absolute;top:0;left:0;right:0;height:3px;background:${i}"></section>`,l&&(r+=`<span style="display:inline-block;padding:3px 10px;font-size:11px;font-weight:700;color:${i};background:${i}0d;border-radius:999px;letter-spacing:0.5px;margin-bottom:10px">${k(l)}</span>`),a&&(r+=`<p style="margin:0px 0px 8px;font-size:16px;font-weight:800;color:#1a1a1a;line-height:1.4;letter-spacing:-0.3px">${k(a)}</p>`),d&&(r+=`<p style="margin:0px;font-size:13px;color:#64748b;line-height:1.6">${k(d)}</p>`),r+="</section>"}),r+="</section>",r}const ja={spec:{name:"cases",category:"conversion",serves:["memorability"],bodyFormat:"rows",label:"案例卡片"},renderer:ne({name:"cases",bodyFormat:"rows"},Sa)};function Na(e,t){const n=t.t.accent;let i='<section style="margin:0px 0px 28px;padding:24px 24px 20px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:14px">';return i+=`<p style="margin:0px 0px 14px;font-size:13px;font-weight:800;color:${n};letter-spacing:2px;text-transform:uppercase">本文要点</p>`,e.markdown.split(`
`).filter(o=>o.trim()).forEach(o=>{const l=o.trim().replace(/^(\d+\.|[-*])\s*/,"");i+='<section style="display:flex;gap:10px;margin-bottom:10px;align-items:flex-start">',i+=`<span style="flex-shrink:0;width:8px;height:8px;border-radius:50%;background:${n};margin-top:8px"></span>`,i+=`<p style="margin:0px;font-size:15px;color:#1a1a1a;line-height:1.7;letter-spacing:0.3px">${te(l,t.t)}</p>`,i+="</section>"}),i+="</section>",i}const Ea={spec:{name:"summary",category:"conversion",serves:["memorability"],bodyFormat:"markdown",label:"文章要点"},renderer:ne({name:"summary",bodyFormat:"markdown"},Na)};function _a(e,t){const n=e.fields,i=t.t.accent;let r=`<section style="margin:0px 0px 28px;padding:28px 24px;background:${i}08;border-left:4px solid ${i};border-radius:0px 14px 14px 0px;text-align:center">`;return r+='<p style="margin:0px 0px 8px;font-size:20px;line-height:1">📢</p>',n.title&&(r+=`<p style="margin:0px 0px 12px;font-size:18px;font-weight:800;color:#1a1a1a;line-height:1.4;letter-spacing:-0.3px">${k(n.title)}</p>`),n.body&&(r+=`<p style="margin:0px;font-size:15px;color:#475569;line-height:1.8;letter-spacing:0.3px;text-align:justify">${te(n.body,t.t)}</p>`),r+="</section>",r}const Ca={spec:{name:"notice",category:"conversion",serves:["readability"],bodyFormat:"fields",label:"重要通知"},renderer:ne({name:"notice",bodyFormat:"fields"},_a)};function Ta(e,t){var d;const n=e.fields,i=t.t.accent,r=k((n.title||"?").charAt(0).toUpperCase()),o=(d=n.avatar)==null?void 0:d.trim(),a=n.tags?n.tags.split(",").map(c=>c.trim()).filter(Boolean):[];let l='<section style="margin:0px 0px 28px;padding:24px;display:flex;gap:18px;align-items:flex-start;background:#fff;border:1px solid #e2e8f0;border-radius:16px">';return o?l+=`<section style="flex-shrink:0;width:64px;height:64px;border-radius:50%;overflow:hidden;border:2px solid ${i}22"><img src="${k(o)}" alt="${k(n.title||"")}" style="width:100%;height:100%;object-fit:cover;display:block"></section>`:l+=`<section style="flex-shrink:0;width:64px;height:64px;border-radius:50%;background:${i}15;border:2px solid ${i}22;display:flex;align-items:center;justify-content:center;font-size:24px;font-weight:900;color:${i}">${r}</section>`,l+='<section style="flex:1;min-width:0">',n.title&&(l+=`<p style="margin:0px 0px 4px;font-size:18px;font-weight:800;color:${t.t.dark};line-height:1.3;letter-spacing:-0.3px">${k(n.title)}</p>`),n.role&&(l+=`<p style="margin:0px 0px 8px;font-size:12px;font-weight:700;color:${i};letter-spacing:1.2px;text-transform:uppercase">${k(n.role)}</p>`),n.bio&&(l+=`<p style="margin:0px 0px 12px;font-size:14px;color:#64748b;line-height:1.7;letter-spacing:0.3px">${k(n.bio)}</p>`),a.length>0&&(l+=`<section style="display:flex;flex-wrap:wrap;gap:6px;margin-bottom:${n.note?"12px":"0px"}">`,a.forEach(c=>{l+=`<span style="display:inline-block;padding:3px 10px;font-size:11px;font-weight:600;color:${i};border:1px solid ${i}44;border-radius:999px;background:${i}08">${k(c)}</span>`}),l+="</section>"),n.note&&(l+=`<p style="margin:0px;font-size:12px;color:#94a3b8;line-height:1.6;font-style:italic">${k(n.note)}</p>`),l+="</section>",l+="</section>",l}const za={spec:{name:"author-card",category:"brand",serves:["conversion"],bodyFormat:"fields",label:"作者信息卡"},renderer:ne({name:"author-card",bodyFormat:"fields"},Ta)};function Aa(e,t){const n=e.fields,i=t.t.accent;let r=`<section style="margin:0px 0px 28px;padding:36px 28px;background:linear-gradient(135deg,${i}0a 0%,${i}05 100%);border:1px solid ${i}22;border-radius:16px;text-align:center">`;return n.title&&(r+=`<p style="margin:0px 0px 10px;font-size:22px;font-weight:900;color:${t.t.dark};line-height:1.35;letter-spacing:-0.5px">${k(n.title)}</p>`),n.body&&(r+=on(k(n.body),{color:"#64748b",size:"14px",align:"center"})),r+='<section style="margin:24px auto 0px;width:100px;height:100px;background:#fff;border:1px solid #e2e8f0;border-radius:12px;display:flex;align-items:center;justify-content:center">',r+='<section style="width:72px;height:72px;background:linear-gradient(135deg,#f8fafc,#e2e8f0);border-radius:8px;border:1px dashed #cbd5e1;display:flex;align-items:center;justify-content:center">',r+='<span style="font-size:10px;color:#94a3b8;font-weight:600;letter-spacing:0.5px">QR</span>',r+="</section></section>",r+='<p style="margin:12px 0px 0px;font-size:12px;color:#94a3b8;letter-spacing:0.5px">长按识别二维码关注</p>',r+="</section>",r}const Ma={spec:{name:"subscribe",category:"brand",serves:["conversion"],bodyFormat:"fields",label:"关注引导卡片"},renderer:ne({name:"subscribe",bodyFormat:"fields"},Aa)};function Ia(e,t){const n=e.rows,i=t.t.accent;let r='<section style="margin:0px 0px 28px;display:flex;flex-wrap:wrap;gap:14px">';return n.forEach(o=>{var p,f,g;const a=((p=o[0])==null?void 0:p.trim())??"",l=((f=o[1])==null?void 0:f.trim())??"",d=((g=o[2])==null?void 0:g.trim())??"",c=k(a.charAt(0).toUpperCase()||"?");r+='<section style="flex:1;min-width:140px;padding:18px 16px;background:#fff;border:1px solid #e2e8f0;border-radius:14px;text-align:center">',r+=`<section style="margin:0px auto 12px;width:48px;height:48px;border-radius:50%;background:${i}15;border:2px solid ${i}22;display:flex;align-items:center;justify-content:center;font-size:18px;font-weight:900;color:${i}">${c}</section>`,a&&(r+=`<p style="margin:0px 0px 4px;font-size:15px;font-weight:800;color:${t.t.dark};line-height:1.3">${k(a)}</p>`),l&&(r+=`<p style="margin:0px 0px 8px;font-size:11px;font-weight:700;color:${i};letter-spacing:1.2px;text-transform:uppercase">${k(l)}</p>`),d&&(r+=`<p style="margin:0px;font-size:12px;color:#94a3b8;line-height:1.6;letter-spacing:0.3px">${k(d)}</p>`),r+="</section>"}),r+="</section>",r}const La={spec:{name:"people",category:"brand",serves:["memorability"],bodyFormat:"rows",label:"人物卡横向排列"},renderer:ne({name:"people",bodyFormat:"rows"},Ia)};function Ra(e,t){const n=e.fields,i=t.t.accent;let r='<section style="margin:0px 0px 28px;padding:32px 28px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:16px;text-align:center">';return n.title&&(r+=`<p style="margin:0px 0px 10px;font-size:26px;font-weight:900;color:${t.t.dark};line-height:1.3;letter-spacing:-0.5px">${k(n.title)}</p>`),n.episode&&(r+=`<p style="margin:0px auto 14px;display:inline-block;padding:4px 14px;font-size:11px;font-weight:800;color:#fff;background:${i};letter-spacing:2.4px;text-transform:uppercase;border-radius:999px">${k(n.episode)}</p>`),n.topic&&(r+=on(k(n.topic),{color:"#64748b",size:"14px",align:"center"})),r+=`<section style="margin:20px auto 0px;width:48px;height:3px;border-radius:2px;background:${i}33"></section>`,r+="</section>",r}const Oa={spec:{name:"series",category:"brand",serves:["readability"],bodyFormat:"fields",label:"系列说明卡片"},renderer:ne({name:"series",bodyFormat:"fields"},Ra)},Da={name:"layout-definition",priority:6,match:e=>/^:::\s*definition\b/.test(e),render:(e,t,n,i)=>{const r=[];let o=i+1;for(;o<n.length&&!/^:::\s*$/.test(n[o]);)r.push(n[o]),o++;if(o>=n.length)return null;const a=r.join(`
`).trim(),l=_e(a);if(!l)return{html:'<section style="padding:12px;background:#fef2f2;border-left:4px solid #dc2626;border-radius:0 8px 8px 0;color:#781e1e;font-size:13px">⚠️ JSON 解析失败，请检查语法</section>',next:o+1};const d=String(l.term??""),c=String(l.def??""),p=String(l.termLabel??"");let f='<section style="margin:16px 0px;display:flex;border:1px solid #e2e8f0;border-radius:12px;overflow:hidden">';return f+=`<section style="flex:0 0 160px;padding:18px 16px;background:${e.t.accent};display:flex;flex-direction:column;justify-content:center;align-items:center;gap:6px">`,p&&(f+=`<p style="margin:0px;font-size:11px;letter-spacing:1.5px;text-transform:uppercase;font-weight:700;color:rgba(255,255,255,0.75)">${k(p)}</p>`),f+=`<p style="margin:0px;font-size:20px;font-weight:800;color:#fff;letter-spacing:-0.5px">${k(d)}</p>`,f+="</section>",f+='<section style="flex:1;padding:18px 20px;background:#fff;display:flex;align-items:center">',f+=`<p style="margin:0px;font-size:15px;color:#475569;line-height:1.75;letter-spacing:0.3px">${k(c)}</p>`,f+="</section>",f+="</section>",{html:f,next:o+1}}},Pa={spec:{name:"definition",category:"sprint4",serves:["readability"],bodyFormat:"json_object",label:"术语定义卡"},renderer:Da},Fa={name:"layout-quote-card",priority:6,match:e=>/^:::\s*quote-card\b/.test(e),render:(e,t,n,i)=>{const r=[];let o=i+1;for(;o<n.length&&!/^:::\s*$/.test(n[o]);)r.push(n[o]),o++;if(o>=n.length)return null;const a=r.join(`
`).trim(),l=_e(a);if(!l)return{html:'<section style="padding:12px;background:#fef2f2;border-left:4px solid #dc2626;border-radius:0 8px 8px 0;color:#781e1e;font-size:13px">⚠️ JSON 解析失败，请检查语法</section>',next:o+1};const d=String(l.text??""),c=String(l.source??"");let p='<section style="margin:16px 0px;padding:28px 32px;background:linear-gradient(135deg,#fefce8 0%,#fef9c3 100%);border-radius:14px;position:relative;overflow:hidden">';return p+=`<span style="position:absolute;top:8px;left:16px;font-size:72px;line-height:1;color:${e.t.accent}30;font-family:Georgia,ser-serif">"</span>`,p+=`<p style="margin:0px;padding:0px 24px;font-size:18px;font-style:italic;color:#1e293b;line-height:1.7;letter-spacing:0.4px;position:relative;z-index:1;text-align:center">${k(d)}</p>`,c&&(p+=`<p style="margin:16px 0px 0px;text-align:right;font-size:13px;color:#94a3b8;letter-spacing:0.5px">— ${k(c)}</p>`),p+="</section>",{html:p,next:o+1}}},Ba={spec:{name:"quote-card",category:"sprint4",serves:["memorability"],bodyFormat:"json_object",label:"金句卡"},renderer:Fa},Ha={name:"layout-tweet",priority:6,match:e=>/^:::\s*tweet\b/.test(e),render:(e,t,n,i)=>{const r=[];let o=i+1;for(;o<n.length&&!/^:::\s*$/.test(n[o]);)r.push(n[o]),o++;if(o>=n.length)return null;const a=r.join(`
`).trim(),l=_e(a);if(!l)return{html:'<section style="padding:12px;background:#fef2f2;border-left:4px solid #dc2626;border-radius:0 8px 8px 0;color:#781e1e;font-size:13px">⚠️ JSON 解析失败，请检查语法</section>',next:o+1};const d=String(l.name??""),c=String(l.handle??""),p=String(l.text??""),f=String(l.timestamp??""),g=String(l.likes??""),x=d.charAt(0).toUpperCase()||"?";let h='<section style="margin:16px 0px;padding:18px 20px;background:#fff;border:1px solid #e2e8f0;border-radius:14px">';return h+='<section style="display:flex;align-items:center;gap:12px;margin-bottom:12px">',h+=`<span style="width:42px;height:42px;border-radius:50%;background:${e.t.accent};display:flex;align-items:center;justify-content:center;font-size:17px;font-weight:700;color:#fff;flex-shrink:0">${k(x)}</span>`,h+='<section style="flex:1;min-width:0">',h+=`<p style="margin:0px;font-size:15px;font-weight:700;color:#1e293b;line-height:1.3">${k(d)}</p>`,c&&(h+=`<p style="margin:2px 0px 0px;font-size:13px;color:#94a3b8">${k(c)}</p>`),h+="</section>",h+="</section>",p&&(h+=`<p style="margin:0px 0px 12px;font-size:15px;color:#334155;line-height:1.7;letter-spacing:0.2px">${k(p)}</p>`),h+='<section style="display:flex;align-items:center;justify-content:space-between;padding-top:10px;border-top:1px solid #f1f5f9">',f?h+=`<p style="margin:0px;font-size:12px;color:#94a3b8">${k(f)}</p>`:h+="<span></span>",g&&(h+=`<p style="margin:0px;font-size:13px;color:#64748b;display:flex;align-items:center;gap:4px"><span style="color:#f43f5e">♥</span> ${k(g)}</p>`),h+="</section>",h+="</section>",{html:h,next:o+1}}},Ua={spec:{name:"tweet",category:"sprint4",serves:["memorability"],bodyFormat:"json_object",label:"推文卡片"},renderer:Ha},qa={name:"layout-stat-row",priority:6,match:e=>/^:::\s*stat-row\b/.test(e),render:(e,t,n,i)=>{const r=[];let o=i+1;for(;o<n.length&&!/^:::\s*$/.test(n[o]);)r.push(n[o]),o++;if(o>=n.length)return null;const a=r.join(`
`).trim(),l=Ye(a);if(!l)return{html:'<section style="padding:12px;background:#fef2f2;border-left:4px solid #dc2626;border-radius:0 8px 8px 0;color:#781e1e;font-size:13px">⚠️ JSON 解析失败，请检查语法</section>',next:o+1};const d=l.filter(f=>typeof f=="object"&&f!==null);if(d.length===0)return{html:'<section style="padding:12px;background:#fef2f2;border-left:4px solid #dc2626;border-radius:0 8px 8px 0;color:#781e1e;font-size:13px">⚠️ JSON 解析失败，请检查语法</section>',next:o+1};const c=d.length;let p='<section style="margin:16px 0px;padding:20px;background:#fff;border:1px solid #e2e8f0;border-radius:12px">';return p+=`<section style="display:grid;grid-template-columns:repeat(${c},1fr);gap:16px">`,d.forEach(f=>{const g=String(f.label??""),x=String(f.value??""),h=String(f.unit??"");p+='<section style="text-align:center">',g&&(p+=`<p style="margin:0px 0px 4px;font-size:12px;color:#94a3b8;letter-spacing:0.5px">${k(g)}</p>`),p+=`<p style="margin:0px;font-size:26px;font-weight:800;color:${e.t.accent};line-height:1.2">${k(x)}`,h&&(p+=`<span style="font-size:14px;font-weight:600;color:#64748b;margin-left:2px">${k(h)}</span>`),p+="</p>",p+="</section>"}),p+="</section></section>",{html:p,next:o+1}}},Wa={spec:{name:"stat-row",category:"sprint4",serves:["attention"],bodyFormat:"json_array",label:"数据指标行"},renderer:qa},Ga={name:"layout-question",priority:6,match:e=>/^:::\s*question\b/.test(e),render:(e,t,n,i)=>{const r=[];let o=i+1;for(;o<n.length&&!/^:::\s*$/.test(n[o]);)r.push(n[o]),o++;if(o>=n.length)return null;const a=r.join(`
`).trim(),l=Ye(a);if(!l)return{html:'<section style="padding:12px;background:#fef2f2;border-left:4px solid #dc2626;border-radius:0 8px 8px 0;color:#781e1e;font-size:13px">⚠️ JSON 解析失败，请检查语法</section>',next:o+1};const d=l.filter(p=>typeof p=="object"&&p!==null);if(d.length===0)return{html:'<section style="padding:12px;background:#fef2f2;border-left:4px solid #dc2626;border-radius:0 8px 8px 0;color:#781e1e;font-size:13px">⚠️ JSON 解析失败，请检查语法</section>',next:o+1};let c='<section style="margin:16px 0px;display:flex;flex-direction:column;gap:10px">';return d.forEach(p=>{const f=String(p.q??""),g=String(p.a??"");c+='<section style="border:1px solid #e2e8f0;border-radius:10px;overflow:hidden">',c+='<section style="padding:14px 18px;background:#f8fafc;border-bottom:1px solid #e2e8f0">',c+=`<p style="margin:0px;font-size:15px;font-weight:700;color:#1e293b;display:flex;align-items:center;gap:8px"><span style="color:${e.t.accent}">Q:</span> ${k(f)}</p>`,c+="</section>",c+='<section style="padding:14px 18px;background:#fff">',c+=`<p style="margin:0px;font-size:14px;color:#475569;line-height:1.75;letter-spacing:0.3px">${k(g)}</p>`,c+="</section>",c+="</section>"}),c+="</section>",{html:c,next:o+1}}},Ka={spec:{name:"question",category:"sprint4",serves:["readability"],bodyFormat:"json_array",label:"问答列表"},renderer:Ga},Va={name:"layout-resource-list",priority:6,match:e=>/^:::\s*resource-list\b/.test(e),render:(e,t,n,i)=>{const r=[];let o=i+1;for(;o<n.length&&!/^:::\s*$/.test(n[o]);)r.push(n[o]),o++;if(o>=n.length)return null;const a=r.join(`
`).trim(),l=Ye(a);if(!l)return{html:'<section style="padding:12px;background:#fef2f2;border-left:4px solid #dc2626;border-radius:0 8px 8px 0;color:#781e1e;font-size:13px">⚠️ JSON 解析失败，请检查语法</section>',next:o+1};const d=l.filter(p=>typeof p=="object"&&p!==null);if(d.length===0)return{html:'<section style="padding:12px;background:#fef2f2;border-left:4px solid #dc2626;border-radius:0 8px 8px 0;color:#781e1e;font-size:13px">⚠️ JSON 解析失败，请检查语法</section>',next:o+1};let c='<section style="margin:16px 0px;display:flex;flex-direction:column;gap:10px">';return d.forEach(p=>{const f=String(p.icon??""),g=String(p.name??""),x=String(p.url??""),h=String(p.desc??"");c+='<section style="padding:14px 16px;background:#fff;border:1px solid #e2e8f0;border-radius:10px;display:flex;align-items:flex-start;gap:12px">',f&&(c+=`<span style="font-size:22px;flex-shrink:0;margin-top:2px">${k(f)}</span>`),c+='<section style="flex:1;min-width:0">',g&&(x?c+=`<p style="margin:0px 0px 4px;font-size:15px;font-weight:700"><a href="${k(x)}" style="color:${e.t.accent};text-decoration:none" target="_blank" rel="noopener noreferrer">${k(g)}</a></p>`:c+=`<p style="margin:0px 0px 4px;font-size:15px;font-weight:700;color:#1e293b">${k(g)}</p>`),h&&(c+=`<p style="margin:0px;font-size:13px;color:#64748b;line-height:1.6">${k(h)}</p>`),c+="</section>",c+="</section>"}),c+="</section>",{html:c,next:o+1}}},Ya={spec:{name:"resource-list",category:"sprint4",serves:["conversion"],bodyFormat:"json_array",label:"资源列表"},renderer:Va},Xa={name:"layout-comparison-table",priority:6,match:e=>/^:::\s*comparison-table\b/.test(e),render:(e,t,n,i)=>{const r=[];let o=i+1;for(;o<n.length&&!/^:::\s*$/.test(n[o]);)r.push(n[o]),o++;if(o>=n.length)return null;const a=r.join(`
`).trim(),l=_e(a);if(!l)return{html:'<section style="padding:12px;background:#fef2f2;border-left:4px solid #dc2626;border-radius:0 8px 8px 0;color:#781e1e;font-size:13px">⚠️ JSON 解析失败，请检查语法</section>',next:o+1};const d=l.left??{},c=l.right??{},p=String(d.title??""),f=String(c.title??""),g=Array.isArray(d.items)?d.items.map(String):[],x=Array.isArray(c.items)?c.items.map(String):[],h=Math.max(g.length,x.length,1);let u='<section style="margin:16px 0px;display:grid;grid-template-columns:1fr 1fr;border-radius:12px;overflow:hidden;border:1px solid #e2e8f0">';u+='<section style="display:flex;flex-direction:column">',u+='<section style="padding:14px 18px;background:#3b82f6;text-align:center">',u+=`<p style="margin:0px;font-size:16px;font-weight:800;color:#fff;letter-spacing:0.5px">${k(p)}</p>`,u+="</section>";for(let y=0;y<h;y++){const j=g[y]??"",w=y%2===0?"#eff6ff":"#dbeafe";u+=`<section style="padding:12px 18px;background:${w};border-top:1px solid #bfdbfe;flex:1;display:flex;align-items:center">`,u+=`<p style="margin:0px;font-size:14px;color:#1e40af;line-height:1.6">${k(j)}</p>`,u+="</section>"}u+="</section>",u+='<section style="display:flex;flex-direction:column;border-left:1px solid #e2e8f0">',u+=`<section style="padding:14px 18px;background:${e.t.accent};text-align:center">`,u+=`<p style="margin:0px;font-size:16px;font-weight:800;color:#fff;letter-spacing:0.5px">${k(f)}</p>`,u+="</section>";for(let y=0;y<h;y++){const j=x[y]??"",w=y%2===0?"#f8fafc":"#f1f5f9";u+=`<section style="padding:12px 18px;background:${w};border-top:1px solid #e2e8f0;flex:1;display:flex;align-items:center">`,u+=`<p style="margin:0px;font-size:14px;color:#334155;line-height:1.6">${k(j)}</p>`,u+="</section>"}return u+="</section>",u+="</section>",{html:u,next:o+1}}},Za={spec:{name:"comparison-table",category:"sprint4",serves:["readability"],bodyFormat:"json_object",label:"对比表"},renderer:Xa},Ja=[{key:"added",label:"新增",bg:"#f0fdf4",fg:"#16a34a",tagBg:"#dcfce7"},{key:"changed",label:"变更",bg:"#fff7ed",fg:"#ea580c",tagBg:"#ffedd5"},{key:"fixed",label:"修复",bg:"#fef2f2",fg:"#dc2626",tagBg:"#fee2e2"}],Qa={name:"layout-changelog",priority:6,match:e=>/^:::\s*changelog\b/.test(e),render:(e,t,n,i)=>{const r=[];let o=i+1;for(;o<n.length&&!/^:::\s*$/.test(n[o]);)r.push(n[o]),o++;if(o>=n.length)return null;const a=r.join(`
`).trim(),l=_e(a);if(!l)return{html:'<section style="padding:12px;background:#fef2f2;border-left:4px solid #dc2626;border-radius:0 8px 8px 0;color:#781e1e;font-size:13px">⚠️ JSON 解析失败，请检查语法</section>',next:o+1};const d=String(l.version??""),c=String(l.date??"");let p='<section style="margin:16px 0px;padding:20px;background:#fff;border:1px solid #e2e8f0;border-radius:12px">';p+='<section style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;padding-bottom:14px;border-bottom:1px solid #f1f5f9">',p+='<section style="display:flex;align-items:center;gap:10px">',d&&(p+=`<span style="display:inline-block;padding:4px 12px;background:${e.t.accent};color:#fff;font-size:14px;font-weight:700;border-radius:6px;letter-spacing:0.5px">${k(d)}</span>`),p+="</section>",c&&(p+=`<p style="margin:0px;font-size:13px;color:#94a3b8">${k(c)}</p>`),p+="</section>";for(const f of Ja){const g=Array.isArray(l[f.key])?l[f.key].map(String):[];g.length!==0&&(p+='<section style="margin-bottom:14px">',p+=`<p style="margin:0px 0px 8px;font-size:13px;font-weight:700;color:${f.fg};display:flex;align-items:center;gap:6px"><span style="display:inline-block;width:4px;height:14px;border-radius:2px;background:${f.fg}"></span>${k(f.label)}</p>`,p+='<section style="display:flex;flex-wrap:wrap;gap:6px">',g.forEach(x=>{p+=`<span style="display:inline-block;padding:4px 10px;background:${f.tagBg};color:${f.fg};font-size:13px;font-weight:500;border-radius:5px;line-height:1.5">${k(x)}</span>`}),p+="</section></section>")}return p+="</section>",{html:p,next:o+1}}},el={spec:{name:"changelog",category:"sprint4",serves:["readability"],bodyFormat:"json_object",label:"版本日志"},renderer:Qa},an=[Oo,Po,Bo,Uo,Wo,Ko,Yo,Zo,Qo,ta,ia,sa,aa,ca,pa,ga,ua,ha,ya,wa,$a,ja,Ea,Ca,za,Ma,La,Oa,Pa,Ba,Ua,Wa,Ka,Ya,Za,el],tl=an.map(e=>e.renderer),nl=an.map(e=>e.spec),il=Object.fromEntries(an.map(e=>[e.spec.name,e.renderer])),Ji={spec:{name:"reading-path",label:"阅读路线",bodyFormat:"markdown",example:`:::reading-path
- 问题定义 | 为什么现有排版让读者在 3 秒内离开，数据背后的认知科学原理
    - 模块原理 | 61 个排版组件各自解决什么场景问题，从开篇到结尾全覆盖
- 实战示例 | 一篇观点文从空白草稿到发布成品的完整排版过程拆解
    - 主题系统 | 52 套专业配色方案，一键切换品牌气质，无需设计背景
- 行动指南 | 今天就能上手的 3 步方法：选模块 → 填内容 → 复制发布
:::`,fields:[]},render(e,t,n,i){const r=(n.markdown||t).split(`
`).map(a=>a.trim()).filter(a=>a.startsWith("- ")).map(a=>a.slice(2).split("|").map(l=>l.trim())).filter(a=>a[0]);if(r.length<2)return"";let o=`<section style="margin:0px 0px ${m[12]}"><section>`;return o+=`<section style="display:flex;align-items:flex-end;justify-content:space-between;padding-bottom:${m[6]};gap:${m[5]}"><section style="flex-shrink:0"><p style="margin:0px;padding:0px 0px ${m[2]};font-size:11px;color:#94a3b8;text-transform:uppercase;letter-spacing:2.8px;font-weight:800;white-space:nowrap">READING PATH</p><p style="margin:0px;font-size:16px;line-height:1.35;color:#1a1a1a;font-weight:700">阅读路线</p></section><p style="margin:0px;font-size:11px;color:#94a3b8;white-space:nowrap">${r.length} 个章节</p></section>`,o+=`<section style="padding:${m[6]} ${m[5]} ${m[5]};border:1px solid #e2e8f0;border-radius:${R["3xl"]};background:linear-gradient(white 0%,#f8fafc 100%);overflow-x:auto;white-space:nowrap;font-size:0px">`,r.forEach((a,l)=>{const d=String(l+1).padStart(2,"0"),c=l===0;o+='<section style="display:inline-flex;vertical-align:middle;align-items:center">',o+='<section style="display:inline-block;vertical-align:top;width:126px;white-space:normal;text-align:center">',o+=`<section style="display:flex;justify-content:center;margin-bottom:${m[4]}">`,o+=`<span style="display:inline-flex;align-items:center;justify-content:center;width:34px;height:34px;border-radius:50%;background:${c?i.accent:"white"};color:${c?"white":i.accent};border:1px solid ${c?i.accent:"#dbe3ee"};font-size:12px;font-weight:900">${d}</span>`,o+="</section>",o+=`<p style="margin:0px;font-size:13px;line-height:1.55;color:${c?"#1a1a1a":"#334155"};font-weight:700;white-space:normal;word-break:break-all">${_(a[0])}</p>`,o+="</section>",l<r.length-1&&(o+=`<span style="display:inline-block;vertical-align:middle;width:32px;height:1px;margin:0px ${m[3]};background:linear-gradient(90deg,#94a3b859,#94a3b8d9);color:transparent;overflow:hidden">-</span>`),o+="</section>"}),o+="</section></section></section>",o},renderLegacy(e,t,n){return this.render(e,t,ge(t,this.spec.bodyFormat),n)}},rl=ye(Ji),In={NOTE:{icon:"📝",bg:"#eff6ff",border:"#2563eb",label:"笔记"},INFO:{icon:"ℹ️",bg:"#f0f9ff",border:"#0ea5e9",label:"信息"},TIP:{icon:"💡",bg:"#f0fdf4",border:"#16a34a",label:"提示"},SUCCESS:{icon:"✅",bg:"#f0fdf4",border:"#16a34a",label:"成功"},WARNING:{icon:"⚠️",bg:"#fffbea",border:"#ea580c",label:"警告"},DANGER:{icon:"❌",bg:"#fef2f2",border:"#dc2626",label:"危险"},CAUTION:{icon:"🚨",bg:"#fef2f2",border:"#dc2626",label:"严重"},IMPORTANT:{icon:"❗",bg:"#f5f3ff",border:"#7c3aed",label:"重要"}};function sl(e,t,n){const i=["info","tip","warning","success","danger","note","caution","important"];let r=(e.type||"").toUpperCase();if(!r){for(const g of i)if(e[g]==="true"){r=g.toUpperCase();break}}r||(r="INFO");let o=e.title||"",a=t;const l=t.match(/^>\s*\[!?(TIP|NOTE|WARNING|CAUTION|IMPORTANT|INFO)\]\s*(.*)/im);if(l){r=l[1].toUpperCase(),o=o||l[2];const g=t.split(`
`),x=[];for(const h of g){const u=h.match(/^>\s?(.*)/);if(u){if(/^>\s*\[!?\[/.test(h)||/^>\s*\[!?\w+\]/.test(h))continue;x.push(u[1])}}a=x.join(`
`).trim()}const d=In[r]||In.INFO,c=d.bg,p=d.border;let f=`<section style="margin:${m[7]} 0px;padding:${m[7]} ${m[6]};background:${c};border-left:4px solid ${p};border-radius:0px ${R.xl} ${R.xl} 0px">`;return o&&(f+=`<p style="margin:0px 0px ${m[2]};font-size:${E.xl};font-weight:${A.bold};color:${p}">${V((d.icon||"")+" "+o)}</p>`),a.trim()&&(f+=`<section style="font-size:${E.xl};color:${D.gray700};line-height:${X.looser};letter-spacing:${K.wider};text-align:justify">${te(a.trim(),n)}</section>`),f+="</section>",f}const Qi={spec:{name:"callout",label:"提示框",bodyFormat:"markdown",example:`:::callout type="tip" title="排版小技巧"
如果你不确定某个段落应该使用哪个模块，可以遵循一个简单原则：**信息型内容用正文模块**（提示框、代码块、表格），**结构型内容用导航模块**（阅读路线、章节分隔、步骤流程）。

这个原则在 80% 的场景下都能帮你快速做出选择。
:::`,fields:[{name:"type",required:!1,description:"提示类型：info / tip / warning / success / danger"},{name:"title",required:!1,description:"标题"}]},render(e,t,n,i){return sl(e,n.markdown,i)},renderLegacy(e,t,n){return this.render(e,t,ge(t,this.spec.bodyFormat),n)}},ol=ye(Qi),er={spec:{name:"code-block",label:"代码块",bodyFormat:"markdown",example:`:::code-block lang="js" title="示例" line-numbers
\`\`\`js{2,4-5}
function hello(name) {
  console.log('Hello', name)        // [!code focus]
  const time = Date.now()           // [!code highlight]
  if (!name) throw new Error('no')  // [!code error]
  return { name, time }             // [!code warning]
}
\`\`\`
:::`,fields:[{name:"lang",required:!1,description:"语言（js/ts/python/css/html/bash/json/md）"},{name:"title",required:!1,description:"标题"},{name:"line-numbers",required:!1,description:"启用行号"}]},render(e,t,n,i){let r=n.markdown.trim(),o=e.lang||"";const a=r.match(/^```(\S*)\n([\s\S]*?)```$/);a&&(o||(o=a[1]),r=a[2]);const l=e.title||"",d=e["line-numbers"]==="true"||e["line-numbers"]==="";let c=`<section style="margin:${m[7]} 0px;border-radius:${R.lg};overflow:hidden">`;return l&&(c+='<section style="display:flex;align-items:center;justify-content:space-between;padding:8px 12px;background:#2d2d3f;border-bottom:1px solid rgba(255,255,255,0.08)">',c+=`<span style="font-size:11px;font-weight:600;color:#a5b4fc">${l}</span>`,c+=`<span style="font-size:10px;color:#64748b">${o||"text"}</span>`,c+="</section>"),c+=Ee(r,o,{lineNumbers:d}),c+="</section>",c},renderLegacy(e,t,n){return this.render(e,t,ge(t,this.spec.bodyFormat),n)}},al=ye(er);function ll(e){const t=e.trim().split(`
`),n=[];for(const i of t){if(/^:{3,4}\s*$/.test(i.trim()))break;n.push(i)}return{title:"",content:n.join(`
`).trim()}}const Ln="",Rn="";function cl(e,t){if(!e)return"";const n=[];let i=e.replace(/```(\w*)\n([\s\S]*?)```/g,(r,o,a)=>{const l=n.length;return n.push(Ee(a,o||"")),`${Ln}CB${l}${Rn}`});return i=te(i,t),i=i.replace(new RegExp(`${Ln}CB(\\d+)${Rn}`,"g"),(r,o)=>n[parseInt(o)]||""),i=i.replace(/\n{2,}/g,`
`).replace(/[ \t]*\n[ \t]*/g,"<br>"),i}const dl={tip:"💡",note:"📝",info:"ℹ️",warning:"⚠️",caution:"🚨",important:"❗"},pl={tip:"提示",note:"注意",info:"信息",warning:"警告",caution:"危险",important:"重要"},fl={tip:"#f0fdf4",note:"#eff6ff",info:"#f0f9ff",warning:"#fffbea",caution:"#fef2f2",important:"#f5f3ff"},gl={tip:"#16a34a",note:"#2563eb",info:"#0ea5e9",warning:"#ea580c",caution:"#dc2626",important:"#7c3aed"},tr={spec:{name:"hint",label:"提示容器",bodyFormat:"markdown",example:':::hint type="info" title="参考信息"\n提供相关的背景资料和补充说明，支持 `inline code` 行内代码。\n```js\nconst a = 1\nconst b = 2\nconst c = a + b\n```\n内容末尾同样支持 **粗体** 和 *斜体*。\n:::',fields:[{name:"type",required:!1,description:"类型（info/tip/note/warning/caution/important）"},{name:"title",required:!1,description:"标题"}]},render(e,t,n,i){const r=e.type||"info",{title:o,content:a}=ll(n.markdown),l=e.title||o||pl[r]||r,d=dl[r]||"",c=fl[r]||"#f0f4fa",p=gl[r]||i.accent;let f=`<section style="margin:${m[7]} 0px;padding:${m[7]} ${m[6]};background:${c};border-left:4px solid ${p};border-radius:0px ${R.xl} ${R.xl} 0px">`;return f+=`<p style="margin:0px 0px ${m[2]};font-size:${E.xl};font-weight:${A.bold};color:${p}">${V(d+" "+l)}</p>`,a&&(f+=`<section style="font-size:${E.xl};color:${D.gray700};line-height:${X.looser};letter-spacing:${K.wider};text-align:justify">${cl(a,i)}</section>`),f+="</section>",f},renderLegacy(e,t,n){return this.render(e,t,ge(t,this.spec.bodyFormat),n)}},xl=ye(tr),ul=[bo,yo,So,ho,To,rl,Ao,Co,wo,ol,al,Mo,xl];function xe(e,t,n,i){if(!e||t<0||t>=e.length)return null;const r=e[t];if(!r)return null;const o=r.match(n);if(!o)return null;const a=o[1]?be(o[1]):{};if(o[2]!==void 0&&i.test(o[2])){const p=o[2].replace(i,"").trim();return{attrs:a,body:p,next:t+1}}let l=o[2]!==void 0?o[2]+`
`:"",d=t+1;const c=50;for(;d<e.length&&!i.test(e[d]);)if(l+=e[d]+`
`,d++,d-t>c)return{attrs:a,body:l.trim(),next:d,warning:`自定义标签未闭合，已扫描 ${c} 行后自动截断，请检查闭合标签`};if(d>=e.length)return null;if(d<e.length){const p=e[d].match(i);p&&(l+=e[d].substring(0,p.index)),d++}return{attrs:a,body:l.trim(),next:d}}function ml(e){if(!e)return!1;const t=e.trim();return/^\s*(<\s*!\[|!\[|<img)/i.test(t)}function hl(e,t){if(t<0||t>=e.length||e[t].indexOf("|")<0)return!1;let i=t+1;for(;i<e.length&&e[i].trim()==="";)i++;return i>=e.length?!1:/\|[\s-:]+\|/.test(e[i])}function bl(e,t){for(let n=t-1;n>=0;n--){if(/^```mermaid\b/.test(e[n].trim()))return!0;if(/^```/.test(e[n].trim()))return!1}return!1}function yl(e,t){for(let n=t-1;n>=0;n--){const i=e[n].trim();if(i!==""){if(ml(i)||/^```\s*$/.test(i)&&bl(e,n))return!0;break}}return!1}function Be(e){return e.tokens??e.tokensRaw}function vl(e,t){for(let n=t+1;n<e.length;n++)if(e[n].trim()!==""){if(hl(e,n))return!0;break}return!1}const wl={name:"emptyLine",match:e=>e.trim()==="",render:(e,t,n,i)=>({html:"",next:i+1})},kl={name:"separator",match:e=>/^---+\s*$/.test(e.trim()),render:(e,t,n,i)=>{const r=Be(e);return{html:`<section style="border:none;height:1px;background:linear-gradient(90deg,transparent,${r.headingColor==="accent"||r.headingColor==="dark"?`${e.t.accent}55`:D.gray350},transparent);margin:${m[10]} 0px"></section>`,next:i+1}}};function $l(e){if(!/<step[\s/>]/.test(e))return e;const t=[];for(const n of e.split(`
`)){const i=n.match(/<step\b([^>]*)>(?:([\s\S]*?)<\/step>)?/);if(!i){t.push(n);continue}const r=be(i[1]),o=(i[2]??"").replace(/<[^>]+>/g,"").trim(),a=r.title||o,l=r.desc||"";(a||l)&&t.push(`- ${a} | ${l}`)}return t.join(`
`)}const Sl={name:"steps",match:e=>/^<steps\b/.test(e),render:(e,t,n,i)=>{const r=xe(n,i,/^<steps\b([^>]*)>(.*)$/,/<\/steps>/)||xe(n,i,/^<steps\b([^>]*)>/,/<\/steps>/);if(!r)return null;const o=$l(r.body),a=o.split(`
`).filter(c=>/^-\s*.+\s*\|\s*.+/.test(c.trim())).length;return{html:(r.attrs.type==="DA02"||!r.attrs.type&&a>3?Zt:Xt).renderLegacy(r.attrs,o,e.t),next:r.next,warning:r.warning}}},jl={name:"statement",match:e=>/^<statement\b/.test(e),render:(e,t,n,i)=>{const r=xe(n,i,/^<statement\b([^>]*)>(.*)$/,/<\/statement>/);return r?{html:Yi.render(r.attrs,r.body,e.t),next:r.next,warning:r.warning}:null}},Nl={name:"badges",match:e=>/^<badges\b/.test(e),render:(e,t,n,i)=>{const r=xe(n,i,/^<badges\b([^>]*)>(.*)$/,/<\/badges>/);return r?{html:Bi.render(r.attrs,r.body,e.t),next:r.next,warning:r.warning}:null}},El={name:"ctaContainer",match:e=>/^:::\s*cta\b/.test(e),render:(e,t,n,i)=>oo(n,i,e.t)},_l={name:"leadContainer",match:e=>/^:::\s*lead\b/.test(e),render:(e,t,n,i)=>{const r=xe(n,i,/^:::\s*lead\b(.*)$/,/^:::\s*$/);return r?{html:Jt.render(r.attrs,r.body,e.t),next:r.next,warning:r.warning}:null}},Cl=["tip","note","warning","info","caution","important"],Tl=new RegExp(`^:{3,4}\\s*(${Cl.join("|")})\\b`),zl={name:"hintContainer",priority:5,match:e=>Tl.test(e),render:(e,t,n,i)=>{const r=t.match(/^(:{3,4})\s*(tip|note|warning|info|caution|important)\b\s*(.*)/);if(!r)return null;const o=r[1].length,a=r[2],l=r[3].trim(),d=new RegExp(`^:{${o}}\\s*$`),c=[];let p=i+1;for(;p<n.length&&!d.test(n[p]);)c.push(n[p]),p++;if(p>=n.length)return null;const f=c.join(`
`).trim(),g=S=>S?e.parseMarkdownFn?e.parseMarkdownFn(S,e.t,e.formulaMap,e.mermaidMap,e.tokens):te(S,e.t,e.formulaMap):"",x={tip:"💡",note:"📝",info:"ℹ️",warning:"⚠️",caution:"🚨",important:"❗"},h={tip:"#f0fdf4",note:"#eff6ff",info:"#f0f9ff",warning:"#fffbea",caution:"#fef2f2",important:"#f5f3ff"},u={tip:"#16a34a",note:"#2563eb",info:"#0ea5e9",warning:"#ea580c",caution:"#dc2626",important:"#7c3aed"},j=l||{tip:"提示",note:"注意",info:"信息",warning:"警告",caution:"危险",important:"重要"}[a]||a,w=h[a]||"#f0f4fa",N=u[a]||e.t.accent,b=Be(e).radiusMap;let $=`<section style="margin:${m[7]} 0px;padding:${m[7]} ${m[6]};background:${w};border-left:4px solid ${N};border-radius:0px ${b.xl} ${b.xl} 0px">`;return $+=`<p style="margin:0px 0px ${m[2]};font-size:${E.xl};font-weight:${A.bold};color:${N}">${V((x[a]||"")+" "+j)}</p>`,f&&($+=`<section style="font-size:${E.xl};color:${D.gray700};line-height:${X.looser};letter-spacing:${K.wider};text-align:justify">${g(f)}</section>`),$+="</section>",{html:$,next:p+1}}},Al={name:"tableContainer",priority:5,match:e=>/^:{3,4}\s*table\b/.test(e),render:(e,t,n,i)=>{const r=t.match(/^(:{3,4})\s*table\b\s*(.*)/);if(!r)return null;const o=r[1].length,a=r[2].trim(),l=new RegExp(`^:{${o}}\\s*$`),d=[];let c=i+1;for(;c<n.length&&!l.test(n[c]);)d.push(n[c]),c++;if(c>=n.length)return null;const p={},f=/(\w[\w-.]*)=("[^"]*"|\S+)/g;let g,x=0;for(;g=f.exec(a);){const N=g[1];let b=g[2];b.startsWith('"')&&b.endsWith('"')&&(b=b.slice(1,-1)),p[N]=b,x=g.index+g[0].length}const h=a.substring(x).trim(),u=d.join(`
`).trim();if(!u)return null;const y=e.t;c++;let j="";if(c<n.length){const N=n[c].trim();N&&!N.includes("|")&&!/^:{3,4}/.test(N)&&(j=N,c++)}let w="";return h&&(w+=`<section style="margin-bottom:${m[3]};padding:${m[3]} 0;text-align:center"><span style="display:inline-flex;align-items:center;gap:6px;font-size:${E.sm};font-weight:${A.semibold};color:${y.accent}"><span style="display:inline-block;width:3px;height:14px;border-radius:2px;background:${y.accent}"></span>${V(h)}</span></section>`),w+=sn.renderLegacy(p,u,y),j&&(w+=`<p style="margin:${m[3]} ${m[3]} 0;text-align:right;font-size:11px;color:#94a3b8">${V(j)}</p>`),{html:w,next:c}}},Ml={name:"alignTag",priority:5,match:e=>/^<align\b/.test(e),render:(e,t,n,i)=>{const r=xe(n,i,/^<align\b([^>]*)>(.*)$/,/<\/align>/);return r?{html:rn.renderLegacy(r.attrs,r.body,e.t),next:r.next,warning:r.warning}:null}},Il={name:"leadTag",match:e=>/^<lead\b/.test(e),render:(e,t,n,i)=>{const r=xe(n,i,/^<lead\b([^>]*)>(.*)$/,/<\/lead>/);return r?{html:Jt.render(r.attrs,r.body,e.t),next:r.next,warning:r.warning}:null}},Ll={name:"breaking",match:e=>/^<breaking\b/.test(e),render:(e,t,n,i)=>{const r=xe(n,i,/^<breaking\b([^>]*)>(.*)$/,/<\/breaking>/)||xe(n,i,/^<breaking\b([^>]*)>/,/<\/breaking>/);return r?{html:Yt.renderLegacy(r.attrs,r.body,e.t),next:r.next,warning:r.warning}:null}},Rl={name:"cta",match:e=>/^<cta\b/.test(e),render:(e,t,n,i)=>/<\/cta>/.test(t)?lo(n,i,e.t):ao(n,i,e.t)},Ol={name:"readingPath",match:e=>/^<reading-path\s*\/?>/.test(e)||/^<reading-path>/.test(e),render:(e,t,n,i)=>{const{t:r,pTitleLevel1List:o}=e;let a="";return o.length>1&&(a+=`<section style="margin:0px 0px ${m[12]}"><section>`,a+=`<section style="display:flex;align-items:flex-end;justify-content:space-between;padding-bottom:${m[6]};gap:${m[5]}"><section style="flex-shrink:0"><p style="margin:0px;padding:0px 0px ${m[2]};font-size:${E["2xs"]};color:${U.inkMuted};text-transform:uppercase;letter-spacing:${K["4xl"]};font-weight:${A.extrabold};white-space:nowrap">${_("READING PATH")}</p><p style="margin:0px;font-size:${E.xl};line-height:${X.snug};color:${U.textPrimary};font-weight:${A.extrabold}">${_("阅读路线")}</p></section><p style="margin:0px;font-size:${E["2xs"]};color:${U.inkFaint};white-space:nowrap">${_(o.length+" 个章节")}</p></section>`,a+=`<section style="padding:${m[6]} ${m[5]} ${m[5]};border:1px solid ${D.gray250};border-radius:${R["3xl"]};background:linear-gradient(${U.surface} 0%,${D.gray100} 100%);${Ke.cardHover};overflow-x:auto;white-space:nowrap;font-size:0px">`,o.forEach((l,d)=>{const c=l.title.replace(/::.*/,"").trim().replace(/^\d+\s*/,""),p=l.num||String(d+1).padStart(2,"0"),f=d===0;a+='<section style="display:inline-flex;vertical-align:middle;align-items:center">',a+='<section style="display:inline-block;vertical-align:top;width:126px;white-space:normal;text-align:center">',a+=`<section style="display:flex;justify-content:center;margin-bottom:${m[4]}">`,a+=`<span style="display:inline-flex;align-items:center;justify-content:center;width:34px;height:34px;border-radius:${R.full};background:${f?r.accent:U.surface};color:${f?U.surface:r.accent};border:1px solid ${f?r.accent:"#dbe3ee"};font-size:${E.xs};font-weight:${A.black};letter-spacing:${K.xl};white-space:nowrap">${_(p)}</span>`,a+="</section>",a+=`<p style="margin:0px;font-size:${E.base};line-height:1.55;color:${f?U.textPrimary:U.inkStrong};font-weight:${A.extrabold};letter-spacing:${K.normal};white-space:normal;word-break:break-all">${_(c)}</p>`,a+="</section>",d<o.length-1&&(a+=`<span style="display:inline-block;vertical-align:middle;width:32px;height:1px;line-height:1px;margin:0px ${m[3]};background:linear-gradient(90deg,${D.gray500}59,${D.gray500}d9);color:transparent;overflow:hidden">${_("-")}</span>`),a+="</section>"}),a+="</section></section></section>"),/^<reading-path>/.test(t)&&i+1<n.length&&/^<\/reading-path>/.test(n[i+1])?{html:a,next:i+2}:{html:a,next:i+1}}},Dl={name:"title",match:e=>/^<title\b/.test(e),render:(e,t,n,i)=>{let r=n[i],o=i;for(;!/<\/title>/.test(r)&&o+1<n.length;)o++,r+=`
`+n[o];const a=r.match(/^<title\b([^>]*)>([\s\S]*?)<\/title>/);if(!a)return null;let l="";const d=be(a[1]),c=a[2].trim();return(d.type||"DA01").toUpperCase()==="DA02"?l+=Ui.render(d,c,e.t,e.md):l+=Hi.render(d,c,e.t,e.md),{html:l,next:o+1}}},Pl={name:"pTitle",match:e=>/^<p-title\b/.test(e),render:(e,t,n,i)=>{let r=n[i],o=i;for(;!/<\/p-title>/.test(r)&&o+1<n.length;)o++,r+=`
`+n[o];const a=r.match(/^<p-title\b([^>]*)>([\s\S]*?)<\/p-title>/);if(!a)return null;const l=be(a[1]),d=a[2].trim();return{html:qi.render(l,d,e.t).replace("<section",'<section data-block="ptitle"'),next:o+1}}},Fl={name:"gallery",match:e=>/^<\s*!\[/.test(e),render:(e,t,n,i)=>po(n,i)},Bl={name:"callout",match:e=>/^>\s*\[!?(TIP|NOTE|INFO|WARNING|CAUTION|IMPORTANT)\]/i.test(e),render:(e,t,n,i)=>co(n,i,e.t)},Hl={name:"quote",match:e=>/^>/.test(e),render:(e,t,n,i)=>{const{t:r,formulaMap:o}=e,a=Be(e),l=[];let d=i;for(;d<n.length&&/^>/.test(n[d]);)l.push(n[d].replace(/^>\s?/,"")),d++;const c=a.quote.bg==="transparent"?"transparent":r.accent+"12",p=a.quote.bg==="transparent"?`3px solid ${r.accent}`:`1px solid ${r.accent}33`;let f=`<section style="margin:${m[6]} 0px;padding:${m[5]} ${m[7]};background:${c};border-left:${p};border-radius:0px ${a.quote.borderRadius} ${a.quote.borderRadius} 0px;color:${D.gray700};font-size:${a.bodyFontSize}">`;return l.forEach(g=>{f+=`<section><p style="margin:${m[1]} 0px;line-height:${X.loosest};text-align:justify;letter-spacing:${K.wider}">${te(g,r,o)}</p></section>`}),f+="</section>",{html:f,next:d}}},Ul={name:"caseFlowTag",match:e=>/^<case-flow\b/.test(e),render:(e,t,n,i)=>{const r=xe(n,i,/^<case-flow\b([^>]*)>(.*)$/,/<\/case-flow>/)||xe(n,i,/^<case-flow\b([^>]*)>/,/<\/case-flow>/);return r?{html:yt.renderLegacy(r.attrs,r.body,e.t),next:r.next,warning:r.warning}:null}},ql={name:"caseFlowInline",match:e=>/^-\s*\[(?![ xX]\])[^\]]+\]/.test(e),render:(e,t,n,i)=>{const r=[];let o=i;for(;o<n.length&&/^-\s*\[(?![ xX]\])[^\]]+\]/.test(n[o]);)r.push(n[o]),o++;return{html:yt.renderLegacy({},r.join(`
`),e.t),next:o}}},Wl={name:"timeline",match:e=>/^<timeline\b/.test(e),render:(e,t,n,i)=>{const r=xe(n,i,/^<timeline\b([^>]*)>(.*)$/,/<\/timeline>/)||xe(n,i,/^<timeline\b([^>]*)>/,/<\/timeline>/);return r?{html:en.renderLegacy(r.attrs,r.body,e.t),next:r.next,warning:r.warning}:null}},Gl={name:"slider",match:e=>/^<slider\b/.test(e),render:(e,t,n,i)=>{const r=xe(n,i,/^<slider\b([^>]*)>(.*)$/,/<\/slider>/);return r?{html:tn.renderLegacy(r.attrs,r.body,e.t),next:r.next,warning:r.warning}:null}},Kl={name:"engage",match:e=>/^:\s*engage\b/.test(e)||/^<engage\b/.test(e),render:(e,t,n,i)=>{const r=be(t);return r.type&&r.type.toUpperCase()==="DA02"?{html:Zi.render(r,"",e.t),next:i+1}:{html:Xi.render(r,"",e.t),next:i+1}}},Vl={name:"heading",match:e=>/^#{1,6}\s+/.test(e),render:(e,t,n,i)=>{const{t:r,formulaMap:o}=e,a=Be(e),l=a.headingColor==="accent"?r.accent:a.headingColor==="dark"?r.dark:U.textPrimary,d=a.headingWeight,c=t.match(/^#\s+(.+)/);if(c)return{html:`<h1 style="margin:0px 0px ${m[7]};font-size:${a.headingSizes[1]};font-weight:${d};color:${l};line-height:${X.normal}">${te(c[1],r,o)}</h1>`,next:i+1};const p=t.match(/^##\s+(.+)/);if(p)return{html:`<h2 style="margin:${m[11]} 0px ${m[5]};font-size:${a.headingSizes[2]};font-weight:${d};color:${l};line-height:${X.normal}">${te(p[1],r,o)}</h2>`,next:i+1};const f=t.match(/^###\s+(.+)/);if(f)return{html:`<h3 style="margin:${m[10]} 0px ${m[4]};font-size:${a.headingSizes[3]};font-weight:${d};color:${l};line-height:${X.normal}">${te(f[1],r,o)}</h3>`,next:i+1};const g=t.match(/^####\s+(.+)/);if(g)return{html:`<h4 style="margin:${m[9]} 0px ${m[3]};font-size:${a.headingSizes[4]};font-weight:${d};color:${U.textQuaternary};line-height:${X.normal}">${te(g[1],r,o)}</h4>`,next:i+1};const x=t.match(/^#####\s+(.+)/);if(x)return{html:`<h5 style="margin:${m[8]} 0px ${m[2]};font-size:${a.headingSizes[5]};font-weight:${d};color:${U.textQuaternary};line-height:${X.normal}">${te(x[1],r,o)}</h5>`,next:i+1};const h=t.match(/^######\s+(.+)/);return h?{html:`<h6 style="margin:${m[8]} 0px ${m[2]};font-size:${a.headingSizes[6]};font-weight:${d};color:${U.textQuaternary};line-height:${X.normal};text-transform:uppercase;letter-spacing:${K.wider}">${te(h[1],r,o)}</h6>`,next:i+1}:{html:"",next:i+1}}},Yl={name:"blockFormula",match:e=>/^\$\$/.test(e),render:(e,t,n,i)=>{const{formulaMap:r}=e,o=p=>{if(r){const f=r.get(`b:${p}`);if(f)return f}return`<code style="display:inline-block;background:${D.gray100};padding:${m[2]} ${m[5]};border-radius:${R.md};font-size:${E.md};font-family:SF Mono,Consolas,monospace;color:#e83e8c;max-width:100%;overflow-x:auto;white-space:nowrap">$$${V(p)}$$</code>`},a=t.match(/^\$\$(.+?)\$\$/);if(a){const p=a[1].trim();return{html:`<section style="text-align:center;margin:${m[10]} 0;overflow-x:auto;color:${D.gray1000}">${o(p)}</section>`,next:i+1}}let l=i+1;const d=[];for(;l<n.length&&!/^\$\$/.test(n[l]);)d.push(n[l]),l++;l<n.length&&l++;const c=d.join(`
`).trim();return{html:`<section style="text-align:center;margin:${m[10]} 0;overflow-x:auto;color:${D.gray1000}">${o(c)}</section>`,next:l}}},Xl={name:"codeBlock",match:e=>/^```/.test(e),render:(e,t,n,i)=>{const{mermaidMap:r}=e,o=t.replace(/^```/,"").trim();let a=i+1,l="";for(;a<n.length&&!/^```/.test(n[a]);)l+=n[a]+`
`,a++;a<n.length&&a++;let d="";if(o==="mermaid"){const c=l.replace(/\s+$/,""),p=r==null?void 0:r.get(`m:${c}`);p!=null&&p.svg?d+=`<section data-block="mermaid" style="max-width:100%;margin:${m[7]} auto;text-align:center;break-inside:avoid"><div class="m2v-mermaid-figure" style="width:100%;max-width:100%;max-height:var(--m2v-mermaid-max-height,none);overflow:hidden">${p.svg}</div></section>`:(p!=null&&p.error&&(d+=`<section data-block="mermaid-error" style="background:${U.errorBg};border-left:3px solid ${U.errorBorder};padding:${m[4]} ${m[6]};margin:${m[6]} 0;font-size:${E.sm};color:${U.errorText}">图表渲染失败：${V(p.error)}</section>`),d+=Ee(l,"mermaid"))}else d+=Ee(l,o);return{html:d,next:a}}},Zl={name:"table",match:(e,t,n)=>e.indexOf("|")>=0&&n+1<t.length&&/\|[\s-:]+\|/.test(t[n+1]),render:(e,t,n,i)=>{const{t:r,formulaMap:o}=e,a=i>0&&n[i-1].includes("（续表）"),l=u=>{let y=u.trim();return y.startsWith("|")&&(y=y.substring(1)),y.endsWith("|")&&(y=y.substring(0,y.length-1)),y.split("|").map(j=>j.trim())},d=l(t);let c=i+2;const p=[];for(;c<n.length&&n[c].indexOf("|")>=0&&n[c].trim()!=="";)p.push(l(n[c])),c++;const f=Math.max(d.length,...p.map(u=>u.length),2),g=Be(e).radiusMap;let x="";a&&(x+=`<section style="margin:0px 0px ${m[4]};text-align:center;font-size:${E.sm};color:${U.inkMuted};font-style:italic">（续表）</section>`),x+=`<section style="margin:${m[7]} 0px;background:#fff;border-radius:${g["2xl"]};overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,0.06),0 1px 2px rgba(0,0,0,0.04)">`,x+='<section style="overflow-x:auto"><table style="border-collapse:collapse;width:100%">',x+="<thead><tr>",d.forEach((u,y)=>{const j=y===0,w=y===d.length-1;x+=`<th style="vertical-align:top;padding:13px ${m[6]};text-align:left;font-size:13px;font-weight:${A.semibold};color:#fff;background:${r.accent};${j?`border-radius:${g["2xl"]} 0 0 0`:""}${w?`border-radius:0 ${g["2xl"]} 0 0`:""};letter-spacing:0.3px">${te(u,r,o)||"&nbsp;"}</th>`});for(let u=d.length;u<f;u++)x+=`<th style="vertical-align:top;padding:13px ${m[6]};text-align:left;font-size:13px;font-weight:${A.semibold};color:#fff;background:${r.accent};border-radius:0 ${g["2xl"]} 0 0;letter-spacing:0.3px">&nbsp;</th>`;x+="</tr></thead>";let h="";return c<n.length&&n[c].trim()&&!n[c].includes("|")&&(h=n[c].trim(),c++),x+="<tbody>",p.forEach((u,y)=>{const j=y===p.length-1;x+="<tr>",u.forEach((w,N)=>{const b=N===0,$=N===u.length-1,S=j&&$,C=j&&b,O=j&&!h?"border-bottom:none":`border-bottom:1px solid ${D.gray100}`,F=C&&!h?`border-radius:0 0 0 ${g["2xl"]}`:S&&!h?`border-radius:0 0 ${g["2xl"]} 0`:"",Q=y%2===1?`background:${D.gray50}`:"background:#fff";x+=`<td style="vertical-align:top;padding:11px ${m[6]};text-align:left;font-size:13px;color:#475569;${O};${F};${Q}">${te(w,r,o)||"&nbsp;"}</td>`});for(let w=u.length;w<f;w++)x+=`<td style="vertical-align:top;padding:11px ${m[6]};text-align:left;font-size:13px;color:#475569;${j&&!h?"border-bottom:none":`border-bottom:1px solid ${D.gray100}`}">&nbsp;</td>`;x+="</tr>"}),x+="</tbody>",h&&(x+=`<tfoot><tr><td colspan="${f}" style="padding:9px ${m[6]};text-align:center;font-size:11px;color:#94a3b8;background:linear-gradient(180deg,${D.gray50} 0%,#f1f5f9 100%);border-top:1px solid ${D.gray200};border-radius:0 0 ${g["2xl"]} ${g["2xl"]}">${V(h)}</td></tr></tfoot>`),x+="</table></section></section>",{html:x,next:c}}},Jl={name:"unorderedList",match:e=>/^\s*[-*+]\s/.test(e),render:(e,t,n,i)=>{const{t:r,formulaMap:o}=e;let a=i,l=`<section style="margin:${m[4]} 0px;padding-left:${m[9]}">`;for(;a<n.length&&/^\s*[-*+]\s/.test(n[a]);){const d=n[a].replace(/^\s*[-*+]\s/,""),c=d.match(/^\[([ x])\]\s*(.*)/);if(c){const p=c[1]==="x",f=r.border===U.borderDefault?U.inkFaint:r.border,g=p?'<svg width="18" height="18" viewBox="0 0 18 18" fill="none"><path d="M5 9l3 3 5-5" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>':`<svg width="18" height="18" viewBox="0 0 18 18" fill="none"><rect x="1" y="1" width="16" height="16" rx="3" stroke="${f}" stroke-width="1.5" fill="none"/></svg>`;l+=`<section style="margin:${m[1]} 0px"><span style="display:inline-flex;align-items:center;gap:${m[3]}"><span style="width:18px;height:18px;display:inline-flex;align-items:center;justify-content:center;flex-shrink:0;${p?`background:${r.accent};border-radius:${R.sm}`:""}">${g}</span><span>${te(c[2],r,o)}</span></span></section>`}else l+=`<section style="margin:${m[1]} 0px;line-height:${X.loosest};letter-spacing:${K.wider};display:flex;align-items:flex-start"><span style="display:inline-block;width:6px;height:6px;border-radius:50%;background-color:${r.accent};margin-right:${m[5]};margin-top:${m[5]};flex-shrink:0"></span><span style="flex:1">${te(d,r,o)}</span></section>`;a++}return l+="</section>",{html:l,next:a}}},Ql={name:"orderedList",match:e=>/^\s*\d+\.\s/.test(e),render:(e,t,n,i)=>{const{t:r,formulaMap:o}=e;let a=i,l=`<section style="margin:${m[4]} 0px;padding-left:${m[9]}">`;for(;a<n.length&&/^\s*\d+\.\s/.test(n[a]);){const d=n[a].match(/^\s*(\d+)\.\s/),c=d?d[1]:"1",p=n[a].replace(/^\s*\d+\.\s/,"");l+=`<section style="margin:${m[1]} 0px;line-height:${X.loosest};letter-spacing:${K.wider};display:flex;align-items:flex-start"><span style="color:${r.accent};font-weight:${A.extrabold};margin-right:${m[3]};flex-shrink:0;min-width:16px">${c}.</span><span style="flex:1">${te(p,r,o)}</span></section>`,a++}return l+="</section>",{html:l,next:a}}},ec={name:"image",match:e=>/^!\[([^\]]*)\]\(([^)]+)\)(?:\[([^\]]+)\])?/.test(e),render:(e,t,n,i)=>{const r=t.match(/^!\[([^\]]*)\]\(([^)]+)\)(?:\[([^\]]+)\])?/);if(!r)return{html:"",next:i+1};const[,o,a,l]=r;let d=a;if(a.startsWith("img://")){const f=a.replace("img://","");d=Ri(f)||a}const c=dt(d,"src");let p="";if(l){const f=l.split(/\s+/);p+=`<section style="max-height:${f[1]||"250px"};overflow-y:auto;border-radius:${R.lg};margin:${m[5]} 0px;display:flex;justify-content:center"><img src="${V(c)}" alt="${V(o)}" style="width:${f[0]||"100%"};display:block;margin:0 auto"></section>`}else p+=`<section style="margin:${m[5]} 0px;display:flex;justify-content:center"><img src="${V(c)}" alt="${V(o)}" style="max-width:100%;border-radius:${R.md};display:block;margin:0 auto"></section>`;return{html:p,next:i+1}}},tc={name:"imgTag",match:e=>/^<img\s/.test(e.trim()),render:(e,t,n,i)=>{const r=be(t);return{html:Qt.render(r,"",e.t),next:i+1}}},nc={name:"govHeader",match:e=>/^<gov-header\b/.test(e),render:(e,t,n,i)=>{const r=xe(n,i,/^<gov-header\b([^>]*)>(.*)$/,/<\/gov-header>/);return r?{html:nn.renderLegacy(r.attrs,r.body,e.t),next:r.next,warning:r.warning}:null}},ic={name:"paragraph",priority:1e3,match:()=>!0,render:(e,t,n,i)=>{const{t:r,formulaMap:o}=e,a=Be(e),l=t.trim(),d=l.replace(/^(\*\*|\*|__|_)*/,"").replace(/(\*\*|\*|__|_)*$/,"").trim(),c=d.match(/^\s*(图|表|Fig|Table|Figure)\.?\s*(\d+|[一二三四五六七八九十百]+)([:：.\s—-]+)/i);let p=!1,f=!1;if(c){f=/^\s*(表|Table)/i.test(d);const x=c[3],h=/^\s+$/.test(x),u=/展示|显示|展现|是|有|如下图|如下表/g.test(d);!(h&&(d.length>60||u))&&(f?p=vl(n,i):p=yl(n,i))}let g="";if(p){const x=f?"document-caption document-caption-table":"document-caption document-caption-image",h=f?"table":"image",u=f?`margin:${m[7]} 0px ${m[4]}`:`margin:${m[4]} 0px ${m[7]}`;g+=`<section data-caption-kind="${h}" style="${u};display:flex;justify-content:center;width:100%"><p class="${x}" style="margin:0px;font-size:${E.base};color:${U.inkMuted};line-height:${X.relaxed};text-align:center;white-space:nowrap">${te(l,r,o)}</p></section>`}else g+=`<section style="margin:0px 0px ${m[10]}"><p style="margin:0px;font-size:${a.bodyFontSize};color:${U.textTertiary};line-height:${a.bodyLineHeight};letter-spacing:${K.wider};text-align:justify;overflow-wrap:break-word">${te(t,r,o)}</p></section>`;return{html:g,next:i+1}}};function rc(){return[wl,kl,Sl,jl,Nl,El,_l,zl,Al,Ml,Il,Ll,Rl,Ol,Dl,Pl,Fl,Bl,Hl,Ul,ql,Wl,Gl,Kl,Vl,Yl,Xl,Zl,Jl,Ql,ec,tc,nc,ic,...tl,...ul].sort((e,t)=>(e.priority??100)-(t.priority??100))}function df(e){const t=new Set,n=[],i=/```mermaid[ \t]*\r?\n([\s\S]*?)```/g;let r;for(;(r=i.exec(e))!==null;){const o=r[1].replace(/\s+$/,""),a=`m:${o}`;t.has(a)||(t.add(a),n.push({key:a,source:o}))}return n}async function pf(e,t){const n=new Map,i=await Promise.all(e.map(async r=>{const o=await Es(r.source,t);return{key:r.key,...o}}));for(const{key:r,svg:o,error:a}of i)n.set(r,{svg:o,error:a});return n}function nr(e,t,n,i,r,o){const a=o??Ge(),l=a,{text:d,store:c}=Ss(e),p=!n,{text:f,store:g}=p?qr(d):{text:d,store:null},x=[],h=new Map,u=/\[([^\]]+)\]\(([^)\s]+)\s+"([^"]+)"\)/g;let y=f.replace(u,(I,L,B,G)=>{const se=`${B}|${G}`,M=h.get(se);let q;return M!==void 0?q=M+1:(q=x.length+1,h.set(se,x.length),x.push({label:L,url:B,desc:G})),`__FN_${q-1}__|${L}|`});const j=[],w=new Set,N=/^\[\^(\w+)\]:\s+(.+)$/gm;let b;for(;(b=N.exec(y))!==null;){const I=b[1];w.has(I)||(w.add(I),j.push({label:I,definition:b[2].trim()}))}j.length>0&&(y=y.replace(/^\[\^(\w+)\]:\s+.+$/gm,"")),y=y.replace(/\[\^(\w+)\](?!:)/g,(I,L)=>{const B=j.findIndex(G=>G.label===L);return B>=0?`<sup><a href="#fn-${L}" id="fnref-${L}" style="color:${t.accent};text-decoration:none;font-weight:600">[${B+1}]</a></sup>`:I});let $=j.length;y=y.replace(/\^\[([^\]]+)\]/g,(I,L)=>{$++;const B=`inline-${$}`;return j.push({label:B,definition:L.trim()}),`<sup><a href="#fn-${B}" id="fnref-${B}" style="color:${t.accent};text-decoration:none;font-weight:600">[${$}]</a></sup>`});const S=y.split(`
`),C=[];let O=0;if(S[0]&&S[0].trim()==="---"&&S.findIndex((L,B)=>B>0&&L.trim()==="---")>0){O=1;const L={};for(;O<S.length&&S[O].trim()!=="---";){const G=S[O].match(/^(\w+):\s*(Meta.+|.+)/)||S[O].match(/^(\w+):\s*(.+)/);G&&(L[G[1]]=G[2].trim()),O++}O++,C.push(so(L,e,t))}const F=[];for(let I=0;I<S.length;I++){const L=S[I].match(/^<p-title\b([^>]*)>([\s\S]*?)<\/p-title>/);if(L){const B=be(L[1]);if(parseInt(B.level||"1",10)===1){const se=B.num||"",M=B.title||L[2].trim(),q=B.subtitle||"";F.push({num:se,title:M,subtitle:q})}}}const Q=rc(),W={t,tokens:a,tokensRaw:l,md:e,formulaMap:n,mermaidMap:i,pTitleLevel1List:F,parseMarkdownFn:(I,L,B,G)=>nr(I,L,B,G,r,a)};for(;O<S.length;){const I=S[O];let L=!1;for(const B of Q)if(B.match(I,S,O)){const G=B.render(W,I,S,O);if(G){C.push(G.html),O=G.next,L=!0,G.warning&&r&&r(G.warning);break}}L||O++}let Z=C.join("");if(x.length>0||j.length>0){const I=[];I.push(`<section style="margin:${m[13]} 0px 0px;padding-top:${m[8]};border-top:1px solid ${t.border}">`),I.push(`<h2 style="margin:0px 0px ${m[7]};font-size:${E["3xl"]};font-weight:${A.bold};color:${U.textPrimary};line-height:${X.normal}">参考资料</h2>`),I.push(`<section style="font-size:${E.md};color:${U.inkMuted};line-height:${X.loosest}">`),x.forEach((L,B)=>{const G=dt(L.url,"href");I.push(`<p style="margin:${m[2]} 0px"><span style="color:${t.accent};font-weight:${A.semibold}">[${B+1}]</span> ${_(L.desc)}：<a href="${V(G)}" style="color:${t.accent};word-break:break-all">${V(G)}</a></p>`)}),j.forEach((L,B)=>{const G=x.length+B+1;I.push(`<p id="fn-${L.label}" style="margin:${m[2]} 0px"><span style="color:${t.accent};font-weight:${A.semibold}">[${G}]</span> ${_(L.definition)} <a href="#fnref-${L.label}" style="color:${t.accent};text-decoration:none;font-size:${E.sm}">↩</a></p>`)}),I.push("</section></section>"),Z+=I.join("")}return g&&(Z=Wr(Z,g)),Z=js(Z,c),Z}function sc(e){const t=e.split(`
`).filter(x=>x.trim());let n=-1,i=-1;for(let x=0;x<t.length;x++){const h=t[x].trim();if(h.includes("|")){if(n===-1)n=x;else if(/^[|\s:-]+$/.test(h)){i=x;break}}}if(n===-1||i===-1)return null;const r=x=>{let h=x.trim();return h.startsWith("|")&&(h=h.substring(1)),h.endsWith("|")&&(h=h.substring(0,h.length-1)),h.split("|").map(u=>u.trim())},o=r(t[n]),a=[];for(let x=i+1;x<t.length;x++){const h=t[x].trim();h.includes("|")&&a.push(r(h))}const l=t.slice(0,n).join(`
`).trim(),d=Array(o.length).fill(2),c=x=>x.trim().replace(/[^\x00-\xff]/g,"aa").length/2;o.forEach((x,h)=>{d[h]=Math.max(d[h],c(x))}),a.forEach(x=>{x.forEach((h,u)=>{u<o.length&&(d[u]=Math.max(d[u],c(h)))})});const p=d.reduce((x,h)=>x+h,0),f=46,g=d.map(x=>Math.max(3,Math.floor(x/p*f)));return{headers:o,rows:a,caption:l||void 0,rawMarkdown:e,colChars:g}}function On(e,t=!1,n){const i=t?46:42;let r=1;return e.forEach((o,a)=>{const l=n&&n[a]?n[a]:18,d=o.trim().replace(/[^\x00-\xff]/g,"aa").length/2,c=Math.max(1,l*.85);r=Math.max(r,Math.ceil(d/c))}),i+(r-1)*24}function oc(e){let t=On(e.headers,!0,e.colChars);for(const n of e.rows)t+=On(n,!1,e.colChars);return t+=60,t}function Bt(e){const t=e.trim();return/^<page-break\s*\/?>/i.test(t)?"pagebreak":/^#{1,6}\s/.test(t)||/^<title\b/.test(t)||/^<p-title\b/.test(t)?"heading":/^```mermaid\b/.test(t)?"mermaid":/^```/.test(t)?"code":/^!\[/.test(t)?"image":/^>/.test(t)?"quote":/^([-*+]\s|\d+\.\s)/.test(t)?"list":/^---+$/.test(t)?"rule":t.includes("|")&&/\n\|?[\s:-]+\|/.test(t)?"table":/^<\w[\s\S]*<\/\w/.test(t)?"component":"paragraph"}function ac(e){const t=e.replace(/\r\n/g,`
`).replace(/\s+$/g,"");if(!t.trim())return[];const n=[],i=[];let r=!1,o=null;const a=()=>{const d=i.join(`
`).trimEnd();d.trim()&&n.push(d),i.length=0};for(const d of t.split(`
`)){const c=d.trim();if(c.startsWith("```")){r=!r,i.push(d);continue}if(!r&&!o){const p=c.match(/^<([a-z][\w-]*)\b[^>]*>/i);p&&!c.includes(`</${p[1]}>`)&&!c.endsWith("/>")&&(o=p[1])}if(!r&&/^<page-break\s*\/?>/i.test(c)){a(),n.push(c);continue}if(!r&&!o&&!c){a();continue}i.push(d),o&&c.includes(`</${o}>`)&&(o=null)}a();const l=[];for(const d of n)if(/^([-*+]\s|\d+\.\s)/.test(d)&&!/^---+$/.test(d)){const c=d.split(`
`);let p="";for(const f of c)/^\s*([-*+]\s|\d+\.\s)/.test(f)?(p&&l.push(p.trim()),p=f):p+=`
`+f;p&&l.push(p.trim())}else l.push(d);return l}function ir(e){return e.trim().replace(/^(\*\*|__|\*|_)+/,"").replace(/(\*\*|__|\*|_)+$/,"").trim()}function lc(e){const t=ir(e);return/^(表|Table)\.?\s*(\d+|[一二三四五六七八九十百]+)([:：.\-—\s]+)/i.test(t)}function cc(e){const t=ir(e);return/^(图|Fig|Figure)\.?\s*(\d+|[一二三四五六七八九十百]+)([:：.\-—\s]+)/i.test(t)}function dc(e){const t=[];for(let n=0;n<e.length;n++){const i=e[n],r=e[n+1];if(r&&lc(i)&&Bt(r)==="table"){t.push(`${i}

${r}`),n++;continue}if(r&&Bt(i)==="image"&&cc(r)){t.push(`${i}

${r}`),n++;continue}t.push(i)}return t}const Ht={pageWidth:794,pageHeight:1123,marginTop:64,marginRight:72,marginBottom:64,marginLeft:72,headerHeight:36,footerHeight:34,headerLeft:"MarkFlow",headerRight:"Pintley Tasia",footerText:"第 {page} / {total} 页",theme:"business",fontFamily:"songti",fontScale:"normal",centerTitle:!1,indentParagraph:!1},pc=/[\\/:*?"<>|]/g;function fc(e){return e.replace(/---[\s\S]*?---\s*/,"").replace(/```[\s\S]*?```/g,"").replace(/<[^>]+>/g,"").replace(/!\[[^\]]*]\([^)]+\)/g,"").replace(/\[([^\]]+)]\([^)]+\)/g,"$1").replace(/[#*`>[\]!|_~=:：.,，。;；\-\s]/g,"").trim()}function gc(e){return e.replace(pc,"_").replace(/\s+/g," ").trim()}function ff(e,t,n=".pdf"){const i=fc(t).slice(0,15)||"未命名文档";return`${gc((e||i).slice(0,60))||"未命名文档"}${n}`}function st(e,t,n,i){const r=e.split(`
`).map(o=>Math.max(1,Math.ceil(o.trim().length/n))).reduce((o,a)=>o+a,0);return t+r*i}function xc(e,t){switch(t){case"heading":return st(e,20,22,28);case"image":return 280;case"table":{const n=sc(e);if(n)return oc(n);const i=e.split(`
`).filter(r=>r.includes("|")).length;return 48+Math.max(1,i)*36}case"mermaid":return 280;case"code":return 42+e.split(`
`).length*22;case"quote":return st(e,32,34,26);case"list":return 18+e.split(`
`).length*30;case"rule":return 32;case"pagebreak":return 0;case"component":return st(e,56,30,28);default:return st(e,10,36,28)}}function gf(e){return dc(ac(e)).map((t,n)=>{const i=Bt(t);return{id:`block-${n+1}`,kind:i,markdown:t,estimatedHeight:xc(t,i),avoidBreak:i!=="paragraph"}})}function rr(e){const{dbName:t,storeName:n,version:i=1,throttleMs:r=1e3}=e;let o=null;const a=new Map,l=new Map;function d(){return o||(typeof indexedDB>"u"?Promise.reject(new Error("IndexedDB is not supported in this environment")):(o=new Promise((g,x)=>{const h=indexedDB.open(t,i);h.onupgradeneeded=()=>{const u=h.result;u.objectStoreNames.contains(n)||u.createObjectStore(n)},h.onsuccess=()=>g(h.result),h.onerror=()=>x(h.error),h.onblocked=()=>x(new Error(`IndexedDB ${t} open blocked`))}),o))}async function c(g,x){const h=await d();return new Promise((u,y)=>{const N=h.transaction(n,"readwrite").objectStore(n).put(x,g);N.onsuccess=()=>u(),N.onerror=()=>y(N.error)})}async function p(g){const x=l.get(g);if(!x||x.flushing)return;x.flushing=!0,a.delete(g);try{await c(x.key,x.value)}catch(u){console.warn(`[idbStorage] 写入失败 (${g}):`,u)}const h=l.get(g);h===x&&l.delete(g),x.resolve()}function f(g){const x=a.get(g);x&&clearTimeout(x);const h=setTimeout(()=>{p(g)},r);a.set(g,h)}return typeof window<"u"&&window.addEventListener("beforeunload",()=>{for(const g of l.keys()){const x=l.get(g);if(x&&!x.flushing){const h=a.get(g);h&&clearTimeout(h),p(g)}}}),{getItem:async g=>{try{const x=await d();return await new Promise((h,u)=>{const w=x.transaction(n,"readonly").objectStore(n).get(g);w.onsuccess=()=>{const N=w.result;h(typeof N=="string"?N:null)},w.onerror=()=>u(w.error)})}catch(x){return console.warn(`[idbStorage] 读取失败 (${g}):`,x),null}},setItem:async(g,x)=>new Promise(h=>{const u=l.get(g);u&&(u.flushing||u.resolve()),l.set(g,{key:g,value:x,resolve:h}),f(g)}),removeItem:async g=>{const x=a.get(g);x&&(clearTimeout(x),a.delete(g));const h=l.get(g);h&&(h.resolve(),l.delete(g));try{const u=await d();await new Promise((y,j)=>{const b=u.transaction(n,"readwrite").objectStore(n).delete(g);b.onsuccess=()=>y(),b.onerror=()=>j(b.error)})}catch(u){console.warn(`[idbStorage] 删除失败 (${g}):`,u)}}}}function uc(){return typeof crypto<"u"&&crypto.randomUUID?crypto.randomUUID():`${Date.now().toString(36)}-${Math.random().toString(36).slice(2,11)}`}const mc=50,Dn=5e3,hc={apiUrl:"",apiKey:"",model:""},bc={activeType:"local"},Pn=ht[3].accent,Fn=ht[3].dark,yc="m2v-mode",vc="m2v-theme",wc="m2v-document-settings",kc="m2v-article-font",$c="m2v-card-font";function At(e,t){if(typeof document>"u")return;const n=document.documentElement;n.style.setProperty("--accent",e),n.style.setProperty("--accent-dark",t)}function Sc(){if(typeof localStorage>"u")return{};const e={},t=localStorage.getItem(yc);t&&["article","document","card","html"].includes(t)&&(e.mode=t,e.inputType=t==="html"?"html":"markdown",e.platform=t==="card"?"xiaohongshu":"longform");const n=localStorage.getItem(kc);n&&["songti","fangsong","heiti"].includes(n)&&(e.articleFont=n);const i=localStorage.getItem($c);i&&["songti","fangsong","heiti"].includes(i)&&(e.cardFont=i);const r=localStorage.getItem(wc);if(r)try{e.documentSettings={...Ht,...JSON.parse(r)}}catch{}const o=localStorage.getItem(vc);if(o)try{const a=JSON.parse(o);a.accent&&a.dark&&(e.accent=a.accent,e.accentDark=a.dark,e.colors=qe(a.accent,a.dark))}catch{}return e}const sr=ai()(li((e,t)=>({mode:"document",inputType:"markdown",platform:"longform",documentSettings:Ht,articleFont:"songti",cardFont:"heiti",cardAspect:"3:4",accent:Pn,accentDark:Fn,colors:qe(Pn,Fn),themeProfileId:"default",themeTokens:Ge(Et(et())),themeProfiles:ci,imageHostConfig:bc,aiConfig:hc,setAiConfig:n=>e(i=>({aiConfig:{...i.aiConfig,...n}})),wechatDraftConfig:{},setWeChatDraftConfig:n=>e(i=>({wechatDraftConfig:{...i.wechatDraftConfig,...n}})),setImageHostConfig:n=>e(i=>({imageHostConfig:{...i.imageHostConfig,...n}})),allowIntranetResources:!1,setAllowIntranetResources:n=>e({allowIntranetResources:n}),customInstructions:[],addCustomInstruction:n=>t().customInstructions.length>=mc?!1:(e(r=>{const o=Date.now();return{customInstructions:[...r.customInstructions,{...n,content:n.content.slice(0,Dn),id:uc(),createdAt:o,updatedAt:o}]}}),!0),updateCustomInstruction:(n,i)=>e(r=>({customInstructions:r.customInstructions.map(o=>o.id===n?{...o,...i,content:(i.content??o.content).slice(0,Dn),updatedAt:Date.now()}:o)})),removeCustomInstruction:n=>e(i=>({customInstructions:i.customInstructions.filter(r=>r.id!==n)})),guideTrigger:{},triggerGuide:n=>e(i=>({guideTrigger:{...i.guideTrigger,[n]:(i.guideTrigger[n]||0)+1}})),hasHydrated:!1,_markHydrated:()=>e({hasHydrated:!0}),setMode:n=>e({mode:n,inputType:n==="html"?"html":"markdown",platform:n==="card"?"xiaohongshu":"longform"}),setInputType:n=>e({inputType:n}),setPlatform:n=>e({platform:n}),updateDocumentSettings:n=>e(i=>({documentSettings:{...i.documentSettings,...n}})),setArticleFont:n=>e({articleFont:n}),setCardFont:n=>e({cardFont:n}),setCardAspect:n=>e({cardAspect:n}),restoreDocumentSettingsDemo:()=>e(n=>{const i=n.documentSettings,r=Ht;return{documentSettings:{...r,headerLeft:i.headerLeft||r.headerLeft,headerRight:i.headerRight||r.headerRight}}}),setTheme:(n,i)=>{At(n,i),e({accent:n,accentDark:i,colors:qe(n,i)})},setThemeProfile:n=>{const i=Dt(n)??et();At(i.accent,i.dark),e({themeProfileId:i.id,accent:i.accent,accentDark:i.dark,colors:qe(i.accent,i.dark),themeTokens:Ge(Et(i))})}}),{name:"m2v-app-store",storage:Gt(()=>rr({dbName:"markflow-app-settings",storeName:"settings",throttleMs:500})),partialize:e=>({mode:e.mode,inputType:e.inputType,platform:e.platform,documentSettings:e.documentSettings,articleFont:e.articleFont,cardFont:e.cardFont,cardAspect:e.cardAspect,accent:e.accent,accentDark:e.accentDark,themeProfileId:e.themeProfileId,imageHostConfig:e.imageHostConfig,aiConfig:e.aiConfig,allowIntranetResources:e.allowIntranetResources,customInstructions:e.customInstructions,wechatDraftConfig:e.wechatDraftConfig}),onRehydrateStorage:()=>e=>{var r,o,a;if(!e)return;const t=Sc();Object.keys(t).length>0&&Object.assign(e,{mode:t.mode??e.mode,inputType:t.inputType??e.inputType,platform:t.platform??e.platform,documentSettings:t.documentSettings??e.documentSettings,articleFont:t.articleFont??e.articleFont,cardFont:t.cardFont??e.cardFont});const n=e.themeProfileId,i=n?Dt(n)??et():et();e.themeProfileId=i.id,e.themeTokens=Ge(Et(i)),e.colors=qe(e.accent,e.accentDark),At(e.accent,e.accentDark),e.customInstructions??(e.customInstructions=[]),e.aiConfig={apiUrl:((r=e.aiConfig)==null?void 0:r.apiUrl)??"",apiKey:((o=e.aiConfig)==null?void 0:o.apiKey)??"",model:((a=e.aiConfig)==null?void 0:a.model)??""},e._markHydrated()}})),Bn=4,Ae=`# MarkFlow

正在加载示例内容，或直接在左侧输入 Markdown。`,Hn='<main style="padding:32px;font-family:sans-serif">正在加载示例 HTML，或直接粘贴 AI 生成的 HTML。</main>';function jc(){if(typeof localStorage>"u")return{};if(localStorage.getItem("m2v-content-store"))return{};const e={},t=localStorage.getItem("m2v-article-markdown");t&&(e.articleMarkdown=t,e.articleDirty=!0);const n=localStorage.getItem("m2v-document-markdown")||localStorage.getItem("m2v-markdown");n&&(e.documentMarkdown=n,e.documentDirty=!0);const i=localStorage.getItem("m2v-card-markdown");i&&(e.cardMarkdown=i,e.cardDirty=!0);const r=localStorage.getItem("m2v-html");return r&&(e.html=r,e.htmlDirty=!0),e}const Nc=rr({dbName:"m2v-content-db",storeName:"persist",throttleMs:1e3}),me=ai()(li((e,t)=>({articleMarkdown:Ae,documentMarkdown:Ae,cardMarkdown:Ae,html:Hn,demoVersion:0,articleDirty:!1,documentDirty:!1,cardDirty:!1,htmlDirty:!1,setArticleMarkdown:n=>e({articleMarkdown:n,articleDirty:!0}),setDocumentMarkdown:n=>e({documentMarkdown:n,documentDirty:!0}),setCardMarkdown:n=>e({cardMarkdown:n,cardDirty:!0}),setHtml:n=>e({html:n,htmlDirty:!0}),syncDemoContent:n=>e(i=>i.demoVersion===Bn?{}:{articleMarkdown:i.articleDirty?i.articleMarkdown:n.article,documentMarkdown:i.documentDirty?i.documentMarkdown:n.document,cardMarkdown:i.cardDirty?i.cardMarkdown:n.card,html:i.htmlDirty?i.html:n.html,demoVersion:Bn}),restoreDemo:(n,i)=>e(r=>{const o={};return n==="article"?(o.articleMarkdown=i.article,o.articleDirty=!1):n==="document"?(o.documentMarkdown=i.document,o.documentDirty=!1):n==="card"?(o.cardMarkdown=i.card,o.cardDirty=!1):n==="html"&&(o.html=i.html,o.htmlDirty=!1),{...r,...o}}),hasHydrated:!1,_markHydrated:()=>e({hasHydrated:!0})}),{name:"m2v-content-store",storage:Gt(()=>Nc),partialize:e=>({articleMarkdown:e.articleMarkdown,documentMarkdown:e.documentMarkdown,cardMarkdown:e.cardMarkdown,html:e.html,demoVersion:e.demoVersion,articleDirty:e.articleDirty,documentDirty:e.documentDirty,cardDirty:e.cardDirty,htmlDirty:e.htmlDirty}),onRehydrateStorage:()=>e=>{if(!e)return;const t=jc();t.articleMarkdown!=null&&e.articleMarkdown===Ae&&(e.articleMarkdown=t.articleMarkdown,e.articleDirty=!0),t.documentMarkdown!=null&&e.documentMarkdown===Ae&&(e.documentMarkdown=t.documentMarkdown,e.documentDirty=!0),t.cardMarkdown!=null&&e.cardMarkdown===Ae&&(e.cardMarkdown=t.cardMarkdown,e.cardDirty=!0),t.html!=null&&e.html===Hn&&(e.html=t.html,e.htmlDirty=!0),e._markHydrated()}})),J=sr;function or({toast:e}){const[t,n]=v.useState(!1);return v.useEffect(()=>{if(!e)return;n(!0);const i=window.setTimeout(()=>n(!1),2200);return()=>window.clearTimeout(i)},[e]),e?s.jsx("div",{className:`pointer-events-none fixed bottom-8 left-1/2 z-50 -translate-x-1/2 transition-all duration-300 ${t?"translate-y-0 opacity-100":"translate-y-3 opacity-0"}`,children:s.jsx("div",{className:"rounded-lg bg-slate-800/90 px-4 py-2.5 text-sm font-medium text-white shadow-lg",children:e.message})}):null}class Mt extends Re.Component{constructor(){super(...arguments);bn(this,"state",{hasError:!1,error:null})}static getDerivedStateFromError(n){return{hasError:!0,error:n}}componentDidCatch(n,i){console.error("[ErrorBoundary]",n,i)}render(){return this.state.hasError?this.props.fallback:this.props.children}}const Ec=[{key:"document",label:"A4 文档"},{key:"article",label:"长图文"},{key:"card",label:"分页图文"},{key:"html",label:"自由画布"}];function _c({mode:e,onChange:t}){return s.jsx("div",{className:"flex items-center gap-1 rounded-lg bg-slate-100 p-1 border border-slate-200/60",children:Ec.map(n=>{const i=e===n.key;return s.jsx("button",{onClick:()=>t(n.key),title:n.label,className:`relative rounded-md px-3.5 py-1.5 text-[13px] font-semibold transition-all duration-200 ${i?"bg-white text-[var(--accent)] shadow-sm":"text-slate-500 hover:text-slate-800 hover:bg-slate-200/50"}`,children:n.label},n.key)})})}function Cc({mode:e,setMode:t,accent:n,setTheme:i,setThemeProfile:r,onOpenMobileMenu:o,onWidthChange:a}){const l=v.useRef(null),[d,c]=v.useState(()=>typeof window<"u"?window.innerWidth:1200),[p,f]=v.useState(!1),[g,x]=v.useState("styles"),[h,u]=v.useState(""),y=J(b=>b.themeProfileId),j=J(b=>b.themeProfiles),w=Dt(y),N=v.useMemo(()=>{const b=h.trim().toLowerCase();return Dr.map($=>({...$,profiles:j.filter(S=>S.category===$.id&&(b===""||S.name.toLowerCase().includes(b)||$.name.toLowerCase().includes(b)))})).filter($=>$.profiles.length>0)},[h,j]);return v.useEffect(()=>{const b=()=>{const $=window.innerWidth;c($),a($)};return b(),window.addEventListener("resize",b),()=>window.removeEventListener("resize",b)},[a]),v.useEffect(()=>{if(!p)return;const b=$=>{const S=$.target;!S.closest("[data-theme-toggle]")&&!S.closest("[data-theme-panel]")&&(f(!1),u(""))};return document.addEventListener("mousedown",b),()=>document.removeEventListener("mousedown",b)},[p]),s.jsxs("header",{ref:l,className:"app-header relative z-20 flex h-14 shrink-0 items-center justify-between border-b border-slate-200 bg-white px-5 shadow-sm",children:[s.jsxs("div",{className:"flex items-center gap-6",children:[s.jsxs("a",{href:"https://www.bx9y.com.cn/",className:"flex items-center gap-1 text-[12px] font-medium text-slate-400 hover:text-slate-600 transition-colors no-underline shrink-0",title:"回到知识分享萌首页",target:"_blank",rel:"noopener noreferrer",children:[s.jsxs("svg",{width:"14",height:"14",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2",strokeLinecap:"round",strokeLinejoin:"round",children:[s.jsx("path",{d:"M19 12H5"}),s.jsx("path",{d:"M12 19l-7-7 7-7"})]}),d>=960&&s.jsx("span",{children:"知识分享萌"})]}),s.jsxs("div",{className:"flex items-center gap-2",children:[s.jsx("div",{className:"app-logo-bg flex h-7 w-7 items-center justify-center rounded-md text-white shadow-sm",children:s.jsxs("svg",{xmlns:"http://www.w3.org/2000/svg",width:"16",height:"16",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2.5",strokeLinecap:"round",strokeLinejoin:"round",children:[s.jsx("path",{d:"M12 19l7-7 3 3-7 7-3-3z"}),s.jsx("path",{d:"M18 13l-1.5-7.5L2 2l3.5 14.5L13 18l5-5z"}),s.jsx("path",{d:"M2 2l7.586 7.586"}),s.jsx("circle",{cx:"11",cy:"11",r:"2"})]})}),d>=1300?s.jsxs("h1",{className:"text-[17px] font-bold tracking-tight text-slate-800",children:["Mark",s.jsx("span",{className:"app-title-accent",children:"Flow"})]}):s.jsx("h1",{className:"text-[17px] font-bold tracking-tight text-slate-800",children:"MF"})]}),d>=960&&s.jsx(_c,{mode:e,onChange:t})]}),s.jsxs("div",{className:"flex items-center gap-3",children:[d>=960&&s.jsxs("div",{className:"relative",children:[s.jsxs("button",{"data-theme-toggle":!0,onClick:()=>f(!p),className:"flex items-center gap-1.5 rounded-md px-2 py-1.5 text-[12px] font-medium text-slate-500 hover:bg-slate-100 hover:text-slate-800 transition-colors cursor-pointer",title:"主题与配色",children:[s.jsxs("svg",{width:"16",height:"16",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2",strokeLinecap:"round",strokeLinejoin:"round",children:[s.jsx("circle",{cx:"13.5",cy:"6.5",r:"0.5",fill:"currentColor"}),s.jsx("circle",{cx:"17.5",cy:"10.5",r:"0.5",fill:"currentColor"}),s.jsx("circle",{cx:"8.5",cy:"7.5",r:"0.5",fill:"currentColor"}),s.jsx("circle",{cx:"6.5",cy:"12.5",r:"0.5",fill:"currentColor"}),s.jsx("path",{d:"M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10c.926 0 1.648-.746 1.648-1.688 0-.437-.18-.835-.437-1.125-.29-.289-.438-.652-.438-1.125a1.64 1.64 0 0 1 1.668-1.668h1.996c3.051 0 5.555-2.503 5.555-5.554C21.965 6.012 17.461 2 12 2z"})]}),d>=1300&&s.jsx("span",{children:(w==null?void 0:w.name)??"主题"})]}),p&&s.jsxs("div",{"data-theme-panel":!0,className:"absolute right-0 top-full mt-2 w-[300px] max-h-[480px] rounded-xl border border-slate-200 bg-white shadow-xl z-50 flex flex-col overflow-hidden",children:[s.jsxs("div",{className:"flex border-b border-slate-100 shrink-0",children:[s.jsx("button",{onClick:()=>x("styles"),className:`flex-1 py-2 text-[12px] font-medium transition-colors cursor-pointer ${g==="styles"?"text-slate-800 border-b-2 border-[var(--accent)]":"text-slate-400 hover:text-slate-600"}`,children:"主题风格"}),s.jsx("button",{onClick:()=>x("colors"),className:`flex-1 py-2 text-[12px] font-medium transition-colors cursor-pointer ${g==="colors"?"text-slate-800 border-b-2 border-[var(--accent)]":"text-slate-400 hover:text-slate-600"}`,children:"配色"})]}),g==="styles"&&s.jsxs("div",{className:"flex flex-col flex-1 overflow-hidden",children:[s.jsx("div",{className:"p-2 border-b border-slate-100 shrink-0",children:s.jsx("input",{type:"text",value:h,onChange:b=>u(b.target.value),placeholder:"搜索主题或分类...",className:"w-full rounded-md border border-slate-200 px-2 py-1 text-[12px] text-slate-700 placeholder:text-slate-400 outline-none focus:border-slate-400"})}),s.jsxs("div",{className:"overflow-y-auto flex-1 p-2",children:[N.map(b=>s.jsxs("div",{className:"mb-3",children:[s.jsx("div",{className:"px-1 py-0.5 mb-1.5 text-[10px] font-semibold text-slate-400 uppercase tracking-wider",children:b.name}),s.jsx("div",{className:"grid grid-cols-4 gap-1.5",children:b.profiles.map($=>s.jsxs("button",{onClick:()=>{r($.id)},className:`relative flex flex-col items-center gap-1 rounded-lg p-1.5 transition-all cursor-pointer ${y===$.id?"bg-slate-100 ring-2 ring-[var(--accent)]":"hover:bg-slate-50"}`,title:$.name,children:[s.jsx("span",{className:"h-8 w-8 rounded-full shadow-sm border border-slate-200",style:{background:`linear-gradient(135deg, ${$.accent} 0%, ${$.dark} 100%)`}}),s.jsx("span",{className:"text-[10px] text-slate-500 truncate w-full text-center leading-tight",children:$.name}),y===$.id&&s.jsx("span",{className:"absolute -top-0.5 -right-0.5 h-3.5 w-3.5 rounded-full bg-[var(--accent)] text-white flex items-center justify-center",children:s.jsx("svg",{width:"8",height:"8",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"4",strokeLinecap:"round",strokeLinejoin:"round",children:s.jsx("polyline",{points:"20 6 9 17 4 12"})})})]},$.id))})]},b.id)),N.length===0&&s.jsx("div",{className:"text-center text-[12px] text-slate-400 py-6",children:"无匹配主题"})]})]}),g==="colors"&&s.jsxs("div",{className:"p-2.5",children:[s.jsx("div",{className:"text-[10px] font-semibold text-slate-400 uppercase tracking-wider mb-2 px-1",children:"纯色（仅改强调色）"}),s.jsx("div",{className:"grid grid-cols-5 gap-2 justify-items-center",children:ht.map(b=>s.jsx("button",{title:b.accent,onClick:()=>{i(b.accent,b.dark)},className:"h-8 w-8 rounded-full transition-transform hover:scale-110 cursor-pointer shrink-0 border border-slate-200",style:{background:`linear-gradient(135deg, ${b.accent} 0%, ${b.dark} 100%)`,boxShadow:n===b.accent?"0 0 0 2px #fff, 0 0 0 3px var(--accent)":"none"}},b.accent))})]})]})]}),d<960&&s.jsx("button",{onClick:o,className:"flex h-9 w-9 items-center justify-center rounded-lg text-slate-500 hover:bg-slate-100 hover:text-slate-800 transition-colors cursor-pointer",title:"更多菜单",children:s.jsxs("svg",{width:"20",height:"20",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2.2",strokeLinecap:"round",strokeLinejoin:"round",children:[s.jsx("line",{x1:"3",y1:"12",x2:"21",y2:"12"}),s.jsx("line",{x1:"3",y1:"6",x2:"21",y2:"6"}),s.jsx("line",{x1:"3",y1:"18",x2:"21",y2:"18"})]})})]})]})}const ee=e=>({width:e,height:e,viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:2,strokeLinecap:"round",strokeLinejoin:"round","aria-hidden":"true"});function Tc({size:e=16,...t}){return s.jsxs("svg",{...ee(e),...t,children:[s.jsx("path",{d:"M12 3l1.9 5.8L19.5 10l-5.6 1.2L12 17l-1.9-5.8L4.5 10l5.6-1.2L12 3z"}),s.jsx("path",{d:"M19 14l.7 2.3L22 17l-2.3.7L19 20l-.7-2.3L16 17l2.3-.7L19 14z"})]})}function xf({size:e=16,...t}){return s.jsxs("svg",{...ee(e),...t,children:[s.jsx("path",{d:"M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"}),s.jsx("polyline",{points:"7 10 12 15 17 10"}),s.jsx("line",{x1:"12",y1:"15",x2:"12",y2:"3"})]})}function uf({size:e=16,...t}){return s.jsxs("svg",{...ee(e),...t,children:[s.jsx("path",{d:"M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"}),s.jsx("rect",{x:"8",y:"2",width:"8",height:"4",rx:"1",ry:"1"})]})}function zc({size:e=16,...t}){return s.jsxs("svg",{...ee(e),...t,children:[s.jsx("rect",{x:"3",y:"3",width:"18",height:"18",rx:"2",ry:"2"}),s.jsx("circle",{cx:"8.5",cy:"8.5",r:"1.5"}),s.jsx("polyline",{points:"21 15 16 10 5 21"})]})}function mf({size:e=16,...t}){return s.jsxs("svg",{...ee(e),...t,children:[s.jsx("path",{d:"M4.5 16.5c-1.5 1.26-2 5-2 5s3.74-.5 5-2c.71-.84.7-2.13-.09-2.91a2.18 2.18 0 0 0-2.91-.09z"}),s.jsx("path",{d:"M12 15l-3-3a22 22 0 0 1 2-3.95A12.88 12.88 0 0 1 22 2c0 2.72-.78 7.5-6 11a22.35 22.35 0 0 1-4 2z"}),s.jsx("path",{d:"M9 12H4s.55-3.03 2-4c1.62-1.08 5 0 5 0"}),s.jsx("path",{d:"M12 15v5s3.03-.55 4-2c1.08-1.62 0-5 0-5"})]})}function hf({size:e=16,...t}){return s.jsxs("svg",{...ee(e),...t,children:[s.jsx("polyline",{points:"6 9 6 2 18 2 18 9"}),s.jsx("path",{d:"M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"}),s.jsx("rect",{x:"6",y:"14",width:"12",height:"8"})]})}function Un({size:e=16,...t}){return s.jsxs("svg",{...ee(e),...t,children:[s.jsx("path",{d:"M16.5 9.4l-9-5.19"}),s.jsx("path",{d:"M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"}),s.jsx("polyline",{points:"3.27 6.96 12 12.01 20.73 6.96"}),s.jsx("line",{x1:"12",y1:"22.08",x2:"12",y2:"12"})]})}function ln({size:e=16,...t}){return s.jsxs("svg",{...ee(e),...t,children:[s.jsx("path",{d:"M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"}),s.jsx("path",{d:"M3 3v5h5"})]})}function ar({size:e=16,...t}){return s.jsxs("svg",{...ee(e),...t,children:[s.jsx("circle",{cx:"12",cy:"12",r:"3"}),s.jsx("path",{d:"M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"})]})}function lr({size:e=16,...t}){return s.jsxs("svg",{...ee(e),...t,children:[s.jsx("circle",{cx:"12",cy:"12",r:"10"}),s.jsx("path",{d:"M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"}),s.jsx("line",{x1:"12",y1:"17",x2:"12.01",y2:"17"})]})}function gt({size:e=16,...t}){return s.jsx("svg",{...ee(e),...t,children:s.jsx("path",{d:"M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"})})}function cn({size:e=16,...t}){return s.jsxs("svg",{...ee(e),...t,children:[s.jsx("path",{d:"M4 19.5A2.5 2.5 0 0 1 6.5 17H20"}),s.jsx("path",{d:"M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"})]})}function cr({size:e=16,...t}){return s.jsxs("svg",{...ee(e),...t,children:[s.jsx("path",{d:"M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"}),s.jsx("polyline",{points:"14 2 14 8 20 8"}),s.jsx("line",{x1:"16",y1:"13",x2:"8",y2:"13"}),s.jsx("line",{x1:"16",y1:"17",x2:"8",y2:"17"}),s.jsx("polyline",{points:"10 9 9 9 8 9"})]})}function Ac({size:e=16,...t}){return s.jsxs("svg",{...ee(e),...t,children:[s.jsx("circle",{cx:"13.5",cy:"6.5",r:".5",fill:"currentColor"}),s.jsx("circle",{cx:"17.5",cy:"10.5",r:".5",fill:"currentColor"}),s.jsx("circle",{cx:"8.5",cy:"7.5",r:".5",fill:"currentColor"}),s.jsx("circle",{cx:"6.5",cy:"12.5",r:".5",fill:"currentColor"}),s.jsx("path",{d:"M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10c.926 0 1.648-.746 1.648-1.688 0-.437-.18-.835-.437-1.125-.29-.289-.438-.652-.438-1.125a1.64 1.64 0 0 1 1.668-1.668h1.996c3.051 0 5.555-2.503 5.555-5.554C21.965 6.012 17.461 2 12 2z"})]})}function qn({size:e=16,...t}){return s.jsx("svg",{...ee(e),...t,children:s.jsx("path",{d:"M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z"})})}function dr({size:e=16,...t}){return s.jsxs("svg",{...ee(e),...t,children:[s.jsx("path",{d:"M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"}),s.jsx("line",{x1:"12",y1:"9",x2:"12",y2:"13"}),s.jsx("line",{x1:"12",y1:"17",x2:"12.01",y2:"17"})]})}function Mc({size:e=16,...t}){return s.jsxs("svg",{...ee(e),...t,children:[s.jsx("line",{x1:"22",y1:"12",x2:"2",y2:"12"}),s.jsx("path",{d:"M5.45 5.11L2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"}),s.jsx("line",{x1:"6",y1:"16",x2:"6.01",y2:"16"}),s.jsx("line",{x1:"10",y1:"16",x2:"10.01",y2:"16"})]})}function bf({size:e=16,...t}){return s.jsx("svg",{...ee(e),...t,children:s.jsx("polygon",{points:"5 3 19 12 5 21 5 3",fill:"currentColor",stroke:"none"})})}function yf({size:e=16,...t}){return s.jsxs("svg",{...ee(e),...t,children:[s.jsx("rect",{x:"2",y:"3",width:"20",height:"14",rx:"2",ry:"2"}),s.jsx("line",{x1:"8",y1:"21",x2:"16",y2:"21"}),s.jsx("line",{x1:"12",y1:"17",x2:"12",y2:"21"})]})}function He({size:e=16,...t}){return s.jsx("svg",{...ee(e),...t,children:s.jsx("path",{d:"M12 2l2.4 7.2L22 12l-7.6 2.8L12 22l-2.4-7.2L2 12l7.6-2.8L12 2z"})})}function dn({size:e=16,...t}){return s.jsxs("svg",{...ee(e),...t,children:[s.jsx("line",{x1:"22",y1:"2",x2:"11",y2:"13"}),s.jsx("polygon",{points:"22 2 15 22 11 13 2 9 22 2"})]})}function Ic({size:e=16,...t}){return s.jsx("svg",{...ee(e),...t,children:s.jsx("polyline",{points:"20 6 9 17 4 12"})})}function kt({size:e=16,...t}){return s.jsxs("svg",{...ee(e),...t,children:[s.jsx("line",{x1:"18",y1:"6",x2:"6",y2:"18"}),s.jsx("line",{x1:"6",y1:"6",x2:"18",y2:"18"})]})}function Lc({size:e=16,...t}){return s.jsxs("svg",{...ee(e),...t,children:[s.jsx("path",{d:"M9 14 4 9l5-5"}),s.jsx("path",{d:"M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5v0a5.5 5.5 0 0 1-5.5 5.5H11"})]})}function Rc({size:e=16,...t}){return s.jsxs("svg",{...ee(e),...t,children:[s.jsx("path",{d:"M15 14 20 9l-5-5"}),s.jsx("path",{d:"M20 9H9.5a5.5 5.5 0 0 0-5.5 5.5v0a5.5 5.5 0 0 0 5.5 5.5H13"})]})}function Oc({size:e=16,...t}){return s.jsxs("svg",{...ee(e),...t,children:[s.jsx("rect",{x:"3",y:"3",width:"7",height:"18",rx:"1"}),s.jsx("rect",{x:"14",y:"3",width:"7",height:"18",rx:"1"})]})}function Dc({size:e=16,...t}){return s.jsxs("svg",{...ee(e),...t,children:[s.jsx("path",{d:"M15 3h6v6"}),s.jsx("path",{d:"M9 21H3v-6"}),s.jsx("path",{d:"M21 3 14 10"}),s.jsx("path",{d:"M3 21 10 14"})]})}function Pc({size:e=16,...t}){return s.jsxs("svg",{...ee(e),...t,children:[s.jsx("path",{d:"M4 14h6v6"}),s.jsx("path",{d:"M20 10h-6V4"}),s.jsx("path",{d:"M14 10 21 3"}),s.jsx("path",{d:"M3 21 10 14"})]})}function Fc({size:e=16,...t}){return s.jsxs("svg",{...ee(e),...t,children:[s.jsx("rect",{x:"3",y:"3",width:"7",height:"7",rx:"1"}),s.jsx("rect",{x:"14",y:"3",width:"7",height:"7",rx:"1"}),s.jsx("rect",{x:"3",y:"14",width:"7",height:"7",rx:"1"}),s.jsx("rect",{x:"14",y:"14",width:"7",height:"7",rx:"1"})]})}function Ue({icon:e,label:t,onClick:n}){return s.jsxs("button",{onClick:n,className:"w-full flex items-center gap-3 px-4 py-3 rounded-xl border border-slate-200 bg-slate-50/50 hover:bg-slate-50 text-[13px] font-medium text-slate-700 transition-colors cursor-pointer",children:[s.jsx("span",{className:"text-slate-500",children:e}),s.jsx("span",{children:t})]})}const Bc=[{key:"document",label:"A4 规范文档",icon:s.jsx(cr,{size:20})},{key:"article",label:"长图文排版",icon:s.jsx(cn,{size:20})},{key:"card",label:"分页图文卡",icon:s.jsx(zc,{size:20})},{key:"html",label:"自由画布",icon:s.jsx(Ac,{size:20})}];function Hc({isOpen:e,onClose:t,mode:n,setMode:i,accent:r,setTheme:o,onTriggerGuide:a,onOpenSettings:l,onOpenPrivacy:d,onRestoreDemo:c,onOpenAiTypeset:p}){return v.useEffect(()=>{if(e){const f=document.body.style.overflow;return document.body.style.overflow="hidden",()=>{document.body.style.overflow=f}}},[e]),e?s.jsxs("div",{className:"fixed inset-0 z-50 flex justify-end bg-black/40 backdrop-blur-xs animate-fade-in",children:[s.jsx("div",{className:"absolute inset-0",onClick:t}),s.jsxs("div",{className:"relative w-80 max-w-full h-full bg-white shadow-2xl flex flex-col p-6 animate-slide-in-right overflow-y-auto",children:[s.jsxs("div",{className:"flex items-center justify-between border-b border-slate-100 pb-4 mb-5",children:[s.jsxs("div",{className:"flex items-center gap-2",children:[s.jsx("div",{className:"app-logo-bg flex h-6 w-6 items-center justify-center rounded-md text-white shadow-sm",children:s.jsxs("svg",{xmlns:"http://www.w3.org/2000/svg",width:"12",height:"12",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2.5",strokeLinecap:"round",strokeLinejoin:"round",children:[s.jsx("path",{d:"M12 19l7-7 3 3-7 7-3-3z"}),s.jsx("path",{d:"M18 13l-1.5-7.5L2 2l3.5 14.5L13 18l5-5z"})]})}),s.jsx("span",{className:"font-bold text-slate-800 text-[15px]",children:"MarkFlow"})]}),s.jsx("button",{onClick:t,className:"rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition-colors","aria-label":"关闭菜单",children:s.jsxs("svg",{width:"18",height:"18",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2.2",strokeLinecap:"round",strokeLinejoin:"round",children:[s.jsx("line",{x1:"18",y1:"6",x2:"6",y2:"18"}),s.jsx("line",{x1:"6",y1:"6",x2:"18",y2:"18"})]})})]}),s.jsxs("div",{className:"mb-6",children:[s.jsx("div",{className:"mb-2.5 text-[11px] font-bold text-slate-400 uppercase tracking-wider",children:"切换场景模式"}),s.jsx("div",{className:"grid grid-cols-2 gap-2",children:Bc.map(f=>{const g=n===f.key;return s.jsxs("button",{onClick:()=>{i(f.key),t()},className:`flex flex-col items-center justify-center p-3 rounded-xl border text-center transition-all cursor-pointer ${g?"border-[var(--accent)] bg-emerald-50/30 text-[var(--accent)] font-semibold shadow-sm":"border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:bg-slate-50"}`,children:[s.jsx("span",{className:"mb-1.5",children:f.icon}),s.jsx("span",{className:"text-[12px]",children:f.label})]},f.key)})})]}),s.jsxs("div",{className:"space-y-3 mb-6 flex-1",children:[s.jsx("div",{className:"mb-2.5 text-[11px] font-bold text-slate-400 uppercase tracking-wider",children:"系统功能"}),s.jsx(Ue,{icon:s.jsx(He,{size:18}),label:"AI 排版",onClick:()=>{t(),p()}}),s.jsx(Ue,{icon:s.jsx(lr,{size:18}),label:"查看使用帮助",onClick:()=>{t(),a()}}),s.jsx(Ue,{icon:s.jsx(ar,{size:18}),label:"图床参数配置",onClick:()=>{t(),l()}}),s.jsx(Ue,{icon:s.jsx(ln,{size:18}),label:"恢复当前示例内容",onClick:()=>{t(),c()}}),s.jsx(Ue,{icon:s.jsx(gt,{size:18}),label:"隐私与安全说明",onClick:()=>{t(),d()}})]}),s.jsxs("div",{className:"border-t border-slate-100 pt-5 mb-6",children:[s.jsx("div",{className:"mb-3 text-[11px] font-bold text-slate-400 uppercase tracking-wider",children:"切换系统主题色"}),s.jsx("div",{className:"flex items-center gap-3 justify-center",children:ht.map(f=>s.jsx("button",{title:f.accent,onClick:()=>o(f.accent,f.dark),className:"h-8 w-8 rounded-full border transition-transform hover:scale-110 cursor-pointer flex items-center justify-center",style:{background:f.accent,borderColor:r===f.accent?"#111":"transparent",boxShadow:r===f.accent?"0 0 0 2px #fff, 0 0 0 4px var(--accent)":"none"}},f.accent))})]}),s.jsxs("div",{className:"border-t border-slate-100 pt-5 text-center space-y-4",children:[s.jsx("div",{className:"flex items-center justify-center",children:s.jsx("a",{href:"https://github.com/huanyu-a/MarkFlow",target:"_blank",rel:"noopener noreferrer",className:"text-xs text-slate-500 hover:underline flex items-center gap-1",children:"GitHub 仓库"})}),s.jsxs("div",{className:"text-[11px] text-slate-400 leading-relaxed",children:["本项目为 100% 纯前端开源工具",s.jsx("br",{}),"所有编辑数据均存储在您的本地浏览器中"]})]})]})]}):null}const ot=8,It=300;function Me({text:e,children:t,position:n="top",disabled:i}){const r=v.useRef(null),[o,a]=v.useState(!1),[l,d]=v.useState({position:"fixed",zIndex:9999}),c=v.useCallback(()=>{if(!e)return;const f=r.current;if(!f)return;const g=f.getBoundingClientRect(),x=window.innerWidth,h=window.innerHeight,u=Math.min(e.length*7.5+24,It),j=Math.ceil((e.length*7.5+24)/It)*18+14;let w=g.left+g.width/2-u/2;w=Math.max(ot,Math.min(w,x-ot-u));let N;n==="top"?(N=g.top-j-8,N<ot&&(N=g.bottom+8)):(N=g.bottom+8,N+j>h-ot&&(N=g.top-j-8)),d({position:"fixed",zIndex:9999,left:w,top:N,maxWidth:It}),a(!0)},[e,n]),p=v.useCallback(()=>a(!1),[]);return i?s.jsx(s.Fragment,{children:t}):s.jsxs("span",{ref:r,className:"inline-flex",onMouseEnter:c,onMouseLeave:p,children:[t,o&&Wt.createPortal(s.jsx("span",{className:"pointer-events-none rounded-md bg-slate-800 px-2.5 py-1.5 text-[12px] leading-snug text-white shadow-lg",style:l,role:"tooltip",children:e}),document.body)]})}const Uc=[{id:"library",icon:cn,label:"指令库"},{id:"demo",icon:ln,label:"恢复示例"}];function qc({activePanel:e,onPanelChange:t,onOpenAiTypeset:n,onOpenExtension:i,onCopyGuide:r}){const[o,a]=v.useState(!1),l=v.useRef();return s.jsxs("nav",{className:"hidden md:flex w-12 shrink-0 flex-col items-center border-r border-slate-200 bg-white pt-2",children:[s.jsxs("div",{className:"flex flex-col items-center gap-1 flex-1",children:[s.jsx(Me,{text:"组件库",children:s.jsx("button",{onClick:()=>i==null?void 0:i(),className:`flex h-9 w-9 items-center justify-center rounded-lg transition-colors cursor-pointer ${e==="extension"?"bg-[var(--accent)]/10 text-[var(--accent)]":"text-slate-400 hover:bg-slate-100 hover:text-slate-700"}`,children:s.jsx(Fc,{size:18})})}),s.jsxs("div",{className:"relative",onMouseEnter:()=>{clearTimeout(l.current),a(!0)},onMouseLeave:()=>{l.current=setTimeout(()=>a(!1),200)},children:[s.jsx(Me,{text:"AI 排版",children:s.jsx("button",{onClick:()=>n==null?void 0:n(),className:`flex h-9 w-9 items-center justify-center rounded-lg transition-colors cursor-pointer ${e==="ai"?"bg-[var(--accent)]/10 text-[var(--accent)]":"text-slate-400 hover:bg-slate-100 hover:text-slate-700"}`,children:s.jsx(He,{size:18})})}),o&&s.jsxs("div",{className:"absolute left-full top-0 ml-2 flex flex-col gap-1 z-50",onMouseEnter:()=>clearTimeout(l.current),onMouseLeave:()=>a(!1),children:[s.jsxs("button",{onClick:()=>{r==null||r(),a(!1)},className:"flex items-center gap-1.5 whitespace-nowrap rounded-md border border-slate-200 bg-white px-2.5 py-1.5 text-[11px] font-medium text-slate-600 shadow-md hover:bg-slate-50 hover:text-slate-800 transition-colors cursor-pointer",children:[s.jsx(Tc,{size:12}),"复制排版指令"]}),s.jsxs("button",{onClick:()=>{n==null||n(),a(!1)},className:"flex items-center gap-1.5 whitespace-nowrap rounded-md bg-[var(--accent)] px-2.5 py-1.5 text-[11px] font-semibold text-white shadow-md hover:opacity-90 transition-opacity cursor-pointer",children:[s.jsx(dn,{size:12}),"AI 排版"]})]})]}),Uc.map(({id:d,icon:c,label:p})=>{const f=e===d;return s.jsx(Me,{text:p,children:s.jsx("button",{onClick:()=>t(f?null:d),className:`flex h-9 w-9 items-center justify-center rounded-lg transition-colors cursor-pointer ${f?"bg-[var(--accent)]/10 text-[var(--accent)]":"text-slate-400 hover:bg-slate-100 hover:text-slate-700"}`,children:s.jsx(c,{size:18})})},d)})]}),s.jsxs("div",{className:"flex flex-col items-center gap-1 pb-3",children:[s.jsx(Me,{text:"设置",children:s.jsx("button",{onClick:()=>t(e==="settings"?null:"settings"),className:`flex h-9 w-9 items-center justify-center rounded-lg transition-colors cursor-pointer ${e==="settings"?"bg-[var(--accent)]/10 text-[var(--accent)]":"text-slate-400 hover:bg-slate-100 hover:text-slate-700"}`,children:s.jsx(ar,{size:18})})}),s.jsx(Me,{text:"查看源码",children:s.jsx("a",{href:"https://github.com/huanyu-a/MarkFlow",target:"_blank",rel:"noopener noreferrer",className:"flex h-9 w-9 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700 transition-colors",children:s.jsx("svg",{width:"18",height:"18",viewBox:"0 0 98 96",fill:"currentColor",children:s.jsx("path",{d:"M41.4395 69.3848C28.8066 67.8535 19.9062 58.7617 19.9062 46.9902C19.9062 42.2051 21.6289 37.0371 24.5 33.5918C23.2559 30.4336 23.4473 23.7344 24.8828 20.959C28.7109 20.4805 33.8789 22.4902 36.9414 25.2656C40.5781 24.1172 44.4062 23.543 49.0957 23.543C53.7852 23.543 57.6133 24.1172 61.0586 25.1699C64.0254 22.4902 69.2891 20.4805 73.1172 20.959C74.457 23.543 74.6484 30.2422 73.4043 33.4961C76.4668 37.1328 78.0937 42.0137 78.0937 46.9902C78.0937 58.7617 69.1934 67.6621 56.3691 69.2891C59.623 71.3945 61.8242 75.9883 61.8242 81.252V91.2051C61.8242 94.0762 64.2168 95.7031 67.0879 94.5547C84.4102 87.9512 98 70.6289 98 49.1914C98 22.1074 75.9883 0 48.9043 0C21.8203 0 0 22.1074 0 49.1914C0 70.4375 13.4941 88.0469 31.6777 94.6504C34.2617 95.6074 36.75 93.8848 36.75 91.3008V83.6445C35.4102 84.2188 33.6875 84.6016 32.1562 84.6016C25.8398 84.6016 22.1074 81.1563 19.4277 74.7441C18.375 72.1602 17.2266 70.6289 15.0254 70.3418C13.877 70.2461 13.4941 69.7676 13.4941 69.1934C13.4941 68.0449 15.4082 67.1836 17.3223 67.1836C20.0977 67.1836 22.4902 68.9063 24.9785 72.4473C26.8926 75.2227 28.9023 76.4668 31.2949 76.4668C33.6875 76.4668 35.2187 75.6055 37.4199 73.4043C39.0469 71.7773 40.291 70.3418 41.4395 69.3848Z"})})})}),s.jsx(Me,{text:"查看使用帮助",children:s.jsx("button",{onClick:()=>t("help"),className:"flex h-9 w-9 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700 transition-colors cursor-pointer",children:s.jsx(lr,{size:18})})})]})]})}const pr=v.createContext({fullscreen:!1,toggleFullscreen:()=>{}}),Wc=()=>v.useContext(pr);function Wn({children:e,defaultWidth:t=768,defaultHeight:n=660,minWidth:i=400,minHeight:r=300}){const[o,a]=v.useState({w:t,h:n}),[l,d]=v.useState(!1),c=v.useRef(null),p=v.useCallback(()=>d(u=>!u),[]),f=v.useCallback(u=>y=>{y.preventDefault(),y.stopPropagation(),y.target.setPointerCapture(y.pointerId),c.current={startX:y.clientX,startY:y.clientY,startW:o.w,startH:o.h,dir:u}},[o]),g=v.useCallback(u=>{if(!c.current)return;const{startX:y,startY:j,startW:w,startH:N,dir:b}=c.current,$=u.clientX-y,S=u.clientY-j;a({w:b.includes("r")?Math.max(i,w+$):b.includes("l")?Math.max(i,w-$):w,h:b.includes("b")?Math.max(r,N+S):b.includes("t")?Math.max(r,N-S):N})},[i,r]),x=v.useCallback(()=>{c.current=null},[]);v.useEffect(()=>{if(!l)return;const u=y=>{y.key==="Escape"&&d(!1)};return window.addEventListener("keydown",u),()=>window.removeEventListener("keydown",u)},[l]);const h="absolute z-10 bg-transparent transition-colors hover:bg-[var(--accent)]/30";return s.jsx(pr.Provider,{value:{fullscreen:l,toggleFullscreen:p},children:s.jsxs("div",{className:"relative flex flex-col overflow-hidden rounded-xl border border-slate-100 bg-white shadow-2xl",style:l?{width:"100vw",height:"100vh",borderRadius:0}:{width:o.w,height:o.h},onPointerMove:g,onPointerUp:x,children:[!l&&s.jsxs(s.Fragment,{children:[s.jsx("div",{className:`${h} right-0 top-2 bottom-2 w-1 cursor-e-resize`,onPointerDown:f("r")}),s.jsx("div",{className:`${h} bottom-0 left-2 right-2 h-1 cursor-s-resize`,onPointerDown:f("b")}),s.jsx("div",{className:`${h} right-0 bottom-0 w-3 h-3 cursor-se-resize`,onPointerDown:f("br")}),s.jsx("div",{className:`${h} left-0 top-2 bottom-2 w-1 cursor-w-resize`,onPointerDown:f("l")}),s.jsx("div",{className:`${h} top-0 left-2 right-2 h-1 cursor-n-resize`,onPointerDown:f("t")}),s.jsx("div",{className:`${h} left-0 top-0 w-3 h-3 cursor-nw-resize`,onPointerDown:f("tl")}),s.jsx("div",{className:`${h} right-0 top-0 w-3 h-3 cursor-ne-resize`,onPointerDown:f("tr")}),s.jsx("div",{className:`${h} left-0 bottom-0 w-3 h-3 cursor-sw-resize`,onPointerDown:f("bl")})]}),e]})})}const le={toolbar:{promptLibrary:{label:"指令库",tooltip:"打开专属指令库，选择并复制 AI 提示词"},copyHtml:{label:"HTML源码",tooltip:"复制带内联样式的 HTML 源码"},copyRichText:{label:"复制富文本",tooltip:"复制富文本，可直接粘贴到微信等编辑器，排版不丢失"},exportLongImage:{label:"导出长图",tooltip:"将预览内容导出为长图 PNG"},exportPdf:{label:"导出 PDF",tooltip:"高保真导出 PDF，视觉完全一致"},exportPptx:{label:"导出全图 PPT",tooltip:"高保真导出 PPT，每页为完整图片，视觉完全一致"},exportPptxEditable:{label:"导出可编辑 PPT",tooltip:"实验性功能：导出为可编辑文本框，字体、复杂样式（渐变、毛玻璃、动画等）可能会丢失，适合二次编辑草稿"},exportPng:{label:"导出 PNG",tooltip:"将预览内容导出为 PNG 图片"},exportCurrentPage:{label:"导出当前页",tooltip:"将当前页面导出为 PNG 图片"},exportZip:{label:"打包 ZIP",tooltip:"将所有页面打包为 ZIP 文件下载"},exportSource:{label:"导出源码",tooltip:"导出底层 HTML 源码为 .html 文件"},fullscreen:{label:"全屏播放",tooltip:"全屏沉浸查看展示区内容"},refresh:{label:"刷新",tooltip:"重新渲染预览区内容"},uploadImage:{label:"上传图片",tooltip:"上传图片到图床并复制链接，可发送给 AI 使用"},allowScripts:{label:"互动脚本",tooltip:"允许预览区执行 JavaScript 交互脚本"}},promptLibrary:{title:"风格指令库",workflowStep1:"选一个喜欢的风格",workflowStep2:"复制提示词发给 AI",workflowStep3:"将生成的 HTML 贴回系统渲染",copyPrompt:{label:"复制提示词",tooltip:"复制完整设计指令到剪贴板"},builtinTab:"内置风格",customTab:"我的指令",addCustom:"新增自定义指令",outputTypeLabel:"先选输出类型",visualToneLabel:"再选视觉气质",showBasic:"显示基础模板",recommendAI:"推荐使用 Claude / ChatGPT / Gemini 生成 HTML"},imageUpload:{successToast:e=>e==="local"?"图片已上传，链接已复制。当前为本地存储，建议配置图床以便 AI 访问链接":"图片已上传，链接已复制到剪贴板，可发送给 AI 使用",errorToast:"图片上传失败"},common:{loading:"加载中…",copySuccess:"已复制到剪贴板",copyFail:"复制失败，请重试",confirm:"确认",cancel:"取消",save:"保存",delete:"删除",edit:"编辑",close:"关闭",exportMd:{label:"导出 Markdown",tooltip:"将编辑器内容导出为 .md 文件"}},aiTypeset:{title:"AI 排版",configTitle:"AI 配置",apiUrl:"API 地址",apiUrlPlaceholder:"https://api.deepseek.com",apiKey:"API Key",apiKeyPlaceholder:"sk-...（本地 API 可留空）",model:"模型名称",modelPlaceholder:"deepseek-chat（可留空）",skillSelect:"排版技能",selectAll:"全选",inlineSkills:"行内标识",blockSkills:"块级组件",runButton:"AI 排版",running:"排版中…",stopButton:"停止",previewTitle:"排版结果",applyButton:"应用到编辑器",retryButton:"重新生成",noContent:"编辑器内容为空",noConfig:"请先配置 API 地址和 Key",copyGuide:"复制排版指令",copyGuideTooltip:"复制当前模式的 AI 排版指令，可手动发给 AI",customInstructions:"自定义指令"}},Gc=new Set(["http:","https:"]),Kc=new Set(["localhost","ip6-localhost","ip6-loopback","metadata.google.internal"]),Vc=[{start:0,end:16777215},{start:167772160,end:184549375},{start:2130706432,end:2147483647},{start:2851995648,end:2852061183},{start:2886729728,end:2887778303},{start:3232235520,end:3232301055}];class Lt extends Error{constructor(t){super(t),this.name="FetchSecurityError"}}class Gn extends Error{constructor(t="请求超时"){super(t),this.name="FetchTimeoutError"}}function Yc(e){const t=e.split(".");if(t.length!==4)return null;let n=0;for(const i of t){const r=Number(i);if(!Number.isInteger(r)||r<0||r>255)return null;n=n<<8|r}return n>>>0}function pn(e){const t=e.toLowerCase().replace(/^\[|\]$/g,"");if(Kc.has(t))return!0;if(/^\d+\.\d+\.\d+\.\d+$/.test(t)){const n=Yc(t);if(n!==null){for(const i of Vc)if(n>=i.start&&n<=i.end)return!0}return!1}if(t.includes(":")){if(t==="::1"||t==="::"||t.startsWith("fe8")||t.startsWith("fe9")||t.startsWith("fea")||t.startsWith("feb")||t.startsWith("fc")||t.startsWith("fd"))return!0;const n=t.match(/:ffff:(\d+\.\d+\.\d+\.\d+)$/);return n?pn(n[1]):!1}return!!(t.endsWith(".local")||t.endsWith(".internal")||t.endsWith(".localhost"))}function Kn(e,t=!1){let n;try{n=new URL(e)}catch{throw new Lt(`无效的 URL: ${e}`)}if(!Gc.has(n.protocol))throw new Lt(`不允许的 URL 协议: ${n.protocol}`);if(!t&&pn(n.hostname))throw new Lt(`不允许请求内网地址: ${n.hostname}（如需加载内网资源，请在设置中开启"允许加载内网资源"）`);return n}function Xc(...e){const t=new AbortController;for(const n of e)if(n){if(n.aborted){t.abort(n.reason);break}n.addEventListener("abort",()=>t.abort(n.reason),{once:!0})}return t.signal}async function Zc(e,t={}){const{timeoutMs:n=3e4,allowIntranet:i=!1,signal:r,...o}=t;Kn(e,i);const a=new AbortController,l=setTimeout(()=>a.abort(),n),d=Xc(r,a.signal);try{const c=await fetch(e,{...o,signal:d,redirect:o.redirect??"follow"});return c.redirected&&Kn(c.url,i),c}catch(c){throw a.signal.aborted&&!(r!=null&&r.aborted)?new Gn(`请求超时（${n}ms）: ${e}`):c instanceof DOMException&&c.name==="AbortError"?r!=null&&r.aborted?c:new Gn(`请求超时（${n}ms）: ${e}`):c}finally{clearTimeout(l)}}async function vf(e,t=3e4){const n=typeof t=="number"?{timeoutMs:t}:t,i=await Zc(e,{mode:"cors",timeoutMs:n.timeoutMs,allowIntranet:n.allowIntranet,signal:n.signal,credentials:n.credentials??"omit",referrerPolicy:"no-referrer"});if(!i.ok)throw new Error(`获取图片失败: HTTP ${i.status} (${i.statusText})`);const r=i.headers.get("content-type");if(r&&!r.startsWith("image/"))throw new Error(`响应不是图片类型: ${r}`);return i.arrayBuffer()}function Jc(e){const t=e.trim().replace(/\/+$/,"");return/\/chat\/completions$/i.test(t)?t:/\/v1$/i.test(t)?`${t}/chat/completions`:`${t}/v1/chat/completions`}function Qc(e){const t=e.trim();if(!t)throw new Error("AI 接口地址为空");if(!/^https?:\/\//i.test(t))throw new Error("AI 接口地址仅支持 http(s) 协议");try{const i=new URL(t).hostname.toLowerCase().replace(/^\[|\]$/g,"");if(/^(localhost|127\.0\.0\.1|0\.0\.0\.0|\[::1\]|::1)$/i.test(i))return;if(/^http:\/\//i.test(t))throw new Error("非本地 AI 接口请使用 https 地址");if(pn(i))throw new Error("AI 接口地址不能指向内网地址")}catch(n){throw n instanceof Error?n:new Error("AI 接口地址格式不正确")}}function ed(e,t,n){const i={"Content-Type":"application/json"};return t.trim()&&(i.Authorization=`Bearer ${t.trim()}`),{url:e,headers:i,viaProxy:!1}}function Vn(e,t=160){if(!e)return"";const n=e.replace(/<script[\s\S]*?<\/script>/gi," ").replace(/<style[\s\S]*?<\/style>/gi," ").replace(/<[^>]+>/g," ").replace(/&[a-z#0-9]+;/gi," ").replace(/\s+/g," ").trim();return n?n.length>t?n.slice(0,t)+"…":n:""}function Yn(e){var n,i;const t=((i=(n=e.headers)==null?void 0:n.get)==null?void 0:i.call(n,"content-type"))||"";return/text\/html|application\/xhtml\+xml/i.test(t)}function fr(e){return/^\s*(?:<!doctype\s+html|<html[\s>])/i.test(e)}function td(e){var t;if(!e||fr(e))return"";try{const n=JSON.parse(e),i=((t=n==null?void 0:n.error)==null?void 0:t.message)??(n==null?void 0:n.message);if(typeof i=="string"&&i.trim())return Vn(i)}catch{}return Vn(e)}async function nd(e,t,n,i){var g,x,h,u,y,j,w,N;const r=Jc(e.apiUrl);Qc(r);const o=JSON.stringify({model:e.model||void 0,messages:t,stream:!0,temperature:.7}),a=ed(r,e.apiKey);let l;try{l=await fetch(a.url,{method:"POST",headers:a.headers,body:o,signal:i})}catch(b){throw b.name==="AbortError"||a.viaProxy?b:new Error("无法直连 AI 接口（可能被浏览器 CORS 拦截或网络不可达）。请换用支持浏览器跨域直连的 API 服务，或用 pnpm dev 启动开发模式经本地代理调用。")}if(l.ok&&Yn(l))throw new Error(a.viaProxy?"AI 接口返回了 HTML 页面而非 JSON 流，请检查「设置 → AI 配置」中的 API 地址是否正确":"AI 接口地址指向了一个网页而非 API 端点，请检查「设置 → AI 配置」中的 API 地址（通常应以 /v1 结尾或为服务方提供的 API 域名）");if(!l.ok){const b=await l.text().catch(()=>"");if(Yn(l)||fr(b))throw new Error(`API 请求失败 (${l.status}): 接口地址返回了网页而非 API 响应，请检查「设置 → AI 配置」中的 API 地址是否正确（通常应以 /v1 结尾）`);const $=td(b);throw new Error(`API 请求失败 (${l.status}): ${$||l.statusText}`)}const d=(g=l.body)==null?void 0:g.getReader();if(!d)throw new Error("当前浏览器不支持流式响应");const c=new TextDecoder;let p="",f="";try{for(;;){const{done:$,value:S}=await d.read();if($)break;p+=c.decode(S,{stream:!0});const C=p.split(`
`);p=C.pop()||"";for(const O of C){const F=O.trim();if(!F||!F.startsWith("data: "))continue;const Q=F.slice(6);if(Q!=="[DONE]")try{const Z=((u=(h=(x=JSON.parse(Q).choices)==null?void 0:x[0])==null?void 0:h.delta)==null?void 0:u.content)||"";Z&&(f+=Z,n(Z))}catch{}}}p+=c.decode();const b=p.trim();if(b&&b!=="data: [DONE]"&&b!=="[DONE]")try{const S=((w=(j=(y=JSON.parse(b.replace(/^data: /,"")).choices)==null?void 0:y[0])==null?void 0:j.delta)==null?void 0:w.content)||"";S&&(f+=S,n(S))}catch{}}finally{try{Promise.resolve((N=d.cancel)==null?void 0:N.call(d)).catch(()=>{})}catch{}}return f}function id(e){const t=e.trim().toLowerCase();return t.startsWith("http://localhost")||t.startsWith("http://127.0.0.1")||t.startsWith("http://[::1]")||t.startsWith("http://0.0.0.0")}function Xn(e){const t=e.apiUrl.trim();return t?id(t)?!0:!!(t&&e.apiKey.trim()):!1}const Zn={info:{bg:"#e3f2fd",fg:"#1565c0",border:"#90caf9"},tip:{bg:"#e8f5e9",fg:"#2e7d32",border:"#a5d6a7"},warning:{bg:"#fff3e0",fg:"#e65100",border:"#ffcc80"},danger:{bg:"#fce4ec",fg:"#c62828",border:"#ef9a9a"}},rd={id:"Badge_DA01",name:"行内徽章",tag:"badge",description:"行内彩色徽章标签，用于标记状态或类型",attrs:[{key:"type",label:"徽章类型",required:!1,default:"info",options:["info","tip","warning","danger"]},{key:"title",label:"标题",required:!0,default:""}],example:'<badge type="tip" title="推荐" />',render(e,t,n){const i=(e.type||"info").toLowerCase(),r=e.title||"Badge",o=Zn[i]||Zn.info;return`<span style="display:inline-block;padding:0 ${m[2]};margin:0 ${m[1]};border-radius:${R.sm};font-size:${E.xs};font-weight:${A.semibold};background:${o.bg};color:${o.fg};border:1px solid ${o.border};line-height:1.6;vertical-align:middle">${_(r)}</span>`}},sd={id:"Icon_DA01",name:"行内图标",tag:"icon",description:"通过 Iconify API 加载的矢量图标，支持 Material Symbols、Fluent、Skill Icons 等数千个图标集",attrs:[{key:"name",label:"图标名称",required:!0,options:["material-symbols:home","material-symbols:star","material-symbols:search","material-symbols:settings","material-symbols:person","material-symbols:mail","material-symbols:call","material-symbols:share","material-symbols:download","material-symbols:upload","material-symbols:edit","material-symbols:delete","material-symbols:add","material-symbols:close","material-symbols:check","material-symbols:arrow-forward","material-symbols:arrow-back","material-symbols:info","material-symbols:warning","material-symbols:favorite","material-symbols:visibility","material-symbols:lock","material-symbols:language","material-symbols:location-on","material-symbols:calendar-today","material-symbols:link","material-symbols:bookmark","material-symbols:thumb-up","material-symbols:notifications","material-symbols:chat"]},{key:"size",label:"图标尺寸",required:!1,default:"1em"}],example:'<icon name="material-symbols:star" size="2em" />',render(e,t,n){const i=e.name||"material-symbols:help",r=e.size||"1em";return`<section style="display:flex;align-items:center;justify-content:center;padding:16px 0"><img src="https://api.iconify.design/${encodeURIComponent(i)}.svg" alt="${V(i)}" style="width:${r==="1em"?"48px":r};height:${r==="1em"?"48px":r};display:block" onerror="this.style.display='none'"></section>`}},Ze=[Hi,Ui,qi,Fi,Bi,Yi,Jt,Xi,Zi,Qt,rd,sd],gr=[Ji,Yt,Xt,Zt,yt,en,tn,nn,Qi,sn,er,tr,rn];Object.fromEntries(Ze.map(e=>[e.id,e]));const wf=Object.fromEntries(Ze.map(e=>[e.tag,e])),od=[{id:"inline-highlight",name:"渐变背景强调",tag:"==",category:"inline",description:`语法：==文字==
效果：主题色渐变背景强调（强调强度大于加粗）`},{id:"inline-badge",name:"胶囊标签",tag:"!!",category:"inline",description:`语法：!!文字!!
效果：圆角药丸标签背景`},{id:"inline-strong-em",name:"加重强调",tag:"^^",category:"inline",description:`语法：^^文字^^
效果：靛青/主题色加重强调`},{id:"inline-soft",name:"柔光重点",tag:"::",category:"inline",description:`语法：::文字::
效果：柔光主题色重点文字`},{id:"inline-underline",name:"主题色下划线",tag:"__",category:"inline",description:`语法：__文字__
效果：主题色下划线`}];function ad(e){return!e||e.length===0?"（无属性）":e.map(t=>{var o;const n=t.required?"必填":"可选",i=t.default?`，默认：${t.default}`:"",r=(o=t.options)!=null&&o.length?`，可选值：${t.options.join(" / ")}`:"";return`- ${t.key}（${n}）${t.label}${i}${r}`}).join(`
`)}function ld(e){const t=[];return t.push(`标签：<${e.tag}>`),e.example&&t.push(`示例：
${e.example}`),t.push(`属性：
${ad(e.attrs)}`),{id:e.id,name:e.name,tag:e.tag,category:"block",description:t.join(`
`)}}function cd(e){var i;const t=e.spec,n=[];return n.push(`容器：:::${t.name}`),t.example&&n.push(`示例：
${t.example}`),(i=t.fields)!=null&&i.length&&n.push(`属性：
${t.fields.map(r=>`  ${r.name}${r.required?"（必填）":""} — ${r.description}`).join(`
`)}`),{id:t.name,name:t.label,tag:`:::${t.name}`,category:"block",description:n.join(`
`)}}function dd(){return[...Ze.map(ld),...gr.map(cd)]}function pd(){return[...od,...dd()]}function fd(e){if(e.length===0)return"（未选择任何技能）";const t=e.filter(r=>r.category==="inline"),n=e.filter(r=>r.category==="block"),i=[];if(t.length>0&&i.push(`### 行内强调语法

`+t.map(r=>`- ${r.name}：${r.description}`).join(`
`)),n.length>0){const r=new Map;for(const l of n){const d=r.get(l.tag)??[];d.push(l),r.set(l.tag,d)}const o=[];let a=1;for(const[l,d]of r){const c=d.map(p=>p.name).join(" / ");o.push(`### ${a}. <${l}> ${c}

${d.map(p=>p.description).join(`

`)}`),a++}i.push(`### 块级组件

`+o.join(`

---

`))}return i.join(`

`)}function gd(e){switch(e){case"article":return["你是一位专业的长图文内容策划与排版助手。","请阅读用户提供的 Markdown 内容，使用下方「可用排版技能」为其匹配最适合的行内标识和块级组件，增强文章的视觉表现力和可读性。","","## 输出要求","1. 只输出增强后的 Markdown 正文，不要有任何额外解释。","2. 不要发明新标签或新属性，只使用「可用排版技能」中列出的语法。","3. 合理搭配组件：开头可用 <title> 或 <breaking>，结尾推荐 <engage>，正文穿插 <steps>、<timeline> 等增强可读性。","4. <statement> 仅在高度总结的核心金句时克制使用。","5. 保留原文的信息和结构，不要删减或改变核心内容。","6. 代码块必须标注语言（如 ```javascript）。"].join(`
`);case"document":return["你是一位专业的 A4 正式文档编辑与排版助手。","请阅读用户提供的 Markdown 内容，优化其结构和排版，使其适合打印、归档和评审。","","## 输出要求","1. 只输出增强后的 Markdown 正文，不要有任何额外解释。","2. 保持文档正式、严谨、适合打印和归档的风格。","3. 在需要分页处插入 <page-break />。附录前必须分页。","4. 不要使用社交互动组件（如 <breaking>、<timeline>、<engage> 等）。","5. 保留原文的信息和结构，不要删减核心内容。"].join(`
`);case"card":return["你是一位专业的小红书图文卡片内容策划助手。","请阅读用户提供的 Markdown 内容，将其优化为适合小红书发布的分页图文卡片稿。","","## 输出要求","1. 只输出增强后的 Markdown 正文，不要有任何额外解释。","2. 使用短段落、清晰小标题和列表，每张图只承载一个重点。","3. 不要使用长图文模式的复杂社交组件。","4. 需要强调时使用 ==重点==、!!标签!!、^^强强调^^ 等行内语法。","5. 保留原文的核心信息。"].join(`
`);case"html":return["你是一位专业的 HTML 内容排版助手。","请阅读用户提供的内容，使用下方「可用排版技能」为其进行排版增强。","","## 输出要求","1. 只输出增强后的内容，不要有任何额外解释。","2. 只使用「可用排版技能」中列出的语法。","3. 保留原文的核心信息。"].join(`
`)}}function xd(e){const t=me.getState();switch(e){case"article":return t.setArticleMarkdown;case"document":return t.setDocumentMarkdown;case"card":return t.setCardMarkdown;case"html":return t.setHtml}}const ud={article:"长图文",document:"A4 文档",card:"小红书卡片",html:"HTML"},md=pd(),hd=fd(md);function bd(e,t,n){const i=J(M=>M.aiConfig),r=J(M=>M.colors),o=J(M=>M.themeTokens),[a,l]=v.useState(!1),[d,c]=v.useState(""),[p,f]=v.useState(""),g=v.useRef(null),x=me(M=>{switch(e){case"article":return M.articleMarkdown;case"document":return M.documentMarkdown;case"card":return M.cardMarkdown;case"html":return M.html}}),h=xd(e),[u,y]=v.useState([]),[j,w]=v.useState([]),[N,b]=v.useState("rendered"),[$,S]=v.useState(""),C=v.useCallback(()=>{if(u.length===0)return;const M=u[u.length-1];y(q=>q.slice(0,-1)),w(q=>[x,...q]),h(M),t("已撤销")},[u,x,h,t]),O=v.useCallback(()=>{if(j.length===0)return;const M=j[0];w(q=>q.slice(1)),y(q=>[...q,x]),h(M),t("已重做")},[j,x,h,t]),F=v.useCallback(async()=>{var re;const M={apiUrl:i.apiUrl,apiKey:i.apiKey,model:i.model};if(!Xn(M)){f("请先在「设置 → AI 配置」中配置 API 地址（本地 API 可留空 Key）");return}if(!x.trim()){f("编辑器内容为空，请先输入内容");return}S(x),(re=g.current)==null||re.abort(),l(!0),c(""),f("");const q=new AbortController;g.current=q;const H=`${gd(e)}

## 可用排版技能

${hd}`;try{await nd(M,[{role:"system",content:H},{role:"user",content:`以下是我的${ud[e]}内容，请使用排版技能对其进行增强排版。

【重要】输出格式要求：
- 如果原文已有 # 标题，则保留原标题，不要重新生成；如果原文没有标题，则在第一行用 # 生成一个简洁有力的标题（不超过20字）
- 在标题之后、正文之前，用 > 引用块 生成一句话摘要（提炼核心观点，不超过50字）
- 摘要之后为正文内容

以下是原文：

${x}`}],de=>c(T=>T+de),q.signal)}catch(de){de.name!=="AbortError"&&f(de.message||"AI 调用失败")}finally{g.current===q&&(l(!1),g.current=null)}},[i,x,e]);v.useEffect(()=>()=>{var M;(M=g.current)==null||M.abort()},[]);const Q=v.useCallback(()=>{var M;(M=g.current)==null||M.abort()},[]),W=v.useRef(!1);v.useEffect(()=>{n&&!W.current&&(W.current=!0,F())},[n,F]);const Z=v.useCallback(()=>{if(!d.trim())return;let M=d.trim();const q=M.match(/^```(?:markdown|md|html)?\s*\n([\s\S]*?)\n```\s*$/i);q&&(M=q[1].trim()),y(ce=>[...ce.slice(-49),x]),w([]),h(M),c(""),t("已应用 AI 排版结果")},[d,h,t,x]),I=v.useCallback(()=>{c(""),f("")},[]),L=d.trim().length>0,B=Xn(i),G=u.length>0,se=j.length>0;return{isRunning:a,streamingResult:d,error:p,pastStack:u,futureStack:j,previewMode:N,setPreviewMode:b,beforeContent:$,setBeforeContent:S,colors:r,themeTokens:o,handleRun:F,handleStop:Q,handleUndo:C,handleRedo:O,handleApply:Z,handleDiscard:I,hasResult:L,configReady:B,canUndo:G,canRedo:se}}function xt(e){return e.replace(/<[^>]+>/g,"").replace(/!\[[^\]]*]\([^)]+\)/g,"").replace(/\[([^\]]+)]\([^)]+\)/g,"$1").replace(/[=^!~`*_#>|-]/g,"").replace(/::/g,"").replace(/\s+/g," ").trim()}function yd(e){var r;const t=e.split(`
`);if(((r=t[0])==null?void 0:r.trim())!=="---")return null;const n={};let i=1;for(;i<t.length&&t[i].trim()!=="---";){const o=t[i].match(/^([\w-]+):\s*(.*)$/);o&&(n[o[1]]=o[2].trim()),i+=1}return i>=t.length?null:{meta:n,body:t.slice(i+1).join(`
`).trimStart()}}function vd(e){const t=e.match(/^#\s+(.+)$/m);return t?xt(t[1]):""}function wd(e){for(const t of e.split(`
`)){const n=t.trim();if(!n||/^(---+|#{1,6}\s|>|[-*+]\s|\d+\.\s|```|<[^>]+>|\|)/.test(n))continue;const i=xt(n);if(i)return i}return""}function kd(e){const t=yd(e),n=(t==null?void 0:t.body)??e,i=(t==null?void 0:t.meta)??{},r=n.match(/<title\b([^>]*)>([\s\S]*?)<\/title>/),o=n.match(/<lead\b[^>]*>([\s\S]*?)<\/lead>/),a=xt(i.title||i.name||(r?r[2]:"")||vd(n)),l=xt(i.summary||i.subtitle||i.description||(o?o[1]:"")||wd(n));return{title:a,summary:l,contentMarkdown:n}}const $d=new Set(["script","noscript","template"]),We=new Set(["href","src","srcset","poster","data","action","formaction"]),Sd=new Set(["http:","https:","mailto:","tel:"]),jd=new Set(["javascript:","vbscript:","jscript:","livescript:","view-source:","filesystem:","mocha:"]),Nd=new Set(["image/","font/","application/json","text/plain","application/pdf"]),Ed=new Set(["allow-forms","allow-pointer-lock","allow-popups","allow-popups-to-escape-sandbox","allow-scripts","allow-downloads","allow-top-navigation-by-user-activation"]),_d=new Set(["stylesheet","icon","preload","preconnect","dns-prefetch"]),Ut="http://www.w3.org/2000/svg",Cd=/[\u0000-\u001F\s]/g,at=new RegExp(["<(?:script|iframe|object|embed|meta|base|link|style|noscript|template|animate|set|animatetransform)\\b","[^\\w]on[a-z]{2,}\\s*=","(?:javascript|vbscript|jscript|livescript|mocha|view-source):","data:(?:text/html|application)","(?:expression\\s*\\(|behavior\\s*:|-moz-binding|@import)",`target\\s*=\\s*["']?_blank`,"&#[xX]?[0-9a-fA-F]+(?![;0-9a-fA-F])","&(?:amp|lt|gt|quot|AMP|LT|GT|QUOT)(?![;a-zA-Z0-9])"].join("|"),"i"),Td={amp:"&",AMP:"&",lt:"<",LT:"<",gt:">",GT:">",quot:'"',QUOT:'"',apos:"'",colon:":",Colon:":",semi:";",Semi:";",sol:"/",Sol:"/",bsol:"\\",num:"#",percnt:"%",excl:"!",Excl:"!",equals:"=",Equals:"=",quest:"?",period:".",comma:",",plus:"+",Plus:"+",lpar:"(",rpar:")",lbrack:"[",rbrack:"]",lcub:"{",rcub:"}",lowbar:"_",grave:"`",ast:"*",Tab:"	",NewLine:`
`,newline:`
`};function zd(e){return e.includes("&")?e.replace(/&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z][a-zA-Z0-9]*);/g,(t,n)=>{if(n[0]==="#"){const i=n[1]==="x"||n[1]==="X"?parseInt(n.slice(2),16):parseInt(n.slice(1),10);return Number.isFinite(i)&&i>0&&i<128?String.fromCharCode(i):" "}return Td[n]??t}):e}function Ad(e){const t=e.replace(/[\t\n\r\f\v]/g,"");if(at.test(e)||at.test(t))return!0;if(e.includes("&")){const n=zd(e);if(n!==e&&(at.test(n)||at.test(n.replace(/[\t\n\r\f\v]/g,""))))return!0}return!1}function Oe(e){return e.startsWith("on")&&e.length>2}function Le(e){return e.split(":").pop()||e}function fn(e){return e.replace(Cd,"")}function Md(e){const t=fn(e).toLowerCase();if(!t)return!1;for(const n of jd)if(t.startsWith(n))return!0;return!1}function Id(e){const t=fn(e).toLowerCase();if(!t.startsWith("data:"))return!1;const n=t.slice(5).split(";")[0];for(const i of Nd)if(n===i||n.startsWith(i))return!0;return!1}function Ne(e){if(!e)return!0;if(Md(e))return!1;const t=fn(e);if(!t||/^(\/|#|\.\.?\/)/.test(t)||Id(t))return!0;try{const n=new URL(t,"http://localhost");return Sd.has(n.protocol)}catch{return!/^[a-z][a-z0-9+.-]*:/i.test(t)}}function Ld(e){return e.split(",").every(t=>{const n=t.trim().split(/\s+/)[0];return!n||Ne(n)})}function Jn(e){let t=e;return t=t.replace(/expression\s*\(/gi,"__removed__("),t=t.replace(/behavior\s*:/gi,"__removed__:"),t=t.replace(/-moz-binding\s*:/gi,"__removed__:"),t=t.replace(/url\s*\(\s*['"]?\s*javascript:[^)]*['"]?\s*\)/gi,'url("")'),t=t.replace(/@import/gi,"@__removed__"),t}function Qn(e,t){const n=e.split(/\s+/).map(i=>i.trim()).filter(i=>i.length>0&&Ed.has(i)&&i!=="allow-same-origin");if(n.includes("allow-forms")||n.unshift("allow-forms"),t&&!n.includes("allow-scripts")&&n.push("allow-scripts"),!t){const i=n.indexOf("allow-scripts");i!==-1&&n.splice(i,1)}return n.join(" ").trim()}function Rd(e,t,n){const i=t.createElementNS(Ut,"svg");for(let r=0;r<e.attributes.length;r++){const o=e.attributes[r];if(!o)continue;const a=o.name;if(!Oe(a)&&!(We.has(Le(a).toLowerCase())&&!Ne(o.value)))try{i.setAttribute(a,o.value)}catch{}}return e.childNodes.forEach(r=>{if(r.nodeType!==Node.ELEMENT_NODE)return;const o=r,a=o.tagName.toLowerCase();if(a==="script"||a==="foreignobject")return;const l=De(o,t,n,!0);l&&i.appendChild(l)}),i}function De(e,t,n,i=!1){if(e.nodeType===Node.TEXT_NODE)return t.createTextNode(e.textContent||"");if(e.nodeType===Node.COMMENT_NODE)return t.createComment(e.nodeValue||"");if(e.nodeType!==Node.ELEMENT_NODE)return null;const r=e,o=r.tagName.toLowerCase();if($d.has(o)||n.strict&&(o==="iframe"||o==="object"||o==="embed"))return null;if(o==="object"||o==="embed"){const d=o==="object"?"data":"src",c=r.getAttribute(d)||"";if(!Ne(c))return null;const p=t.createElement("iframe");p.setAttribute("src",c);const f=r.getAttribute("title");return f&&p.setAttribute("title",f),p.setAttribute("sandbox",Qn("",n.allowScripts)),p}if(o==="iframe"){const d=t.createElement("iframe");let c="";for(let p=0;p<r.attributes.length;p++){const f=r.attributes[p];if(!f)continue;const g=f.name.toLowerCase();if(!Oe(g)){if(g==="sandbox"){c=f.value;continue}if(g==="srcdoc"){const x=gn(f.value,n);x&&d.setAttribute("srcdoc",x);continue}if(!(We.has(Le(g))&&!Ne(f.value)))try{d.setAttribute(g,f.value)}catch{}}}return d.setAttribute("sandbox",Qn(c,n.allowScripts)),d}if(o==="svg")return Rd(r,t,n);if(o==="style"){const d=Jn(r.textContent||"").replace(/</g,"\\3c ").replace(/>/g,"\\3e "),c=i?t.createElementNS(Ut,"style"):t.createElement("style");return c.textContent=d,c}if(i&&(o==="xmp"||o==="noembed"||o==="noframes"||o==="plaintext"))return null;if(o==="animate"||o==="set"||o==="animatetransform"){const d=(r.getAttribute("attributeName")||r.getAttribute("attributename")||"").toLowerCase(),c=Le(d);if(Oe(c)||We.has(c))return null}if(o==="meta"&&(r.getAttribute("http-equiv")||"").trim().toLowerCase()==="refresh"||o==="base")return null;if(o==="link"){const p=(r.getAttribute("rel")||"").toLowerCase().split(/\s+/).filter(Boolean).filter(x=>_d.has(x));if(p.length===0)return null;const f=r.getAttribute("href")||"";if(!Ne(f))return null;const g=t.createElement("link");for(let x=0;x<r.attributes.length;x++){const h=r.attributes[x];if(!h)continue;const u=h.name.toLowerCase();if(!Oe(u)){if(u==="rel"){g.setAttribute("rel",p.join(" "));continue}if(!(We.has(Le(u))&&!Ne(h.value)))try{g.setAttribute(u,h.value)}catch{}}}return g}const a=i?t.createElementNS(Ut,r.tagName):t.createElement(o);for(let d=0;d<r.attributes.length;d++){const c=r.attributes[d];if(!c)continue;const p=c.name,f=p.toLowerCase();if(Oe(f))continue;let g=c.value;if(We.has(Le(f))){if(Le(f)==="srcset"){if(!Ld(g))continue}else if(!Ne(g))continue}f==="style"&&(g=Jn(g));try{a.setAttribute(p,g)}catch{}}const l=(a.getAttribute("target")||"").toLowerCase();if((o==="a"||o==="area")&&(l==="_blank"||l==="_new")){const d=(a.getAttribute("rel")||"").toLowerCase().split(/\s+/).filter(Boolean),c=["noopener","noreferrer"];for(const p of c)d.includes(p)||d.push(p);a.setAttribute("rel",d.join(" "))}return r.childNodes.forEach(d=>{const c=De(d,t,n,i);c&&a.appendChild(c)}),a}function gn(e,t){if(!e)return"";const n=e.trim(),i=/^<!DOCTYPE\s+html/i.test(n)||/^<html[\s>]/i.test(n);if(!Ad(e))return e;const o=new DOMParser().parseFromString(e,"text/html");if(i){const d=o.createElement("html"),c=o.createElement("head"),p=o.createElement("body");o.head.childNodes.forEach(f=>{const g=De(f,o,t);g&&c.appendChild(g)}),o.body.childNodes.forEach(f=>{const g=De(f,o,t);g&&p.appendChild(g)});for(const f of[o.documentElement,o.body]){const g=f===o.documentElement?d:p;for(let x=0;x<f.attributes.length;x++){const h=f.attributes[x];if(!h)continue;const u=h.name.toLowerCase();if(!Oe(u))try{g.setAttribute(u,h.value)}catch{}}}return d.appendChild(c),d.appendChild(p),`<!DOCTYPE html>
`+d.outerHTML}const a=o.createDocumentFragment();o.head.childNodes.forEach(d=>{const c=De(d,o,t);c&&a.appendChild(c)}),o.body.childNodes.forEach(d=>{const c=De(d,o,t);c&&a.appendChild(c)});const l=o.createElement("div");return l.appendChild(a),l.innerHTML}function ut(e,t={}){return gn(e,{strict:!1,allowScripts:!!t.allowScripts})}function kf(e){return gn(e,{strict:!0,allowScripts:!1})}function ei(e,t,n,i,r){const o=kd(e),a=nr(o.contentMarkdown,t,void 0,n,i,r);return{meta:o,html:ut(a)}}function Od({streamingResult:e,isRunning:t,previewMode:n,beforeContent:i,colors:r,themeTokens:o,onApply:a,onDiscard:l,onRerun:d,onChangeMode:c}){const p=v.useMemo(()=>{if(!i)return null;try{return ei(i,r,void 0,void 0,o)}catch{return null}},[i,r,o]),f=v.useMemo(()=>{if(!e.trim())return null;let g=e.trim();const x=g.match(/^```(?:markdown|md|html)?\s*\n([\s\S]*?)\n```\s*$/i);x&&(g=x[1].trim());try{return ei(g,r,void 0,void 0,o)}catch{return null}},[e,r,o]);return s.jsxs("div",{className:"flex flex-col h-full",children:[s.jsxs("div",{className:"flex items-center justify-between shrink-0 px-3 py-2 border-b border-slate-100",children:[s.jsxs("span",{className:"text-[12px] font-semibold text-slate-700 flex items-center gap-1.5",children:[s.jsx(He,{size:13,className:"text-[var(--accent)]"}),le.aiTypeset.previewTitle,t&&s.jsx("span",{className:"text-[10px] text-slate-400 animate-pulse",children:"生成中…"})]}),s.jsxs("div",{className:"flex items-center gap-1",children:[s.jsx("button",{onClick:()=>c("rendered"),title:"渲染对比",className:`rounded p-1 transition-colors cursor-pointer ${n==="rendered"?"bg-[var(--accent)]/10 text-[var(--accent)]":"text-slate-400 hover:text-slate-600"}`,children:s.jsx(Oc,{size:14})}),s.jsx("button",{onClick:()=>c("raw"),title:"纯文本",className:`rounded p-1 transition-colors cursor-pointer ${n==="raw"?"bg-[var(--accent)]/10 text-[var(--accent)]":"text-slate-400 hover:text-slate-600"}`,children:s.jsx(cr,{size:14})})]})]}),n==="rendered"?s.jsx("div",{className:"flex-1 min-h-0 overflow-y-auto",children:s.jsxs("div",{className:"grid grid-cols-2 divide-x divide-slate-100",children:[s.jsxs("div",{children:[s.jsx("div",{className:"sticky top-0 z-[1] bg-slate-50/95 border-b border-slate-100 px-3 py-1 text-[10px] font-medium text-slate-400",children:"排版前"}),s.jsx("div",{className:"p-3 text-[12px] leading-relaxed text-slate-600",dangerouslySetInnerHTML:{__html:ut((p==null?void 0:p.html)??'<p class="text-slate-300">无内容</p>')}})]}),s.jsxs("div",{children:[s.jsx("div",{className:"sticky top-0 z-[1] bg-[var(--accent)]/5 border-b border-slate-100 px-3 py-1 text-[10px] font-medium text-[var(--accent)]",children:"排版后"}),s.jsx("div",{className:"p-3 text-[12px] leading-relaxed text-slate-600",dangerouslySetInnerHTML:{__html:ut((f==null?void 0:f.html)??'<p class="text-slate-300">渲染中…</p>')}})]})]})}):s.jsx("div",{className:"flex-1 min-h-0 overflow-y-auto p-3",children:s.jsx("pre",{className:"text-[11px] leading-relaxed text-slate-600 whitespace-pre-wrap break-words font-mono",children:e})}),!t&&s.jsxs("div",{className:"flex items-center gap-2 shrink-0 border-t border-slate-100 px-3 py-2",children:[s.jsxs("button",{onClick:a,className:"flex flex-1 items-center justify-center gap-1.5 rounded-md bg-[var(--accent)] py-1.5 text-[12px] font-semibold text-white hover:opacity-90 transition-opacity cursor-pointer",children:[s.jsx(Ic,{size:14})," ",le.aiTypeset.applyButton]}),s.jsxs("button",{onClick:d,className:"flex items-center gap-1 rounded-md border border-slate-200 px-2.5 py-1.5 text-[11px] font-medium text-slate-600 hover:bg-slate-50 transition-colors cursor-pointer",children:[s.jsx(ln,{size:12})," ",le.aiTypeset.retryButton]}),s.jsxs("button",{onClick:l,className:"flex items-center gap-1 rounded-md border border-slate-200 px-2.5 py-1.5 text-[11px] font-medium text-slate-600 hover:bg-slate-50 transition-colors cursor-pointer",children:[s.jsx(kt,{size:12})," 丢弃"]})]})]})}function Dd({isRunning:e,configReady:t,error:n,onRun:i,onStop:r}){return s.jsxs("div",{className:"flex flex-col items-center justify-center gap-3 p-4 overflow-y-auto h-full",children:[!t&&s.jsxs("div",{className:"w-full rounded-lg border border-amber-200 bg-amber-50 px-3 py-2.5 text-[12px] text-amber-700 flex items-start gap-2",children:[s.jsx(He,{size:14,className:"shrink-0 mt-0.5 text-amber-500"}),s.jsxs("span",{children:["请先在",s.jsx("strong",{children:"「设置 → AI 配置」"}),"中配置 API 地址后使用（本地 API 可留空 Key）。"]})]}),s.jsx("button",{onClick:e?r:i,className:`flex items-center justify-center gap-2 w-48 rounded-lg py-2.5 text-[13px] font-bold transition-all cursor-pointer ${e?"bg-red-50 text-red-600 border border-red-200 hover:bg-red-100":"bg-[var(--accent)] text-white shadow-sm hover:opacity-90"}`,children:e?s.jsxs(s.Fragment,{children:[s.jsx(kt,{size:15})," ",le.aiTypeset.stopButton]}):s.jsxs(s.Fragment,{children:[s.jsx(dn,{size:15})," ",le.aiTypeset.runButton]})}),n&&s.jsx("div",{className:"rounded-lg border border-red-100 bg-red-50 px-3 py-2 text-[12px] text-red-600",children:n})]})}function Pd({mode:e,onToast:t,onClose:n,autoRun:i}){const{fullscreen:r,toggleFullscreen:o}=Wc(),a=bd(e,t,i);return s.jsxs("div",{className:"flex h-full flex-col bg-white",children:[s.jsxs("div",{className:"flex shrink-0 items-center justify-between border-b border-slate-100 px-4 py-3",children:[s.jsxs("h3",{className:"flex items-center gap-1.5 text-sm font-bold text-slate-800",children:[s.jsx(He,{size:15,className:"text-[var(--accent)]"}),le.aiTypeset.title]}),s.jsxs("div",{className:"flex items-center gap-1",children:[s.jsx("button",{onClick:a.handleUndo,disabled:!a.canUndo,title:"撤销",className:`rounded p-1.5 transition-colors cursor-pointer ${a.canUndo?"text-slate-500 hover:bg-slate-100 hover:text-slate-700":"text-slate-200 cursor-not-allowed"}`,children:s.jsx(Lc,{size:15})}),s.jsx("button",{onClick:a.handleRedo,disabled:!a.canRedo,title:"重做",className:`rounded p-1.5 transition-colors cursor-pointer ${a.canRedo?"text-slate-500 hover:bg-slate-100 hover:text-slate-700":"text-slate-200 cursor-not-allowed"}`,children:s.jsx(Rc,{size:15})}),s.jsx("button",{onClick:o,title:r?"退出全屏":"全屏",className:"rounded p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition-colors cursor-pointer",children:r?s.jsx(Pc,{size:15}):s.jsx(Dc,{size:15})}),s.jsx("button",{onClick:n,className:"rounded p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition-colors cursor-pointer",children:s.jsx(kt,{size:16})})]})]}),s.jsx("div",{className:"flex-1 min-h-0 overflow-hidden",children:a.hasResult?s.jsx(Od,{streamingResult:a.streamingResult,isRunning:a.isRunning,previewMode:a.previewMode,beforeContent:a.beforeContent,colors:a.colors,themeTokens:a.themeTokens,onApply:a.handleApply,onDiscard:a.handleDiscard,onRerun:a.handleRun,onChangeMode:a.setPreviewMode}):s.jsx(Dd,{isRunning:a.isRunning,configReady:a.configReady,error:a.error,onRun:a.handleRun,onStop:a.handleStop})})]})}const Fd="fixed inset-0 z-[var(--dialog-z,50)] flex items-center justify-center bg-black/40 backdrop-blur-xs px-4",Bd="w-full max-w-lg rounded-xl border border-slate-100 bg-white p-6 shadow-2xl animate-in fade-in zoom-in-95 duration-200";function $t({isOpen:e,onClose:t,title:n,description:i,children:r,footer:o,closeOnOverlay:a=!0,zIndex:l=50,panelClassName:d,overlayClassName:c,ariaLabel:p}){const f=v.useRef(null);if(v.useEffect(()=>{e&&(f.current=document.activeElement)},[e]),v.useEffect(()=>{var u;if(!e)return;const x=document.querySelectorAll("[data-dialog] a, [data-dialog] button, [data-dialog] input, [data-dialog] textarea, [data-dialog] select");x.length>0?x[0].focus():(u=document.querySelector("[data-dialog]"))==null||u.focus();const h=y=>{if(y.key==="Escape"){t();return}if(y.key==="Tab"){const j=document.querySelectorAll("[data-dialog] a, [data-dialog] button, [data-dialog] input, [data-dialog] textarea, [data-dialog] select");if(j.length===0)return;const w=j[0],N=j[j.length-1];y.shiftKey?document.activeElement===w&&(y.preventDefault(),N.focus()):document.activeElement===N&&(y.preventDefault(),w.focus())}};return document.addEventListener("keydown",h),()=>document.removeEventListener("keydown",h)},[e,t]),v.useEffect(()=>{!e&&f.current instanceof HTMLElement&&f.current.focus()},[e]),!e)return null;const g=s.jsx("div",{className:c??Fd,style:{"--dialog-z":l},onClick:a?t:void 0,children:s.jsxs("div",{"data-dialog":!0,role:"dialog","aria-modal":"true","aria-label":n??p,tabIndex:-1,className:d??Bd,onClick:x=>x.stopPropagation(),children:[(n||i)&&s.jsxs("div",{className:"mb-4",children:[n&&s.jsx("h2",{className:"text-base font-bold text-slate-800",children:n}),i&&s.jsx("p",{className:"mt-1 text-sm text-slate-500",children:i})]}),n||i?s.jsx("div",{className:"flex flex-col gap-4",children:r}):r,o&&s.jsx("div",{className:"mt-5 flex items-center justify-end gap-2 border-t border-slate-100 pt-3",children:o})]})});return Wt.createPortal(g,document.body)}const P=(e,t,n,i="primary")=>({outputType:e,visualTone:t,family:n,displayLevel:i}),Hd={vercel:P("长页","极简","minimal-product"),stripe:P("长页","科技","fintech-product"),linear:P("长页","科技","product-tool"),apple:P("长页","极简","brand-story"),spotify:P("长页","编辑","media-entertainment"),editorial:P("长页","编辑","editorial-magazine"),terminal:P("长页","代码","developer-code"),xiaohongshu:P("卡片","温暖","social-card-custom"),notion:P("文档","温暖","knowledge-doc"),"xhs-multipage":P("卡片","温暖","social-card-multipage"),"ppt-slide":P("幻灯片","极简","presentation-basic","basic"),dashboard:P("仪表盘","数据","business-dashboard"),resume:P("文档","极简","resume-profile"),claude:P("长页","温暖","ai-assistant"),figma:P("长页","科技","design-tool"),airbnb:P("长页","温暖","consumer-brand"),supabase:P("长页","代码","developer-code"),raycast:P("长页","科技","system-tool"),mongodb:P("长页","科技","enterprise-data"),framer:P("长页","科技","site-builder"),github:P("长页","代码","developer-code"),openai:P("长页","极简","frontier-ai"),arc:P("长页","温暖","system-experience"),discord:P("长页","科技","community-chat"),tailwind:P("长页","极简","web-components"),report:P("报告","编辑","annual-report"),poster:P("卡片","编辑","poster-design"),"ai-console":P("仪表盘","科技","ai-console"),"blueprint-tech":P("长页","科技","blueprint-tech"),"keynote-cinematic":P("幻灯片","编辑","keynote-cinematic"),"consulting-deck":P("幻灯片","数据","consulting-deck"),"startup-pitch":P("幻灯片","温暖","startup-pitch"),"neon-tech-launch":P("幻灯片","科技","launch-event"),"growth-review":P("幻灯片","数据","growth-review"),"developer-conf":P("幻灯片","代码","developer-code"),"project-kickoff-rally":P("幻灯片","温暖","project-kickoff"),"roadmap-planning":P("幻灯片","数据","roadmap-planning"),"project-retro":P("幻灯片","数据","project-retro"),"annual-story-review":P("幻灯片","温暖","annual-story"),"proposal-lab":P("幻灯片","科技","proposal-lab"),"workshop-canvas":P("幻灯片","温暖","workshop-canvas"),"editorial-ink-deck":P("幻灯片","编辑","editorial-ink-deck"),"swiss-presentation-system":P("幻灯片","极简","swiss-presentation-system"),"swiss-grid":P("卡片","编辑","swiss-grid"),"bauhaus-composition":P("卡片","编辑","bauhaus-composition"),"newsroom-feature":P("长页","编辑","newsroom-feature"),"documentary-scroll":P("长页","编辑","documentary-scroll"),"data-command-center":P("仪表盘","数据","data-screen"),"data-journalism":P("报告","数据","data-journalism"),"academic-paper":P("文档","编辑","academic-paper"),"product-spec":P("文档","数据","product-spec"),"gov-doc":P("文档","编辑","gov-document")},Ud=[{id:"vercel",name:"极简黑白 · Vercel",category:"科技产品/极简工程",accent:"#000000",description:"黑白精确主义，大留白，无衬线极简体，锐利分割线",previewHtml:`<div style="font-family: Geist, Inter, sans-serif; background: #fff; padding: 18px; height: 100%; border: 1px solid #eaeaea; display: flex; flex-direction: column; box-sizing: border-box;">
    <div style="font-size: 17px; font-weight: 700; color: #000; margin-bottom: 6px; letter-spacing: -0.3px;">Vercel Deploy</div>
    <div style="font-size: 12px; color: #666; margin-bottom: auto; line-height: 1.45;">Push your code and deploy instantly.</div>
    <div style="border-top: 1px solid #eaeaea; padding-top: 12px; display: flex; gap: 8px;">
      <div style="background: #000; color: #fff; padding: 7px 14px; border-radius: 6px; font-size: 11px; font-weight: 500;">Deploy</div>
      <div style="border: 1px solid #eaeaea; color: #666; padding: 7px 14px; border-radius: 6px; font-size: 11px;">Cancel</div>
    </div>
  </div>`,style:`【视觉主题】黑白精确主义，极简工程师审美（参考 Vercel）
  【色彩系统】
   - 基础底色：纯白 #ffffff
   - 文本颜色：主标题纯黑 #000000，次要文本中灰 #666666
   - 分割线与边框：极浅灰 #eaeaea
   - 强调色：纯黑 #000000
  【排版规则】
   - 字体：无衬线体（优先 Geist / Inter）
   - 层级：标题字重 700 且字距收紧，正文字重 400 行高 1.6
  【组件特征】
   - 卡片：纯白背景，6px 极小圆角，1px #eaeaea 边框，**严格禁止使用阴影**。
   - 按钮：纯黑背景，纯白文字，6px 圆角。
  【布局原则】大量留白，元素间距严格对齐，视觉呈现绝对的冷静与精确。`},{id:"stripe",name:"紫色渐变 · Stripe",category:"科技产品/金融科技",accent:"#635bff",description:"标志性紫色渐变，轻盈优雅的细字重，斜切色块",previewHtml:`<div style="font-family: sans-serif; background: #fff; padding: 18px; height: 100%; border-radius: 12px; box-shadow: 0 4px 16px rgba(99,91,255,0.12); display: flex; flex-direction: column; box-sizing: border-box;">
    <div style="font-size: 18px; font-weight: 400; color: #30313d; margin-bottom: 6px;">Payment</div>
    <div style="font-size: 12px; color: #425466; margin-bottom: auto; line-height: 1.45;">Secure processing with Stripe.</div>
    <div style="background: linear-gradient(90deg, #635bff, #00d4ff); color: #fff; padding: 10px; border-radius: 12px; font-size: 12px; font-weight: 600; text-align: center;">Pay $120</div>
  </div>`,style:`【视觉主题】科技与优雅融合，顶级金融科技质感（参考 Stripe）
  【色彩系统】
   - 基础底色：纯白 #ffffff 或极浅灰
   - 文本颜色：深灰标题 #30313d，正文偏蓝灰 #425466
   - 强调色：标志性紫色 #635bff 及其渐变（蓝紫到青色）
  【排版规则】
   - 字体：现代无衬线体
   - 层级：标题常用细字重（300-500），显得轻盈高级，正文行距宽松
  【组件特征】
   - 卡片：白底，柔和大圆角（12-16px），伴有轻微且柔和的彩色投影（如 rgba(99, 91, 255, 0.1)）。
   - 按钮：紫色渐变或纯色填充，大圆角或完全胶囊形。
  【布局原则】常伴随斜切的背景色块或柔和的渐变光晕，具备极强的信任感与高级感。`},{id:"linear",name:"精密深色 · Linear",category:"科技产品/精密工具",accent:"#5e6ad2",description:"极简深色界面，精密网格，淡紫强调色，极致克制",previewHtml:`<div style="font-family: Inter, sans-serif; background: linear-gradient(180deg, #1c1c1f, #08090a); padding: 18px; height: 100%; border: 1px solid rgba(255,255,255,0.08); display: flex; flex-direction: column; box-sizing: border-box;">
    <div style="display: flex; align-items: center; gap: 6px; margin-bottom: 14px;">
      <div style="width: 11px; height: 11px; border-radius: 50%; border: 1.5px solid #5e6ad2;"></div>
      <div style="font-size: 12px; font-weight: 600; color: #f7f8f8;">LIN-128</div>
    </div>
    <div style="font-size: 17px; font-weight: 600; color: #f7f8f8; margin-bottom: 6px;">Update API</div>
    <div style="font-size: 12px; color: #8a8f98; line-height: 1.45; margin-bottom: auto;">Implement the new v2 endpoints.</div>
    <div style="display: flex; gap: 6px; margin-top: 14px;">
      <div style="background: rgba(94,106,210,0.2); color: #5e6ad2; font-size: 10px; padding: 3px 8px; border-radius: 12px; font-weight: 600;">In Progress</div>
      <div style="background: rgba(255,255,255,0.05); color: #8a8f98; font-size: 10px; padding: 3px 8px; border-radius: 12px;">Backend</div>
    </div>
  </div>`,style:`【视觉主题】精密深色界面，冷峻的工程师审美（参考 Linear）
  【色彩系统】
   - 基础底色：深色底（#08090a ~ #1c1c1f 渐变）
   - 文本颜色：高亮白 #f7f8f8 与次要灰 #8a8f98
   - 强调色：淡靛紫 #5e6ad2
   - 边框色：微亮边框 rgba(255,255,255,0.08)
  【排版规则】
   - 字体：紧凑无衬线体，字距略收紧
   - 层级：标题字重 600，高对比度
  【组件特征】
   - 卡片：半透明深色面板，1px 微亮边框，细腻低调的暗投影。
   - 按钮：极简深灰底色或淡紫底色。
  【布局原则】精密网格，所有元素边缘对齐极致严谨，极度克制。`},{id:"terminal",name:"开发者代码 · Terminal",category:"科技产品/开发极客",accent:"#00ff9c",description:"等宽字体、命令片段、API 示例和调试信息清晰排布",previewHtml:`<div style="font-family: Consolas, Monaco, monospace; background: #0f1115; color: #a9b1d6; padding: 16px; height: 100%; border: 1px solid rgba(255,255,255,0.08); display: flex; flex-direction: column; box-sizing: border-box;">
    <div style="display: flex; gap: 6px; margin-bottom: 12px;">
      <div style="width: 9px; height: 9px; border-radius: 50%; background: #ff5f56;"></div>
      <div style="width: 9px; height: 9px; border-radius: 50%; background: #ffbd2e;"></div>
      <div style="width: 9px; height: 9px; border-radius: 50%; background: #27c93f;"></div>
    </div>
    <div style="font-size: 13px; color: #00ff9c; margin-bottom: 6px;">$ npm run dev</div>
    <div style="font-size: 11px; color: #787c99; margin-bottom: auto; line-height: 1.5;">
      > markflow@1.0.0 dev<br>
      > vite --port 3000<br>
      <span style="color: #3b82f6;">➜</span> Local: http://127.0.0.1:3000
    </div>
    <div style="font-size: 10px; color: #565f89; border-top: 1px solid rgba(255,255,255,0.06); padding-top: 8px; text-align: right;">UTF-8</div>
  </div>`,style:`【视觉主题】开发者代码工作台，清晰、克制、可信
  【色彩系统】
   - 基础底色：深灰 #0f1115 或冷白 #f8fafc，按内容密度选择。
   - 文本颜色：主文本高对比，注释与辅助信息使用中性灰。
   - 强调色：青绿 #22c55e 或蓝 #38bdf8，仅用于命令提示、状态和关键参数。
  【排版规则】
   - 字体：代码、命令、参数使用 JetBrains Mono / Fira Code；说明文字使用现代无衬线体。
   - 层级：先给结论，再给命令、输出、解释，避免满屏代码。
  【组件特征】
   - 代码块：带标题、语言标签、行号或输出区，边框清晰，不使用发光装饰。
   - 信息块：API 请求、响应、环境变量、错误提示分别用稳定区块表达。
  【布局原则】适合 CLI 教程、SDK 说明、调试记录、技术方案附录；重点是可读和可复制。`},{id:"claude",name:"克制温和 · Claude",category:"科技产品/AI 助手",accent:"#d97757",description:"暖白底色，衬线标题搭配无衬线正文，温和克制的 AI 助手气质",previewHtml:`<div style="font-family: Georgia, serif; background: #faf9f8; padding: 16px; height: 100%; border: 1px solid #e5e5db; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box;">
    <div style="display: flex; gap: 10px; align-items: flex-start; margin-bottom: auto;">
      <div style="width: 24px; height: 24px; border-radius: 5px; background: #d97757; color: #fff; font-size: 14px; display: flex; align-items: center; justify-content: center; font-weight: bold; shrink: 0; font-family: sans-serif;">C</div>
      <div style="flex: 1;">
        <div style="font-size: 14px; font-weight: bold; color: #1a1a1a; font-family: Georgia, serif; margin-bottom: 6px;">Claude's Perspective</div>
        <div style="font-size: 11px; color: #444; line-height: 1.5; text-align: justify; font-family: sans-serif;">This layout prioritizes long-form readability, warm editorial spacing, and elegant serif typography.</div>
      </div>
    </div>
    <div style="display: flex; justify-content: space-between; align-items: center; border-top: 1px solid #e5e5db; padding-top: 10px; margin-top: 10px;">
      <span style="font-size: 10px; color: #8a8a80;">Model: Sonnet 3.5</span>
      <span style="font-size: 10px; background: #f0ede6; color: #6e6e65; padding: 2px 6px; border-radius: 4px;">1.4k tokens</span>
    </div>
  </div>`,style:`【视觉主题】克制、温和、富有书卷气的 AI 对话窗（参考 Claude）
  【色彩系统】
   - 基础底色：暖白 #fdfdfc 或 #faf9f8
   - 文本颜色：深灰偏暖 #2a2a2a
   - 强调色：古典陶土红/深橘 #d97757
  【排版规则】
   - 字体：标题优雅衬线体（Tiempos/宋体），正文无衬线。
  【组件特征】
   - 卡片：圆角适中（8-12px），淡淡的灰边框或阴影。
  【布局原则】无多余装饰，对话体阅读体验极佳。`},{id:"supabase",name:"开源极客 · Supabase",category:"科技产品/开源数据",accent:"#3ecf8e",description:"深灰背景，亮绿强调，等宽字体点缀，暗黑开源风",previewHtml:`<div style="font-family: Consolas, monospace; background: #1c1c1c; color: #ededed; padding: 16px; height: 100%; border: 1px solid #2e2e2e; border-radius: 8px; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box;">
    <div>
      <div style="display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #2e2e2e; padding-bottom: 8px; margin-bottom: 10px;">
        <span style="color: #3ecf8e; font-size: 13px; font-weight: bold;">⚡ supabase</span>
        <span style="color: #666; font-size: 10px;">active</span>
      </div>
      <div style="display: flex; flex-direction: column; gap: 6px;">
        <div style="font-size: 12px; color: #888;">SELECT * FROM profiles;</div>
        <div style="background: #242424; border: 1px solid #333; border-radius: 4px; padding: 8px; font-size: 11px; line-height: 1.6;">
          <span style="color: #3ecf8e;">id:</span> 1 &nbsp;
          <span style="color: #3ecf8e;">name:</span> "Antigravity"<br>
          <span style="color: #3ecf8e;">role:</span> "Developer"
        </div>
      </div>
    </div>
    <div style="font-size: 10px; color: #555; text-align: right;">PostgreSQL 15.1</div>
  </div>`,style:`【视觉主题】暗色极客，现代开源数据库（参考 Supabase）
  【色彩系统】
   - 基础底色：极深灰/近黑 #1c1c1c
   - 文本颜色：灰白 #ededed
   - 强调色：高亮翠绿 #3ecf8e
  【排版规则】
   - 字体：代码和数据大量穿插等宽字体。
  【组件特征】
   - 面板：1px 反光内描边，常带有微弱的绿色泛光。
  【布局原则】严谨的开发者文档式排版。`},{id:"raycast",name:"毛玻璃 · Raycast",category:"科技产品/系统工具",accent:"#ff6363",description:"深色悬浮窗，macOS 原生质感，搜索框驱动的命令面板",previewHtml:`<div style="font-family: sans-serif; background: #18181b; padding: 14px; height: 100%; border: 1px solid #27272a; border-radius: 10px; display: flex; flex-direction: column; justify-content: flex-start; gap: 8px; box-sizing: border-box; box-shadow: 0 10px 25px rgba(0,0,0,0.5);">
    <div style="background: #27272a; border-radius: 6px; padding: 8px 12px; display: flex; align-items: center; justify-content: space-between;">
      <span style="color: #d4d4d8; font-size: 12px;">Search commands...</span>
      <span style="background: #3f3f46; color: #a1a1aa; font-size: 10px; padding: 2px 6px; border-radius: 3px;">⌘ K</span>
    </div>
    <div style="display: flex; flex-direction: column; gap: 5px; margin-top: 4px;">
      <div style="background: rgba(255,99,99,0.12); border-radius: 4px; padding: 7px 10px; display: flex; align-items: center; justify-content: space-between;">
        <span style="color: #ff6363; font-size: 12px; font-weight: bold;">Create Snippet</span>
        <span style="color: #71717a; font-size: 10px;">Extension</span>
      </div>
      <div style="padding: 7px 10px; display: flex; align-items: center; justify-content: space-between;">
        <span style="color: #a1a1aa; font-size: 12px;">Clear Clipboard History</span>
        <span style="color: #71717a; font-size: 10px;">System</span>
      </div>
      <div style="padding: 7px 10px; display: flex; align-items: center; justify-content: space-between;">
        <span style="color: #a1a1aa; font-size: 12px;">Convert Image</span>
        <span style="color: #71717a; font-size: 10px;">Tools</span>
      </div>
    </div>
  </div>`,style:`【视觉主题】极致丝滑的 macOS 悬浮窗口（参考 Raycast）
  【色彩系统】
   - 基础底色：深色毛玻璃（rgba(0,0,0,0.5) 配合 blur(20px)）
   - 强调色：红色或高亮蓝。
  【排版规则】
   - 极简苹果原生系统字体，小字号，精细对齐。
  【组件特征】
   - 带有内反光（rgba(255,255,255,0.1)）的精美卡片。
   - 深色小标签高亮快捷键。
  【布局原则】列表排布密集且克制。`},{id:"mongodb",name:"企业数据 · MongoDB",category:"科技产品/企业数据",accent:"#00ed64",description:"深蓝底色，几何粗体，标志性亮绿点缀，企业级数据平台质感",previewHtml:`<div style="font-family: sans-serif; background: #001e2b; padding: 16px; height: 100%; border-radius: 8px; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box; border: 1px solid rgba(0,237,100,0.2);">
    <div>
      <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 14px;">
        <div style="width: 18px; height: 18px; border-radius: 4px; background: #00ed64; display: flex; align-items: center; justify-content: center; font-size: 12px;">🍃</div>
        <span style="color: #fff; font-size: 14px; font-weight: 800; letter-spacing: 0.5px;">MongoDB Atlas</span>
      </div>
      <div style="display: flex; flex-direction: column; gap: 6px; font-family: monospace; font-size: 12px;">
        <div style="color: #88a4bf;">db.users.find({ status: "active" })</div>
        <div style="color: #00ed64; padding-left: 10px;">➜ [ 128 documents found ]</div>
      </div>
    </div>
    <div style="display: flex; justify-content: space-between; align-items: center; border-top: 1px solid rgba(255,255,255,0.1); padding-top: 10px;">
      <span style="color: #88a4bf; font-size: 10px;">Cluster0.primary</span>
      <div style="width: 8px; height: 8px; border-radius: 50%; background: #00ed64; box-shadow: 0 0 8px #00ed64;"></div>
    </div>
  </div>`,style:`【视觉主题】稳重、强大的企业数据平台（参考 MongoDB）
  【色彩系统】
   - 基础底色：午夜深蓝 #001e2b 或 乳白底
   - 强调色：极其明亮的树叶绿 #00ed64
  【排版规则】
   - 字体：几何感极强的粗壮无衬线体。
  【组件特征】
   - 大块深色和浅色的截然对比，力量感强。
  【布局原则】大版面，块状分明。`},{id:"github",name:"开源协作 · GitHub",category:"科技产品/开源协作",accent:"#2f81f7",description:"经典浅色冷灰底，蓝色链接，代码原生的开源协作感",previewHtml:`<div style="font-family: sans-serif; background: #fff; padding: 16px; height: 100%; border: 1px solid #d0d7de; border-radius: 8px; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box;">
    <div>
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
        <span style="font-size: 13px; font-weight: bold; color: #0969da;">huanyu-a/MarkFlow</span>
        <span style="border: 1px solid #d0d7de; font-size: 10px; padding: 2px 8px; border-radius: 12px; color: #57606a;">Public</span>
      </div>
      <div style="font-size: 11px; color: #57606a; line-height: 1.4; margin-bottom: 12px;">A pure frontend, zero backend workspace to render and export Markdown files.</div>
    </div>
    <div style="display: flex; flex-wrap: wrap; gap: 4px; margin-bottom: 6px;">
      <div style="width: 13px; height: 13px; background: #ebedf0; border-radius: 2px;"></div>
      <div style="width: 13px; height: 13px; background: #9be9a8; border-radius: 2px;"></div>
      <div style="width: 13px; height: 13px; background: #40c463; border-radius: 2px;"></div>
      <div style="width: 13px; height: 13px; background: #30a14e; border-radius: 2px;"></div>
      <div style="width: 13px; height: 13px; background: #216e39; border-radius: 2px;"></div>
      <div style="width: 13px; height: 13px; background: #9be9a8; border-radius: 2px;"></div>
      <div style="width: 13px; height: 13px; background: #40c463; border-radius: 2px;"></div>
      <div style="width: 13px; height: 13px; background: #216e39; border-radius: 2px;"></div>
      <div style="width: 13px; height: 13px; background: #30a14e; border-radius: 2px;"></div>
      <div style="width: 13px; height: 13px; background: #ebedf0; border-radius: 2px;"></div>
      <div style="width: 13px; height: 13px; background: #9be9a8; border-radius: 2px;"></div>
      <div style="width: 13px; height: 13px; background: #40c463; border-radius: 2px;"></div>
    </div>
    <div style="font-size: 11px; color: #57606a;">142 commits this year</div>
  </div>`,style:`【视觉主题】冷色、逻辑性、代码原生的开源环境（参考 GitHub）
  【色彩系统】
   - 基础底色：纯白配浅灰区块，或深黑 #0d1117。
   - 边框色：冷灰色 #d0d7de 或 #30363d。
   - 强调色：克莱因蓝 #2f81f7
  【排版规则】
   - 系统级无衬线体，代码段落紧密镶嵌。
  【组件特征】
   - 大量浅色精细边框切分区域。按钮多为冷灰白底。
  【布局原则】极其规整的盒子模型。`},{id:"openai",name:"未来黑白 · OpenAI",category:"科技产品/前沿 AI",accent:"#10a37f",description:"纯黑白对比，优雅细体或宋体，微弱绿色点缀，未来极简",previewHtml:`<div style="font-family: sans-serif; background: #000; color: #fff; padding: 22px; height: 100%; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box;">
    <div style="font-size: 22px; font-weight: 300; letter-spacing: -0.6px; line-height: 1.15; margin-top: auto; margin-bottom: auto;">
      Introducing GPT-4o.<br>
      Our most advanced model.
    </div>
    <div style="display: flex; justify-content: space-between; align-items: flex-end; border-top: 1px solid #222; padding-top: 12px;">
      <span style="font-size: 11px; color: #888;">AI Frontier Research</span>
      <span style="font-size: 12px; color: #10a37f; font-weight: bold;">Learn more ➜</span>
    </div>
  </div>`,style:`【视觉主题】前沿科技、充满哲学与神秘感的极致极简（参考 OpenAI 官网）
  【色彩系统】
   - 基础底色：极致纯黑 #000000 或 纯白。
   - 强调色：极度节制的特有青绿 #10a37f
  【排版规则】
   - 优雅的中文字体（黑体或具有书籍感的衬线）。
  【组件特征】
   - 没有任何多余边框或阴影。
  【布局原则】大面积空旷带来的未来压迫感与高级感。`},{id:"tailwind",name:"现代实用 · Tailwind CSS",category:"科技产品/开发框架",accent:"#38bdf8",description:"系统级字体栈，柔和弥散阴影，蓝青主色，标准实用主义",previewHtml:`<div style="font-family: ui-sans-serif, system-ui; background: #f9fafb; padding: 18px; height: 100%; display: flex; flex-direction: column; justify-content: center; box-sizing: border-box;">
    <div style="background: #ffffff; border: 1px solid #e2e8f0; border-radius: 8px; padding: 16px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1);">
      <div style="font-size: 16px; font-weight: 600; color: #1e293b; margin-bottom: 6px;">Beautiful UI</div>
      <div style="font-size: 11px; color: #475569; margin-bottom: 14px; line-height: 1.4;">Built with utility classes.</div>
      <div style="background: #38bdf8; color: #fff; padding: 9px; border-radius: 6px; font-size: 11px; font-weight: 500; text-align: center;">Get Started</div>
    </div>
  </div>`,style:`【视觉主题】完美的实用主义现代网页标杆（参考 Tailwind UI）
  【色彩系统】
   - 基础底色：白 #ffffff 配 浅灰 #f9fafb。
   - 文本颜色：石板灰 #1e293b 到 #475569。
   - 强调色：天空蓝 #38bdf8 或 靛蓝 #6366f1。
  【排版规则】
   - 规整平衡的字体缩放。
  【组件特征】
   - 标志性的精美弥散阴影层级（shadow-md/xl）。
  【布局原则】标准、通用、无可挑剔的商业组件范式。`},{id:"ai-console",name:"智能控制台",category:"科技产品/智能控制台",accent:"#7c3aed",description:"AI 产品控制台，深浅混合界面，模型状态、任务流和提示词面板清晰分区",previewHtml:`<div style="font-family: sans-serif; background: #0f172a; padding: 14px; height: 100%; display: flex; gap: 10px; box-sizing: border-box; border: 1px solid #1e293b; border-radius: 8px;">
    <div style="width: 26%; border-right: 1px solid #1e293b; padding-right: 8px; display: flex; flex-direction: column; gap: 9px;">
      <div style="width: 18px; height: 18px; border-radius: 4px; background: #7c3aed;"></div>
      <div style="height: 5px; width: 100%; background: #1e293b; border-radius: 1px;"></div>
      <div style="height: 5px; width: 70%; background: #1e293b; border-radius: 1px;"></div>
      <div style="height: 5px; width: 85%; background: #1e293b; border-radius: 1px;"></div>
    </div>
    <div style="flex: 1; display: flex; flex-direction: column; justify-content: space-between;">
      <div>
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
          <span style="font-size: 12px; font-weight: bold; color: #cbd5e1;">Model Panel</span>
          <span style="font-size: 10px; color: #34d399;">● Online</span>
        </div>
        <div style="background: #1e293b; padding: 7px 9px; border-radius: 4px; font-size: 11px; color: #38bdf8; font-family: monospace;">Claude 3.5 Sonnet</div>
      </div>
      <div style="display: flex; justify-content: space-between; align-items: flex-end; height: 28px;">
        <div style="width: 14%; height: 30%; background: #7c3aed; border-radius: 1px;"></div>
        <div style="width: 14%; height: 60%; background: #7c3aed; border-radius: 1px;"></div>
        <div style="width: 14%; height: 45%; background: #7c3aed; border-radius: 1px;"></div>
        <div style="width: 14%; height: 80%; background: #7c3aed; border-radius: 1px;"></div>
        <div style="width: 14%; height: 95%; background: #7c3aed; border-radius: 1px;"></div>
      </div>
    </div>
  </div>`,style:`【视觉主题】面向 AI 产品的专业控制台，兼具实验室感与可操作性
  【色彩系统】
   - 基础底色：冷白 #f8fafc 或深灰 #0f172a；不要做单纯黑底。
   - 文本颜色：主文本 #0f172a / #f8fafc，次要文本 #64748b。
   - 强调色：紫色 #7c3aed 与青色 #06b6d4，用于状态、进度与关键按钮。
  【排版规则】
   - 字体：现代无衬线体，代码、模型名、参数值使用等宽字体。
   - 层级：顶部任务目标清晰，参数标签小而精确，结果区域保持高可读性。
  【组件特征】
   - 面板：左侧导航 / 中央工作流 / 右侧参数检查器三栏结构，边框细、圆角 10-12px。
   - 状态：使用小型状态点、进度条、token 计数、运行日志块，不要空白图表。
  【布局原则】适合 AI 工作台、模型评测、自动化流程页面；强调“可控、可审计、可复用”。`},{id:"blueprint-tech",name:"蓝图科技",category:"科技产品/蓝图架构",accent:"#2563eb",description:"工程蓝图风，细网格、结构线，系统架构与模块说明清晰直观",previewHtml:`<div style="font-family: sans-serif; background: #08111f; color: #22d3ee; padding: 16px; height: 100%; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box; border: 1px solid #112640; border-radius: 8px; position: relative; background-image: radial-gradient(rgba(37,99,235,0.18) 1px, transparent 1px); background-size: 12px 12px;">
    <div>
      <div style="display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid rgba(34,211,238,0.25); padding-bottom: 6px; margin-bottom: 12px;">
        <span style="font-size: 12px; font-weight: bold; letter-spacing: 1px;">ARCHITECT-v2</span>
        <span style="font-size: 10px; color: rgba(34,211,238,0.6);">GRID: 12px</span>
      </div>
      <div style="display: flex; gap: 8px; align-items: center; margin-top: 12px;">
        <div style="border: 1px solid #22d3ee; border-radius: 4px; padding: 6px 9px; font-size: 11px; font-family: monospace;">Module A</div>
        <span style="font-size: 12px; color: rgba(34,211,238,0.6);">━━▶</span>
        <div style="border: 1px dashed #22d3ee; border-radius: 4px; padding: 6px 9px; font-size: 11px; font-family: monospace;">Module B</div>
      </div>
    </div>
    <div style="font-size: 10px; color: rgba(34,211,238,0.6); text-align: right;">SYSTEM TOPOLOGY SCHEMA</div>
  </div>`,style:`【视觉主题】工程蓝图与系统架构说明，理性、清晰、技术可信
  【色彩系统】
   - 基础底色：深海军蓝 #08111f 或冷白 #f8fbff。
   - 网格线：rgba(37,99,235,0.12) 的细线，不要过密。
   - 强调色：科技蓝 #2563eb，辅助色青色 #22d3ee。
  【排版规则】
   - 字体：标题使用几何无衬线体，技术标签和编号使用等宽字体。
   - 层级：一级标题简短，模块标题用编号，正文说明控制在 2-3 行内。
  【组件特征】
   - 模块：架构卡、连接线、编号节点、接口表、流程箭头都用 CSS 边框和网格实现。
   - 图示：可用纯 HTML/CSS 绘制流程图和系统拓扑，禁止依赖图片占位。
  【布局原则】适合技术方案、产品架构、API 能力介绍；视觉像一张可交付的工程说明图。`}],qd=[{id:"apple",name:"高级留白 · Apple",category:"设计创意/品牌叙事",accent:"#0071e3",description:"SF Pro 风格，超大留白，居中叙事，电影感大标题",previewHtml:`<div style="font-family: -apple-system, BlinkMacSystemFont, sans-serif; background: #f5f5f7; padding: 24px; height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; box-sizing: border-box;">
    <div style="font-size: 22px; font-weight: 600; color: #1d1d1f; letter-spacing: -0.5px; margin-bottom: 4px;">Pro cameras.</div>
    <div style="font-size: 22px; font-weight: 600; color: #1d1d1f; letter-spacing: -0.5px; margin-bottom: 10px;">Pro display.</div>
    <div style="font-size: 12px; color: #86868b; margin-bottom: 16px;">The most advanced system yet.</div>
    <div style="background: #0071e3; color: #fff; padding: 7px 18px; border-radius: 14px; font-size: 12px; font-weight: 500;">Buy</div>
  </div>`,style:`【视觉主题】极简、高级、电影感叙事（参考 Apple 官网）
  【色彩系统】
   - 基础底色：纯净白底或极浅灰 #f5f5f7
   - 文本颜色：深黑 #1d1d1f，次要灰 #86868b
   - 强调色：苹果蓝 #0071e3
  【排版规则】
   - 字体：SF Pro 风格现代无衬线体
   - 层级：超大居中大标题（字重 600，字距紧凑），配小巧精致的副标题
  【组件特征】
   - 卡片：圆润边角（18px+），白色背景，近乎无边界或柔和微投影。
   - 按钮：经典蓝色胶囊形（全圆角）。
  【布局原则】超大垂直留白（120px+），居中对齐为主，产品图极其突出。`},{id:"figma",name:"创意工具 · Figma",category:"设计创意/设计工具",accent:"#0d99ff",description:"纯白底，纯黑字，鲜艳纯色点缀，粗边框与工具感面板",previewHtml:`<div style="font-family: sans-serif; background: #f5f5f5; border: 1px solid #e2e8f0; height: 100%; display: flex; box-sizing: border-box;">
    <div style="width: 38px; background: #fff; border-right: 1px solid #e2e8f0; padding: 10px 6px; display: flex; flex-direction: column; gap: 10px; align-items: center; justify-content: space-between;">
      <div style="display: flex; flex-direction: column; gap: 8px;">
        <div style="width: 20px; height: 20px; border-radius: 3px; background: #0d99ff; display: flex; justify-content: center; align-items: center; color: #fff; font-size: 11px; font-weight: bold;">⌘</div>
        <div style="width: 20px; height: 20px; border-radius: 3px; background: #f5f5f5; display: flex; justify-content: center; align-items: center; color: #555; font-size: 11px;">T</div>
      </div>
      <div style="width: 20px; height: 20px; border-radius: 50%; background: #f24e1e;"></div>
    </div>
    <div style="flex: 1; padding: 14px; display: flex; flex-direction: column; justify-content: space-between; position: relative;">
      <div style="border: 1px dashed #0d99ff; border-radius: 4px; padding: 10px; background: #fff; box-shadow: 0 2px 6px rgba(0,0,0,0.04);">
        <div style="font-size: 12px; font-weight: bold; color: #000; margin-bottom: 3px;">Card Frame</div>
        <div style="font-size: 10px; color: #666; line-height: 1.3;">Designing micro components...</div>
      </div>
      <div style="position: absolute; bottom: 16px; right: 24px; display: flex; gap: 4px; align-items: center;">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="#1abc9c" stroke="#1abc9c" style="transform: rotate(-45deg);"><polygon points="5 3 19 12 12 14 5 3"/></svg>
        <span style="background: #1abc9c; color: #fff; font-size: 10px; padding: 2px 6px; border-radius: 3px;">Alex</span>
      </div>
      <div style="font-size: 10px; color: #999;">Canvas: 80%</div>
    </div>
  </div>`,style:`【视觉主题】设计工具面板，极其紧凑且充满创造力（参考 Figma）
  【色彩系统】
   - 基础底色：纯白 #ffffff 与工具面板灰 #f5f5f5
   - 文本颜色：纯黑 #000000
   - 强调色：多色纯粹饱和（蓝 #0d99ff、粉 #f24e1e、绿 #1abc9c）
  【排版规则】
   - 字体：系统极简字体 Inter。字号整体偏小。
  【组件特征】
   - 面板：极细分割线，偶尔出现深色带有小尾巴的气泡提示（Tooltip）。
  【布局原则】UI 元素极度靠近，紧凑型网格。`},{id:"airbnb",name:"亲和旅行 · Airbnb",category:"设计创意/消费品牌",accent:"#ff385c",description:"圆润大字重，柔和投影，标志性粉红，以图为主",previewHtml:`<div style="font-family: sans-serif; background: #fff; border: 1px solid #e2e8f0; border-radius: 12px; height: 100%; display: flex; flex-direction: column; justify-content: space-between; overflow: hidden; box-sizing: border-box; padding-bottom: 14px;">
    <div style="height: 50%; background: linear-gradient(135deg, #ff385c, #ff5a5f); display: flex; justify-content: center; align-items: center; color: #fff; font-size: 28px; font-weight: bold; position: relative;">
      ✈️
      <div style="position: absolute; top: 10px; right: 10px; background: rgba(0,0,0,0.3); color: #fff; font-size: 10px; padding: 3px 8px; border-radius: 12px;">★ 4.95</div>
    </div>
    <div style="padding: 0 14px; margin-top: auto; display: flex; flex-direction: column; gap: 4px;">
      <div style="display: flex; justify-content: space-between; align-items: center;">
        <span style="font-size: 14px; font-weight: bold; color: #222;">京都 · 隐世木屋</span>
        <span style="font-size: 12px; font-weight: bold; color: #ff385c;">¥580 / 晚</span>
      </div>
      <div style="font-size: 11px; color: #717171; line-height: 1.4;">庭院樱花盛开，步行 5 分钟到地铁站。</div>
    </div>
  </div>`,style:`【视觉主题】温暖、友善的高质量消费界面（参考 Airbnb）
  【色彩系统】
   - 基础底色：纯净白
   - 文本颜色：深黑与中灰
   - 强调色：珊瑚粉红 #ff385c
  【排版规则】
   - 字体：现代圆润无衬线。
   - 标题使用极粗的字重（800），充满安全感和亲和力。
  【组件特征】
   - 卡片：无硬边框，极其宽泛弥散的柔和投影。
  【布局原则】注重超大图片的展示力。`},{id:"framer",name:"丝滑动效 · Framer",category:"设计创意/建站动效",accent:"#0055ff",description:"高对比度，柔和发光效果，精美卡片悬浮，现代建站工具审美",previewHtml:`<div style="font-family: sans-serif; background: #000; color: #fff; padding: 16px; height: 100%; border-radius: 10px; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box; position: relative; overflow: hidden; border: 1px solid #111;">
    <div style="position: absolute; top: -35px; left: -35px; width: 100px; height: 100px; background: radial-gradient(circle, rgba(0,85,255,0.45) 0%, rgba(0,0,0,0) 70%); pointer-events: none;"></div>
    <div style="display: flex; justify-content: space-between; align-items: center; z-index: 1;">
      <span style="font-size: 13px; font-weight: bold; color: #fff; letter-spacing: 0.5px;">⚡ Framer Pro</span>
      <span style="font-size: 10px; border: 1px solid #0055ff; color: #0055ff; padding: 2px 7px; border-radius: 12px; font-weight: bold;">PUBLISHED</span>
    </div>
    <div style="z-index: 1; margin: 12px 0 auto 0;">
      <div style="font-size: 20px; font-weight: 800; line-height: 1.1; margin-bottom: 6px; background: linear-gradient(90deg, #fff, #0055ff); -webkit-background-clip: text; -webkit-text-fill-color: transparent;">Build websites, faster.</div>
      <div style="font-size: 11px; color: #888; line-height: 1.4;">Design with canvas, publish with speed.</div>
    </div>
    <div style="display: flex; gap: 8px; z-index: 1;">
      <div style="background: #0055ff; color: #fff; font-size: 11px; padding: 6px 12px; border-radius: 6px; font-weight: 600;">Remix</div>
      <div style="background: #111; border: 1px solid #222; color: #aaa; font-size: 11px; padding: 6px 12px; border-radius: 6px;">Preview</div>
    </div>
  </div>`,style:`【视觉主题】顶级现代建站的视觉冲击力（参考 Framer 官网）
  【色彩系统】
   - 基础底色：纯净深黑或纯白。
   - 强调色：电光蓝 #0055ff，以及不规则的渐变色晕（radial-gradient）。
  【排版规则】
   - 字体：极具设计感的大字号无衬线。
  【组件特征】
   - 大圆角（24px+），卡片叠加或交织。
  【布局原则】自由但极度考究的留白比例。`},{id:"arc",name:"多彩卡片 · Arc Browser",category:"设计创意/系统体验",accent:"#ff8a8a",description:"侧边栏布局，柔和粉彩色调，极大圆角，半透明玻璃质感",previewHtml:`<div style="font-family: sans-serif; background: linear-gradient(135deg, #ffafbd 0%, #ffc3a0 100%); padding: 12px; height: 100%; border-radius: 12px; display: flex; gap: 10px; box-sizing: border-box;">
    <div style="width: 30%; background: rgba(255,255,255,0.28); backdrop-filter: blur(10px); border-radius: 8px; padding: 10px 6px; display: flex; flex-direction: column; gap: 8px; border: 1px solid rgba(255,255,255,0.25);">
      <div style="width: 16px; height: 16px; border-radius: 50%; background: rgba(255,255,255,0.5); margin-bottom: 6px;"></div>
      <div style="height: 4px; width: 85%; background: rgba(255,255,255,0.55); border-radius: 1px;"></div>
      <div style="height: 4px; width: 60%; background: rgba(255,255,255,0.55); border-radius: 1px;"></div>
      <div style="height: 4px; width: 70%; background: rgba(255,255,255,0.55); border-radius: 1px;"></div>
    </div>
    <div style="flex: 1; background: #fff; border-radius: 8px; padding: 12px; display: flex; flex-direction: column; justify-content: space-between; border: 1px solid rgba(255,255,255,0.4);">
      <div style="font-size: 13px; font-weight: bold; color: #333;">Arc Browser</div>
      <div style="font-size: 11px; color: #666; line-height: 1.4;">A new internet experience with smart vertical tabs.</div>
      <div style="font-size: 10px; color: #ff8a8a; text-align: right; font-weight: bold;">v1.2.0</div>
    </div>
  </div>`,style:`【视觉主题】多彩、透明、灵动现代的新型操作系统（参考 Arc 浏览器）
  【色彩系统】
   - 基础底色：低饱和度粉彩（淡紫、淡蓝、淡黄）与半透明。
  【排版规则】
   - 字体较小且精致。
  【组件特征】
   - 极其夸张的大圆角或完全胶囊状。带有玻璃质感与反光。
  【布局原则】卡片拼接排列（侧边与中央区块）。`},{id:"poster",name:"平面海报",category:"设计创意/平面海报",accent:"#ff3366",description:"硬核平面海报美学，Bento 网格布局，超大标题，视觉冲击力极强",previewHtml:`<div style="font-family: 'Arial Black', sans-serif; background: #ff3366; color: #000; padding: 16px; height: 100%; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box; border: 1px solid #d61845; position: relative;">
    <div style="position: absolute; bottom: 10px; right: 10px; width: 68px; height: 68px; background: #000; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: #ff3366; font-size: 13px; font-weight: 900; transform: rotate(-15deg); box-shadow: 0 4px 12px rgba(0,0,0,0.25); text-align: center; line-height: 1;">
      NEW<br>ART
    </div>
    <div>
      <div style="font-size: 30px; font-weight: 900; line-height: 0.9; letter-spacing: -1.5px;">TYPO<br>GRID<br>POSTER</div>
      <div style="font-size: 12px; font-weight: bold; background: #000; color: #ff3366; display: inline-block; padding: 3px 8px; margin-top: 8px; border-radius: 2px;">2026 EVENT</div>
    </div>
    <div style="font-family: sans-serif; font-size: 10px; line-height: 1.3; font-weight: bold; max-width: 55%;">
      BREAKING RULES OF MODERN LAYOUTS AND GRID SYSTEMS.
    </div>
  </div>`,style:`【视觉主题】极具表现力、打破常规的网页海报（Modern Poster）
  【色彩系统】
   - 基础底色：极高对比度的背景（纯黑底色，或极鲜艳纯色背景）。
   - 文本颜色：与背景形成极端反差。
  【排版规则】
   - 字体层级：主标题使用极具个性的超大展示字体（Display Font），甚至文字字距收紧、填满容器边缘。正文则配以规整微小的无衬线体，形成极端的字号大小对比。
   - 对齐：倾向于硬切分的网格对齐（CSS Bento Grid 或 Template Areas）或刻意的非对称不对齐。
  【组件特征】
   - 容器尺寸：如果是多张海报，每一张必须使用 \`<section class="page">\` 包装，并推荐赋予其明确的版面比例（如 \`.page{width:100%; aspect-ratio:3/4; overflow:hidden;}\`）。
   - 装饰元素：常用几何色块拼贴，以及利用粗细不一的纯色线条（border）切割版面空间。
  【布局原则】完全不同于普通网页，将屏幕视作一张实体画布，排版张力优先于常规阅读流。`},{id:"swiss-grid",previewHtml:`<div style="font-family: Helvetica, Inter, sans-serif; background: #ffffff; padding: 18px; height: 100%; border: 1px solid #111111; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box;">
    <div style="display: flex; gap: 12px; align-items: stretch; height: 100%;">
      <div style="flex: 1.2; display: flex; flex-direction: column; justify-content: space-between; height: 100%;">
        <div>
          <div style="width: 18px; height: 18px; background: #e11d48; margin-bottom: 14px;"></div>
          <div style="font-size: 21px; font-weight: 900; color: #111111; line-height: 1.05; letter-spacing: -0.8px;">SWISS DESIGN SYSTEM</div>
        </div>
        <div style="font-size: 10px; color: #e11d48; font-weight: bold; font-family: monospace;">ZÜRICH 2026</div>
      </div>
      <div style="flex: 0.8; border-left: 1px solid #111111; padding-left: 12px; display: flex; flex-direction: column; justify-content: space-between; height: 100%;">
        <div>
          <div style="font-size: 10px; font-weight: bold; color: #111111; text-transform: uppercase; margin-bottom: 8px; letter-spacing: 0.5px;">Order / Logic</div>
          <div style="font-size: 11px; color: #666666; line-height: 1.4;">A rigid grid layout provides visual cohesion across complex documents.</div>
        </div>
        <div style="font-size: 10px; color: #111111; font-weight: bold; text-align: right;">P.09</div>
      </div>
    </div>
  </div>`,name:"瑞士网格",category:"设计创意/网格排版",accent:"#e11d48",description:"瑞士国际主义平面设计，非对称网格、强字号对比、红黑白秩序感",style:`【视觉主题】瑞士国际主义平面设计，冷静、秩序、强网格
  【色彩系统】
   - 基础底色：纯白 #ffffff 或极浅灰 #f5f5f5。
   - 文本颜色：纯黑 #111111，辅助文字 #666666。
   - 强调色：瑞士红 #e11d48，必须节制使用。
  【排版规则】
   - 字体：Helvetica / Inter 风格无衬线体；标题可极大但必须贴合网格。
   - 对齐：严格使用 12 栏或 6 栏网格，允许非对称布局，但边界必须对齐。
  【组件特征】
   - 装饰：粗细对比的线条、编号、坐标、栏目标签；禁止柔和阴影和卡通插画。
   - 图片：如使用图片，必须矩形裁切并与网格线对齐。
  【布局原则】适合海报、展览介绍、品牌规范、课程封面；信息像海报一样有视觉张力。`},{id:"bauhaus-composition",previewHtml:`<div style="font-family: sans-serif; background: #f5f1e8; padding: 18px; height: 100%; border: 1.5px solid #111111; display: flex; flex-direction: column; justify-content: space-between; position: relative; overflow: hidden; box-sizing: border-box;">
    <div style="position: absolute; right: -12px; top: -12px; width: 76px; height: 76px; border-radius: 50%; background: #e11d48; opacity: 0.95; z-index: 1;"></div>
    <div style="position: absolute; right: 24px; top: 28px; width: 50px; height: 50px; border-radius: 50%; background: #2563eb; opacity: 0.85; z-index: 1;"></div>
    <div style="position: absolute; right: 12px; top: 70px; width: 62px; height: 12px; background: #facc15; z-index: 2;"></div>
    <div style="position: absolute; left: 0; right: 0; bottom: 42px; height: 5px; background: #111111; z-index: 1;"></div>

    <div style="z-index: 3; max-width: 68%;">
      <div style="font-size: 24px; font-weight: 900; color: #111111; line-height: 1.0; letter-spacing: -0.5px; text-transform: uppercase;">BAUHAUS</div>
      <div style="font-size: 13px; font-weight: 700; color: #111111; margin-top: 5px; letter-spacing: 1px;">构成设计</div>
    </div>

    <div style="z-index: 3; display: flex; justify-content: space-between; align-items: flex-end;">
      <div style="font-size: 10px; color: #111111; font-weight: bold; line-height: 1.2;">
        WEIMAR<br>DESAU
      </div>
      <div style="font-size: 18px; font-weight: 900; color: #111111;">1919</div>
    </div>
  </div>`,name:"包豪斯构成",category:"设计创意/几何构成",accent:"#facc15",description:"包豪斯几何构成，红黄蓝黑基础形，适合创意海报和展览页",style:`【视觉主题】包豪斯平面构成，几何、理性、色块鲜明
  【色彩系统】
   - 基础底色：米白 #f5f1e8 或纯白。
   - 主色：红 #e11d48、黄 #facc15、蓝 #2563eb、黑 #111111。
   - 颜色规则：一屏最多使用 3 个主色，避免彩虹化。
  【排版规则】
   - 字体：几何无衬线体，标题可使用强字重和垂直/横向排版。
   - 层级：用数字、短词、粗线和几何形建立阅读路径。
  【组件特征】
   - 装饰：圆形、半圆、矩形、粗线条和网格块必须服务布局，不要随机漂浮。
   - 容器：海报或多页卡片必须使用 \`<section class="page">\` 包裹并固定比例。
  【布局原则】适合艺术展、设计课程、品牌海报；视觉张力优先，但正文仍要可读。`}],Wd=[{id:"spotify",name:"暗色霓虹 · Spotify",category:"媒体内容/音乐娱乐",accent:"#1db954",description:"深黑底霓虹绿，超粗大标题，专辑封面式视觉，沉浸暗色",previewHtml:`<div style="font-family: Circular, sans-serif; background: linear-gradient(180deg, #333, #121212); padding: 16px; height: 100%; border-radius: 8px; display: flex; flex-direction: column; box-sizing: border-box;">
    <div style="width: 100%; aspect-ratio: 1; background: #282828; border-radius: 4px; margin-bottom: 12px; box-shadow: 0 8px 24px rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center;">
      <span style="font-size: 26px;">🎵</span>
    </div>
    <div style="font-size: 16px; font-weight: 800; color: #fff; margin-bottom: 4px; letter-spacing: -0.5px;">Daily Mix 1</div>
    <div style="font-size: 11px; color: #b3b3b3; line-height: 1.4;">Made for you</div>
    <div style="margin-top: auto; align-self: flex-end; width: 30px; height: 30px; border-radius: 50%; background: #1db954; display: flex; align-items: center; justify-content: center; color: #000; font-size: 14px; box-shadow: 0 4px 12px rgba(0,0,0,0.3);">▶</div>
  </div>`,style:`【视觉主题】暗色活力、音乐与情绪驱动（参考 Spotify）
  【色彩系统】
   - 基础底色：近黑 #121212 与深灰渐变
   - 文本颜色：纯白 #ffffff 与亮灰 #b3b3b3
   - 强调色：霓虹绿 #1db954
  【排版规则】
   - 字体：极粗大无衬线体
   - 层级：标题字重 800-900，视觉冲击极强
  【组件特征】
   - 卡片：深灰圆角面板，悬浮上浮互动，常配大图。
   - 按钮：鲜艳的绿色圆角或胶囊。
  【布局原则】卡片与图库排布紧凑，大面积暗色映衬彩色封面图，强调沉浸感。`},{id:"editorial",name:"杂志编辑 · WIRED",category:"媒体内容/杂志编辑",accent:"#1a1aff",description:"报刊高密度排版，粗衬线大标题，墨蓝强调色，印刷质感",previewHtml:`<div style="font-family: Georgia, serif; background: #fafafa; padding: 16px; height: 100%; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box; border-top: 4px solid #111111;">
    <div>
      <div style="display: flex; justify-content: space-between; align-items: baseline; border-bottom: 1px solid #111111; padding-bottom: 5px; margin-bottom: 10px;">
        <span style="font-size: 10px; font-weight: 900; color: #111111; letter-spacing: 2px;">FEATURE</span>
        <span style="font-size: 10px; color: #1a1aff; font-weight: bold;">TECH · ISSUE 42</span>
      </div>
      <div style="font-size: 19px; font-weight: 900; color: #111111; line-height: 1.1; margin-bottom: 6px; font-family: Georgia, serif; letter-spacing: -0.3px;">The Quiet Revolution of Front-End</div>
      <div style="font-size: 11px; color: #333333; line-height: 1.45; text-align: justify; font-family: Georgia, serif;">
        <span style="font-size: 20px; font-weight: 900; float: left; line-height: 0.9; margin: 2px 4px 0 0; color: #1a1aff;">A</span>s browsers evolve, a new generation of tools renders content without servers.
      </div>
    </div>
    <div style="border-top: 1px solid #111111; padding-top: 6px; font-size: 10px; color: #666; font-family: sans-serif; display: flex; justify-content: space-between;">
      <span>BY THE EDITORS</span>
      <span style="color: #1a1aff; font-weight: bold;">READ MORE →</span>
    </div>
  </div>`,style:`【视觉主题】科技杂志编辑风，印刷质感（参考 WIRED）
  【色彩系统】
   - 基础底色：纸白底 #fafafa
   - 文本颜色：纯黑正文 #111111，浅灰标注
   - 强调色：墨蓝或克莱因蓝 #1a1aff
  【排版规则】
   - 字体：大标题必须用极粗的现代衬线体（Georgia/Times 风格），正文使用衬线或清晰无衬线。
   - 层级：首字下沉，压紧的标题行距。
  【组件特征】
   - 装饰：利用粗细对比的实体横线（border-top/bottom）分隔小节。
   - 引用块：左侧竖线或极大引号标注。
  【布局原则】多栏排版，信息密度高，具备传统报刊的权威感。`},{id:"xiaohongshu",name:"社媒卡片 · 小红书",category:"媒体内容/社交卡片",accent:"#ff2e4d",description:"竖屏卡片，柔和渐变，大圆角，亲切手账感",previewHtml:`<div style="font-family: sans-serif; background: linear-gradient(160deg, #fff5f5 0%, #ffeef0 100%); padding: 18px; height: 100%; border-radius: 18px; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box; border: 1px solid #ffe3e6;">
    <div style="display: flex; align-items: center; gap: 6px;">
      <span style="font-size: 11px; background: #ff2e4d; color: #fff; padding: 3px 9px; border-radius: 12px; font-weight: 600;"># 好物推荐</span>
      <span style="font-size: 18px;">✨</span>
    </div>
    <div>
      <div style="font-size: 18px; font-weight: 700; color: #333333; line-height: 1.3; margin-bottom: 6px;">📝 我私藏的高效工具清单</div>
      <div style="font-size: 11px; color: #777777; line-height: 1.5;">这些小工具真的太提升幸福感啦，分享给姐妹们～ 💕</div>
    </div>
    <div style="display: flex; align-items: center; gap: 8px; font-size: 11px; color: #999999;">
      <span>❤️ 2.3w</span>
      <span>💬 826</span>
      <span style="margin-left: auto; color: #ff2e4d; font-weight: 600;">收藏 ⭐</span>
    </div>
  </div>`,style:`【视觉主题】亲切、可爱、手账感的种草卡片（参考 小红书）
  【色彩系统】
   - 基础底色：柔和的粉彩或奶油渐变（如浅黄到浅粉）
   - 文本颜色：深灰近黑 #333333，次要灰 #999999
   - 强调色：明快红粉 #ff2e4d
  【排版规则】
   - 字体：圆润活泼的无衬线体。
   - 层级：标题字重 700 且经常搭配 Emoji。
  【组件特征】
   - 卡片：大圆角面板（20-24px），柔和宽泛的背景投影。
   - 标签：彩色背景小圆角 Tag，用于圈出重点。
  【布局原则】非常适合竖向浏览，分点排版，重点极其突出。`},{id:"xhs-multipage",name:"小红书多页图文",category:"媒体内容/多页图文",accent:"#ff2e4d",description:"3:4 多页卡片：封面页 + N 张内容页",previewHtml:`<div style="font-family: sans-serif; background: #fffbeb; padding: 14px; height: 100%; border-radius: 12px; border: 1px solid #ffe3e6; display: flex; gap: 10px; box-sizing: border-box;">
    <div style="flex: 1; background: linear-gradient(135deg, #ff2e4d, #ff6b8b); border-radius: 8px; padding: 10px; display: flex; flex-direction: column; justify-content: space-between; color: #fff;">
      <div style="font-size: 11px; font-weight: bold;">COVER</div>
      <div style="font-size: 14px; font-weight: 900; line-height: 1.2;">小红书多页<br>排版秘籍</div>
      <div style="font-size: 10px; opacity: 0.85;">Page 1/3</div>
    </div>
    <div style="flex: 1; background: #fff; border-radius: 8px; padding: 10px; display: flex; flex-direction: column; justify-content: space-between; border: 1px solid #eaeaea;">
      <div style="font-size: 11px; font-weight: bold; color: #ff2e4d;">01/干货</div>
      <div style="font-size: 11px; color: #444; line-height: 1.4;">内容页展示：分步排版，左右滑动查看...</div>
      <div style="display: flex; justify-content: space-between; align-items: center; font-size: 10px; color: #999;">
        <span>Swipe ➜</span>
        <span>2/3</span>
      </div>
    </div>
  </div>`,style:`【视觉主题】小红书划动图文，多页独立卡片
  【色彩系统】
   - 封面底色：强视觉渐变或大图叠加
   - 内容底色：干净白底 #ffffff 辅以微小装饰
   - 强调色：主红 #ff2e4d
  【排版规则】
   - 字体：大字号无衬线加粗，强烈的情绪传递。
  【组件特征】
   - **强制分页容器**：每一张图必须使用 \`<section class="page">\` 包裹。
   - 页面尺寸：宽高固定比例 3:4（例如内部强制 height:100vh 或者 1440x1080 设定，需保证撑满一屏）。
  【布局原则】
   - 封面大标题居中 + 底部标签；
   - 内容页顶部小标题，中间内容，底部页码指示器。`},{id:"discord",name:"游戏连麦 · Discord",category:"媒体内容/社区聊天",accent:"#5865F2",description:"深灰偏紫底色，标志性蓝紫 Blurple，对话流排版，年轻社群感",previewHtml:`<div style="font-family: sans-serif; background: #2f3136; color: #fff; padding: 14px; height: 100%; display: flex; gap: 10px; box-sizing: border-box;">
    <div style="width: 26px; display: flex; flex-direction: column; gap: 8px; align-items: center; border-right: 1px solid rgba(255,255,255,0.06); padding-right: 6px;">
      <div style="width: 18px; height: 18px; border-radius: 50%; background: #5865F2; display: flex; align-items: center; justify-content: center; font-size: 10px; font-weight: bold;">D</div>
      <div style="width: 18px; height: 18px; border-radius: 50%; background: #3f4248;"></div>
      <div style="width: 18px; height: 18px; border-radius: 50%; background: #57F287;"></div>
    </div>
    <div style="flex: 1; display: flex; flex-direction: column; justify-content: space-between;">
      <div>
        <div style="font-size: 11px; color: #8e9297; margin-bottom: 8px;"># general-chat</div>
        <div style="display: flex; gap: 8px; align-items: flex-start;">
          <div style="width: 20px; height: 20px; border-radius: 50%; background: #ff73fa; shrink: 0;"></div>
          <div>
            <div style="font-size: 11px; font-weight: bold; color: #fff;">GamerX <span style="font-size: 10px; color: #72767d; font-weight: normal;">12:04</span></div>
            <div style="font-size: 11px; color: #dcddde; line-height: 1.4;">Let's host a tech meetup tonight!</div>
          </div>
        </div>
      </div>
      <div style="background: #40444b; border-radius: 5px; padding: 6px 10px; font-size: 11px; color: #72767d;">Message #general-chat</div>
    </div>
  </div>`,style:`【视觉主题】年轻化、社群驱动的深色游戏平台（参考 Discord）
  【色彩系统】
   - 基础底色：深灰偏紫 #36393f / #2f3136
   - 强调色：专有蓝紫 Blurple #5865f2
  【排版规则】
   - 紧凑的对话流排版模式。
  【组件特征】
   - 圆形头像搭配状态小圆点，悬浮带有暗沉背景高亮。
  【布局原则】信息高密度但不杂乱。`},{id:"newsroom-feature",previewHtml:`<div style="font-family: Georgia, serif; background: #fbfaf7; padding: 16px; height: 100%; border: 1.5px solid #111827; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box;">
    <div>
      <div style="text-align: center; border-bottom: 2px double #111827; padding-bottom: 6px; margin-bottom: 10px;">
        <span style="font-size: 13px; font-weight: 900; letter-spacing: 2px; color: #111827; text-transform: uppercase;">THE DAILY CHRONICLE</span>
      </div>
      <div style="font-size: 16px; font-weight: 900; color: #b91c1c; line-height: 1.2; margin-bottom: 8px; text-align: justify;">
        人工智能工作流席卷全球，零代码部署成为新常态
      </div>
      <div style="display: flex; gap: 10px;">
        <div style="flex: 1.2; font-size: 11px; color: #111827; line-height: 1.4; text-align: justify; font-family: serif;">
          <span style="font-size: 18px; font-weight: bold; float: left; line-height: 0.9; margin: 2px 3px 0 0;">本</span>报讯：随着前端容器化技术成熟，静态 HTML 导出能力已极大解放创作者的生产力。
        </div>
        <div style="flex: 0.8; border-left: 1px solid #cbd5e1; padding-left: 8px; display: flex; flex-direction: column; justify-content: space-between;">
          <div style="font-size: 10px; color: #6b7280; font-weight: bold;">【专题事实】</div>
          <div style="font-size: 10px; color: #111827; line-height: 1.3;">超 85% 企业表示将引入生成式提案流程。</div>
        </div>
      </div>
    </div>
    <div style="border-top: 1px solid #111827; padding-top: 6px; display: flex; justify-content: space-between; align-items: center; font-size: 10px; color: #6b7280;">
      <span>VOL. CLXVIII No. 42</span>
      <span>JUNE 2026</span>
    </div>
  </div>`,name:"新闻专题",category:"媒体内容/新闻专题",accent:"#b91c1c",description:"严肃新闻专题页，强标题、导语、事实框、时间线和引用证据",style:`【视觉主题】严肃新闻与深度专题报道，克制、可信、有现场感
  【色彩系统】
   - 基础底色：新闻纸白 #fbfaf7 或纯白。
   - 文本颜色：正文近黑 #111827，说明文字 #6b7280。
   - 强调色：深红 #b91c1c，仅用于栏目、关键事实和链接。
  【排版规则】
   - 字体：标题使用强衬线体，正文使用高可读衬线或清晰无衬线。
   - 层级：标题、导语、署名/日期、事实摘要、正文分节必须完整。
  【组件特征】
   - 信息块：事实框、关键数字、时间线、人物引用、资料来源列表。
   - 图片：新闻图必须带图注，图注字号小且靠近图片。
  【布局原则】适合深度文章、事件复盘、行业报道；不要营销化，不要过度装饰。`},{id:"documentary-scroll",previewHtml:`<div style="font-family: sans-serif; background: #0f0f0f; color: #f5f5f4; padding: 18px; height: 100%; border: 1px solid rgba(255,255,255,0.1); border-radius: 8px; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box; background-image: linear-gradient(180deg, #18130f 0%, #0f0f0f 100%);">
    <div style="display: flex; justify-content: space-between; align-items: center;">
      <span style="font-size: 11px; color: #a8a29e; letter-spacing: 1px; font-family: monospace;">SCENE 03 / THE SEARCHERS</span>
      <span style="font-size: 11px; color: #f97316; font-weight: bold;">REC ●</span>
    </div>
    <div style="margin: auto 0; text-align: center;">
      <div style="font-size: 17px; font-weight: 300; color: #f5f5f4; letter-spacing: 1px; line-height: 1.4; margin-bottom: 10px;">
        “我们寻找的，往往是那些被时间遗忘的声音。”
      </div>
      <div style="display: inline-block; background: rgba(250,204,21,0.15); color: #facc15; font-size: 11px; padding: 4px 10px; border-radius: 4px; border: 1px solid rgba(250,204,21,0.35); font-family: serif; font-style: italic;">
        —— 纪录片《回响》· 镜头 14
      </div>
    </div>
    <div style="display: flex; justify-content: space-between; align-items: center; border-top: 1px solid rgba(255,255,255,0.1); padding-top: 8px; font-size: 10px; color: #a8a29e; font-family: monospace;">
      <span>TC 10:14:32:08</span>
      <span>4K ULTRA HD</span>
    </div>
  </div>`,name:"纪录片叙事",category:"媒体内容/影像叙事",accent:"#f97316",description:"纪录片式滚动叙事，暗色剧照感、大段留白、章节镜头语言",style:`【视觉主题】纪录片式长页叙事，沉浸、克制、带镜头感
  【色彩系统】
   - 基础底色：炭黑 #0f0f0f 或暗棕黑 #18130f。
   - 文本颜色：主文本 #f5f5f4，次要文本 #a8a29e。
   - 强调色：暖橙 #f97316 或胶片黄 #facc15。
  【排版规则】
   - 字体：标题使用电影海报式大字号，正文行宽收窄。
   - 节奏：每个章节像一个镜头，使用短标题、导语、关键画面和旁白段落。
  【组件特征】
   - 媒体：大图或渐变背景可以 full-bleed，但必须保留文字安全区。
   - 卡片：少用普通卡片，多用字幕条、章节编号、片段引用、时间戳。
  【布局原则】适合品牌故事、人物专题、影像项目介绍；强调情绪和叙事推进。`}],Gd=[{id:"dashboard",name:"现代仪表盘",category:"数据分析/仪表盘",accent:"#3b82f6",description:"B 端现代数据面板，Bento 网格布局，清晰的信息层级与微交互",previewHtml:`<div style="font-family: sans-serif; background: #f8fafc; padding: 14px; height: 100%; display: flex; flex-direction: column; gap: 10px; box-sizing: border-box;">
    <div style="display: flex; justify-content: space-between; align-items: center;">
      <span style="font-size: 13px; font-weight: bold; color: #0f172a;">Business Panel</span>
      <span style="font-size: 10px; background: #e2e8f0; color: #475569; padding: 2px 7px; border-radius: 4px; font-weight: bold;">LIVE</span>
    </div>
    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 8px;">
      <div style="background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; padding: 10px; display: flex; flex-direction: column; gap: 4px;">
        <span style="font-size: 11px; color: #64748b;">Daily Revenue</span>
        <span style="font-size: 18px; font-weight: 800; color: #2563eb;">$12.4K</span>
      </div>
      <div style="background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; padding: 10px; display: flex; flex-direction: column; gap: 4px;">
        <span style="font-size: 11px; color: #64748b;">Conversion</span>
        <span style="font-size: 18px; font-weight: 800; color: #10b981;">3.42%</span>
      </div>
    </div>
    <div style="background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; padding: 10px; display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-top: auto;">
      <span style="font-size: 11px; color: #64748b;">Active Users</span>
      <div style="display: flex; gap: 3px; align-items: flex-end; height: 22px;">
        <div style="width: 4px; height: 10px; background: #2563eb; border-radius: 1px;"></div>
        <div style="width: 4px; height: 16px; background: #2563eb; border-radius: 1px;"></div>
        <div style="width: 4px; height: 8px; background: #2563eb; border-radius: 1px;"></div>
        <div style="width: 4px; height: 18px; background: #2563eb; border-radius: 1px;"></div>
        <div style="width: 4px; height: 22px; background: #2563eb; border-radius: 1px;"></div>
      </div>
    </div>
  </div>`,style:`【视觉主题】现代B端商业数据仪表盘（Data Dashboard）
  【色彩系统】
   - 基础底色：浅灰全局背景（如 #f8fafc）配以纯白数据卡片，营造呼吸感。
   - 文本颜色：信息层级分明，指标标题用次级灰（#64748b），核心数值用纯黑（#0f172a）。
   - 强调色：强语义色彩（绿 #10b981 代表上升，红 #ef4444 代表下降，蓝 #3b82f6 代表强调）。严禁滥用彩虹色。
  【排版规则】
   - 字体：极简无衬线体。数字必须使用等宽数字（font-variant-numeric: tabular-nums）以保证垂直对齐。
   - KPI展示：核心指标采用超大字号加粗，旁边必须配有带背景色的微小趋势标签（如 ↑ 12%）。
  【组件特征】
   - 卡片容器：利用 CSS Grid 或 Bento 网格系统，将图表与数据切割在带圆角（12px）和细腻阴影（shadow-sm/border）的白色卡片中。
   - 微型图表：用纯 CSS 或 HTML 元素绘制进度条、状态指示灯或极简的趋势柱状块，拒绝复杂的空白图表占位。
  【布局原则】"少即是多"（Less is more）。顶部展示核心数据卡片，下方展示详细图表或表格区域，整体高度对齐，减少用户的认知负荷。`},{id:"data-command-center",previewHtml:`<div style="font-family: Consolas, Monaco, monospace; background: #06111f; color: #e5f2ff; padding: 16px; height: 100%; border: 1px solid #06b6d4; border-radius: 8px; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box;">
    <div style="display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid rgba(6,182,212,0.35); padding-bottom: 8px; margin-bottom: 10px;">
      <div style="display: flex; align-items: center; gap: 6px;">
        <div style="width: 8px; height: 8px; background: #22c55e; border-radius: 50%;"></div>
        <span style="font-size: 11px; font-weight: bold; color: #06b6d4;">CORE DATACENTER</span>
      </div>
      <span style="font-size: 10px; color: rgba(229,242,255,0.6);">SYS_OK</span>
    </div>
    <div style="display: flex; gap: 8px; margin-bottom: auto;">
      <div style="flex: 1.2; background: #0f1b2d; padding: 10px; border-radius: 4px; border: 1px solid rgba(6,182,212,0.2);">
        <div style="font-size: 10px; color: #8aa4bf;">CPU USAGE</div>
        <div style="font-size: 20px; font-weight: bold; color: #06b6d4; margin-top: 3px;">42.8%</div>
        <div style="display: flex; gap: 3px; align-items: flex-end; height: 16px; margin-top: 6px;">
          <div style="height: 6px; flex: 1; background: #06b6d4; opacity: 0.3;"></div>
          <div style="height: 9px; flex: 1; background: #06b6d4; opacity: 0.5;"></div>
          <div style="height: 7px; flex: 1; background: #06b6d4; opacity: 0.4;"></div>
          <div style="height: 12px; flex: 1; background: #06b6d4; opacity: 0.8;"></div>
          <div style="height: 14px; flex: 1; background: #06b6d4;"></div>
        </div>
      </div>
      <div style="flex: 0.8; background: #0f1b2d; padding: 10px; border-radius: 4px; border: 1px solid rgba(6,182,212,0.2); display: flex; flex-direction: column; justify-content: space-between;">
        <div>
          <div style="font-size: 10px; color: #8aa4bf;">TASKS</div>
          <div style="font-size: 16px; font-weight: bold; color: #22c55e; margin-top: 3px;">1,248</div>
        </div>
        <div style="font-size: 10px; color: #f97316;">ERRORS: 0</div>
      </div>
    </div>
    <div style="font-size: 10px; color: rgba(229,242,255,0.5); border-top: 1px solid rgba(6,182,212,0.2); padding-top: 8px; display: flex; justify-content: space-between;">
      <span>LOAD: 0.42</span>
      <span>MEM: 12.4GB</span>
    </div>
  </div>`,name:"数据指挥舱",category:"数据分析/实时监控",accent:"#06b6d4",description:"深色实时数据大屏，指标卡片、网格背景、告警与趋势模块清晰分区",style:`【视觉主题】实时数据指挥舱，冷静、精密、态势感强
  【色彩系统】
   - 基础底色：深蓝黑 #06111f，面板 #0f1b2d。
   - 文本颜色：主文本 #e5f2ff，次要文本 #8aa4bf。
   - 强调色：青色 #06b6d4、绿色 #22c55e、告警橙 #f97316。
  【排版规则】
   - 字体：数字必须使用等宽数字；指标标签小而清楚。
   - 层级：核心 KPI 最大，趋势和告警次之，说明文字最弱。
  【组件特征】
   - 图表：用 CSS 绘制柱状条、折线近似、环形进度、状态灯和告警列表。
   - 面板：细描边、弱发光、网格背景；禁止复杂空白图表占位。
  【布局原则】适合运营监控、设备状态、业务大屏；所有数据模块必须有标题、单位和状态含义。`},{id:"data-journalism",previewHtml:`<div style="font-family: sans-serif; background: #fbfbf8; padding: 16px; height: 100%; border: 1px solid #cbd5e1; border-radius: 8px; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box;">
    <div>
      <div style="font-size: 10px; font-weight: bold; color: #0f766e; text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 6px;">DATA FOCUS / 深度解读</div>
      <div style="font-size: 15px; font-weight: 700; color: #1f2937; line-height: 1.3; margin-bottom: 10px;">主要前端渲染模式的耗时对比</div>
      <div style="display: flex; flex-direction: column; gap: 8px; margin-top: 8px;">
        <div>
          <div style="display: flex; justify-content: space-between; font-size: 11px; color: #4b5563; margin-bottom: 3px;">
            <span>静态 SSR 导出 (M2V)</span>
            <strong>24ms</strong>
          </div>
          <div style="height: 8px; background: #e2e8f0; border-radius: 4px; overflow: hidden;">
            <div style="width: 20%; height: 100%; background: #0f766e; border-radius: 4px;"></div>
          </div>
        </div>
        <div>
          <div style="display: flex; justify-content: space-between; font-size: 11px; color: #4b5563; margin-bottom: 3px;">
            <span>传统 Headless 截图</span>
            <strong>1,480ms</strong>
          </div>
          <div style="height: 8px; background: #e2e8f0; border-radius: 4px; overflow: hidden;">
            <div style="width: 90%; height: 100%; background: #d97706; border-radius: 4px;"></div>
          </div>
        </div>
      </div>
    </div>
    <div style="font-size: 10px; color: #6b7280; border-top: 1px solid #e2e8f0; padding-top: 8px; display: flex; justify-content: space-between;">
      <span>数据来源：W3C Performance API</span>
      <span style="color: #0f766e; font-weight: bold;">效率提升 60x+</span>
    </div>
  </div>`,name:"数据新闻",category:"数据分析/数据叙事",accent:"#0f766e",description:"数据新闻风，图表与解释并重，适合研究结论和公众传播",style:`【视觉主题】数据新闻与解释型可视化，理性但亲近读者
  【色彩系统】
   - 基础底色：柔和白 #fbfbf8，图表底色 #f3f4f0。
   - 文本颜色：正文 #1f2937，注释 #6b7280。
   - 强调色：墨绿 #0f766e，辅助色琥珀 #d97706。
  【排版规则】
   - 字体：正文高可读，图表标签字号清晰，不要为了密度牺牲可读性。
   - 层级：先给一句结论，再给图表，最后给解释和来源。
  【组件特征】
   - 图表：条形图、排名表、对比卡、注释标线用 HTML/CSS 实现。
   - 来源：每个关键数据区域必须预留“数据来源/口径说明”。
  【布局原则】适合调研报告、行业数据解读、年度盘点；用故事解释数据，而不是堆仪表盘。`}],Kd=[{id:"notion",name:"暖色极简 · Notion",category:"文档知识/知识文档",accent:"#0f0f0f",description:"暖白底色，衬线标题，柔和灰色区块，专注文档阅读体验",previewHtml:`<div style="font-family: -apple-system, sans-serif; background: #ffffff; padding: 18px 20px; height: 100%; display: flex; flex-direction: column; box-sizing: border-box;">
    <div style="font-family: Lyon-Text, Georgia, serif; font-size: 20px; font-weight: 700; color: #37352f; margin-bottom: 14px;">Project Spec</div>
    <div style="background: #f1f1ef; padding: 10px; border-radius: 4px; display: flex; gap: 8px; margin-bottom: 14px;">
      <span style="font-size: 14px;">💡</span>
      <div style="font-size: 11px; color: #37352f; line-height: 1.5;">This is an important callout block for key notes.</div>
    </div>
    <div style="font-size: 11px; color: #787774; line-height: 1.7;">Start writing your document here...</div>
    <div style="display: flex; align-items: center; gap: 6px; margin-top: auto; padding-top: 8px;">
      <div style="width: 14px; height: 2px; background: #37352f;"></div>
      <span style="font-size: 11px; color: #37352f; font-weight: 600;">Toggle list</span>
    </div>
  </div>`,style:`【视觉主题】专注阅读与书写的暖色极简文档（参考 Notion）
  【色彩系统】
   - 基础底色：暖白 #ffffff 或 #f7f6f3
   - 文本颜色：深灰近黑 #37352f，次要文本 #787774
   - 背景块：极其柔和的浅灰色块（如 #f1f1ef）
  【排版规则】
   - 字体：标题强制使用优雅衬线体，正文使用无衬线体。
   - 层级：正文行高舒适（1.6-1.7），段落间距明显。
  【组件特征】
   - Callout 卡片：带有一侧边框或浅底色，前缀常带一个大 Emoji。
   - 引用块：左侧细竖线。
  【布局原则】左对齐或居中定宽，无冗余装饰。`},{id:"resume",name:"简历 / 个人主页",category:"文档知识/个人简历",accent:"#0891b2",description:"单页简历，左右分栏，清晰层级，A4 打印友好",previewHtml:`<div style="font-family: sans-serif; background: #fff; border: 1px solid #e2e8f0; height: 100%; display: flex; box-sizing: border-box;">
    <div style="width: 32%; background: #f8fafc; border-right: 1px solid #e2e8f0; padding: 14px 10px; display: flex; flex-direction: column; gap: 10px; justify-content: space-between;">
      <div>
        <div style="width: 30px; height: 30px; border-radius: 50%; background: #0891b2; margin-bottom: 8px;"></div>
        <div style="font-size: 13px; font-weight: bold; color: #0f172a;">张明华</div>
        <div style="font-size: 10px; color: #64748b; margin-bottom: 10px;">前端工程师</div>
      </div>
      <div style="font-size: 10px; color: #64748b; line-height: 1.6;">
        📍 深圳<br>
        ✉️ hi@jming.me<br>
        💬 微信号：zhang_minghua
      </div>
    </div>
    <div style="flex: 1; padding: 14px; display: flex; flex-direction: column; gap: 10px; justify-content: space-between;">
      <div>
        <div style="font-size: 11px; font-weight: bold; color: #0891b2; border-bottom: 1px solid #e2e8f0; padding-bottom: 3px; margin-bottom: 6px;">工作经历</div>
        <div style="margin-bottom: 6px;">
          <div style="font-size: 11px; font-weight: bold; color: #0f172a;">深圳科技发展有限公司</div>
          <div style="font-size: 10px; color: #64748b;">高级前端 · 2024 - 至今</div>
        </div>
        <div style="font-size: 10px; color: #475569; line-height: 1.4;">主导重构核心排版引擎，包体积减少 40%。</div>
      </div>
      <div>
        <div style="font-size: 11px; font-weight: bold; color: #0891b2; border-bottom: 1px solid #e2e8f0; padding-bottom: 3px; margin-bottom: 6px;">开源项目</div>
        <div style="font-size: 10px; color: #475569; font-weight: bold;">MarkFlow ➜</div>
      </div>
    </div>
  </div>`,style:`【视觉主题】专业清晰的 A4 打印级简历
  【色彩系统】
   - 基础底色：纯白 #ffffff
   - 侧边栏色（可选）：浅灰 #f8fafc 或深青色
   - 强调色：商务青 #0891b2
  【排版规则】
   - 字体：现代无衬线体，阅读流畅。
   - 层级：经历时间线对齐，公司名加粗，职位名偏灰。
  【组件特征】
   - 容器：如果有多页请使用 \`<section class="page">\` 包裹，整体定宽 A4 比例。
   - 模块：时间轴节点（小圆点与左侧虚线）。
  【布局原则】典型的左右双栏结构，左窄右宽，高度紧凑，留白克制但绝不拥挤。`},{id:"report",name:"年度报告",category:"文档知识/年度报告",accent:"#2f4f4f",description:"现代数字年度报告，暖白底自然色调，衬线与无衬线混搭，适合数据叙事",previewHtml:`<div style="font-family: Georgia, serif; background: #fffaf5; padding: 18px; height: 100%; border: 1px solid #ebdcc5; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box;">
    <div>
      <div style="font-size: 10px; font-weight: 800; color: #2f4f4f; text-transform: uppercase; letter-spacing: 1.5px; margin-bottom: 6px;">Annual Report 2026</div>
      <div style="font-size: 19px; font-weight: 900; color: #2f4f4f; line-height: 1.15; margin-bottom: 8px; border-bottom: 2px solid #2f4f4f; padding-bottom: 8px;">Data-Driven<br>Digital Economy</div>
    </div>
    <div style="display: flex; align-items: baseline; gap: 10px;">
      <span style="font-size: 28px; font-weight: 900; color: #2f4f4f;">+84%</span>
      <span style="font-size: 11px; color: #6b6359; line-height: 1.4;">Year-over-Year User growth in APAC</span>
    </div>
    <div style="font-size: 10px; color: #8c8375; text-align: right; border-top: 1px solid #ebdcc5; padding-top: 8px;">PUBLISHED BY CONSULTING DEPT</div>
  </div>`,style:`【视觉主题】故事驱动的现代数字年度报告（Annual Report）
  【色彩系统】
   - 基础底色：暖调灰白/纸张色（如 #faf9f6 或 #f5f5f0），避免刺眼的纯白。
   - 文本颜色：深灰（非纯黑，如 #2c2c2c）以提供柔和的对比度。
   - 强调色：成熟、自然的色调（如森林绿、橄榄绿、大地色），用于重点数据和图表。
  【排版规则】
   - 字体：标题可混搭优雅的古典衬线体（传达权威感与人文感）与现代无衬线体。正文使用高可读性的无衬线体。
   - 留白：极其慷慨的段落间距和页边距，减轻视觉疲劳。
  【组件特征】
   - 核心数据：用醒目的大字号与强调色展示核心数字，取代密集的传统表格。
   - **强制分页容器**：如果是一份多页报告，每一页必须使用 \`<section class="page">\` 独立包裹，尺寸定宽（如 A4 或横屏幻灯片比例）。
  【布局原则】单页突出一个核心洞察，排版类似高端商业杂志，内容结构化且叙事清晰。`},{id:"academic-paper",previewHtml:`<div style="font-family: Georgia, serif; background: #ffffff; padding: 18px 20px; height: 100%; border: 1px solid #d1d5db; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box; text-align: justify;">
    <div>
      <div style="text-align: center; margin-bottom: 10px;">
        <div style="font-size: 14px; font-weight: bold; color: #111827; font-family: Georgia, serif; line-height: 1.25;">A Decoupled Client-Side Rendering Architecture</div>
        <div style="font-size: 10px; color: #374151; font-style: italic; margin-top: 3px;">Dr. Alan Turing, DeepMind Team</div>
      </div>
      <div style="border-top: 1px solid #111827; border-bottom: 1px solid #111827; padding: 8px 0; margin-bottom: 10px;">
        <div style="font-size: 10px; font-weight: bold; color: #111827; margin-bottom: 3px;">Abstract</div>
        <div style="font-size: 10px; color: #374151; line-height: 1.4; font-family: serif;">
          We present a novel, zero-backend rendering framework that operates entirely within user space sandboxes.
        </div>
      </div>
      <div style="font-size: 10px; color: #111827; line-height: 1.4; font-family: sans-serif;">
        <strong>1. Introduction</strong><br>
        Let D represent the sandboxed DOM tree. Traditional rendering introduces O(N) overhead...
      </div>
    </div>
    <div style="border-top: 0.5px solid #d1d5db; padding-top: 6px; display: flex; justify-content: space-between; align-items: center; font-size: 10px; color: #6b7280; font-family: sans-serif;">
      <span>arXiv:2606.12874 [cs.SE]</span>
      <span>Page 1 of 12</span>
    </div>
  </div>`,name:"学术论文",category:"文档知识/学术论文",accent:"#374151",description:"论文式阅读排版，摘要、章节、脚注、图表题注和参考文献清晰",style:`【视觉主题】学术论文与研究手稿，严谨、安静、长文友好
  【色彩系统】
   - 基础底色：纯白 #ffffff。
   - 文本颜色：正文 #111827，二级信息 #6b7280。
   - 强调色：中性灰 #374151 或深蓝 #1e3a8a，必须克制。
  【排版规则】
   - 字体：标题可用衬线体，正文使用高可读衬线或宋体风格。
   - 结构：标题、作者信息、摘要、关键词、章节、图表、参考文献必须层级明确。
  【组件特征】
   - 图表：题注必须靠近图表；表格使用细线和清晰表头。
   - 注释：脚注、引用编号、参考文献列表要整齐，不要做装饰卡片。
  【布局原则】适合研究摘要、论文预印本、学术报告；优先阅读和打印，不追求强视觉冲击。`},{id:"product-spec",previewHtml:`<div style="font-family: -apple-system, sans-serif; background: #ffffff; padding: 16px; height: 100%; border: 1px solid #cbd5e1; border-radius: 8px; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box;">
    <div>
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px;">
        <span style="font-size: 12px; font-weight: bold; color: #1e293b;">PRD-2026 / 导出引擎规范</span>
        <span style="background: rgba(37,99,235,0.12); color: #2563eb; font-size: 10px; padding: 3px 8px; border-radius: 4px; font-weight: bold;">DRAFT</span>
      </div>
      <div style="font-size: 15px; font-weight: 700; color: #0f172a; margin-bottom: 10px;">自由画布导出能力规格</div>
      <table style="width: 100%; border-collapse: collapse; margin-top: 6px; font-size: 11px;">
        <thead>
          <tr style="border-bottom: 1.5px solid #e2e8f0; text-align: left; color: #64748b;">
            <th style="padding: 4px 0;">需求描述</th>
            <th style="padding: 4px 0; text-align: center;">优先级</th>
          </tr>
        </thead>
        <tbody>
          <tr style="border-bottom: 1px solid #f1f5f9; color: #334155;">
            <td style="padding: 6px 0;">支持 PDF / PNG 一键导出</td>
            <td style="padding: 6px 0; text-align: center;"><span style="background: #fee2e2; color: #ef4444; padding: 2px 6px; border-radius: 3px; font-weight: bold;">P0</span></td>
          </tr>
          <tr style="border-bottom: 1px solid #f1f5f9; color: #334155;">
            <td style="padding: 6px 0;">保持 0 个 TypeScript 报错</td>
            <td style="padding: 6px 0; text-align: center;"><span style="background: #fef9c3; color: #ca8a04; padding: 2px 6px; border-radius: 3px; font-weight: bold;">P1</span></td>
          </tr>
        </tbody>
      </table>
    </div>
    <div style="font-size: 10px; color: #64748b; border-top: 1px solid #e2e8f0; padding-top: 8px; text-align: right;">最后修改人: Antigravity</div>
  </div>`,name:"产品规格书",category:"文档知识/产品文档",accent:"#2563eb",description:"PRD/规格书风格，目录、需求表、状态标签、流程和验收标准完整",style:`【视觉主题】产品规格书与 PRD，结构化、可评审、可执行
  【色彩系统】
   - 基础底色：白 #ffffff，辅助区块 #f8fafc。
   - 文本颜色：主文本 #111827，说明 #64748b。
   - 强调色：蓝 #2563eb，状态色绿/黄/红用于优先级和风险。
  【排版规则】
   - 字体：现代无衬线体；标题编号清楚，表格信息密度适中。
   - 层级：背景、目标、用户故事、需求列表、流程、验收标准必须分区。
  【组件特征】
   - 模块：需求表、优先级标签、流程步骤、状态徽标、风险提示和开放问题列表。
   - 文档容器：长文用定宽阅读区；多页输出可使用 \`<section class="page">\`。
  【布局原则】适合产品方案、技术规格、项目需求；重点是让团队一眼看懂范围、状态和下一步。`},{id:"gov-doc",name:"公文排版",category:"文档知识/公文文档",accent:"#c0202c",description:"符合 GB/T 9704-2012 标准的党政机关公文排版，红头文件、发文字号、密级标注、仿宋正文",previewHtml:`<div style="font-family: 'FangSong', 'STFangsong', serif; background: #ffffff; padding: 18px 20px; height: 100%; display: flex; flex-direction: column; box-sizing: border-box;">
  <div style="text-align: left; font-size: 11px; color: #c0202c; font-weight: bold; margin-bottom: 4px;">绝密★保密期限</div>
  <div style="text-align: center; font-size: 22px; font-weight: bold; color: #c0202c; font-family: 'STSong', 'SimSun', serif; letter-spacing: 4px; margin-bottom: 6px;">XX市人民政府办公厅</div>
  <div style="text-align: center; font-size: 12px; color: #000; margin-bottom: 4px;">市政发〔2026〕第1号</div>
  <div style="height: 3px; background: #c0202c; margin-bottom: 14px;"></div>
  <div style="text-align: center; font-size: 16px; font-weight: bold; color: #000; font-family: 'STSong', 'SimSun', serif; margin-bottom: 12px;">关于推进数字经济发展的通知</div>
  <div style="font-size: 11px; color: #000; line-height: 1.8; text-indent: 2em; text-align: justify;">各区人民政府，市政府各委、办、局：为深入贯彻数字经济发展战略，现就有关事项通知如下...</div>
  <div style="margin-top: auto; text-align: right; font-size: 11px; color: #000; padding-top: 10px;">XX市人民政府办公厅<br>2026年6月20日</div>
</div>`,style:`【视觉主题】符合 GB/T 9704-2012 标准的党政机关公文排版
  【色彩系统】
   - 基础底色：纯白 #ffffff
   - 红头颜色：公文红 #c0202c（发文机关名称与分隔线）
   - 正文颜色：纯黑 #000000
   - 密级标注：红色 #c0202c
  【排版规则】
   - 字体：发文机关名称用宋体加粗红色；正文使用仿宋 GB2312 / FangSong，三号字。
   - 红头：发文机关名称居中，字号较大，下方红色分隔线。
   - 发文字号：居中，置于红头下方、分隔线上方。
   - 密级与紧急程度：左上角顶格，红色加粗。
   - 标题：居中，二号宋体加粗。
   - 主送机关：左顶格，后跟全角冒号。
   - 正文：首行缩进两字，两端对齐，行距 28-30 磅。
   - 落款：发文机关署名与日期右对齐，距正文两行。
  【组件特征】
   - 使用 <gov-header> 标签渲染公文头部（红头 + 发文字号 + 密级 + 签发人）。
   - 公文头部独占首页顶部，正文紧随其后。
  【布局原则】严格遵循公文格式规范，适合打印、归档、正式发文。`}],Vd=[{id:"ppt-slide",name:"基础幻灯片",category:"演示汇报/基础幻灯",accent:"#2563eb",description:"通用 16:9 横版幻灯片，适合作为空白起点",previewHtml:`<div style="font-family: sans-serif; background: #002FA7; color: #fff; padding: 18px; height: 100%; display: flex; flex-direction: column; box-sizing: border-box; justify-content: space-between;">
    <div style="display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid rgba(255,255,255,0.2); padding-bottom: 8px;">
      <span style="font-size: 11px; font-weight: bold; letter-spacing: 1.5px;">PRESENTATION</span>
      <span style="font-size: 11px; opacity: 0.6;">01</span>
    </div>
    <div style="margin: auto 0;">
      <div style="font-size: 22px; font-weight: 700; line-height: 1.15; margin-bottom: 8px; letter-spacing: -0.3px;">构建纯前端渲染工作台</div>
      <div style="font-size: 12px; opacity: 0.75; line-height: 1.4;">An elegant way to export PDF and PNG files.</div>
    </div>
    <div style="display: flex; justify-content: space-between; align-items: flex-end; border-top: 1px solid rgba(255,255,255,0.2); padding-top: 8px;">
      <span style="font-size: 10px; opacity: 0.6;">Antigravity Design</span>
      <div style="display: flex; gap: 5px;">
        <div style="width: 6px; height: 6px; border-radius: 50%; background: #fff;"></div>
        <div style="width: 6px; height: 6px; border-radius: 50%; background: #fff; opacity: 0.35;"></div>
        <div style="width: 6px; height: 6px; border-radius: 50%; background: #fff; opacity: 0.35;"></div>
      </div>
    </div>
  </div>`,style:`【视觉主题】专业商务幻灯片，演示大屏展示
  【色彩系统】
   - 基础底色：深蓝商务底色或纯白底
   - 强调色：可信蓝 #2563eb
  【排版规则】
   - 字体：现代无衬线体，极大字号以保证远距离可读。
   - 层级：标题层级极度分明。
  【组件特征】
   - **强制分页容器**：每一页必须使用 \`<section class="slide">\` 包裹。
   - 页面尺寸：固定比例 16:9 横板。
  【布局原则】单页信息极少，大面积留白。封面居中，内容页分左右栏或上下结构。`},{id:"keynote-cinematic",name:"电影发布会",category:"演示汇报/发布会",accent:"#f59e0b",description:"Keynote 式大屏演示，深色舞台、超大标题、强节奏单页信息",previewHtml:`<div style="font-family: sans-serif; background: radial-gradient(circle at center, #1b2030 0%, #05070c 100%); color: #fff; padding: 20px; height: 100%; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box; text-align: center; align-items: center;">
    <div style="font-size: 12px; font-weight: bold; color: #f59e0b; letter-spacing: 2.5px; text-transform: uppercase;">SPECIAL EVENT</div>
    <div style="margin: auto 0;">
      <div style="font-size: 26px; font-weight: 700; line-height: 1.1; letter-spacing: -0.5px; margin-bottom: 8px;">ONE MORE THING.</div>
      <div style="font-size: 12px; color: #cbd5e1; font-weight: 300; line-height: 1.4;">The next generation of web publishing starts today.</div>
    </div>
    <div style="font-size: 10px; opacity: 0.55; letter-spacing: 1px;">LIVE FROM THE THEATER</div>
  </div>`,style:`【视觉主题】电影级产品发布会幻灯片，适合大屏演讲与发布稿
  【色彩系统】
   - 基础底色：深黑蓝 #05070c 或暗灰渐变。
   - 文本颜色：主标题近白 #f8fafc，说明文字 #cbd5e1。
   - 强调色：金色 #f59e0b 或电光蓝 #38bdf8，只用于关键词和页码。
  【排版规则】
   - 字体：超大标题，字重 700-800；正文少而有力，行高宽松。
   - 单页限制：每张 slide 只表达一个观点，最多 1 个主标题、1 个副标题、3 个要点。
  【组件特征】
   - **强制分页容器**：每一页必须使用 \`<section class="slide">\` 包裹，16:9 比例。
   - 视觉：允许大幅背景图、产品剪影、光束式渐变，但文字必须始终清晰。
  【布局原则】封面强冲击，内容页大留白，结尾页突出一句总结或行动口号。`},{id:"consulting-deck",name:"咨询汇报",category:"演示汇报/咨询顾问",accent:"#1d4ed8",description:"咨询公司式汇报页，结论先行、矩阵图、分栏和数据证据清楚",previewHtml:`<div style="font-family: sans-serif; background: #ffffff; padding: 16px; height: 100%; border: 1px solid #e2e8f0; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box;">
    <div>
      <div style="display: flex; justify-content: space-between; align-items: baseline; border-bottom: 2px solid #1d4ed8; padding-bottom: 6px; margin-bottom: 10px;">
        <span style="font-size: 13px; font-weight: bold; color: #0f172a;">2x2 Matrix Strategy</span>
        <span style="font-size: 10px; color: #64748b; font-weight: bold;">CONSULTING</span>
      </div>
      <div style="font-size: 11px; font-weight: bold; color: #1d4ed8; margin-bottom: 8px;">Market Attractiveness vs Competency</div>
      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 6px;">
        <div style="background: rgba(29,78,216,0.07); border: 1px solid rgba(29,78,216,0.2); padding: 8px; font-size: 11px; border-radius: 4px; font-weight: 600;">★ Stars</div>
        <div style="background: #f8fafc; border: 1px solid #e2e8f0; padding: 8px; font-size: 11px; border-radius: 4px;">? Question</div>
        <div style="background: #f8fafc; border: 1px solid #e2e8f0; padding: 8px; font-size: 11px; border-radius: 4px;">💵 Cash Cow</div>
        <div style="background: rgba(220,38,38,0.06); border: 1px solid rgba(220,38,38,0.15); padding: 8px; font-size: 11px; border-radius: 4px; color: #dc2626; font-weight: 600;">🚯 Dogs</div>
      </div>
    </div>
    <div style="font-size: 10px; color: #94a3b8; text-align: right;">Page 12 / Source: Industry Research</div>
  </div>`,style:`【视觉主题】咨询公司董事会汇报，结论先行、结构严谨、证据可追踪
  【色彩系统】
   - 基础底色：纯白 #ffffff，辅助背景 #f8fafc。
   - 文本颜色：标题 #111827，正文 #374151，注释 #6b7280。
   - 强调色：商务蓝 #1d4ed8，风险或下降使用克制红 #dc2626。
  【排版规则】
   - 字体：现代无衬线体；标题像汇报结论而不是章节名。
   - 层级：每页顶部一行“核心结论”，下方用图表、矩阵或表格证明。
  【组件特征】
   - **强制分页容器**：每一页必须使用 \`<section class="slide">\` 包裹，16:9 比例。
   - 图表：2x2 矩阵、瀑布图、关键数字、路线图用 HTML/CSS 绘制，表格要有清晰表头。
  【布局原则】适合战略汇报、经营复盘、项目方案；不要做花哨动画，重点是专业可信。`},{id:"startup-pitch",name:"创业路演",category:"演示汇报/年轻路演",accent:"#ff4d8d",description:"年轻清爽的 Pitch Deck，故事线、市场机会和产品证据突出",previewHtml:`<div style="font-family: sans-serif; background: #171329; color: #fff; padding: 16px; height: 100%; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box; border: 1px solid #2d264d; border-radius: 8px;">
    <div style="display: flex; justify-content: space-between; align-items: center;">
      <span style="font-size: 12px; font-weight: bold; color: #ff4d8d;">PITCH DECK</span>
      <span style="background: rgba(34,211,238,0.2); color: #22d3ee; font-size: 10px; padding: 3px 8px; border-radius: 4px; font-weight: bold;">SERIES A</span>
    </div>
    <div style="margin: 12px 0 auto 0;">
      <div style="font-size: 20px; font-weight: 800; line-height: 1.2; color: #fff;">Disrupting SaaS Workflows</div>
      <div style="font-size: 11px; color: #cbd5e1; margin-top: 6px;">Market Size: $42B / CAGR: 24%</div>
    </div>
    <div style="display: flex; align-items: flex-end; justify-content: space-between; border-top: 1px solid rgba(255,255,255,0.1); padding-top: 8px;">
      <span style="font-size: 11px; color: #8b5cf6;">Traction Graph 📈</span>
      <span style="font-size: 12px; font-weight: bold; color: #22d3ee;">10x Growth</span>
    </div>
  </div>`,style:`【视觉主题】年轻创业团队路演，清爽、有冲劲、但仍然可信
  【色彩系统】
   - 基础底色：亮白 #ffffff 或深紫灰 #171329。
   - 强调色：玫红 #ff4d8d、靛紫 #8b5cf6、亮青 #22d3ee，单页最多使用两种。
   - 文本颜色：深色背景用 #ffffff / #cbd5e1，浅色背景用 #111827 / #4b5563。
  【排版规则】
   - 字体：现代圆润无衬线体，标题短促有力，正文用证据支撑。
   - 单页限制：每页只讲一个路演问题，如痛点、方案、市场、商业模式、增长、团队。
  【组件特征】
   - **强制分页容器**：每一页必须使用 \`<section class="slide">\` 包裹，16:9 比例。
   - 可使用大号数字、机会卡片、增长曲线、产品 mockup 框、投资亮点标签。
  【布局原则】适合融资 BP、Demo Day、创新项目汇报；视觉年轻，但信息结构必须稳。`},{id:"neon-tech-launch",name:"科技产品发布",category:"演示汇报/科技发布",accent:"#00e5ff",description:"高科技发布会风，深色舞台、产品能力、规格参数和路线图清晰",previewHtml:`<div style="font-family: monospace; background: #050816; color: #00e5ff; padding: 14px; height: 100%; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box; border: 1px solid rgba(0,229,255,0.25); border-radius: 6px;">
    <div style="display: flex; justify-content: space-between; align-items: center; border-bottom: 1px dashed rgba(0,229,255,0.25); padding-bottom: 6px;">
      <span style="font-size: 11px; font-weight: bold;">DEVICE::PRO_X1</span>
      <span style="font-size: 11px; color: #8b5cf6;">STATUS: READY</span>
    </div>
    <div style="margin: auto 0; padding: 6px 0;">
      <div style="font-size: 18px; font-weight: 700; color: #fff; margin-bottom: 8px; font-family: sans-serif;">NEON SPECIFICATIONS</div>
      <div style="font-size: 12px; color: #00e5ff; line-height: 1.5;">
        - CPU: 12-Core CyberEngine<br>
        - GPU: RayTrace Ultra v2<br>
        - Memory: 64GB Unified
      </div>
    </div>
    <div style="font-size: 10px; color: rgba(0,229,255,0.55); border-top: 1px dashed rgba(0,229,255,0.25); padding-top: 6px; text-align: right;">v2.04-patch</div>
  </div>`,style:`【视觉主题】高科技产品发布会，未来感来自结构、节奏和产品中心
  【色彩系统】
   - 基础底色：深黑蓝 #050816 或 #08111f。
   - 强调色：电青 #00e5ff、冷蓝 #3b82f6、克制紫 #8b5cf6。
   - 边框与高光：使用细描边和局部高光，禁止大面积刺眼光晕。
  【排版规则】
   - 字体：几何无衬线体；参数、版本号、规格值使用等宽字体。
   - 层级：产品名最大，能力模块次之，参数说明必须清晰可读。
  【组件特征】
   - **强制分页容器**：每一页必须使用 \`<section class="slide">\` 包裹，16:9 比例。
   - 组件：能力芯片、规格矩阵、产品框线、版本路线图可用 HTML/CSS 绘制。
  【布局原则】适合 AI、新硬件、SaaS 新功能发布；发布感来自清楚的节奏，不靠随机装饰。`},{id:"growth-review",name:"增长战报",category:"演示汇报/增长复盘",accent:"#22c55e",description:"活跃的数据复盘演示，增长指标、实验结果、行动清单一页讲透",previewHtml:`<div style="font-family: sans-serif; background: #ffffff; padding: 16px; height: 100%; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box; border: 1px solid #e2e8f0; border-radius: 10px;">
    <div style="display: flex; justify-content: space-between; align-items: center;">
      <span style="font-size: 13px; font-weight: bold; color: #0f172a;">Growth Review</span>
      <span style="font-size: 11px; background: rgba(34,197,94,0.12); color: #22c55e; padding: 3px 8px; border-radius: 12px; font-weight: bold;">+182% QTD</span>
    </div>
    <div style="margin: 12px 0 auto 0; display: flex; flex-direction: column; gap: 6px;">
      <div style="font-size: 13px; font-weight: bold; color: #1e293b;">Key Experiment: A/B Checkout V2</div>
      <div style="font-size: 11px; color: #64748b; line-height: 1.4;">Conversion Rate improved from 2.1% to 3.8% with statistical significance.</div>
    </div>
    <div style="display: flex; justify-content: space-between; align-items: center; border-top: 1px solid #f1f5f9; padding-top: 8px;">
      <span style="font-size: 10px; color: #94a3b8;">Owner: Growth Team</span>
      <span style="font-size: 11px; color: #2563eb; font-weight: bold;">Next Actions ➜</span>
    </div>
  </div>`,style:`【视觉主题】增长团队复盘战报，积极、清爽、行动导向
  【色彩系统】
   - 基础底色：浅色 #f8fafc 或纯白 #ffffff。
   - 强调色：增长绿 #22c55e、行动蓝 #2563eb、提醒橙 #f97316。
   - 语义色：上涨用绿、下降或风险用红 #ef4444，中性指标用灰蓝。
  【排版规则】
   - 字体：现代无衬线体，数字使用 tabular-nums 保持对齐。
   - 层级：每页顶部必须有一句结论，下面用指标和实验结果支撑。
  【组件特征】
   - **强制分页容器**：每一页必须使用 \`<section class="slide">\` 包裹，16:9 比例。
   - 组件：KPI 卡、实验 A/B 对照、漏斗、行动看板、下周计划列表用 CSS 实现。
  【布局原则】适合增长复盘、运营周报、营销战报；年轻活跃但必须数据清楚、结论明确。`},{id:"developer-conf",name:"开发者大会",category:"演示汇报/技术大会",accent:"#38bdf8",description:"开发者大会技术分享，深色代码感、架构图、API 示例和路线图并重",previewHtml:`<div style="font-family: sans-serif; background: #0b1020; color: #e5e7eb; padding: 16px; height: 100%; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box; border: 1px solid #1c274c; border-radius: 8px;">
    <div style="display: flex; justify-content: space-between; align-items: center;">
      <span style="font-size: 12px; font-weight: bold; color: #38bdf8; letter-spacing: 0.5px;">DEVCONF 2026</span>
      <span style="font-size: 10px; color: #a78bfa;">TRACK A</span>
    </div>
    <div style="margin: 10px 0 auto 0;">
      <div style="font-size: 18px; font-weight: 800; line-height: 1.2; color: #fff; margin-bottom: 8px;">Scalable State with Zustand</div>
      <div style="background: #111827; border-radius: 4px; padding: 6px 8px; font-family: monospace; font-size: 11px; color: #a78bfa; border: 1px solid #1f2937;">
        const useStore = create((set) => ({...}))
      </div>
    </div>
    <div style="font-size: 10px; color: #94a3b8; border-top: 1px solid rgba(255,255,255,0.06); padding-top: 8px;">Presenter: Antigravity / Senior Architect</div>
  </div>`,style:`【视觉主题】开发者大会技术分享，专业、清晰、带舞台科技感
  【色彩系统】
   - 基础底色：深色 IDE 背景 #0b1020，辅助面板 #111827。
   - 文本颜色：主文本 #e5e7eb，注释 #94a3b8。
   - 强调色：天空蓝 #38bdf8、紫色 #a78bfa、成功绿 #34d399。
  【排版规则】
   - 字体：标题用现代无衬线，代码块与 API 参数使用 JetBrains Mono / Fira Code。
   - 层级：概念标题要短，代码示例必须留足行距并可读。
  【组件特征】
   - **强制分页容器**：每一页必须使用 \`<section class="slide">\` 包裹，16:9 比例。
   - 组件：终端窗口、代码片段、架构节点、发布路线图、API 请求/响应卡片。
  【布局原则】适合技术大会、SDK 发布、工程方案分享；不要把代码塞满整页，观众要能在远处看懂。`},{id:"project-kickoff-rally",name:"项目启动动员",category:"演示汇报/项目动员",accent:"#f97316",description:"启动会动员风，目标、角色、节奏、里程碑和团队士气都要有画面感",previewHtml:`<div style="font-family: sans-serif; background: #fff7ed; padding: 16px; height: 100%; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box; border: 1px solid #ffedd5; border-radius: 8px;">
    <div style="display: flex; align-items: center; justify-content: space-between;">
      <span style="font-size: 12px; font-weight: bold; color: #f97316; letter-spacing: 0.5px;">🚀 KICKOFF MEETING</span>
      <span style="font-size: 11px; font-weight: bold; color: #0ea5e9;">Sprint #01</span>
    </div>
    <div style="margin: 10px 0 auto 0;">
      <div style="font-size: 18px; font-weight: 800; line-height: 1.25; color: #1f2937; margin-bottom: 8px;">攻坚行动：排版引擎升级</div>
      <div style="display: flex; flex-direction: column; gap: 5px; font-size: 11px; color: #4b5563;">
        <div>☑ 确立里程碑：2周内核心跑通</div>
        <div>☐ 团队承诺：零阻塞，高协同</div>
      </div>
    </div>
    <div style="font-size: 11px; color: #f97316; font-weight: bold; text-align: right; border-top: 1px dashed #fed7aa; padding-top: 8px;">目标：完美交付 🎯</div>
  </div>`,style:`【视觉主题】项目启动会与团队动员，热烈、有方向感、带行动召集感
  【色彩系统】
   - 基础底色：暖白 #fff7ed 或深色 #1c1917，避免传统公文蓝。
   - 强调色：活力橙 #f97316、琥珀黄 #f59e0b、清爽蓝 #0ea5e9。
   - 文本颜色：标题 #1f2937 或 #ffffff，正文 #4b5563 或 #d6d3d1。
  【排版规则】
   - 字体：标题要有力量，短句化；正文使用短段落和行动列表。
   - 叙事顺序：为什么做 / 要做到什么 / 谁负责 / 怎么推进 / 第一周行动。
  【组件特征】
   - **强制分页容器**：每一页必须使用 \`<section class="slide">\` 包裹，16:9 比例。
   - 组件：目标旗帜、角色卡、里程碑跑道、启动清单、风险护栏、团队承诺墙。
  【布局原则】适合 Kickoff、专项行动、攻坚项目启动；氛围要鼓舞人，但不要变成鸡血海报。`},{id:"roadmap-planning",name:"项目路线图",category:"演示汇报/项目规划",accent:"#6366f1",description:"规划路线图风，阶段目标、依赖关系、优先级与资源安排清晰",previewHtml:`<div style="font-family: sans-serif; background: #f8fafc; padding: 16px; height: 100%; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box; border: 1px solid #e2e8f0; border-radius: 8px;">
    <div style="display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #e2e8f0; padding-bottom: 6px;">
      <span style="font-size: 13px; font-weight: bold; color: #0f172a;">Project Roadmap</span>
      <span style="font-size: 11px; color: #6366f1; font-weight: bold;">2026 OKR</span>
    </div>
    <div style="display: flex; flex-direction: column; gap: 8px; margin: 10px 0 auto 0;">
      <div style="display: flex; align-items: center; gap: 8px;">
        <span style="font-size: 11px; color: #64748b; width: 24px; font-weight: 600;">Q1</span>
        <div style="flex: 1; background: #eef2ff; border-radius: 4px; height: 16px; position: relative; overflow: hidden; border: 1px solid #e0e7ff;">
          <div style="width: 70%; background: #6366f1; height: 100%;"></div>
        </div>
      </div>
      <div style="display: flex; align-items: center; gap: 8px;">
        <span style="font-size: 11px; color: #64748b; width: 24px; font-weight: 600;">Q2</span>
        <div style="flex: 1; background: #ecfdf5; border-radius: 4px; height: 16px; position: relative; overflow: hidden; border: 1px solid #d1fae5;">
          <div style="width: 45%; background: #10b981; height: 100%;"></div>
        </div>
      </div>
    </div>
    <div style="font-size: 11px; color: #94a3b8; text-align: right;">Timeline status: On Track</div>
  </div>`,style:`【视觉主题】项目规划与路线图，清楚、有节奏、能让团队对齐预期
  【色彩系统】
   - 基础底色：冷白 #f8fafc 或淡靛蓝 #eef2ff。
   - 强调色：靛蓝 #6366f1、蓝 #2563eb、薄荷绿 #10b981。
   - 状态色：已完成用绿，进行中用蓝，风险用橙，阻塞用红。
  【排版规则】
   - 字体：现代无衬线体，阶段名称要短，说明文字不超过两行。
   - 结构：用季度/月度/阶段分栏，所有里程碑必须有时间、负责人或交付物。
  【组件特征】
   - **强制分页容器**：每一页必须使用 \`<section class="slide">\` 包裹，16:9 比例。
   - 组件：时间轴、泳道图、优先级矩阵、资源看板、依赖箭头、关键决策点。
  【布局原则】适合项目计划、产品路线图、OKR 拆解；风格轻快但必须可执行。`},{id:"project-retro",name:"项目总结复盘",category:"演示汇报/项目总结",accent:"#14b8a6",description:"项目复盘风，结果、经验、问题、改进动作一屏说清，避免流水账",previewHtml:`<div style="font-family: sans-serif; background: #ffffff; padding: 16px; height: 100%; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box; border: 1px solid #e2e8f0; border-radius: 8px;">
    <div style="display: flex; justify-content: space-between; align-items: center;">
      <span style="font-size: 13px; font-weight: bold; color: #0f172a;">Retro: Engine Upgrade</span>
      <span style="font-size: 10px; background: rgba(20,184,166,0.12); color: #14b8a6; padding: 3px 8px; border-radius: 10px; font-weight: bold;">COMPLETE</span>
    </div>
    <div style="display: flex; gap: 12px; margin: 14px 0; padding: 6px 0;">
      <div style="flex: 1; border-left: 3px solid #ef4444; padding-left: 8px;">
        <div style="font-size: 10px; color: #ef4444; font-weight: 600;">Before (Lags)</div>
        <div style="font-size: 16px; font-weight: bold; color: #374151; margin-top: 2px;">1.2s Render</div>
      </div>
      <div style="flex: 1; border-left: 3px solid #14b8a6; padding-left: 8px;">
        <div style="font-size: 10px; color: #14b8a6; font-weight: 600;">After (Boost)</div>
        <div style="font-size: 16px; font-weight: bold; color: #111827; margin-top: 2px;">0.1s Fast</div>
      </div>
    </div>
    <div style="font-size: 11px; color: #64748b; border-top: 1px solid #f1f5f9; padding-top: 8px;">Key Lesson: Decouple CM Re-render extensions.</div>
  </div>`,style:`【视觉主题】项目总结与复盘，坦诚、清晰、重视经验沉淀
  【色彩系统】
   - 基础底色：白 #ffffff 或浅青灰 #f0fdfa。
   - 强调色：青绿 #14b8a6、深蓝 #1e40af、提醒橙 #f97316。
   - 文本颜色：主文本 #0f172a，次要文本 #64748b。
  【排版规则】
   - 字体：干净无衬线体；标题直接写结论，例如“提前 2 周完成核心交付”。
   - 结构：目标回顾 / 结果数据 / 做对了什么 / 暴露了什么 / 下一步改进。
  【组件特征】
   - **强制分页容器**：每一页必须使用 \`<section class="slide">\` 包裹，16:9 比例。
   - 组件：结果仪表、Before/After 对比、经验卡、问题清单、改进行动表。
  【布局原则】适合项目收口、阶段验收、复盘分享；要有真实感，不要只报喜。`},{id:"annual-story-review",previewHtml:`<div style="font-family: sans-serif; background: #171024; color: #fffaf5; padding: 16px; height: 100%; border: 1.5px solid #f59e0b; border-radius: 8px; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box;">
    <div style="display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid rgba(245,158,11,0.25); padding-bottom: 8px;">
      <span style="font-size: 12px; color: #f59e0b; font-weight: bold; letter-spacing: 1px;">ANNUAL STORY</span>
      <span style="font-size: 11px; color: #a855f7; font-weight: bold;">2026</span>
    </div>
    <div style="margin-top: 10px;">
      <div style="font-size: 18px; font-weight: 800; color: #fff; line-height: 1.25; margin-bottom: 6px;">攀登者：向光而行，聚沙成塔</div>
      <div style="font-size: 11px; color: #a855f7; font-weight: 600; margin-bottom: 10px;">年度关键词：突破 · 协同 · 坚韧</div>
      <div style="display: flex; gap: 8px; margin-top: 8px;">
        <div style="flex: 1; background: rgba(168,85,247,0.15); padding: 8px; border-radius: 4px; border: 1px solid rgba(168,85,247,0.35);">
          <div style="font-size: 10px; color: #a855f7;">核心战役</div>
          <div style="font-size: 13px; font-weight: 700; color: #fff; margin-top: 3px;">业务出海</div>
        </div>
        <div style="flex: 1; background: rgba(245,158,11,0.15); padding: 8px; border-radius: 4px; border: 1px solid rgba(245,158,11,0.35);">
          <div style="font-size: 10px; color: #f59e0b;">用户规模</div>
          <div style="font-size: 13px; font-weight: 700; color: #fff; margin-top: 3px;">+145%</div>
        </div>
      </div>
    </div>
    <div style="font-size: 10px; color: rgba(255,250,245,0.65); border-top: 1px dashed rgba(255,255,255,0.12); padding-top: 8px; text-align: right;">致敬每一位努力的伙伴</div>
  </div>`,name:"年终故事总结",category:"演示汇报/年终总结",accent:"#a855f7",description:"年终总结叙事风，年度主题、关键战役、数据成果和团队瞬间更有温度",style:`【视觉主题】年终总结与年度回顾，既有成绩单，也有故事和温度
  【色彩系统】
   - 基础底色：深紫黑 #171024、暖白 #fffaf5 或柔和渐变。
   - 强调色：紫 #a855f7、金 #f59e0b、玫红 #ec4899。
   - 文本颜色：深色底用 #f8fafc，浅色底用 #111827。
  【排版规则】
   - 字体：标题可更有庆典感，正文仍保持清晰；数字使用大号展示。
   - 结构：年度关键词 / 关键战役 / 成果数据 / 团队成长 / 下一年展望。
  【组件特征】
   - **强制分页容器**：每一页必须使用 \`<section class="slide">\` 包裹，16:9 比例。
   - 组件：年度时间胶片、荣誉墙、关键数字、团队语录、里程碑地图、展望卡片。
  【布局原则】适合部门年终、项目年度总结、团队述职；不要像财务报表一样僵硬，要有叙事节奏。`},{id:"proposal-lab",previewHtml:`<div style="font-family: sans-serif; background: #f1f5f9; padding: 16px; height: 100%; border: 1px solid #cbd5e1; border-radius: 8px; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box;">
    <div>
      <div style="display: flex; align-items: center; gap: 6px; margin-bottom: 10px;">
        <span style="background: #0ea5e9; color: #fff; font-size: 10px; padding: 3px 8px; border-radius: 4px; font-weight: bold;">PROPOSAL</span>
        <span style="font-size: 11px; color: #64748b;">Strategy Lab v1.2</span>
      </div>
      <div style="font-size: 16px; font-weight: 700; color: #0f172a; line-height: 1.3; margin-bottom: 8px;">全渠道用户数字化增长方案</div>
      <div style="font-size: 11px; color: #475569; line-height: 1.45; background: #fff; padding: 8px; border-radius: 4px; border-left: 3px solid #0ea5e9;">
        <strong>核心洞察：</strong>当前存量用户活跃度下滑 15%，急需精细化社群运营切入。
      </div>
    </div>
    <div style="display: flex; gap: 8px; align-items: center; justify-content: space-between; border-top: 1px solid #e2e8f0; padding-top: 8px; font-size: 11px; color: #64748b;">
      <span>预期 ROI: <strong>3.5x</strong></span>
      <span style="color: #0ea5e9; font-weight: bold;">查看路线图 ➜</span>
    </div>
  </div>`,name:"方案提案",category:"演示汇报/方案提案",accent:"#0ea5e9",description:"提案实验室风，问题洞察、方案架构、价值证明和落地路径有说服力",style:`【视觉主题】方案提案与创意提案，像一间清爽的策略实验室
  【色彩系统】
   - 基础底色：白 #ffffff、浅蓝灰 #f1f5f9，允许局部淡色渐变。
   - 强调色：天空蓝 #0ea5e9、青绿 #10b981、亮紫 #8b5cf6。
   - 文本颜色：标题 #0f172a，正文 #475569，注释 #64748b。
  【排版规则】
   - 字体：现代无衬线体，标题用“观点句”而不是抽象名词。
   - 结构：问题洞察 / 核心策略 / 方案模块 / 价值证明 / 资源与时间计划。
  【组件特征】
   - **强制分页容器**：每一页必须使用 \`<section class="slide">\` 包裹，16:9 比例。
   - 组件：洞察卡、方案架构图、价值阶梯、投入产出表、实施路线图、决策页。
  【布局原则】适合客户提案、内部方案评审、创新方案推荐；要有设计感，但逻辑必须能说服决策者。`},{id:"workshop-canvas",previewHtml:`<div style="font-family: sans-serif; background: #fffbeb; padding: 16px; height: 100%; border: 1px dashed #eab308; border-radius: 8px; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box;">
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px;">
      <span style="font-size: 12px; font-weight: bold; color: #854d0e; background: #fef08a; padding: 3px 8px; border-radius: 10px;">⚡ 共创工作坊</span>
      <span style="font-size: 10px; color: #fb7185; font-weight: bold; border: 1px solid #fb7185; padding: 2px 6px; border-radius: 4px;">⏱️ 15 Mins</span>
    </div>
    <div style="font-size: 14px; font-weight: 700; color: #1f2937; margin-bottom: 10px;">议题：如何优化新用户首周体验？</div>
    <div style="display: flex; gap: 8px; margin-top: auto;">
      <div style="flex: 1; background: #fff9db; padding: 8px 7px; border-radius: 2px; box-shadow: 2px 2px 0px rgba(0,0,0,0.06); transform: rotate(-1.5deg);">
        <div style="font-size: 11px; font-weight: bold; color: #854d0e; margin-bottom: 3px;">痛点</div>
        <div style="font-size: 10px; color: #4b5563; line-height: 1.3;">注册流程验证码延迟高</div>
      </div>
      <div style="flex: 1; background: #ffe4e6; padding: 8px 7px; border-radius: 2px; box-shadow: 2px 2px 0px rgba(0,0,0,0.06); transform: rotate(1deg);">
        <div style="font-size: 11px; font-weight: bold; color: #9f1239; margin-bottom: 3px;">创意</div>
        <div style="font-size: 10px; color: #4b5563; line-height: 1.3;">微信一键快捷登录</div>
      </div>
      <div style="flex: 1; background: #ecfeff; padding: 8px 7px; border-radius: 2px; box-shadow: 2px 2px 0px rgba(0,0,0,0.06); transform: rotate(-0.5deg);">
        <div style="font-size: 11px; font-weight: bold; color: #0891b2; margin-bottom: 3px;">行动</div>
        <div style="font-size: 10px; color: #4b5563; line-height: 1.3;">开发快捷登录接口</div>
      </div>
    </div>
  </div>`,name:"共创工作坊",category:"演示汇报/共创工作坊",accent:"#eab308",description:"工作坊引导风，议程、分组任务、讨论模板和产出看板轻松但有秩序",style:`【视觉主题】团队共创工作坊，轻松、开放、适合讨论和协作
  【色彩系统】
   - 基础底色：柔和米白 #fffbeb 或浅灰 #f8fafc。
   - 强调色：黄 #eab308、湖蓝 #06b6d4、珊瑚红 #fb7185，作为标签和分组识别。
   - 文本颜色：主文本 #1f2937，辅助文本 #64748b。
  【排版规则】
   - 字体：圆润无衬线体，标题友好但不幼稚。
   - 结构：目标 / 议程 / 分组任务 / 讨论模板 / 投票规则 / 输出物。
  【组件特征】
   - **强制分页容器**：每一页必须使用 \`<section class="slide">\` 包裹，16:9 比例。
   - 组件：分组任务卡、计时器、讨论看板、投票点、问题引导卡、成果模板。
  【布局原则】适合工作坊、头脑风暴、需求共创、复盘会；轻松但必须整齐可执行。`},{id:"editorial-ink-deck",previewHtml:`<div style="font-family: Georgia, serif; background: #f1efea; padding: 16px; height: 100%; border: 1px solid #dcdad5; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box;">
    <div style="display: flex; justify-content: space-between; border-bottom: 1px solid #0a1f3d; padding-bottom: 6px; margin-bottom: 10px;">
      <span style="font-size: 10px; font-weight: bold; color: #0a1f3d; letter-spacing: 1.5px; font-family: monospace;">INK & PIXEL</span>
      <span style="font-size: 10px; color: #6e6b64;">CHAPTER 02</span>
    </div>
    <div style="display: flex; gap: 10px; align-items: stretch; margin-bottom: auto;">
      <div style="flex: 1.2;">
        <div style="font-size: 19px; font-weight: 700; color: #0a1f3d; line-height: 1.15; margin-bottom: 8px; font-family: Georgia, serif;">重塑阅读的温度与节奏</div>
        <div style="font-size: 11px; color: #2e2d2a; line-height: 1.45; text-align: justify; font-family: sans-serif;">
          在这个信息爆炸的时代，我们试图通过电子杂志的墨水屏质感，为读者寻回那份克制且有呼吸感的深度阅读体验。
        </div>
      </div>
      <div style="flex: 0.8; min-height: 90px; background: #0a1f3d; border-radius: 2px; overflow: hidden; display: flex; align-items: center; justify-content: center;">
        <div style="font-size: 28px; color: #f1efea; font-style: italic; font-weight: bold; opacity: 0.35;">Ink</div>
      </div>
    </div>
    <div style="font-size: 10px; color: #8a867c; border-top: 1px dashed #dcdad5; padding-top: 8px; font-family: monospace; display: flex; justify-content: space-between;">
      <span>ESTABLISHED 2026</span>
      <span>P. 18</span>
    </div>
  </div>`,name:"电子杂志",category:"演示汇报/杂志演讲",accent:"#0a1f3d",description:"电子杂志式网页 PPT，衬线大标题、纸感底色、叙事节奏和图片证据并重",style:`【视觉主题】电子杂志 × 电子墨水，像一份可演示的深度杂志专题
  【色彩系统】
   - 基础底色：暖纸白 #f1efea、瓷白 #f1f3f5 或沙色 #f0e6d2，整份作品只选一套。
   - 文本颜色：墨黑、深靛蓝或炭灰，避免纯彩色正文。
   - 强调色：只用于页眉、编号、关键词和数据标记，不做大面积装饰。
  【排版规则】
   - 字体：大标题使用高质感衬线体，正文使用清晰无衬线体，元数据和编号使用等宽字体。
   - 主题节奏：先规划每页的 hero / light / dark 节奏，每 3-4 页插入封面、章节幕封、大引用或问题页。
   - 单页限制：每页只承载一个叙事动作，长内容拆成“钩子 -> 背景 -> 证据 -> 转折 -> 收束”。
  【组件特征】
   - **强制分页容器**：每一页必须使用 \`<section class="slide">\` 包裹，16:9 比例。
   - 页面组件：杂志页眉、衬线大标题、导语、数据大字报、图文分栏、图片网格、引用页和收束页。
   - 图片：图片是第一公民，使用 16:10、4:3、3:2、1:1 或 16:9 等标准比例；优先只裁底部，保留顶部和左右关键信息。
  【布局原则】适合观点分享、行业观察、人文叙事、产品故事和私享会演讲；克制优于炫技，结构优于装饰。`},{id:"swiss-presentation-system",previewHtml:`<div style="font-family: Inter, Helvetica, sans-serif; background: #fafaf8; padding: 16px; height: 100%; border: 1.5px solid #0a0a0a; display: flex; flex-direction: column; justify-content: space-between; box-sizing: border-box;">
    <div style="display: flex; justify-content: space-between; align-items: baseline; border-bottom: 2px solid #0a0a0a; padding-bottom: 8px; margin-bottom: 10px;">
      <span style="font-size: 14px; font-weight: 900; color: #002FA7; letter-spacing: -0.5px;">SWISS GRID SYSTEM</span>
      <span style="font-size: 10px; font-family: monospace; color: #737373;">GRID STACK</span>
    </div>
    <div style="display: flex; flex-direction: column; gap: 10px; margin-bottom: auto;">
      <div style="font-size: 18px; font-weight: 300; color: #0a0a0a; line-height: 1.15; letter-spacing: -0.5px;">
        MAXIMUM INFORMATION.<br>MINIMUM EMBELLISHMENT.
      </div>
      <div style="display: flex; gap: 12px; border-top: 1px solid #d4d4d2; padding-top: 8px;">
        <div style="flex: 1;">
          <div style="font-size: 22px; font-weight: 200; color: #002FA7;">01</div>
          <div style="font-size: 10px; font-weight: 700; color: #0a0a0a; margin-top: 3px;">SYSTEM ORDER</div>
        </div>
        <div style="flex: 1; border-left: 1px solid #d4d4d2; padding-left: 12px;">
          <div style="font-size: 22px; font-weight: 200; color: #0a0a0a;">960</div>
          <div style="font-size: 10px; font-weight: 700; color: #737373; margin-top: 3px;">BASE WIDTH</div>
        </div>
      </div>
    </div>
    <div style="font-size: 10px; font-weight: bold; color: #0a0a0a; letter-spacing: 0.5px; border-top: 1px solid #0a0a0a; padding-top: 6px;">
      SEC. 04 / DATA REPORT
    </div>
  </div>`,name:"瑞士国际主义",category:"演示汇报/瑞士国际主义",accent:"#002FA7",description:"瑞士国际主义网页 PPT，12 栏网格、单一锚点色、直角纯色和超强字号对比",style:'【视觉主题】瑞士国际主义系统，信息驱动、冷静、强秩序\n  【色彩系统】\n   - 基础底色：极浅暖白 #fafaf8，辅助灰阶 #f0f0ee / #d4d4d2 / #737373。\n   - 文本颜色：近黑 #0a0a0a，高对比优先。\n   - 单一锚点色（克莱因蓝 #002FA7 等）只能做画龙点睛的单点高亮（如 KPI 数字、小编号、栏目徽章），在全页视觉占比控制在 5% 以下，绝对禁止作为大面积背景、边框或让大段文本高亮。\n  【排版与防溢出规则】\n   - 字体：全程无衬线，优先 Inter / Helvetica / Noto Sans SC；代码和标签可用等宽字体.\n   - 字号阶梯：越大的字越轻，主标题和 KPI 使用 200-300 字重，小标签和图表标注使用 500-600 字重。\n   - 防折行溢出：中文大标题禁止使用死大字号，必须配合双向视口比例约束（如 `font-size: min(5.2vw, 9.2vh)`），以防页面被长标题折行撑爆。\n   - 外层间距限制：`.canvas-card` 外层包裹容器的内边距设置为 `padding: 32px 24px 24px`（上32px，左右24px，下24px）。顶部页眉 `.chrome-min` 必须设置 `margin-bottom: 16px` 以内，底部页脚 `.foot-min` 必须设置 `margin-top: 14px` 以内。\n   - 垂直高度预算：每页幻灯片的主内容区可用高度不能超过 `380px`。禁止使用大间距（如 `gap-10` / `gap-12`，网格列 gap 建议控制在 `12px - 20px`，绝对不能多层嵌套）。\n  【组件特征】\n   - **强制分页容器**：每一页必须使用 `<section class="slide">` 包裹，16:9 比例。\n   - 极简代码块：多卡片或幻灯片中的代码块行数必须控制在 **12行** 以内，若超出则精简。必须采用紧凑样式：`.swiss-code-block`，且具体样式声明为 `font-size: 11px; line-height: 1.45; padding: 12px 16px;` 以防垂直高度超出。\n   - 版式：优先使用登记过的 12 栏结构，如封面、statement、KPI tower、横向时间线、duo compare、矩阵、系统图、规格表和 image hero。\n   - 几何：直角、纯色块、1px 发丝线、点阵或网格背景；禁止渐变、阴影、圆角、玻璃拟态和随意图标堆叠。\n   - 图片：主图优先 21:9，多图统一 21:9 或 16:10；同组图片比例、高度、边距和标题样式必须一致。\n  【布局原则】适合数据汇报、产品方法论、工程分享、年度总结和技术发布；视觉冲击来自网格、留白、字号对比和单一锚点色，而不是装饰。'}],xr=["幻灯片","长页","卡片","报告","仪表盘","文档"],ct=["极简","编辑","科技","数据","温暖","代码"],ur=e=>`你是一名资深网页设计师、信息架构师与前端工程师。请基于我提供的内容，输出一个**完整、自包含、可直接在浏览器打开**的 HTML 文档。

【设计系统令牌 (Micro Design System)】
${e}

【先理解内容，再设计】
1. 先判断输入内容最适合做成哪种成品：单页网页、长图海报、多页卡片、幻灯片、仪表盘、报告或简历。
2. 保留原始内容里的核心事实、数据、名称和顺序；可以重组表达，但不要编造不存在的案例、数据、引用或品牌背书。
3. 为成品建立清晰的信息层级：主标题 / 导语 / 关键结论 / 分节内容 / 行动或总结。
4. 如果内容很长，优先拆成分区、分屏或分页；不要把所有文字塞进一个拥挤容器。
5. 页面上的每一块内容都必须有明确作用，删除空洞装饰、重复口号和无意义占位。

【防 AI-Slop 与排版硬约束】
1. **中文字体栈优先**：请设置 font-family 为系统级现代中文字体（如 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif）。英文采用 Inter 或 Manrope。
2. **严格执行所选风格**：颜色、字体气质、圆角、边框、阴影、留白、组件形态必须服从上面的设计系统令牌；不要混入其它品牌风格。
3. **8px 基线网格**：margin、padding、gap、line-height、圆角数值尽可能基于 8 像素或 4 像素倍数，保证节奏稳定。
4. **颜色对比度约束**：请确保文字与背景的对比度 ≥ 4.5:1，绝对避免看不清的浅灰字。
5. **视觉去 slop化**：拒绝滥用无意义的大投影、彩虹渐变、漂浮光球、玻璃拟态堆叠和随机装饰。除非风格明确要求，不要使用极端纯黑大面积背景或过饱和荧光色。
6. **内容真实感**：如果需要补全文案，必须具体、可信、贴近业务场景；严禁出现 "Lorem ipsum"、"您的标题在这里"、"示例文本" 等占位符。
7. **可读性优先**：正文不可被装饰、图片、渐变或固定层遮挡；按钮、标签、卡片内文字不能溢出容器。

【自由画布生产经验】
1. **先列出版式节奏**：生成多页卡片或幻灯片前，先在心里规划每页承担的角色（封面 / 数据 / 证据 / 对比 / 结构 / 收束），避免所有页面长得一样。
2. **单一视觉系统**：一份作品只使用一套主题色、一套字体分工 and 一组组件规则；不要把多个风格拼贴到同一份 HTML 里。
3. **标准比例**：图片、截图、信息图和卡片槽位使用 21:9、16:10、16:9、4:3、3:2、1:1、3:4 或 9:16 等标准比例，不要复制原图的奇怪宽高比。
4. **图文安全区**：底部页码、导航、说明文字和图片 caption 不得贴近画布边缘；核心内容应明显避开导出裁切区。
5. **无脚本交互与低性能兜底**：系统会移除所有 \`<script>\` 与事件属性。如需交互反馈，请使用纯 CSS（\`:hover\`、\`:checked\`、\`@keyframes\`）实现，并保证低性能或无脚本时主要内容完整可读。
6. **成品宽高比与物理边界预算（防内容裁剪溢出，极其重要）**：
   在编写 CSS 之前，请务必根据当前要求的成品类型，显式分析画布物理空间上限（系统会像物理沙箱一样对页面进行硬剪裁或滚动约束）：
   - **横版幻灯片（16:9 画布，基准宽 960px，高 540px）**：可用高度极窄。外层 \`.canvas-card\` 内边距最多为 \`padding: 32px 24px 24px\`，顶部页眉 \`.chrome-min\` 的 \`margin-bottom\` 限制在 \`16px\` 以内，底部页脚 \`.foot-min\` 的 \`margin-top\` 限制在 \`14px\` 以内。主内容区高度预算绝对不可超过 **380px**（即总高 70%）。严禁堆叠高内容块，网格 gap 建议在 \`12px - 20px\`。
   - **竖版卡片/分卡片图文（如 3:4 比例卡片，基准 720px * 960px，或 9:16 比例）**：垂直空间相对充裕，但水平宽度极其受限。必须防止文本单行过宽、卡片或网格的左右 margin 过大（建议 \`padding\` 左右不超过 \`20px\`），避免元素顶格导致左右被切边。
   - **单页网页/长图海报（自然延伸高度）**：垂直高度无限制，可向下无限滚动，但必须设置主内容的最大宽度（如 \`max-width: 800px\` 或 \`max-width: 60vh\`）进行居中约束，防止在宽屏下行宽过宽。
7. **分页与分卡片下的代码块极简主义**：
   如果是多页/幻灯片/卡片等有固定高度物理边界的成品，代码块是垂直溢出的最大温床。
   - 代码行数必须压缩在 **12行** 以内，若超出必须做局部精炼截取，隐藏非关键的闭合括号、辅助包或引入行。
   - 样式必须强制声明为紧凑版以节约高度：\`font-size: 11px; line-height: 1.45; padding: 12px 16px; font-family: monospace; overflow-x: auto;\`。
8. **大字号标题的双重缩放约束**：
   在有高度限制的成品（如 16:9 幻灯片）中，大字号中文标题如果由于字数稍长折行，会瞬间占满高度，挤占正文空间导致排版崩溃。
   - 严禁对大标题使用绝对死大字号（如直接使用 \`48px\`/\`60px\` 或单独用大 \`vw\`）。
   - 必须使用双向视口缩放约束限制（如 \`font-size: min(5.2vw, 9.2vh)\` 或 \`font-size: clamp(24px, 4.5vw, 42px)\`），确保无论页面怎么缩放或在什么屏幕高度下，标题都不会因为折行折腾出额外高度。

【安全与净化约束（强制，不可绕过）】
系统会对生成的 HTML 进行安全净化，以下能力会被移除或失效，请不要使用：
1. **禁止 \`<script>\` 标签**：包括内联脚本、外部脚本、事件脚本。所有内容必须在无脚本时完整可读。
2. **禁止事件处理器属性**：如 \`onclick\`、\`onload\`、\`onerror\`、\`onmouseover\` 等以 \`on\` 开头的属性都会被移除。
3. **禁止危险 URL 协议**：\`href\` / \`src\` 等链接禁止使用 \`javascript:\`、\`data:text/html\`、\`data:application/javascript\` 等可执行协议；允许 \`data:image/*\`、\`data:font/*\`、\`data:application/json\`、\`data:text/plain\` 等安全内联资源；链接请使用相对路径或锚点，不要使用外部 \`https:\` 链接（公众号不支持外链跳转）。
4. **禁止危险 CSS**：\`style\` 属性与 \`<style>\` 标签中禁止使用 \`expression()\`、\`behavior\`、\`@import\` 等 IE 遗留攻击向量。
5. **嵌入标签受限**：\`<iframe>\`、\`<object>\`、\`<embed>\` 在预览时会被强制加 sandbox，导出/严格模式下会被直接丢弃；请勿将它们作为关键内容载体。

**推荐替代方案**：
- 动态效果：使用 CSS 动画（\`@keyframes\`、\`transition\`）代替 JS 交互。
- 交互反馈：用 \`:hover\` / \`:checked\` 纯 CSS 状态实现轻量交互，且必须保证静态状态内容完整。
- 外部内容：用 \`<img>\` 引入稳定 HTTPS 图片，而非 iframe 或远程 canvas。

【技术与容器规范（兼容 html-anything 引擎）】
1. 只输出完整 \`<!DOCTYPE html>\` 文档，必须包含 \`<html>\`、\`<head>\`、\`<meta charset="utf-8">\`、\`<meta name="viewport" content="width=device-width, initial-scale=1">\` 与 \`<body>\`。
2. **样式必须内联在 \`<style>\` 中**。可以使用 Tailwind CSS 类名（系统会自动注入本地 Tailwind 运行时）。**外链资源原则**：如果源文本为中文，禁止引入任何海外 CDN 资源（包括 Google Fonts、其他外部 CSS/JS 文件），必须使用系统内置字体栈或国内可访问的 CDN；如果源文本为英文，可酌情考虑但非必要不推荐海外 CDN。
3. **导出友好**：所有核心内容必须在初始状态可见，不要依赖 hover、点击、滚动触发动画后才出现；避免视频、音频、iframe、远程 canvas 作为关键信息载体。
4. **资源约束**：图片优先使用稳定的 https URL，必须设置 \`max-width:100%\` 与明确尺寸或比例；不要使用跨域受限图片、登录后图片或会过期的私有链接。
5. **响应式与流式输出**：移动端自适应，所有组件应当使用 Flex/Grid 弹性布局；正文不可横向溢出，长单词/代码需 \`overflow-wrap:anywhere\` 或横向滚动容器。
6. 根据业务场景，如果你设计的是**单页网页/海报长图**：
   允许自然延伸高度，但 \`body\` 必须 \`margin:0\`，且内容请务必包裹在一个主容器内（如 \`<main>\` 或 \`<div>\`），页面主容器建议使用 \`max-width\` 控制阅读宽度。
7. 如果设计是**多页图文/幻灯片/多卡片报告**：
   【强制分页】每一页（每一帧）**必须**独立使用 \`<section class="page">\`（竖版图文）、\`<section class="slide">\`（横版幻灯片）或 \`<section class="card">\`（独立卡片）完全包裹。
   这是为了配合渲染器的导出机制自动切割 PDF，严禁省略包裹层或全部堆叠在一起！
   推荐尺寸示例：
   - 小红书/竖版卡片：\`.page{width:min(100vw,720px);aspect-ratio:3/4;overflow:hidden;margin:0 auto 24px;}\`
   - 9:16 竖版故事：\`.page{width:min(100vw,540px);aspect-ratio:9/16;overflow:hidden;margin:0 auto 24px;}\`
   - 16:9 幻灯片：\`.slide{width:min(100vw,960px);aspect-ratio:16/9;overflow:hidden;margin:0 auto 24px;}\`
8. 如需打印/PDF 友好，请补充 \`@media print\`，确保背景色保留、页面不被浏览器默认边距破坏。
9. 如果页面包含数据展示，优先使用 HTML/CSS 绘制轻量图表、表格、进度条和指标卡；不要依赖外部图表库。
10. 交互只能用纯 CSS（\`:hover\`、\`:checked\`、\`@keyframes\`）实现，禁止写 JavaScript；无脚本时主要内容必须完整可读。

【输出要求】
直接返回唯一的代码，不要任何前后解释性说明废话，不要以 \`\`\`html 包装代码块。

【我的输入内容与业务诉求】
（在此粘贴你的文章、大纲、数据或描述）`;function mr(e){return ur(e.style)}function Yd(e){return ur(e)}const Xd=[...Ud,...qd,...Wd,...Gd,...Kd,...Vd],Zd=Xd.map(e=>{const t=Hd[e.id];if(!t)throw new Error(`Missing design style metadata: ${e.id}`);return{...e,...t}}),Jd={极简:"#ffffff",编辑:"#fafaf8",科技:"#0f1117",数据:"#f0f2f5",温暖:"#fdf8f3",代码:"#0f1115"},Qd={极简:"#1a1a1a",编辑:"#222222",科技:"#e8eaed",数据:"#1f2937",温暖:"#3d3929",代码:"#d4d4d8"},ep={极简:"#999999",编辑:"#666666",科技:"#8a8f98",数据:"#6b7280",温暖:"#8b8570",代码:"#71717a"},tp={极简:"system-ui, sans-serif",编辑:'Georgia, "Times New Roman", serif',科技:"system-ui, sans-serif",数据:"system-ui, sans-serif",温暖:'"Noto Serif SC", Georgia, serif',代码:'"Courier New", monospace'},np={极简:"4px",编辑:"2px",科技:"10px",数据:"6px",温暖:"14px",代码:"6px"};function Rt(e){return e.replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;")}function ip(e){if(typeof e!="string")return!1;const t=e.trim();return t.length===0||t.length>40?!1:/^#(?:[0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/.test(t)||/^rgba?\(\s*\d{1,3}%?\s*,\s*\d{1,3}%?\s*,\s*\d{1,3}%?\s*(,\s*(?:0|1|0?\.\d+)\s*)?\)$/.test(t)?!0:/^[a-z]{3,25}$/i.test(t)}const rp="#6366f1";function sp(e){const t=Jd[e.visualTone]||"#fff",n=Qd[e.visualTone]||"#1a1a1a",i=ep[e.visualTone]||"#999",r=tp[e.visualTone]||"sans-serif",o=np[e.visualTone]||"6px",a=["科技","代码"].includes(e.visualTone),l=a?"rgba(255,255,255,0.08)":"#e5e5e5",d=a?"rgba(255,255,255,0.05)":"#fff",c=Rt(e.name.split(/·|·/)[0].trim()),p=e.description.length>20?e.description.slice(0,20)+"…":e.description,f=Rt(p),g=ip(e.accent)?e.accent:rp,x=`1px solid ${l}`;return e.outputType==="幻灯片"?`<div style="font-family:${r};background:${t};color:${n};padding:14px;height:100%;display:flex;flex-direction:column;box-sizing:border-box;">
      <div style="height:4px;width:28%;background:${g};border-radius:2px;margin-bottom:12px;"></div>
      <div style="font-size:16px;font-weight:700;margin-bottom:6px;line-height:1.2;">${c}</div>
      <div style="font-size:11px;color:${i};margin-bottom:auto;">${f}</div>
      <div style="display:flex;gap:5px;align-items:flex-end;margin-top:8px;">
        <div style="width:20%;height:12px;background:${g};border-radius:1px;opacity:0.6;"></div>
        <div style="width:14%;height:8px;background:${g};border-radius:1px;opacity:0.3;"></div>
        <div style="width:18%;height:10px;background:${g};border-radius:1px;opacity:0.4;"></div>
      </div>
      <div style="display:flex;gap:4px;justify-content:flex-end;margin-top:6px;">
        <div style="width:5px;height:5px;border-radius:50%;background:${g};"></div>
        <div style="width:5px;height:5px;border-radius:50%;background:${i};opacity:0.3;"></div>
        <div style="width:5px;height:5px;border-radius:50%;background:${i};opacity:0.3;"></div>
      </div>
    </div>`:e.outputType==="卡片"?`<div style="font-family:${r};background:${t};color:${n};padding:14px;height:100%;display:flex;flex-direction:column;box-sizing:border-box;">
      <div style="height:4px;width:100%;background:${g};border-radius:2px;margin-bottom:10px;opacity:0.8;"></div>
      <div style="font-size:15px;font-weight:700;margin-bottom:4px;">${c}</div>
      <div style="font-size:11px;color:${i};margin-bottom:8px;line-height:1.4;">${f}</div>
      <div style="display:flex;gap:6px;margin-top:auto;">
        <div style="background:${g};color:#fff;font-size:10px;padding:2px 6px;border-radius:3px;font-weight:600;">标签A</div>
        <div style="background:${a?"rgba(255,255,255,0.08)":"#f1f1f1"};color:${n};font-size:10px;padding:2px 6px;border-radius:3px;">标签B</div>
      </div>
    </div>`:e.outputType==="仪表盘"?`<div style="font-family:${r};background:${t};color:${n};padding:10px;height:100%;display:flex;flex-direction:column;gap:6px;box-sizing:border-box;">
      <div style="font-size:11px;font-weight:700;padding:2px 0;border-bottom:1px solid ${l};">${c}</div>
      <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:6px;flex:1;">
        <div style="background:${d};border:${x};border-radius:${o};padding:6px;display:flex;flex-direction:column;gap:3px;justify-content:center;">
          <div style="height:3px;width:50%;background:${i};border-radius:1px;opacity:0.3;"></div>
          <div style="font-size:15px;font-weight:700;color:${g};">128</div>
        </div>
        <div style="background:${d};border:${x};border-radius:${o};padding:6px;display:flex;flex-direction:column;gap:3px;justify-content:center;">
          <div style="height:3px;width:50%;background:${i};border-radius:1px;opacity:0.3;"></div>
          <div style="font-size:15px;font-weight:700;color:${g};">56%</div>
        </div>
        <div style="background:${d};border:${x};border-radius:${o};padding:6px;display:flex;flex-direction:column;gap:3px;justify-content:center;">
          <div style="height:3px;width:50%;background:${i};border-radius:1px;opacity:0.3;"></div>
          <div style="font-size:15px;font-weight:700;color:${g};">3.2k</div>
        </div>
      </div>
      <div style="display:flex;gap:4px;align-items:flex-end;height:32px;margin-top:4px;">
        <div style="width:12%;height:30%;background:${g};border-radius:1px;opacity:0.3;"></div>
        <div style="width:12%;height:55%;background:${g};border-radius:1px;opacity:0.5;"></div>
        <div style="width:12%;height:40%;background:${g};border-radius:1px;opacity:0.4;"></div>
        <div style="width:12%;height:80%;background:${g};border-radius:1px;opacity:0.7;"></div>
        <div style="width:12%;height:65%;background:${g};border-radius:1px;opacity:0.6;"></div>
        <div style="width:12%;height:95%;background:${g};border-radius:1px;opacity:0.8;"></div>
        <div style="width:12%;height:50%;background:${g};border-radius:1px;opacity:0.5;"></div>
      </div>
    </div>`:e.outputType==="报告"?`<div style="font-family:${r};background:${t};color:${n};padding:12px;height:100%;display:flex;flex-direction:column;gap:6px;box-sizing:border-box;">
      <div style="font-size:9px;font-weight:600;color:${g};text-transform:uppercase;letter-spacing:1px;">${Rt(e.category.split("/")[0]||"REPORT")}</div>
      <div style="font-size:15px;font-weight:700;line-height:1.2;">${c}</div>
      <div style="height:1px;background:${l};"></div>
      <div style="font-size:11px;color:${i};line-height:1.4;">${f}</div>
      <div style="display:flex;gap:6px;margin-top:auto;">
        <div style="font-size:14px;font-weight:800;color:${g};">42%</div>
        <div style="font-size:14px;font-weight:800;color:${g};opacity:0.6;">¥3.2M</div>
      </div>
    </div>`:e.outputType==="文档"?`<div style="font-family:${r};background:${t};color:${n};padding:14px 16px;height:100%;display:flex;flex-direction:column;gap:8px;box-sizing:border-box;">
      <div style="font-size:15px;font-weight:700;line-height:1.3;">${c}</div>
      <div style="font-size:11px;color:${i};">${f}</div>
      <div style="height:1px;background:${l};"></div>
      <div style="display:flex;flex-direction:column;gap:4px;flex:1;">
        <div style="height:3px;width:95%;background:${i};border-radius:1px;opacity:0.15;"></div>
        <div style="height:3px;width:88%;background:${i};border-radius:1px;opacity:0.15;"></div>
        <div style="height:3px;width:92%;background:${i};border-radius:1px;opacity:0.12;"></div>
        <div style="height:3px;width:60%;background:${i};border-radius:1px;opacity:0.1;"></div>
      </div>
    </div>`:`<div style="font-family:${r};background:${t};color:${n};padding:14px;height:100%;display:flex;flex-direction:column;box-sizing:border-box;">
    <div style="height:4px;width:100%;background:${g};border-radius:2px;margin-bottom:10px;opacity:0.8;"></div>
    <div style="font-size:15px;font-weight:700;margin-bottom:3px;line-height:1.2;">${c}</div>
    <div style="font-size:11px;color:${i};margin-bottom:10px;line-height:1.4;">${f}</div>
    <div style="display:flex;flex-direction:column;gap:4px;flex:1;">
      <div style="height:3px;width:95%;background:${i};border-radius:1px;opacity:0.15;"></div>
      <div style="height:3px;width:88%;background:${i};border-radius:1px;opacity:0.15;"></div>
      <div style="height:3px;width:92%;background:${i};border-radius:1px;opacity:0.12;"></div>
      <div style="height:3px;width:70%;background:${i};border-radius:1px;opacity:0.1;"></div>
    </div>
    <div style="display:flex;gap:6px;margin-top:10px;">
      <div style="background:${g};color:#fff;font-size:10px;padding:2px 8px;border-radius:${o};font-weight:600;">操作</div>
      <div style="border:1px solid ${l};font-size:10px;padding:2px 8px;border-radius:${o};color:${i};">详情</div>
    </div>
  </div>`}const ti=Re.memo(function({style:t}){const n=t.previewHtml||sp(t);return s.jsx("div",{className:"group/thumb relative w-full overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm transition-all",style:{aspectRatio:"4/3"},children:s.jsx("div",{className:"absolute inset-0 origin-top-left",style:{width:"125%",height:"125%",transform:"scale(0.8)",transformOrigin:"top left",pointerEvents:"none"},children:s.jsx("div",{dangerouslySetInnerHTML:{__html:n},style:{width:"100%",height:"100%",overflow:"hidden"}})})})},(e,t)=>e.style.id===t.style.id&&e.style.name===t.style.name&&e.style.accent===t.style.accent&&e.style.description===t.style.description&&e.style.style===t.style.style&&e.style.visualTone===t.style.visualTone);function op({mode:e,editingId:t,cloneFromStyle:n,onClose:i,onToast:r}){const o=J(C=>C.customInstructions),a=J(C=>C.addCustomInstruction),l=J(C=>C.updateCustomInstruction),d=t?o.find(C=>C.id===t):null,[c,p]=v.useState((d==null?void 0:d.name)??(n==null?void 0:n.name)??""),[f,g]=v.useState((d==null?void 0:d.content)??(n==null?void 0:n.style)??""),[x,h]=v.useState((d==null?void 0:d.accent)??(n==null?void 0:n.accent)??"#6366f1"),[u,y]=v.useState((d==null?void 0:d.description)??(n==null?void 0:n.description)??""),[j,w]=v.useState((d==null?void 0:d.outputType)??(n==null?void 0:n.outputType)??"长页"),[N,b]=v.useState((d==null?void 0:d.visualTone)??(n==null?void 0:n.visualTone)??"极简"),$=()=>{if(!c.trim()||!f.trim())return;const C={name:c.trim(),content:f.trim(),accent:x,description:u.trim(),outputType:j,visualTone:N,mode:e};t&&d?(l(t,C),i()):a(C)?i():r("自定义指令已达上限 (50条)，请先删除部分旧指令再保存。")},S=/^#[0-9a-fA-F]{6}$/.test(x);return s.jsxs("div",{className:"flex flex-col gap-4 p-4",children:[s.jsx("h3",{className:"text-[15px] font-bold text-slate-800",children:t?"编辑自定义指令":"新增自定义指令"}),s.jsxs("div",{children:[s.jsx("label",{className:"mb-1 block text-[12px] font-semibold text-slate-500",children:"指令名称 *"}),s.jsx("input",{type:"text",value:c,onChange:C=>p(C.target.value),placeholder:"如：科技蓝色商务风",maxLength:50,className:"w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-[14px] text-slate-800 placeholder:text-slate-400 focus:border-[var(--accent)] focus:outline-none focus:ring-1 focus:ring-[var(--accent)]/30"})]}),s.jsxs("div",{children:[s.jsx("label",{className:"mb-1 block text-[12px] font-semibold text-slate-500",children:"简短描述"}),s.jsx("input",{type:"text",value:u,onChange:C=>y(C.target.value),placeholder:"一句话描述这个风格的特点",maxLength:100,className:"w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-[14px] text-slate-800 placeholder:text-slate-400 focus:border-[var(--accent)] focus:outline-none focus:ring-1 focus:ring-[var(--accent)]/30"})]}),s.jsxs("div",{className:"grid grid-cols-3 gap-3",children:[s.jsxs("div",{children:[s.jsx("label",{className:"mb-1 block text-[12px] font-semibold text-slate-500",children:"强调色"}),s.jsxs("div",{className:"flex items-center gap-2",children:[s.jsx("input",{type:"color",value:S?x:"#6366f1",onChange:C=>h(C.target.value),"aria-label":"强调色选择器",className:"h-9 w-9 cursor-pointer rounded border border-slate-200"}),s.jsx("input",{type:"text",value:x,onChange:C=>h(C.target.value),"aria-label":"强调色十六进制值",className:"flex-1 rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-[13px] font-mono text-slate-700 focus:border-[var(--accent)] focus:outline-none"})]})]}),s.jsxs("div",{children:[s.jsx("label",{className:"mb-1 block text-[12px] font-semibold text-slate-500",children:"输出类型"}),s.jsx("select",{value:j,onChange:C=>w(C.target.value),"aria-label":"输出类型",className:"w-full rounded-lg border border-slate-200 bg-white px-2 py-2 text-[13px] text-slate-700 focus:border-[var(--accent)] focus:outline-none",children:xr.map(C=>s.jsx("option",{value:C,children:C},C))})]}),s.jsxs("div",{children:[s.jsx("label",{className:"mb-1 block text-[12px] font-semibold text-slate-500",children:"视觉气质"}),s.jsx("select",{value:N,onChange:C=>b(C.target.value),"aria-label":"视觉气质",className:"w-full rounded-lg border border-slate-200 bg-white px-2 py-2 text-[13px] text-slate-700 focus:border-[var(--accent)] focus:outline-none",children:ct.map(C=>s.jsx("option",{value:C,children:C},C))})]})]}),s.jsxs("div",{children:[s.jsxs("label",{className:"mb-1 block text-[12px] font-semibold text-slate-500",children:["指令内容 * ",s.jsx("span",{className:"font-normal text-slate-400",children:"（设计系统令牌描述，最多 5000 字符）"})]}),s.jsx("textarea",{value:f,onChange:C=>g(C.target.value.slice(0,5e3)),rows:10,placeholder:`在此输入你的设计系统令牌描述，如：
【视觉主题】...
【色彩系统】...
【排版规则】...`,className:"w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-[13px] leading-relaxed text-slate-800 font-mono placeholder:text-slate-400 focus:border-[var(--accent)] focus:outline-none focus:ring-1 focus:ring-[var(--accent)]/30 resize-y"}),s.jsxs("div",{className:"mt-1 text-right text-[11px] text-slate-400",children:[f.length," / 5000"]})]}),s.jsxs("div",{className:"flex items-center justify-end gap-2 pt-2",children:[s.jsx("button",{onClick:i,className:"rounded-lg border border-slate-200 bg-white px-4 py-2 text-[13px] font-medium text-slate-600 hover:bg-slate-50 transition-colors",children:"取消"}),s.jsx("button",{onClick:$,disabled:!c.trim()||!f.trim(),className:"rounded-lg px-5 py-2 text-[13px] font-semibold text-white transition-opacity hover:opacity-90 disabled:opacity-50",style:{backgroundColor:x},children:t?"保存修改":"保存指令"})]})]})}const Y=e=>"`"+e+"`";function ap(e){return!e||e.length===0?"  （无属性）":e.map(t=>{const n=t.required?"【必填】":"【可选】",i=t.default?`，默认：${t.default}`:"",r=t.options&&t.options.length?`，可选值：${t.options.join(" / ")}`:"";return`  - ${t.key} ${n} ${t.label}${i}${r}`}).join(`
`)}function lp(){const e=new Map;for(const i of Ze){const r=e.get(i.tag)??[];r.push(i),e.set(i.tag,r)}const t=[];let n=1;for(const[i,r]of e){const o=Array.from(new Set(r.map(d=>d.name))).join(" / "),a=[];a.push(`### ${n}. \`<${i}>\`  ${o}`),r.forEach(d=>{const c=r.length>1?`（${d.id}）`:"";d.example&&a.push(`示例${c}：
${d.example}`)});const l=new Map;r.forEach(d=>{var c;return(c=d.attrs)==null?void 0:c.forEach(p=>{l.has(p.key)||l.set(p.key,p)})}),a.push("属性："),a.push(ap(Array.from(l.values()))),t.push(a.join(`
`)),n+=1}return t.join(`

`)}const cp=`## 二、行内强调语法（写在正文里）

- ==文字==        渐变背景强调（主题色。注：强调强度大于加粗）
- !!文字!!        胶囊标签背景（圆角药丸）
- ^^文字^^        靛青/主题色加重强调
- ::文字::        柔光主题色重点文字
- **文字**        粗体
- *文字*          斜体
- ***文字***      粗体 + 斜体
- __文字__        主题色下划线
- ${Y("<u>文字</u>")}     普通下划线
- ~~文字~~        删除线
- ~文字~          下标（如 H~2~O）
- ^文字^          上标（如 m^2^）
- \`文字\`          行内代码
- ${Y('<Badge type="info" text="标签" />')}  行内徽章（type 可选 info/tip/warning/danger）
- ${Y('<Icon name="material-symbols:star" />')}  行内图标`,dp=`## 一、标准 Markdown

- 标题：# 一级 / ## 二级 / ### 三级 / #### 四级
- 无序列表用 - ，有序列表用 1. ；支持引用 > 、表格、分隔线
- 任务列表：- [x] 已完成   - [ ] 未完成
- 代码块：用三个反引号包裹，必须标注语言，例如 \`\`\`javascript
  - 系统会自动做代码高亮和自动换行，不需要额外写 HTML 样式
- 图片：![描述](图片地址)
  - 限制尺寸：![描述](图片地址)[100% 250px]  （格式为 [宽度 高度]，可超出部分滚动）
- 多图横向并排（左右滑动）：< ![图1](地址1), ![图2](地址2), ![图3](地址3) >
- 流程图：用 mermaid 代码块绘制，系统自动渲染为 SVG 图表：
  \`\`\`mermaid
  flowchart LR
    A --> B --> C
  \`\`\`
  - 流程图图注写在代码块下方，写法和图片图注一样：\`图 1: xxx\` 或 \`Fig. 2. xxx\`，系统会自动识别并居中显示。`,pp=`## 输出结构（必须遵守）

请先输出文章元信息，再输出正文。推荐使用 YAML frontmatter：

---
title: 这里写适合平台展示的标题
summary: 这里写 50-120 字摘要，便于单独复制到平台简介/导语
---

正文从这里开始。

要求：
- title 必须是可直接发布的标题，不要超过 30 个汉字。
- summary 必须概括正文核心信息，不要写成宣传口号。
- 正文继续使用下面的扩展 Markdown 语法。
- 不要把整篇文章包在代码块里。`,xn=`## 输出结构（必须遵守）

请先输出文档元信息，再输出正文。推荐使用 YAML frontmatter：

---
title: 这里写正式文档标题
summary: 这里写 50-120 字摘要，概括文档目的、范围和结论
---

正文从这里开始。

要求：
- title 必须是正式、清晰、可直接作为文件名或封面标题的名称。
- summary 必须客观概括文档内容，不要写成营销口号。
- 正文只使用标准 Markdown 与本文档指令中允许的扩展标记。
- 不要把整篇文档包在代码块里。`,fp=`## 三、提示框（Callout）

> [TIP] 这里是标题
> 这里是提示框正文内容

可用类型：[TIP] / [NOTE] / [INFO] / [WARNING] / [CAUTION] / [IMPORTANT]`,St=`## 五、数学公式（KaTeX）

- 行内公式：用单个美元符号包裹，例如 $E=mc^2$
- 块级公式（独占一行、居中显示）：用两个美元符号包裹，例如：
$$
\\int_0^1 x^2 \\,dx = \\frac{1}{3}
$$
- 公式语法遵循 LaTeX / KaTeX 规范。`,gp=`## 六、使用规则（重要）

1. 只能使用上面列出的语法与标签，不要发明新标签或新属性。不要直接混入 ${Y("<script>")}、事件处理器属性（如 \`onclick\`）、\`javascript:\` 链接或未列出的任意 HTML 标签，以免造成渲染异常或安全风险。
2. 组件标签写法与普通 HTML 一致：<tag 属性="值">内容</tag> 或自闭合 <tag ...></tag>。
3. 绝大多数属性都是可选的，不确定时可以省略，会使用默认值。
4. 颜色值可用十六进制（如 #e74c3c）或预设名（如 red/green/yellow），留空则跟随全局主题色。
5. ${Y("<steps>")}、${Y("<engage>")}、${Y("<title>")} 存在多种样式变体，用 type 属性切换（如 type="DA02"）。
6. 双栏对比请使用 ${Y(":::compare")} 容器语法（注意：不是 <compare> 标签）：
   \`\`\`
   :::compare
   维度 | A 方描述 | B 方描述 | accent
   另一维度 | A 方描述 | B 方描述 | default
   :::
   \`\`\`
   每行三列加可选颜色列，最后一列写 accent 的行会整行主题色高亮，写 default 为普通白底。
7. 直接输出可粘贴的 Markdown 正文，不要额外解释，不要用代码块把整篇文章包起来。
8. 合理搭配组件：开头可用 ${Y("<title>")} 或 ${Y("<breaking>")}，结尾推荐使用 ${Y('<engage type="DA02">')}（彩色引导卡片样式）。注意：不要轻易/频繁使用 ${Y("<statement>")} 居中强调语，仅在高度总结的观点或核心金句时才克制使用，正文穿插 ${Y("<steps>")}、${Y("<timeline>")} 等增强可读性。
9. ${Y("<steps>")} 步骤流规则：
   - active 属性控制强调：active="2" 仅第2步强调（默认 active="1"）；active="all" 全部步骤强调；active="none" 全部不强调。
   - 步骤超过3个时，系统自动切换为竖向布局（DA02）；也可以主动指定 type="DA02"。
   - 2–3步用默认横向布局（DA01）即可，4步及以上建议主动写 type="DA02"。`,un="## 一、标准 Markdown 与文档规范\n\n- 标题：# 一级标题（用作文档主标题）/ ## 二级标题 / ### 三级标题 / #### 四级标题\n  - 说明：第一个、最大的一级标题会作为文档主标题居中展示；后续章节标题保持正式文档的左对齐层级。\n- 列表与引用：无序列表用 - ，有序列表用 1. ；支持引用 > 以及水平分割线。\n- 强制分页：在需要强行换页（例如分隔封面页、目录页、新章节、附录）处，写一行 \\`<page-break>\\` 或 \\`<page-break />\\`。**附录前必须分页。**\n- 代码块：使用三个反引号包裹并标注语言，例如 ```javascript\n- 流程图：用 ```mermaid 代码块绘制，系统自动渲染为 SVG 图表，图注写在代码块下方（见下方题注说明）。\n- 段首空格：如需保留段首空格，请直接在段落开头输入全角空格或半角空格；系统会按文档模式保留这些空格。\n- 图片与表格题注（Caption）自动居中：\n  - **图片题注只能写在图片下方**，形如 `图 1: xxxx` 或 `Fig. 2. xxxx`；写在图片上方会被当作普通段落。\n  - **表格题注只能写在表格上方**，形如 `表 1: xxxx` 或 `Table 2. xxxx`；写在表格下方会被当作普通段落。\n  - **Mermaid 流程图题注写在代码块下方**，写法和图片题注一样：`图 1: xxxx` 或 `Fig. 2. xxxx`，系统会自动识别并居中显示。\n  - **写法建议**：优先使用普通独立行作为题注；如写成 `**图 1: xxxx**` 或 `**表 1: xxxx**`，系统也会识别为题注并居中。\n  - **编号规则**：图题和表题分别独立编号，可以同时存在图 1 和表 1，不要把表格编号接在图片编号后面。\n  - **系统表现**：系统会自动识别这些符合前缀的单独行，并用 `document-caption` 题注标识渲染为居中、灰色小字，且自动贴合相邻的图表（首行缩进对其无效）。",hr=`## 四、排版规范与要求（重要）

1. **不要使用**长图文里的社交互动组件（如 ${Y("<breaking>")}、${Y("<timeline>")}、${Y("<engage>")} 等），保持文档正式、严谨、适合打印和归档。不要直接混入 ${Y("<script>")}、事件处理器属性（如 \`onclick\`）、\`javascript:\` 链接或未列出的任意 HTML 标签，以免造成渲染异常或安全风险。
2. 表格首行（表头）内容默认会强制居中，表格体内容默认左对齐。
3. **附录必须另起一页**：在附录（如"附录 A"、"参考文献"、"术语表"等）之前必须插入 \\\`<page-break />\\\`，确保附录从新页面开始。
4. **长文档建议按大章节分页**：当文档篇幅较长时，可在每个一级或二级大章节（\\\`##\\\`）之前插入 \\\`<page-break />\\\`，使每章从新页开始，提升可读性。短文档不必强求。
5. 不要在正文中写死颜色、字号、字体或 HTML 样式；系统会统一使用导航栏主题色与 A4 文档样式。
6. 直接输出可粘贴的 Markdown 正文，不要有任何多余的解释，不要用代码块包住整篇文档。`,xp=`## 三、封面页写法

如果文档需要封面页，在第一个 \\\`<page-break/>\\\` 之前只写一个一级标题和一个信息表格。系统会自动识别封面页并将内容在垂直方向等距分布（标题到页眉、标题到表格、表格到页脚的间距相等）。

封面页参考格式：

\\\`\\\`\\\`
# 文档正式标题

| 文档编号 | XXX-DOC-2026-001 | 版本号 | V1.0 |
| --- | --- | --- | --- |
| 编写 | 编写人/编写组 | 编写日期 | 2026-06-11 |
| 审核 | 审核人/审核组 | 审核日期 | 2026-06-12 |
| 发布状态 | 草稿/已发布 | 机密等级 | 绝密/机密/内部公开/授权公开/公开 |

<page-break/>
\\\`\\\`\\\`

封面页要求：
- 一级标题只能有一个，作为文档主标题。
- 信息表格采用四列双键值对格式（字段名 | 值 | 字段名 | 值），每行放两组字段，共四行。字段可根据实际情况增减。
- 封面页内不要使用列表、代码块、图片等非标题/表格元素。
- \\\`<page-break/>\\\` 之后开始写正文。`,up=`## 三、封面页写法

**本文档不需要封面页。** 请直接从正文开始，不要在开头插入 \\\`<page-break/>\\\`。

- 第一个一级标题作为文档主标题居中展示。
- 标题后紧随正文内容，不要生成"文档编号、版本号、编写者"等信息表格。
- 不要在文档开头插入分页符或封面元数据表格。`;function mp(e){return!!(e.docNo||e.version||e.author||e.authorDate||e.reviewer||e.reviewDate||e.status||e.classification)}function br(e){if(!mp(e))return"";const t=[],n=(i,r,o,a)=>{(r||a)&&t.push(`| ${i} | ${r} | ${o} | ${a} |`)};return n("文档编号",e.docNo||"","版本号",e.version||""),n("编写",e.author||"","编写日期",e.authorDate||""),n("审核",e.reviewer||"","审核日期",e.reviewDate||""),n("文档状态",e.status||"","机密等级",e.classification||""),t.length===0?"":`## 六、封面元数据（用户已确认，请直接使用）

请使用以下元数据生成封面页，表格后必须插入 ${Y("<page-break/>")} 再开始正文：

\`\`\`
# 文档标题（请根据素材生成，不要使用占位符）

${t.join(`
`)}
| --- | --- | --- | --- |

<page-break/>
\`\`\`

要求：
- 上述元数据字段已由用户确认，请原样填入封面页表格，不要修改、补全或省略已填写的字段。
- 文档标题请根据素材自动生成。
- 表格采用四列双键值对格式，字段顺序与上面一致；未填写的字段可省略整行。
- 表格后必须插入 ${Y("<page-break/>")} 分页符，再开始正文。`}function hp(e){if(!(e.issuer||e.docNo||e.classification||e.urgency||e.signer||e.recipient||e.publishDate))return"";const n=[];e.issuer&&n.push(`issuer="${e.issuer}"`),e.docNo&&n.push(`doc-no="${e.docNo}"`),e.classification&&n.push(`classification="${e.classification}"`),e.urgency&&n.push(`urgency="${e.urgency}"`),e.signer&&n.push(`signer="${e.signer}"`);const i=[];return i.push("## 六、公文元数据（用户已确认，请直接使用）"),i.push(""),i.push(`请使用以下元数据生成 ${Y("<gov-header>")} 标签：`),i.push(""),i.push("```"),i.push(`<gov-header ${n.join(" ")}></gov-header>`),i.push("```"),i.push(""),e.recipient&&(i.push(`主送机关：${e.recipient}`),i.push("")),e.publishDate&&(i.push(`成文日期：${e.publishDate}`),i.push("")),i.push("要求："),i.push(`- 上述元数据已由用户确认，请原样填入 ${Y("<gov-header>")} 标签属性，不要修改、补全或省略已填写的字段。`),i.push("- 公文标题请根据素材自动生成，不要使用占位符。"),e.recipient&&i.push("- 主送机关请使用上述确认的内容，后跟全角冒号。"),e.publishDate&&i.push("- 落款日期请使用上述确认的成文日期。"),i.join(`
`)}function qt(){return["# 长图文排版 Markdown 语法指令","","你是一位长图文内容策划与排版助手。请把我提供的素材整理成适合公众号、知识长图或图文平台发布的长文章，","并严格使用下面这套「扩展 Markdown 语法」输出，方便后续一键渲染、复制富文本或导出长图。","","写作目标：先搭好文章结构，再安排视觉节奏。标题要清楚，摘要要能独立传播，正文要有层次、有重点、有可读性。","",pp,"",dp,"",cp,"",fp,"","## 四、块级组件（直接以标签形式写在正文中）","",lp(),"",St,"",gp,"","## 七、内容组织建议","","1. 开头用 1-2 段说明问题、对象和价值，不要直接堆概念。",'2. 正文按"背景 / 核心观点 / 方法步骤 / 案例或数据 / 总结行动"组织；没有素材时不要编造事实。',"3. 每个二级标题下优先使用短段落、列表、引用或步骤组件，不要生成一整块难读的大段文字。","4. 组件用于强化阅读体验，不要为了炫技过度堆叠；同一屏内避免连续放多个重装饰组件。",`5. 结尾给出清晰总结或行动提示，推荐使用 ${Y('<engage type="DA02">')}（彩色引导卡片样式）引导收藏、关注或分享。`,""].join(`
`)}function mn(e){const t=e&&e.enabled===!1?up:xp,n=e&&e.enabled!==!1?br(e):"",i=["# A4 文档排版 Markdown 语法指令","","你是一位专业的 A4 正式文档编辑与排版助手。请把我提供的素材整理成适合打印、归档、评审或 PDF 交付的正式文档，","并严格使用标准 Markdown 及以下排版规范输出，确保结构清晰、术语克制、版面端庄。","",xn,"",un,"",t,"",hr,"",St];return n&&i.push("",n,""),i.join(`
`)}const bp=`## 三、公文头部写法（${Y("<gov-header>")} 标签）

公文必须在正文开始前使用 ${Y("<gov-header>")} 标签渲染红头文件头部。标签属性：

- issuer（必填）：发文机关名称，如"XX市人民政府办公厅"
- doc-no（可选）：发文字号，如"市政发〔2026〕第1号"
- classification（可选）：密级，可选值：绝密 / 机密 / 秘密
- urgency（可选）：紧急程度，可选值：特急 / 加急
- signer（可选）：签发人姓名（仅上行文需要）

示例：

\`\`\`
<gov-header issuer="XX市人民政府办公厅" doc-no="市政发〔2026〕第1号" classification="机密" signer="张三"></gov-header>
\`\`\`

公文头部要求：
- 发文机关名称会以红色大字居中显示，下方有红色分隔线。
- 密级与紧急程度显示在左上角，签发人显示在右上角。
- 所有属性除 issuer 外均为可选，根据实际情况填写。`,yp=`## 四、公文排版规范（重要）

1. **必须使用 ${Y("<gov-header>")} 标签**渲染公文头部，不要用普通标题或表格模拟红头文件。
2. 公文标题（正文第一个一级标题）居中显示，使用二号宋体加粗。
3. 主送机关左顶格，后跟全角冒号，如"各区人民政府，市政府各委、办、局："
4. 正文使用仿宋字体，首行缩进两字，两端对齐。
5. 落款（发文机关署名 + 日期）右对齐，距正文两行。
6. **不要使用**长图文里的社交互动组件（如 ${Y("<breaking>")}、${Y("<timeline>")}、${Y("<engage>")} 等），保持公文严肃性。不要直接混入 ${Y("<script>")}、事件处理器属性（如 \`onclick\`）、\`javascript:\` 链接或未列出的任意 HTML 标签。
7. 附录前必须插入 ${Y("<page-break/>")}。
8. 直接输出可粘贴的 Markdown 正文，不要有任何多余的解释。`;function yr(e){const t=e?hp(e):"",n=["# 公文排版 Markdown 语法指令","","你是一位专业的党政机关公文编辑与排版助手。请把我提供的素材整理成符合 GB/T 9704-2012 标准的正式公文，","并严格使用标准 Markdown 及以下排版规范输出，确保格式规范、用语严谨、适合打印和归档。","",xn,"",un,"",bp,"",yp,"",St];return t&&n.push("",t,""),n.join(`
`)}const vp=`## 三、封面页写法（技术文档）

技术文档可选择是否需要封面页。如需封面，在第一个 ${Y("<page-break/>")} 之前只写一个一级标题和一个信息表格。

封面页元数据字段（全部可选，根据实际情况填写）：

- 文档编号：如 PRD-2026-001
- 版本号：如 V1.0
- 编写者 / 编写日期
- 审核者 / 审核日期
- 文档状态：草稿 / 评审中 / 已发布 / 已归档
- 机密等级：绝密 / 机密 / 内部公开 / 授权公开 / 公开

封面页参考格式：

\`\`\`
# 技术文档标题

| 文档编号 | PRD-2026-001 | 版本号 | V1.0 |
| --- | --- | --- | --- |
| 编写 | 编写人 | 编写日期 | 2026-06-20 |
| 审核 | 审核人 | 审核日期 | 2026-06-21 |
| 文档状态 | 草稿 | 机密等级 | 内部公开 |

<page-break/>
\`\`\`

封面页要求：
- 一级标题只能有一个，作为文档主标题。
- 信息表格采用四列双键值对格式，字段可根据实际情况增减。
- 不需要封面时，直接从正文开始，不要插入 ${Y("<page-break/>")}。`,wp=`## 三、封面页写法（技术文档）

**本文档不需要封面页。** 请直接从正文开始，不要在开头插入 ${Y("<page-break/>")}。

- 第一个一级标题作为文档主标题居中展示。
- 标题后紧随正文内容，不要生成"文档编号、版本号、编写者"等信息表格。
- 不要在文档开头插入分页符或封面元数据表格。`;function vr(e){const t=e&&e.enabled===!1?wp:vp,n=e&&e.enabled!==!1?br(e):"",i=["# 技术文档排版 Markdown 语法指令","","你是一位专业的技术文档编辑与排版助手。请把我提供的素材整理成适合技术评审、归档和交付的正式技术文档，","并严格使用标准 Markdown 及以下排版规范输出，确保结构清晰、术语准确、版面端庄。","",xn,"",un,"",t,"",hr,"",St];return n&&i.push("",n,""),i.join(`
`)}function wr(e){const t="小红书";return[`# ${t}图文卡片 Markdown 生成指令`,"",`请把我提供的素材改写为适合 ${t} 发布的分页图文卡片稿，画布比例为 ${e}。`,"输出必须是 Markdown，不要把整篇包在代码块里，也不要直接输出 HTML。","","核心目标：封面负责吸引点击，内容页负责一页讲清一个重点，发布文案负责承接互动与搜索。","","## 输出结构","","先输出 YAML frontmatter：","","---","title: 适合作为封面大标题的标题，不超过 24 个汉字","summary: 适合作为发布文案摘要的 50-120 字内容","badge: 可选角标，例如 GUIDE / NOTE / 清单","hook: 可选金句或行动提示","chips: 话题1|话题2|话题3","brand: 可选账号名","---","","然后输出正文 Markdown。正文会被自动拆成 N 张内容图，系统会统一生成封面图、发布文案和内容图。","","## 排版规则","","- 使用短段落、清晰小标题和列表，每张图只承载一个重点；不要把多个复杂观点塞进同一页。","- 封面标题要短、明确、有记忆点；正文标题要像目录一样可扫读。","- 内容页优先使用 ## 小标题、列表、引用和短段落；一页建议 3-6 个信息单元。","- 支持标准 Markdown、表格、引用、图片、数学公式；除行内强调外，不要使用长图文模式的复杂社交组件。","- 代码块必须标注语言，例如 ```ts；系统会自动代码高亮并自动换行。",`- 不要直接输出 HTML 样式或混入 ${Y("<script>")}、事件处理器属性（如 \`onclick\`）、\`javascript:\` 链接；需要强调时使用 ==重点==、!!标签!!、^^强强调^^ 等行内语法。`,"- 如果素材不足，请保守改写，不要编造数据、案例或不存在的来源。","- 不要额外解释，只输出可粘贴回编辑器的 Markdown。","","## 分页建议","",'- 第 1 页内容图承接封面，快速说明"为什么要看"。',"- 中间页按步骤、清单、误区、对比或案例展开，每页只突出一个关键词。","- 最后一页做总结、行动建议或收藏理由，方便用户停留和互动。"].join(`
`)}function kr(e){const t=document.createElement("textarea");return t.value=e,t.style.cssText="position:fixed;top:0;left:0;width:1px;height:1px;padding:0;border:none;outline:none;box-shadow:none;opacity:0;",t.setAttribute("readonly","readonly"),document.body.appendChild(t),t}async function Fe(e){try{return await navigator.clipboard.writeText(e),!0}catch{}const t=kr(e);try{return t.focus(),t.select(),t.setSelectionRange(0,t.value.length),document.execCommand("copy")}catch{return!1}finally{document.body.removeChild(t)}}async function $r(e){const t=e.cloneNode(!0),n=t.querySelectorAll("img");for(const i of Array.from(n)){const r=i.getAttribute("src")||"";if(r.startsWith("blob:")||r.startsWith("img://")){const o=r.startsWith("img://")?r.replace("img://",""):qs().get(r)||"";if(o){const a=await Li(o);if(a)try{const l=await to(a);i.setAttribute("src",l)}catch(l){console.error(`Failed to compile image ${o} to base64 during copy:`,l)}}}}return t}async function Sr(e,t){const n=e.querySelectorAll(".m2v-mermaid-figure");for(const i of Array.from(n)){const r=i.closest('section[data-block="mermaid"]');if(r&&t&&t.activeType!=="local")try{const{domToBlob:o}=await $e(async()=>{const{domToBlob:p}=await import("./index-3aiWr_xC.js");return{domToBlob:p}},[]),a=await o(i,{scale:2,type:"image/png",backgroundColor:"#ffffff"});if(!a)continue;const l=new File([a],"mermaid.png",{type:"image/png"}),d=await no(l,t),c=document.createElement("img");c.setAttribute("src",d),c.setAttribute("style","max-width:100%;height:auto;display:block;margin:0 auto;"),r.replaceChildren(c)}catch(o){console.error("[m2v] 上传 mermaid 图表到图床失败:",o)}}return e}async function $f(e,t,n){const i=await $r(e);await Sr(i,n);const o=`<section style="background-color:#fff;color:#333;padding:0${t?`;font-family:${t}`:""}">${i.innerHTML}</section>`,a=i.innerText;try{const l=new ClipboardItem({"text/html":new Blob([o],{type:"text/html"}),"text/plain":new Blob([a],{type:"text/plain;charset=utf-8"})});return await navigator.clipboard.write([l]),!0}catch{}try{const l=document.createElement("div");l.innerHTML=o,l.style.cssText="position:fixed;top:0;left:0;width:1px;height:1px;opacity:0;overflow:hidden;",document.body.appendChild(l);const d=document.createRange();d.selectNodeContents(l);const c=window.getSelection();c==null||c.removeAllRanges(),c==null||c.addRange(d);const p=document.execCommand("copy");return c==null||c.removeAllRanges(),document.body.removeChild(l),p}catch{return!1}}async function Sf(e,t){const n=await $r(e);await Sr(n,t);const i=n.innerHTML;try{return await navigator.clipboard.writeText(i),!0}catch{}try{const r=kr(i);r.focus(),r.select(),r.setSelectionRange(0,r.value.length);const o=document.execCommand("copy");return document.body.removeChild(r),o}catch{return!1}}const ae=v.forwardRef(({className:e="",...t},n)=>{const r=["h-8 rounded-md border border-slate-200 bg-white px-2.5 text-[13px] text-slate-700 outline-none transition-colors focus:border-[var(--accent)] focus-visible:ring-2 focus-visible:ring-[var(--accent)]/30 disabled:bg-slate-50 disabled:opacity-50",e].filter(Boolean).join(" ");return s.jsx("input",{ref:n,className:r,...t})});ae.displayName="Input";const jr=v.forwardRef(({className:e="",...t},n)=>{const r=["h-8 rounded-md border border-slate-200 bg-white px-2 py-0 text-[13px] text-slate-700 outline-none transition-colors focus:border-[var(--accent)] focus-visible:ring-2 focus-visible:ring-[var(--accent)]/30 disabled:bg-slate-50 disabled:opacity-50",e].filter(Boolean).join(" ");return s.jsx("select",{ref:n,className:r,...t})});jr.displayName="Select";const kp={article:[{id:"article-default",name:"公众号长图文",category:"长图文",accent:"#07C160",description:"适合微信公众号、长图文发布，支持各种交互组件与优雅排版。",outputType:"长页",visualTone:"编辑",family:"heiti",displayLevel:"primary",style:qt(),previewHtml:'<div style="padding: 20px; font-family: sans-serif; font-size: 14px; color: #333; line-height: 1.6; max-width: 400px; margin: 0 auto; background: #f9f9f9; border-radius: 8px;"> <h1 style="font-size: 18px; margin-bottom: 12px; color: #000;">公众号长图文排版示例</h1> <p>这是一种极其优雅的图文排版模式，支持高亮、下划线、卡片以及复杂的步骤组件。非常适合用来做长图文输出。</p> </div>'}],document:[{id:"document-default",name:"正式文档",category:"文档",accent:"#2563eb",description:"适合 A4 打印、报告导出、正式文档交付，自动分页与页眉页脚。",outputType:"文档",visualTone:"极简",family:"songti",displayLevel:"primary",style:mn(),previewHtml:'<div style="padding: 30px; font-family: serif; font-size: 13px; color: #000; line-height: 1.8; width: 300px; margin: 0 auto; background: #fff; box-shadow: 0 4px 12px rgba(0,0,0,0.1);"> <div style="text-align: center; font-size: 18px; font-weight: bold; margin-bottom: 20px;">正式研究报告</div> <p style="text-indent: 2em; text-align: justify;">这是一种适合严肃阅读、打印和归档的A4排版风格，支持题注自动居中、强制分页等正式排版特性。</p> </div>'},{id:"document-tech",name:"极简报告",category:"文档",accent:"#0891b2",description:"适合 PRD、技术方案、设计文档，支持封面元数据（文档编号、版本号、审核者等）。",outputType:"文档",visualTone:"极简",family:"songti",displayLevel:"primary",style:vr(),previewHtml:'<div style="padding: 20px; font-family: sans-serif; font-size: 12px; color: #1e293b; line-height: 1.6; width: 280px; margin: 0 auto; background: #fff; border: 1px solid #cbd5e1; border-radius: 8px;"> <div style="display: flex; justify-content: space-between; margin-bottom: 10px;"> <span style="font-weight: bold;">PRD-2026</span> <span style="background: rgba(8,145,178,0.12); color: #0891b2; padding: 2px 6px; border-radius: 3px; font-size: 10px;">DRAFT</span> </div> <div style="font-size: 15px; font-weight: bold; margin-bottom: 8px;">技术方案文档</div> <div style="font-size: 10px; color: #64748b;">版本 V1.0 | 编写: 张三 | 审核: 李四</div> </div>'},{id:"document-gov",name:"严肃公文",category:"文档",accent:"#c0202c",description:"符合 GB/T 9704-2012 标准的党政机关公文，红头文件、发文字号、密级标注。",outputType:"文档",visualTone:"极简",family:"fangsong",displayLevel:"primary",style:yr(),previewHtml:'<div style="padding: 18px; font-family: FangSong, serif; font-size: 11px; color: #000; line-height: 1.8; width: 280px; margin: 0 auto; background: #fff;"> <div style="text-align: center; font-size: 18px; font-weight: bold; color: #c0202c; font-family: SimSun, serif; margin-bottom: 4px;">XX市人民政府办公厅</div> <div style="text-align: center; font-size: 10px; margin-bottom: 4px;">市政发〔2026〕第1号</div> <div style="height: 2px; background: #c0202c; margin-bottom: 10px;"></div> <div style="text-align: center; font-size: 13px; font-weight: bold; margin-bottom: 8px;">关于推进数字经济发展的通知</div> <div style="text-indent: 2em; text-align: justify;">各区人民政府，市政府各委、办、局：为深入贯彻数字经济发展战略...</div> </div>'}],card:[{id:"card-default",name:"小红书卡片",category:"卡片",accent:"#ff2442",description:"适合小红书等多图文卡片社交平台，一键生成封面与多张内容图。",outputType:"卡片",visualTone:"科技",family:"sans-serif",displayLevel:"primary",style:wr("3:4"),previewHtml:'<div style="padding: 20px; font-family: sans-serif; font-size: 14px; color: #333; line-height: 1.6; max-width: 250px; margin: 0 auto; background: #fff; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); display: flex; flex-direction: column; align-items: center; justify-content: center; height: 333px; border-bottom: 4px solid #ff2442;"> <h1 style="font-size: 20px; margin-bottom: 8px; color: #ff2442; text-align: center;">吸睛大标题</h1> <p style="text-align: center; color: #666;">直击痛点的短文案</p> </div>'}]},$p=[{key:"docNo",label:"文档编号",type:"text",placeholder:"如 PRD-2026-001"},{key:"version",label:"版本号",type:"text",placeholder:"如 V1.0"},{key:"author",label:"编写者",type:"text",placeholder:"编写人姓名"},{key:"authorDate",label:"编写日期",type:"date"},{key:"reviewer",label:"审核者",type:"text",placeholder:"审核人姓名"},{key:"reviewDate",label:"审核日期",type:"date"},{key:"status",label:"文档状态",type:"select",options:["","草稿","评审中","已发布","已归档"]},{key:"classification",label:"机密等级",type:"select",options:["","绝密","机密","内部公开","授权公开","公开"]}],Sp=[{key:"issuer",label:"发文机关",type:"text",placeholder:"XX市人民政府办公厅"},{key:"docNo",label:"发文字号",type:"text",placeholder:"如 市政发〔2026〕第1号"},{key:"classification",label:"密级",type:"select",options:["","绝密","机密","秘密"]},{key:"urgency",label:"紧急程度",type:"select",options:["","特急","加急"]},{key:"signer",label:"签发人",type:"text",placeholder:"签发人姓名（上行文）"},{key:"recipient",label:"主送机关",type:"text",placeholder:"如 各区人民政府，市政府各委、办、局"},{key:"publishDate",label:"成文日期",type:"date"}];function jp({mode:e,open:t,onClose:n,onCopy:i,onToast:r}){const[o,a]=v.useState("builtin"),[l,d]=v.useState("幻灯片"),[c,p]=v.useState("全部"),[f,g]=v.useState(!1),[x,h]=v.useState(!1),[u,y]=v.useState(null),[j,w]=v.useState(null),[N,b]=v.useState(null),[$,S]=v.useState({enabled:!0}),[C,O]=v.useState({}),[F,Q]=v.useState(!1),[W,Z]=v.useState("cover"),I=v.useMemo(()=>Object.entries($).filter(([T,ie])=>T!=="enabled"&&!!ie).length,[$]),L=v.useMemo(()=>Object.entries(C).filter(([,T])=>!!T).length,[C]),B=J(T=>T.customInstructions),G=J(T=>T.removeCustomInstruction),se=v.useMemo(()=>B.filter(T=>(T.mode||"html")===e),[B,e]),M=v.useMemo(()=>e!=="html"?kp[e]||[]:Zd.filter(T=>!(T.outputType!==l||c!=="全部"&&T.visualTone!==c||!f&&T.displayLevel==="basic")),[e,l,c,f]),q=v.useMemo(()=>{const T={};return M.forEach(ie=>{T[ie.visualTone]||(T[ie.visualTone]=[]),T[ie.visualTone].push(ie)}),Object.entries(T).sort((ie,oe)=>ct.indexOf(ie[0])-ct.indexOf(oe[0]))},[M]),ce=async T=>{await Fe(T.content)&&r&&r("复制指令成功")},H=T=>e!=="document"?T:T.id==="document-default"?{...T,style:mn($)}:T.id==="document-tech"?{...T,style:vr($)}:T.id==="document-gov"?{...T,style:yr(C)}:T,re=(T,ie)=>{S(oe=>({...oe,[T]:ie}))},de=(T,ie)=>{O(oe=>({...oe,[T]:ie}))};return s.jsxs(s.Fragment,{children:[s.jsx("div",{onClick:n,className:`fixed inset-0 z-40 bg-slate-900/40 backdrop-blur-sm transition-opacity ${t?"opacity-100":"pointer-events-none opacity-0"}`}),s.jsxs("aside",{className:`fixed left-1/2 top-1/2 z-50 flex w-[90vw] max-w-[1000px] max-h-[85vh] -translate-x-1/2 -translate-y-1/2 flex-col bg-slate-50 shadow-2xl transition-all duration-200 ease-out rounded-xl overflow-hidden ${t?"opacity-100 scale-100":"pointer-events-none opacity-0 scale-95"}`,children:[s.jsxs("header",{className:"flex items-center justify-between border-b border-slate-200 bg-white px-6 py-4",children:[s.jsxs("div",{children:[s.jsxs("div",{className:"flex items-center gap-2 text-lg font-bold text-slate-900",children:[s.jsx(cn,{size:18})," 风格指令库"]}),s.jsxs("div",{className:"mt-1 flex items-center gap-2 text-[13px] text-slate-500",children:[s.jsxs("span",{className:"rounded bg-blue-50 px-1.5 text-blue-700 font-semibold flex items-center gap-0.5",children:[s.jsx("svg",{width:"12",height:"12",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2.5",strokeLinecap:"round",strokeLinejoin:"round",children:s.jsx("path",{d:"m9 18 6-6-6-6"})}),"工作流"]}),s.jsx("span",{children:"1. 选一个喜欢的风格"}),s.jsx("span",{className:"text-slate-300",children:"→"}),s.jsx("span",{children:"2. 复制提示词发给 AI"}),s.jsx("span",{className:"text-slate-300",children:"→"}),s.jsx("span",{children:"3. 将生成的 HTML/Markdown 贴回系统渲染"})]})]}),s.jsx("button",{onClick:n,className:"rounded-lg p-2 text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition-colors","aria-label":"关闭",children:s.jsxs("svg",{width:"20",height:"20",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2",strokeLinecap:"round",strokeLinejoin:"round",children:[s.jsx("line",{x1:"18",y1:"6",x2:"6",y2:"18"}),s.jsx("line",{x1:"6",y1:"6",x2:"18",y2:"18"})]})})]}),s.jsxs("div",{className:"flex border-b border-slate-200 bg-white",children:[s.jsx("button",{onClick:()=>a("builtin"),className:`flex-1 py-3 text-[14px] font-semibold transition-colors ${o==="builtin"?"border-b-2 border-[var(--accent)] text-slate-900":"text-slate-500 hover:text-slate-700"}`,children:le.promptLibrary.builtinTab}),s.jsxs("button",{onClick:()=>a("custom"),className:`flex-1 py-3 text-[14px] font-semibold transition-colors ${o==="custom"?"border-b-2 border-[var(--accent)] text-slate-900":"text-slate-500 hover:text-slate-700"}`,children:[le.promptLibrary.customTab," ",se.length>0&&s.jsx("span",{className:"ml-1 rounded-full bg-slate-200 px-1.5 py-0.5 text-[11px]",children:se.length})]})]}),o==="builtin"&&s.jsxs(s.Fragment,{children:[e==="html"&&s.jsxs("div",{className:"border-b border-slate-200 bg-white px-6 py-4",children:[s.jsx("div",{className:"mb-3 text-xs font-semibold text-slate-400",children:le.promptLibrary.outputTypeLabel}),s.jsx("div",{className:"flex flex-wrap gap-2",children:xr.map(T=>s.jsx("button",{onClick:()=>d(T),className:`rounded-md px-3 py-1.5 text-[13px] font-semibold transition-colors ${l===T?"bg-slate-900 text-white":"bg-slate-100 text-slate-600 hover:bg-slate-200"}`,children:T},T))}),s.jsx("div",{className:"mt-4 mb-3 text-xs font-semibold text-slate-400",children:le.promptLibrary.visualToneLabel}),s.jsxs("div",{className:"flex flex-wrap items-center gap-2",children:[["全部",...ct].map(T=>s.jsx("button",{onClick:()=>p(T),className:`rounded-md px-3 py-1.5 text-[13px] font-medium transition-colors ${c===T?"bg-[var(--accent)] text-white":"bg-slate-100 text-slate-600 hover:bg-slate-200"}`,children:T},T)),s.jsxs("label",{className:"ml-auto flex items-center gap-2 text-[13px] text-slate-500",children:[s.jsx("input",{type:"checkbox",checked:f,onChange:T=>g(T.target.checked)}),"显示基础模板"]})]})]}),e==="document"&&s.jsxs("div",{className:"border-b border-slate-200 bg-white px-6 py-4",children:[s.jsxs("button",{onClick:()=>Q(!F),className:"flex w-full items-center justify-between text-left",children:[s.jsxs("div",{className:"flex flex-col gap-1.5 md:flex-row md:items-center md:gap-3",children:[s.jsxs("div",{className:"flex items-center gap-2",children:[s.jsx("span",{className:"h-4 w-1 rounded-full bg-[var(--accent)]"}),s.jsx("span",{className:"text-sm font-semibold text-slate-800",children:"封面与元数据选项"})]}),s.jsxs("div",{className:"flex flex-wrap items-center gap-2",children:[s.jsx("span",{className:`inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium transition-colors ${$.enabled===!1?"bg-slate-100 text-slate-500 border border-slate-200":I>0?"bg-blue-50 text-blue-700 border border-blue-100":"bg-orange-50 text-orange-700 border border-orange-200"}`,children:s.jsxs("span",{className:"flex items-center gap-1.5",children:[$.enabled!==!1&&I===0&&s.jsxs("span",{className:"relative flex h-2 w-2",children:[s.jsx("span",{className:"animate-ping absolute inline-flex h-full w-full rounded-full bg-orange-400 opacity-75"}),s.jsx("span",{className:"relative inline-flex rounded-full h-2 w-2 bg-orange-500"})]}),"正式封面: ",$.enabled===!1?"已禁用":I>0?`已填${I}项`:"待填写"]})}),s.jsx("span",{className:`inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium transition-colors ${L>0?"bg-red-50 text-red-700 border border-red-100":"bg-orange-50 text-orange-700 border border-orange-200"}`,children:s.jsxs("span",{className:"flex items-center gap-1.5",children:[L===0&&s.jsxs("span",{className:"relative flex h-2 w-2",children:[s.jsx("span",{className:"animate-ping absolute inline-flex h-full w-full rounded-full bg-orange-400 opacity-75"}),s.jsx("span",{className:"relative inline-flex rounded-full h-2 w-2 bg-orange-500"})]}),"公文设置: ",L>0?`已填${L}项`:"待填写"]})})]})]}),s.jsx("svg",{width:"16",height:"16",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2",strokeLinecap:"round",strokeLinejoin:"round",className:`text-slate-400 transition-transform ${F?"rotate-180":""}`,children:s.jsx("polyline",{points:"6 9 12 15 18 9"})})]}),F&&s.jsxs("div",{className:"mt-4 space-y-4 border-t border-slate-100 pt-4",children:[s.jsxs("div",{className:"flex border-b border-slate-100",children:[s.jsxs("button",{type:"button",onClick:()=>Z("cover"),className:`pb-2 text-[13px] font-semibold transition-colors relative mr-6 ${W==="cover"?"text-[var(--accent)]":"text-slate-500 hover:text-slate-700"}`,children:["封面元数据",I>0&&s.jsx("span",{className:"ml-1 rounded bg-blue-50 px-1 text-[10px] text-blue-700 border border-blue-100 font-bold",children:I}),W==="cover"&&s.jsx("span",{className:"absolute bottom-0 left-0 right-0 h-[2px] bg-[var(--accent)]"})]}),s.jsxs("button",{type:"button",onClick:()=>Z("gov"),className:`pb-2 text-[13px] font-semibold transition-colors relative ${W==="gov"?"text-[var(--accent)]":"text-slate-500 hover:text-slate-700"}`,children:["公文元数据",L>0&&s.jsx("span",{className:"ml-1 rounded bg-red-50 px-1 text-[10px] text-red-700 border border-red-100 font-bold",children:L}),W==="gov"&&s.jsx("span",{className:"absolute bottom-0 left-0 right-0 h-[2px] bg-[var(--accent)]"})]})]}),s.jsxs("div",{className:"transition-all duration-200",children:[W==="cover"&&s.jsxs("div",{className:"space-y-3",children:[s.jsxs("div",{className:"flex items-center justify-between",children:[s.jsx("span",{className:"text-[11px] font-medium text-slate-400",children:"适用于「正式文档」「极简报告」，控制是否生成独立封面页"}),s.jsxs("label",{className:"flex items-center gap-1.5 text-[12px] text-slate-600 cursor-pointer select-none",children:[s.jsx("input",{type:"checkbox",checked:$.enabled!==!1,onChange:T=>S(ie=>({...ie,enabled:T.target.checked})),className:"rounded border-slate-300 accent-[var(--accent)] cursor-pointer"}),"生成封面页"]})]}),$.enabled!==!1?s.jsx("div",{className:"grid grid-cols-2 gap-2.5 md:grid-cols-4",children:$p.map(T=>s.jsx(ni,{field:T,value:$[T.key]??"",onChange:ie=>re(T.key,ie)},T.key))}):s.jsx("div",{className:"rounded-lg bg-slate-50 p-3 text-center border border-dashed border-slate-200",children:s.jsx("p",{className:"text-xs text-slate-500 italic",children:"已关闭封面页生成，指令将告知 AI 直接从正文开始。"})})]}),W==="gov"&&s.jsxs("div",{className:"space-y-3",children:[s.jsx("div",{className:"flex items-center justify-between",children:s.jsx("span",{className:"text-[11px] font-medium text-slate-400",children:"适用于「严肃公文」，红头文件专用的发文机关、字号、密级等属性"})}),s.jsx("div",{className:"grid grid-cols-2 gap-2.5 md:grid-cols-4",children:Sp.map(T=>s.jsx(ni,{field:T,value:C[T.key]??"",onChange:ie=>de(T.key,ie)},T.key))})]})]})]})]}),s.jsx("div",{className:"flex-1 overflow-y-auto p-6 lg:p-8",children:s.jsxs("div",{className:"mx-auto space-y-10",children:[q.length===0?s.jsx("div",{className:"rounded-lg border border-dashed border-slate-300 bg-white p-8 text-center text-sm text-slate-500",children:"当前组合下没有推荐风格，可以切换视觉气质，或勾选显示基础模板。"}):null,q.map(([T,ie])=>s.jsxs("section",{children:[s.jsxs("h3",{className:"mb-4 flex items-center gap-2 border-b border-slate-200 pb-2 text-base font-bold text-slate-800",children:[s.jsx("span",{className:"h-4 w-1 rounded-full bg-[var(--accent)]"}),T,"气质",s.jsxs("span",{className:"text-[13px] font-normal text-slate-400",children:["(",ie.length,")"]})]}),s.jsx("div",{className:"grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 lg:gap-5",children:ie.map(oe=>{const pe=oe.category.includes("/")?oe.category.split("/")[1]:"常规";return s.jsxs("div",{className:"group flex h-full flex-col rounded-xl border border-slate-200 bg-white p-4 shadow-sm transition-all hover:-translate-y-0.5 hover:border-slate-300 hover:shadow-md",children:[s.jsx("div",{className:"mb-3",children:s.jsx(ti,{style:oe})}),s.jsxs("div",{className:"mb-2 flex items-center gap-2",children:[s.jsx("span",{className:"h-3 w-3 shrink-0 rounded-full shadow-inner",style:{background:oe.accent}}),s.jsx("span",{className:"font-semibold text-slate-900 truncate",children:oe.name}),s.jsx("span",{className:"ml-auto shrink-0 rounded bg-slate-100 px-1.5 py-0.5 text-[11px] font-medium text-slate-500",children:pe})]}),s.jsx("p",{className:"mb-4 flex-1 text-[13px] leading-relaxed text-slate-600",children:oe.description}),e==="document"&&s.jsx("div",{className:"mt-2 mb-3 rounded-lg bg-slate-50 p-2 text-[11px] text-slate-600 border border-slate-100 flex flex-col gap-0.5",children:oe.id==="document-gov"?s.jsxs(s.Fragment,{children:[s.jsxs("div",{className:"flex items-center gap-1 font-semibold text-red-700",children:[s.jsx("span",{children:"关联：公文配置"}),s.jsx("span",{className:"ml-auto rounded bg-red-50 border border-red-100 px-1 text-[9px] font-bold text-red-700",children:L>0?`已填 ${L} 项`:"未填写"})]}),s.jsx("span",{className:"text-slate-400",children:L>0?"将应用已填写的公文数据生成红头":"当前未配置，生成默认示例红头"})]}):s.jsxs(s.Fragment,{children:[s.jsxs("div",{className:"flex items-center gap-1 font-semibold text-blue-700",children:[s.jsx("span",{children:"关联：封面配置"}),s.jsx("span",{className:"ml-auto rounded bg-blue-50 border border-blue-100 px-1 text-[9px] font-bold text-blue-700",children:$.enabled!==!1?`已启用 (${I}项)`:"已禁用"})]}),s.jsx("span",{className:"text-slate-400",children:$.enabled===!1?"封面已禁用，直接从正文开始":I===0?"将生成默认封面表格 (当前未配置)":"将应用已填写的封面数据"})]})}),s.jsxs("div",{className:"mt-auto flex gap-2",children:[s.jsxs("button",{onClick:()=>i(H(oe)),className:"accent-bg flex flex-1 items-center justify-center gap-1.5 rounded-lg py-2.5 text-[13px] font-semibold text-white opacity-90 transition-all hover:opacity-100 hover:shadow-md hover:ring-2 hover:ring-[var(--accent)] hover:ring-offset-2 active:scale-[0.98] active:opacity-80 shadow-sm",children:[s.jsxs("svg",{width:"14",height:"14",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2",strokeLinecap:"round",strokeLinejoin:"round",children:[s.jsx("rect",{x:"9",y:"9",width:"13",height:"13",rx:"2",ry:"2"}),s.jsx("path",{d:"M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"})]}),le.promptLibrary.copyPrompt.label]}),s.jsx("button",{onClick:()=>b(mr(H(oe))),className:"flex items-center justify-center rounded-lg border border-slate-200 px-2.5 py-2 text-[13px] text-slate-500 hover:bg-slate-50 hover:text-slate-700 transition-colors",title:"预览指令",children:s.jsxs("svg",{width:"14",height:"14",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2",strokeLinecap:"round",strokeLinejoin:"round",children:[s.jsx("path",{d:"M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"}),s.jsx("circle",{cx:"12",cy:"12",r:"3"})]})})]}),s.jsxs("button",{onClick:()=>{w(H(oe)),y(null),a("custom"),h(!0)},className:"mt-2 flex w-full items-center justify-center gap-1.5 rounded-lg py-2 text-[12px] font-medium text-slate-500 border border-slate-200 hover:bg-slate-50 hover:text-slate-700 transition-colors",children:[s.jsxs("svg",{width:"12",height:"12",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2",strokeLinecap:"round",strokeLinejoin:"round",children:[s.jsx("path",{d:"M8 7V5a2 2 0 0 1 2-2h4.5l5.5 5.5v8a2 2 0 0 1-2 2h-2"}),s.jsx("path",{d:"M16 18a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6.5L16 11.5z"})]}),"克隆为自定义"]})]},oe.id)})})]},T))]})})]}),o==="custom"&&s.jsx("div",{className:"flex-1 overflow-y-auto bg-slate-50 p-6 lg:p-8",children:x?s.jsx(op,{mode:e,editingId:u,cloneFromStyle:j,onClose:()=>{h(!1),y(null),w(null)},onToast:r||(()=>{})}):s.jsxs(s.Fragment,{children:[s.jsxs("div",{className:"mb-6 flex items-center justify-between",children:[s.jsx("p",{className:"text-[13px] text-slate-500",children:"可保存自己常用的提示词配置（仅保存在本地），每个模块独立管理。"}),s.jsxs("button",{onClick:()=>{y(null),w(null),h(!0)},className:"flex items-center gap-1.5 rounded-md bg-white px-3 py-1.5 text-[13px] font-medium text-slate-700 shadow-sm transition-colors hover:bg-slate-50 hover:text-slate-900",children:["+ ",le.promptLibrary.addCustom]})]}),se.length===0?s.jsxs("div",{className:"flex flex-col items-center justify-center py-20 text-slate-400",children:[s.jsx("p",{className:"mb-4 text-sm",children:"暂无自定义指令"}),s.jsx("button",{onClick:()=>{y(null),w(null),h(!0)},className:"rounded-full bg-slate-900 px-5 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800",children:le.promptLibrary.addCustom})]}):s.jsx("div",{className:"grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4",children:se.map(T=>s.jsxs("div",{className:"flex h-full flex-col rounded-xl border border-slate-200 bg-white p-4 shadow-sm transition-all hover:-translate-y-0.5 hover:border-slate-300 hover:shadow-md",children:[s.jsx("div",{className:"mb-3",children:s.jsx(ti,{style:{...T,id:T.id,category:`自定义/${T.visualTone}`,displayLevel:"primary",style:T.content,family:"custom"}})}),s.jsxs("div",{className:"mb-2 flex items-center gap-2",children:[s.jsx("span",{className:"h-3 w-3 shrink-0 rounded-full shadow-inner",style:{background:T.accent}}),s.jsx("span",{className:"font-semibold text-slate-900 truncate",children:T.name}),s.jsx("span",{className:"ml-auto shrink-0 rounded bg-amber-50 px-1.5 py-0.5 text-[11px] font-semibold text-amber-700",children:"自定义"})]}),T.description&&s.jsx("p",{className:"mb-4 flex-1 text-[13px] leading-relaxed text-slate-600",children:T.description}),s.jsxs("div",{className:"mt-auto flex gap-2",children:[s.jsxs("button",{onClick:()=>ce(T),className:"flex flex-1 items-center justify-center gap-1.5 rounded-lg bg-[var(--accent)] py-2 text-[13px] font-semibold text-white opacity-90 hover:opacity-100 hover:shadow-md hover:ring-2 hover:ring-[var(--accent)] hover:ring-offset-2 active:scale-[0.98] transition-all",children:[s.jsxs("svg",{width:"14",height:"14",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2",strokeLinecap:"round",strokeLinejoin:"round",children:[s.jsx("rect",{x:"9",y:"9",width:"13",height:"13",rx:"2",ry:"2"}),s.jsx("path",{d:"M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"})]}),le.promptLibrary.copyPrompt.label]}),s.jsx("button",{onClick:()=>b(Yd(T.content)),className:"flex items-center justify-center rounded-lg border border-slate-200 px-2.5 py-2 text-[13px] text-slate-500 hover:bg-slate-50 hover:text-slate-700 transition-colors",title:"预览指令",children:s.jsxs("svg",{width:"14",height:"14",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2",strokeLinecap:"round",strokeLinejoin:"round",children:[s.jsx("path",{d:"M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"}),s.jsx("circle",{cx:"12",cy:"12",r:"3"})]})}),s.jsx("button",{onClick:()=>{y(T.id),h(!0)},className:"rounded-lg border border-slate-200 px-3 py-2 text-[13px] text-slate-600 hover:bg-slate-50 transition-colors",title:le.common.edit,children:s.jsxs("svg",{width:"13",height:"13",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2",strokeLinecap:"round",strokeLinejoin:"round",children:[s.jsx("path",{d:"M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"}),s.jsx("path",{d:"M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"})]})}),s.jsx("button",{onClick:()=>G(T.id),className:"rounded-lg border border-slate-200 px-3 py-2 text-[13px] text-red-500 hover:bg-red-50 transition-colors",title:"删除",children:s.jsxs("svg",{width:"13",height:"13",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2",strokeLinecap:"round",strokeLinejoin:"round",children:[s.jsx("polyline",{points:"3 6 5 6 21 6"}),s.jsx("path",{d:"M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"})]})})]})]},T.id))})]})})]}),N&&s.jsxs($t,{isOpen:!!N,onClose:()=>b(null),ariaLabel:"指令预览",zIndex:60,overlayClassName:"fixed inset-0 bg-slate-900/40 backdrop-blur-sm",panelClassName:"fixed left-1/2 top-1/2 flex w-[90vw] max-w-[680px] max-h-[80vh] -translate-x-1/2 -translate-y-1/2 flex-col rounded-xl bg-white shadow-2xl overflow-hidden",children:[s.jsxs("header",{className:"flex items-center justify-between border-b border-slate-200 px-5 py-3.5",children:[s.jsxs("div",{className:"flex items-center gap-2 text-[15px] font-bold text-slate-900",children:[s.jsxs("svg",{width:"16",height:"16",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2",strokeLinecap:"round",strokeLinejoin:"round",children:[s.jsx("path",{d:"M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"}),s.jsx("circle",{cx:"12",cy:"12",r:"3"})]}),"指令预览"]}),s.jsxs("div",{className:"flex items-center gap-2",children:[s.jsxs("button",{onClick:async()=>{await Fe(N)&&r&&r("已复制指令")},className:"flex items-center gap-1.5 rounded-md bg-slate-900 px-3 py-1.5 text-[12px] font-medium text-white hover:bg-slate-800 transition-colors",children:[s.jsxs("svg",{width:"12",height:"12",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2",strokeLinecap:"round",strokeLinejoin:"round",children:[s.jsx("rect",{x:"9",y:"9",width:"13",height:"13",rx:"2",ry:"2"}),s.jsx("path",{d:"M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"})]}),"复制"]}),s.jsx("button",{onClick:()=>b(null),className:"rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition-colors","aria-label":"关闭预览",children:s.jsxs("svg",{width:"18",height:"18",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2",strokeLinecap:"round",strokeLinejoin:"round",children:[s.jsx("line",{x1:"18",y1:"6",x2:"6",y2:"18"}),s.jsx("line",{x1:"6",y1:"6",x2:"18",y2:"18"})]})})]})]}),s.jsx("pre",{className:"flex-1 overflow-auto p-5 text-[13px] leading-relaxed text-slate-700 whitespace-pre-wrap break-words font-[inherit]",children:N})]})]})}function ni({field:e,value:t,onChange:n}){var r;const i="w-full h-8 rounded-md border border-slate-200 bg-white px-2.5 text-[12px] text-slate-700 outline-none transition-colors focus:border-[var(--accent)] focus-visible:ring-2 focus-visible:ring-[var(--accent)]/30";return s.jsxs("label",{className:"flex flex-col gap-1",children:[s.jsx("span",{className:"text-[11px] font-medium text-slate-600",children:e.label}),e.type==="select"?s.jsx(jr,{value:t,onChange:o=>n(o.target.value),className:i,children:(r=e.options)==null?void 0:r.map(o=>s.jsx("option",{value:o,children:o||"（不填）"},o))}):e.type==="date"?s.jsx(ae,{type:"date",value:t,onChange:o=>n(o.target.value),className:i}):s.jsx(ae,{type:"text",value:t,onChange:o=>n(o.target.value),placeholder:e.placeholder,className:i})]})}const mt=v.forwardRef(({className:e="",variant:t="outline",size:n="sm",...i},r)=>{const o="inline-flex items-center justify-center rounded-md font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)]/30 focus-visible:ring-offset-1 disabled:opacity-50 disabled:pointer-events-none",a={primary:"bg-[var(--accent)] text-white hover:opacity-90 shadow-sm",outline:"border border-slate-200 bg-white text-slate-700 hover:bg-slate-50",ghost:"bg-transparent text-slate-600 hover:bg-slate-100 hover:text-slate-900"},l={sm:"h-8 px-3 text-[13px]",md:"h-9 px-4 text-sm"},d=[o,a[t],l[n],e].filter(Boolean).join(" ");return s.jsx("button",{ref:r,className:d,...i})});mt.displayName="Button";function ii(e){var t,n,i,r,o,a,l,d,c;return{activeType:e.activeType,smmsToken:((t=e.smms)==null?void 0:t.token)||"",ossRegion:((n=e.oss)==null?void 0:n.region)||"",ossKeyId:((i=e.oss)==null?void 0:i.accessKeyId)||"",ossKeySecret:((r=e.oss)==null?void 0:r.accessKeySecret)||"",ossBucket:((o=e.oss)==null?void 0:o.bucket)||"",cosSecretId:((a=e.cos)==null?void 0:a.SecretId)||"",cosSecretKey:((l=e.cos)==null?void 0:l.SecretKey)||"",cosBucket:((d=e.cos)==null?void 0:d.Bucket)||"",cosRegion:((c=e.cos)==null?void 0:c.Region)||"",sendCredentials:e.sendCredentials??!1}}function Np(e){var t,n,i,r,o;return!!((t=e.smms)!=null&&t.token||(n=e.oss)!=null&&n.accessKeyId||(i=e.oss)!=null&&i.accessKeySecret||(r=e.cos)!=null&&r.SecretId||(o=e.cos)!=null&&o.SecretKey)}function Ep({form:e,allowIntranet:t,needsUnlock:n,cryptoOk:i,secureContext:r,vaultExists:o,remember:a,passphrase:l,saveError:d,unlockPass:c,unlocking:p,unlockError:f,onFormChange:g,onRememberChange:x,onPassphraseChange:h,onUnlockPassChange:u,onUnlock:y,onAllowIntranetChange:j}){return s.jsxs(s.Fragment,{children:[s.jsxs("div",{children:[s.jsx("label",{className:"text-[13px] font-semibold text-slate-500 block mb-2",children:"图片上传目的地"}),s.jsx("div",{className:"grid grid-cols-2 sm:grid-cols-3 gap-2",children:[{type:"local",name:"本地 IndexedDB"},{type:"catbox",name:"Catbox 免费图床"},{type:"smms",name:"Sm.ms（已收费）"},{type:"oss",name:"阿里云 OSS"},{type:"cos",name:"腾讯云 COS"}].map(w=>s.jsx("button",{onClick:()=>g("activeType",w.type),className:`flex flex-col items-center justify-center p-2.5 rounded-lg border text-center transition-all cursor-pointer ${e.activeType===w.type?"border-[var(--accent)] bg-[var(--accent)]/5 text-[var(--accent)] font-semibold shadow-xs":"border-slate-200 bg-white text-slate-600 hover:bg-slate-50 hover:text-slate-800"}`,children:s.jsx("span",{className:"text-[12px]",children:w.name})},w.type))})]}),n&&e.activeType!=="local"&&s.jsxs("div",{className:"rounded-lg border border-[var(--accent)]/30 bg-[var(--accent)]/5 p-3 flex flex-col gap-2",children:[s.jsxs("div",{className:"flex items-center gap-1.5 text-[13px] font-semibold text-slate-700",children:[s.jsx(gt,{size:15,className:"text-[var(--accent)]"}),"已加密保存图床密钥，请输入口令解锁"]}),s.jsx("p",{className:"text-[12px] text-slate-500",children:"输入口令解锁后即可使用，无需重新填写密钥。"}),s.jsxs("div",{className:"flex items-center gap-2",children:[s.jsx(ae,{type:"password",placeholder:"输入解锁口令",value:c,onChange:w=>u(w.target.value),onKeyDown:w=>{w.key==="Enter"&&c&&!p&&y()},className:"flex-1"}),s.jsx(mt,{variant:"primary",onClick:y,disabled:p||!c,children:p?"解锁中…":"解锁"})]}),f&&s.jsx("p",{className:"text-[12px] text-red-500",children:f})]}),s.jsxs("div",{className:"min-h-[160px] rounded-lg bg-slate-50 p-4 border border-slate-100",children:[e.activeType==="local"&&s.jsxs("div",{className:"text-[13px] leading-relaxed text-slate-500",children:[s.jsx("p",{className:"font-semibold text-slate-700 mb-1.5",children:s.jsxs("span",{className:"inline-flex items-center gap-1.5",children:[s.jsx(Mc,{size:15})," 本地 IndexedDB 模式"]})}),s.jsxs("ul",{className:"list-disc pl-4 space-y-1",children:[s.jsx("li",{children:"无需任何第三方配置，直接将图片保存在浏览器本地数据库中。"}),s.jsx("li",{children:"图片大小经 Canvas 压缩，性能流畅，对本地预览与 PDF 导出十分友好。"}),s.jsxs("li",{children:["注意：",s.jsx("strong",{className:"text-amber-600 font-medium",children:"由于本地图片为虚拟链接"}),"，直接复制 HTML 粘贴到微信公众号会导致图片失效（裂图），在公众号发布文章建议配置免费/付费图床。"]})]})]}),e.activeType==="catbox"&&s.jsxs("div",{className:"text-[13px] leading-relaxed text-slate-500",children:[s.jsx("p",{className:"font-semibold text-slate-700 mb-1.5",children:s.jsxs("span",{className:"inline-flex items-center gap-1.5",children:[s.jsx(qn,{size:15})," Catbox 免费图床"]})}),s.jsxs("ul",{className:"list-disc pl-4 space-y-1",children:[s.jsx("li",{children:"无需注册、无需 API Key，上传的图片永久保存。"}),s.jsx("li",{children:"上传时优先直连 Catbox；若当前环境有跨域限制，会自动回退到本地 dev 代理。"}),s.jsx("li",{children:"适合公众号富文本、知识平台等需要公网图片链接的场景。"})]})]}),e.activeType==="smms"&&s.jsxs("div",{className:"flex flex-col gap-3",children:[s.jsxs("div",{className:"text-[13px] leading-relaxed text-slate-500 mb-1",children:[s.jsx("p",{className:"font-semibold text-slate-700",children:s.jsxs("span",{className:"inline-flex items-center gap-1.5",children:[s.jsx(qn,{size:15})," Sm.ms 免费图床"]})}),s.jsxs("p",{children:["请先在 ",s.jsx("a",{href:"https://sm.ms/",target:"_blank",rel:"noopener noreferrer",className:"text-[var(--accent)] underline font-medium",children:"Sm.ms 官网"})," 注册并获取 API Token 填入下方。"]})]}),s.jsxs("div",{className:"flex flex-col gap-1.5",children:[s.jsx("label",{className:"text-[12px] font-medium text-slate-600",children:"API Token"}),s.jsx(ae,{type:"password",placeholder:"输入 Sm.ms 秘钥 Token",value:e.smmsToken,onChange:w=>g("smmsToken",w.target.value),className:"w-full"})]})]}),e.activeType==="oss"&&s.jsxs("div",{className:"grid grid-cols-2 gap-3",children:[s.jsxs("div",{className:"col-span-2 text-[13px] leading-relaxed text-slate-500 mb-1",children:[s.jsx("p",{className:"font-semibold text-slate-700",children:s.jsxs("span",{className:"inline-flex items-center gap-1.5",children:[s.jsx(Un,{size:15})," 阿里云对象存储 (OSS)"]})}),s.jsx("p",{children:"使用您的阿里云 Bucket 进行客户端直接上传。"})]}),s.jsxs("div",{className:"flex flex-col gap-1",children:[s.jsx("label",{className:"text-[12px] text-slate-600",children:"Region (区域，如 oss-cn-hangzhou)"}),s.jsx(ae,{value:e.ossRegion,onChange:w=>g("ossRegion",w.target.value),placeholder:"oss-cn-hangzhou"})]}),s.jsxs("div",{className:"flex flex-col gap-1",children:[s.jsx("label",{className:"text-[12px] text-slate-600",children:"Bucket Name (存储空间名称)"}),s.jsx(ae,{value:e.ossBucket,onChange:w=>g("ossBucket",w.target.value),placeholder:"my-bucket"})]}),s.jsxs("div",{className:"flex flex-col gap-1",children:[s.jsx("label",{className:"text-[12px] text-slate-600",children:"AccessKey ID"}),s.jsx(ae,{value:e.ossKeyId,onChange:w=>g("ossKeyId",w.target.value),placeholder:"LTAI..."})]}),s.jsxs("div",{className:"flex flex-col gap-1",children:[s.jsx("label",{className:"text-[12px] text-slate-600",children:"AccessKey Secret"}),s.jsx(ae,{type:"password",value:e.ossKeySecret,onChange:w=>g("ossKeySecret",w.target.value),placeholder:"Secret Key"})]})]}),e.activeType==="cos"&&s.jsxs("div",{className:"grid grid-cols-2 gap-3",children:[s.jsxs("div",{className:"col-span-2 text-[13px] leading-relaxed text-slate-500 mb-1",children:[s.jsx("p",{className:"font-semibold text-slate-700",children:s.jsxs("span",{className:"inline-flex items-center gap-1.5",children:[s.jsx(Un,{size:15})," 腾讯云对象存储 (COS)"]})}),s.jsx("p",{children:"使用您的腾讯云 Bucket 进行客户端直接上传。"})]}),s.jsxs("div",{className:"flex flex-col gap-1",children:[s.jsx("label",{className:"text-[12px] text-slate-600",children:"Region (区域，如 ap-shanghai)"}),s.jsx(ae,{value:e.cosRegion,onChange:w=>g("cosRegion",w.target.value),placeholder:"ap-shanghai"})]}),s.jsxs("div",{className:"flex flex-col gap-1",children:[s.jsx("label",{className:"text-[12px] text-slate-600",children:"Bucket Name (存储桶，含 AppId)"}),s.jsx(ae,{value:e.cosBucket,onChange:w=>g("cosBucket",w.target.value),placeholder:"my-bucket-125000"})]}),s.jsxs("div",{className:"flex flex-col gap-1",children:[s.jsx("label",{className:"text-[12px] text-slate-600",children:"SecretId"}),s.jsx(ae,{value:e.cosSecretId,onChange:w=>g("cosSecretId",w.target.value),placeholder:"AKID..."})]}),s.jsxs("div",{className:"flex flex-col gap-1",children:[s.jsx("label",{className:"text-[12px] text-slate-600",children:"SecretKey"}),s.jsx(ae,{type:"password",value:e.cosSecretKey,onChange:w=>g("cosSecretKey",w.target.value),placeholder:"Secret Key"})]})]})]}),e.activeType!=="local"&&i&&s.jsxs("div",{className:"rounded-lg border border-slate-100 bg-slate-50 p-3 flex flex-col gap-2",children:[s.jsxs("label",{className:"flex items-center gap-2 cursor-pointer select-none",children:[s.jsx("input",{type:"checkbox",checked:a,onChange:w=>x(w.target.checked),className:"h-3.5 w-3.5 accent-[var(--accent)]"}),s.jsxs("span",{className:"text-[13px] font-medium text-slate-700 inline-flex items-center gap-1.5",children:[s.jsx(gt,{size:14,className:"text-[var(--accent)]"})," 记住密钥（用口令加密保存到本地）"]})]}),a&&s.jsxs("div",{className:"flex flex-col gap-1",children:[s.jsx(ae,{type:"password",placeholder:o?"留空沿用旧口令，填写则重新加密":"设置加密口令",value:l,onChange:w=>h(w.target.value),className:"w-full"}),l&&s.jsx(_p,{passphrase:l}),s.jsx("p",{className:"text-[11px] text-slate-400",children:"口令仅用于本地加密，不上传；遗忘需重新填写密钥。"})]}),d&&s.jsx("p",{className:"text-[12px] text-red-500",children:d})]}),e.activeType!=="local"&&s.jsxs("div",{className:"text-[11px] leading-relaxed text-amber-600 bg-amber-50 rounded-lg p-3 border border-amber-100 flex items-start gap-1.5",children:[s.jsx("span",{className:"shrink-0 mt-0.5",children:s.jsx(dr,{size:14,className:"text-amber-600"})}),s.jsxs("span",{children:[s.jsx("strong",{children:"安全提示"}),"：本应用纯前端无后端。",s.jsx("strong",{children:"默认密钥仅存当前会话内存"}),"，刷新即清除。勾选「记住密钥」后会用口令加密保存在本地。请勿在公共计算机上配置生产环境密钥。",!r&&s.jsxs(s.Fragment,{children:[s.jsx("br",{}),s.jsx("strong",{children:"当前为非安全上下文（非 HTTPS）"}),"，Web Crypto 不可用，密钥仅在当前会话内存中保留，刷新即丢失。如需加密保存，请在 HTTPS 或 localhost 环境下使用。"]})]})]}),e.activeType!=="local"&&s.jsxs("div",{className:"rounded-lg border border-slate-100 bg-slate-50 p-3 flex flex-col gap-2.5",children:[s.jsxs("label",{className:"flex items-center gap-2 cursor-pointer select-none",children:[s.jsx("input",{type:"checkbox",checked:e.sendCredentials,onChange:w=>g("sendCredentials",w.target.checked),className:"h-3.5 w-3.5 accent-[var(--accent)]"}),s.jsx("span",{className:"text-[12px] text-slate-600",children:"导出时向图床域名发送凭证（Cookie）。仅依赖 Cookie 鉴权的私有图床需要开启，默认关闭。"})]}),s.jsxs("label",{className:"flex items-center gap-2 cursor-pointer select-none",children:[s.jsx("input",{type:"checkbox",checked:t,onChange:w=>j(w.target.checked),className:"h-3.5 w-3.5 accent-[var(--accent)]"}),s.jsx("span",{className:"text-[12px] text-slate-600",children:"允许加载内网资源（如 127.0.0.1、192.168.x.x）。企业内网部署场景可开启，默认关闭以防止 SSRF。"})]})]})]})}function _p({passphrase:e}){const t=Is(e),n=t.level==="weak"?"text-orange-500":t.level==="fair"?"text-amber-500":"text-green-600";return s.jsxs("p",{className:`text-[11px] ${n}`,children:[t.label,"。建议使用 8 位以上、含字母与数字的口令。"]})}function Cp({apiUrl:e,apiKey:t,model:n}){const i=J(r=>r.setAiConfig);return s.jsxs("div",{className:"rounded-lg bg-slate-50 p-4 border border-slate-100 flex flex-col gap-3",children:[s.jsxs("div",{className:"text-[13px] leading-relaxed text-slate-500 mb-1",children:[s.jsxs("p",{className:"font-semibold text-slate-700 flex items-center gap-1.5",children:[s.jsx(He,{size:15,className:"text-[var(--accent)]"})," AI 排版配置"]}),s.jsxs("p",{children:["支持 DeepSeek / Moonshot / 通义千问等 OpenAI 兼容协议，也支持本地 API 如 ",s.jsx("code",{children:"http://127.0.0.1:20128/v1"}),"。"]})]}),s.jsxs("div",{children:[s.jsx("label",{className:"text-[12px] font-medium text-slate-600",children:le.aiTypeset.apiUrl}),s.jsx(ae,{value:e,onChange:r=>i({apiUrl:r.target.value,apiKey:t,model:n}),placeholder:le.aiTypeset.apiUrlPlaceholder,className:"w-full mt-0.5"})]}),s.jsxs("div",{children:[s.jsx("label",{className:"text-[12px] font-medium text-slate-600",children:le.aiTypeset.apiKey}),s.jsx(ae,{type:"password",value:t,onChange:r=>i({apiUrl:e,apiKey:r.target.value,model:n}),placeholder:le.aiTypeset.apiKeyPlaceholder,className:"w-full mt-0.5"})]}),s.jsxs("div",{children:[s.jsx("label",{className:"text-[12px] font-medium text-slate-600",children:le.aiTypeset.model}),s.jsx(ae,{value:n,onChange:r=>i({apiUrl:e,apiKey:t,model:r.target.value}),placeholder:le.aiTypeset.modelPlaceholder,className:"w-full mt-0.5"})]}),s.jsxs("p",{className:"text-[11px] text-slate-400",children:["配置会自动保存到 IndexedDB。本地 API 地址可填 ",s.jsx("code",{children:"http://127.0.0.1:20128/v1"}),"，Key 可留空。"]})]})}function Tp({form:e,onChange:t}){return s.jsxs("div",{className:"rounded-lg bg-slate-50 p-4 border border-slate-100 flex flex-col gap-3",children:[s.jsxs("div",{className:"text-[13px] leading-relaxed text-slate-500 mb-1",children:[s.jsx("p",{className:"font-semibold text-slate-700",children:"公众号草稿箱发布"}),s.jsx("p",{children:"配置后点击长图文工具栏的「发布到草稿箱」，会直接调用本地发布服务创建草稿。"})]}),s.jsxs("div",{className:"grid grid-cols-1 gap-2.5",children:[s.jsxs("div",{className:"flex flex-col gap-1",children:[s.jsx("label",{className:"text-[12px] font-medium text-slate-600",children:"AppID"}),s.jsx(ae,{value:e.appId,onChange:n=>t({appId:n.target.value}),placeholder:"wx..."})]}),s.jsxs("div",{className:"flex flex-col gap-1",children:[s.jsx("label",{className:"text-[12px] font-medium text-slate-600",children:"AppSecret"}),s.jsx(ae,{type:"password",value:e.appSecret,onChange:n=>t({appSecret:n.target.value}),placeholder:"保存在本机浏览器数据库，不会上传"})]}),s.jsxs("div",{className:"flex flex-col gap-1",children:[s.jsx("label",{className:"text-[12px] font-medium text-slate-600",children:"封面素材 thumb_media_id（可选）"}),s.jsx(ae,{value:e.thumbMediaId,onChange:n=>t({thumbMediaId:n.target.value}),placeholder:"留空则自动上传封面图"})]}),s.jsxs("div",{className:"flex flex-col gap-1",children:[s.jsx("label",{className:"text-[12px] font-medium text-slate-600",children:"封面图 URL（可选）"}),s.jsx(ae,{value:e.coverImageUrl,onChange:n=>t({coverImageUrl:n.target.value}),placeholder:"https://.../cover.jpg；留空则使用正文第一张图片"})]}),s.jsxs("div",{className:"flex flex-col gap-1",children:[s.jsx("label",{className:"text-[12px] font-medium text-slate-600",children:"发布服务端点"}),s.jsx(ae,{value:e.publishEndpoint,onChange:n=>t({publishEndpoint:n.target.value}),className:"font-mono text-[12px]",placeholder:"留空使用同源 /__markflow_wechat_publish（需服务器已部署发布服务）"}),s.jsx("p",{className:"text-[11px] text-slate-400",children:"发布服务负责调用微信 API（浏览器无法直连）。自建部署见 tools/wechat/publish_server.py。"})]})]})]})}function zp({isOpen:e,onClose:t}){const n=J(H=>H.imageHostConfig),i=J(H=>H.setImageHostConfig),r=J(H=>H.allowIntranetResources),o=J(H=>H.setAllowIntranetResources),a=J(H=>H.aiConfig),l=J(H=>H.wechatDraftConfig),d=J(H=>H.setWeChatDraftConfig),[c,p]=v.useState("imageHost"),[f,g]=v.useState(()=>ii(n)),x=Kt(),h=Ai(),[u,y]=v.useState(!1),[j,w]=v.useState(!1),[N,b]=v.useState(""),[$,S]=v.useState(""),[C,O]=v.useState(""),[F,Q]=v.useState(!1),[W,Z]=v.useState(""),[I,L]=v.useState({appId:l.appId??"",appSecret:l.appSecret??"",thumbMediaId:l.thumbMediaId??"",coverImageUrl:l.coverImageUrl??"",publishEndpoint:l.publishEndpoint??"/__markflow_wechat_publish"}),[B,G]=v.useState(r);if(v.useEffect(()=>{if(!e)return;g(ii(n));const H=Mi();y(H),w(H),b(""),O(""),Z(""),S(""),G(r),L({appId:l.appId??"",appSecret:l.appSecret??"",thumbMediaId:l.thumbMediaId??"",coverImageUrl:l.coverImageUrl??"",publishEndpoint:l.publishEndpoint??"/__markflow_wechat_publish"})},[e,n,r,l.appId,l.appSecret,l.thumbMediaId,l.coverImageUrl,l.publishEndpoint]),!e)return null;const se=u&&!Np(n),M=(H,re)=>{g(de=>({...de,[H]:re}))},q=async()=>{var H,re,de,T,ie,oe,pe,Je,Qe;Z(""),Q(!0);try{const Se=await Ms(C);i({smms:{token:((H=Se.smms)==null?void 0:H.token)||""},oss:{region:((re=n.oss)==null?void 0:re.region)||"",bucket:((de=n.oss)==null?void 0:de.bucket)||"",accessKeyId:((T=Se.oss)==null?void 0:T.accessKeyId)||"",accessKeySecret:((ie=Se.oss)==null?void 0:ie.accessKeySecret)||""},cos:{Bucket:((oe=n.cos)==null?void 0:oe.Bucket)||"",Region:((pe=n.cos)==null?void 0:pe.Region)||"",SecretId:((Je=Se.cos)==null?void 0:Je.SecretId)||"",SecretKey:((Qe=Se.cos)==null?void 0:Qe.SecretKey)||""}}),O("")}catch{Z("口令错误或数据已损坏，请重试")}finally{Q(!1)}},ce=async()=>{if(c==="wechat"){d({appId:I.appId.trim(),appSecret:I.appSecret,thumbMediaId:I.thumbMediaId.trim(),coverImageUrl:I.coverImageUrl.trim(),publishEndpoint:I.publishEndpoint.trim()||"/__markflow_wechat_publish"}),t();return}if(j&&x){if(N)try{await As({smms:{token:f.smmsToken},oss:{accessKeyId:f.ossKeyId,accessKeySecret:f.ossKeySecret},cos:{SecretId:f.cosSecretId,SecretKey:f.cosSecretKey}},N)}catch(re){S(`加密保存失败：${re instanceof Error?re.message:"未知错误"}`);return}else if(!u){S("请输入用于加密的口令，或取消勾选「记住密钥」");return}}else Ts();const H={activeType:f.activeType,smms:{token:f.smmsToken},oss:{region:f.ossRegion,accessKeyId:f.ossKeyId,accessKeySecret:f.ossKeySecret,bucket:f.ossBucket},cos:{SecretId:f.cosSecretId,SecretKey:f.cosSecretKey,Bucket:f.cosBucket,Region:f.cosRegion},sendCredentials:f.sendCredentials};i(H),o(B),t()};return s.jsx($t,{isOpen:e,onClose:t,closeOnOverlay:!1,children:s.jsxs("div",{children:[s.jsxs("div",{className:"flex items-center justify-between border-b border-slate-100 pb-3",children:[s.jsxs("h2",{className:"text-base font-bold text-slate-800 flex items-center gap-2",children:[s.jsxs("svg",{xmlns:"http://www.w3.org/2000/svg",width:"18",height:"18",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2.2",strokeLinecap:"round",strokeLinejoin:"round",className:"text-[var(--accent)]",children:[s.jsx("path",{d:"M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"}),s.jsx("polyline",{points:"3.27 6.96 12 12.01 20.73 6.96"}),s.jsx("line",{x1:"12",y1:"22.08",x2:"12",y2:"12"})]}),"设置"]}),s.jsx("button",{type:"button",onClick:t,"aria-label":"关闭",className:"rounded-md p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition-colors",children:s.jsxs("svg",{width:"16",height:"16",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2",strokeLinecap:"round",strokeLinejoin:"round",children:[s.jsx("line",{x1:"18",y1:"6",x2:"6",y2:"18"}),s.jsx("line",{x1:"6",y1:"6",x2:"18",y2:"18"})]})})]}),s.jsxs("div",{className:"flex gap-1 mt-3 mb-1",children:[s.jsx("button",{onClick:()=>p("imageHost"),className:`px-3 py-1.5 rounded-md text-[12px] font-semibold transition-colors cursor-pointer ${c==="imageHost"?"bg-[var(--accent)]/10 text-[var(--accent)]":"text-slate-500 hover:bg-slate-100 hover:text-slate-700"}`,children:"图床设置"}),s.jsxs("button",{onClick:()=>p("wechat"),className:`px-3 py-1.5 rounded-md text-[12px] font-semibold transition-colors cursor-pointer flex items-center gap-1.5 ${c==="wechat"?"bg-[var(--accent)]/10 text-[var(--accent)]":"text-slate-500 hover:bg-slate-100 hover:text-slate-700"}`,children:[s.jsx(dn,{size:13})," 公众号发布"]}),s.jsx("button",{onClick:()=>p("ai"),className:`px-3 py-1.5 rounded-md text-[12px] font-semibold transition-colors cursor-pointer flex items-center gap-1.5 ${c==="ai"?"bg-[var(--accent)]/10 text-[var(--accent)]":"text-slate-500 hover:bg-slate-100 hover:text-slate-700"}`,children:"AI 配置"})]}),s.jsxs("div",{className:"mt-4 flex flex-col gap-4",children:[c==="imageHost"&&s.jsx(Ep,{form:f,allowIntranet:B,needsUnlock:se,cryptoOk:x,secureContext:h,vaultExists:u,remember:j,passphrase:N,saveError:$,unlockPass:C,unlocking:F,unlockError:W,onFormChange:M,onRememberChange:w,onPassphraseChange:b,onUnlockPassChange:O,onUnlock:q,onAllowIntranetChange:G}),c==="wechat"&&s.jsx(Tp,{form:I,onChange:H=>L(re=>({...re,...H}))}),c==="ai"&&s.jsx(Cp,{apiUrl:a.apiUrl,apiKey:a.apiKey,model:a.model})]}),s.jsxs("div",{className:"mt-5 flex items-center justify-end gap-2 border-t border-slate-100 pt-3",children:[s.jsx(mt,{variant:"outline",onClick:t,children:"取消"}),s.jsx(mt,{variant:"primary",onClick:ce,children:"保存配置"})]})]})})}function Ap({isOpen:e,onClose:t}){return s.jsxs($t,{isOpen:e,onClose:t,ariaLabel:"隐私与数据安全说明",closeOnOverlay:!1,zIndex:100,overlayClassName:"fixed inset-0 flex items-center justify-center bg-black/40 backdrop-blur-md",panelClassName:"mx-4 w-full max-w-lg overflow-hidden rounded-2xl bg-white shadow-2xl border border-slate-100 flex flex-col animate-in fade-in zoom-in-95 duration-200",children:[s.jsx("div",{className:"h-1.5 w-full bg-gradient-to-r from-emerald-400 via-teal-500 to-cyan-500"}),s.jsxs("div",{className:"p-6",children:[s.jsxs("div",{className:"flex items-start gap-4 mb-5",children:[s.jsx("span",{className:"flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600",children:s.jsx("svg",{xmlns:"http://www.w3.org/2000/svg",width:"24",height:"24",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2.2",strokeLinecap:"round",strokeLinejoin:"round",children:s.jsx("path",{d:"M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"})})}),s.jsxs("div",{children:[s.jsx("h2",{className:"text-[18px] font-bold text-slate-900",children:"隐私与数据安全说明"}),s.jsx("p",{className:"text-[12px] text-slate-500 mt-0.5",children:"为您提供完全透明、安全独立的数据处理服务"})]})]}),s.jsxs("div",{className:"space-y-4 text-slate-600 text-[13px] leading-relaxed",children:[s.jsxs("div",{className:"rounded-xl border border-slate-100 bg-slate-50/50 p-4",children:[s.jsxs("h3",{className:"font-semibold text-slate-800 flex items-center gap-1.5 mb-1",children:[s.jsx("span",{className:"h-2 w-2 rounded-full bg-emerald-500"}),"(a) 纯前端沙箱运行，数据零上传"]}),s.jsxs("p",{className:"text-slate-500 pl-3.5",children:["本项目为",s.jsx("strong",{children:"纯前端项目"}),"，没有设计任何后端服务器。您的所有 Markdown 文本、参数设置和生成的 HTML/图片/PDF 等成果，均完全在您本机的浏览器中进行实时解析与导出，",s.jsx("strong",{children:"绝对不会传输至任何服务器"}),"，充分保障您的商业机密与个人隐私。"]})]}),s.jsxs("div",{className:"rounded-xl border border-slate-100 bg-slate-50/50 p-4",children:[s.jsxs("h3",{className:"font-semibold text-slate-800 flex items-center gap-1.5 mb-1",children:[s.jsx("span",{className:"h-2 w-2 rounded-full bg-emerald-500"}),"(b) 本地持久化，安全又便捷"]}),s.jsxs("p",{className:"text-slate-500 pl-3.5",children:["您的个性化配置、历史编辑状态和图床设置，均安全地存储于浏览器的 ",s.jsx("code",{children:"IndexedDB"})," 本地数据库中。",s.jsx("strong",{children:"只要您不手动清除浏览器缓存"}),"，在同一设备和浏览器上便可一直保持，避免数据丢失的同时实现了即开即用。"]})]}),s.jsxs("div",{className:"rounded-xl border border-slate-100 bg-slate-50/50 p-4",children:[s.jsxs("h3",{className:"font-semibold text-slate-800 flex items-center gap-1.5 mb-1",children:[s.jsx("span",{className:"h-2 w-2 rounded-full bg-emerald-500"}),"(c) 直连云服务，拒绝中间中转"]}),s.jsxs("p",{className:"text-slate-500 pl-3.5",children:["如果您在「图床设置」中配置了个人对象存储（如阿里云 OSS、腾讯云 COS），系统仅在您主动上传图片时",s.jsx("strong",{children:"直连您的云服务商 API"}),"，同样绝无任何中转服务器截留，密钥完全由您掌控。"]})]})]})]}),s.jsxs("div",{className:"flex items-center justify-between border-t border-slate-100 bg-slate-50 px-6 py-4",children:[s.jsxs("div",{className:"text-[11px] text-slate-400 flex items-center gap-1",children:[s.jsxs("svg",{xmlns:"http://www.w3.org/2000/svg",width:"12",height:"12",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2",strokeLinecap:"round",strokeLinejoin:"round",className:"text-emerald-500",children:[s.jsx("path",{d:"M22 11.08V12a10 10 0 1 1-5.93-9.14"}),s.jsx("polyline",{points:"22 4 12 14.01 9 11.01"})]}),"100% 离线可用 / 开源受信任"]}),s.jsx("button",{onClick:t,className:"rounded-lg bg-slate-900 px-5 py-2 text-[13px] font-semibold text-white hover:bg-slate-800 transition-colors cursor-pointer",children:"我知道了"})]})]})}function Mp(){if(typeof navigator>"u")return{shouldWarn:!1,envName:"未知",recommendations:[]};const e=navigator.userAgent,t=[[/MicroMessenger/i,"微信内置浏览器"],[/QQ\/[\d.]/i,"QQ 内置浏览器"],[/QQLite/i,"QQ 轻聊版浏览器"],[/Weibo/i,"微博内置浏览器"],[/DingTalk/i,"钉钉内置浏览器"],[/baidubrowser/i,"百度浏览器"],[/UCBrowser/i,"UC 浏览器"],[/Quark/i,"夸克浏览器"],[/SogouMobileBrowser/i,"搜狗浏览器"],[/360 Aphone|360browser/i,"360 浏览器"],[/SamsungBrowser/i,"三星浏览器"]];for(const[n,i]of t)if(n.test(e))return{shouldWarn:!0,envName:i,recommendations:["Google Chrome","Microsoft Edge"]};return/Firefox/i.test(e)&&!/Chrome/i.test(e)?{shouldWarn:!0,envName:"Firefox 浏览器",recommendations:["Google Chrome","Microsoft Edge"]}:/Safari/i.test(e)&&!/Chrome|CriOS|EdgiOS|OPiOS/i.test(e)?{shouldWarn:!0,envName:"Safari 浏览器",recommendations:["Google Chrome","Microsoft Edge"]}:/iPhone|iPad|iPod/i.test(e)&&!/Chrome|CriOS|EdgiOS|OPiOS|Firefox|FxiOS/i.test(e)&&!/Safari/i.test(e)?{shouldWarn:!0,envName:"iOS 应用内嵌浏览器",recommendations:["Safari（iOS 推荐）","Google Chrome"]}:{shouldWarn:!1,envName:"",recommendations:[]}}const ri="m2v-browser-compat-dismissed";function Ip(){const[e,t]=v.useState(null),[n,i]=v.useState(!1),r=v.useRef();if(v.useEffect(()=>()=>clearTimeout(r.current),[]),v.useEffect(()=>{if(sessionStorage.getItem(ri)==="1")return;const l=Mp();l.shouldWarn&&t(l)},[]),!e)return null;const o=async()=>{await Fe(window.location.href)&&(i(!0),r.current=setTimeout(()=>i(!1),2e3))},a=()=>{sessionStorage.setItem(ri,"1"),t(null)};return s.jsxs($t,{isOpen:!!e,onClose:a,ariaLabel:"浏览器兼容性提示",closeOnOverlay:!1,zIndex:55,overlayClassName:"fixed inset-0 flex items-center justify-center bg-black/50 backdrop-blur-sm animate-fade-in",panelClassName:"mx-4 w-full max-w-md rounded-2xl bg-white shadow-2xl overflow-hidden",children:[s.jsx("div",{className:"h-1 bg-gradient-to-r from-amber-400 via-amber-500 to-orange-400"}),s.jsxs("div",{className:"px-6 pt-6 pb-5",children:[s.jsxs("div",{className:"flex items-start gap-4 mb-5",children:[s.jsx("div",{className:"flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-amber-50 text-amber-500",children:s.jsx(dr,{size:26})}),s.jsxs("div",{className:"pt-0.5",children:[s.jsx("h2",{className:"text-lg font-bold text-slate-900 tracking-tight",children:"浏览器兼容性提示"}),s.jsxs("p",{className:"mt-1 text-sm text-slate-500 leading-relaxed",children:["检测到您当前使用的是",s.jsxs("span",{className:"font-semibold text-slate-700",children:["「",e.envName,"」"]}),"，可能无法完整加载本工具的渲染引擎与高级特性。"]})]})]}),s.jsxs("div",{className:"rounded-xl border border-slate-100 bg-slate-50/60 p-4 mb-5",children:[s.jsx("p",{className:"text-xs font-semibold text-slate-400 uppercase tracking-wider mb-3",children:"推荐使用以下浏览器打开"}),s.jsx("div",{className:"flex gap-3",children:e.recommendations.map(l=>s.jsxs("div",{className:"flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-medium text-slate-700 shadow-sm",children:[s.jsx(Lp,{name:l}),s.jsx("span",{children:l})]},l))})]}),s.jsxs("div",{className:"mb-5 flex items-center gap-2 rounded-lg bg-slate-50 border border-slate-100 px-3.5 py-2.5 text-xs text-slate-500",children:[s.jsx(gt,{size:14,className:"text-emerald-500 shrink-0"}),s.jsx("span",{children:"本工具 100% 纯前端运行，数据仅保存在您的本地浏览器中"})]}),s.jsxs("div",{className:"flex items-center gap-3",children:[s.jsx("button",{onClick:a,className:"flex-1 rounded-lg border border-slate-200 bg-white px-4 py-2.5 text-sm font-medium text-slate-500 hover:bg-slate-50 hover:text-slate-700 transition-colors cursor-pointer",children:"已知晓，继续使用"}),s.jsx("button",{onClick:o,className:`flex-1 rounded-lg px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all cursor-pointer ${n?"bg-emerald-500 hover:bg-emerald-500":"bg-[var(--accent)] hover:opacity-90"}`,children:n?s.jsxs("span",{className:"flex items-center justify-center gap-1.5",children:[s.jsx("svg",{width:"14",height:"14",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2.5",strokeLinecap:"round",strokeLinejoin:"round",children:s.jsx("polyline",{points:"20 6 9 17 4 12"})}),"已复制"]}):s.jsxs("span",{className:"flex items-center justify-center gap-1.5",children:[s.jsxs("svg",{width:"14",height:"14",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2",strokeLinecap:"round",strokeLinejoin:"round",children:[s.jsx("path",{d:"M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"}),s.jsx("path",{d:"M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"})]}),"复制网址，去浏览器打开"]})})]})]})]})}function Lp({name:e}){return e.includes("Chrome")||e.includes("Google")?s.jsxs("svg",{width:"18",height:"18",viewBox:"0 0 24 24",fill:"none",className:"shrink-0",children:[s.jsx("circle",{cx:"12",cy:"12",r:"10",stroke:"currentColor",strokeWidth:"1.5"}),s.jsx("circle",{cx:"12",cy:"12",r:"4",stroke:"currentColor",strokeWidth:"1.5"}),s.jsx("line",{x1:"12",y1:"8",x2:"20",y2:"8",stroke:"currentColor",strokeWidth:"1.5"}),s.jsx("line",{x1:"8",y1:"14",x2:"4",y2:"8",stroke:"currentColor",strokeWidth:"1.5"}),s.jsx("line",{x1:"14",y1:"16",x2:"18",y2:"20",stroke:"currentColor",strokeWidth:"1.5"})]}):e.includes("Edge")||e.includes("Microsoft")?s.jsxs("svg",{width:"18",height:"18",viewBox:"0 0 24 24",fill:"none",className:"shrink-0",children:[s.jsx("path",{d:"M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10c4.2 0 7.8-2.6 9.3-6.3",stroke:"currentColor",strokeWidth:"1.5",strokeLinecap:"round"}),s.jsx("path",{d:"M21 12c0-1.7-.7-3.2-1.8-4.3",stroke:"currentColor",strokeWidth:"1.5",strokeLinecap:"round"}),s.jsx("circle",{cx:"12",cy:"12",r:"3",stroke:"currentColor",strokeWidth:"1.5"})]}):e.includes("Safari")?s.jsxs("svg",{width:"18",height:"18",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"1.5",strokeLinecap:"round",strokeLinejoin:"round",className:"shrink-0",children:[s.jsx("circle",{cx:"12",cy:"12",r:"10"}),s.jsx("polygon",{points:"16.24 7.76 14.12 14.12 7.76 16.24 9.88 9.88 16.24 7.76"})]}):s.jsxs("svg",{width:"18",height:"18",viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"1.5",strokeLinecap:"round",strokeLinejoin:"round",className:"shrink-0",children:[s.jsx("circle",{cx:"12",cy:"12",r:"10"}),s.jsx("line",{x1:"2",y1:"12",x2:"22",y2:"12"}),s.jsx("path",{d:"M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"})]})}function Rp(e){switch(e){case"fields":return`label: 示例标签
title: 示例标题
subtitle: 示例副标题`;case"rows":return`项目一 | 描述一
项目二 | 描述二
项目三 | 描述三`;case"markdown":return`示例正文内容，支持 **粗体** 和 *斜体*。
- 列表项一
- 列表项二`;case"json_object":return'{"term":"示例术语","def":"示例定义","termLabel":"术语"}';case"json_array":return'[{"label":"示例指标","value":"100%"}]';default:return""}}const Op={hero:`label: 深度观察
title: 为什么读者不愿意读下去
subtitle: 不是你的内容不够好，是结构没有替读者省力气。模块化排版让每篇文章都像专业编辑操刀。
cta_text: 查看完整方法论 →`,toc:`01 | 问题定义 | 为什么现有排版让读者在 3 秒内离开
02 | 模块原理 | 61 个排版组件各自解决什么场景问题
03 | 实战示例 | 一篇观点文的完整排版过程拆解
04 | 数据验证 | 模块化 vs 手工排版的阅读数据对比
05 | 上手路径 | 从第一个模块到完整工作流的 10 分钟指南`,cards:`⚡ 极简上手 | 3 步写完第一篇排版 | 从空白草稿到可发布成品，只需填写字段 + 预览 + 复制
🎨 52 套主题 | 一键切换品牌气质 | 从学术严谨到新锐科技，选中即用无需调色
📊 数据驱动 | 完读率提升 1.8x | 真实用户测试验证的排版模式，不是拍脑袋设计`,part:`label: CHAPTER 02
title: 模块化排版的四个核心原则
body: 在深入具体模块之前，我们先建立一套评估框架——什么样的排版才算「好排版」？本章从认知负荷、视觉节奏、信息密度和品牌一致性四个维度展开。`,metrics:`完读率 | 79% | 高于行业均值 1.8 倍 | accent
制作时间 | 35 分钟 | 较旧版手工排版节省 60% | default
读者收藏率 | 23.6% | 同比增长 4.2 个百分点 | default
分享转发率 | 8.1% | 干货类推文排名前 5% | accent`,infographic:`label: 读者画像
title: 谁在看你的文章
subtitle: 基于 12,000 份问卷的核心发现
body: |
  78% 的读者会在 5 秒内判断是否继续阅读
  排版质量直接影响信任度评分（r=0.71）
  手机端阅读占比 83%，但大多数文章按桌面端设计`,compare:`模块化排版 | 上手 10 分钟 | 品牌一致性 95% | 读者完读率 79% | accent
手工排版 | 熟练需 3 个月 | 品牌一致性 60% | 读者完读率 41% | default`,steps:`01 | 发现模块 | 浏览组件库，找到适合当前场景的排版模块
02 | 查看规格 | 展开模块详情，确认必填字段和示例格式
03 | 复制语法 | 复制 ::: 容器代码到编辑器中对应位置
04 | 编辑内容 | 替换字段值为实际内容，保存后实时预览
05 | 导出发布 | 导出公众号 HTML，或复制富文本到公众号后台`,timeline:`2024 Q1 | 项目启动 | 完成团队组建与需求调研，确定技术选型
2024 Q2 | MVP 上线 | 核心渲染引擎完成，支持 12 个基础模块
2024 Q3 | 主题系统 | 52 套专业主题上线，支持一键切换品牌风格
2024 Q4 | 模块扩展 | 排版组件增至 61 个，覆盖全内容场景
2025 Q1 | 生态建设 | 开放 AI 排版指令库，社区贡献 200+ 风格模板`,verdict:`label: 最终判断
title: 排版的本质不是「好看」，是「降低认知成本」
body: 每一篇文章都是一次决策——读者在 3 秒内决定留下还是划走。排版的价值不在于装饰，而在于用结构告诉读者：这里有你要的答案，而且不难读。`,"audience-fit":`技术团队 | 结构严谨、代码块清晰、API 文档可直接复制 | 高
运营人员 | 步骤卡片 + 指标看板，一眼看到关键数据和行动项 | 高
C 端读者 | 标题吸睛、金句醒目、图文混排降低阅读疲劳 | 中`,"myth-fact":`排版就是加粗变色 | 排版是信息架构的可视化，核心是降低读者的认知负荷
好看的文章一定好读 | 视觉吸引力只是入口，阅读完成率取决于结构是否匹配阅读习惯
模块化排版让文章千篇一律 | 52 套主题 + 灵活字段组合，每篇都可以有独特气质`,manifesto:`label: 设计原则
title: 好排版让信息自己会说话
subtitle: 六个字概括：少即是多，结构优先`,bridge:`from: 为什么内容有问题
to: 用结构化的模块化排版解决它`,quote:"最好的排版是让读者感觉不到排版的存在——他们的注意力完全被内容吸引，而不是被装饰分散。 | 唐·诺曼 | 《设计心理学》作者","image-annotate":`src: https://robocopmao.github.io/r-markdown/banner4.webp
title: 模块化排版引擎架构
body: 从上到下依次为：Markdown 解析层 → 模块匹配层 → 主题令牌注入 → 内联样式 HTML 输出`,"image-compare":`before: https://robocopmao.github.io/r-markdown/banner4.webp
after: https://robocopmao.github.io/r-markdown/banner4.webp
label_before: 手工排版（2h）
label_after: MarkFlow 一键生成（3min）`,"image-steps":`01 | 在组件库中找到合适的排版模块 | https://robocopmao.github.io/r-markdown/banner4.webp
02 | 按字段格式替换为实际文案 | https://robocopmao.github.io/r-markdown/banner4.webp
03 | 右侧即时查看渲染效果 | https://robocopmao.github.io/r-markdown/banner4.webp`,"image-text":`src: https://robocopmao.github.io/r-markdown/banner4.webp
title: 移动端阅读体验优化
body: 所有模块均针对手机竖屏（375-414px 视口）做了适配。图片自动缩放、表格横向滚动、卡片单列堆叠。无需额外调整即可同时适配桌面端与移动端。
layout: right`,faq:`这些排版模块能在公众号后台直接使用吗？ | 可以。MarkFlow 输出的内联样式 HTML 可直接粘贴到公众号编辑器，样式不会丢失。
需要付费吗？ | 全部 61 个排版组件 + 52 套主题免费使用，无任何功能限制。
支持导出为图片吗？ | 支持一键导出完整长图 PNG，适合在知识星球、社群等平台分发。`,checklist:`☐ 确定文章核心观点（一句话能说清） | true
☐ 搭建大纲框架（3-5 个主段落） | true
☐ 为每个段落选择合适的排版模块 | false
☐ 填充正文内容并调整字段 | false
☐ 预览移动端显示效果 | false
☐ 复制富文本到公众号后台 | false`,cases:`01 | 自媒体主创小李 | 从 2 小时到 10 分钟，排版效率提升 12 倍，月产出从 8 篇增至 20 篇
02 | 技术博主老张 | 代码块 + 术语定义卡的组合让技术教程的收藏率翻倍
03 | 企业培训师王姐 | A4 文档模式直接输出培训手册，省去排版外包费用`,summary:`核心要点回顾：

1. **模块化排版的本质**是降低读者的认知成本，而非堆砌装饰
2. **61 个排版组件**覆盖从开篇吸引到结尾转化的完整阅读旅程
3. **52 套主题**让你一键切换品牌气质，无需设计背景
4. **导出链路**支持富文本、长图、A4 文档、卡片等多种成品形态

下一步：打开组件库，挑一个模块试写你的第一段排版。`,notice:`title: 主题系统 v2.0 已上线
body: 新增 12 套专业主题配色，涵盖学术论文、科技周刊、品牌营销三大场景。旧版主题配置文件仍可使用，但建议迁移至新版以获得更好的色彩一致性。`,"author-card":`title: 极客旅程
avatar: https://robocopmao.github.io/r-markdown/banner4.webp
bio: 专注内容排版与知识管理工具链，帮助创作者用更少的时间做出更好的内容。
role: 主理人 · 全栈开发者
tags: 排版|知识管理|效率工具`,subscribe:`title: 关注「极客旅程」
subtitle: 每周更新排版技巧与内容策略
body: 已有 12,000+ 创作者订阅。不打扰，只发干货。
placeholder: 输入邮箱地址
btn: 立即订阅`,people:`张三 | 前端工程师 | 负责 Markdown 渲染引擎开发，热衷于探索 CSS 内联样式在富文本场景下的极限 | https://robocopmao.github.io/r-markdown/banner4.webp
李四 | 产品设计师 | 52 套主题的主要设计者，坚持「好看的前提是好读」 | https://robocopmao.github.io/r-markdown/banner4.webp`,series:`topic: MarkFlow 完全指南
episode: 02
title: 第二章：掌握 61 个排版组件`,callout:`> **💡 提示**
> 如果你不确定应该用哪个模块，打开组件库的「结构导航」分类，那里的 **阅读路线**（reading-path）和 **章节分隔**（part）是最常用的两种结构模块。`,definition:'{"term":"认知负荷","def":"人在处理信息时心智资源的总消耗量。排版的核心目标之一就是将认知负荷降到最低，让读者能专注于内容本身。","termLabel":"UX 术语"}',"quote-card":'{"text":"如果你不能向一个六岁孩子解释清楚，那你就是没真正理解。","source":"理查德·费曼 · 诺贝尔物理学奖得主"}',tweet:'{"name":"独立开发者周刊","handle":"@indiedev","verified":true,"text":"MarkFlow 的模块化排版彻底改变了我的内容工作流。以前公众号排版要 2 小时，现在写好 Markdown、套上模块、复制粘贴，10 分钟搞定。关键是样式还能保持一致——这在以前根本不敢想。","timestamp":"2026-06-15","likes":"2.4K","retweets":"586","replies":"127"}',"stat-row":'[{"label":"覆盖模块数","value":"61 个","trend":"+18","color":"accent"},{"label":"支持主题","value":"52 套","trend":"+4 套","color":"default"},{"label":"平均导出耗时","value":"0.8s","trend":"-40%","color":"accent"},{"label":"月活创作者","value":"12K+","trend":"+23%","color":"default"}]',question:'[{"q":"排版模块和 Markdown 扩展语法是什么关系？","a":"排版模块是 Markdown 的自定义容器扩展（:::name），在标准 Markdown 基础上增加了结构化排版能力，同时保持纯文本可读性。"},{"q":"我可以在一个模块里嵌套另一个模块吗？","a":"目前不支持嵌套，但可以将多个模块按顺序排列。每个模块的 body 区域支持标准 Markdown 语法。"},{"q":"导出的富文本能在其他平台使用吗？","a":"导出的 HTML 使用内联样式（inline style），兼容微信公众号、知乎、语雀、Notion 等主流平台。"}]',"resource-list":'[{"title":"MarkFlow 使用指南 v2.0","desc":"从零到一掌握所有模块和主题的完整教程","url":"#","type":"pdf"},{"title":"排版认知科学白皮书","desc":"为什么结构化排版能提升阅读完成率——来自认知心理学的证据","url":"#","type":"link"},{"title":"示例模板库","desc":"10 套可直接复用的排版模板，覆盖观点文、教程、周报等场景","url":"#","type":"download"}]',"comparison-table":'{"left":{"title":"传统手工排版","items":["上手 2-4 周","依赖设计师手感","需逐篇调整移动端"]},"right":{"title":"MarkFlow 模块化","items":["上手 10 分钟","52 套主题自动保证","所有模块内置适配"]}}',changelog:'{"version":"v2.5.0","date":"2026-07-31","added":["新增 4 套主题：摸鱼绿、石墨极简、留白禅意、橄榄手记","公众号草稿箱发布配置与命令生成","组件库按渲染样式自动去重"],"changed":["主题总数为 52 套","组件库分类映射完善"],"fixed":["组件库分类正确映射到 11 个功能分组"]}'},Dp=[{key:"all",label:"全部"},{key:"title",label:"标题"},{key:"intro",label:"开篇引导"},{key:"structure",label:"结构导航"},{key:"content",label:"正文内容"},{key:"data",label:"数据流程"},{key:"image",label:"图文媒体"},{key:"emph",label:"强调引用"},{key:"cards",label:"卡片"},{key:"cta",label:"行动引导"},{key:"inline",label:"行内元素"},{key:"other",label:"其他"}],Pp={Title_DA01:"title",Title_DA02:"title",PTitle_DA01:"title",Lead_DA01:"intro",CTA_DA01:"cta",Engage_DA01:"cta",Engage_DA02:"cta",Statement_DA01:"emph",Img_DA01:"image",Badge_DA01:"inline",Badges_DA01:"inline",Icon_DA01:"inline","reading-path":"structure",breaking:"emph","steps-horizontal":"data","steps-vertical":"data","case-flow":"content",timeline:"data",slider:"image","gov-header":"other",callout:"content",table:"data","code-block":"content",hint:"content",align:"content","layout-hero":"intro","layout-toc":"structure","layout-cards":"intro","layout-part":"structure","layout-label-title":"title","layout-metrics":"data","layout-infographic":"data","layout-compare":"data","layout-steps":"data","layout-timeline":"data","layout-checklist":"content","layout-stat-row":"data","layout-comparison-table":"data","layout-image-annotate":"image","layout-image-compare":"image","layout-image-steps":"image","layout-image-text":"image","layout-verdict":"emph","layout-audience-fit":"emph","layout-myth-fact":"emph","layout-manifesto":"emph","layout-bridge":"emph","layout-quote":"emph","layout-quote-card":"emph","layout-tweet":"cards","layout-question":"content","layout-faq":"content","layout-summary":"content","layout-notice":"emph","layout-definition":"cards","layout-cases":"cards","layout-author-card":"cards","layout-subscribe":"cards","layout-people":"cards","layout-series":"cards","layout-resource-list":"content","layout-callout":"content","layout-changelog":"other"};function Fp(e){return Pp[e]||"other"}function Bp(e){return e?e.replace(/>(.*?)</gs,"><").replace(/style="[^"]*"/g,t=>t.replace(/\$\{[^}]+\}|var\([^)]+\)|[#][0-9a-f]{3,8}\b/gi,"")).replace(/\s+/g," ").trim():""}function Hp(e){const t=new Map,n=[];for(const i of e){const r=Bp(i.rendered);if(!r){n.push(i);continue}t.get(r)===void 0&&(t.set(r,n.length),n.push(i))}return n}function ke(e){return"spec"in e?{name:e.spec.label,tag:`:::${e.spec.name}`,example:e.spec.example||""}:{name:e.name,tag:`<${e.tag}>`,example:e.example||""}}function Up(e,t){return`:::${e}
${t}
:::`}function qp(e){const t={},n=e.match(/^<([A-Za-z_][\w.-]*)([^>]*)>/);if(!n)return{attrs:t,body:e};const i=n[1],r=n[2].trim();if(r){const c=/([A-Za-z_][\w.-]*)="([^"]*)"/g;let p;for(;p=c.exec(r);)t[p[1]]=p[2]}const o=n[0].length,a=`</${i}>`,l=e.lastIndexOf(a),d=l>o?e.slice(o,l).trim():"";return{attrs:t,body:d}}function Wp({example:e,onCopy:t,onInsert:n}){const i=v.useRef(null),[r,o]=v.useState(!1),{def:a,rendered:l}=e,d=v.useCallback(p=>{const f=i.current;if(!f)return;const g=f.getBoundingClientRect();f.style.setProperty("--mouse-x",`${p.clientX-g.left}px`),f.style.setProperty("--mouse-y",`${p.clientY-g.top}px`)},[]),c=()=>s.jsxs("svg",{viewBox:"0 0 16 16",width:"12",height:"12",fill:"none",stroke:"currentColor",strokeWidth:"1.5",strokeLinecap:"round",strokeLinejoin:"round",children:[s.jsx("rect",{x:"5",y:"5",width:"9",height:"9",rx:"1.5"}),s.jsx("path",{d:"M11 5V3.5A1.5 1.5 0 009.5 2h-6A1.5 1.5 0 002 3.5v6A1.5 1.5 0 003.5 11H5"})]});return s.jsxs("div",{ref:i,className:"group relative break-inside-avoid mb-5 inline-block w-full rounded-2xl bg-white border border-slate-200 shadow-sm cursor-default overflow-hidden",style:{"--mouse-x":"50%","--mouse-y":"50%"},onMouseMove:d,children:[s.jsx("div",{className:"absolute inset-0 z-[3] opacity-0 group-hover:opacity-100 transition-opacity duration-300 pointer-events-none spotlight-glow",style:{background:"radial-gradient(350px circle at var(--mouse-x) var(--mouse-y), rgba(var(--accent-rgb,108,92,231),0.12), rgba(var(--accent-rgb,108,92,231),0.04) 40%, transparent 70%)"}}),s.jsxs("div",{className:"relative z-[1]",children:[s.jsxs("div",{className:"flex items-center justify-between px-5 pt-4 pb-2",children:[s.jsxs("div",{className:"flex items-center gap-2",children:[s.jsx("span",{className:"text-sm font-bold text-slate-800",children:ke(a).name}),s.jsx("span",{className:"text-[10px] font-mono text-[var(--accent)] bg-[var(--accent)]/8 px-1.5 py-0.5 rounded",children:ke(a).tag})]}),s.jsxs("div",{className:"flex items-center gap-1.5",children:[ke(a).example&&s.jsxs("button",{onClick:()=>n(ke(a).example),className:"inline-flex items-center gap-1 px-2.5 py-1 rounded-md border border-emerald-200 bg-emerald-50 text-[10px] text-emerald-600 cursor-pointer hover:bg-emerald-500 hover:text-white hover:border-emerald-500 transition-all",children:[s.jsx("svg",{viewBox:"0 0 16 16",width:"12",height:"12",fill:"none",stroke:"currentColor",strokeWidth:"1.5",strokeLinecap:"round",strokeLinejoin:"round",children:s.jsx("path",{d:"M8 3v10M3 8h10"})}),"插入"]}),ke(a).example&&s.jsxs("button",{onClick:()=>t(ke(a).example),className:"inline-flex items-center gap-1 px-2.5 py-1 rounded-md border border-slate-200 bg-[var(--accent)]/5 text-[10px] text-[var(--accent)] cursor-pointer hover:bg-[var(--accent)] hover:text-white hover:border-[var(--accent)] transition-all",children:[s.jsx(c,{}),"复制"]}),ke(a).example&&s.jsx("button",{onClick:()=>o(p=>!p),className:`inline-flex items-center gap-1 px-2 py-1 rounded-md border text-[10px] cursor-pointer transition-all ${r?"border-slate-300 bg-slate-100 text-slate-600":"border-slate-200 bg-white text-slate-400 hover:text-slate-600 hover:border-slate-300"}`,children:s.jsx("svg",{viewBox:"0 0 16 16",width:"12",height:"12",fill:"none",stroke:"currentColor",strokeWidth:"1.5",strokeLinecap:"round",strokeLinejoin:"round",className:`transition-transform duration-200 ${r?"rotate-180":""}`,children:s.jsx("path",{d:"M4 6l4 4 4-4"})})})]})]}),s.jsx("div",{className:"px-5 pb-4",children:l?s.jsx("div",{className:"preview-content max-w-full [&_section]:transition-opacity [&_section]:duration-150",dangerouslySetInnerHTML:{__html:ut(l)}}):s.jsx("div",{className:"text-xs text-slate-300 italic py-8 text-center",children:"暂无示例"})}),r&&s.jsxs("div",{className:"px-5 pb-5 space-y-4 transition-all duration-150",children:[ke(a).example&&s.jsx("div",{children:s.jsx("pre",{className:"m-0 p-3 bg-[#1e1e2e] rounded-xl border border-white/5 text-[#e0e0e0] font-mono text-[11px] leading-6 overflow-auto whitespace-pre-wrap break-all max-h-40",children:s.jsx("code",{children:ke(a).example})})}),"attrs"in a&&a.attrs&&a.attrs.length>0&&s.jsxs("div",{className:"rounded-lg border border-slate-200 overflow-hidden text-[11px]",children:[s.jsxs("div",{className:"grid grid-cols-[90px_1fr] bg-[var(--accent)]/5 font-semibold text-slate-400 text-[10px] uppercase tracking-wider",children:[s.jsx("span",{className:"px-2.5 py-1.5",children:"属性"}),s.jsx("span",{className:"px-2.5 py-1.5",children:"说明"})]}),a.attrs.map(p=>{var f;return s.jsxs("div",{className:"grid grid-cols-[90px_1fr] border-t border-slate-100",children:[s.jsxs("span",{className:"px-2.5 py-1.5 flex items-center",children:[s.jsx("code",{className:"font-mono text-[11px] text-[var(--accent)] bg-[var(--accent)]/8 px-1.5 py-0.5 rounded",children:p.key}),p.required&&s.jsx("span",{className:"text-[9px] text-red-500 ml-1 font-semibold",children:"必填"})]}),s.jsxs("span",{className:"px-2.5 py-1.5 text-slate-500 text-[11px] leading-5 flex flex-wrap items-center",children:[p.label,p.default&&s.jsxs(s.Fragment,{children:[", 默认 ",s.jsx("code",{className:"font-mono text-[10px] text-[var(--accent)] bg-[var(--accent)]/8 px-1 rounded",children:p.default})]}),(((f=p.options)==null?void 0:f.length)??0)>0&&s.jsxs(s.Fragment,{children:[", 可选 ",p.options.map((g,x)=>s.jsxs("code",{className:"font-mono text-[10px] text-[var(--accent)] bg-[var(--accent)]/8 px-1 rounded",children:[g,x<p.options.length-1?" / ":""]},g))]})]})]},p.key)})]}),"spec"in a&&a.spec.fields&&a.spec.fields.length>0&&s.jsxs("div",{className:"rounded-lg border border-slate-200 overflow-hidden text-[11px]",children:[s.jsxs("div",{className:"grid grid-cols-[90px_1fr] bg-[var(--accent)]/5 font-semibold text-slate-400 text-[10px] uppercase tracking-wider",children:[s.jsx("span",{className:"px-2.5 py-1.5",children:"字段"}),s.jsx("span",{className:"px-2.5 py-1.5",children:"说明"})]}),a.spec.fields.map(p=>s.jsxs("div",{className:"grid grid-cols-[90px_1fr] border-t border-slate-100",children:[s.jsxs("span",{className:"px-2.5 py-1.5 flex items-center",children:[s.jsx("code",{className:"font-mono text-[11px] text-[var(--accent)] bg-[var(--accent)]/8 px-1.5 py-0.5 rounded",children:p.name}),p.required&&s.jsx("span",{className:"text-[9px] text-red-500 ml-1 font-semibold",children:"必填"})]}),s.jsx("span",{className:"px-2.5 py-1.5 text-slate-500 text-[11px] leading-5",children:p.description})]},p.name))]})]})]})]})}function Gp({onClose:e}){const t=J(g=>g.colors),[n,i]=v.useState("all"),[r,o]=v.useState(null),a=v.useRef(),[l,d]=v.useState([]);v.useEffect(()=>{const g=Ze.filter(b=>b.example).map(b=>{try{const{attrs:$,body:S}=qp(b.example);return{def:b,rendered:b.render.call(b,$,S,t),id:b.id}}catch($){return console.error(`[ExtensionPage] render failed for ${b.id}:`,$),{def:b,rendered:"",id:b.id}}}),x=gr.filter(b=>b.spec.example).map(b=>{try{return{def:b,rendered:mo(b,t),id:b.spec.name}}catch($){return console.error(`[ExtensionPage] render failed for ${b.spec.name}:`,$),{def:b,rendered:"",id:b.spec.name}}}),h=sr.getState().themeTokens,u=Ge(),y={t,tokens:h??u,tokensRaw:u,md:"",pTitleLevel1List:[]},j=nl.map(b=>{const $=Op[b.name]??Rp(b.bodyFormat),S=Up(b.name,$),C=S.split(`
`),O=il[b.name];let F="";if(O)try{const W=O.render(y,C[0],C,0);F=(W==null?void 0:W.html)??""}catch(W){console.error(`[ExtensionPage] layout render failed for ${b.name}:`,W)}return{def:{spec:{name:b.name,label:b.label,bodyFormat:b.bodyFormat,example:S},render:()=>"",renderLegacy:()=>""},rendered:F,id:`layout-${b.name}`}}),w=[...g,...x,...j],N=Hp(w);d(N)},[t]);const c=v.useMemo(()=>n==="all"?l:l.filter(g=>Fp(g.id)===n),[l,n]),p=v.useCallback(async g=>{await Fe(g)&&(o("已复制到剪贴板"),clearTimeout(a.current),a.current=setTimeout(()=>o(null),1500))},[]),f=v.useCallback(g=>{window.dispatchEvent(new CustomEvent("m2v-editor-insert",{detail:{text:g}})),o("已插入到编辑器"),clearTimeout(a.current),a.current=setTimeout(()=>o(null),1500)},[]);return s.jsxs("div",{className:"flex h-full flex-col bg-slate-50 overflow-hidden",children:[s.jsxs("div",{className:"flex items-center justify-between border-b border-slate-200 bg-white px-6 py-3 shrink-0",children:[s.jsxs("div",{children:[s.jsx("h2",{className:"text-lg font-bold text-slate-800 m-0",children:"扩展组件"}),s.jsx("p",{className:"text-xs text-slate-400 m-0 mt-0.5",children:"浏览和使用丰富的排版组件"})]}),s.jsx("button",{onClick:e,className:"flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700 transition-colors cursor-pointer",children:s.jsx(kt,{size:16})})]}),s.jsx("div",{className:"flex gap-2 px-6 py-3 border-b border-slate-100 bg-white shrink-0 flex-wrap",children:Dp.map(g=>s.jsx("button",{onClick:()=>i(g.key),className:`rounded-full px-4 py-1.5 text-xs font-medium border transition-all cursor-pointer ${n===g.key?"bg-[var(--accent)] text-white border-[var(--accent)]":"bg-transparent text-slate-500 border-slate-200 hover:text-slate-700 hover:border-slate-300"}`,children:g.label},g.key))}),s.jsx("div",{className:"flex-1 overflow-y-auto px-6 py-6",children:s.jsx("div",{className:"columns-2 gap-5 max-w-[900px] mx-auto max-[1024px]:columns-1",children:c.map(g=>s.jsx(Wp,{example:g,onCopy:p,onInsert:f},g.id))})}),s.jsx(or,{toast:r?{message:r,key:Date.now()}:null})]})}const Kp=`<title type="DA02" badge="LONGFORM" subtitle="从 Markdown 草稿到可复制富文本与长图 PNG 的完整示例。" chips="长图文|公众号|组件化排版">MarkFlow 长图文排版工作流</title>

<reading-path></reading-path>

<lead>
这份示例用于展示长图文模式的核心能力：用扩展 Markdown 写出适合公众号、知识长图和图文平台发布的文章，并一键复制富文本或导出完整长图。
</lead>

<p-title num="01" title="它解决什么问题" subtitle="ONE SOURCE · MULTI OUTPUT" level="1"></p-title>

内容团队常常会遇到同一个问题：素材已经写好，但不同平台需要不同形态。公众号要富文本排版，正式交付要 A4 文档，小红书要分页卡片，活动展示又可能需要风格化 HTML。

MarkFlow 的长图文模式把重点放在“文章表达”上：你仍然写 Markdown，但可以按需要插入标题卡、提示框、步骤流、对比卡片、标签、行动号召和结尾互动，让内容更像一篇已经排好版的发布稿。

> [TIP] 你可以在左侧继续编辑 Markdown，右侧会实时预览长图文效果。完成后可复制富文本，也可导出为一张完整长图 PNG。

<p-title num="02" title="推荐工作流" subtitle="WORKFLOW" level="1"></p-title>

<steps label="HOW IT WORKS" title="从草稿到发布" hint="按顺序完成即可" active="3">
- 写作 | 在 Markdown 中完成正文、标题层级和核心信息
- 增强 | 使用扩展组件突出路径、步骤、对比、结论和行动入口
- 预览 | 在右侧检查段落节奏、组件间距、代码块和公式显示效果
- 交付 | 复制富文本到公众号，或导出长图用于知识平台分发
</steps>

<p-title num="03" title="适合放进长图文的内容" subtitle="CONTENT PATTERN" level="1"></p-title>

<badges tone="accent">产品介绍|教程文章|运营复盘|知识科普|项目更新</badges>

长图文不是把所有信息堆在一页里，而是把读者的阅读路径安排清楚。一个实用结构通常包含：

- 开头：一句话说明读者能获得什么。
- 中段：用步骤、对比、表格或清单拆解复杂信息。
- 结尾：给出结论、行动建议或下一步入口。

<statement>长图文的关键不是装饰更多，而是让读者更快理解重点。</statement>

<p-title num="04" title="对比：手工排版 vs 模块化输出" subtitle="BEFORE / AFTER" level="1"></p-title>

:::compare
排版方式 | 重复调整字号和间距 | 组件化 Markdown 描述内容 | accent
样式一致性 | 多平台样式难以统一 | 按场景生成不同成品 | default
复制导出 | 容易变形 | 导出链路可重复复用 | default
后续复用 | 成本较高 | 示例、指令和内容可持续迭代 | default
:::

<p-title num="05" title="图文混排与基础样式" subtitle="RICH MEDIA & BASICS" level="1"></p-title>

![优质代码环境](https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=800&auto=format&fit=crop&q=80)
图 1：展示高质量插图在长文中的视觉焦点作用

< ![ ![沉浸式开发](https://images.unsplash.com/photo-1555066931-4365d14bab8c?w=400&auto=format&fit=crop&q=80) ![极简工作流](https://images.unsplash.com/photo-1498050108023-c5249f4df085?w=400&auto=format&fit=crop&q=80) ]
图 2：排版精美的图片组合可以增强技术文档的阅读体验

### 文本强调

普通正文用于承载叙述，**加粗** 用于标记重点，*斜体* 用于轻量强调，\`inline code\` 适合标记字段、按钮名或命令。

### 引用提示

> [NOTE] 如果你准备把文章复制到公众号后台，建议先在这里完成结构、标题和组件排版，再去平台后台补充封面、合集和发布设置。

### 表格

| 输出方式 | 适合用途 | 说明 |
| --- | --- | --- |
| 复制富文本 | 公众号、图文编辑器 | 保留排版结构，便于继续发布 |
| 导出长图 | 知识平台、社群传播 | 将整篇内容输出为单张 PNG |
| 复制 AI 指令 | 外部 AI 改写 | 让 AI 按当前支持语法产出内容 |

<p-title num="06" title="实践路径与成功案例" subtitle="TIMELINE & CASES" level="1"></p-title>

我们在实际排版与推广中，整理出了如下关键的发展路线和案例分享：

<timeline>
- 2026年01月 | 项目启动 | 确定纯前端、零后端的极致流畅体验，搭建基础渲染器
- 2026年03月 | 多端适配 | 引入小红书卡片、A4 文档双排版内核，解决图片跨页等排版痛点 | ![多端适配](https://images.unsplash.com/photo-1531403009284-440f080d1e12?w=800&auto=format&fit=crop&q=80)[100% 140px]
- 2026年06月 | 画布升级 | 突破静态限制，加入自由画布，支持横向网页 PPT 导出与打包下载 | ![自由画布](https://images.unsplash.com/photo-1507238691740-187a5b1d37b8?w=800&auto=format&fit=crop&q=80)[100% 140px]
</timeline>

以下是使用本工作台的用户真实反馈：

<case-flow>
- [案例 01] 自媒体主创：原先排版公众号加导出小红书卡片需要2小时，现在10分钟内一键生成双平台素材，工作流效率大升。
- [案例 02] 独立开发者：使用 HTML 可视化模式，用 AI 快速生成炫酷的 Swiss 风格网页 PPT 并在发布会上大获好评。
</case-flow>

<p-title num="07" title="代码与公式也能放进长文" subtitle="TECH CONTENT" level="1"></p-title>

技术类文章常常需要展示代码、配置或公式。长图文模式会继续复用 Markdown 渲染能力，让工程说明和知识教程更容易交付。

\`\`\`ts
const modes = ['article', 'document', 'card', 'html']

function render(mode: string, content: string) {
  return 'render ' + mode + ' with ' + content.length + ' chars'
}
\`\`\`

行内公式示例：当 $a \\ne 0$ 时，二次方程可以写成 $ax^2 + bx + c = 0$。块级公式如下：

$$
x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}
$$

<p-title num="08" title="什么时候切换到其他模式" subtitle="SCENARIO GUIDE" level="1"></p-title>

:::compare
内容形态 | 需要讲清一个完整主题 | 正式归档：切到 A4 文档 | accent
阅读体验 | 希望读者连续阅读 | 多张发布图：切到分页图文 | default
发布渠道 | 要复制富文本到公众号 | 高度视觉化页面：切到自由画布 | default
交付方式 | 要导出一张完整长图 | 需要打印交付：切到 A4 文档 | default
:::

<p-title num="09" title="高级图片版式：轮播图" subtitle="CAROUSEL / SLIDER" level="1"></p-title>

如果你有更多的图片想要展示，还可以使用轮播组件。这在展示多个产品细节、多步骤操作演示时非常有用。由于它利用 SVG 原生动画，因此复制到公众号或各大知识平台时均可完美展示，无需任何额外插件。

<slider images="https://images.unsplash.com/photo-1555066931-4365d14bab8c?w=600&h=300&fit=crop&q=80,https://images.unsplash.com/photo-1498050108023-c5249f4df085?w=600&h=300&fit=crop&q=80,https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=600&h=300&fit=crop&q=80" interval="3" width="600" height="300" type="1"></slider>

<cta label="NEXT STEP" title="把这篇示例改成你的发布稿" button="复制长图文 AI 指令"></cta>

<engage type="DA02" title="感谢阅读" subtitle="继续把同一份内容变成更多可发布的成品。" color="green"></engage>
`,Vp=`---
title: MarkFlow A4 文档排版示例
summary: 纯前端、零后端的 A4 正式文档排版与导出工作台示例。
---

# MarkFlow A4 文档排版示例

| 文档编号 | M2V-DOC-2026-001 | 版本号 | V1.0 |
| --- | --- | --- | --- |
| 编写 | 文档编写组 | 编写日期 | 2026-06-11 |
| 审核 | 技术评审组 | 审核日期 | 2026-06-12 |
| 发布状态 | 已发布 | 机密等级 | 内部公开 |

<page-break/>

## 1. 产品定位

MarkFlow 是一个纯前端、零后端的 Markdown / HTML 多场景渲染与导出工作台。它将同一份内容按交付场景渲染为长图文、A4 文档、分页图文卡片或自由画布 HTML，帮助创作者和项目团队减少重复排版工作。

A4 文档模式面向正式、稳定、可归档的信息表达。它强调版心、页边距、分页、页眉页脚和 PDF 输出一致性，适合承载项目说明书、评审材料、操作手册与交付报告。

## 2. 当前能力总览

| 能力类别 | 当前支持 | A4 文档中的作用 |
| --- | --- | --- |
| 内容输入 | Markdown + YAML 元信息 | 保持正文轻量，可快速粘贴和复用 |
| 文档结构 | 标题、段落、列表、引用、分隔线 | 建立清晰层级，适合正式阅读 |
| 数据表达 | 表格、代码块、行内代码 | 呈现参数、配置、清单和示例 |
| 学术排版 | 行内公式、块级公式、题注 | 支持技术说明、算法说明和图表标注 |
| 分页控制 | 自动分页、手动分页符 | 避免章节断裂，便于 PDF 交付 |
| 导出交付 | 浏览器本地 PDF 导出 | 不依赖后端服务，适合离线工作流 |

## 3. 推荐工作流

1. 在左侧编辑器中输入或粘贴 Markdown 正文。
2. 用一级标题定义文档主题，用二级、三级标题组织章节。
3. 用表格汇总结构化信息，用引用块强调结论、边界或风险。
4. 在必须另起一页的位置插入 \`<page-break/>\`。
5. 检查分页预览、页眉页脚和字体设置，再导出 PDF。

> 建议：A4 文档应优先保证信息密度、层级清晰和打印可读性。复杂视觉组件更适合长图文或 HTML 自由画布模式。

## 4. 正式文档排版示例

### 4.1 段落与强调

普通段落用于承载主要说明文字。你可以使用 **加粗** 标出关键概念，使用 *斜体* 表示轻量强调，也可以使用 \`inline code\` 标记命令、字段名、配置项或技术术语。

当文档需要保留修订痕迹时，也可以使用删除线表达废弃方案：~~旧方案：将所有模式共用同一份默认示例~~。当前推荐方案是为每个模式提供独立示例，让恢复示例时只影响当前工作区。

### 4.2 引用块

> A4 文档模式更适合表达稳定、可打印、可归档的信息。相比长图文，它更重视纸张尺寸、页边距、页眉页脚和分页一致性。

### 4.3 列表

- 适合写项目说明、需求范围、技术方案和交付清单。
- 支持自动分页，并会尽量避免标题、表格、代码块等结构被不合理拆开。
- 支持在长列表之间分页，减少内容溢出或底部遮挡。
- 支持字体、字号、主题、页眉、页脚等文档级设置。

### 4.4 分隔线

下面的分隔线适合用于区分阶段、附录或补充说明。

---

### 4.5 表格

**表 1：MarkFlow 多场景输出能力概览**

| 模式 | 主要输出 | 推荐内容形态 | 典型导出 |
| --- | --- | --- |
| 长图文 | 富文本 / 长图 PNG | 公众号文章、深度教程、产品解读 | 复制富文本、导出长图 |
| A4 文档 | 多页 PDF | 正式说明书、评审文档、归档材料 | 导出 PDF |
| 分页图文 | 封面 + 多张内容图 | 小红书图文、知识卡片、运营素材 | 单图下载、ZIP 打包 |
| 自由画布 HTML | 网页 / 图片 / PDF / ZIP | AI 生成页面、活动页、视觉稿 | 导出图片、PDF、源码包 |

<page-break/>

## 5. 技术与内容能力展示

### 5.1 代码块

\`\`\`ts
type RenderMode = 'article' | 'document' | 'card' | 'html'

interface WorkspaceContent {
  mode: RenderMode
  title: string
  markdown?: string
  html?: string
}

function describeExport(content: WorkspaceContent) {
  const source = content.html ? 'HTML' : 'Markdown'
  return '使用 ' + source + ' 渲染 ' + content.title
}
\`\`\`

### 5.2 数学公式

行内公式示例：当 $a \\ne 0$ 时，方程 $ax^2 + bx + c = 0$ 的解可以用求根公式表示。

块级公式示例：

$$
x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}
$$

### 5.3 图片与题注

正式文档常见的图片题注会放在图片下方，表格题注会放在表格上方。当前 A4 解析会结合上下文判断题注，减少普通段落被误识别为图注或表注的概率。图题和表题分别独立编号，因此前文的表 1 与下方的图 1 可以同时存在。

![MarkFlow 工作流示意图](https://images.unsplash.com/photo-1516321318423-f06f85e504b3?q=80&w=1200&auto=format&fit=crop)

**图 1：从 Markdown 草稿到多场景成品的工作流示意**

## 6. 适用场景与边界

### 6.1 适用场景

**表 2：A4 文档模式适用场景示例**

| 场景 | 推荐写法 | 说明 |
| --- | --- | --- |
| 项目说明 | 标题层级 + 表格 + 引用 | 方便评审和归档 |
| 操作手册 | 步骤列表 + 截图 + 注意事项 | 方便读者按步骤执行 |
| 技术方案 | 代码块 + 公式 + 风险说明 | 适合保留推导过程 |
| 交付报告 | 摘要 + 结论 + 附录 | 适合导出 PDF 后分发 |

### 6.2 使用边界

- A4 文档不追求强装饰性视觉效果；复杂视觉叙事建议使用长图文或 HTML 模式。
- 超宽表格、超长代码块和大图需要在导出前检查分页效果。
- 本项目采用纯前端架构，AI 协作采用“复制指令 → 外部 AI 生成 → 回填系统”的离线流程。

## 7. 结论

MarkFlow 的 A4 文档模式适合把已经写清楚的内容转化为稳定、正式、可导出的交付物。它不替代写作本身，而是把排版、分页和导出这部分重复劳动交给浏览器本地完成。

<page-break/>

## 附录 A：检查清单

- [ ] 标题层级是否清晰。
- [ ] 表格、代码块、图片是否没有溢出页面。
- [ ] 必须另起一页的位置是否已插入 \`<page-break/>\`。
- [ ] 页眉、页脚、字体和字号是否符合交付要求。
- [ ] PDF 文件名是否来自文档标题，便于归档。
`,Yp=`---
title: 一份 Markdown，生成多张发布图
summary: 用分页图文模式，把产品说明、知识清单或运营内容自动拆成封面与多张内容卡。
badge: M2V CARD
hook: 适合小红书、知识卡片和社群分发。
chips: 分页图文|小红书卡片|自动拆页|图片导出
brand: MarkFlow
---

## 这是什么模式

分页图文模式会把一篇 Markdown 拆成一张封面图和多张内容图。

它适合把长文里的重点提炼成一组可以连续滑动阅读的卡片。

## 它和长图文不同

长图文强调“一次读完”，适合公众号文章、教程长文和完整解读。

分页图文强调“逐页吸收”，适合小红书、知识卡片、产品亮点、清单总结和社群转发。

## 当前能展示什么

- 自动读取 frontmatter，生成封面标题、摘要、标签和发布文案。

- 支持 3:4 与 9:16 画布比例，适配不同平台的阅读习惯。

- 根据真实 DOM 高度测量内容，尽量把段落、标题、列表拆到合适的内容卡里。

- 支持作者名、字体和主题色调整，让同一份内容快速换风格。

- 支持单张下载，也支持把所有卡片打包为 ZIP。

## 推荐写法

一张卡只讲一个重点。

段落要短，标题要清楚，列表要有节奏。

如果某个观点需要解释太多，拆成两个小节会比塞进同一页更稳。

## 示例结构

| 部分 | 作用 | 写法建议 |
| --- | --- | --- |
| 封面 | 抓住注意力 | 用 title、summary、hook 控制 |
| 内容卡 | 连续解释重点 | 用二级标题和短段落拆分 |
| 发布文案 | 平台正文 | 由标题、摘要和 chips 自动组合 |

## 适合谁用

- 内容运营：把产品更新、活动介绍、方法论整理成多图。

- 知识博主：把一篇长笔记拆成更容易收藏的知识卡。

- 项目团队：把复杂说明压缩成汇报前的视觉摘要。

- 设计和产品同学：快速验证一组图文叙事是否顺畅。

## 写作小技巧

> [TIP] 如果你发现某张内容图太挤，优先缩短段落，或者把一个长列表拆成多个小标题。

保持每个小节独立完整。

少用大段铺陈，多用“结论 + 解释 + 示例”的结构。

## 导出前检查

- 封面标题是否足够明确。

- 每张内容图是否只承担一个重点。

- 表格和代码块是否没有被挤压。

- 作者名、字体、比例和主题色是否符合发布账号风格。

- 是否需要使用“打包下载”一次性导出全部图片。

## 最后一句

分页图文模式的价值，是把已经写清楚的内容拆成更适合滑动阅读、收藏和转发的一组图片。
`,Xp=`<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>MarkFlow — Markdown 多场景排版工作台</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@200;300;400;500;600;700&family=JetBrains+Mono:wght@400;500;600&family=Noto+Sans+SC:wght@200;300;400;500;700&display=swap" rel="stylesheet">
<style>

:root {
  --paper: #fafaf8;
  --ink: #0a0a0a;
  --grey-1: #f0f0ee;
  --grey-2: #d4d4d2;
  --grey-3: #737373;
  --accent: #002FA7;
  --accent-rgb: 0,47,167;
  --accent-on: #ffffff;
  --accent-bright: #5B7BFF;

  --text-primary: #0a0a0a;
  --text-secondary: #525252;
  --text-helper: #737373;
  --text-on-color: #ffffff;
  --border-subtle: #e0e0e0;
  --border-strong: #a3a3a3;

  --sans: "Inter", "Helvetica Neue", "Arial", "Segoe UI", system-ui, -apple-system, sans-serif;
  --sans-zh: "PingFang SC", "Hiragino Sans GB", "Source Han Sans SC", "Noto Sans SC", "Microsoft YaHei UI", "Microsoft YaHei", sans-serif;
  --mono: "JetBrains Mono", "IBM Plex Mono", "SF Mono", "Consolas", monospace;

  /* Spacing模数 (基于8px) */
  --sp-3: 8px;
  --sp-4: 12px;
  --sp-5: 16px;
  --sp-6: 24px;
  --sp-7: 32px;
  --sp-8: 40px;
  --sp-9: 48px;
  --sp-10: 64px;
  --sp-11: 80px;
  --sp-12: 96px;
  --sp-13: 160px;
}

*, *::before, *::after {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

html, body {
  width: 100%;
  background: #e2e8f0;
  font-family: var(--sans), var(--sans-zh);
  font-feature-settings: "ss01", "cv11";
  -webkit-font-smoothing: antialiased;
  color: var(--ink);
  padding: 32px 0 64px;
}

/* ── SLIDE CONTAINER (Editor Stack Preview Mode) ── */
.slide {
  width: min(100vw - 32px, 960px);
  aspect-ratio: 16 / 9;
  overflow: hidden;
  margin: 0 auto 24px;
  position: relative;
  background: var(--paper);
  border: 1px solid var(--grey-2);
  color: var(--ink);
  transition: transform 0.3s ease;
}

.slide.dark {
  background: var(--ink);
  color: var(--paper);
  border-color: #1a1a1a;
}

.slide.accent {
  background: var(--accent);
  color: var(--accent-on);
  border-color: var(--accent);
}

/* ── CANVAS CARD ── */
.canvas-card {
  position: absolute;
  inset: 0;
  padding: 32px 48px 24px 48px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  z-index: 2;
}

/* ── DOT MATRIX BACKGROUNDS ── */
.dots-fine {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(rgba(0,47,167,0.06) 1px, transparent 1px);
  background-size: 12px 12px;
  pointer-events: none;
  z-index: 1;
}
.slide.accent .dots-fine {
  background-image: radial-gradient(rgba(255,255,255,0.08) 1.2px, transparent 1.2px);
  background-size: 18px 18px;
}
.slide.dark .dots-fine {
  background-image: radial-gradient(rgba(255,255,255,0.04) 1px, transparent 1px);
  background-size: 12px 12px;
}

/* ── CHROME META HEADER & FOOTER ── */
.chrome-min {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-family: var(--mono);
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-helper);
  border-bottom: 1px solid var(--border-subtle);
  padding-bottom: 10px;
  margin-bottom: 16px;
  position: relative;
  z-index: 3;
}
.slide.dark .chrome-min {
  color: rgba(255,255,255,0.5);
  border-bottom-color: rgba(255,255,255,0.1);
}
.slide.accent .chrome-min {
  color: rgba(255,255,255,0.7);
  border-bottom-color: rgba(255,255,255,0.15);
}

.foot-min {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  font-family: var(--mono);
  font-size: 11px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--text-helper);
  border-top: 1px solid var(--border-subtle);
  padding-top: 10px;
  margin-top: 14px;
  position: relative;
  z-index: 3;
}
.slide.dark .foot-min {
  color: rgba(255,255,255,0.5);
  border-top-color: rgba(255,255,255,0.1);
}
.slide.accent .foot-min {
  color: rgba(255,255,255,0.7);
  border-top-color: rgba(255,255,255,0.15);
}

/* ── SWISS TYPOGRAPHY ── */
.h-hero {
  font-family: var(--sans);
  font-weight: 200;
  font-size: min(8vw, 76px);
  line-height: 0.94;
  letter-spacing: -0.04em;
}
.h-hero-zh {
  font-family: var(--sans), var(--sans-zh);
  font-weight: 200;
  font-size: min(6.8vw, 64px);
  line-height: 1.02;
  letter-spacing: -0.025em;
}
.h-xl-zh {
  font-family: var(--sans), var(--sans-zh);
  font-weight: 200;
  font-size: min(4.8vw, 38px);
  line-height: 1.1;
  letter-spacing: -0.02em;
  color: var(--ink);
}
.slide.dark .h-xl-zh {
  color: var(--paper);
}
.h-md {
  font-family: var(--sans), var(--sans-zh);
  font-weight: 600;
  font-size: 18px;
  line-height: 1.25;
  letter-spacing: -0.01em;
}
.t-cat {
  font-family: var(--mono);
  font-size: 11px;
  letter-spacing: 0.24em;
  text-transform: uppercase;
  color: var(--accent);
  font-weight: 600;
  display: block;
}
.slide.accent .t-cat {
  color: var(--accent-on);
}
.slide.dark .t-cat {
  color: var(--accent-bright);
}

.lead {
  font-family: var(--sans), var(--sans-zh);
  font-weight: 300;
  font-size: 16px;
  line-height: 1.6;
  color: var(--text-secondary);
}
.slide.dark .lead {
  color: rgba(255,255,255,0.8);
}
.slide.accent .lead {
  color: rgba(255,255,255,0.9);
}

.body {
  font-family: var(--sans), var(--sans-zh);
  font-weight: 400;
  font-size: 14px;
  line-height: 1.55;
  color: var(--text-secondary);
}
.slide.dark .body {
  color: rgba(255,255,255,0.7);
}

/* ── LAYOUT PRIMITIVES ── */
.grid-12 {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  gap: 24px;
  flex: 1;
}

.split-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 32px;
  flex: 1;
}

/* Hairline border boxes (Straight straight edges, no shadows, no rounding) */
.card-fill {
  background: var(--grey-1);
  border: 1px solid var(--grey-2);
  padding: 16px 20px;
}
.slide.dark .card-fill {
  background: #111;
  border-color: #222;
}

.card-outlined {
  border: 1px solid var(--grey-2);
  padding: 16px 20px;
}
.slide.dark .card-outlined {
  border-color: rgba(255,255,255,0.15);
}

.card-accent {
  background: var(--accent);
  color: var(--accent-on);
  padding: 16px 20px;
}

/* ── SPECIFIC COMPONENT STYLES ── */
/* Slide 2: Concept Asymmetric Layout */
.concept-cols {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 32px;
  flex: 1;
}

.principle-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.principle-row {
  display: grid;
  grid-template-columns: 24px 1fr;
  gap: 12px;
  align-items: start;
}
.principle-num {
  font-family: var(--mono);
  font-size: 12px;
  font-weight: 600;
  color: var(--accent);
  margin-top: 2px;
}
.principle-text strong {
  font-weight: 600;
  color: var(--text-primary);
  display: block;
}

/* Slide 3: Big Quote Statement */
.s3-statement-box {
  display: flex;
  flex-direction: column;
  justify-content: center;
  flex: 1;
  max-width: 800px;
}
.s3-quote {
  font-family: var(--sans), var(--sans-zh);
  font-weight: 200;
  font-size: min(4vw, 32px);
  line-height: 1.45;
  letter-spacing: -0.02em;
  color: var(--accent-on);
}
.s3-quote .em {
  font-style: italic;
  font-weight: 400;
}
.s3-source {
  font-family: var(--mono);
  font-size: 11px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  margin-top: 24px;
  opacity: 0.8;
}

/* Slide 4 & 5: Card Grids */
.swiss-grid-4 {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  flex: 1;
}
.swiss-card-feature {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  height: 100%;
}
.swiss-card-feature .num {
  font-family: var(--mono);
  font-size: 12px;
  color: var(--accent);
  margin-bottom: 12px;
  font-weight: 600;
}
.slide.dark .swiss-card-feature .num {
  color: var(--accent-bright);
}

/* Slide 6: Social Cards */
.mini-card-layout {
  display: flex;
  gap: 16px;
  align-items: flex-end;
  justify-content: center;
  height: 100%;
}
.swiss-mini-card {
  border: 1px solid var(--grey-2);
  background: var(--paper);
  flex-shrink: 0;
  position: relative;
}
.swiss-mini-card.r34 { width: 90px; height: 120px; }
.swiss-mini-card.r916 { width: 68px; height: 120px; }
.swiss-mini-card .header-bar {
  height: 12px;
  background: var(--grey-1);
  border-bottom: 1px solid var(--grey-2);
}
.swiss-mini-card .body-lines {
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.swiss-mini-card .line {
  height: 3px;
  background: var(--grey-2);
}
.swiss-mini-card .line.accent {
  background: var(--accent);
  width: 70%;
}

/* Slide 7: Codeblock and Sandbox */
.swiss-code-block {
  background: var(--grey-1);
  border: 1px solid var(--grey-2);
  padding: 12px 16px;
  font-family: var(--mono);
  font-size: 11px;
  line-height: 1.45;
  color: var(--text-primary);
  white-space: pre-wrap;
  word-break: break-all;
  flex: 1;
}
.slide.dark .swiss-code-block {
  background: #111;
  border-color: #222;
  color: rgba(255,255,255,0.9);
}
.swiss-code-block .keyword { color: #002FA7; font-weight: 600; }
.slide.dark .swiss-code-block .keyword { color: var(--accent-bright); }
.swiss-code-block .comment { color: var(--text-helper); font-style: italic; }

/* Slide 8: Tech Stack Matrix */
.matrix-grid-5 {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  flex: 1;
}
.matrix-cell {
  background: var(--grey-1);
  border: 1px solid var(--grey-2);
  padding: 12px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.matrix-cell.highlight {
  background: var(--accent);
  border-color: var(--accent);
  color: var(--accent-on);
}
.matrix-cell .num {
  font-family: var(--mono);
  font-size: 11px;
  color: var(--accent);
  font-weight: 600;
}
.matrix-cell.highlight .num {
  color: var(--accent-on);
}
.matrix-cell h4 {
  font-family: var(--sans), var(--sans-zh);
  font-size: 14px;
  font-weight: 600;
  margin-top: 6px;
  margin-bottom: 2px;
}
.matrix-cell p {
  font-size: 11px;
  line-height: 1.4;
  color: var(--text-secondary);
}
.matrix-cell.highlight p {
  color: rgba(255,255,255,0.85);
}

/* Slide 9: Architecture */
.arch-grid {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 32px;
  flex: 1;
}
.arch-dir-tree {
  font-family: var(--mono);
  font-size: 11px;
  line-height: 1.7;
  color: var(--text-secondary);
  white-space: pre-wrap;
  border-right: 1px solid var(--grey-2);
  padding-right: 20px;
}
.arch-dir-tree .dir {
  color: var(--accent);
  font-weight: 600;
}
.arch-rows {
  display: flex;
  flex-direction: column;
  gap: 10px;
  justify-content: center;
}
.arch-row-block {
  border: 1px solid var(--grey-2);
  padding: 10px 16px;
  display: grid;
  grid-template-columns: 100px 1fr;
  gap: 16px;
  align-items: center;
}
.arch-row-block.highlight {
  background: var(--accent);
  color: var(--accent-on);
  border-color: var(--accent);
}
.arch-row-block .label {
  font-family: var(--mono);
  font-size: 11px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  font-weight: 600;
}
.arch-row-block .val {
  font-size: 13px;
  font-weight: 500;
}

/* Slide 10: References */
.references-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  flex: 1;
}

/* Slide 11: Quick Start */
.quick-start-box {
  display: flex;
  flex-direction: column;
  justify-content: center;
  flex: 1;
}
.terminal-block {
  background: var(--ink);
  color: var(--paper);
  font-family: var(--mono);
  font-size: 20px;
  padding: 24px;
  border: 1px solid #222;
  margin: 16px 0;
  letter-spacing: 0.05em;
}
.terminal-block .prompt {
  color: var(--accent-bright);
  margin-right: 12px;
  font-weight: 600;
}

/* Slide 12: Thanks Split Closing */
.closing-split {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 40px;
  flex: 1;
}
.closing-left {
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.closing-right {
  display: flex;
  flex-direction: column;
  gap: 16px;
  justify-content: center;
}
.channel-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid rgba(255,255,255,0.15);
  padding-bottom: 12px;
}
.channel-name {
  font-family: var(--mono);
  font-size: 13px;
  letter-spacing: 0.1em;
  font-weight: 600;
}
.channel-value {
  font-size: 13px;
  opacity: 0.8;
}

/* ── PRINT ── */
@media print {
  html { background: white; }
  body { padding: 0; background: white; }
  .slide {
    width: 100%;
    aspect-ratio: 16 / 9;
    margin: 0;
    page-break-after: always;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
    box-shadow: none;
    border-radius: 0;
    border: none;
  }
}

</style>
</head>
<body>

<!-- ── SLIDE 01: COVER (accent IKB) ── -->
<section class="slide accent" data-layout="S01">
  <div class="dots-fine"></div>
  <div class="canvas-card">
    <div class="chrome-min">
      <div class="l">MarkFlow · Open Source Project</div>
      <div class="r">SS · 2026.06.19 · 01 / 12</div>
    </div>
    <div style="flex:1; display:flex; flex-direction:column; justify-content:center; gap:16px;">
      <span class="t-cat">Volume 01 · Tech Product</span>
      <h1 class="h-hero">Mark<br>Flow</h1>
      <p class="lead" style="max-width:550px;">纯前端、零后端的 Markdown / HTML 多场景排版与导出工作台。把同一份内容渲染为面向不同受众的成品形态，并一键复制或导出。</p>
    </div>
    <div class="foot-min">
      <div class="l">React 18 · TypeScript · Vite · CodeMirror 6 · MIT</div>
      <div class="r">→ Swipe / Keyboard keys</div>
    </div>
  </div>
</section>

<!-- ── SLIDE 02: CONCEPT (light) ── -->
<section class="slide" data-layout="S08">
  <div class="canvas-card">
    <div class="chrome-min">
      <div class="l">MarkFlow · Core Concept</div>
      <div class="r">SS · 02 / 12</div>
    </div>
    <div class="concept-cols">
      <div style="display:flex; flex-direction:column; justify-content:center;">
        <span class="t-cat">Core Goal · 核心目标</span>
        <h2 class="h-xl-zh" style="margin: 12px 0;">内容一次创作<br>多端无损分发</h2>
        <p class="body" style="max-width:380px;">免去繁琐的后端依赖与服务部署，利用浏览器原生的渲染能力、排版技术与沙箱机制，实现极致的内容分发与设计自由。</p>
      </div>
      <div style="display:flex; flex-direction:column; justify-content:center;">
        <span class="t-cat" style="margin-bottom:16px;">Three Principles · 三大原则</span>
        <div class="principle-stack">
          <div class="principle-row">
            <span class="principle-num">01</span>
            <div class="principle-text">
              <strong>零服务器依赖</strong>
              <span class="body-sm">所有处理在浏览器本地完成，数据不离开设备，无隐私风险。</span>
            </div>
          </div>
          <div class="principle-row">
            <span class="principle-num">02</span>
            <div class="principle-text">
              <strong>内容重用优先</strong>
              <span class="body-sm">写一次，适配多种成品形态；平台差异由渲染引擎透明处理。</span>
            </div>
          </div>
          <div class="principle-row">
            <span class="principle-num">03</span>
            <div class="principle-text">
              <strong>完全开放可扩展</strong>
              <span class="body-sm">基于 MIT 协议开源，自定义组件与主题可以直接接入排版引擎。</span>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="foot-min">
      <div class="l">Origin · Core Principles</div>
      <div class="r">MarkFlow</div>
    </div>
  </div>
</section>

<!-- ── SLIDE 03: STATEMENT (accent) ── -->
<section class="slide accent" data-layout="S09">
  <div class="dots-fine"></div>
  <div class="canvas-card">
    <div class="chrome-min">
      <div class="l">MarkFlow · Big Idea</div>
      <div class="r">SS · 03 / 12</div>
    </div>
    <div class="s3-statement-box">
      <h1 class="s3-quote">
        “ 同一份 Markdown 草稿，一键切换为<span class="em">公众号长图、A4 文档、小红书卡片或网页 PPT</span> —— 内容一次创作，多端无损分发。”
      </h1>
      <span class="s3-source">— MarkFlow · 核心产品主张</span>
    </div>
    <div class="foot-min">
      <div class="l">Statement · One Source Multi Output</div>
      <div class="r">MarkFlow</div>
    </div>
  </div>
</section>

<!-- ── SLIDE 04: A4 MODE (light) ── -->
<section class="slide" data-layout="S19">
  <div class="canvas-card">
    <div class="chrome-min">
      <div class="l">Mode 01 · A4 Document</div>
      <div class="r">SS · 04 / 12</div>
    </div>
    <div style="display:flex; flex-direction:column; gap:16px; flex:1;">
      <div>
        <span class="t-cat">Mode 01 · A4 Mode</span>
        <h2 class="h-xl-zh" style="margin-top:6px;">纯前端智能分页 · 还原印刷质感</h2>
      </div>
      <div class="swiss-grid-4">
        <div class="card-fill swiss-card-feature">
          <span class="num">01</span>
          <div>
            <h4 style="font-size:15px; margin-bottom:4px;">物理分页</h4>
            <p class="body-sm">Paged.js 真实分页，跨页防孤立标题，续表 thead 自动重复。</p>
          </div>
        </div>
        <div class="card-fill swiss-card-feature">
          <span class="num">02</span>
          <div>
            <h4 style="font-size:15px; margin-bottom:4px;">页眉页脚</h4>
            <p class="body-sm">页码、标题、首行缩进、字体倍率等样式属性完全自调。</p>
          </div>
        </div>
        <div class="card-fill swiss-card-feature">
          <span class="num">03</span>
          <div>
            <h4 style="font-size:15px; margin-bottom:4px;">矢量 PDF</h4>
            <p class="body-sm">调用原生打印机制，背景色百分百保留，可导出超清 PDF。</p>
          </div>
        </div>
        <div class="card-fill swiss-card-feature">
          <span class="num">04</span>
          <div>
            <h4 style="font-size:15px; margin-bottom:4px;">Word 导出</h4>
            <p class="body-sm">docx 格式客户端直接生成，全离线实现，不依赖服务器。</p>
          </div>
        </div>
      </div>
    </div>
    <div class="foot-min">
      <div class="l">W3C Paged Media · Paged.js · docx</div>
      <div class="r">MarkFlow</div>
    </div>
  </div>
</section>

<!-- ── SLIDE 05: LONGFORM MODE (light) ── -->
<section class="slide" data-layout="S19">
  <div class="canvas-card">
    <div class="chrome-min">
      <div class="l">Mode 02 · WeChat Longform</div>
      <div class="r">SS · 05 / 12</div>
    </div>
    <div style="display:flex; flex-direction:column; gap:16px; flex:1;">
      <div>
        <span class="t-cat">Mode 02 · Longform Mode</span>
        <h2 class="h-xl-zh" style="margin-top:6px;">公众号无损渲染 · 万字流畅编辑</h2>
      </div>
      <div class="swiss-grid-4">
        <div class="card-fill swiss-card-feature">
          <span class="num">01</span>
          <div>
            <h4 style="font-size:15px; margin-bottom:4px;">无损渲染</h4>
            <p class="body-sm">内置 steps, timeline, compare, slider 自定义排版组件。</p>
          </div>
        </div>
        <div class="card-fill swiss-card-feature">
          <span class="num">02</span>
          <div>
            <h4 style="font-size:15px; margin-bottom:4px;">一键复制</h4>
            <p class="body-sm">完美兼容微信公众号、知乎、头条编辑器，格式无偏差。</p>
          </div>
        </div>
        <div class="card-fill swiss-card-feature">
          <span class="num">03</span>
          <div>
            <h4 style="font-size:15px; margin-bottom:4px;">流畅编辑</h4>
            <p class="body-sm">输入防抖 Debounce 与状态解耦设计，确保万字编辑不卡顿。</p>
          </div>
        </div>
        <div class="card-fill swiss-card-feature">
          <span class="num">04</span>
          <div>
            <h4 style="font-size:15px; margin-bottom:4px;">本地持久化</h4>
            <p class="body-sm">Zustand persist 中间件自动缓存，关窗数据永不丢失。</p>
          </div>
        </div>
      </div>
    </div>
    <div class="foot-min">
      <div class="l">CodeMirror 6 · Zustand · Clipboard 保真</div>
      <div class="r">MarkFlow</div>
    </div>
  </div>
</section>

<!-- ── SLIDE 06: SOCIAL CARDS (light) ── -->
<section class="slide" data-layout="S08">
  <div class="canvas-card">
    <div class="chrome-min">
      <div class="l">Mode 03 · Social Cards</div>
      <div class="r">SS · 06 / 12</div>
    </div>
    <div class="concept-cols">
      <div style="display:flex; flex-direction:column; justify-content:center;">
        <span class="t-cat">Mode 03 · Card Mode</span>
        <h2 class="h-xl-zh" style="margin: 12px 0;">小红书多页卡片</h2>
        <p class="body" style="margin-bottom:16px; max-width:380px;">3:4 与 9:16 多尺寸自动适配，Frontmatter 智能解析生成社交文案，支持批量 ZIP 导出或逐张高清 PNG 下载。</p>
        <p class="body-sm" style="color:var(--text-helper);">采用 modern-screenshot 高清截图方案，结合 fflate 异步压缩算法，完全运行在浏览器沙箱端，保障零网络传输和隐私安全。</p>
      </div>
      <div class="mini-card-layout">
        <div class="swiss-mini-card r34">
          <div class="header-bar"></div>
          <div class="body-lines">
            <div class="line accent"></div>
            <div class="line"></div>
            <div class="line"></div>
            <span style="font-family:var(--mono); font-size:10px; position:absolute; bottom:8px; left:10px;">3:4</span>
          </div>
        </div>
        <div class="swiss-mini-card r916">
          <div class="header-bar"></div>
          <div class="body-lines">
            <div class="line accent"></div>
            <div class="line"></div>
            <span style="font-family:var(--mono); font-size:10px; position:absolute; bottom:8px; left:10px;">9:16</span>
          </div>
        </div>
        <div style="display:flex; flex-direction:column; font-family:var(--mono); font-size:10px; color:var(--text-helper); line-height:1.6; margin-left:12px;">
          <span>MULTI RATIO</span>
          <span>AUTO NUMBERING</span>
        </div>
      </div>
    </div>
    <div class="foot-min">
      <div class="l">fflate ZIP · modern-screenshot · PNG 导出</div>
      <div class="r">MarkFlow</div>
    </div>
  </div>
</section>

<!-- ── SLIDE 07: HTML CANVAS (dark) ── -->
<section class="slide dark" data-layout="S08">
  <div class="dots-fine"></div>
  <div class="canvas-card">
    <div class="chrome-min">
      <div class="l">Mode 04 · HTML Canvas</div>
      <div class="r">SS · 07 / 12</div>
    </div>
    <div class="concept-cols">
      <div style="display:flex; flex-direction:column; justify-content:center;">
        <span class="t-cat">Mode 04 · HTML Canvas</span>
        <h2 class="h-xl-zh" style="margin: 12px 0; color: #fff;">沙箱隔离渲染 · 网页 PPT 专属呈现</h2>
        <p class="body" style="color:rgba(255,255,255,0.7); max-width:380px;">内置基于 iframe 容器的隔离机制，防止外部样式污染。预加载本地化 Tailwind 运行时，完美转译「瑞士国际主义风格」横向翻页网页 PPT。</p>
        <p class="body-sm" style="color:rgba(255,255,255,0.5); margin-top:16px;">基于 MutationObserver 机制自动探测 DOM 渲染稳定性，保障截图及矢量导出的稳定性。</p>
      </div>
      <div style="display:flex; flex-direction:column; justify-content:center;">
        <div class="swiss-code-block">
<span class="comment">// 探测 DOM 稳定后截图</span>
<span class="keyword">async function</span> waitForStability() {
  <span class="keyword">return new</span> Promise(resolve => {
    <span class="keyword">const</span> obs = <span class="keyword">new</span> MutationObserver(debounce(() => {
      obs.disconnect(); resolve();
    }, 200));
    obs.observe(document.body, {
      subtree: <span class="keyword">true</span>, childList: <span class="keyword">true</span>
    });
  });
}</div>
      </div>
    </div>
    <div class="foot-min">
      <div class="l">iframe Sandbox · MutationObserver · Tailwind CSS</div>
      <div class="r">MarkFlow</div>
    </div>
  </div>
</section>

<!-- ── SLIDE 08: TECH STACK (light) ── -->
<section class="slide" data-layout="S15">
  <div class="canvas-card">
    <div class="chrome-min">
      <div class="l">MarkFlow · Technology Stack</div>
      <div class="r">SS · 08 / 12</div>
    </div>
    <div style="display:flex; flex-direction:column; gap:16px; flex:1;">
      <span class="t-cat">Core Stack · 技术栈全览</span>
      <div class="matrix-grid-5">
        <div class="matrix-cell highlight">
          <span class="num">01</span>
          <div>
            <h4>React 18</h4>
            <p>UI 渲染框架</p>
          </div>
        </div>
        <div class="matrix-cell highlight">
          <span class="num">02</span>
          <div>
            <h4>CodeMirror 6</h4>
            <p>编辑器内核组件</p>
          </div>
        </div>
        <div class="matrix-cell">
          <span class="num">03</span>
          <div>
            <h4>Zustand</h4>
            <p>轻量本地状态管理</p>
          </div>
        </div>
        <div class="matrix-cell">
          <span class="num">04</span>
          <div>
            <h4>Tailwind v4</h4>
            <p>原子化样式系统</p>
          </div>
        </div>
        <div class="matrix-cell">
          <span class="num">05</span>
          <div>
            <h4>Screenshot</h4>
            <p>modern-screenshot</p>
          </div>
        </div>
        <div class="matrix-cell">
          <span class="num">06</span>
          <div>
            <h4>自研引擎</h4>
            <p>扩展组件解析逻辑</p>
          </div>
        </div>
        <div class="matrix-cell">
          <span class="num">07</span>
          <div>
            <h4>Paged.js</h4>
            <p>物理分页标准实现</p>
          </div>
        </div>
        <div class="matrix-cell">
          <span class="num">08</span>
          <div>
            <h4>docx</h4>
            <p>客户端 Word 导出</p>
          </div>
        </div>
        <div class="matrix-cell">
          <span class="num">09</span>
          <div>
            <h4>fflate</h4>
            <p>批量异步 ZIP 压缩</p>
          </div>
        </div>
        <div class="matrix-cell">
          <span class="num">10</span>
          <div>
            <h4>MIT</h4>
            <p>开源协议许可证</p>
          </div>
        </div>
      </div>
    </div>
    <div class="foot-min">
      <div class="l">Browser Native Engine · Zero Server Cost</div>
      <div class="r">MarkFlow</div>
    </div>
  </div>
</section>

<!-- ── SLIDE 09: ARCHITECTURE (light) ── -->
<section class="slide" data-layout="S17">
  <div class="canvas-card">
    <div class="chrome-min">
      <div class="l">MarkFlow · Directory & Layering</div>
      <div class="r">SS · 09 / 12</div>
    </div>
    <div class="arch-grid">
      <div class="arch-dir-tree">
<span class="dir">src/</span>
├─ <span class="dir">engine/</span> (自研 Markdown 解析引擎)
│  ├─ <span class="dir">utils/</span> (核心语法解析)
│  └─ <span class="dir">editor-components/</span> (特殊排版组件)
├─ <span class="dir">components/</span> (共享交互组件)
│  └─ <span class="dir">editor/</span> (CodeMirror 6 封装)
├─ <span class="dir">modes/</span> (四大排版模式)
│  ├─ <span class="dir">article/</span> | <span class="dir">document/</span>
│  └─ <span class="dir">card/</span> | <span class="dir">html/</span>
└─ <span class="dir">App.tsx</span> (全局模式切换入口)</div>
      <div class="arch-rows">
        <div class="arch-row-block">
          <span class="label">输入层 · Input</span>
          <span class="val">CodeMirror 6 编辑器 ↔ 自由 HTML 画布输入实时绑定</span>
        </div>
        <div class="arch-row-block">
          <span class="label">引擎层 · Engine</span>
          <span class="val">自定义 Markdown 解析（steps / timeline / slider）与 iframe 沙箱样式隔离</span>
        </div>
        <div class="arch-row-block highlight">
          <span class="label">渲染层 · Modes</span>
          <span class="val">四大渲染终端：长图文、A4 打印、小红书社交卡片、自由网页 PPT</span>
        </div>
        <div class="arch-row-block">
          <span class="label">导出层 · Export</span>
          <span class="val">一键复制富文本、高清 PNG 截图、浏览器 PDF 矢量打印、批量 ZIP 压缩包</span>
        </div>
      </div>
    </div>
    <div class="foot-min">
      <div class="l">System Layering & Directory Design</div>
      <div class="r">MarkFlow</div>
    </div>
  </div>
</section>

<!-- ── SLIDE 10: REFERENCES (light) ── -->
<section class="slide" data-layout="S13">
  <div class="canvas-card">
    <div class="chrome-min">
      <div class="l">MarkFlow · Open Source References</div>
      <div class="r">SS · 10 / 12</div>
    </div>
    <div style="display:flex; flex-direction:column; gap:16px; flex:1;">
      <span class="t-cat">References · 站在巨人的肩膀上</span>
      <div class="references-grid">
        <div class="card-fill" style="display:flex; flex-direction:column; justify-content:space-between;">
          <div>
            <span style="font-family:var(--mono); font-size:12px; font-weight:600; color:var(--accent);">01</span>
            <h4 style="font-size:16px; margin: 8px 0 4px;">r-markdown</h4>
            <p class="body-sm">移植了微信公众号渲染引擎的核心解析逻辑、多款排版组件以及主题配色方案。是长图文模式的技术基石。</p>
          </div>
          <span class="body-sm" style="color:var(--text-helper); font-family:var(--mono); margin-top:12px;">RobocopMao · 引擎算法</span>
        </div>
        <div class="card-fill" style="display:flex; flex-direction:column; justify-content:space-between;">
          <div>
            <span style="font-family:var(--mono); font-size:12px; font-weight:600; color:var(--accent);">02</span>
            <h4 style="font-size:16px; margin: 8px 0 4px;">html-anything</h4>
            <p class="body-sm">启示了 HTML 可视化画布中基于 iframe 容器的安全隔离设计与原生图片导出规范，奠定沙箱架构基础。</p>
          </div>
          <span class="body-sm" style="color:var(--text-helper); font-family:var(--mono); margin-top:12px;">nexu-io · 隔离与沙箱设计</span>
        </div>
        <div class="card-fill" style="display:flex; flex-direction:column; justify-content:space-between;">
          <div>
            <span style="font-family:var(--mono); font-size:12px; font-weight:600; color:var(--accent);">03</span>
            <h4 style="font-size:16px; margin: 8px 0 4px;">guizang-ppt-skill</h4>
            <p class="body-sm">自由画布中「电子杂志」「瑞士国际主义」的风格参考，以及网页 PPT 主题节奏、标准图片比例等经验启发。</p>
          </div>
          <span class="body-sm" style="color:var(--text-helper); font-family:var(--mono); margin-top:12px;">op7418 · 设计美学转译</span>
        </div>
      </div>
    </div>
    <div class="foot-min">
      <div class="l">Open Source Community · Standing on Shoulders</div>
      <div class="r">MarkFlow</div>
    </div>
  </div>
</section>

<!-- ── SLIDE 11: QUICK START (dark) ── -->
<section class="slide dark" data-layout="S12">
  <div class="dots-fine"></div>
  <div class="canvas-card">
    <div class="chrome-min">
      <div class="l">MarkFlow · Get Started</div>
      <div class="r">SS · 11 / 12</div>
    </div>
    <div class="quick-start-box">
      <span class="t-cat">Quick Start · 快速开始</span>
      <div class="terminal-block">
        <span class="prompt">$</span>pnpm install && pnpm dev
      </div>
      <p class="body" style="color:rgba(255,255,255,0.7); max-width:550px;">运行环境依赖 Node.js ≥ 20 与 pnpm ≥ 10。默认启动于 http://localhost:5173，本地秒级热更新，完全在本地沙箱运行。</p>
    </div>
    <div class="foot-min">
      <div class="l">Zero Server · MIT License · 4 Print Modes</div>
      <div class="r">MarkFlow</div>
    </div>
  </div>
</section>

<!-- ── SLIDE 12: THANKS (dark) ── -->
<section class="slide dark" data-layout="S10">
  <div class="dots-fine"></div>
  <div class="canvas-card">
    <div class="chrome-min">
      <div class="l">MarkFlow · Closing</div>
      <div class="r">SS · 12 / 12</div>
    </div>
    <div class="closing-split">
      <div class="closing-left">
        <span class="t-cat">End of Field Note</span>
        <h1 class="h-hero" style="color: #fff; margin-top:12px;">Build once.<br>Run forever.</h1>
        <p class="body" style="color:rgba(255,255,255,0.6); margin-top:16px;">内容一次创作，多端无损分发。</p>
      </div>
      <div class="closing-right">
        <span class="t-cat" style="margin-bottom:12px;">Contact & Community</span>
        <div class="channel-row">
          <span class="channel-name">GitHub</span>
          <span class="channel-value">huanyu-a/MarkFlow</span>
        </div>
        <div class="channel-row">
          <span class="channel-name">Feedback</span>
          <span class="channel-value">Open Issue</span>
        </div>
        <div class="channel-row">
          <span class="channel-name">License</span>
          <span class="channel-value">MIT License</span>
        </div>
      </div>
    </div>
    <div class="foot-min">
      <div class="l">Zero Network Transfer · Privacy Preserved</div>
      <div class="r">THANK YOU.</div>
    </div>
  </div>
</section>

</body>
</html>
`,Zp=v.lazy(()=>$e(()=>import("./ArticleMode-CnNbpX5i.js"),__vite__mapDeps([6,1,7,8,9,10])).then(e=>({default:e.ArticleMode}))),Jp=v.lazy(()=>$e(()=>import("./DocumentMode-Btp15Yh8.js"),__vite__mapDeps([11,1,8,9,7,10,12,13])).then(e=>({default:e.DocumentMode}))),Qp=v.lazy(()=>$e(()=>import("./CardMode-CnbRJrKs.js"),__vite__mapDeps([14,1,10,7,8,9,15,12,13])).then(e=>({default:e.CardMode}))),ef=v.lazy(()=>$e(()=>import("./HtmlMode-B6FSZu4o.js"),__vite__mapDeps([16,1,8,9,7,13,15])).then(e=>({default:e.HtmlMode})));function tf(){return s.jsx("main",{className:"flex min-h-0 flex-1 items-center justify-center bg-slate-50 text-sm text-slate-400",children:"正在加载工作台..."})}function nf(){return s.jsxs("main",{className:"flex min-h-0 flex-1 flex-col items-center justify-center gap-3 bg-slate-50 text-sm text-slate-500",children:[s.jsx("span",{children:"渲染模块出现异常"}),s.jsx("button",{onClick:()=>window.location.reload(),className:"rounded-md border border-slate-200 bg-white px-4 py-1.5 text-slate-700 hover:bg-slate-50 transition-colors cursor-pointer",children:"刷新页面"})]})}const si={article:Kp,document:Vp,card:Yp,html:Xp};function rf(){const e=me(z=>z.articleMarkdown),t=me(z=>z.setArticleMarkdown),n=me(z=>z.documentMarkdown),i=me(z=>z.setDocumentMarkdown),r=me(z=>z.cardMarkdown),o=me(z=>z.setCardMarkdown),a=me(z=>z.html),l=me(z=>z.setHtml),d=me(z=>z.syncDemoContent),c=me(z=>z.restoreDemo),p=me(z=>z.hasHydrated),f=J(z=>z.colors),g=J(z=>z.accent),x=J(z=>z.setTheme),h=J(z=>z.setThemeProfile),u=J(z=>z.mode),y=J(z=>z.cardAspect),j=J(z=>z.setMode),w=J(z=>z.platform),N=J(z=>z.documentSettings),b=J(z=>z.updateDocumentSettings),$=J(z=>z.triggerGuide),S=J(z=>z.hasHydrated),C=p&&S,[O,F]=v.useState(!1),[Q,W]=v.useState(!1),[Z,I]=v.useState(!1),[L,B]=v.useState(!1),[G,se]=v.useState(!1),[M,q]=v.useState(!1),[ce,H]=v.useState(!1),[re]=v.useState(null),[de,T]=v.useState(()=>typeof window<"u"?window.innerWidth:1200),[ie,oe]=v.useState(null),pe=v.useCallback(z=>oe({message:z,key:Date.now()}),[]),Je=v.useCallback(async z=>{const Nt=await Fe(mr(z));pe(Nt?`已复制「${z.name}」风格指令`:"复制失败，请重试")},[pe]),Qe=v.useCallback(async()=>{if(u==="html"){B(!0),pe("自由画布的 AI 指令请在指令库中按风格选择后复制");return}let z;switch(u){case"article":z=qt();break;case"document":z=mn();break;case"card":z=wr(y);break;default:z=qt()}const Nt=await Fe(z);pe(Nt?`已复制${u==="article"?"长图文":u==="document"?"A4 文档":"小红书卡片"} AI 排版指令`:"复制失败")},[u,y,pe]),Se=v.useCallback(z=>T(z),[]);v.useEffect(()=>{de>=960&&H(!1)},[de]),v.useEffect(()=>{C&&d(si)},[C,d]);const hn=J(z=>z.restoreDocumentSettingsDemo),jt=v.useCallback(()=>{window.confirm("确定要恢复当前模块的示例内容吗？这将会覆盖当前编辑区内容。")&&(u==="document"&&hn(),c(u,si),pe("已恢复当前模块示例"))},[u,c,hn,pe]);v.useEffect(()=>{const z=()=>F(!0);return window.addEventListener("m2v-open-settings",z),()=>window.removeEventListener("m2v-open-settings",z)},[]);const Nr=v.useCallback(z=>{if(z==="ai"){I(!1),W(!0);return}if(z==="library"){B(!0);return}if(z==="settings"){F(!0);return}if(z==="demo"){jt();return}if(z==="help"){$(u);return}},[jt,$,u]);return s.jsxs("div",{className:"flex h-full flex-col overflow-hidden",children:[s.jsx(Cc,{mode:u,setMode:j,accent:g,setTheme:x,setThemeProfile:h,onOpenMobileMenu:()=>H(!0),onWidthChange:Se}),s.jsxs("div",{className:"flex min-h-0 flex-1",children:[s.jsx(qc,{activePanel:re,onPanelChange:Nr,onOpenAiTypeset:()=>{I(!0),W(!0)},onOpenExtension:()=>q(!0),onCopyGuide:Qe}),s.jsx(Mt,{fallback:s.jsx(nf,{}),children:s.jsxs(v.Suspense,{fallback:s.jsx(tf,{}),children:[u==="article"&&s.jsx(Zp,{markdown:e,setMarkdown:t,colors:f,onToast:pe}),u==="html"&&s.jsx(ef,{html:a,setHtml:l,onToast:pe}),u==="document"&&s.jsx(Jp,{markdown:n,setMarkdown:i,colors:f,settings:N,updateSettings:b,onToast:pe}),u==="card"&&s.jsx(Qp,{markdown:r,setMarkdown:o,colors:f,platform:w==="xiaohongshu"?w:"xiaohongshu",onToast:pe})]})},u)]}),Q&&s.jsx("div",{className:"fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-xs px-4",children:s.jsx(Mt,{fallback:s.jsx("div",{className:"rounded-lg border border-red-200 bg-white p-6 text-center text-sm text-red-600",children:"AI 排版组件加载异常，请刷新页面重试"}),children:s.jsx(Wn,{defaultWidth:768,defaultHeight:660,minWidth:400,minHeight:300,children:s.jsx(Pd,{mode:u,onToast:pe,onClose:()=>{W(!1),I(!1)},autoRun:Z})})})}),M&&s.jsx("div",{className:"fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-xs px-4",children:s.jsx(Mt,{fallback:s.jsx("div",{className:"rounded-lg border border-red-200 bg-white p-6 text-center text-sm text-red-600",children:"组件库加载异常，请刷新页面重试"}),children:s.jsx(Wn,{defaultWidth:900,defaultHeight:700,minWidth:500,minHeight:400,children:s.jsx(Gp,{onClose:()=>q(!1)})})})}),s.jsx(jp,{mode:u,open:L,onClose:()=>B(!1),onCopy:Je,onToast:pe}),s.jsx(zp,{isOpen:O,onClose:()=>F(!1)}),s.jsx(Ap,{isOpen:G,onClose:()=>se(!1)}),s.jsx(or,{toast:ie}),s.jsx(Hc,{isOpen:ce,onClose:()=>H(!1),mode:u,setMode:j,accent:g,setTheme:x,onTriggerGuide:()=>$(u),onOpenSettings:()=>F(!0),onOpenPrivacy:()=>se(!0),onRestoreDemo:jt,onOpenAiTypeset:()=>{H(!1),I(!1),W(!0)}}),s.jsx(Ip,{})]})}oi(document.getElementById("root")).render(s.jsx(v.StrictMode,{children:s.jsx(rf,{})}));export{dr as A,mt as B,uf as C,$t as D,sc as E,cr as F,vf as G,Lt as H,zc as I,Gn as J,gf as K,ff as L,hf as M,no as N,yf as O,Un as P,bf as Q,mf as R,jr as S,ln as T,le as U,Me as V,kf as W,kt as X,cf as Y,lf as Z,$e as _,xf as a,dn as b,Fe as c,df as d,$f as e,Sf as f,U as g,E as h,A as i,K as j,V as k,X as l,R as m,D as n,be as o,pf as p,ac as q,ei as r,m as s,wf as t,J as u,Bt as v,ut as w,nr as x,ae as y,Ns as z};
