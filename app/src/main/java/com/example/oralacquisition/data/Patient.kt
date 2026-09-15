package com.example.oralacquisition.data

import java.io.Serializable

data class Patient(
    val name: String,
    val opNumber: String,
    val age: String = ""
) : Serializable