"""Reproducible aggregate tables, scientific plots and a Portuguese study report."""

import csv
import hashlib
from importlib.metadata import version
import os
from pathlib import Path
import platform

from .io import StudyError, digest, read_json, write_json

NAMES = {"aft": "AFT congelado", "km_global": "KM global", "km_delta": "KM por delta", "d012": "D-012"}


def csv_table(path, rows):
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerows(rows)


def number(value, digits=3):
    return "indisponivel" if value is None else f"{value:.{digits}f}"


def estimate(metric):
    text = number(metric["estimate"])
    if metric["ci95"] is not None:
        text += " [" + ", ".join(number(v) for v in metric["ci95"]) + "]"
    return text


def conclusion(result):
    diff = result["paired_differences_common_support"]["aft_minus_km_delta"]
    metric = diff["distribution/integrated_brier_ipcw"]
    if metric["ci95"] is not None and metric["ci95"][0] > 0:
        return "KM por delta teve Brier integrado menor que o AFT nesta amostra, inclusive no IC pareado."
    if metric["ci95"] is not None and metric["ci95"][1] < 0:
        return "AFT teve Brier integrado menor que KM por delta nesta amostra, inclusive no IC pareado."
    return "A comparacao com KM por delta tem evidencia insuficiente para indicar menor Brier integrado."


def plots(output, result):
    os.environ.setdefault("MPLCONFIGDIR", str((output / ".matplotlib").resolve()))
    os.environ.setdefault("XDG_CACHE_HOME", str((output / ".cache").resolve()))
    Path(os.environ["XDG_CACHE_HOME"]).mkdir(parents=True, exist_ok=True)
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    matplotlib.rcParams.update({"font.family": "DejaVu Sans", "svg.hashsalt": digest(result), "svg.fonttype": "none"})
    def save(fig, stem):
        fig.tight_layout()
        fig.savefig(output / (stem + ".svg"), metadata={"Date": None, "Creator": "Lorem study"})
        svg = output / (stem + ".svg")
        svg.write_text("\n".join(line.rstrip() for line in svg.read_text(encoding="utf-8").splitlines()) + "\n", encoding="utf-8")
        fig.savefig(output / (stem + ".png"), dpi=160, metadata={"Software": "Lorem study"})
        plt.close(fig)
    fig, ax = plt.subplots(figsize=(8, 4))
    for i, (name, model) in enumerate(result["models"].items()):
        metric = model["metrics"]["time/mae"]
        if metric["estimate"] is None:
            continue
        value, ci = metric["estimate"], metric["ci95"]
        ax.errorbar(value, i, xerr=[[max(0, value-ci[0])], [max(0, ci[1]-value)]] if ci else None,
                    fmt="o", capsize=4, color="#1667a6")
        ax.text(value + .4, i + .15, f'n={metric["n"]:,}', fontsize=8)
    ax.set_yticks(range(len(result["models"])), [NAMES[k] for k in result["models"]])
    ax.set_xlabel("MAE (minutos); IC95% por grupo de contests")
    ax.set_title("Erro nos resolvidos com mediana disponivel; denominadores diferem")
    ax.grid(axis="x", alpha=.25)
    save(fig, "time-mae")
    for kind, suffix, label in (("known", "known", "Casos conhecidos (selecao)"),
                                ("ipcw", "ipcw", "IPCW com G estimado no treino")):
        fig, axes = plt.subplots(1, 3, figsize=(12, 4), sharex=True)
        for ax, budget in zip(axes, (30, 60, 120)):
            ax.plot([0, 1], [0, 1], color="gray", linestyle="--", linewidth=1)
            for name, color in (("aft", "#1667a6"), ("km_delta", "#b55c00")):
                metrics = result["models"][name]["metrics"]
                for band in range(5):
                    base = f"probability/{budget}/bin/{band}/"
                    x = metrics[base + ("prediction_known" if kind == "known" else "prediction_all")]
                    y = metrics[base + "observed_" + kind]
                    if x["estimate"] is None or y["estimate"] is None:
                        continue
                    ci, value = y["ci95"], y["estimate"]
                    ax.errorbar(x["estimate"], value,
                                yerr=[[max(0, value-ci[0])], [max(0, ci[1]-value)]] if ci else None,
                                fmt="o", color=color, capsize=3,
                                label=NAMES[name] if band == 0 else None)
            ax.set_title(f"AC ate {budget} min")
            ax.set_xlabel("Probabilidade media prevista")
            ax.grid(alpha=.2)
        axes[0].set_ylabel("Fracao observada; IC95% por grupo")
        # Explicit legend independent of empty first bins.
        from matplotlib.lines import Line2D
        axes[-1].legend(handles=[Line2D([0], [0], color=c, marker="o", label=NAMES[n], linestyle="")
                                for n, c in (("aft", "#1667a6"), ("km_delta", "#b55c00"))], fontsize=8)
        fig.suptitle(label + "; n por bin em calibration.csv")
        save(fig, "calibration-" + suffix)


