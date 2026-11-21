package com.mrperfectyadav.app

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    var showYouTube by remember { mutableStateOf(false) }

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
                item {
                    FeatureCard("YouTube Music Player", "Search and play videos inside this app", Icons.Default.PlayCircle) {
                        showYouTube = true
                    }
                }
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
        if (showYouTube) {
            YouTubePlayerDialog(onDismiss = { showYouTube = false })
        }
    }
}

@Composable
private fun YouTubePlayerDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(Black)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Panel).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = White)
                }
                Text(
                    "YouTube Music",
                    color = White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    WebView(viewContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        loadUrl("https://m.youtube.com/")
                    }
                },
                onRelease = { webView ->
                    webView.stopLoading()
                    webView.destroy()
                }
            )
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
