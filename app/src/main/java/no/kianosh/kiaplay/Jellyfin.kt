package no.kianosh.kiaplay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class MediaItem(
  val id: String,
  val name: String,
  val type: String,
  val year: Int?,
  val endYear: Int?,
  val overview: String,
  val genres: List<String>,
  val runTimeMinutes: Int?,
  val playedPercent: Float,
  val positionTicks: Long,
  val played: Boolean,
  val favorite: Boolean,
  val seriesId: String?,
  val seriesName: String?,
  val season: Int?,
  val episode: Int?,
  val hasPrimary: Boolean,
  val backdropItemId: String?,
  val childCount: Int?,
  val artist: String? = null,
  val album: String? = null,
  val albumId: String? = null,
) {
  val minutesLeft: Int?
    get() = runTimeMinutes?.let { ((it * (100f - playedPercent)) / 100f).toInt().coerceAtLeast(1) }
}

/** An audio or subtitle stream as Jellyfin describes it. */
data class JStream(
  val index: Int,
  val type: String,
  val language: String?,
  val title: String?,
  val codec: String?,
  val channels: Int?,
  val external: Boolean,
  val isDefault: Boolean,
  val forced: Boolean,
  val isText: Boolean,
) {
  val label: String
    get() {
      val lang = languageName(language)
      val extra = mutableListOf<String>()
      title?.takeIf { it.isNotBlank() && !it.equals(lang, true) }?.let { extra += it }
      if (type == "Audio") {
        channels?.let { extra += when (it) { 1 -> "MONO"; 2 -> "STEREO"; 6 -> "5.1"; 8 -> "7.1"; else -> "${it}K" } }
      }
      if (forced) extra += "TVUNGEN"
      if (isDefault) extra += "STANDARD"
      return (listOf(lang) + extra).joinToString(" · ").uppercase()
    }
}

fun languageName(code: String?): String = when (code?.lowercase()) {
  "nor", "nob", "nno", "no", "nb", "nn" -> "Norsk"
  "eng", "en" -> "Engelsk"
  "swe", "sv" -> "Svensk"
  "dan", "da" -> "Dansk"
  "ger", "deu", "de" -> "Tysk"
  "fre", "fra", "fr" -> "Fransk"
  "spa", "es" -> "Spansk"
  "ita", "it" -> "Italiensk"
  "jpn", "ja" -> "Japansk"
  "fin", "fi" -> "Finsk"
  null, "", "und" -> "Ukjent språk"
  else -> code?.uppercase() ?: "Ukjent språk"
}

fun isNorwegian(code: String?) = code?.lowercase() in setOf("nor", "nob", "nno", "no", "nb", "nn")
fun isEnglish(code: String?) = code?.lowercase() in setOf("eng", "en")

/** Minimal Jellyfin client: login, resume list, library listings, episodes and favourites. */
class Jellyfin(private val prefs: Prefs) {
  private val version = "0.1"

  private fun authHeader(token: String?): String {
    val base = "MediaBrowser Client=\"Kiaplay\", Device=\"Google TV\", DeviceId=\"${prefs.deviceId}\", Version=\"$version\""
    return if (token.isNullOrBlank()) base else "$base, Token=\"$token\""
  }

  private fun request(path: String, method: String = "GET", body: JSONObject? = null, token: String? = prefs.token): String {
    val conn = URL(prefs.server + path).openConnection() as HttpURLConnection
    try {
      conn.requestMethod = method
      conn.connectTimeout = 6000
      conn.readTimeout = 10000
      conn.setRequestProperty("Authorization", authHeader(token))
      conn.setRequestProperty("X-Emby-Authorization", authHeader(token))
      conn.setRequestProperty("Accept", "application/json")
      if (body != null) {
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
      }
      val code = conn.responseCode
      if (code !in 200..299) throw IllegalStateException("Jellyfin svarte $code")
      return conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
      conn.disconnect()
    }
  }

  /** Logs in and stores the access token. Returns an error message, or null on success. */
  suspend fun login(server: String, user: String, password: String): String? = withContext(Dispatchers.IO) {
    try {
      prefs.updateServer(if (server.startsWith("http")) server else "http://$server")
      prefs.updateUserName(user)
      val res = JSONObject(
        request(
          "/Users/AuthenticateByName",
          "POST",
          JSONObject().put("Username", user).put("Pw", password),
          token = null,
        ),
      )
      prefs.updateSession(res.getString("AccessToken"), res.getJSONObject("User").getString("Id"))
      null
    } catch (e: Exception) {
      e.message ?: "Kunne ikke logge inn"
    }
  }

  private val fields = "Overview,Genres,ChildCount"

