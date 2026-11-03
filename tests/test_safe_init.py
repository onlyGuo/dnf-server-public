import os
from pathlib import Path
import subprocess
import tempfile
import unittest


class SafeDatabaseInitializationTest(unittest.TestCase):
    def test_initialization_never_includes_drop_or_sample_rows(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            mysql = root / 'mysql'
            mysql.write_text('#!/bin/sh\ncase "$*" in *"COLUMN_NAME=\'admin\'"*) echo 0;; *) echo 1;; esac\n')
            mysql.chmod(0o755)
            template = root / 'init.sql'
            template.write_text('DROP TABLE IF EXISTS `example`;\n'
                                'CREATE TABLE `example` (`id` int) ENGINE=InnoDB;\n'
                                'INSERT INTO `example` VALUES (1);\n')
            env = dict(os.environ, PATH=str(root) + os.pathsep + os.environ.get('PATH', ''),
                       DNF_INIT_SQL=str(template), DNF_DB_INIT_NAME='test_web')
            script = Path(__file__).resolve().parents[1] / 'deploy/integrated/init_db.py'
            result = subprocess.check_output(['python3', str(script)], env=env, text=True)
            self.assertIn('CREATE DATABASE IF NOT EXISTS `test_web`', result)
            self.assertIn('CREATE TABLE IF NOT EXISTS `example`', result)
            self.assertIn('ADD COLUMN `admin`', result)
            self.assertNotIn('DROP TABLE', result)
            self.assertNotIn('INSERT INTO', result)


if __name__ == '__main__':
    unittest.main()
