package com.example.osrohden

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Estrutura completa e compatível com a UI atual
data class OsItem(
    val id: Long = System.currentTimeMillis(),
    val machine: String,
    val message: String = "",
    val receivedAt: Long = System.currentTimeMillis(),
    val acceptedAt: Long? = null,
    val accepted: Boolean = false,
    val notes: String = "",
    val technicians: String = "",
    // Campos estruturados exibidos no card:
    val nome: String = "",
    val tipo: String = "",
    val setor: Int = 0,
    val prioridade: String = "",
    val obs: String = ""
)

object OsStorage {
    private const val PREFS = "os_prefs"
    private const val KEY = "items"
    private val gson = Gson()
    private val type = object : TypeToken<MutableList<OsItem>>() {}.type

    /** Carrega a lista de OS do armazenamento local */
    fun load(context: Context): MutableList<OsItem> {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = sp.getString(KEY, null) ?: return mutableListOf()
        return runCatching { gson.fromJson<MutableList<OsItem>>(json, type) }
            .getOrElse { mutableListOf() }
    }

    /** Salva a lista completa */
    private fun save(context: Context, list: List<OsItem>) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().putString(KEY, gson.toJson(list)).apply()
    }

    /** Limpa todas as OS */
    fun clear(context: Context) = save(context, emptyList())

    /** Adiciona ou substitui uma OS existente com base na máquina */
    fun addOrReplaceByMachine(context: Context, item: OsItem) {
        val list = load(context)
        val idx = list.indexOfFirst { it.machine.equals(item.machine, true) }
        if (idx >= 0) {
            val prev = list[idx]
            list[idx] = item.copy(
                id = prev.id,
                receivedAt = prev.receivedAt,
                acceptedAt = prev.acceptedAt,
                accepted = prev.accepted,
                notes = prev.notes,
                technicians = prev.technicians
            )
        } else {
            list.add(0, item)
        }
        save(context, list)
    }

    /** Marca uma OS como aceita (usado localmente após aceite) */
    fun markAcceptedByMachine(context: Context, machine: String, acceptedAt: Long) {
        val list = load(context).map {
            if (it.machine.equals(machine, true))
                it.copy(accepted = true, acceptedAt = acceptedAt)
            else it
        }
        save(context, list)
    }

    /** Atualiza observações de uma OS específica */
    fun updateNotes(context: Context, id: Long, notes: String) {
        val list = load(context).map { if (it.id == id) it.copy(notes = notes) else it }
        save(context, list)
    }

    /** Atualiza técnicos adicionais */
    fun updateTechnicians(context: Context, id: Long, technicians: String) {
        val list = load(context).map {
            if (it.id == id) it.copy(technicians = technicians) else it
        }
        save(context, list)
    }

    /** Busca uma OS pelo ID */
    fun getById(context: Context, id: Long): OsItem? =
        load(context).firstOrNull { it.id == id }

    /** Remove uma OS pelo ID */
    fun removeById(context: Context, id: Long) {
        val list = load(context).filterNot { it.id == id }
        save(context, list)
    }

    /** Remove uma OS pelo código da máquina (quando outro técnico aceita) */
    fun removeByMachine(context: Context, machine: String) {
        val list = load(context).filterNot { it.machine.equals(machine, true) }
        save(context, list)
    }

    /** Verifica se já existe uma OS dessa máquina salva */
    fun existsMachine(context: Context, machine: String): Boolean {
        return load(context).any { it.machine.equals(machine, true) }
    }

    /** Retorna o total de OS aceitas localmente (para estatísticas futuras) */
    fun countAccepted(context: Context): Int {
        return load(context).count { it.accepted }
    }
}
