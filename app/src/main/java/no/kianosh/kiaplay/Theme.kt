package no.kianosh.kiaplay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Palette taken from the Kiaplay design canvas. */
object Kp {
  val Black = Color(0xFF000000)
  val Surface = Color(0xFF141110)
  val SurfaceDeep = Color(0xFF0A0808)
  val Border = Color(0xFF2A2420)
  val Cream = Color(0xFFEFE4CC)
  val CreamBright = Color(0xFFF6EEDC)
  val CreamText = Color(0xFFFFF6E6)
  val Muted = Color(0xFF8F8579)
  val Body = Color(0xFFC9BFAE)
  val Ink = Color(0xFF1A1513)
  val Band = Color(0xFFD93A28)
  val Green = Color(0xFF8DB580)
  val Mustard = Color(0xFFD9A441)
  val Teal = Color(0xFF6FA8A0)
  val Lilac = Color(0xFF8C6A9E)

  val accents = listOf(Color(0xFFE0402E), Teal, Mustard, Lilac)

  val Anton = FontFamily(Font(R.font.anton))
  val Vt = FontFamily(Font(R.font.vt323))

  fun anton(size: TextUnit, color: Color = Cream, spacing: TextUnit = 2.sp) =
    TextStyle(fontFamily = Anton, fontSize = size, color = color, letterSpacing = spacing)

  fun vt(size: TextUnit, color: Color = Cream, spacing: TextUnit = 0.sp) =
    TextStyle(fontFamily = Vt, fontSize = size, color = color, letterSpacing = spacing)
}

/** How much the 1280×720 design surface is scaled to reach the real screen (e.g. 0.75 on 1080p). */
val LocalDesignScale = staticCompositionLocalOf { 1f }

/**
 * Lays the UI out on a fixed 1280×720 design surface (the same size as the design canvas)
 * and scales it to whatever the TV reports, so every number in the design maps 1:1.
 */
@Composable
fun DesignSurface(content: @Composable () -> Unit) {
  BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    val scale = minOf(maxWidth.value / 1280f, maxHeight.value / 720f)
    Box(
      Modifier
        .requiredSize(1280.dp, 720.dp)
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
        },
    ) { CompositionLocalProvider(LocalDesignScale provides scale) { content() } }
  }
}