  private fun parse(o: JSONObject): MediaItem {
    val ud = o.optJSONObject("UserData")
    val ticks = if (o.has("RunTimeTicks")) o.optLong("RunTimeTicks") else 0L
    val tags = o.optJSONObject("ImageTags")
    val backdrops = o.optJSONArray("BackdropImageTags")
    val parentBackdrop = o.optJSONArray("ParentBackdropImageTags")
    val backdropId = when {
      backdrops != null && backdrops.length() > 0 -> o.getString("Id")
      parentBackdrop != null && parentBackdrop.length() > 0 -> o.optString("ParentBackdropItemId").ifBlank { null }
      else -> null
    }
    val genres = mutableListOf<String>()
    o.optJSONArray("Genres")?.let { a -> for (i in 0 until a.length()) genres += a.getString(i) }
    val endDate = o.optString("EndDate", "")
    return MediaItem(
      id = o.getString("Id"),
      name = o.optString("Name"),
      type = o.optString("Type"),
      year = if (o.has("ProductionYear")) o.optInt("ProductionYear") else null,
      endYear = endDate.take(4).toIntOrNull(),
      overview = o.optString("Overview", ""),
      genres = genres,
      runTimeMinutes = if (ticks > 0) (ticks / 600_000_000L).toInt() else null,
      playedPercent = ud?.optDouble("PlayedPercentage", 0.0)?.toFloat()?.takeIf { !it.isNaN() } ?: 0f,
      positionTicks = ud?.optLong("PlaybackPositionTicks", 0L) ?: 0L,
      played = ud?.optBoolean("Played", false) ?: false,
      favorite = ud?.optBoolean("IsFavorite", false) ?: false,
      seriesId = o.optString("SeriesId").ifBlank { null },
      seriesName = o.optString("SeriesName").ifBlank { null },
      season = if (o.has("ParentIndexNumber")) o.optInt("ParentIndexNumber") else null,
      episode = if (o.has("IndexNumber")) o.optInt("IndexNumber") else null,
      hasPrimary = tags?.has("Primary") == true,
      backdropItemId = backdropId,
      childCount = if (o.has("ChildCount")) o.optInt("ChildCount") else null,
      artist = o.optString("AlbumArtist").ifBlank { null }
        ?: o.optJSONArray("Artists")?.let { a -> if (a.length() > 0) a.getString(0) else null },
      album = o.optString("Album").ifBlank { null },
      albumId = o.optString("AlbumId").ifBlank { null },
    )
  }

  private fun items(json: String): List<MediaItem> {
    val arr: JSONArray = JSONObject(json).optJSONArray("Items") ?: JSONArray()
    return (0 until arr.length()).map { parse(arr.getJSONObject(it)) }
  }

  private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

  suspend fun resume(): List<MediaItem> = withContext(Dispatchers.IO) {
    val q = "Limit=16&MediaTypes=Video&Fields=$fields&EnableImageTypes=Primary,Backdrop"
    try {
      items(request("/UserItems/Resume?userId=${enc(prefs.userId)}&$q"))
    } catch (_: Exception) {
      items(request("/Users/${enc(prefs.userId)}/Items/Resume?$q"))
    }
  }

  suspend fun library(type: String): List<MediaItem> = withContext(Dispatchers.IO) {
    items(
      request(
        "/Items?userId=${enc(prefs.userId)}&IncludeItemTypes=$type&Recursive=true" +
          "&SortBy=SortName&SortOrder=Ascending&Fields=$fields&Limit=500",
      ),
    )
  }

  suspend fun episodes(seriesId: String): List<MediaItem> = withContext(Dispatchers.IO) {
    items(request("/Shows/${enc(seriesId)}/Episodes?userId=${enc(prefs.userId)}&Fields=$fields"))
  }

  suspend fun setFavorite(id: String, favorite: Boolean) = withContext(Dispatchers.IO) {
    val method = if (favorite) "POST" else "DELETE"
    try {
      request("/UserFavoriteItems/${enc(id)}?userId=${enc(prefs.userId)}", method)
    } catch (_: Exception) {
      request("/Users/${enc(prefs.userId)}/FavoriteItems/${enc(id)}", method)
    }
    Unit
  }

  suspend fun setPlayed(id: String, played: Boolean) = withContext(Dispatchers.IO) {
    val method = if (played) "POST" else "DELETE"
    try {
      request("/UserPlayedItems/${enc(id)}?userId=${enc(prefs.userId)}", method)
    } catch (_: Exception) {
      request("/Users/${enc(prefs.userId)}/PlayedItems/${enc(id)}", method)
    }
    Unit
  }

  fun posterUrl(item: MediaItem, height: Int = 420): String? = when {
    item.hasPrimary -> "${prefs.server}/Items/${item.id}/Images/Primary?maxHeight=$height&quality=85"
    item.albumId != null -> "${prefs.server}/Items/${item.albumId}/Images/Primary?maxHeight=$height&quality=85"
    else -> null
  }

