import { CatalogItem, MediaDetails, ReleaseStream, SubtitleOption } from "../../types";

export const STREAM_REFERER = "https://sportslive.wine";

export function isDeprecationNoticeUrl(url: string): boolean {
  const lower = url.toLowerCase();
  return (
    lower.includes("1c7de0bd3393702d9191801f15f88f8d") ||
    lower.includes("9a0461bc39da389663bf3dbb17091d3f") ||
    lower.includes("b164fbfb4347792950bdfbfb563d39d9") ||
    lower.includes("/notice.mp4") ||
    (lower.includes("macdn.aoneroom.com") && lower.includes("/other/"))
  );
}

function toDashMpd(urlStr: string): string | null {
  const base = urlStr.replace(/[*\/]+$/, "");
  if (base.length > 0 && (base.startsWith("http://") || base.startsWith("https://")) && base.includes("/dash/")) {
    return `${base}/index.mpd`;
  }
  return null;
}

function decodeB64Padded(s: string): Uint8Array | null {
  try {
    let clean = s.replace(/-/g, "+").replace(/_/g, "/");
    const pad = (4 - (clean.length % 4)) % 4;
    if (pad > 0) clean += "=".repeat(pad);
    const binary = atob(clean);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
  } catch {
    return null;
  }
}

export function resolveDashManifestFromPolicy(signCookie: string): string | null {
  for (const part of signCookie.split(";")) {
    const trimmed = part.trim();
    if (trimmed.includes("urlprefix=")) {
      const idx = trimmed.indexOf("urlprefix=");
      const prefixPart = trimmed.substring(idx + "urlprefix=".length);
      const b64Token = prefixPart.split(":")[0].trim();
      const bytes = decodeB64Padded(b64Token);
      if (bytes) {
        const urlStr = new TextDecoder().decode(bytes);
        const mpd = toDashMpd(urlStr);
        if (mpd) return mpd;
      }
    }
    if (trimmed.startsWith("CloudFront-Policy=")) {
      const policyRaw = trimmed.substring("CloudFront-Policy=".length).trim();
      const bytes = decodeB64Padded(policyRaw);
      if (bytes) {
        try {
          const json = JSON.parse(new TextDecoder().decode(bytes));
          const resource = json?.Statement?.[0]?.Resource;
          if (typeof resource === "string") {
            const mpd = toDashMpd(resource);
            if (mpd) return mpd;
          }
        } catch {}
      }
    }
  }
  return null;
}

export function captionsJsonToOptions(payload: any): SubtitleOption[] {
  const captions = payload?.extCaptions || payload?.data?.extCaptions;
  if (!Array.isArray(captions)) return [];

  const seenUrls = new Set<string>();
  const options: SubtitleOption[] = [];

  for (const cap of captions) {
    const url = cap?.url;
    if (!url || typeof url !== "string" || url.includes("aa348f2541d13ffe")) continue;
    const size = typeof cap.size === "number" ? cap.size : parseInt(cap?.size || "0", 10);
    if (size > 0 && size <= 50) continue;

    const rawName = cap.lanName || cap.lan || "Unknown";
    if (seenUrls.has(url)) continue;
    seenUrls.add(url);

    options.push({
      language: rawName,
      url,
    });
  }
  return options;
}

export function movieboxSubjectToCatalogItem(s: any): CatalogItem | null {
  const idStr = s?.subjectId !== undefined ? String(s.subjectId) : s?.id !== undefined ? String(s.id) : null;
  if (!idStr) return null;

  const title = s?.title || s?.name || "Unknown";
  const stype = s?.subjectType !== undefined ? Number(s.subjectType) : Number(s?.stype || 1);
  const mediaType = stype === 2 ? "series" : "movie";

  const rawYear = s?.releaseDate || s?.year || s?.releaseInfo || "";
  const yearMatch = String(rawYear).match(/\b(19\d{2}|20\d{2})\b/);
  const year = yearMatch ? yearMatch[1] : undefined;

  const posterUrl = s?.cover?.url || s?.coverUrl || s?.poster || s?.pic || undefined;
  const seasonCount = typeof s?.season === "number" ? s.season : undefined;

  return {
    id: idStr,
    provider: "moviebox",
    title,
    mediaType,
    year,
    posterUrl,
    seasonCount,
  };
}

