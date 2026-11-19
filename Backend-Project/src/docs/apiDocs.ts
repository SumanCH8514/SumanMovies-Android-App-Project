export interface ApiParam {
  name: string;
  in: "query" | "path" | "body" | "header";
  type: string;
  required: boolean;
  description: string;
}

export interface ApiEndpoint {
  category: string;
  method: "GET" | "POST";
  path: string;
  description: string;
  params: ApiParam[];
  requestBody?: any;
  exampleUrl: string;
  curlExample: string;
  responsePreview: any;
}

export function getEndpoints(baseUrl: string): ApiEndpoint[] {
  return [
    {
      category: "Providers",
      method: "GET",
      path: "/api/v1/providers",
      description: "List all supported streaming providers (MovieBox, Play4U, 4KHDHub, Dramachi, ReAnime, Live TV) and their streaming capabilities.",
      params: [],
      exampleUrl: `${baseUrl}/api/v1/providers`,
      curlExample: `curl -s "${baseUrl}/api/v1/providers"`,
      responsePreview: {
        providers: [
          {
            id: "play4u",
            name: "Play4U",
            capabilities: {
              search: true,
              pagination: true,
              series: true,
              subtitles: true,
              formats: ["HLS"],
            },
          },
          {
            id: "moviebox",
            name: "MovieBox",
            capabilities: {
              search: true,
              pagination: true,
              series: true,
              subtitles: true,
              formats: ["DASH", "MP4"],
            },
          },
        ],
      },
    },
    {
      category: "Search",
      method: "GET",
      path: "/api/v1/search",
      description: "Unified media search across all providers simultaneously, or filtered to a single provider.",
      params: [
        {
          name: "q",
          in: "query",
          type: "string",
          required: true,
          description: "Search keyword, movie or series title",
        },
        {
          name: "provider",
          in: "query",
          type: "string",
          required: false,
          description: "Optional provider filter: play4u, moviebox, fourkhdhub, dramachi, reanime",
        },
      ],
      exampleUrl: `${baseUrl}/api/v1/search?q=Mirzapur&provider=play4u`,
      curlExample: `curl -s "${baseUrl}/api/v1/search?q=Mirzapur&provider=play4u"`,
      responsePreview: {
        provider: "play4u",
        count: 2,
        data: [
          {
            id: "play4u:tt6473300",
            provider: "play4u",
            title: "Mirzapur",
            mediaType: "series",
            year: "2018",
            posterUrl: "https://image.tmdb.org/t/p/w342/...",
          },
        ],
      },
    },
    {
      category: "Media Details",
      method: "GET",
      path: "/api/v1/media/:provider/:id",
      description: "Retrieve comprehensive metadata, synopsis, cast, and season/episode hierarchy for a specific title.",
      params: [
        {
          name: "provider",
          in: "path",
          type: "string",
          required: true,
          description: "Target provider: play4u, moviebox, fourkhdhub, dramachi, reanime",
        },
        {
          name: "id",
          in: "path",
          type: "string",
          required: true,
          description: "Media ID, IMDb ID (e.g. tt6473300), or provider slug",
        },
      ],
      exampleUrl: `${baseUrl}/api/v1/media/play4u/tt6473300`,
      curlExample: `curl -s "${baseUrl}/api/v1/media/play4u/tt6473300"`,
      responsePreview: {
        data: {
          id: "play4u:tt6473300",
          provider: "play4u",
          title: "Mirzapur",
          mediaType: "series",
          year: "2018",
          description: "A shocking incident at a wedding procession ignites a series of events...",
          posterUrl: "https://image.tmdb.org/t/p/w500/...",
          seasons: [
            { seasonNumber: 1, episodeCount: 9 },
            { seasonNumber: 2, episodeCount: 10 },
            { seasonNumber: 3, episodeCount: 10 },
          ],
        },
      },
    },
    {
      category: "Stream Resolver",
      method: "GET",
      path: "/api/v1/streams/:provider/:id",
      description: "Universal stream resolution endpoint. Returns direct streaming URLs (HLS, DASH, MP4), stream headers, and subtitle tracks.",
      params: [
        {
          name: "provider",
          in: "path",
          type: "string",
          required: true,
          description: "Target provider: play4u, moviebox, fourkhdhub, dramachi, reanime",
        },
        {
          name: "id",
          in: "path",
          type: "string",
          required: true,
          description: "Media ID or IMDb ID (e.g. tt27988879)",
        },
        {
          name: "season",
          in: "query",
          type: "number",
          required: false,
          description: "Season number (required for series, e.g. 1)",
        },
        {
          name: "episode",
          in: "query",
          type: "number",
          required: false,
          description: "Episode number (required for series, e.g. 1)",
        },
        {
          name: "title",
          in: "query",
          type: "string",
          required: false,
          description: "Optional title hint for fuzzy catalog matching",
        },
        {
          name: "year",
          in: "query",
          type: "string",
          required: false,
          description: "Optional release year hint",
        },
      ],
      exampleUrl: `${baseUrl}/api/v1/streams/play4u/tt27988879`,
      curlExample: `curl -s "${baseUrl}/api/v1/streams/play4u/tt27988879"`,
      responsePreview: {
        data: {
          provider: "play4u",
          id: "tt27988879",
          title: "Ramayana: Rise of a Legend",
          streams: [
            {
              quality: "1080p",
              format: "HLS",
              url: "https://neopulse.vip/range/frtndWFuTxKCY2f_HIWl4NjrBZjT8q5RP6vCma60VGTscnVpnkRiHvzA_ilF3BG-ScIbgSokxeHK1M2he2bSgKjkATAAdzRHq2xSfsHdXmR0hXl81rSU7w.css",
              label: "Play4U - 1080p (HI, TA, TE) HLS",
              directFile: false,
              headers: {
                "User-Agent": "Mozilla/5.0 (Linux; Android 13; SM-S908B) ...",
                Referer: "https://play4u.org/",
                Origin: "https://play4u.org",
              },
            },
          ],
          subtitles: [
            {
              language: "English",
              url: "https://play4u.org/subtitles/...",
            },
          ],
        },
      },
    },
    {
      category: "Play4U Direct",
      method: "GET",
      path: "/api/v1/play4u/direct",
      description: "Direct Play4U stream resolution endpoint via GET query parameters.",
      params: [
        {
          name: "id",
          in: "query",
          type: "string",
          required: false,
          description: "IMDb ID (tt...), Play4U ID, or slug",
        },
        {
          name: "title",
          in: "query",
          type: "string",
          required: false,
          description: "Media title (e.g. Ramayana, Mirzapur)",
        },
        {
          name: "season",
          in: "query",
          type: "number",
          required: false,
          description: "Season number (e.g. 1)",
        },
        {
          name: "episode",
          in: "query",
          type: "number",
          required: false,
          description: "Episode number (e.g. 1)",
        },
        {
          name: "year",
          in: "query",
          type: "string",
          required: false,
          description: "Release year (e.g. 2026)",
        },
      ],
      exampleUrl: `${baseUrl}/api/v1/play4u/direct?id=tt27988879&title=Ramayana`,
      curlExample: `curl -s "${baseUrl}/api/v1/play4u/direct?id=tt27988879&title=Ramayana"`,
      responsePreview: {
        provider: "play4u",
        id: "tt27988879",
        title: "Ramayana: Rise of a Legend",
        streams: [
          {
            quality: "1080p",
            format: "HLS",
            url: "https://neopulse.vip/range/...",
            label: "Play4U - 1080p (Multi-Audio) HLS",
            directFile: false,
            headers: {
              "User-Agent": "Mozilla/5.0 ...",
              Referer: "https://play4u.org/",
              Origin: "https://play4u.org",
            },
          },
        ],
        subtitles: [],
      },
    },
    {
      category: "Play4U Direct",
      method: "POST",
      path: "/api/v1/play4u/direct",
      description: "Direct Play4U stream resolution endpoint via JSON body payload.",
      params: [],
      requestBody: {
        id: "tt6473300",
        title: "Mirzapur",
        season: 1,
        episode: 1,
      },
      exampleUrl: `${baseUrl}/api/v1/play4u/direct`,
      curlExample: `curl -s -X POST "${baseUrl}/api/v1/play4u/direct" -H "Content-Type: application/json" -d '{"id":"tt6473300","season":1,"episode":1}'`,
      responsePreview: {
        provider: "play4u",
        id: "tt6473300",
        title: "Mirzapur",
        season: 1,
        episode: 1,
        streams: [
          {
            quality: "1080p",
            format: "HLS",
            url: "https://uprising.best/range/IOCW_H-i6iZGFW4ABch0SjM5XQbG_CL4bDHSs8wsD42nX-3qcx3KjMfz5ERon3kLVJBQQvjAnQRbU2UxBuvcKZNTE-47ibFeFUM9fekLSOSHP_Z9CyQ.png",
            label: "Play4U - 1080p (Multi-Audio) HLS",
            directFile: false,
            headers: {
              "User-Agent": "Mozilla/5.0 (Linux; Android 13; SM-S908B) ...",
              Referer: "https://play4u.org/",
              Origin: "https://play4u.org",
            },
          },
        ],
        subtitles: [
          {
            language: "English",
            url: "https://play4u.org/subtitles/...",
          },
        ],
      },
    },
    {
      category: "Play4U Cipher Utility",
      method: "POST",
      path: "/api/v1/play4u/unseal",
      description: "Decrypt raw Play4U XOR cipher tokens (pa!~...) into cleartext master playlist URLs.",
      params: [],
      requestBody: {
        sealed: "pa!~GBVABV5JW10LBAJdBgkSCQMAWABORhRDFBFd...",
        key: "pa4u-stream-seal-v1",
      },
      exampleUrl: `${baseUrl}/api/v1/play4u/unseal`,
      curlExample: `curl -s -X POST "${baseUrl}/api/v1/play4u/unseal" -H "Content-Type: application/json" -d '{"sealed":"pa!~GBVABV5JW10LBAJdBgkSCQMAWABORhRDFBFd..."}'`,
      responsePreview: {
        unsealed: "https://neopulse.vip/range/frtndWFuTxKCY2f_HIWl4NjrBZjT8q5RP6vCma60VGTscnVpnkRiHvzA_ilF3BG-ScIbgSokxeHK1M2he2bSgKjkATAAdzRHq2xSfsHdXmR0hXl81rSU7w.css",
      },
    },
    {
      category: "MovieBox Signer",
      method: "POST",
      path: "/api/v1/moviebox/sign",
      description: "Generate HMAC MD5 signed request headers required to authenticate with MovieBox mobile backend nodes.",
      params: [],
      requestBody: {
        method: "GET",
        url: "/wefeed-mobile-bff/detail-api/get-item-detail?id=123",
        body: null,
        token: null,
      },
      exampleUrl: `${baseUrl}/api/v1/moviebox/sign`,
      curlExample: `curl -s -X POST "${baseUrl}/api/v1/moviebox/sign" -H "Content-Type: application/json" -d '{"url":"/wefeed-mobile-bff/detail-api/get-item-detail?id=123"}'`,
      responsePreview: {
        headers: {
          "x-client-info": "...",
          "x-request-sign": "...",
          "x-timestamp": 1731998000,
          "User-Agent": "...",
        },
      },
    },
    {
      category: "Dramachi",
      method: "GET",
      path: "/api/v1/dramachi/search",
      description: "Search Asian dramas, K-dramas, anime, and movies on the Dramachi provider.",
      params: [
        {
          name: "q",
          in: "query",
          type: "string",
          required: true,
          description: "Drama title or keyword (e.g. Squid Game, Queen of Tears)",
        },
        {
          name: "page",
          in: "query",
          type: "number",
          required: false,
          description: "Page number (defaults to 1)",
        },
      ],
      exampleUrl: `${baseUrl}/api/v1/dramachi/search?q=Squid%20Game`,
      curlExample: `curl -s "${baseUrl}/api/v1/dramachi/search?q=Squid%20Game"`,
      responsePreview: {
        provider: "dramachi",
        count: 2,
        data: [
          {
            id: "44459",
            provider: "dramachi",
            title: "Squid Game",
            mediaType: "series",
            year: "2021",
            posterUrl: "https://static.nodeobjects.com/thumbnail/squidgamektv.jpg",
          },
        ],
      },
    },
    {
      category: "Dramachi",
      method: "GET",
      path: "/api/v1/dramachi/details/:id",
      description: "Retrieve comprehensive drama details, storyline, season groupings, and episode counts from Dramachi.",
      params: [
        {
          name: "id",
          in: "path",
          type: "string",
          required: true,
          description: "Dramachi title ID (e.g. 44459)",
        },
      ],
      exampleUrl: `${baseUrl}/api/v1/dramachi/details/44459`,
      curlExample: `curl -s "${baseUrl}/api/v1/dramachi/details/44459"`,
      responsePreview: {
        data: {
          id: "44459",
          provider: "dramachi",
          title: "Squid Game",
          mediaType: "series",
          year: "2021",
          description: "Hundreds of cash-strapped players accept a strange invitation to compete in children's games...",
          posterUrl: "https://static.nodeobjects.com/thumbnail/squidgamektv.jpg",
          genres: ["Drama", "Action", "Mystery"],
          seasons: [
            { seasonNumber: 1, episodeCount: 9 },
            { seasonNumber: 2, episodeCount: 7 },
            { seasonNumber: 3, episodeCount: 6 },
          ],
        },
      },
    },
    {
      category: "Dramachi",
      method: "GET",
      path: "/api/v1/dramachi/direct",
      description: "Direct Dramachi CDN video stream resolver via query parameters. Returns direct fast CDN MKV/MP4 links.",
      params: [
        {
          name: "id",
          in: "query",
          type: "string",
          required: true,
          description: "Dramachi title ID (e.g. 44459)",
        },
        {
          name: "season",
          in: "query",
          type: "number",
          required: false,
          description: "Season number (defaults to 1)",
        },
        {
          name: "episode",
          in: "query",
          type: "number",
          required: false,
          description: "Episode number (defaults to 1)",
        },
      ],
      exampleUrl: `${baseUrl}/api/v1/dramachi/direct?id=44459&season=1&episode=1`,
      curlExample: `curl -s "${baseUrl}/api/v1/dramachi/direct?id=44459&season=1&episode=1"`,
      responsePreview: {
        provider: "dramachi",
        id: "44459",
        title: "Dramachi Stream 44459",
        season: 1,
        episode: 1,
        streams: [
          {
            quality: "720p",
            format: "MKV",
            url: "https://o4nbr1fc.pdkvzojivp.xyz/cdn/zone1/K_Drama/Squid_Game/Season_01/Squid_Game_S01E01.mkv",
            headers: {},
            directFile: true,
            label: "Dramachi CDN 720p",
          },
        ],
        subtitles: [],
      },
    },
    {
      category: "Dramachi",
      method: "POST",
      path: "/api/v1/dramachi/direct",
      description: "Direct Dramachi CDN video stream resolver via JSON body payload.",
      params: [],
      requestBody: {
        id: "44459",
        season: 1,
        episode: 1,
      },
      exampleUrl: `${baseUrl}/api/v1/dramachi/direct`,
      curlExample: `curl -s -X POST "${baseUrl}/api/v1/dramachi/direct" -H "Content-Type: application/json" -d '{"id":"44459","season":1,"episode":1}'`,
      responsePreview: {
        provider: "dramachi",
        id: "44459",
        title: "Dramachi Stream 44459",
        season: 1,
        episode: 1,
        streams: [
          {
            quality: "720p",
            format: "MKV",
            url: "https://o4nbr1fc.pdkvzojivp.xyz/cdn/zone1/K_Drama/Squid_Game/Season_01/Squid_Game_S01E01.mkv",
            headers: {},
            directFile: true,
            label: "Dramachi CDN 720p",
          },
        ],
        subtitles: [],
      },
    },
    {
      category: "ReAnime",
      method: "GET",
      path: "/api/v1/reanime/search",
      description: "Search ReAnime anime catalog for titles, episodes, and AniList identifiers.",
      params: [
        {
          name: "q",
          in: "query",
          type: "string",
          required: true,
          description: "Anime title or keyword (e.g. Naruto, Bleach, Attack on Titan)",
        },
      ],
      exampleUrl: `${baseUrl}/api/v1/reanime/search?q=Naruto`,
      curlExample: `curl -s "${baseUrl}/api/v1/reanime/search?q=Naruto"`,
      responsePreview: {
        provider: "reanime",
        count: 1,
        data: [
          {
            id: "naruto-bjfend",
            provider: "reanime",
            title: "Naruto",
            mediaType: "series",
            year: "2002",
            posterUrl: "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx20-dE6UHbFFg1A5.jpg",
            seasonCount: 1,
          },
        ],
      },
    },
    {
      category: "ReAnime",
      method: "GET",
      path: "/api/v1/reanime/anilist/details",
      description: "Retrieve comprehensive anime metadata, storyline, status, and episodes by AniList ID.",
      params: [
        {
          name: "id",
          in: "query",
          type: "number",
          required: true,
          description: "Numeric AniList anime ID (e.g. 20)",
        },
      ],
      exampleUrl: `${baseUrl}/api/v1/reanime/anilist/details?id=20`,
      curlExample: `curl -s "${baseUrl}/api/v1/reanime/anilist/details?id=20"`,
      responsePreview: {
        provider: "reanime",
        anilistId: 20,
        data: {
          id: "20",
          provider: "reanime",
          title: "Naruto",
          mediaType: "series",
          year: "2002",
          description: "Naruto Uzumaki, a hyperactive and knuckle-headed ninja...",
          posterUrl: "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx20-dE6UHbFFg1A5.jpg",
          duration: "23m",
          genres: ["Action", "Adventure", "Comedy"],
          seasons: [{ seasonNumber: 1, episodeCount: 220 }],
          rating: "8.0/10",
        },
      },
    },
    {
      category: "ReAnime",
      method: "GET",
      path: "/api/v1/reanime/mal/details",
      description: "Retrieve comprehensive anime metadata, storyline, status, and episodes by MyAnimeList (MAL) ID.",
      params: [
        {
          name: "id",
          in: "query",
          type: "number",
          required: true,
          description: "Numeric MyAnimeList (MAL) ID (e.g. 20)",
        },
      ],
      exampleUrl: `${baseUrl}/api/v1/reanime/mal/details?id=20`,
      curlExample: `curl -s "${baseUrl}/api/v1/reanime/mal/details?id=20"`,
      responsePreview: {
        provider: "reanime",
        malId: 20,
        data: {
          id: "20",
          provider: "reanime",
          title: "Naruto",
          mediaType: "series",
          year: "2002",
          description: "Naruto Uzumaki, a hyperactive and knuckle-headed ninja...",
          posterUrl: "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx20-dE6UHbFFg1A5.jpg",
          duration: "23m",
          genres: ["Action", "Adventure", "Comedy"],
          seasons: [{ seasonNumber: 1, episodeCount: 220 }],
          rating: "8.0/10",
        },
      },
    },
    {
      category: "ReAnime",
      method: "GET",
      path: "/api/v1/reanime/anilist/streams",
      description: "Resolve embeds, direct HLS streams, direct MKV downloads, and subtitles by AniList ID and episode number.",
      params: [
        {
          name: "id",
          in: "query",
          type: "number",
          required: true,
          description: "Numeric AniList anime ID (e.g. 20)",
        },
        {
          name: "episode",
          in: "query",
          type: "number",
          required: true,
          description: "Episode number (e.g. 1)",
        },
        {
          name: "mal",
          in: "query",
          type: "number",
          required: false,
          description: "Optional MyAnimeList ID hint",
        },
      ],
      exampleUrl: `${baseUrl}/api/v1/reanime/anilist/streams?id=20&episode=1`,
      curlExample: `curl -s "${baseUrl}/api/v1/reanime/anilist/streams?id=20&episode=1"`,
      responsePreview: {
        provider: "reanime",
        anilistId: 20,
        episode: 1,
        embeds: [
          { server: "HD-1", type: "sub", url: "https://flixcloud.cc/e/bo2qdw3m3kjf?v=1" },
          { server: "HD-1", type: "dub", url: "https://flixcloud.cc/e/bo2qdw3m3kjf?v=1" },
          { server: "HD-2", type: "sub", url: "https://flixcloud.cc/e/bo2qdw3m3kjf?v=2" },
          { server: "H-SUB", type: "sub", hardsub: true, url: "https://flixcloud.cc/e/bo2qdw3m3kjf?v=3" },
        ],
        streams: [
          {
            quality: "Auto",
            format: "HLS",
            url: "https://fetch9.flixcloud.cc/_v7/09e32e55-0b53-41df-b46e-934dbafea437/master.m3u8?token=...",
            headers: { Referer: "https://flixcloud.cc/", "User-Agent": "Mozilla/5.0" },
            directFile: false,
            label: "FlixCloud HLS (Adaptive)",
          },
        ],
        downloads: [
          {
            quality: "480p",
            format: "MKV",
            filename: "Naruto.001.v4.480p.DVD.Dual-Audio.FLAC2.0.Hi10P.x264-JySzE.mkv",
            sizeBytes: 194829467,
            sizeHuman: "186 MB",
            url: "https://dl.spacedl.top/dl/09e32e55-0b53-41df-b46e-934dbafea437/ff80c550d2cc9d48.mkv?exp=1795089824&sig=...",
            headers: { Referer: "https://flixcloud.cc/", "User-Agent": "Mozilla/5.0" },
            directFile: true,
          },
        ],
        subtitles: [
          {
            language: "English ([SoM] English)",
            url: "https://vault-93.rundowncdn.top/subtitles/09e32e55-0b53-41df-b46e-934dbafea437/09e32e55-0b53-41df-b46e-934dbafea437_eng_3.ass",
          },
        ],
      },
    },
    {
      category: "ReAnime",
      method: "GET",
      path: "/api/v1/reanime/mal/streams",
      description: "Resolve embeds, direct HLS streams, direct MKV downloads, and subtitles by MyAnimeList (MAL) ID and episode number.",
      params: [
        {
          name: "id",
          in: "query",
          type: "number",
          required: true,
          description: "Numeric MyAnimeList (MAL) ID (e.g. 20)",
        },
        {
          name: "episode",
          in: "query",
          type: "number",
          required: true,
          description: "Episode number (e.g. 1)",
        },
      ],
      exampleUrl: `${baseUrl}/api/v1/reanime/mal/streams?id=20&episode=1`,
      curlExample: `curl -s "${baseUrl}/api/v1/reanime/mal/streams?id=20&episode=1"`,
      responsePreview: {
        provider: "reanime",
        malId: 20,
        episode: 1,
        embeds: [
          { server: "HD-1", type: "sub", url: "https://flixcloud.cc/e/bo2qdw3m3kjf?v=1" },
          { server: "HD-1", type: "dub", url: "https://flixcloud.cc/e/bo2qdw3m3kjf?v=1" },
        ],
        streams: [
          {
            quality: "Auto",
            format: "HLS",
            url: "https://fetch9.flixcloud.cc/_v7/09e32e55-0b53-41df-b46e-934dbafea437/master.m3u8?token=...",
            headers: { Referer: "https://flixcloud.cc/", "User-Agent": "Mozilla/5.0" },
            directFile: false,
          },
        ],
        downloads: [
          {
            quality: "480p",
            format: "MKV",
            filename: "Naruto.001.v4.480p.DVD.Dual-Audio.FLAC2.0.Hi10P.x264-JySzE.mkv",
            sizeBytes: 194829467,
            sizeHuman: "186 MB",
            url: "https://dl.spacedl.top/dl/09e32e55-0b53-41df-b46e-934dbafea437/ff80c550d2cc9d48.mkv?exp=1795089824&sig=...",
            headers: { Referer: "https://flixcloud.cc/", "User-Agent": "Mozilla/5.0" },
            directFile: true,
          },
        ],
        subtitles: [],
      },
    },
    {
      category: "ReAnime",
      method: "GET",
      path: "/api/v1/reanime/direct",
      description: "Direct FlixCloud/SpaceDL MKV download URL generator from access ID.",
      params: [
        {
          name: "id",
          in: "query",
          type: "string",
          required: true,
          description: "FlixCloud access ID (e.g. bo2qdw3m3kjf)",
        },
      ],
      exampleUrl: `${baseUrl}/api/v1/reanime/direct?id=bo2qdw3m3kjf`,
      curlExample: `curl -s "${baseUrl}/api/v1/reanime/direct?id=bo2qdw3m3kjf"`,
      responsePreview: {
        provider: "reanime",
        data: {
          quality: "480p",
          format: "MKV",
          filename: "Naruto.001.v4.480p.DVD.Dual-Audio.FLAC2.0.Hi10P.x264-JySzE.mkv",
          sizeBytes: 194829467,
          sizeHuman: "186 MB",
          url: "https://dl.spacedl.top/dl/09e32e55-0b53-41df-b46e-934dbafea437/ff80c550d2cc9d48.mkv?exp=1795089824&sig=3f37899a261e7c9c460c17478dd5b26468bbaaa37c8b8d796ad3b70aee163b6e",
          headers: { Referer: "https://flixcloud.cc/", "User-Agent": "Mozilla/5.0" },
          directFile: true,
        },
      },
    },
    {
      category: "Live TV",
      method: "GET",
      path: "/api/v1/tv/channels",
      description: "Fetch live IPTV channel streams with logo metadata, category tags, and stream URLs.",
      params: [
        {
          name: "category",
          in: "query",
          type: "string",
          required: false,
          description: "Filter channels by category/genre (e.g. sports, news, entertainment, movies)",
        },
        {
          name: "url",
          in: "query",
          type: "string",
          required: false,
          description: "Optional custom remote M3U playlist URL",
        },
      ],
      exampleUrl: `${baseUrl}/api/v1/tv/channels?category=sports`,
      curlExample: `curl -s "${baseUrl}/api/v1/tv/channels?category=sports"`,
      responsePreview: {
        count: 24,
        data: [
          {
            id: "sports-1",
            name: "Star Sports 1 HD",
            streamUrl: "https://...",
            logoUrl: "https://...",
            groupTitle: "Sports",
          },
        ],
      },
    },
    {
      category: "Live TV",
      method: "GET",
      path: "/api/v1/iptv/channels",
      description: "Edge-cached IPTV-Org Channels API proxy (https://iptv-org.github.io/api/channels.json) with filter support by country, category, search query, NSFW filter, pagination, and optional metadata wrap.",
      params: [
        {
          name: "country",
          in: "query",
          type: "string",
          required: false,
          description: "Two-letter ISO country code (e.g. IN, US, UK, CA, FR, DE)",
        },
        {
          name: "category",
          in: "query",
          type: "string",
          required: false,
          description: "Channel category (e.g. sports, news, movies, animation, kids, music)",
        },
        {
          name: "q",
          in: "query",
          type: "string",
          required: false,
          description: "Search filter against channel name, network, or alternative names",
        },
        {
          name: "nsfw",
          in: "query",
          type: "boolean",
          required: false,
          description: "Filter out NSFW channels (false) or only return NSFW channels (true)",
        },
        {
          name: "limit",
          in: "query",
          type: "number",
          required: false,
          description: "Maximum number of channels to return (defaults to all matching channels)",
        },
        {
          name: "page",
          in: "query",
          type: "number",
          required: false,
          description: "Page number when using limit (1-indexed)",
        },
        {
          name: "wrap",
          in: "query",
          type: "boolean",
          required: false,
          description: "When true, returns object with total, count, and data array. Defaults to raw JSON array.",
        },
      ],
      exampleUrl: `${baseUrl}/api/v1/iptv/channels?country=IN&category=sports&limit=50&wrap=true`,
      curlExample: `curl -s "${baseUrl}/api/v1/iptv/channels?country=IN&category=sports&limit=50&wrap=true"`,
      responsePreview: {
        total: 18,
        count: 18,
        page: 1,
        limit: 50,
        data: [
          {
            id: "StarSports1HD.in",
            name: "Star Sports 1 HD",
            native_name: null,
            network: "Star India",
            owners: ["The Walt Disney Company India"],
            country: "IN",
            subdivision: null,
            city: null,
            broadcast_area: ["r/IND"],
            languages: ["hin", "eng"],
            categories: ["sports"],
            is_nsfw: false,
            launched: "2013-11-06",
            closed: null,
            replaced_by: null,
            website: "https://www.hotstar.com",
            logo: "https://i.imgur.com/example.png",
          },
        ],
      },
    },
  ];
}

