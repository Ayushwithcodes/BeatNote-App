package com.app.beatnote.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NotesDao {
    @Query("SELECT * FROM note_drafts WHERE song_uri = :songUri ORDER BY createdAt, id")
    fun getDraftsForSong(songUri: String): Flow<List<NoteDraft>>

    @Query("SELECT * FROM note_drafts WHERE song_uri = :songUri ORDER BY createdAt, id")
    suspend fun getDraftsForSongOnce(songUri: String): List<NoteDraft>

    @Query(
        "SELECT * FROM note_drafts WHERE song_uri = :songUri AND draftTitle = :draftTitle ORDER BY id LIMIT 1"
    )
    suspend fun getDraftForSongAndTitle(songUri: String, draftTitle: String): NoteDraft?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNoteEntity(noteEntity: NoteEntity)

    @Insert
    suspend fun insertDraft(draft: NoteDraft): Long

    @Update
    suspend fun updateDraft(draft: NoteDraft)

    @Delete
    suspend fun deleteDraft(draft: NoteDraft)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrackMetadataIfMissing(metadata: TrackMetadataEntity)

    @Query(
        """
        UPDATE track_metadata
        SET isPinned = CASE WHEN isPinned = 1 THEN 0 ELSE 1 END
        WHERE songUri = :songUri
        """
    )
    suspend fun togglePinTrack(songUri: String)

    @Query("SELECT songUri FROM track_metadata WHERE isPinned = 1")
    fun getPinnedSongUris(): Flow<List<String>>

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC, id DESC")
    fun getPlaylists(): Flow<List<PlaylistEntity>>

    @Insert
    suspend fun createPlaylist(playlist: PlaylistEntity): Long

    @Query(
        "SELECT songUri FROM playlist_song_cross_ref WHERE playlistId = :playlistId ORDER BY addedAt, songUri"
    )
    fun getSongsForPlaylist(playlistId: Long): Flow<List<String>>
}
