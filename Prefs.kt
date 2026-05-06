package com.example.osrohden

import android.content.Context
import androidx.core.content.edit

object Prefs {
    private const val FILE = "os_prefs"
    private const val KEY_NAME = "user_name"
    private const val KEY_TIPO = "user_tipo"
    private const val KEY_SECTORS = "user_sectors" // conjunto {"1"}, {"2"} ou {"1","2"}

    /** Salva o nome do técnico */
    fun setName(ctx: Context, v: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit { putString(KEY_NAME, v.trim()) }
    }

    /** Retorna o nome salvo */
    fun getName(ctx: Context): String? =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_NAME, null)

    /** Salva o tipo de atendimento (Eletrico / Mecanico) */
    fun setTipo(ctx: Context, v: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit { putString(KEY_TIPO, v.trim()) }
    }

    /** Retorna o tipo salvo */
    fun getTipo(ctx: Context): String? =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_TIPO, null)

    /** Salva os setores selecionados (1, 2 ou ambos) */
    fun setSectors(ctx: Context, sectors: Set<Int>) {
        val asStr = sectors.map { it.toString() }.toSet()
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit { putStringSet(KEY_SECTORS, asStr) }
    }

    /** Retorna o conjunto de setores marcados (ex: {1}, {2} ou {1,2}) */
    fun getSectors(ctx: Context): Set<Int> {
        val def = emptySet<String>()
        val prefs = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_SECTORS, def) ?: def
        return set.mapNotNull { it.toIntOrNull() }.toSet()
    }

    /**
     * Compatibilidade antiga:
     * Retorna 1, 2, 3 (ambos) ou 0 (nenhum)
     */
    fun getSector(ctx: Context): Int {
        val s = getSectors(ctx)
        return when {
            s.contains(1) && s.contains(2) -> 3
            s.contains(1) -> 1
            s.contains(2) -> 2
            else -> 0
        }
    }
}
