package com.cool2645.sms_relay

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import androidx.annotation.RequiresPermission
import com.cool2645.sms_relay.network.NetworkManager
import com.cool2645.sms_relay.utils.SmsLogManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * Broadcast receiver that handles incoming SMS messages and forwards them to the backend
 */
class SmsReceiver : BroadcastReceiver() {

    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val smsMessages = extractSmsMessages(intent) ?: return
        val simInfo = extractSimInfo(context, intent)

        if (shouldProcessMessages(context, simInfo)) {
            processMessages(context, smsMessages, simInfo)
        }
    }

    /**
     * Extract SMS messages from the intent
     */
    private fun extractSmsMessages(intent: Intent): List<SmsMessage>? {
        val bundle = intent.extras ?: return null
        val pdus = bundle.get("pdus") as? Array<*> ?: return null
        val format = bundle.getString("format")

        return pdus.mapNotNull { pdu ->
            SmsMessage.createFromPdu(pdu as ByteArray, format)
        }
    }

    /**
     * Extract SIM card information from the intent
     */
    private fun extractSimInfo(context: Context, intent: Intent): SimInfo {
        val subId = intent.getIntExtra("subscription", -1)
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager

        var number: String? = null
        var slotIndex: Int? = null

        if (subId != -1 && subscriptionManager != null) {
            try {
                val info = subscriptionManager.getActiveSubscriptionInfo(subId)
                number = info?.number
                slotIndex = info?.simSlotIndex
            } catch (e: SecurityException) {
                // Permission not granted, continue with null values
                android.util.Log.w("SmsReceiver", "Permission not granted for subscription info")
            }
        }

        return SimInfo(subId, number, slotIndex)
    }

    /**
     * Check if messages should be processed based on SIM preferences
     */
    private fun shouldProcessMessages(context: Context, simInfo: SimInfo): Boolean {
        val prefs = context.getSharedPreferences(Constants.SMS_RELAY_PREFS, Context.MODE_PRIVATE)

        // Generate preference keys
        val numberKey = if (!simInfo.number.isNullOrEmpty()) {
            "${Constants.SMS_RELAY_ENABLED_PREFIX}${simInfo.number}"
        } else null

        val slotKey = if (simInfo.slotIndex != null) {
            "${Constants.SMS_RELAY_ENABLED_SLOT_PREFIX}${simInfo.slotIndex}"
        } else null

        // Check if enabled for this SIM
        val enabled = when {
            !numberKey.isNullOrEmpty() && prefs.getBoolean(numberKey, false) -> true
            !slotKey.isNullOrEmpty() && prefs.getBoolean(slotKey, false) -> true
            else -> false
        }

        // Fallback: check if any relay is enabled
        val fallbackEnabled = prefs.all.any {
            it.key.startsWith(Constants.SMS_RELAY_ENABLED_PREFIX) && it.value == true
        }

        return (simInfo.subId != -1 && enabled) || (simInfo.subId == -1 && fallbackEnabled)
    }

    /**
     * Process SMS messages: log and forward them
     */
    private fun processMessages(context: Context, messages: List<SmsMessage>, simInfo: SimInfo) {
        for (message in messages) {
            processSingleMessage(context, message, simInfo)
        }
    }

    /**
     * Process a single SMS message
     */
    private fun processSingleMessage(context: Context, message: SmsMessage, simInfo: SimInfo) {
        val timestamp = SimpleDateFormat(Constants.DATE_FORMAT_PATTERN, Locale.getDefault()).format(Date())
        val recipient = simInfo.number ?: Constants.UNKNOWN_PLACEHOLDER
        val slot = simInfo.slotIndex ?: -1

        // Create initial log entry with pending status
        val initialLogEntry = SmsLogManager.createLogEntry(
            from = message.displayOriginatingAddress,
            recipient = recipient,
            slot = slot,
            status = Constants.STATUS_PENDING,
            body = message.messageBody,
            timestamp = timestamp
        )

        // Save initial log entry
        SmsLogManager.saveLogEntry(context, initialLogEntry)

        // Send initial broadcast with pending status
        SmsLogManager.sendLogBroadcast(context, initialLogEntry)

        // Forward SMS asynchronously
        forwardSmsAsync(context, message, recipient, slot, timestamp)
    }

    /**
     * Forward SMS to backend asynchronously and update log with result
     */
    private fun forwardSmsAsync(
        context: Context,
        message: SmsMessage,
        recipient: String,
        slot: Int,
        timestamp: String
    ) {
        Thread {
            val success = NetworkManager.forwardSmsToBackend(
                context = context,
                phoneNumber = recipient,
                from = message.displayOriginatingAddress,
                body = message.messageBody
            )

            val status = if (success) Constants.STATUS_FORWARDED else Constants.STATUS_FAILED

            // Update the existing log entry's status instead of creating a new one
            val updated = SmsLogManager.updateMostRecentLogEntryStatus(
                context = context,
                timestamp = timestamp,
                from = message.displayOriginatingAddress,
                newStatus = status
            )

            if (updated) {
                // Send updated broadcast with final status
                SmsLogManager.sendLogBroadcast(
                    context = context,
                    from = message.displayOriginatingAddress,
                    body = message.messageBody,
                    recipient = recipient,
                    slot = slot,
                    timestamp = timestamp,
                    status = status
                )
            }
        }.start()
    }

    /**
     * Data class to hold SIM card information
     */
    private data class SimInfo(
        val subId: Int,
        val number: String?,
        val slotIndex: Int?
    )
}
