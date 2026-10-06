package com.joaobarcelos.financas.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.joaobarcelos.financas.domain.model.Ciclo
import com.joaobarcelos.financas.domain.model.Configuracoes
import com.joaobarcelos.financas.domain.repository.ConfiguracoesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** Configurações no DataStore. O que não foi gravado vale o padrão de [Configuracoes]. */
class DataStoreConfiguracoesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ConfiguracoesRepository {
    override fun configuracoes(): Flow<Configuracoes> = dataStore.data
        // Arquivo ilegível: usa os padrões em vez de fechar o app
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.paraConfiguracoes() }

    override suspend fun atualizar(mudanca: (Configuracoes) -> Configuracoes) {
        dataStore.edit { it.gravar(mudanca(it.paraConfiguracoes())) }
    }

    companion object {
        /**
         * Cria o DataStore das configurações. Arquivo corrompido vira configurações padrão, em vez
         * de fechar o app ao ler ou ao salvar.
         */
        fun criarDataStore(
            escopo: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            arquivo: () -> File,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = escopo,
            produceFile = arquivo,
        )
    }

    private object Chaves {
        val DIA_PAGAMENTO = intPreferencesKey("dia_pagamento")
        val INICIO_CICLO_ATUAL = stringPreferencesKey("inicio_ciclo_atual")
        val FIM_CICLO_ATUAL = stringPreferencesKey("fim_ciclo_atual")
        val LIMITE_SEMANAL_MANUAL = longPreferencesKey("limite_semanal_manual")
        val PERCENTUAL_ATENCAO = intPreferencesKey("percentual_atencao")
        val PERCENTUAL_CRITICO = intPreferencesKey("percentual_critico")
        val HORA_RESUMO = stringPreferencesKey("hora_resumo")
    }

    private fun Preferences.paraConfiguracoes(): Configuracoes {
        val padrao = Configuracoes()
        val inicio = this[Chaves.INICIO_CICLO_ATUAL]
        val fim = this[Chaves.FIM_CICLO_ATUAL]
        return Configuracoes(
            diaPagamento = this[Chaves.DIA_PAGAMENTO] ?: padrao.diaPagamento,
            cicloIrregular = if (inicio != null && fim != null) Ciclo(LocalDate.parse(inicio), LocalDate.parse(fim)) else null,
            limiteSemanalManual = this[Chaves.LIMITE_SEMANAL_MANUAL],
            percentualAtencao = this[Chaves.PERCENTUAL_ATENCAO] ?: padrao.percentualAtencao,
            percentualCritico = this[Chaves.PERCENTUAL_CRITICO] ?: padrao.percentualCritico,
            horaResumo = this[Chaves.HORA_RESUMO]?.let(LocalTime::parse) ?: padrao.horaResumo,
        )
    }

    private fun MutablePreferences.gravar(c: Configuracoes) {
        this[Chaves.DIA_PAGAMENTO] = c.diaPagamento
        gravarOuApagar(Chaves.INICIO_CICLO_ATUAL, c.cicloIrregular?.inicio?.toString())
        gravarOuApagar(Chaves.FIM_CICLO_ATUAL, c.cicloIrregular?.fim?.toString())
        gravarOuApagar(Chaves.LIMITE_SEMANAL_MANUAL, c.limiteSemanalManual)
        this[Chaves.PERCENTUAL_ATENCAO] = c.percentualAtencao
        this[Chaves.PERCENTUAL_CRITICO] = c.percentualCritico
        this[Chaves.HORA_RESUMO] = c.horaResumo.toString()
    }

    private fun <T> MutablePreferences.gravarOuApagar(chave: Preferences.Key<T>, valor: T?) {
        if (valor == null) remove(chave) else this[chave] = valor
    }
}
