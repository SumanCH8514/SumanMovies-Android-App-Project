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
    const result = await client.episodeStreams(anilistId, 1, episode, malId);
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
    const result = await client.episodeStreams(anilistId, 1, episode, malId);
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
    const result = await client.episodeStreams(0, 1, episode, malId);
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
    const result = await client.episodeStreams(0, 1, episode, malId);
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
