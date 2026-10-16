"""Exercise the real resource validator against hostile changes in disposable pack copies."""
import shutil
import subprocess
import sys
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class FoxModelValidatorTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='contract-model-validator-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'tools').mkdir()
        for name in ('validate_fox_models.py', 'fox_model_runtime_manifest.json'):
            shutil.copy2(ROOT / 'tools' / name, self.root / 'tools' / name)
        resource = self.root / 'src/main/resources'
        shutil.copytree(ROOT / 'src/main/resources/modelpacks', resource / 'modelpacks')
        shutil.copytree(ROOT / 'src/main/resources/licenses/fox_models', resource / 'licenses/fox_models')
        self.pack = resource / 'modelpacks/contract-fox-1.0.1.zip'

    def validate(self):
        return subprocess.run([sys.executable, str(self.root / 'tools/validate_fox_models.py')],
                              capture_output=True, text=True)

    def test_reviewed_pack_passes(self):
        self.assertEqual(self.validate().returncode, 0)

    def test_unknown_asset_rejected(self):
        with zipfile.ZipFile(self.pack, 'a') as pack:
            pack.writestr('assets/contract_fox/unreviewed.bin', b'unreviewed')
        result = self.validate()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('reviewed resource inventory', result.stderr)

    def test_known_asset_tampering_rejected(self):
        replacement = self.pack.with_suffix('.tmp')
        with zipfile.ZipFile(self.pack) as original, zipfile.ZipFile(replacement, 'w') as altered:
            for info in original.infolist():
                data = original.read(info.filename)
                if info.filename == 'pack.png':
                    data += b'altered'
                altered.writestr(info, data)
        replacement.replace(self.pack)
        result = self.validate()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Unreviewed resource change', result.stderr)

    def test_duplicate_member_rejected(self):
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)  # Duplicate creation is the intentional fault.
            with zipfile.ZipFile(self.pack, 'a') as pack:
                pack.writestr('pack.png', pack.read('pack.png'))
        result = self.validate()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Duplicate ZIP entries', result.stderr)


if __name__ == '__main__':
    unittest.main()
