package no.kianosh.kiaplay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** All installed apps: tabs for all / favourites / system, and an action bar for the focused app. */
@Composable
fun AppsScreen(state: AppState, onTab: (Tab) -> Unit, onMenu: () -> Unit) {
  val prefs = state.prefs
  val context = LocalContext.current
  var filter by remember { mutableStateOf("ALLE") }
  var selected by remember { mutableStateOf(state.apps.firstOrNull()) }
  val favs = state.favoriteApps().map { it.packageName }.toSet()
  val list = state.apps.filter {
    when (filter) {
      "FAVORITTER" -> it.packageName in favs
      "SYSTEM" -> it.isSystem
      else -> true
    }
  }

  ScreenFrame(state, Tab.APPS, onTab, onMenu) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Txt("APPER", Kp.anton(40.sp, Kp.Cream, 2.sp))
      Txt("${state.apps.size} INSTALLERT", Kp.vt(26.sp, Kp.Muted, 1.sp), Modifier.padding(end = 16.dp))
      ChipRow(listOf("ALLE", "FAVORITTER", "SYSTEM"), filter, prefs.accent) { filter = it }
    }
    LazyVerticalGrid(
      columns = GridCells.Fixed(6),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
      contentPadding = PaddingValues(8.dp),
      modifier = Modifier.weight(1f),
    ) {
      items(list, key = { it.packageName }) { app ->
        Focusable(onClick = { AppSource.launch(context, app.packageName) }, onFocus = { selected = app }) { focused ->
          Column {
            AppTile(app, prefs.accent, focused, 172.dp, 104.dp)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
              Txt(app.label.uppercase(), Kp.vt(18.sp, Kp.Muted), Modifier.weight(1f), maxLines = 1)
              Txt(if (app.packageName in favs) "★" else "☆", Kp.vt(18.sp, if (app.packageName in favs) prefs.accent else Kp.Muted))
            }
          }
        }
      }
    }
    val sel = selected
    Row(
      Modifier
        .fillMaxWidth()
        .border(2.dp, Kp.Border, corner)
        .background(Kp.SurfaceDeep)
        .padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Txt("VALGT: ${sel?.label?.uppercase() ?: "–"}", Kp.anton(22.sp, Kp.Cream, 2.sp), maxLines = 1)
      if (sel != null) {
        val isFav = sel.packageName in favs
        KpButton(if (isFav) "★ I FAVORITTER" else "☆ LEGG TIL FAVORITT", prefs.accent, {
          state.toggleFavoriteApp(sel.packageName)
        }, primary = isFav, height = 40.dp, fontSize = 18)
        KpButton("ⓘ APPINFO", prefs.accent, { AppSource.openAppInfo(context, sel.packageName) }, height = 40.dp, fontSize = 18)
      }
      Spacer(Modifier.weight(1f))
      Txt("Favoritter vises på startskjermen", Kp.vt(20.sp, Kp.Muted))
    }
    Box(Modifier.height(0.dp))
    FooterHint("◀ ▶ ▲ ▼ NAVIGER   OK ÅPNE   ▼ ALTERNATIVER")
  }
}
