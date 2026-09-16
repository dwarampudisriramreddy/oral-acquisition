package com.example.oralacquisition.data

import java.io.Serializable

data class Patient(
    val name: String,
    val opNumber: String,
    val age: String = ""
) : Serializable {
    fun folderName(): String {
        return listOf(name, opNumber, age)
            .filter { it.isNotBlank() }
            .joinToString("_") { it.replace(Regex("[^A-Za-z0-9_\\-]"), "_") }
    }
}