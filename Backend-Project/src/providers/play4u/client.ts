import { CatalogItem, MediaDetails, ReleaseStream, SubtitleOption } from "../../types";

const DEFAULT_BASE_URL = "https://play4u.org";
const SEARCH_API_BASE = "https://play4u.org/api/search.php";
const DEFAULT_SEAL_KEY = "pa4u-stream-seal-v1";
const MOBILE_USER_AGENT =
  "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

const STREAM_HEADERS: Record<string, string> = {
  "User-Agent": MOBILE_USER_AGENT,
  "Referer": "https://play4u.org/",
  "Origin": "https://play4u.org",
};

export function unseal(sealed: string, key = DEFAULT_SEAL_KEY): string {
  if (!sealed || !sealed.startsWith("pa!~")) return sealed || "";
  try {
    const raw = sealed.slice(4);
    const b64 = raw.replace(/-/g, "+").replace(/_/g, "/");
    const pad = (4 - (b64.length % 4)) % 4;
    const padded = pad > 0 ? b64 + "=".repeat(pad) : b64;
    const binary = atob(padded);
    const keyBytes = new TextEncoder().encode(key);
    let result = "";
    for (let i = 0; i < binary.length; i++) {
      const b = binary.charCodeAt(i);
      const k = keyBytes[i % keyBytes.length];
      result += String.fromCharCode(b ^ k);
    }
    return result;
  } catch {
    return "";
  }
}

