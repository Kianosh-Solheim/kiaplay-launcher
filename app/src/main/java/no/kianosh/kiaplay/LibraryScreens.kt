package no.kianosh.kiaplay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun ScreenFrame(state: AppState, tab: Tab?, onTab: (Tab) -> Unit, onMenu: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
  Column(
    Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, top = 32.dp, bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    TopBar(state.prefs, tab, state.weather, onTab, onMenu)
    content()
  }
}

@Composable
fun ChipRow(options: List<String>, selected: String, accent: androidx.compose.ui.graphics.Color, onPick: (String) -> Unit) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    for (o in options) {
      KpButton(o, accent, { onPick(o) }, active = o == selected, height = 38.dp, fontSize = 18)
    }
  }
}

/** Films (posters) or music (square covers): filter by genre and sort. */
@Composable
fun LibraryScreen(state: AppState, tab: Tab, onOpen: (MediaItem) -> Unit, onTab: (Tab) -> Unit, onMenu: () -> Unit) {
  val prefs = state.prefs
  val context = LocalContext.current
  val all = if (tab == Tab.MUSIC) state.music else state.films
  var genre by remember(tab) { mutableStateOf("ALLE") }
  var sort by remember(tab) { mutableStateOf("A–Å") }
  val genres = remember(all) {
    listOf("ALLE") + all.flatMap { it.genres }.groupingBy { it.uppercase() }.eachCount()
      .entries.sortedByDescending { it.value }.take(5).map { it.key }
  }
  val list = all
    .filter { genre == "ALLE" || it.genres.any { g -> g.uppercase() == genre } }
    .let { l ->
      when (sort) {
        "NYESTE" -> l.sortedByDescending { it.year ?: 0 }
        "ELDSTE" -> l.sortedBy { it.year ?: 9999 }
        else -> l
      }
    }

  ScreenFrame(state, tab, onTab, onMenu) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Txt(tab.label, Kp.anton(40.sp, Kp.Cream, 2.sp))
      Txt("${list.size} TITLER", Kp.vt(26.sp, Kp.Muted, 1.sp), Modifier.padding(end = 16.dp))
      ChipRow(genres, genre, prefs.accent) { genre = it }
      Spacer(Modifier.weight(1f))
      Txt("SORTER:", Kp.vt(22.sp, Kp.Muted))
      ChipRow(listOf("A–Å", "NYESTE", "ELDSTE"), sort, prefs.accent) { sort = it }
    }
    if (!prefs.loggedIn || all.isEmpty()) {
      Txt(
        if (!prefs.loggedIn) "Logg inn på Jellyfin i innstillingene for å se biblioteket." else "Ingen titler funnet.",
        Kp.vt(26.sp, Kp.Muted),
      )
    }
    val music = tab == Tab.MUSIC
    LazyVerticalGrid(
      columns = GridCells.Fixed(8),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
      contentPadding = PaddingValues(8.dp),
      modifier = Modifier.weight(1f),
    ) {
      items(list, key = { it.id }) { item ->
        Focusable(onClick = {
          if (music) AppSource.launch(context, Playback.JELLYFIN_TV) else onOpen(item)
        }) { focused ->
          Cover(
            item.name,
            item.year?.toString() ?: "",
            state.jellyfin.posterUrl(item),
            124.dp,
            prefs.accent,
            focused,
            item.playedPercent / 100f,
            ratio = if (music) 1f else 1.42f,
          )
        }
      }
    }
    FooterHint("◀ ▶ ▲ ▼ NAVIGER   OK DETALJER", "JELLYFIN · ${state.status}")
  }
}

