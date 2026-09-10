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

    fun finalizeMediaStoreUri(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, values, null, null)
        }
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