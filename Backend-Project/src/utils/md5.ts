import crypto from "node:crypto";

export function md5Hex(input: Uint8Array | string): string {
  const hash = crypto.createHash("md5");
  hash.update(input);
  return hash.digest("hex");
}

export function md5Bytes(input: Uint8Array | string): Uint8Array {
  const hash = crypto.createHash("md5");
  hash.update(input);
  return new Uint8Array(hash.digest());
}

export function hmacMd5Base64(key: Uint8Array, message: string | Uint8Array): string {
  const hmac = crypto.createHmac("md5", Buffer.from(key));
  hmac.update(typeof message === "string" ? message : Buffer.from(message));
  return hmac.digest("base64");
}

export function bytesToBase64(bytes: Uint8Array): string {
  return Buffer.from(bytes).toString("base64");
}
