package com.cool2645.sms_relay.models

/**
 * Data models for API requests and responses
 */

/**
 * Request model for SMS forwarding
 */
data class SMSRequest(
    val phone_number: String,
    val from: String,
    val body: String
)

/**
 * Login request model
 */
data class LoginRequest(
    val username: String,
    val password: String
)

/**
 * User model from API response
 */
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

/**
 * Login response model
 */
data class LoginResponse(
    val user: User?,
    val token: String?,
    val token_expire_after: String?
)

/**
 * SMS log entry model
 */
data class SmsLogEntry(
    val timestamp: String,
    val from: String,
    val recipient: String,
    val slot: Int,
    val status: String,
    val body: String
) {
    /**
     * Formats the log entry for display
     */
    fun toDisplayString(): String {
        val slotStr = if (slot >= 0) slot.toString() else "(unknown)"
        return "[$timestamp] From: $from\nTo: $recipient\nSlot: $slotStr\nStatus: $status\n$body"
    }
}
