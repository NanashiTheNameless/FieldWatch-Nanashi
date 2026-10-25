import gzip
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from prepare_data_assets import CONFIG_NAME, UPSTREAM_FILES, prepare, validate_export
from publish_data_assets import ASSETS, publish

CATALOG = json.dumps({"format": "fieldwatch-signatures", "fleets": [{"id": "test"}]}).encode()
SENSORS = gzip.compress(json.dumps({"type": "FeatureCollection", "features": [
    {"type": "Feature", "geometry": {"type": "Point", "coordinates": [0, 0]}, "properties": {}}
]}).encode())
NOTICE = b"OpenStreetMap contributors; https://opendatacommons.org/licenses/odbl/1-0/"
CONFIG = json.dumps({"id": "dev.namelessnanashi.fieldwatch", "additionalSettings": "{}"}).encode()
EXPORTS = dict(zip(UPSTREAM_FILES, (CATALOG, NOTICE, SENSORS)))


class DataAssetsTest(unittest.TestCase):
    def test_prepares_consistent_snapshot_with_app_named_config(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            (directory / "fieldwatch-signatures-v2.json").write_bytes(CATALOG)
            config = directory / "config-source.json"
            config.write_bytes(CONFIG)
            urls = []
            def fetch(url):
                urls.append(url)
                return EXPORTS[url.rsplit('/', 1)[-1]]
            prepare(directory, "a" * 40, fetch, config)
            self.assertEqual((directory / CONFIG_NAME).read_bytes(), CONFIG)
            self.assertEqual(len(urls), 3)
            self.assertTrue(all('/' + 'a' * 40 + '/dist/' in url for url in urls))
            for name, contents in EXPORTS.items():
                self.assertEqual((directory / name).read_bytes(), contents)

    def test_bad_final_download_does_not_replace_any_previous_exports(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            (directory / "fieldwatch-signatures-v2.json").write_bytes(CATALOG)
            for name in UPSTREAM_FILES:
                (directory / name).write_bytes(b"previous")
            config = directory / "config-source.json"
            config.write_bytes(CONFIG)
            def fetch(url):
                name = url.rsplit('/', 1)[-1]
                return gzip.compress(b'{}') if name.endswith('.gz') else EXPORTS[name]
            with self.assertRaises(ValueError):
                prepare(directory, 'b' * 40, fetch, config)
            for name in UPSTREAM_FILES:
                self.assertEqual((directory / name).read_bytes(), b"previous")
            self.assertFalse((directory / CONFIG_NAME).exists())

    def test_rejects_missing_attribution_and_unpinned_upstream(self):
        with self.assertRaises(ValueError):
            validate_export('fieldwatch-sensors.txt', b'No license here')
        with self.assertRaises(ValueError):
            prepare(Path('/unused'), 'main')

    def test_publish_skips_unchanged_assets_and_preserves_data_release_flags(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            contents = {"fieldwatch-signatures-v2.json": CATALOG, **EXPORTS, CONFIG_NAME: CONFIG}
            for name, data in contents.items():
                (directory / name).write_bytes(data)
            release = {"assets": [{"name": n, "digest": "sha256:" + hashlib.sha256(d).hexdigest()}
                                  for n, d in contents.items() if n != CONFIG_NAME]}
            calls = []
            def run(*args):
                calls.append(args)
                return json.dumps(release) if args[0] == 'api' else ''
            publish(directory, 'owner/repo', run)
            uploads = [args for args in calls if args[:2] == ('release', 'upload')]
            self.assertEqual(len(uploads), 1)
            self.assertEqual(uploads[0][2], 'Data-Assets')
            self.assertEqual(Path(uploads[0][-1]).name, CONFIG_NAME)
            self.assertIn('--latest=false', calls[-1])
            self.assertIn('--prerelease=false', calls[-1])
            self.assertFalse(any('Nightly-Rolling' in args for args in calls))

    def test_invalid_publication_never_contacts_github(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            for name in ASSETS:
                (directory / name).write_bytes(b'{}')
            run = Mock()
            with self.assertRaises(ValueError):
                publish(directory, 'owner/repo', run)
            run.assert_not_called()


if __name__ == '__main__':
    unittest.main()
