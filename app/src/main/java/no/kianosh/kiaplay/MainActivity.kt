package no.kianosh.kiaplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object BuildInfo {
  var versionName: String = ""
}

sealed interface Nav {
  data object Home : Nav
  data class Library(val tab: Tab) : Nav
  data class Series(val initial: MediaItem?) : Nav
  data object Apps : Nav
  data object Settings : Nav
  data class Detail(val item: MediaItem) : Nav
}

class MainActivity : ComponentActivity() {
  private lateinit var state: AppState
  private var screenOnTick by mutableIntStateOf(0)
  private var homeTick by mutableIntStateOf(0)
  private var resumeTick by mutableIntStateOf(0)
  private var menuTick by mutableIntStateOf(0)

  private val screenReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      if (intent.action == Intent.ACTION_SCREEN_ON) screenOnTick++
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    BuildInfo.versionName = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""
    state = AppState(applicationContext)
    ContextCompat.registerReceiver(
      this,
      screenReceiver,
      IntentFilter(Intent.ACTION_SCREEN_ON),
      ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    val introAtStart = savedInstanceState == null && state.prefs.introMode != IntroMode.OFF
    setContent {
      KiaplayApp(state, introAtStart, screenOnTick, homeTick, resumeTick, menuTick)
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    // Pressing Home while Kiaplay is already open goes back to the start screen.
    if (intent.hasCategory(Intent.CATEGORY_HOME)) homeTick++
  }

  override fun onResume() {
    super.onResume()
    resumeTick++
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    if (keyCode == KeyEvent.KEYCODE_MENU) {
      menuTick++
      return true
    }
    return super.onKeyDown(keyCode, event)
  }

  override fun onDestroy() {
    runCatching { unregisterReceiver(screenReceiver) }
    super.onDestroy()
  }
}

@Composable
fun KiaplayApp(state: AppState, introAtStart: Boolean, screenOnTick: Int, homeTick: Int, resumeTick: Int, menuTick: Int) {
  val prefs = state.prefs
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var intro by remember { mutableStateOf(introAtStart) }
  var stack by remember { mutableStateOf(listOf<Nav>(Nav.Home)) }
  var menu by remember { mutableStateOf(false) }

  LaunchedEffect(screenOnTick) {
    if (screenOnTick > 0 && prefs.introMode == IntroMode.TV_ON) intro = true
  }
  LaunchedEffect(homeTick) {
    if (homeTick > 0) {
      stack = listOf(Nav.Home)
      menu = false
    }
  }
  LaunchedEffect(menuTick) { if (menuTick > 0) menu = !menu }
  LaunchedEffect(Unit) {
    launch { state.refreshApps(context) }
    launch {
      while (true) {
        state.refreshWeather()
        delay(30 * 60 * 1000L)
      }
    }
  }
  LaunchedEffect(resumeTick) {
    state.refreshMedia()
    state.refreshApps(context)
  }

  fun go(nav: Nav) { stack = stack + nav; menu = false }
  fun back() {
    when {
      menu -> menu = false
      stack.size > 1 -> stack = stack.dropLast(1)
    }
  }
  fun tab(t: Tab) {
    stack = when (t) {
      Tab.HOME -> listOf(Nav.Home)
      Tab.FILMS, Tab.MUSIC -> listOf(Nav.Home, Nav.Library(t))
      Tab.SERIES -> listOf(Nav.Home, Nav.Series(null))
      Tab.APPS -> listOf(Nav.Home, Nav.Apps)
    }
    menu = false
  }
  fun open(item: MediaItem) {
    if (item.type == "Series") go(Nav.Series(item)) else go(Nav.Detail(item))
  }

  BackHandler(enabled = !intro && state.playRequest == null && (menu || stack.size > 1)) { back() }

  val play = state.playRequest
  if (play != null) {
    PlayerScreen(state, play) {
      state.playRequest = null
      scope.launch {
        delay(800)
        state.refreshMedia()
      }
    }
    return
  }

  DesignSurface {
    val shape = if (prefs.crt) RoundedCornerShape(54.dp) else RoundedCornerShape(0.dp)
    Box(Modifier.fillMaxSize().crtCurve(prefs.curvature).clip(shape).background(Kp.Black)) {
      if (!intro) {
        when (val screen = stack.last()) {
          Nav.Home -> HomeScreen(state, ::open, ::tab, { menu = true }, { go(Nav.Settings) })
          is Nav.Library -> LibraryScreen(state, screen.tab, ::open, ::tab) { menu = true }
          is Nav.Series -> SeriesScreen(state, screen.initial, ::open, ::tab) { menu = true }
          Nav.Apps -> AppsScreen(state, ::tab) { menu = true }
          Nav.Settings -> SettingsScreen(
            state,
            onBack = ::back,
            onPreviewIntro = { intro = true },
            onLoggedIn = { scope.launch { state.refreshMedia() } },
          )
          is Nav.Detail -> DetailScreen(state, screen.item, ::back)
        }
        if (menu) QuickMenu(state, onClose = { menu = false }, onSettings = { go(Nav.Settings) })
      } else {
        IntroScreen(prefs) { intro = false }
      }
      CrtOverlay(prefs.scanlines, prefs.crt)
    }
  }
}
