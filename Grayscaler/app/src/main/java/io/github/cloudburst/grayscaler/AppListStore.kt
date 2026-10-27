package io.github.cloudburst.grayscaler

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Context.USAGE_STATS_SERVICE
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.io.File

data class AppEntry(
    val packageName: String,
    val appName: String,
    val icon: ImageBitmap
)

class AppListStore(
    val context: Context
) {

    val path = context.filesDir.resolve("apps.txt")
    val delayPath = context.filesDir.resolve("delay.txt")
    var whitelist: Boolean = true
    var delayMs: Long = DEFAULT_DELAY_MS
    var toggledApps: Set<String> = emptySet()

    // Settings file stamp: the service re-reads them only when the files changed.
    private var loadedStamp: Long = -1L

    fun save() {
        val sb = StringBuilder()
        sb.append(if (whitelist) "w\n" else "b\n")
        toggledApps.forEach { sb.append(it).append('\n') }
        writeAtomic(path, sb.toString())
        writeAtomic(delayPath, delayMs.toString())
    }

    fun load() {
        delayMs = try {
            delayPath.readText().trim().toLong().coerceIn(0L, 5000L)
        } catch (e: Exception) {
            DEFAULT_DELAY_MS
        }
        if (!path.exists()) return
        val stream = path.inputStream().bufferedReader()
        whitelist = stream.readLine() != "b"
        toggledApps = stream.readLines().filter { it.isNotBlank() }.toSet()
        stream.close()
    }

    /** Cheap (two file timestamp checks): reads from disk only when something changed. */
    fun reloadIfChanged() {
        val stamp = path.lastModified() * 31 + delayPath.lastModified()
        if (stamp == loadedStamp) return
        load()
        loadedStamp = stamp
    }

    fun shouldGrayScale(packageName: String): Boolean {
        return if (whitelist) !toggledApps.contains(packageName) else toggledApps.contains(packageName)
    }

    /** Heavy operation: call from a background thread. Icons are prepared once. */
    fun loadApps(): List<AppEntry> {
        val pm = context.packageManager
        return (toggledApps + listPackages()).mapNotNull { packageName ->
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val appName = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo).toBitmap(96, 96).asImageBitmap()
                AppEntry(packageName, appName, icon)
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.appName.lowercase() }
    }

    private fun writeAtomic(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        try {
            tmp.writeText(text)
            if (!tmp.renameTo(target)) {
                target.writeText(text)
                tmp.delete()
            }
        } catch (e: Exception) {
            target.writeText(text)
        }
    }

    private fun listPackages(): Set<String> {
        return try {
            val usageStatsManager = context.getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                0,
                System.currentTimeMillis()
            )
                .filter { it.packageName != context.packageName && it.totalTime > 0 }
                .sortedBy { -it.lastTimeUsed }
                .map { it.packageName }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    companion object {
        const val DEFAULT_DELAY_MS = 500L
    }
}

private val UsageStats.totalTime get() = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) totalTimeVisible else totalTimeInForeground
