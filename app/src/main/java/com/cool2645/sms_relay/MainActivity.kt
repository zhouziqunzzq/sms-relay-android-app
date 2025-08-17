package com.cool2645.sms_relay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.json.JSONArray

class MainActivity : ComponentActivity() {
    private var smsLog = mutableListOf<String>()
    private var smsLogTextView: TextView? = null
    private val smsLogReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.cool2645.sms_relay.SMS_LOG") {
                val from = intent.getStringExtra("from") ?: "(unknown)"
                val body = intent.getStringExtra("body") ?: ""
                val recipient = intent.getStringExtra("recipient") ?: "(unknown)"
                val slot = intent.getIntExtra("slot", -1)
                val slotStr = if (slot >= 0) slot.toString() else "(unknown)"
                val logLine = "From: $from\nTo: $recipient\nSlot: $slotStr\n$body"
                smsLog.add(0, logLine)
                smsLogTextView?.text = smsLog.joinToString("\n\n")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        smsLogTextView = findViewById(R.id.smsLogTextView)
        val openSettingsButton = findViewById<Button>(R.id.openSettingsButton)
        openSettingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        // Load persisted log from SharedPreferences
        val logPrefs = getSharedPreferences("sms_log", Context.MODE_PRIVATE)
        val logArray = JSONArray(logPrefs.getString("log", "[]"))
        for (i in 0 until logArray.length()) {
            smsLog.add(logArray.getString(i))
        }
        smsLogTextView?.text = smsLog.joinToString("\n\n")
        // Register local broadcast receiver
        LocalBroadcastManager.getInstance(this).registerReceiver(smsLogReceiver, IntentFilter("com.cool2645.sms_relay.SMS_LOG"))
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(smsLogReceiver)
    }
}