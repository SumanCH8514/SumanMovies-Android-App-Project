import { buildSignedHeaders, generateClientInfoAndUA, randomSpoofedIp } from "./crypto";
import {
  movieboxSearchJsonToCatalog,
  movieboxDetailsJsonToMediaDetails,
  movieboxPlayInfoJsonToReleases,
  captionsJsonToOptions,
  isDeprecationNoticeUrl,
  STREAM_REFERER,
} from "./adapt";
import { CatalogItem, MediaDetails, ReleaseStream, SubtitleOption } from "../../types";

const HOST_POOL = [
  "https://api6.aoneroom.com",
  "https://api5.aoneroom.com",
  "https://api4.aoneroom.com",
  "https://api4sg.aoneroom.com",
  "https://api3.aoneroom.com",
  "https://api6sg.aoneroom.com",
  "https://api.inmoviebox.com",
];

const RETRY_STATUS_CODES = [403, 406, 407, 429, 500, 502, 503, 504];

export class MovieBoxClient {
  private static globalTimeOffset: number | null = null;
  private activeHostIndex = 0;
  private authToken: string | null = null;
  private userAgent: string;
  private clientInfo: string;
  private spoofedIp: string;

  constructor() {
    const { userAgent, clientInfo } = generateClientInfoAndUA();
    this.userAgent = userAgent;
    this.clientInfo = clientInfo;
    this.spoofedIp = randomSpoofedIp();
  }

  private now(): number {
    return Date.now() + (MovieBoxClient.globalTimeOffset ?? 0);
  }

  private async ensureSession(): Promise<string> {
    if (this.authToken) return this.authToken;

    const path = "/wefeed-mobile-bff/user-api/visitor-login";
    const bodyStr = "{}";
    const res = await this.requestHosts("POST", path, bodyStr, null);
    const token = res?.data?.token || res?.token;
    if (!token || typeof token !== "string") {
      throw new Error("MovieBox visitor login failed: missing token");
    }
    this.authToken = token;
    return token;
  }

  private async requestHosts(
    method: string,
    pathAndQuery: string,
    body: string | null,
    token: string | null
  ): Promise<any> {
    const startIdx = this.activeHostIndex;
    let lastErr = "";
    for (let i = 0; i < HOST_POOL.length; i++) {
      const idx = (startIdx + i) % HOST_POOL.length;
      const base = HOST_POOL[idx];
      const url = `${base}${pathAndQuery}`;

      const headers = buildSignedHeaders(
        method,
        url,
        body,
        token,
        this.userAgent,
        this.clientInfo,
        this.spoofedIp,
        this.now()
      );

      try {
        const resp = await fetch(url, {
          method,
          headers,
          body: body ?? undefined,
        });

        const serverDate = resp.headers.get("date");
        if (serverDate) {
          const parsedServerTime = Date.parse(serverDate);
          if (!isNaN(parsedServerTime)) {
            MovieBoxClient.globalTimeOffset = parsedServerTime - Date.now();
          }
        }

        const status = resp.status;
        if (status === 440) {
          if (serverDate) {
            const parsedServerTime = Date.parse(serverDate);
            if (!isNaN(parsedServerTime)) {
              MovieBoxClient.globalTimeOffset = parsedServerTime - Date.now();
              const retryHeaders = buildSignedHeaders(
                method,
                url,
                body,
                token,
                this.userAgent,
                this.clientInfo,
                this.spoofedIp,
                this.now()
              );
              const retryResp = await fetch(url, {
                method,
                headers: retryHeaders,
                body: body ?? undefined,
              });
              if (retryResp.ok) {
                this.activeHostIndex = idx;
                const json: any = await retryResp.json();
                return json?.data !== undefined ? json.data : json;
              }
            }
          }
          lastErr = `Host ${base} returned 440 timestamp error`;
          this.activeHostIndex = (idx + 1) % HOST_POOL.length;
          continue;
        }

        const xUser = resp.headers.get("x-user");
        if (xUser) {
          try {
            const parsed = JSON.parse(xUser);
            if (parsed?.token) this.authToken = parsed.token;
          } catch {}
        }

        if (RETRY_STATUS_CODES.includes(status)) {
          lastErr = `Host ${base} returned status ${status}`;
          this.activeHostIndex = (idx + 1) % HOST_POOL.length;
          continue;
        }

        if (status === 401) {
          lastErr = `Host ${base} returned 401`;
          this.authToken = null;
          const freshToken = await this.ensureSession();
          return this.requestHosts(method, pathAndQuery, body, freshToken);
        }

        if (!resp.ok) {
          lastErr = `Host ${base} returned HTTP ${status}: ${resp.statusText}`;
          this.activeHostIndex = (idx + 1) % HOST_POOL.length;
          continue;
        }

        this.activeHostIndex = idx;
        const json: any = await resp.json();
        return json?.data !== undefined ? json.data : json;
      } catch (e: any) {
        lastErr = `Host ${base} threw fetch error: ${e?.message || String(e)}`;
        this.activeHostIndex = (idx + 1) % HOST_POOL.length;
      }
    }
    throw new Error(`MovieBox: All host servers exhausted or rate-limited. Last error: ${lastErr || "none"}`);
  }

  private async get(pathAndQuery: string): Promise<any> {
    const token = await this.ensureSession();
    return this.requestHosts("GET", pathAndQuery, null, token);
  }

  private async post(pathAndQuery: string, bodyObj: any): Promise<any> {
    const token = await this.ensureSession();
    return this.requestHosts("POST", pathAndQuery, JSON.stringify(bodyObj), token);
  }

