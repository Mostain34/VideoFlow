# VideoFlow

A minimal Android direct-media downloader starter using Kotlin, Jetpack Compose Material 3 and Android DownloadManager.

## Scope
- Downloads direct HTTP/HTTPS media-file URLs that the user is authorized to save.
- Does not extract platform page URLs, bypass DRM, login restrictions, or platform download controls.
- `DownloadManager` stores files in the public Movies folder.

## Build
The included GitHub Actions workflow builds a debug APK on pushes to `main` or manually through Actions > Build VideoFlow APK > Run workflow.

APK artifact: `app-debug.apk`.

## Package
`com.example.videoflow`
