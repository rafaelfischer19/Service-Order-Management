package com.example.osrohden

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.text.SimpleDateFormat
import java.util.*

// aliases
import com.example.osrohden.Prefs as AppPrefs
import com.example.osrohden.MqttService as AppMqttService

class MainActivity : ComponentActivity() {

    private var rxNew: BroadcastReceiver? = null
    private var rxRemove: BroadcastReceiver? = null

    private val askNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // aceita 1, 2 ou 3 (ambos)
        val ok = !AppPrefs.getName(this).isNullOrBlank() &&
                AppPrefs.getSector(this) in 1..3 &&
                !AppPrefs.getTipo(this).isNullOrBlank()
        if (!ok) {
            startActivity(Intent(this, SetupActivity::class.java).putExtra("from_main", true))
            finish()
            return
        }

        enableEdgeToEdge()
        ensureNotificationPermission()
        maybeAskIgnoreBatteryOptimizations()

        // inicia MQTT service
        ContextCompat.startForegroundService(
            applicationContext,
            Intent(applicationContext, AppMqttService::class.java)
                .setAction(AppMqttService.ACTION_START)
        )

        setContent { App() }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) askNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @SuppressLint("BatteryLife")
    private fun maybeAskIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                runCatching {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun App() {
        val nav = rememberNavController()

        MaterialTheme {
            NavHost(navController = nav, startDestination = "list") {

                composable("list") {
                    val osList = remember {
                        mutableStateListOf<OsItem>().apply { addAll(OsStorage.load(this@MainActivity)) }
                    }
                    var acceptTarget by remember { mutableStateOf<OsItem?>(null) }

                    // NOVAS OS recebidas
                    DisposableEffect(Unit) {
                        val r = object : BroadcastReceiver() {
                            override fun onReceive(context: Context?, intent: Intent?) {
                                if (intent == null) return
                                val machine = intent.getStringExtra(AppMqttService.EXTRA_MAQUINA) ?: return
                                val nome = intent.getStringExtra(AppMqttService.EXTRA_NOME).orEmpty()
                                val tipo = intent.getStringExtra(AppMqttService.EXTRA_TIPO).orEmpty()
                                val setor = intent.getIntExtra(AppMqttService.EXTRA_SETOR, 0)
                                val prio = intent.getStringExtra(AppMqttService.EXTRA_PRIORIDADE).orEmpty()
                                val obs = intent.getStringExtra(AppMqttService.EXTRA_OBS).orEmpty()

                                val resumo = buildString {
                                    if (nome.isNotBlank()) append("Nome: $nome  •  ")
                                    if (tipo.isNotBlank()) append("Tipo: $tipo  •  ")
                                    if (setor in 1..2) append("Setor: $setor  •  ")
                                    if (prio.isNotBlank()) append("Prioridade: $prio  •  ")
                                    if (obs.isNotBlank()) append("Obs: $obs")
                                }

                                val item = OsItem(
                                    machine = machine,
                                    message = resumo,
                                    nome = nome,
                                    tipo = tipo,
                                    setor = setor,
                                    prioridade = prio,
                                    obs = obs
                                )

                                OsStorage.addOrReplaceByMachine(this@MainActivity, item)
                                osList.removeAll { it.machine.equals(machine, true) }
                                osList.add(0, item)
                            }
                        }

                        ContextCompat.registerReceiver(
                            this@MainActivity, r,
                            IntentFilter(AppMqttService.ACTION_NEW_OS),
                            ContextCompat.RECEIVER_NOT_EXPORTED
                        )
                        rxNew = r
                        onDispose { rxNew?.let { unregisterReceiver(it) }; rxNew = null }
                    }

                    // REMOVE quando outro técnico aceita (mas mantém se aceitei aqui)
                    DisposableEffect(Unit) {
                        val r = object : BroadcastReceiver() {
                            override fun onReceive(context: Context?, intent: Intent?) {
                                val machine = intent?.getStringExtra("machine") ?: return
                                // verifica se localmente essa máquina já foi aceita por mim
                                val localAccepted = OsStorage.load(this@MainActivity)
                                    .firstOrNull { it.machine.equals(machine, true) }
                                    ?.accepted == true
                                if (localAccepted) {
                                    // ignora remoção: este aparelho aceitou, precisa continuar vendo para encerrar
                                    return
                                }
                                OsStorage.removeByMachine(this@MainActivity, machine)
                                osList.removeAll { it.machine.equals(machine, true) }
                            }
                        }
                        ContextCompat.registerReceiver(
                            this@MainActivity, r,
                            IntentFilter("REMOVE_OS_BY_MACHINE"),
                            ContextCompat.RECEIVER_NOT_EXPORTED
                        )
                        rxRemove = r
                        onDispose { rxRemove?.let { unregisterReceiver(it) }; rxRemove = null }
                    }

                    // INTERFACE
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Rohden OS") },
                                actions = {
                                    TextButton(onClick = {
                                        startActivity(
                                            Intent(this@MainActivity, SetupActivity::class.java)
                                                .putExtra("from_main", true)
                                        )
                                        finish()
                                    }) { Text("Configurações") }
                                }
                            )
                        }
                    ) { inner ->
                        if (osList.isEmpty()) {
                            EmptyState(inner)
                        } else {
                            OsList(
                                list = osList,
                                innerPadding = inner,
                                onAccept = { acceptTarget = it },
                                onOpen = { nav.navigate("detail/${it.id}") }
                            )
                        }

                        // Diálogo de aceite
                        acceptTarget?.let { item ->
                            AcceptDialog(
                                presetName = AppPrefs.getName(this@MainActivity) ?: "",
                                onConfirm = { typedName ->
                                    val finalName = typedName.trim().ifBlank {
                                        AppPrefs.getName(this@MainActivity) ?: ""
                                    }

                                    // envia aceite oficial p/ servidor
                                    startService(
                                        Intent(applicationContext, AppMqttService::class.java)
                                            .setAction(AppMqttService.ACTION_PUBLISH)
                                            .putExtra(AppMqttService.EXTRA_PUB_TOPIC, AppMqttService.TOPIC_ACCEPT)
                                            .putExtra(
                                                AppMqttService.EXTRA_PUB_PAYLOAD,
                                                "OSAceita/${item.machine}/$finalName"
                                            )
                                    )

                                    // 🔔 Broadcast p/ todos removerem a OS (exceto quem aceitou)
                                    startService(
                                        Intent(applicationContext, AppMqttService::class.java)
                                            .setAction(AppMqttService.ACTION_PUBLISH)
                                            .putExtra(AppMqttService.EXTRA_PUB_TOPIC, AppMqttService.TOPIC_ACCEPT_BC)
                                            .putExtra(AppMqttService.EXTRA_PUB_PAYLOAD, "${item.machine}/$finalName")
                                    )

                                    // ✅ Atualiza localmente e mantém a OS na lista marcada como Aceita
                                    val ts = System.currentTimeMillis()
                                    OsStorage.markAcceptedByMachine(this@MainActivity, item.machine, ts)
                                    osList.replaceAll {
                                        if (it.machine.equals(item.machine, true))
                                            it.copy(accepted = true, acceptedAt = ts)
                                        else it
                                    }
                                    // não remover da lista/storage aqui!
                                    acceptTarget = null
                                },
                                onDismiss = { acceptTarget = null }
                            )
                        }
                    }
                }

                composable(
                    "detail/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { back ->
                    DetailScreen(back.arguments?.getLong("id") ?: -1L) { nav.popBackStack() }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun DetailScreen(osId: Long, onBack: () -> Unit) {
        var item by remember { mutableStateOf(OsStorage.getById(this@MainActivity, osId)) }
        var notes by remember { mutableStateOf(item?.notes.orEmpty()) }
        var extraTechs by remember { mutableStateOf(item?.technicians.orEmpty()) }
        var showAddTech by remember { mutableStateOf(false) }
        var techInput by remember { mutableStateOf("") }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Detalhes da OS") },
                    navigationIcon = { TextButton(onClick = onBack) { Text("Voltar") } }
                )
            }
        ) { inner ->
            if (item == null) {
                Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                    Text("OS não encontrada")
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(inner).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(item!!.machine, style = MaterialTheme.typography.headlineSmall)
                    Text("Recebida: ${fmt(item!!.receivedAt)}")
                    Text("Aceita: ${item!!.acceptedAt?.let { fmt(it) } ?: "—"}")

                    OutlinedTextField(
                        value = notes,
                        onValueChange = {
                            notes = it
                            OsStorage.updateNotes(this@MainActivity, osId, it)
                        },
                        label = { Text("Observações") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Técnicos: ${if (extraTechs.isBlank()) "—" else extraTechs}")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { showAddTech = true }) { Text("Adicionar Técnico") }
                        Button(onClick = {
                            val mainName = AppPrefs.getName(this@MainActivity) ?: "Sem nome"
                            val nomes = if (extraTechs.isBlank()) mainName else "$mainName, $extraTechs"
                            val payload = "${item!!.machine}/OSEncerrada:${notes.ifBlank { "Sem observações" }}/$nomes"

                            startService(
                                Intent(applicationContext, AppMqttService::class.java)
                                    .setAction(AppMqttService.ACTION_PUBLISH)
                                    .putExtra(AppMqttService.EXTRA_PUB_TOPIC, AppMqttService.TOPIC_CLOSE)
                                    .putExtra(AppMqttService.EXTRA_PUB_PAYLOAD, payload)
                            )
                            OsStorage.removeById(this@MainActivity, osId)
                            onBack()
                        }) { Text("Encerrar OS") }
                    }

                    if (showAddTech) {
                        AlertDialog(
                            onDismissRequest = { showAddTech = false },
                            title = { Text("Adicionar Técnico") },
                            text = {
                                OutlinedTextField(
                                    value = techInput,
                                    onValueChange = { techInput = it },
                                    label = { Text("Nome do técnico") },
                                    singleLine = true
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    val name = techInput.trim()
                                    if (name.isNotEmpty()) {
                                        extraTechs =
                                            if (extraTechs.isBlank()) name else "$extraTechs, $name"
                                        OsStorage.updateTechnicians(this@MainActivity, osId, extraTechs)
                                    }
                                    techInput = ""
                                    showAddTech = false
                                }) { Text("Adicionar") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showAddTech = false }) { Text("Cancelar") }
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun EmptyState(inner: PaddingValues) {
        Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
            Text("Aguardando Ordens de Serviço…")
        }
    }

    @Composable
    private fun OsList(
        list: List<OsItem>,
        innerPadding: PaddingValues,
        onAccept: (OsItem) -> Unit,
        onOpen: (OsItem) -> Unit
    ) {
        LazyColumn(
            Modifier.fillMaxSize().padding(innerPadding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(list, key = { it.id }) { os ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(os) }) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(os.machine, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (os.tipo?.isNotBlank() == true)
                                    ChipColor(os.tipo!!, tipoColor(os.tipo!!))
                                if (os.setor in 1..2)
                                    ChipColor("Setor ${os.setor}", Color.Gray)
                                if (os.prioridade?.isNotBlank() == true)
                                    ChipColor(os.prioridade!!, prioColor(os.prioridade!!))
                                if (os.accepted)
                                    ChipColor("Aceita", Color(0xFF2E7D32))
                            }
                            if (os.nome?.isNotBlank() == true) {
                                Spacer(Modifier.height(8.dp))
                                Text("Nome: ${os.nome}", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (os.obs?.isNotBlank() == true) {
                                Spacer(Modifier.height(4.dp))
                                Text("Observações: ${os.obs}", maxLines = 3)
                            }
                            Spacer(Modifier.height(8.dp))
                            val rodape = buildString {
                                append("Recebida: ${fmt(os.receivedAt)}")
                                if (os.acceptedAt != null) append("  •  Aceita: ${fmt(os.acceptedAt!!)}")
                            }
                            Text(rodape, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { if (!os.accepted) onAccept(os) }, enabled = !os.accepted) {
                            Text(if (os.accepted) "Aceita" else "Aceitar")
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ChipColor(text: String, color: Color) {
        AssistChip(
            onClick = { },
            label = { Text(text) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = color,
                labelColor = Color.White
            )
        )
    }

    @Composable
    private fun AcceptDialog(
        presetName: String,
        onConfirm: (String) -> Unit,
        onDismiss: () -> Unit
    ) {
        var name by remember { mutableStateOf(presetName) }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Confirmar aceite") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Digite seu nome para aceitar a OS:")
                    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(name) }) { Text("Aceitar") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        )
    }
}

// ===== Helpers =====
private fun tipoColor(tipo: String): Color = when (tipo.trim().lowercase()) {
    "eletrico", "elétrico" -> Color(0xFF1565C0)
    "mecanico", "mecânico" -> Color(0xFF6A1B9A)
    else -> Color(0xFF546E7A)
}

private fun prioColor(p: String): Color = when (p.trim().uppercase()) {
    "P1" -> Color(0xFF2E7D32)
    "P2" -> Color(0xFFFFC107)
    "P3" -> Color(0xFFFF6D00)
    "P4" -> Color(0xFFB00020)
    else -> Color(0xFF455A64)
}

private fun fmt(ts: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(ts))
}
