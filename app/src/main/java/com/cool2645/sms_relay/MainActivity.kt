package com.cool2645.sms_relay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.cool2645.sms_relay.models.SmsLogEntry
import com.cool2645.sms_relay.utils.SmsLogManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main activity that displays SMS relay logs and provides service control
 */
class MainActivity : ComponentActivity() {

    // UI Components
    private var smsLog = mutableListOf<SmsLogEntry>()
    private var smsLogTextView: TextView? = null
    private var scrollView: ScrollView? = null
    private var serviceToggleButton: Button? = null

    // Service state
    private var isServiceRunning = false

    /**
     * Broadcast receiver for SMS log updates
     */
    private val smsLogReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            handleSmsLogBroadcast(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        startForegroundService()
        setupButtonListeners()
        loadPersistedLogs()
        registerBroadcastReceiver()
    }

    /**
     * Initialize all UI components
     */
    private fun initializeViews() {
        smsLogTextView = findViewById(R.id.smsLogTextView)
        scrollView = findViewById(R.id.scrollView)
        serviceToggleButton = findViewById(R.id.serviceToggleButton)

        // Initialize service state
        isServiceRunning = true
        updateServiceButton()
    }

    /**
     * Start the foreground service to keep the app alive
     */
    private fun startForegroundService() {
        val serviceIntent = Intent(this, SmsRelayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    /**
     * Setup click listeners for all buttons
     */
    private fun setupButtonListeners() {
        serviceToggleButton?.setOnClickListener { toggleService() }

        findViewById<Button>(R.id.openSettingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.clearLogsButton).setOnClickListener {
            clearAllLogs()
        }
    }

    /**
     * Load persisted logs from storage and display them
     */
    private fun loadPersistedLogs() {
        val persistedLogs = SmsLogManager.loadPersistedLog(this)

        if (persistedLogs.isEmpty()) {
            smsLogTextView?.text = Constants.DEFAULT_LOG_MESSAGE
        } else {
            smsLog.addAll(persistedLogs)
            updateLogDisplay()
        }
    }

    /**
     * Register broadcast receiver for SMS log updates
     */
    private fun registerBroadcastReceiver() {
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(smsLogReceiver, IntentFilter(Constants.SMS_LOG_ACTION))
    }

    /**
     * Handle incoming SMS log broadcast
     */
    private fun handleSmsLogBroadcast(intent: Intent?) {
        if (intent?.action != Constants.SMS_LOG_ACTION) return

        val from = intent.getStringExtra(Constants.EXTRA_FROM) ?: Constants.UNKNOWN_PLACEHOLDER
        val body = intent.getStringExtra(Constants.EXTRA_BODY) ?: ""
        val recipient = intent.getStringExtra(Constants.EXTRA_RECIPIENT) ?: Constants.UNKNOWN_PLACEHOLDER
        val slot = intent.getIntExtra(Constants.EXTRA_SLOT, -1)
        val timestamp = intent.getStringExtra(Constants.EXTRA_TIMESTAMP)
            ?: SimpleDateFormat(Constants.DATE_FORMAT_PATTERN, Locale.getDefault()).format(Date())
        val forwardingStatus = intent.getStringExtra(Constants.EXTRA_FORWARDING_STATUS)
            ?: Constants.STATUS_UNKNOWN

        val logEntry = SmsLogEntry(
            timestamp = timestamp,
            from = from,
            recipient = recipient,
            slot = slot,
            status = forwardingStatus,
            body = body
        )

        // Add to beginning of list (newest first)
        smsLog.add(0, logEntry)

        // Apply cap to in-memory list
        while (smsLog.size > Constants.MAX_LOG_ENTRIES) {
            smsLog.removeAt(smsLog.size - 1)
        }

        updateLogDisplay()
    }

    /**
     * Update the log display in the UI
     */
    private fun updateLogDisplay() {
        if (smsLog.isEmpty()) {
            smsLogTextView?.text = Constants.DEFAULT_LOG_MESSAGE
        } else {
            val displayText = smsLog.joinToString("\n\n") { it.toDisplayString() }
            smsLogTextView?.text = displayText
        }
    }

    /**
     * Clear all logs from memory and persistent storage
     */
    private fun clearAllLogs() {
        smsLog.clear()
        SmsLogManager.clearAllLogs(this)
        smsLogTextView?.text = Constants.DEFAULT_LOG_MESSAGE
    }

    /**
     * Toggle the background service on/off
     */
    private fun toggleService() {
        isServiceRunning = !isServiceRunning
        updateServiceButton()

        val serviceIntent = Intent(this, SmsRelayService::class.java)
        if (isServiceRunning) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } else {
            stopService(serviceIntent)
        }
    }

    /**
     * Update service button text based on current state
     */
    private fun updateServiceButton() {
        serviceToggleButton?.text = if (isServiceRunning) {
            "Stop Service"
        } else {
            "Start Service"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(smsLogReceiver)
    }
}
