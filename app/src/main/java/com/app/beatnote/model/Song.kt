package com.app.beatnote.model

import android.net.Uri

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: String,
    val uri: Uri,
    val albumArtUri: Uri?
)

