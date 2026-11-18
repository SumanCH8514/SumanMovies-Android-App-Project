import { Hono } from "hono";
import { cors } from "hono/cors";
import { searchRouter } from "./routes/search";
import { mediaRouter } from "./routes/media";
import { streamsRouter } from "./routes/streams";
import { tvRouter } from "./routes/tv";

const app = new Hono();

app.use(
  "*",
  cors({
    origin: "*",
    allowMethods: ["GET", "POST", "OPTIONS"],
    allowHeaders: ["Content-Type", "Authorization"],
  })
);

app.get("/", (c) => {
  return c.json({
    name: "SumanMovies Streaming Backend API",
    status: "healthy",
    version: "1.0.0",
    docs: {
      providers: "/api/v1/providers",
      search: "/api/v1/search?q={query}&provider={optional}",
      details: "/api/v1/media/:provider/:id",
      streams: "/api/v1/streams/:provider/:id?season={s}&episode={e}",
      tv: "/api/v1/tv/channels?category={optional}",
    },
  });
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

app.get("/api/v1/providers", (c) => {
  return c.json({
    providers: [
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
    ],
  });
});

app.route("/api/v1/search", searchRouter);
app.route("/api/v1/media", mediaRouter);
app.route("/api/v1/streams", streamsRouter);
app.route("/api/v1/tv", tvRouter);

app.notFound((c) => {
  return c.json({ error: "Endpoint not found" }, 404);
});

app.onError((err, c) => {
  return c.json({ error: err.message || "Internal server error" }, 500);
});

export default app;
