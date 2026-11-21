package no.kianosh.kiaplay

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
private fun Heading(text: String) {
  Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
    Txt("▸ $text", Kp.vt(26.sp, Kp.Mustard, 3.sp))
    Box(Modifier.fillMaxWidth().height(2.dp).background(Kp.Border))
  }
}

@Composable
private fun ToggleRow(label: String, on: Boolean, accent: Color, onToggle: () -> Unit) {
  Focusable(onClick = onToggle) { focused ->
    Row(
      Modifier
        .fillMaxWidth()
        .height(48.dp)
        .focusRing(focused, accent)
        .clip(corner)
        .background(Kp.Surface)
        .padding(horizontal = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Txt(label, Kp.vt(24.sp), Modifier.weight(1f))
      Box(
        Modifier.clip(corner).background(if (on) Kp.Green else Kp.Border).padding(horizontal = 12.dp, vertical = 2.dp),
      ) { Txt(if (on) "PÅ" else "AV", Kp.vt(24.sp, if (on) Kp.Black else Kp.Muted)) }
    }
  }
}

@Composable
private fun Field(label: String, value: String, accent: Color, password: Boolean = false, onChange: (String) -> Unit) {
  var focused by remember { mutableStateOf(false) }
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Txt(label, Kp.vt(24.sp))
    BasicTextField(
      value = value,
      onValueChange = onChange,
      singleLine = true,
      textStyle = Kp.vt(24.sp),
      cursorBrush = SolidColor(accent),
      visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
      keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri),
      modifier = Modifier
        .fillMaxWidth()
        .onFocusChanged { focused = it.isFocused }
        .focusRing(focused, accent)
        .clip(corner)
        .background(Kp.Surface)
        .padding(horizontal = 12.dp, vertical = 8.dp),
    )
  }
}

@Composable
fun SettingsScreen(state: AppState, onBack: () -> Unit, onPreviewIntro: () -> Unit, onLoggedIn: () -> Unit) {
  val prefs = state.prefs
  val scope = rememberCoroutineScope()
  var server by remember { mutableStateOf(prefs.server) }
  var user by remember { mutableStateOf(prefs.userName) }
  var password by remember { mutableStateOf("") }
  var loginStatus by remember { mutableStateOf(if (prefs.loggedIn) "Innlogget som ${prefs.userName}" else "Ikke innlogget") }
  val first = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

  Column(
    Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, top = 32.dp, bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
      KpButton("◀", prefs.accent, onBack, Modifier.focusRequester(first), height = 48.dp)
      Txt("INNSTILLINGER", Kp.anton(40.sp, prefs.accent, 4.sp))
      Spacer(Modifier.weight(1f))
      Txt("KIAPLAY ${BuildInfo.versionName} · GOOGLE TV", Kp.vt(24.sp, Kp.Muted, 2.sp))
    }
    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
      Column(
        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Heading("OPPSTART")
        Txt("Spill av animasjon", Kp.vt(24.sp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          for (m in IntroMode.entries) KpButton(m.label, prefs.accent, { prefs.updateIntroMode(m) }, active = prefs.introMode == m, height = 44.dp, fontSize = 18)
        }
        Txt("Lengde", Kp.vt(24.sp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          KpButton("FULL · 6 S", prefs.accent, { prefs.updateIntroShort(false) }, active = !prefs.introShort, height = 44.dp, fontSize = 18)
          KpButton("KORT · 2 S", prefs.accent, { prefs.updateIntroShort(true) }, active = prefs.introShort, height = 44.dp, fontSize = 18)
          KpButton("▶ FORHÅNDSVIS", prefs.accent, onPreviewIntro, height = 44.dp, fontSize = 18)
        }
        ToggleRow("Hopp over med hvilken som helst knapp", prefs.skipOnKey, prefs.accent) { prefs.updateSkipOnKey(!prefs.skipOnKey) }

        Heading("LYD")
        ToggleRow("Oppstartslyd (dunk, summing, akkord)", prefs.introSound, prefs.accent) { prefs.updateIntroSound(!prefs.introSound) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Txt("Volum", Kp.vt(24.sp), Modifier.width(80.dp))
          KpButton("−", prefs.accent, { prefs.updateVolume(prefs.volume - 1) }, height = 40.dp)
          Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (i in 0 until 10) Box(Modifier.size(12.dp, 22.dp).background(if (i < prefs.volume) Kp.Teal else Kp.Border))
          }
          KpButton("+", prefs.accent, { prefs.updateVolume(prefs.volume + 1) }, height = 40.dp)
          Txt("${prefs.volume * 10} %", Kp.vt(24.sp, Kp.Teal))
        }

        Heading("UTSEENDE")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Txt("Aksentfarge", Kp.vt(24.sp), Modifier.width(160.dp))
          Kp.accents.forEachIndexed { i, c ->
            Focusable(onClick = { prefs.updateAccent(i) }) { focused ->
              Box(
                Modifier
                  .size(if (focused) 40.dp else 34.dp)
                  .clip(CircleShape)
                  .background(if (prefs.accentIndex == i || focused) Kp.Cream else Kp.Border)
                  .padding(3.dp)
                  .clip(CircleShape)
                  .background(c),
              )
            }
          }
        }
        ToggleRow("Skannelinjer (gammelt bilderør)", prefs.scanlines, prefs.accent) { prefs.updateScanlines(!prefs.scanlines) }
        ToggleRow("Buede hjørner og filmkorn", prefs.crt, prefs.accent) { prefs.updateCrt(!prefs.crt) }
      }

      Column(
        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Heading("HJEMMESERVER (JELLYFIN)")
        Field("Adresse, f.eks. 192.168.1.20:8096", server, prefs.accent) { server = it }
        Field("Brukernavn", user, prefs.accent) { user = it }
        Field("Passord", password, prefs.accent, password = true) { password = it }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          KpButton("LOGG INN", prefs.accent, {
            loginStatus = "Logger inn …"
            scope.launch {
              val error = state.jellyfin.login(server, user, password)
              loginStatus = error?.let { "Feil: $it" } ?: "Innlogget som $user"
              if (error == null) {
                password = ""
                onLoggedIn()
              }
            }
          }, primary = true, height = 44.dp, fontSize = 20)
          Txt(loginStatus, Kp.vt(22.sp, if (prefs.loggedIn) Kp.Green else Kp.Muted), maxLines = 1)
        }
        Txt("Avspiller", Kp.vt(24.sp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          for (p in Player.entries) KpButton(p.label, prefs.accent, { prefs.updatePlayer(p) }, active = prefs.player == p, height = 44.dp, fontSize = 18)
        }

        Txt("Lydspråk", Kp.vt(24.sp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          for (l in AudioLang.entries) KpButton(l.label, prefs.accent, { prefs.updateAudioLang(l) }, active = prefs.audioLang == l, height = 40.dp, fontSize = 18)
        }
        Txt("Undertekster", Kp.vt(24.sp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          for (l in SubLang.entries) KpButton(l.label, prefs.accent, { prefs.updateSubLang(l) }, active = prefs.subLang == l, height = 40.dp, fontSize = 18)
        }

        Heading("VÆRET")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          cities.forEachIndexed { i, c ->
            KpButton(c.name, prefs.accent, {
              prefs.updateCity(i)
              scope.launch { state.refreshWeather() }
            }, active = prefs.cityIndex == i, height = 40.dp, fontSize = 16)
          }
        }
        Txt("Værdata fra MET Norway (api.met.no)", Kp.vt(20.sp, Kp.Muted))
      }
    }
    FooterHint("▲ ▼ ◀ ▶ VELG   OK ENDRE   ◀ TILBAKE")
  }
}

