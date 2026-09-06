"""Negative tests for source preservation, not game tests."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from verify_preservation import check

class PreservationTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='eln-preservation-')
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.ref = self.root / 'reference/rewired-1.12.2'
        self.files = {
            'src/main/java/mods/eln/init/Items.kt': b'val id = "Copper Ingot"\n',
            'src/main/java/mods/eln/init/Descriptors.kt': b'val id = "Battery"\n',
            'src/main/java/mods/eln/Test.java': b'class Test {}\n',
            'src/main/resources/test.bin': bytes([0, 13, 10, 255]),
        }
        for name, data in self.files.items():
            for base in (self.root, self.ref):
                path = base / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)
        manifest = {'files': {name: hashlib.sha256(data).hexdigest() for name, data in self.files.items()}}
        (self.ref / 'SNAPSHOT.json').write_text(json.dumps(manifest))

    def test_unchanged_passes(self):
        self.assertEqual(check(self.root)['errors'], [])

    def test_active_code_can_change(self):
        (self.root / 'src/main/java/mods/eln/Test.java').write_text('class Test { int count; }\n')
        result = check(self.root)
        self.assertEqual(result['errors'], [])
        self.assertEqual(result['changed_original_source_files'], 1)

    def test_removed_code_fails(self):
        (self.root / 'src/main/java/mods/eln/Test.java').unlink()
        self.assertTrue(any('Original input missing' in x for x in check(self.root)['errors']))

    def test_modified_reference_fails(self):
        (self.ref / 'src/main/java/mods/eln/Test.java').write_text('class Changed {}\n')
        self.assertTrue(any('Immutable reference changed' in x for x in check(self.root)['errors']))

    def test_binary_normalization_fails(self):
        (self.root / 'src/main/resources/test.bin').write_bytes(bytes([0, 10, 255]))
        self.assertTrue(any('Resource differs' in x for x in check(self.root)['errors']))

    def test_renamed_catalogue_literal_fails(self):
        (self.root / 'src/main/java/mods/eln/init/Items.kt').write_text('val id = "Toy Block"\n')
        self.assertTrue(any('registration literals changed' in x for x in check(self.root)['errors']))

if __name__ == '__main__':
    unittest.main()
