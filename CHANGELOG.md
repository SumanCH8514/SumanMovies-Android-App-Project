# Changelog

All notable changes to the **SumanMovies** Android application project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.1.0] - 2026-11-24

### Overview
SumanMovies `v1.1.0` is a major feature and reliability release introducing live IPTV streaming, dedicated anime providers (AnimeXin, ReAnime), a modern in-player stream and quality selection drawer, a high-performance segmented HLS stream downloader with MP4 remuxing, complete MovieBox stream and dub resolution fixes, Room database v5 migration, and an in-app About Project screen.

### Added
- **Live TV & IPTV Channel Streaming**:
  - Full Live TV screen and catalog with categorized channel grids (Entertainment, Movies, Sports, News, Music).
  - Curated IPTV playlist parser with resilient M3U8 playback.
  - Direct playback via Media3 ExoPlayer with adaptive HLS live window recovery (`ERROR_CODE_BEHIND_LIVE_WINDOW`).
- **Dedicated Anime Providers**:
  - **AnimeXin**: Full catalog browsing, episodic resolution, and multi-source streaming.
  - **ReAnime**: Server aggregation with episode scraping and multi-quality HLS streams.
- **In-Player Stream & Quality Drawer (`PlayerScreenModern.kt`)**:
  - Slide-out bottom/side drawer for instant streaming server switching without exiting the player.
  - Quality selection (1080p FHD, 720p HD, 480p SD, Auto) with active bitrate indicators.
  - In-drawer direct stream downloading trigger.
  - Active audio track and subtitle language switcher.
- **Segmented HLS Downloader with MP4 Remuxing**:
  - Multi-threaded HLS TS segment downloader (`HlsSegmentDownloader.kt`).
  - Native MP4 container remuxer (`MediaRemuxer.kt`) outputting standard seekable MP4 files for downloaded HLS streams.
  - Foreground download service with notification actions (Pause, Resume, Cancel).
- **Dedicated About Project Screen (`AboutProjectScreen.kt`)**:
  - Comprehensive in-app about page with branding, version information (`v1.1.0`, Build 2), release channel, and package metadata.
  - Architectural highlights, open-source licenses, interactive changelog dialog, and legal disclaimer.
  - Direct links to the GitHub repository and the official web portal.
- **Automated APK Copy Tasks**:
  - Added Gradle tasks `:app:copyReleaseApk`, `:app:copyDebugApk`, and `:app:copyAllApks` targeting root `apk/` directory.

### Changed
- **Version Management**:
  - Promoted application to `versionName = "1.1.0"`, `versionCode = 2`.
  - Replaced hardcoded version strings across `SettingsScreen.kt`, `ProfileScreen.kt`, `SettingsViewModel.kt`, `ProfileViewModel.kt`, and `AppUpdateManager.kt` with dynamic `BuildConfig.VERSION_NAME` and `BuildConfig.VERSION_CODE`.
- **MovieBox Provider Reliability & Full Stream Parsing**:
  - Resolved 15-minute preview stream limitation by filtering preview tracks and prioritizing full-length DASH/HLS manifests.
  - Improved multilingual dub and subtitle track matching across visitor login, token rotation, and subject detail API calls.
  - Extended API resolution timeout to 30 seconds for MovieBox to accommodate multi-step token and manifest resolution.
  - Resilient host pool pruning and request signing algorithm updates.
- **Room Database Schema Migration (v5)**:
  - Added `backdropUrl` and `stillUrl` column persistence to `WatchHistoryEntity`.
  - Safe automatic migration ensuring zero watch history data loss.
- **Player Recovery Engine**:
  - Implemented jittered exponential backoff for network reconnections.
  - Added URI-independent cache key generation (`PlayerCacheManager.generateCacheKey`) for reliable video segment caching across ephemeral query tokens.

### Fixed
- Fixed MovieBox series episode and season number parsing from raw title strings.
- Fixed `NumberFormatException` crash during media rating and score parsing.
- Fixed 4K stream source selection on non-HEVC capable hardware devices, automatically falling back to 1080p FHD.
- Fixed subtitle styling and text alignment contrast across light and dark system themes.
- Fixed JVM unit test execution for `PlaybackEngineUnitTest.kt` and `PlayerCacheManager.kt`.

---

## [1.0.0] - 2026-11-19

### Overview
Initial stable release of SumanMovies Android application.

### Features
- **Multi-Provider Media Streaming Engine**:
  - Integration with Play4U, MovieBox, HDHub4U, 4KHub4U, KMDL, and MoviesLeech.
  - Server availability health checks with dynamic fallback.
- **Media3 ExoPlayer Video Engine**:
  - Hardware-accelerated HLS and DASH stream playback.
  - Gesture controls for volume, brightness, and seeking.
  - Dynamic aspect ratio adjustment (Fit, Zoom, Stretch, 16:9, 21:9).
- **Download Manager**:
  - Foreground download service with persistent notification progress.
  - Offline media playback via Android `FileProvider`.
- **Local Persistence & Room Database**:
  - Watch history tracking with playback resumption timestamps.
  - Favorites and bookmarks storage.
- **Design & Theming**:
  - Modern Jetpack Compose UI adhering to Emil Kowalski design engineering principles.
  - 7 selectable accent color palettes and dark/light/system appearance modes.
  - Official web companion integration with `https://v1.cinema.sumanonline.com/`.
