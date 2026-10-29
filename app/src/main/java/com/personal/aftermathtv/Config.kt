package com.personal.aftermathtv

/** An extra channel with a direct HLS stream (e.g. an Owncast server). */
data class ExtraChannel(
    val id: String,
    val title: String,
    val shortName: String,
    val hlsUrl: String,
    val note: String,
    val scheduleAsset: String = ""
)

/** Endpoints taken from the official Kodi addon and the public schedule API. */
object Config {
    const val CHANNELS_URL = "http://toonamiaftermath.com/tatv.json"
    const val STREAM_API = "http://api.toonamiaftermath.com:3000/streamUrl"
    const val SCHEDULE_URL = "https://api.toonamiaftermath.com/channelsCurrentMedia"

    // Guide colours: "gray" (silver cells, green selection) or "blue" (lavender cells, gold selection)
    const val GUIDE_THEME = "gray"

    // Retro CRT power-on animation at launch
    const val BOOT_ANIMATION = true

    // Text shown in the guide header
    const val WORDMARK = "aftermath tv"

    // Optional: free key from omdbapi.com. If set, show info comes from IMDb data
    // (via OMDb). If empty, the app uses TVmaze (free, no key).
    const val OMDB_KEY = ""

    // Optional: set a direct .m3u8 here to bypass the API entirely
    const val STREAM_URL_OVERRIDE = ""

    // Add your own direct .m3u8 channels here. Only add streams you are allowed to use.
    val EXTRA_CHANNELS: List<ExtraChannel> = listOf(
        // Third-party streams run by their owners. Remove any you don't have permission to use.
        ExtraChannel("retroblast", "Retro Blast", "RETRO BLAST", "https://retroblast.tv/hls/stream.m3u8", "SEE RETROBLAST.TV FOR THE SCHEDULE", "retroblast.json"),
        ExtraChannel("cnclassic", "CN Classic", "CN CLASSIC", "https://gateway33.vaughnsoft.net/vl/cnclassic.m3u8", "24/7 CLASSIC CARTOONS - NO LISTINGS"),
        ExtraChannel("thatsodisney", "ThatSoDisney", "THATSODISNEY", "https://gateway33.vaughnsoft.net/vl/thatsodisney.m3u8", "24/7 DISNEY CHANNEL CLASSICS - NO LISTINGS"),
        ExtraChannel("nickpicks", "NickPicks", "NICKPICKS", "https://gateway33.vaughnsoft.net/vl/nickpicks.m3u8", "24/7 NICK CLASSICS - NO LISTINGS"),
        ExtraChannel("nnretro", "NNRetro", "NNRETRO", "https://gateway33.vaughnsoft.net/vl/nnretro.m3u8", "24/7 RETRO NICK - NO LISTINGS"),
        // ExtraChannel("example", "Example Channel", "EXAMPLE", "https://example.com/hls/stream.m3u8", "NO LISTINGS")
    )

    val HEADERS: Map<String, String> = mapOf()
    const val RETRY_DELAY_MS = 3000L
}
