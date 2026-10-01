# GTIN → WMS QR — Cloud Build

This project is prepared for a cloud Android build using GitHub Actions.
No Android Studio is required to create the APK once the project is uploaded
to GitHub.

## Build from a phone

1. Sign in to GitHub and create a new repository.
2. Upload the **contents of this project folder** (not the outer ZIP folder).
3. Make sure `.github/workflows/build.yml` is uploaded.
4. Open the repository's **Actions** tab.
5. Select **Build Android APK**.
6. Tap **Run workflow**.
7. Wait for the green checkmark.
8. Open the completed workflow run.
9. Under **Artifacts**, download `GTIN-WMS-QR-debug-apk`.
10. Extract the downloaded artifact and install `app-debug.apk` on Android.

## App

- Camera starts automatically on launch.
- Rear camera scans GTIN/barcodes using ML Kit.
- Manual GTIN entry is available as a fallback.
- Catalog is bundled locally: 31,415 products.
- Lookup: `pbarcode_canonical` → `wms_barcode`.
- QR generation is local/offline.
- Save QR and Print are included.
- Yellow + black scanner interface.
- No top navigation tabs.

## Important

The APK produced by this workflow is a debug APK for personal/internal use.
For Play Store distribution, a release signing key and a release workflow should
be added later.
