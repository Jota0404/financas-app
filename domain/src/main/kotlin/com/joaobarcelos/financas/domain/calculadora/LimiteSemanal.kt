package com.joaobarcelos.financas.domain.calculadora

import com.joaobarcelos.financas.domain.model.Ciclo
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Limite de gastos de uma semana (RN10) recortada pelo ciclo, de [inicio] a [fim].
 * [ritmoNaoFechaCiclo]: o limite manual passou do automático, e o app avisa (RN12).
 */
data class LimiteSemanal(
    val inicio: LocalDate,
    val fim: LocalDate,
    val valorCentavos: Long,
    val ritmoNaoFechaCiclo: Boolean = false,
)

/**
 * Limite da semana que contém [data] (RN10 a RN13). [limiteManual] vale para uma semana cheia.
 * Usa o disponível do primeiro dia da semana dentro do ciclo: os gastos da própria semana não mudam
 * o limite, mas um gasto retroativo de uma semana anterior muda (RN14).
 */
fun Orcamento.limiteSemanal(ciclo: Ciclo, data: LocalDate, limiteManual: Long? = null): LimiteSemanal {
    require(data in ciclo) { "$data fora do ciclo $ciclo" }
    val segunda = data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val domingo = segunda.plusDays(6)
    val inicio = if (segunda < ciclo.inicio) ciclo.inicio else segunda
    val fim = if (domingo > ciclo.fim) ciclo.fim else domingo
    val dias = ChronoUnit.DAYS.between(inicio, fim) + 1
    val diasRestantes = ChronoUnit.DAYS.between(inicio, ciclo.fim) + 1

    val disponivel = resumo(ciclo, gastosAntesDe = inicio).disponivel
    // RN11: multiplica antes de dividir, e a divisão arredonda para baixo (RN13)
    val automatico = if (disponivel <= 0) 0L else Math.floorDiv(disponivel * dias, diasRestantes)
    // RN12: proporcional aos dias da semana dentro do ciclo
    val manual = limiteManual?.let { Math.floorDiv(it * dias, 7L) }
    return LimiteSemanal(inicio, fim, manual ?: automatico, manual != null && manual > automatico)
}
