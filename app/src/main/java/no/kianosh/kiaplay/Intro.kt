package no.kianosh.kiaplay

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

private const val INTRO_SECONDS = 6.2f

/** Piecewise-linear keyframes: pairs of (progress, value). */
private fun keys(p: Float, vararg kv: Float): Float {
  if (p <= kv[0]) return kv[1]
  var i = 0
  while (i + 3 < kv.size) {
    val p0 = kv[i]; val v0 = kv[i + 1]; val p1 = kv[i + 2]; val v1 = kv[i + 3]
    if (p <= p1) return v0 + (v1 - v0) * ((p - p0) / (p1 - p0))
    i += 2
  }
  return kv[kv.size - 1]
}

private fun span(t: Float, start: Float, len: Float) = ((t - start) / len).coerceIn(0f, 1f)

/** The startup ident: red square pulses in, striped K wipes down, frame and name grow out. */
@Composable
fun IntroScreen(prefs: Prefs, onDone: () -> Unit) {
  val clock = remember { Animatable(0f) }
  var jitter by remember { mutableFloatStateOf(0f) }
  val focus = remember { FocusRequester() }
  val speed = if (prefs.introShort) 3f else 1f

  LaunchedEffect(Unit) {
    focus.requestFocus()
    if (prefs.introSound && prefs.volume > 0) {
      launch(Dispatchers.Default) { IntroSound.play(prefs.volume / 10f, speed) }
    }
    launch {
      while (true) {
        jitter = Random.nextFloat() * 1.6f - 0.8f
        delay(90)
      }
    }
    clock.animateTo(INTRO_SECONDS, tween((INTRO_SECONDS * 1000 / speed).toInt(), easing = LinearEasing))
    onDone()
  }

  val t = clock.value
  val state = LogoState(
    square = keys(span(t, 0.3f, 1.1f), 0f, 0f, 0.35f, 0.55f, 0.55f, 0.35f, 1f, 1f),
    glow = keys(span(t, 0.3f, 2.5f), 0f, 0f, 0.3f, 0.55f, 0.45f, 0.35f, 0.75f, 1f, 0.88f, 1f, 1f, 0.4f),
    k = floor(span(t, 1.4f, 0.8f) * 9f) / 9f,
    bands = if (t > 1.4f) (t - 1.4f) / 1.6f * 4f else 0f,
    frame = span(t, 2.4f, 0.8f).let { 1f - (1f - it) * (1f - it) },
    word = span(t, 2.9f, 0.6f),
    wordStretch = 1.25f - 0.25f * span(t, 2.9f, 0.6f),
  )
  val fade = 1f - span(t, 5.6f, 0.6f)

  Box(
    Modifier
      .fillMaxSize()
      .background(Kp.Black)
      .onKeyEvent {
        if (prefs.skipOnKey && it.type == KeyEventType.KeyDown) {
          IntroSound.stop()
          onDone()
          true
        } else {
          false
        }
      }
      .focusRequester(focus)
      .focusable(),
    contentAlignment = Alignment.Center,
  ) {
    KiaplayLogo(
      width = 360.dp,
      accent = prefs.accent,
      state = state,
      modifier = Modifier.offset(x = jitter.dp).alpha(fade),
    )
  }
}

/** Synthesises the intro sound (deep boom, hum, swoosh, warm chord, bell) at runtime. */
object IntroSound {
  private var track: AudioTrack? = null

  fun stop() {
    try { track?.stop(); track?.release() } catch (_: Exception) {}
    track = null
  }

