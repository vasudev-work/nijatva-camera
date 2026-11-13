package com.nijatva.camera.settings

import com.nijatva.camera.R

/**
 * Theme and accent, as offered on the Nijatva site's Appearance panel.
 *
 * Theme is the surface the app draws on; accent is the single colour the app uses
 * for selected modes, the shutter ring at rest and headings. Both are stored with
 * the rest of the settings, so they travel with a preset or a settings backup.
 */
enum class ThemeChoice(val labelRes: Int) {
    SYSTEM(R.string.theme_system),
    DARK(R.string.theme_dark),
    LIGHT(R.string.theme_light),
    BLACK(R.string.theme_black),
}

/**
 * The three accents the site ships. Saffron is the default: it is the Nijatva
 * colour, and the one the site's own Appearance panel starts on.
 */
enum class AccentChoice(val labelRes: Int, val argb: Int) {
    GREEN(R.string.accent_green, 0xFF1E7A46.toInt()),
    SAFFRON(R.string.accent_saffron, 0xFFD98431.toInt()),
    INDIGO(R.string.accent_indigo, 0xFF3F45A8.toInt()),
}
