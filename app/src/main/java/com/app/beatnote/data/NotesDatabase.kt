package com.app.beatnote.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NoteEntity::class,
        NoteDraft::class,
        TrackMetadataEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class
    ],
    version = 3,
    exportSchema = false
)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun notesDao(): NotesDao

    companion object {
        private const val DATABASE_NAME = "beatnote_notes.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS note_drafts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        song_uri TEXT NOT NULL,
                        draftTitle TEXT NOT NULL,
                        content TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(song_uri) REFERENCES song_notes(song_id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_note_drafts_song_uri ON note_drafts(song_uri)"
                )
                database.execSQL(
                    """
                    INSERT INTO note_drafts (song_uri, draftTitle, content, createdAt, updatedAt)
                    SELECT song_id, 'Draft 1', notes,
                        CAST(strftime('%s', 'now') AS INTEGER) * 1000,
                        CAST(strftime('%s', 'now') AS INTEGER) * 1000
                    FROM song_notes
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS track_metadata (
                        songUri TEXT NOT NULL PRIMARY KEY,
                        isPinned INTEGER NOT NULL DEFAULT 0,
                        customTitle TEXT
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS playlists (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS playlist_song_cross_ref (
                        playlistId INTEGER NOT NULL,
                        songUri TEXT NOT NULL,
                        addedAt INTEGER NOT NULL,
                        PRIMARY KEY(playlistId, songUri),
                        FOREIGN KEY(playlistId) REFERENCES playlists(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_playlist_song_cross_ref_playlistId ON playlist_song_cross_ref(playlistId)"
                )
            }
        }

        @Volatile
        private var instance: NotesDatabase? = null

        fun getInstance(context: Context): NotesDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NotesDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
