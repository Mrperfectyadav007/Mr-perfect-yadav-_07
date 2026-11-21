package com.mrperfectyadav.app

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Black = Color(0xFF090909)
private val Panel = Color(0xFF191919)
private val Red = Color(0xFFFF3030)
private val White = Color(0xFFF7F7F7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MrPerfectApp() }
    }
}

@Composable
fun MrPerfectApp() {
    val context = LocalContext.current

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Red,
        background = Black,
        surface = Panel,
        onPrimary = White,
        onBackground = White,
        onSurface = White
    )) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF260707), Black, Black)))
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text("MR. PERFECT YADAV", color = Red, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Spacer(Modifier.height(18.dp))
                    Text("WELCOME", color = White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text("Your personal digital world", color = Color.LightGray, fontSize = 15.sp)
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Panel),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier.size(92.dp).background(
                                    Brush.linearGradient(listOf(Red, Color(0xFF5A0909))),
                                    RoundedCornerShape(50)
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("MPY", color = White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("MR. PERFECT YADAV", color = White, fontSize = 21.sp,
                                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Text("Official Personal App", color = Color.LightGray, fontSize = 13.sp)
                        }
                    }
                }
                item { SectionTitle("MY FEATURES") }
                item {
                    FeatureCard("Telegram Bot", "Open your official Telegram bot", Icons.Default.Send) {
                        openLink(context, "https://t.me/Mr_perfect_yadav_07robot")
                    }
                }
                item { MusicPlayerCard() }
                item {
                    FeatureCard("AI Assistant", "Open ChatGPT and start an AI conversation", Icons.Default.AutoAwesome) {
                        openLink(context, "https://chatgpt.com/")
                    }
                }
                item {
                    FeatureCard("Chat & Community", "Visit the official Telegram channel", Icons.Default.Groups) {
                        openLink(context, "https://t.me/RADHEY_RADHEY_07")
                    }
                }
                item {
                    FeatureCard("Instagram Profile", "Follow the official profile", Icons.Default.CameraAlt) {
                        openLink(context, "https://instagram.com/mr_perfect_yadav_07")
                    }
                }
                item {
                    FeatureCard("About App", "Version 1.2 • MR. PERFECT YADAV", Icons.Default.Info) {
                        openLink(context, "https://mrperfectyadav007.github.io/mr-perfect-yadav-_07/")
                    }
                }
                item {
                    Text("Made with ❤️ for MR. PERFECT YADAV",
                        color = Color.Gray, fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun MusicPlayerCard() {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var trackName by remember { mutableStateOf("अभी कोई गाना नहीं चुना") }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedUri = uri
        trackName = if (uri == null) "अभी कोई गाना नहीं चुना" else "आपका चुना हुआ गाना"
    }

    LaunchedEffect(selectedUri) {
        player?.release()
        player = null
        isPlaying = false
        isPrepared = false
        val uri = selectedUri ?: return@LaunchedEffect
        val newPlayer = MediaPlayer()
        player = newPlayer
        newPlayer.setOnPreparedListener {
            isPrepared = true
            it.start()
            isPlaying = true
        }
        newPlayer.setOnCompletionListener { isPlaying = false }
        try {
            newPlayer.setDataSource(context, uri)
            newPlayer.prepareAsync()
        } catch (_: Exception) {
            newPlayer.release()
            player = null
            trackName = "गाना नहीं चल पाया"
        }
    }

    DisposableEffect(player) {
        val currentPlayer = player
        onDispose { currentPlayer?.release() }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column(Modifier.fillMaxWidth().padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Red, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(15.dp))
                Column(Modifier.weight(1f)) {
                    Text("Music Player", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(trackName, color = Color.LightGray, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { audioPicker.launch("audio/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = Red),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("गाना चुनें")
                }
                IconButton(
                    onClick = {
                        val current = player ?: return@IconButton
                        if (isPlaying) {
                            current.pause()
                            isPlaying = false
                        } else if (isPrepared) {
                            current.start()
                            isPlaying = true
                        }
                    },
                    enabled = isPrepared
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = if (isPrepared) Red else Color.Gray,
                        modifier = Modifier.size(34.dp)
                    )
                }
                IconButton(
                    onClick = {
                        player?.let {
                            if (isPrepared) {
                                it.pause()
                                it.seekTo(0)
                                isPlaying = false
                            }
                        }
                    },
                    enabled = isPrepared
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = if (isPrepared) Red else Color.Gray)
                }
            }
            Text("फोन से MP3 या दूसरी audio file चुनकर सुनें।",
                color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, color = Red, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
}

@Composable
private fun FeatureCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Red, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Color.LightGray, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Red)
        }
    }
}

private fun openLink(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
