import { Hono } from "hono";
import { cors } from "hono/cors";
import { searchRouter } from "./routes/search";
import { mediaRouter } from "./routes/media";
import { streamsRouter } from "./routes/streams";
import { tvRouter } from "./routes/tv";
import { reanimeRouter } from "./routes/reanime";
import { iptvRouter } from "./routes/iptv";
import { getApiDocsJson, renderApiDocsHtml } from "./docs/apiDocs";

const app = new Hono();

app.use(
  "*",
  cors({
    origin: "*",
    allowMethods: ["GET", "POST", "OPTIONS"],
    allowHeaders: ["Content-Type", "Authorization"],
  })
);

function resolveBaseUrl(c: any): string {
  const host = c.req.header("host") || "api.streaming.sumanonline.com";
  const proto = c.req.header("x-forwarded-proto") || "https";
  return `${proto}://${host}`;
}

app.get("/", (c) => {
  const accept = c.req.header("accept") || "";
  const baseUrl = resolveBaseUrl(c);

  if (accept.includes("text/html")) {
    return c.html(renderApiDocsHtml(baseUrl));
  }
  return c.json(getApiDocsJson(baseUrl));
});

app.get("/docs", (c) => {
  const baseUrl = resolveBaseUrl(c);
  return c.html(renderApiDocsHtml(baseUrl));
});

app.get("/docs/json", (c) => {
  const baseUrl = resolveBaseUrl(c);
  return c.json(getApiDocsJson(baseUrl));
});

app.post("/api/v1/moviebox/sign", async (c) => {
  try {
    const { buildSignedHeaders, generateClientInfoAndUA, randomSpoofedIp } = await import(
      "./providers/moviebox/crypto"
    );
    const bodyJson = await c.req.json().catch(() => ({}));
    const { method = "GET", url, body = null, token = null } = bodyJson;
    if (!url) {
      return c.json({ error: "Missing url parameter" }, 400);
    }
    const { userAgent, clientInfo } = generateClientInfoAndUA();
    const spoofedIp = randomSpoofedIp();
    const headers = buildSignedHeaders(
      method,
      url,
      body,
      token,
      userAgent,
      clientInfo,
      spoofedIp
    );
    return c.json({ headers });
  } catch (e: any) {
    return c.json({ error: e?.message || "Sign failed" }, 500);
  }
});

app.get("/api/v1/play4u/direct", async (c) => {
  try {
    const { Play4UClient } = await import("./providers/play4u/client");
    const id = c.req.query("id") || c.req.query("slug") || "";
    const season = parseInt(c.req.query("season") || c.req.query("se") || "0", 10);
    const episode = parseInt(c.req.query("episode") || c.req.query("ep") || "0", 10);
    const title = c.req.query("title") || c.req.query("q") || undefined;
    const year = c.req.query("year") || c.req.query("y") || undefined;

    if (!id && !title) {
      return c.json({ error: "Either 'id' or 'title' parameter is required" }, 400);
    }

    const client = new Play4UClient();
    const result = await client.episodeStreams(id, season, episode, title, year);
    return c.json({
      provider: "play4u",
      id: id || title,
      title: title || `Play4U Stream ${id}`,
      season: season > 0 ? season : undefined,
      episode: episode > 0 ? episode : undefined,
      streams: result.streams,
      subtitles: result.subtitles,
    });
  } catch (e: any) {
    return c.json({ error: e?.message || "Failed to resolve Play4U stream" }, 500);
  }
});

app.post("/api/v1/play4u/direct", async (c) => {
  try {
    const { Play4UClient } = await import("./providers/play4u/client");
    const body = await c.req.json().catch(() => ({}));
    const id = body?.id || body?.slug || c.req.query("id") || "";
    const season = parseInt(body?.season ?? body?.se ?? c.req.query("season") ?? "0", 10);
    const episode = parseInt(body?.episode ?? body?.ep ?? c.req.query("episode") ?? "0", 10);
    const title = body?.title ?? body?.q ?? c.req.query("title") ?? undefined;
    const year = body?.year ?? body?.y ?? c.req.query("year") ?? undefined;

    if (!id && !title) {
      return c.json({ error: "Either 'id' or 'title' parameter is required" }, 400);
    }

    const client = new Play4UClient();
    const result = await client.episodeStreams(id, season, episode, title, year);
    return c.json({
      provider: "play4u",
      id: id || title,
      title: title || `Play4U Stream ${id}`,
      season: season > 0 ? season : undefined,
      episode: episode > 0 ? episode : undefined,
      streams: result.streams,
      subtitles: result.subtitles,
    });
  } catch (e: any) {
    return c.json({ error: e?.message || "Failed to resolve Play4U stream" }, 500);
  }
});

app.post("/api/v1/play4u/unseal", async (c) => {
  try {
    const { unseal } = await import("./providers/play4u/client");
    const body = await c.req.json().catch(() => ({}));
    const sealed = body?.sealed || body?.token || "";
    const key = body?.key || undefined;
    if (!sealed) {
      return c.json({ error: "Missing sealed parameter" }, 400);
    }
    const unsealed = unseal(sealed, key);
    return c.json({ unsealed });
  } catch (e: any) {
    return c.json({ error: e?.message || "Unseal failed" }, 500);
  }
});

