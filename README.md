# VideoFlow 1.1

Android direct-media downloader and public social-video metadata preview starter.

## Features
- YouTube oEmbed metadata preview: title, channel and thumbnail.
- Public Facebook Video oEmbed detection and embedded player preview.
- Direct HTTP/HTTPS media downloads only after a HEAD Content-Type check.
- Rejects platform page URLs and HTML responses instead of saving them as `.bin`.
- Stores authorized direct media downloads in Movies.

## Important limitations
- YouTube oEmbed provides metadata, not a downloadable media stream. This app does not extract YouTube streams or bypass platform restrictions.
- Meta's current oEmbed response no longer includes author or thumbnail fields. The app uses the official embedded player HTML where available.
- Facebook/YouTube platform URLs are preview-only. Download is available only for direct media URLs the user is authorized to save.
- Quality selection is not fabricated: separate qualities can only be shown when a permitted source explicitly supplies them. A direct URL downloads the original resource.

## Build
Use GitHub Actions > Build VideoFlow APK > Run workflow, or push to `main`.

Package: `com.example.videoflow`
