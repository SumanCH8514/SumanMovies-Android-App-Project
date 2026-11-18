import { CatalogItem, MediaDetails, ReleaseStream, SubtitleOption } from "../../types";
import { resolveHubCloud } from "./hubcloud";

const DEFAULT_BASE_URL = "https://4khdhub.one/";
const BROWSER_UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36";

export class FourKHdHubClient {
  private baseUrl: string;

  constructor(baseUrl = DEFAULT_BASE_URL) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl : `${baseUrl}/`;
  }

  public async search(query: string): Promise<CatalogItem[]> {
    const url = `${this.baseUrl}?s=${encodeURIComponent(query)}`;
    const resp = await fetch(url, {
      headers: {
        "User-Agent": BROWSER_UA,
      },
    });
    if (!resp.ok) return [];

    const html = await resp.text();
    const items: CatalogItem[] = [];
    const cardRegex = /<a[^>]+href=["']([^"']+)["'][^>]*class=["'][^"']*movie-card[^"']*["'][^>]*>([\s\S]*?)<\/a>/gi;
    let match: RegExpExecArray | null;

    while ((match = cardRegex.exec(html)) !== null) {
      const rawHref = match[1];
      const cardHtml = match[2];

      const pageUrl = rawHref.startsWith("http") ? rawHref : new URL(rawHref, this.baseUrl).toString();
      const titleMatch = cardHtml.match(/class=["'][^"']*movie-card-title[^"']*["'][^>]*>([^<]+)/i);
      const rawTitle = titleMatch ? titleMatch[1].trim() : "";
      if (!rawTitle) continue;

      const metaMatch = cardHtml.match(/class=["'][^"']*movie-card-meta[^"']*["'][^>]*>([^<]+)/i);
      const metaText = metaMatch ? metaMatch[1].trim() : "";
      const yearMatch = metaText.match(/\b(19\d{2}|20\d{2})\b/) || rawTitle.match(/\b(19\d{2}|20\d{2})\b/);
      const year = yearMatch ? yearMatch[1] : undefined;

      const imgMatch = cardHtml.match(/<img[^>]+src=["']([^"']+)["']/i);
      const posterUrl = imgMatch ? imgMatch[1] : undefined;

      const isSeries = /season|s\d+|complete|episode/i.test(rawTitle) || rawHref.includes("-series-");

      items.push({
        id: encodeURIComponent(pageUrl),
        provider: "fourkhdhub",
        title: rawTitle,
        mediaType: isSeries ? "series" : "movie",
        year,
        posterUrl,
      });
    }

    return items;
  }

  public async getDetails(encodedUrl: string): Promise<MediaDetails> {
    const pageUrl = decodeURIComponent(encodedUrl);
    const resp = await fetch(pageUrl, {
      headers: {
        "User-Agent": BROWSER_UA,
      },
    });
    if (!resp.ok) {
      throw new Error(`4KHDHub page fetch failed: ${resp.status}`);
    }

    const html = await resp.text();
    const titleMatch = html.match(/<h1[^>]*>([\s\S]*?)<\/h1>/i);
    const title = titleMatch ? titleMatch[1].replace(/<[^>]+>/g, "").trim() : "Unknown Title";

    const imgMatch = html.match(/<img[^>]+(?:src|data-src)=["'](https?:\/\/[^"']+)["']/i);
    const posterUrl = imgMatch ? imgMatch[1] : undefined;

    const descMatch = html.match(/<p><strong>Storyline:?<\/strong>([\s\S]*?)<\/p>/i);
    const description = descMatch ? descMatch[1].replace(/<[^>]+>/g, "").trim() : undefined;

    const isSeries = /season|s\d+|series|episodes/i.test(title);
    const yearMatch = title.match(/\b(19\d{2}|20\d{2})\b/);
    const year = yearMatch ? yearMatch[1] : undefined;

    return {
      id: encodedUrl,
      provider: "fourkhdhub",
      title,
      mediaType: isSeries ? "series" : "movie",
      year,
      description,
      posterUrl,
      genres: ["4K UHD", "HDR", "HEVC"],
      seasons: isSeries ? [{ seasonNumber: 1, episodeCount: 10 }] : [],
    };
  }

  public async episodeStreams(
    encodedUrl: string,
    season = 0,
    episode = 0
  ): Promise<{ streams: ReleaseStream[]; subtitles: SubtitleOption[] }> {
    const pageUrl = decodeURIComponent(encodedUrl);
    const resp = await fetch(pageUrl, {
      headers: {
        "User-Agent": BROWSER_UA,
      },
    });
    if (!resp.ok) return { streams: [], subtitles: [] };

    const html = await resp.text();
    const lockerRegex = /href=["'](https?:\/\/[^"']*(?:hubcloud|hubdrive|greenmountmotors|greenmotors)[^"']*)["']/gi;
    const lockerUrls: string[] = [];
    let match: RegExpExecArray | null;

    while ((match = lockerRegex.exec(html)) !== null) {
      const u = match[1];
      if (!lockerUrls.includes(u)) {
        lockerUrls.push(u);
      }
    }

    const streamResults: ReleaseStream[] = [];
    const resolverTasks = lockerUrls.slice(0, 4).map((url) => resolveHubCloud(url));
    const settled = await Promise.allSettled(resolverTasks);

    for (const res of settled) {
      if (res.status === "fulfilled") {
        streamResults.push(...res.value);
      }
    }

    return { streams: streamResults, subtitles: [] };
  }
}

export { FourKHdHubClient as FourKHDHubClient };
