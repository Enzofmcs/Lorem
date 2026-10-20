"""Presentation only: figures consume the exact exported analytical tables."""
import html
import json
from pathlib import Path

from .baseline import km_probability
from .io import StudyError
from .notebook_analysis import (budget_outcomes, funnel, grid, select_region,
                                stability, summarize_curve, time_bands)
from .notebook_session import INVENTORY


def table(rows, columns=None):
    from IPython.display import HTML, display
    rows = list(rows)
    if not rows:
        display(HTML("<p>Sem dados disponíveis neste modo.</p>"))
        return
    columns = columns or list(rows[0])
    def render(value):
        if value is None:
            return "indisponível"
        if isinstance(value, float):
            return f"{value:.6g}"
        if isinstance(value, (list, dict)):
            return html.escape(json.dumps(value, ensure_ascii=False))
        return html.escape(str(value))
    display(HTML("<table><thead><tr>" + "".join(f"<th>{html.escape(k)}</th>" for k in columns) +
                 "</tr></thead><tbody>" + "".join("<tr>" + "".join(f"<td>{render(r.get(k))}</td>" for k in columns) +
                 "</tr>" for r in rows) + "</tbody></table>"))


def notice(text):
    from IPython.display import Markdown, display
    display(Markdown(text))


def save_figure(session, fig, name):
    import matplotlib.pyplot as plt
    directory = session.output / "figures"
    directory.mkdir(parents=True, exist_ok=True)
    for extension in ("svg", "png"):
        options = {"metadata": {"Date": None}} if extension == "svg" else {}
        with plt.rc_context({"svg.hashsalt": "lorem-notebooks-v2"}):
            fig.savefig(directory / f"{name}.{extension}", bbox_inches="tight", dpi=150, **options)
    plt.show()
    plt.close(fig)


def overview(session):
    notice("**Modo: " + session.mode + "**. " + ("Verificação técnica SINTÉTICA; sem evidência empírica." if session.mode == "synthetic" else "Resultados reais identificados pela fonte e pelo hash."))
    table([{"campo": k, "valor": v} for k, v in session.overview().items()])
    table([{"etapa": a, "módulo": b, "destino": c} for a,b,c in INVENTORY])
    notice("Run All lê arquivos locais e grava somente a nova análise. Coleta e treinamento são comandos externos explícitos. Consulte README.md para restaurar o backup ou coletar uma nova versão.")


def quality(session, contest=None, rating_lower=None):
    import matplotlib.pyplot as plt
    if session.rows is None:
        report = session.historical["sample"]["funnel"]
        result = {"total": {k: report[k] for k in ("observed_attempts", "excluded", "valid_events", "valid_censored")},
                  "contests": report["contests"], "old_rating": report["by_participant_rating_band_400"],
                  "problem_rating": report["by_problem_rating_band_400"], "reasons": report["reasons"]}
        notice("Agregados reais retidos: totais por contest e faixa disponíveis; participantes/problemas distintos globais e cruzamento contest×faixa exigem microdados. Não somar participantes por contest como pessoas distintas.")
        if contest is not None:
            notice("Seleção por contest abaixo; total global permanece identificado.")
        table([r for r in report["contests"] if contest is None or r["contest_id"] == contest],
              ["contest_id", "observed_attempts", "excluded", "valid_events", "valid_censored", "official_individuals"])
        for field in ("old_rating", "problem_rating"):
            table([{"faixa_400": k, **v} for k,v in result[field].items() if rating_lower is None or k == str(rating_lower)])
    else:
        selected = [r for r in session.rows if (contest is None or r["contest_id"] == contest) and
                    (rating_lower is None or (r["old_rating"] is not None and rating_lower <= r["old_rating"] < rating_lower+400))]
        result = funnel(selected)
        table([{"contest": k, **v} for k,v in result["contest_id"].items()])
        for field in ("old_rating", "problem_rating"):
            table([{"faixa_400": k, **v} for k,v in result[field].items()])
    totals = result["total"]
    assert totals["observed_attempts"] == sum(totals[k] for k in ("excluded", "valid_events", "valid_censored"))
    table([totals])
    table([{"motivo": k, "n": v} for k,v in (result.get("reasons") or totals.get("reasons", {})).items()])
    fig, ax = plt.subplots(figsize=(8,3))
    ax.barh(["Observadas", "Excluídas", "Eventos AC", "Censuras"], [totals[k] for k in ("observed_attempts", "excluded", "valid_events", "valid_censored")], color=["#274060", "#a6adba", "#16827a", "#d5a021"])
    ax.set(xlabel="Tentativas (contagens disjuntas nas três últimas linhas)", title="Cobertura · " + session.source["data_kind"])
    save_figure(session, fig, "quality")
    session.export("quality", {"selection": {"contest": contest, "rating_lower": rating_lower}, **result})


