@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.example.pixelisland.ui.island

import android.app.PendingIntent
import android.graphics.Bitmap
import android.os.SystemClock
import android.media.session.PlaybackState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.pixelisland.model.NavigationEvent
import com.example.pixelisland.model.TimerEvent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pixelisland.IslandPrefs
import com.example.pixelisland.IslandRepository
import com.example.pixelisland.IslandSettings
import com.example.pixelisland.model.CallEvent
import com.example.pixelisland.model.IslandEvent
import com.example.pixelisland.model.MediaEvent
import com.example.pixelisland.model.NotificationEvent
import com.example.pixelisland.model.ProgressEvent
import com.example.pixelisland.ui.theme.PixelIslandTheme
import kotlinx.coroutines.delay

/** Geometry of the front camera punch-hole in dp (top edge measured from the screen top). */
data class CutoutInfo(val width: Dp, val height: Dp, val top: Dp)

private val CallGreen = Color(0xFF34A853)
private val CallRed = Color(0xFFEA4335)

@Composable
fun IslandHost(cutout: CutoutInfo) {
    // The island is always dark so it blends into the black punch-hole; accents still follow dynamic color.
    PixelIslandTheme(darkTheme = true) { IslandContent(cutout) }
}

@Composable
private fun IslandContent(cutout: CutoutInfo) {
    val events by IslandRepository.events.collectAsState()
    val settings by IslandPrefs.settings.collectAsState()

    val current = events.firstOrNull { settings.allows(it) }
    var last by remember { mutableStateOf<IslandEvent?>(null) }
    SideEffect { if (current != null) last = current }

    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { IslandRepository.collapseRequests.collect { expanded = false } }

    LaunchedEffect(current?.key) {
        when (val c = current) {
            null -> expanded = false
            is CallEvent -> expanded = !c.ongoing                     // incoming call pops open
            is NotificationEvent -> if (settings.autoExpand) {
                expanded = true
                delay(4_500)
                expanded = false
            }
            else -> Unit
        }
    }

    LaunchedEffect(current?.key, current?.expiresAtMillis) {
        val cur = current ?: return@LaunchedEffect
        val at = cur.expiresAtMillis ?: return@LaunchedEffect
        delay((at - System.currentTimeMillis()).coerceAtLeast(0))
        IslandRepository.remove(cur.key)
    }

    val motion = MaterialTheme.motionScheme
    val origin = TransformOrigin(0.5f, 0f)
    val ev = current ?: last

    AnimatedVisibility(
        visible = current != null,
        enter = fadeIn(motion.fastEffectsSpec()) +
            scaleIn(motion.fastSpatialSpec(), initialScale = 0.4f, transformOrigin = origin),
        exit = fadeOut(motion.fastEffectsSpec()) +
            scaleOut(motion.fastSpatialSpec(), targetScale = 0.4f, transformOrigin = origin),
    ) {
        if (ev != null) {
            Island(
                ev = ev,
                expanded = expanded,
                cutout = cutout,
                settings = settings,
                onToggle = { expanded = !expanded },
                onClose = { expanded = false },
            )
        }
    }
}

@Composable
private fun Island(
    ev: IslandEvent,
    expanded: Boolean,
    cutout: CutoutInfo,
    settings: IslandSettings,
    onToggle: () -> Unit,
    onClose: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val pad = 4.dp
    val pillH = cutout.height + pad * 2
    val sideW = 64.dp * settings.scale
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val expandedW = minOf(344.dp * settings.scale, screenW - 16.dp)
    val compactW = cutout.width + sideW * 2

    val width by animateDpAsState(
        targetValue = if (expanded) expandedW else compactW,
        animationSpec = motion.defaultSpatialSpec(),
        label = "islandWidth",
    )
    val corner by animateDpAsState(
        targetValue = if (expanded) 34.dp else pillH / 2,
        animationSpec = motion.defaultSpatialSpec(),
        label = "islandCorner",
    )
    val shape = RoundedCornerShape(corner.coerceAtLeast(0.dp))

    // Press feedback: the island squishes slightly on a spring, like the system chips.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = motion.fastSpatialSpec(),
        label = "pressScale",
    )
    val haptics = LocalHapticFeedback.current

    Box(Modifier.padding(top = (cutout.top - pad).coerceAtLeast(0.dp) + settings.topOffset.dp)) {
        Column(
            Modifier
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .width(width.coerceAtLeast(0.dp))
                .animateContentSize(motion.defaultSpatialSpec())
                .clip(shape)
                .background(Color.Black)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onToggle,
                    // Long press dismisses the current activity from the island
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        IslandRepository.remove(ev.key)
                        onClose()
                    },
                )
        ) {
            CompactRow(ev, cutout, pillH)
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(motion.defaultEffectsSpec()),
                exit = fadeOut(motion.fastEffectsSpec()),
            ) {
                ExpandedBody(ev, onClose)
            }
        }
    }
}

