"""Preregistered local transport experiment; no network or Android integration."""

from pathlib import Path

from .io import StudyError, digest, read_json, read_rows, write_json
from .ipsum import PROTOCOL, participant_partitions


TRANSPORT = {
    "schema_version": 1, "transport_version": "ipsum-transport-v1", "seed": 20261019,
    "bootstrap_replicates": 1000, "bootstrap_unit": "whole participants; paired; fixed mapping/model/censoring",
    "minimum_participants": 100, "minimum_attempts": 2000, "minimum_events": 1000,
    "partition_minimum_participants": {"train": 60, "validation": 20, "test": 20},
    "partition_minimum_attempts": {"train": 1200, "validation": 400, "test": 400},
    "partition_minimum_events": {"train": 600, "validation": 200, "test": 200},
    "budgets_minutes": [30, 60, 120], "integration_range_minutes": [0, 120],
    "mae_upper_ci_max_minutes": 20, "mae_ci_width_max_minutes": 10,
    "integrated_brier_upper_ci_max": 0.15, "integrated_brier_ci_width_max": 0.05,
    "calibration_ece_upper_ci_max": 0.10, "calibration_ece_ci_width_max": 0.10,
    "minimum_in_domain_fraction": 0.90, "minimum_median_coverage_events": 0.95,
    "minimum_unassisted_test_participants": 20, "minimum_unassisted_test_attempts": 200,
    "minimum_unassisted_test_events": 100,
    "maps": ["identity", "linear_train_only"], "models": ["km_delta", "aft"],
    "mapping_fit": "CF_before = intercept + slope * Lorem_before; equal total weight per training participant; positive slope",
    "selection": "validation full 0..120 IPCW Brier, ties: identity then KM delta; no test tuning",
    "calibration": "five fixed bins; ECE of IPCW event fractions; participant bootstrap recomputes bins' absolute gaps",
    "gate_population": "all test attempts AND unassisted test attempts; no supported pauses claim",
    "reference_28": {"km_delta_mae_minutes": 15.135245901639344,
                     "km_delta_integrated_brier": 0.12274572742520427,
                     "aft_integrated_brier": 0.12669257972028425},
    "reference_artifacts": {"baseline": "7549c4e98d2e2ea50ef4c7a3d61db490f7914d181d55968858040692cd666f6a",
                            "aft": "2bb92b581b909e9a6b95002d99a4e2602e2b0c2309f7df81b0a4184b55b7899f",
                            "evaluation_plan": "4c8e07ab186cd25a3a4761da0d063d64c3e53728a721c508cdda5db8604d7922"},
    "limitations": "operational sample floors, not power guarantee; CI precision also required; conditional censoring and shared problems remain assumptions",
    "decision": "failed/insufficient => no integration; passing real evidence => eligible for explicit Story 31 domain decision, never automatic app authorization",
}


def freeze(path, value):
    if path.exists() and read_json(path) != value:
        raise StudyError(f"Frozen transport input changed ({path.name}); use a new preregistered experiment, never retune test")
    write_json(path, value)


def freeze_transport(root, source):
    """Write protocol/model bindings before opening any local attempt outcomes."""
    output = Path(root) / "ipsum" / "transport"
    baseline = read_json(Path(source) / "baseline" / "model.json")
    aft = read_json(Path(source) / "aft" / "model.json")
    if (baseline.get("schema_version") != 1 or baseline.get("model_version") != "kaplan-meier-v1" or
        aft.get("schema_version") != 1 or aft.get("model_version") != "aft-v1" or
        aft.get("selection", {}).get("status") != "selected" or
        baseline.get("data_kind") != aft.get("data_kind") or
        baseline.get("training_contest_ids") != aft.get("training_contest_ids")):
        raise StudyError("Incompatible or unselected frozen contest models")
    evidence = read_json(Path(source) / "evaluation" / "plan.json")
    if evidence.get("baseline_sha256") != digest(baseline) or evidence.get("aft_sha256") != digest(aft):
        raise StudyError("Contest models differ from frozen held-out evaluation")
    if baseline.get("data_kind") not in ("real", "synthetic"):
        raise StudyError("Unknown contest model data kind")
    if baseline["data_kind"] == "real" and TRANSPORT["reference_artifacts"] != {
        "baseline": digest(baseline), "aft": digest(aft), "evaluation_plan": digest(evidence)
    }:
        raise StudyError("Real transport v1 requires exact preregistered Story 28 artifacts")
    plan = {**TRANSPORT, "preparation_protocol_sha256": digest(PROTOCOL),
            "source_data_kind": baseline["data_kind"], "baseline_sha256": digest(baseline),
            "aft_sha256": digest(aft), "contest_evaluation_plan_sha256": digest(evidence)}
    freeze(output / "plan.json", plan)
    return output, plan, baseline, aft


