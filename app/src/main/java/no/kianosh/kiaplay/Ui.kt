package no.kianosh.kiaplay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.util.Calendar

val corner = RoundedCornerShape(3.dp)

/** A focusable, clickable surface that reports whether the remote's focus is on it. */
@Composable
fun Focusable(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  onFocus: () -> Unit = {},
  content: @Composable (focused: Boolean) -> Unit,
) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  LaunchedEffect(focused) { if (focused) onFocus() }
  Box(modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)) {
    content(focused)
  }
}

/** Glow + ring used for the focused element, in the style of the design. */
fun Modifier.focusRing(focused: Boolean, accent: Color, idle: Color = Kp.Border, width: Dp = 3.dp): Modifier =
  if (focused) {
    this
      .scale(1.05f)
      .shadow(18.dp, corner, ambientColor = accent, spotColor = accent)
      .border(width, accent, corner)
  } else {
    this.border(2.dp, idle, corner)
  }

@Composable
fun Txt(text: String, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
  BasicText(text, modifier, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
fun KpButton(
  text: String,
  accent: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  primary: Boolean = false,
  active: Boolean = false,
  height: Dp = 48.dp,
  fontSize: Int = 22,
) {
  Focusable(onClick, modifier) { focused ->
    val bg = when {
      primary -> accent
      active -> Kp.Cream
      else -> Color.Black.copy(alpha = 0.6f)
    }
    val fg = when {
      primary -> Kp.CreamText
      active -> Kp.Black
      else -> Kp.Cream
    }
    Box(
      Modifier
        .height(height)
        .focusRing(focused, if (primary) Kp.Cream else accent, idle = if (primary || active) bg else Kp.Border)
        .clip(corner)
        .background(bg)
        .padding(horizontal = 22.dp),
      contentAlignment = Alignment.Center,
    ) {
      Txt(text, Kp.anton(fontSize.sp, fg))
    }
  }
}

/** Current time as HH:MM, refreshed every 15 seconds. */
@Composable
fun rememberClock(): Calendar {
  var now by remember { mutableStateOf(Calendar.getInstance()) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(15_000)
      now = Calendar.getInstance()
    }
  }
  return now
}

fun Calendar.hhmm(plusMinutes: Int = 0): String {
  val c = clone() as Calendar
  c.add(Calendar.MINUTE, plusMinutes)
  return String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}

enum class Tab(val label: String) { HOME("HJEM"), FILMS("FILMER"), SERIES("SERIER"), MUSIC("MUSIKK"), APPS("APPER") }

@Composable
fun TopBar(
  prefs: Prefs,
  active: Tab?,
  weather: Weather?,
  onTab: (Tab) -> Unit,
  onMenu: () -> Unit,
) {
  val clock = rememberClock()
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
    KiaplayLogo(58.dp, prefs.accent)
    Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
      for (tab in Tab.entries) {
        Focusable(onClick = { onTab(tab) }) { focused ->
          val on = tab == active
          Column {
            Txt(tab.label, Kp.anton(22.sp, if (on || focused) Kp.Cream else Kp.Muted, 3.sp))
            Spacer(Modifier.height(4.dp))
            Box(
              Modifier
                .width(if (on || focused) 60.dp else 0.dp)
                .height(3.dp)
                .background(if (focused) Kp.Cream else prefs.accent),
            )
          }
        }
      }
    }
    Spacer(Modifier.weight(1f))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
      weather?.let { Txt("${it.temp}° ${it.label} · ${prefs.city.name}", Kp.vt(24.sp, Kp.Muted)) }
      Focusable(onClick = onMenu) { focused ->
        Box(
          Modifier
            .height(36.dp)
            .focusRing(focused, prefs.accent)
            .padding(horizontal = 12.dp),
          contentAlignment = Alignment.Center,
        ) { Txt("☰ MENY", Kp.vt(22.sp)) }
      }
      Txt(clock.hhmm(), Kp.vt(44.sp))
    }
  }
}

private val coverPalette = listOf(
  Triple(Color(0xFF2F5D2A), Kp.CreamBright, Color(0xFFE0A93A)),
  Triple(Color(0xFF1F2A3A), Kp.CreamBright, Color(0xFFE0402E)),
  Triple(Color(0xFF0E0E0E), Color(0xFFE0402E), Kp.Cream),
  Triple(Color(0xFFC9A24A), Color(0xFF111111), Color(0xFF111111)),
  Triple(Color(0xFF4A2A5A), Kp.CreamBright, Kp.Mustard),
  Triple(Color(0xFF23343A), Kp.CreamBright, Color(0xFFE0402E)),
  Triple(Color(0xFFE06A3A), Color(0xFF111111), Color(0xFF1D3A5A)),
  Triple(Color(0xFF6B4A2A), Kp.CreamBright, Color(0xFF111111)),
  Triple(Color(0xFFE8DCC0), Color(0xFFB0281C), Color(0xFF111111)),
)

/** Poster from Jellyfin, or a retro VHS-style cover when the item has no artwork. */
@Composable
fun Cover(
  title: String,
  subtitle: String,
  imageUrl: String?,
  width: Dp,
  accent: Color,
  focused: Boolean,
  progress: Float = 0f,
  ratio: Float = 1.42f,
) {
  val (bg, ink, band) = coverPalette[(title.hashCode() and 0x7fffffff) % coverPalette.size]
  Column(
    Modifier
      .size(width, width * ratio)
      .focusRing(focused, accent)
      .clip(corner)
      .background(Kp.Surface),
  ) {
    Box(Modifier.weight(1f).fillMaxWidth()) {
      Column(
        Modifier.fillMaxSize().background(bg).padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Txt("KIAPLAY VIDEO", Kp.vt(12.sp, ink.copy(alpha = 0.8f), 1.sp))
        Box(
          Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .height(10.dp)
            .drawBehind {
              var x = 0f
              while (x < size.width) {
                drawRect(band, Offset(x, 0f), Size(14.dp.toPx(), size.height))
                x += 18.dp.toPx()
              }
            },
        )
        Spacer(Modifier.weight(1f))
        Txt(
          title.uppercase(),
          Kp.anton(if (title.length > 14) 15.sp else if (title.length > 9) 18.sp else 22.sp, ink, 0.5.sp),
          maxLines = 4,
        )
        Txt(subtitle, Kp.vt(15.sp, ink))
      }
      if (imageUrl != null) {
        AsyncImage(
          model = imageUrl,
          contentDescription = title,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      }
    }
    if (progress > 0f) {
      Box(Modifier.fillMaxWidth().height(4.dp).background(Kp.Border)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).background(accent))
      }
    }
  }
}

@Composable
fun ProgressBar(fraction: Float, accent: Color, width: Dp, height: Dp = 5.dp) {
  Box(Modifier.width(width).height(height).background(Kp.Border)) {
    Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).background(accent))
  }
}

@Composable
fun Tag(text: String) {
  Box(Modifier.border(2.dp, Kp.Border, corner).padding(horizontal = 10.dp, vertical = 2.dp)) {
    Txt(text, Kp.vt(20.sp))
  }
}

@Composable
fun SectionTitle(text: String) {
  Txt(text, Kp.anton(22.sp, Kp.Cream, 3.sp))
}

@Composable
fun FooterHint(left: String, right: String = "") {
  Row(Modifier.fillMaxWidth()) {
    Txt(left, Kp.vt(20.sp, Kp.Muted, 2.sp))
    Spacer(Modifier.weight(1f))
    Txt(right, Kp.vt(20.sp, Kp.Muted, 2.sp))
  }
}
