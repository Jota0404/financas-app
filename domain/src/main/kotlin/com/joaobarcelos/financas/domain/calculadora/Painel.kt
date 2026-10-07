package com.joaobarcelos.financas.domain.calculadora

import com.joaobarcelos.financas.domain.model.Ciclo
import com.joaobarcelos.financas.domain.model.Configuracoes
import java.time.LocalDate

/** Cor da barra de consumo da semana. */
enum class Faixa { VERDE, AMARELA, VERMELHA }

/** Tudo o que a tela Início mostra, calculado num lugar só. */
data class Painel(
    val semana: LimiteSemanal,
    val gastosDaSemana: Long,
    val resumoDoCiclo: Resumo,
    val faixa: Faixa,
) {
    /** Quanto ainda dá para gastar nesta semana; fica negativo quando passa do limite. */
    val disponivelDaSemana: Long get() = semana.valorCentavos - gastosDaSemana

    /** Parte do limite já gasta, em pontos-base (10000 = barra cheia). Limite zero enche a barra. */
    val consumoPontosBase: Long
        get() = if (semana.valorCentavos <= 0) 10_000 else minOf(10_000, Math.floorDiv(gastosDaSemana * 10_000, semana.valorCentavos))
}

/**
 * Painel da semana de [hoje]. A barra fica amarela quando os gastos atingem o percentual de atenção
 * e vermelha quando atingem o crítico, os mesmos pontos dos alertas A2 e A3 (decisão do dono). Com
 * limite zero, fica vermelha.
 */
fun Orcamento.painel(ciclo: Ciclo, hoje: LocalDate, configuracoes: Configuracoes): Painel {
    val semana = limiteSemanal(ciclo, hoje, configuracoes.limiteSemanalManual)
    val gastosDaSemana = gastos.filter { it.data >= semana.inicio && it.data <= semana.fim }.sumOf { it.valorCentavos }
    val limite = semana.valorCentavos
    val faixa = when {
        limite <= 0 -> Faixa.VERMELHA
        // compara gastos / limite com o percentual sem dividir: gastos x 10000 contra limite x percentual
        gastosDaSemana * 10_000 < limite * configuracoes.percentualAtencao -> Faixa.VERDE
        gastosDaSemana * 10_000 < limite * configuracoes.percentualCritico -> Faixa.AMARELA
        else -> Faixa.VERMELHA
    }
    return Painel(semana, gastosDaSemana, resumo(ciclo), faixa)
}
