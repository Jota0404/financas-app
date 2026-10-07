package com.joaobarcelos.financas.ui

import com.joaobarcelos.financas.domain.calculadora.Orcamento
import com.joaobarcelos.financas.domain.calculadora.cicloAtual
import com.joaobarcelos.financas.domain.model.Ciclo
import com.joaobarcelos.financas.domain.model.Configuracoes
import com.joaobarcelos.financas.domain.repository.ConfiguracoesRepository
import com.joaobarcelos.financas.domain.repository.OrcamentoRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A data de hoje, de novo a cada meia-noite. Ao voltar para o app, as telas voltam a observar e
 * recebem a data atual, mesmo se o celular dormiu na virada.
 */
fun diaAtual(relogio: Clock): Flow<LocalDate> = flow {
    while (true) {
        val agora = LocalDateTime.now(relogio)
        emit(agora.toLocalDate())
        delay(Duration.between(agora, agora.toLocalDate().plusDays(1).atStartOfDay()).toMillis() + 1)
    }
}.distinctUntilChanged()

/** O que as telas precisam saber agora: hoje, o ciclo atual, os cadastros e as configurações. */
data class Momento(val hoje: LocalDate, val ciclo: Ciclo, val dados: Orcamento, val configuracoes: Configuracoes)

/** Emite de novo quando um cadastro ou configuração muda, e na virada do dia. */
fun momento(orcamento: OrcamentoRepository, configuracoes: ConfiguracoesRepository, relogio: Clock): Flow<Momento> =
    combine(orcamento.orcamento(), configuracoes.configuracoes(), diaAtual(relogio)) { dados, config, hoje ->
        Momento(hoje, config.cicloAtual(hoje), dados, config)
    }
