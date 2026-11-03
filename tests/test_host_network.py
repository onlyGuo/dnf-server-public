import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('host_network', Path(__file__).resolve().parents[1] / 'deploy/integrated/host_network.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class HostNetworkTest(unittest.TestCase):
    def create_files(self, root):
        mysql = root / 'my.cnf'
        supervisor = root / 'supervisord.conf'
        main = root / 'main.sh'
        group = root / 'group.sh'
        conf_dir = root / 'conf.d'
        conf_dir.mkdir()
        mysql.write_text('[client]\nport=3306\n[mysqld]\nport=4000\n[mysqldump]\nquick\n')
        supervisor.write_text('[inet_http_server]\nport=0.0.0.0:180\nusername=test\n[supervisord]\nlogfile=/tmp/log\n')
        main.write_text('socat TCP-LISTEN:3307,fork,reuseaddr TCP:127.0.0.1:4000\n')
        group.write_text('socat TCP-LISTEN:3306,fork,reuseaddr TCP:127.0.0.1:4000\n')
        return mysql, supervisor, main, group, conf_dir

    def test_loopback_proxies_and_aliases_are_idempotent(self):
        with tempfile.TemporaryDirectory() as directory:
            paths = self.create_files(Path(directory))
            module.configure(*map(str, paths))
            first = [p.read_text() for p in paths[:4]]
            module.configure(*map(str, paths))
            self.assertEqual(first, [p.read_text() for p in paths[:4]])
            self.assertIn('[mysqld]\nbind-address=127.0.0.1\nport=4000\n[mysqldump]', first[0])
            self.assertIn('[inet_http_server]\nport=127.0.0.1:180\nusername=test\n[supervisord]', first[1])
            self.assertIn('TCP-LISTEN:3307,bind=127.0.0.1,fork,reuseaddr', first[2])
            self.assertIn('TCP-LISTEN:3306,bind=127.0.0.1,fork,reuseaddr', first[3])
            aliases = (paths[4] / 'dnf-admin-host-aliases.conf').read_text()
            self.assertIn('TCP-LISTEN:3000,fork,reuseaddr', aliases)
            self.assertIn('TCP-LISTEN:2000,fork,reuseaddr', aliases)

    def test_unknown_proxy_is_rejected_before_any_file_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            paths = self.create_files(Path(directory))
            paths[3].write_text('unsupported custom proxy\n')
            before = [p.read_text() for p in paths[:4]]
            with self.assertRaises(ValueError):
                module.configure(*map(str, paths))
            self.assertEqual(before, [p.read_text() for p in paths[:4]])
            self.assertFalse((paths[4] / 'dnf-admin-host-aliases.conf').exists())


if __name__ == '__main__':
    unittest.main()
