export type MediaType = "movie" | "series";

export type ProviderKind = "moviebox" | "fourkhdhub" | "dramachi" | "play4u" | "tv" | "reanime";

export interface CatalogItem {
  id: string;
  provider: ProviderKind;
  title: string;
  mediaType: MediaType;
  year?: string;
  posterUrl?: string;
  seasonCount?: number;
}

export interface SeasonInfo {
  seasonNumber: number;
  episodeCount: number;
}

export interface MediaDetails {
  id: string;
  provider: ProviderKind;
  title: string;
  mediaType: MediaType;
  year?: string;
  description?: string;
  posterUrl?: string;
  duration?: string;
  genres: string[];
  seasons: SeasonInfo[];
  director?: string;
  stars?: string;
  rating?: string;
}

export interface ReleaseStream {
  quality: string;
  format: "DASH" | "MP4" | "MKV" | "HLS";
  url: string;
  headers: Record<string, string>;
  sizeBytes?: number;
  directFile: boolean;
  codec?: string;
  label?: string;
  audioLangs?: string;
}

export interface SubtitleOption {
  language: string;
  url: string;
}

export interface StreamResponse {
  provider: ProviderKind;
  id: string;
  title: string;
  season?: number;
  episode?: number;
  streams: ReleaseStream[];
  subtitles: SubtitleOption[];
}

export interface TVChannel {
  id: string;
  name: string;
  streamUrl: string;
  logoUrl?: string;
  groupTitle?: string;
}
