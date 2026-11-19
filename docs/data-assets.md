# Data assets

[Data-Assets release](https://github.com/NanashiTheNameless/FieldWatch-Nanashi/releases/tag/Data-Assets)

This is a regular release, explicitly **not latest** and **not a pre-release**. Its tag stays fixed; data assets can be refreshed without mixing downloads into the nightly APK release.

| File | Purpose |
|---|---|
| `fieldwatch-signatures.rsi` | Current stock catalog exported from this fork's `DefaultCatalog` in [Radio Signature Interchange](https://github.com/OffGridPete/radio-signature-interchange) format (`radio-signatures`, publisher `fieldwatch`). The app's **Update stock catalog from GitHub** uses this file when **Stock catalog source** is **FieldWatch-Nanashi** and **Stock catalog format** is **RSI** (the default). The data-assets workflow regenerates it after unit tests pass. |
| `fieldwatch-signatures-v2.json` | The same catalog in the older `fieldwatch-signatures` format, used when **Stock catalog format** is **Legacy JSON**, and by fork builds from before RSI support. Regenerated alongside the RSI file. |
| `fieldwatch-signatures.json` | Upstream's legacy-compatible catalog, refreshed from upstream `dist/`. The app does not fetch this mirror; its **Upstream Fieldwatch** source reads upstream's `dist/` directly. |
| `fieldwatch-sensors.geojson.gz` | Optional upstream US sensor-location export: speed cameras, red lights, gunshot detectors, and other cameras. This fork currently does not load it. |
| `fieldwatch-sensors.txt` | Description and OpenStreetMap attribution/license notice accompanying the sensor dataset. |
| `FieldWatch-Nanashi-obtainium.json` | Obtainium import configuration for this app; also attached to the nightly APK release. |

The app includes its stock signatures, so downloading a catalog is optional for normal scanning.

`.github/workflows/data-assets.yml` runs daily at 04:17 UTC, when relevant catalog/configuration sources change on `main`, and through **Actions → Data assets → Run workflow**. It exports this fork's catalog through `build.sh`, downloads the optional upstream files from one pinned upstream commit, validates the JSON, compressed GeoJSON, and attribution notice, and uploads changed assets only. Invalid downloads fail before publication. It requires no APK signing secrets.

The APK workflow publishes its own copy of `FieldWatch-Nanashi-obtainium.json`. The data workflow does not touch APKs or the nightly release, avoiding cross-workflow publication races.

The initial optional exports were preserved from repository commit `aef33decba8526b26ad279344f9ba74370a8a5d3`; subsequent updates use upstream's latest published `dist/` snapshot. Sensor data is © OpenStreetMap contributors under the [Open Database License 1.0](https://opendatacommons.org/licenses/odbl/1-0/); see the accompanying text file and [OpenStreetMap copyright notice](https://www.openstreetmap.org/copyright).

The stable URLs for catalog updates are (RSI, then legacy JSON):

```text
https://github.com/NanashiTheNameless/FieldWatch-Nanashi/releases/download/Data-Assets/fieldwatch-signatures.rsi
https://github.com/NanashiTheNameless/FieldWatch-Nanashi/releases/download/Data-Assets/fieldwatch-signatures-v2.json
```
