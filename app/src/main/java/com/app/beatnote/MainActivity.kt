package com.app.beatnote

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.app.beatnote.data.MusicRepository
import com.app.beatnote.data.NotesRepository
import com.app.beatnote.ui.MainViewModel
import com.app.beatnote.ui.screens.MusicLibraryScreen
import com.app.beatnote.ui.screens.NotesScreen
import com.app.beatnote.ui.theme.BeatNoteTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(
            this,
            MainViewModelFactory(
                applicationContext,
                MusicRepository(applicationContext),
                NotesRepository(applicationContext)
            )
        )[MainViewModel::class.java]
        setContent {
            BeatNoteTheme {
                BeatNoteApp(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setPermissionGranted(
            ContextCompat.checkSelfPermission(this, requiredAudioPermission()) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    override fun onStop() {
        viewModel.flushNotes()
        super.onStop()
    }
}

private fun requiredAudioPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

private class MainViewModelFactory(
    private val context: android.content.Context,
    private val repository: MusicRepository,
    private val notesRepository: NotesRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(MainViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        return modelClass.cast(MainViewModel(context, repository, notesRepository))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BeatNoteApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val songs by viewModel.songs.collectAsState()
    val pinnedSongs by viewModel.pinnedSongs.collectAsState()
    val libraryTab by viewModel.activeLibraryTab.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsState()
    val playlistSongUris by viewModel.playlistSongUris.collectAsState()
    val libraryActionError by viewModel.libraryActionError.collectAsState()
    val selectedSong by viewModel.selectedSong.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val permissionGranted by viewModel.permissionGranted.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackError by viewModel.playbackError.collectAsState()
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val totalDurationMs by viewModel.totalDurationMs.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val currentSongDrafts by viewModel.currentSongDrafts.collectAsState()
    val activeDraft by viewModel.activeDraft.collectAsState()
    val notesLoading by viewModel.notesLoading.collectAsState()
    val notesError by viewModel.notesError.collectAsState()

    val audioPermission = remember { requiredAudioPermission() }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = viewModel::setPermissionGranted
    )

    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    BackHandler(enabled = pagerState.currentPage == 1) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(0)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> MusicLibraryScreen(
                    songs = songs,
                    pinnedSongs = pinnedSongs,
                    libraryTab = libraryTab,
                    playlists = playlists,
                    selectedPlaylistId = selectedPlaylistId,
                    playlistSongUris = playlistSongUris,
                    libraryActionError = libraryActionError,
                    selectedSong = selectedSong,
                    selectedSongId = selectedSong?.id,
                    onSongSelected = { song ->
                        viewModel.selectSong(song)
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(1)
                        }
                    },
                    onRefresh = {
                        if (permissionGranted) {
                            viewModel.refreshLibrary()
                        } else {
                            permissionLauncher.launch(audioPermission)
                        }
                    },
                    isLoading = isLoading,
                    permissionGranted = permissionGranted,
                    errorMessage = errorMessage,
                    onRequestPermission = { permissionLauncher.launch(audioPermission) },
                    isPlaying = isPlaying,
                    currentPositionMs = currentPositionMs,
                    totalDurationMs = totalDurationMs,
                    onPlayPrevious = viewModel::playPreviousSong,
                    onSeekBackward = { viewModel.seekBy(-5_000L) },
                    onTogglePlayback = viewModel::togglePlayback,
                    onSeekForward = { viewModel.seekBy(5_000L) },
                    onPlayNext = viewModel::playNextSong,
                    onOpenStudio = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(1)
                        }
                    },
                    onLibraryTabSelected = viewModel::setLibraryTab,
                    onTogglePin = viewModel::togglePinSong,
                    onCreatePlaylist = viewModel::createPlaylist,
                    onDeletePlaylist = viewModel::deletePlaylist,
                    onSelectPlaylist = viewModel::selectPlaylist,
                    onAddSongToPlaylist = viewModel::addSongToPlaylist
                )
                1 -> NotesScreen(
                    selectedSong = selectedSong,
                    isPlaying = isPlaying,
                    playbackError = playbackError,
                    currentSongDrafts = currentSongDrafts,
                    activeDraft = activeDraft,
                    notesLoading = notesLoading,
                    notesError = notesError,
                    onActiveDraftContentChanged = viewModel::updateActiveDraftContent,
                    onDraftSelected = viewModel::selectDraft,
                    onAddDraft = { viewModel.addNewDraft() },
                    onBackClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(0)
                        }
                    },
                    onExportLyrics = {
                        selectedSong?.let { song ->
                            val lyrics = buildString {
                                append(song.title)
                                append(" — ")
                                append(song.artist)
                                appendLine()
                                appendLine()
                                append(activeDraft?.content.orEmpty())
                            }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, song.title)
                                putExtra(Intent.EXTRA_TEXT, lyrics)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Export lyrics"))
                        }
                    },
                    currentPositionMs = currentPositionMs,
                    totalDurationMs = totalDurationMs,
                    repeatMode = repeatMode,
                    isShuffleEnabled = isShuffleEnabled,
                    onSeekTo = viewModel::seekTo,
                    onPlayPrevious = viewModel::playPreviousSong,
                    onSeekBackward = { viewModel.seekBy(-5_000L) },
                    onTogglePlayback = viewModel::togglePlayback,
                    onSeekForward = { viewModel.seekBy(5_000L) },
                    onPlayNext = viewModel::playNextSong,
                    onToggleRepeat = viewModel::toggleRepeatMode,
                    onToggleShuffle = viewModel::toggleShuffleMode
                )
            }
        }
    }
}
