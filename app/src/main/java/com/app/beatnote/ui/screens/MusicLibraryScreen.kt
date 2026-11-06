package com.app.beatnote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.app.beatnote.data.PlaylistEntity
import com.app.beatnote.model.Song
import com.app.beatnote.ui.LibraryTab
import com.app.beatnote.ui.theme.CharcoalDark
import com.app.beatnote.ui.theme.CharcoalMedium
import com.app.beatnote.ui.theme.SubtleGrey
import com.app.beatnote.ui.theme.White
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun MusicLibraryScreen(
    songs: List<Song>,
    pinnedSongs: List<Song>,
    libraryTab: LibraryTab,
    playlists: List<PlaylistEntity>,
    selectedPlaylistId: Long?,
    playlistSongUris: List<String>,
    libraryActionError: String?,
    selectedSong: Song?,
    selectedSongId: String?,
    onSongSelected: (Song, List<Song>) -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean,
    permissionGranted: Boolean,
    errorMessage: String?,
    onRequestPermission: () -> Unit,
    isPlaying: Boolean,
    currentPositionMs: Long,
    totalDurationMs: Long,
    onPlayPrevious: () -> Unit,
    onSeekBackward: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekForward: () -> Unit,
    onPlayNext: () -> Unit,
    onOpenStudio: () -> Unit,
    onLibraryTabSelected: (LibraryTab) -> Unit,
    onTogglePin: (Song) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (PlaylistEntity) -> Unit,
    onSelectPlaylist: (PlaylistEntity?) -> Unit,
    onAddSongToPlaylist: (PlaylistEntity, Song) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }
    val filteredSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) {
            songs
        } else {
            songs.filter { song ->
                song.title.contains(searchQuery, ignoreCase = true) ||
                    song.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    val filteredPinnedSongs = remember(pinnedSongs, searchQuery) {
        if (searchQuery.isBlank()) pinnedSongs else pinnedSongs.filter { song ->
            song.title.contains(searchQuery, ignoreCase = true) ||
                song.artist.contains(searchQuery, ignoreCase = true)
        }
    }
    val filteredPlaylistSongs = remember(playlistSongUris, songs, searchQuery) {
        playlistSongUris.mapNotNull { uri -> songs.firstOrNull { it.id == uri } }
            .filter { song ->
                searchQuery.isBlank() ||
                    song.title.contains(searchQuery, ignoreCase = true) ||
                    song.artist.contains(searchQuery, ignoreCase = true)
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            if (permissionGranted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text("Search beats or songs...", color = SubtleGrey)
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = SubtleGrey)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search", tint = SubtleGrey)
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CharcoalDark,
                            unfocusedContainerColor = CharcoalDark,
                            focusedBorderColor = CharcoalMedium,
                            unfocusedBorderColor = CharcoalMedium,
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            cursorColor = White
                        )
                    )
                    IconButton(onClick = onRefresh, enabled = !isLoading) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh music library", tint = White)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141418), RoundedCornerShape(18.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        LibraryTab.PINNED to "Pin",
                        LibraryTab.SONGS to "Song",
                        LibraryTab.PLAYLISTS to "Playlist"
                    ).forEach { (tab, label) ->
                        val selected = libraryTab == tab
                        TextButton(
                            onClick = { onLibraryTabSelected(tab) },
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (selected) CharcoalMedium else Color.Transparent,
                                    RoundedCornerShape(14.dp)
                                )
                        ) {
                            Text(
                                text = label,
                                color = if (selected) White else SubtleGrey,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            when {
                !permissionGranted -> {
                    Box(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        ) {
                            Text("Audio Permission Required", style = MaterialTheme.typography.titleMedium, color = White)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("BEATNOTE needs permission to read audio files on this device.", style = MaterialTheme.typography.bodyMedium, color = SubtleGrey)
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = onRequestPermission, colors = ButtonDefaults.buttonColors(containerColor = CharcoalMedium)) {
                                Text("Grant Permission", color = White)
                            }
                        }
                    }
                }
                songs.isEmpty() && isLoading && libraryTab != LibraryTab.PLAYLISTS -> {
                    Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = White)
                    }
                }
                songs.isEmpty() && errorMessage != null && libraryTab != LibraryTab.PLAYLISTS -> {
                    Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(errorMessage, style = MaterialTheme.typography.bodyMedium, color = SubtleGrey)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onRefresh, colors = ButtonDefaults.buttonColors(containerColor = CharcoalMedium)) {
                                Text("Try Again", color = White)
                            }
                        }
                    }
                }
                else -> {
                    when (libraryTab) {
                        LibraryTab.SONGS -> {
                            when {
                                songs.isEmpty() -> LibraryEmptyState(
                                    "No music found",
                                    "No audio files were found.",
                                    Modifier.weight(1f)
                                )
                                filteredSongs.isEmpty() -> LibraryEmptyState(
                                    "No matching songs found",
                                    modifier = Modifier.weight(1f)
                                )
                                else -> SongList(
                                    songs = filteredSongs,
                                    selectedSongId = selectedSongId,
                                    pinnedSongIds = pinnedSongs.mapTo(mutableSetOf()) { it.id },
                                    onSongSelected = { song -> onSongSelected(song, filteredSongs) },
                                    onTogglePin = onTogglePin,
                                    onAddToPlaylist = { songToAddToPlaylist = it }
                                )
                            }
                        }
                        LibraryTab.PINNED -> {
                            if (filteredPinnedSongs.isEmpty()) {
                                LibraryEmptyState(
                                    if (pinnedSongs.isEmpty()) "No pinned songs" else "No matching songs found",
                                    if (pinnedSongs.isEmpty()) "Pin songs to keep them easy to find." else null,
                                    Modifier.weight(1f)
                                )
                            } else {
                                SongList(
                                    songs = filteredPinnedSongs,
                                    selectedSongId = selectedSongId,
                                    pinnedSongIds = pinnedSongs.mapTo(mutableSetOf()) { it.id },
                                    onSongSelected = { song ->
                                        onSongSelected(song, filteredPinnedSongs)
                                    },
                                    onTogglePin = onTogglePin,
                                    onAddToPlaylist = { songToAddToPlaylist = it }
                                )
                            }
                        }
                        LibraryTab.PLAYLISTS -> {
                            PlaylistContent(
                                playlists = playlists,
                                selectedPlaylistId = selectedPlaylistId,
                                playlistSongs = filteredPlaylistSongs,
                                selectedSongId = selectedSongId,
                                pinnedSongIds = pinnedSongs.mapTo(mutableSetOf()) { it.id },
                                actionError = libraryActionError,
                                onCreatePlaylist = { showCreatePlaylistDialog = true },
                                onSelectPlaylist = onSelectPlaylist,
                                onDeletePlaylist = onDeletePlaylist,
                                onSongSelected = { song, playlistSongs ->
                                    onSongSelected(song, playlistSongs)
                                },
                                onTogglePin = onTogglePin,
                                onAddToPlaylist = { songToAddToPlaylist = it }
                            )
                        }
                    }
                }
            }
        }

        if (showCreatePlaylistDialog) {
            AlertDialog(
                onDismissRequest = {
                    showCreatePlaylistDialog = false
                    newPlaylistName = ""
                },
                title = { Text("New playlist", color = White) },
                text = {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        singleLine = true,
                        placeholder = { Text("Playlist name") }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onCreatePlaylist(newPlaylistName)
                            showCreatePlaylistDialog = false
                            newPlaylistName = ""
                        },
                        enabled = newPlaylistName.isNotBlank()
                    ) {
                        Text("Create", color = White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreatePlaylistDialog = false; newPlaylistName = "" }) {
                        Text("Cancel", color = SubtleGrey)
                    }
                },
                containerColor = Color(0xFF141418)
            )
        }

        songToAddToPlaylist?.let { song ->
            AlertDialog(
                onDismissRequest = { songToAddToPlaylist = null },
                title = { Text("Add to playlist", color = White) },
                text = {
                    if (playlists.isEmpty()) {
                        Text("Create a playlist first.", color = SubtleGrey)
                    } else {
                        Column {
                            playlists.forEach { playlist ->
                                TextButton(
                                    onClick = {
                                        onAddSongToPlaylist(playlist, song)
                                        songToAddToPlaylist = null
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        playlist.name,
                                        modifier = Modifier.fillMaxWidth(),
                                        color = White
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { songToAddToPlaylist = null }) {
                        Text("Close", color = SubtleGrey)
                    }
                },
                containerColor = Color(0xFF141418)
            )
        }

        if (selectedSong != null) {
            MiniPlayerBar(
                song = selectedSong,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                totalDurationMs = totalDurationMs,
                onPlayPrevious = onPlayPrevious,
                onSeekBackward = onSeekBackward,
                onTogglePlayback = onTogglePlayback,
                onSeekForward = onSeekForward,
                onPlayNext = onPlayNext,
                onOpenStudio = onOpenStudio,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun LibraryEmptyState(
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = SubtleGrey)
            if (message != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SubtleGrey.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.SongList(
    songs: List<Song>,
    selectedSongId: String?,
    pinnedSongIds: Set<String>,
    onSongSelected: (Song) -> Unit,
    onTogglePin: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(songs, key = { song -> song.id }) { song ->
            SongRow(
                song = song,
                selected = song.id == selectedSongId,
                isPinned = song.id in pinnedSongIds,
                onClick = { onSongSelected(song) },
                onTogglePin = { onTogglePin(song) },
                onAddToPlaylist = { onAddToPlaylist(song) }
            )
        }
    }
}

@Composable
private fun ColumnScope.PlaylistContent(
    playlists: List<PlaylistEntity>,
    selectedPlaylistId: Long?,
    playlistSongs: List<Song>,
    selectedSongId: String?,
    pinnedSongIds: Set<String>,
    actionError: String?,
    onCreatePlaylist: () -> Unit,
    onSelectPlaylist: (PlaylistEntity?) -> Unit,
    onDeletePlaylist: (PlaylistEntity) -> Unit,
    onSongSelected: (Song, List<Song>) -> Unit,
    onTogglePin: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    val selectedPlaylist = playlists.firstOrNull { it.id == selectedPlaylistId }
    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
        Button(
            onClick = onCreatePlaylist,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CharcoalMedium)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("+ New Playlist", color = White)
        }
        if (actionError != null) {
            Text(
                text = actionError,
                color = SubtleGrey,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (playlists.isEmpty()) {
                item {
                    Text(
                        "Your playlists will appear here.",
                        color = SubtleGrey,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
            items(playlists, key = { playlist -> "playlist-${playlist.id}" }) { playlist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (playlist.id == selectedPlaylistId) CharcoalMedium else CharcoalDark,
                            MaterialTheme.shapes.medium
                        )
                        .clickable { onSelectPlaylist(playlist) }
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = playlist.name,
                        modifier = Modifier.weight(1f),
                        color = if (playlist.id == selectedPlaylistId) White else SubtleGrey,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { onDeletePlaylist(playlist) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete ${playlist.name}",
                            tint = SubtleGrey
                        )
                    }
                }
            }
            if (selectedPlaylist != null) {
                item(key = "playlist-songs-title") {
                    Text(
                        text = "Songs in ${selectedPlaylist.name}",
                        color = White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                if (playlistSongs.isEmpty()) {
                    item(key = "playlist-empty") {
                        Text("This playlist is empty.", color = SubtleGrey)
                    }
                } else {
                    items(
                        playlistSongs,
                        key = { song -> "playlist-${selectedPlaylist.id}-song-${song.id}" }
                    ) { song ->
                        SongRow(
                            song = song,
                            selected = song.id == selectedSongId,
                            isPinned = song.id in pinnedSongIds,
                            onClick = { onSongSelected(song, playlistSongs) },
                            onTogglePin = { onTogglePin(song) },
                            onAddToPlaylist = { onAddToPlaylist(song) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerBar(
    song: Song,
    isPlaying: Boolean,
    currentPositionMs: Long,
    totalDurationMs: Long,
    onPlayPrevious: () -> Unit,
    onSeekBackward: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekForward: () -> Unit,
    onPlayNext: () -> Unit,
    onOpenStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val capsuleShape = RoundedCornerShape(22.dp)
    val progress = if (totalDurationMs > 0L) {
        (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(capsuleShape)
            .background(Color(0xFF141418))
            .border(1.dp, White.copy(alpha = 0.08f), capsuleShape)
            .clickable(onClick = onOpenStudio)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPlayPrevious, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous track", tint = White)
                }
                TextButton(
                    onClick = onSeekBackward,
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text("-5s", color = SubtleGrey, style = MaterialTheme.typography.labelLarge)
                }
                IconButton(
                    onClick = onTogglePlayback,
                    modifier = Modifier
                        .size(38.dp)
                        .background(White.copy(alpha = 0.12f), androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = White,
                        modifier = Modifier.size(21.dp)
                    )
                }
                TextButton(
                    onClick = onSeekForward,
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text("+5s", color = SubtleGrey, style = MaterialTheme.typography.labelLarge)
                }
                IconButton(onClick = onPlayNext, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next track", tint = White)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = SubtleGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(2.dp)
                .background(White.copy(alpha = 0.06f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(Color(0xFFB388FF))
            )
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    selected: Boolean,
    isPinned: Boolean = false,
    onClick: () -> Unit,
    onTogglePin: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) CharcoalMedium else CharcoalDark,
                shape = MaterialTheme.shapes.medium
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(CharcoalMedium, shape = MaterialTheme.shapes.small)
                .clip(MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = SubtleGrey)
            if (song.albumArtUri != null) {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleMedium, color = White, maxLines = 1)
            Spacer(modifier = Modifier.height(4.dp))
            Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = SubtleGrey, maxLines = 1)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(song.duration, style = MaterialTheme.typography.bodyMedium, color = SubtleGrey)
        if (onTogglePin != null) {
            IconButton(onClick = onTogglePin, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.PushPin,
                    contentDescription = if (isPinned) "Unpin ${song.title}" else "Pin ${song.title}",
                    tint = if (isPinned) White else SubtleGrey,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        if (onAddToPlaylist != null) {
            IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add ${song.title} to playlist",
                    tint = SubtleGrey,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
