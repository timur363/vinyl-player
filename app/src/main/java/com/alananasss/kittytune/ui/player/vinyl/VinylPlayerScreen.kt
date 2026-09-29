package com.alananasss.kittytune.ui.player.vinyl

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.player.AudioControlDock
import com.alananasss.kittytune.ui.player.PlayerProgress
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.QueueContent
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val Wood = Color(0xFF2B1E16)
private val WoodDark = Color(0xFF17100C)
private val Platter = Color(0xFF1C1C1E)
private val VinylBlack = Color(0xFF0C0C0D)
private val Chrome = Color(0xFFC9CCD1)

@Composable
fun VinylPlayerScreen(viewModel: PlayerViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { VinylSettings.load(context) }

    val track = viewModel.currentTrack
    var showEffects by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showPresets by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Wood, WoodDark)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = Color.White)
                }
                Text(
                    stringResource(R.string.vinyl_mode_title),
                    modifier = Modifier.weight(1f),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { VinylSettings.setEnabled(context, false) }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.vinyl_mode_exit), tint = Color.White)
                }
            }

            Spacer(Modifier.height(8.dp))

            Turntable(
                viewModel = viewModel,
                artworkUrl = track?.fullResArtwork,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )

            Spacer(Modifier.height(16.dp))

            Text(
                track?.title ?: "",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                track?.displayArtist ?: "",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth()) { PlayerProgress(viewModel = viewModel, textColor = Color.White) }
            Spacer(Modifier.height(8.dp))

            // Transport
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showQueue = true }) {
                    Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                }
                IconButton(onClick = { viewModel.playPrevious() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                FilledIconButton(
                    onClick = { viewModel.vinylTogglePlayPause() },
                    modifier = Modifier.size(76.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Chrome, contentColor = WoodDark)
                ) {
                    Icon(
                        if (viewModel.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(onClick = { viewModel.playNext() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = { showEffects = true }) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                }
            }

            Spacer(Modifier.height(12.dp))

            // Speed selector: 16 / 33⅓ / 45 / 78
            val speed = viewModel.effectsState.speed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                VinylSettings.Rpm.entries.forEach { rpm ->
                    val selected = abs(speed - rpm.speed) < 0.01f && viewModel.effectsState.isPitchEnabled
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.setVinylRpm(rpm) },
                        label = { Text(rpm.label, fontWeight = FontWeight.Bold) },
                        colors = vinylChipColors()
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                FilterChip(
                    selected = VinylPresets.forTrack(track) != null,
                    onClick = { showPresets = true },
                    label = { Text(stringResource(R.string.vinyl_presets)) },
                    colors = vinylChipColors()
                )
                FilterChip(
                    selected = viewModel.effectsState.isVinylLoFiEnabled,
                    onClick = { viewModel.setVinylCrackle(!viewModel.effectsState.isVinylLoFiEnabled) },
                    label = { Text(stringResource(R.string.vinyl_crackle)) },
                    colors = vinylChipColors()
                )
                FilterChip(
                    selected = VinylSettings.spinUpDown,
                    onClick = { VinylSettings.setSpinUpDown(context, !VinylSettings.spinUpDown) },
                    label = { Text(stringResource(R.string.vinyl_spin)) },
                    colors = vinylChipColors()
                )
                FilterChip(
                    selected = VinylSettings.scratchEnabled,
                    onClick = { VinylSettings.setScratchEnabled(context, !VinylSettings.scratchEnabled) },
                    label = { Text(stringResource(R.string.vinyl_scratch)) },
                    colors = vinylChipColors()
                )
            }
            Spacer(Modifier.weight(1f))
        }
    }

    if (showEffects) {
        com.alananasss.kittytune.ui.common.KittyModalBottomSheet(
            onDismissRequest = { showEffects = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            AudioControlDock(viewModel)
            Spacer(Modifier.height(32.dp))
        }
    }
    if (showPresets) {
        com.alananasss.kittytune.ui.common.KittyModalBottomSheet(
            onDismissRequest = { showPresets = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            PresetsSheet(viewModel)
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showQueue) {
        com.alananasss.kittytune.ui.common.KittyModalBottomSheet(
            onDismissRequest = { showQueue = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            QueueContent(
                viewModel = viewModel,
                isQueueOpen = true,
                onCloseQueue = { showQueue = false },
                onOpenExpandedQueue = {
                    showQueue = false
                    viewModel.navigateToExpandedQueue()
                }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun vinylChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.White.copy(alpha = 0.06f),
    labelColor = Color.White.copy(alpha = 0.8f),
    selectedContainerColor = Chrome,
    selectedLabelColor = WoodDark
)

/**
 * Platter, record and tone arm. The record turns at the real speed (33⅓ rpm × playback speed),
 * winds up and down with play/pause, and can be grabbed and turned by hand.
 */
@Composable
private fun Turntable(viewModel: PlayerViewModel, artworkUrl: String?, modifier: Modifier = Modifier) {
    var angle by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    val spinUp = VinylSettings.spinUpDown
    val spin = animateFloatAsState(
        targetValue = if (viewModel.isPlaying && !dragging) 1f else 0f,
        animationSpec = tween(if (spinUp) 700 else 120),
        label = "spin"
    )
    val speed by rememberUpdatedState(viewModel.effectsState.speed)

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L && !dragging) {
                    val dt = (now - last) / 1_000_000_000f
                    val degPerSec = VinylSettings.Rpm.RPM_33.rpm / 60f * 360f * speed
                    angle = (angle + dt * degPerSec * spin.value) % 360f
                }
                last = now
            }
        }
    }

    // Tone arm: resting outside the record, then tracking from the outer to the inner groove.
    val progress = if (viewModel.duration > 0) (viewModel.currentPosition.toFloat() / viewModel.duration).coerceIn(0f, 1f) else 0f
    val armOnRecord = viewModel.isPlaying || viewModel.currentPosition > 0
    val armProgress by animateFloatAsState(
        targetValue = if (armOnRecord) progress else -1f,
        animationSpec = tween(600),
        label = "arm"
    )

    BoxWithConstraints(modifier = modifier) {
        val recordFraction = 0.86f

        // Platter + record. Touch is handled on this un-rotated box so finger angles are stable;
        // the record itself turns inside it.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize(recordFraction)
                .pointerInput(VinylSettings.scratchEnabled) {
                    if (!VinylSettings.scratchEnabled) return@pointerInput
                    var lastAngle = 0f
                    var lastTime = 0L
                    var pendingBack = 0f
                    detectDragGestures(
                        onDragStart = { pos ->
                            dragging = true
                            val c = Offset(size.width / 2f, size.height / 2f)
                            lastAngle = Math.toDegrees(atan2((pos.y - c.y).toDouble(), (pos.x - c.x).toDouble())).toFloat()
                            lastTime = System.nanoTime()
                            pendingBack = 0f
                            viewModel.vinylScratchStart()
                        },
                        onDragEnd = {
                            dragging = false
                            viewModel.vinylScratchEnd()
                        },
                        onDragCancel = {
                            dragging = false
                            viewModel.vinylScratchEnd()
                        },
                        onDrag = { change, _ ->
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val p = change.position
                            val a = Math.toDegrees(atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())).toFloat()
                            var d = a - lastAngle
                            if (d > 180f) d -= 360f
                            if (d < -180f) d += 360f
                            lastAngle = a
                            val now = System.nanoTime()
                            val dt = ((now - lastTime) / 1_000_000_000f).coerceAtLeast(0.004f)
                            lastTime = now
                            angle = (angle + d) % 360f
                            if (d >= 0f) {
                                if (pendingBack < 0f) {
                                    viewModel.vinylScratchMove(pendingBack, pendingBack / dt)
                                    pendingBack = 0f
                                }
                                viewModel.vinylScratchMove(d, d / dt)
                            } else {
                                pendingBack += d
                                if (pendingBack < -12f) {
                                    viewModel.vinylScratchMove(pendingBack, pendingBack / dt)
                                    pendingBack = 0f
                                }
                            }
                            change.consume()
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { viewModel.vinylTogglePlayPause() })
                }
        ) {
          Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = angle }) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                val c = center
                drawCircle(VinylBlack, radius = r, center = c)
                // Grooves
                var gr = r * 0.97f
                var i = 0
                while (gr > r * 0.40f) {
                    drawCircle(
                        color = Color.White.copy(alpha = if (i % 7 == 0) 0.07f else 0.025f),
                        radius = gr,
                        center = c,
                        style = Stroke(width = 1f)
                    )
                    gr -= r * 0.012f
                    i++
                }
                // Light reflection that turns with the record
                rotate(35f, c) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
                            center = c
                        ),
                        startAngle = -20f, sweepAngle = 40f, useCenter = true,
                        topLeft = Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2)
                    )
                }
                drawCircle(Color.Black, radius = r * 0.40f, center = c)
            }
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize(0.36f)
                    .clip(CircleShape)
                    .background(Color(0xFF7A2E2E))
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Chrome)
            )
          }
        }

        // Tone arm (fixed, not rotating)
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val pivot = Offset(w * 0.92f, w * 0.08f)
            val length = w * 0.70f
            val recordR = w * recordFraction / 2f
            val c = center
            fun needleAt(theta: Float) = Offset(
                pivot.x - length * sin(theta),
                pivot.y + length * cos(theta)
            )
            fun thetaForRadius(target: Float): Float {
                var best = 0f
                var bestErr = Float.MAX_VALUE
                var t = 0f
                while (t < 1.2f) {
                    val n = needleAt(t)
                    val err = abs(hypot(n.x - c.x, n.y - c.y) - target)
                    if (err < bestErr) { bestErr = err; best = t }
                    t += 0.004f
                }
                return best
            }
            val outer = thetaForRadius(recordR * 0.95f)
            val inner = thetaForRadius(recordR * 0.45f)
            val rest = outer - 0.22f
            val theta = if (armProgress < 0f) rest + (outer - rest) * (armProgress + 1f)
            else outer + (inner - outer) * armProgress
            val needle = needleAt(theta)
            // Base
            drawCircle(Color(0xFF3A3A3C), radius = w * 0.06f, center = pivot)
            drawCircle(Chrome, radius = w * 0.035f, center = pivot)
            // Arm
            drawLine(Chrome, pivot, needle, strokeWidth = w * 0.014f, cap = StrokeCap.Round)
            // Headshell
            val dir = Offset(needle.x - pivot.x, needle.y - pivot.y)
            val len = hypot(dir.x, dir.y)
            val ux = dir.x / len
            val uy = dir.y / len
            val head = Offset(needle.x + ux * w * 0.05f, needle.y + uy * w * 0.05f)
            drawLine(Color(0xFF8E8E93), needle, head, strokeWidth = w * 0.035f, cap = StrokeCap.Round)
            // Counterweight
            val cw = Offset(pivot.x - ux * w * 0.07f, pivot.y - uy * w * 0.07f)
            drawCircle(Color(0xFF636366), radius = w * 0.03f, center = cw)
        }
    }
}


