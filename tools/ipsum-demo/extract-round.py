"""Extract the converted TCC workbook without changing the source file."""

import hashlib
import json
import sys
from datetime import datetime
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

import openpyxl


def extract(path):
    workbook = openpyxl.load_workbook(path, read_only=True, data_only=True)
    summary = dict(list(workbook["Resumo da rodada"].values)[1:])
    rows = list(workbook["Ipsums"].values)[1:]
    references = list(workbook["Dados originais"].values)[1:]
    if not (len(rows) == len(references) == summary["Ipsums"] == 200
            and summary["Habilidade"] == 1000 and summary["Rating inicial"] == 800
            and summary["Prazo (min)"] == 120 and summary["K"] == 32):
        raise ValueError("A amostra precisa ter 200 Ipsums, habilidade 1000, saldo 800, K32 e prazo de 120 min.")

    attempts = []
    for number, (row, ref) in enumerate(zip(rows, references), 1):
        contest, index, title, difficulty, tags, start, minutes, result, errors, hint, upsolve = row
        if (ref[1] != number or ref[3] != difficulty
                or result != {"AC": "AC", "Sem AC": "PENDENTE"}[ref[4]]
                or abs(Decimal(str(minutes)) - Decimal(str(ref[6]))) > Decimal("0.000025")
                or hint is not None or upsolve is not None):
            raise ValueError(f"Ipsum {number} incompatível com o histórico sem dicas da rodada.")
        category = {"Fluência": "FLUENCY", "Nível atual": "CURRENT_LEVEL", "Desafio": "CHALLENGE"}[ref[2]]
        wall_time = Decimal(str(minutes)) * 60_000
        attempts.append({
            "contestId": contest, "index": index, "name": title, "difficulty": difficulty,
            "tags": tags.split("|"), "startedAt": datetime.fromisoformat(start).isoformat(timespec="milliseconds"),
            "elapsedMillis": int(wall_time.quantize(Decimal("1"), rounding=ROUND_HALF_UP)),
            "accepted": result == "AC", "errors": errors, "category": category,
            "sheetLorem": ref[12:15], "sheetDolor": ref[15:18],
        })
    return {
        "source": {
            "workbook": path.name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "original": summary["Arquivo original"], "round": summary["Rodada"], "seed": summary["Semente"],
            "ability": summary["Habilidade"],
        },
        "attempts": attempts,
    }


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit("Uso: python tools/ipsum-demo/extract-round.py arquivo.xlsx amostra.json")
    Path(sys.argv[2]).write_text(json.dumps(extract(Path(sys.argv[1])), ensure_ascii=False) + "\n", encoding="utf-8")
