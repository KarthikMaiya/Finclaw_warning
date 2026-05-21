package com.example.financeguardian

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.core.content.edit

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(
            "FinClawSMS",
            "SMS RECEIVER TRIGGERED"
        )


        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (smsMessage in messages) {
                val sender = smsMessage.displayOriginatingAddress
                val isTestSender =

                    sender.contains(
                        "Vishal",
                        ignoreCase = true
                    )



                val message = smsMessage.messageBody
                Log.d("FinClawSMS", "SMS FROM: $sender")
                Log.d("FinClawSMS", "MESSAGE: $message")

                val lowerMessage = message.lowercase()
                val financeKeywords = listOf(
                    "debited",
                    "credited",
                    "spent",
                    "upi",
                    "payment",
                    "txn",
                    "transaction",
                    "withdrawn",
                    "purchase",

                    // NEW EASY TEST WORDS
                    "rs",
                    "inr",
                    "₹"

                )

                val isFinanceMessage =

                    financeKeywords.any {
                        lowerMessage.contains(it)
                    }

                if (
                    isFinanceMessage ||
                    isTestSender
                ) {


                    Log.d("FinClawSMS", "FINANCIAL SMS DETECTED")

                    // 1. Amount extraction
                    val amountRegex = Regex(
                        "(rs\\.?|inr|₹)\\s?[\\d,]+",
                        RegexOption.IGNORE_CASE
                    )


                    val amountMatch = amountRegex.find(lowerMessage)
                    val amountStr = amountMatch?.groupValues?.getOrNull(2) ?: ""

                    // 2. Merchant extraction


// Different merchant patterns
                    var merchant = "Unknown Merchant"

                    val lines =

                        message.split("\n")

                    for (line in lines) {

                        if (

                            line.trim()

                                .startsWith(
                                    "To ",
                                    ignoreCase = true
                                )
                        ) {

                            merchant =

                                line

                                    .replace(
                                        "To ",
                                        "",
                                        ignoreCase = true
                                    )

                                    .trim()

                            break
                        }
                    }

                    Log.d(
                        "FinClawSMS",
                        "MERCHANT: $merchant"
                    )






                    Log.d("FinClawSMS", "MERCHANT: $merchant")

                    if (amountStr.isNotEmpty()) {
                        try {
                            val numericAmount = amountStr.replace(",", "").toInt()
                            Log.d("FinClawSMS", "PARSED AMOUNT: $numericAmount")

                            val sharedPreferences = context.getSharedPreferences("FinanceGuardian", Context.MODE_PRIVATE)
                            val currentBalance = sharedPreferences.getInt("CURRENT_BALANCE", 0)

                            // 3. Logic: If "credited", add; else assume "debited/spent"
                            val isCredit = lowerMessage.contains("credited")
                            val updatedBalance = if (isCredit) {
                                currentBalance + numericAmount
                            } else {
                                (currentBalance - numericAmount).coerceAtLeast(0)
                            }

                            sharedPreferences.edit {
                                putInt("CURRENT_BALANCE", updatedBalance)
                            }

                            Log.d("FinClawSMS", "UPDATED BALANCE: $updatedBalance")

                            // 4. Categorization
                            val merchantLower = merchant.lowercase()
                            val category = when {
                                merchantLower.contains("zomato") || merchantLower.contains("swiggy") -> "Food"
                                merchantLower.contains("amazon") || merchantLower.contains("flipkart") -> "Shopping"
                                merchantLower.contains("medical") || merchantLower.contains("pharmacy") -> "Utilities"
                                merchantLower.contains("paytm") || merchantLower.contains("phonepe") || merchantLower.contains("gpay") -> "Personal"
                                else -> "Others"
                            }
                            Log.d("FinClawSMS", "CATEGORY: $category")

                            // 5. Update category total
                            val previousCategoryAmount = sharedPreferences.getInt(category, 0)
                            val updatedCategoryAmount = previousCategoryAmount + numericAmount

                            sharedPreferences.edit {
                                putInt(category, updatedCategoryAmount)
                            }
                            Log.d("FinClawSMS", "CATEGORY TOTAL UPDATED: $category = $updatedCategoryAmount")
                            val existingTransactions =

                                sharedPreferences.getString(
                                    "TRANSACTION_HISTORY",
                                    ""
                                ) ?: ""

                            val timestamp =

                                java.text.SimpleDateFormat(
                                    "hh:mm a",
                                    java.util.Locale.getDefault()
                                ).format(
                                    java.util.Date()
                                )

                            val newTransaction =

                                "$merchant|₹$numericAmount|$category|$timestamp\n"

                            val updatedTransactions =

                                newTransaction + existingTransactions

                            sharedPreferences
                                .edit()
                                .putString(
                                    "TRANSACTION_HISTORY",
                                    updatedTransactions
                                )
                                .apply()

                            Log.d(
                                "FinClawSMS",
                                "TRANSACTION SAVED"
                            )



                        } catch (e: Exception) {
                            Log.d("FinClawSMS", "SMS PROCESSING ERROR: ${e.message}")
                        }
                    }
                }
            }
        }
    }
}
