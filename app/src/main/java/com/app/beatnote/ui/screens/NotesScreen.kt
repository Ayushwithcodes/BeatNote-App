package com.app.beatnote.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.app.beatnote.data.NoteDraft
import com.app.beatnote.model.Song
import com.app.beatnote.ui.theme.CharcoalDark
import com.app.beatnote.ui.theme.CharcoalMedium
import com.app.beatnote.ui.theme.SubtleGrey
import com.app.beatnote.ui.theme.White

@Composable
fun NotesScreen(
    selectedSong: Song?,
    isPlaying: Boolean,
    playbackError: String?,
    currentSongDrafts: List<NoteDraft>,
    activeDraft: NoteDraft?,
    notesLoading: Boolean,
    notesError: String?,
    onActiveDraftContentChanged: (String) -> Unit,
    onDraftSelected: (NoteDraft) -> Unit,
    onAddDraft: () -> Unit,
    onBackClick: () -> Unit,
    onExportLyrics: () -> Unit,
    currentPositionMs: Long,
    totalDurationMs: Long,
    repeatMode: Int,
    isShuffleEnabled: Boolean,
    onSeekTo: (Long) -> Unit,
    onPlayPrevious: () -> Unit,
    onSeekBackward: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekForward: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit
) {
    var isEditorFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val isWritingMode = isEditorFocused
    BackHandler(enabled = isEditorFocused) {
        focusManager.clearFocus()
        isEditorFocused = false
    }
    var isSeeking by remember(selectedSong?.id) { mutableStateOf(false) }
    var sliderPosition by remember(selectedSong?.id) {
        mutableFloatStateOf(currentPositionMs.toFloat())
    }

    LaunchedEffect(currentPositionMs, isSeeking) {
        if (!isSeeking) {
            sliderPosition = currentPositionMs.toFloat()
        }
    }
    val sliderMax = totalDurationMs.coerceAtLeast(1L).toFloat()
    val displayedPosition = (if (isSeeking) sliderPosition else currentPositionMs.toFloat())
        .coerceIn(0f, sliderMax)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = if (isWritingMode) 8.dp else 20.dp)
            .then(if (isWritingMode) Modifier else Modifier.padding(vertical = 16.dp))
    ) {
        if (isWritingMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        isEditorFocused = false
                        onBackClick()
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = White)
                }
                IconButton(onClick = onSeekBackward, enabled = selectedSong != null) {
                    Text("-5s", fontWeight = FontWeight.Bold, color = White)
                }
                IconButton(
                    onClick = onTogglePlayback,
                    enabled = selectedSong != null,
                    modifier = Modifier
                        .size(40.dp)
                        .background(CharcoalMedium, shape = androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = White
                    )
                }
                IconButton(onClick = onSeekForward, enabled = selectedSong != null) {
                    Text("+5s", fontWeight = FontWeight.Bold, color = White)
                }
                IconButton(
                    onClick = onExportLyrics,
                    enabled = selectedSong != null,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Export lyrics", tint = White)
                }
            }

        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = White)
                }
                IconButton(
                    onClick = onExportLyrics,
                    enabled = selectedSong != null
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Export lyrics", tint = White)
                }
            }

            Text(
                text = selectedSong?.title ?: "No Song Selected",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = White,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = selectedSong?.artist ?: "Select a song from library",
                style = MaterialTheme.typography.bodyMedium,
                color = SubtleGrey,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatPlaybackTime(displayedPosition.toLong()),
                    style = MaterialTheme.typography.labelMedium,
                    color = SubtleGrey
                )
                Text(
                    text = formatPlaybackTime(totalDurationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = SubtleGrey
                )
            }
            Slider(
                value = displayedPosition,
                onValueChange = { value ->
                    isSeeking = true
                    sliderPosition = value
                },
                onValueChangeFinished = {
                    onSeekTo(sliderPosition.toLong())
                    isSeeking = false
                },
                valueRange = 0f..sliderMax,
                enabled = selectedSong != null && totalDurationMs > 0L,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CharcoalDark, shape = MaterialTheme.shapes.medium)
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleShuffle,
                    enabled = selectedSong != null,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = if (isShuffleEnabled) "Disable shuffle" else "Enable shuffle",
                        tint = if (isShuffleEnabled) White else SubtleGrey
                    )
                }
                IconButton(
                    onClick = onPlayPrevious,
                    enabled = selectedSong != null,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous track", tint = White)
                }
                TextButton(
                    onClick = onSeekBackward,
                    enabled = selectedSong != null,
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text("-5s", fontWeight = FontWeight.Bold, color = White)
                }
                IconButton(
                    onClick = onTogglePlayback,
                    enabled = selectedSong != null,
                    modifier = Modifier
                        .size(44.dp)
                        .background(CharcoalMedium, shape = androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = White
                    )
                }
                TextButton(
                    onClick = onSeekForward,
                    enabled = selectedSong != null,
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text("+5s", fontWeight = FontWeight.Bold, color = White)
                }
                IconButton(
                    onClick = onPlayNext,
                    enabled = selectedSong != null,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next track", tint = White)
                }
                IconButton(
                    onClick = onToggleRepeat,
                    enabled = selectedSong != null,
                    modifier = Modifier.size(40.dp)
                ) {
                    val repeatIcon = if (repeatMode == Player.REPEAT_MODE_ONE) {
                        Icons.Default.RepeatOne
                    } else {
                        Icons.Default.Repeat
                    }
                    Icon(
                        imageVector = repeatIcon,
                        contentDescription = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> "Repeat one"
                            Player.REPEAT_MODE_ALL -> "Repeat all"
                            else -> "Repeat off"
                        },
                        tint = if (repeatMode == Player.REPEAT_MODE_OFF) SubtleGrey else White
                    )
                }
            }

            if (playbackError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = playbackError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SubtleGrey
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                currentSongDrafts.forEach { draft ->
                    val isActive = draft.id == activeDraft?.id
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .border(
                                width = 1.dp,
                                color = if (isActive) White.copy(alpha = 0.7f) else White.copy(alpha = 0.08f),
                                shape = MaterialTheme.shapes.large
                            )
                            .background(
                                color = if (isActive) CharcoalDark else CharcoalMedium,
                                shape = MaterialTheme.shapes.large
                            )
                            .clickable { onDraftSelected(draft) }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = draft.draftTitle,
                            color = if (isActive) White else SubtleGrey,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1
                        )
                    }
                }
                IconButton(
                    onClick = onAddDraft,
                    enabled = selectedSong != null && !notesLoading
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add draft", tint = White)
                }
            }
        }

        OutlinedTextField(
            value = activeDraft?.content.orEmpty(),
            onValueChange = onActiveDraftContentChanged,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .imePadding()
                .onFocusChanged { focusState ->
                    isEditorFocused = focusState.isFocused
                },
            placeholder = {
                Text(
                    text = if (selectedSong == null) {
                        "Select a song to write notes..."
                    } else {
                        "Write your lyrics here..."
                    },
                    color = SubtleGrey.copy(alpha = 0.5f)
                )
            },
            enabled = selectedSong != null && !notesLoading,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                keyboardType = KeyboardType.Text
            ),
            minLines = 1,
            maxLines = Int.MAX_VALUE,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CharcoalDark,
                unfocusedContainerColor = CharcoalDark,
                disabledContainerColor = CharcoalDark,
                focusedBorderColor = CharcoalMedium,
                unfocusedBorderColor = CharcoalMedium,
                focusedTextColor = White,
                unfocusedTextColor = White,
                cursorColor = White
            ),
            shape = MaterialTheme.shapes.medium
        )

        if (!isWritingMode && notesError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = notesError,
                style = MaterialTheme.typography.bodyMedium,
                color = SubtleGrey
            )
        }
    }
}

private fun formatPlaybackTime(timeMs: Long): String {
    val totalSeconds = timeMs.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}
