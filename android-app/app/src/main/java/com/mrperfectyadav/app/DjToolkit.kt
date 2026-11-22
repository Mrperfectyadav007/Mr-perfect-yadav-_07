package com.mrperfectyadav.app

import android.media.MediaPlayer
import android.media.ToneGenerator
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

private val DjGreen = Color(0xFF20D67B)
private val DjBlue = Color(0xFF328BFF)
private val DjRed = Color(0xFFFF4057)
private val DjPanel = Color(0xFF111923)

@Composable
fun DjToolkitDialog(player: MediaPlayer, isPlaying: Boolean, onDismiss: () -> Unit) {
    var tempo by remember { mutableFloatStateOf(1f) }
    var bpm by remember { mutableFloatStateOf(120f) }
    var crossfader by remember { mutableFloatStateOf(0f) }
    var loopEnabled by remember { mutableStateOf(false) }
    var reverbEnabled by remember { mutableStateOf(false) }
    val pads = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 75) }.getOrNull() }

    DisposableEffect(Unit) {
        onDispose { runCatching { pads?.release() } }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("DJ TOOLKIT", color = DjGreen, fontWeight = FontWeight.Black)
                Text("MR. PERFECT YADAV • MIX DECK", color = Color.LightGray, fontSize = 10.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DjStat("BPM", bpm.toInt().toString(), DjBlue, Modifier.weight(1f))
                    DjStat("TEMPO", "${((tempo - 1f) * 100).toInt()}%", DjGreen, Modifier.weight(1f))
                    DjStat("DECK", if (isPlaying) "LIVE" else "READY", DjRed, Modifier.weight(1f))
                }
                Text("BPM / BEAT", color = DjGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Slider(value = bpm, onValueChange = { bpm = it }, valueRange = 60f..180f,
                    colors = SliderDefaults.colors(thumbColor = DjGreen, activeTrackColor = DjGreen))
                Text("TEMPO / PITCH", color = DjBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Slider(value = tempo, onValueChange = {
                    tempo = it
                    runCatching { player.playbackParams = player.playbackParams.setSpeed(it) }
                }, valueRange = 0.5f..2f,
                    colors = SliderDefaults.colors(thumbColor = DjBlue, activeTrackColor = DjBlue))
                Text("CROSSFADER", color = DjRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Slider(value = crossfader, onValueChange = { crossfader = it }, valueRange = -1f..1f,
                    colors = SliderDefaults.colors(thumbColor = DjRed, activeTrackColor = DjRed))
                Text("A  ◀                 CENTER                 ▶  B", color = Color.LightGray, fontSize = 10.sp)
                Text("PLAYBACK TOOLS", color = DjGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { runCatching { player.seekTo(0) } }, modifier = Modifier.weight(1f)) {
                        Text("CUE / RESET")
                    }
                    OutlinedButton(onClick = {
                        loopEnabled = !loopEnabled
                        runCatching { player.isLooping = loopEnabled }
                    }, modifier = Modifier.weight(1f)) {
                        Text(if (loopEnabled) "LOOP ON" else "LOOP OFF")
                    }
                }
                Text("SOUND PADS", color = DjBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("KICK", "SNARE", "HAT").forEachIndexed { index, label ->
                        Button(
                            onClick = {
                                val tone = when (index) {
                                    0 -> ToneGenerator.TONE_PROP_BEEP
                                    1 -> ToneGenerator.TONE_DTMF_5
                                    else -> ToneGenerator.TONE_PROP_ACK
                                }
                                runCatching { pads?.startTone(tone, 180) }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = listOf(DjGreen, DjBlue, DjRed)[index])
                        ) { Text(label, fontWeight = FontWeight.Bold) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("REVERB FX", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Coming with advanced audio engine", color = Color.LightGray, fontSize = 10.sp)
                    }
                    Switch(checked = reverbEnabled, onCheckedChange = { reverbEnabled = it })
                }
                Text(
                    "Note: This version controls one playing track. Crossfader is a layout/control preview; true two-deck mixing and live reverb require a dedicated dual-deck audio engine.",
                    color = Color.LightGray, fontSize = 10.sp
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("DONE", color = DjGreen) } },
        containerColor = DjPanel,
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

@Composable
private fun DjStat(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier.background(Color(0xFF1B2A38), RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text(label, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}
