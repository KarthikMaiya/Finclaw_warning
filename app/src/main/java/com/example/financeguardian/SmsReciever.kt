package com.example.financeguardian

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.core.content.edit
import org.json.JSONObject
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

    override fun onReceive(context: Context, intent: Intent) {
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

                        } catch (e: Exception) {
                            Log.e("FinClawSMS", "ERROR: ${e.message}")
                        }
                    }
                }
            }
        }
    }
}
