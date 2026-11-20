import { Hono } from "hono";
import { ReAnimeClient } from "../providers/reanime/client";

export const reanimeRouter = new Hono();

reanimeRouter.get("/search", async (c) => {
  try {
    const q = c.req.query("q")?.trim() || "";
    if (!q) {
      return c.json({ error: "Missing query parameter 'q'" }, 400);
    }
    const client = new ReAnimeClient();
    const results = await client.search(q);
    return c.json({ provider: "reanime", count: results.length, data: results });
  } catch (e: any) {
    return c.json({ error: e?.message || "ReAnime search failed" }, 500);
  }
});

reanimeRouter.get("/anilist/details", async (c) => {
  try {
    const rawId = c.req.query("id") || "";
    const anilistId = parseInt(rawId, 10);
    if (!anilistId || isNaN(anilistId)) {
      return c.json({ error: "Valid numeric 'id' parameter is required" }, 400);
    }
    const client = new ReAnimeClient();
    const details = await client.getDetailsByAnilist(anilistId);
    return c.json({ provider: "reanime", anilistId, data: details });
  } catch (e: any) {
    return c.json({ error: e?.message || "Failed to fetch AniList details" }, 500);
  }
});

reanimeRouter.get("/anilist/details/:id", async (c) => {
  try {
    const rawId = c.req.param("id") || "";
    const anilistId = parseInt(rawId, 10);
    if (!anilistId || isNaN(anilistId)) {
      return c.json({ error: "Valid numeric ID is required" }, 400);
    }
    const client = new ReAnimeClient();
    const details = await client.getDetailsByAnilist(anilistId);
    return c.json({ provider: "reanime", anilistId, data: details });
  } catch (e: any) {
    return c.json({ error: e?.message || "Failed to fetch AniList details" }, 500);
  }
});

reanimeRouter.get("/mal/details", async (c) => {
  try {
    const rawId = c.req.query("id") || "";
    const malId = parseInt(rawId, 10);
    if (!malId || isNaN(malId)) {
      return c.json({ error: "Valid numeric 'id' parameter is required" }, 400);
    }
    const client = new ReAnimeClient();
    const details = await client.getDetailsByMal(malId);
    return c.json({ provider: "reanime", malId, data: details });
  } catch (e: any) {
    return c.json({ error: e?.message || "Failed to fetch MAL details" }, 500);
  }
});

reanimeRouter.get("/mal/details/:id", async (c) => {
  try {
    const rawId = c.req.param("id") || "";
    const malId = parseInt(rawId, 10);
    if (!malId || isNaN(malId)) {
      return c.json({ error: "Valid numeric ID is required" }, 400);
    }
    const client = new ReAnimeClient();
    const details = await client.getDetailsByMal(malId);
    return c.json({ provider: "reanime", malId, data: details });
  } catch (e: any) {
    return c.json({ error: e?.message || "Failed to fetch MAL details" }, 500);
  }
});

reanimeRouter.get("/anilist/streams", async (c) => {
  try {
    const rawId = c.req.query("id") || "";
    const anilistId = parseInt(rawId, 10);
    const episode = Math.max(1, parseInt(c.req.query("episode") || c.req.query("ep") || "1", 10));
    const malId = c.req.query("mal") ? parseInt(c.req.query("mal")!, 10) : undefined;

    if (!anilistId || isNaN(anilistId)) {
      return c.json({ error: "Valid numeric 'id' parameter is required" }, 400);
    }

    const client = new ReAnimeClient();
    const origin = new URL(c.req.url).origin;
    const result = await client.episodeStreams(anilistId, 1, episode, malId, origin);
    return c.json({
      provider: "reanime",
      anilistId,
      malId,
      episode,
      embeds: result.embeds,
      streams: result.streams,
      downloads: result.downloads,
      subtitles: result.subtitles,
    });
  } catch (e: any) {
    return c.json({ error: e?.message || "Failed to resolve AniList streams" }, 500);
  }
});

reanimeRouter.get("/anilist/streams/:id", async (c) => {
  try {
    const rawId = c.req.param("id") || "";
    const anilistId = parseInt(rawId, 10);
    const episode = Math.max(1, parseInt(c.req.query("episode") || c.req.query("ep") || "1", 10));
    const malId = c.req.query("mal") ? parseInt(c.req.query("mal")!, 10) : undefined;

    if (!anilistId || isNaN(anilistId)) {
      return c.json({ error: "Valid numeric ID is required" }, 400);
    }

    const client = new ReAnimeClient();
    const origin = new URL(c.req.url).origin;
    const result = await client.episodeStreams(anilistId, 1, episode, malId, origin);
    return c.json({
      provider: "reanime",
      anilistId,
      malId,
      episode,
      embeds: result.embeds,
      streams: result.streams,
      downloads: result.downloads,
      subtitles: result.subtitles,
    });
  } catch (e: any) {
    return c.json({ error: e?.message || "Failed to resolve AniList streams" }, 500);
  }
});

reanimeRouter.get("/mal/streams", async (c) => {
  try {
    const rawId = c.req.query("id") || "";
    const malId = parseInt(rawId, 10);
    const episode = Math.max(1, parseInt(c.req.query("episode") || c.req.query("ep") || "1", 10));

    if (!malId || isNaN(malId)) {
      return c.json({ error: "Valid numeric 'id' parameter is required" }, 400);
    }

    const client = new ReAnimeClient();
    const origin = new URL(c.req.url).origin;
    const result = await client.episodeStreams(0, 1, episode, malId, origin);
    return c.json({
      provider: "reanime",
      malId,
      episode,
      embeds: result.embeds,
      streams: result.streams,
      downloads: result.downloads,
      subtitles: result.subtitles,
    });
  } catch (e: any) {
    return c.json({ error: e?.message || "Failed to resolve MAL streams" }, 500);
  }
});

