package com.alananasss.kittytune.ui.player.vinyl

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Settings of the vinyl player, kept as Compose state so every screen that reads them updates
 * the moment one of them changes (the player itself, the customization screen, the toggle tile).
 */
object VinylSettings {
    private const val PREFS = "vinyl_mode"

    /** Records are cut for one speed; everything else is played relative to it. */
    enum class Rpm(val label: String, val rpm: Float) {
        RPM_16("16", 16f + 2f / 3f),
        RPM_33("33⅓", 33f + 1f / 3f),
        RPM_45("45", 45f),
        RPM_78("78", 78f);

        /** Playback speed when a record cut at 33⅓ is spun at this speed. */
        val speed: Float get() = rpm / RPM_33.rpm
    }

    var enabled by mutableStateOf(true)
        private set
    var spinUpDown by mutableStateOf(true)
        private set
    var rpm by mutableStateOf(Rpm.RPM_33)
        private set
    var scratchEnabled by mutableStateOf(true)
        private set

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = p.getBoolean("enabled", true)
        spinUpDown = p.getBoolean("spin_up_down", true)
        scratchEnabled = p.getBoolean("scratch", true)
        rpm = runCatching { Rpm.valueOf(p.getString("rpm", Rpm.RPM_33.name)!!) }.getOrDefault(Rpm.RPM_33)
        loaded = true
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        prefs(context).edit().putBoolean("enabled", value).apply()
    }

    fun setSpinUpDown(context: Context, value: Boolean) {
        spinUpDown = value
        prefs(context).edit().putBoolean("spin_up_down", value).apply()
    }

    fun setScratchEnabled(context: Context, value: Boolean) {
        scratchEnabled = value
        prefs(context).edit().putBoolean("scratch", value).apply()
    }

    fun setRpm(context: Context, value: Rpm) {
        rpm = value
        prefs(context).edit().putString("rpm", value.name).apply()
    }
}
