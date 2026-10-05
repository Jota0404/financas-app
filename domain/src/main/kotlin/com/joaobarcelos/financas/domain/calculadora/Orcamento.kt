package com.joaobarcelos.financas.domain.calculadora

import com.joaobarcelos.financas.domain.model.Ciclo
import com.joaobarcelos.financas.domain.model.ContaFixa
import com.joaobarcelos.financas.domain.model.Entrada
import com.joaobarcelos.financas.domain.model.Gasto
import com.joaobarcelos.financas.domain.model.MetaReserva
import com.joaobarcelos.financas.domain.model.TipoEntrada
import com.joaobarcelos.financas.domain.model.TipoMeta
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Tudo o que foi cadastrado. Cada cálculo filtra o que vale no ciclo pedido. */
data class Orcamento(
    val entradas: List<Entrada> = emptyList(),
    val contas: List<ContaFixa> = emptyList(),
    val metas: List<MetaReserva> = emptyList(),
    val gastos: List<Gasto> = emptyList(),
) {
    /** Totais de [ciclo]. Só entram os gastos com data antes de [gastosAntesDe] (padrão: o ciclo todo). */
    fun resumo(ciclo: Ciclo, gastosAntesDe: LocalDate = ciclo.fim.plusDays(1)): Resumo {
        val totalEntradas = entradas.filter { it.entraEm(ciclo) }.sumOf { it.valorCentavos }
        return Resumo(
            entradas = totalEntradas,
            fixas = contas.filter { it.ativaEm(ciclo) }.sumOf { it.valorCentavos },
            reserva = metas.filter { it.ativa }.sumOf { it.valorNoCiclo(totalEntradas) },
            gastos = gastos.filter { it.data in ciclo && it.data < gastosAntesDe }.sumOf { it.valorCentavos },
        )
    }
}

/** Totais de um ciclo, em centavos: os mesmos que o CicloFechado guarda. */
data class Resumo(val entradas: Long, val fixas: Long, val reserva: Long, val gastos: Long) {
    /** RN07: a reserva sai antes dos gastos e nunca aparece como disponível. */
    val disponivel: Long get() = entradas - fixas - reserva - gastos

    /** RN09: quanto o disponível ficou negativo, mesmo que passe do total da reserva. */
    val reservaInvadida: Long get() = maxOf(0L, -disponivel)

    /** RN08: quanto falta para as metas caberem em Entradas − Contas fixas (0 quando cabem). */
    val faltaParaMetas: Long get() = maxOf(0L, reserva - (entradas - fixas))
}

/** Recorrente: todo ciclo que encosta em [dataInicio, dataFim]. Avulsa: só o ciclo da data. */
fun Entrada.entraEm(ciclo: Ciclo): Boolean = when (tipo) {
    TipoEntrada.AVULSA -> dataInicio in ciclo
    TipoEntrada.RECORRENTE -> dataInicio <= ciclo.fim && (dataFim == null || dataFim >= ciclo.inicio)
}

/**
 * RN03: vale nos [ContaFixa.duracaoMeses] ciclos a partir de [ContaFixa.cicloInicio], contados pelo
 * mês em que cada ciclo começa. RN04: sem duração, vale até ser encerrada; encerrada no meio de um
 * ciclo, ainda vale nele e sai a partir do seguinte.
 */
fun ContaFixa.ativaEm(ciclo: Ciclo): Boolean {
    val meses = ChronoUnit.MONTHS.between(YearMonth.from(cicloInicio), YearMonth.from(ciclo.inicio))
    return meses >= 0 &&
        (duracaoMeses == null || meses < duracaoMeses) &&
        (encerradaEm == null || encerradaEm >= ciclo.inicio)
}

/** RN06: valor que a meta reserva no ciclo. O percentual arredonda para cima (exceção da RN13). */
fun MetaReserva.valorNoCiclo(entradasCentavos: Long): Long = when (tipo) {
    TipoMeta.VALOR -> valor
    TipoMeta.PERCENTUAL -> -Math.floorDiv(-entradasCentavos * valor, 10_000L)
}