reanimeRouter.get("/mal/streams/:id", async (c) => {
  try {
    const rawId = c.req.param("id") || "";
    const malId = parseInt(rawId, 10);
    const episode = Math.max(1, parseInt(c.req.query("episode") || c.req.query("ep") || "1", 10));

    if (!malId || isNaN(malId)) {
      return c.json({ error: "Valid numeric ID is required" }, 400);
    }

    const client = new ReAnimeClient();
    const origin = new URL(c.req.url).origin;
    const result = await client.episodeStreams(0, 1, episode, malId, origin);
    return c.json({
      provider: "reanime",
      malId,
      episode,
      embeds: result.embeds,
      streams: result.streams,
      downloads: result.downloads,
      subtitles: result.subtitles,
    });
  } catch (e: any) {
    return c.json({ error: e?.message || "Failed to resolve MAL streams" }, 500);
  }
});

reanimeRouter.get("/direct", async (c) => {
  try {
    const accessId = c.req.query("id") || c.req.query("access_id") || "";
    if (!accessId) {
      return c.json({ error: "Parameter 'id' or 'access_id' is required" }, 400);
    }
    const client = new ReAnimeClient();
    const dl = await client.resolveDirectMkv(accessId);
    if (!dl) {
      return c.json({ error: "Direct download link could not be generated" }, 404);
    }
    return c.json({ provider: "reanime", data: dl });
  } catch (e: any) {
    return c.json({ error: e?.message || "Failed to resolve direct download link" }, 500);
  }
});

reanimeRouter.get("/hls/master.m3u8", async (c) => {
  try {
    const masterUrl = c.req.query("url") || "";
    const pkKeyB64 = c.req.query("pk") || "";
    if (!masterUrl || !pkKeyB64) {
      return c.text("Missing url or pk parameter", 400);
    }

    const res = await fetch(masterUrl, {
      headers: {
        Referer: "https://flixcloud.cc/",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
      },
    });

    if (!res.ok) {
      return c.text("Failed to fetch upstream master playlist", res.status);
    }

    const encText = await res.text();
    const l = atob(pkKeyB64);
    const u = atob(encText.trim());
    const decBytes = new Uint8Array(u.length);
    for (let h = 0; h < u.length; h++) {
      decBytes[h] = u.charCodeAt(h) ^ l.charCodeAt(h % l.length);
    }

    const plain = new TextDecoder().decode(decBytes);
    const origin = new URL(c.req.url).origin;
    const lines = plain.split("\n");
    const rewritten = lines.map((line) => {
      const trimmed = line.trim();
      if (!trimmed) return line;
      if (trimmed.startsWith("#")) {
        if (trimmed.startsWith("#EXT-X-MEDIA:") && trimmed.includes('URI="')) {
          return trimmed.replace(/URI="([^"]+)"/, (_, relUri) => {
            const abs = new URL(relUri, masterUrl).href;
            return `URI="${origin}/api/v1/reanime/hls/media.m3u8?url=${encodeURIComponent(abs)}&pk=${encodeURIComponent(pkKeyB64)}"`;
          });
        }
        return line;
      }
      const absUrl = new URL(trimmed, masterUrl).href;
      return `${origin}/api/v1/reanime/hls/media.m3u8?url=${encodeURIComponent(absUrl)}&pk=${encodeURIComponent(pkKeyB64)}`;
    });

    c.header("Content-Type", "application/vnd.apple.mpegurl");
    c.header("Access-Control-Allow-Origin", "*");
    return c.body(rewritten.join("\n"));
  } catch (e: any) {
    return c.text(e?.message || "HLS master proxy error", 500);
  }
});

reanimeRouter.get("/hls/media.m3u8", async (c) => {
  try {
    const mediaUrl = c.req.query("url") || "";
    const pkKeyB64 = c.req.query("pk") || "";
    if (!mediaUrl || !pkKeyB64) {
      return c.text("Missing url or pk parameter", 400);
    }

    const res = await fetch(mediaUrl, {
      headers: {
        Referer: "https://flixcloud.cc/",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
      },
    });

    if (!res.ok) {
      return c.text("Failed to fetch upstream media playlist", res.status);
    }

    const encText = await res.text();
    const l = atob(pkKeyB64);
    const u = atob(encText.trim());
    const decBytes = new Uint8Array(u.length);
    for (let h = 0; h < u.length; h++) {
      decBytes[h] = u.charCodeAt(h) ^ l.charCodeAt(h % l.length);
    }

    const plain = new TextDecoder().decode(decBytes);
    const lines = plain.split("\n");
    const rewritten = lines.map((line) => {
      const trimmed = line.trim();
      if (!trimmed) return line;
      if (trimmed.startsWith("#EXT-X-KEY:") && trimmed.includes('URI="')) {
        return trimmed.replace(/URI="([^"]+)"/, (_, relUri) => {
          const absKey = new URL(relUri, mediaUrl).href;
          return `URI="${absKey}"`;
        });
      }
      return line;
    });

    c.header("Content-Type", "application/vnd.apple.mpegurl");
    c.header("Access-Control-Allow-Origin", "*");
    return c.body(rewritten.join("\n"));
  } catch (e: any) {
    return c.text(e?.message || "HLS media proxy error", 500);
  }
});
