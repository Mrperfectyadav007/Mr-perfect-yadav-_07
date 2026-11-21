package com.mrperfectyadav.app

import android.content.Intent
import androidx.core.content.ContextCompat
import android.content.BroadcastReceiver
import android.content.Context
import android.app.DownloadManager
import android.os.Environment
import android.widget.Toast
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.media.audiofx.BassBoost
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.provider.OpenableColumns
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

private val Black = Color(0xFF080808)
private val Panel = Color(0xFF181818)
private val PanelLight = Color(0xFF242424)
private val Red = Color(0xFFFF3030)
private val White = Color(0xFFF7F7F7)
private val Muted = Color(0xFFB8B8B8)

private data class LocalSong(val uri: Uri, val title: String)

class MainActivity : ComponentActivity() {
    private var mediaSession: MediaSessionCompat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Playback controls for MR. PERFECT YADAV Music" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

@Composable
fun MrPerfectApp() {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var onlineSongs by remember { mutableStateOf(listOf<LocalSong>()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchMessage by remember { mutableStateOf("Search for a song, artist or album.") }
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
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var showSettings by remember { mutableStateOf(false) }
    var eqPresetNames by remember { mutableStateOf(listOf<String>()) }
    var selectedEqPreset by remember { mutableIntStateOf(-1) }
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
                }.onFailure {
                    equalizer = null
                    eqPresetNames = emptyList()
                    selectedEqPreset = -1
                }
                isPreparing = false
                mp.start()
                isPlaying = true
            }
            player.setOnCompletionListener {
                if (autoPlayNext && currentIndex + 1 < songs.size) playTrack(currentIndex + 1)
                else { isPlaying = false; position = 0 }
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
        primary = Red, background = Black, surface = Panel,
        onPrimary = White, onBackground = White, onSurface = White
    )) {
        BackHandler(enabled = showSettings || onlineSongs.isNotEmpty() || searchQuery.isNotBlank()) {
            when {
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
                        listOf(Color(0xB8080808), Color(0xD9080808), Color(0xF2080808))
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
                            Text("MR. PERFECT YADAV", color = Red, fontSize = 12.sp,
                                fontWeight = FontWeight.Black, letterSpacing = 2.5.sp)
                            Text("Good music. Good mood.", color = White, fontSize = 25.sp,
                                fontWeight = FontWeight.ExtraBold)
                        }
                        Image(
                            painter = painterResource(id = R.drawable.mr_perfect_dp),
                            contentDescription = "MR. PERFECT YADAV profile photo",
                            modifier = Modifier.size(54.dp).clip(CircleShape)
                        )
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Red)
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
                                Brush.linearGradient(listOf(Color(0xFF9E1515), Color(0xFF3A0808), Color(0xFF171717)))
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
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Panel)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = Red)
                                Spacer(Modifier.width(8.dp))
                                Text("Licensed music catalog", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            }
                            Text(
                                "Commercial full-track catalog integration is ready for a licensed provider, but no provider account or API credentials are connected yet. Only tracks authorized by that provider can be streamed.",
                                color = Muted, fontSize = 12.sp
                            )
                            Divider(color = PanelLight)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Gavel, contentDescription = null, tint = Red)
                                Spacer(Modifier.width(8.dp))
                                Text("Free legal full-length music", color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Text(
                                "Search below for tracks marked Creative Commons or Public Domain. Check each track's license and attribution terms before reuse or sharing.",
                                color = Muted, fontSize = 12.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Red)
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
                            focusedBorderColor = Red, unfocusedBorderColor = PanelLight,
                            focusedLabelColor = Red, cursorColor = Red
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
                                            val archiveQuery = "mediatype:audio AND licenseurl:http* AND title:\"$safeQuery\""
                                            val encoded = URLEncoder.encode(archiveQuery, "UTF-8")
                                            val searchUrl = URL("https://archive.org/advancedsearch.php?q=$encoded&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=licenseurl&rows=40&page=1&sort[]=downloads+desc&output=json")
                                            val connection = (searchUrl.openConnection() as HttpURLConnection)
                                            connection.connectTimeout = 15000
                                            connection.readTimeout = 20000
                                            connection.requestMethod = "GET"
                                            try {
                                                val body = connection.inputStream.bufferedReader().use { it.readText() }
                                                val docs = JSONObject(body).getJSONObject("response").getJSONArray("docs")
                                                buildList {
                                                    for (i in 0 until docs.length()) {
                                                        val doc = docs.getJSONObject(i)
                                                        val identifier = doc.optString("identifier")
                                                        val license = doc.optString("licenseurl").lowercase()
                                                        if (identifier.isBlank() || !(license.contains("creativecommons.org") || license.contains("publicdomain"))) continue
                                                        val metadataUrl = URL("https://archive.org/metadata/${Uri.encode(identifier)}")
                                                        val metaConnection = (metadataUrl.openConnection() as HttpURLConnection)
                                                        metaConnection.connectTimeout = 10000
                                                        metaConnection.readTimeout = 12000
                                                        try {
                                                            val metaText = metaConnection.inputStream.bufferedReader().use { it.readText() }
                                                            val files = JSONObject(metaText).optJSONArray("files") ?: continue
                                                            var audioFile: String? = null
                                                            for (j in 0 until files.length()) {
                                                                val file = files.optJSONObject(j) ?: continue
                                                                val name = file.optString("name")
                                                                val format = file.optString("format")
                                                                if (name.lowercase().endsWith(".mp3") &&
                                                                    (format.contains("mp3", true) || format.contains("audio", true))) {
                                                                    audioFile = name
                                                                    break
                                                                }
                                                            }
                                                            if (audioFile != null) {
                                                                val title = doc.optString("title", "Untitled audio")
                                                                val creator = doc.optString("creator", "Unknown artist")
                                                                val streamUrl = "https://archive.org/download/${Uri.encode(identifier)}/${Uri.encode(audioFile!!)}"
                                                                add(LocalSong(Uri.parse(streamUrl), "$title — $creator"))
                                                            }
                                                        } finally { metaConnection.disconnect() }
                                                    }
                                                }
                                            } finally { connection.disconnect() }
                                        }.getOrDefault(emptyList())
                                    }
                                    onlineSongs = found
                                    songs = (songs + found).distinctBy { it.uri }
                                    searchMessage = if (found.isEmpty()) "No licensed full tracks found. Try another title or artist." else "Full-length Creative Commons / public-domain tracks found. Tap to stream in this app."
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
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Red)
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
                            IconButton(onClick = { downloadFullTrack(context, song) }) {
                                Icon(Icons.Default.Download, contentDescription = "Download full track", tint = White)
                            }
                        }
                    }
                    Text("Play or download full-length tracks. Download is offered for the Creative Commons / public-domain results shown here.", color = Muted, fontSize = 10.sp)
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Panel)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Equalizer, contentDescription = null, tint = Red)
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
                            }
                            Text("Dolby Atmos is available only on supported phones; this uses Android's built-in equalizer.", color = Muted, fontSize = 10.sp)
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
                                Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = Red, modifier = Modifier.size(38.dp))
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
                                if (index == currentIndex) Color(0xFF351010) else Panel,
                                RoundedCornerShape(14.dp)
                            ).clickable { playTrack(index) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(44.dp).background(PanelLight, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Red)
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
                Column(Modifier.fillMaxWidth().background(Color(0xFF202020)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Red, modifier = Modifier.size(30.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(songs[currentIndex].title, color = White, fontSize = 13.sp,
                                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (isPreparing) "Loading..." else "MR. PERFECT YADAV MUSIC",
                                color = Muted, fontSize = 10.sp)
                        }
                        IconButton(onClick = { if (currentIndex > 0) playTrack(currentIndex - 1) }) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = White)
                        }
                        IconButton(onClick = {
                            if (isPreparing) return@IconButton
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
                        }) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play pause", tint = Red, modifier = Modifier.size(30.dp))
                        }
                        IconButton(onClick = { if (currentIndex + 1 < songs.size) playTrack(currentIndex + 1) }) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = White)
                        }
                    }
                    if (duration > 0) {
                        Slider(
                            value = position.coerceIn(0, duration).toFloat(),
                            onValueChange = { position = it.toInt() },
                            onValueChangeFinished = { runCatching { player.seekTo(position) } },
                            valueRange = 0f..duration.toFloat(),
                            modifier = Modifier.height(22.dp),
                            colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red)
                        )
                    }
                }
            }
            }
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
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Red)
        }
    }
}



private fun downloadFullTrack(context: Context, song: LocalSong) {
    runCatching {
        val request = DownloadManager.Request(song.uri)
            .setTitle(song.title)
            .setDescription("Downloading to Music — MR. PERFECT YADAV")
            .setMimeType("audio/mpeg")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val safeName = song.title
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .trim()
            .take(90)
            .ifBlank { "Mr_Perfect_Yadav_Track" }
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "$safeName.mp3")
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        Toast.makeText(context, "Download started. Check your Music folder.", Toast.LENGTH_LONG).show()
    }.onFailure {
        Toast.makeText(context, "Download could not start: ${it.localizedMessage ?: "unknown error"}", Toast.LENGTH_LONG).show()
    }
}

private object PlaybackBridge {
    var onPlay: (() -> Unit)? = null
    var onPause: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onPrevious: (() -> Unit)? = null
}

private object NotificationController {
    const val CHANNEL_ID = "mr_perfect_music_playback"
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
            .setContentText("MR. PERFECT YADAV MUSIC")
            .setSubText("Full-track player")
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

private fun openLink(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
