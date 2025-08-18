package com.cool2645.sms_relay.utils

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.cool2645.sms_relay.Constants
import com.cool2645.sms_relay.models.SmsLogEntry
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility class for managing SMS log operations
 */
object SmsLogManager {

    /**
     * Creates a structured SMS log entry
     */
    fun createLogEntry(
        from: String,
        recipient: String,
        slot: Int,
        status: String,
        body: String,
        timestamp: String = SimpleDateFormat(Constants.DATE_FORMAT_PATTERN, Locale.getDefault()).format(Date())
    ): SmsLogEntry {
        return SmsLogEntry(
            timestamp = timestamp,
            from = from,
            recipient = recipient,
            slot = slot,
            status = status,
            body = body
        )
    }

    /**
     * Loads SMS log from SharedPreferences in reverse chronological order (newest first)
     */
    fun loadPersistedLog(context: Context): List<SmsLogEntry> {
        val logPrefs = context.getSharedPreferences(Constants.SMS_LOG_PREFS, Context.MODE_PRIVATE)
        val logArray = JSONArray(logPrefs.getString(Constants.PREF_LOG, "[]") ?: "[]")
        val logList = mutableListOf<SmsLogEntry>()

        // Add entries in reverse order (newest first for display)
        for (i in logArray.length() - 1 downTo 0) {
            try {
                val jsonObj = logArray.getJSONObject(i)
                val logEntry = SmsLogEntry(
                    timestamp = jsonObj.getString("timestamp"),
                    from = jsonObj.getString("from"),
                    recipient = jsonObj.getString("recipient"),
                    slot = jsonObj.getInt("slot"),
                    status = jsonObj.getString("status"),
                    body = jsonObj.getString("body")
                )
                logList.add(logEntry)
            } catch (e: Exception) {
                // Skip malformed entries
                android.util.Log.w("SmsLogManager", "Skipping malformed log entry: $e")
            }
        }

        return logList
    }

    /**
     * Saves a log entry to SharedPreferences (chronological order)
     */
    fun saveLogEntry(context: Context, logEntry: SmsLogEntry) {
        val logPrefs = context.getSharedPreferences(Constants.SMS_LOG_PREFS, Context.MODE_PRIVATE)
        val logArray = JSONArray(logPrefs.getString(Constants.PREF_LOG, "[]") ?: "[]")

        // Convert log entry to JSON
        val jsonObj = JSONObject().apply {
            put("timestamp", logEntry.timestamp)
            put("from", logEntry.from)
            put("recipient", logEntry.recipient)
            put("slot", logEntry.slot)
            put("status", logEntry.status)
            put("body", logEntry.body)
        }

        // Append to end (chronological order)
        logArray.put(logArray.length(), jsonObj)

        // Apply cap - remove oldest entries
        while (logArray.length() > Constants.MAX_LOG_ENTRIES) {
            logArray.remove(0)
        }

        // Save updated array
        logPrefs.edit().putString(Constants.PREF_LOG, logArray.toString()).apply()
    }

    /**
     * Updates the status of the most recent log entry that matches the given criteria
     * This is more efficient than creating duplicate entries for status updates
     */
    fun updateMostRecentLogEntryStatus(
        context: Context,
        timestamp: String,
        from: String,
        newStatus: String
    ): Boolean {
        val logPrefs = context.getSharedPreferences(Constants.SMS_LOG_PREFS, Context.MODE_PRIVATE)
        val logArray = JSONArray(logPrefs.getString(Constants.PREF_LOG, "[]") ?: "[]")

        // Search from the end (most recent entries) for matching entry
        for (i in logArray.length() - 1 downTo 0) {
            try {
                val jsonObj = logArray.getJSONObject(i)
                if (jsonObj.getString("timestamp") == timestamp &&
                    jsonObj.getString("from") == from) {
                    jsonObj.put("status", newStatus)
                    logArray.put(i, jsonObj)

                    logPrefs.edit().putString(Constants.PREF_LOG, logArray.toString()).apply()
                    return true
                }
            } catch (e: Exception) {
                android.util.Log.e("SmsLogManager", "Failed to update log entry status: $e")
            }
        }
        return false
    }

    /**
     * Clears all log entries
     */
    fun clearAllLogs(context: Context) {
        val logPrefs = context.getSharedPreferences(Constants.SMS_LOG_PREFS, Context.MODE_PRIVATE)
        logPrefs.edit().putString(Constants.PREF_LOG, "[]").apply()
    }

    /**
     * Sends a broadcast intent with SMS log data
     */
    fun sendLogBroadcast(
        context: Context,
        from: String?,
        body: String?,
        recipient: String,
        slot: Int,
        timestamp: String,
        status: String
    ) {
        val logIntent = Intent(Constants.SMS_LOG_ACTION).apply {
            putExtra(Constants.EXTRA_FROM, from)
            putExtra(Constants.EXTRA_BODY, body)
            putExtra(Constants.EXTRA_RECIPIENT, recipient)
            putExtra(Constants.EXTRA_SLOT, slot)
            putExtra(Constants.EXTRA_TIMESTAMP, timestamp)
            putExtra(Constants.EXTRA_FORWARDING_STATUS, status)
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(logIntent)
    }

    /**
     * Sends a broadcast intent with SmsLogEntry
     */
    fun sendLogBroadcast(context: Context, logEntry: SmsLogEntry) {
        sendLogBroadcast(
            context = context,
            from = logEntry.from,
            body = logEntry.body,
            recipient = logEntry.recipient,
            slot = logEntry.slot,
            timestamp = logEntry.timestamp,
            status = logEntry.status
        )
    }
}
