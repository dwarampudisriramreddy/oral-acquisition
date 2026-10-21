package com.example.oralacquisition.data

import android.net.Uri

data class OralArea(
    val id: Int = 0,
    val name: String,
    var photoPath: String? = null,
    var photoUri: Uri? = null
)
