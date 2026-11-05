package com.app.beatnote.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.app.beatnote.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

class MusicRepository(private val context: Context) {

    suspend fun scanSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songList = mutableListOf<Song>()
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        val cursor = context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        ) ?: throw IOException("MediaStore did not return an audio cursor")

        cursor.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val displayNameColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val displayName = it.getString(displayNameColumn).orEmpty().trim()
                val title = it.getString(titleColumn)
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: displayName.substringBeforeLast('.', displayName).trim()
                if (id <= 0 || title.isEmpty()) continue

                val artist = it.getString(artistColumn).toDisplayMetadata("Unknown Artist")
                val album = it.getString(albumColumn).toDisplayMetadata("Unknown Album")
                val durationMs = it.getLong(durationColumn)
                val albumId = it.getLong(albumIdColumn)
                val contentUri = ContentUris.withAppendedId(collection, id)
                val albumArtUri = if (albumId > 0) {
                    ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    )
                } else {
                    null
                }

                songList.add(
                    Song(
                        id = contentUri.toString(),
                        title = title,
                        artist = artist,
                        album = album,
                        duration = formatDuration(durationMs),
                        uri = contentUri,
                        albumArtUri = albumArtUri
                    )
                )
            }
        }

        songList
    }

    private fun String?.toDisplayMetadata(fallback: String): String =
        if (isNullOrBlank() || equals("<unknown>", ignoreCase = true)) fallback else trim()

    private fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return "0:00"
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}
