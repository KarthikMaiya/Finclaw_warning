package com.example.financeguardian

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.ProgressBar
import android.widget.TextView

/**
 * WarningOverlay
 *
 * Handles the fullscreen TYPE_ACCESSIBILITY_OVERLAY. This bypasses the need for
 * the 'Draw over other apps' permission while the Accessibility Service is active.
 *
 * Features:
 * - Real-time amount and warning injection.
 * - Neon border pulse (via NeonBorderView in XML).
 * - Animated auto-dismiss progress bar.
 */
class WarningOverlay(context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val inflater      = LayoutInflater.from(context)
    private var overlayView: View? = null

    /** Tracks if the overlay is currently attached to the window manager. */
    var isShowing = false
        private set

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Inflates and shows the overlay.
     * Safe to call from any thread.
     */
    fun show(amount: String, warning: String) {
        mainHandler.post {
            if (isShowing) return@post

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.CENTER
            }

            try {
                overlayView = inflater.inflate(R.layout.warning_overlay, null).apply {
                    findViewById<TextView>(R.id.tvAmount).text = amount
                    findViewById<TextView>(R.id.tvWarningMessage).text = warning

                    val progressBar = findViewById<ProgressBar>(R.id.dismissProgress)
                    startDismissAnimation(progressBar)
                    
                    // Root is clickable to prevent touches passing to background app
                    findViewById<View>(R.id.overlayRoot).setOnClickListener {
                        dismiss()
                    }
                }

                windowManager.addView(overlayView, params)
                isShowing = true

                // Auto-dismiss after 4 seconds
                mainHandler.postDelayed({
                    dismiss()
                }, 4000)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Removes the overlay from the window manager.
     */
    fun dismiss() {
        mainHandler.post {
            if (!isShowing || (overlayView == null)) return@post
            try {
                windowManager.removeView(overlayView)
            } catch (e: Exception) {
                // View might have been already removed
            } finally {
                overlayView = null
                isShowing   = false
            }
        }
    }

    private fun startDismissAnimation(progressBar: ProgressBar) {
        ObjectAnimator.ofInt(progressBar, "progress", 100, 0).apply {
            duration     = 4000
            interpolator = LinearInterpolator()
            start()
        }
    }
}
