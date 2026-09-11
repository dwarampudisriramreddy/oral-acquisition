package com.example.oralacquisition.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
    private var captureArea: OralArea? = null
    private var captureStartedAt = 0L
    private var pollActive = false
    private var pendingCopyArea: OralArea? = null
    private var pendingCopyUri: Uri? = null
    private val handler = Handler(Looper.getMainLooper())

    private val writePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val area = pendingCopyArea
        val uri = pendingCopyUri
        if (granted && area != null && uri != null) {
            attachPhotoFromGallery(area, uri)
        } else {
            pendingCopyArea = null
            pendingCopyUri = null
            Toast.makeText(this, "Storage permission is required", Toast.LENGTH_SHORT).show()
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

    override fun onResume() {
        super.onResume()
        val area = captureArea ?: return
        if (captureStartedAt == 0L || pollActive) return
        Log.d("OralCapture", "Checking for new photo for ${area.name}")
        pollActive = true
        pollForPhoto(area, 0)
    }

    private fun launchCamera(area: OralArea) {
        captureArea = area
        captureStartedAt = System.currentTimeMillis()
        openCameraApp()
    }

    private fun openCameraApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage("com.android.camera")
        if (launchIntent != null) {
            startActivity(launchIntent)
            return
        }
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val camera = packageManager.queryIntentActivities(mainIntent, 0).firstOrNull {
            it.activityInfo.packageName.lowercase().contains("camera")
        }
        if (camera != null) {
            startActivity(mainIntent.setPackage(camera.activityInfo.packageName))
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show()
            captureArea = null
        }
    }

    private fun pollForPhoto(area: OralArea, attempt: Int) {
        if (captureArea !== area) {
            pollActive = false
            return
        }
        val found = StorageUtil.latestImageAfter(this, captureStartedAt)
        if (found != null) {
            Log.d("OralCapture", "New photo found for ${area.name}")
            captureArea = null
            pollActive = false
            attachPhotoFromGallery(area, found)
            return
        }
        if (attempt >= 4) {
            captureArea = null
            pollActive = false
            Toast.makeText(
                this,
                "No photo found. Open Camera app and try again.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        handler.postDelayed({ pollForPhoto(area, attempt + 1) }, 2000)
    }

    private fun attachPhotoFromGallery(area: OralArea, uri: Uri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingCopyArea = area
                pendingCopyUri = uri
                writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        val destination = StorageUtil.copyPhotoIntoArea(
            this, patient.name, area.name, uri
        )
        if (destination != null) {
            area.photoUri = destination.uri
            area.photoPath = destination.filePath
            areaAdapter.notifyDataSetChanged()
            Toast.makeText(
                this,
                "Photo saved to ${area.name}",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(this, "Could not save the photo", Toast.LENGTH_SHORT).show()
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
}