def load_cohort(root, output):
    """Bind prepared-table hashes first; validate exact participant isolation."""
    root = Path(root) / "ipsum"
    manifest = read_json(root / "manifest.json")
    if manifest.get("protocol") != PROTOCOL or not manifest.get("attempts_sha256"):
        raise StudyError("Re-run prepare-ipsums with the current protocol and table checksum")
    if manifest["status"] == "insufficient_data":
        return manifest, None
    partitions = read_json(root / "partitions.json")
    if (partitions.get("dataset_sha256") != manifest["dataset_sha256"] or
        partitions.get("protocol_sha256") != digest(PROTOCOL)):
        raise StudyError("Ipsum partitions differ from prepared cohort")
    freeze(output / "cohort.json", {"dataset_sha256": manifest["dataset_sha256"],
                                     "attempts_sha256": manifest["attempts_sha256"],
                                     "data_kind": manifest["data_kind"], "partitions_sha256": digest(partitions)})
    rows = list(read_rows(root / "attempts.jsonl"))
    if digest(rows) != manifest["attempts_sha256"]:
        raise StudyError("Prepared Ipsum table checksum mismatch")
    valid = [r for r in rows if r["quality"] == "valid"]
    people = partitions["participants"]
    if set(people) != {"train", "validation", "test"}:
        raise StudyError("Missing participant partition")
    flat = [p for part in people.values() for p in part]
    if len(flat) != len(set(flat)) or set(flat) != {r["participant_key"] for r in valid}:
        raise StudyError("Leaking/missing Ipsum participant partition")
    if partitions != participant_partitions(flat, manifest["dataset_sha256"]):
        raise StudyError("Ipsum participant assignment differs from preregistered hash order")
    if len({(r["participant_key"], r["sample_id"]) for r in rows}) != len(rows):
        raise StudyError("Duplicate Ipsum attempt")
    if (manifest["observed_attempts"] != len(rows) or manifest["valid_attempts"] != len(valid) or
        manifest["valid_participants"] != len(flat)):
        raise StudyError("Ipsum counts differ from prepared table")
    return manifest, {name: [r for r in valid if r["participant_key"] in set(ids)] for name, ids in people.items()}


def counts(rows):
    return {"participants": len({r["participant_key"] for r in rows}), "attempts": len(rows),
            "events": sum(r["event"] for r in rows), "censored": sum(1-r["event"] for r in rows)}


def evaluate_ipsums(root, source):
    output, plan, baseline, aft = freeze_transport(root, source)
    manifest, parts = load_cohort(root, output)
    sizes = {name: counts(rows) for name, rows in parts.items()} if parts else {}
    enough = bool(parts) and all(
        sizes[name][key] >= plan["partition_minimum_" + key][name]
        for name in sizes for key in ("participants", "attempts", "events"))
    status = {"schema_version": 1, "transport_version": plan["transport_version"],
              "data_kind": manifest["data_kind"], "plan_sha256": digest(plan),
              "observed_attempts": manifest["observed_attempts"], "valid_attempts": manifest["valid_attempts"],
              "valid_participants": manifest["valid_participants"], "partitions": sizes,
              "quality_exclusions": manifest["reasons_multiple_per_row"],
              "coverage": manifest["valid_attempts"] / manifest["observed_attempts"] if manifest["observed_attempts"] else None,
              "integration_gate": "CLOSED", "app_decision": "retain distinct ratings and D-012",
              "network_requests": 0, "test_evaluated": False}
    if not enough:
        if (output / "selection.json").exists() or (output / "metrics.json").exists():
            raise StudyError("Evaluated cohort cannot become insufficient; use a new experiment")
        status.update(status="insufficient_data", decision="do_not_integrate; no real transport evidence")
    else:
        if manifest["data_kind"] not in ("ipsum_local", "synthetic"):
            raise StudyError("Unknown transport data kind")
        if manifest["data_kind"] == "ipsum_local" and plan["source_data_kind"] != "real":
            raise StudyError("Local observations require real frozen contest models")
        try:
            from .transport_stats import evaluate_cohort
        except ImportError as error:
            raise StudyError("Transport metrics require requirements-model.lock dependencies") from error
        metrics = evaluate_cohort(parts, baseline, aft, plan, output)
        status.update(status="evaluated" if manifest["data_kind"] == "ipsum_local" else "synthetic_software_check",
                      test_evaluated=metrics["test_evaluated"], numeric_gate_passed=metrics["numeric_gate_passed"],
                      decision=("eligible_for_domain_decision; not_app_authorization" if
                                metrics["numeric_gate_passed"] and manifest["data_kind"] == "ipsum_local" else
                                "do_not_integrate; retain_distinct_ratings"))
        write_json(output / "metrics.json", metrics)
        status["metrics_sha256"] = digest(metrics)
    write_json(output / "manifest.json", status)
    write_transport_report(output, status)
    return status


