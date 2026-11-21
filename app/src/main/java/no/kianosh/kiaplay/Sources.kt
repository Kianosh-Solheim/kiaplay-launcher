package no.kianosh.kiaplay

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class Weather(val temp: Int, val label: String)

/** Current weather from MET Norway (api.met.no). */
object WeatherSource {
  private val words = mapOf(
    "clearsky" to "KLART",
    "fair" to "LETTSKYET",
    "partlycloudy" to "DELVIS SKYET",
    "cloudy" to "SKYET",
    "fog" to "TÅKE",
    "rain" to "REGN",
    "lightrain" to "LETT REGN",
    "heavyrain" to "KRAFTIG REGN",
    "rainshowers" to "REGNBYGER",
    "sleet" to "SLUDD",
    "snow" to "SNØ",
    "lightsnow" to "LETT SNØ",
    "snowshowers" to "SNØBYGER",
  )

  suspend fun fetch(city: City): Weather? = withContext(Dispatchers.IO) {
    try {
      val url = URL(
        String.format(
          Locale.US,
          "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.2f&lon=%.2f",
          city.lat,
          city.lon,
        ),
      )
      val conn = url.openConnection() as HttpURLConnection
      conn.setRequestProperty("User-Agent", "Kiaplay/0.1 github.com/Kianosh-Solheim/kiaplay-launcher")
      conn.connectTimeout = 6000
      conn.readTimeout = 10000
      val text = conn.inputStream.bufferedReader().use { it.readText() }
      conn.disconnect()
      val first = JSONObject(text).getJSONObject("properties").getJSONArray("timeseries").getJSONObject(0)
      val data = first.getJSONObject("data")
      val temp = data.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature")
      val symbol = data.optJSONObject("next_1_hours")?.optJSONObject("summary")?.optString("symbol_code") ?: ""
      val key = symbol.substringBefore('_').replace("andthunder", "")
      Weather(Math.round(temp).toInt(), words[key] ?: key.uppercase())
    } catch (_: Exception) {
      null
    }
  }
}

data class AppEntry(val label: String, val packageName: String, val banner: ImageBitmap?, val icon: ImageBitmap?, val isSystem: Boolean)

/** Installed TV apps, from the Leanback and normal launcher categories. */
object AppSource {
  private val systemPackages = setOf("com.android.vending", "com.android.tv.settings", "com.google.android.tvlauncher")

  suspend fun load(context: Context): List<AppEntry> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val seen = mutableSetOf<String>()
    val result = mutableListOf<AppEntry>()
    for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
      val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
      for (ri in pm.queryIntentActivities(intent, 0)) {
        val pkg = ri.activityInfo.packageName
        if (pkg == context.packageName || !seen.add(pkg)) continue
        val ai = ri.activityInfo.applicationInfo
        val banner = try { ri.activityInfo.loadBanner(pm) ?: ai.loadBanner(pm) } catch (_: Exception) { null }
        val icon = try { ri.loadIcon(pm) } catch (_: Exception) { null }
        result += AppEntry(
          label = ri.loadLabel(pm).toString(),
          packageName = pkg,
          banner = banner?.let { runCatching { it.toBitmap(320, 180).asImageBitmap() }.getOrNull() },
          icon = icon?.let { runCatching { it.toBitmap(96, 96).asImageBitmap() }.getOrNull() },
          isSystem = pkg in systemPackages || (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
        )
      }
    }
    result.sortedBy { it.label.lowercase() }
  }

  fun launch(context: Context, pkg: String) {
    val pm = context.packageManager
    val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
  }

  fun openAppInfo(context: Context, pkg: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
  }

  fun isInstalled(context: Context, pkg: String): Boolean = try {
    context.packageManager.getPackageInfo(pkg, 0); true
  } catch (_: PackageManager.NameNotFoundException) {
    false
  }
}

/** Starts playback: an external player at the saved position, or the Jellyfin app. */
object Playback {
  const val JELLYFIN_TV = "org.jellyfin.androidtv"

  fun play(context: Context, prefs: Prefs, jellyfin: Jellyfin, item: MediaItem, fromStart: Boolean) {
    if (prefs.player == Player.EXTERNAL) {
      val positionMs = if (fromStart) 0L else item.positionTicks / 10_000L
      val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(jellyfin.streamUrl(item)), "video/*")
        .putExtra("title", item.name)
        .putExtra("position", positionMs)
        .putExtra("from_start", fromStart)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      if (intent.resolveActivity(context.packageManager) != null) {
        runCatching { context.startActivity(intent) }
        return
      }
    }
    AppSource.launch(context, JELLYFIN_TV)
  }
}

/** Shortcuts into the system's own settings screens. */
object SystemShortcuts {
  fun open(context: Context, action: String) {
    runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
  }
}
