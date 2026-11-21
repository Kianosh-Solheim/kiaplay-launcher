package no.kianosh.kiaplay

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Everything the screens show, loaded from Jellyfin, MET and the package manager. */
class AppState(context: Context) {
  val prefs = Prefs(context)
  val jellyfin = Jellyfin(prefs)

  var resume by mutableStateOf<List<MediaItem>>(emptyList())
  var films by mutableStateOf<List<MediaItem>>(emptyList())
  var series by mutableStateOf<List<MediaItem>>(emptyList())
  var music by mutableStateOf<List<MediaItem>>(emptyList())
  var apps by mutableStateOf<List<AppEntry>>(emptyList())
  var weather by mutableStateOf<Weather?>(null)
  var status by mutableStateOf("KOBLER TIL …")

  fun favoriteApps(): List<AppEntry> {
    val favs = prefs.favorites
    return if (favs == null) apps.filter { !it.isSystem }.take(6) else apps.filter { it.packageName in favs }
  }

  fun toggleFavoriteApp(pkg: String) {
    val current = prefs.favorites ?: favoriteApps().map { it.packageName }.toSet()
    prefs.updateFavorites(if (pkg in current) current - pkg else current + pkg)
  }

  suspend fun refreshApps(context: Context) {
    apps = AppSource.load(context)
  }

  suspend fun refreshWeather() {
    weather = WeatherSource.fetch(prefs.city)
  }

  suspend fun refreshMedia() {
    if (!prefs.loggedIn) {
      status = "IKKE TILKOBLET"
      return
    }
    try {
      resume = jellyfin.resume()
      films = jellyfin.library("Movie")
      series = jellyfin.library("Series")
      music = runCatching { jellyfin.library("MusicAlbum") }.getOrDefault(emptyList())
      status = "NAS TILKOBLET"
    } catch (e: Exception) {
      status = "SERVEREN SVARER IKKE"
    }
  }
}
