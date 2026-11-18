import { CatalogItem, MediaDetails, ReleaseStream, SubtitleOption } from "../../types";

const DEFAULT_BASE_URL = "https://api.nodeobjects.com/";
const IMAGE_CDN_BASE = "https://static.nodeobjects.com/thumbnail/";

export class DramachiClient {
  private baseUrl: string;

  constructor(baseUrl = DEFAULT_BASE_URL) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl : `${baseUrl}/`;
  }

  public async search(query: string, page = 1): Promise<CatalogItem[]> {
    const trimmed = query.trim();
    if (!trimmed) return [];

    const url = `${this.baseUrl}?interface=search&q=${encodeURIComponent(trimmed)}&filter=all&page=${Math.max(1, page)}`;
    const resp = await fetch(url);
    if (!resp.ok) return [];

    const json: any = await resp.json();
    const items = json?.data || [];
    if (!Array.isArray(items)) return [];

    return items.map((item: any) => {
      const content = String(item?.content || "").toLowerCase();
      const isMovie = content === "movie" || content === "movies";
      const thumb = item?.thumb || item?.poster || "";
      const posterUrl = thumb.startsWith("http") ? thumb : thumb ? `${IMAGE_CDN_BASE}${thumb.replace(/^\//, "")}` : undefined;

      return {
        id: String(item?.id || ""),
        provider: "dramachi",
        title: item?.title || "Unknown",
        mediaType: isMovie ? "movie" : "series",
        year: item?.year ? String(item.year) : undefined,
        posterUrl,
      };
    });
  }

  public async getDetails(titleId: string): Promise<MediaDetails> {
    const url = `${this.baseUrl}?interface=title_v2&id=${encodeURIComponent(titleId)}`;
    const resp = await fetch(url);
    if (!resp.ok) {
      throw new Error(`Dramachi details failed: ${resp.status}`);
    }

    const data: any = await resp.json();
    const album = Array.isArray(data?.album) ? data.album[0] : (data?.album || data);
    const title = album?.title || "Unknown";

    const content = String(album?.content || "").toLowerCase();
    const isMovie = content === "movie" || content === "movies";

    const thumb = album?.thumb || album?.poster || "";
    const posterUrl = thumb.startsWith("http") ? thumb : thumb ? `${IMAGE_CDN_BASE}${thumb.replace(/^\//, "")}` : undefined;

    const seasons: { seasonNumber: number; episodeCount: number }[] = [];
    const seasonsMap = data?.seasons;
    if (seasonsMap && typeof seasonsMap === "object") {
      let idx = 1;
      for (const [key, val] of Object.entries(seasonsMap)) {
        const episodes = (val as any)?.episodes;
        const count = Array.isArray(episodes) ? episodes.length : 1;
        seasons.push({
          seasonNumber: idx++,
          episodeCount: count,
        });
      }
    }

    const rawGenres = album?.genre || album?.genres;
    const genres: string[] = Array.isArray(rawGenres)
      ? rawGenres.map((g: any) => String(g))
      : typeof rawGenres === "string"
      ? rawGenres.split(",").map((s) => s.trim())
      : [];

    return {
      id: titleId,
      provider: "dramachi",
      title,
      mediaType: isMovie ? "movie" : "series",
      year: album?.year ? String(album.year) : undefined,
      description: album?.storyline || undefined,
      posterUrl,
      genres,
      seasons,
      director: album?.director || undefined,
    };
  }

  private async fetchFileStream(fid: string, disk: string): Promise<{ url: string; filename: string }> {
    const url = `${this.baseUrl}?interface=getFile&fid=${encodeURIComponent(fid)}&findex=${encodeURIComponent(disk)}`;
    const resp = await fetch(url);
    if (!resp.ok) {
      throw new Error(`Dramachi getFile failed: ${resp.status}`);
    }

    const data: any = await resp.json();
    const hostInfo = data?.hostInfo || data?.host_info;
    const fileInfo = data?.fileInfo?.[0] || data?.file_info?.[0];

    if (!hostInfo?.host || !fileInfo?.url) {
      throw new Error("Missing host_info or file_info in Dramachi response");
    }

    const host = hostInfo.host.replace(/\/+$/, "");
    const filePath = fileInfo.url.replace(/^\/+/, "");
    const streamUrl = `https://${host}/cdn/${filePath}`;
    const filename = fileInfo?.filename || fileInfo?.f_title || `${fid}.mkv`;

    return { url: streamUrl, filename };
  }

  public async episodeStreams(
    id: string,
    season = 1,
    episode = 1
  ): Promise<{ streams: ReleaseStream[]; subtitles: SubtitleOption[] }> {
    const titleDetails = await this.getDetails(id);

    const titleUrl = `${this.baseUrl}?interface=title_v2&id=${encodeURIComponent(id)}`;
    const titleResp = await fetch(titleUrl);
    let seasonKey = "hd Rip";
    if (titleResp.ok) {
      const rawTitleData: any = await titleResp.json().catch(() => null);
      if (rawTitleData?.seasons && typeof rawTitleData.seasons === "object") {
        const keys = Object.keys(rawTitleData.seasons);
        if (keys.length > 0) {
          const selectedKey = keys[season - 1] || keys[0];
          seasonKey = selectedKey;
        }
      }
    }

    const eplistUrl = `${this.baseUrl}?interface=eplist&season=${encodeURIComponent(seasonKey)}&id=${encodeURIComponent(id)}`;
    const resp = await fetch(eplistUrl);
    if (!resp.ok) return { streams: [], subtitles: [] };

    const data: any = await resp.json();
    const episodeList = data?.episode_list || [];
    if (!Array.isArray(episodeList) || episodeList.length === 0) {
      return { streams: [], subtitles: [] };
    }

    const targetEp =
      episodeList.find((ep: any) => {
        const titleStr = String(ep?.f_title || "");
        const match = titleStr.match(/\bE(?:pisode)?\s*0*(\d+)\b/i) || titleStr.match(/\b0*(\d+)\b/);
        return match ? parseInt(match[1], 10) === episode : false;
      }) || (episode > 0 && episode <= episodeList.length ? episodeList[episode - 1] : episodeList[0]);

    if (!targetEp?.fid || !targetEp?.disk) {
      return { streams: [], subtitles: [] };
    }

    const streams: ReleaseStream[] = [];
    try {
      const fileStream = await this.fetchFileStream(String(targetEp.fid), String(targetEp.disk));
      const quality = targetEp?.quality ? `${targetEp.quality}` : "720p";
      streams.push({
        quality,
        format: "MKV",
        url: fileStream.url,
        headers: {},
        directFile: true,
        label: `Dramachi CDN ${quality}`,
      });
    } catch {}

    return { streams, subtitles: [] };
  }
}
