package io.github.cloudburst.grayscaler

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

class MainService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private val store by lazy { AppListStore(this) }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        if (e.eventType != TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        if (IGNORED_PACKAGES.contains(pkg) || pkg == currentKeyboard()) return

        store.reloadIfChanged()
        val gray = store.shouldGrayScale(pkg)

        // Новое событие отменяет ещё не выполненное переключение (защита от дёрганья).
        cancelPending()
        // Если экран уже в нужном состоянии, ничего не делаем и ничего не пишем в систему.
        if (gray == isGray()) return

        val task = Runnable {
            pending = null
            if (gray) enableMonochrome() else disableMonochrome()
        }
        pending = task
        val delay = store.delayMs
        if (delay <= 0L) task.run() else handler.postDelayed(task, delay)
    }

    override fun onInterrupt() {
        cancelPending()
        disableMonochrome()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // Служба выключена: не оставляем экран в чёрно-белом режиме.
        cancelPending()
        disableMonochrome()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cancelPending()
        super.onDestroy()
    }

    private fun cancelPending() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
    }

    private fun currentKeyboard(): String? = try {
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')
    } catch (e: Exception) {
        null
    }

    private fun isGray(): Boolean = try {
        Settings.Secure.getInt(contentResolver, DISPLAY_DALTONIZER_ENABLED, OFF) == ON &&
            Settings.Secure.getInt(contentResolver, DISPLAY_DALTONIZER, -1) == MONOCHROME
    } catch (e: Exception) {
        false
    }

    // В систему пишем только то, что реально отличается от текущего значения.
    private fun enableMonochrome() {
        try {
            if (Settings.Secure.getInt(contentResolver, DISPLAY_DALTONIZER, -1) != MONOCHROME)
                Settings.Secure.putInt(contentResolver, DISPLAY_DALTONIZER, MONOCHROME)
            if (Settings.Secure.getInt(contentResolver, DISPLAY_DALTONIZER_ENABLED, OFF) != ON)
                Settings.Secure.putInt(contentResolver, DISPLAY_DALTONIZER_ENABLED, ON)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot enable monochrome", e)
        }
    }

    private fun disableMonochrome() {
        try {
            if (Settings.Secure.getInt(contentResolver, DISPLAY_DALTONIZER_ENABLED, OFF) != OFF)
                Settings.Secure.putInt(contentResolver, DISPLAY_DALTONIZER_ENABLED, OFF)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot disable monochrome", e)
        }
    }

    companion object {
        const val TAG = "Grayscaler"
        const val DISPLAY_DALTONIZER = "accessibility_display_daltonizer"
        const val DISPLAY_DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
        const val MONOCHROME = 0
        const val OFF = 0
        const val ON = 1
        val IGNORED_PACKAGES = listOf(
            "com.android.systemui",
            "com.android.launcher3",
        )
    }
}
