package com.example.oralacquisition.ui

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private var pendingAttachArea: OralArea? = null
    private var lastCancelDebug = ""
    private val handler = Handler(Looper.getMainLooper())

    private val pickPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val area = pendingAttachArea
        pendingAttachArea = null
        if (area != null && uri != null) {
            attachPhotoFromGallery(area, uri)
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val area = currentArea ?: return@registerForActivityResult
        val uri = pendingUri
        val photoInPlace = uri != null && photoWritten(uri, pendingFilePath)
        if ((success || photoInPlace) && uri != null) {
            StorageUtil.finalizeMediaStoreUri(this, uri)
            area.photoUri = uri
            area.photoPath = pendingFilePath ?: queryDataPath(uri)
            areaAdapter.notifyDataSetChanged()
            Toast.makeText(this, "Photo captured for ${area.name}", Toast.LENGTH_SHORT).show()
            pendingUri = null
            pendingFilePath = null
            currentArea = null
        } else {
            lastCancelDebug = debugCancelledReason(
                success = success,
                uri = uri,
                photoInPlace = photoInPlace,
                foundLatest = false
            )
            Log.w("OralCapture", lastCancelDebug)
            pendingUri = null
            pendingFilePath = null
            currentArea = null
            pendingAttachArea = area
            pollForNewPhoto(area, 0)
        }
    }

    private fun pollForNewPhoto(area: OralArea, attempt: Int) {
        if (pendingAttachArea !== area) return
        val found = StorageUtil.latestImageAfter(this, captureStartedAt)
        if (found != null) {
            showAttachDialog(area, found)
            return
        }
        if (attempt >= 4) {
            showNoPhotoDialog(area)
            return
        }
        handler.postDelayed({ pollForNewPhoto(area, attempt + 1) }, 2000)
    }

    private fun showAttachDialog(area: OralArea, uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("Photo found")
            .setMessage(
                "$lastCancelDebug\nnewGalleryPhotoFound=true\n\n" +
                    "ATTACH the photo the camera saved to its gallery to '${area.name}'?"
            )
            .setPositiveButton("Attach") { _, _ -> attachPhotoFromGallery(area, uri) }
            .setNegativeButton("Retake") { _, _ -> retake(area) }
            .show()
    }

    private fun showNoPhotoDialog(area: OralArea) {
        AlertDialog.Builder(this)
            .setTitle("Capture cancelled")
            .setMessage(
                "$lastCancelDebug\nnewGalleryPhotoFound=false\n\n" +
                    "No new photo was found after 8s." +
                    "\nChoose 'Gallery' to attach any photo, or Retake."
            )
            .setPositiveButton("Gallery") { _, _ ->
                pendingAttachArea = area
                pickPhotoLauncher.launch("image/*")
            }
            .setNegativeButton("Retake") { _, _ -> retake(area) }
            .show()
    }

    private fun retake(area: OralArea) {
        if (pendingAttachArea === area) {
            pendingAttachArea = null
        }
        launchCamera(area)
    }

    private fun debugCancelledReason(
        success: Boolean,
        uri: Uri?,
        photoInPlace: Boolean,
        foundLatest: Boolean
    ): String {
        return buildString {
            append("Capture debug:\n")
            append("cameraResultOK=$success\n")
            append("outputUriProvided=${uri != null}\n")
            append("photoSavedToOurFolder=$photoInPlace\n")
            append("newGalleryPhotoFound=$foundLatest\n")
            append("elapsedSeconds=${(System.currentTimeMillis() - captureStartedAt) / 1000}\n")
            append("androidSdk=${Build.VERSION.SDK_INT}\n")
            append("Only why-cancelled info above helps debugging")
        }
    }

    private fun attachPhotoFromGallery(area: OralArea, uri: Uri) {
        val destination = StorageUtil.copyPhotoIntoArea(
            this, patient.name, area.name, uri
        )
        if (destination != null) {
            area.photoUri = destination.uri
            area.photoPath = destination.filePath
            areaAdapter.notifyDataSetChanged()
            Toast.makeText(
                this,
                "Photo attached to ${area.name}",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(this, "Could not attach the photo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun photoWritten(uri: Uri?, filePath: String?): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (uri == null) return false
            var cursor: Cursor? = null
            try {
                cursor = contentResolver.query(
                    uri,
                    arrayOf(MediaStore.Images.Media.SIZE),
                    null,
                    null,
                    null
                )
                if (cursor != null && cursor.moveToFirst() && cursor.getLong(0) > 0L) {
                    return true
                }
            } catch (e: Exception) {
                // fall through to fd check below
            } finally {
                cursor?.close()
            }
            try {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
                    return fd.length > 0L
                } ?: return false
            } catch (e: Exception) {
                false
            }
        } else {
            val file = filePath?.let { File(it) }
            file != null && file.exists() && file.length() > 0L
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
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