/** Side panel with shortcuts into the TV's own settings and a volume control. */
@Composable
fun QuickMenu(state: AppState, onClose: () -> Unit, onSettings: () -> Unit) {
  val prefs = state.prefs
  val context = LocalContext.current
  val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
  var vol by remember { mutableStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)) }
  val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
  val first = remember { FocusRequester() }
  val clock = rememberClock()
  LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

  fun changeVolume(dir: Int) {
    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
    vol = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
  }

  Row(Modifier.fillMaxSize()) {
    Box(Modifier.weight(1f).fillMaxHeight().background(Color.Black.copy(alpha = 0.7f)))
    Column(
      Modifier
        .width(480.dp)
        .fillMaxHeight()
        .background(prefs.accent)
        .padding(start = 4.dp)
        .background(Kp.SurfaceDeep)
        .padding(36.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Txt("HURTIGMENY", Kp.anton(32.sp, Kp.Cream, 2.sp), Modifier.weight(1f))
        Txt(clock.hhmm(), Kp.vt(26.sp, Kp.Teal))
      }
      Txt("TV OG INNDATA", Kp.vt(22.sp, Kp.Mustard, 3.sp))
      KpButton("⚙ TV-INNSTILLINGER", prefs.accent, { SystemShortcuts.open(context, Settings.ACTION_SETTINGS) }, Modifier.focusRequester(first).fillMaxWidth())
      KpButton("WI-FI OG NETTVERK", prefs.accent, { SystemShortcuts.open(context, Settings.ACTION_WIFI_SETTINGS) }, Modifier.fillMaxWidth())
      Txt("HDMI-inngang byttes med TV-ens fjernkontroll eller HDMI-CEC i TV-innstillingene.", Kp.vt(18.sp, Kp.Muted))
      Txt("LYD", Kp.vt(22.sp, Kp.Mustard, 3.sp))
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KpButton("−", prefs.accent, { changeVolume(AudioManager.ADJUST_LOWER) }, height = 44.dp)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          val steps = 12
          for (i in 0 until steps) {
            Box(Modifier.weight(1f).height(22.dp).background(if (i < vol * steps / max) Kp.Teal else Kp.Border))
          }
        }
        KpButton("+", prefs.accent, { changeVolume(AudioManager.ADJUST_RAISE) }, height = 44.dp)
      }
      Txt("KIAPLAY", Kp.vt(22.sp, Kp.Mustard, 3.sp))
      KpButton("⚙ KIAPLAY-INNSTILLINGER", prefs.accent, onSettings, Modifier.fillMaxWidth())
      Spacer(Modifier.weight(1f))
      KpButton("◀ LUKK", prefs.accent, onClose)
    }
  }
}