def timeline(session):
    import matplotlib.pyplot as plt
    fig, ax = plt.subplots(figsize=(9,2.8))
    ax.hlines(1, 0, 28, color="#274060", linewidth=3)
    ax.scatter([0,10,20,28], [1]*4, s=65, color=["#274060", "#16827a", "#a6adba", "#16827a"])
    for t,label in [(0,"Início"),(10,"AC A / âncora B"),(20,"1ª submissão B"),(28,"AC B")]:
        ax.annotate(label,(t,1), xytext=(0,18 if t !=20 else -30), textcoords="offset points", ha="center")
    ax.set(xlim=(-3,32), ylim=(.5,1.6), xlabel="Minutos desde o início · exemplo didático SINTÉTICO", yticks=[],
           title="B: sequencial = 28−10 = 18min; desde início = 28min; desde 1ª submissão = 8min")
    save_figure(session, fig, "sequential-timeline")
    notice("A linha temporal é didática, não uma observação real. Âncora não é início de leitura: preparação, paralelismo e abandono não são observados. Sem submissão não há tentativa. Sem AC válido há censura no fim observado, não fracasso permanente.")


def support_table(cells):
    table([{"x_inferior": c["x"], "y_inferior": c["y"], "motivo": c["reason"],
            **c["support"]} for c in cells if c["support"]["attempts"]])


def heatmaps(session, analysis):
    import matplotlib.pyplot as plt
    import numpy as np
    xs, ys = session.plan["old_rating_edges"][:-1], session.plan["problem_rating_edges"][:-1]
    fig, axes = plt.subplots(1,4,figsize=(19,5), sharey=True)
    metrics = [("Mediana (min)", None), *[(f"P(AC ≤ {h}min)",str(h)) for h in session.plan["horizons_minutes"]]]
    if len(metrics) != 4:
        raise StudyError("Presentation expects the frozen 30/60/120 horizons")
    for ax,(label,h) in zip(axes,metrics):
        matrix = np.full((len(ys),len(xs)), np.nan)
        for c in analysis["cells"]:
            value = c["median_minutes"] if h is None else c["probabilities"][h]["value"]
            if value is not None:
                matrix[ys.index(c["y"]),xs.index(c["x"])] = value
        cmap = plt.get_cmap("viridis").copy(); cmap.set_bad("#e6e9ed")
        vmax = max(120, float(np.nanmax(matrix))) if h is None and np.isfinite(matrix).any() else (120 if h is None else 1)
        im = ax.imshow(np.ma.masked_invalid(matrix), origin="lower", aspect="auto", cmap=cmap, vmin=0, vmax=vmax)
        ax.set(title=label, xlabel="oldRating · limite inferior [a,b)", xticks=range(len(xs)), xticklabels=xs,
               yticks=range(len(ys)), yticklabels=ys)
        ax.tick_params(axis="x",rotation=90)
        fig.colorbar(im,ax=ax,shrink=.7)
    axes[0].set_ylabel("Rating do problema · limite inferior [a,b)")
    fig.suptitle(session.source["data_kind"] + " · " + analysis["scope"] + " · cinza = indisponível, sem fallback")
    fig.tight_layout()
    save_figure(session,fig,"rating-heatmaps")


def ratings(session):
    if session.rows is None:
        notice("**Reanálise x/y BLOQUEADA: microdados e KM completo ausentes.** Os agregados por uma dimensão não identificam a distribuição conjunta. Nenhum mapa de calor real pode ser reconstruído.")
        session.export("ratings", {"reason": "full_snapshot_missing", "cells": None})
        return
    result = grid(session.rows,session.plan)
    heatmaps(session,result)
    support_table(result["cells"])
    table([{"delta_inferior": c["delta_lower"], "mediana_min": c["median_minutes"], "motivo": c["reason"], **c["support"]} for c in result["delta"]])
    notice("Amostra descritiva por delta da mesma grade. Não é o modelo histórico ajustado só no treino; os pares x/y preservam os níveis absolutos que o delta perde.")
    session.export("ratings",result)


