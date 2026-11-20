import { CatalogItem, MediaDetails, ReleaseStream, SubtitleOption } from "../../types";

export interface ReAnimeEmbed {
  server: string;
  type: string;
  hardsub?: boolean;
  url: string;
}

export interface ReAnimeDownload {
  quality: string;
  format: "MKV" | "MP4";
  filename: string;
  sizeBytes: number;
  sizeHuman: string;
  url: string;
  headers: Record<string, string>;
  directFile: boolean;
  audioLangs?: string;
}

export interface ReAnimeStreamsResult {
  embeds: ReAnimeEmbed[];
  streams: ReleaseStream[];
  downloads: ReAnimeDownload[];
  subtitles: SubtitleOption[];
}

const REANIME_DOMAINS = ["https://reanime.wtf", "https://reindex.to", "https://reanime.cz"];

export class ReAnimeClient {
  private baseHeaders = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
    Accept: "application/json, text/plain, */*",
  };

  private async fetchWithFallback(path: string, options?: RequestInit): Promise<Response | null> {
    for (const base of REANIME_DOMAINS) {
      try {
        const fullUrl = `${base}${path.startsWith("/") ? path : "/" + path}`;
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 4000);
        const res = await fetch(fullUrl, {
          ...options,
          signal: controller.signal,
          headers: {
            ...this.baseHeaders,
            ...(options?.headers || {}),
          },
        });
        clearTimeout(timeoutId);
        if (res.ok) {
          return res;
        }
      } catch {}
    }
    return null;
  }

  async search(query: string): Promise<CatalogItem[]> {
    const trimmed = query.trim();
    if (!trimmed) return [];

    const res = await this.fetchWithFallback(`/api/v1/search?q=${encodeURIComponent(trimmed)}`);
    if (!res || !res.ok) return [];

    const data = (await res.json()) as any;
    const results = data?.results || [];

    return results.map((item: any) => {
      const titleObj = item?.title || {};
      const titleStr = titleObj.english || titleObj.userPreferred || titleObj.romaji || item.anime_id;
      const poster = item?.cover_image?.extra_large || item?.cover_image?.large || item?.cover_image?.medium || undefined;
      const year = item?.season_year ? String(item.season_year) : undefined;
      const anilistId = item?.anilist_id ? String(item.anilist_id) : undefined;
      const effectiveId = anilistId || item.anime_id;

      return {
        id: effectiveId,
        provider: "reanime" as const,
        title: titleStr,
        mediaType: "series" as const,
        year,
        posterUrl: poster,
        seasonCount: 1,
      };
    });
  }

  async getDetails(id: string): Promise<MediaDetails> {
    const slug = id.replace(/^reanime:/, "").trim();
    if (/^\d+$/.test(slug)) {
      return this.getDetailsByAnilist(parseInt(slug, 10));
    }

    const res = await this.fetchWithFallback(`/api/v1/anime/${encodeURIComponent(slug)}`);
    if (!res || !res.ok) {
      throw new Error(`ReAnime item not found: ${slug}`);
    }

    const data = (await res.json()) as any;
    const titleObj = data?.title || {};
    const titleStr = titleObj.english || titleObj.user_preferred || titleObj.romaji || slug;
    const poster = data?.cover_image?.extra_large || data?.cover_image?.large || undefined;
    const totalEps = data?.episodes_total || data?.last_episode || 1;
    const returnId = data?.anilist_id ? String(data.anilist_id) : (data?.anime_id || slug);

    return {
      id: returnId,
      provider: "reanime",
      title: titleStr,
      mediaType: "series",
      year: data.season_year ? String(data.season_year) : undefined,
      description: data.description ? data.description.replace(/<[^>]*>/g, "") : undefined,
      posterUrl: poster,
      duration: data.duration ? `${data.duration}m` : undefined,
      genres: Array.isArray(data.genres) ? data.genres : [],
      seasons: [
        {
          seasonNumber: 1,
          episodeCount: totalEps,
        },
      ],
      rating: data.average_score ? `${(data.average_score / 10).toFixed(1)}/10` : undefined,
    };
  }

  async getAnilistMetadata(options: { anilistId?: number; malId?: number; search?: string }): Promise<any> {
    const { anilistId, malId, search } = options;
    const query = `
      query ($id: Int, $idMal: Int, $search: String) {
        Media (id: $id, idMal: $idMal, search: $search, type: ANIME) {
          id
          idMal
          title { english romaji native userPreferred }
          format
          status
          episodes
          duration
          season
          seasonYear
          description(asHtml: false)
          coverImage { extraLarge large medium color }
          bannerImage
          genres
          averageScore
        }
      }
    `;

    const variables: Record<string, any> = {};
    if (anilistId) variables.id = anilistId;
    if (malId) variables.idMal = malId;
    if (search) variables.search = search;

    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 3500);
      const res = await fetch("https://graphql.anilist.co", {
        method: "POST",
        signal: controller.signal,
        headers: {
          "User-Agent": "SumanMovies/1.0.0 (Android; okhttp/4.12.0)",
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify({ query, variables }),
      });
      clearTimeout(timeoutId);

      if (res.ok) {
        const json = (await res.json()) as any;
        if (json?.data?.Media) {
          return json.data.Media;
        }
      }
    } catch {}

    return null;
  }

  async getDetailsByAnilist(anilistId: number): Promise<MediaDetails> {
    const media = await this.getAnilistMetadata({ anilistId });
    if (media) {
      const titleStr = media.title?.english || media.title?.userPreferred || media.title?.romaji || `AniList #${anilistId}`;
      return {
        id: String(media.id),
        provider: "reanime",
        title: titleStr,
        mediaType: "series",
        year: media.seasonYear ? String(media.seasonYear) : undefined,
        description: media.description || undefined,
        posterUrl: media.coverImage?.extraLarge || media.coverImage?.large || undefined,
        duration: media.duration ? `${media.duration}m` : undefined,
        genres: media.genres || [],
        seasons: [
          {
            seasonNumber: 1,
            episodeCount: media.episodes || 1,
          },
        ],
        rating: media.averageScore ? `${(media.averageScore / 10).toFixed(1)}/10` : undefined,
      };
    }

    const { servers } = await this.getFlixServers(anilistId, 1);
    const accessId = servers[0]?.dataLink?.substring(servers[0].dataLink.indexOf("/e/") + 3)?.split("?")[0];
    if (accessId) {
      const dl = await this.resolveDirectMkv(accessId);
      if (dl?.filename) {
        return {
          id: String(anilistId),
          provider: "reanime",
          title: dl.filename.replace(/\.[a-zA-Z0-9]+$/, ""),
          mediaType: "series",
          genres: [],
          seasons: [{ seasonNumber: 1, episodeCount: 1 }],
        };
      }
    }

    return {
      id: String(anilistId),
      provider: "reanime",
      title: `Anime #${anilistId}`,
      mediaType: "series",
      genres: [],
      seasons: [{ seasonNumber: 1, episodeCount: 1 }],
    };
  }

  async getDetailsByMal(malId: number): Promise<MediaDetails> {
    const media = await this.getAnilistMetadata({ malId });
    if (media) {
      const titleStr = media.title?.english || media.title?.userPreferred || media.title?.romaji || `MAL #${malId}`;
      return {
        id: String(media.id),
        provider: "reanime",
        title: titleStr,
        mediaType: "series",
        year: media.seasonYear ? String(media.seasonYear) : undefined,
        description: media.description || undefined,
        posterUrl: media.coverImage?.extraLarge || media.coverImage?.large || undefined,
        duration: media.duration ? `${media.duration}m` : undefined,
        genres: media.genres || [],
        seasons: [
          {
            seasonNumber: 1,
            episodeCount: media.episodes || 1,
          },
        ],
        rating: media.averageScore ? `${(media.averageScore / 10).toFixed(1)}/10` : undefined,
      };
    }

    return {
      id: String(malId),
      provider: "reanime",
      title: `MAL Anime #${malId}`,
      mediaType: "series",
      genres: [],
      seasons: [{ seasonNumber: 1, episodeCount: 1 }],
    };
  }

  async getFlixServers(anilistId: number, epNum: number, malId?: number): Promise<{ success: boolean; servers: any[] }> {
    const qs = malId ? `?mal=${malId}` : "";
    const res = await this.fetchWithFallback(`/api/flix/${anilistId}/${epNum}${qs}`);

    if (!res || !res.ok) {
      return { success: false, servers: [] };
    }

    const data = (await res.json()) as any;
    return {
      success: Boolean(data?.success),
      servers: Array.isArray(data?.servers) ? data.servers : [],
    };
  }

  async resolveDirectMkv(accessId: string): Promise<ReAnimeDownload | null> {
    const cleanId = accessId.includes("/e/")
      ? accessId.substring(accessId.indexOf("/e/") + 3).split("?")[0]
      : accessId.split("?")[0];

    const metaUrl = `https://flixcloud.cc/d/${cleanId}/__data.json`;
    const res = await fetch(metaUrl, {
      headers: {
        Referer: `https://flixcloud.cc/d/${cleanId}`,
        "User-Agent": this.baseHeaders["User-Agent"],
      },
    });

    if (!res.ok) return null;
    const json = (await res.json()) as any;
    const dataArr = json?.nodes?.[1]?.data;
    if (!Array.isArray(dataArr)) return null;

    const fileId = dataArr[2] as string;
    const filename = (dataArr[4] as string) || `${cleanId}.mkv`;
    const sizeBytes = Number(dataArr[5]) || 0;
    const sizeHuman = (dataArr[6] as string) || "Unknown size";
    const resolution = (dataArr[8] as string) || "1080p";
    const baseHost = dataArr[16] as string;
    const token = dataArr[17] as string;

    if (!baseHost || !token || !fileId) return null;

    const directEndpoint = `${baseHost}/download/${fileId}?token=${encodeURIComponent(token)}`;
    const redirectRes = await fetch(directEndpoint, {
      method: "GET",
      redirect: "manual",
      headers: {
        Referer: `https://flixcloud.cc/d/${cleanId}`,
        "User-Agent": this.baseHeaders["User-Agent"],
      },
    });

    const location = redirectRes.headers.get("location");
    if (!location) return null;

    return {
      quality: resolution,
      format: filename.toLowerCase().endsWith(".mp4") ? "MP4" : "MKV",
      filename,
      sizeBytes,
      sizeHuman,
      url: location,
      headers: {
        Referer: "https://flixcloud.cc/",
        "User-Agent": this.baseHeaders["User-Agent"],
      },
      directFile: true,
    };
  }

  async resolveFlixHls(embedUrl: string): Promise<{ masterUrl: string; pkKey: string; subtitles: SubtitleOption[]; resolution: string; rawMasterM3u8: string } | null> {
    try {
      const res = await fetch(embedUrl, {
        headers: {
          Referer: "https://reanime.wtf/",
          "User-Agent": this.baseHeaders["User-Agent"],
        },
      });

      if (!res.ok) return null;
      const html = await res.text();
      const match = html.match(/data:\s*(\[.*?\]),\s*form:/s);
      if (!match) return null;

      const jsonStr = match[1].replace(/([{,]\s*)([a-zA-Z0-9_$]+)\s*:/g, '$1"$2":');
      const rawData = JSON.parse(jsonStr);
      const payload = rawData?.[2]?.data;
      if (!payload?.obfuscation_seed || !payload?.w_payload) {
        return null;
      }

      const subtitles: SubtitleOption[] = Array.isArray(payload.subtitles)
        ? payload.subtitles.map((sub: any) => ({
            language: sub.language || "Unknown",
            url: sub.url,
          }))
        : [];

      const toUint8Array = (b64: string): Uint8Array => {
        const bin = atob(b64);
        const arr = new Uint8Array(bin.length);
        for (let i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
        return arr;
      };

      const sha256Hex = async (str: string): Promise<string> => {
        const enc = new TextEncoder().encode(str);
        const hash = await crypto.subtle.digest("SHA-256", enc);
        return Array.from(new Uint8Array(hash))
          .map((b) => b.toString(16).padStart(2, "0"))
          .join("");
      };

      const seed = payload.obfuscation_seed;
      let seed1 = seed;
      for (let s = 0; s < 3; s++) seed1 = await sha256Hex(seed1 + s.toString());
      let seed2 = seed1;
      for (let s = 0; s < 3; s++) seed2 = await sha256Hex(seed2 + s.toString());

      const fields = {
        keyField: `kf_${seed1.substring(8, 16)}`,
        ivField: `ivf_${seed1.substring(16, 24)}`,
        containerName: `cd_${seed1.substring(24, 32)}`,
        arrayName: `ad_${seed1.substring(32, 40)}`,
        objectName: `od_${seed1.substring(40, 48)}`,
        tokenField: `${seed1.substring(48, 64)}_${seed1.substring(56, 64)}`,
        keyFrag2Field: `${seed2.substring(0, 16)}_${seed2.substring(16, 24)}`,
      };

      const cryptoData = payload.obfuscated_crypto_data || payload;
      const container = cryptoData[fields.containerName];
      const itemObj = container?.[fields.arrayName]?.[0]?.[fields.objectName];
      const frag1 = itemObj?.[fields.keyField];
      const ivB64 = itemObj?.[fields.ivField];
      const frag2 = cryptoData[fields.keyFrag2Field] || payload[fields.keyFrag2Field];

      let tokenRef = "";
      for (const [k, v] of Object.entries(payload)) {
        if (k === fields.tokenField && typeof v === "string" && v.length > 0) {
          tokenRef = v;
          break;
        }
      }
      if (!tokenRef) {
        for (const [k, v] of Object.entries(cryptoData)) {
          if (k === fields.tokenField && typeof v === "string" && v.length > 0) {
            tokenRef = v;
            break;
          }
        }
      }

      if (!frag1 || !frag2 || !ivB64 || !tokenRef) return null;

      const tokenRes = await fetch(`https://flixcloud.cc/api/m3u8/${tokenRef}`, {
        headers: {
          Referer: embedUrl,
          "User-Agent": this.baseHeaders["User-Agent"],
        },
      });

      if (!tokenRes.ok) return null;
      const tokenData = (await tokenRes.json()) as any;

      const vidKey = (await sha256Hex(tokenRef + "vid")).substring(0, 10);
      const encKey = (await sha256Hex(tokenRef + "key")).substring(0, 10);
      const cipherB64 = tokenData[vidKey];
      const frag3 = tokenData[encKey];

      if (!cipherB64 || !frag3) return null;

      const wasmBytes = toUint8Array(payload.w_payload);
      const wasmRes: any = await WebAssembly.instantiate(wasmBytes, {});
      const instance = wasmRes.instance || wasmRes;
      const exports = instance.exports as any;
      const mem = exports.memory;
      if (mem.buffer.byteLength === 0) mem.grow(1);

      const t1 = toUint8Array(frag1);
      const t2 = toUint8Array(frag2);
      const t3 = toUint8Array(frag3);
      const L = t1.length;
      const T = 1000;
      const A = T + L;
      const w = A + L;
      const z = w + L;

      const memView = new Uint8Array(mem.buffer);
      memView.set(t1, T);
      memView.set(t2, A);
      memView.set(t3, w);

      const sVal = parseInt(seed.substring(0, 8), 16);
      exports._s(sVal);
      exports._r(T, A, w, z, L);

      const combinedKey = new Uint8Array(L);
      combinedKey.set(memView.subarray(z, z + L));

      const pbkdfKey = await crypto.subtle.importKey("raw", combinedKey, { name: "PBKDF2" }, false, ["deriveBits"]);
      const derived = await crypto.subtle.deriveBits(
        {
          name: "PBKDF2",
          salt: new TextEncoder().encode(seed),
          iterations: 1000,
          hash: "SHA-256",
        },
        pbkdfKey,
        256
      );

      const finalKeyBytes = new Uint8Array(derived);
      for (let idx = 0; idx < 32; idx++) {
        finalKeyBytes[idx] ^= seed.charCodeAt(idx % seed.length);
      }

      const aesKeyDigest = await crypto.subtle.digest("SHA-256", finalKeyBytes);
      const aesKey = await crypto.subtle.importKey("raw", aesKeyDigest, { name: "AES-CBC" }, false, ["decrypt"]);
      const decryptedBuf = await crypto.subtle.decrypt(
        { name: "AES-CBC", iv: toUint8Array(ivB64) },
        aesKey,
        toUint8Array(cipherB64)
      );

      const masterUrl = new TextDecoder().decode(decryptedBuf);
      const pkPtr = exports._c();
      const pkBytes = new Uint8Array(mem.buffer).slice(pkPtr, pkPtr + 32);
      let binStr = "";
      for (let i = 0; i < pkBytes.length; i++) {
        binStr += String.fromCharCode(pkBytes[i]);
      }
      const pkKeyB64 = btoa(binStr);

      let resolution = "1080p";
      let rawMasterM3u8 = "";
      try {
        const m3u8Res = await fetch(masterUrl, {
          headers: {
            Referer: "https://flixcloud.cc/",
            "User-Agent": this.baseHeaders["User-Agent"],
          },
        });
        if (m3u8Res.ok) {
          const encM3u8Text = await m3u8Res.text();
          const l = binStr;
          const u = atob(encM3u8Text.trim());
          const decBytes = new Uint8Array(u.length);
          for (let h = 0; h < u.length; h++) {
            decBytes[h] = u.charCodeAt(h) ^ l.charCodeAt(h % l.length);
          }
          rawMasterM3u8 = new TextDecoder().decode(decBytes);
          const resMatch = rawMasterM3u8.match(/RESOLUTION=\d+x(\d+)/);
          if (resMatch?.[1]) {
            const height = parseInt(resMatch[1], 10);
            if (height >= 1080) resolution = "1080p";
            else if (height >= 720) resolution = "720p";
            else if (height >= 480) resolution = "480p";
            else if (height >= 360) resolution = "360p";
          }
        }
      } catch {}

      return { masterUrl, pkKey: pkKeyB64, subtitles, resolution, rawMasterM3u8 };
    } catch {
      return null;
    }
  }

  async episodeStreams(
    idOrAnilist: string | number,
    season: number,
    episode: number,
    malId?: number,
    requestOrigin?: string
  ): Promise<ReAnimeStreamsResult> {
    let targetAnilistId = 0;
    let targetMalId = malId;

    if (typeof idOrAnilist === "number") {
      targetAnilistId = idOrAnilist;
    } else {
      const clean = idOrAnilist.replace(/^reanime:/, "").trim();
      if (/^\d+$/.test(clean)) {
        targetAnilistId = parseInt(clean, 10);
      } else {
        try {
          const detailRes = await this.fetchWithFallback(`/api/v1/anime/${encodeURIComponent(clean)}`);
          if (detailRes && detailRes.ok) {
            const detailData = (await detailRes.json()) as any;
            if (detailData?.anilist_id) {
              targetAnilistId = detailData.anilist_id;
              targetMalId = detailData.mal_id || targetMalId;
            }
          }
        } catch {}

        if (!targetAnilistId) {
          const searchRes = await this.search(clean);
          const found = searchRes.find((item) => item.id === clean) || searchRes[0];
          if (found && /^\d+$/.test(found.id)) {
            targetAnilistId = parseInt(found.id, 10);
          }
        }
      }
    }

    if (!targetAnilistId && targetMalId) {
      const anilistInfo = await this.getAnilistMetadata({ malId: targetMalId });
      if (anilistInfo?.id) {
        targetAnilistId = anilistInfo.id;
      }
    }

    if (!targetAnilistId && typeof idOrAnilist === "string") {
      const cleanTitle = idOrAnilist
        .replace(/^reanime:/, "")
        .replace(/-\d+$/, "")
        .replace(/[-_]/g, " ")
        .trim();
      const anilistInfo = await this.getAnilistMetadata({ search: cleanTitle });
      if (anilistInfo?.id) {
        targetAnilistId = anilistInfo.id;
        targetMalId = anilistInfo.idMal || targetMalId;
      }
    }

    if (!targetAnilistId) {
      throw new Error(`Could not resolve AniList ID for target: ${idOrAnilist}`);
    }

    const targetEp = Math.max(1, episode);
    let { servers } = await this.getFlixServers(targetAnilistId, targetEp, targetMalId);
    if ((!servers || servers.length === 0) && targetEp > 1) {
      const fallback = await this.getFlixServers(targetAnilistId, 1, targetMalId);
      if (fallback.servers?.length > 0) {
        servers = fallback.servers;
      }
    }

    const embeds: ReAnimeEmbed[] = servers.map((s) => ({
      server: s.serverName || "HD-1",
      type: s.dataType || "sub",
      hardsub: Boolean(s.hardsub),
      url: s.dataLink,
    }));

    const dubServers = servers.filter((s) => (s.dataType || "").toLowerCase() === "dub");
    const subServers = servers.filter((s) => (s.dataType || "").toLowerCase() === "sub");
    const otherServers = servers.filter(
      (s) => !["sub", "dub"].includes((s.dataType || "").toLowerCase())
    );

    const streams: ReleaseStream[] = [];
    const downloads: ReAnimeDownload[] = [];
    const subtitles: SubtitleOption[] = [];
    const processedEmbeds = new Set<string>();

    const processServerItem = async (serverItem: any, defaultLang: "ja" | "en") => {
      const embedUrl = serverItem.dataLink;
      if (!embedUrl) return;

      const accessId = embedUrl.includes("/e/")
        ? embedUrl.substring(embedUrl.indexOf("/e/") + 3).split("?")[0]
        : embedUrl.split("?")[0];
      const dataTypeStr = (serverItem.dataType || (defaultLang === "en" ? "dub" : "sub")).toUpperCase();
      const dedupeKey = `${accessId}_${dataTypeStr}`;
      if (processedEmbeds.has(dedupeKey)) return;
      processedEmbeds.add(dedupeKey);

      const [dlResult, hlsResult] = await Promise.allSettled([
        this.resolveDirectMkv(accessId),
        this.resolveFlixHls(embedUrl),
      ]);

      const dl = dlResult.status === "fulfilled" ? dlResult.value : null;
      const hls = hlsResult.status === "fulfilled" ? hlsResult.value : null;

      if (hls?.subtitles?.length) {
        for (const sub of hls.subtitles) {
          if (!subtitles.some((existing) => existing.url === sub.url)) {
            subtitles.push(sub);
          }
        }
      }

      const audioLangTitle = defaultLang === "en" ? "English Dub" : "Japanese Sub";

      if (dl) {
        const dlWithLang = { ...dl, audioLangs: audioLangTitle };
        downloads.push(dlWithLang);

        streams.push({
          quality: dl.quality,
          format: dl.format,
          url: dl.url,
          headers: dl.headers,
          sizeBytes: dl.sizeBytes,
          directFile: true,
          label: `[${dataTypeStr}] ${dl.quality} Direct [${dl.sizeHuman}]`,
          audioLangs: audioLangTitle,
        });

        if (dl.quality === "1080p") {
          streams.push({
            quality: "720p",
            format: dl.format,
            url: dl.url,
            headers: dl.headers,
            sizeBytes: dl.sizeBytes,
            directFile: true,
            label: `[${dataTypeStr}] 720p Optimized [${dl.sizeHuman}]`,
            audioLangs: audioLangTitle,
          });
        }
      }

      if (hls?.masterUrl) {
        const qualityName = hls.resolution || dl?.quality || "1080p";
        const hlsStreamUrl = requestOrigin
          ? `${requestOrigin.replace(/\/+$/, "")}/api/v1/reanime/hls/master.m3u8?url=${encodeURIComponent(hls.masterUrl)}&pk=${encodeURIComponent(hls.pkKey)}`
          : hls.masterUrl;

        streams.push({
          quality: qualityName,
          format: "HLS",
          url: hlsStreamUrl,
          headers: {
            Referer: "https://flixcloud.cc/",
            "User-Agent": this.baseHeaders["User-Agent"],
          },
          directFile: false,
          label: `[${dataTypeStr}] ${qualityName} Adaptive`,
          audioLangs: audioLangTitle,
        });

        if (qualityName === "1080p") {
          streams.push({
            quality: "720p",
            format: "HLS",
            url: hlsStreamUrl,
            headers: {
              Referer: "https://flixcloud.cc/",
              "User-Agent": this.baseHeaders["User-Agent"],
            },
            directFile: false,
            label: `[${dataTypeStr}] 720p Adaptive`,
            audioLangs: audioLangTitle,
          });
        }
      }
    };

    const serverPromises: Promise<void>[] = [];
    for (const s of dubServers) {
      serverPromises.push(processServerItem(s, "en"));
    }
    for (const s of subServers) {
      serverPromises.push(processServerItem(s, "ja"));
    }
    for (const s of otherServers) {
      serverPromises.push(processServerItem(s, "ja"));
    }

    await Promise.allSettled(serverPromises);

    return {
      embeds,
      streams,
      downloads,
      subtitles,
    };
  }
}
