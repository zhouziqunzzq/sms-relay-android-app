package com.cool2645.sms_relay

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import androidx.annotation.RequiresPermission
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.json.JSONArray

data class SMSRequest(
    val phone_number: String,
    val from: String,
    val body: String
)

class SmsReceiver : BroadcastReceiver() {
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val bundle: Bundle? = intent.extras
            if (bundle != null) {
                val pdus = bundle.get("pdus") as? Array<*>
                val format = bundle.getString("format")
                if (pdus != null) {
                    val messages = pdus.mapNotNull { pdu ->
                        SmsMessage.createFromPdu(pdu as ByteArray, format)
                    }
                    val prefs = context.getSharedPreferences("sms_relay_prefs", Context.MODE_PRIVATE)
                    // Try to get subscription ID from intent
                    val subId = intent.getIntExtra("subscription", -1)
                    val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                    var slotKey: String? = null
                    var numberKey: String? = null
                    var debugNumber: String? = null
                    var debugSlotIndex: Int? = null
                    if (subId != -1 && subscriptionManager != null) {
                        val info = subscriptionManager.getActiveSubscriptionInfo(subId)
                        val slotIndex = info?.simSlotIndex
                        val number = info?.number
                        debugNumber = number
                        debugSlotIndex = slotIndex
                        if (!number.isNullOrEmpty()) {
                            numberKey = "sms_relay_enabled_$number"
                        }
                        if (slotIndex != null) {
                            slotKey = "sms_relay_enabled_slot_$slotIndex"
                        }
                    }
                    val enabled = when {
                        !numberKey.isNullOrEmpty() && prefs.getBoolean(numberKey, false) -> true
                        !slotKey.isNullOrEmpty() && prefs.getBoolean(slotKey, false) -> true
                        else -> false
                    }
                    // Fallback: if no subId or info, show if any toggle is enabled
                    val fallbackEnabled = prefs.all.any { it.key.startsWith("sms_relay_enabled_") && it.value == true }
                    if ((subId != -1 && enabled) || (subId == -1 && fallbackEnabled)) {
                        for (msg in messages) {
                            // Compose log entry
                            val recipient = debugNumber ?: "(unknown)"
                            val slot = debugSlotIndex ?: -1
                            val logLine = "From: ${msg.displayOriginatingAddress}\nTo: $recipient\nSlot: ${if (slot >= 0) slot else "(unknown)"}\n${msg.messageBody}"
                            // Persist log entry
                            val logPrefs = context.getSharedPreferences("sms_log", Context.MODE_PRIVATE)
                            val logArray = JSONArray(logPrefs.getString("log", "[]"))
                            logArray.put(0, logLine)
                            logPrefs.edit().putString("log", logArray.toString()).apply()
                            // Send local broadcast to MainActivity
                            val logIntent = Intent("com.cool2645.sms_relay.SMS_LOG")
                            logIntent.putExtra("from", msg.displayOriginatingAddress)
                            logIntent.putExtra("body", msg.messageBody)
                            logIntent.putExtra("recipient", recipient)
                            logIntent.putExtra("slot", slot)
                            LocalBroadcastManager.getInstance(context).sendBroadcast(logIntent)
                            // Forward to backend
                            forwardSmsToBackend(context, recipient, msg.displayOriginatingAddress, msg.messageBody)
                        }
                    }
                }
            }
        }
    }

    private fun forwardSmsToBackend(context: Context, phoneNumber: String, from: String?, body: String?) {
        val request = SMSRequest(
            phone_number = phoneNumber,
            from = from ?: "",
            body = body ?: ""
        )
        val gson = Gson()
        val json = gson.toJson(request)
        val client = OkHttpClient()
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = json.toRequestBody(mediaType)
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val encryptedPrefs = EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        val username = encryptedPrefs.getString("username", null)
        val password = encryptedPrefs.getString("password", null)
        fun doForward(token: String?, hasRetried: Boolean = false) {
            if (token.isNullOrEmpty()) {
                android.util.Log.e("SmsReceiver", "No token available for SMS forwarding.")
                return
            }
            val httpRequest = Request.Builder()
                .url("${ApiConfig.API_BASE_URL}/sms")
                .post(requestBody)
                .addHeader("Authorization", "Bearer $token")
                .build()
            try {
                val response = client.newCall(httpRequest).execute()
                val responseBody = response.body?.string()
                if ((response.code == 401 || response.code == 403) && !hasRetried) {
                    android.util.Log.w("SmsReceiver", "Token expired, attempting to renew...")
                    val newToken = renewToken(context, username, password)
                    if (!newToken.isNullOrEmpty()) {
                        android.util.Log.i("SmsReceiver", "Token renewed, retrying SMS forward...")
                        doForward(newToken, hasRetried = true)
                    } else {
                        android.util.Log.e("SmsReceiver", "Token renewal failed, cannot forward SMS.")
                    }
                } else {
                    android.util.Log.i("SmsReceiver", "SMS forward response: ${response.code} $responseBody")
                }
            } catch (e: Exception) {
                android.util.Log.e("SmsReceiver", "SMS forward error", e)
            }
        }
        Thread {
            val token = encryptedPrefs.getString("token", null)
            doForward(token)
        }.start()
    }

    private fun renewToken(context: Context, username: String?, password: String?): String? {
        if (username.isNullOrEmpty() || password.isNullOrEmpty()) return null
        return try {
            val client = OkHttpClient()
            val gson = Gson()
            val loginRequest = mapOf("username" to username, "password" to password)
            val json = gson.toJson(loginRequest)
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val requestBody = json.toRequestBody(mediaType)
            val httpRequest = Request.Builder()
                .url("${ApiConfig.API_BASE_URL}/login")
                .post(requestBody)
                .build()
            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string()
            if (response.isSuccessful && responseBody != null) {
                val loginResponse = gson.fromJson(responseBody, Map::class.java)
                val token = loginResponse["token"] as? String
                if (!token.isNullOrEmpty()) {
                    val masterKey = MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                    val encryptedPrefs = EncryptedSharedPreferences.create(
                        context,
                        "secure_prefs",
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                    encryptedPrefs.edit().putString("token", token).apply()
                    return token
                }
            }
            null
        } catch (e: Exception) {
            android.util.Log.e("SmsReceiver", "Token renewal error", e)
            null
        }
    }
}
