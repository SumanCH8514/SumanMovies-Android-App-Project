import { Hono } from "hono";
import { MovieBoxClient } from "../providers/moviebox/client";
import { FourKHdHubClient } from "../providers/fourkhdhub/client";
import { DramachiClient } from "../providers/dramachi/client";
import { CatalogItem } from "../types";

export const searchRouter = new Hono();

searchRouter.get("/", async (c) => {
  const query = c.req.query("q")?.trim();
  const provider = c.req.query("provider")?.toLowerCase();

  if (!query) {
    return c.json({ error: "Query parameter 'q' is required" }, 400);
  }

  const moviebox = new MovieBoxClient();
  const fourkhd = new FourKHdHubClient();
  const dramachi = new DramachiClient();

  if (provider === "moviebox") {
    const results = await moviebox.search(query);
    return c.json({ provider: "moviebox", count: results.length, data: results });
  }

  if (provider === "fourkhdhub" || provider === "4khdhub") {
    const results = await fourkhd.search(query);
    return c.json({ provider: "fourkhdhub", count: results.length, data: results });
  }

  if (provider === "dramachi") {
    const results = await dramachi.search(query);
    return c.json({ provider: "dramachi", count: results.length, data: results });
  }

  const [mbRes, fourkRes, dramaRes] = await Promise.allSettled([
    moviebox.search(query),
    fourkhd.search(query),
    dramachi.search(query),
  ]);

  const combined: CatalogItem[] = [];
  if (mbRes.status === "fulfilled") combined.push(...mbRes.value);
  if (fourkRes.status === "fulfilled") combined.push(...fourkRes.value);
  if (dramaRes.status === "fulfilled") combined.push(...dramaRes.value);

  return c.json({
    provider: "all",
    count: combined.length,
    data: combined,
  });
});
