package no.kianosh.kiaplay

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlinx.coroutines.delay
import kotlin.random.Random

private val noiseBitmap by lazy {
  val size = 192
  val pixels = IntArray(size * size) {
    val v = Random.nextInt(256)
    (0xFF shl 24) or (v shl 16) or (v shl 8) or v
  }
  Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * Draws the CRT layer over the whole screen: scanlines, film grain and a dark vignette.
 * Grain uses an overlay blend, so pure black stays black.
 */
@Composable
fun CrtOverlay(scanlines: Boolean, crt: Boolean) {
  var grainShift by remember { mutableStateOf(Offset.Zero) }
  if (crt) {
    LaunchedEffect(Unit) {
      while (true) {
        grainShift = Offset(Random.nextInt(0, 192).toFloat(), Random.nextInt(0, 192).toFloat())
        delay(90)
      }
    }
  }
  val grainBrush = remember { ShaderBrush(ImageShader(noiseBitmap, TileMode.Repeated, TileMode.Repeated)) }
  // The UI is drawn at 1280×720 and scaled to the screen, so measure in real screen pixels;
  // otherwise 1-pixel lines end up thinner than a pixel and blur into an even grey.
  val designScale = LocalDesignScale.current
  Canvas(Modifier.fillMaxSize()) {
    val screenPx = 1f / designScale.coerceAtLeast(0.1f)
    if (crt) {
      val grainSize = 2f * screenPx
      withTransform({ scale(grainSize, grainSize, pivot = Offset.Zero) }) {
        translate(-grainShift.x, -grainShift.y) {
          drawRect(
            brush = grainBrush,
            topLeft = Offset.Zero,
            size = Size(size.width / grainSize + 192f, size.height / grainSize + 192f),
            alpha = 0.3f,
            blendMode = BlendMode.Overlay,
          )
        }
      }
    }
    if (scanlines) {
      // A dark line two screen pixels tall every four pixels: about 270 lines on a 1080p TV.
      val line = Color.Black.copy(alpha = 0.38f)
      val h = 2f * screenPx
      val step = 4f * screenPx
      var y = 0f
      while (y < size.height) {
        drawRect(line, topLeft = Offset(0f, y), size = Size(size.width, h))
        y += step
      }
    }
    drawRect(
      Brush.radialGradient(
        0.55f to Color.Transparent,
        1.0f to Color.Black.copy(alpha = 0.88f),
        center = center,
        radius = size.width * 0.62f,
      ),
    )
  }
}
