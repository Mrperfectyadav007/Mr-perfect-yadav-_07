package com.mrperfectyadav.app

import android.content.Intent
import androidx.core.content.ContextCompat
import android.content.BroadcastReceiver
import android.content.Context
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.media.audiofx.BassBoost
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.provider.OpenableColumns
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.session.MediaSessionCompat
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

private val Black = Color(0xFF070B10)
private val Panel = Color(0xFF111923)
private val PanelLight = Color(0xFF1B2A38)
private val Green = Color(0xFF20D67B)
private val Blue = Color(0xFF328BFF)
private val Red = Color(0xFFFF4057)
private val Purple = Color(0xFF9B5CFF)
private val BrandGradient = listOf(Green, Blue, Purple, Red)
private val White = Color(0xFFF7FAFF)
private val Muted = Color(0xFFB8C5D3)

private data class LocalSong(val uri: Uri, val title: String)

class MainActivity : ComponentActivity() {
    private var mediaSession: MediaSessionCompat? = null
    var sharedYoutubeUrl by mutableStateOf<String?>(null)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiveSharedYoutubeLink(intent)
        createMusicNotificationChannel()
        mediaSession = MediaSessionCompat(this, "MrPerfectYadavMusic").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { PlaybackBridge.onPlay?.invoke() }
                override fun onPause() { PlaybackBridge.onPause?.invoke() }
                override fun onSkipToNext() { PlaybackBridge.onNext?.invoke() }
                override fun onSkipToPrevious() { PlaybackBridge.onPrevious?.invoke() }
            })
            isActive = true
        }
        NotificationController.sessionToken = mediaSession?.sessionToken
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        setContent { MrPerfectApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveSharedYoutubeLink(intent)
    }

    private fun receiveSharedYoutubeLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val match = Regex("https?://(?:[a-z0-9-]+\\.)*(?:youtube\\.com|youtu\\.be)/[^\\s]+", RegexOption.IGNORE_CASE)
            .find(sharedText)?.value?.trimEnd('.', ',', ')', ']', '}', '>')
        if (!match.isNullOrBlank()) sharedYoutubeUrl = match
    }

    override fun onDestroy() {
        stopService(Intent(this, MusicPlaybackService::class.java))
        NotificationController.cancel(this)
        mediaSession?.release()
        mediaSession = null
        NotificationController.sessionToken = null
        super.onDestroy()
    }

    private fun createMusicNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NotificationController.CHANNEL_ID,
                "Music playback",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Premium MR. PERFECT YADAV music controls and playback pop-up"
                enableLights(true)
                lightColor = android.graphics.Color.rgb(155, 92, 255)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

