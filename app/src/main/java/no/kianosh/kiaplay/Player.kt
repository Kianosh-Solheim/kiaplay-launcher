package no.kianosh.kiaplay

import android.graphics.Color as AColor
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/** What to play: a queue (one film, the rest of a season, or an album) and where to start. */
data class PlayRequest(val queue: List<MediaItem>, val index: Int, val fromStart: Boolean)

private fun clockText(ms: Long): String {
  val s = (ms / 1000).coerceAtLeast(0)
  val h = s / 3600
  val m = (s % 3600) / 60
  val sec = s % 60
  return if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%d:%02d", m, sec)
}

private const val SUB_PREFIX = "kp-sub-"
private val imageSubMimes = setOf(MimeTypes.APPLICATION_PGS, MimeTypes.APPLICATION_VOBSUB, MimeTypes.APPLICATION_DVBSUBS)

/**
 * Kiaplay's own player. Plays straight from Jellyfin (or a transcoded stream when the file
 * can't be played directly), resumes where you left off, reports progress back to Jellyfin,
 * and lets you pick audio track and subtitles.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(state: AppState, request: PlayRequest, onExit: () -> Unit) {
  val context = LocalContext.current
  val view = LocalView.current
  val prefs = state.prefs
  val scope = rememberCoroutineScope()
  val sessionId = remember { UUID.randomUUID().toString().replace("-", "") }
  val exo = remember { ExoPlayer.Builder(context).build() }
  val focus = remember { FocusRequester() }
  val clock = rememberClock()

  var index by remember { mutableIntStateOf(request.index) }
  var transcoding by remember { mutableStateOf(false) }
  var positionMs by remember { mutableLongStateOf(0L) }
  var durationMs by remember { mutableLongStateOf(0L) }
  var playing by remember { mutableStateOf(true) }
  var osd by remember { mutableStateOf("▶ PLAY") }
  var osdTick by remember { mutableIntStateOf(0) }
  var barTick by remember { mutableIntStateOf(0) }
  var barVisible by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }
  var menuOpen by remember { mutableStateOf(false) }

  // Streams as Jellyfin lists them, and what is chosen (null subtitle = off).
  var streams by remember { mutableStateOf<List<JStream>>(emptyList()) }
  var audioPick by remember { mutableStateOf<JStream?>(null) }
  var subPick by remember { mutableStateOf<JStream?>(null) }
  var tracksApplied by remember { mutableStateOf(false) }
  var transcodeAudio by remember { mutableStateOf<Int?>(null) }

  val item = request.queue[index]
  val isAudio = item.type == "Audio"
  val audioStreams = streams.filter { it.type == "Audio" }
  val subStreams = streams.filter { it.type == "Subtitle" }

  fun showOsd(text: String) { osd = text; osdTick++ }
  fun showBar() { barVisible = true; barTick++ }

  fun startWith(i: Int, startMs: Long, transcode: Boolean, list: List<JStream>) {
    val it = request.queue[i]
    val url = if (transcode && it.type != "Audio") {
      state.jellyfin.transcodeUrl(it, sessionId, transcodeAudio)
    } else {
      state.jellyfin.streamUrl(it)
    }
    transcoding = transcode
    error = null
    // Text subtitles come from Jellyfin as WebVTT, so they work in direct play and transcoding alike.
    val subs = list.filter { s -> s.type == "Subtitle" && s.isText }.map { s ->
      androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(Uri.parse(state.jellyfin.subtitleUrl(it, s)))
        .setId("$SUB_PREFIX${s.index}")
        .setMimeType(MimeTypes.TEXT_VTT)
        .setLanguage(s.language)
        .setLabel(s.label)
        .build()
    }
    val media = androidx.media3.common.MediaItem.Builder()
      .setUri(url)
      .setSubtitleConfigurations(subs)
      .build()
    exo.setMediaItem(media, startMs)
    exo.prepare()
    exo.playWhenReady = true
    scope.launch { runCatching { state.jellyfin.reportStart(it, startMs, sessionId, transcode) } }
  }

  fun start(i: Int, startMs: Long, transcode: Boolean) {
    val it = request.queue[i]
    if (it.type == "Audio") {
      streams = emptyList()
      startWith(i, startMs, transcode, emptyList())
      return
    }
    scope.launch {
      val list = runCatching { state.jellyfin.streams(it) }.getOrDefault(emptyList())
      if (!transcode) {
        streams = list
        tracksApplied = false
        transcodeAudio = null
      }
      startWith(i, startMs, transcode, list)
    }
  }

  fun groupsOf(type: Int): List<Tracks.Group> = exo.currentTracks.groups.filter { it.type == type }

  fun selectAudio(s: JStream) {
    audioPick = s
    if (transcoding) {
      // The server sends only one audio track when transcoding, so ask it for the other one.
      transcodeAudio = s.index
      startWith(index, exo.currentPosition, true, streams)
      return
    }
    val ordinal = audioStreams.indexOf(s)
    val group = groupsOf(C.TRACK_TYPE_AUDIO).getOrNull(ordinal) ?: return
    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
      .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
      .build()
  }

  fun selectSub(s: JStream?) {
    subPick = s
    val builder = exo.trackSelectionParameters.buildUpon()
    if (s == null) {
      exo.trackSelectionParameters = builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
      return
    }
    val texts = groupsOf(C.TRACK_TYPE_TEXT)
    val group = if (s.isText) {
      texts.firstOrNull { g -> g.getTrackFormat(0).id == "$SUB_PREFIX${s.index}" }
    } else {
      // Picture subtitles (PGS/DVD) only exist inside the file, in the same order Jellyfin lists them.
      val ordinal = subStreams.filter { !it.isText }.indexOf(s)
      texts.filter { g ->
        val f = g.getTrackFormat(0)
        // Media3 may have converted the track to cues already; the original type is then in codecs.
        f.id?.startsWith(SUB_PREFIX) != true && (f.sampleMimeType in imageSubMimes || f.codecs in imageSubMimes)
      }.getOrNull(ordinal)
    }
    if (group == null) {
      showOsd("UNDERTEKST IKKE TILGJENGELIG")
      return
    }
    exo.trackSelectionParameters = builder
      .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
      .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
      .build()
  }

  /** First time tracks are known: apply the language choices from the settings. */
  fun applyDefaults() {
    if (tracksApplied || streams.isEmpty()) return
    tracksApplied = true
    val audio = when (prefs.audioLang) {
      AudioLang.NO -> audioStreams.firstOrNull { isNorwegian(it.language) }
      AudioLang.EN -> audioStreams.firstOrNull { isEnglish(it.language) }
      AudioLang.DEFAULT -> null
    } ?: audioStreams.firstOrNull { it.isDefault } ?: audioStreams.firstOrNull()
    if (audio != null) {
      if (audioStreams.indexOf(audio) > 0 && !transcoding) selectAudio(audio) else audioPick = audio
    }
    val sub = when (prefs.subLang) {
      SubLang.OFF -> subStreams.firstOrNull { it.forced }
      SubLang.NO -> subStreams.firstOrNull { isNorwegian(it.language) && !it.forced } ?: subStreams.firstOrNull { it.forced }
      SubLang.EN -> subStreams.firstOrNull { isEnglish(it.language) && !it.forced } ?: subStreams.firstOrNull { it.forced }
    }
    selectSub(sub)
  }

  fun stopReport(i: Int, pos: Long) {
    val it = request.queue[i]
    val tc = transcoding
    // Must outlive this screen, so it runs outside the composition's scope.
    CoroutineScope(Dispatchers.IO).launch { runCatching { state.jellyfin.reportStopped(it, pos, sessionId, tc) } }
  }

  fun skipTo(i: Int) {
    if (i !in request.queue.indices) return
    stopReport(index, exo.currentPosition)
    index = i
    audioPick = null
    subPick = null
    start(i, 0L, false)
    showBar()
  }

  DisposableEffect(Unit) {
    val listener = object : Player.Listener {
      override fun onPlayerError(e: PlaybackException) {
        if (!transcoding && request.queue[index].type != "Audio") {
          // Codec or container the TV can't handle: let Jellyfin transcode it instead.
          start(index, exo.currentPosition, true)
        } else {
          error = "KAN IKKE SPILLE AV DENNE FILEN"
        }
      }

      override fun onTracksChanged(tracks: Tracks) {
        if (!tracks.isEmpty) applyDefaults()
      }

      override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }

      override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
          if (index < request.queue.lastIndex) skipTo(index + 1) else onExit()
        }
      }
    }
    exo.addListener(listener)
    val first = request.queue[request.index]
    start(request.index, if (request.fromStart) 0L else first.positionTicks / 10_000L, false)
    view.keepScreenOn = true
    onDispose {
      stopReport(index, exo.currentPosition)
      exo.removeListener(listener)
      exo.release()
      view.keepScreenOn = false
    }
  }

  LaunchedEffect(Unit) {
    var n = 0
    while (true) {
      positionMs = exo.currentPosition
      durationMs = exo.duration.takeIf { it > 0 } ?: ((request.queue[index].runTimeMinutes ?: 0) * 60_000L)
      delay(500)
      n++
      if (n % 20 == 0) {
        val it = request.queue[index]
        launch { runCatching { state.jellyfin.reportProgress(it, exo.currentPosition, sessionId, transcoding, !exo.isPlaying) } }
      }
    }
  }
  LaunchedEffect(menuOpen) { if (!menuOpen) runCatching { focus.requestFocus() } }
  LaunchedEffect(osdTick) { if (osdTick > 0) { delay(2500); if (playing) osd = "" } }
  LaunchedEffect(barTick, playing, menuOpen) {
    if (playing && !menuOpen) { delay(4000); barVisible = false } else barVisible = true
  }

  BackHandler { if (menuOpen) menuOpen = false else onExit() }

  Box(
    Modifier
      .fillMaxSize()
      .background(Color.Black)
      .onPreviewKeyEvent { e ->
        if (menuOpen || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (e.key) {
          Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.MediaPlayPause -> {
            if (exo.isPlaying) { exo.pause(); showOsd("▮▮ PAUSE") } else { exo.play(); showOsd("▶ PLAY") }
            showBar(); true
          }
          Key.MediaPlay -> { exo.play(); showOsd("▶ PLAY"); showBar(); true }
          Key.MediaPause -> { exo.pause(); showOsd("▮▮ PAUSE"); showBar(); true }
          Key.DirectionLeft, Key.MediaRewind -> {
            exo.seekTo((exo.currentPosition - 10_000L).coerceAtLeast(0L)); showOsd("◀◀ −10 S"); showBar(); true
          }
          Key.DirectionRight, Key.MediaFastForward -> {
            exo.seekTo(exo.currentPosition + 30_000L); showOsd("▶▶ +30 S"); showBar(); true
          }
          Key.MediaNext -> { skipTo(index + 1); true }
          Key.MediaPrevious -> { if (exo.currentPosition > 5_000L) exo.seekTo(0L) else skipTo(index - 1); true }
          Key.DirectionDown, Key.Menu -> {
            if (!isAudio && streams.isNotEmpty()) menuOpen = true else showBar()
            true
          }
          Key.DirectionUp -> { showBar(); true }
          else -> false
        }
      }
      .focusRequester(focus)
      .focusable(),
  ) {
    if (!isAudio) {
      AndroidView(
        factory = { ctx ->
          PlayerView(ctx).apply {
            useController = false
            isFocusable = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            player = exo
            subtitleView?.apply {
              setApplyEmbeddedStyles(false)
              setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.25f)
              setStyle(
                CaptionStyleCompat(
                  AColor.parseColor("#EFE4CC"),
                  AColor.TRANSPARENT,
                  AColor.TRANSPARENT,
                  CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                  AColor.BLACK,
                  ResourcesCompat.getFont(ctx, R.font.vt323),
                ),
              )
            }
          }
        },
        modifier = Modifier.fillMaxSize(),
      )
    }
    DesignSurface {
      Box(Modifier.fillMaxSize()) {
        if (isAudio) {
          Box(Modifier.fillMaxSize().crtCurve(prefs.curvature).background(Color.Black)) {
            NowPlaying(state, request.queue, index)
            CrtOverlay(prefs.scanlines, prefs.crt)
          }
        }

        // VCR-style on-screen display.
        if (osd.isNotEmpty() || !playing) {
          Txt(
            if (!playing && osd.isEmpty()) "▮▮ PAUSE" else osd,
            Kp.vt(44.sp, Kp.Green, 2.sp),
            Modifier.padding(start = 56.dp, top = 40.dp),
          )
        }
        error?.let {
          Txt(it, Kp.vt(36.sp, prefs.accent, 2.sp), Modifier.align(Alignment.Center))
        }

        if ((barVisible || isAudio) && !menuOpen) {
          Column(
            Modifier
              .align(Alignment.BottomStart)
              .fillMaxWidth()
              .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))))
              .padding(start = 56.dp, end = 56.dp, top = 60.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            if (!isAudio) {
              val sub = listOfNotNull(
                item.seriesName?.let { "S${item.season ?: 1} E${item.episode ?: 1} · ${item.name.uppercase()}" },
                item.year?.toString(),
                audioPick?.let { "LYD: ${languageName(it.language).uppercase()}" },
                "TEKST: " + (subPick?.let { languageName(it.language).uppercase() } ?: "AV"),
                if (transcoding) "OMKODES AV SERVEREN" else null,
              ).joinToString(" · ")
              Txt((item.seriesName ?: item.name).uppercase(), Kp.anton(40.sp, Kp.Cream, 1.sp), maxLines = 1)
              Txt(sub, Kp.vt(24.sp, Kp.Muted), maxLines = 1)
            }
            val fraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
            Box(Modifier.fillMaxWidth().height(6.dp).background(Kp.Border)) {
              Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).background(prefs.accent))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              Txt("${clockText(positionMs)} / ${clockText(durationMs)}", Kp.vt(24.sp))
              if (durationMs > 0) {
                val leftMin = ((durationMs - positionMs) / 60_000L).toInt()
                Txt("  ·  FERDIG KL. ${clock.hhmm(leftMin)}", Kp.vt(24.sp, Kp.Muted))
              }
              Spacer(Modifier.weight(1f))
              Txt(
                if (isAudio) "OK PAUSE   ◀ −10 S   ▶ +30 S   ⏭ NESTE   ◀ TILBAKE STOPP"
                else "OK PAUSE   ◀ −10 S   ▶ +30 S   ▼ SPRÅK OG TEKST   ◀ TILBAKE STOPP",
                Kp.vt(20.sp, Kp.Muted, 2.sp),
              )
            }
          }
        }

        if (menuOpen) {
          TrackMenu(
            accent = prefs.accent,
            audio = audioStreams,
            subs = subStreams,
            audioPick = audioPick,
            subPick = subPick,
            canPictureSubs = !transcoding,
            onAudio = { selectAudio(it); showOsd("LYD: ${languageName(it.language).uppercase()}") },
            onSub = { selectSub(it); showOsd("TEKST: " + (it?.let { s -> languageName(s.language).uppercase() } ?: "AV")) },
            onClose = { menuOpen = false },
          )
        }
      }
    }
  }
}

