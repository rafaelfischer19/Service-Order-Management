@file:Suppress("SpellCheckingInspection")

package com.example.osrohden

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class SetupActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔹 Detecta se veio explicitamente da MainActivity
        val fromMain = intent.getBooleanExtra("from_main", false)

        // 🔹 Se já configurado e não veio da Main, pula direto para a tela principal
        val ok = !Prefs.getName(this).isNullOrBlank() &&
                Prefs.getSector(this) in 1..3 && // 3 = ambos
                !Prefs.getTipo(this).isNullOrBlank()

        if (ok && !fromMain) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContent {
            MaterialTheme {
                var nome by remember { mutableStateOf(Prefs.getName(this) ?: "") }
                var tipo by remember { mutableStateOf(Prefs.getTipo(this) ?: "Eletrico") }
                val current = Prefs.getSectors(this)
                var setor1 by remember { mutableStateOf(current.contains(1)) }
                var setor2 by remember { mutableStateOf(current.contains(2)) }

                Scaffold(
                    topBar = { TopAppBar(title = { Text("Configuração inicial") }) }
                ) { innerPadding: PaddingValues ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = nome,
                            onValueChange = { nome = it },
                            label = { Text("Nome do técnico") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Tipo de atendimento
                        Column {
                            Text("Tipo de atendimento")
                            Row(modifier = Modifier.padding(top = 6.dp)) {
                                RadioButton(
                                    selected = (tipo == "Eletrico"),
                                    onClick = { tipo = "Eletrico" }
                                )
                                Text("Elétrico", modifier = Modifier.padding(end = 16.dp, top = 10.dp))
                                RadioButton(
                                    selected = (tipo == "Mecanico"),
                                    onClick = { tipo = "Mecanico" }
                                )
                                Text("Mecânico", modifier = Modifier.padding(top = 10.dp))
                            }
                        }

                        // Seleção de Setores (um ou ambos)
                        Column {
                            Text("Setor (pode marcar ambos)")
                            Row(modifier = Modifier.padding(top = 6.dp)) {
                                FilterChip(
                                    selected = setor1,
                                    onClick = { setor1 = !setor1 },
                                    label = { Text("Setor 1") }
                                )
                                Spacer(Modifier.width(16.dp))
                                FilterChip(
                                    selected = setor2,
                                    onClick = { setor2 = !setor2 },
                                    label = { Text("Setor 2") }
                                )
                            }
                        }

                        // Botão de salvar
                        Button(
                            onClick = {
                                val chosen = buildSet {
                                    if (setor1) add(1)
                                    if (setor2) add(2)
                                }

                                Prefs.setName(this@SetupActivity, nome)
                                Prefs.setTipo(this@SetupActivity, tipo)
                                Prefs.setSectors(this@SetupActivity, chosen)

                                startActivity(Intent(this@SetupActivity, MainActivity::class.java))
                                finish()
                            },
                            enabled = nome.isNotBlank() && (setor1 || setor2),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Salvar e continuar")
                        }
                    }
                }
            }
        }
    }
}
