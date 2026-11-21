package no.kianosh.kiaplay

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import java.util.UUID

enum class IntroMode(val label: String) { TV_ON("Hver gang TV-en slås på"), BOOT("Bare ved omstart"), OFF("Av") }

enum class Player(val label: String) { INTERNAL("Kiaplay-spiller"), EXTERNAL("Ekstern (VLC o.l.)"), JELLYFIN("Jellyfin-appen") }

enum class SubLang(val label: String) { OFF("AV"), NO("NORSK"), EN("ENGELSK") }

enum class AudioLang(val label: String) { DEFAULT("STANDARD"), NO("NORSK"), EN("ENGELSK") }

data class City(val name: String, val lat: Double, val lon: Double)

val cities = listOf(
  City("BERGEN", 60.39, 5.32),
  City("OSLO", 59.91, 10.75),
  City("TRONDHEIM", 63.43, 10.40),
  City("STAVANGER", 58.97, 5.73),
  City("TROMSØ", 69.65, 18.96),
)

/** All user settings, backed by SharedPreferences and exposed as Compose state. */
class Prefs(context: Context) {
  private val sp = context.getSharedPreferences("kiaplay", Context.MODE_PRIVATE)

  var introMode by mutableStateOf(IntroMode.entries[sp.getInt("introMode", 0).coerceIn(0, 2)])
    private set
  var introShort by mutableStateOf(sp.getBoolean("introShort", false))
    private set
  var skipOnKey by mutableStateOf(sp.getBoolean("skipOnKey", true))
    private set
  var introSound by mutableStateOf(sp.getBoolean("introSound", true))
    private set
  var volume by mutableStateOf(sp.getInt("volume", 6))
    private set
  var scanlines by mutableStateOf(sp.getBoolean("scanlines", true))
    private set
  var crt by mutableStateOf(sp.getBoolean("crt", true))
    private set
  var accentIndex by mutableStateOf(sp.getInt("accent", 0).coerceIn(0, Kp.accents.size - 1))
    private set
  var cityIndex by mutableStateOf(sp.getInt("city", 0).coerceIn(0, cities.size - 1))
    private set
  var player by mutableStateOf(Player.entries[sp.getInt("player2", 0).coerceIn(0, 2)])
    private set
  var curvature by mutableStateOf(Curvature.entries[sp.getInt("curvature", 0).coerceIn(0, 2)])
    private set
  var subLang by mutableStateOf(SubLang.entries[sp.getInt("subLang", 0).coerceIn(0, 2)])
    private set
  var audioLang by mutableStateOf(AudioLang.entries[sp.getInt("audioLang", 0).coerceIn(0, 2)])
    private set
  var favorites by mutableStateOf(sp.getStringSet("favorites", null)?.toSet())
    private set

  var server by mutableStateOf(sp.getString("server", "") ?: "")
    private set
  var userName by mutableStateOf(sp.getString("userName", "") ?: "")
    private set
  var token by mutableStateOf(sp.getString("token", "") ?: "")
    private set
  var userId by mutableStateOf(sp.getString("userId", "") ?: "")
    private set

  val deviceId: String = sp.getString("deviceId", null) ?: UUID.randomUUID().toString().also {
    sp.edit().putString("deviceId", it).apply()
  }

  val accent: Color get() = Kp.accents[accentIndex]
  val city: City get() = cities[cityIndex]
  val loggedIn: Boolean get() = server.isNotBlank() && token.isNotBlank() && userId.isNotBlank()

  fun updateIntroMode(v: IntroMode) { introMode = v; sp.edit().putInt("introMode", v.ordinal).apply() }
  fun updateIntroShort(v: Boolean) { introShort = v; sp.edit().putBoolean("introShort", v).apply() }
  fun updateSkipOnKey(v: Boolean) { skipOnKey = v; sp.edit().putBoolean("skipOnKey", v).apply() }
  fun updateIntroSound(v: Boolean) { introSound = v; sp.edit().putBoolean("introSound", v).apply() }
  fun updateVolume(v: Int) { volume = v.coerceIn(0, 10); sp.edit().putInt("volume", volume).apply() }
  fun updateScanlines(v: Boolean) { scanlines = v; sp.edit().putBoolean("scanlines", v).apply() }
  fun updateCrt(v: Boolean) { crt = v; sp.edit().putBoolean("crt", v).apply() }
  fun updateAccent(i: Int) { accentIndex = i; sp.edit().putInt("accent", i).apply() }
  fun updateCity(i: Int) { cityIndex = i; sp.edit().putInt("city", i).apply() }
  fun updatePlayer(v: Player) { player = v; sp.edit().putInt("player2", v.ordinal).apply() }

  fun updateCurvature(v: Curvature) { curvature = v; sp.edit().putInt("curvature", v.ordinal).apply() }
  fun updateSubLang(v: SubLang) { subLang = v; sp.edit().putInt("subLang", v.ordinal).apply() }
  fun updateAudioLang(v: AudioLang) { audioLang = v; sp.edit().putInt("audioLang", v.ordinal).apply() }

  /** Favourite app packages; null until the user changes them (then the first apps are used). */
  fun updateFavorites(v: Set<String>) { favorites = v; sp.edit().putStringSet("favorites", v).apply() }

  fun updateServer(v: String) { server = v.trim().trimEnd('/'); sp.edit().putString("server", server).apply() }
  fun updateUserName(v: String) { userName = v; sp.edit().putString("userName", v).apply() }
  fun updateSession(token: String, userId: String) {
    this.token = token
    this.userId = userId
    sp.edit().putString("token", token).putString("userId", userId).apply()
  }
}
