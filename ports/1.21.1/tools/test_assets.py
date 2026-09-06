import unittest
from validate_obj import ROOT,validate

class AssetRegressionTests(unittest.TestCase):
    def setUp(self): self.good=(ROOT/'src/main/resources/assets/eln/models/block/circuit_bench.obj').read_text()
    def test_visible_main_is_complete(self): self.assertEqual(validate(self.good)['triangles'],12)
    def test_missing_uv_rejected(self):
        with self.assertRaises(ValueError):validate(self.good.replace('6/1/1','6//1',1))
    def test_invalid_uv_rejected(self):
        with self.assertRaises(ValueError):validate(self.good.replace('6/1/1','6/999/1',1))
    def test_helper_object_rejected(self):
        with self.assertRaises(ValueError):validate(self.good+'o Minecraft_Block_Cube\n')
    def test_nan_vertex_rejected(self):
        with self.assertRaises(ValueError):validate(self.good.replace('v 0.366666667','v NaN',1))

if __name__=='__main__':unittest.main()