def report(root):
    root = Path(root)
    metrics_path = root / "evaluation" / "metrics.json"
    if not metrics_path.exists():
        # The core fixture/collection can still inspect stages without model dependencies.
        prepared = root / "prepared" / "manifest.json"
        return {"schema_version": 1, "status": "pipeline_status_only; evaluate required for scientific report",
                "data_kind": read_json(prepared)["data_kind"] if prepared.exists() else "not_collected"}
    result = read_json(metrics_path)
    manifest = read_json(root / "evaluation" / "manifest.json")
    plan = read_json(root / "evaluation" / "plan.json")
    if manifest["metrics_sha256"] != digest(result) or result["plan_sha256"] != digest(plan):
        raise StudyError("Evaluation report checksum mismatch")
    prepared = read_json(root / "prepared" / "manifest.json")
    baseline = read_json(root / "baseline" / "model.json")
    aft = read_json(root / "aft" / "model.json")
    if (digest(aft) != plan["aft_sha256"] or digest(baseline) != plan["baseline_sha256"] or
        digest(prepared) != plan["prepared_sha256"] or digest(read_json(root / "splits.json")) != plan["splits_sha256"]):
        raise StudyError("Report inputs differ from frozen evaluation")
    output = root / "report"
    output.mkdir(parents=True, exist_ok=True)
    rows = [["model", "metric", "estimate", "n", "ci95_low", "ci95_high", "bootstrap_valid"]]
    for name, model in result["models"].items():
        for key, metric in model["metrics"].items():
            rows.append([name, key, metric["estimate"], metric["n"], *(metric["ci95"] or [None, None]), metric["bootstrap_valid"]])
    csv_table(output / "metrics.csv", rows)
    rows = [["comparison", "metric", "difference", "n_common", "ci95_low", "ci95_high", "bootstrap_valid"]]
    for name, differences in result["paired_differences_common_support"].items():
        for key, m in differences.items():
            rows.append([name, key, m["estimate"], m["n_common"], *(m["ci95"] or [None, None]), m["bootstrap_valid"]])
    csv_table(output / "paired-differences.csv", rows)
    rows = [["model", "minutes", "bin", "kind", "prediction", "observed", "n", "ci95_low", "ci95_high"]]
    for name, model in result["models"].items():
        for budget in (30, 60, 120):
            for band in range(5):
                for kind in ("known", "ipcw"):
                    base = f"probability/{budget}/bin/{band}/"
                    x = model["metrics"][base + ("prediction_known" if kind == "known" else "prediction_all")]
                    y = model["metrics"][base + "observed_" + kind]
                    rows.append([name, budget, band, kind, x["estimate"], y["estimate"], y["n"], *(y["ci95"] or [None, None])])
    csv_table(output / "calibration.csv", rows)
    rows = [["feature", "band_start", "n", "events", "censored", "model", "metric", "estimate", "n_metric", "ci95_low", "ci95_high"]]
    for field, bands in result["rating_slices"].items():
        for band, data in bands.items():
            for name, metrics in data["models"].items():
                for key, m in metrics.items():
                    rows.append([field, band, data["n"], data["events"], data["censored"], name, key, m["estimate"], m["n"], *(m["ci95"] or [None, None])])
    csv_table(output / "rating-slices.csv", rows)
    csv_table(output / "quality.csv", [["quality", "test_attempts"]] + sorted(result["quality_funnel_test"].items()))
    plots(output, result)
    from .aft import predict_aft
    from .baseline import predict
    demo = {"data_kind": result["data_kind"], "old_rating": 1200, "problem_rating": 1600,
            "budgets_minutes": [30, 60, 120], "aft": predict_aft(aft, 1200, 1600, [30, 60, 120]),
            "km_delta": predict(baseline, 1200, 1600, [30, 60, 120]),
            "note": "Consulta ilustrativa do modelo real, nao observacao nem precisao individual"}
    write_json(output / "demo.json", demo)
    lines = ["# Estudo quantitativo do Lorem", "", f"Dados: **{result['data_kind']}**. " + conclusion(result), "",
             "## Populacao e metodo", "",
             f"{len(prepared['complete_contest_ids'])} contests elegiveis; {prepared.get('observed_attempts')} tentativas observadas; "
             f"{prepared.get('valid_events')} eventos, {prepared.get('valid_censored')} censuras, {prepared.get('excluded')} exclusoes. "
             f"Cobertura: {number(prepared.get('coverage'))}.", "",
             "Contests especiais e qualquer contest com problemas em duas versoes foram excluidos integralmente. "
             "Amostra estrutural congelada antes dos desfechos; rating de problemas observado na coleta. "
             "Populacao: participantes oficiais individuais com oldRating positivo e prefixo sequencial valido.", "",
             f"Teste reservado: {result['test_contest_ids']}; {len(result['test_groups'])} grupos inteiros, "
             f"{result['n']} tentativas, {result['events']} eventos e {result['censored']} censuras. "
             f"Treino/validacao/teste: {plan['splits_sha256']}.", "",
             f"AFT escolhido apenas na validacao: {plan['selection']}; hash {plan['aft_sha256']}. "
             "Entradas: oldRating e delta, sem resultados futuros. Likelihood inclui censuras. "
             "Nenhum modelo foi retunado no teste. G marginal e transformacoes usam apenas treino.", "",
             "## Erros de tempo nos resolvidos", "",
             "Denominadores diferem por suporte da mediana. Ha vies de selecao: esses erros nao representam os nao resolvidos. "
             "Valores em minutos; colchetes sao IC95% por grupo.", "",
             "| Modelo | n | MAE | Mediana absoluta | RMSE | Vies previsto-observado |", "|---|---:|---|---|---|---|"]
    for name, model in result["models"].items():
        m = model["metrics"]
        lines.append(f"| {NAMES[name]} | {m['time/mae']['n']} | " + " | ".join(estimate(m[k]) for k in
                    ("time/mae", "time/median_absolute_error", "time/rmse", "time/bias")) + " |")
    lines += ["", "![Erros de tempo](time-mae.svg)", "", "## Probabilidades e censura", "",
              "Brier menor indica menor erro. Casos censurados antes de b sao desconhecidos; nunca negativos. "
              "IPCW usa todas as tentativas no denominador, com G do treino e minimo 0,05. "
              "Integracao: grade 0-120 min em passos de cinco, limitada ao prefixo suportado. "
              "D-012 nao possui probabilidade.", "",
              "| Modelo | Brier integrado | Brier IPCW 30 | 60 | 120 |", "|---|---|---|---|---|"]
    for name, model in result["models"].items():
        m = model["metrics"]
        lines.append(f"| {NAMES[name]} | " + " | ".join(estimate(m[k]) for k in
                    ("distribution/integrated_brier_ipcw", "probability/30/brier_ipcw", "probability/60/brier_ipcw", "probability/120/brier_ipcw")) + " |")
    lines += ["", "Brier/log-loss conhecidos, bins com n/IC e fatias de ambos os ratings/delta estao nos CSVs. "
              "Log-loss limita apenas o argumento do log em 1e-15. Calibracao IPCW pode exceder um sem truncamento.", "",
              "![Calibracao conhecida](calibration-known.svg)", "", "![Calibracao IPCW](calibration-ipcw.svg)", "",
              "| Horizonte | Conhecidos | Desconhecidos | G(b-) | Maior peso | IPCW disponivel |", "|---|---:|---:|---:|---:|---|"]
    for s in result["models"]["aft"]["support"]["horizons"]:
        lines.append(f"| {s['minutes']} | {s['known']} | {s['unknown']} | {number(s['g_left'])} | {number(s['max_ipcw_weight'])} | {s['ipcw_available']} |")
    lines += ["", "## Diferencas pareadas e intervalos preditivos", "",
              "Diferenca AFT menos referencia no mesmo suporte; negativo favorece AFT para erros/Brier. "
              "200 replicacoes de grupos inteiros com reposicao, seed do split; IC percentil 95%. "
              "Com apenas poucos grupos, ICs podem ser instaveis e nao incluem incerteza do ajuste.", "",
              "| Referencia | Delta MAE (n comum) | Delta Brier integrado |", "|---|---|---|"]
    for name, diff in result["paired_differences_common_support"].items():
        lines.append(f"| {name} | {estimate(diff['time/mae'])} (n={diff['time/mae']['n_common']}) | {estimate(diff['distribution/integrated_brier_ipcw'])} |")
    lines += ["", "Intervalos preditivos 10/90 descrevem variacao de tempo; IC95% descreve incerteza das metricas. "
              "Cobertura apenas em resolvidos com ambas as pontas disponiveis tem vies e nao mede cobertura populacional.", "",
              "| Modelo | n | Cobertura | Largura media (min) |", "|---|---:|---|---|"]
    for name, model in result["models"].items():
        m = model["metrics"]
        lines.append(f"| {NAMES[name]} | {m['interval80/coverage']['n']} | {estimate(m['interval80/coverage'])} | {estimate(m['interval80/width_minutes'])} |")
    alt = result["sensitivity_last_submission"]
    lines += ["", "## Sensibilidade e limites", "",
              f"Observacao alternativa ate ultima submissao: treino n={alt['train_n']}, teste n={alt['test_n']}, "
              f"ausentes={alt['test_missing']}. Previsoes ficam fixas e G e reestimado somente no treino alternativo. "
              "Isso muda a hipotese de observacao, sem medir dedicacao efetiva e sem escolher a regra pelo teste.", "",
              "| Modelo | Brier integrado alternativo |", "|---|---|"]
    for name, model in alt["models"].items():
        lines.append(f"| {NAMES[name]} | {estimate(model['metrics']['distribution/integrated_brier_ipcw'])} |")
    lines += [""] + ["- " + note for note in result["limitations"]]
    lines += ["", "## Consulta demonstravel e decisao", "",
              "Dois ratings 1200/1600 e orcamentos 30/60/120 -> demo.json, com probabilidades, mediana, "
              "quantis e suporte. Ponta indisponivel permanece null. Consulta ilustrativa nao e acerto individual.", "",
              conclusion(result) + " O candidato congelado e preservado; este teste nao e usado para retuning. "
              "Manter D-012 no aplicativo e ratings distintos. Integracao permanece bloqueada ate a validacao em Ipsums. "
              "Nao ha evidencia de melhoria pedagogica ou de transporte para treino individual.", "",
              "Novo experimento exigira nova amostra reservada e protocolo previo; diagnosticar calibracao/abandono "
              "antes de aumentar complexidade. A proxima base deve registrar CF anterior, Lorem inicial, inicio, "
              "AC/encerramento, dicas e interrupcoes, com particoes por participante (Historias 30-31).", "",
              "## Reproducao", "",
              "Seguir study/README.md e REPRODUCE.md. Snapshot/cache bruto local e necessario para bytes identicos. "
              "Nenhum handle ou identificador de participante e publicado. Manifesto lista hashes dos agregados, "
              "versoes e seeds; tempos/custo da coleta estao na evidencia da amostra.", ""]
    (output / "REPORT.md").write_text("\n".join(lines), encoding="utf-8")
    (output / "REPRODUCE.md").write_text(
        "# Reproducao do snapshot ao relatorio\n\n"
        "Na pasta study, criar um venv limpo e instalar requirements.lock e requirements-report.lock. "
        "Instalar o pacote com pip install --no-build-isolation --no-deps .\n\n"
        "Executar com o Python desse venv: `python scripts/reproduce.py SOURCE DESTINATION`. "
        "SOURCE deve ser o diretorio do snapshot original, restaurado do backup local se necessario; "
        "DESTINATION nao pode existir. O script copia os ponteiros e usa brutos imutaveis, executa "
        "collect --offline, prepare, split, train-baseline, train, evaluate e report; compara todos "
        "os hashes de tabelas/modelos/metricas/figuras com SOURCE. reproduction.json registra "
        "tempos, versoes, zero requisicoes de rede e igualdade por arquivo.\n\n"
        "manifest.json neste diretorio identifica todos os agregados por SHA-256. Seed e plano "
        "estao em evaluation/plan.json. Aquisicao real previa: 308 respostas, 1.654.835.689 bytes, "
        "633,889 s; novas chamadas da API nao garantem os mesmos dados. O backup bruto contem "
        "handles publicos e permanece local/fora do Git. Nenhuma fixture substitui dados reais.\n",
        encoding="utf-8")
    files = sorted(p for p in output.iterdir() if p.is_file() and p.name != "manifest.json")
    info = {"schema_version": 1, "report_version": "report-v1", "data_kind": result["data_kind"],
            "evaluation_sha256": digest(result), "seed": plan["seed"],
            "dependencies": {k: version(k) for k in ("numpy", "scipy", "matplotlib")},
            "python": platform.python_version(), "conclusion": conclusion(result),
            "files": {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in files}}
    write_json(output / "manifest.json", info)
    return info