  public async search(query: string, page = 1): Promise<CatalogItem[]> {
    const payload = {
      keyword: query,
      page,
      perPage: 15,
      subjectType: 0,
    };
    const res = await this.post("/wefeed-mobile-bff/subject-api/search/v2", payload);
    return movieboxSearchJsonToCatalog(res);
  }

  public async getDetails(subjectId: string): Promise<MediaDetails> {
    const detailsPath = `/wefeed-mobile-bff/subject-api/get?subjectId=${subjectId}`;
    let details = await this.get(detailsPath);

    const stype = Number(details?.subjectType || details?.stype || 1);
    if (stype === 2) {
      try {
        const seasonPath = `/wefeed-mobile-bff/subject-api/season-info?subjectId=${subjectId}`;
        const seasonInfo = await this.get(seasonPath);
        if (seasonInfo && typeof details === "object") {
          details.seasons = seasonInfo;
        }
      } catch {}
    }

    return movieboxDetailsJsonToMediaDetails(details);
  }

  public async getPlayInfo(subjectId: string, season: number, episode: number): Promise<any> {
    const path =
      season === 0 && episode === 0
        ? `/wefeed-mobile-bff/subject-api/play-info/v2?subjectId=${subjectId}`
        : `/wefeed-mobile-bff/subject-api/play-info/v2?subjectId=${subjectId}&se=${season}&ep=${episode}`;
    return this.get(path);
  }

  public async getResourcePosition(subjectId: string, resourceId: string, failUrl: string): Promise<any> {
    const params = new URLSearchParams({
      subjectId,
      resourceId,
      failUrl,
    });
    return this.get(`/wefeed-mobile-bff/subject-api/resource-position?${params.toString()}`);
  }

  public async getCaptions(subjectId: string, resourceId: string): Promise<SubtitleOption[]> {
    try {
      const path = `/wefeed-mobile-bff/subject-api/get-ext-captions?subjectId=${subjectId}&resourceId=${resourceId}`;
      const res = await this.get(path);
      return captionsJsonToOptions(res);
    } catch {
      return [];
    }
  }

  public async episodeStreams(
    subjectId: string,
    season = 0,
    episode = 0
  ): Promise<{ streams: ReleaseStream[]; subtitles: SubtitleOption[] }> {
    const detailsPath = `/wefeed-mobile-bff/subject-api/get?subjectId=${subjectId}`;
    const resourcePath =
      season === 0 && episode === 0
        ? `/wefeed-mobile-bff/subject-api/resource?subjectId=${subjectId}&page=1&perPage=20`
        : `/wefeed-mobile-bff/subject-api/resource?subjectId=${subjectId}&se=${season}&ep=${episode}&page=1&perPage=20`;

    const [playInfoRes, resourcesRes] = await Promise.allSettled([
      this.getPlayInfo(subjectId, season, episode),
      this.get(resourcePath),
    ]);

    const releases: ReleaseStream[] = [];
    const seenUrls = new Set<string>();

    if (playInfoRes.status === "fulfilled" && playInfoRes.value) {
      const playStreams = movieboxPlayInfoJsonToReleases(
        playInfoRes.value,
        season,
        episode,
        this.userAgent
      );
      for (const rel of playStreams) {
        const base = rel.url.split("?")[0];
        if (base && !seenUrls.has(base)) {
          seenUrls.add(base);
          releases.push(rel);
        }
      }
    }

    if (resourcesRes.status === "fulfilled" && resourcesRes.value) {
      const items = resourcesRes.value?.list;
      if (Array.isArray(items)) {
        for (const item of items) {
          const itemSe = Number(item?.se || 0);
          const itemEp = Number(item?.ep || 0);
          if (season !== 0 || episode !== 0) {
            if (itemSe !== season || itemEp !== episode) continue;
          }

          let link = item?.resourceLink || item?.url;
          const resourceId = String(item?.resourceId || item?.id || "");

          if (link && isDeprecationNoticeUrl(link) && resourceId) {
            try {
              const rec = await this.getResourcePosition(subjectId, resourceId, link);
              const recoveredList = rec?.list || rec?.data?.list;
              if (Array.isArray(recoveredList) && recoveredList.length > 0) {
                const recItem = recoveredList.find((r: any) => String(r?.resourceId || "") === resourceId) || recoveredList[0];
                if (recItem?.resourceLink && !isDeprecationNoticeUrl(recItem.resourceLink)) {
                  link = recItem.resourceLink;
                }
              }
            } catch {}
          }

          if (link && !isDeprecationNoticeUrl(link) && link.startsWith("http")) {
            const base = link.split("?")[0];
            if (!seenUrls.has(base)) {
              seenUrls.add(base);
              const resNum = Number(item?.resolution || 1080);
              releases.push({
                quality: `${resNum}p`,
                format: "MP4",
                url: link,
                headers: {
                  "User-Agent": this.userAgent,
                },
                sizeBytes: Number(item?.size || 0) || undefined,
                directFile: true,
                codec: item?.codecName || item?.codec || undefined,
                label: `${resNum}p Direct File`,
              });
            }
          }
        }
      }
    }

    let subtitles: SubtitleOption[] = [];
    try {
      subtitles = await this.getCaptions(subjectId, "0");
    } catch {}

    releases.sort((a, b) => {
      const qA = parseInt(a.quality, 10) || 0;
      const qB = parseInt(b.quality, 10) || 0;
      return qB - qA;
    });

    return { streams: releases, subtitles };
  }
}