// ------------------------------------------------------------------ compact

@Composable
private fun CompactRow(ev: IslandEvent, cutout: CutoutInfo, pillH: Dp) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(pillH)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { Leading(ev) }
        Spacer(Modifier.width(cutout.width))          // keeps the punch-hole centered between the slots
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { Trailing(ev) }
    }
}

@Composable
private fun Leading(ev: IslandEvent) {
    val cookie = MaterialShapes.Cookie9Sided.toShape()
    when (ev) {
        is MediaEvent -> Artwork(ev.art, 26.dp, cookie, Icons.Rounded.MusicNote)
        is CallEvent -> Box(
            Modifier.size(26.dp).clip(cookie).background(CallGreen),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Call, null, tint = Color.White, modifier = Modifier.size(15.dp)) }
        is ProgressEvent -> Artwork(ev.icon, 26.dp, cookie, Icons.Rounded.Timer)
        is NavigationEvent -> Box(
            Modifier.size(26.dp).clip(cookie).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Navigation, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(15.dp)) }
        is TimerEvent -> Box(
            Modifier.size(26.dp).clip(cookie).background(MaterialTheme.colorScheme.tertiary),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.size(15.dp)) }
        is NotificationEvent -> Artwork(ev.icon, 26.dp, cookie, Icons.Rounded.Notifications)
    }
}

