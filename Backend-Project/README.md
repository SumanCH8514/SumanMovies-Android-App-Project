# SumanMovies Backend API

Production-ready, serverless streaming backend designed for Cloudflare Workers (Free Plan). This service unifies multiple streaming providers into a single, standardized REST API for the SumanMovies Android application.

---

## Supported Providers & Capabilities

* **MovieBox:** Enterprise mobile streaming backend with reverse-engineered HMAC-MD5 signing, CloudFront/Akamai cookie recovery, and DASH/MP4 resolution.
* **4KHDHub:** High-bitrate 4K UHD and 1080p HEVC releases resolved via multi-hop locker scrapers (HubCloud/PixelDrain) with HTTP seekable range validation.
* **Dramachi:** Asian Drama & Anime cluster with dynamic edge node resolution (`getFile`).
* **Live TV (IPTV):** Extended M3U playlist stream parsing with channel categorization.

---

## API Endpoints

### 1. Health & Documentation
* `GET /` - Root status and API sitemap.
* `GET /api/v1/providers` - Lists all active providers and supported stream formats.

### 2. Search
* `GET /api/v1/search?q={query}` - Searches across all providers in parallel.
* `GET /api/v1/search?q={query}&provider=moviebox` - Searches MovieBox.
* `GET /api/v1/search?q={query}&provider=fourkhdhub` - Searches 4KHDHub.
* `GET /api/v1/search?q={query}&provider=dramachi` - Searches Dramachi.

### 3. Media Metadata & Details
* `GET /api/v1/media/:provider/:id` - Returns unified synopsis, cast, director, rating, poster, and seasons list.

### 4. Stream Resolution
* `GET /api/v1/streams/:provider/:id?season={s}&episode={e}` - Resolves direct playable URLs, necessary headers, formats (DASH/MP4/MKV), and subtitle tracks.

### 5. Live Television
* `GET /api/v1/tv/channels` - Returns parsed live streaming channels.
* `GET /api/v1/tv/channels?category={category}` - Filters channels by group.

---

## Local Development & Deployment

### Prerequisites
* Node.js 18+ or 20+
* npm or pnpm

### 1. Install Dependencies
```bash
cd Backend-Project
npm install
```

### 2. Run Locally with Wrangler
```bash
npm run dev
```
The API will be available at `http://localhost:8787`.

### 3. Deploy to Cloudflare Workers (Free Tier)
```bash
npm run deploy
```
Wrangler will deploy your worker to `https://suman-movies-api.<your-subdomain>.workers.dev`.