/** Series grid on the left, the selected series with its episodes on the right. */
@Composable
fun SeriesScreen(state: AppState, initial: MediaItem?, onOpen: (MediaItem) -> Unit, onTab: (Tab) -> Unit, onMenu: () -> Unit) {
  val prefs = state.prefs
  val context = LocalContext.current
  val clock = rememberClock()
  val scope = rememberCoroutineScope()
  var selected by remember { mutableStateOf(initial ?: state.series.firstOrNull()) }
  var episodes by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
  var season by remember { mutableStateOf<Int?>(null) }
  var favorite by remember(selected) { mutableStateOf(selected?.favorite ?: false) }

  LaunchedEffect(selected?.id) {
    episodes = emptyList()
    selected?.let { s ->
      episodes = runCatching { state.jellyfin.episodes(s.id) }.getOrDefault(emptyList())
      season = episodes.firstOrNull { !it.played }?.season ?: episodes.firstOrNull()?.season
    }
  }
  val next = episodes.firstOrNull { it.playedPercent > 0f && !it.played } ?: episodes.firstOrNull { !it.played }

  ScreenFrame(state, Tab.SERIES, onTab, onMenu) {
    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
      Column(Modifier.width(620.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Txt("SERIER", Kp.anton(40.sp, Kp.Cream, 2.sp))
          Txt("${state.series.size} SERIER", Kp.vt(26.sp, Kp.Muted, 1.sp))
        }
        if (!prefs.loggedIn) Txt("Logg inn på Jellyfin i innstillingene.", Kp.vt(26.sp, Kp.Muted))
        LazyVerticalGrid(
          columns = GridCells.Fixed(4),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalArrangement = Arrangement.spacedBy(18.dp),
          contentPadding = PaddingValues(8.dp),
        ) {
          items(state.series, key = { it.id }) { s ->
            Focusable(onClick = { selected = s }, onFocus = { selected = s }) { focused ->
              val years = listOfNotNull(s.year, s.endYear).distinct().joinToString("–")
              Cover(s.name, years, state.jellyfin.posterUrl(s), 132.dp, prefs.accent, focused || s.id == selected?.id)
            }
          }
        }
      }

      val s = selected
      Column(
        Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (s != null) {
          Txt("JELLYFIN · SERIE", Kp.vt(22.sp, prefs.accent, 3.sp))
          Txt(s.name.uppercase(), Kp.anton(50.sp, Kp.Cream, 1.sp), maxLines = 2)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOfNotNull(s.year, s.endYear).distinct().joinToString("–").takeIf { it.isNotBlank() }?.let { Tag(it) }
            Tag("${episodes.size} EPISODER")
            s.genres.take(2).takeIf { it.isNotEmpty() }?.let { Tag(it.joinToString(" · ").uppercase()) }
          }
          if (s.overview.isNotBlank()) Txt(s.overview, Kp.vt(22.sp, Kp.Body), maxLines = 3)
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (next != null) {
              KpButton(
                "▶ ${if (next.playedPercent > 0f) "FORTSETT" else "SPILL AV"} S${next.season ?: 1} E${next.episode ?: 1}",
                prefs.accent,
                { Playback.play(context, prefs, state.jellyfin, next, fromStart = false) },
                primary = true,
              )
            }
            KpButton(if (favorite) "★ FAVORITT" else "☆ FAVORITT", prefs.accent, {
              favorite = !favorite
              scope.launch { runCatching { state.jellyfin.setFavorite(s.id, favorite) } }
            }, active = favorite)
          }
          next?.minutesLeft?.let {
            Txt("▶ Starter du nå, er episoden ferdig kl. ${clock.hhmm(it)}", Kp.vt(21.sp, Kp.Muted))
          }
          val seasons = episodes.mapNotNull { it.season }.distinct().sorted()
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (n in seasons) {
              KpButton(if (n == 0) "SPESIALER" else "SESONG $n", prefs.accent, { season = n }, active = n == season, height = 36.dp, fontSize = 18)
            }
          }
          LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(6.dp)) {
            items(episodes.filter { it.season == season }, key = { it.id }) { e ->
              Focusable(onClick = { onOpen(e) }) { focused ->
                EpisodeRow(e, prefs.accent, focused || e.id == next?.id)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun EpisodeRow(e: MediaItem, accent: androidx.compose.ui.graphics.Color, highlighted: Boolean) {
  Row(
    Modifier
      .fillMaxWidth()
      .focusRing(highlighted, accent)
      .clip(corner)
      .background(if (highlighted) Kp.Surface else Kp.Black)
      .padding(horizontal = 14.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Txt("${e.episode ?: ""}", Kp.anton(26.sp, Kp.Muted), Modifier.width(34.dp))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Txt(e.name.uppercase(), Kp.anton(20.sp, Kp.Cream, 1.sp), maxLines = 1)
      val pct = if (e.played) 1f else e.playedPercent / 100f
      Box(Modifier.fillMaxWidth().height(4.dp).background(Kp.Border)) {
        Box(Modifier.fillMaxWidth(pct).height(4.dp).background(accent))
      }
    }
    when {
      e.played -> Txt("✓ SETT", Kp.vt(20.sp, Kp.Muted))
      e.playedPercent > 0f -> Txt("${e.playedPercent.toInt()} %", Kp.vt(20.sp, Kp.Muted))
      else -> Box(Modifier.background(accent).padding(horizontal = 8.dp)) { Txt("NY", Kp.vt(20.sp, Kp.CreamText)) }
    }
  }
}
