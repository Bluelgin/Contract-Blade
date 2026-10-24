"""Negative artifact checks: removed resources and development code must stay out."""
import contextlib
import io
import unittest
import zipfile
from validate_release_readiness import ROOT, VERSION, check_jar


class DistributionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = ROOT / f'build/libs/contract-blade-forge-1.20.1-{VERSION}.jar'
        cls.contents = path.read_bytes()

    def check(self, extra=None):
        content = io.BytesIO(self.contents)
        if extra:
            with zipfile.ZipFile(content, 'a') as jar:
                jar.writestr(*extra)
        content.seek(0)
        with contextlib.redirect_stdout(io.StringIO()):
            check_jar(content)

    def test_current_distribution(self):
        self.check()

    def test_original_model_archive_is_rejected(self):
        with self.assertRaises(AssertionError):
            self.check(('modelpacks/contract-fox-1.0.0.zip', b'fixture'))

    def test_soundtrack_is_rejected(self):
        with self.assertRaises(AssertionError):
            self.check(('assets/maid_weapon/sounds/black_fox/epicbattle_j_loop.ogg', b'fixture'))

    def test_development_class_is_rejected(self):
        with self.assertRaises(AssertionError):
            self.check(('com/maidweapon/forge/compat/tlm/AkatsukiModelValidation.class', b'fixture'))


if __name__ == '__main__':
    unittest.main()
