"""Execute notebook 06 in a fresh offline kernel; never collect/train or edit sources."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys

import nbformat
from nbclient import NotebookClient
from nbconvert import HTMLExporter
from jupyter_client.kernelspec import KernelSpecManager
from jinja2 import DictLoader


def run(study,output,mode="aggregate",results=None,full_root=None):
    study,output=Path(study).resolve(),Path(output).resolve()
    protected=[study/"results",study/"evidence",study/"notebooks"]
    if results:protected.append(Path(results).resolve())
    if full_root:protected.append(Path(full_root).resolve())
    if any(output==p or p in output.parents or output in p.parents for p in protected):
        raise ValueError("Use a separate empty output directory")
    if output.exists() and any(output.iterdir()):raise ValueError("Output must be empty")
    output.mkdir(parents=True,exist_ok=True)
    spec=output/"kernel/lorem-scale"
    spec.mkdir(parents=True)
    (spec/"kernel.json").write_text(json.dumps({"argv":[sys.executable,"-m","ipykernel_launcher","-f","{connection_file}"],
                                               "display_name":"Lorem scale (isolated)","language":"python"}))
    env={**os.environ,"LOREM_SCALE_MODE":mode,"LOREM_SCALE_OUTPUT":str(output/"exports"),
         "LOREM_SCALE_RESULTS":str(Path(results).resolve()) if results else "",
         "LOREM_SCALE_FULL":str(Path(full_root).resolve()) if full_root else "",
         "MPLCONFIGDIR":str(output/"matplotlib"),"IPYTHONDIR":str(output/"ipython"),
         "XDG_CACHE_HOME":str(output/"cache"),"JUPYTER_RUNTIME_DIR":str(output/"jupyter"),
         "OPENBLAS_NUM_THREADS":"1"}
    source=study/"notebooks/06_escala_t50.ipynb"
    notebook=nbformat.read(source,as_version=4)
    manager=KernelSpecManager(kernel_dirs=[str(spec.parent)])
    client=NotebookClient(notebook,timeout=3600,kernel_name="lorem-scale",allow_errors=False,
                          resources={"metadata":{"path":str(study)}})
    client.create_kernel_manager();client.km.kernel_spec_manager=manager
    client.execute(env=env)
    executed=output/source.name
    nbformat.write(notebook,executed)
    # Notebook 06 uses ordinary HTML/SVG/text, not MathJax/Mermaid/widgets.
    # Override their CDN-loading blocks so the exported page is fully local.
    template="""{% extends 'lab/index.html.j2' %}
    {% block html_head_js_requirejs %}{% endblock %}
    {% block html_head_js_mathjax %}{% endblock %}
    {% block html_head_js_mermaidjs %}{% endblock %}"""
    exporter=HTMLExporter(template_name="lab",template_file="scale-offline.html.j2",embed_images=True,
                          extra_loaders=[DictLoader({"scale-offline.html.j2":template})])
    html,_=exporter.from_notebook_node(notebook)
    executed.with_suffix(".html").write_text(html,encoding="utf-8")
    summary={"mode":mode,"clean_kernels":1,"network":"urllib HTTP disabled; no collection or fitting",
             "html":"embedded figures and interactive iframe; no external JS/CDN dependencies",
             "source_sha256":hashlib.sha256(source.read_bytes()).hexdigest(),
             "executed_sha256":hashlib.sha256(executed.read_bytes()).hexdigest(),
             "html_sha256":hashlib.sha256(executed.with_suffix('.html').read_bytes()).hexdigest(),
             "executed_cells":sum(c.cell_type=="code" for c in notebook.cells)}
    (output/"execution.json").write_text(json.dumps(summary,indent=2)+"\n")
    print(summary)
    return summary


if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output",type=Path,required=True)
    parser.add_argument("--mode",choices=["aggregate","full"],default="aggregate")
    parser.add_argument("--results",type=Path)
    parser.add_argument("--full-root",type=Path)
    args=parser.parse_args()
    run(Path(__file__).resolve().parents[1],args.output,args.mode,args.results,args.full_root)