export function movieboxSearchJsonToCatalog(payload: any): CatalogItem[] {
  const subjects =
    payload?.data?.items ||
    payload?.items ||
    payload?.data?.results?.[0]?.subjects ||
    payload?.results?.[0]?.subjects ||
    payload?.data?.list ||
    payload?.list;

  if (!Array.isArray(subjects)) return [];

  const items: CatalogItem[] = [];
  for (const s of subjects) {
    const item = movieboxSubjectToCatalogItem(s);
    if (item) items.push(item);
  }
  return items;
}

export function movieboxDetailsJsonToMediaDetails(payload: any): MediaDetails {
  const subject = payload?.data?.subject || payload?.subject || payload?.data || payload;
  const idStr = String(subject?.subjectId || subject?.id || "");
  const title = subject?.title || "Unknown";
  const stype = Number(subject?.subjectType || subject?.stype || 1);
  const mediaType = stype === 2 ? "series" : "movie";

  const rawYear = subject?.releaseDate || subject?.year || "";
  const yearMatch = String(rawYear).match(/\b(19\d{2}|20\d{2})\b/);
  const year = yearMatch ? yearMatch[1] : undefined;

  const description = subject?.description || subject?.intro || undefined;
  const posterUrl = subject?.cover?.url || subject?.coverUrl || undefined;

  let duration: string | undefined = undefined;
  if (typeof subject?.duration === "number" && subject.duration > 0) {
    duration = `${Math.floor(subject.duration / 60)}m`;
  }

  const rawGenres = subject?.genre || subject?.genres;
  const genres: string[] = Array.isArray(rawGenres) ? rawGenres.map((g: any) => String(g)) : [];

  const seasons: { seasonNumber: number; episodeCount: number }[] = [];
  const seasonsData = subject?.seasons?.seasons;
  if (Array.isArray(seasonsData)) {
    for (const s of seasonsData) {
      seasons.push({
        seasonNumber: Number(s.se || 1),
        episodeCount: Number(s.maxEp || s.episodeCount || 0),
      });
    }
  }

  const rating = subject?.imdbRatingValue !== undefined ? String(subject.imdbRatingValue) : undefined;
  const director = subject?.director || undefined;
  const stars = subject?.stars || undefined;

  return {
    id: idStr,
    provider: "moviebox",
    title,
    mediaType,
    year,
    description,
    posterUrl,
    duration,
    genres,
    seasons,
    director,
    stars,
    rating,
  };
}

export function movieboxPlayInfoJsonToReleases(
  payload: any,
  season: number,
  episode: number,
  userAgent: string
): ReleaseStream[] {
  const data = payload?.data || payload;
  const streams = data?.streams;
  if (!Array.isArray(streams)) return [];

  const releases: ReleaseStream[] = [];

  for (const stream of streams) {
    const codec = stream?.codecName || stream?.codec || undefined;
    const sizeBytes = typeof stream?.size === "number" ? stream.size : parseInt(stream?.size || "0", 10) || undefined;
    const signCookie = stream?.signCookie || "";
    const streamUrl = stream?.url || "";

    let manifestUrl = resolveDashManifestFromPolicy(signCookie);
    if (!manifestUrl && !isDeprecationNoticeUrl(streamUrl) && streamUrl.startsWith("http")) {
      manifestUrl = streamUrl;
    }
    if (!manifestUrl) continue;

    const headers: Record<string, string> = {
      Referer: STREAM_REFERER,
      "User-Agent": userAgent,
    };

    if (signCookie) {
      const cleanCookie = signCookie
        .split(";")
        .map((s: string) => s.trim())
        .filter((s: string) => s.length > 0)
        .join("; ");
      headers["Cookie"] = cleanCookie;
    }

    const resolutionsStr = String(stream?.resolutions || data?.displayResolutions || "1080,720,480");
    const parsedRes = resolutionsStr
      .split(",")
      .map((r: string) => parseInt(r.trim(), 10))
      .filter((r: number) => !isNaN(r) && r > 0);

    const resList = parsedRes.length > 0 ? parsedRes : [1080];
    const isDash = manifestUrl.includes(".mpd") || manifestUrl.includes("/dash/");

    for (const res of resList) {
      releases.push({
        quality: `${res}p`,
        format: isDash ? "DASH" : "MP4",
        url: manifestUrl,
        headers,
        sizeBytes,
        directFile: !isDash,
        codec,
        label: `${res}p ${codec || ""}`.trim(),
      });
    }
  }

  return releases;
}
