import hashlib
import tempfile
import unittest
from pathlib import Path

from src.download import SourceSpec, ensure_source


class DownloadTests(unittest.TestCase):
    def test_force_download_and_verified_cache(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            origin = root / "origin.bin"
            origin.write_bytes(b"official bytes")
            expected = hashlib.sha256(origin.read_bytes()).hexdigest()
            spec = SourceSpec(
                key="fixture",
                source_page="https://example.invalid/source",
                download_url=origin.as_uri(),
                filename="cached.bin",
                expected_sha256=expected,
                citation="test fixture",
            )
            raw = root / "raw"
            path, _, cached = ensure_source(
                spec, raw, force_download=True, previous_manifest=None
            )
            self.assertFalse(cached)
            self.assertEqual(path.read_bytes(), b"official bytes")
            _, _, cached = ensure_source(
                spec, raw, force_download=False, previous_manifest=None
            )
            self.assertTrue(cached)


if __name__ == "__main__":
    unittest.main()