  suspend fun play(volume: Float, speed: Float) = withContext(Dispatchers.Default) {
    val rate = 22050
    val seconds = INTRO_SECONDS / speed
    val n = (rate * seconds).toInt()
    val out = FloatArray(n)
    fun at(s: Float) = s / speed

    fun env(time: Float, start: Float, attack: Float, end: Float): Float {
      if (time < start || time > end) return 0f
      val a = (time - start) / attack
      if (a < 1f) return a
      return exp(-3f * (time - start - attack) / (end - start - attack))
    }

    var lp1 = 0f
    var bpLow = 0f
    var bpBand = 0f
    val chord = floatArrayOf(130.81f, 196f, 261.63f, 329.63f, 392f)
    val phases = FloatArray(chord.size)
    var chordLp = 0f
    var boomPhase = 0f
    var subPhase = 0f
    var boom2Phase = 0f
    var punchLp = 0f
    val combs = listOf(0.0297f, 0.0371f, 0.0411f, 0.0437f).map { FloatArray((it * rate).toInt()) }
    val combPos = IntArray(combs.size)
    val combLp = FloatArray(combs.size)
    for (i in 0 until n) {
      val s = i / rate.toFloat()
      var v = 0f

      // Oomph: a deep, heavy boom as the square appears, plus a smaller hit when the frame opens.
      val tf = s * speed
      var boom = 0f
      if (tf >= 0.3f) {
        val f1 = (95f * exp(-0.86f * (tf - 0.3f))).coerceAtLeast(24f)
        boomPhase += 2f * PI.toFloat() * f1 / rate
        boom += sin(boomPhase) * 1.0f * env(s, at(0.3f), at(0.01f), at(2.8f))
        val f2 = (48f * exp(-0.42f * (tf - 0.3f))).coerceAtLeast(20f)
        subPhase += 2f * PI.toFloat() * f2 / rate
        boom += sin(subPhase) * 0.9f * env(s, at(0.3f), at(0.02f), at(3.3f))
        val punchEnv = env(s, at(0.3f), at(0.003f), at(0.45f))
        if (punchEnv > 0f) {
          punchLp += 0.07f * ((Random.nextFloat() * 2f - 1f) - punchLp)
          boom += punchLp * 4f * punchEnv
        }
      }
      if (tf >= 2.4f) {
        val f3 = (70f * exp(-0.94f * (tf - 2.4f))).coerceAtLeast(30f)
        boom2Phase += 2f * PI.toFloat() * f3 / rate
        boom += sin(boom2Phase) * 0.55f * env(s, at(2.4f), at(0.01f), at(3.6f))
      }
      // Saturate so the boom is felt on small TV speakers too, then add a dark hall tail.
      boom = tanh(2.6f * boom) / tanh(2.6f)
      var wet = 0f
      for (k in combs.indices) {
        val buf = combs[k]
        val pos = combPos[k]
        val delayed = buf[pos]
        combLp[k] += 0.25f * (delayed - combLp[k])
        buf[pos] = boom + combLp[k] * 0.84f
        combPos[k] = (pos + 1) % buf.size
        wet += delayed
      }
      v += boom * 0.95f + wet * 0.16f

      // Low CRT hum.
      val saw = ((s * 50f) % 1f) * 2f - 1f
      lp1 += 0.05f * (saw - lp1)
      v += lp1 * 0.18f * env(s, at(0.3f), at(0.6f), at(5.8f))

      // Swoosh while the K wipes in: band-passed noise with a rising centre.
      val sweepEnv = env(s, at(1.4f), at(0.2f), at(2.3f))
      if (sweepEnv > 0f) {
        val fc = 600f * exp(2.08f * span(tf, 1.4f, 0.8f))
        val f = 2f * sin(PI.toFloat() * fc / rate)
        val noise = Random.nextFloat() * 2f - 1f
        bpLow += f * bpBand
        val high = noise - bpLow - 0.25f * bpBand
        bpBand += f * high
        v += bpBand * 0.35f * sweepEnv
      }

      // Warm synth chord when the frame opens.
      val chordEnv = env(s, at(2.4f), at(0.5f), at(5.9f))
      if (chordEnv > 0f) {
        var c = 0f
        for (k in chord.indices) {
          phases[k] = (phases[k] + chord[k] / rate) % 1f
          c += phases[k] * 2f - 1f
        }
        val cutoff = 0.02f + 0.1f * span(tf, 2.4f, 1.0f)
        chordLp += cutoff * (c / chord.size - chordLp)
        v += chordLp * 0.9f * chordEnv
      }

      // Bell with the wordmark.
      v += sin(2f * PI.toFloat() * 1046.5f * s) * 0.12f * env(s, at(2.95f), 0.02f, at(4.5f))

      // Fade the tail.
      val tail = 1f - span(tf, 5.6f, 0.6f)
      out[i] = (v * tail * 0.45f * volume).coerceIn(-1f, 1f)
    }

    val pcm = ShortArray(n) { (out[it] * 32767).toInt().toShort() }
    stop()
    val t = AudioTrack.Builder()
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build(),
      )
      .setAudioFormat(
        AudioFormat.Builder()
          .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
          .setSampleRate(rate)
          .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
          .build(),
      )
      .setBufferSizeInBytes(n * 2)
      .setTransferMode(AudioTrack.MODE_STATIC)
      .build()
    t.write(pcm, 0, n)
    track = t
    t.play()
  }
}
