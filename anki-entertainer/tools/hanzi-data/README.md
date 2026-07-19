# Hanzi metadata build pipeline

Builds `app/src/main/assets/databases/hanzi_metadata.db` from pinned sources in `sources.lock.json`.

```bash
# From anki-entertainer/
python3 tools/hanzi-data/build.py
python3 -m unittest discover -s tools/hanzi-data/tests -v
```

See [docs/HANZI_DATA.md](../../docs/HANZI_DATA.md).