def plot_curves(session, summaries, h, name):
    import matplotlib.pyplot as plt
    fig,ax=plt.subplots(figsize=(8,4))
    for label, summary in summaries:
        curve=summary.get("curve")
        if curve is None:
            continue
        points=[r for r in curve["table"] if r["seconds"]<=h*60]
        times=[0]+[r["seconds"]/60 for r in points]
        ps=[0]+[1-r["survival"] for r in points]
        end = min(h,curve["max_followup_seconds"]/60)
        if times[-1]<end:
            times.append(end);ps.append(ps[-1])
        ax.step(times,ps,where="post",label=label)
    ax.set(xlim=(0,h),ylim=(0,1),xlabel="Minutos (aproximação sequencial)",ylabel="P(AC até t)",title=session.source["data_kind"]+" · KM sem interpolação")
    ax.axvline(h,color="#777",linestyle="--",label="h: orçamento")
    ax.legend(loc="best")
    save_figure(session,fig,name)


def quantiles(session,x=1200,y=1600,h=120,second_x=1600,second_y=1600):
    demo=session.historical["demo"]
    old=demo["km_delta"]
    p120=next(p["p_ac"] for p in old["probabilities"] if p["budget_minutes"]==120)
    historical={"reference":"historical delta bin [400,800), not exact 1200/1600 pair", "p_ac_120":p120,
                "median_minutes":old["median_minutes"], "p25_minutes":None, "p75_by_120_minutes":None,
                "p25_reason":"full_KM_missing", "p75_reason":"F(120)<0.75; not identified by this horizon"}
    table([historical])
    import matplotlib.pyplot as plt
    fig,ax=plt.subplots(figsize=(7,3))
    ax.scatter([p["budget_minutes"] for p in old["probabilities"]], [p["p_ac"] for p in old["probabilities"]],color="#16827a",label="Consulta histórica por delta (pontos retidos)")
    ax.axhline(.75,color="#777",linestyle="--",label="P75 requer F(t)≥0,75")
    ax.set(xlabel="Minutos",ylabel="P(AC até t)",ylim=(0,1),title="Real · agregados; pontos não reconstroem a curva")
    ax.legend(fontsize=8);save_figure(session,fig,"historical-budget")
    if session.rows is None:
        notice("P75 não é identificado até 120min: F(120)=0,622099. P25 não está retido e não será interpolado. h=120 é orçamento, não quantil. Novos x/y/h exigem o snapshot completo.")
        session.export("quantiles",{"historical":historical,"requested":{"x":x,"y":y,"h":h},"reason":"full_snapshot_missing"})
        return
    pairs=[(x,y),(second_x,second_y)]
    summaries=[];exports=[]
    for xx,yy in pairs:
        rows,reason=select_region(session.rows,session.plan,xx,yy)
        summary=summarize_curve(rows,session.plan)
        if reason:
            summary["reason"]=reason
        bands=time_bands(summary["curve"],h) if summary["curve"] else None
        exports.append({"x":xx,"y":yy,"summary":summary,"bands":bands,"budget_outcomes":budget_outcomes(rows,h)})
        table([{"x":xx,"y":yy,"motivo":summary["reason"],"marcos":bands,**summary["support"]}])
        summaries.append((f"x≈{xx}/y≈{yy}",summary))
    result=grid(session.rows,session.plan)
    d=(y-x)//session.plan["delta_bin_width"]*session.plan["delta_bin_width"]
    reference=next((c for c in result["delta"] if c["delta_lower"]==d),None)
    if reference:
        summaries.append((f"delta [{d},{d+session.plan['delta_bin_width']}) · descritivo",reference))
    plot_curves(session,summaries,h,"selected-curves")
    session.export("quantiles",{"historical":historical,"budget_minutes":h,"pairs":exports,"delta_reference":reference})