app.get("/api/v1/dramachi/search", async (c) => {
  try {
    const { DramachiClient } = await import("./providers/dramachi/client");
    const q = c.req.query("q")?.trim() || "";
    const page = parseInt(c.req.query("page") || "1", 10);
    if (!q) {
      return c.json({ error: "Missing query parameter 'q'" }, 400);
    }
    const client = new DramachiClient();
    const results = await client.search(q, page);
    return c.json({ provider: "dramachi", count: results.length, data: results });
  } catch (e: any) {
    return c.json({ error: e?.message || "Dramachi search failed" }, 500);
  }
});

app.get("/api/v1/dramachi/details/:id", async (c) => {
  try {
    const { DramachiClient } = await import("./providers/dramachi/client");
    const id = c.req.param("id");
    if (!id) {
      return c.json({ error: "Missing ID parameter" }, 400);
    }
    const client = new DramachiClient();
    const details = await client.getDetails(id);
    return c.json({ data: details });
  } catch (e: any) {
    return c.json({ error: e?.message || "Failed to fetch Dramachi details" }, 500);
  }
});

app.get("/api/v1/dramachi/direct", async (c) => {
  try {
    const { DramachiClient } = await import("./providers/dramachi/client");
    const id = c.req.query("id") || "";
    const season = Math.max(1, parseInt(c.req.query("season") || c.req.query("se") || "1", 10));
    const episode = Math.max(1, parseInt(c.req.query("episode") || c.req.query("ep") || "1", 10));

    if (!id) {
      return c.json({ error: "Parameter 'id' is required" }, 400);
    }

    const client = new DramachiClient();
    const result = await client.episodeStreams(id, season, episode);
    return c.json({
      provider: "dramachi",
      id,
      title: `Dramachi Stream ${id}`,
      season,
      episode,
      streams: result.streams,
      subtitles: result.subtitles,
    });
  } catch (e: any) {
    return c.json({ error: e?.message || "Failed to resolve Dramachi stream" }, 500);
  }
});

app.post("/api/v1/dramachi/direct", async (c) => {
  try {
    const { DramachiClient } = await import("./providers/dramachi/client");
    const body = await c.req.json().catch(() => ({}));
    const id = String(body?.id || c.req.query("id") || "").trim();
    const season = Math.max(1, parseInt(body?.season ?? body?.se ?? c.req.query("season") ?? "1", 10));
    const episode = Math.max(1, parseInt(body?.episode ?? body?.ep ?? c.req.query("episode") ?? "1", 10));

    if (!id) {
      return c.json({ error: "Parameter 'id' is required" }, 400);
    }

    const client = new DramachiClient();
    const result = await client.episodeStreams(id, season, episode);
    return c.json({
      provider: "dramachi",
      id,
      title: `Dramachi Stream ${id}`,
      season,
      episode,
      streams: result.streams,
      subtitles: result.subtitles,
    });
  } catch (e: any) {
    return c.json({ error: e?.message || "Failed to resolve Dramachi stream" }, 500);
  }
});

app.get("/api/v1/providers", (c) => {
  return c.json({
    providers: [
      {
        id: "play4u",
        name: "Play4U",
        capabilities: {
          search: true,
          pagination: true,
          series: true,
          subtitles: true,
          formats: ["HLS"],
        },
      },
      {
        id: "moviebox",
        name: "MovieBox",
        capabilities: {
          search: true,
          pagination: true,
          series: true,
          subtitles: true,
          formats: ["DASH", "MP4"],
        },
      },
      {
        id: "fourkhdhub",
        name: "4KHDHub",
        capabilities: {
          search: true,
          pagination: false,
          series: true,
          subtitles: false,
          formats: ["MP4", "MKV"],
        },
      },
      {
        id: "dramachi",
        name: "Dramachi",
        capabilities: {
          search: true,
          pagination: true,
          series: true,
          subtitles: false,
          formats: ["MKV", "MP4"],
        },
      },
      {
        id: "reanime",
        name: "ReAnime",
        capabilities: {
          search: true,
          pagination: false,
          series: true,
          subtitles: true,
          formats: ["HLS", "MKV"],
        },
      },
      {
        id: "tv",
        name: "Live TV (IPTV)",
        capabilities: {
          search: false,
          pagination: false,
          series: false,
          subtitles: false,
          formats: ["HLS"],
        },
      },
      {
        id: "iptv",
        name: "IPTV Org Channels",
        capabilities: {
          search: true,
          pagination: true,
          series: false,
          subtitles: false,
          formats: ["JSON"],
        },
      },
    ],
  });
});

app.route("/api/v1/search", searchRouter);
app.route("/api/v1/media", mediaRouter);
app.route("/api/v1/streams", streamsRouter);
app.route("/api/v1/tv", tvRouter);
app.route("/api/v1/reanime", reanimeRouter);
app.route("/api/v1/iptv", iptvRouter);

app.notFound((c) => {
  return c.json({ error: "Endpoint not found" }, 404);
});

app.onError((err, c) => {
  return c.json({ error: err.message || "Internal server error" }, 500);
});

export default app;