export function getApiDocsJson(baseUrl: string) {
  const endpoints = getEndpoints(baseUrl);
  return {
    name: "SumanMovies Streaming Backend API",
    status: "healthy",
    version: "1.0.0",
    baseUrl,
    docsUrl: `${baseUrl}/docs`,
    endpointsCount: endpoints.length,
    endpoints: endpoints.map((ep) => ({
      category: ep.category,
      method: ep.method,
      path: ep.path,
      description: ep.description,
      parameters: ep.params,
      requestBody: ep.requestBody,
      exampleUrl: ep.exampleUrl,
      curlExample: ep.curlExample,
    })),
  };
}

export function renderApiDocsHtml(baseUrl: string): string {
  const endpoints = getEndpoints(baseUrl);
  const categories = Array.from(new Set(endpoints.map((e) => e.category)));

  const navPillsHtml = categories
    .map(
      (cat) =>
        `<a href="#cat-${cat.toLowerCase().replace(/[^a-z0-9]/g, "-")}" class="nav-pill">${cat}</a>`
    )
    .join("");

  const endpointsHtml = categories
    .map((cat) => {
      const items = endpoints.filter((e) => e.category === cat);
      const itemsHtml = items
        .map((ep, idx) => {
          const methodClass = ep.method === "GET" ? "badge-get" : "badge-post";
          const cardId = `ep-${cat.toLowerCase().replace(/[^a-z0-9]/g, "-")}-${idx}`;

          const paramsTable =
            ep.params.length > 0
              ? `
            <div class="table-wrap">
              <table class="params-table">
                <thead>
                  <tr>
                    <th>Parameter</th>
                    <th>In</th>
                    <th>Type</th>
                    <th>Required</th>
                    <th>Description</th>
                  </tr>
                </thead>
                <tbody>
                  ${ep.params
                    .map(
                      (p) => `
                    <tr>
                      <td class="param-name"><code>${p.name}</code></td>
                      <td><span class="param-in">${p.in}</span></td>
                      <td><span class="param-type">${p.type}</span></td>
                      <td>${p.required ? '<span class="tag-req">Required</span>' : '<span class="tag-opt">Optional</span>'}</td>
                      <td class="param-desc">${p.description}</td>
                    </tr>
                  `
                    )
                    .join("")}
                </tbody>
              </table>
            </div>
          `
              : "";

          const bodyBlock = ep.requestBody
            ? `
            <div class="meta-row">
              <div class="sub-heading">Request Payload (JSON)</div>
              <pre class="code-block"><code>${JSON.stringify(ep.requestBody, null, 2)}</code></pre>
            </div>
          `
            : "";

          const previewBlock = `
            <div class="meta-row">
              <div class="sub-heading">Example Response</div>
              <pre class="code-block"><code>${JSON.stringify(ep.responsePreview, null, 2)}</code></pre>
            </div>
          `;

          const tryLink =
            ep.method === "GET"
              ? `<a href="${ep.exampleUrl}" target="_blank" rel="noopener noreferrer" class="btn-secondary">Test Live ↗</a>`
              : "";

          return `
            <article class="endpoint-card" id="${cardId}">
              <header class="endpoint-header">
                <span class="method-badge ${methodClass}">${ep.method}</span>
                <span class="endpoint-path">${ep.path}</span>
                ${tryLink}
              </header>
              <p class="endpoint-desc">${ep.description}</p>
              ${paramsTable}
              ${bodyBlock}
              <div class="meta-row">
                <div class="curl-header">
                  <span class="sub-heading">cURL Request</span>
                  <button type="button" class="btn-copy" onclick="copyCode(this, '${encodeURIComponent(ep.curlExample)}')">Copy cURL</button>
                </div>
                <div class="curl-box">
                  <code>${ep.curlExample}</code>
                </div>
              </div>
              ${previewBlock}
            </article>
          `;
        })
        .join("");

      return `
        <section class="category-section" id="cat-${cat.toLowerCase().replace(/[^a-z0-9]/g, "-")}">
          <div class="category-header">
            <h2 class="category-title">${cat}</h2>
            <span class="category-count">${items.length} ${items.length === 1 ? "endpoint" : "endpoints"}</span>
          </div>
          <div class="category-grid">
            ${itemsHtml}
          </div>
        </section>
      `;
    })
    .join("");

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>SumanMovies Edge Streaming Gateway API</title>
  <style>
    :root {
      --bg: #0d1117;
      --card-bg: #161b22;
      --card-alt: #0e131b;
      --code-bg: #090d12;
      --border: #30363d;
      --border-hover: #484f58;
      --text-main: #f0f6fc;
      --text-muted: #8b949e;
      --text-sub: #6e7681;
      --brand: #f59e0b;
      --brand-hover: #d97706;
      --badge-get-bg: #0d281e;
      --badge-get-text: #3fb950;
      --badge-get-border: #1b4731;
      --badge-post-bg: #1d1b38;
      --badge-post-text: #a371f7;
      --badge-post-border: #382e66;
    }
    * {
      box-sizing: border-box;
      margin: 0;
      padding: 0;
    }
    html {
      scroll-behavior: smooth;
    }
    body {
      background: var(--bg);
      color: var(--text-main);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      line-height: 1.5;
      padding-bottom: 64px;
      -webkit-font-smoothing: antialiased;
    }
    header {
      background: #161b22;
      border-bottom: 1px solid var(--border);
      position: sticky;
      top: 0;
      z-index: 100;
      padding: 12px 24px;
    }
    .header-content {
      max-width: 1200px;
      margin: 0 auto;
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 16px;
    }
    .logo-group {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .logo-badge {
      width: 28px;
      height: 28px;
      border-radius: 6px;
      background: var(--brand);
      color: #000;
      font-weight: 800;
      font-size: 15px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .logo-title {
      font-size: 14px;
      font-weight: 700;
      color: var(--text-main);
      letter-spacing: -0.01em;
    }
    .status-group {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .status-indicator {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      background: var(--badge-get-bg);
      border: 1px solid var(--badge-get-border);
      color: var(--badge-get-text);
      padding: 4px 10px;
      border-radius: 6px;
      font-size: 12px;
      font-weight: 600;
    }
    .status-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: var(--badge-get-text);
    }
    .btn-ghost {
      color: var(--text-muted);
      font-size: 12px;
      font-weight: 600;
      text-decoration: none;
      padding: 5px 12px;
      border: 1px solid var(--border);
      border-radius: 6px;
      transition: all 150ms ease-out;
    }
    .btn-ghost:hover {
      color: var(--text-main);
      border-color: var(--border-hover);
      background: rgba(255, 255, 255, 0.04);
    }
    .container {
      max-width: 1200px;
      margin: 24px auto 0;
      padding: 0 24px;
    }
    .hero-panel {
      background: var(--card-bg);
      border: 1px solid var(--border);
      border-radius: 8px;
      padding: 24px;
      margin-bottom: 24px;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .hero-top {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 16px;
      flex-wrap: wrap;
    }
    .hero-info h1 {
      font-size: 20px;
      font-weight: 800;
      letter-spacing: -0.02em;
      color: var(--text-main);
      margin-bottom: 6px;
    }
    .hero-info p {
      font-size: 13px;
      color: var(--text-muted);
      max-width: 760px;
      line-height: 1.6;
    }
    .hero-actions {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .btn-primary {
      background: var(--brand);
      color: #000;
      font-weight: 700;
      font-size: 13px;
      padding: 8px 16px;
      border-radius: 6px;
      border: 1px solid var(--brand);
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      transition: background 150ms ease-out;
    }
    .btn-primary:hover {
      background: var(--brand-hover);
      border-color: var(--brand-hover);
    }
    .base-url-box {
      display: flex;
      align-items: center;
      gap: 8px;
      background: var(--code-bg);
      border: 1px solid var(--border);
      padding: 8px 12px;
      border-radius: 6px;
      font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
      font-size: 12px;
      color: var(--text-muted);
    }
    .base-url-box strong {
      color: var(--text-main);
      font-weight: 600;
    }
    .nav-bar {
      display: flex;
      align-items: center;
      gap: 8px;
      overflow-x: auto;
      padding-bottom: 8px;
      margin-bottom: 24px;
      border-bottom: 1px solid var(--border);
    }
    .nav-pill {
      font-size: 12px;
      font-weight: 600;
      color: var(--text-muted);
      text-decoration: none;
      padding: 6px 12px;
      border-radius: 6px;
      border: 1px solid transparent;
      white-space: nowrap;
      transition: all 150ms ease-out;
    }
    .nav-pill:hover {
      color: var(--text-main);
      border-color: var(--border);
      background: var(--card-bg);
    }
    .category-section {
      margin-bottom: 32px;
    }
    .category-header {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      margin-bottom: 16px;
      padding-bottom: 8px;
      border-bottom: 1px solid var(--border);
    }
    .category-title {
      font-size: 15px;
      font-weight: 700;
      color: var(--text-main);
      letter-spacing: -0.01em;
    }
    .category-count {
      font-size: 12px;
      color: var(--text-sub);
    }
    .category-grid {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .endpoint-card {
      background: var(--card-bg);
      border: 1px solid var(--border);
      border-radius: 8px;
      padding: 20px;
      display: flex;
      flex-direction: column;
      gap: 12px;
      transition: border-color 150ms ease-out;
    }
    .endpoint-card:hover {
      border-color: var(--border-hover);
    }
    .endpoint-header {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-wrap: wrap;
    }
    .method-badge {
      font-size: 11px;
      font-weight: 700;
      padding: 2px 8px;
      border-radius: 4px;
      letter-spacing: 0.04em;
    }
    .badge-get {
      background: var(--badge-get-bg);
      color: var(--badge-get-text);
      border: 1px solid var(--badge-get-border);
    }
    .badge-post {
      background: var(--badge-post-bg);
      color: var(--badge-post-text);
      border: 1px solid var(--badge-post-border);
    }
    .endpoint-path {
      font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
      font-size: 13px;
      font-weight: 600;
      color: var(--text-main);
    }
    .btn-secondary {
      margin-left: auto;
      font-size: 12px;
      font-weight: 600;
      color: var(--text-muted);
      text-decoration: none;
      padding: 4px 10px;
      border-radius: 4px;
      border: 1px solid var(--border);
      background: transparent;
      transition: all 150ms ease-out;
    }
    .btn-secondary:hover {
      color: var(--text-main);
      border-color: var(--border-hover);
      background: rgba(255, 255, 255, 0.04);
    }
    .endpoint-desc {
      color: var(--text-muted);
      font-size: 13px;
      line-height: 1.5;
    }
    .meta-row {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .sub-heading {
      font-size: 11px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: var(--text-sub);
    }
    .table-wrap {
      overflow-x: auto;
      border-radius: 6px;
      border: 1px solid var(--border);
    }
    .params-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 12px;
      text-align: left;
    }
    .params-table th {
      background: var(--code-bg);
      padding: 8px 12px;
      font-weight: 600;
      color: var(--text-muted);
      border-bottom: 1px solid var(--border);
    }
    .params-table td {
      padding: 8px 12px;
      border-bottom: 1px solid var(--border);
      color: var(--text-muted);
    }
    .params-table tr:last-child td {
      border-bottom: none;
    }
    .param-name code {
      color: var(--text-main);
      font-weight: 600;
      font-family: ui-monospace, monospace;
    }
    .param-in {
      background: rgba(255, 255, 255, 0.06);
      padding: 2px 5px;
      border-radius: 4px;
      font-size: 10px;
      color: var(--text-sub);
    }
    .param-type {
      font-family: ui-monospace, monospace;
      color: var(--text-muted);
    }
    .tag-req {
      color: #f87171;
      font-weight: 600;
      font-size: 10px;
    }
    .tag-opt {
      color: var(--text-sub);
      font-size: 10px;
    }
    .curl-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .btn-copy {
      font-size: 11px;
      font-weight: 600;
      color: var(--text-muted);
      background: transparent;
      border: 1px solid var(--border);
      padding: 2px 8px;
      border-radius: 4px;
      cursor: pointer;
      transition: all 150ms ease-out;
    }
    .btn-copy:hover {
      color: var(--text-main);
      border-color: var(--border-hover);
      background: rgba(255, 255, 255, 0.04);
    }
    .curl-box {
      background: var(--code-bg);
      border: 1px solid var(--border);
      border-radius: 6px;
      padding: 10px 12px;
      overflow-x: auto;
      font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
      font-size: 12px;
      color: var(--text-main);
    }
    .code-block {
      background: var(--code-bg);
      border: 1px solid var(--border);
      border-radius: 6px;
      padding: 12px;
      overflow-x: auto;
      font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
      font-size: 11px;
      color: var(--text-muted);
      max-height: 240px;
    }
    .toast {
      position: fixed;
      bottom: 24px;
      right: 24px;
      background: #1f2937;
      border: 1px solid var(--border-hover);
      color: #fff;
      font-size: 12px;
      font-weight: 600;
      padding: 8px 16px;
      border-radius: 6px;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
      opacity: 0;
      transform: translateY(8px);
      transition: opacity 150ms ease-out, transform 150ms ease-out;
      pointer-events: none;
      z-index: 1000;
    }
    .toast.show {
      opacity: 1;
      transform: translateY(0);
    }
    @media (max-width: 768px) {
      header {
        padding: 12px 16px;
      }
      .container {
        padding: 0 16px;
        margin-top: 16px;
      }
      .hero-panel {
        padding: 16px;
      }
      .endpoint-card {
        padding: 16px;
      }
      .btn-secondary {
        margin-left: 0;
        width: 100%;
        text-align: center;
      }
    }
  </style>
</head>
<body>
  <header>
    <div class="header-content">
      <div class="logo-group">
        <div class="logo-badge">S</div>
        <div class="logo-title">SumanMovies Streaming Gateway</div>
      </div>
      <div class="status-group">
        <div class="status-indicator">
          <span class="status-dot"></span>
          Operational v1.0.0
        </div>
        <a href="${baseUrl}/docs/json" class="btn-ghost">JSON Spec</a>
      </div>
    </div>
  </header>

  <main class="container">
    <section class="hero-panel">
      <div class="hero-top">
        <div class="hero-info">
          <h1>Edge Streaming & Metadata API</h1>
          <p>High-performance edge reverse-proxy delivering low-latency stream manifests, HLS/DASH decryption, and direct high-speed CDN downloads across 6 integrated media networks: MovieBox, Play4U, 4KHDHub, Dramachi, ReAnime, and Live IPTV.</p>
        </div>
        <div class="hero-actions">
          <button type="button" class="btn-primary" onclick="copyBaseUrl('${baseUrl}')">
            <span>Copy Base URL</span>
          </button>
        </div>
      </div>
      <div class="base-url-box">
        <span>Base Endpoint:</span>
        <strong>${baseUrl}</strong>
      </div>
    </section>

    <nav class="nav-bar" aria-label="Category Navigation">
      ${navPillsHtml}
    </nav>

    ${endpointsHtml}
  </main>

  <div id="toast" class="toast">Copied to clipboard</div>

  <script>
    function showToast(text) {
      var toast = document.getElementById('toast');
      toast.textContent = text || 'Copied to clipboard';
      toast.classList.add('show');
      setTimeout(function() {
        toast.classList.remove('show');
      }, 2000);
    }
    function copyBaseUrl(url) {
      navigator.clipboard.writeText(url).then(function() {
        showToast('Base URL copied: ' + url);
      });
    }
    function copyCode(btn, encoded) {
      var code = decodeURIComponent(encoded);
      navigator.clipboard.writeText(code).then(function() {
        showToast('cURL command copied');
      });
    }
  </script>
</body>
</html>`;
}
