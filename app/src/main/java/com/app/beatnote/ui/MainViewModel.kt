package com.app.beatnote.ui

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.app.beatnote.data.MusicRepository
import com.app.beatnote.data.PlaylistEntity
import com.app.beatnote.data.NoteDraft
import com.app.beatnote.data.NotesRepository
import com.app.beatnote.model.Song
import com.app.beatnote.playback.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class LibraryTab {
    SONGS,
    PINNED,
    PLAYLISTS
}

class MainViewModel(
    context: Context,
    private val repository: MusicRepository,
    private val notesRepository: NotesRepository
) : ViewModel() {
    private val applicationContext = context.applicationContext

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _pinnedSongs = MutableStateFlow<List<Song>>(emptyList())
    val pinnedSongs: StateFlow<List<Song>> = _pinnedSongs.asStateFlow()

    private val _pinnedSongUris = MutableStateFlow<Set<String>>(emptySet())

    private val _activeLibraryTab = MutableStateFlow(LibraryTab.SONGS)
    val activeLibraryTab: StateFlow<LibraryTab> = _activeLibraryTab.asStateFlow()

    private val _playlists = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    val playlists: StateFlow<List<PlaylistEntity>> = _playlists.asStateFlow()

    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)
    val selectedPlaylistId: StateFlow<Long?> = _selectedPlaylistId.asStateFlow()

    private val _playlistSongUris = MutableStateFlow<List<String>>(emptyList())
    val playlistSongUris: StateFlow<List<String>> = _playlistSongUris.asStateFlow()

    private val _libraryActionError = MutableStateFlow<String?>(null)
    val libraryActionError: StateFlow<String?> = _libraryActionError.asStateFlow()

    private val _selectedSong = MutableStateFlow<Song?>(null)
    val selectedSong: StateFlow<Song?> = _selectedSong.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private val _playbackQueue = MutableStateFlow<List<Song>>(emptyList())
    val playbackQueue: StateFlow<List<Song>> = _playbackQueue.asStateFlow()

    private val _currentSongIndex = MutableStateFlow(-1)
    val currentSongIndex: StateFlow<Int> = _currentSongIndex.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _totalDurationMs = MutableStateFlow(0L)
    val totalDurationMs: StateFlow<Long> = _totalDurationMs.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _currentSongDrafts = MutableStateFlow<List<NoteDraft>>(emptyList())
    val currentSongDrafts: StateFlow<List<NoteDraft>> = _currentSongDrafts.asStateFlow()

    private val _activeDraft = MutableStateFlow<NoteDraft?>(null)
    val activeDraft: StateFlow<NoteDraft?> = _activeDraft.asStateFlow()

    private val _notesLoading = MutableStateFlow(false)
    val notesLoading: StateFlow<Boolean> = _notesLoading.asStateFlow()

    private val _notesError = MutableStateFlow<String?>(null)
    val notesError: StateFlow<String?> = _notesError.asStateFlow()

    private var scanJob: Job? = null
    private var allScannedSongs: List<Song> = emptyList()
    private var playbackPositionJob: Job? = null
    private var draftsLoadJob: Job? = null
    private var draftSaveJob: Job? = null
    private var pendingDraftSaveJob: Job? = null
    private var notesSongId: String? = null
    private var notesDirty = false
    private var activeDraftId: Long? = null
    private var playlistsJob: Job? = null
    private var pendingSong: Song? = null
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            controller?.let { player ->
                if (isPlaying) {
                    startPositionUpdates(player)
                } else {
                    playbackPositionJob?.cancel()
                    updatePlaybackTimeline(player)
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val songId = mediaItem?.mediaId
            val song = _playbackQueue.value.firstOrNull { it.id == songId }
                ?: _songs.value.firstOrNull { it.id == songId }
            if (song != null) {
                _currentSongIndex.value = _playbackQueue.value.indexOfFirst { it.id == song.id }
                updateSelectedSong(song)
            }
            controller?.let(::updatePlaybackTimeline)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            controller?.let(::updatePlaybackTimeline)
            if (playbackState == Player.STATE_ENDED) {
                playNextSong()
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.value = repeatMode
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _isShuffleEnabled.value = shuffleModeEnabled
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Audio playback failed", error)
            _isPlaying.value = false
            _playbackError.value = "This audio file can't be played or is no longer available."
        }
    }

    init {
        observeLibraryMetadata()
        connectToPlaybackService()
    }

    fun setLibraryTab(tab: LibraryTab) {
        _activeLibraryTab.value = tab
    }

    fun selectPlaylist(playlist: PlaylistEntity?) {
        _selectedPlaylistId.value = playlist?.id
        _playlistSongUris.value = emptyList()
        playlistsJob?.cancel()
        if (playlist != null) {
            playlistsJob = viewModelScope.launch {
                try {
                    notesRepository.getSongsForPlaylist(playlist.id).collect { songUris ->
                        if (_selectedPlaylistId.value == playlist.id) {
                            _playlistSongUris.value = songUris
                        }
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.e(TAG, "Failed to load playlist ${playlist.id}", exception)
                    _libraryActionError.value = "Couldn't load this playlist."
                }
            }
        }
    }

    fun togglePinSong(song: Song) {
        viewModelScope.launch {
            try {
                notesRepository.togglePinTrack(song.id)
                _libraryActionError.value = null
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to update pin state for song ${song.id}", exception)
                _libraryActionError.value = "Couldn't update the pinned songs."
            }
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) {
            _libraryActionError.value = "Enter a playlist name."
            return
        }
        viewModelScope.launch {
            try {
                val playlistId = notesRepository.createPlaylist(name)
                _libraryActionError.value = null
                val updatedPlaylists = notesRepository.getPlaylists().first { playlists ->
                    playlists.any { it.id == playlistId }
                }
                _playlists.value = updatedPlaylists
                updatedPlaylists.firstOrNull { it.id == playlistId }?.let(::selectPlaylist)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to create playlist", exception)
                _libraryActionError.value = "Couldn't create this playlist."
            }
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            try {
                notesRepository.deletePlaylist(playlist.id)
                if (_selectedPlaylistId.value == playlist.id) {
                    selectPlaylist(null)
                }
                _libraryActionError.value = null
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to delete playlist ${playlist.id}", exception)
                _libraryActionError.value = "Couldn't delete this playlist."
            }
        }
    }

    fun addSongToPlaylist(playlist: PlaylistEntity, song: Song) {
        viewModelScope.launch {
            try {
                notesRepository.addSongToPlaylist(playlist.id, song.id)
                _libraryActionError.value = null
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to add song ${song.id} to playlist ${playlist.id}", exception)
                _libraryActionError.value = "Couldn't add this song to the playlist."
            }
        }
    }

    fun setPermissionGranted(granted: Boolean) {
        if (_permissionGranted.value == granted) return

        _permissionGranted.value = granted
        if (granted) {
            refreshLibrary()
        } else {
            scanJob?.cancel()
            allScannedSongs = emptyList()
            _songs.value = emptyList()
            _pinnedSongs.value = emptyList()
            _errorMessage.value = null
        }
    }

    fun selectSong(song: Song) {
        val queue = allScannedSongs.toMutableList()
        val selectedIndex = queue.indexOfFirst { it.id == song.id }
        val currentIndex = if (selectedIndex >= 0) {
            selectedIndex
        } else {
            queue.add(song)
            queue.lastIndex
        }
        _playbackQueue.value = queue
        _currentSongIndex.value = currentIndex
        updateSelectedSong(song)
        _playbackError.value = null
        val connectedController = controller
        if (connectedController == null) {
            pendingSong = song
        } else {
            playQueue(connectedController, queue, currentIndex)
        }
    }

    private fun updateSelectedSong(song: Song) {
        if (notesSongId != song.id) {
            flushNotes()
            notesSongId = song.id
            activeDraftId = null
            notesDirty = false
            _currentSongDrafts.value = emptyList()
            _activeDraft.value = null
            _notesLoading.value = true
            _notesError.value = null
            draftsLoadJob?.cancel()
            draftsLoadJob = viewModelScope.launch {
                try {
                    pendingDraftSaveJob?.join()
                    notesRepository.ensureDraftForSong(song.id)
                    notesRepository.getDraftsForSong(song.id).collect { drafts ->
                        if (notesSongId == song.id) {
                            _currentSongDrafts.value = drafts
                            val selectedDraft = drafts.firstOrNull { it.id == activeDraftId }
                                ?: drafts.firstOrNull()
                            if (selectedDraft != null) {
                                activeDraftId = selectedDraft.id
                                if (!notesDirty) {
                                    _activeDraft.value = selectedDraft
                                }
                            }
                            _notesLoading.value = false
                        }
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.e(TAG, "Failed to load notes for song ${song.id}", exception)
                    if (notesSongId == song.id) {
                        _notesError.value = "Couldn't load notes for this song."
                    }
                } finally {
                    if (notesSongId == song.id) {
                        _notesLoading.value = false
                    }
                }
            }
        }

        _selectedSong.value = song
    }

    fun selectDraft(draft: NoteDraft) {
        if (draft.songUri != notesSongId || _activeDraft.value?.id == draft.id) return
        flushNotes()
        activeDraftId = draft.id
        _activeDraft.value = draft
        notesDirty = false
        _notesError.value = null
    }

    fun addNewDraft(title: String = "") {
        val songId = notesSongId ?: return
        flushNotes()
        viewModelScope.launch {
            try {
                val newDraft = notesRepository.addDraft(songId, title)
                if (notesSongId == songId) {
                    activeDraftId = newDraft.id
                    _activeDraft.value = newDraft
                    notesDirty = false
                    _notesError.value = null
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to create a note draft for song $songId", exception)
                if (notesSongId == songId) {
                    _notesError.value = "Couldn't create a new draft."
                }
            }
        }
    }

    fun updateActiveDraftContent(newContent: String) {
        val draft = _activeDraft.value ?: return
        _activeDraft.value = draft.copy(content = newContent)
        notesDirty = true
        _notesError.value = null
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            delay(NOTES_SAVE_DEBOUNCE_MS)
            if (_activeDraft.value?.id == draft.id && notesDirty) {
                queueDraftSave(_activeDraft.value ?: return@launch)
            }
        }
    }

    fun deleteDraft(draft: NoteDraft) {
        if (draft.songUri != notesSongId) return
        flushNotes()
        viewModelScope.launch {
            try {
                pendingDraftSaveJob?.join()
                notesRepository.deleteDraft(draft)
                if (activeDraftId == draft.id) {
                    activeDraftId = null
                    _activeDraft.value = null
                    notesDirty = false
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to delete note draft ${draft.id}", exception)
                if (notesSongId == draft.songUri) {
                    _notesError.value = "Couldn't delete this draft."
                }
            }
        }
    }

    fun flushNotes() {
        draftSaveJob?.cancel()
        draftSaveJob = null
        val draft = _activeDraft.value
        if (draft != null && notesDirty) {
            queueDraftSave(draft)
        }
    }

    fun togglePlayback() {
        val connectedController = controller ?: return
        if (connectedController.isPlaying) {
            connectedController.pause()
        } else if (connectedController.currentMediaItem != null) {
            _playbackError.value = null
            connectedController.play()
        } else {
            val queue = _playbackQueue.value
            val currentIndex = _currentSongIndex.value
            if (queue.isNotEmpty() && currentIndex in queue.indices) {
                _playbackError.value = null
                playQueue(connectedController, queue, currentIndex)
            }
        }
    }

    fun seekBy(offsetMs: Long) {
        val connectedController = controller ?: return
        if (connectedController.currentMediaItem == null) return

        val duration = connectedController.duration
        if (duration < 0) return

        val targetPosition = (connectedController.currentPosition + offsetMs)
            .coerceIn(0L, duration)
        seekTo(targetPosition)
    }

    fun seekTo(positionMs: Long) {
        val connectedController = controller ?: return
        if (connectedController.currentMediaItem == null) return

        val duration = connectedController.duration
        val targetPosition = if (duration >= 0) {
            positionMs.coerceIn(0L, duration)
        } else {
            positionMs.coerceAtLeast(0L)
        }
        connectedController.seekTo(targetPosition)
        _currentPositionMs.value = targetPosition
    }

    fun playNextSong() {
        val connectedController = controller ?: return
        if (connectedController.hasNextMediaItem()) {
            connectedController.seekToNextMediaItem()
            connectedController.play()
        }
    }

    fun playPreviousSong() {
        val connectedController = controller ?: return
        if (connectedController.currentPosition > PREVIOUS_TRACK_RESTART_THRESHOLD_MS) {
            connectedController.seekTo(0L)
        } else if (connectedController.hasPreviousMediaItem()) {
            connectedController.seekToPreviousMediaItem()
        } else {
            connectedController.seekTo(0L)
        }
    }

    fun toggleRepeatMode() {
        val connectedController = controller ?: return
        connectedController.repeatMode = when (connectedController.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleShuffleMode() {
        val connectedController = controller ?: return
        connectedController.shuffleModeEnabled = !connectedController.shuffleModeEnabled
    }

    fun refreshLibrary() {
        if (!_permissionGranted.value || scanJob?.isActive == true) return

        scanJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val scannedSongs = repository.scanSongs()
                allScannedSongs = scannedSongs
                updatePinnedSongs()
                if (_playbackQueue.value.isEmpty()) {
                    _playbackQueue.value = scannedSongs
                }
                val currentSong = _selectedSong.value
                if (currentSong != null && scannedSongs.none { it.id == currentSong.id }) {
                    if (controller?.currentMediaItem?.mediaId != currentSong.id) {
                        _selectedSong.value = null
                    }
                }
                syncSelectedSongWithController()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: SecurityException) {
                Log.e(TAG, "Audio permission was revoked during library scan", exception)
                _permissionGranted.value = false
                allScannedSongs = emptyList()
                _songs.value = emptyList()
                _pinnedSongs.value = emptyList()
                _errorMessage.value = "Audio permission is required to scan your library."
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to scan the music library", exception)
                _errorMessage.value = "Couldn't load your music. Try refreshing the library."
            } finally {
                _isLoading.value = false
            }
        }
    }

    override fun onCleared() {
        playbackPositionJob?.cancel()
        draftsLoadJob?.cancel()
        draftSaveJob?.cancel()
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onCleared()
    }

    private fun connectToPlaybackService() {
        val sessionToken = SessionToken(
            applicationContext,
            ComponentName(applicationContext, PlaybackService::class.java)
        )
        val future = MediaController.Builder(applicationContext, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                if (controllerFuture !== future) return@addListener
                try {
                    val connectedController = future.get()
                    controller = connectedController
                    connectedController.addListener(playerListener)
                    _isPlaying.value = connectedController.isPlaying
                    _repeatMode.value = connectedController.repeatMode
                    _isShuffleEnabled.value = connectedController.shuffleModeEnabled
                    updatePlaybackTimeline(connectedController)
                    if (connectedController.isPlaying) {
                        startPositionUpdates(connectedController)
                    }

                    syncSelectedSongWithController()
                    pendingSong?.let { song ->
                        pendingSong = null
                        val queue = _playbackQueue.value.ifEmpty { listOf(song) }
                        val index = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                        _playbackQueue.value = queue
                        _currentSongIndex.value = index
                        playQueue(connectedController, queue, index)
                    }
                } catch (exception: Exception) {
                    Log.e(TAG, "Unable to connect to the playback service", exception)
                    _playbackError.value = "The audio player couldn't be started. Try again."
                }
            },
            ContextCompat.getMainExecutor(applicationContext)
        )
    }

    private fun observeLibraryMetadata() {
        viewModelScope.launch {
            try {
                notesRepository.getPinnedSongUris().collect { pinnedUris ->
                    val pinnedSet = pinnedUris.toSet()
                    _pinnedSongUris.value = pinnedSet
                    _pinnedSongs.value = allScannedSongs.filter { it.id in pinnedSet }
                    _songs.value = allScannedSongs.sortedByDescending { it.id in pinnedSet }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to observe pinned songs", exception)
                _libraryActionError.value = "Couldn't load pinned song settings."
            }
        }
        viewModelScope.launch {
            try {
                notesRepository.getPlaylists().collect { savedPlaylists ->
                    _playlists.value = savedPlaylists
                    if (_selectedPlaylistId.value != null &&
                        savedPlaylists.none { it.id == _selectedPlaylistId.value }
                    ) {
                        selectPlaylist(null)
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to observe playlists", exception)
                _libraryActionError.value = "Couldn't load playlists."
            }
        }
    }

    private fun updatePinnedSongs() {
        val pinnedIds = _pinnedSongUris.value
        _pinnedSongs.value = allScannedSongs.filter { it.id in pinnedIds }
        _songs.value = allScannedSongs.sortedByDescending { it.id in pinnedIds }
    }

    private fun playQueue(player: MediaController, queue: List<Song>, startIndex: Int) {
        if (queue.isEmpty() || startIndex !in queue.indices) return
        try {
            _playbackError.value = null
            player.setMediaItems(queue.map(::createMediaItem), startIndex, 0L)
            player.repeatMode = _repeatMode.value
            player.shuffleModeEnabled = _isShuffleEnabled.value
            player.prepare()
            player.play()
            _currentPositionMs.value = 0L
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Unable to play selected audio", exception)
            _isPlaying.value = false
            _playbackError.value = "This audio file can't be played or is no longer available."
        }
    }

    private fun createMediaItem(song: Song): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .apply {
                song.albumArtUri?.let(::setArtworkUri)
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(song.uri)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun syncSelectedSongWithController() {
        val currentMediaId = controller?.currentMediaItem?.mediaId ?: return
        val song = _songs.value.firstOrNull { it.id == currentMediaId } ?: return
        if (_playbackQueue.value.isEmpty()) {
            _playbackQueue.value = allScannedSongs
        }
        _currentSongIndex.value = _playbackQueue.value.indexOfFirst { it.id == song.id }
        if (_currentSongIndex.value < 0) {
            _playbackQueue.value = _playbackQueue.value + song
            _currentSongIndex.value = _playbackQueue.value.lastIndex
        }
        updateSelectedSong(song)
    }

    private fun startPositionUpdates(player: MediaController) {
        if (playbackPositionJob?.isActive == true) return
        playbackPositionJob = viewModelScope.launch {
            while (controller === player && player.isPlaying) {
                updatePlaybackTimeline(player)
                delay(POSITION_UPDATE_INTERVAL_MS)
            }
            updatePlaybackTimeline(player)
        }
    }

    private fun updatePlaybackTimeline(player: MediaController) {
        if (controller !== player) return
        _currentPositionMs.value = player.currentPosition.coerceAtLeast(0L)
        _totalDurationMs.value = player.duration.takeIf { it >= 0L } ?: 0L
    }

    private fun queueDraftSave(draft: NoteDraft) {
        notesDirty = false
        val previousSave = pendingDraftSaveJob
        pendingDraftSaveJob = viewModelScope.launch {
            previousSave?.join()
            try {
                notesRepository.updateDraft(draft)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to save draft ${draft.id}", exception)
                if (_activeDraft.value?.id == draft.id) {
                    notesDirty = true
                    _notesError.value = "Couldn't save your notes. Keep the app open and try again."
                }
            }
        }
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val NOTES_SAVE_DEBOUNCE_MS = 500L
        const val POSITION_UPDATE_INTERVAL_MS = 200L
        const val PREVIOUS_TRACK_RESTART_THRESHOLD_MS = 3_000L
    }
}
