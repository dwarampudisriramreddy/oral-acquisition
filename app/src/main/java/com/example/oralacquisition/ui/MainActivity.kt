package com.example.oralacquisition.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.oralacquisition.data.Patient
import com.example.oralacquisition.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnStart.setOnClickListener {
            val name = binding.etPatientName.text.toString().trim()
            val opNumber = binding.etOpNumber.text.toString().trim()

            if (name.isEmpty()) {
                binding.tilPatientName.error = "Patient name is required"
                return@setOnClickListener
            }
            if (opNumber.isEmpty()) {
                binding.tilOpNumber.error = "OP number is required"
                return@setOnClickListener
            }

            val patient = Patient(name = name, opNumber = opNumber)
            val intent = Intent(this, CaptureActivity::class.java)
            intent.putExtra("patient", patient)
            startActivity(intent)
        }
    }
}
