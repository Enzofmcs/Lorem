"""Portable plots and a self-contained interactive HTML view of exported t50 cells."""

import html
import json
from pathlib import Path

from .io import StudyError, read_json

LABELS={"observed":"KM observado — população total", "common_observed":"KM observado — suporte comum",
        "aft":"AFT congelado", "km_delta":"KM por delta congelado", "km_global":"KM global congelado"}
STYLES={"observed":"-","common_observed":"-","aft":"--","km_delta":"-.","km_global":":"}
COLORS=["#64748b","#9333ea","#2563eb","#0891b2","#059669","#d97706","#dc2626","#be185d","#7c3aed","#334155"]


def value_of(cell,method):
    return cell[method] if method in ("observed","common_observed") else cell["predictions"][method]


def plot_curves(report,*,bands=None,methods=("observed",),budget=None,problem_range=(800,3600),show_ci=True):
    import matplotlib.pyplot as plt
    import numpy as np
    bands=report["plan"]["initial_user_bands"] if bands is None else bands
    budget=report["plan"]["budget_minutes"] if budget is None else budget
    if any(m not in LABELS for m in methods):raise StudyError("Unknown plotting method")
    fig,ax=plt.subplots(figsize=(12,7))
    edges=report["plan"]["old_rating_edges"]
    for old in bands:
        selected=sorted([r for r in report["rows"] if r["old_lower"]==old and problem_range[0]<=r["problem_lower"]<problem_range[1]],key=lambda r:r["problem_lower"])
        if not selected:continue
        color=COLORS[edges.index(old)%len(COLORS)]
        for method in methods:
            values=[value_of(r,method) for r in selected]
            x=[r["problem_x"] for r in selected]
            y=[np.nan if v["t50_minutes"] is None else v["t50_minutes"] for v in values]
            label=f"[{old},{selected[0]['old_upper']}) · {LABELS[method]}"
            ax.plot(x,y,marker="o",markersize=3,color=color,linestyle=STYLES[method],label=label)
            if show_ci:
                low,high=[],[]
                for v in values:
                    ci=v.get("uncertainty",{}).get("ci95_minutes")
                    low.append(ci[0] if ci and v["t50_minutes"] is not None else np.nan)
                    high.append(ci[1] if ci and v["t50_minutes"] is not None else np.nan)
                ax.fill_between(x,low,high,color=color,alpha=.10)
    ax.axhline(budget,color="#475569",linestyle="--",linewidth=1,label=f"h={budget:g}min · referência operacional")
    centers=sorted({r["problem_x"] for r in report["rows"] if problem_range[0]<=r["problem_lower"]<problem_range[1]})
    cell_by_x={r["problem_x"]:r for r in report["rows"]}
    ax.set_xticks(centers,[f"[{cell_by_x[x]['problem_lower']},{cell_by_x[x]['problem_upper']})" for x in centers],rotation=45,ha="right",fontsize=8)
    ax.set(xlabel="Faixa de rating do exercício [a,b)",ylabel="t50 até AC (minutos)",ylim=(0,None),
           title="Tempo até F_AC(t) ≥ 50% por faixa de oldRating\n"+f"{report['data_kind']} · comparação descritiva · lacunas permanecem ausentes")
    ax.grid(alpha=.2)
    ax.legend(fontsize=7,ncol=2,loc="upper left",bbox_to_anchor=(0,-.29))
    fig.text(.01,.005,f"Fonte: {report['version']} · snapshot {report['snapshot_sha256'][:16]} · plano {report['plan_sha256'][:16]}\nIC: bootstrap por grupos, quando identificável. Tempo de parede sequencial; ratings iguais não garantem 50% por prazo.",fontsize=8)
    fig.subplots_adjust(bottom=.34)
    return fig


