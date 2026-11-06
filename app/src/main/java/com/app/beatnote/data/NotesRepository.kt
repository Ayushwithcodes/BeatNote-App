package com.app.beatnote.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class NotesRepository(context: Context) {
    private val database = NotesDatabase.getInstance(context)
    private val notesDao = database.notesDao()

    fun getDraftsForSong(songUri: String): Flow<List<NoteDraft>> =
        notesDao.getDraftsForSong(songUri)

    fun getPinnedSongUris(): Flow<List<String>> =
        notesDao.getPinnedSongUris()

    fun getPlaylists(): Flow<List<PlaylistEntity>> =
        notesDao.getPlaylists()

    fun getSongsForPlaylist(playlistId: Long): Flow<List<String>> =
        notesDao.getSongsForPlaylist(playlistId)

    suspend fun togglePinTrack(songUri: String) {
        database.withTransaction {
            notesDao.insertTrackMetadataIfMissing(TrackMetadataEntity(songUri = songUri))
            notesDao.togglePinTrack(songUri)
        }
    }

    suspend fun createPlaylist(name: String): Long {
        require(name.isNotBlank()) { "Playlist name must not be blank" }
        return notesDao.createPlaylist(PlaylistEntity(name = name.trim()))
    }

    suspend fun deletePlaylist(playlistId: Long) {
        notesDao.deletePlaylist(playlistId)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songUri: String) {
        notesDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songUri))
    }

    suspend fun ensureDraftForSong(songUri: String) {
        database.withTransaction {
            if (notesDao.getDraftsForSongOnce(songUri).isEmpty()) {
                notesDao.insertNoteEntity(NoteEntity(songUri = songUri, legacyContent = ""))
                notesDao.insertDraft(
                    NoteDraft(
                        songUri = songUri,
                        draftTitle = DEFAULT_DRAFT_TITLE,
                        content = ""
                    )
                )
            }
        }
    }

    suspend fun restoreDraftsFromBackup(
        songUri: String,
        drafts: List<Pair<String, String>>
    ): Int = database.withTransaction {
        if (drafts.isEmpty()) return@withTransaction 0
        var upsertedCount = 0
        notesDao.insertNoteEntity(NoteEntity(songUri = songUri, legacyContent = ""))
        drafts.distinctBy { it.first }.forEach { (title, content) ->
            val existingDraft = notesDao.getDraftForSongAndTitle(songUri, title)
            if (existingDraft == null) {
                notesDao.insertDraft(
                    NoteDraft(songUri = songUri, draftTitle = title, content = content)
                )
            } else {
                notesDao.updateDraft(
                    existingDraft.copy(
                        content = content,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            upsertedCount++
        }
        android.util.Log.d(
            BACKUP_LOG_TAG,
            "Upserted $upsertedCount backup drafts for song URI $songUri"
        )
        upsertedCount
    }

    suspend fun addDraft(songUri: String, title: String): NoteDraft =
        database.withTransaction {
            notesDao.insertNoteEntity(NoteEntity(songUri = songUri, legacyContent = ""))
            val existingDrafts = notesDao.getDraftsForSongOnce(songUri)
            val draftTitle = title.trim().ifEmpty {
                var sequence = existingDrafts.size + 1
                while (existingDrafts.any { it.draftTitle == "Draft $sequence" }) {
                    sequence++
                }
                "Draft $sequence"
            }
            val draft = NoteDraft(
                songUri = songUri,
                draftTitle = draftTitle,
                content = ""
            )
            val insertedId = notesDao.insertDraft(draft)
            draft.copy(id = insertedId)
        }

    suspend fun updateDraft(draft: NoteDraft) {
        notesDao.updateDraft(draft.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteDraft(draft: NoteDraft) {
        database.withTransaction {
            notesDao.deleteDraft(draft)
            if (notesDao.getDraftsForSongOnce(draft.songUri).isEmpty()) {
                notesDao.insertNoteEntity(
                    NoteEntity(songUri = draft.songUri, legacyContent = "")
                )
                notesDao.insertDraft(
                    NoteDraft(
                        songUri = draft.songUri,
                        draftTitle = DEFAULT_DRAFT_TITLE,
                        content = ""
                    )
                )
            }
        }
    }

    companion object {
        private const val BACKUP_LOG_TAG = "BeatNoteBackup"
        private const val DEFAULT_DRAFT_TITLE = "Draft 1"
    }
}
