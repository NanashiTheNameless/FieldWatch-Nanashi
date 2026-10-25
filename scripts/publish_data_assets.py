#!/usr/bin/env python3
"""Publish validated data files, preserving unchanged asset IDs and download dates."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

from prepare_data_assets import ROOT, CONFIG_NAME, UPSTREAM_FILES, validate_catalog, validate_export

TAG = "Data-Assets"
ASSETS = ("fieldwatch-signatures-v2.json", *UPSTREAM_FILES, CONFIG_NAME)


def gh(*args):
    return subprocess.run(["gh", *args], check=True, capture_output=True, text=True).stdout


def publish(directory, repo, run=gh):
    directory = Path(directory)
    for name in ASSETS:
        data = (directory / name).read_bytes()
        if name == CONFIG_NAME:
            if json.loads(data).get("id") != "dev.namelessnanashi.fieldwatch":
                raise ValueError("Wrong Obtainium app ID")
        elif name == "fieldwatch-signatures-v2.json":
            validate_catalog(data)
        else:
            validate_export(name, data)
    release = json.loads(run("api", f"repos/{repo}/releases/tags/{TAG}"))
    current = {asset["name"]: asset for asset in release["assets"]}
    for name in ASSETS:
        path = directory / name
        digest = "sha256:" + hashlib.sha256(path.read_bytes()).hexdigest()
        if current.get(name, {}).get("digest") == digest:
            print(f"Unchanged: {name}")
            continue
        run("release", "upload", TAG, "--repo", repo, "--clobber", str(path))
        print(f"Updated: {name}")
    run("release", "edit", TAG, "--repo", repo, "--draft=false", "--prerelease=false", "--latest=false", "--notes-file", str(ROOT / "docs" / "data-assets.md"))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--repo", required=True)
    args = parser.parse_args()
    publish(args.directory, args.repo)
