# Drone Frame Extractor

A minimal offline Android app for extracting frames from drone videos. No account, ads, network permission, or third-party SDKs.

## Build from GitHub on your phone

1. Create a new **public or private** GitHub repository (for example `drone-frame-extractor`).
2. Upload the **contents** of this project to the repository, preserving `.github/workflows/build-apk.yml`.
3. Go to **Actions** > **Build Android APK** > **Run workflow** (or push to `main`).
4. Open the successful workflow run and download the artifact named `drone-frame-extractor-apk`.
5. Unzip that artifact on Android and install `app-debug.apk` (Android may ask to allow installation from your browser/file manager).

The APK is a debug build, suitable for personal testing, not a Play Store release. The build requires GitHub Actions to fetch Gradle and Android dependencies. The app itself works offline.

## Output

ZIP with `frames/*.jpg` or `frames/*.png`, `frames.csv`, and `README.txt`. Timestamps are relative to the start of the selected video. Images are exported at decoded video resolution. Frames use Android MediaMetadataRetriever with `OPTION_CLOSEST`; timestamps indicate requested sampling positions and may differ slightly from actual decoded frame presentation timestamps. This app does not perform photogrammetry or extract drone telemetry.

## Limitations

- Processing long 4K clips may take time and consume significant storage and battery.
- Keep the app in the foreground during export; Android may suspend the process if backgrounded.
- The saved ZIP is finalized when extraction completes; cancelling leaves a partial ZIP.
- JPEG quality 95 is a high-quality re-encoding, not a byte-for-byte copy of source frames.
