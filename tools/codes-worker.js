/* Ironfield server: a Cloudflare Worker for redeem codes, live settings and verified badges.
   Setup: a secret named SECRET (the same key as in the owner's admin panel) and a KV namespace bound as USED.
   A code is 15 characters: 6-character id + 1 letter + 8-character signature (HMAC-SHA256 of the id and letter,
   keyed with SECRET), so only someone with the key can make codes.
   - Letter A (old codes): all vehicles; KV holds "used:<time>" or "off" under the bare id.
   - Letter S (panel codes): what the code gives lives in KV under "c:<id>" (all vehicles, RP, one vehicle or a
     badge, with a number of uses, an optional expiry and an on/off switch). Deleting the record kills the code.
   KV also holds "_all"="off" (every code off), "cfg" (news and live settings, read by the game from GET /config),
   "log" (the last 200 redemptions), "b:<id>" (badges given out) and "n:<sig>" (used admin signatures, so a
   request can't be replayed). Admin requests are signed with the key and valid for 5 minutes. */
const A='ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const OLD={A:['all_vehicles']};
const enc=new TextEncoder();
async function hmac(secret,msg){const k=await crypto.subtle.importKey('raw',enc.encode(secret),{name:'HMAC',hash:'SHA-256'},false,['sign']);return new Uint8Array(await crypto.subtle.sign('HMAC',k,enc.encode(msg)));}
function b32(bytes,n){let bits=0,val=0,out='';for(const b of bytes){val=((val<<8)|b)&0xffff;bits+=8;while(bits>=5){out+=A[(val>>>(bits-5))&31];bits-=5;}if(out.length>=n)break;}return out.slice(0,n);}
const hex=b=>[...b].map(x=>x.toString(16).padStart(2,'0')).join('');
function same(a,b){a=String(a);b=String(b);if(a.length!==b.length)return false;let r=0;for(let i=0;i<a.length;i++)r|=a.charCodeAt(i)^b.charCodeAt(i);return r===0;}
const CORS={'access-control-allow-origin':'*','access-control-allow-methods':'GET, POST, OPTIONS','access-control-allow-headers':'content-type'};
const json=(o,s=200,h)=>new Response(JSON.stringify(o),{status:s,headers:Object.assign({'content-type':'application/json'},CORS,h||{})});
const txt=(v,n)=>String(v==null?'':v).replace(/[\u0000-\u001f<>]/g,'').slice(0,n);
const int=(v,lo,hi)=>Math.min(hi,Math.max(lo,Math.round(+v||0)));
const randId=n=>{const r=crypto.getRandomValues(new Uint8Array(n));let s='';for(const v of r)s+=A[v%32];return s;};
const perksOf=r=>r.k==='all'?['all_vehicles']:r.k==='rp'?['rp:'+r.v]:r.k==='veh'?['veh:'+r.v]:r.k==='badge'?['badge']:[];
const meta=r=>({k:r.k,v:r.v,uses:r.uses,n:r.n,exp:r.exp,off:r.off,note:r.note,made:r.made});
async function putRec(env,id,r){await env.USED.put('c:'+id,JSON.stringify(r),{metadata:meta(r)});}
/* live settings: only these fields, clamped, are ever stored or served */
function cleanCfg(c){c=c&&typeof c==='object'?c:{};const n=c.news||{},rp=c.rp||{},m=c.maint||{};
  return{news:{on:!!n.on,text:txt(n.text,300),id:txt(n.id,20)},rp:{mul:[1,1.5,2,3].includes(+rp.mul)?+rp.mul:1,until:int(rp.until,0,1e14)},
    maint:{on:!!m.on,text:txt(m.text,200)},minBuild:int(c.minBuild,0,1e6)};}
