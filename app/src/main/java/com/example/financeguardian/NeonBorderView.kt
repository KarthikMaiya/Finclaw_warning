package com.example.financeguardian

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.toColorInt

/**
 * NeonBorderView
 *
 * Draws a full-screen neon red border that pulses in alpha to simulate
 * an emergency-alert glow. Runs its own ValueAnimator — no Compose needed.
 *
 * Place this as the first child inside warning_overlay.xml so it renders
 * behind the card. The card's elevation keeps it on top.
 */
class NeonBorderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── Paint objects (allocated once, never in onDraw) ───────────────────────

    private val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        color       = "#FF3D5A".toColorInt()   // DangerRed
        strokeWidth = 40f
        alpha       = 80
    }

    private val innerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        color       = "#FF3D5A".toColorInt()
        strokeWidth = 6f
        alpha       = 200
    }

    private val cornerAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        color       = "#FF8FA3".toColorInt()   // lighter red for corner accents
        strokeWidth = 10f
        alpha       = 220
    }

    // ── Reusable RectF ────────────────────────────────────────────────────────

    private val borderRect = RectF()

    // ── Animator state ────────────────────────────────────────────────────────

    /** 0f → 1f → 0f pulsing alpha multiplier */
    private var pulseAlpha = 1f

    private val pulseAnimator = ValueAnimator.ofFloat(0.2f, 1f).apply {
        duration      = 600
        repeatCount   = ValueAnimator.INFINITE
        repeatMode    = ValueAnimator.REVERSE
        interpolator  = LinearInterpolator()
        addUpdateListener { anim ->
            pulseAlpha = anim.animatedValue as Float
            invalidate()
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        pulseAnimator.start()
    }

    override fun onDetachedFromWindow() {
        pulseAnimator.cancel()
        super.onDetachedFromWindow()
    }

    // ── Draw ──────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val inset = 3f

        borderRect.set(inset, inset, w - inset, h - inset)

        // Outer soft glow (wide, transparent)
        outerGlowPaint.alpha = (80 * pulseAlpha).toInt().coerceIn(0, 255)
        canvas.drawRect(borderRect, outerGlowPaint)

        // Inner crisp border
        innerBorderPaint.alpha = (200 * pulseAlpha).toInt().coerceIn(0, 255)
        canvas.drawRect(borderRect, innerBorderPaint)

        // Corner accent lines — top-left, top-right, bottom-left, bottom-right
        cornerAccentPaint.alpha = (220 * pulseAlpha).toInt().coerceIn(0, 255)
        val cornerLen = 80f
        val cx = inset + 2f   // corner x offset

        // Top-left
        canvas.drawLine(cx, cx, cx + cornerLen, cx, cornerAccentPaint)
        canvas.drawLine(cx, cx, cx, cx + cornerLen, cornerAccentPaint)

        // Top-right
        canvas.drawLine(w - cx - cornerLen, cx, w - cx, cx, cornerAccentPaint)
        canvas.drawLine(w - cx, cx, w - cx, cx + cornerLen, cornerAccentPaint)

        // Bottom-left
        canvas.drawLine(cx, h - cx - cornerLen, cx, h - cx, cornerAccentPaint)
        canvas.drawLine(cx, h - cx, cx + cornerLen, h - cx, cornerAccentPaint)

        // Bottom-right
        canvas.drawLine(w - cx, h - cx - cornerLen, w - cx, h - cx, cornerAccentPaint)
        canvas.drawLine(w - cx - cornerLen, h - cx, w - cx, h - cx, cornerAccentPaint)
    }
}