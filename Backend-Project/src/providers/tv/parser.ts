import { TVChannel } from "../../types";

export function parseM3uPlaylist(content: string): TVChannel[] {
  const lines = content.split(/\r?\n/);
  const channels: TVChannel[] = [];
  let currentChannel: Partial<TVChannel> | null = null;
  let counter = 1;

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed) continue;

    if (trimmed.startsWith("#EXTINF:")) {
      currentChannel = {};
      const logoMatch = trimmed.match(/tvg-logo=["']([^"']+)["']/i);
      if (logoMatch) currentChannel.logoUrl = logoMatch[1];

      const groupMatch = trimmed.match(/group-title=["']([^"']+)["']/i);
      if (groupMatch) currentChannel.groupTitle = groupMatch[1];

      const idMatch = trimmed.match(/tvg-id=["']([^"']+)["']/i);
      currentChannel.id = idMatch ? idMatch[1] : `channel_${counter++}`;

      const nameParts = trimmed.split(",");
      if (nameParts.length > 1) {
        currentChannel.name = nameParts.slice(1).join(",").trim();
      } else {
        currentChannel.name = currentChannel.id;
      }
    } else if (!trimmed.startsWith("#") && currentChannel) {
      if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        currentChannel.streamUrl = trimmed;
        if (currentChannel.name && currentChannel.id && currentChannel.streamUrl) {
          channels.push(currentChannel as TVChannel);
        }
      }
      currentChannel = null;
    }
  }

  return channels;
}
