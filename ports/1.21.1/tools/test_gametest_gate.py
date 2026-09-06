import unittest
from verify_gametests import EXPECTED, validate

class GameTestGateTests(unittest.TestCase):
    def sample(self):
        return '\n'.join('ELN_GAMETEST_PASS '+name for name in sorted(EXPECTED))+f'\n{len(EXPECTED)} GAME TESTS COMPLETE\nAll {len(EXPECTED)} required tests passed :)\nBUILD SUCCESSFUL\n'
    def test_exact_case_and_framework_match(self):
        self.assertEqual(validate(self.sample())['required_gametests'],len(EXPECTED))
    def test_duplicates_cannot_replace_missing_test(self):
        text=self.sample().replace('ELN_GAMETEST_PASS bench_fault_latch','ELN_GAMETEST_PASS server_charge')
        with self.assertRaises(ValueError):validate(text)
    def test_markers_without_framework_success_rejected(self):
        with self.assertRaises(ValueError):validate(self.sample().replace('required tests passed','required tests FAILED'))
    def test_failed_gradle_rejected(self):
        with self.assertRaises(ValueError):validate(self.sample()+'BUILD FAILED\n')
