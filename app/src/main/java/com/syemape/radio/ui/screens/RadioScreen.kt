package com.syemape.radio.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syemape.radio.data.RadioManager
import com.syemape.radio.ui.Avatar
import com.syemape.radio.ui.MapeIcons
import com.syemape.radio.ui.avatarColor
import com.syemape.radio.ui.initialsOf
import com.syemape.radio.ui.pressScale
import com.syemape.radio.ui.theme.Outfit

private object RadioColors {
    val Bg = Color(0xFF090909)
    val Card = Color(0xFF1D1D1D)
    val Ink = Color(0xFFE8E8E8)
    val White = Color(0xFF171717)
    val Text = Color(0xFFF2F2F2)
    val TextMuted = Color(0xFFB5B5B5)
    val TextFaint = Color(0xFFAAAAAA)
    val Border = Color(0xFF424242)
}

@Composable
fun RadioScreen(
    topPadding: Dp,
    bottomPadding: Dp = 0.dp,
    onOpenChannelChat: (String, String) -> Unit = { _, _ -> },
    onOpenSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val micLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) RadioManager.start(app) }
    DisposableEffect(Unit) {
        if (RadioManager.hasMicPermission(context)) RadioManager.start(app)
        else micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        onDispose { }
    }

    Column(Modifier.fillMaxSize().background(RadioColors.Bg)) {
        RadioHero(topPadding, onOpenSettings, compact = true)
        Column(
            Modifier.weight(1f).fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = bottomPadding + 76.dp, top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (RadioManager.channels.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    RadioManager.channels.forEach { channel ->
                        val selected = channel.id == RadioManager.channelId
                        Box(
                            Modifier.clip(CircleShape)
                                .background(if (selected) RadioColors.Ink else RadioColors.Card)
                                .border(1.dp, RadioColors.Border, CircleShape)
                                .clickable { RadioManager.selectChannel(channel.id) }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            Text(
                                channel.name ?: "Canal",
                                color = if (selected) RadioColors.White else RadioColors.Text,
                                fontFamily = Outfit,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            RadioChannelCard(compact = true,
                onOpen = {
                    RadioManager.channelId?.let { onOpenChannelChat(it, RadioManager.channelName) }
                },
            )
            RadioMemberCard()

            val lastNote = RadioManager.lastVoiceNote
            var lastPlaying by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                RadioActionCard(
                    icon = MapeIcons.Chat,
                    label = "Chat del canal",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        RadioManager.channelId?.let { onOpenChannelChat(it, RadioManager.channelName) }
                    },
                )
                if (lastNote != null) {
                    RadioActionCard(
                        icon = if (lastPlaying) MapeIcons.Pause else MapeIcons.Play,
                        label = if (lastPlaying) "Pausar nota" else "Última nota · ${lastNote.durationSec?.toInt() ?: 0}s",
                        modifier = Modifier.weight(1.35f),
                        onClick = { RadioManager.playLastVoiceNote { id -> lastPlaying = id != null } },
                    )
                }
            }

            Box(
                Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp))
                    .background(RadioColors.Card).border(1.dp, RadioColors.Border, RoundedCornerShape(18.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                TunerRuler(
                    active = RadioManager.talking || RadioManager.remoteSpeaking,
                    level = RadioManager.audioLevel,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            RadioSpeakerStatus()

            Row(
                Modifier.fillMaxWidth().clip(CircleShape).background(RadioColors.Card)
                    .border(1.dp, RadioColors.Border, CircleShape).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutputPill(RadioManager.normalDeviceLabel, selected = !RadioManager.speakerOn) {
                    RadioManager.setSpeaker(false)
                }
                OutputPill("Altavoz", selected = RadioManager.speakerOn) {
                    RadioManager.setSpeaker(true)
                }
            }

            Row(
                Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(MapeIcons.Speaker, null, tint = RadioColors.TextMuted, modifier = Modifier.size(18.dp))
                VolumeSlider(Modifier.weight(1f))
                Text("${(RadioManager.callVolume * 100).toInt()}%", color = RadioColors.TextMuted, fontFamily = Outfit, fontSize = 11.sp)
            }

            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(1f).padding(top = 3.dp, bottom = 8.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                // Phones with a short display get a smaller PTT target instead of
                // pushing the microphone below the bottom navigation bar.
                val micSize = minOf(maxWidth, maxHeight, 220.dp)
                if (micSize > 0.dp) HoldTalkButton(size = micSize)
            }
        }
    }
}

@Composable
private fun RadioHero(topPadding: Dp, onOpenSettings: () -> Unit, compact: Boolean = false) {
    Box(
        Modifier.fillMaxWidth().height(if (compact) 146.dp else 184.dp).clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)),
    ) {
        Image(
            painter = painterResource(com.syemape.radio.R.drawable.radio_hero),
            contentDescription = "Camioneta de emergencia MAPE en carretera de montaña",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.82f), Color.Black.copy(alpha = 0.18f))),
            ),
        )
        Row(
            Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 72.dp, bottom = if (compact) 9.dp else 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                Modifier.size(if (compact) 74.dp else 88.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(com.syemape.radio.R.drawable.radio_logo),
                    contentDescription = "Logo MAPE",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("S & E MAPE E.I.R.L.", color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                Text("Supervisión y Emergencias", color = Color.White.copy(alpha = 0.94f), fontFamily = Outfit, fontSize = 11.sp, maxLines = 1)
                Text("Resguardo · Seguridad · Transporte", color = Color.White.copy(alpha = 0.84f), fontFamily = Outfit, fontSize = 9.sp, maxLines = 1)
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = topPadding + 8.dp, end = 18.dp)
                .size(42.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f))
                .clickable(onClick = onOpenSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(MapeIcons.Sliders, "Ajustes", tint = Color.White, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun RadioChannelCard(compact: Boolean = false, onOpen: () -> Unit) {
    val brandRed = Color(0xFFD71920)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(RadioColors.Card)
            .border(1.dp, RadioColors.Border, RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen).padding(if (compact) 8.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(15.dp)).background(RadioColors.Ink),
            contentAlignment = Alignment.Center,
        ) { Icon(MapeIcons.Radio, null, tint = RadioColors.White, modifier = Modifier.size(21.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("CANAL · ${RadioManager.channelName.uppercase()}", color = RadioColors.TextMuted, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = if (compact) 8.sp else 10.sp, maxLines = 1)
            Text(RadioManager.channelName, color = RadioColors.Text, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = if (compact) 17.sp else 22.sp, maxLines = 1)
            Text("${RadioManager.members} conectados · ${if (RadioManager.connected) "En vivo" else "Conectando…"}", color = RadioColors.TextMuted, fontFamily = Outfit, fontSize = if (compact) 9.sp else 11.sp, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(if (RadioManager.connected) Color(0xFF20B15A) else Color(0xFFE7A820)))
            Text(if (RadioManager.connected) "EN VIVO" else "CONECTA", color = brandRed, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 9.sp)
        }
    }
}

@Composable
private fun RadioMemberCard() {
    val users = RadioManager.connectedUsers
    val aliases = users.mapNotNull { user ->
        user.nickname?.trim()?.takeIf { it.isNotEmpty() }
            ?: user.name?.trim()?.takeIf { it.isNotEmpty() }
    }
    val firstUser = users.firstOrNull()
    val firstAlias = aliases.firstOrNull() ?: "Equipo de radio"
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(RadioColors.Card)
            .border(1.dp, RadioColors.Border, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Avatar(initialsOf(firstAlias), avatarColor(firstUser?.id?.takeIf { it.isNotBlank() } ?: firstAlias), size = 38.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${RadioManager.members} CONECTADOS", color = RadioColors.TextMuted, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 9.sp, maxLines = 1)
            if (aliases.isEmpty()) {
                Text("Esperando conectados…", color = RadioColors.TextMuted, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, maxLines = 1)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(aliases) { alias ->
                        Text(
                            text = alias,
                            modifier = Modifier.clip(CircleShape)
                                .background(RadioColors.Ink.copy(alpha = 0.12f))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            color = RadioColors.Text,
                            fontFamily = Outfit,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Icon(MapeIcons.Truck, null, tint = RadioColors.TextMuted, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun RadioActionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(43.dp).clip(RoundedCornerShape(14.dp)).background(RadioColors.Card)
            .border(1.dp, RadioColors.Border, RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Color(0xFFD71920), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = RadioColors.Text, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun RadioSpeakerStatus() {
    val label = when {
        RadioManager.talking -> "TRANSMITIENDO"
        RadioManager.remoteSpeaking -> "HABLANDO AHORA"
        RadioManager.lastSpeakerLabel != null -> "ÚLTIMO EN HABLAR"
        RadioManager.txFailed -> "ERROR DE TRANSMISIÓN"
        else -> "EN EL CANAL"
    }
    val name = when {
        RadioManager.talking -> "Tú · en el canal"
        RadioManager.remoteSpeaking -> RadioManager.speakerLabel ?: "En vivo"
        RadioManager.lastSpeakerLabel != null -> RadioManager.lastSpeakerLabel!!
        RadioManager.txFailed -> "Vuelve a intentar hablar"
        else -> "En silencio"
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(RadioColors.Card)
            .border(1.dp, RadioColors.Border, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Icon(MapeIcons.Speaker, null, tint = Color(0xFFD71920), modifier = Modifier.size(25.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Color(0xFFD71920), fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            Text(name, color = RadioColors.Text, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(22.dp)) {
            val strength = if (RadioManager.netOnline) 4 else 1
            repeat(4) { index ->
                Box(
                    Modifier.width(4.dp).height((7 + index * 4).dp).clip(CircleShape)
                        .background(if (index < strength) Color(0xFF20B15A) else RadioColors.Border),
                )
            }
        }
        Text(if (RadioManager.netOnline) "Buena" else "Sin red", color = RadioColors.TextMuted, fontFamily = Outfit, fontSize = 10.sp)
    }
}

@Composable
private fun HoldTalkButton(size: Dp) {
    val talking = RadioManager.talking
    val ready = RadioManager.connected // true solo cuando el canal de audio está listo
    val brandRed = Color(0xFFD71920)
    val buttonSize = size * 0.89f
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size).clip(CircleShape)
                .background(if (talking) brandRed.copy(alpha = 0.13f) else brandRed.copy(alpha = 0.07f))
                .border(2.dp, brandRed.copy(alpha = 0.45f), CircleShape),
        )
        Column(
            Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .background(
                    when {
                        talking -> brandRed
                        !ready -> RadioColors.TextMuted // apagado: aún conectando
                        else -> RadioColors.Card
                    },
                )
                .border(2.dp, brandRed, CircleShape)
                .pointerInput(ready) {
                    if (!ready) return@pointerInput
                    // PTT a prueba de cancelación: empieza al tocar y SOLO termina cuando se
                    // levanta el dedo de verdad. Antes, con detectTapGestures, las recomposiciones
                    // (la onda de audio recompone ~8 veces/s) podían cancelar el gesto a los 2-3s
                    // y cortar/perder la transmisión.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        RadioManager.startTalking()
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.none { it.pressed }) break // todos los dedos arriba
                            }
                        } finally {
                            RadioManager.stopTalking()
                        }
                    }
                },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(MapeIcons.Mic, null, tint = if (talking) Color.White else brandRed, modifier = Modifier.size((buttonSize * 0.35f).coerceAtMost(60.dp)))
            Spacer(Modifier.height(buttonSize * 0.025f))
            Text(
                when {
                    talking -> "CORTAR"
                    !ready -> "CONECTANDO…"
                    else -> "HABLAR"
                },
                color = if (talking) Color.White else brandRed, fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = (buttonSize.value * 0.105f).coerceAtMost(18f).sp, letterSpacing = 1.2.sp,
            )
        }
    }
}

/** Pastilla de selección de salida de audio; se resalta la activa (dispositivo en uso). */
@Composable
private fun RowScope.OutputPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val brandRed = Color(0xFFD71920)
    Box(
        Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
            .background(if (selected) brandRed else Color.Transparent)
            .pressScale { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color.White else RadioColors.TextMuted,
            fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1,
        )
    }
}

/**
 * Onda de audio animada con estética de dial: líneas verticales finas cuya altura
 * recorre una onda (como sonido en vivo) + indicador rojo central. [active] sube la
 * amplitud cuando alguien transmite.
 */
@Composable
private fun TunerRuler(active: Boolean, level: Float, modifier: Modifier) {
    val tickColor = RadioColors.Border
    val tickActive = Color(0xFFD71920).copy(alpha = 0.58f)
    val red = Color(0xFFD71920)
    val transition = rememberInfiniteTransition(label = "audiowave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(if (active) 900 else 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    // La amplitud sigue el volumen real de la voz: más fuerte = onda más alta.
    val amp = if (active) (0.3f + 1.25f * level).coerceIn(0.22f, 1.3f) else 0.45f
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // Compose can measure this Canvas at zero (or sub-pixel) size during
        // transitions. Keep all drawing bounds valid until it gets space.
        if (w <= 0f || h <= 0f) return@Canvas
        val n = 52
        val gap = w / n
        for (i in 0..n) {
            val x = i * gap
            val t = i.toFloat() / n
            val env = kotlin.math.sin(t * Math.PI).toFloat() // atenúa en los bordes
            val wave = kotlin.math.sin((t * 20f + phase).toDouble()).toFloat() * 0.6f +
                kotlin.math.sin((t * 37f - phase * 1.6f).toDouble()).toFloat() * 0.4f
            val norm = (0.5f + 0.5f * wave).coerceIn(0f, 1f)
            val tickH = (h * (0.14f + 0.46f * amp * env * norm)).coerceIn(0f, h)
            drawLine(
                color = if (active) tickActive else tickColor,
                start = Offset(x, h / 2f - tickH / 2f),
                end = Offset(x, h / 2f + tickH / 2f),
                strokeWidth = 2.2f,
                cap = StrokeCap.Round,
            )
        }
        drawLine(
            color = red,
            start = Offset(w / 2f, 0f),
            end = Offset(w / 2f, h),
            strokeWidth = 5f,
            cap = StrokeCap.Round,
        )
    }
}

/** Slider de volumen de la radio: arrastrable y tocable; controla STREAM_VOICE_CALL. */
@Composable
private fun VolumeSlider(modifier: Modifier) {
    val fraction = RadioManager.callVolume.coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier
            .height(34.dp)
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    if (size.width > 0) {
                        RadioManager.setVolume((pos.x / size.width).coerceIn(0f, 1f))
                    }
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    if (size.width > 0) {
                        RadioManager.setVolume((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val w = maxWidth
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(RadioColors.Border))
        Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(CircleShape).background(RadioColors.Ink))
        // El pulgar (22dp) se mantiene dentro del carril: 0 → izquierda, 1 → derecha.
        Box(
            Modifier.offset(x = (w - 22.dp) * fraction).size(22.dp).clip(CircleShape).background(RadioColors.Ink).border(3.dp, RadioColors.White, CircleShape),
        )
    }
}
