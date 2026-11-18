import { ReleaseStream } from "../../types";

const BROWSER_UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36";

function rot13(input: string): string {
  return input.replace(/[a-zA-Z]/g, (c) => {
    const base = c <= "Z" ? 65 : 97;
    return String.fromCharCode(((c.charCodeAt(0) - base + 13) % 26) + base);
  });
}

function extractGreenMotorsPayload(html: string): string | null {
  const needle = "s(";
  let searchIdx = 0;
  while (true) {
    const pos = html.indexOf(needle, searchIdx);
    if (pos < 0) break;
    const absPos = pos + needle.length;
    const rest = html.substring(absPos).trimStart();
    const afterKey = rest.startsWith("'o'")
      ? rest.substring(3)
      : rest.startsWith('"o"')
      ? rest.substring(3)
      : null;
    if (afterKey === null) {
      searchIdx = absPos;
      continue;
    }
    const trimmedAfterKey = afterKey.trimStart();
    if (!trimmedAfterKey.startsWith(",")) {
      searchIdx = absPos;
      continue;
    }
    const trimmedAfterComma = trimmedAfterKey.substring(1).trimStart();
    const quote = trimmedAfterComma[0];
    if (quote !== "'" && quote !== '"') {
      searchIdx = absPos;
      continue;
    }
    const payloadSlice = trimmedAfterComma.substring(1);
    const end = payloadSlice.indexOf(quote);
    if (end >= 0) {
      return payloadSlice.substring(0, end);
    }
    searchIdx = absPos;
  }
  return null;
}

function decodeGreenMotorsPayload(payload: string): string | null {
  try {
    const step1 = atob(payload);
    const step2 = atob(step1);
    const step3 = rot13(step2);
    const step4 = atob(step3);
    const json = JSON.parse(step4);
    if (!json?.o) return null;
    return atob(json.o);
  } catch {
    return null;
  }
}

function extractPixelDrainUrls(html: string): string[] {
  const urls: string[] = [];
  const regex = /https?:\/\/pixeldrain\.com\/(?:u|api\/file)\/([a-zA-Z0-9_-]+)/g;
  let match: RegExpExecArray | null;
  while ((match = regex.exec(html)) !== null) {
    const fileId = match[1];
    urls.push(`https://pixeldrain.com/api/file/${fileId}?download`);
  }
  return urls;
}

function extractDownloadButtonUrl(html: string): string | null {
  const match = html.match(/<a[^>]+href=["'](https?:\/\/[^"']*(?:hubcloud|download|gamerxyt)[^"']*)["'][^>]*>/i);
  return match ? match[1] : null;
}

export async function resolveHubCloud(lockerUrl: string): Promise<ReleaseStream[]> {
  const results: ReleaseStream[] = [];
  const seenUrls = new Set<string>();

  try {
    let targetUrl = lockerUrl;

    // Handle greenmotors / greenmountmotors landing pages
    if (lockerUrl.includes("greenmotors") || lockerUrl.includes("greenmountmotors")) {
      const resp = await fetch(lockerUrl, {
        headers: { "User-Agent": BROWSER_UA, Referer: "https://4khdhub.one/" },
      });
      if (resp.ok) {
        const gmHtml = await resp.text();
        const payload = extractGreenMotorsPayload(gmHtml);
        const unpacked = payload ? decodeGreenMotorsPayload(payload) : null;
        if (unpacked && unpacked.startsWith("http")) {
          targetUrl = unpacked;
        }
      }
    }

    const driveResp = await fetch(targetUrl, {
      headers: {
        "User-Agent": BROWSER_UA,
        Referer: "https://4khdhub.one/",
      },
    });
    if (!driveResp.ok) return results;
    const driveHtml = await driveResp.text();

    const is4k = driveHtml.includes("2160p") || driveHtml.includes("4K") || driveHtml.includes("UHD");
    const qualityLabel = is4k ? "4K 2160p UHD HDR" : "1080p Full HD";

    const directPixelDrain = extractPixelDrainUrls(driveHtml);
    for (const url of directPixelDrain) {
      if (!seenUrls.has(url)) {
        seenUrls.add(url);
        results.push({
          quality: qualityLabel,
          format: "MKV",
          url,
          headers: { "User-Agent": BROWSER_UA },
          directFile: true,
          label: `4KHDHub PixelDrain Direct (${qualityLabel})`,
        });
      }
    }

    const nextUrl = extractDownloadButtonUrl(driveHtml);
    if (nextUrl && nextUrl !== targetUrl) {
      const resolverResp = await fetch(nextUrl, {
        headers: {
          "User-Agent": BROWSER_UA,
          Referer: targetUrl,
        },
      });
      if (resolverResp.ok) {
        const resolverHtml = await resolverResp.text();

        // Cloudflare R2 direct stream links
        const r2Regex = /href=["'](https?:\/\/[^"']*r2\.cloudflarestorage\.com\/[^"']*)["']/g;
        let r2Match: RegExpExecArray | null;
        while ((r2Match = r2Regex.exec(resolverHtml)) !== null) {
          const r2Url = r2Match[1];
          if (!seenUrls.has(r2Url)) {
            seenUrls.add(r2Url);
            results.push({
              quality: qualityLabel,
              format: r2Url.includes(".mkv") ? "MKV" : "MP4",
              url: r2Url,
              headers: { "User-Agent": BROWSER_UA },
              directFile: true,
              label: `4KHDHub Cloudflare R2 (${qualityLabel})`,
            });
          }
        }

        // 10Gbps CDN servers
        const gpdlRegex = /href=["'](https?:\/\/gpdl\.hubcloud\.[^"']*)["']/g;
        let gpMatch: RegExpExecArray | null;
        while ((gpMatch = gpdlRegex.exec(resolverHtml)) !== null) {
          const gpUrl = gpMatch[1];
          if (!seenUrls.has(gpUrl)) {
            seenUrls.add(gpUrl);
            results.push({
              quality: qualityLabel,
              format: "MKV",
              url: gpUrl,
              headers: { "User-Agent": BROWSER_UA, Referer: nextUrl },
              directFile: true,
              label: `4KHDHub 10Gbps High-Speed (${qualityLabel})`,
            });
          }
        }

        // Second pass PixelDrain
        const secondPixelDrain = extractPixelDrainUrls(resolverHtml);
        for (const url of secondPixelDrain) {
          if (!seenUrls.has(url)) {
            seenUrls.add(url);
            results.push({
              quality: qualityLabel,
              format: "MKV",
              url,
              headers: { "User-Agent": BROWSER_UA },
              directFile: true,
              label: `4KHDHub PixelDrain (${qualityLabel})`,
            });
          }
        }

        // hbplay.pages.dev unwrap
        const hbMatch = resolverHtml.match(/https?:\/\/hbplay\.pages\.dev\/\?u=([a-zA-Z0-9+/=]+)/);
        if (hbMatch) {
          try {
            const decoded = atob(hbMatch[1]);
            if (decoded.startsWith("http") && !seenUrls.has(decoded)) {
              seenUrls.add(decoded);
              results.push({
                quality: qualityLabel,
                format: "MKV",
                url: decoded,
                headers: { "User-Agent": BROWSER_UA },
                directFile: true,
                label: `4KHDHub Direct Playable (${qualityLabel})`,
              });
            }
          } catch {}
        }
      }
    }
  } catch {}

  return results;
}
