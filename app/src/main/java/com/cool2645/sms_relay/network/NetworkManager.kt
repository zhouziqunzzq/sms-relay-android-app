package com.cool2645.sms_relay.network

import android.content.Context
import android.util.Log
import com.cool2645.sms_relay.ApiConfig
import com.cool2645.sms_relay.models.SMSRequest
import com.cool2645.sms_relay.utils.SecurePrefsManager
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Network utility class for handling API communication
 */
object NetworkManager {
    private const val TAG = "NetworkManager"
    private val gson = Gson()
    private val client = OkHttpClient()
    private val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

    /**
     * Forwards SMS to backend API
     * @return true if successful, false otherwise
     */
    fun forwardSmsToBackend(
        context: Context,
        phoneNumber: String,
        from: String?,
        body: String?
    ): Boolean {
        val request = SMSRequest(
            phone_number = phoneNumber,
            from = from ?: "",
            body = body ?: ""
        )

        val json = gson.toJson(request)
        val requestBody = json.toRequestBody(mediaType)
        val token = SecurePrefsManager.getToken(context)

        return executeForwardRequest(context, requestBody, token)
    }

    /**
     * Executes the forward request with token refresh retry logic
     */
    private fun executeForwardRequest(
        context: Context,
        requestBody: okhttp3.RequestBody,
        token: String?,
        hasRetried: Boolean = false
    ): Boolean {
        if (token.isNullOrEmpty()) {
            Log.e(TAG, "No token available for SMS forwarding.")
            return false
        }

        val httpRequest = Request.Builder()
            .url("${ApiConfig.API_BASE_URL}/sms")
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        return try {
            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string()

            when {
                response.isSuccessful -> {
                    Log.i(TAG, "SMS forward successful: ${response.code}")
                    true
                }
                (response.code == 401 || response.code == 403) && !hasRetried -> {
                    Log.w(TAG, "Token expired, attempting to renew...")
                    val newToken = renewToken(context)
                    if (!newToken.isNullOrEmpty()) {
                        Log.i(TAG, "Token renewed, retrying SMS forward...")
                        executeForwardRequest(context, requestBody, newToken, hasRetried = true)
                    } else {
                        Log.e(TAG, "Token renewal failed, cannot forward SMS.")
                        false
                    }
                }
                else -> {
                    Log.e(TAG, "SMS forward failed: ${response.code} $responseBody")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SMS forward error", e)
            false
        }
    }

    /**
     * Renews authentication token
     * @return new token if successful, null otherwise
     */
    fun renewToken(context: Context): String? {
        val (username, password) = SecurePrefsManager.getCredentials(context)

        if (username.isNullOrEmpty() || password.isNullOrEmpty()) {
            Log.e(TAG, "No credentials available for token renewal")
            return null
        }

        return try {
            val loginRequest = mapOf("username" to username, "password" to password)
            val json = gson.toJson(loginRequest)
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
                    // Store the new token
                    SecurePrefsManager.storeCredentials(context, username, password, token)
                    Log.i(TAG, "Token renewal successful")
                    token
                } else {
                    Log.e(TAG, "Token renewal failed: no token in response")
                    null
                }
            } else {
                Log.e(TAG, "Token renewal failed: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token renewal error", e)
            null
        }
    }
}
