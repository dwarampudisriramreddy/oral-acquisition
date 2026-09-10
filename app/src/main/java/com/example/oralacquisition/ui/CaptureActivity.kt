package com.example.oralacquisition.ui

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val area = currentArea
        if (result.resultCode == RESULT_OK && result.data != null) {
            val uri = result.data!!
                .getStringExtra(CameraCaptureActivity.EXTRA_PHOTO_URI)
                ?.let { Uri.parse(it) }
            val path = result.data!!
                .getStringExtra(CameraCaptureActivity.EXTRA_PHOTO_PATH)
                ?.ifEmpty { null }

            area?.photoUri = uri
            area?.photoPath = path ?: uri?.let { queryDataPath(it) }
            area?.let { areaAdapter.notifyDataSetChanged() }
            Toast.makeText(
                this,
                "Photo captured for ${area?.name ?: "area"}",
                Toast.LENGTH_SHORT
            ).show()
        }
        currentArea = null
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
        val intent = Intent(this, CameraCaptureActivity::class.java).apply {
            putExtra(CameraCaptureActivity.EXTRA_PATIENT_NAME, patient.name)
            putExtra(CameraCaptureActivity.EXTRA_AREA_NAME, area.name)
        }
        cameraLauncher.launch(intent)
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