@Composable
fun MrPerfectApp() {
    val context = LocalContext.current
    val sharedYoutubeUrl = (context as? MainActivity)?.sharedYoutubeUrl
    var searchQuery by remember { mutableStateOf("") }
    var onlineSongs by remember { mutableStateOf(listOf<LocalSong>()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchMessage by remember { mutableStateOf("Search for a song, artist or album.") }
    var downloadMessage by remember { mutableStateOf("") }
    var permittedFileUrl by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var songs by remember { mutableStateOf(listOf<LocalSong>()) }
    var currentIndex by remember { mutableIntStateOf(-1) }
    var isPlaying by remember { mutableStateOf(false) }
    var isPreparing by remember { mutableStateOf(false) }
    var duration by remember { mutableIntStateOf(0) }
    var position by remember { mutableIntStateOf(0) }
    var equalizer by remember { mutableStateOf<Equalizer?>(null) }
    var bassBoost by remember { mutableStateOf<BassBoost?>(null) }
    var bassBoostEnabled by remember { mutableStateOf(false) }
    var autoPlayNext by remember { mutableStateOf(true) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatCurrent by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var showSettings by remember { mutableStateOf(false) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var showDjToolkit by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("Check whether a newer APK is available.") }
    var updateUrl by remember { mutableStateOf<String?>(null) }
    var checkingUpdates by remember { mutableStateOf(false) }
    var showAssistant by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf("HOME") }
    var eqPresetNames by remember { mutableStateOf(listOf<String>()) }
    var selectedEqPreset by remember { mutableIntStateOf(-1) }
    var eqBandLevels by remember { mutableStateOf(listOf<Float>()) }
    val player = remember { MediaPlayer() }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val added = uris.map { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val title = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && column >= 0) cursor.getString(column) else null
            } ?: (uri.lastPathSegment ?: "Audio track")
            LocalSong(uri, title)
        }
        songs = (songs + added).distinctBy { it.uri }
    }

    fun playTrack(index: Int) {
        if (index !in songs.indices) return
        val serviceIntent = Intent(context, MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_UPDATE
            putExtra(MusicPlaybackService.EXTRA_TITLE, songs[index].title)
        }
        runCatching { ContextCompat.startForegroundService(context, serviceIntent) }
        currentIndex = index
        isPreparing = true
        isPlaying = false
        position = 0
        duration = 0
        runCatching {
            player.reset()
            player.setOnPreparedListener { mp ->
                duration = mp.duration.coerceAtLeast(0)
                runCatching { equalizer?.release() }
                runCatching { bassBoost?.release() }
                runCatching {
                    val boost = BassBoost(0, mp.audioSessionId)
                    boost.enabled = bassBoostEnabled
                    bassBoost = boost
                }.onFailure { bassBoost = null }
                runCatching {
                    val effect = Equalizer(0, mp.audioSessionId)
                    effect.enabled = true
                    equalizer = effect
                    eqPresetNames = (0 until effect.numberOfPresets.toInt()).map { effect.getPresetName(it.toShort()).toString() }
                    selectedEqPreset = -1
                    eqBandLevels = (0 until effect.numberOfBands.toInt()).map { effect.getBandLevel(it.toShort()).toFloat() }
                }.onFailure {
                    equalizer = null
                    eqPresetNames = emptyList()
                    selectedEqPreset = -1
                    eqBandLevels = emptyList()
                }
                isPreparing = false
                mp.start()
                isPlaying = true
            }
            player.setOnCompletionListener {
                when {
                    repeatCurrent && currentIndex in songs.indices -> playTrack(currentIndex)
                    shuffleEnabled && songs.isNotEmpty() -> {
                        val candidates = songs.indices.filter { it != currentIndex }.ifEmpty { songs.indices.toList() }
                        playTrack(candidates.random())
                    }
                    autoPlayNext && currentIndex + 1 < songs.size -> playTrack(currentIndex + 1)
                    else -> { isPlaying = false; position = 0 }
                }
            }
            player.setOnErrorListener { _, _, _ ->
                isPreparing = false
                isPlaying = false
                true
            }
            player.setDataSource(context, songs[index].uri)
            player.prepareAsync()
        }.onFailure {
            isPreparing = false
            isPlaying = false
        }
    }

    DisposableEffect(Unit) {
        PlaybackBridge.onPlay = {
            if (currentIndex in songs.indices && !isPreparing) {
                if (isPlaying) { runCatching { player.pause() }; isPlaying = false }
                else { runCatching { if (duration > 0) player.start() else playTrack(currentIndex) }; isPlaying = true }
            }
        }
        PlaybackBridge.onPause = {
            runCatching { player.pause() }
            isPlaying = false
        }
        PlaybackBridge.onNext = { if (currentIndex + 1 < songs.size) playTrack(currentIndex + 1) }
        PlaybackBridge.onPrevious = { if (currentIndex > 0) playTrack(currentIndex - 1) }
        onDispose {
            PlaybackBridge.onPlay = null
            PlaybackBridge.onPause = null
            PlaybackBridge.onNext = null
            PlaybackBridge.onPrevious = null
            runCatching { equalizer?.release() }
            runCatching { bassBoost?.release() }
            runCatching { player.release() }
        }
    }

    LaunchedEffect(currentIndex, isPlaying, isPreparing) {
        if (currentIndex in songs.indices) {
            NotificationController.show(context, songs[currentIndex].title, isPlaying)
        } else {
            NotificationController.cancel(context)
        }
    }

    LaunchedEffect(isPlaying, currentIndex) {
        while (isPlaying) {
            if (runCatching { player.isPlaying }.getOrDefault(false)) {
                position = runCatching { player.currentPosition }.getOrDefault(position)
            }
            delay(500)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Blue, secondary = Green, tertiary = Purple,
        background = Black, surface = Panel,
        onPrimary = White, onBackground = White, onSurface = White
    )) {
        BackHandler(enabled = showAssistant || showSettings || onlineSongs.isNotEmpty() || searchQuery.isNotBlank()) {
            when {
                showAssistant -> showAssistant = false
                showSettings -> showSettings = false
                onlineSongs.isNotEmpty() -> {
                    onlineSongs = emptyList()
                    searchMessage = "Search for a song, artist or album."
                }
                searchQuery.isNotBlank() -> {
                    searchQuery = ""
                    searchMessage = "Search for a song, artist or album."
                }
            }
        }
        CompositionLocalProvider(LocalTextStyle provides TextStyle(fontWeight = FontWeight.Bold)) {
        Box(Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.home_background),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.40f
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color(0xB8081714), Color(0xD90C1020), Color(0xF2180B19))
                    )
                )
            )
            Column(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("MR. PERFECT YADAV", color = Green, fontSize = 12.sp,
                                fontWeight = FontWeight.Black, letterSpacing = 2.5.sp)
                            Text("Good music. Good mood.", color = White, fontSize = 25.sp,
                                fontWeight = FontWeight.ExtraBold)
                        }
                        Image(
                            painter = painterResource(id = R.drawable.mr_perfect_dp),
                            contentDescription = "MR. PERFECT YADAV profile photo",
                            modifier = Modifier.size(54.dp).clip(CircleShape)
                        )
                        IconButton(onClick = { showAssistant = true }) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "AI Assistant", tint = Red)
                        }
                        IconButton(onClick = { showDjToolkit = true }) {
                            Icon(Icons.Default.GraphicEq, contentDescription = "DJ Toolkit", tint = Green)
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Blue)
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("APP PAGES", color = White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        SmallLinkCard("Music Player", "Your songs and playback controls", Icons.Default.MusicNote) { currentPage = "MUSIC" }
                        SmallLinkCard("WEB MUSIC PLAYER", "Connect your hosted Telegram Web Player", Icons.Default.Language) { currentPage = "WEB_PLAYER" }
                        SmallLinkCard("YouTube", "Open YouTube separately inside this app", Icons.Default.PlayCircle) { currentPage = "YOUTUBE" }
                        SmallLinkCard("Downloads", "Direct file downloads and status", Icons.Default.Download) { currentPage = "DOWNLOADS" }
                        SmallLinkCard("Equalizer", "Sound presets and Bass Boost", Icons.Default.Equalizer) { currentPage = "EQUALIZER" }
                        SmallLinkCard("DJ Toolkit", "DJ controls and tools", Icons.Default.GraphicEq) { currentPage = "DJ"; showDjToolkit = true }
                        SmallLinkCard("Studio Master", "Recording, mixing and vocal effects", Icons.Default.Mic) { currentPage = "STUDIO" }
                        SmallLinkCard("AI Assistant", "Open the assistant chat", Icons.Default.AutoAwesome) { currentPage = "AI"; showAssistant = true }
                        SmallLinkCard("Settings", "Playback and app updates", Icons.Default.Settings) { currentPage = "SETTINGS"; showSettings = true }
                        if (!sharedYoutubeUrl.isNullOrBlank()) {
                            SmallLinkCard("Shared YouTube Link", "View the link shared to this app", Icons.Default.Share) { currentPage = "SHARED" }
                        }
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().height(172.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.linearGradient(listOf(Color(0xFF123D2D), Color(0xFF12365E), Color(0xFF39205A), Color(0xFF4A1727), Color(0xFF101923)))
                            ).padding(20.dp)
                        ) {
                            Column(Modifier.align(Alignment.CenterStart)) {
                                Text("YOUR MUSIC, YOUR WORLD", color = White.copy(alpha = .8f),
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                Spacer(Modifier.height(7.dp))
                                Text("Play what you love.", color = White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { picker.launch(arrayOf("audio/*")) },
                                    colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Black),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(7.dp))
                                    Text("Add MP3 songs", fontWeight = FontWeight.Bold)
                                }
                            }
                            Icon(Icons.Default.MusicNote, contentDescription = null,
                                tint = White.copy(alpha = .16f), modifier = Modifier.size(112.dp).align(Alignment.CenterEnd))
                        }
                    }
                }
                item {
                    if (!sharedYoutubeUrl.isNullOrBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Panel)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = Red)
                                    Spacer(Modifier.width(8.dp))
                                    Text("YouTube link received", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                }
                                Text(sharedYoutubeUrl!!, color = Muted, fontSize = 12.sp)
                                Text("You can open this shared link in YouTube. Offline saving is available through YouTube Music Premium where supported; this app cannot extract YouTube audio/video into an MP3 or MP4 file.", color = Muted, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { openLink(context, sharedYoutubeUrl!!) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = White)
                                    ) { Text("Open YouTube") }
                                    OutlinedButton(onClick = { openLink(context, "https://music.youtube.com/") }) {
                                        Text("YouTube Music")
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Panel)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = Green)
                                Spacer(Modifier.width(8.dp))
                                Text("Direct Media Downloads", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            }
                            Divider(color = PanelLight)
                            Text("Paste a direct HTTPS link to a media file you own or are authorized to download. Supported: MP3, M4A, OGG, MP4, WEBM.", color = Muted, fontSize = 12.sp)
                            OutlinedTextField(
                                value = permittedFileUrl,
                                onValueChange = { permittedFileUrl = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("Paste direct media file URL") },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White, focusedBorderColor = Green, unfocusedBorderColor = PanelLight)
                            )
                            Button(
                                onClick = {
                                    val uri = runCatching { Uri.parse(permittedFileUrl.trim()) }.getOrNull()
                                    downloadMessage = if (uri != null) enqueuePermittedDownload(context, uri, uri.lastPathSegment ?: "media-file") else "Enter a valid media file link."
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Black)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Download permitted file")
                            }
                            if (downloadMessage.isNotBlank()) Text(downloadMessage, color = Green, fontSize = 12.sp)
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
                                    }.onFailure {
                                        downloadMessage = "Downloads app could not be opened. Check the phone's Downloads folder."
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Open Download Manager")
                            }
                            Text("THIRD-PARTY DOWNLOAD SOURCES", color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Open trusted libraries that provide downloads for content marked free to download or public domain.", color = Muted, fontSize = 11.sp)
                            OutlinedButton(
                                onClick = { openLink(context, "https://archive.org/details/audio") },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Internet Archive Audio Library") }
                            OutlinedButton(
                                onClick = { openLink(context, "https://pixabay.com/music/") },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Pixabay Music Downloads") }
                            OutlinedButton(
                                onClick = {
                                    val raw = permittedFileUrl.trim()
                                    val uri = runCatching { Uri.parse(raw) }.getOrNull()
                                    if (uri != null && uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank()) {
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, raw)
                                        }
                                        runCatching { context.startActivity(Intent.createChooser(share, "Open link with another app")) }
                                    } else downloadMessage = "Paste a valid website or media URL first."
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Send link to another downloader app") }
                            Text(
                                "Use third-party providers only for files you are allowed to save. YouTube links open in YouTube; this app does not extract YouTube streams.",
                                color = Muted, fontSize = 11.sp
                            )
                        }
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Panel)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = Green)
                                Spacer(Modifier.width(8.dp))
                                Text("Licensed music catalog", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            }
                            Text(
                                "Commercial full-track catalog integration is ready for a licensed provider, but no provider account or API credentials are connected yet. Only tracks authorized by that provider can be streamed.",
                                color = Muted, fontSize = 12.sp
                            )
                            Divider(color = PanelLight)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Gavel, contentDescription = null, tint = Blue)
                                Spacer(Modifier.width(8.dp))
                                Text("Free legal full-length music", color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Text(
                                "Search below for tracks marked Creative Commons or Public Domain. Check each track's license and attribution terms before reuse or sharing.",
                                color = Muted, fontSize = 12.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Green)
                                Spacer(Modifier.width(8.dp))
                                Text("Your phone's MP3 files", color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Text(
                                "Add your own audio files with the Add MP3 songs button. They play as full local tracks.",
                                color = Muted, fontSize = 12.sp
                            )
                        }
                    }
                }
                item {
                    Text("Online legal full-song search", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Song, singer or album search") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White, unfocusedTextColor = White,
                            focusedBorderColor = Blue, unfocusedBorderColor = PanelLight,
                            focusedLabelColor = Green, cursorColor = Green
                        ),
                        trailingIcon = {
                            IconButton(onClick = {
                                val q = searchQuery.trim()
                                if (q.isNotEmpty()) scope.launch {
                                    isSearching = true
                                    searchMessage = "Searching songs..."
                                    val found = withContext(Dispatchers.IO) {
                                        runCatching {
                                            val safeQuery = q.replace("\\", " ").replace("\"", " ").trim()
                                            val terms = safeQuery.split(Regex("\\s+")).filter { it.length > 1 }.take(6)
                                            val phraseQuery = "(title:\"$safeQuery\" OR creator:\"$safeQuery\")"
                                            val tokenQuery = terms.joinToString(" OR ") { "title:$it OR creator:$it OR description:$it" }
                                            val archiveQuery = "mediatype:audio AND ($phraseQuery OR $tokenQuery)"
                                            val encoded = URLEncoder.encode(archiveQuery, "UTF-8")
                                            val searchUrl = URL("https://archive.org/advancedsearch.php?q=$encoded&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=licenseurl&rows=100&page=1&sort[]=downloads+desc&output=json")
                                            val connection = (searchUrl.openConnection() as HttpURLConnection)
                                            connection.connectTimeout = 15000
                                            connection.readTimeout = 20000
                                            connection.requestMethod = "GET"
                                            try {
                                                val body = connection.inputStream.bufferedReader().use { it.readText() }
                                                val docs = JSONObject(body).getJSONObject("response").getJSONArray("docs")
                                                coroutineScope {
                                                    (0 until minOf(docs.length(), 40)).map { i ->
                                                        async {
                                                            runCatching {
                                                                val doc = docs.getJSONObject(i)
                                                                val identifier = doc.optString("identifier")
                                                                if (identifier.isBlank()) return@runCatching null
                                                                val metadataUrl = URL("https://archive.org/metadata/${Uri.encode(identifier)}")
                                                                val metaConnection = (metadataUrl.openConnection() as HttpURLConnection)
                                                                metaConnection.connectTimeout = 10000
                                                                metaConnection.readTimeout = 12000
                                                                try {
                                                                    val metaText = metaConnection.inputStream.bufferedReader().use { it.readText() }
                                                                    val metadataRoot = JSONObject(metaText)
                                                                    val metadata = metadataRoot.optJSONObject("metadata")
                                                                    val license = (doc.optString("licenseurl").takeIf { it.isNotBlank() }
                                                                        ?: metadata?.optString("licenseurl").orEmpty()).lowercase()
                                                                    val allowedLicense = license.contains("creativecommons.org/licenses") ||
                                                                        license.contains("creativecommons.org/publicdomain") ||
                                                                        license.contains("publicdomain")
                                                                    if (!allowedLicense) return@runCatching null
                                                                    val files = metadataRoot.optJSONArray("files") ?: return@runCatching null
                                                                    var audioFile: String? = null
                                                                    for (j in 0 until files.length()) {
                                                                        val file = files.optJSONObject(j) ?: continue
                                                                        val name = file.optString("name")
                                                                        val format = file.optString("format")
                                                                        val playable = name.lowercase().endsWith(".mp3") ||
                                                                            name.lowercase().endsWith(".m4a") ||
                                                                            name.lowercase().endsWith(".ogg")
                                                                        if (playable && (format.contains("mp3", true) ||
                                                                                format.contains("mpeg", true) ||
                                                                                format.contains("aac", true) ||
                                                                                format.contains("ogg", true) ||
                                                                                format.contains("audio", true))) {
                                                                            audioFile = name
                                                                            break
                                                                        }
                                                                    }
                                                                    val selectedFile = audioFile ?: return@runCatching null
                                                                    val title = doc.optString("title", "Untitled audio")
                                                                    val creator = doc.optString("creator", "Unknown artist")
                                                                    val streamUrl = "https://archive.org/download/${Uri.encode(identifier)}/${Uri.encode(selectedFile)}"
                                                                    LocalSong(Uri.parse(streamUrl), "$title — $creator")
                                                                } finally {
                                                                    metaConnection.disconnect()
                                                                }
                                                            }.getOrNull()
                                                        }
                                                    }.awaitAll().filterNotNull()
                                                }
                                            } finally { connection.disconnect() }
                                        }.getOrDefault(emptyList())
                                    }
                                    onlineSongs = found
                                    songs = (songs + found).distinctBy { it.uri }
                                    searchMessage = if (found.isEmpty()) "No matching licensed MP3 found. Try a shorter song name, artist name, or another spelling." else "${found.size} licensed MP3 track(s) found. Tap Play to stream."
                                    isSearching = false
                                }
                            }) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = Red)
                            }
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(searchMessage, color = Muted, fontSize = 12.sp)
                    if (isSearching) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Blue, trackColor = PanelLight)
                    }
                    onlineSongs.forEach { song ->
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp)
                                .background(Panel, RoundedCornerShape(14.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                Text(song.title, color = White, fontSize = 13.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("Full-length audio • Legal catalogue", color = Muted, fontSize = 10.sp)
                            }
                            IconButton(onClick = {
                                val index = songs.indexOfFirst { it.uri == song.uri }
                                if (index >= 0) playTrack(index)
                            }) {
                                Icon(Icons.Default.PlayCircle, contentDescription = "Play full track", tint = Red)
                            }
                            IconButton(onClick = {
                                downloadMessage = enqueuePermittedDownload(context, song.uri, song.title)
                            }) {
                                Icon(Icons.Default.Download, contentDescription = "Download permitted track", tint = Green)
                            }
                        }
                    }
                    if (downloadMessage.isNotBlank()) Text(downloadMessage, color = Green, fontSize = 11.sp)
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Panel)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Equalizer, contentDescription = null, tint = Blue)
                                Spacer(Modifier.width(8.dp))
                                Text("Audio Equalizer", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                if (eqPresetNames.isEmpty()) "Play a song to check equalizer support on this device."
                                else "Choose a sound preset. Availability depends on your phone.",
                                color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                            )
                            if (eqPresetNames.isNotEmpty()) {
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                    eqPresetNames.forEachIndexed { index, preset ->
                                        FilterChip(
                                            selected = selectedEqPreset == index,
                                            onClick = {
                                                runCatching {
                                                    equalizer?.usePreset(index.toShort())
                                                    selectedEqPreset = index
                                                }
                                            },
                                            label = { Text(preset) },
                                            modifier = Modifier.padding(end = 6.dp)
                                        )
                                    }
                                }
                                val activeEq = equalizer
                                if (activeEq != null) {
                                    val bandCount = runCatching { activeEq.numberOfBands.toInt() }.getOrDefault(0)
                                    val levelRange = runCatching { activeEq.bandLevelRange }.getOrNull()
                                    if (levelRange != null && bandCount > 0) {
                                        Text("Manual band controls", color = White, fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(top = 8.dp))
                                        for (bandIndex in 0 until bandCount) {
                                            val band = bandIndex.toShort()
                                            val centerHz = runCatching { activeEq.getCenterFreq(band) / 1000 }.getOrDefault(0)
                                            val currentLevel = runCatching { activeEq.getBandLevel(band).toFloat() }.getOrDefault(0f)
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(if (centerHz >= 1000) "${centerHz / 1000f} kHz" else "$centerHz Hz",
                                                        color = White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                                    Text("${String.format("%+.1f", currentLevel / 100f)} dB",
                                                        color = Muted, fontSize = 11.sp)
                                                }
                                                Slider(
                                                    value = currentLevel.coerceIn(levelRange[0].toFloat(), levelRange[1].toFloat()),
                                                    onValueChange = { value ->
                                                        runCatching {
                                                            activeEq.setBandLevel(band, value.toInt().toShort())
                                                            selectedEqPreset = -1
                                                        }
                                                    },
                                                    valueRange = levelRange[0].toFloat()..levelRange[1].toFloat(),
                                                    colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text("Equalizer controls appear after a local song starts playing. Your phone must support Android audio effects.",
                                    color = Muted, fontSize = 11.sp)
                            }
                            Text("Preset and manual frequency controls use Android's built-in Equalizer. Actual effect depends on device support.",
                                color = Muted, fontSize = 10.sp)
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Bass Boost", color = White, modifier = Modifier.weight(1f))
                                Switch(checked = bassBoostEnabled, onCheckedChange = { enabled ->
                                    bassBoostEnabled = enabled
                                    runCatching { bassBoost?.enabled = enabled }
                                })
                            }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Your Library", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f))
                        IconButton(onClick = { picker.launch(arrayOf("audio/*")) }) {
                            Icon(Icons.Default.Add, contentDescription = "Add songs", tint = Red)
                        }
                    }
                }
                if (songs.isEmpty()) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = Green, modifier = Modifier.size(38.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No songs added yet", color = White, fontWeight = FontWeight.Bold)
                                Text("Add MP3 files from your phone to start listening.", color = Muted,
                                    fontSize = 12.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                } else {
                    itemsIndexed(songs) { index, song ->
                        Row(
                            Modifier.fillMaxWidth().background(
                                if (index == currentIndex) Color(0xFF123B35) else Panel,
                                RoundedCornerShape(14.dp)
                            ).clickable { playTrack(index) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(44.dp).background(PanelLight, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Blue)
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(song.title, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Local audio", color = Muted, fontSize = 11.sp)
                            }
                            Icon(
                                if (index == currentIndex && isPlaying) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                                contentDescription = "Play", tint = Red
                            )
                        }
                    }
                }
                item { LibraryFeaturePanel(trackTitles = songs.map { it.title }, currentIndex = currentIndex) }
                item { Text("Connect", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                item { SmallLinkCard("Telegram Bot", "t.me/Mr_perfect_yadav_07robot", Icons.Default.Send) {
                    openLink(context, "https://t.me/Mr_perfect_yadav_07robot")
                } }
                item { SmallLinkCard("Official Channel", "t.me/RADHEY_RADHEY_07", Icons.Default.Groups) {
                    openLink(context, "https://t.me/RADHEY_RADHEY_07")
                } }
                item { SmallLinkCard("Instagram", "@mr_perfect_yadav_07", Icons.Default.CameraAlt) {
                    openLink(context, "https://instagram.com/mr_perfect_yadav_07")
                } }
                item {
                    Text("MR. PERFECT YADAV • MUSIC PLAYER", color = Color.Gray, fontSize = 10.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = TextAlign.Center)
                }
            }

            if (currentIndex in songs.indices) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
                        .clickable { showNowPlaying = true },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth()
                            .background(Brush.linearGradient(listOf(Color(0xFF142C2B), Color(0xFF142943), Color(0xFF251B2A))))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                                    .background(Brush.linearGradient(listOf(Green, Blue))),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = White,
                                    modifier = Modifier.size(24.dp))
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(songs[currentIndex].title, color = White, fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (isPreparing) "Loading..." else "MR. PERFECT YADAV • NOW PLAYING",
                                    color = Muted, fontSize = 9.sp, letterSpacing = 0.7.sp)
                            }
                            Text(if (isPlaying) "PLAYING" else if (isPreparing) "LOADING" else "PAUSED",
                                color = if (isPlaying) Green else Blue, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PremiumPlaybackButton(
                                icon = Icons.Default.SkipPrevious, description = "Previous song",
                                size = 46.dp, colors = listOf(Blue, Color(0xFF2455A8)),
                                enabled = currentIndex > 0,
                                onClick = { if (currentIndex > 0) playTrack(currentIndex - 1) }
                            )
                            Spacer(Modifier.width(22.dp))
                            PremiumPlaybackButton(
                                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                description = if (isPlaying) "Pause" else "Play",
                                size = 58.dp, colors = listOf(Green, Blue),
                                enabled = !isPreparing,
                                onClick = {
                                    if (isPreparing) return@PremiumPlaybackButton
                                    if (isPlaying) {
                                        runCatching { player.pause() }
                                        isPlaying = false
                                    } else {
                                        runCatching {
                                            if (duration > 0) player.start()
                                            else playTrack(currentIndex)
                                        }
                                        isPlaying = true
                                    }
                                }
                            )
                            Spacer(Modifier.width(22.dp))
                            PremiumPlaybackButton(
                                icon = Icons.Default.SkipNext, description = "Next song",
                                size = 46.dp, colors = listOf(Red, Color(0xFFB62949)),
                                enabled = currentIndex + 1 < songs.size,
                                onClick = { if (currentIndex + 1 < songs.size) playTrack(currentIndex + 1) }
                            )
                        }
                        if (duration > 0) {
                            Spacer(Modifier.height(8.dp))
                            Slider(
                                value = position.coerceIn(0, duration).toFloat(),
                                onValueChange = { position = it.toInt() },
                                onValueChangeFinished = { runCatching { player.seekTo(position) } },
                                valueRange = 0f..duration.toFloat(),
                                modifier = Modifier.height(24.dp),
                                colors = SliderDefaults.colors(thumbColor = Green, activeTrackColor = Blue,
                                    inactiveTrackColor = PanelLight)
                            )
                        }
                    }
                }
            }
            }
        }
        BackHandler(enabled = showNowPlaying) { showNowPlaying = false }
        if (showNowPlaying && currentIndex in songs.indices) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(Color(0xFF07120F), Color(0xFF0A1930), Color(0xFF24101A)))
                ).padding(horizontal = 22.dp, vertical = 18.dp)
            ) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showNowPlaying = false }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = White)
                        }
                        Text("NOW PLAYING", color = Green, fontSize = 13.sp,
                            fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                            modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = White)
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).padding(horizontal = 18.dp)
                            .clip(RoundedCornerShape(30.dp))
                            .background(Brush.linearGradient(listOf(Green.copy(alpha = .85f), Blue, Red.copy(alpha = .8f)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Album, contentDescription = "Album artwork",
                            tint = White.copy(alpha = .92f), modifier = Modifier.size(150.dp))
                    }
                    Spacer(Modifier.height(30.dp))
                    Text(songs[currentIndex].title, color = White, fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold, maxLines = 2, textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Text("MR. PERFECT YADAV • MUSIC PLAYER", color = Muted,
                        fontSize = 11.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.height(22.dp))
                    Slider(
                        value = position.coerceIn(0, duration.coerceAtLeast(1)).toFloat(),
                        onValueChange = { position = it.toInt() },
                        onValueChangeFinished = { runCatching { player.seekTo(position) } },
                        valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                        colors = SliderDefaults.colors(thumbColor = Green, activeTrackColor = Blue,
                            inactiveTrackColor = PanelLight)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("%d:%02d".format(position / 60000, (position / 1000) % 60),
                            color = Muted, fontSize = 11.sp)
                        Text("%d:%02d".format(duration / 60000, (duration / 1000) % 60),
                            color = Muted, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { shuffleEnabled = !shuffleEnabled }) {
                            Icon(Icons.Default.Shuffle, contentDescription = "Shuffle",
                                tint = if (shuffleEnabled) Green else Muted)
                        }
                        PremiumPlaybackButton(
                            icon = Icons.Default.SkipPrevious, description = "Previous song",
                            size = 54.dp, colors = listOf(Blue, Color(0xFF2455A8)),
                            enabled = currentIndex > 0,
                            onClick = { if (currentIndex > 0) playTrack(currentIndex - 1) }
                        )
                        PremiumPlaybackButton(
                            icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            description = if (isPlaying) "Pause" else "Play",
                            size = 76.dp, colors = listOf(Green, Blue), enabled = !isPreparing,
                            onClick = {
                                if (!isPreparing) {
                                    if (isPlaying) { runCatching { player.pause() }; isPlaying = false }
                                    else { runCatching { if (duration > 0) player.start() else playTrack(currentIndex) }; isPlaying = true }
                                }
                            }
                        )
                        PremiumPlaybackButton(
                            icon = Icons.Default.SkipNext, description = "Next song",
                            size = 54.dp, colors = listOf(Red, Color(0xFFB62949)),
                            enabled = currentIndex + 1 < songs.size,
                            onClick = { if (currentIndex + 1 < songs.size) playTrack(currentIndex + 1) }
                        )
                        IconButton(onClick = { repeatCurrent = !repeatCurrent }) {
                            Icon(Icons.Default.RepeatOne, contentDescription = "Repeat current song",
                                tint = if (repeatCurrent) Red else Muted)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (songs[currentIndex].uri.host?.endsWith("archive.org") == true) {
                        OutlinedButton(onClick = {
                            downloadMessage = enqueuePermittedDownload(context, songs[currentIndex].uri, songs[currentIndex].title)
                        }) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Download permitted track")
                        }
                        if (downloadMessage.isNotBlank()) Text(downloadMessage, color = Green, fontSize = 11.sp)
                    }
                    Text(if (isPreparing) "LOADING TRACK…" else if (isPlaying) "NOW PLAYING" else "PAUSED",
                        color = if (isPlaying) Green else Blue, fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                }
            }
        }
        if (currentPage != "HOME") {
            FeaturePage(
                page = currentPage,
                onBack = { currentPage = "HOME"; showAssistant = false; showDjToolkit = false; showSettings = false },
                content = {
                    when (currentPage) {
                        "WEB_PLAYER" -> {
                            WebPlayerScreen(modifier = Modifier.weight(1f))
                        }
                        "YOUTUBE" -> {
                            YouTubePage()
                        }
                        "MUSIC" -> {
                            Text("Your music library", color = Muted, fontSize = 13.sp)
                            if (currentIndex in songs.indices) {
                                Text("Now playing: ${songs[currentIndex].title}", color = Green, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(onClick = { showNowPlaying = true }, colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Black)) { Text("Open Player") }
                                    OutlinedButton(onClick = { if (isPlaying) { player.pause(); isPlaying = false } else if (currentIndex in songs.indices) { if (duration > 0) player.start() else playTrack(currentIndex); isPlaying = true } }) { Text(if (isPlaying) "Pause" else "Play") }
                                }
                            }
                            Button(onClick = { picker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Add songs from phone") }
                            if (songs.isEmpty()) Text("No songs added yet. Add MP3 files from your phone.", color = Muted)
                            songs.forEachIndexed { index, song ->
                                SmallLinkCard(song.title, if (index == currentIndex) "Now playing" else "Tap to play", Icons.Default.MusicNote) { playTrack(index) }
                            }
                        }
                        "DOWNLOADS" -> {
                            Text("Paste a direct HTTPS MP3, M4A, OGG, MP4 or WEBM file link that you are allowed to download.", color = Muted, fontSize = 12.sp)
                            OutlinedTextField(value = permittedFileUrl, onValueChange = { permittedFileUrl = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("https://example.com/song.mp3") })
                            Button(onClick = {
                                val uri = runCatching { Uri.parse(permittedFileUrl.trim()) }.getOrNull()
                                downloadMessage = if (uri != null) enqueuePermittedDownload(context, uri, uri.lastPathSegment ?: "media-file") else "Enter a valid media file link."
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Black)) { Icon(Icons.Default.Download, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Download file") }
                            if (downloadMessage.isNotBlank()) Text(downloadMessage, color = Green, fontSize = 12.sp)
                            OutlinedButton(onClick = { runCatching { context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) } }, modifier = Modifier.fillMaxWidth()) { Text("Open Android Download Manager") }
                            Text("THIRD-PARTY DOWNLOAD SOURCES", color = Green, fontSize = 13.sp)
                            OutlinedButton(onClick = { openLink(context, "https://archive.org/details/audio") }, modifier = Modifier.fillMaxWidth()) { Text("Internet Archive Audio Library") }
                            OutlinedButton(onClick = { openLink(context, "https://pixabay.com/music/") }, modifier = Modifier.fillMaxWidth()) { Text("Pixabay Music Downloads") }
                            OutlinedButton(
                                onClick = {
                                    val raw = permittedFileUrl.trim()
                                    val uri = runCatching { Uri.parse(raw) }.getOrNull()
                                    if (uri != null && uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank()) {
                                        val share = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, raw) }
                                        runCatching { context.startActivity(Intent.createChooser(share, "Open link with another app")) }
                                    } else downloadMessage = "Paste a valid website or media URL first."
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Send link to another downloader app") }
                            Text("Use third-party providers only for files you are allowed to save. YouTube links open in YouTube; this app does not extract YouTube streams.", color = Muted, fontSize = 11.sp)
                        }
                        "EQUALIZER" -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(26.dp),
                                colors = CardDefaults.cardColors(containerColor = Panel),
                                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().background(
                                        Brush.linearGradient(listOf(Color(0xFF153C35), Color(0xFF142E50), Color(0xFF321C48)))
                                    ).padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Green, Blue, Purple))),
                                            contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Equalizer, contentDescription = null, tint = White, modifier = Modifier.size(28.dp))
                                        }
                                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                            Text("STUDIO SOUND ENGINE", color = Green, fontSize = 10.sp, letterSpacing = 1.8.sp)
                                            Text("Premium Equalizer", color = White, fontSize = 21.sp, fontWeight = FontWeight.Black)
                                        }
                                        Text("PRO", color = White, fontSize = 11.sp,
                                            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Purple, Red))).padding(horizontal = 11.dp, vertical = 6.dp))
                                    }
                                    Text(if (currentIndex in songs.indices) "NOW TUNING • ${songs[currentIndex].title}" else "Play a local song to unlock live sound controls.",
                                        color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("SOUND PRESETS", color = White, fontSize = 12.sp, letterSpacing = 1.2.sp)
                                    if (eqPresetNames.isEmpty()) {
                                        Text("No presets available yet. Start a song and check your device audio-effect support.", color = Muted, fontSize = 12.sp)
                                    } else {
                                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                            eqPresetNames.forEachIndexed { index, preset ->
                                                FilterChip(
                                                    selected = selectedEqPreset == index,
                                                    onClick = {
                                                        runCatching {
                                                            equalizer?.usePreset(index.toShort())
                                                            selectedEqPreset = index
                                                            eqBandLevels = (0 until (equalizer?.numberOfBands?.toInt() ?: 0)).map { equalizer?.getBandLevel(it.toShort())?.toFloat() ?: 0f }
                                                        }
                                                    },
                                                    label = { Text(preset, fontWeight = FontWeight.Bold) },
                                                    modifier = Modifier.padding(end = 7.dp),
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = Purple, selectedLabelColor = White,
                                                        containerColor = PanelLight, labelColor = White
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    Divider(color = White.copy(alpha = .14f))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("MANUAL 5-BAND / DEVICE EQ", color = White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                        Text("LIVE", color = Green, fontSize = 10.sp)
                                    }
                                    val activeEq = equalizer
                                    val range = runCatching { activeEq?.bandLevelRange }.getOrNull()
                                    if (activeEq != null && range != null && eqBandLevels.isNotEmpty()) {
                                        eqBandLevels.forEachIndexed { index, level ->
                                            val hz = runCatching { activeEq.getCenterFreq(index.toShort()) / 1000 }.getOrDefault(0)
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(if (hz >= 1000) "${hz / 1000f} kHz" else "${hz} Hz",
                                                        color = White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                                    Text(String.format("%+.1f dB", level / 100f), color = Green, fontSize = 11.sp)
                                                }
                                                Slider(
                                                    value = level.coerceIn(range[0].toFloat(), range[1].toFloat()),
                                                    onValueChange = { value ->
                                                        runCatching {
                                                            activeEq.setBandLevel(index.toShort(), value.toInt().toShort())
                                                            eqBandLevels = eqBandLevels.toMutableList().also { it[index] = value }
                                                            selectedEqPreset = -1
                                                        }
                                                    },
                                                    valueRange = range[0].toFloat()..range[1].toFloat(),
                                                    colors = SliderDefaults.colors(thumbColor = Purple, activeTrackColor = Green, inactiveTrackColor = PanelLight)
                                                )
                                            }
                                        }
                                    } else {
                                        Text("Manual frequency sliders will appear when a track is playing.", color = Muted, fontSize = 11.sp)
                                    }
                                    Divider(color = White.copy(alpha = .14f))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("BASS BOOST", color = White, fontSize = 13.sp)
                                            Text("Deeper low-end punch", color = Muted, fontSize = 10.sp)
                                        }
                                        Switch(checked = bassBoostEnabled, onCheckedChange = { enabled ->
                                            bassBoostEnabled = enabled
                                            runCatching { bassBoost?.enabled = enabled }
                                        }, colors = SwitchDefaults.colors(checkedThumbColor = White, checkedTrackColor = Green))
                                    }
                                    Button(
                                        onClick = {
                                            runCatching {
                                                equalizer?.let { eq ->
                                                    eq.usePreset(0)
                                                    selectedEqPreset = 0
                                                    eqBandLevels = (0 until eq.numberOfBands.toInt()).map { eq.getBandLevel(it.toShort()).toFloat() }
                                                }
                                                bassBoostEnabled = false
                                                bassBoost?.enabled = false
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Black)
                                    ) {
                                        Icon(Icons.Default.RestartAlt, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("RESET SOUND")
                                    }
                                }
                            }
                            Text("Equalizer capability and sound output depend on your Android phone and audio hardware.", color = Muted, fontSize = 10.sp)
                        }
                        "STUDIO" -> {
                            Text("STUDIO MASTER", color = Green, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            Text("Your separate recording and audio-mixing workspace.", color = Muted, fontSize = 13.sp)
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Mic, contentDescription = null, tint = Green)
                                        Spacer(Modifier.width(10.dp))
                                        Text("Voice Recording", color = White, fontWeight = FontWeight.Bold)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Blue)
                                        Spacer(Modifier.width(10.dp))
                                        Text("Audio Mixer", color = White, fontWeight = FontWeight.Bold)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Tune, contentDescription = null, tint = Red)
                                        Spacer(Modifier.width(10.dp))
                                        Text("Vocal Effects • Echo • Reverb", color = White, fontWeight = FontWeight.Bold)
                                    }
                                    Text("Studio controls are a separate workspace. Recording and export controls will be enabled in a later update.", color = Muted, fontSize = 12.sp)
                                }
                            }
                        }
                        "AI" -> {
                            Text("AI Assistant page", color = Green, fontWeight = FontWeight.Bold)
                            Text("Assistant chat is opening. If it is not visible, tap the button below.", color = Muted, fontSize = 12.sp)
                            Button(onClick = { showAssistant = true }, modifier = Modifier.fillMaxWidth()) { Text("Open AI Assistant") }
                        }
                        "DJ" -> {
                            Text("DJ Toolkit page", color = Green, fontWeight = FontWeight.Bold)
                            Text("DJ tools are available in the dedicated toolkit panel.", color = Muted, fontSize = 12.sp)
                            Button(onClick = { showDjToolkit = true }, modifier = Modifier.fillMaxWidth()) { Text("Open DJ Toolkit") }
                        }
                        "SETTINGS" -> {
                            Text("App settings and update controls", color = Muted)
                            Button(onClick = { showSettings = true }, modifier = Modifier.fillMaxWidth()) { Text("Open Settings") }
                        }
                        "SHARED" -> {
                            Text(sharedYoutubeUrl ?: "No shared YouTube link received.", color = White, fontSize = 13.sp)
                            Button(onClick = { sharedYoutubeUrl?.let { openLink(context, it) } }, enabled = !sharedYoutubeUrl.isNullOrBlank(), modifier = Modifier.fillMaxWidth()) { Text("Open YouTube") }
                        }
                    }
                }
            )
        }
        if (showAssistant) {
            AssistantDialog(onDismiss = { showAssistant = false })
        }
        if (showDjToolkit) {
            DjToolkitDialog(player = player, isPlaying = isPlaying, onDismiss = { showDjToolkit = false })
        }
        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                title = { Text("MR. PERFECT YADAV Settings", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Music Player Settings", color = Red, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Auto-play next song", modifier = Modifier.weight(1f))
                            Switch(checked = autoPlayNext, onCheckedChange = { autoPlayNext = it })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Shuffle playback", modifier = Modifier.weight(1f))
                            Switch(checked = shuffleEnabled, onCheckedChange = { shuffleEnabled = it })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Repeat current track", modifier = Modifier.weight(1f))
                            Switch(checked = repeatCurrent, onCheckedChange = { repeatCurrent = it })
                        }
                        Text("Audio playback speed: ${String.format("%.2f", playbackSpeed)}x")
                        Slider(value = playbackSpeed, onValueChange = { speed ->
                            playbackSpeed = speed
                            runCatching { if (duration > 0) player.playbackParams = player.playbackParams.setSpeed(speed) }
                        }, valueRange = 0.5f..2f)
                        Text("Equalizer and Bass Boost", color = Red, fontWeight = FontWeight.Bold)
                        Text("Use the Audio Equalizer section on the home screen. Bass Boost works when the phone audio effect is supported.", fontSize = 12.sp)
                        Text("Playback notification includes Previous, Play/Pause and Next controls. Lock-screen display depends on Android device settings and notification permission.", fontSize = 12.sp, color = Muted)
                        Text("Free online search is limited to tracks marked Creative Commons / Public Domain. A commercial licensed catalog needs a provider account and API credentials before it can be enabled.", fontSize = 12.sp, color = Muted)
                        Text("Phone MP3 files play locally in full. Background playback reliability still needs a dedicated foreground playback service and device testing.", fontSize = 12.sp, color = Muted)
                        Divider(color = PanelLight)
                        Text("App Updates", color = Red, fontWeight = FontWeight.Bold)
                        Text(updateMessage, fontSize = 12.sp, color = Muted)
                        Button(
                            onClick = {
                                if (!checkingUpdates) {
                                    checkingUpdates = true
                                    updateMessage = "Checking for updates…"
                                    updateUrl = null
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            runCatching {
                                                val connection = (URL("https://api.github.com/repos/Mrperfectyadav007/Mr-perfect-yadav-_07/releases/latest").openConnection() as HttpURLConnection)
                                                connection.connectTimeout = 10000
                                                connection.readTimeout = 10000
                                                connection.setRequestProperty("Accept", "application/vnd.github+json")
                                                try {
                                                    if (connection.responseCode != 200) {
                                                        "NO_RELEASE" to null
                                                    } else {
                                                        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                                                        json.optString("tag_name", "latest") to json.optString("html_url").takeIf { it.startsWith("https://") }
                                                    }
                                                } finally { connection.disconnect() }
                                            }
                                        }
                                        checkingUpdates = false
                                        result.onSuccess { (tag, url) ->
                                            if (tag == "NO_RELEASE" || url == null) {
                                                updateMessage = "No published update is available yet."
                                            } else {
                                                updateMessage = "Latest release: $tag. Open the release page to download and install."
                                                updateUrl = url
                                            }
                                        }.onFailure {
                                            updateMessage = "Could not check updates. Please try again later."
                                        }
                                    }
                                }
                            },
                            enabled = !checkingUpdates,
                            colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = White)
                        ) {
                            if (checkingUpdates) CircularProgressIndicator(
                                modifier = Modifier.size(16.dp), color = White, strokeWidth = 2.dp
                            ) else Icon(Icons.Default.SystemUpdate, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (checkingUpdates) "Checking…" else "Check for Updates")
                        }
                        if (updateUrl != null) {
                            TextButton(onClick = { openLink(context, updateUrl!!) }) {
                                Text("Open APK Update", color = Green)
                            }
                        }
                        Text("About MR. PERFECT YADAV", color = Red, fontWeight = FontWeight.Bold)
                        Text("Premium red-and-black music player", fontSize = 12.sp)
                    }
                },
                confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done") } },
                containerColor = Panel,
                titleContentColor = White,
                textContentColor = White
            )
        }
    }
}
}


