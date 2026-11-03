from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / 'deploy/web/setup-keys.sh'


class WebKeySetupTest(unittest.TestCase):
    def test_existing_private_key_is_preserved_when_public_key_is_missing(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            private = root / 'private.key'
            subprocess.run(['openssl', 'genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:1024', '-out', str(private)],
                           check=True, capture_output=True)
            original = private.read_bytes()
            expected_public = subprocess.check_output(['openssl', 'pkey', '-in', str(private), '-pubout'])
            subprocess.run(['sh', str(SCRIPT), str(root)], check=True, capture_output=True)
            self.assertEqual(original, private.read_bytes())
            self.assertEqual(expected_public, (root / 'publickey.pem').read_bytes())

    def test_public_key_alone_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            public = root / 'publickey.pem'
            public.write_text('existing public key')
            subprocess.run(['sh', str(SCRIPT), str(root)], check=True, capture_output=True)
            self.assertEqual('existing public key', public.read_text())
            self.assertFalse((root / 'private.key').exists())

    def test_raw_pkcs8_project_key_can_derive_its_public_key(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / 'source.pem'
            subprocess.run(['openssl', 'genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:1024', '-out', str(source)],
                           check=True, capture_output=True)
            lines = source.read_text().splitlines()
            private = root / 'private.key'
            private.write_text('\n'.join(lines[1:-1]) + '\n')
            original = private.read_bytes()
            expected_public = subprocess.check_output(['openssl', 'pkey', '-in', str(source), '-pubout'])
            subprocess.run(['sh', str(SCRIPT), str(root)], check=True, capture_output=True)
            self.assertEqual(original, private.read_bytes())
            self.assertEqual(expected_public, (root / 'publickey.pem').read_bytes())


if __name__ == '__main__':
    unittest.main()
