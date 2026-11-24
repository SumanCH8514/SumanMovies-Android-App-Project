# SumanMovies 🎬✨

<div align="center">

![SumanMovies Banner](Android-Project/SumanMovies_Android_Assets/splash_1080x1920.png)

**A modern, production-grade Android streaming & download application built with Jetpack Compose, Material 3, and Kotlin Coroutines.**

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg?style=flat&logo=android)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.10.00-blue.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![ExoPlayer](https://img.shields.io/badge/Media3%20ExoPlayer-1.4.1-red.svg?style=flat)](https://developer.android.com/guide/topics/media/media3)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

[**📥 Download Latest APK**](#-direct-apk-download) • [**✨ Key Features**](#-key-features) • [**🏗️ Architecture**](#%EF%B8%8F-architecture) • [**🚀 Getting Started**](#-getting-started)

</div>

---

## 📥 Direct APK Download

Ready-to-install APK artifacts built with complete hardware acceleration, multi-provider resolvers, and the offline download manager:

- **Release Build (v1.1.0):** [**`SumanMovies-v1.1.0-release-unsigned.apk`**](apk/SumanMovies-v1.1.0-release-unsigned.apk) (Located in [`apk/`](apk/))
- **Debug Build (v1.1.0):** [**`SumanMovies-v1.1.0-debug.apk`**](apk/SumanMovies-v1.1.0-debug.apk)
- **Version:** `v1.1.0` (Build `2`)
- **Architecture:** `arm64-v8a`, `armeabi-v7a`, `x86_64`
- **Minimum OS:** Android 8.0 (API Level 26) or higher
- **Target OS:** Android 15 (API Level 35)

---

## ✨ Key Features

### 1. Multi-Provider Streaming Engine
- **MovieBox Integration:** Encrypted DASH/HLS streams, trending catalogs, multilingual metadata.
- **Play4U Integration:** Lossless 1080p HLS playback with audio track switcher.
- **HDHub4U & 4KHub4U:** High-efficiency bypass and direct download resolution for 4K UHD, 1080p, and 720p sources.
- **IMAX Badge & Filtering:** Dynamic quality tag resolution (IMAX, 4K, 1080p FHD, 720p HD).

### 2. Production-Grade Download Manager
- **Foreground / Background Execution:** Reliable chunked media downloads with live pause, resume, and cancellation.
- **Real-Time Metrics:** Live throughput speed (MB/s), downloaded vs total bytes, and animated progress percentages.
- **Artwork Playback & External Launch:** Instant inline playback directly from the download card or launch in external players (VLC, MX Player, etc.) via secure Android `FileProvider`.
- **Database Persistence:** Powered by Room (`DownloadDao`) for persistent offline state and error recovery.
- **Interactive Badges:** Dynamic badge counts on the bottom navigation bar alerting active and pending downloads.

### 3. Media3 ExoPlayer Engine
- High-efficiency hardware-accelerated streaming with adaptive bitrate switching.
- Aspect ratio scaling (Fit, Crop, Fill, 16:9, 21:9).
- Subtitle rendering and multi-track audio stream selector.
- Double-tap seek gestures, brightness/volume swipe gestures, and screen orientation locks.

### 4. App Customization & Dynamic Theming
- **Appearance Modes:** Real-time switching between **Device Theme**, **Dark Mode**, and clean bright **Light Mode** with dynamic status bar and navigation bar contrast.
- **Home Category Tab Styles:** Choose between **Segmented Control** (pill tabs directly below hero banner) or **Top Glass Bar** (glassmorphic top navigation).
- **Accent Palettes:** 7 selectable accent themes (Royal Indigo, Crimson Red, Sunset Gold, Neon Purple, Electric Cyan, Emerald Mint, Mono).
- **Official Web Companion:** Seamlessly linked to the official web client at [**v1.cinema.sumanonline.com**](https://v1.cinema.sumanonline.com/).

### 5. Emil Kowalski Design-Engineering UI/UX
- **8-Pixel Strict Spatial System:** Clean vertical rhythm and consistent element hierarchy.
- **Calm Tinted Neutrals:** Eliminates harsh neon gradients in favor of refined `#131926` card surfaces on `#090D16` backdrops.
- **Focal Point Discipline:** Dedicated Search & Discovery workflow with real persistent search history, individual entry deletion, and one-tap history wipe.

### 6. Backend Serverless Proxy
- Cloudflare Workers microservice located in [`Backend-Project/`](Backend-Project/) responsible for token signing, API aggregation, and CORS/link resolution.

---

## 🏗️ Architecture & Tech Stack

```
com.sumanonline.sumanmovies
├── data
│   ├── download       # AppDownloadManager & background workers
│   ├── local          # Room DB, DAOs (Downloads, Favorites, Watch History)
│   ├── models         # Domain & DTO media models
│   ├── remote         # API clients (TMDB, MovieBox, Play4U, 4KHub4U, HDHub4U)
│   └── repository     # MediaRepository, SearchHistoryManager, PlaybackResolver
├── ui
│   ├── components     # Reusable Compose UI elements (PosterCards, GlassBars)
│   ├── navigation     # Navigation3 & Jetpack Compose Navigation
│   ├── screens
│   │   ├── activity   # User history & activity feed
│   │   ├── catalog    # Search & discovery with real history
│   │   ├── details    # Movie/Show details, seasons & download modal
│   │   ├── home       # Trending carousel & category rows
│   │   ├── library    # Saved favorites & download manager screen
│   │   └── player     # ExoPlayer UI with gesture overlays
│   └── theme          # Color, Type, Shape & AppAccent systems
```

- **Language:** Kotlin 2.0.21
- **UI Framework:** Jetpack Compose (BOM 2024.10.00) + Material 3
- **Async & Reactive:** Kotlin Coroutines & StateFlow
- **Image Loading:** Coil Compose
- **Media Engine:** AndroidX Media3 ExoPlayer 1.4.1
- **Local Storage:** Android Jetpack Room 2.6.1 & SharedPreferences
- **Networking:** OkHttp 4.12.0 & Retrofit 2.11.0 with KotlinX Serialization

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio:** Ladybug (2024.2.1) or newer
- **JDK:** Java 17
- **Android SDK:** Platform 35, Build-Tools 35.0.0

### Build & Run
1. Clone the repository:
   ```bash
   git clone https://github.com/SumanCH8514/SumanMovies-Android-App-Project.git
   cd SumanMovies-Android-App-Project/Android-Project
   ```
2. Open the `Android-Project` directory in Android Studio.
3. Sync Gradle and build the project:
   ```bash
   ./gradlew assembleDebug
   ```
4. Deploy to your connected device or emulator:
   ```bash
   ./gradlew installDebug
   ```

---

## 📄 License
This project is for educational and personal research purposes only. All movie metadata and artwork are provided by TMDB and their respective rights holders.