def export_figures(report,output):
    import matplotlib.pyplot as plt
    output=Path(output); output.mkdir(parents=True,exist_ok=True)
    for name,bands,methods in (("t50-observado",None,("observed",)),
                              ("t50-modelos",[1200,1600],("common_observed","aft","km_delta","km_global"))):
        fig=plot_curves(report,bands=bands,methods=methods)
        for extension in ("svg","png"):
            with plt.rc_context({"svg.hashsalt":"lorem-scale-t50-v1"}):
                fig.savefig(output/f"{name}.{extension}",dpi=150,bbox_inches="tight",metadata={"Date":None} if extension=="svg" else None)
        plt.close(fig)


def interactive_html(report,fixed_queries=None):
    """No network, CDN or kernel required. Gaps never become connected segments."""
    # Bootstrap draws are retained in analysis.json, but not duplicated in every HTML.
    compact=json.loads(json.dumps(report))
    for row in compact["rows"]:
        for key in ("observed","common_observed"):
            uncertainty=row[key].get("uncertainty",{})
            for field in ("replicate_t50_minutes","by_group_t50_minutes","leave_one_group_out_t50_minutes"):
                uncertainty.pop(field,None)
        row.pop("estimand_sensitivity",None)
        row.pop("support_sensitivity",None)
    compact.pop("grid_sensitivity",None)
    payload=json.dumps({"report":compact,"fixed":fixed_queries or {"rows":[]},"labels":LABELS,"colors":COLORS},ensure_ascii=False,allow_nan=False).replace("<","\\u003c")
    return _HTML.replace("__PAYLOAD__",payload)


def display_interactive(report,fixed_queries=None):
    from IPython.display import HTML,display
    document=interactive_html(report,fixed_queries)
    display(HTML('<iframe title="Curvas t50 interativas offline" style="width:100%;height:1150px;border:1px solid #cbd5e1" srcdoc="'+html.escape(document,quote=True)+'"></iframe>'))


