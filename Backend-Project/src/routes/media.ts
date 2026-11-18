import { Hono } from "hono";
import { MovieBoxClient } from "../providers/moviebox/client";
import { FourKHdHubClient } from "../providers/fourkhdhub/client";
import { DramachiClient } from "../providers/dramachi/client";

export const mediaRouter = new Hono();

mediaRouter.get("/:provider/:id", async (c) => {
  const provider = c.req.param("provider").toLowerCase();
  const id = c.req.param("id");

  if (!id) {
    return c.json({ error: "Media ID parameter is required" }, 400);
  }

  try {
    if (provider === "moviebox") {
      const client = new MovieBoxClient();
      const details = await client.getDetails(id);
      return c.json({ data: details });
    }

    if (provider === "fourkhdhub" || provider === "4khdhub") {
      const client = new FourKHdHubClient();
      const details = await client.getDetails(id);
      return c.json({ data: details });
    }

    if (provider === "dramachi") {
      const client = new DramachiClient();
      const details = await client.getDetails(id);
      return c.json({ data: details });
    }

    return c.json({ error: `Unsupported provider: ${provider}` }, 400);
  } catch (error: any) {
    return c.json({ error: error?.message || "Failed to fetch media details" }, 500);
  }
});