def historical_comparison(session):
    import matplotlib.pyplot as plt
    rows=session.historical["comparison"]
    table(rows)
    fig,axes=plt.subplots(1,2,figsize=(11,4))
    for ax,metric,title in zip(axes,["time/mae","distribution/integrated_brier_ipcw"],["MAE nos AC com mediana disponível (min)","Brier integrado IPCW · todas as tentativas"]):
        subset=[r for r in rows if r["metric"]==metric and r["estimate"]]
        for i,r in enumerate(subset):
            estimate=float(r["estimate"])
            lo=float(r["ci95_low"]) if r["ci95_low"] else estimate
            hi=float(r["ci95_high"]) if r["ci95_high"] else estimate
            ax.hlines(i,lo,hi,color="#16827a")
            ax.plot(estimate,i,"o",color="#16827a")
        ax.set(title=title,yticks=range(len(subset)),yticklabels=[f"{r['model']} (n={r['n']})" for r in subset],xlabel="Estimativa histórica / IC95% por 4 grupos")
    fig.tight_layout();save_figure(session,fig,"historical-comparison")
    m=session.historical["metrics"]
    demo=session.historical["demo"]
    table([{"referência":name,"mediana_min":demo[name]["median_minutes"],
            "intervalo_preditivo_10_90_min":demo[name]["predictive_interval_80_minutes"],
            "tipo":demo[name]["interval_kind"]} for name in ("km_delta","aft")])
    table([{"referência":"D-012","tempo_operacional_min":demo["km_delta"]["d012_expected_minutes"],
            "probabilidade":None,"intervalo_preditivo":None}])
    table([{"n_teste":m["n"],"eventos":m["events"],"censuras":m["censored"],"grupos":len(m["test_groups"]),"bootstrap":m["bootstrap"]}])
    notice("Intervalo P25–P75 descreve variação entre tentativas; IC95% descreve incerteza da estimativa sob a reamostragem. Os ICs históricos fixam modelos treinados e censura do treino; quatro grupos e usuários repetidos limitam generalização. MAE condicionado aos resolvidos tem viés e denominador diferente. D-012 não tem probabilidade.")
    sensitivity=m["sensitivity_last_submission"]
    table([{"modelo":name,"estimando":"censura até última submissão (histórico)","métrica":metric,**stats}
           for name,entry in sensitivity["models"].items() for metric,stats in entry["metrics"].items()
           if metric in ("time/mae","distribution/integrated_brier_ipcw")])
    session.export("historical-comparison",{"metrics":rows,"n":m["n"],"groups":m["test_groups"],"bootstrap":m["bootstrap"],"sensitivity_last_submission":sensitivity,
                                            "new_predictive_evaluation":"not_performed"})


def uncertainty(session,x=1200,y=1600,h=120):
    if session.rows is None:
        notice("**Estabilidade nova BLOQUEADA: microdados ausentes.** Não há réplicas, ICs por região ou sensibilidade nova a publicar. A comparação histórica acima continua válida como resultado anterior. Avaliação preditiva nova não realizada; a entrega acadêmica pode discutir resultados disponíveis e limites.")
        session.export("stability",{"reason":"full_snapshot_missing","new_predictive_evaluation":"not_performed"})
        return
    result=stability(session.rows,session.plan,x,y,h)
    table([{"campo":k,"valor":v} for k,v in result.items() if k not in ("bootstrap","by_group","leave_one_group_out","estimand_sensitivity")])
    bootstrap=result.get("bootstrap")
    if bootstrap:
        table([{"estatística":k,**v} for k,v in bootstrap["statistics"].items()])
        table([{"n_grupos":bootstrap["groups"],"seed":bootstrap.get("seed"),"réplicas":len(bootstrap["replicates"]),"motivo":bootstrap.get("reason")}])
        import matplotlib.pyplot as plt
        fig,ax=plt.subplots(figsize=(8,3))
        replicas=bootstrap["replicates"]
        values=[r.get("p75_minutes") for r in replicas]
        ax.scatter([i for i,v in enumerate(values) if v is not None],[v for v in values if v is not None],s=10,color="#16827a",label="P75 identificado")
        ax.scatter([i for i,v in enumerate(values) if v is None],[0 for v in values if v is None],marker="x",color="#a23b37",label="P75 ausente (marcador em zero, não estimativa)")
        ax.set(xlabel="Réplica por grupos",ylabel="P75 (min)",title=session.source["data_kind"]+" · ausências preservadas");ax.legend(fontsize=8)
        save_figure(session,fig,"bootstrap-missing")
        for key in ("by_group","leave_one_group_out"):
            table([{k:v for k,v in r.items() if k not in ("curve","probabilities")} for r in result.get(key,[])])
        table([{"estimando":k,"mediana_min":v["median_minutes"],"probabilidades":v["probabilities"],"sem_tempo":v["missing"],"suporte":v["support"]} for k,v in result.get("estimand_sensitivity",{}).items()])
    sensitivity=[]
    for width in session.plan["sensitivity_widths"]:
        plan={**session.plan,"old_rating_edges":list(range(0,4001,width)),"problem_rating_edges":list(range(0,4001,width))}
        rows,reason=select_region(session.rows,plan,x,y)
        summary=summarize_curve(rows,plan)
        sensitivity.append({"largura":width,"motivo":reason or summary["reason"],"mediana_min":summary["median_minutes"],"probabilidades":summary["probabilities"],"suporte":summary["support"]})
    table(sensitivity)
    session.export("stability",{"x":x,"y":y,"budget_minutes":h,**result,"grid_sensitivity":sensitivity,"new_predictive_evaluation":"not_performed"})
