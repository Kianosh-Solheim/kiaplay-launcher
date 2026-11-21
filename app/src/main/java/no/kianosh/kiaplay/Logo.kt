package no.kianosh.kiaplay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/** How far each part of the logo has come in; 1f everywhere is the finished logo. */
data class LogoState(
  val square: Float = 1f,
  val glow: Float = 0.4f,
  val k: Float = 1f,
  val bands: Float = 0f,
  val frame: Float = 1f,
  val word: Float = 1f,
  val wordStretch: Float = 1f,
)

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * The Kiaplay mark: cream frame, red square with a striped "K", and KIAPLAY stretched to the
 * square's width. Geometry follows the design (square 304 inside a 360×450 frame).
 */
@Composable
fun KiaplayLogo(width: Dp, accent: Color, state: LogoState = LogoState(), modifier: Modifier = Modifier) {
  val measurer = rememberTextMeasurer()
  val word = measurer.measure("KIAPLAY", TextStyle(fontFamily = Kp.Anton, fontSize = 100.sp))
  Canvas(modifier.size(width, width * (450f / 360f))) {
    val u = size.width / 360f
    val sq = Offset(28 * u, 28 * u)
    val sqSize = 304 * u

    if (state.glow > 0f) {
      drawRect(
        Brush.radialGradient(
          listOf(accent.copy(alpha = 0.85f * state.glow), Color.Transparent),
          center = Offset(180 * u, 180 * u),
          radius = 300 * u,
        ),
        topLeft = Offset(-140 * u, -140 * u),
        size = Size(640 * u, 640 * u),
      )
    }

    if (state.frame > 0f) {
      val t = state.frame
      val l = lerp(28 * u, 0f, t)
      val top = lerp(28 * u, 0f, t)
      val r = lerp(332 * u, 360 * u, t)
      val b = lerp(332 * u, 450 * u, t)
      drawRect(Kp.Cream.copy(alpha = lerp(0.2f, 1f, t)), Offset(l, top), Size(r - l, b - top))
    }

    drawRect(accent.copy(alpha = state.square), sq, Size(sqSize, sqSize))

    if (state.k > 0f) {
      clipRect(sq.x, sq.y, sq.x + sqSize, sq.y + sqSize * state.k) {
        drawK(sq, u, Kp.CreamBright)
      }
      clipRect(sq.x, sq.y, sq.x + sqSize, sq.y + sqSize) {
        var y = sq.y + 9 * u + (state.bands % 1f) * 12 * u - 12 * u
        while (y < sq.y + sqSize) {
          drawRect(Kp.Band, Offset(sq.x, y), Size(sqSize, 3 * u))
          y += 12 * u
        }
      }
    }

    if (state.word > 0f) {
      val sx = sqSize / word.size.width
      val cx = 180 * u
      val cy = (332 + 59) * u
      withTransform({
        scale(sx * state.wordStretch, sx * 1.12f, pivot = Offset(cx, cy))
      }) {
        drawText(
          word,
          color = Kp.Ink.copy(alpha = state.word),
          topLeft = Offset(cx - word.size.width / 2f, cy - word.size.height / 2f),
        )
      }
    }
  }
}

private fun DrawScope.drawK(origin: Offset, u: Float, color: Color) {
  fun poly(vararg pts: Float) {
    val p = Path()
    p.moveTo(origin.x + pts[0] * u, origin.y + pts[1] * u)
    var i = 2
    while (i < pts.size) {
      p.lineTo(origin.x + pts[i] * u, origin.y + pts[i + 1] * u)
      i += 2
    }
    p.close()
    drawPath(p, color)
  }
  poly(72f, 40f, 132f, 40f, 132f, 264f, 72f, 264f)
  poly(132f, 150f, 196f, 40f, 260f, 40f, 172f, 180f)
  poly(150f, 150f, 204f, 150f, 262f, 264f, 198f, 264f)
}
