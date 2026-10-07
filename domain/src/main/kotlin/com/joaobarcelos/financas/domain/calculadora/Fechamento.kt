package com.joaobarcelos.financas.domain.calculadora

import com.joaobarcelos.financas.domain.model.Ciclo
import com.joaobarcelos.financas.domain.model.CicloFechado
import com.joaobarcelos.financas.domain.model.Configuracoes
import java.time.LocalDate

/**
 * Ciclos que já terminaram antes de [hoje] e ainda não foram fechados: a partir do ciclo seguinte ao
 * [ultimoFechado] ou, sem nenhum fechado, a partir do ciclo do [primeiroGasto] (decisão do dono). Assim,
 * os ciclos que terminaram com o app parado também são fechados (QA Etapa 5, B2).
 */
fun ciclosParaFechar(ultimoFechado: Ciclo?, primeiroGasto: LocalDate?, configuracoes: Configuracoes, hoje: LocalDate): List<Ciclo> {
    var dia = ultimoFechado?.fim?.plusDays(1) ?: primeiroGasto ?: return emptyList()
    val ciclos = mutableListOf<Ciclo>()
    while (true) {
        val ciclo = configuracoes.cicloAtual(dia)
        if (ciclo.fim >= hoje) return ciclos
        ciclos += ciclo
        dia = ciclo.fim.plusDays(1)
    }
}

/** RN05: o retrato do ciclo, com os totais de agora. Editar um cadastro depois não muda o retrato. */
fun Orcamento.fechar(ciclo: Ciclo): CicloFechado = CicloFechado(ciclo, resumo(ciclo))
