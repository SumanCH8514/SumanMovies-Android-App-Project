import { Hono } from "hono";
import { parseM3uPlaylist } from "../providers/tv/parser";

const DEFAULT_IPTV_URL = "https://iptv-org.github.io/iptv/index.m3u";

export const tvRouter = new Hono();

tvRouter.get("/channels", async (c) => {
  const customUrl = c.req.query("url") || DEFAULT_IPTV_URL;
  const category = c.req.query("category")?.toLowerCase();

  try {
    const resp = await fetch(customUrl);
    if (!resp.ok) {
      return c.json({ error: `Failed to fetch IPTV playlist: ${resp.status}` }, 502);
    }

    const content = await resp.text();
    let channels = parseM3uPlaylist(content);

    if (category) {
      channels = channels.filter((ch) => ch.groupTitle?.toLowerCase().includes(category));
    }

    return c.json({
      count: channels.length,
      data: channels.slice(0, 500),
    });
  } catch (error: any) {
    return c.json({ error: error?.message || "Failed to process IPTV playlist" }, 500);
  }
});
