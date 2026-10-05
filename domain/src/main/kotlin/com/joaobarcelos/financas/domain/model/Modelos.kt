package com.joaobarcelos.financas.domain.model

import java.time.LocalDate

/** Período entre dois pagamentos, de [inicio] a [fim], os dois inclusive (RN01). */
data class Ciclo(val inicio: LocalDate, val fim: LocalDate) {
    operator fun contains(data: LocalDate) = data >= inicio && data <= fim
}

enum class TipoEntrada { RECORRENTE, AVULSA }

/**
 * Recorrente entra em todo ciclo de [dataInicio] até [dataFim] (nulo = sem fim), inclusive nos
 * ciclos em que essas datas caem. Avulsa entra só no ciclo de [dataInicio].
 */
data class Entrada(
    val valorCentavos: Long,
    val tipo: TipoEntrada,
    val dataInicio: LocalDate,
    val dataFim: LocalDate? = null,
)

/**
 * [cicloInicio] é o primeiro dia do primeiro ciclo da conta. [duracaoMeses] nulo é "sem fim" (RN04).
 * [encerradaEm] é o dia em que o usuário encerrou a conta.
 */
data class ContaFixa(
    val valorCentavos: Long,
    val cicloInicio: LocalDate,
    val duracaoMeses: Int? = null,
    val encerradaEm: LocalDate? = null,
)

enum class TipoMeta { VALOR, PERCENTUAL }

/** [valor] em centavos (VALOR) ou em pontos-base, 1000 = 10% (PERCENTUAL) (RN06). */
data class MetaReserva(val tipo: TipoMeta, val valor: Long, val ativa: Boolean = true)

data class Gasto(val valorCentavos: Long, val data: LocalDate)
