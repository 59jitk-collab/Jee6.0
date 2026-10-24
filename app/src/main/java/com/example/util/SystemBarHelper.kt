package com.example.util

import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Manages Android Status Bar and Navigation Bar visibility and behavior.
 * Enforces immersive hidden status bar (transient reveal on swipe) across the app
 * while preserving safe display cutout handling and normal bottom navigation bar usability.
 */
object SystemBarHelper {

    @Volatile
    var isImmersiveModeActive: Boolean = false
        private set

    /**
     * Hides the top status bar in immersive full-screen mode (transient reveal by swipe)
     * while keeping the bottom navigation bar visible and responsive.
     */
    fun ensureNormalSystemBars(window: Window?) {
        isImmersiveModeActive = false
        if (window == null) return
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            val decorView = window.decorView
            val controller = WindowCompat.getInsetsController(window, decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            // Always hide status bar in immersive full-screen mode per user requirement
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.show(WindowInsetsCompat.Type.navigationBars())
        } catch (_: Exception) {
            // Safe fallback for unit test / headless environments
        }
    }

    /**
     * Opt-in full immersive mode for Ultra Focus Lock (hides both status and navigation bars).
     * Passing enabled = false restores normal navigation bar while keeping status bar hidden.
     */
    fun setOptInImmersiveMode(window: Window?, enabled: Boolean) {
        if (!enabled) {
            ensureNormalSystemBars(window)
            return
        }
        isImmersiveModeActive = true
        if (window == null) return
        try {
            val decorView = window.decorView
            val controller = WindowCompat.getInsetsController(window, decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } catch (_: Exception) {
            isImmersiveModeActive = false
        }
    }
}
