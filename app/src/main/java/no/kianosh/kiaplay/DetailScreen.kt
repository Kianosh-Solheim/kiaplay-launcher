package no.kianosh.kiaplay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Film or episode details: resume / restart with finish times, favourite and watched toggles. */
@Composable
fun DetailScreen(state: AppState, item: MediaItem, onBack: () -> Unit) {
  val prefs = state.prefs
  val context = LocalContext.current
  val clock = rememberClock()
  val scope = rememberCoroutineScope()
  var favorite by remember(item.id) { mutableStateOf(item.favorite) }
  var played by remember(item.id) { mutableStateOf(item.played) }
  val playFocus = remember { FocusRequester() }
  LaunchedEffect(item.id) { runCatching { playFocus.requestFocus() } }
  val inProgress = item.playedPercent > 0f && !played

  Box(Modifier.fillMaxSize()) {
    Backdrop(state.jellyfin.backdropUrl(item), width = 900, height = 506)
    Column(
      Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, top = 32.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        KpButton("◀", prefs.accent, onBack, height = 44.dp)
        val trail = listOfNotNull("HJEM", item.seriesName?.uppercase(), item.name.uppercase()).joinToString("  ›  ")
        Txt(trail, Kp.vt(22.sp, Kp.Muted, 3.sp), maxLines = 1)
        Spacer(Modifier.weight(1f))
        Txt(clock.hhmm(), Kp.vt(40.sp))
      }

      Column(Modifier.width(600.dp).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Txt(if (item.type == "Episode") "JELLYFIN · EPISODE" else "JELLYFIN · FILM", Kp.vt(22.sp, prefs.accent, 3.sp))
        Txt(item.name.uppercase(), Kp.anton(72.sp, Kp.Cream, 1.sp), maxLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (item.seriesName != null) Tag("S${item.season ?: 1} E${item.episode ?: 1}")
          item.year?.let { Tag(it.toString()) }
          item.runTimeMinutes?.let { Tag("$it MIN") }
          if (item.genres.isNotEmpty()) Tag(item.genres.take(2).joinToString(" · ").uppercase())
        }
        if (item.overview.isNotBlank()) Txt(item.overview, Kp.vt(24.sp, Kp.Body), maxLines = 4)
        if (inProgress) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgressBar(item.playedPercent / 100f, prefs.accent, 260.dp, 6.dp)
            Txt("${item.playedPercent.toInt()} % SETT", Kp.vt(22.sp, Kp.Muted))
            item.minutesLeft?.let { Txt("FERDIG KL. ${clock.hhmm(it)}", Kp.vt(22.sp)) }
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        KpButton(
          if (inProgress) "▶ FORTSETT" else "▶ SPILL AV",
          prefs.accent,
          { Playback.play(context, state, item, fromStart = !inProgress) },
          Modifier.focusRequester(playFocus),
          primary = true,
          height = 54.dp,
          fontSize = 24,
        )
        if (inProgress) {
          KpButton("↺ START PÅ NYTT", prefs.accent, { Playback.play(context, state, item, fromStart = true) }, height = 54.dp)
        }
        KpButton(if (favorite) "★ FAVORITT" else "☆ FAVORITT", prefs.accent, {
          favorite = !favorite
          scope.launch { runCatching { state.jellyfin.setFavorite(item.id, favorite) } }
        }, active = favorite, height = 54.dp)
        KpButton(if (played) "✓ SETT" else "○ MERK SOM SETT", prefs.accent, {
          played = !played
          scope.launch { runCatching { state.jellyfin.setPlayed(item.id, played) } }
        }, active = played, height = 54.dp)
      }

      item.runTimeMinutes?.let { total ->
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
          if (inProgress) {
            Txt("▶ Fortsetter du nå, er den ferdig kl. ${clock.hhmm(item.minutesLeft ?: total)}", Kp.vt(22.sp, Kp.Muted))
            Txt("↺ Fra start: ferdig kl. ${clock.hhmm(total)}", Kp.vt(22.sp, Kp.Muted))
          } else {
            Txt("▶ Starter du nå, er den ferdig kl. ${clock.hhmm(total)}", Kp.vt(22.sp, Kp.Muted))
          }
        }
      }

      Spacer(Modifier.weight(1f))
      FooterHint(
        "OK SPILL AV   ◀ TILBAKE",
        "AVSPILLER: ${prefs.player.label.uppercase()}",
      )
    }
  }
}
