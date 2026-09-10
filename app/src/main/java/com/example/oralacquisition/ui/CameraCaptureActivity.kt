package com.example.oralacquisition.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.oralacquisition.databinding.ActivityCameraCaptureBinding
import com.example.oralacquisition.util.StorageUtil
import java.io.File

class CameraCaptureActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PATIENT_NAME = "patient_name"
        const val EXTRA_AREA_NAME = "area_name"
        const val EXTRA_PHOTO_URI = "photo_uri"
        const val EXTRA_PHOTO_PATH = "photo_path"
    }

    private lateinit var binding: ActivityCameraCaptureBinding
    private lateinit var patientName: String
    private lateinit var areaName: String

    private val handler = Handler(Looper.getMainLooper())
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var boundCamera: androidx.camera.core.Camera? = null
    private var macroCameraId: String? = null
    private var macroAfSupported = false
    private var autoAfSupported = false
    private var macroOn = false
    private var currentZoom = 1.0f
    private var pinchStartDistance = -1f
    private var pinchStartZoom = 1.0f

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val cameraOk = grants[Manifest.permission.CAMERA] == true
        if (cameraOk) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        patientName = intent.getStringExtra(EXTRA_PATIENT_NAME) ?: "Unknown"
        areaName = intent.getStringExtra(EXTRA_AREA_NAME) ?: "Area"

        binding.btnClose.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        binding.btnMacro.setOnClickListener {
            macroOn = !macroOn
            updateMacroUi()
            bindCamera()
        }

        binding.btnFocus.setOnClickListener { triggerFocusAtCenter() }

        binding.btnZoomIn.setOnClickListener { applyZoom(currentZoom * 1.5f) }
        binding.btnZoomOut.setOnClickListener { applyZoom(currentZoom / 1.5f) }

        binding.pvPreview.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (event.pointerCount == 1) {
                        triggerFocus(event.x, event.y)
                    }
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    pinchStartDistance = pinchDistance(event)
                    pinchStartZoom = currentZoom
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount >= 2 && pinchStartDistance > 0f) {
                        val ratio = pinchDistance(event) / pinchStartDistance
                        applyZoom(pinchStartZoom * ratio)
                    }
                }
                MotionEvent.ACTION_POINTER_UP,
                MotionEvent.ACTION_UP -> pinchStartDistance = -1f
            }
            true
        }

        binding.btnShutter.setOnClickListener { capturePhoto() }

        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            startCamera()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
            discoverCameras()
            bindCamera()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun discoverCameras() {
        val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var bestId: String? = null
        var bestDistance = Float.MAX_VALUE
        for (id in cm.cameraIdList) {
            try {
                val characteristics = cm.getCameraCharacteristics(id)
                if (characteristics.get(CameraCharacteristics.LENS_FACING)
                    != CameraCharacteristics.LENS_FACING_BACK
                ) {
                    continue
                }
                val distance = characteristics.get(
                    CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE
                ) ?: Float.MAX_VALUE
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestId = id
                }
            } catch (_: Exception) {
            }
        }
        macroCameraId = bestId

        val macroId = macroCameraId
        macroAfSupported = if (macroId != null) {
            try {
                val modes = cm.getCameraCharacteristics(macroId)
                    .get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                modes?.any { it == CameraCharacteristics.CONTROL_AF_MODE_MACRO } == true
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }

        autoAfSupported = if (macroId != null) {
            try {
                val modes = cm.getCameraCharacteristics(macroId)
                    .get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                modes?.any { it == CameraCharacteristics.CONTROL_AF_MODE_AUTO } == true
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }

        if (macroId != null) {
            val defaultBackId = Camera2CameraInfo.from(
                findDefaultBackCamera() ?: return
            ).cameraId
            binding.tvMacroHint.text =
                if (macroId == defaultBackId) "Keep phone very close and zoom in" else "Macro lens detected"
        }
    }

    private fun findDefaultBackCamera(): androidx.camera.core.CameraInfo? {
        val provider = cameraProvider ?: return null
        for (info in provider.availableCameraInfos) {
            val isBack = try {
                Camera2CameraInfo.from(info)
                    .getCameraCharacteristic(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            } catch (_: Exception) {
                false
            }
            if (isBack) return info
        }
        return null
    }

    private fun macroSelector(): CameraSelector? {
        val id = macroCameraId ?: return null
        return CameraSelector.Builder().addCameraFilter { cameras ->
            listOfNotNull(
                cameras.firstOrNull {
                    runCatching { Camera2CameraInfo.from(it).cameraId }.getOrNull() == id
                }
            )
        }.build()
    }

    private fun updateMacroUi() {
        binding.btnMacro.text = if (macroOn) "MACRO ON" else "MACRO OFF"
        binding.btnMacro.setTextColor(
            ContextCompat.getColor(
                this,
                if (macroOn) android.R.color.holo_green_light else android.R.color.white
            )
        )
        binding.tvMacroHint.text =
            if (macroOn) "Macro on. Zoom +, tap to focus, shoot." else "Close up the camera to the area"
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        val selector = if (macroOn) macroSelector() else CameraSelector.DEFAULT_BACK_CAMERA
        val resolvedSelector = selector ?: CameraSelector.DEFAULT_BACK_CAMERA

        provider.unbindAll()

        val previewBuilder = Preview.Builder()
        val captureBuilder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(binding.pvPreview.display?.rotation ?: 0)

        if (macroOn) {
            val afMode = when {
                macroAfSupported -> CaptureRequest.CONTROL_AF_MODE_MACRO
                autoAfSupported -> CaptureRequest.CONTROL_AF_MODE_AUTO
                else -> null
            }
            if (afMode != null) {
                Camera2Interop.Extender(previewBuilder)
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, afMode)
                Camera2Interop.Extender(captureBuilder)
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, afMode)
            }
        }

        val preview = previewBuilder
            .build()
            .also { it.setSurfaceProvider(binding.pvPreview.surfaceProvider) }

        val imageCapture = captureBuilder.build()
        this.imageCapture = imageCapture

        try {
            val camera = provider.bindToLifecycle(
                this, resolvedSelector, preview, imageCapture
            )
            boundCamera = camera
            currentZoom = 1.0f
            updateZoomLabel()
            if (macroOn) {
                handler.postDelayed({ triggerFocusAtCenter() }, 700)
            } else {
                handler.removeCallbacksAndMessages(null)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to start camera: ${e.message}", Toast.LENGTH_SHORT).show()
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun triggerFocusAtCenter() {
        triggerFocus(
            binding.pvPreview.width / 2f,
            binding.pvPreview.height / 2f
        )
    }

    private fun applyZoom(target: Float) {
        val camera = boundCamera ?: return
        val maxZoom = camera.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
        currentZoom = target.coerceIn(1f, maxZoom)
        camera.cameraControl.setZoomRatio(currentZoom)
        updateZoomLabel()
    }

    private fun updateZoomLabel() {
        binding.tvZoom.text = String.format("%.1fx", currentZoom)
    }

    private fun pinchDistance(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun triggerFocus(x: Float, y: Float) {
        val camera = boundCamera ?: return
        try {
            val point = binding.pvPreview.meteringPointFactory.createPoint(x, y)
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            ).build()
            camera.cameraControl.startFocusAndMetering(action)
        } catch (_: Exception) {
        }
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return
        capture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bytes = image.toJpegBytes()
                        saveAndFinish(bytes)
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        this@CameraCaptureActivity,
                        "Capture failed: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    private fun saveAndFinish(bytes: ByteArray) {
        val location = StorageUtil.createPhotoLocation(this, patientName, areaName)
        val saved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                contentResolver.openOutputStream(location.uri)?.use {
                    it.write(bytes)
                    it.flush()
                }
                StorageUtil.finalizeMediaStoreUri(this, location.uri)
                true
            } catch (e: Exception) {
                contentResolver.delete(location.uri, null, null)
                false
            }
        } else {
            val path = location.filePath
            if (path != null) {
                try {
                    File(path).outputStream().use {
                        it.write(bytes)
                        it.flush()
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            } else {
                false
            }
        }

        if (saved) {
            val data = Intent().apply {
                putExtra(EXTRA_PHOTO_URI, location.uri.toString())
                putExtra(EXTRA_PHOTO_PATH, location.filePath ?: "")
            }
            setResult(RESULT_OK, data)
            finish()
        } else {
            StorageUtil.deletePhoto(this, location)
            Toast.makeText(this, "Could not save photo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ImageProxy.toJpegBytes(): ByteArray {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }
}