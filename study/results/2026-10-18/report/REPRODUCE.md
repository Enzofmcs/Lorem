# Reproducao do snapshot ao relatorio

Na pasta study, criar um venv limpo e instalar requirements.lock e requirements-report.lock. Instalar o pacote com pip install --no-build-isolation --no-deps .

Executar com o Python desse venv: `python scripts/reproduce.py SOURCE DESTINATION`. SOURCE deve ser o diretorio do snapshot original, restaurado do backup local se necessario; DESTINATION nao pode existir. O script copia os ponteiros e usa brutos imutaveis, executa collect --offline, prepare, split, train-baseline, train, evaluate e report; compara todos os hashes de tabelas/modelos/metricas/figuras com SOURCE. reproduction.json registra tempos, versoes, zero requisicoes de rede e igualdade por arquivo.

manifest.json neste diretorio identifica todos os agregados por SHA-256. Seed e plano estao em evaluation/plan.json. Aquisicao real previa: 308 respostas, 1.654.835.689 bytes, 633,889 s; novas chamadas da API nao garantem os mesmos dados. O backup bruto contem handles publicos e permanece local/fora do Git. Nenhuma fixture substitui dados reais.
