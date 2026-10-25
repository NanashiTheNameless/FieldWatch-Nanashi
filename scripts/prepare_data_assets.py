#!/usr/bin/env python3
"""Validate the fork catalog, then collect optional exports from one upstream commit."""
import argparse
import gzip
import io
import json
from pathlib import Path
import re
import urllib.request

ROOT = Path(__file__).resolve().parent.parent
CONFIG_NAME = "FieldWatch-Nanashi-obtainium.json"
UPSTREAM_FILES = ("fieldwatch-signatures.json", "fieldwatch-sensors.txt", "fieldwatch-sensors.geojson.gz")
MAX_DOWNLOAD = 16 * 1024 * 1024
MAX_EXPANDED = 64 * 1024 * 1024


def validate_catalog(data):
    catalog = json.loads(data)
    if not isinstance(catalog, dict) or catalog.get("format") != "fieldwatch-signatures" or not isinstance(catalog.get("fleets"), list) or not catalog["fleets"]:
        raise ValueError("Missing or invalid signature catalog")
    return catalog


def validate_export(name, data):
    if not data or len(data) > MAX_DOWNLOAD:
        raise ValueError(f"Empty or oversized export: {name}")
    if name.endswith(".json"):
        validate_catalog(data)
    elif name.endswith(".gz"):
        with gzip.GzipFile(fileobj=io.BytesIO(data)) as compressed:
            expanded = compressed.read(MAX_EXPANDED + 1)
        if len(expanded) > MAX_EXPANDED:
            raise ValueError("Expanded sensor export exceeds limit")
        collection = json.loads(expanded)
        if not isinstance(collection, dict) or collection.get("type") != "FeatureCollection" or not isinstance(collection.get("features"), list) or not collection["features"]:
            raise ValueError("Invalid or empty sensor FeatureCollection")
    else:
        text = data.decode("utf-8").lower()
        if "openstreetmap" not in text or "opendatacommons.org/licenses/odbl" not in text:
            raise ValueError("Sensor export is missing its attribution/license notice")


def download(url):
    request = urllib.request.Request(url, headers={"User-Agent": "FieldWatch-Nanashi data updater"})
    with urllib.request.urlopen(request, timeout=60) as response:
        data = response.read(MAX_DOWNLOAD + 1)
    if len(data) > MAX_DOWNLOAD:
        raise ValueError("Export download exceeds limit")
    return data


def prepare(directory, upstream_commit, fetch=download, config_path=None):
    if not re.fullmatch(r"[0-9a-f]{40}", upstream_commit):
        raise ValueError("Use a full upstream commit SHA to keep exports consistent")
    directory = Path(directory)
    validate_catalog((directory / "fieldwatch-signatures-v2.json").read_bytes())
    config_data = (config_path or ROOT / "docs" / CONFIG_NAME).read_bytes()
    config = json.loads(config_data)
    if config.get("id") != "dev.namelessnanashi.fieldwatch":
        raise ValueError("Wrong Obtainium app ID")
    json.loads(config["additionalSettings"])
    pending = {}
    for name in UPSTREAM_FILES:
        url = f"https://raw.githubusercontent.com/OffGridPete/Fieldwatch/{upstream_commit}/dist/{name}"
        data = fetch(url)
        validate_export(name, data)
        pending[name] = data
    # No source file is replaced until every download has passed validation.
    for name, data in pending.items():
        (directory / name).write_bytes(data)
    (directory / CONFIG_NAME).write_bytes(config_data)
    print(f"Prepared five data assets; upstream snapshot {upstream_commit}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--upstream-commit", required=True)
    args = parser.parse_args()
    prepare(args.directory, args.upstream_commit)
