package com.mrperfectyadav.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val FeatureRed = Color(0xFFFF3030)
private val FeaturePanel = Color(0xFF181818)
private val FeatureWhite = Color(0xFFF7F7F7)
private val FeatureMuted = Color(0xFFB8B8B8)

/**
 * Local library features. Favorites, named playlists and listening history are
 * persisted on-device; no account or network service is required.
 */
@Composable
fun LibraryFeaturePanel(trackTitles: List<String>, currentIndex: Int) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("mr_perfect_library", android.content.Context.MODE_PRIVATE) }
    var favorites by remember {
        mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet())
    }
    var playlistNames by remember {
        mutableStateOf(prefs.getStringSet("playlist_names", emptySet())?.toSet() ?: emptySet())
    }
    var history by remember {
        mutableStateOf(prefs.getStringSet("recent_history", emptySet())?.toList()?.sortedDescending() ?: emptyList())
    }
    var playlistName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var showPrivacy by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    val currentTitle = trackTitles.getOrNull(currentIndex)
    LaunchedEffect(currentTitle) {
        if (!currentTitle.isNullOrBlank()) {
            val updated = (listOf("${System.currentTimeMillis()}|$currentTitle") +
                history.filterNot { it.substringAfter("|") == currentTitle }).take(30)
            history = updated
            prefs.edit().putStringSet("recent_history", updated.toSet()).apply()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = FeaturePanel)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Your Music Library", color = FeatureWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                currentTitle ?: "Play a song to use Favorites and Playlists.",
                color = FeatureMuted, fontSize = 12.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val title = currentTitle ?: return@Button
                        val updated = favorites.toMutableSet()
                        if (!updated.add(title)) updated.remove(title)
                        favorites = updated
                        prefs.edit().putStringSet("favorites", updated).apply()
                        message = if (title in updated) "Added to Favorites" else "Removed from Favorites"
                    },
                    enabled = currentTitle != null
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null)
                    Text(if (currentTitle in favorites) "Saved" else "Favorite")
                }
                Text("Favorites: ${favorites.size}", color = FeatureMuted, modifier = Modifier.padding(top = 12.dp))
            }
            OutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("New playlist name") }
            )
            Button(
                onClick = {
                    val title = currentTitle
                    val name = playlistName.trim()
                    if (title == null) message = "Play a track first."
                    else if (name.isBlank()) message = "Enter a playlist name."
                    else {
                        val names = playlistNames.toMutableSet().apply { add(name) }
                        val tracks = prefs.getStringSet("playlist:$name", emptySet())?.toMutableSet() ?: mutableSetOf()
                        tracks.add(title)
                        playlistNames = names
                        prefs.edit()
                            .putStringSet("playlist_names", names)
                            .putStringSet("playlist:$name", tracks)
                            .apply()
                        playlistName = ""
                        message = "Added current track to $name"
                    }
                },
                enabled = currentTitle != null
            ) {
                Icon(Icons.Default.PlaylistAdd, contentDescription = null)
                Text("Add current song to playlist")
            }
            Text("Playlists: ${playlistNames.sorted().joinToString().ifBlank { "None yet" }}", color = FeatureMuted, fontSize = 12.sp)
            Row {
                Icon(Icons.Default.History, contentDescription = null, tint = FeatureRed)
                Text(" Recently played: ${history.size} tracks", color = FeatureMuted, fontSize = 12.sp)
            }
            if (message.isNotBlank()) Text(message, color = FeatureRed, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { showPrivacy = true }) { Text("Privacy") }
                TextButton(onClick = { showAbout = true }) { Text("About") }
            }
        }
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("Privacy") },
            text = { Text("Favorites, playlist names, playlist entries and recent listening history are stored locally on this device. This feature panel does not upload your library.") },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("OK") } }
        )
    }
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("MR. PERFECT YADAV Music") },
            text = { Text("A red-and-black music player for local audio and legally available online tracks. Commercial catalogue access, AI assistant and paid membership require separate provider/backend setup.") },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("OK") } }
        )
    }
}
