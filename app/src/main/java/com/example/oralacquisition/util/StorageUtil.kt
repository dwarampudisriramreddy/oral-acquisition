package com.example.oralacquisition.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

object StorageUtil {

    const val BASE_FOLDER_NAME = "OralAcquisition"
    private const val BASE_PATH = "Pictures/$BASE_FOLDER_NAME"

    data class PhotoLocation(
        val uri: Uri,
        val filePath: String?
    )

    fun createPhotoLocation(
        context: Context,
        patientName: String,
        areaName: String
    ): PhotoLocation {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PhotoLocation(
                uri = createMediaStoreUri(
                    context, patientName, areaName
                ),
                filePath = null
            )
        } else {
            val file = createLegacyFile(
                context, patientName, areaName
            )
            PhotoLocation(
                uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                ),
                filePath = file.absolutePath
            )
        }
    }

    fun createTempPhotoLocation(
        context: Context,
        patientName: String,
        areaName: String
    ): PhotoLocation {
        val folder = File(context.getExternalFilesDir(null), "OralAcquisition/${sanitize(patientName)}/${sanitize(areaName)}")
        if (!folder.exists()) folder.mkdirs()
        val file = File(folder, "${System.currentTimeMillis()}.jpg")
        return PhotoLocation(
            uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
            filePath = file.absolutePath
        )
    }

    fun finalizeMediaStoreUri(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, values, null, null)
        }
    }

    fun copyPhotoIntoArea(
        context: Context,
        patientName: String,
        areaName: String,
        sourceUri: Uri
    ): PhotoLocation? {
        val destination = createPhotoLocation(context, patientName, areaName)
        val copied = runCatching {
            val input = context.contentResolver.openInputStream(sourceUri) ?: return null
            input.use { source ->
                val output = context.contentResolver.openOutputStream(
                    destination.uri, "w"
                ) ?: throw IllegalStateException("Cannot open destination")
                output.use { dest ->
                    source.copyTo(dest)
                }
            }
            true
        }.getOrElse { false }
        if (!copied) {
            deletePhoto(context, destination)
            return null
        }
        finalizeMediaStoreUri(context, destination.uri)
        return destination
    }

    fun latestImageAfter(context: Context, afterMillis: Long): Uri? {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ${afterMillis / 1000}"
        return context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            selection,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                Uri.withAppendedPath(collection, cursor.getLong(0).toString())
            } else {
                null
            }
        }
    }

    fun latestImageInFolder(context: Context, afterMillis: Long): Boolean {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.RELATIVE_PATH
        } else {
            MediaStore.Images.Media.DATA
        }
        val selection =
            "$pathColumn LIKE ? AND ${MediaStore.Images.Media.DATE_ADDED} >= ${afterMillis / 1000}"
        val selectionArgs = arrayOf("%Pictures/$BASE_FOLDER_NAME/%")
        return context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            cursor.moveToFirst()
        } ?: false
    }

    data class PhotoItem(
        val uri: Uri?,
        val filePath: String?,
        val label: String
    )

    fun queryAllPhotos(context: Context): List<PhotoItem> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            queryMediaStorePhotos(context)
        } else {
            queryLegacyPhotos(context)
        }
    }

    private fun queryMediaStorePhotos(context: Context): List<PhotoItem> {
        val collection = MediaStore.Images.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL_PRIMARY
        )
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("$BASE_PATH/%")
        return context.contentResolver.query(
            collection,
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.RELATIVE_PATH
            ),
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val items = ArrayList<PhotoItem>()
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                val relativePath = cursor.getString(
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                ) ?: ""
                items.add(
                    PhotoItem(
                        uri = Uri.withAppendedPath(collection, id.toString()),
                        filePath = null,
                        label = labelFromPath(relativePath)
                    )
                )
            }
            items
        } ?: emptyList()
    }

    private fun queryLegacyPhotos(context: Context): List<PhotoItem> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Images.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("%/Pictures/$BASE_FOLDER_NAME/%")
        return context.contentResolver.query(
            collection,
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA
            ),
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val items = ArrayList<PhotoItem>()
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                val data = cursor.getString(
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                ) ?: ""
                items.add(
                    PhotoItem(
                        uri = Uri.withAppendedPath(collection, id.toString()),
                        filePath = data,
                        label = labelFromPath(data)
                    )
                )
            }
            items
        } ?: emptyList()
    }

    private fun labelFromPath(path: String): String {
        val segments = path.split("/").filter { it.isNotBlank() }
        val baseIndex = segments.indexOf(BASE_FOLDER_NAME)
        if (baseIndex == -1) return BASE_FOLDER_NAME
        var parts = segments.subList(baseIndex + 1, segments.size)
        if (parts.isNotEmpty() && parts.last().contains('.')) {
            parts = parts.subList(0, parts.size - 1)
        }
        return if (parts.isEmpty()) BASE_FOLDER_NAME else parts.joinToString("/")
    }

    fun deletePhoto(context: Context, location: PhotoLocation) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.delete(location.uri, null, null)
        } else {
            location.filePath?.let { path ->
                File(path).takeIf { it.exists() }?.delete()
            }
        }
    }

    private fun createMediaStoreUri(
        context: Context,
        patientName: String,
        areaName: String
    ): Uri {
        val collection = MediaStore.Images.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL_PRIMARY
        )
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "$BASE_PATH/${sanitize(patientName)}/${sanitize(areaName)}"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        return context.contentResolver.insert(collection, values)!!
    }

    private fun createLegacyFile(
        context: Context,
        patientName: String,
        areaName: String
    ): File {
        val folder = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "$BASE_PATH/${sanitize(patientName)}/${sanitize(areaName)}"
        )
        if (!folder.exists()) {
            folder.mkdirs()
        }
        return File(folder, "${System.currentTimeMillis()}.jpg")
    }

    private fun sanitize(name: String): String {
        return name.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
    }
}