package com.example.osrohden

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Reinicia o MqttService após o boot do dispositivo.
 * Requer a permissão RECEIVE_BOOT_COMPLETED no Manifest.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val i = Intent(context, MqttService::class.java).apply {
                action = MqttService.ACTION_START
            }
            ContextCompat.startForegroundService(context, i)
        }
    }
}
