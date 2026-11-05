package com.app.beatnote.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "track_metadata")
data class TrackMetadataEntity(
    @PrimaryKey
    val songUri: String,
    @ColumnInfo(defaultValue = "0")
    val isPinned: Boolean = false,
    val customTitle: String? = null
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_song_cross_ref",
    primaryKeys = ["playlistId", "songUri"],
    indices = [Index(value = ["playlistId"])],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ]
)
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songUri: String,
    val addedAt: Long = System.currentTimeMillis()
)
