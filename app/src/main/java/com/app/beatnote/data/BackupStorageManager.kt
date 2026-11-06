package com.app.beatnote.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.app.beatnote.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class BackupStorageManager(context: Context) {
    private val applicationContext = context.applicationContext
    private val contentResolver = applicationContext.contentResolver

    suspend fun saveDraftToFile(songTitle: String, draftName: String, content: String) {
        withContext(Dispatchers.IO) {
            val songFolder = sanitizeName(songTitle, "Untitled Song")
            val draftFileName = "${sanitizeName(draftName, "Untitled Draft")}.txt"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveWithMediaStore(songFolder, draftFileName, content)
            } else {
                saveWithLegacyFileApi(songFolder, draftFileName, content)
            }
        }
    }

    suspend fun deleteDraftBackup(songTitle: String, draftName: String) {
        withContext(Dispatchers.IO) {
            val songFolder = sanitizeName(songTitle, "Untitled Song")
            val draftFileName = "${sanitizeName(draftName, "Untitled Draft")}.txt"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val fileUri = findMediaStoreFile(
                    "$BACKUP_RELATIVE_PATH$songFolder/",
                    draftFileName
                ) ?: return@withContext
                if (contentResolver.delete(fileUri, null, null) != 1) {
                    throw IOException("MediaStore could not delete the lyrics backup")
                }
            } else {
                requireLegacyPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                val file = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "$BACKUP_FOLDER_NAME/$songFolder/$draftFileName"
                )
                if (file.exists() && !file.delete()) {
                    throw IOException("Could not delete the lyrics backup")
                }
            }
        }
    }

    suspend fun restoreDraftsFromStorage(
        existingSongs: List<Song>
    ): Map<String, List<Pair<String, String>>> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            restoreWithMediaStore(existingSongs)
        } else {
            restoreWithLegacyFileApi(existingSongs)
        }
    }

    private fun saveWithMediaStore(songFolder: String, fileName: String, content: String) {
        val relativePath = "$BACKUP_RELATIVE_PATH$songFolder/"
        val existingUri = findMediaStoreFile(relativePath, fileName)
        val fileUri = existingUri ?: contentResolver.insert(
            mediaStoreCollection(),
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, TEXT_MIME_TYPE)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        ) ?: throw IOException("MediaStore could not create the lyrics backup")

        try {
            val output = contentResolver.openOutputStream(fileUri, "wt")
                ?: throw IOException("MediaStore could not open the lyrics backup")
            output.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            if (existingUri == null) {
                val published = contentResolver.update(
                    fileUri,
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    },
                    null,
                    null
                )
                if (published != 1) {
                    throw IOException("MediaStore could not publish the lyrics backup")
                }
            }
        } catch (exception: Exception) {
            if (existingUri == null) {
                contentResolver.delete(fileUri, null, null)
            }
            throw exception
        }
    }

    private fun findMediaStoreFile(relativePath: String, fileName: String): Uri? {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection =
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        val cursor = contentResolver.query(
            mediaStoreCollection(),
            projection,
            selection,
            arrayOf(relativePath, fileName),
            null
        ) ?: throw IOException("MediaStore could not search for the lyrics backup")

        cursor.use {
            if (!it.moveToFirst()) return null
            val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
            return Uri.withAppendedPath(mediaStoreCollection(), id.toString())
        }
    }

    private fun restoreWithMediaStore(
        existingSongs: List<Song>
    ): Map<String, List<Pair<String, String>>> {
        val songsByFolder = existingSongs.groupBy { sanitizeName(it.title, "Untitled Song") }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH
        )
        val cursor = contentResolver.query(
            mediaStoreCollection(),
            projection,
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf("$BACKUP_RELATIVE_PATH%"),
            "${MediaStore.MediaColumns.RELATIVE_PATH} ASC, ${MediaStore.MediaColumns.DISPLAY_NAME} ASC"
        ) ?: throw IOException("MediaStore could not search the lyrics backup folder")
        val restored = mutableMapOf<String, MutableList<Pair<String, String>>>()
        val backupFolders = mutableSetOf<String>()
        val matchedSongIds = mutableSetOf<String>()
        var draftFileCount = 0

        cursor.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            while (it.moveToNext()) {
                val fileName = it.getString(nameColumn).orEmpty()
                if (!fileName.endsWith(".txt", ignoreCase = true)) continue
                val relativePath = it.getString(pathColumn).orEmpty()
                val songFolder = songFolderFromRelativePath(relativePath) ?: continue
                backupFolders += songFolder
                draftFileCount++
                if (songFolder.isEmpty()) continue

                val fileUri = Uri.withAppendedPath(
                    mediaStoreCollection(),
                    it.getLong(idColumn).toString()
                )
                val draftContent = contentResolver.openInputStream(fileUri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { reader -> reader.readText() }
                    ?: throw IOException("MediaStore could not read $fileName")
                val draftName = fileName.substringBeforeLast('.')
                songsByFolder[songFolder].orEmpty().forEach { song ->
                    matchedSongIds += song.id
                    restored.getOrPut(song.id) { mutableListOf() }
                        .add(draftName to draftContent)
                }
            }
        }
        Log.d(
            BACKUP_LOG_TAG,
            "MediaStore scan found ${backupFolders.size} backup folders and $draftFileCount text files; " +
                "matched ${matchedSongIds.size} songs and restored ${restored.values.sumOf { it.size }} drafts"
        )
        return restored
    }

    private fun saveWithLegacyFileApi(songFolder: String, fileName: String, content: String) {
        requireLegacyPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "$BACKUP_FOLDER_NAME/$songFolder"
        )
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Could not create the lyrics backup folder")
        }
        val target = File(directory, fileName)
        val temporary = File.createTempFile("draft-", ".tmp", directory)
        try {
            temporary.writeText(content, Charsets.UTF_8)
            android.system.Os.rename(temporary.absolutePath, target.absolutePath)
        } finally {
            temporary.delete()
        }
    }

    private fun restoreWithLegacyFileApi(
        existingSongs: List<Song>
    ): Map<String, List<Pair<String, String>>> {
        requireLegacyPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        val backupDirectory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            BACKUP_FOLDER_NAME
        )
        if (!backupDirectory.exists()) return emptyMap()
        if (!backupDirectory.isDirectory) {
            throw IOException("The lyrics backup path is not a directory")
        }

        val songsByFolder = existingSongs.groupBy { sanitizeName(it.title, "Untitled Song") }
        val restored = mutableMapOf<String, MutableList<Pair<String, String>>>()
        val songDirectories = backupDirectory.listFiles()
            ?: throw IOException("Could not read the lyrics backup folder")
        val backupFolders = songDirectories.filter { it.isDirectory }
        val matchedSongIds = mutableSetOf<String>()
        var draftFileCount = 0
        backupFolders.forEach { songDirectory ->
            val matchingSongs = songsByFolder[songDirectory.name].orEmpty()
            if (matchingSongs.isEmpty()) return@forEach
            val draftFiles = songDirectory.listFiles()
                ?: throw IOException("Could not read backup folder ${songDirectory.name}")
            draftFiles
                .filter { it.isFile && it.extension.equals("txt", ignoreCase = true) }
                .forEach { draftFile ->
                    val content = draftFile.readText(Charsets.UTF_8)
                    val draftName = draftFile.nameWithoutExtension
                    draftFileCount++
                    matchingSongs.forEach { song ->
                        matchedSongIds += song.id
                        restored.getOrPut(song.id) { mutableListOf() }
                            .add(draftName to content)
                    }
                }
        }
        Log.d(
            BACKUP_LOG_TAG,
            "File scan found ${backupFolders.size} backup folders and $draftFileCount text files; " +
                "matched ${matchedSongIds.size} songs and restored ${restored.values.sumOf { it.size }} drafts"
        )
        return restored
    }

    private fun songFolderFromRelativePath(relativePath: String): String? {
        val normalizedPath = relativePath.replace('\\', '/').trimEnd('/')
        if (!normalizedPath.startsWith(BACKUP_RELATIVE_PATH, ignoreCase = true)) return null
        val relativeFolder = normalizedPath.substring(BACKUP_RELATIVE_PATH.length)
        if (relativeFolder.isEmpty()) return null
        return relativeFolder.substringBefore('/')
    }

    private fun requireLegacyPermission(permission: String) {
        if (applicationContext.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Missing required storage permission: $permission")
        }
    }

    private fun mediaStoreCollection(): Uri =
        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private fun sanitizeName(value: String, fallback: String): String {
        val cleanName = value
            .replace(INVALID_FILE_NAME_CHARACTERS, "_")
            .filterNot { it.isISOControl() }
            .trim()
            .trim('.')
            .take(MAX_NAME_LENGTH)
            .trim()
        return cleanName.ifEmpty { fallback }
    }

    private companion object {
        const val BACKUP_LOG_TAG = "BeatNoteBackup"
        const val BACKUP_FOLDER_NAME = "BeatNote"
        const val BACKUP_RELATIVE_PATH = "Documents/BeatNote/"
        const val TEXT_MIME_TYPE = "text/plain"
        const val MAX_NAME_LENGTH = 100
        val INVALID_FILE_NAME_CHARACTERS = Regex("""[\\/:*?"<>|]""")
    }
}
