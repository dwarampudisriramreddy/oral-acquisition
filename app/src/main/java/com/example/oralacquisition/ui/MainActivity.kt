package com.example.oralacquisition.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.example.oralacquisition.adapter.GalleryAdapter
import com.example.oralacquisition.data.Patient
import com.example.oralacquisition.databinding.ActivityMainBinding
import com.example.oralacquisition.util.StorageUtil
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var galleryAdapter: GalleryAdapter
    private var allGalleryItems: List<StorageUtil.PhotoItem> = emptyList()

    private val readPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            loadGallery()
        } else {
            binding.tvGalleryEmpty.text = "Storage permission needed to show photos"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnStart.setOnClickListener {
            val name = binding.etPatientName.text.toString().trim()
            val opNumber = binding.etOpNumber.text.toString().trim()

            if (name.isEmpty()) {
                binding.etPatientName.error = "Patient name is required"
                return@setOnClickListener
            }
            if (opNumber.isEmpty()) {
                binding.etOpNumber.error = "OP number is required"
                return@setOnClickListener
            }

            val patient = Patient(name = name, opNumber = opNumber)
            val intent = Intent(this, CaptureActivity::class.java)
            intent.putExtra("patient", patient)
            startActivity(intent)
        }
        
        binding.btnClear.setOnClickListener {
            binding.etPatientName.text?.clear()
            binding.etPatientName.error = null
            binding.etOpNumber.text?.clear()
            binding.etOpNumber.error = null
            binding.etPatientName.requestFocus()
        }

        binding.btnOpenGallery.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                type = "image/*"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(Intent.createChooser(intent, "Open Gallery"))
        }

        binding.etSearchGallery.doOnTextChanged { text, _, _, _ ->
            filterGallery(text?.toString() ?: "")
        }

        binding.rvGallery.layoutManager = GridLayoutManager(this, 3)
        galleryAdapter = GalleryAdapter(emptyList()) { item -> showPhotoPreview(item) }
        binding.rvGallery.adapter = galleryAdapter
    }

    override fun onResume() {
        super.onResume()
        loadGallery()
    }

    private fun filterGallery(query: String) {
        val filtered = if (query.isBlank()) {
            allGalleryItems
        } else {
            val terms = query.lowercase().split(" ")
            allGalleryItems.filter { item ->
                val label = item.label.lowercase()
                terms.all { term -> label.contains(term) }
            }
        }
        binding.tvGalleryEmpty.visibility = if (filtered.isEmpty()) ViewGroup.VISIBLE else ViewGroup.GONE
        binding.tvGalleryEmpty.text = if (allGalleryItems.isEmpty()) "No photos captured yet" else "No photos match your search"
        
        galleryAdapter = GalleryAdapter(filtered) { item -> showPhotoPreview(item) }
        binding.rvGallery.adapter = galleryAdapter
    }

    private fun loadGallery() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                readPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                return
            }
        }
        allGalleryItems = StorageUtil.queryAllPhotos(this)
        filterGallery(binding.etSearchGallery.text?.toString() ?: "")
    }

    private fun showPhotoPreview(item: StorageUtil.PhotoItem) {
        val imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.8).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            item.uri?.let { setImageURI(it) }
        }
        AlertDialog.Builder(this)
            .setTitle(item.label)
            .setView(imageView)
            .setPositiveButton("Close", null)
            .show()
    }
}