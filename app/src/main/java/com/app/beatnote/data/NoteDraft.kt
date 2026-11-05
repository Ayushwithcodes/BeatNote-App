package com.app.beatnote.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "song_notes",
    primaryKeys = ["song_id"]
)
data class NoteEntity(
    @ColumnInfo(name = "song_id")
    val songUri: String,
    @ColumnInfo(name = "notes")
    val legacyContent: String
)

@Entity(
    tableName = "note_drafts",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["song_id"],
            childColumns = ["song_uri"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["song_uri"])]
)
data class NoteDraft(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "song_uri")
    val songUri: String,
    val draftTitle: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
