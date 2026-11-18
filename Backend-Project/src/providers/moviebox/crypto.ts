import { md5Hex, hmacMd5Base64 } from "../../utils/md5";

const DEFAULT_SECRET_BYTES = new Uint8Array([
  0xef, 0xa8, 0x91, 0x97, 0x4e, 0xec, 0xd3, 0x14, 0x8d, 0xf6, 0x3a, 0xa6, 0x11,
  0x60, 0x2d, 0xef, 0xd1, 0x01, 0x25, 0x9b, 0xa5, 0x21, 0x02, 0x2c, 0x57, 0xae,
  0x05, 0x66, 0xbd, 0x8e,
]);

const STOCK_CERT_SHA256 = "03d4b741632d136bc23e2de4c6adcdb7712f1980b619c183221801730bbd0410";
const BUILD_TAG_SALT = "oneroom";
const SIGNATURE_BODY_MAX_BYTES = 102400;

export function generateClientToken(ts: number): string {
  const tsStr = ts.toString();
  const reversedTs = tsStr.split("").reverse().join("");
  const hashVal = md5Hex(reversedTs);
  return `${tsStr},${hashVal}`;
}

export function generateClientBuild(ts: number): string {
  const randPart = Math.floor(Math.random() * 1000);
  const stamp = `${Math.floor(ts / 1000)}${String(ts % 1000).padStart(3, "0")}000${randPart}`;
  const hash = md5Hex(`${STOCK_CERT_SHA256}${BUILD_TAG_SALT}${stamp}`);
  return `${stamp}.${hash}`;
}

function sortedQueryString(urlStr: string): string {
  try {
    const url = new URL(urlStr);
    const params = Array.from(url.searchParams.entries());
    if (params.length === 0) return "";
    params.sort((a, b) => a[0].localeCompare(b[0]));
    return params.map(([k, v]) => `${k}=${v}`).join("&");
  } catch {
    return "";
  }
}

export function buildCanonicalString(
  method: string,
  accept: string,
  contentType: string,
  urlStr: string,
  body: string | null,
  timestampMs: number
): string {
  let canonicalUrl = urlStr;
  try {
    const url = new URL(urlStr);
    const sorted = sortedQueryString(urlStr);
    canonicalUrl = sorted ? `${url.pathname}?${sorted}` : url.pathname;
  } catch {
    canonicalUrl = urlStr;
  }

  let bodyHash = "";
  let bodyLength = "";
  if (body !== null) {
    const bytes = new TextEncoder().encode(body);
    const len = bytes.length;
    const truncated = len > SIGNATURE_BODY_MAX_BYTES ? bytes.slice(0, SIGNATURE_BODY_MAX_BYTES) : bytes;
    bodyHash = md5Hex(truncated);
    bodyLength = len.toString();
  }

  return `${method.toUpperCase()}\n${accept}\n${contentType}\n${bodyLength}\n${timestampMs}\n${bodyHash}\n${canonicalUrl}`;
}

export function generateSignature(
  method: string,
  accept: string,
  contentType: string,
  urlStr: string,
  body: string | null,
  timestampMs: number
): string {
  const canonical = buildCanonicalString(method, accept, contentType, urlStr, body, timestampMs);
  const sigB64 = hmacMd5Base64(DEFAULT_SECRET_BYTES, canonical);
  return `${timestampMs}|2|${sigB64}`;
}

export function randomHex(len: number): string {
  let result = "";
  const chars = "0123456789abcdef";
  for (let i = 0; i < len; i++) {
    result += chars[Math.floor(Math.random() * chars.length)];
  }
  return result;
}

export function randomUuid(): string {
  return `${randomHex(8)}-${randomHex(4)}-${randomHex(4)}-${randomHex(4)}-${randomHex(12)}`;
}

export function generateClientInfoAndUA(): { userAgent: string; clientInfo: string } {
  const androidVersions = [
    { ver: "9", build: "PQ3A.190605.03081104" },
    { ver: "10", build: "QP1A.191005.007.A3" },
    { ver: "11", build: "RP1A.200720.011" },
    { ver: "12", build: "S1B.220414.015" },
    { ver: "13", build: "TQ2A.230405.003" },
  ];
  const redmiDevices = [
    { model: "23078RKD5C", brand: "Redmi" },
    { model: "2201117TY", brand: "Redmi" },
    { model: "2201117TG", brand: "Redmi" },
    { model: "22101316G", brand: "Redmi" },
    { model: "M2012K11AG", brand: "Redmi" },
  ];
  const versionCodes = [50020117, 50020118, 50020119, 50020120, 50020121];
  const timezones = ["Asia/Kolkata", "Asia/Shanghai", "Asia/Tokyo", "America/New_York", "Europe/London"];

  const av = androidVersions[Math.floor(Math.random() * androidVersions.length)];
  const rd = redmiDevices[Math.floor(Math.random() * redmiDevices.length)];
  const vc = versionCodes[Math.floor(Math.random() * versionCodes.length)];
  const tz = timezones[Math.floor(Math.random() * timezones.length)];
  const gaid = randomUuid();
  const deviceId = randomHex(32);

  const userAgent = `com.community.oneroom/${vc} (Linux; U; Android ${av.ver}; en_US; ${rd.model}; Build/${av.build}; Cronet/135.0.7012.3)`;

  const clientInfo = JSON.stringify({
    package_name: "com.community.oneroom",
    version_name: "4.0.01.0813.03",
    version_code: vc,
    os: "android",
    os_version: av.ver,
    install_ch: "ps",
    device_id: deviceId,
    install_store: "ps",
    gaid: gaid,
    brand: rd.brand,
    model: rd.model,
    system_language: "en",
    net: "NETWORK_WIFI",
    region: "US",
    timezone: tz,
    sp_code: "40401",
    "X-Play-Mode": "2",
  });

  return { userAgent, clientInfo };
}

export function randomSpoofedIp(): string {
  const prefixes = ["103.245.236.", "104.28.198.", "106.51.242.", "110.235.221.", "115.127.112."];
  const prefix = prefixes[Math.floor(Math.random() * prefixes.length)];
  return `${prefix}${Math.floor(Math.random() * 254) + 1}`;
}

export function buildSignedHeaders(
  method: string,
  urlStr: string,
  body: string | null,
  authToken: string | null,
  userAgent: string,
  clientInfo: string,
  spoofedIp: string,
  overrideTs?: number
): Record<string, string> {
  const ts = overrideTs ?? Date.now();
  const accept = "application/json";
  const contentType = "application/json";

  const clientToken = generateClientToken(ts);
  const signature = generateSignature(method, accept, contentType, urlStr, body, ts);
  const buildTag = generateClientBuild(ts);

  let updatedClientInfo = clientInfo;
  try {
    const parsed = JSON.parse(clientInfo);
    parsed["X-Client-Build"] = buildTag;
    updatedClientInfo = JSON.stringify(parsed);
  } catch {}

  const headers: Record<string, string> = {
    "User-Agent": userAgent,
    Accept: accept,
    "Content-Type": contentType,
    Connection: "keep-alive",
    "x-client-token": clientToken,
    "x-tr-signature": signature,
    "x-client-build": buildTag,
    "x-client-info": updatedClientInfo,
    "x-client-status": "0",
    "x-forwarded-for": spoofedIp,
  };

  if (authToken) {
    headers["Authorization"] = `Bearer ${authToken}`;
  }

  return headers;
}
