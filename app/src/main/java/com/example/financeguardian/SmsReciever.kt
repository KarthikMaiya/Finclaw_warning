package com.example.financeguardian

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Telephony
import android.util.Log
import androidx.core.content.edit
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PHASE 1 — TRANSACTION EVENT STANDARDIZATION
 * Converts parsed transaction data into canonical JSON event format.
 * Defined at top-level for reuse in MainActivity simulation.
 */
fun buildTransactionEvent(
    amount: Double,
    isCredit: Boolean,
    merchant: String,
    category: String,
    riskLevel: String,
    notes: String,
    source: String = "android_sms"
): JSONObject {

    val timestamp = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        Locale.getDefault()
    ).format(Date())

    val dateOnly = SimpleDateFormat(
        "yyyy-MM-dd",
        Locale.getDefault()
    ).format(Date())

    // Rule: Expenses are negative, Income is positive
    val signedAmount = if (isCredit) kotlin.math.abs(amount) else -kotlin.math.abs(amount)

    val transactionObject = JSONObject().apply {
        put("amount", signedAmount)
        put("payee", merchant)
        put("merchant", merchant)
        put("categoryHint", category)
        put("riskLevel", riskLevel)
        put("notes", notes)
        put("date", dateOnly)
    }

    return JSONObject().apply {
        put("eventType", "TRANSACTION_SYNC")
        put("source", source)
        put("timestamp", timestamp)
        put("transaction", transactionObject)
    }
}

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TELEGRAM_BOT_TOKEN = "8729828854:AAFyVoI-ktc3Q6vrW42ft192MQMoJFQAQ24"
        private const val GROUP_CHAT_ID      = "-1003714859222"


        private const val TELEGRAM_API_URL   = "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage"

        private val httpClient = OkHttpClient()

        private fun isInternetAvailable(context: Context): Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        }

        private fun sendTransactionToTelegram(context: Context, event: JSONObject) {
            val messageText = "FINCLAW_EVENT:\n$event"
            executeTelegramApiCall(context, messageText)
        }

        /**
         * CORE TRANSPORT LOGIC
         * Executes the actual HTTP POST to Telegram with detailed logging.
         */
        private fun executeTelegramApiCall(context: Context, messageText: String) {
            if (!isInternetAvailable(context)) {
                Log.e("FinClawTelegram", "SYNC ABORTED: Internet unavailable. Verify device connectivity.")
                return
            }

            val payload = JSONObject().apply {
                put("chat_id", GROUP_CHAT_ID)
                put("text", messageText)
            }

            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(TELEGRAM_API_URL)
                .post(requestBody)
                .build()

            Log.d("FinClawTelegram", ">>> INITIATING TELEGRAM REQUEST <<<")
            Log.d("FinClawTelegram", "TARGET URL: $TELEGRAM_API_URL")
            Log.d("FinClawTelegram", "PAYLOAD JSON:\n${payload.toString(2)}")

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("FinClawTelegram", "!!! TRANSPORT FAILURE (IOException) !!!")
                    Log.e("FinClawTelegram", "Exception Type: ${e.javaClass.simpleName}")
                    Log.e("FinClawTelegram", "Error Message: ${e.message}")
                    
                    // Detailed logging for common network issues
                    when (e) {
                        is java.net.UnknownHostException -> Log.e("FinClawTelegram", "DIAGNOSIS: DNS failure. Cannot resolve api.telegram.org")
                        is java.net.SocketTimeoutException -> Log.e("FinClawTelegram", "DIAGNOSIS: Connection timed out.")
                        is java.net.ConnectException -> Log.e("FinClawTelegram", "DIAGNOSIS: Connection refused.")
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string() ?: ""
                    Log.d("FinClawTelegram", "<<< TELEGRAM API RESPONSE >>>")
                    Log.d("FinClawTelegram", "HTTP STATUS: ${response.code} ${response.message}")
                    Log.d("FinClawTelegram", "RESPONSE BODY:\n$responseBody")

                    if (response.isSuccessful) {
                        Log.i("FinClawTelegram", "SUCCESS: Message delivered to group $GROUP_CHAT_ID")
                    } else {
                        Log.e("FinClawTelegram", "REJECTED: Telegram API returned error code ${response.code}")
                        Log.e("FinClawTelegram", "DIAGNOSIS: Check if bot is a member of group $GROUP_CHAT_ID and has 'Send Messages' permission.")
                    }
                    response.close()
                }
            })
        }

        /**
         * TASK 9: TEMPORARY HARD-CODED TEST
         * Triggers a simple text message to verify group reachability.
         */
        fun sendSimpleTelegramTest(context: Context) {
            Log.d("FinClawTelegram", "MANUAL TEST TRIGGERED: sendSimpleTelegramTest")
            executeTelegramApiCall(context, "TEST_MESSAGE_FROM_FINCLAW")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        // QUICK DEBUG: Trigger simple test for ANY incoming intent to verify transport
        // Log.d("FinClawTelegram", "onReceive triggered - running simple test")
        // sendSimpleTelegramTest(context)

        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (smsMessage in messages) {
                val sender = smsMessage.displayOriginatingAddress ?: ""
                val message = smsMessage.messageBody ?: ""
                
                Log.d("FinClawSMS", "SMS FROM: $sender, MSG: $message")

                val lowerMessage = message.lowercase()
                val financeKeywords = listOf(
                    "debited", "credited", "spent", "upi", "payment",
                    "txn", "transaction", "withdrawn", "purchase",
                    "rs", "inr", "₹"
                )

                val isFinanceMessage = financeKeywords.any { lowerMessage.contains(it) }
                val isTestSender = sender.contains("Vishal", ignoreCase = true)

                if (isFinanceMessage || isTestSender) {
                    Log.d("FinClawSMS", "FINANCIAL SMS DETECTED")

                    // 1. Amount extraction
                    val amountRegex = Regex("(rs\\.?|inr|₹)\\s?([\\d,]+)", RegexOption.IGNORE_CASE)
                    val amountMatch = amountRegex.find(lowerMessage)
                    val amountStr = amountMatch?.groupValues?.getOrNull(2)?.replace(",", "") ?: ""

                    // 2. Merchant extraction
                    var merchant = "Unknown Merchant"
                    val lines = message.split("\n")
                    for (line in lines) {
                        if (line.trim().startsWith("To ", ignoreCase = true)) {
                            merchant = line.replace("To ", "", ignoreCase = true).trim()
                            break
                        }
                    }

                    if (amountStr.isNotEmpty()) {
                        try {
                            val numericAmount = amountStr.toInt()
                            Log.d("FinClawSMS", "AMOUNT: $numericAmount, MERCHANT: $merchant")

                            val sharedPreferences = context.getSharedPreferences("FinanceGuardian", Context.MODE_PRIVATE)
                            val currentBalance = sharedPreferences.getInt("CURRENT_BALANCE", 0)
                            val totalBudget = sharedPreferences.getInt("TOTAL_BUDGET", 0)

                            // 3. Update Balance (Add if credited, else subtract)
                            val isCredit = lowerMessage.contains("credited")
                            val updatedBalance = if (isCredit) {
                                currentBalance + numericAmount
                            } else {
                                (currentBalance - numericAmount).coerceAtLeast(0)
                            }

                            // 4. Categorization
                            val mLower = merchant.lowercase()
                            val category = when {
                                mLower.contains("zomato") || mLower.contains("swiggy") || mLower.contains("restaurant") || mLower.contains("cafe") || mLower.contains("hotel") -> "Food"
                                mLower.contains("amazon") || mLower.contains("flipkart") || mLower.contains("myntra") || mLower.contains("ajio") -> "Shopping"
                                mLower.contains("uber") || mLower.contains("ola") || mLower.contains("rapido") || mLower.contains("metro") || mLower.contains("irctc") -> "Transport"
                                mLower.contains("netflix") || mLower.contains("spotify") || mLower.contains("prime") || mLower.contains("hotstar") || mLower.contains("bookmyshow") -> "Entertainment"
                                mLower.contains("apollo") || mLower.contains("pharmacy") || mLower.contains("hospital") || mLower.contains("clinic") -> "Health"
                                mLower.contains("electricity") || mLower.contains("water") || mLower.contains("wifi") || mLower.contains("recharge") -> "Utilities"
                                mLower.contains("paytm") || mLower.contains("phonepe") || mLower.contains("gpay") -> "Personal"
                                else -> "Others"
                            }

                            // 5. Risk Estimation (Simplified for Sync)
                            var estimatedRisk = "SAFE"
                            if (!isCredit && totalBudget > 0) {
                                val transRatio = numericAmount.toDouble() / totalBudget.toDouble()
                                if (transRatio > 0.2) estimatedRisk = "HIGH"
                                else if (transRatio > 0.1) estimatedRisk = "CAUTION"
                            }

                            // 6. Update Category Total
                            val prevCatAmount = sharedPreferences.getInt(category, 0)
                            
                            // 7. Transaction History
                            val existingHistory = sharedPreferences.getString("TRANSACTION_HISTORY", "") ?: ""
                            val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                            val newTransaction = "$merchant|₹$numericAmount|$category|$timeStr|$dateStr\n"

                            sharedPreferences.edit {
                                putInt("CURRENT_BALANCE", updatedBalance)
                                putInt(category, prevCatAmount + numericAmount)
                                putString("TRANSACTION_HISTORY", newTransaction + existingHistory)
                            }

                            Log.d("FinClawSMS", "SAVED: Balance=$updatedBalance, Category=$category, Total=${prevCatAmount + numericAmount}")

                            // PHASE 1: Generate Standardized Event
                            val syncEvent = buildTransactionEvent(
                                amount = numericAmount.toDouble(),
                                isCredit = isCredit,
                                merchant = merchant,
                                category = category,
                                riskLevel = estimatedRisk,
                                notes = "SMS Payment Detection"
                            )

                            // TASK 4: Log pretty JSON
                            Log.d("FinClawSync", "CANONICAL TRANSACTION EVENT GENERATED:\n${syncEvent.toString(4)}")

                            // PHASE 2A: Passive Synchronization to Telegram
                            sendTransactionToTelegram(context, syncEvent)

                        } catch (e: Exception) {
                            Log.e("FinClawSMS", "ERROR: ${e.message}")
                        }
                    }
                }
            }
        }
    }
}
