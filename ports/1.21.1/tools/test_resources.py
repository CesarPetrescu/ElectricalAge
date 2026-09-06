import json, pathlib, shutil, tempfile, unittest
from validate_resources import ROOT, validate
class ResourceTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.root=pathlib.Path(self.tmp.name)/'resources';shutil.copytree(ROOT/'src/main/resources',self.root)
    def mutate(self,path,fn):
        p=self.root/path;data=json.loads(p.read_text());fn(data);p.write_text(json.dumps(data))
    def test_complete_resources(self): self.assertEqual(validate(self.root)['network_blockstates'],48)
    def test_missing_variant_rejected(self):
        self.mutate('assets/eln/blockstates/capacitor.json',lambda d:d['variants'].pop('facing=up,lit=true'))
        with self.assertRaises(ValueError):validate(self.root)
    def test_missing_model_rejected(self):
        (self.root/'assets/eln/models/block/capacitor_lit.json').unlink()
        with self.assertRaises(FileNotFoundError):validate(self.root)
    def test_wrong_item_parent_rejected(self):
        self.mutate('assets/eln/models/item/capacitor.json',lambda d:d.update(parent='eln:block/missing'))
        with self.assertRaises(ValueError):validate(self.root)
    def test_wrong_recipe_rejected(self):
        self.mutate('data/eln/recipe/capacitor.json',lambda d:d['result'].update(id='eln:other'))
        with self.assertRaises(ValueError):validate(self.root)
    def test_wrong_loot_rejected(self):
        self.mutate('data/eln/loot_table/blocks/capacitor.json',lambda d:d['pools'][0]['entries'][0].update(name='eln:other'))
        with self.assertRaises(ValueError):validate(self.root)