@Composable
private fun FeaturePage(
    page: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val title = when (page) {
        "MUSIC" -> "MUSIC PLAYER"
        "WEB_PLAYER" -> "WEB MUSIC PLAYER"
        "YOUTUBE" -> "YOUTUBE"
        "DOWNLOADS" -> "DOWNLOAD MANAGER"
        "EQUALIZER" -> "EQUALIZER"
        "DJ" -> "DJ TOOLKIT"
        "AI" -> "AI ASSISTANT"
        "SETTINGS" -> "SETTINGS"
        "SHARED" -> "SHARED LINK"
        else -> "MR. PERFECT YADAV"
    }
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF07120F), Color(0xFF0A1930), Color(0xFF24101A))))
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back to Home", tint = White) }
                Text(title, color = Green, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("HOME", color = Red, fontWeight = FontWeight.Bold) }
            }
            Divider(color = PanelLight)
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.fillMaxSize().padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
private fun PremiumPlaybackButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    size: androidx.compose.ui.unit.Dp,
    colors: List<Color>,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(colors))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) White else White.copy(alpha = 0.35f),
            modifier = Modifier.size(if (size >= 56.dp) 31.dp else 25.dp)
        )
    }
}

@Composable
private fun SmallLinkCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Red, modifier = Modifier.size(25.dp))
            Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                Text(title, color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subtitle, color = Muted, fontSize = 11.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Blue)
        }
    }
}



