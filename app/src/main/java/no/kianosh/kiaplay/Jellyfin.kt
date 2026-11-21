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
) {
  val minutesLeft: Int?
    get() = runTimeMinutes?.let { ((it * (100f - playedPercent)) / 100f).toInt().coerceAtLeast(1) }
}

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
      prefs.setServer(if (server.startsWith("http")) server else "http://$server")
      prefs.setUserName(user)
      val res = JSONObject(
        request(
          "/Users/AuthenticateByName",
          "POST",
          JSONObject().put("Username", user).put("Pw", password),
          token = null,
        ),
      )
      prefs.setSession(res.getString("AccessToken"), res.getJSONObject("User").getString("Id"))
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

  fun posterUrl(item: MediaItem, height: Int = 420): String? =
    if (item.hasPrimary) "${prefs.server}/Items/${item.id}/Images/Primary?maxHeight=$height&quality=85" else null

  fun backdropUrl(item: MediaItem): String? =
    item.backdropItemId?.let { "${prefs.server}/Items/$it/Images/Backdrop/0?maxWidth=1280&quality=80" }

  fun streamUrl(item: MediaItem): String =
    "${prefs.server}/Videos/${item.id}/stream?static=true&api_key=${prefs.token}"
}
