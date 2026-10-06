# Android Auto Service dumps (v2.6 CLUSTER)

Compared 2026-09-11. CLUSTER video is **not** proven on the car.

| Dump | Path | Size | MD5 |
|---|---|---|---|
| Vendor (Dropbox `/Haval/androidauto-apks/AndroidAutoService_vendor.apk`, 2026-09-07) | `scripts/.build/aa-service-dumps/AndroidAutoService_vendor.apk` | 2,423,318 (zip) | `48FFDED64E9B485521E3174DCD70DB27` |
| Bundled Impulse asset | `app/src/main/assets/aa_patches/AndroidAutoService.apk` | 6,490,581 | `54DF14713BF26466AF55A76382A67CE6` |

`48ff…` is the stock vendor hash recorded in `deploy_android_auto_service_stock48ff.sh`. The zip is smaller because DEX is stored compressed (`classes.dex` ~6.3 MB uncompressed). `54df…` is the current Impulse staging Service APK (slightly larger DEX).

Both DEX trees contain the GAL APIs needed for a second stream:

- `GalIntegration.registerCarService`
- `Lcom/google/android/projection/protocol/VideoSink;` (`setDisplayIdAndType`, `setSurface`)
- `Lcom/google/android/projection/protocol/InputSource;`
- `DISPLAY_TYPE_CLUSTER`
- `NavigationStatus` / `NavigationStatusListener`
- `InstrumentClusterListener`

Patch baseline for v2.6: decode **stock `48ff`** (or a fresh car pull), apply `patch_android_auto_service_cluster.py`, then re-apply pause-guard if still required. Do not treat `54df` as stock.

The 2026-06-24 `stock48ff` **deploy candidate** (`27c625…`) is unrelated and known-bad (SSL client cert). That is not this vendor dump.

Do not bundle a rebuilt CLUSTER Service APK until two settled on-car fps runs confirm MAIN did not collapse.