def write_transport_report(output, status):
    lines = ["# Validacao de transporte para Ipsums", "", f"Estado: `{status['status']}`.",
             f"Dados: `{status['data_kind']}`; {status['valid_attempts']} tentativas validas de {status['valid_participants']} participantes.",
             f"Gate de integracao: **{status['integration_gate']}**. Decisao: `{status['decision']}`.", "",
             "Ratings continuam distintos e D-012 permanece vigente. Fixture verifica software, nunca transporte real.",
             "Limiares, hashes e pressupostos: [plan.json](plan.json). Estado e cobertura: [manifest.json](manifest.json).", ""]
    if status.get("metrics_sha256"):
        metrics = read_json(output / "metrics.json")
        lines += ["[Metricas completas, IC95%, suporte, estratos e pares de ratings](metrics.json).", "",
                  f"Escolha congelada na validacao: `{metrics['selection']['chosen']}`.",
                  "MAE considera somente eventos com mediana disponivel; censura pode ser informativa.",
                  "Bootstrap agrupa participantes e condiciona modelo/mapeamento/censura ajustados.",
                  "Problemas repetidos, pausas desconhecidas e dominio de contest versus treino limitam transporte.", ""]
        if metrics["test_evaluated"]:
            def formatted(metric):
                value, ci = metric["estimate"], metric["ci95"]
                text = f"{value:.4f}" if value is not None else "indisponivel"
                if ci is not None:
                    text += f" [{ci[0]:.4f}, {ci[1]:.4f}]"
                return f"{text}; n={metric['n']}"
            lines += ["Valores com IC95% por participante; erros de tempo apenas nos eventos.", "",
                      "| Candidato no teste | MAE (min) | Brier integrado | Dominio | Mediana nos eventos |",
                      "|---|---|---|---|---|"]
            for name, result in metrics["test"].items():
                values = result["metrics"]
                lines.append(f"| {name} | {formatted(values['time/mae'])} | "
                             f"{formatted(values['distribution/integrated_brier_ipcw'])} | "
                             f"{result['in_domain_fraction']:.1%} | {result['median_coverage_events']:.1%} |")
            chosen = metrics["selection"]["chosen"]
            selected = metrics["test"][chosen]
            lines += ["", "Calibracao IPCW do candidato escolhido:", "",
                      "| Orcamento (min) | ECE [IC95%]; n |", "|---|---|"]
            for budget in TRANSPORT["budgets_minutes"]:
                lines.append(f"| {budget} | {formatted(selected['metrics'][f'probability/{budget}/ece_ipcw'])} |")
            lines += ["", "Pares anteriores ao inicio; correlacao nao prova equivalencia:", "",
                      "| Particao | Lorem-CF [IC95%]; n | Pearson [IC95%]; n |", "|---|---|---|"]
            for part, relation in metrics["rating_pairs"].items():
                lines.append(f"| {part} | {formatted(relation['metrics']['lorem_minus_cf_bias'])} | "
                             f"{formatted(relation['metrics']['pearson'])} |")
            lines += ["", "Gate numerico do candidato escolhido:", ""]
            for group, gate in metrics["gates"].items():
                lines.append(f"- {group}: {'limites atendidos' if gate['passed'] else 'falhou'}; "
                             f"motivos: {', '.join(gate['failures']) or 'nenhum'}.")
            lines += ["", "Estratos e comparacoes pareadas com denominadores/suporte comum constam em metrics.json.", ""]
    else:
        lines += ["Amostra insuficiente: nenhum mapeamento ajustado, nenhuma metrica de teste produzida.", ""]
    (output / "REPORT.md").write_text("\n".join(lines), encoding="utf-8")