private object PlaybackBridge {
    var onPlay: (() -> Unit)? = null
    var onPause: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onPrevious: (() -> Unit)? = null
}

private object NotificationController {
    const val CHANNEL_ID = "mr_perfect_music_playback_v2"
    private const val NOTIFICATION_ID = 701
    var sessionToken: MediaSessionCompat.Token? = null

    fun show(context: Context, title: String, isPlaying: Boolean) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val previous = actionIntent(context, "previous", 1)
        val toggle = actionIntent(context, if (isPlaying) "pause" else "play", 2)
        val next = actionIntent(context, "next", 3)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle(title)
            .setContentText(if (isPlaying) "NOW PLAYING  •  MR. PERFECT YADAV" else "PAUSED  •  MR. PERFECT YADAV")
            .setSubText("MR. PERFECT YADAV MUSIC  •  PREMIUM PLAYER")
            .setTicker("MR. PERFECT YADAV MUSIC")
            .setColor(android.graphics.Color.rgb(155, 92, 255))
            .setColorized(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Previous", previous)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                toggle
            )
            .addAction(android.R.drawable.ic_media_next, "Next", next)
            .setStyle(
                MediaStyle()
                    .setMediaSession(sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }

    private fun actionIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MusicActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class MusicActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            "play" -> PlaybackBridge.onPlay?.invoke()
            "pause" -> PlaybackBridge.onPause?.invoke()
            "next" -> PlaybackBridge.onNext?.invoke()
            "previous" -> PlaybackBridge.onPrevious?.invoke()
        }
    }
}

