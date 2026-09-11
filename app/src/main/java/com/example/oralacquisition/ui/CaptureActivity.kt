package com.example.oralacquisition.ui

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.oralacquisition.adapter.OralAreaAdapter
import com.example.oralacquisition.data.OralArea
import com.example.oralacquisition.data.Patient
import com.example.oralacquisition.databinding.ActivityCaptureBinding
import com.example.oralacquisition.util.StorageUtil
import java.io.File

class CaptureActivity : AppCompatActivity() {

    companion object {
        private val ORAL_AREAS = listOf(
            OralArea(1, "Upper Labial Mucosa"),
            OralArea(2, "Lower Labial Mucosa"),
            OralArea(3, "Right Buccal Mucosa"),
            OralArea(4, "Left Buccal Mucosa"),
            OralArea(5, "Palate"),
            OralArea(6, "Tongue"),
            OralArea(7, "Floor of the Mouth"),
            OralArea(8, "Right Bite"),
            OralArea(9, "Anterior Bite"),
            OralArea(10, "Left Bite")
        )
    }

    private lateinit var binding: ActivityCaptureBinding
    private lateinit var patient: Patient
    private lateinit var areaAdapter: OralAreaAdapter
    private val areas = ORAL_AREAS.map { it.copy() }.toMutableList()
    private var currentArea: OralArea? = null
    private var pendingUri: Uri? = null
    private var pendingFilePath: String? = null
    private var captureStartedAt = 0L

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val area = currentArea ?: return@registerForActivityResult
        var uri = pendingUri
        var written = uri != null && photoWritten(uri, pendingFilePath)
        val folderFresh = StorageUtil.latestImageInFolder(this, captureStartedAt)
        var savedInOurFolder = uri != null && (written || folderFresh)

        var finalPath = pendingFilePath

        if (!success && !savedInOurFolder) {
            val latest = StorageUtil.latestImageAfter(this, captureStartedAt)
            if (latest != null && latest != uri) {
                val copied = StorageUtil.copyPhotoIntoArea(this, patient.name, area.name, latest)
                if (copied != null) {
                    deletePhoto(uri, pendingFilePath)
                    uri = copied.uri
                    finalPath = copied.filePath
                    written = true
                    savedInOurFolder = true
                }
            }
        }

        if (success || savedInOurFolder) {
            if (uri != null) {
                StorageUtil.finalizeMediaStoreUri(this, uri)
                area.photoUri = uri
                area.photoPath = finalPath ?: queryDataPath(uri)
                areaAdapter.notifyDataSetChanged()
                Toast.makeText(this, "Photo saved to ${area.name}", Toast.LENGTH_SHORT).show()
            }
        } else {
            val debug = buildCancelledDebug(
                success = success,
                uri = uri,
                written = written,
                folderFresh = folderFresh
            )
            Log.w("OralCapture", debug)
            deletePhoto(uri, pendingFilePath)
            AlertDialog.Builder(this)
                .setTitle("Capture cancelled")
                .setMessage(debug)
                .setPositiveButton("OK", null)
                .show()
        }
        pendingUri = null
        pendingFilePath = null
        currentArea = null
        captureStartedAt = 0L
    }

    private fun buildCancelledDebug(
        success: Boolean,
        uri: Uri?,
        written: Boolean,
        folderFresh: Boolean
    ): String {
        return buildString {
            append("Capture debug:\n")
            append("cameraResultOK=$success\n")
            append("outputUriProvided=${uri != null}\n")
            append("photoFileHasData=$written\n")
            append("newPhotoInAppFolder=$folderFresh\n")
            append("photoWrittenDebug=$photoWrittenDebug\n")
            append("elapsedSeconds=${(System.currentTimeMillis() - captureStartedAt) / 1000}\n")
            append("androidSdk=${Build.VERSION.SDK_INT}\n")
            append("Share these lines to debug")
        }
    }

    private val writePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val area = currentArea
        if (granted && area != null) {
            launchCamera(area)
        } else {
            Toast.makeText(this, "Storage permission is required", Toast.LENGTH_SHORT).show()
            currentArea = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        patient = intent.getSerializableExtra("patient") as? Patient
            ?: Patient(name = "Unknown", opNumber = "Unknown")

        binding.tvPatientName.text = "Patient: ${patient.name}"
        binding.tvOpNumber.text = "OP Number: ${patient.opNumber}"

        binding.toolbar.title = "Capture Photos"
        binding.toolbar.setTitleTextColor(resources.getColor(android.R.color.white, null))

        areaAdapter = OralAreaAdapter(
            areas = areas,
            onCapture = { area -> launchCamera(area) },
            onRemove = { area -> removePhoto(area) }
        )

        binding.rvAreas.layoutManager = LinearLayoutManager(this)
        binding.rvAreas.adapter = areaAdapter
    }

    private fun launchCamera(area: OralArea) {
        currentArea = area
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                writePermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                return
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        val location = StorageUtil.createPhotoLocation(this, patient.name, area.name)
        pendingUri = location.uri
        pendingFilePath = location.filePath
        captureStartedAt = System.currentTimeMillis()
        takePictureLauncher.launch(location.uri)
    }

    private var photoWrittenDebug = ""

    private fun photoWritten(uri: Uri?, filePath: String?): Boolean {
        photoWrittenDebug = ""
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (uri == null) {
                photoWrittenDebug = "uri is null"
                return false
            }
            var cursor: Cursor? = null
            try {
                cursor = contentResolver.query(
                    uri,
                    arrayOf(MediaStore.Images.Media.SIZE),
                    null,
                    null,
                    null
                )
                if (cursor != null && cursor.moveToFirst()) {
                    val size = cursor.getLong(0)
                    if (size > 0L) {
                        return true
                    } else {
                        photoWrittenDebug += "cursor size is $size; "
                    }
                } else {
                    photoWrittenDebug += "cursor is null or empty; "
                }
            } catch (e: Exception) {
                photoWrittenDebug += "cursor error: ${e.message}; "
            } finally {
                cursor?.close()
            }
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val hasData = stream.read() != -1
                    if (!hasData) photoWrittenDebug += "stream empty; "
                    return hasData
                } ?: run {
                    photoWrittenDebug += "openInputStream returned null; "
                    return false
                }
            } catch (e: Exception) {
                photoWrittenDebug += "stream error: ${e.message}; "
                false
            }
        } else {
            val file = filePath?.let { File(it) }
            val exists = file?.exists() == true
            val length = file?.length() ?: -1L
            if (!exists || length <= 0L) {
                photoWrittenDebug = "file exists=$exists length=$length"
            }
            exists && length > 0L
        }
    }

    private fun removePhoto(area: OralArea) {
        deletePhoto(area.photoUri, area.photoPath)
        area.photoUri = null
        area.photoPath = null
        areaAdapter.notifyDataSetChanged()
    }

    private fun deletePhoto(uri: Uri?, filePath: String?) {
        if (uri == null && filePath == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            uri?.let { contentResolver.delete(it, null, null) }
        } else {
            filePath?.let { path ->
                File(path).takeIf { it.exists() }?.delete()
            }
        }
    }

    private fun queryDataPath(uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(
                uri,
                arrayOf(MediaStore.Images.Media.DATA),
                null,
                null,
                null
            ) ?: return null
            if (cursor.moveToFirst()) {
                cursor.getString(
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                )
            } else {
                null
            }
        } finally {
            cursor?.close()
        }
    }
}