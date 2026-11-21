package no.kianosh.kiaplay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** Big backdrop on the right, faded into black towards the text and the rows below. */
@Composable
fun Backdrop(url: String?, width: Int = 760, height: Int = 428) {
  Box(Modifier.fillMaxSize()) {
    Box(Modifier.align(Alignment.TopEnd).size(width.dp, height.dp)) {
      if (url != null) {
        AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
      } else {
        Box(
          Modifier.fillMaxSize().background(
            Brush.verticalGradient(
              listOf(Color(0xFF3A0F0A), Color.Black),
            ),
          ),
        )
      }
      Box(
        Modifier.fillMaxSize().background(
          Brush.horizontalGradient(
            0f to Color.Black,
            0.3f to Color.Black.copy(alpha = 0.85f),
            0.7f to Color.Transparent,
          ),
        ),
      )
      Box(
        Modifier.fillMaxSize().background(
          Brush.verticalGradient(0.7f to Color.Transparent, 1f to Color.Black),
        ),
      )
    }
  }
}

@Composable
fun HomeScreen(state: AppState, onOpen: (MediaItem) -> Unit, onTab: (Tab) -> Unit, onMenu: () -> Unit, onSettings: () -> Unit) {
  val prefs = state.prefs
  val context = LocalContext.current
  val clock = rememberClock()
  var hero by remember(state.resume) { mutableStateOf(state.resume.firstOrNull()) }
  val playFocus = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }

  Box(Modifier.fillMaxSize()) {
    Backdrop(hero?.let { state.jellyfin.backdropUrl(it) })
    Column(
      Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, top = 32.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      TopBar(prefs, Tab.HOME, state.weather, onTab, onMenu)

      // Hero
      Column(
        Modifier.height(214.dp).width(620.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
      ) {
        val item = hero
        if (!prefs.loggedIn) {
          Txt("JELLYFIN", Kp.vt(22.sp, prefs.accent, 3.sp))
          Txt("KOBLE TIL HJEMMESERVEREN", Kp.anton(52.sp, Kp.Cream, 1.sp))
          Txt("Logg inn på Jellyfin i innstillingene for å se filmer og serier her.", Kp.vt(22.sp, Kp.Body))
          KpButton("⚙ INNSTILLINGER", prefs.accent, onSettings, Modifier.focusRequester(playFocus), primary = true)
        } else if (item == null) {
          Txt("JELLYFIN · ${state.status}", Kp.vt(22.sp, prefs.accent, 3.sp))
          Txt("INGENTING Å FORTSETTE", Kp.anton(52.sp, Kp.Cream, 1.sp))
          KpButton("FILMER", prefs.accent, { onTab(Tab.FILMS) }, Modifier.focusRequester(playFocus), primary = true)
        } else {
          Txt("JELLYFIN · FORTSETT Å SE", Kp.vt(22.sp, prefs.accent, 3.sp))
          Txt(
            (item.seriesName ?: item.name).uppercase(),
            Kp.anton(58.sp, Kp.Cream, 1.sp),
            maxLines = 1,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            val meta = buildList {
              if (item.seriesName != null) add("S${item.season ?: 1} E${item.episode ?: 1} · ${item.name.uppercase()}")
              item.year?.let { add(it.toString()) }
              item.runTimeMinutes?.let { add("$it MIN") }
            }.joinToString(" · ")
            Txt(meta, Kp.vt(22.sp, Kp.Muted), maxLines = 1)
            ProgressBar(item.playedPercent / 100f, prefs.accent, 140.dp)
            Txt("${item.playedPercent.toInt()} % SETT", Kp.vt(22.sp, Kp.Muted))
            item.minutesLeft?.let { Txt("· FERDIG KL. ${clock.hhmm(it)}", Kp.vt(22.sp)) }
          }
          if (item.overview.isNotBlank()) Txt(item.overview, Kp.vt(22.sp, Kp.Body), maxLines = 2)
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpButton(
              "▶ SPILL AV",
              prefs.accent,
              { Playback.play(context, state, item, fromStart = false) },
              Modifier.focusRequester(playFocus),
              primary = true,
            )
            KpButton("MER INFO", prefs.accent, { onOpen(item) })
          }
        }
      }

      if (state.resume.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          SectionTitle("FORTSETT Å SE")
          LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 6.dp)) {
            items(state.resume, key = { it.id }) { item ->
              Focusable(onClick = { onOpen(item) }, onFocus = { hero = item }) { focused ->
                Cover(
                  item.seriesName ?: item.name,
                  item.year?.toString() ?: "",
                  state.jellyfin.posterUrl(item),
                  92.dp,
                  prefs.accent,
                  focused,
                  item.playedPercent / 100f,
                )
              }
            }
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("DINE APPER")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 6.dp)) {
          items(state.favoriteApps(), key = { it.packageName }) { app ->
            Focusable(onClick = { AppSource.launch(context, app.packageName) }) { focused ->
              AppTile(app, prefs.accent, focused, 172.dp, 66.dp)
            }
          }
          item {
            Focusable(onClick = { onTab(Tab.APPS) }) { focused ->
              Box(
                Modifier.size(120.dp, 66.dp).focusRing(focused, prefs.accent, Kp.Muted),
                contentAlignment = Alignment.Center,
              ) { Txt("+ ALLE", Kp.anton(20.sp, Kp.Muted)) }
            }
          }
        }
      }

      Spacer(Modifier.weight(1f))
      FooterHint(
        "◀ ▶ ▲ ▼ NAVIGER   OK ÅPNE   ☰ MENY",
        if (prefs.loggedIn) "● ${state.status}" else "",
      )
    }
  }
}

/** App tile: stripe in the app's colour on top, banner or name below. */
@Composable
fun AppTile(app: AppEntry, accent: Color, focused: Boolean, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp) {
  val stripe = appColor(app.packageName, accent)
  Column(
    Modifier
      .size(w, h)
      .focusRing(focused, accent)
      .clip(corner)
      .background(Kp.Surface),
  ) {
    Box(Modifier.fillMaxWidth().height(5.dp).background(stripe))
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      if (app.banner != null) {
        Image(app.banner, app.label, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp))
      } else {
        Txt(app.label.uppercase(), Kp.anton(20.sp, Kp.Cream, 2.sp), Modifier.padding(horizontal = 8.dp), maxLines = 1)
      }
    }
  }
}

fun appColor(pkg: String, accent: Color): Color {
  val colors = listOf(accent, Kp.Cream, Kp.Mustard, Kp.Green, Kp.Teal, Kp.Lilac)
  return colors[(pkg.hashCode() and 0x7fffffff) % colors.size]
}
