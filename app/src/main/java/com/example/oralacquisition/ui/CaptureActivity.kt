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
            OralArea(1, "Extraoral"),
            OralArea(2, "Upper Labial Mucosa"),
            OralArea(3, "Lingual Labial Mucosa"),
            OralArea(4, "Anterior Bite"),
            OralArea(5, "Right Bite"),
            OralArea(6, "Left Bite"),
            OralArea(7, "Right Buccal Mucosa"),
            OralArea(8, "Left Buccal Mucosa"),
            OralArea(9, "Palate"),
            OralArea(10, "Upper Lingual"),
            OralArea(11, "Floor of Mouth"),
            OralArea(12, "Lower Lingual"),
            OralArea(13, "Tongue Surface"),
            OralArea(14, "Upper Right Retract"),
            OralArea(15, "Upper Left Retract"),
            OralArea(16, "Lower Left Retract"),
            OralArea(17, "Lower Right Retract"),
            OralArea(18, "Pathology")
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
        var successfullyRecovered = false

        // Fallback: camera app completely ignored our output file and saved to its own gallery
        if (!written && !savedInOurFolder) {
            val latest = StorageUtil.latestImageAfter(this, captureStartedAt)
            if (latest != null && latest != uri) {
                val copied = StorageUtil.copyPhotoIntoArea(this, "${patient.name}_${patient.opNumber}", area.name, latest)
                if (copied != null) {
                    deletePhoto(uri, pendingFilePath)
                    uri = copied.uri
                    finalPath = copied.filePath
                    written = true
                    savedInOurFolder = true
                    successfullyRecovered = true
                }
            }
        }

        if ((success || written) && uri != null) {
            // If it wasn't a recovered gallery photo, it's our temp file. Copy to MediaStore.
            if (!successfullyRecovered) {
                val copied = StorageUtil.copyPhotoIntoArea(this, "${patient.name}_${patient.opNumber}", area.name, uri)
                if (copied != null) {
                    deletePhoto(uri, pendingFilePath)
                    uri = copied.uri
                    finalPath = copied.filePath
                }
            }
            StorageUtil.finalizeMediaStoreUri(this, uri)
            area.photoUri = uri
            area.photoPath = finalPath ?: queryDataPath(uri)
            areaAdapter.notifyDataSetChanged()
            Toast.makeText(this, "Photo saved to ${area.name}", Toast.LENGTH_SHORT).show()
        } else {
            deletePhoto(uri, pendingFilePath)
            Toast.makeText(this, "Capture cancelled", Toast.LENGTH_SHORT).show()
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val areaIds = ArrayList<Int>()
        val areaUris = ArrayList<String>()
        val areaPaths = ArrayList<String>()
        val areaNames = ArrayList<String>()
        
        for (area in areas) {
            areaIds.add(area.id)
            areaNames.add(area.name)
            areaUris.add(area.photoUri?.toString() ?: "")
            areaPaths.add(area.photoPath ?: "")
        }
        
        outState.putIntegerArrayList("areaIds", areaIds)
        outState.putStringArrayList("areaNames", areaNames)
        outState.putStringArrayList("areaUris", areaUris)
        outState.putStringArrayList("areaPaths", areaPaths)
        
        outState.putInt("currentAreaId", currentArea?.id ?: -1)
        outState.putString("pendingUri", pendingUri?.toString())
        outState.putString("pendingFilePath", pendingFilePath)
        outState.putLong("captureStartedAt", captureStartedAt)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        patient = intent.getSerializableExtra("patient") as? Patient
            ?: Patient(name = "Unknown", opNumber = "Unknown")
            
        if (savedInstanceState != null) {
            val areaIds = savedInstanceState.getIntegerArrayList("areaIds")
            val areaNames = savedInstanceState.getStringArrayList("areaNames")
            val areaUris = savedInstanceState.getStringArrayList("areaUris")
            val areaPaths = savedInstanceState.getStringArrayList("areaPaths")
            
            if (areaIds != null && areaNames != null && areaUris != null && areaPaths != null) {
                areas.clear()
                for (i in areaIds.indices) {
                    val uriStr = areaUris[i]
                    val pathStr = areaPaths[i]
                    areas.add(
                        OralArea(
                            id = areaIds[i],
                            name = areaNames[i],
                            photoUri = if (uriStr.isNotEmpty()) Uri.parse(uriStr) else null,
                            photoPath = if (pathStr.isNotEmpty()) pathStr else null
                        )
                    )
                }
            }
            
            val currentAreaId = savedInstanceState.getInt("currentAreaId", -1)
            if (currentAreaId != -1) {
                currentArea = areas.find { it.id == currentAreaId }
            }
            
            val pendingUriStr = savedInstanceState.getString("pendingUri")
            if (pendingUriStr != null) {
                pendingUri = Uri.parse(pendingUriStr)
            }
            
            pendingFilePath = savedInstanceState.getString("pendingFilePath")
            captureStartedAt = savedInstanceState.getLong("captureStartedAt", 0L)
        }

        binding.tvPatientName.text = "Patient: ${patient.name}"
        binding.tvOpNumber.text = "OP Number: ${patient.opNumber}"

        binding.toolbar.title = "Capture Photos"
        binding.toolbar.setTitleTextColor(resources.getColor(android.R.color.white, null))
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            // Cancel session and return to main
            finish()
        }

        areaAdapter = OralAreaAdapter(
            areas = areas,
            onCapture = { area -> launchCamera(area) },
            onRemove = { area -> removePhoto(area) }
        )

        binding.rvAreas.layoutManager = LinearLayoutManager(this)
        binding.rvAreas.adapter = areaAdapter

        binding.btnAddPathology.setOnClickListener {
            val currentCount = areas.count { it.name.startsWith("Pathology") }
            val newId = areas.maxOf { it.id } + 1
            areas.add(OralArea(newId, "Pathology ${currentCount + 1}"))
            areaAdapter.notifyItemInserted(areas.size - 1)
            binding.rvAreas.scrollToPosition(areas.size - 1)
        }

        binding.btnSubmit.setOnClickListener {
            val capturedCount = areas.count { it.photoUri != null }
            Toast.makeText(this, "Session submitted with $capturedCount photos!", Toast.LENGTH_SHORT).show()
            finish()
        }
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
        val location = StorageUtil.createTempPhotoLocation(this, "${patient.name}_${patient.opNumber}", area.name)
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