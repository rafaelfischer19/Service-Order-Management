package com.example.osrohden

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

class MqttService : Service() {

    companion object {
        const val ACTION_START      = "START"
        const val ACTION_STOP       = "STOP"
        const val ACTION_PUBLISH    = "PUBLISH"

        // broadcast interno para a UI
        const val ACTION_NEW_OS     = "NEW_OS"
        const val EXTRA_JSON        = "EXTRA_JSON"
        const val EXTRA_MAQUINA     = "EXTRA_MAQUINA"
        const val EXTRA_NOME        = "EXTRA_NOME"
        const val EXTRA_TIPO        = "EXTRA_TIPO"
        const val EXTRA_SETOR       = "EXTRA_SETOR"
        const val EXTRA_PRIORIDADE  = "EXTRA_PRIORIDADE"
        const val EXTRA_OBS         = "EXTRA_OBS"

        const val EXTRA_PUB_TOPIC   = "PUB_TOPIC"
        const val EXTRA_PUB_PAYLOAD = "PUB_PAYLOAD"

        // tópicos
        const val TOPIC_SET1        = "/OSRAM1"
        const val TOPIC_SET2        = "/OSRAM2"
        const val TOPIC_ACCEPT_BC   = "/OSAceitaBroadcast"   // <- todos recebem p/ remover
        const val TOPIC_ACCEPT      = "/OSRAMAC"             // (se quiser separar aceite)
        const val TOPIC_CLOSE       = "/OSRAMEncerrada"

        private const val CHAN_STATUS     = "os_status"
        private const val NOTI_ID_STATUS  = 1001
    }

    private var client: Mqtt3AsyncClient? = null
    private var host  = "ec2-54-160-176-23.compute-1.amazonaws.com"
    private var port  = 1883

    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var connectivityManager: ConnectivityManager? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    // ===== utils =====
    private fun norm(s: String?): String {
        if (s.isNullOrBlank()) return ""
        val n = Normalizer.normalize(s, Normalizer.Form.NFD)
        return n.replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()
    }
    private fun tipoMatches(msgTipo: String?, userTipo: String?): Boolean {
        val mt = norm(msgTipo); val ut = norm(userTipo)
        return ut.isBlank() || mt == ut
    }
    private fun setorMatches(msgSetor: Int, userSetor: Int): Boolean {
        return (userSetor !in 1..2) || msgSetor == userSetor
    }

