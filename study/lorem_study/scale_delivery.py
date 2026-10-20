"""Retained aggregate delivery: coverage, exact tables/figures and offline HTML."""

import argparse
from collections import Counter
from datetime import datetime
from pathlib import Path
import shutil
import sqlite3

from .io import StudyError, digest, read_json, write_json
from .scale_prepare import file_sha
from .scale_view import export_figures, interactive_html


def coverage_report(snapshot_root,prepared_root,analysis):
    snapshot_root,prepared_root=Path(snapshot_root),Path(prepared_root)
    snapshot=read_json(snapshot_root/"snapshot.json")
    prepared=read_json(prepared_root/"manifest.json")
    if snapshot["data_kind"]!=prepared["data_kind"] or analysis["data_kind"]!=prepared["data_kind"]:
        raise StudyError("Coverage cannot mix real and synthetic sources")
    if digest(snapshot)!=prepared["snapshot_sha256"] or analysis["snapshot_sha256"]!=prepared["snapshot_sha256"]:
        raise StudyError("Delivery snapshot/preparation/analysis mismatch")
    if file_sha(prepared_root/"attempts.sqlite")!=prepared["database_sha256"]:
        raise StudyError("Delivery database checksum mismatch")
    if snapshot["coverage"]["complete"]<len(snapshot["selection"]["contest_ids"]):
        status="partial_acquisition; failures retained, no replacement"
    else:
        status="available_structural_universe_acquired; target shortfall retained"
    universe=read_json(snapshot_root/"universe.json")
    screening=read_json(snapshot_root/"screening.json")
    budget=read_json(snapshot_root/"network-budget.json")
    cache=read_json(snapshot_root/"cache-import.json") if (snapshot_root/"cache-import.json").exists() else None
    response_status=Counter(read_json(p)["http_status"] for p in (snapshot_root/"responses").glob("*.json"))
    decisions=Counter(d["reason"] or "eligible_structure" for d in screening["decisions"])
    with sqlite3.connect((prepared_root/'attempts.sqlite').resolve().as_uri()+"?mode=ro",uri=True) as db:
        def totals(where="1",params=()):
            row=db.execute(f"""SELECT COUNT(*),COALESCE(SUM(quality='valid' AND event=1),0),
              COALESCE(SUM(quality='valid' AND event=0),0),COALESCE(SUM(quality='excluded'),0),
              COUNT(DISTINCT participant),COUNT(DISTINCT CASE WHEN quality='valid' THEN participant END),
              COUNT(DISTINCT CAST(problem_contest_id AS TEXT)||':'||problem_index),
              COUNT(DISTINCT CASE WHEN quality='valid' THEN CAST(problem_contest_id AS TEXT)||':'||problem_index END),
              COUNT(DISTINCT contest_id),COUNT(DISTINCT group_id) FROM attempts WHERE {where}""",params).fetchone()
            result=dict(zip(("observed_attempts","valid_events","valid_censored","excluded","participants_observed_unique", "participants_valid_unique",
                             "problems_observed_unique","problems_valid_unique","contests_with_observations","groups_with_observations"),row))
            if result["observed_attempts"]!=sum(result[k] for k in ("valid_events","valid_censored","excluded")):
                raise StudyError("Delivery funnel does not reconcile")
            return result
        global_totals=totals()
        by_group=[{"group_id":min(group),"contest_ids":group,**totals("group_id=?",[min(group)])} for group in prepared["groups"]]
        by_contest=[]
        for part in prepared["partitions"]:
            cid=part["contest_id"]
            by_contest.append({**part["prepared"]["contests"][0],**totals("contest_id=?",[cid]),
                               "source_partition_sha256":part["source_sha256"],"peak_rss_bytes":part["peak_rss_bytes"],
                               "compressed_partition_bytes":sum(s["bytes"] for s in part["streams"].values()),
                               "preparation_seconds":part["seconds"]})
        for field in ("observed_attempts","valid_events","valid_censored","excluded"):
            if global_totals[field]!=prepared[field] or sum(g[field] for g in by_group)!=prepared[field] or sum(c[field] for c in by_contest)!=prepared[field]:
                raise StudyError("Global/contest/group coverage mismatch")
        reasons=dict(db.execute("SELECT reason,COUNT(*) FROM attempts WHERE quality='excluded' GROUP BY reason"))
    if sum(reasons.values())!=prepared["excluded"]:
        raise StudyError("Excluded reasons do not reconcile")
    if sum(r["observed_attempts"] for r in analysis["rows"])+analysis["outside_grid_observed"]!=prepared["observed_attempts"]:
        raise StudyError("Rating grid coverage does not reconcile")
    first=datetime.fromisoformat(budget["first_start_utc"].replace("Z","+00:00"))
    last=datetime.fromisoformat(budget["last_response_utc"].replace("Z","+00:00"))
    acquisition={"status":status,"metadata_candidates":universe["metadata_eligible"],"regular_metadata":universe["regular"],
                 "upper_bound_before_standings":universe["upper_bound_before_standings"],
                 "screening_decisions":dict(decisions),"selected":len(snapshot["selection"]["contest_ids"]),
                 "complete":snapshot["coverage"]["complete"],"eligible_after_joins":len(prepared["eligible_contest_ids"]),
                 "target":snapshot["config"]["target_contests"],"structural_shortfall":snapshot["selection"]["shortfall"],
                 "complete_shortfall":snapshot["config"]["target_contests"]-snapshot["coverage"]["complete"],
                 "network_attempts":budget["attempts"],"new_http_bytes":budget["http_bytes"],"response_status_counts":dict(response_status),
                 "first_start_utc":budget["first_start_utc"],"last_response_utc":budget["last_response_utc"],
                 "elapsed_seconds":(last-first).total_seconds(),"minimum_start_gap_seconds":budget["minimum_start_gap_seconds"],
                 "status_pages_in_snapshot":sum(len(e.get("status_refs",[])) for e in snapshot["contests"].values()),
                 "imported_cache_objects":len(cache["objects"]) if cache else 0,
                 "imported_cache_dates_are_original":True,"cache_source_snapshot_sha256":cache["source_snapshot_sha256"] if cache else None,
                 "incomplete_contests":[{"contest_id":cid,"reason":snapshot["contests"].get(str(cid),{}).get("error","not acquired before stop")} for cid in snapshot["selection"]["contest_ids"] if not snapshot["contests"].get(str(cid),{}).get("complete")]}
    return {"schema_version":1,"version":"scale-t50-coverage-v1","data_kind":prepared["data_kind"],"snapshot_sha256":prepared["snapshot_sha256"],
            "plan_sha256":analysis["plan_sha256"],"selection_sha256":prepared["selection_sha256"],
            "acquisition":acquisition,"totals":global_totals,"by_contest":by_contest,"by_group":by_group,
            "excluded_reasons":reasons,"groups":prepared["groups"],"eligible_contest_ids":prepared["eligible_contest_ids"],
            "participants_repeated_across_groups":prepared["participants_repeated_across_groups"],
            "distincts_note":"Distinct people/problems across cells or groups must not be summed as global distincts",
            "participant_identity":"Normalized handle pseudonym, not verified natural person; renamed handles may differ",
            "bootstrap_universe":"all complete acquired shared-problem groups, including zero eligible rows in some groups/cells; whole groups drawn globally",
            "maximum_worker_rss_bytes":prepared["maximum_worker_rss_bytes"],"database_bytes":prepared["database_bytes"],
            "database_sha256":prepared["database_sha256"],
            "overlap_with_examined_samples":analysis["overlap_with_examined_models"],
            "new_independent_predictive_evaluation":"not performed"}


