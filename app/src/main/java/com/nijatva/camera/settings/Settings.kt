package com.nijatva.camera.settings

import android.content.Context
import org.json.JSONObject

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
    val histogram: Boolean = false,
    val focusPeaking: Boolean = false,
    val recordAudio: Boolean = true,
    val astroSeconds: Int = 4,
    val astroFrames: Int = 6,
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
            histogram = prefs.getBoolean("histogram", defaults.histogram),
            focusPeaking = prefs.getBoolean("focusPeaking", defaults.focusPeaking),
            recordAudio = prefs.getBoolean("recordAudio", defaults.recordAudio),
            astroSeconds = prefs.getInt("astroSeconds", defaults.astroSeconds).coerceIn(2, 8),
            astroFrames = prefs.getInt("astroFrames", defaults.astroFrames).coerceIn(4, 10),
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
            .putBoolean("histogram", settings.histogram)
            .putBoolean("focusPeaking", settings.focusPeaking)
            .putBoolean("recordAudio", settings.recordAudio)
            .putInt("astroSeconds", settings.astroSeconds)
            .putInt("astroFrames", settings.astroFrames)
            .apply()
    }

    /** Serialises settings to JSON, for a backup or for moving a setup to another phone. */
    fun exportJson(s: Settings): String = JSONObject().apply {
        put("flash", s.flash.name)
        put("hdr", s.hdr.name)
        put("timer", s.timer.name)
        put("ratio", s.ratio.name)
        put("grid", s.grid.name)
        put("framingHints", s.framingHints)
        put("quickAccessControls", s.quickAccessControls)
        put("saveSelfieAsPreviewed", s.saveSelfieAsPreviewed)
        put("rawPlusJpeg", s.rawPlusJpeg)
        put("timerLight", s.timerLight)
        put("wideColour", s.wideColour)
        put("ois", s.ois)
        put("rawBitDepth", s.rawBitDepth)
        put("jpegQuality", s.jpegQuality)
        put("vibrationOnShutter", s.vibrationOnShutter)
        put("histogram", s.histogram)
        put("focusPeaking", s.focusPeaking)
        put("recordAudio", s.recordAudio)
        put("astroSeconds", s.astroSeconds)
        put("astroFrames", s.astroFrames)
    }.toString(2)

    /**
     * Parses JSON made by exportJson. Missing or invalid fields fall back to
     * defaults, and out-of-range numbers are clamped. Returns null only when the
     * text is not JSON at all.
     */
    fun importJsonOrNull(text: String): Settings? = importJson(text)

    fun importJson(text: String): Settings? {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val d = Settings()
        return Settings(
            flash = enumFrom(o, "flash", d.flash),
            hdr = enumFrom(o, "hdr", d.hdr),
            timer = enumFrom(o, "timer", d.timer),
            ratio = enumFrom(o, "ratio", d.ratio),
            grid = enumFrom(o, "grid", d.grid),
            framingHints = o.optBoolean("framingHints", d.framingHints),
            quickAccessControls = o.optBoolean("quickAccessControls", d.quickAccessControls),
            saveSelfieAsPreviewed = o.optBoolean("saveSelfieAsPreviewed", d.saveSelfieAsPreviewed),
            rawPlusJpeg = o.optBoolean("rawPlusJpeg", d.rawPlusJpeg),
            timerLight = o.optBoolean("timerLight", d.timerLight),
            wideColour = o.optBoolean("wideColour", d.wideColour),
            ois = o.optBoolean("ois", d.ois),
            rawBitDepth = if (o.optInt("rawBitDepth", d.rawBitDepth) == 10) 10 else 16,
            jpegQuality = o.optInt("jpegQuality", d.jpegQuality).coerceIn(85, 100),
            vibrationOnShutter = o.optBoolean("vibrationOnShutter", d.vibrationOnShutter),
            histogram = o.optBoolean("histogram", d.histogram),
            focusPeaking = o.optBoolean("focusPeaking", d.focusPeaking),
            recordAudio = o.optBoolean("recordAudio", d.recordAudio),
            astroSeconds = o.optInt("astroSeconds", d.astroSeconds).coerceIn(2, 8),
            astroFrames = o.optInt("astroFrames", d.astroFrames).coerceIn(4, 10),
        )
    }

    /** Puts every setting back to its default. */
    /** Clears the current settings. Saved presets are kept. */
    fun reset() {
        val editor = prefs.edit()
        prefs.all.keys
            .filterNot { it == PRESET_INDEX || it.startsWith(PRESET_PREFIX) }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    /** Names of saved presets, sorted. */
    fun presetNames(): List<String> =
        prefs.getStringSet(PRESET_INDEX, emptySet()).orEmpty().sorted()

    fun savePreset(name: String, s: Settings) {
        val names = HashSet(prefs.getStringSet(PRESET_INDEX, emptySet()).orEmpty()).apply { add(name) }
        prefs.edit()
            .putString(PRESET_PREFIX + name, exportJson(s))
            .putStringSet(PRESET_INDEX, names)
            .apply()
    }

    fun loadPreset(name: String): Settings? =
        prefs.getString(PRESET_PREFIX + name, null)?.let { importJson(it) }

    fun deletePreset(name: String) {
        val names = HashSet(prefs.getStringSet(PRESET_INDEX, emptySet()).orEmpty()).apply { remove(name) }
        prefs.edit()
            .remove(PRESET_PREFIX + name)
            .putStringSet(PRESET_INDEX, names)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumFrom(o: JSONObject, key: String, fallback: T): T =
        runCatching { enumValueOf<T>(o.getString(key)) }.getOrDefault(fallback)

    private inline fun <reified T : Enum<T>> readEnum(key: String, fallback: T): T {
        val stored = prefs.getString(key, null) ?: return fallback
        return runCatching { enumValueOf<T>(stored) }.getOrDefault(fallback)
    }
}

private const val PRESET_INDEX = "presetNames"
private const val PRESET_PREFIX = "preset:"