/** Right-hand panel listing audio tracks and subtitles. */
@Composable
private fun TrackMenu(
  accent: Color,
  audio: List<JStream>,
  subs: List<JStream>,
  audioPick: JStream?,
  subPick: JStream?,
  canPictureSubs: Boolean,
  onAudio: (JStream) -> Unit,
  onSub: (JStream?) -> Unit,
  onClose: () -> Unit,
) {
  val first = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
  Row(Modifier.fillMaxSize()) {
    Spacer(Modifier.weight(1f))
    Column(
      Modifier
        .width(520.dp)
        .fillMaxHeight()
        .background(accent)
        .padding(start = 4.dp)
        .background(Color(0xF00A0808))
        .padding(36.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Txt("SPRÅK OG TEKST", Kp.anton(32.sp, Kp.Cream, 2.sp))
      Txt("LYDSPOR", Kp.vt(22.sp, Kp.Mustard, 3.sp))
      audio.forEachIndexed { i, s ->
        TrackRow(s.label, s == audioPick, accent, if (i == 0) Modifier.focusRequester(first) else Modifier) { onAudio(s) }
      }
      Txt("UNDERTEKSTER", Kp.vt(22.sp, Kp.Mustard, 3.sp), Modifier.padding(top = 8.dp))
      TrackRow("AV", subPick == null, accent, if (audio.isEmpty()) Modifier.focusRequester(first) else Modifier) { onSub(null) }
      for (s in subs) {
        if (!s.isText && !canPictureSubs) continue
        TrackRow(s.label, s == subPick, accent) { onSub(s) }
      }
      Spacer(Modifier.weight(1f))
      KpButton("◀ LUKK", accent, onClose, height = 44.dp)
    }
  }
}

@Composable
private fun TrackRow(label: String, selected: Boolean, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
  Focusable(onClick, modifier) { focused ->
    Row(
      Modifier
        .fillMaxWidth()
        .height(44.dp)
        .focusRing(focused, accent)
        .background(if (focused) Kp.Surface else Color.Transparent)
        .padding(horizontal = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Txt(if (selected) "●" else "○", Kp.vt(24.sp, if (selected) accent else Kp.Muted), Modifier.width(30.dp))
      Txt(label, Kp.vt(24.sp), Modifier.weight(1f), maxLines = 1)
    }
  }
}

/** "Now playing" screen for music: cover on the left, track and queue on the right. */
@Composable
private fun NowPlaying(state: AppState, queue: List<MediaItem>, index: Int) {
  val prefs = state.prefs
  val item = queue[index]
  Row(
    Modifier.fillMaxSize().padding(start = 96.dp, end = 96.dp, top = 80.dp, bottom = 170.dp),
    horizontalArrangement = Arrangement.spacedBy(56.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Cover(item.album ?: item.name, item.artist ?: "", state.jellyfin.posterUrl(item, 600), 400.dp, prefs.accent, false, ratio = 1f)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Txt("SPILLES NÅ · ${index + 1} AV ${queue.size}", Kp.vt(24.sp, prefs.accent, 3.sp))
      Txt(item.name.uppercase(), Kp.anton(56.sp, Kp.Cream, 1.sp), maxLines = 2)
      Txt(listOfNotNull(item.artist, item.album).joinToString(" · ").uppercase(), Kp.vt(28.sp, Kp.Body), maxLines = 1)
      Spacer(Modifier.height(12.dp))
      Txt("NESTE", Kp.anton(20.sp, Kp.Muted, 3.sp))
      for (next in queue.drop(index + 1).take(5)) {
        Row(Modifier.width(560.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
          Txt("${next.episode ?: ""}", Kp.vt(24.sp, Kp.Muted), Modifier.width(36.dp))
          Txt(next.name.uppercase(), Kp.vt(24.sp), Modifier.weight(1f), maxLines = 1)
          next.runTimeMinutes?.let { Txt("$it MIN", Kp.vt(22.sp, Kp.Muted)) }
        }
      }
    }
  }
}
