import csv
from pathlib import Path
import tempfile
import unittest

import numpy as np

from lorem_study.io import read_json
from lorem_study.scale_analysis import export_table
from lorem_study.scale_view import export_figures,interactive_html,plot_curves


def example_report():
    """Artificial missing bin between two identified values, including t50>h."""
    plan=read_json(Path(__file__).parents[1]/'configs/scale-t50-plan-v1.json')
    support={'attempts':100,'events':60,'censored':40,'participants':90,'problems':5,
             'contests':10,'groups':10,'max_followup_seconds':10800,'at_risk':{'120':20}}
    rows=[]
    for old in (1200,1600):
        for problem,median in [(800,20.),(1000,None),(1200,130.)]:
            value={'t50_minutes':median,'unfiltered_t50_minutes':median,
                   'reason':None if median else 'quantile_not_reached_in_followup',
                   'uncertainty':{'ci95_minutes':[median-2,median+2] if median else None,'missing_replicates':0 if median else 200}}
            predictions={k:{'t50_minutes':median,'reason':value['reason'],'artifact_sha256':'f'*64} for k in ('aft','km_delta','km_global')}
            rows.append({'old_lower':old,'old_upper':old+400,'problem_lower':problem,'problem_upper':problem+200,
                         'problem_x':problem+100,'mode':'observed_covariate_mixture','support':support,'common_support':support,
                         'common_coverage':1.,'observed':value,'common_observed':value,'predictions':predictions})
    return {'schema_version':1,'version':'scale-t50-analysis-v1','data_kind':'synthetic','plan':plan,
            'plan_sha256':'a'*64,'snapshot_sha256':'b'*64,'groups':[[1],[2]],'total_valid':100,'rows':rows}


class ScalePresentationTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.output=Path(self.temp.name)

    def test_static_lines_equal_exported_table_and_keep_null_gaps(self):
        import matplotlib.pyplot as plt
        report=example_report()
        export_table(report,self.output/'t50.csv')
        with (self.output/'t50.csv').open() as source:
            rows=list(csv.DictReader(source))
        fig=plot_curves(report,bands=[1200],methods=('observed',))
        self.addCleanup(lambda:plt.close(fig))
        line=fig.axes[0].lines[0]
        expected=[r for r in rows if r['method']=='observed' and r['old_lower']=='1200']
        actual=line.get_ydata()
        self.assertEqual(expected[0]['units'],'minutes')
        self.assertEqual(expected[0]['population'],'all eligible attempts in cell')
        self.assertEqual(expected[0]['model_version'],'kaplan-meier-v1')
        report['model_hashes']={'aft':'e'*64,'baseline':'d'*64}
        report['rows'][0]['predictions']['aft']={'t50_minutes':None,'reason':'no_common_support'}
        export_table(report,self.output/'missing.csv')
        with (self.output/'missing.csv').open() as source:
            missing=next(r for r in csv.DictReader(source) if r['method']=='aft')
        self.assertEqual(missing['artifact_sha256'],'e'*64)
        self.assertEqual(actual[0],float(expected[0]['t50_minutes']))
        self.assertTrue(np.isnan(actual[1]))
        self.assertEqual(expected[1]['t50_minutes'],'')
        self.assertEqual(actual[2],130.)
        self.assertGreater(fig.axes[0].get_ylim()[1],130.)
        np.testing.assert_array_equal(line.get_xdata(),[900,1100,1300])

    def test_exports_are_real_svg_png_and_serialize_the_same_values(self):
        report=example_report()
        export_figures(report,self.output)
        for name in ('t50-observado','t50-modelos'):
            self.assertEqual((self.output/f'{name}.png').read_bytes()[:8],b'\x89PNG\r\n\x1a\n')
            svg=(self.output/f'{name}.svg').read_text()
            self.assertIn('<svg',svg)
            self.assertIn(report['snapshot_sha256'][:16],svg)
        page=interactive_html(report)
        self.assertIn('130.0',page)
        self.assertIn('quantile_not_reached_in_followup',page)
        self.assertIn('value="fixed"',page)
        self.assertNotIn('<script src=',page)

    def test_payload_cannot_close_the_inline_script(self):
        report=example_report();report['note']='</script><img src=x onerror=alert(1)>'
        page=interactive_html(report)
        self.assertEqual(page.count('</script>'),1)
        self.assertIn('\\u003c/script>',page)


if __name__=='__main__':unittest.main()
