package com.example.oralacquisition.util

import android.content.Context
import android.net.Uri
import com.example.oralacquisition.data.OralArea
import com.example.oralacquisition.data.Patient

object SessionStore {

    private const val PREFS = "oral_session"
    private const val KEY_ACTIVE = "active"
    private const val KEY_TS = "ts"
    private const val KEY_PATIENT_NAME = "patient_name"
    private const val KEY_PATIENT_OP = "patient_op"
    private const val KEY_PATIENT_AGE = "patient_age"
    private const val KEY_AREA_COUNT = "area_count"
    private const val FRESH_WINDOW_MS = 30L * 60L * 1000L

    data class SessionInfo(
        val patient: Patient,
        val areas: MutableList<OralArea>
    )

    fun save(
        context: Context,
        patientName: String,
        patientOp: String,
        patientAge: String,
        areas: List<OralArea>
    ) {
        val editor = prefs(context).edit()
        editor.putBoolean(KEY_ACTIVE, true)
        editor.putLong(KEY_TS, System.currentTimeMillis())
        editor.putString(KEY_PATIENT_NAME, patientName)
        editor.putString(KEY_PATIENT_OP, patientOp)
        editor.putString(KEY_PATIENT_AGE, patientAge)
        editor.putInt(KEY_AREA_COUNT, areas.size)
        areas.forEachIndexed { index, area ->
            editor.putInt("id_$index", area.id)
            editor.putString("name_$index", area.name)
            editor.putString("uri_$index", area.photoUri?.toString() ?: "")
            editor.putString("path_$index", area.photoPath ?: "")
        }
        editor.apply()
    }

    fun load(context: Context): SessionInfo? {
        val sp = prefs(context)
        if (!sp.getBoolean(KEY_ACTIVE, false)) return null
        val count = sp.getInt(KEY_AREA_COUNT, 0)
        if (count <= 0) return null
        val patient = Patient(
            name = sp.getString(KEY_PATIENT_NAME, "Unknown") ?: "Unknown",
            opNumber = sp.getString(KEY_PATIENT_OP, "Unknown") ?: "Unknown",
            age = sp.getString(KEY_PATIENT_AGE, "") ?: ""
        )
        val areas = ArrayList<OralArea>(count)
        for (i in 0 until count) {
            val uriStr = sp.getString("uri_$i", "") ?: ""
            val pathStr = sp.getString("path_$i", "") ?: ""
            areas.add(
                OralArea(
                    id = sp.getInt("id_$i", 0),
                    name = sp.getString("name_$i", "Area ${i + 1}") ?: "Area ${i + 1}",
                    photoUri = uriStr.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) },
                    photoPath = pathStr.takeIf { it.isNotEmpty() }
                )
            )
        }
        return SessionInfo(patient, areas)
    }

    fun isFresh(context: Context): Boolean {
        val ts = prefs(context).getLong(KEY_TS, 0L)
        return ts > 0L && System.currentTimeMillis() - ts < FRESH_WINDOW_MS
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}