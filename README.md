# VideoFlow 2.0 (development build)

## Included in this source update
- Simple in-app browser (WebView) with address/search field.
- Light/Dark theme toggle.
- Existing YouTube oEmbed metadata preview and Facebook oEmbed embed preview.
- Direct URL downloads with MIME-type validation for video, audio and subtitle files.
- MP3 download when the supplied direct source is `audio/mpeg`; SRT/VTT download when direct subtitle URLs are supplied.
- DownloadManager history/status screen.

## Not yet implemented
- True pause/resume controls. Android DownloadManager exposes status but no public pause/resume API; a separate HTTP Range-capable engine is needed.
- Multi-quality extraction/selection. The app currently offers Original only; 360p/480p/720p/1080p can be listed only when a permitted source provides separate stream URLs.
- Automatic subtitle discovery, audio extraction/transcoding, and platform stream extraction.
- YouTube/Facebook page URLs are preview-only; no DRM, login, paywall, or platform restriction bypass.

## Build
Push to `main`, or run GitHub Actions > Build VideoFlow APK > Run workflow.

Package: `com.example.videoflow`
