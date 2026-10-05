package com.joaobarcelos.financas.domain

import com.joaobarcelos.financas.domain.calculadora.Orcamento
import com.joaobarcelos.financas.domain.model.ContaFixa
import com.joaobarcelos.financas.domain.model.Entrada
import com.joaobarcelos.financas.domain.model.MetaReserva
import com.joaobarcelos.financas.domain.model.TipoEntrada
import com.joaobarcelos.financas.domain.model.TipoMeta
import java.time.LocalDate

/** Data na ordem do briefing: dia, mês e ano (padrão: 2026). */
fun data(dia: Int, mes: Int, ano: Int = 2026): LocalDate = LocalDate.of(ano, mes, dia)

/** Cenário base dos critérios de aceite do briefing. Valores em centavos. */
object CenarioBase {
    const val DIA_PAGAMENTO = 1

    /** Salário recorrente de R$ 3.000,00. */
    val salario = Entrada(300_000, TipoEntrada.RECORRENTE, dataInicio = data(1, 10))

    /** Aluguel de R$ 1.000,00, sem fim. */
    val aluguel = ContaFixa(100_000, cicloInicio = data(1, 10))

    /** Celular de R$ 200,00 por 3 meses a partir do ciclo de outubro/2026. */
    val celular = ContaFixa(20_000, cicloInicio = data(1, 10), duracaoMeses = 3)

    /** Reserva de 10% das entradas. */
    val reserva = MetaReserva(TipoMeta.PERCENTUAL, 1000)

    val orcamento = Orcamento(
        entradas = listOf(salario),
        contas = listOf(aluguel, celular),
        metas = listOf(reserva),
    )
}