private fun enqueuePermittedDownload(context: Context, uri: Uri, title: String): String {
    if (uri.scheme != "https" || uri.host.isNullOrBlank()) {
        return "Please use a valid HTTPS direct file URL."
    }
    return runCatching {
        val extension = uri.path?.substringAfterLast(".", "")?.lowercase()
            ?.takeIf { it in setOf("mp3", "m4a", "ogg", "mp4", "webm") }
            ?: return "URL must directly point to an MP3, M4A, OGG, MP4 or WEBM file."
        val safeName = title.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(70).ifBlank { "media-file" } + ".$extension"
        val request = DownloadManager.Request(uri)
            .setTitle(safeName)
            .setDescription("MR. PERFECT YADAV Music — download only files you are authorized to use")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        "Download started. Check your Downloads folder."
    }.getOrElse { "Could not start download: ${it.message ?: "please try again"}" }
}

private fun openLink(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}


@Composable
private fun YouTubePage() {
    val context = LocalContext.current
    var pageError by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "YouTube opens here as a separate page. Sign in only if you trust this device. Ads and playback are controlled by YouTube; this page does not remove ads.",
            color = Muted,
            fontSize = 11.sp
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { openLink(context, "https://www.youtube.com/") }, modifier = Modifier.weight(1f)) {
                Text("Open in YouTube app")
            }
            OutlinedButton(onClick = { openLink(context, "https://music.youtube.com/") }, modifier = Modifier.weight(1f)) {
                Text("YouTube Music")
            }
        }
        if (pageError) {
            Text("YouTube page could not load. Check your internet connection or use the buttons above.", color = Red, fontSize = 12.sp)
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = true
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView,
                            request: android.webkit.WebResourceRequest,
                            error: android.webkit.WebResourceError
                        ) {
                            if (request.isForMainFrame) pageError = true
                        }
                        override fun onPageFinished(view: WebView, url: String) {
                            pageError = false
                        }
                    }
                    webChromeClient = WebChromeClient()
                    loadUrl("https://m.youtube.com/")
                }
            },
            update = { view ->
                if (view.url.isNullOrBlank()) view.loadUrl("https://m.youtube.com/")
            }
        )
    }
}
