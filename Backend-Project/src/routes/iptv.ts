import { Hono } from "hono";

const CHANNELS_JSON_URL = "https://iptv-org.github.io/api/channels.json";

export const iptvRouter = new Hono();

iptvRouter.get("/channels", async (c) => {
  const country = c.req.query("country")?.trim().toUpperCase();
  const category = c.req.query("category")?.trim().toLowerCase();
  const q = (c.req.query("q") || c.req.query("search") || c.req.query("name"))?.trim().toLowerCase();
  const nsfw = c.req.query("nsfw")?.trim().toLowerCase();
  const limitParam = c.req.query("limit");
  const pageParam = c.req.query("page");
  const wrap = c.req.query("wrap")?.trim().toLowerCase() === "true" || c.req.query("meta")?.trim().toLowerCase() === "true";

  const hasFilters = Boolean(country || category || q || nsfw !== undefined || limitParam || pageParam || wrap);

  const ifNoneMatch = c.req.header("if-none-match");

  try {
    const upstreamHeaders: Record<string, string> = {
      "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
      Accept: "application/json, text/plain, */*",
    };
    if (ifNoneMatch && !hasFilters) {
      upstreamHeaders["If-None-Match"] = ifNoneMatch;
    }

    const resp = await fetch(CHANNELS_JSON_URL, {
      headers: upstreamHeaders,
      cf: {
        cacheTtl: 3600,
        cacheEverything: true,
      },
    });

    if (resp.status === 304 && !hasFilters) {
      return new Response(null, {
        status: 304,
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Cache-Control": "public, max-age=3600, s-maxage=14400, stale-while-revalidate=86400",
          ETag: resp.headers.get("etag") || "",
        },
      });
    }

    if (!resp.ok) {
      return c.json({ error: `Upstream error: ${resp.status} ${resp.statusText}` }, 502);
    }

    const etag = resp.headers.get("etag");
    const lastModified = resp.headers.get("last-modified");

    if (!hasFilters) {
      const body = await resp.text();
      const responseHeaders: Record<string, string> = {
        "Content-Type": "application/json; charset=utf-8",
        "Cache-Control": "public, max-age=3600, s-maxage=14400, stale-while-revalidate=86400",
        "Access-Control-Allow-Origin": "*",
      };
      if (etag) responseHeaders["ETag"] = etag;
      if (lastModified) responseHeaders["Last-Modified"] = lastModified;
      return new Response(body, {
        status: 200,
        headers: responseHeaders,
      });
    }

    const allChannels: any[] = await resp.json();
    let filtered = allChannels;

    if (country) {
      filtered = filtered.filter((ch) => ch.country?.toUpperCase() === country);
    }

    if (category) {
      filtered = filtered.filter((ch) =>
        Array.isArray(ch.categories) && ch.categories.some((cat: string) => String(cat).toLowerCase().includes(category))
      );
    }

    if (q) {
      filtered = filtered.filter((ch) => {
        const nameMatch = typeof ch.name === "string" && ch.name.toLowerCase().includes(q);
        const netMatch = typeof ch.network === "string" && ch.network.toLowerCase().includes(q);
        const altMatch = Array.isArray(ch.alt_names) && ch.alt_names.some((a: string) => String(a).toLowerCase().includes(q));
        return nameMatch || netMatch || altMatch;
      });
    }

    if (nsfw === "false") {
      filtered = filtered.filter((ch) => !ch.is_nsfw);
    } else if (nsfw === "true") {
      filtered = filtered.filter((ch) => ch.is_nsfw === true);
    }

    const total = filtered.length;
    const page = pageParam ? Math.max(1, parseInt(pageParam, 10) || 1) : 1;
    const limit = limitParam ? Math.max(1, Math.min(5000, parseInt(limitParam, 10) || 50)) : total;

    if (limitParam || pageParam) {
      const startIndex = (page - 1) * limit;
      filtered = filtered.slice(startIndex, startIndex + limit);
    }

    const responseHeaders: Record<string, string> = {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "public, max-age=1800, s-maxage=7200, stale-while-revalidate=86400",
      "Access-Control-Allow-Origin": "*",
    };

    if (wrap) {
      return new Response(
        JSON.stringify({
          total,
          count: filtered.length,
          page: pageParam ? page : undefined,
          limit: limitParam ? limit : undefined,
          data: filtered,
        }),
        { status: 200, headers: responseHeaders }
      );
    }

    return new Response(JSON.stringify(filtered), {
      status: 200,
      headers: responseHeaders,
    });
  } catch (error: any) {
    return c.json({ error: error?.message || "Failed to proxy IPTV channels" }, 500);
  }
});
