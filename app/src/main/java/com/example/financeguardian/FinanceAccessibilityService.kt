package com.example.financeguardian

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

// ─── Constants ────────────────────────────────────────────────────────────────

private const val TAG                  = "FinClaw"
private const val CHANNEL_ID           = "fg_ai_warnings"
private const val CHANNEL_NAME         = "FinClaw AI Warnings"
private const val NOTIFICATION_ID      = 1001
private const val COOLDOWN_MS          = 30_000L
private const val BACKEND_URL          = "http://10.228.135.39:5000/payment-alert"
private const val PREFS_NAME           = "FinanceGuardian"
private const val PREF_PENDING_PAYMENT = "PENDING_PAYMENT"

private val SUPPORTED_APPS = mapOf(
    "in.swiggy.android"                to "Swiggy",
    "com.application.zomato"           to "Zomato",
    "in.amazon.mShop.android.shopping" to "Amazon",
    "com.flipkart.android"             to "Flipkart"
)

private val PAYMENT_KEYWORDS = listOf(
    "place order", "proceed to pay", "pay now",
    "pay ₹", "to pay", "grand total",
    "total amount", "order total", "confirm order", "make payment"
)

private val AMOUNT_REGEX = Regex("₹\\s?[\\d,]+")

// ─── Service ──────────────────────────────────────────────────────────────────

class FinanceAccessibilityService : AccessibilityService() {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val warningOverlay: WarningOverlay by lazy { WarningOverlay(this) }

    private val lastAlertTimestamps = mutableMapOf<String, Long>()
    private val lastSentAmounts     = mutableMapOf<String, String>()
    private var detectedAmount      = ""

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        createNotificationChannel()
        Log.i(TAG, "Accessibility Service connected")
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service interrupted")
    }

    // ── Event Entry Point ─────────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return
        if (!SUPPORTED_APPS.containsKey(packageName)) return

        val root = rootInActiveWindow ?: return
        detectedAmount = ""
        traverseNode(root, packageName)
    }

    // ── UI Tree Traversal ─────────────────────────────────────────────────────

    private fun traverseNode(node: AccessibilityNodeInfo, packageName: String) {
        val text = node.text?.toString().orEmpty()
        if (text.isNotEmpty()) {
            extractLargestAmount(text)
            checkForPaymentScreen(text, packageName)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNode(child, packageName)
        }
    }

    // ── Amount Extraction ─────────────────────────────────────────────────────

    private fun extractLargestAmount(text: String) {
        AMOUNT_REGEX.findAll(text).forEach { match ->
            val raw     = match.value
            val numeric = parseRupeeAmount(raw)
            if (numeric > 0 && numeric > parseRupeeAmount(detectedAmount.ifEmpty { "₹0" })) {
                detectedAmount = raw
                Log.d(TAG, "Amount candidate: $raw ($numeric)")
            }
        }
    }

    private fun parseRupeeAmount(raw: String): Int =
        raw.replace("₹", "").replace(",", "").trim().toIntOrNull() ?: 0

    // ── Payment Screen Detection ───────────────────────────────────────────────

    private fun checkForPaymentScreen(text: String, packageName: String) {
        val lower   = text.lowercase()
        val matched = PAYMENT_KEYWORDS.any { lower.contains(it) }
        if (!matched) return

        Log.d(TAG, "Payment screen detected in $packageName — text: $text")

        if (detectedAmount.isEmpty() || detectedAmount == "₹0") {
            Log.d(TAG, "Skipping — no valid amount")
            return
        }
        if (isDuplicate(packageName, detectedAmount)) {
            Log.d(TAG, "Skipping — duplicate suppressed")
            return
        }

        persistPendingPayment(parseRupeeAmount(detectedAmount))
        lastAlertTimestamps[packageName] = System.currentTimeMillis()
        lastSentAmounts[packageName]     = detectedAmount

        Log.i(TAG, "Triggering intervention: $packageName -> $detectedAmount")
        sendPaymentToBackend(packageName, detectedAmount)
    }

    // ── Deduplication ─────────────────────────────────────────────────────────

    private fun isDuplicate(packageName: String, amount: String): Boolean {
        val lastTime   = lastAlertTimestamps[packageName] ?: 0L
        val lastAmount = lastSentAmounts[packageName].orEmpty()
        return (System.currentTimeMillis() - lastTime) < COOLDOWN_MS && lastAmount == amount
    }

    // ── SharedPreferences ─────────────────────────────────────────────────────

    private fun persistPendingPayment(amount: Int) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
            putInt(PREF_PENDING_PAYMENT, amount)
        }
        Log.d(TAG, "Pending payment stored: $amount")
    }

    // ── Networking ────────────────────────────────────────────────────────────

    private fun sendPaymentToBackend(packageName: String, amount: String) {
        val appFriendlyName = SUPPORTED_APPS[packageName] ?: packageName
        val json = JSONObject().apply {
            put("app",    appFriendlyName)
            put("amount", amount)
        }.toString()

        val request = Request.Builder()
            .url(BACKEND_URL)
            .addHeader("Content-Type", "application/json")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Backend unreachable: ${e.message}")
                val fallback = "You're about to spend $amount on $appFriendlyName. Pause and reconsider."
                showOverlay(amount, fallback)
                showWarningNotification(appFriendlyName, amount, fallback)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                Log.d(TAG, "Backend responded ${response.code}: $body")
                response.close()
                val warning = parseWarningFromResponse(body)
                showOverlay(amount, warning)
                showWarningNotification(appFriendlyName, amount, warning)
            }
        })
    }

    private fun parseWarningFromResponse(body: String?): String {
        if (body.isNullOrBlank()) return defaultWarning()
        return try {
            JSONObject(body).optString("warning", defaultWarning())
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse warning JSON: ${e.message}")
            defaultWarning()
        }
    }

    private fun defaultWarning(): String =
        "OpenClaw AI has flagged this transaction. Review your budget before proceeding."

    // ── Overlay ───────────────────────────────────────────────────────────────

    private fun showOverlay(amount: String, warning: String) {
        if (warningOverlay.isShowing) {
            Log.d(TAG, "Overlay already visible — skipping")
            return
        }
        warningOverlay.show(amount = amount, warning = warning)
        Log.i(TAG, "Overlay shown: $amount")
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "AI-powered spending intervention alerts"
                enableVibration(true)
                enableLights(true)
                lightColor = android.graphics.Color.CYAN
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun showWarningNotification(appName: String, amount: String, warning: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🦞 FinClaw AI  ·  $appName")
            .setContentText(warning)
            .setSubText("Spending: $amount detected")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(warning)
                    .setSummaryText("OpenClaw intervention · $amount")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColorized(true)
            .setColor("#00C2FF".toColorInt())
            .setVibrate(longArrayOf(0, 250, 150, 250))
            .setTimeoutAfter(60_000L)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
        Log.i(TAG, "Notification shown: $appName $amount")
    }
}
