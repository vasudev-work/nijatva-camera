package com.nijatva.camera

import android.app.Application
import android.content.Context
import java.io.File

/**
 * Writes the last uncaught exception to a file so the next launch can show it.
 *
 * Without this, a crash on a phone is invisible: the app closes and the only
 * trace lives in logcat, which needs a cable. Here the stack trace lands in the
 * app's own files directory and the activity offers to show it.
 */
class NijatvaApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val file = crashFile(this)
                file.parentFile?.mkdirs()
                file.writeText(
                    buildString {
                        appendLine("Nijatva crash report")
                        appendLine("thread: ${thread.name}")
                        appendLine(error.toString())
                        appendLine()
                        appendLine(error.stackTraceToString())
                    }
                )
            } catch (_: Throwable) {
                // Reporting must never mask the original failure.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    companion object {
        fun crashFile(context: Context): File =
            File(context.getExternalFilesDir(null) ?: context.filesDir, "last_crash.txt")
    }
}
