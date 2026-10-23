/* Ironfield code checker: a Cloudflare Worker that makes redeem codes single-use for everyone.
   Setup: a secret named SECRET (the same key as in the owner's private code maker) and a KV namespace bound as USED.
   A code is 15 characters: 6-character id + 1 perk letter + 8-character signature (HMAC-SHA256 of the id and letter,
   keyed with SECRET), so only someone with the key can make codes, and this worker can check them without a list.
   KV holds "used:<time>" or "off" per id, and "_all"="off" switches every code off. */
const A='ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const PERKS={A:['all_vehicles']};
const enc=new TextEncoder();
async function hmac(secret,msg){const k=await crypto.subtle.importKey('raw',enc.encode(secret),{name:'HMAC',hash:'SHA-256'},false,['sign']);return new Uint8Array(await crypto.subtle.sign('HMAC',k,enc.encode(msg)));}
function b32(bytes,n){let bits=0,val=0,out='';for(const b of bytes){val=((val<<8)|b)&0xffff;bits+=8;while(bits>=5){out+=A[(val>>>(bits-5))&31];bits-=5;}if(out.length>=n)break;}return out.slice(0,n);}
const hex=b=>[...b].map(x=>x.toString(16).padStart(2,'0')).join('');
function same(a,b){a=String(a);b=String(b);if(a.length!==b.length)return false;let r=0;for(let i=0;i<a.length;i++)r|=a.charCodeAt(i)^b.charCodeAt(i);return r===0;}
const json=(o,s=200)=>new Response(JSON.stringify(o),{status:s,headers:{'content-type':'application/json','access-control-allow-origin':'*','access-control-allow-methods':'POST, OPTIONS','access-control-allow-headers':'content-type'}});
export default{async fetch(req,env){
  if(req.method==='OPTIONS')return json({});
  if(req.method!=='POST')return json({ok:false,reason:'method'},405);
  const path=new URL(req.url).pathname;let b={};try{b=await req.json();}catch(e){}
  const S=env.SECRET;if(!S||!env.USED)return json({ok:false,reason:'setup'},500);
  if(path==='/redeem'){
    const code=String(b.code||'').toUpperCase().replace(/[^A-Z0-9]/g,''),nonce=String(b.nonce||'').slice(0,40);
    if(code.length!==15)return json({ok:false,reason:'invalid'});
    const id=code.slice(0,6),fl=code[6];
    if(!PERKS[fl]||!same(b32(await hmac(S,'code:'+id+fl),8),code.slice(7)))return json({ok:false,reason:'invalid'});
    if(await env.USED.get('_all')==='off')return json({ok:false,reason:'off'});
    const st=await env.USED.get(id);if(st)return json({ok:false,reason:st==='off'?'off':'used'});
    await env.USED.put(id,'used:'+Date.now());
    const t=Date.now(),ticket=hex(await hmac(S,'ticket:'+id+':'+fl+':'+t+':'+nonce));
    return json({ok:true,id,flags:fl,perks:PERKS[fl],t,ticket});}
  if(path==='/verify'){/* a host checks that a guest really redeemed a code (tickets last 12 hours) */
    const id=String(b.id||'').slice(0,6),fl=String(b.flags||''),t=+b.t||0,nonce=String(b.nonce||'').slice(0,40);
    if(!PERKS[fl]||Date.now()-t>12*3600e3||t>Date.now()+60e3)return json({ok:false});
    return json({ok:same(hex(await hmac(S,'ticket:'+id+':'+fl+':'+t+':'+nonce)),b.ticket),perks:PERKS[fl]});}
  if(path==='/admin'){/* the code maker: status, switch codes off/on, master switch; signed with the key, valid for 5 minutes */
    const ts=+b.ts||0,act=String(b.action||''),ids=Array.isArray(b.ids)?b.ids.map(x=>String(x).slice(0,6)).slice(0,500):[];
    if(Math.abs(Date.now()-ts)>5*60e3||!same(hex(await hmac(S,'admin:'+ts+':'+act+':'+ids.join(','))),b.sig))return json({ok:false,reason:'auth'},403);
    if(act==='status'){const status={};for(const id of ids)status[id]=(await env.USED.get(id))||'unused';return json({ok:true,status,all:(await env.USED.get('_all'))!=='off'});}
    if(act==='off'){for(const id of ids){const st=await env.USED.get(id);if(!st)await env.USED.put(id,'off');}return json({ok:true});}
    if(act==='on'){for(const id of ids)if(await env.USED.get(id)==='off')await env.USED.delete(id);return json({ok:true});}
    if(act==='alloff'){await env.USED.put('_all','off');return json({ok:true});}
    if(act==='allon'){await env.USED.delete('_all');return json({ok:true});}
    return json({ok:false,reason:'action'});}
  return json({ok:false,reason:'path'},404);}};
