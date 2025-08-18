package com.cool2645.sms_relay.utils

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cool2645.sms_relay.Constants

/**
 * Utility class for managing encrypted SharedPreferences operations
 */
object SecurePrefsManager {

    /**
     * Creates and returns encrypted SharedPreferences instance
     */
    fun getEncryptedPrefs(context: Context): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            Constants.SECURE_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Stores login credentials securely
     */
    fun storeCredentials(context: Context, username: String, password: String, token: String?) {
        val prefs = getEncryptedPrefs(context)
        prefs.edit()
            .putString(Constants.PREF_USERNAME, username)
            .putString(Constants.PREF_PASSWORD, password)
            .putString(Constants.PREF_TOKEN, token)
            .apply()
    }

    /**
     * Alternative method name for backwards compatibility
     */
    fun saveCredentials(context: Context, username: String, password: String, token: String?) {
        storeCredentials(context, username, password, token)
    }

    /**
     * Save only the token
     */
    fun saveToken(context: Context, token: String?) {
        getEncryptedPrefs(context).edit()
            .putString(Constants.PREF_TOKEN, token)
            .apply()
    }

    /**
     * Retrieves stored token
     */
    fun getToken(context: Context): String? {
        return getEncryptedPrefs(context).getString(Constants.PREF_TOKEN, null)
    }

    /**
     * Retrieves stored username and password
     */
    fun getCredentials(context: Context): Pair<String?, String?> {
        val prefs = getEncryptedPrefs(context)
        return Pair(
            prefs.getString(Constants.PREF_USERNAME, null),
            prefs.getString(Constants.PREF_PASSWORD, null)
        )
    }

    /**
     * Clears stored token (for logout)
     */
    fun clearToken(context: Context) {
        getEncryptedPrefs(context).edit()
            .remove(Constants.PREF_TOKEN)
            .apply()
    }
}