  fun backdropUrl(item: MediaItem): String? =
    item.backdropItemId?.let { "${prefs.server}/Items/$it/Images/Backdrop/0?maxWidth=1280&quality=80" }

  fun streamUrl(item: MediaItem): String =
    "${prefs.server}/${if (item.type == "Audio") "Audio" else "Videos"}/${item.id}/stream?static=true&MediaSourceId=${item.id}&api_key=${prefs.token}"

  /** HLS stream the server transcodes to H.264/AAC, used when the file can't be played directly. */
  fun transcodeUrl(item: MediaItem, playSessionId: String, audioIndex: Int? = null): String =
    "${prefs.server}/Videos/${item.id}/master.m3u8?MediaSourceId=${item.id}&PlaySessionId=$playSessionId" +
      (audioIndex?.let { "&AudioStreamIndex=$it" } ?: "") +
      "&DeviceId=${enc(prefs.deviceId)}&VideoCodec=h264&AudioCodec=aac,ac3,eac3&MaxAudioChannels=6" +
      "&TranscodingMaxAudioChannels=6&SegmentContainer=ts&MinSegments=1&BreakOnNonKeyFrames=True" +
      "&VideoBitrate=20000000&AudioBitrate=384000&api_key=${prefs.token}"

  /** Audio and subtitle streams of the item's first media source. */
  suspend fun streams(item: MediaItem): List<JStream> = withContext(Dispatchers.IO) {
    val o = JSONObject(request("/Items/${enc(item.id)}?userId=${enc(prefs.userId)}&Fields=MediaSources,MediaStreams"))
    val source = o.optJSONArray("MediaSources")?.optJSONObject(0)
    val arr = source?.optJSONArray("MediaStreams") ?: o.optJSONArray("MediaStreams") ?: return@withContext emptyList()
    (0 until arr.length()).mapNotNull { i ->
      val st = arr.getJSONObject(i)
      val type = st.optString("Type")
      if (type != "Audio" && type != "Subtitle") return@mapNotNull null
      JStream(
        index = st.optInt("Index"),
        type = type,
        language = st.optString("Language").ifBlank { null },
        title = st.optString("Title").ifBlank { null },
        codec = st.optString("Codec").ifBlank { null },
        channels = if (st.has("Channels")) st.optInt("Channels") else null,
        external = st.optBoolean("IsExternal", false),
        isDefault = st.optBoolean("IsDefault", false),
        forced = st.optBoolean("IsForced", false),
        isText = st.optBoolean("IsTextSubtitleStream", type == "Subtitle"),
      )
    }
  }

  fun subtitleUrl(item: MediaItem, stream: JStream): String =
    "${prefs.server}/Videos/${item.id}/${item.id}/Subtitles/${stream.index}/0/Stream.vtt?api_key=${prefs.token}"

  suspend fun tracks(albumId: String): List<MediaItem> = withContext(Dispatchers.IO) {
    items(
      request(
        "/Items?userId=${enc(prefs.userId)}&ParentId=${enc(albumId)}&IncludeItemTypes=Audio&Recursive=true" +
          "&SortBy=ParentIndexNumber,IndexNumber,SortName&Fields=$fields",
      ),
    )
  }

  private fun playbackBody(item: MediaItem, positionMs: Long, sessionId: String, transcoding: Boolean, paused: Boolean) =
    JSONObject()
      .put("ItemId", item.id)
      .put("MediaSourceId", item.id)
      .put("PlaySessionId", sessionId)
      .put("PositionTicks", positionMs * 10_000L)
      .put("IsPaused", paused)
      .put("CanSeek", true)
      .put("PlayMethod", if (transcoding) "Transcode" else "DirectPlay")

  /** Tells Jellyfin what is playing, so "keep watching" and played status stay right. */
  suspend fun reportStart(item: MediaItem, positionMs: Long, sessionId: String, transcoding: Boolean) = withContext(Dispatchers.IO) {
    request("/Sessions/Playing", "POST", playbackBody(item, positionMs, sessionId, transcoding, false))
    Unit
  }

  suspend fun reportProgress(item: MediaItem, positionMs: Long, sessionId: String, transcoding: Boolean, paused: Boolean) =
    withContext(Dispatchers.IO) {
      request("/Sessions/Playing/Progress", "POST", playbackBody(item, positionMs, sessionId, transcoding, paused))
      Unit
    }

  suspend fun reportStopped(item: MediaItem, positionMs: Long, sessionId: String, transcoding: Boolean) = withContext(Dispatchers.IO) {
    request("/Sessions/Playing/Stopped", "POST", playbackBody(item, positionMs, sessionId, transcoding, false))
    Unit
  }
}