async function listAll(env,prefix){const out=[];let cursor;for(let i=0;i<10;i++){const r=await env.USED.list({prefix,cursor});for(const k of r.keys)out.push([k.name.slice(prefix.length),k.metadata||{}]);if(r.list_complete)break;cursor=r.cursor;}return out;}
export default{async fetch(req,env){
  if(req.method==='OPTIONS')return json({});
  const path=new URL(req.url).pathname;
  const S=env.SECRET;if(!S||!env.USED)return json({ok:false,reason:'setup'},500);
  if(req.method==='GET'&&path==='/config')return json(cleanCfg(await env.USED.get('cfg','json')),200,{'cache-control':'public, max-age=60'});
  if(req.method!=='POST')return json({ok:false,reason:'method'},405);
  let b={};try{b=await req.json();}catch(e){}
  if(path==='/redeem'){
    const code=String(b.code||'').toUpperCase().replace(/[^A-Z0-9]/g,''),nonce=String(b.nonce||'').slice(0,40);
    if(code.length!==15)return json({ok:false,reason:'invalid'});
    const id=code.slice(0,6),fl=code[6];
    if((fl!=='S'&&!OLD[fl])||!same(b32(await hmac(S,'code:'+id+fl),8),code.slice(7)))return json({ok:false,reason:'invalid'});
    if(await env.USED.get('_all')==='off')return json({ok:false,reason:'off'});
    const t=Date.now(),name=txt(b.name,16),ticket=hex(await hmac(S,'ticket:'+id+':'+fl+':'+t+':'+nonce));
    let perks,out={};
    if(fl==='S'){const r=await env.USED.get('c:'+id,'json');if(!r)return json({ok:false,reason:'invalid'});
      if(r.off)return json({ok:false,reason:'off'});if(r.exp&&t>r.exp)return json({ok:false,reason:'expired'});if(r.n>=r.uses)return json({ok:false,reason:'used'});
      const dev=String(b.dev||'').replace(/[^a-z0-9]/gi,'').slice(0,24);if(dev&&(r.by||[]).includes(dev))return json({ok:false,reason:'mine'});
      r.n++;r.by=(r.by||[]).concat(dev?[dev]:[]).slice(-500);await putRec(env,id,r);perks=perksOf(r);
      if(r.k==='badge'){const tid=randId(10),label=txt(r.v,16)||'Creator';await env.USED.put('b:'+tid,JSON.stringify({label,name,made:t,code:id}),{metadata:{label,name,made:t,code:id}});
        out={badge:tid+'.'+b32(await hmac(S,'badge:'+tid),12),label};}}
    else{const st=await env.USED.get(id);if(st)return json({ok:false,reason:st==='off'?'off':'used'});await env.USED.put(id,'used:'+t);perks=OLD[fl];}
    const log=(await env.USED.get('log','json'))||[];log.push({t,id,p:perks.join(','),name});await env.USED.put('log',JSON.stringify(log.slice(-200)));
    return json(Object.assign({ok:true,id,flags:fl,perks,t,ticket},out));}
  if(path==='/verify'){/* a host checks that a guest really redeemed a code (tickets last 12 hours) */
    const id=String(b.id||'').slice(0,6),fl=String(b.flags||''),t=+b.t||0,nonce=String(b.nonce||'').slice(0,40);
    if((fl!=='S'&&!OLD[fl])||Date.now()-t>12*3600e3||t>Date.now()+60e3)return json({ok:false});
    if(!same(hex(await hmac(S,'ticket:'+id+':'+fl+':'+t+':'+nonce)),b.ticket))return json({ok:false});
    if(fl==='S'){const r=await env.USED.get('c:'+id,'json');return json({ok:!!r,perks:r?perksOf(r):[]});}
    return json({ok:true,perks:OLD[fl]});}
  if(path==='/badge'){/* anyone checks badge tokens: real, not taken back, and for which callsign (a badge only shows under that name) */
    const tks=Array.isArray(b.tks)?b.tks.slice(0,20).map(x=>String(x).slice(0,30)):[],labels={};
    for(const tk of tks){const[tid,sig]=tk.split('.');labels[tk]=null;if(!tid||!sig||!same(b32(await hmac(S,'badge:'+tid),12),sig))continue;
      const r=await env.USED.get('b:'+tid,'json');if(r)labels[tk]={l:r.label,n:r.name};}
    return json({ok:true,labels});}
  if(path==='/admin'){
    const ts=+b.ts||0,act=String(b.action||''),ids=Array.isArray(b.ids)?b.ids.map(x=>String(x).slice(0,12)).slice(0,500):[],data=typeof b.data==='string'?b.data.slice(0,4000):null;
    const sig=String(b.sig||'');
    if(Math.abs(Date.now()-ts)>5*60e3||!same(hex(await hmac(S,'admin:'+ts+':'+act+':'+ids.join(',')+(data!==null?':'+data:''))),sig))return json({ok:false,reason:'auth'},403);
    if(act!=='status'&&act!=='list'&&act!=='log'&&act!=='badges'&&act!=='cfgget'){if(await env.USED.get('n:'+sig))return json({ok:false,reason:'replay'},403);await env.USED.put('n:'+sig,'1',{expirationTtl:600});}
    let d={};try{d=data?JSON.parse(data):{};}catch(e){return json({ok:false,reason:'data'});}
    /* old codes (letter A) */
    if(act==='status'){const status={};for(const id of ids)status[id]=(await env.USED.get(id))||'unused';return json({ok:true,status,all:(await env.USED.get('_all'))!=='off'});}
    if(act==='off'){for(const id of ids){const st=await env.USED.get(id);if(!st)await env.USED.put(id,'off');}return json({ok:true});}
    if(act==='on'){for(const id of ids)if(await env.USED.get(id)==='off')await env.USED.delete(id);return json({ok:true});}
    if(act==='alloff'){await env.USED.put('_all','off');return json({ok:true});}
    if(act==='allon'){await env.USED.delete('_all');return json({ok:true});}
    /* live settings */
    if(act==='cfgget')return json({ok:true,cfg:cleanCfg(await env.USED.get('cfg','json'))});
    if(act==='cfgset'){const c=cleanCfg(d);await env.USED.put('cfg',JSON.stringify(c));return json({ok:true,cfg:c});}
    /* panel codes (letter S) */
    if(act==='mk'){const id=String(ids[0]||'');if(!/^[A-Z2-9]{6}$/.test(id))return json({ok:false,reason:'id'});if(await env.USED.get('c:'+id))return json({ok:false,reason:'exists'});
      const k=['all','rp','veh','badge'].includes(d.k)?d.k:'';if(!k)return json({ok:false,reason:'kind'});
      const v=k==='rp'?int(d.v,1,1e6):k==='veh'?txt(d.v,24).replace(/[^a-z0-9_]/g,''):k==='badge'?txt(d.v,16):'';
      const r={k,v,uses:int(d.uses,1,10000),n:0,exp:int(d.exp,0,1e14),off:false,note:txt(d.note,60),made:Date.now(),by:[]};await putRec(env,id,r);return json({ok:true});}
    if(act==='list')return json({ok:true,codes:await listAll(env,'c:'),all:(await env.USED.get('_all'))!=='off'});
    if(act==='set'){for(const id of ids){const r=await env.USED.get('c:'+id,'json');if(!r)continue;if('off' in d)r.off=!!d.off;await putRec(env,id,r);}return json({ok:true});}
    if(act==='del'){for(const id of ids)await env.USED.delete('c:'+id);return json({ok:true});}
    if(act==='log')return json({ok:true,log:(await env.USED.get('log','json'))||[]});
    if(act==='badges')return json({ok:true,badges:await listAll(env,'b:')});
    if(act==='unbadge'){for(const id of ids)await env.USED.delete('b:'+id);return json({ok:true});}
    return json({ok:false,reason:'action'});}
  return json({ok:false,reason:'path'},404);}};
