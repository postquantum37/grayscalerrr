package io.github.cloudburst.grayscaler

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

class MainService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private var currentState: Boolean? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == TYPE_WINDOW_STATE_CHANGED) {
            if (event.packageName == null || IGNORED_PACKAGES.contains(event.packageName.toString()))
                return
            val store = AppListStore(this)
            store.load()
            val gray = store.shouldGrayScale(event.packageName.toString())
            pending?.let { handler.removeCallbacks(it) }
            if (gray == currentState) return
            pending = Runnable {
                if (gray) enableMonochrome() else disableMonochrome()
                currentState = gray
            }
            handler.postDelayed(pending!!, SWITCH_DELAY_MS)
        }
    }

    override fun onInterrupt() {
        pending?.let { handler.removeCallbacks(it) }
        disableMonochrome()
    }

    private fun enableMonochrome(){
        Settings.Secure.putInt(contentResolver, DISPLAY_DALTONIZER, MONOCHROME)
        Settings.Secure.putInt(contentResolver, DISPLAY_DALTONIZER_ENABLED, ON)
    }

    private fun disableMonochrome(){
        Settings.Secure.putInt(contentResolver, DISPLAY_DALTONIZER_ENABLED, OFF)
    }

    companion object {
        const val DISPLAY_DALTONIZER = "accessibility_display_daltonizer"
        const val DISPLAY_DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
        const val MONOCHROME = 0
        const val OFF = 0
        const val ON = 1
        const val SWITCH_DELAY_MS = 1000L
        val IGNORED_PACKAGES = listOf(
            "com.android.systemui",
            "com.android.launcher3",
        )
    }
}