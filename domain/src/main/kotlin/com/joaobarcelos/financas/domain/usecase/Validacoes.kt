package com.joaobarcelos.financas.domain.usecase

import com.joaobarcelos.financas.domain.model.Ciclo
import com.joaobarcelos.financas.domain.model.Gasto
import java.time.LocalDate

/** RN15: valor zero ou negativo é bloqueado em qualquer cadastro. */
fun valorValido(valor: Long): Boolean = valor > 0

enum class ErroGasto { VALOR_ZERO_OU_NEGATIVO, DATA_EM_CICLO_FECHADO, DATA_FUTURA }

/** RN14 e RN15: devolve o motivo do bloqueio, ou null quando o gasto pode ser salvo. */
fun validarGasto(gasto: Gasto, cicloAtual: Ciclo, hoje: LocalDate): ErroGasto? = when {
    !valorValido(gasto.valorCentavos) -> ErroGasto.VALOR_ZERO_OU_NEGATIVO
    gasto.data < cicloAtual.inicio -> ErroGasto.DATA_EM_CICLO_FECHADO
    gasto.data > hoje -> ErroGasto.DATA_FUTURA
    else -> null
}
