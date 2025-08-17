package com.cool2645.sms_relay

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class SettingsActivity : AppCompatActivity() {
    data class User(
        val id: String?,
        val username: String?,
        val user_type: String?,
        val name: String?,
        val device_id: String?,
        val email: String?,
        val created_at: String?,
        val updated_at: String?
    )
    data class LoginResponse(
        val user: User?,
        val token: String?,
        val token_expire_after: String?
    )
    data class LoginRequest(val username: String, val password: String)

    companion object {
        private const val REQUEST_CODE_PHONE_PERMISSIONS = 1001
        private const val REQUEST_CODE_NOTIFICATION_PERMISSION = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE_NOTIFICATION_PERMISSION)
        }
        // Initialize EncryptedSharedPreferences
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val encryptedPrefs = EncryptedSharedPreferences.create(
            this,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        val usernameEditText = findViewById<EditText>(R.id.usernameEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val userProfileTextView = findViewById<TextView>(R.id.userProfileTextView)
        val phoneNumbersContainer = findViewById<android.widget.LinearLayout>(R.id.phoneNumbersContainer)
        // Auto-load saved username and password if present
        val savedUsername = encryptedPrefs.getString("username", null)
        val savedPassword = encryptedPrefs.getString("password", null)
        val savedToken = encryptedPrefs.getString("token", null)
        if (!savedUsername.isNullOrEmpty()) {
            usernameEditText.setText(savedUsername)
        }
        if (!savedPassword.isNullOrEmpty()) {
            passwordEditText.setText(savedPassword)
        }
        // Try to load user profile if token is present
        if (!savedToken.isNullOrEmpty()) {
            fetchUserProfile(
                token = savedToken,
                encryptedPrefs = encryptedPrefs,
                userProfileTextView = userProfileTextView,
                username = savedUsername,
                password = savedPassword
            )
        } else {
            userProfileTextView.text = getString(R.string.login_profile_placeholder)
        }
        val loginButton = findViewById<Button>(R.id.loginButton)
        loginButton.setOnClickListener {
            handleLoginClick(encryptedPrefs, userProfileTextView)
        }
        val logoutButton = findViewById<Button>(R.id.logoutButton)
        logoutButton.setOnClickListener {
            encryptedPrefs.edit()
                .remove("token")
                .apply()
            userProfileTextView.text = getString(R.string.login_profile_placeholder)
        }
        // Request phone permissions and load phone numbers
        checkAndLoadPhoneNumbers(phoneNumbersContainer)
    }

    private fun handleLoginClick(encryptedPrefs: android.content.SharedPreferences, userProfileTextView: TextView) {
        val usernameEditText = findViewById<EditText>(R.id.usernameEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val username = usernameEditText.text.toString()
        val password = passwordEditText.text.toString()

        val loginRequest = LoginRequest(username, password)
        val gson = Gson()
        val json = gson.toJson(loginRequest)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val body = json.toRequestBody(mediaType)
                val request = Request.Builder()
                    .url("${ApiConfig.API_BASE_URL}/login")
                    .post(body)
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    val errorMsg = responseBody ?: "Login failed with status code ${response.code}"
                    runOnUiThread {
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle(getString(R.string.login_error))
                            .setMessage(errorMsg)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                } else {
                    val loginResponse = try { gson.fromJson(responseBody, LoginResponse::class.java) } catch (e: Exception) { null }
                    val token = loginResponse?.token
                    // Store username, password, and token securely
                    encryptedPrefs.edit()
                        .putString("username", username)
                        .putString("password", password)
                        .putString("token", token)
                        .apply()
                    // Show login success popup, then fetch user profile
                    runOnUiThread {
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle(getString(R.string.login_success_title))
                            .setMessage(getString(R.string.login_success))
                            .setPositiveButton("OK") { _, _ ->
                                if (!token.isNullOrEmpty()) {
                                    fetchUserProfile(token, encryptedPrefs, userProfileTextView, username, password)
                                } else {
                                    userProfileTextView.text = getString(R.string.login_profile_placeholder)
                                }
                            }
                            .show()
                    }
                }
            } catch (e: Exception) {
                Log.e("SettingsActivity", "Login error", e)
                runOnUiThread {
                    AlertDialog.Builder(this@SettingsActivity)
                        .setTitle(getString(R.string.login_error))
                        .setMessage(e.localizedMessage ?: "Unknown error")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun fetchUserProfile(
        token: String,
        encryptedPrefs: android.content.SharedPreferences,
        userProfileTextView: TextView,
        username: String?,
        password: String?,
        hasRetriedLogin: Boolean = false
    ) {
        val gson = Gson()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("${ApiConfig.API_BASE_URL}/user")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                if (response.isSuccessful) {
                    val user = try { gson.fromJson(responseBody, User::class.java) } catch (e: Exception) { null }
                    val userText = user?.let {
                        "ID: ${it.id}\nUsername: ${it.username}\nType: ${it.user_type}\nName: ${it.name}\nDevice ID: ${it.device_id}\nEmail: ${it.email}"
                    } ?: getString(R.string.failed_parse_user_profile)
                    runOnUiThread {
                        userProfileTextView.text = userText
                    }
                } else if ((response.code == 401 || response.code == 403) && !hasRetriedLogin && !username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                    // Try to renew token once
                    renewTokenAndRetry(
                        username = username,
                        password = password,
                        encryptedPrefs = encryptedPrefs,
                        userProfileTextView = userProfileTextView
                    )
                } else {
                    runOnUiThread {
                        userProfileTextView.text = getString(R.string.login_profile_placeholder)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    userProfileTextView.text = getString(R.string.login_profile_placeholder)
                }
            }
        }
    }

    private fun renewTokenAndRetry(
        username: String,
        password: String,
        encryptedPrefs: android.content.SharedPreferences,
        userProfileTextView: TextView
    ) {
        val gson = Gson()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val loginRequest = LoginRequest(username, password)
                val json = gson.toJson(loginRequest)
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val body = json.toRequestBody(mediaType)
                val request = Request.Builder()
                    .url("${ApiConfig.API_BASE_URL}/login")
                    .post(body)
                    .build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                if (response.isSuccessful) {
                    val loginResponse = try { gson.fromJson(responseBody, LoginResponse::class.java) } catch (e: Exception) { null }
                    val token = loginResponse?.token
                    // Store new token
                    encryptedPrefs.edit().putString("token", token).apply()
                    // Retry fetching user profile with new token, but do not retry again if it fails
                    if (!token.isNullOrEmpty()) {
                        fetchUserProfile(token, encryptedPrefs, userProfileTextView, username, password, hasRetriedLogin = true)
                    } else {
                        runOnUiThread {
                            userProfileTextView.text = getString(R.string.login_profile_placeholder)
                        }
                    }
                } else {
                    runOnUiThread {
                        userProfileTextView.text = getString(R.string.login_profile_placeholder)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    userProfileTextView.text = getString(R.string.login_profile_placeholder)
                }
            }
        }
    }

    private fun checkAndLoadPhoneNumbers(phoneNumbersContainer: android.widget.LinearLayout) {
        val permissions = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECEIVE_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissions.add(Manifest.permission.READ_PHONE_NUMBERS)
        }
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), REQUEST_CODE_PHONE_PERMISSIONS)
        } else {
            loadPhoneNumbers(phoneNumbersContainer)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PHONE_PERMISSIONS) {
            val phoneNumbersContainer = findViewById<android.widget.LinearLayout>(R.id.phoneNumbersContainer)
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                loadPhoneNumbers(phoneNumbersContainer)
            } else {
                phoneNumbersContainer.removeAllViews()
                val tv = TextView(this)
                tv.text = getString(R.string.sim_permission_denied)
                phoneNumbersContainer.addView(tv)
            }
        } else if (requestCode == REQUEST_CODE_NOTIFICATION_PERMISSION) {
            // No action needed, just requesting permission
        }
    }

    private fun loadPhoneNumbers(phoneNumbersContainer: android.widget.LinearLayout) {
        val subscriptionManager = getSystemService(SubscriptionManager::class.java)
        val telephonyManager = getSystemService(TelephonyManager::class.java)
        val prefs = getSharedPreferences("sms_relay_prefs", MODE_PRIVATE)
        val subs = if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            subscriptionManager.activeSubscriptionInfoList
        } else null
        runOnUiThread {
            phoneNumbersContainer.removeAllViews()
            if (subs != null && subs.isNotEmpty()) {
                for (sub in subs) {
                    val number = if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED) {
                        // Deprecated, but required for API < 33 compatibility
                        sub.number
                    } else {
                        telephonyManager.line1Number
                    }
                    val displayNumber = if (!number.isNullOrEmpty()) number else "(unavailable)"
                    val numberKey = if (!number.isNullOrEmpty()) "sms_relay_enabled_$number" else null
                    val slotKey = "sms_relay_enabled_slot_${sub.simSlotIndex}"
                    val enabled = (numberKey?.let { prefs.getBoolean(it, false) } == true) || prefs.getBoolean(slotKey, false)
                    val row = android.widget.LinearLayout(this)
                    row.orientation = android.widget.LinearLayout.HORIZONTAL
                    row.layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    val tv = TextView(this)
                    tv.text = "SIM Slot ${sub.simSlotIndex + 1}: $displayNumber"
                    tv.layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    val toggle = android.widget.Switch(this)
                    toggle.layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    toggle.isChecked = enabled
                    toggle.setOnCheckedChangeListener { _, isChecked ->
                        if (numberKey != null) prefs.edit().putBoolean(numberKey, isChecked).apply()
                        prefs.edit().putBoolean(slotKey, isChecked).apply()
                    }
                    row.addView(tv)
                    row.addView(toggle)
                    phoneNumbersContainer.addView(row)
                }
            } else {
                val tv = TextView(this)
                tv.text = getString(R.string.no_sim_cards)
                phoneNumbersContainer.addView(tv)
            }
        }
    }
}
