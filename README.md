# SumanMovies

<div align="center">

<img src="https://cdn.photos.sumanonline.com/R29vZ2xl/AVvXsEjOBsZegZgVmfO_IywlUkERPXNlTllsNJUY3YiOACh7O8_5mMkJ6a98DdwvROninbaPBP6P-S0IZKBt_B8f5L8ITG85me3nvVEj4nAiz84QWnFGo-1sgwodJM3W-3ssn7szcItCHV6f7CMXxPtuWbkT3wMK1fQsiNrv60chhOu8HZNgu2AyHxYRSp6LBCk/s400/SumanMovies%20Cinematic%20App%20Icon.png" alt="SumanMovies Cinematic App Icon" width="124" height="124" />

### Modern High-Performance Android Streaming, Live IPTV & Offline Download Platform

An enterprise-grade, modern Android application engineered with **Jetpack Compose**, **Material 3**, **AndroidX Media3 ExoPlayer**, and **Kotlin Coroutines**. Features multi-provider stream resolution, live TV streaming, an intelligent HLS segment downloader with MP4 remuxing, AniList-enriched anime browsing, and customizable UI/UX design engineering.

[![Platform](https://img.shields.io/badge/Platform-Android_8.0+_(API_26+)-3DDC84?style=flat&logo=android&logoColor=white)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target_SDK-35_(Android_15)-blue?style=flat&logo=android&logoColor=white)](https://developer.android.com/about/versions/15)
[![Release](https://img.shields.io/badge/Release-v1.1.0_(Build_2)-E50914?style=flat&logo=github&logoColor=white)](https://github.com/SumanCH8514/SumanMovies-Android-App-Project/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Compose_BOM-2024.11.00-4285F4?style=flat&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Media3 ExoPlayer](https://img.shields.io/badge/Media3_ExoPlayer-1.5.0-FF4081?style=flat&logo=youtube&logoColor=white)](https://developer.android.com/guide/topics/media/media3)
[![Room](https://img.shields.io/badge/Room_DB-2.6.1_(Schema_v5)-F58220?style=flat&logo=sqlite&logoColor=white)](https://developer.android.com/jetpack/androidx/releases/room)

[**📥 Direct APK Downloads**](#-direct-apk-downloads) • [**✨ Features & Capabilities**](#-features--capabilities) • [**🏗️ Architecture & Tech Stack**](#%EF%B8%8F-architecture--technology-stack) • [**📱 Compatibility & Permissions**](#-android-compatibility--permissions) • [**🛠️ Installation & Build**](#%EF%B8%8F-installation--build-instructions) • [**📸 Visual Showcase**](#-screenshots--visual-showcase) • [**📋 Changelog**](#-releases--changelog)

</div>

---

## 📥 Direct APK Downloads

Ready-to-install Android APK artifacts built with complete hardware acceleration, multi-provider resolvers, offline HLS downloading, and adaptive streaming:

| Package Variant | Application Version | Architecture | Minimum OS | File Path |
| :--- | :--- | :--- | :--- | :--- |
| **Release Build** | `v1.1.0` (Build `2`) | `arm64-v8a`, `armeabi-v7a`, `x86_64` | Android 8.0 (API 26) | [**`apk/SumanMovies-v1.1.0-release-unsigned.apk`**](apk/SumanMovies-v1.1.0-release-unsigned.apk) |
| **Debug Build** | `v1.1.0` (Build `2`) | `arm64-v8a`, `armeabi-v7a`, `x86_64` | Android 8.0 (API 26) | [**`apk/SumanMovies-v1.1.0-debug.apk`**](apk/SumanMovies-v1.1.0-debug.apk) |

Automated Gradle copy tasks (`copyReleaseApk`, `copyDebugApk`, `copyAllApks`) are configured directly within `app/build.gradle.kts` to output verified builds to the root `apk/` directory.

---

## 📖 About the Project

**SumanMovies** is a high-performance Android media streaming client and offline manager developed to deliver a seamless, ad-free, and cinema-grade playback experience on mobile devices.

### Problems Solved
- **Fragmented Sources**: Aggregates catalog metadata, multi-server streaming links, and anime episodes into a unified, clean interface with automatic server fallback.
- **Flaky Mobile Networks**: Employs adaptive HLS/DASH streaming with jittered exponential backoff and URI-independent video segment caching to eliminate buffering interruptions.
- **Offline Download Friction**: Solves segmented HLS streaming limitations by downloading TS chunks concurrently in the background and remuxing them into standard, seekable MP4 containers with full media player interoperability.
- **Bloated & Inconsistent UI**: Implements a strict spatial hierarchy based on Emil Kowalski design-engineering principles, offering responsive 60/120fps Jetpack Compose components without lag.

The application utilizes a serverless Cloudflare Workers proxy located in [`Backend-Project/`](Backend-Project/) for token signing, rate-limit shielding, and secure API aggregation.

---

## ✨ Features & Capabilities

### 1. Multi-Provider Streaming Engine
- **Aggregated Media Providers**: Supports streaming resolution from MovieBox, Play4U, AnimeXin, ReAnime, KMDL, MoviesLeech, MoviesMod, HDHub4U, and 4KHub4U.
- **Automated Health Checks & Dynamic Fallback**: Validates server responsiveness before playback, filtering dead endpoints and prioritizing responsive CDNs.
- **Quality & Format Badging**: Dynamic resolution labels (4K UHD, 1080p FHD, 720p HD, 480p SD, and IMAX) with real-time link count indicators.
- **Cloudflare Serverless Proxy**: Employs a TypeScript-based proxy microservice to negotiate tokens, rotate headers, and protect client IP addresses.

### 2. Media3 ExoPlayer Engine with FFmpeg Decoders
- **Hardware-Accelerated Playback**: Powered by AndroidX Media3 1.5.0 with adaptive bitrate switching for HLS and DASH streams.
- **Extended Codec Support**: Integrates `org.jellyfin.media3:media3-ffmpeg-decoder:1.5.0+1` for native decoding of AC3, EAC3, TrueHD, and DTS audio streams.
- **In-Player Stream & Quality Drawer (`PlayerScreenModern.kt`)**: Slide-out panel allowing viewers to switch streaming servers, select resolutions, view links, and trigger downloads on the fly without interrupting playback.
- **Animated Retry State**: Live breathing animation and rotating status indicator allowing one-tap retry when a selected provider has no active streams.
- **Intuitive Touch Controls**:
  - Horizontal swipe on right and left sides for 10-second forward and backward seeking with animated ripple indicators.
  - Middle section toggle for play and pause.
  - Vertical gesture controls for brightness (left side) and volume (right side).
  - Picture-in-Picture (PiP) support with automated activity lifecycle transitions.
  - Dynamic aspect ratio formatting (Fit, Crop, Stretch, 16:9, 21:9).
  - Subtitle selection modal and multi-track audio stream switcher.

### 3. Production-Grade HLS & Direct Download Manager
- **Segmented HLS Downloader (`HlsSegmentDownloader.kt`)**: Concurrently fetches segmented M3U8 TS chunks over HTTP/HTTPS with automated retry and byte-range verification.
- **Native MP4 Container Remuxer (`MediaRemuxer.kt`)**: Remuxes completed TS segments into standard, seekable MP4 files for maximum battery efficiency and universal gallery compatibility.
- **Foreground Service Execution (`DownloadForegroundService.kt`)**: Operates as a persistent Android foreground service with action buttons (Pause, Resume, Cancel) and live notification metrics.
- **Real-Time Throughput Telemetry**: Displays instant download speed (MB/s), downloaded vs total bytes, estimated time remaining, and animated progress percentages.
- **Android `FileProvider` Integration**: Enables offline playback directly within the app or allows launching downloaded videos in external players like VLC or MX Player.
- **Persistent Room Database**: Stores download state, resumed byte offsets, and media metadata across device reboots.

### 4. Live TV & IPTV Channel Streaming
- **Categorized Channel Hub**: Browse live streams sorted into Entertainment, Movies, Sports, News, and Music grids.
- **Resilient M3U8 Live Playback**: Built-in recovery from live window desynchronization (`ERROR_CODE_BEHIND_LIVE_WINDOW`) with automatic timestamp resynchronization.
- **Channel Search & Instant Switching**: Fast channel previewing and quick switching via clean card grids.

### 5. Dedicated Anime Hub (ReAnime & AnimeXin)
- **AniList API Integration**: Fetches detailed metadata, release schedules, cover artwork, relations, and episodic data via GraphQL.
- **Interactive Episode Navigation**: Comprehensive episode picker supporting both compact grid views and rich list views with thumbnails, duration, and SUB/DUB badges.
- **Active Episode Auto-Scroll**: Automatically scrolls to and highlights the currently playing episode when opening the drawer.
- **Dual Engine Options**: Seamlessly toggle between direct ExoPlayer streaming and in-app embedded player view.

### 6. App Theming & Emil Kowalski Design System
- **Appearance Modes**: Real-time switching between Dark Mode, Light Mode, and System Theme with contrast-adaptive system bars.
- **Custom Accent Palettes**: 7 tailored color palettes (Royal Indigo, Crimson Red, Sunset Gold, Neon Purple, Electric Cyan, Emerald Mint, and Mono).
- **Home Navigation Layouts**: Choose between Segmented Controls (compact pills under hero banner) or Top Glass Bar (frosted glassmorphic header).
- **Persistent Search Engine**: Local search history manager with individual query deletion and one-tap history wipe.

---

## 📸 Screenshots & Visual Showcase

<div align="center">

![SumanMovies Cinematic Splash Banner](Android-Project/SumanMovies_Android_Assets/splash_1080x1920.png)

</div>

### Application Screen Showcase

| Home & Discover | Anime Hub & Episodes | Modern Video Player | Quality & Server Drawer |
| :---: | :---: | :---: | :---: |
| *docs/screenshots/home.png* | *docs/screenshots/anime.png* | *docs/screenshots/player.png* | *docs/screenshots/servers.png* |
| Trending catalog, hero banner & category rails | AniList episode grid, metadata & season selector | Gesture overlays, PiP & adaptive playback | Live server switching & resolution selector |

| Live TV & IPTV | Download Manager | Playback Settings | Theming & Accents |
| :---: | :---: | :---: | :---: |
| *docs/screenshots/livetv.png* | *docs/screenshots/downloads.png* | *docs/screenshots/settings.png* | *docs/screenshots/theming.png* |
| Categorized M3U8 live stream directory | Real-time speeds, remuxing & offline queue | Audio switcher, subtitles & playback speeds | 7 accent palettes & light/dark contrast |

> **Adding Screenshots**: Capture device screenshots from an active device or emulator and place them in `docs/screenshots/` matching the filenames above.

---

## 🏗️ Architecture & Technology Stack

SumanMovies is architected following **Clean Architecture** and the **MVVM (Model-View-ViewModel)** design pattern, keeping presentation, domain business rules, and data sources isolated and testable.

```
com.sumanonline.sumanmovies
├── data
│   ├── download
│   │   ├── hls                    # HlsPlaylistParser, HlsSegmentDownloader
│   │   ├── remux                  # MediaRemuxer (TS to MP4 container conversion)
│   │   ├── AppDownloadManager.kt  # Centralized download dispatcher & state engine
│   │   └── DownloadForegroundService.kt # Foreground service & notification actions
│   ├── local
│   │   ├── dao                    # DownloadDao, FavoriteDao, WatchHistoryDao
│   │   ├── entities               # DownloadEntity, FavoriteEntity, WatchHistoryEntity
│   │   ├── AppDatabase.kt         # Room DB v5 with automated schema migrations
│   │   └── AppPreferencesManager.kt # Theme, accent & playback settings (SharedPreferences)
│   ├── models                     # MediaItem, StreamSource, Provider, Episode models
│   ├── remote                     # API clients: TMDB, MovieBox, Play4U, ReAnime,
│   │                              # AnimeXin, KMDL, MoviesMod, MoviesLeech, 4KHub4U
│   └── repository                 # MediaRepository, PlaybackResolver, SearchHistoryManager
├── ui
│   ├── components                 # Reusable Compose UI elements (PosterCards, GlassBars)
│   ├── navigation                 # Screen definitions & Jetpack Navigation Compose graph
│   ├── screens
│   │   ├── about                  # AboutProjectScreen with build metadata & licenses
│   │   ├── animexin               # Dedicated AnimeXin catalog & episode stream viewer
│   │   ├── catalog                # Search & multi-filter discovery with search history
│   │   ├── customization          # Accent palettes & home tab appearance settings
│   │   ├── details                # Movie/Series details, cast list & season drawers
│   │   ├── home                   # Hero banner carousel & dynamic category rows
│   │   ├── library                # Saved bookmarks, watch history & download manager
│   │   ├── livetv                 # Live TV IPTV streaming with category channel grids
│   │   ├── player                 # PlayerScreenModern, gesture handlers & server drawer
│   │   ├── reanime                # ReAnime details, AniList metadata & episode grid
│   │   └── settings               # Player preferences, cache manager & app update check
│   └── theme                      # AppAccent, Material 3 Color Schemes & Typography
└── util                           # Formatting, network state observers & safe extensions
```

### Technology Breakdown

| Category | Technology | Version | Purpose |
| :--- | :--- | :--- | :--- |
| **Language** | Kotlin | `2.0.21` | Modern, null-safe language for Android development |
| **Runtime Target** | Java / JVM | `17` | Compile and target bytecode standard |
| **UI Framework** | Jetpack Compose | `BOM 2024.11.00` | Declarative, reactive UI toolkit for Android |
| **Design System** | Material 3 | `1.3.1` | Modern components, surface tonal palettes, and elevation |
| **Navigation** | Navigation Compose | `2.8.4` | Single-activity screen navigation with type-safe arguments |
| **Media Engine** | AndroidX Media3 ExoPlayer | `1.5.0` | Hardware-accelerated DASH/HLS streaming & cache management |
| **Audio Decoders** | Jellyfin FFmpeg Extension | `1.5.0+1` | Software decoding for AC3, EAC3, TrueHD, and DTS tracks |
| **Local Database** | Jetpack Room | `2.6.1` (KSP) | SQLite persistence for watch history, bookmarks, and downloads |
| **Image Loading** | Coil Compose | `2.7.0` | Asynchronous image loading with memory and disk caching |
| **HTTP Networking** | OkHttp & Retrofit | `4.12.0` / `2.11.0` | REST API requests, custom headers, and logging interceptors |
| **Async & State** | Kotlinx Coroutines | `1.9.0` | Asynchronous flows, StateFlow, and background worker threads |
| **Build System** | Gradle & AGP | `8.7.2` | Dependency management, APK optimization, and copy tasks |
| **Compiler Plugin** | Google KSP | `2.0.21-1.0.27` | Kotlin Symbol Processing for Room code generation |
| **Backend Service** | Cloudflare Workers | TypeScript | API signing, TMDB proxying, and secure link resolution |

---

## 📱 Android Compatibility & Permissions

### System Requirements
- **Minimum SDK**: Android 8.0 (API Level 26 - Oreo)
- **Target SDK**: Android 15 (API Level 35)
- **Compile SDK**: Android 15 (API Level 35)
- **Supported ABIs**: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- **Internet**: Required for online catalog browsing and stream playback

### Permissions Explained

| Permission | Protection Level | Purpose |
| :--- | :--- | :--- |
| `android.permission.INTERNET` | Normal | Connect to streaming servers, API endpoints, and metadata services |
| `android.permission.ACCESS_NETWORK_STATE` | Normal | Monitor Wi-Fi / cellular state to prevent streaming on disconnected networks |
| `android.permission.FOREGROUND_SERVICE` | Normal | Run media download and playback processes reliably in the background |
| `android.permission.FOREGROUND_SERVICE_DATA_SYNC` | Normal | Required on Android 14+ (API 34+) for foreground download data synchronization |
| `android.permission.POST_NOTIFICATIONS` | Runtime (API 33+) | Display active download throughput progress and player controls |
| `android.permission.WAKE_LOCK` | Normal | Prevent CPU sleep during high-bitrate streaming and MP4 remuxing |
| `android.permission.WRITE_EXTERNAL_STORAGE` | Legacy (`maxSdkVersion="28"`) | Compatibility fallback for writing downloads on Android 8.0 and 9.0 |

---

## 🛠️ Installation & Build Instructions

### Prerequisites
- **Android Studio**: Ladybug (2024.2.1) or newer
- **Java Development Kit**: JDK 17 (Eclipse Temurin or OpenJDK recommended)
- **Android SDK**: Build-Tools `35.0.0`, Platform SDK `35`
- **Git**: Installed and configured on your system

### 1. Clone the Repository
```bash
git clone https://github.com/SumanCH8514/SumanMovies-Android-App-Project.git
cd SumanMovies-Android-App-Project/Android-Project
```

### 2. Build Debug APK
Build the debug version using the Gradle wrapper:

- **Linux / macOS**:
  ```bash
  ./gradlew assembleDebug
  ```
- **Windows (Command Prompt / PowerShell)**:
  ```powershell
  .\gradlew.bat assembleDebug
  ```

### 3. Generate Release APK & Copy Artifacts
To assemble the release APK and automatically copy all artifacts into the root `apk/` directory:

```bash
./gradlew copyAllApks
```

Generated APKs will be available at:
- `apk/SumanMovies-v1.1.0-release-unsigned.apk`
- `apk/SumanMovies-v1.1.0-debug.apk`
- `Android-Project/app/build/outputs/apk/debug/app-debug.apk`
- `Android-Project/app/build/outputs/apk/release/app-release-unsigned.apk`

### 4. Run Unit Tests
Verify database migrations, parsing engines, and player cache managers:

```bash
./gradlew testDebugUnitTest
```

---

## 🚀 Application Usage Guide

### Sideloading the APK
1. Download `SumanMovies-v1.1.0-release-unsigned.apk` or `SumanMovies-v1.1.0-debug.apk` from the [`apk/`](apk/) folder or the [GitHub Releases](https://github.com/SumanCH8514/SumanMovies-Android-App-Project/releases) page.
2. Transfer the APK to your Android device or download it directly via your mobile browser.
3. If prompted, grant **Install Unknown Apps** permission for your browser or file manager.
4. Tap the APK file and select **Install**.

### Main Navigation & Features
1. **Home Screen**:
   - Browse trending movies, popular series, and top-rated media rails.
   - Switch between **Segmented Control** and **Top Glass Bar** layouts via App Customization settings.
2. **Search & Catalog**:
   - Tap the search icon to explore by title, actor, or genre.
   - Access recent search history with quick one-tap replay or deletion.
3. **Player Experience**:
   - Tap any title to view details, trailers, and seasons.
   - In the player, open the **Quality & Servers** drawer to switch servers (MovieBox, Play4U, AnimeXin, etc.) or select resolutions (1080p, 720p).
   - Use double-tap gestures to seek 10s forward or backward, or vertical edge swipes to adjust volume and brightness.
   - Tap the **Download** button inside the drawer or details screen to queue the stream for offline access.
4. **Library & Downloads**:
   - Track active downloads with live speed and progress metrics.
   - Pause, resume, or cancel downloads anytime.
   - Play completed files directly or share them via `FileProvider` to external players.
5. **Live TV**:
   - Open the **Live TV** tab to stream live sports, entertainment, and news channels directly.

---

## 📋 Releases & Changelog

- **Latest Release**: [**v1.1.0 (Build 2)**](https://github.com/SumanCH8514/SumanMovies-Android-App-Project/releases)
- **Detailed History**: Refer to [**`CHANGELOG.md`**](CHANGELOG.md) for full release notes and version-by-version migrations.

### Recent Release Highlights (v1.1.0)
- Added full Live TV & IPTV channel streaming with resilient M3U8 playback.
- Integrated dedicated anime providers (**AnimeXin** and **ReAnime**) with AniList metadata enrichment.
- Added in-player stream and quality selection drawer with animated retry states.
- Implemented segmented HLS multi-threaded downloader with native MP4 remuxer.
- Upgraded Room database schema to **v5** with safe automatic migrations.
- Added dynamic versioning via `BuildConfig` and in-app **About Project** screen.

---

## 🔒 Privacy, Permissions & Security

- **No Telemetry or User Tracking**: The application does not bundle analytics trackers, advertising SDKs, or background telemetry services.
- **Client Credential Protection**: API keys, proxy signing tokens, and provider secrets are securely resolved through the backend Cloudflare Workers proxy and are never exposed in client code.
- **Local Storage Isolation**: Downloaded files and databases are securely contained within app-specific storage or shared strictly through verified Android `FileProvider` authorities.
- **Permission Minimalism**: Only permissions strictly necessary for network media streaming, foreground downloads, and media playback notifications are declared.

---

## 🤝 Contributing

Contributions to improve performance, add provider parsers, or enhance the Compose UI are welcome.

1. **Fork the Repository**:
   Click the **Fork** button at the top of the [GitHub repository](https://github.com/SumanCH8514/SumanMovies-Android-App-Project).
2. **Create a Feature Branch**:
   ```bash
   git checkout -b feature/amazing-feature
   ```
3. **Commit Your Changes**:
   ```bash
   git commit -m "feat: add amazing feature"
   ```
4. **Push to Your Branch**:
   ```bash
   git push origin feature/amazing-feature
   ```
5. **Open a Pull Request**:
   Submit your pull request with a detailed description of the changes and testing performed.

---

## ⚖️ License & Legal Disclaimer

This project is developed and maintained for **personal research and educational purposes only**.

- All movie, series, and anime metadata, cover art, and actor profiles are provided via the **TMDB API** and **AniList GraphQL API**, and belong to their respective copyright holders.
- SumanMovies does not host, store, or upload any video files, media streams, or copyrighted digital assets on its own servers. All media streams are dynamically resolved from publicly accessible third-party providers.
- Formal licensing terms for this repository have not yet been published. Please review local laws regarding the use of media aggregation software.

---

## 👨‍💻 Author & Maintainer

- **Developer**: **Suman Ghosh**
- **GitHub**: [@SumanCH8514](https://github.com/SumanCH8514)
- **Repository**: [SumanMovies-Android-App-Project](https://github.com/SumanCH8514/SumanMovies-Android-App-Project)
