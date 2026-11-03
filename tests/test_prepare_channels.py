import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('prepare_channels', Path(__file__).resolve().parents[1] / 'deploy/integrated/prepare_channels.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PrepareChannelsTest(unittest.TestCase):
    def test_import_and_restart_keep_existing_choices(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            data = root / 'data'
            (data / 'run').mkdir(parents=True)
            start = data / 'run/start_game.sh'
            start.write_text('#!/bin/bash\ncp /tmp/$channel_name.cfg /home/neople/game/cfg/$channel_name.cfg\n')
            conf = root / 'channel.conf'
            generated = ('[program:channel]\ncommand=/data/run/start_channel.sh\n'
                         '[program:game_siroco11]\ncommand=/data/run/start_game.sh 11 3\n'
                         '[program:game_siroco52]\ncommand=/data/run/start_game.sh 52 5\n'
                         '[group:dnf_channel]\nprograms=channel,game_siroco11,game_siroco52\n')
            conf.write_text(generated)
            with patch.multiple(module, DATA=str(data), ROOT=str(data / 'channel-manager'), CONF=str(conf), START=str(start)), \
                 patch.dict(os.environ, {'SERVER_GROUP': '3', 'SERVER_GROUP_DB': 'cain'}, clear=False):
                module.prepare()
                settings = data / 'channel-manager/channels/52.properties'
                self.assertIn('type=\n', settings.read_text())
                self.assertIn('enabled=true', settings.read_text())
                self.assertIn('programs=channel\n', conf.read_text())
                self.assertNotIn('[program:game_siroco', conf.read_text())
                self.assertIn('[program:channel_admin_52]', (data / 'conf.d/channel-admin-52.conf').read_text())
                self.assertIn('DNF_CHANNEL_MANAGER_IP_OVERRIDE', start.read_text())
                settings.write_text('mode=STATIC\ntarget=192.0.2.22\nenabled=false\ntype=13\n')
                conf.write_text(generated)
                module.prepare()
                self.assertIn('target=192.0.2.22', settings.read_text())
                self.assertIn('type=13', settings.read_text())
                self.assertIn('autostart=false', (data / 'conf.d/channel-admin-52.conf').read_text())
                self.assertEqual(1, start.read_text().count('DNF_CHANNEL_MANAGER_IP_OVERRIDE'))

    def test_refuses_to_patch_unknown_start_script(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            data = root / 'data'
            (data / 'run').mkdir(parents=True)
            start = data / 'run/start_game.sh'
            start.write_text('#!/bin/bash\necho custom script\n')
            conf = root / 'channel.conf'
            original = '[program:channel]\ncommand=/data/run/start_channel.sh\n[group:dnf_channel]\nprograms=channel\n'
            conf.write_text(original)
            with patch.multiple(module, DATA=str(data), ROOT=str(data / 'channel-manager'), CONF=str(conf), START=str(start)):
                with self.assertRaises(SystemExit):
                    module.prepare()
            self.assertEqual(original, conf.read_text())
            self.assertEqual('#!/bin/bash\necho custom script\n', start.read_text())

    def test_invalid_group_does_not_finish_migration(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            data = root / 'data'
            (data / 'run').mkdir(parents=True)
            start = data / 'run/start_game.sh'
            start.write_text('#!/bin/bash\ncp /tmp/$channel_name.cfg /home/neople/game/cfg/$channel_name.cfg\n')
            conf = root / 'channel.conf'
            original = ('[program:channel]\ncommand=/data/run/start_channel.sh\n'
                        '[program:game_siroco52]\ncommand=/data/run/start_game.sh 52 5\n'
                        '[group:dnf_channel]\nprograms=channel,game_siroco52\n')
            conf.write_text(original)
            with patch.multiple(module, DATA=str(data), ROOT=str(data / 'channel-manager'), CONF=str(conf), START=str(start)), \
                 patch.dict(os.environ, {'SERVER_GROUP': '9', 'SERVER_GROUP_DB': 'cain'}, clear=False):
                with self.assertRaises(SystemExit):
                    module.prepare()
            self.assertEqual(original, conf.read_text())
            self.assertFalse((data / 'channel-manager/initialized').exists())


if __name__ == '__main__':
    unittest.main()