@Composable
private fun Trailing(ev: IslandEvent) {
    when (ev) {
        is MediaEvent -> Equalizer(active = ev.isPlaying, color = MaterialTheme.colorScheme.primary)
        is CallEvent -> if (ev.ongoing) {
            val elapsed by rememberElapsed(ev.startedAtMillis)
            Text(elapsed, color = CallGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        is ProgressEvent -> when {
            !ev.chipText.isNullOrBlank() -> Text(
                ev.chipText, color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            )
            ev.progress != null -> CircularProgressIndicator(
                progress = { ev.progress },
                modifier = Modifier.size(20.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.2f),
            )
            else -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        is NavigationEvent -> Text(
            ev.distance, color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        is TimerEvent -> {
            val remaining by rememberTimerText(ev)
            Text(
                remaining, color = MaterialTheme.colorScheme.tertiary,
                fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            )
        }
        is NotificationEvent -> Unit
    }
}

// ----------------------------------------------------------------- expanded

@Composable
private fun ExpandedBody(ev: IslandEvent, onClose: () -> Unit) {
    when (ev) {
        is MediaEvent -> MediaBody(ev)
        is CallEvent -> CallBody(ev, onClose)
        is ProgressEvent -> ProgressBody(ev, onClose)
        is NavigationEvent -> NavigationBody(ev, onClose)
        is TimerEvent -> TimerBody(ev, onClose)
        is NotificationEvent -> NotificationBody(ev, onClose)
    }
}

@Composable
private fun NavigationBody(ev: NavigationEvent, onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                ev.contentIntent.fire(); onClose()
            }
            .padding(start = 20.dp, end = 24.dp, top = 4.dp, bottom = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Navigation, null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                ev.distance, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.headlineMediumEmphasized,
            )
            if (ev.instruction.isNotBlank()) {
                Text(
                    ev.instruction, color = Color.White.copy(alpha = 0.75f), maxLines = 2,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(ev.appName, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun TimerBody(ev: TimerEvent, onClose: () -> Unit) {
    val time by rememberTimerText(ev)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                ev.contentIntent.fire(); onClose()
            }
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(ev.title, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium)
        Text(
            time, color = MaterialTheme.colorScheme.tertiary,
            style = MaterialTheme.typography.displayMediumEmphasized,
        )
        Text(
            if (ev.countDown) "Таймер" else "Секундомер",
            color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun MediaBody(ev: MediaEvent) {
    val motion = MaterialTheme.motionScheme
    val progress by produceState(0f, ev.controller, ev.durationMs) {
        while (true) {
            val s = ev.controller?.playbackState
            if (s != null && ev.durationMs > 0) {
                val extra = if (s.state == PlaybackState.STATE_PLAYING)
                    (SystemClock.elapsedRealtime() - s.lastPositionUpdateTime) * s.playbackSpeed else 0f
                value = ((s.position + extra) / ev.durationMs).coerceIn(0f, 1f)
            }
            delay(500)
        }
    }
    val controls = ev.controller?.transportControls
    val playing = ev.controller?.playbackState?.state == PlaybackState.STATE_PLAYING
    val playCorner by animateDpAsState(if (playing) 18.dp else 28.dp, motion.fastSpatialSpec(), label = "playCorner")

    Column(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Artwork(ev.art, 64.dp, RoundedCornerShape(20.dp), Icons.Rounded.MusicNote)
            Column(Modifier.weight(1f)) {
                Text(
                    ev.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                Text(
                    ev.artist, color = Color.White.copy(alpha = 0.7f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        LinearWavyProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.White.copy(alpha = 0.2f),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { controls?.skipToPrevious() }) {
                Icon(Icons.Rounded.SkipPrevious, "Назад", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            FilledIconButton(
                onClick = { if (playing) controls?.pause() else controls?.play() },
                modifier = Modifier.size(width = 76.dp, height = 56.dp),
                shape = RoundedCornerShape(playCorner),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (playing) "Пауза" else "Играть",
                    modifier = Modifier.size(32.dp),
                )
            }
            IconButton(onClick = { controls?.skipToNext() }) {
                Icon(Icons.Rounded.SkipNext, "Вперёд", tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun CallBody(ev: CallEvent, onClose: () -> Unit) {
    Column(
        Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column {
            Text(
                ev.caller, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
            val status = if (ev.ongoing) {
                val elapsed by rememberElapsed(ev.startedAtMillis)
                elapsed
            } else "Входящий вызов"
            Text(status, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyLarge)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CallButton(
                modifier = Modifier.weight(1f),
                color = CallRed,
                icon = Icons.Rounded.CallEnd,
                label = if (ev.ongoing) "Завершить" else "Отклонить",
            ) { ev.primaryAction.fire(); onClose() }
            if (!ev.ongoing) {
                CallButton(
                    modifier = Modifier.weight(1f),
                    color = CallGreen,
                    icon = Icons.Rounded.Call,
                    label = "Ответить",
                ) { ev.answerAction.fire(); onClose() }
            }
        }
    }
}

@Composable
private fun CallButton(
    modifier: Modifier,
    color: Color,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = Color.White),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ProgressBody(ev: ProgressEvent, onClose: () -> Unit) {
    Column(
        Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { ev.contentIntent.fire(); onClose() }
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(ev.appName, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
        Text(
            ev.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
        if (ev.text.isNotBlank()) {
            Text(ev.text, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
        }
        if (ev.progress != null) {
            LinearWavyProgressIndicator(
                progress = { ev.progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.2f),
            )
        } else {
            LinearWavyProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.2f),
            )
        }
    }
}

@Composable
private fun NotificationBody(ev: NotificationEvent, onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                ev.contentIntent.fire()
                IslandRepository.remove(ev.key)
                onClose()
            }
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Artwork(ev.icon, 44.dp, RoundedCornerShape(16.dp), Icons.Rounded.Notifications)
        Column(Modifier.weight(1f)) {
            Text(ev.appName, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
            Text(
                ev.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            if (ev.text.isNotBlank()) {
                Text(
                    ev.text, color = Color.White.copy(alpha = 0.75f), maxLines = 3,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ pieces

@Composable
private fun Artwork(bitmap: Bitmap?, size: Dp, shape: Shape, fallback: ImageVector) {
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                fallback, null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}

@Composable
private fun Equalizer(active: Boolean, color: Color) {
    val transition = rememberInfiniteTransition(label = "eq")
    Row(
        Modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(420, 560, 340, 480).forEachIndexed { i, duration ->
            val h by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(duration, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 90),
                ),
                label = "bar$i",
            )
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(if (active) h else 0.3f)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun rememberElapsed(startedAt: Long) = produceState(formatElapsed(startedAt), startedAt) {
    while (true) {
        value = formatElapsed(startedAt)
        delay(1_000)
    }
}

@Composable
private fun rememberTimerText(ev: TimerEvent) = produceState(timerText(ev), ev.whenMillis, ev.countDown) {
    while (true) {
        value = timerText(ev)
        delay(250)
    }
}

private fun timerText(ev: TimerEvent): String {
    val now = System.currentTimeMillis()
    val seconds = (if (ev.countDown) (ev.whenMillis - now + 999) / 1000 else (now - ev.whenMillis) / 1000)
        .coerceAtLeast(0)
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun formatElapsed(startedAt: Long): String {
    val s = ((System.currentTimeMillis() - startedAt) / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

private fun PendingIntent?.fire() {
    runCatching { this?.send() }
}
