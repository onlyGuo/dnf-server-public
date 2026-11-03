import importlib.util
from pathlib import Path
import tempfile
import unittest
import os
import signal
import subprocess
import time

spec = importlib.util.spec_from_file_location('patch_monitor', Path(__file__).resolve().parents[1] / 'deploy/integrated/patch_monitor.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class PatchMonitorTest(unittest.TestCase):
    def test_replaces_only_expected_dns_lookup_and_is_idempotent(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'monitor.sh'
            path.write_text('#!/bin/bash\n' + module.OLD + '\necho keep\n')
            module.patch(str(path))
            once = path.read_text()
            self.assertIn(module.NEW, once)
            self.assertIn('echo keep', once)
            self.assertNotIn(module.OLD, once)
            module.patch(str(path))
            self.assertEqual(once, path.read_text())

    def test_unknown_script_fails_without_changing_it(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'monitor.sh'
            path.write_text('custom monitor\n')
            with self.assertRaises(ValueError): module.patch(str(path))
            self.assertEqual('custom monitor\n', path.read_text())

    def test_current_dnf_upstream_layout_is_supported(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'monitor.sh'
            path.write_text(module.UPSTREAM_QUERY + '\n  # preserve comment\n' + module.SAFE_PARSE + '\n')
            module.patch(str(path))
            once = path.read_text()
            self.assertIn(module.SAFE_QUERY, once)
            self.assertIn('  # preserve comment', once)
            self.assertIn(module.SAFE_PARSE, once)
            module.patch(str(path))
            self.assertEqual(once, path.read_text())

    def test_default_address_wrapper_preserves_original_and_restores_cached_ip(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / 'dnf data'
            (root / 'channel-manager').mkdir(parents=True)
            (root / 'monitor_ip').mkdir()
            path = Path(directory) / 'monitor.sh'
            original = '#!/bin/bash\n# DDNS_ENABLE MONITOR_PUBLIC_IP\necho original\n'
            path.write_text(original)
            path.chmod(0o755)
            module.install_default_wrapper(str(path), str(root))
            once = path.read_text()
            module.install_default_wrapper(str(path), str(root))
            self.assertEqual(once, path.read_text())
            self.assertEqual(original, Path(str(path) + '.before-web-default').read_text())
            self.assertEqual('original', subprocess.check_output([str(path)], text=True).strip())
            (root / 'channel-manager/default-address.properties').write_text('mode=STATIC\ntarget=192.0.2.9\n')
            (root / 'channel-manager/default-address-ip').write_text('192.0.2.9\n')
            process = subprocess.Popen([str(path)], start_new_session=True)
            try:
                result = root / 'monitor_ip/MONITOR_PUBLIC_IP'
                for _ in range(50):
                    if result.exists(): break
                    time.sleep(0.05)
                self.assertEqual('192.0.2.9', result.read_text().strip())
            finally:
                os.killpg(process.pid, signal.SIGTERM)
                process.wait(timeout=5)

if __name__ == '__main__': unittest.main()
