package no.kianosh.kiaplay

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

enum class Curvature(val label: String, val amount: Float) { OFF("AV", 0f), LIGHT("LITT", 5.5f), STRONG("MYE", 3.6f) }

/** True when the TV can run the bulge shader (Android 13 and newer). */
val curveSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

// Classic CRT barrel distortion: the picture bows outwards and the corners fall away into black.
private const val BARREL = """
uniform shader content;
uniform float2 size;
uniform float curve;
half4 main(float2 coord) {
  float2 uv = coord / size * 2.0 - 1.0;
  float2 off = abs(uv.yx) / curve;
  uv = uv + uv * off * off;
  uv = uv * 0.5 + 0.5;
  if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return half4(0.0, 0.0, 0.0, 1.0);
  return content.eval(uv * size);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object BarrelShader {
  val shader = RuntimeShader(BARREL)
}

/** Bends everything drawn inside this modifier like the glass of an old picture tube. */
fun Modifier.crtCurve(curvature: Curvature): Modifier {
  if (curvature == Curvature.OFF || !curveSupported) return this
  return this.graphicsLayer {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && size.width > 0f) {
      val s = BarrelShader.shader
      s.setFloatUniform("size", size.width, size.height)
      s.setFloatUniform("curve", curvature.amount)
      renderEffect = RenderEffect.createRuntimeShaderEffect(s, "content").asComposeRenderEffect()
    }
  }
}
