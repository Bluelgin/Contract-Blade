"""Exercise synchronization on real disposable Git histories, without game dependencies."""
import pathlib
import subprocess
import sys
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).with_name('shared_patch.py')


class SharedPatchTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = pathlib.Path(self.directory.name)
        self.git('init', '-q')
        self.git('config', 'user.email', 'fixture@example.invalid')
        self.git('config', 'user.name', 'Fixture')
        self.write('src/shared/java/Rule.java', 'old\n')
        self.write('src/port/java/Adapter.java', 'platform\n')
        self.commit('baseline')
        self.baseline = self.git('rev-parse', 'HEAD').strip()
        self.write('src/shared/java/Rule.java', 'new\n')
        self.write('src/port/java/Adapter.java', 'must not copy\n')
        self.commit('source')
        self.source = self.git('rev-parse', 'HEAD').strip()
        self.git('checkout', '-q', '--detach', self.baseline)

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.root, text=True)

    def write(self, path, text):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text)

    def commit(self, message):
        self.git('add', '.')
        self.git('commit', '-qm', message)

    def run_patch(self, *args):
        return subprocess.run([sys.executable, str(SCRIPT), self.baseline, self.source, *args],
                              cwd=self.root, text=True, capture_output=True)

    def test_apply_is_scoped_and_drift_is_detected(self):
        self.assertNotEqual(0, self.run_patch('--check').returncode)
        self.assertEqual(0, self.run_patch().returncode)
        self.assertEqual('old\n', (self.root/'src/shared/java/Rule.java').read_text())
        self.assertEqual(0, self.run_patch('--apply').returncode)
        self.assertEqual('new\n', (self.root/'src/shared/java/Rule.java').read_text())
        self.assertEqual('platform\n', (self.root/'src/port/java/Adapter.java').read_text())
        self.commit('synced')
        self.assertEqual(0, self.run_patch('--check').returncode)

    def test_conflicts_and_untracked_work_are_not_overwritten(self):
        self.write('src/shared/java/Rule.java', 'target customization\n')
        self.commit('divergence')
        self.assertNotEqual(0, self.run_patch('--apply').returncode)
        self.assertEqual('target customization\n', (self.root/'src/shared/java/Rule.java').read_text())
        self.git('checkout', '-q', '--detach', self.baseline)
        self.write('untracked', 'preserve')
        self.assertNotEqual(0, self.run_patch('--apply').returncode)
        self.assertEqual('old\n', (self.root/'src/shared/java/Rule.java').read_text())


if __name__ == '__main__':
    unittest.main()
