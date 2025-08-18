package com.cool2645.sms_relay

/**
 * Application-wide constants to maintain consistency and avoid magic numbers/strings
 */
object Constants {
    // SharedPreferences keys
    const val SMS_RELAY_PREFS = "sms_relay_prefs"
    const val SMS_LOG_PREFS = "sms_log"
    const val SECURE_PREFS = "secure_prefs"

    // Preference keys
    const val PREF_USERNAME = "username"
    const val PREF_PASSWORD = "password"
    const val PREF_TOKEN = "token"
    const val PREF_LOG = "log"

    // SMS relay preferences
    const val SMS_RELAY_ENABLED_PREFIX = "sms_relay_enabled_"
    const val SMS_RELAY_ENABLED_SLOT_PREFIX = "sms_relay_enabled_slot_"

    // Broadcast actions
    const val SMS_LOG_ACTION = "com.cool2645.sms_relay.SMS_LOG"

    // Intent extras
    const val EXTRA_FROM = "from"
    const val EXTRA_BODY = "body"
    const val EXTRA_RECIPIENT = "recipient"
    const val EXTRA_SLOT = "slot"
    const val EXTRA_TIMESTAMP = "timestamp"
    const val EXTRA_FORWARDING_STATUS = "forwardingStatus"

    // Status indicators
    const val STATUS_PENDING = "⏳ Pending"
    const val STATUS_FORWARDED = "✓ Forwarded"
    const val STATUS_FAILED = "✗ Failed"
    const val STATUS_UNKNOWN = "✗ Unknown"

    // Logging
    const val MAX_LOG_ENTRIES = 100
    const val DATE_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss"

    // Permission request codes
    const val REQUEST_CODE_PHONE_PERMISSIONS = 1001
    const val REQUEST_CODE_NOTIFICATION_PERMISSION = 1002

    // Service notification
    const val NOTIFICATION_ID = 1001
    const val NOTIFICATION_CHANNEL_ID = "SMS_RELAY_CHANNEL"
    const val NOTIFICATION_CHANNEL_NAME = "SMS Relay Service"

    // UI messages
    const val DEFAULT_LOG_MESSAGE = "SMS log will appear here."
    const val UNKNOWN_PLACEHOLDER = "(unknown)"
}
