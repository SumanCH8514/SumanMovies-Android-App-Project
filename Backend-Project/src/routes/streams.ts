import { Hono } from "hono";
import { MovieBoxClient } from "../providers/moviebox/client";
import { FourKHdHubClient } from "../providers/fourkhdhub/client";
import { DramachiClient } from "../providers/dramachi/client";
import { Play4UClient } from "../providers/play4u/client";
import { ReAnimeClient } from "../providers/reanime/client";
import { StreamResponse } from "../types";

export const streamsRouter = new Hono();

streamsRouter.get("/:provider/:id", async (c) => {
  const provider = c.req.param("provider").toLowerCase();
  const id = c.req.param("id");
  const season = parseInt(c.req.query("season") || c.req.query("se") || "0", 10);
  const episode = parseInt(c.req.query("episode") || c.req.query("ep") || "0", 10);
  const title = c.req.query("title") || c.req.query("q") || undefined;
  const year = c.req.query("year") || c.req.query("y") || undefined;
  const mal = c.req.query("mal") ? parseInt(c.req.query("mal")!, 10) : undefined;

  if (!id) {
    return c.json({ error: "Media ID parameter is required" }, 400);
  }

  try {
    if (provider === "reanime") {
      const client = new ReAnimeClient();
      const origin = new URL(c.req.url).origin;
      const result = await client.episodeStreams(id, Math.max(1, season), Math.max(1, episode), mal, origin);
      const response: StreamResponse = {
        provider: "reanime",
        id,
        title: title || `ReAnime Stream ${id}`,
        season: season > 0 ? season : undefined,
        episode: episode > 0 ? episode : undefined,
        streams: result.streams,
        subtitles: result.subtitles,
      };
      return c.json({ data: response, embeds: result.embeds, downloads: result.downloads });
    }

    if (provider === "play4u") {
      const client = new Play4UClient();
      const result = await client.episodeStreams(id, season, episode, title, year);
      const response: StreamResponse = {
        provider: "play4u",
        id,
        title: title || `Play4U Stream ${id}`,
        season: season > 0 ? season : undefined,
        episode: episode > 0 ? episode : undefined,
        streams: result.streams,
        subtitles: result.subtitles,
      };
      return c.json({ data: response });
    }

    if (provider === "moviebox") {
      const detailPath = c.req.query("detailPath") || c.req.query("path") || "";
      const client = new MovieBoxClient();
      const result = await client.episodeStreams(id, season, episode, detailPath);
      const response: StreamResponse = {
        provider: "moviebox",
        id,
        title: `MovieBox Stream ${id}`,
        season: season > 0 ? season : undefined,
        episode: episode > 0 ? episode : undefined,
        streams: result.streams,
        subtitles: result.subtitles,
        downloads: result.downloads,
      };
      return c.json({ data: response, downloads: result.downloads });
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
