package com.nijatva.camera.settings

import android.content.Context

enum class FlashMode(val label: String) {
    OFF("Off"), AUTO("Auto"), ON("On")
}

enum class HdrMode(val label: String) {
    OFF("Off"), ON("HDR+"), ENHANCED("Enhanced")
}

enum class TimerDelay(val seconds: Int, val label: String) {
    OFF(0, "Off"), THREE(3, "3 seconds"), TEN(10, "10 seconds")
}

enum class Ratio(val label: String, val subtitle: String) {
    FULL("Full image", "4:3 uses the whole sensor"),
    WIDE("Wide", "16:9 crops the top and bottom"),
}

enum class GridType(val label: String) {
    NONE("No grid"), THIRDS("3 x 3"), QUARTERS("4 x 4"), GOLDEN("Golden ratio")
}

/**
 * Everything the settings sheets can change.
 *
 * Only settings that change real behaviour live here. Google Camera's Advanced
 * page carries switches for things this app cannot do - HDRnet, Top Shot, depth
 * capture - and those are listed in docs/SETTINGS.md rather than faked with a
 * toggle that does nothing.
 */
data class Settings(
    val flash: FlashMode = FlashMode.OFF,
    val hdr: HdrMode = HdrMode.ON,
    val timer: TimerDelay = TimerDelay.OFF,
    val ratio: Ratio = Ratio.FULL,
    val grid: GridType = GridType.NONE,
    val framingHints: Boolean = true,
    val quickAccessControls: Boolean = false,
    val saveSelfieAsPreviewed: Boolean = true,
    val rawPlusJpeg: Boolean = false,
    val timerLight: Boolean = true,
    val wideColour: Boolean = false,
    val ois: Boolean = true,
    val rawBitDepth: Int = 16,
    val jpegQuality: Int = 97,
    val vibrationOnShutter: Boolean = true,
)

/**
 * Persists settings in SharedPreferences.
 *
 * Enums are stored by name, so reordering an enum cannot silently change a saved
 * value into a different one.
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("nijatva_settings", Context.MODE_PRIVATE)

    fun load(): Settings {
        val defaults = Settings()
        return Settings(
            flash = readEnum("flash", defaults.flash),
            hdr = readEnum("hdr", defaults.hdr),
            timer = readEnum("timer", defaults.timer),
            ratio = readEnum("ratio", defaults.ratio),
            grid = readEnum("grid", defaults.grid),
            framingHints = prefs.getBoolean("framingHints", defaults.framingHints),
            quickAccessControls = prefs.getBoolean("quickAccessControls", defaults.quickAccessControls),
            saveSelfieAsPreviewed = prefs.getBoolean("saveSelfieAsPreviewed", defaults.saveSelfieAsPreviewed),
            rawPlusJpeg = prefs.getBoolean("rawPlusJpeg", defaults.rawPlusJpeg),
            timerLight = prefs.getBoolean("timerLight", defaults.timerLight),
            wideColour = prefs.getBoolean("wideColour", defaults.wideColour),
            ois = prefs.getBoolean("ois", defaults.ois),
            rawBitDepth = prefs.getInt("rawBitDepth", defaults.rawBitDepth),
            jpegQuality = prefs.getInt("jpegQuality", defaults.jpegQuality),
            vibrationOnShutter = prefs.getBoolean("vibrationOnShutter", defaults.vibrationOnShutter),
        )
    }

    fun save(settings: Settings) {
        prefs.edit()
            .putString("flash", settings.flash.name)
            .putString("hdr", settings.hdr.name)
            .putString("timer", settings.timer.name)
            .putString("ratio", settings.ratio.name)
            .putString("grid", settings.grid.name)
            .putBoolean("framingHints", settings.framingHints)
            .putBoolean("quickAccessControls", settings.quickAccessControls)
            .putBoolean("saveSelfieAsPreviewed", settings.saveSelfieAsPreviewed)
            .putBoolean("rawPlusJpeg", settings.rawPlusJpeg)
            .putBoolean("timerLight", settings.timerLight)
            .putBoolean("wideColour", settings.wideColour)
            .putBoolean("ois", settings.ois)
            .putInt("rawBitDepth", settings.rawBitDepth)
            .putInt("jpegQuality", settings.jpegQuality)
            .putBoolean("vibrationOnShutter", settings.vibrationOnShutter)
            .apply()
    }

    private inline fun <reified T : Enum<T>> readEnum(key: String, fallback: T): T {
        val stored = prefs.getString(key, null) ?: return fallback
        return runCatching { enumValueOf<T>(stored) }.getOrDefault(fallback)
    }
}
