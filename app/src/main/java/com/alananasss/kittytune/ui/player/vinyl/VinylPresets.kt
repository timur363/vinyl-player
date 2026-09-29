package com.alananasss.kittytune.ui.player.vinyl

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.AudioEffectsState
import com.alananasss.kittytune.ui.player.EqualizerState
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Everything that shapes the sound: speed/pitch, effects and the equalizer. */
data class SoundSnapshot(
    val effects: AudioEffectsState,
    val equalizer: EqualizerState
)

data class SoundPreset(
    val name: String,
    val sound: SoundSnapshot
)

/**
 * Named presets ("Night vibe" = 45 rpm + bass + crackle) and settings remembered per track.
 * Stored as JSON in one SharedPreferences file.
 */
object VinylPresets {
    private const val PREFS = "vinyl_presets"
    private val gson = Gson()

    val presets = mutableStateListOf<SoundPreset>()
    val perTrack = mutableStateMapOf<String, SoundSnapshot>()

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        runCatching {
            val list: List<SoundPreset>? = gson.fromJson(
                p.getString("presets", null) ?: "[]",
                object : TypeToken<List<SoundPreset>>() {}.type
            )
            presets.clear()
            list?.let { presets.addAll(it) }
        }
        runCatching {
            val map: Map<String, SoundSnapshot>? = gson.fromJson(
                p.getString("per_track", null) ?: "{}",
                object : TypeToken<Map<String, SoundSnapshot>>() {}.type
            )
            perTrack.clear()
            map?.let { perTrack.putAll(it) }
        }
        loaded = true
    }

    private fun save(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("presets", gson.toJson(presets.toList()))
            .putString("per_track", gson.toJson(perTrack.toMap()))
            .apply()
    }

    fun addPreset(context: Context, name: String, sound: SoundSnapshot) {
        presets.removeAll { it.name.equals(name, ignoreCase = true) }
        presets.add(SoundPreset(name, sound))
        save(context)
    }

    fun deletePreset(context: Context, name: String) {
        presets.removeAll { it.name == name }
        save(context)
    }

    /** Stable key for a track across sources (Yandex ids survive being saved locally). */
    fun trackKey(track: Track): String {
        val ya = com.alananasss.kittytune.data.yandex.YandexMusic
        if (ya.isYandex(track)) {
            ya.yandexIds(track)?.let { return "yandex:${it.first}" }
        }
        return "${track.source ?: "soundcloud"}:${track.id}"
    }

    fun rememberForTrack(context: Context, track: Track, sound: SoundSnapshot) {
        perTrack[trackKey(track)] = sound
        save(context)
    }

    fun forgetForTrack(context: Context, track: Track) {
        perTrack.remove(trackKey(track))
        save(context)
    }

    fun forTrack(track: Track?): SoundSnapshot? = track?.let { perTrack[trackKey(it)] }
}