export function cleanPlay4uSlug(raw: string): string {
  let s = raw.replace(/^play4u:/i, "").trim();
  if (s.startsWith("http://") || s.startsWith("https://")) {
    try {
      const parsed = new URL(s);
      s = parsed.pathname;
    } catch {
      s = s.replace(/^https?:\/\/[^/]+/i, "");
    }
  }
  s = s.replace(/^\/+/g, "");
  s = s.replace(/^(series|watch)\//i, "");
  s = s.replace(/^series-/i, "");
  s = s.replace(/\/s-\d+\/ep-\d+.*$/i, "");
  s = s.replace(/\/s\d+e\d+.*$/i, "");
  s = s.replace(/-s\d+e\d+.*$/i, "");
  return s.replace(/\/+$/g, "").trim();
}

export class Play4UClient {
  private baseUrl: string;

  constructor(baseUrl = DEFAULT_BASE_URL) {
    this.baseUrl = baseUrl.replace(/\/+$/g, "");
  }

  public async search(query: string, page = 1): Promise<CatalogItem[]> {
    const trimmed = query.trim();
    if (!trimmed) return [];

    const apiResults = await this.searchViaApi(trimmed, page);
    if (apiResults.length > 0) {
      return apiResults;
    }

    return this.searchViaScrape(trimmed);
  }

  private async searchViaApi(query: string, page = 1): Promise<CatalogItem[]> {
    try {
      const targetUrl = `${SEARCH_API_BASE}?q=${encodeURIComponent(query)}&per_page=24&page=${Math.max(1, page)}`;
      const res = await fetch(targetUrl, {
        headers: {
          "User-Agent": MOBILE_USER_AGENT,
          "Accept": "application/json, text/plain, */*",
          "Referer": `${this.baseUrl}/`,
        },
      });

      if (!res.ok) return [];
      const json: any = await res.json();
      const items = json?.items;
      if (!Array.isArray(items) || items.length === 0) return [];

      const catalogItems: CatalogItem[] = [];
      for (const obj of items) {
        const title = obj?.title;
        if (!title || typeof title !== "string") continue;

        const imdbId = obj?.imdb_id || obj?.imdb;
        const tmdbId = obj?.tmdb_id;
        const id = obj?.id;
        const yearRegex = /\b(19\d{2}|20\d{2})\b/;
        const year = obj?.year ? String(obj.year) : (yearRegex.exec(title)?.[1] || undefined);
        const posterUrl = obj?.poster || obj?.poster_url || obj?.poster_sm || undefined;

        const mediaTypeStr = String(obj?.media_type || obj?.type || "").toLowerCase();
        const isSeries =
          mediaTypeStr.includes("series") ||
          mediaTypeStr.includes("tv") ||
          /season\s*\d+|s0\d+/i.test(title);

        const watchPath = typeof obj?.watch === "string" ? cleanPlay4uSlug(obj.watch) : "";
        const effectiveId = watchPath
          ? `play4u:${watchPath}`
          : imdbId
          ? `play4u:${imdbId}`
          : id
          ? `play4u:${id}`
          : tmdbId
          ? `play4u:${tmdbId}`
          : `play4u:${encodeURIComponent(cleanPlay4uSlug(title))}`;

        catalogItems.push({
          id: effectiveId,
          provider: "play4u",
          title,
          mediaType: isSeries ? "series" : "movie",
          year,
          posterUrl,
        });
      }
      return catalogItems;
    } catch {
      return [];
    }
  }

  private async searchViaScrape(query: string): Promise<CatalogItem[]> {
    try {
      const targetUrl = `${this.baseUrl}/?s=${encodeURIComponent(query)}`;
      const res = await fetch(targetUrl, {
        headers: {
          "User-Agent": MOBILE_USER_AGENT,
          "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
          "Referer": `${this.baseUrl}/`,
        },
      });

      if (!res.ok) return [];
      const html = await res.text();
      return this.parseCatalogHtml(html);
    } catch {
      return [];
    }
  }

  private parseCatalogHtml(html: string): CatalogItem[] {
    const items: CatalogItem[] = [];
    const itemRegex = /<a[^>]+href=["'](https?:\/\/[^"']+\/(?:watch|series)\/[^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi;
    let match: RegExpExecArray | null;

    while ((match = itemRegex.exec(html)) !== null) {
      const href = match[1];
      const inner = match[2];

      const titleMatch =
        inner.match(/class=["'][^"']*title[^"']*["'][^>]*>([^<]+)/i) ||
        inner.match(/alt=["']([^"']+)["']/i) ||
        inner.match(/<h\d[^>]*>([^<]+)<\/h\d>/i);
      const rawTitle = titleMatch ? titleMatch[1].trim() : "";
      if (!rawTitle) continue;

      const imgMatch = inner.match(/<img[^>]+src=["']([^"']+)["']/i);
      const posterUrl = imgMatch ? imgMatch[1] : undefined;

      const isSeries = href.includes("/series/") || /season\s*\d+|s\d+/i.test(rawTitle);
      const yearMatch = rawTitle.match(/\b(19\d{2}|20\d{2})\b/) || inner.match(/\b(19\d{2}|20\d{2})\b/);
      const year = yearMatch ? yearMatch[1] : undefined;

      const slug = cleanPlay4uSlug(href);
      if (!slug) continue;

      items.push({
        id: `play4u:${slug}`,
        provider: "play4u",
        title: rawTitle,
        mediaType: isSeries ? "series" : "movie",
        year,
        posterUrl,
      });
    }

    return items;
  }

  public async getDetails(id: string): Promise<MediaDetails> {
    const slug = cleanPlay4uSlug(id);
    const isSeriesHint = id.includes("/series/") || id.includes("series");
    const candidateUrls = isSeriesHint
      ? [`${this.baseUrl}/series/${slug}`, `${this.baseUrl}/watch/${slug}`]
      : [`${this.baseUrl}/watch/${slug}`, `${this.baseUrl}/series/${slug}`];

    let html = "";
    let finalUrl = "";
    for (const u of candidateUrls) {
      try {
        const res = await fetch(u, {
          headers: {
            "User-Agent": MOBILE_USER_AGENT,
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Referer": `${this.baseUrl}/`,
          },
        });
        if (res.ok) {
          html = await res.text();
          finalUrl = u;
          break;
        }
      } catch {}
    }

    if (!html && (id.startsWith("tt") || id.startsWith("play4u:tt") || /^\d+$/.test(slug))) {
      const searchItems = await this.search(slug);
      if (searchItems.length > 0) {
        const matchedSlug = cleanPlay4uSlug(searchItems[0].id);
        const retryUrl = searchItems[0].mediaType === "series"
          ? `${this.baseUrl}/series/${matchedSlug}`
          : `${this.baseUrl}/watch/${matchedSlug}`;
        try {
          const res = await fetch(retryUrl, {
            headers: {
              "User-Agent": MOBILE_USER_AGENT,
              "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
              "Referer": `${this.baseUrl}/`,
            },
          });
          if (res.ok) {
            html = await res.text();
            finalUrl = retryUrl;
          }
        } catch {}
      }
    }

    if (!html) {
      throw new Error(`Failed to resolve Play4U details for ${id}`);
    }

    const titleMatch =
      html.match(/<h1[^>]*>([^<]+)<\/h1>/i) ||
      html.match(/<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)["']/i);
    const title = titleMatch ? titleMatch[1].trim() : slug;

    const descMatch =
      html.match(/<meta[^>]+property=["']og:description["'][^>]+content=["']([^"']+)["']/i) ||
      html.match(/<meta[^>]+name=["']description["'][^>]+content=["']([^"']+)["']/i);
    const description = descMatch ? descMatch[1].trim() : undefined;

    const posterMatch =
      html.match(/<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']/i) ||
      html.match(/<img[^>]+class=["'][^"']*poster[^"']*["'][^>]+src=["']([^"']+)["']/i);
    const posterUrl = posterMatch ? posterMatch[1] : undefined;

    const yearMatch = title.match(/\b(19\d{2}|20\d{2})\b/) || html.match(/class=["'][^"']*release-year[^"']*["'][^>]*>([^<]+)/i);
    const year = yearMatch ? yearMatch[1].trim() : undefined;

    const isSeries = finalUrl.includes("/series/") || /season\s*\d+|s0\d+/i.test(title);

    const seasonsMap = new Map<number, number>();
    const epRegex = /href=["'](?:\/series\/[^"']+|https?:\/\/[^"']+\/series\/[^"']+)\/s-(\d+)\/ep-(\d+)["']/gi;
    let epMatch: RegExpExecArray | null;
    while ((epMatch = epRegex.exec(html)) !== null) {
      const sNum = parseInt(epMatch[1], 10);
      const eNum = parseInt(epMatch[2], 10);
      if (sNum > 0 && eNum > 0) {
        const curMax = seasonsMap.get(sNum) || 0;
        if (eNum > curMax) seasonsMap.set(sNum, eNum);
      }
    }

    const seasons = Array.from(seasonsMap.entries())
      .sort((a, b) => a[0] - b[0])
      .map(([seasonNumber, episodeCount]) => ({ seasonNumber, episodeCount }));

    return {
      id: `play4u:${slug}`,
      provider: "play4u",
      title,
      mediaType: isSeries ? "series" : "movie",
      year,
      description,
      posterUrl,
      genres: [],
      seasons,
    };
  }

  public async episodeStreams(
    id: string,
    season = 0,
    episode = 0,
    title?: string,
    year?: string
  ): Promise<{ streams: ReleaseStream[]; subtitles: SubtitleOption[] }> {
    const sPad = season > 0 ? String(season).padStart(2, "0") : "01";
    const ePad = episode > 0 ? String(episode).padStart(2, "0") : "01";
    const isSeries = season > 0 || episode > 0 || id.includes("/series/") || id.includes("series");

    const slug = cleanPlay4uSlug(id);
    const candidateUrls: string[] = [];

    if (isSeries) {
      if (slug && !/^\d+$/.test(slug)) {
        candidateUrls.push(`${this.baseUrl}/series/${slug}/s-${sPad}/ep-${ePad}`);
      }
    } else {
      if (slug && !/^\d+$/.test(slug)) {
        candidateUrls.push(`${this.baseUrl}/watch/${slug}`);
      }
    }

    for (const url of candidateUrls) {
      const res = await this.fetchAndParsePlayback(url);
      if (res.streams.length > 0) {
        return res;
      }
    }

    const querySearch = title ? title.trim() : slug;
    if (querySearch) {
      const searchItems = await this.search(querySearch);
      const matched = this.findBestMatch(searchItems, querySearch, year, season, isSeries);

      if (matched) {
        const matchedSlug = cleanPlay4uSlug(matched.id);
        const resolvedUrl = isSeries
          ? `${this.baseUrl}/series/${matchedSlug}/s-${sPad}/ep-${ePad}`
          : `${this.baseUrl}/watch/${matchedSlug}`;

        const res = await this.fetchAndParsePlayback(resolvedUrl);
        if (res.streams.length > 0) {
          return res;
        }
      }
    }

    return { streams: [], subtitles: [] };
  }

  private async fetchAndParsePlayback(
    targetUrl: string
  ): Promise<{ streams: ReleaseStream[]; subtitles: SubtitleOption[] }> {
    try {
      const res = await fetch(targetUrl, {
        headers: {
          "User-Agent": MOBILE_USER_AGENT,
          "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
          "Referer": `${this.baseUrl}/`,
        },
      });

      if (!res.ok) return { streams: [], subtitles: [] };
      const html = await res.text();
      return this.parsePlaybackPayload(html, targetUrl);
    } catch {
      return { streams: [], subtitles: [] };
    }
  }

  private async parsePlaybackPayload(
    html: string,
    pageUrl: string
  ): Promise<{ streams: ReleaseStream[]; subtitles: SubtitleOption[] }> {
    const playJsonMatch =
      html.match(/window\.__PA_PLAY__\s*=\s*(\{.+?\});/s) ||
      html.match(/window\.__PA_PLAY__\s*=\s*(\{.*?\});\s*(?:window\.|<\/script>)/);

    const sealKeyMatch = html.match(/window\.__PA_SEAL_KEY__\s*=\s*["']([^"']+)["']/);
    const sealKey = sealKeyMatch?.[1]?.trim() || DEFAULT_SEAL_KEY;

    const streams: ReleaseStream[] = [];
    const subtitles: SubtitleOption[] = [];

    if (playJsonMatch) {
      try {
        const root = JSON.parse(playJsonMatch[1]);
        const streamsArr = root?.streams;

        if (Array.isArray(streamsArr)) {
          for (const item of streamsArr) {
            const rawM3u8 = item?.m3u8_url;
            if (!rawM3u8 || typeof rawM3u8 !== "string") continue;

            const unsealed = unseal(rawM3u8, sealKey);
            if (!unsealed) continue;

            const finalUrl = unsealed.startsWith("http")
              ? unsealed
              : unsealed.startsWith("/")
              ? `${this.baseUrl}${unsealed}`
              : `${this.baseUrl}/${unsealed}`;

            const langArr = Array.isArray(item?.languages)
              ? item.languages.map((l: any) => l?.name || "").filter(Boolean)
              : [];
            const langLabel = langArr.length > 0 ? langArr.slice(0, 3).join(", ") : "Multi-Audio";

            streams.push({
              quality: "1080p",
              format: "HLS",
              url: finalUrl,
              label: `Play4U - 1080p (${langLabel}) HLS`,
              directFile: false,
              headers: STREAM_HEADERS,
            });
          }
        }

        if (streams.length === 0) {
          const movieObj = root?.movie || root?.item;
          const rawUrl =
            movieObj?.video_url || movieObj?.m3u8_url || root?.video_url || root?.m3u8_url;

          if (rawUrl && typeof rawUrl === "string") {
            const unsealed = unseal(rawUrl, sealKey);
            if (unsealed) {
              const finalUrl = unsealed.startsWith("http")
                ? unsealed
                : unsealed.startsWith("/")
                ? `${this.baseUrl}${unsealed}`
                : `${this.baseUrl}/${unsealed}`;

              streams.push({
                quality: "1080p",
                format: "HLS",
                url: finalUrl,
                label: "Play4U - 1080p (Multi-Audio) HLS",
                directFile: false,
                headers: STREAM_HEADERS,
              });
            }
          }
        }

        const subsArr = root?.subtitles;
        if (Array.isArray(subsArr)) {
          for (const sub of subsArr) {
            const sUrl = sub?.url;
            if (!sUrl || typeof sUrl !== "string") continue;
            const sLang = sub?.lang || sub?.language || "English";
            subtitles.push({
              language: sLang,
              url: sUrl.startsWith("http") ? sUrl : `${this.baseUrl}/${sUrl.replace(/^\//, "")}`,
            });
          }
        }
      } catch {}
    }

    if (streams.length === 0) {
      const directM3u8Match = html.match(/["'](https?:\/\/[^"'\s]+\.m3u8[^"'\s]*)["']/);
      if (directM3u8Match) {
        streams.push({
          quality: "1080p",
          format: "HLS",
          url: directM3u8Match[1],
          label: "Play4U - 1080p (Multi-Audio) HLS",
          directFile: false,
          headers: STREAM_HEADERS,
        });
      }
    }

    if (streams.length === 0) {
      const sealedMatch = html.match(/pa!~[A-Za-z0-9_\-]+/);
      if (sealedMatch) {
        const unsealed = unseal(sealedMatch[0], sealKey);
        if (unsealed && (unsealed.includes(".m3u8") || unsealed.startsWith("http"))) {
          const finalUrl = unsealed.startsWith("http")
            ? unsealed
            : unsealed.startsWith("/")
            ? `${this.baseUrl}${unsealed}`
            : `${this.baseUrl}/${unsealed}`;

          streams.push({
            quality: "1080p",
            format: "HLS",
            url: finalUrl,
            label: "Play4U - 1080p (Multi-Audio) HLS",
            directFile: false,
            headers: STREAM_HEADERS,
          });
        }
      }
    }

    if (streams.length === 0) {
      const iframeMatch = html.match(/<iframe[^>]+src=["']([^"']+)["']/i);
      if (iframeMatch) {
        const iframeSrc = iframeMatch[1];
        const hlsUrl = await this.extractIframeStreams(iframeSrc, sealKey);
        if (hlsUrl) {
          streams.push({
            quality: "1080p",
            format: "HLS",
            url: hlsUrl,
            label: "Play4U - 1080p (Multi-Audio) HLS",
            directFile: false,
            headers: STREAM_HEADERS,
          });
        }
      }
    }

    const uniqueStreams = streams.filter(
      (item, index, self) => index === self.findIndex((t) => t.url === item.url)
    );

    return { streams: uniqueStreams, subtitles };
  }

  private async extractIframeStreams(iframeUrl: string, sealKey: string): Promise<string | null> {
    try {
      const target = iframeUrl.startsWith("http")
        ? iframeUrl
        : iframeUrl.startsWith("/")
        ? `${this.baseUrl}${iframeUrl}`
        : `${this.baseUrl}/${iframeUrl}`;

      const res = await fetch(target, {
        headers: {
          "User-Agent": MOBILE_USER_AGENT,
          "Referer": `${this.baseUrl}/`,
        },
      });

      if (!res.ok) return null;
      const body = await res.text();

      const directM3u8 = body.match(/["'](https?:\/\/[^"'\s]+\.m3u8[^"'\s]*)["']/);
      if (directM3u8) return directM3u8[1];

      const sealed = body.match(/pa!~[A-Za-z0-9_\-]+/);
      if (sealed) {
        const unsealed = unseal(sealed[0], sealKey);
        if (unsealed.includes(".m3u8")) return unsealed;
      }

      return null;
    } catch {
      return null;
    }
  }

  private findBestMatch(
    items: CatalogItem[],
    targetTitle: string,
    targetYear?: string,
    season = 0,
    isSeries = false
  ): CatalogItem | null {
    if (items.length === 0) return null;

    const norm = (s: string) =>
      s
        .toLowerCase()
        .replace(/[^a-z0-9]/g, " ")
        .replace(/\s+/g, " ")
        .trim();

    const cleanTarget = norm(
      targetTitle
        .replace(/season\s*\d+/gi, "")
        .replace(/s\d+/gi, "")
        .replace(/\b(19\d{2}|20\d{2})\b/g, "")
    );
    const targetTokens = cleanTarget.split(" ").filter((t) => t.length > 2);

    let bestScore = -1;
    let bestItem: CatalogItem | null = null;

    for (const item of items) {
      if (isSeries && item.mediaType !== "series") continue;

      const itemNorm = norm(item.title);
      let score = 0;

      if (cleanTarget.length > 3 && itemNorm.includes(cleanTarget)) {
        score += 50;
      }

      if (targetTokens.length > 0) {
        const matches = targetTokens.filter((t) => itemNorm.includes(t)).length;
        score += Math.round((matches / targetTokens.length) * 30);
      }

      if (targetYear && item.year && targetYear === item.year) {
        score += 20;
      }

      if (score > bestScore && score >= 40) {
        bestScore = score;
        bestItem = item;
      }
    }

    return bestItem;
  }
}