@Composable
private fun PresetsSheet(viewModel: PlayerViewModel) {
    val track = viewModel.currentTrack
    var name by remember { mutableStateOf("") }
    val remembered = VinylPresets.forTrack(track) != null
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.vinyl_presets),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // Per-track memory
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.vinyl_remember_track), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.vinyl_remember_track_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.material3.Switch(
                checked = remembered,
                enabled = track != null,
                onCheckedChange = { on ->
                    if (on) viewModel.rememberSoundForCurrentTrack() else viewModel.forgetSoundForCurrentTrack()
                }
            )
        }

        androidx.compose.material3.HorizontalDivider()

        // Save current sound as a named preset
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.vinyl_preset_name)) },
                modifier = Modifier.weight(1f)
            )
            androidx.compose.material3.Button(
                onClick = {
                    viewModel.savePreset(name)
                    name = ""
                },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.vinyl_preset_save)) }
        }
        Text(
            stringResource(R.string.vinyl_preset_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (VinylPresets.presets.isEmpty()) {
            Text(
                stringResource(R.string.vinyl_presets_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val crackleLabel = stringResource(R.string.vinyl_crackle)
        VinylPresets.presets.toList().forEach { preset ->
            androidx.compose.material3.Surface(
                onClick = { viewModel.applyPreset(preset) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(preset.name, style = MaterialTheme.typography.titleMedium)
                        val fx = preset.sound.effects
                        val parts = buildList {
                            add("×" + String.format(java.util.Locale.US, "%.2f", fx.speed))
                            if (fx.isVinylLoFiEnabled) add(crackleLabel)
                            if (fx.isBassBoostEnabled) add("Bass")
                            if (preset.sound.equalizer.isEnabled) add("EQ")
                        }
                        Text(
                            parts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.deletePreset(preset) }) {
                        Icon(Icons.Rounded.Close, contentDescription = null)
                    }
                }
            }
        }
    }
}