def build_delivery(snapshot_root,prepared_root,models_root,output,*,backup=None,restoration=None,replay=None):
    output=Path(output).resolve()
    for root in (snapshot_root,prepared_root,models_root):
        root=Path(root).resolve()
        if root==output or root in output.parents or output in root.parents:
            raise StudyError("Aggregate delivery must be separate from full source data")
    report=read_json(output/"analysis.json")
    if (output/"delivery-manifest.json").exists() and read_json(output/"delivery-manifest.json")["snapshot_sha256"]!=report["snapshot_sha256"]:
        raise StudyError("Do not overwrite another retained snapshot; use a new versioned delivery")
    if report["data_kind"]!="real":raise StudyError("Real delivery cannot silently publish fixtures")
    coverage=coverage_report(snapshot_root,prepared_root,report)
    for item in report["plan"]["model_inventory"]:
        source=Path(models_root)/Path(item["path"]).name
        if file_sha(source)!=item["sha256"]:raise StudyError("Model changed before delivery")
        target=output/"models"/source.name;target.parent.mkdir(exist_ok=True)
        shutil.copyfile(source,target)
    prior=Path(models_root)/"prior-samples.json"
    if prior.exists():shutil.copyfile(prior,output/"models/prior-samples.json")
    write_json(output/"coverage.json",coverage)
    write_json(output/"plan.json",report["plan"])
    fixed=read_json(output/"fixed-queries.json")
    export_figures(report,output/"figures")
    (output/"dashboard.html").write_text(interactive_html(report,fixed),encoding="utf-8")
    known=sum(r["observed"]["t50_minutes"] is not None for r in report["rows"])
    intervals=sum(r["observed"].get("uncertainty",{}).get("ci95_minutes") is not None and r["observed"]["t50_minutes"] is not None for r in report["rows"])
    acq,t=coverage["acquisition"],coverage["totals"]
    text=f'''# Estudo ampliado: curvas de tempo até 50% de AC

**Dados reais, comparação descritiva; avaliação preditiva independente não realizada.**
Histórias 50–56. A história 49 e o artigo do TCC não são alterados.

## Amostra efetiva

- Alvo: {acq['target']}; universo de metadados: {acq['metadata_candidates']} candidatos.
- Triagem estrutural completa: **{acq['selected']} selecionados**, déficit estrutural {acq['structural_shortfall']}.
- Aquisição: **{acq['complete']} completos**; **{acq['eligible_after_joins']} elegíveis após joins**.
- {t['observed_attempts']:,} observações = {t['valid_events']:,} eventos + {t['valid_censored']:,} censuras + {t['excluded']:,} exclusões.
- Participantes (handles pseudonimizados) distintos válidos: {t['participants_valid_unique']:,}; problemas distintos válidos: {t['problems_valid_unique']:,}.
- {len(coverage['groups'])} grupos conhecidos; {coverage['participants_repeated_across_groups']:,} participantes se repetem entre grupos.
- {known}/{len(report['rows'])} células com t50 principal identificado; {intervals} dessas com IC conservador disponível.
- Fora da grade: {report['outside_grid_observed']:,} observações, {report['outside_grid_valid']:,} válidas.

O universo não comporta 400 sob os filtros existentes. Não foram aceitos especiais,
versões fácil/difícil ou origens anteriores ao corte para preencher a meta.
As perdas, motivos, grupos, denominadores e sobreposição com amostras examinadas
estão em [coverage.json](coverage.json). Não some pessoas distintas entre células.

## Abrir e comparar

[Painel HTML interativo offline](dashboard.html), [CSV gerador](t50.csv),
[JSON completo/ICs/réplicas](analysis.json), [cenários fixos](fixed-queries.json),
[KM observado](figures/t50-observado.png) e [modelos sobrepostos](figures/t50-modelos.png).
Há versões SVG das duas figuras. [Notebook fonte](../../notebooks/06_escala_t50.ipynb)
e [guia Linux/Windows, modo completo e aquisição](../../SCALE_T50_README.md).
Quando incluídos, `06_escala_t50.ipynb` e `.html` nesta pasta são as cópias executadas.

X é faixa de rating do exercício [a,b); Y é primeira passagem da CDF de AC >=0,5,
em minutos. Cores representam oldRating prévio, estilos representam métodos.
Mediana considera censura; não é percentil só dos AC. O orçamento h=120min é
referência, sem cortar quantis conhecidos. Ausências são null com motivo, sem
interpolação de lacunas. O painel oferece faixas/modelos, suporte, cenários fixos,
destaque e exportação. Seus controles funcionam sem kernel/rede.

## Modelos e incerteza

AFT, KM global e KM por delta de 20/10 são congelados, com hashes e domínio em
[plan.json](plan.json). A população/pesos são os mesmos no suporte comum; o KM
observado é reestimado nesse subconjunto. Primeiro mistura-se a CDF nas covariáveis
reais e depois se inverte. Cenário fixo é separado. KM global pode coincidir entre
faixas; KM delta perde níveis absolutos. Sem extrapolação/fallback oculto.
Consulta parcial de 18/10 e trabalhos externos sem artefato verificável não
produzem curvas arbitrárias. D-012 é duração operacional, sem CDF/t50.

Bootstrap usa {report['plan']['bootstrap']['replicates']} réplicas de grupos globais inteiros com multiplicidade,
inclusive grupos/células com zero linhas elegíveis. Grupos efetivos são reportados
por célula. Qualquer réplica sem quantil torna o IC principal ausente, sem descartar
réplicas. O JSON retém réplicas, resultados por grupo, leave-one-group-out,
sensibilidades de grade/pisos e estimandos. ICs são de amostragem do observado;
os parâmetros dos modelos ficam fixos, sem estimar incerteza de ajuste.

Trata-se de tempo sequencial de parede, com seleção de prefixos e possível censura
informativa; não mede dedicação exclusiva/capacidade individual. Pessoas repetidas
e aliases não conhecidos limitam independência. Ratings iguais não garantem 50%
de AC por prazo. Nenhuma superioridade preditiva, efeito pedagógico ou transporte
para Ipsums é estabelecido por este aumento de N. Android/D-012/time-curve-v1
continuam preservados.

## Proveniência e recursos

- Snapshot: `{report['snapshot_sha256']}`.
- Plano: `{report['plan_sha256']}`; seleção: `{report['selection_sha256']}`.
- Aquisição nova: {acq['network_attempts']} tentativas de rede, intervalo mínimo medido {acq['minimum_start_gap_seconds']:.6f}s.
- Janela: {acq['first_start_utc']} até {acq['last_response_utc']}; {acq['elapsed_seconds']:.1f}s de parede.
- Bytes HTTP novos: {acq['new_http_bytes']:,}; páginas status no snapshot: {acq['status_pages_in_snapshot']}.
- Cache importado: {acq['imported_cache_objects']} objetos, com datas/hashes originais, não chamados de coleta atual.
- Maior RSS de worker de preparo: {coverage['maximum_worker_rss_bytes']} bytes; SQLite: {coverage['database_bytes']} bytes.

O preparo reutiliza o preparador existente por contest, em processos isolados,
com streams comprimidos, joins 1:1 verificados e SQLite transacional. A equivalência
com o caminho anterior foi conferida em fixture e nos 20 contests reais anteriores.
As medições acima são desta execução, sem projetá-las como medição de 400.

## Backup separado e verificações

O Git/pacote leve contém agregados e modelos, **não** snapshot/audit com handles.
'''
    if backup:
        text+=f"\nBackup completo local: `{backup['path']}`; {backup['bytes']:,} bytes; SHA-256 `{backup['sha256']}`.\nEsse caminho identifica onde o arquivo existe no ambiente, sem ser um link público.\nAcesso ao ambiente ou transferência específica é necessário para obter o arquivo.\n"
    else:
        text+="\nBackup completo: verificação ainda não registrada; não anunciado como entregue.\n"
    if restoration:text+=f"\nRestauração verificada: {restoration['files_verified']} arquivos, {restoration['raw']['objects']} objetos HTTP descomprimidos; SQLite `{restoration['sqlite_integrity']}`.\n"
    if replay:text+=f"\nReplay offline: {replay['network_requests_this_run']} requisições; snapshot `{replay['snapshot_sha256']}`.\n"
    text+="\nTestes automatizados e kernels limpos detalhados na evidência. **TESTE MANUAL OPCIONAL - NÃO EXECUTADO.**\n"
    (output/"REPORT.md").write_text(text,encoding="utf-8")
    files={p.relative_to(output).as_posix():file_sha(p) for p in sorted(output.rglob("*")) if p.is_file() and p.name!="delivery-manifest.json"}
    manifest={"schema_version":1,"version":"scale-t50-delivery-v1","snapshot_sha256":report["snapshot_sha256"],
              "plan_sha256":report["plan_sha256"],"acquisition":acq,"files_sha256":files,
              "full_backup":backup,"restoration":restoration,"offline_replay":replay,
              "note":"Aggregates/models only; no raw handles or microdata; no new independent predictive evaluation"}
    write_json(output/"delivery-manifest.json",manifest)
    return manifest


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ("snapshot","prepared","models","output"):parser.add_argument(name,type=Path)
    for name in ("backup","restoration","replay"):parser.add_argument("--"+name,type=Path)
    args=parser.parse_args(argv)
    manifest=build_delivery(args.snapshot,args.prepared,args.models,args.output,
                            **{k:read_json(getattr(args,k)) if getattr(args,k) else None for k in ("backup","restoration","replay")})
    print({"files":len(manifest["files_sha256"]),"acquisition":manifest["acquisition"]})


if __name__=="__main__":main()
