import { Hono } from "hono";
import { MovieBoxClient } from "../providers/moviebox/client";
import { FourKHdHubClient } from "../providers/fourkhdhub/client";
import { DramachiClient } from "../providers/dramachi/client";
import { StreamResponse } from "../types";

export const streamsRouter = new Hono();

streamsRouter.get("/:provider/:id", async (c) => {
  const provider = c.req.param("provider").toLowerCase();
  const id = c.req.param("id");
  const season = parseInt(c.req.query("season") || c.req.query("se") || "0", 10);
  const episode = parseInt(c.req.query("episode") || c.req.query("ep") || "0", 10);

  if (!id) {
    return c.json({ error: "Media ID parameter is required" }, 400);
  }

  try {
    if (provider === "moviebox") {
      const client = new MovieBoxClient();
      const result = await client.episodeStreams(id, season, episode);
      const response: StreamResponse = {
        provider: "moviebox",
        id,
        title: `MovieBox Stream ${id}`,
        season: season > 0 ? season : undefined,
        episode: episode > 0 ? episode : undefined,
        streams: result.streams,
        subtitles: result.subtitles,
      };
      return c.json({ data: response });
    }

    if (provider === "fourkhdhub" || provider === "4khdhub") {
      const client = new FourKHdHubClient();
      const result = await client.episodeStreams(id, season, episode);
      const response: StreamResponse = {
        provider: "fourkhdhub",
        id,
        title: `4KHDHub Stream`,
        season: season > 0 ? season : undefined,
        episode: episode > 0 ? episode : undefined,
        streams: result.streams,
        subtitles: result.subtitles,
      };
      return c.json({ data: response });
    }

    if (provider === "dramachi") {
      const client = new DramachiClient();
      const result = await client.episodeStreams(id, Math.max(1, season), Math.max(1, episode));
      const response: StreamResponse = {
        provider: "dramachi",
        id,
        title: `Dramachi Stream`,
        season: season > 0 ? season : undefined,
        episode: episode > 0 ? episode : undefined,
        streams: result.streams,
        subtitles: result.subtitles,
      };
      return c.json({ data: response });
    }

    return c.json({ error: `Unsupported provider: ${provider}` }, 400);
  } catch (error: any) {
    return c.json({ error: error?.message || "Failed to resolve media stream" }, 500);
  }
});
