"""Gate unit tests. Synthetic images here are test data, NEVER native evidence."""
import copy
import json
import io
from pathlib import Path
import random
import tempfile
import unittest
from PIL import Image
from native_campaign_plan import expected, gallery_id, SUITES
from native_campaign_report import validate_phase, build_index

class NativeReportGateTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name);self.addCleanup(self.temp.cleanup)
        self.suite='mechanical';self.nonce='unit-test-nonce';self.sha='a'*64
        self.gallery=[dict(id=f'eln:unit_fixture_{i}',descriptor=i,side=0,kind='six',x=i,y=80,z=0) for i in range(4)]
        self.names=expected(self.suite)
        self.assigned=[gallery_id(self.gallery[2])]
        random.seed(17)
        im=Image.frombytes('RGB',(640,400),random.randbytes(640*400*3))
        image_bytes=io.BytesIO();im.save(image_bytes,format='PNG',compress_level=0)
        png=image_bytes.getvalue()
        rows=[]
        for name in self.names+self.assigned:
            f=f'{name}.png';(self.root/f).write_bytes(png)
            before=f'before-{name}.png';(self.root/before).write_bytes(png)
            observations = {
                'shaft-unsafe-reinsert': dict(motorBeforeRadS=187.6,generatorBeforeRadS=30.,placementObserved=True,replacementDestroyed=True,ghostConnection=False,sharedNetwork=False),
                'shaft-safe-reinsert': dict(motorBeforeRadS=12.,generatorBeforeRadS=5.,sharedNetwork=True,survivedSlowTicks=True),
                'clutch-synchronised': dict(distinctNetworks=True,slipping=False,deltaRadS=0.),
                'clutch-coal-destroyed': dict(engagementDeltaRadS=100.,clutchPresent=False,expectedDestruction=True,distinctNetworks=True),
            }
            rows.append(dict(id=name,status='passed',kind='gallery' if name in self.assigned else 'functional',observation=observations.get(name,{'unitData':True}),screenshot=f,beforeScreenshot=before,components=['eln:unit_fixture_2']))
        self.report=dict(schema=1,suite=self.suite,runId=self.nonce,jarSha256=self.sha,restart=False,complete=True,expected=self.names,galleryExpected=self.assigned,
            runtime=dict(pid=123,jarSha256=self.sha,runId=self.nonce),results=rows)
        self.coverage=dict(runId=self.nonce,jarSha256=self.sha,seedProduction=True,seedJarSha256=self.sha,registry=[dict(id=e['id'],descriptor=e['descriptor']) for e in self.gallery],galleryAll=self.gallery,assignedGallery=self.assigned)
        self.write()
    def write(self):
        (self.root/'report.json').write_text(json.dumps(self.report));(self.root/'coverage.json').write_text(json.dumps(self.coverage))
    def valid(self): return validate_phase(self.root,self.suite,self.nonce,self.sha,False)
    def invalid(self):
        self.write()
        with self.assertRaises((ValueError,KeyError,FileNotFoundError,OSError)):self.valid()
    def test_accepts_exact_nonempty_evidence(self):
        r=self.valid();self.assertEqual(r['functional'],15);self.assertEqual(r['gallery'],1);self.assertEqual(len(r['captures']),31)
    def test_missing_before_capture(self):self.report['results'][0].pop('beforeScreenshot');self.invalid()
    def test_reused_before_capture(self):self.report['results'][0]['beforeScreenshot']=self.report['results'][0]['screenshot'];self.invalid()
    def test_dev_seed_rejected(self):self.coverage['seedProduction']=False;self.invalid()
    def test_other_seed_jar_rejected(self):self.coverage['seedJarSha256']='b'*64;self.invalid()
    def test_missing_result(self):self.report['results'].pop();self.invalid()
    def test_duplicate_result(self):self.report['results'].append(copy.deepcopy(self.report['results'][0]));self.invalid()
    def test_failed_result(self):self.report['results'][0]['status']='failed';self.invalid()
    def test_skipped_result(self):self.report['results'][0]['status']='skipped';self.invalid()
    def test_empty_observation(self):self.report['results'][0]['observation']={};self.invalid()
    def test_stale_run(self):self.report['runId']='old';self.invalid()
    def test_wrong_jar(self):self.report['runtime']['jarSha256']='b'*64;self.invalid()
    def test_missing_process(self):self.report['runtime']['pid']=0;self.invalid()
    def test_incomplete(self):self.report['complete']=False;self.invalid()
    def test_fake_boolean(self):self.report['complete']=1;self.invalid()
    def test_empty_plans(self):self.report['expected']=[];self.report['results']=[];self.invalid()
    def test_truncated_declared_plan(self):self.report['expected']=self.names[:-1];self.invalid()
    def test_registry_missing_descriptor(self):self.coverage['registry'].append({'id':'eln:missing','descriptor':100});self.invalid()
    def test_gallery_partition(self):self.coverage['assignedGallery']=[];self.invalid()
    def test_duplicate_gallery_plan(self):self.report['galleryExpected']*=2;self.invalid()
    def test_mislabelled_gallery(self):self.report['results'][-1]['kind']='functional';self.invalid()
    def test_missing_png(self):(self.root/self.report['results'][0]['screenshot']).unlink();self.invalid()
    def test_reused_png(self):self.report['results'][1]['screenshot']=self.report['results'][0]['screenshot'];self.invalid()
    def test_traversal(self):self.report['results'][0]['screenshot']='../../not-a-capture.png';self.invalid()
    def test_absolute_path(self):self.report['results'][0]['screenshot']=str(self.root/self.report['results'][0]['screenshot']);self.invalid()
    def test_blank_image(self):Image.new('RGB',(640,400),'white').save(self.root/self.report['results'][0]['screenshot']);self.invalid()
    def test_small_image(self):Image.new('RGB',(16,16)).save(self.root/self.report['results'][0]['screenshot']);self.invalid()
    def test_corrupt_image(self):(self.root/self.report['results'][0]['screenshot']).write_bytes(b'x'*2048);self.invalid()
    def test_restart_must_have_retained_state(self):
        self.report.update(restart=True,expected=[],galleryExpected=[],results=[]);self.write()
        with self.assertRaises(ValueError):validate_phase(self.root,self.suite,self.nonce,self.sha,True,0)
    def test_html_is_generated_for_failed_or_incomplete_run(self):
        phase=self.root/'first';phase.mkdir();(phase/'report.json').write_text(json.dumps({'results':[{'id':'failure-test','title':'<script>bad</script>','status':'failed','detail':'assertion missing'}]}))
        build_index(self.root);text=(self.root/'index.html').read_text();self.assertIn('&lt;script&gt;',text);self.assertIn('Incomplete run',text)
    def test_expected_plans_are_nonempty_unique(self):
        for suite in SUITES:
            items=expected(suite);self.assertTrue(items);self.assertEqual(len(items),len(set(items)))

if __name__=='__main__':unittest.main(verbosity=2)