_HTML=r'''<!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Lorem · curvas t50</title>
<style>body{font:15px system-ui,sans-serif;margin:24px;color:#172554;background:#f8fafc}h1{font-size:25px}fieldset{border:1px solid #cbd5e1;border-radius:8px;margin:10px 0;padding:12px}label{display:inline-block;margin:5px 14px 5px 0}input[type=number]{width:75px}button,select,input{font:inherit}button{padding:7px 12px}svg{background:white;width:100%;border:1px solid #cbd5e1;border-radius:8px}table{border-collapse:collapse;width:100%;font-size:12px}td,th{border:1px solid #cbd5e1;padding:5px;text-align:left}.muted{color:#475569}.scroll{max-height:280px;overflow:auto}#point{min-height:5em;white-space:pre-wrap;background:white;padding:12px}#legend span{margin:6px;display:inline-block}a{color:#1d4ed8}</style>
<h1>Tempo até 50% de probabilidade de AC</h1><p id="source"></p>
<p>Tempo de parede sequencial com censura, sem medir dedicação exclusiva. Igualdade de rating Elo não garante 50% de AC por prazo. Estudo descritivo; modelos congelados. Lacunas indicam ausência de suporte/quantil.</p>
<fieldset><legend id="user-label">Faixas de oldRating [a,b)</legend><div id="users"></div><label>Destaque <select id="focus"><option value="all">Todas as faixas</option></select></label></fieldset>
<fieldset><legend>Linhas no mesmo gráfico</legend><div id="methods"></div><label>Modo <select id="mode"><option value="mixture">Faixas · mistura nas tentativas observadas</option><option value="fixed">Cenários fixos · sem mistura observada</option></select></label><label>IC do observado <input id="ci" type="checkbox" checked></label></fieldset>
<fieldset><legend>Janela gráfica e pisos de exibição</legend><label>Problema ≥ <input id="xmin" type="number" value="800" step="100"></label><label>Problema &lt; <input id="xmax" type="number" value="3600" step="100"></label><label>h (min) <input id="budget" type="number" value="120" min="1"></label><label>Tentativas ≥ <input id="nmin" type="number" value="50" min="1"></label><label>Eventos ≥ <input id="emin" type="number" value="20" min="1"></label><label>Grupos ≥ <input id="gmin" type="number" value="5" min="1"></label><p class="muted">h é referência operacional, sem cortar t50 conhecido. Alterar pisos é exploração de apresentação; não recalcula ICs ou bootstrap. Modo fixo usa ratings exatos retidos (o número do início da faixa), não a média da faixa.</p></fieldset>
<div id="legend"></div><svg id="chart" viewBox="0 0 1100 700" role="img" aria-label="X rating do problema, Y t50 em minutos"></svg><pre id="point">Passe o mouse ou clique em um ponto para ver suporte, cobertura e IC. IC ausente não implica t50 ausente.</pre>
<p id="status"></p><button id="csv">Baixar tabela CSV da seleção</button> <button id="json">Baixar JSON da seleção</button> <button id="svg">Baixar SVG</button> <button id="png">Baixar PNG</button>
<details><summary>Pontos, lacunas e motivos</summary><div class="scroll"><table id="table"></table></div></details>
<p class="muted">No modo por faixas, comparações com modelos usam somente suporte comum e o KM observado é reestimado nesse mesmo subconjunto. A linha da população total tem denominador distinto e legenda própria. Mistura da CDF é invertida; não é média de medianas. KM global pode coincidir entre faixas; KM por delta perde níveis absolutos. Linhas dos modelos não são ICs de ajuste. D-012 é duração operacional e não aparece como t50. Janelas e faixas selecionadas não provam superioridade de modelo.</p>
<script>const DATA=__PAYLOAD__;const R=DATA.report, NS='http://www.w3.org/2000/svg', colors=DATA.colors;
const $=id=>document.getElementById(id), esc=x=>String(x??'indisponível').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const userEdges=R.plan.old_rating_edges, methods=Object.keys(DATA.labels), dashes={observed:'',common_observed:'',aft:'8 5',km_delta:'9 3 2 3',km_global:'2 4'};
$('source').textContent=`Fonte ${R.version} · ${R.data_kind} · ${R.groups.flat().length} contests preparados / ${R.eligible_contest_ids?.length??'ver cobertura'} elegíveis / ${R.groups.length} grupos · ${R.total_valid.toLocaleString('pt-BR')} tentativas válidas. Snapshot ${R.snapshot_sha256} · plano ${R.plan_sha256}.`;
userEdges.slice(0,-1).forEach((old,i)=>{$('users').insertAdjacentHTML('beforeend',`<label style="color:${colors[i%colors.length]}"><input type="checkbox" name="user" value="${old}" ${R.plan.initial_user_bands.includes(old)?'checked':''}><span data-band-old="${old}">[${old},${userEdges[i+1]})</span></label>`);$('focus').insertAdjacentHTML('beforeend',`<option value="${old}">[${old},${userEdges[i+1]})</option>`)});
methods.forEach(m=>$('methods').insertAdjacentHTML('beforeend',`<label><input type="checkbox" name="method" value="${m}" ${['common_observed','aft'].includes(m)?'checked':''}>${esc(DATA.labels[m])}</label>`));
for(const [id,key] of [['nmin','attempts'],['emin','events'],['gmin','groups']])$(id).value=R.plan.support[key];$('budget').value=R.plan.budget_minutes;
let selected=[];
function node(tag,attrs={},text){const n=document.createElementNS(NS,tag);for(const [k,v]of Object.entries(attrs))n.setAttribute(k,v);if(text!==undefined)n.textContent=text;return n}
function download(name,type,body){const u=URL.createObjectURL(new Blob([body],{type}));const a=document.createElement('a');a.href=u;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(u),1000)}
function draw(){const fixed=$('mode').value==='fixed';document.querySelectorAll('[name=method]').forEach(n=>n.disabled=fixed&&['observed','common_observed'].includes(n.value));const users=[...document.querySelectorAll('[name=user]:checked')].map(x=>+x.value), wanted=[...document.querySelectorAll('[name=method]:checked')].filter(n=>!n.disabled).map(x=>x.value), xmin=+$('xmin').value,xmax=+$('xmax').value,h=+$('budget').value;
 $('user-label').textContent=fixed?'Ratings fixos x0 · cenários de modelo':'Faixas de oldRating [a,b)';for(const id of ['nmin','emin','gmin','ci'])$(id).disabled=fixed;document.querySelectorAll('[data-band-old]').forEach(n=>{const o=+n.dataset.bandOld;n.textContent=fixed?`x0=${o}`:`[${o},${userEdges[userEdges.indexOf(o)+1]})`});[...$('focus').options].forEach(n=>{if(n.value!=='all'){const o=+n.value;n.textContent=fixed?`x0=${o}`:`[${o},${userEdges[userEdges.indexOf(o)+1]})`}});
 selected=[];let lines=[];if(![xmin,xmax,h,+$('nmin').value,+$('emin').value,+$('gmin').value].every(Number.isFinite)||xmax<=xmin||h<=0||['nmin','emin','gmin'].some(id=>+$(id).value<1)){$('chart').replaceChildren();$('status').textContent='Use limites válidos: problema máximo maior que mínimo, h e pisos positivos.';return}
 for(const old of users)for(const method of wanted){let points=[];
 if(fixed){for(const problem of R.plan.problem_rating_edges.slice(0,-1).filter(p=>p>=xmin&&p<xmax)){const r=DATA.fixed.rows.find(r=>r.old_rating===old&&r.method===method&&r.problem_rating===problem)||{old_rating:old,problem_rating:problem,t50_minutes:null,reason:old===0?'fixed oldRating must be positive':'observed band is not a fixed-covariate scenario'};const p={x:r.problem_rating,y:r.t50_minutes,old,method,reason:r.reason,support:null,ci:null,mode:'fixed_covariates',raw:r};points.push(p);selected.push(p)}}
 else{for(const r of R.rows.filter(r=>r.old_lower===old&&r.problem_lower>=xmin&&r.problem_lower<xmax)){const v=['observed','common_observed'].includes(method)?r[method]:r.predictions[method],s=method==='observed'?r.support:r.common_support;let reason=v.reason,y=v.unfiltered_t50_minutes??v.t50_minutes;
 const missing=[['attempts','nmin'],['events','emin'],['groups','gmin']].filter(([k,id])=>s[k]<+$(id).value).map(([k])=>k);
 if(missing.length){y=null;reason='insufficient_support:'+missing.join(',')}else if(y!==null)reason=null;
 const p={x:r.problem_x,y,old,method,reason,support:s,ci:v.uncertainty?.ci95_minutes??null,mode:r.mode,raw:{user_band:[r.old_lower,r.old_upper],problem_band:[r.problem_lower,r.problem_upper],support:s,common_coverage:r.common_coverage,estimate:v}};points.push(p);selected.push(p)}}
 points.sort((a,b)=>a.x-b.x);lines.push({old,method,points})}
 const svg=$('chart');svg.replaceChildren();svg.append(node('desc',{},JSON.stringify({snapshot_sha256:R.snapshot_sha256,plan_sha256:R.plan_sha256,units:'minutes',mode:fixed?'fixed_covariates':'observed_covariate_mixture',methods:wanted,user_lower_edges:users,problem_range:[xmin,xmax],model_hashes:R.model_hashes})));const W=1100,H=620,L=80,T=40,PW=980,PH=490;const values=selected.filter(p=>p.y!==null).flatMap(p=>[p.y,...($('ci').checked&&p.ci?p.ci:[])]);const ymax=Math.max(10,h,...values)*1.12;
 const x=x=>L+(x-xmin)/(xmax-xmin)*PW,y=v=>T+PH-(v/ymax)*PH;
 for(let i=0;i<=6;i++){const v=ymax*i/6;svg.append(node('line',{x1:L,x2:L+PW,y1:y(v),y2:y(v),stroke:'#e2e8f0'}));svg.append(node('text',{x:L-10,y:y(v)+4,'text-anchor':'end','font-size':12},v.toFixed(1)))}
 for(let v=Math.ceil(xmin/200)*200;v<xmax;v+=200){svg.append(node('text',{x:x(v),y:T+PH+25,'text-anchor':'middle','font-size':12},v));}
 svg.append(node('line',{x1:L,x2:L+PW,y1:y(h),y2:y(h),stroke:'#64748b','stroke-dasharray':'6 4'}));svg.append(node('text',{x:L+6,y:y(h)-6,'font-size':12},`h=${h} min (referência)`));
 for(const line of lines){const color=colors[userEdges.indexOf(line.old)%colors.length],opacity=$('focus').value==='all'||+$('focus').value===line.old?1:.15;let segment=[];
 const flush=()=>{if(segment.length>1)svg.append(node('polyline',{points:segment.join(' '),fill:'none',stroke:color,'stroke-width':2,'stroke-dasharray':dashes[line.method],'pointer-events':'none',opacity}));segment=[]};
 for(const p of line.points){if(p.y===null){flush();continue}segment.push(`${x(p.x)},${y(p.y)}`);
 if($('ci').checked&&p.ci){svg.append(node('line',{x1:x(p.x),x2:x(p.x),y1:y(p.ci[0]),y2:y(p.ci[1]),stroke:color,'stroke-width':3,opacity:.3*opacity}))}
 const dot=node('circle',{cx:x(p.x),cy:y(p.y),r:4,fill:color,opacity,tabindex:0});const detail=()=>{$('point').textContent=`${DATA.labels[p.method]} · oldRating ${p.old} · problema ${p.x} · t50 ${p.y.toFixed(3)} min\n`+JSON.stringify(p.raw,null,2)};dot.addEventListener('mouseenter',detail);dot.addEventListener('focus',detail);dot.addEventListener('click',detail);dot.append(node('title',{},`oldRating ${p.old}; problema ${p.x}; t50 ${p.y.toFixed(3)} min`));svg.append(dot)}flush()}
 svg.append(node('text',{x:L+PW/2,y:H-30,'text-anchor':'middle','font-size':15},fixed?'Rating exato do exercício · cenário fixo':'Faixa de rating do exercício [a,b) · posição no centro do bin'));
 svg.append(node('text',{x:20,y:T+PH/2,transform:`rotate(-90 20 ${T+PH/2})`,'text-anchor':'middle','font-size':15},'t50 até AC (minutos)'));
 svg.append(node('text',{x:L,y:625,'font-size':11},'oldRating '+users.map(o=>fixed?`x0=${o}`:`[${o},${userEdges[userEdges.indexOf(o)+1]})`).join(' · ')));
 svg.append(node('text',{x:L,y:645,'font-size':10},wanted.map(m=>DATA.labels[m]+' '+(dashes[m]||'contínua')).join(' · ')));
 svg.append(node('text',{x:L,y:665,'font-size':10},`Descritivo · ${R.data_kind} · IC de grupos, sem incerteza de ajuste · tempo sequencial, sem garantia Elo`));
 svg.append(node('text',{x:L,y:685,'font-size':10},`snapshot ${R.snapshot_sha256.slice(0,20)} · plano ${R.plan_sha256.slice(0,20)} · fontes completas em desc/SVG e CSV/JSON`));
 $('legend').innerHTML=users.map(o=>`<span style="color:${colors[userEdges.indexOf(o)%colors.length]}">● ${fixed?`x0=${o}`:`[${o},${userEdges[userEdges.indexOf(o)+1]})`}</span>`).join('')+'<br>'+wanted.map(m=>`<span>${esc(DATA.labels[m])} (${esc(dashes[m]||'contínua')})</span>`).join('');
 const missing=selected.filter(p=>p.y===null).length;$('status').textContent=`${selected.length-missing} pontos identificados; ${missing} ausentes. ${fixed?'Cenários fixos de ratings exatos, no domínio de cada modelo; não representam a mistura observada.':'Células em suporte comum usam os mesmos pesos em todos os modelos.'} Alterações de exibição não recalculam bootstrap.`;
 $('table').innerHTML='<tr><th>oldRating início</th><th>Problema X</th><th>Método</th><th>t50 min</th><th>Motivo</th><th>N/eventos/censuras/grupos</th></tr>'+selected.map(p=>`<tr><td>${p.old}</td><td>${p.x}</td><td>${esc(DATA.labels[p.method])}</td><td>${p.y===null?'indisponível':p.y.toFixed(3)}</td><td>${esc(p.reason??'')}</td><td>${p.support?[p.support.attempts,p.support.events,p.support.censored,p.support.groups].join('/'):'cenário fixo'}</td></tr>`).join('')}
document.querySelectorAll('input,select').forEach(n=>n.addEventListener('change',draw));
function exportContext(p){const fixed=p.mode==='fixed_covariates';return {old_rating_lower_or_fixed:p.old,old_rating_upper:fixed?null:p.raw.user_band[1],problem_x:p.x,problem_lower:fixed?null:p.raw.problem_band[0],problem_upper:fixed?null:p.raw.problem_band[1],mode:p.mode,method:p.method,model_version:p.method==='aft'?'aft-v1':'kaplan-meier-v1',artifact_sha256:p.raw.artifact_sha256??p.raw.estimate?.artifact_sha256??null,units:'minutes',population:fixed?'fixed covariates':p.method==='observed'?'all eligible attempts in cell':'common model support',weights:fixed?'single fixed scenario':'one per eligible attempt',t50_minutes:p.y,reason:p.reason,attempts:p.support?.attempts,events:p.support?.events,censored:p.support?.censored,participants:p.support?.participants,problems:p.support?.problems,contests:p.support?.contests,groups:p.support?.groups,ci95_lower_minutes:p.ci?.[0],ci95_upper_minutes:p.ci?.[1],snapshot_sha256:R.snapshot_sha256,plan_sha256:R.plan_sha256,selection_sha256:R.selection_sha256}}
$('json').onclick=()=>download('t50-selecao.json','application/json',JSON.stringify({snapshot_sha256:R.snapshot_sha256,plan_sha256:R.plan_sha256,model_hashes:R.model_hashes,units:'minutes',points:selected.map(p=>({...p,...exportContext(p)}))},null,2));
$('csv').onclick=()=>{const values=selected.map(exportContext),keys=Object.keys(values[0]??exportContext({old:null,x:null,mode:'fixed_covariates',method:'aft',raw:{},support:null})),rows=[keys,...values.map(v=>keys.map(k=>v[k]))];download('t50-selecao.csv','text/csv;charset=utf-8',rows.map(r=>r.map(x=>'"'+String(x??'').replaceAll('"','""')+'"').join(',')).join('\n'))};
$('svg').onclick=()=>download('t50-grafico.svg','image/svg+xml',new XMLSerializer().serializeToString($('chart')));
$('png').onclick=()=>{const svg=new XMLSerializer().serializeToString($('chart'));const u=URL.createObjectURL(new Blob([svg],{type:'image/svg+xml'}));const image=new Image();image.onload=()=>{const canvas=document.createElement('canvas');canvas.width=1650;canvas.height=1050;const c=canvas.getContext('2d');c.fillStyle='white';c.fillRect(0,0,1650,1050);c.drawImage(image,0,0,1650,1050);canvas.toBlob(blob=>{const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='t50-grafico.png';a.click();setTimeout(()=>URL.revokeObjectURL(a.href),1000)});URL.revokeObjectURL(u)};image.src=u};draw();</script></html>'''
