package com.joaobarcelos.financas.domain.calculadora

import com.joaobarcelos.financas.domain.CenarioBase
import com.joaobarcelos.financas.domain.data
import com.joaobarcelos.financas.domain.model.Ciclo
import com.joaobarcelos.financas.domain.model.Configuracoes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FechamentoTest {
    private val config = Configuracoes()
    private val outubro = Ciclo(data(1, 10), data(31, 10))
    private val novembro = Ciclo(data(1, 11), data(30, 11))

    @Test
    fun `sem gastos e sem ciclos fechados nao ha o que fechar`() {
        assertTrue(ciclosParaFechar(null, null, config, data(3, 12)).isEmpty())
    }

    @Test
    fun `o ciclo fecha no dia seguinte ao ultimo dia dele`() {
        assertTrue(ciclosParaFechar(null, data(5, 10), config, data(31, 10)).isEmpty())
        assertEquals(listOf(outubro), ciclosParaFechar(null, data(5, 10), config, data(1, 11)))
    }

    @Test
    fun `ciclos que terminaram com o app parado tambem fecham a partir do primeiro gasto`() {
        // QA Etapa 5, B2 e decisão do dono: o app volta a rodar em 03/12
        assertEquals(listOf(outubro, novembro), ciclosParaFechar(null, data(5, 10), config, data(3, 12)))
    }

    @Test
    fun `depois do ultimo fechado so fecha os seguintes que ja terminaram`() {
        assertTrue(ciclosParaFechar(outubro, data(5, 10), config, data(15, 11)).isEmpty())
        assertEquals(listOf(novembro), ciclosParaFechar(outubro, data(5, 10), config, data(1, 12)))
    }

    @Test
    fun `RN01 o ciclo irregular depois da mudanca do dia fecha inteiro`() {
        // P14: dia 1 -> 15 em 20/10 deixou o ciclo atual de 01/10 a 14/11
        val irregular = Configuracoes(diaPagamento = 15, cicloIrregular = Ciclo(data(1, 10), data(14, 11)))
        assertEquals(listOf(Ciclo(data(1, 10), data(14, 11))), ciclosParaFechar(null, data(5, 10), irregular, data(20, 11)))
        assertEquals(
            listOf(Ciclo(data(1, 10), data(14, 11)), Ciclo(data(15, 11), data(14, 12))),
            ciclosParaFechar(null, data(5, 10), irregular, data(15, 12)),
        )
    }

    @Test
    fun `RN05 o retrato guarda os totais do ciclo`() {
        val fechado = CenarioBase.orcamento.fechar(outubro)
        assertEquals(outubro, fechado.ciclo)
        assertEquals(120_000, fechado.resumo.fixas)
        assertEquals(150_000, fechado.resumo.disponivel)
        assertEquals(0, fechado.reservaInvadida)
    }
}