    private fun persistentClientId(): String {
        val sp = getSharedPreferences("mqtt_prefs", Context.MODE_PRIVATE)
        val saved = sp.getString("cid", null)
        if (!saved.isNullOrBlank()) return saved
        val cid = "android-" + UUID.randomUUID().toString().take(8)
        sp.edit().putString("cid", cid).apply()
        return cid
    }

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        registerNetworkCallback()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterNetworkCallback()
        client?.disconnect()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // reergue o serviço se o usuário "varrer" o app
        val i = Intent(applicationContext, MqttService::class.java).apply { action = ACTION_START }
        if (Build.VERSION.SDK_INT >= 26) applicationContext.startForegroundService(i)
        else applicationContext.startService(i)
        super.onTaskRemoved(rootIntent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_PUBLISH -> {
                val t = intent.getStringExtra(EXTRA_PUB_TOPIC)
                val p = intent.getStringExtra(EXTRA_PUB_PAYLOAD)
                if (!t.isNullOrBlank() && p != null) publishNow(t, p)
            }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    private fun start() {
        ensureChannel()
        postStatus("Conectando…", ongoing = true)
        connectAndSubscribe()

        // watchdog de 15s
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                val ok = client?.state?.isConnected ?: false
                if (!ok && isNetworkUp()) connectAndSubscribe()
                mainHandler.postDelayed(this, 15_000)
            }
        }, 15_000)
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHAN_STATUS, "OS Status", NotificationManager.IMPORTANCE_LOW)
            )
        }

        val intent = Intent(this, MainActivity::class.java)
        val pending: PendingIntent = TaskStackBuilder.create(this).run {
            addNextIntentWithParentStack(intent)
            val flags = if (Build.VERSION.SDK_INT >= 23)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
            getPendingIntent(0, flags)!!
        }

        val notif: Notification = NotificationCompat.Builder(this, CHAN_STATUS)
            .setContentTitle("OS Rohden")
            .setContentText("Iniciando…")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
        startForeground(NOTI_ID_STATUS, notif)
    }

    private fun postStatus(text: String, ongoing: Boolean = true) {
        val nm = getSystemService(NotificationManager::class.java)
        val notif = NotificationCompat.Builder(this, CHAN_STATUS)
            .setContentTitle("OS Rohden")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(ongoing)
            .build()
        nm.notify(NOTI_ID_STATUS, notif)
    }

    private fun connectAndSubscribe() {
        client?.disconnect()

        val cid = persistentClientId()
        client = MqttClient.builder()
            .useMqttVersion3()
            .serverHost(host)
            .serverPort(port)
            .identifier(cid)
            .buildAsync()

        client?.connectWith()
            ?.cleanSession(false)
            ?.keepAlive(30)
            ?.willPublish()
            ?.topic("/OSapp/LWT/$cid")
            ?.qos(MqttQos.AT_LEAST_ONCE)
            ?.payload("offline".toByteArray())
            ?.applyWillPublish()
            ?.send()
            ?.whenComplete { _, ex ->
                if (ex != null) {
                    postStatus("Falha ao conectar: ${ex.message}")
                } else {
                    publishNow("/OSapp/LWT/$cid", "online")
                    subscribeAll()
                    postStatus("Conectado. Assinando…")
                }
            }
    }

    private fun subscribeAll() {
        val c = client ?: return
        val topics = listOf(TOPIC_SET1, TOPIC_SET2, TOPIC_ACCEPT_BC)
        topics.forEach { t ->
            c.subscribeWith()
                ?.topicFilter(t)
                ?.qos(MqttQos.AT_LEAST_ONCE)
                ?.callback { publish: Mqtt3Publish ->
                    val payload = String(publish.payloadAsBytes, StandardCharsets.UTF_8).trim()
                    handleIncoming(t, payload)
                }
                ?.send()
        }
        postStatus("Assinado em ${topics.joinToString()}")
    }

    private fun handleIncoming(topic: String, payload: String) {
        val myName   = Prefs.getName(this) ?: ""
        val mySector = Prefs.getSector(this)
        val myTipo   = Prefs.getTipo(this) ?: ""

        // 1) alguém aceitou: remover nos outros aparelhos
        if (topic == TOPIC_ACCEPT_BC) {
            // formato: MAQUINA/NOME_TECNICO
            val parts = payload.split('/')
            if (parts.size >= 2) {
                val maq  = parts[0].trim().uppercase(Locale.ROOT)
                val tech = parts[1].trim()
                if (!tech.equals(myName, ignoreCase = true)) {
                    sendBroadcast(
                        Intent("REMOVE_OS_BY_MACHINE")
                            .setPackage(packageName)
                            .putExtra("machine", maq)
                    )
                }
            }
            return
        }

        // 2) cartões normais (JSON) com filtros de setor/tipo
        val json = runCatching { gson.fromJson(payload, JsonObject::class.java) }.getOrNull()
        if (json != null && json.has("maquina")) {
            val machine    = json.get("maquina")?.asString?.uppercase(Locale.ROOT)?.trim() ?: return
            val tipoMsg    = json.get("tipo")?.asString
            val setorMsg   = json.get("setor")?.asInt ?: 0
            val nome       = json.get("nome")?.asString ?: ""
            val prioridade = json.get("prioridade")?.asString ?: ""
            val obs        = json.get("obs")?.asString ?: ""

            if (!setorMatches(setorMsg, mySector)) return
            if (!tipoMatches(tipoMsg, myTipo)) return

            sendBroadcast(
                Intent(ACTION_NEW_OS)
                    .setPackage(packageName)
                    .putExtra(EXTRA_JSON, payload)
                    .putExtra(EXTRA_MAQUINA, machine)
                    .putExtra(EXTRA_NOME, nome)
                    .putExtra(EXTRA_TIPO, tipoMsg ?: "")
                    .putExtra(EXTRA_SETOR, setorMsg)
                    .putExtra(EXTRA_PRIORIDADE, prioridade)
                    .putExtra(EXTRA_OBS, obs)
            )
            return
        }

        // 3) fallback payload = nome da máquina
        val machine = payload.ifBlank { return }
        sendBroadcast(
            Intent(ACTION_NEW_OS).setPackage(packageName).putExtra(EXTRA_MAQUINA, machine)
        )
    }

    private fun publishNow(topic: String, payload: String) {
        val c = client ?: return
        if (!(c.state?.isConnected ?: false)) return
        c.publishWith()
            .topic(topic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .payload(payload.toByteArray(StandardCharsets.UTF_8))
            .send()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ===== rede =====
    private fun isNetworkUp(): Boolean {
        val cm = connectivityManager ?: return false
        val n = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(n) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun registerNetworkCallback() {
        val cm = connectivityManager ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                connectAndSubscribe()
            }
            override fun onLost(network: Network) {
                postStatus("Rede indisponível")
            }
        }
        cm.registerDefaultNetworkCallback(cb)
        netCallback = cb
    }

    private fun unregisterNetworkCallback() {
        val cm = connectivityManager ?: return
        netCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        netCallback = null
    }
}
