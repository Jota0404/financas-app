package com.joaobarcelos.financas.domain.calculadora

import com.joaobarcelos.financas.domain.CenarioBase
import com.joaobarcelos.financas.domain.data
import com.joaobarcelos.financas.domain.model.Ciclo
import com.joaobarcelos.financas.domain.model.Configuracoes
import com.joaobarcelos.financas.domain.model.Gasto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PainelTest {
    private val outubro = Ciclo(data(1, 10), data(31, 10))
    private val padrao = Configuracoes()
    private fun comGastos(vararg gastos: Gasto) = CenarioBase.orcamento.copy(gastos = gastos.toList())

    @Test
    fun `disponivel da semana e o limite menos os gastos da propria semana`() {
        // semana de 05/10: limite 388,88; gasto de 02/10 é de outra semana e já entrou no limite
        val painel = comGastos(Gasto(10_000, data(2, 10)), Gasto(5_000, data(6, 10))).painel(outubro, data(7, 10), padrao)
        assertEquals(data(5, 10), painel.semana.inicio)
        assertEquals(5_000, painel.gastosDaSemana)
        assertEquals(painel.semana.valorCentavos - 5_000, painel.disponivelDaSemana)
    }

    @Test
    fun `painel mostra o disponivel do ciclo e a reserva`() {
        val painel = comGastos(Gasto(50_000, data(6, 10))).painel(outubro, data(7, 10), padrao)
        assertEquals(100_000, painel.resumoDoCiclo.disponivel)
        assertEquals(30_000, painel.resumoDoCiclo.reserva)
        assertEquals(0, painel.resumoDoCiclo.reservaInvadida)
    }

    @Test
    fun `barra verde abaixo de 70 por cento e amarela ao atingir 70`() {
        // 70% de 388,88 = 272,216: 272,21 ainda é verde, 272,22 já atinge (os mesmos pontos do A2, CA08)
        assertEquals(Faixa.VERDE, comGastos(Gasto(27_221, data(5, 10))).painel(outubro, data(5, 10), padrao).faixa)
        assertEquals(Faixa.AMARELA, comGastos(Gasto(27_222, data(5, 10))).painel(outubro, data(5, 10), padrao).faixa)
        assertEquals(Faixa.VERDE, CenarioBase.orcamento.painel(outubro, data(5, 10), padrao).faixa)
    }

    @Test
    fun `barra vermelha ao atingir 90 por cento e cheia ao passar do limite`() {
        // 90% de 388,88 = 349,992
        assertEquals(Faixa.AMARELA, comGastos(Gasto(34_999, data(5, 10))).painel(outubro, data(5, 10), padrao).faixa)
        assertEquals(Faixa.VERMELHA, comGastos(Gasto(35_000, data(5, 10))).painel(outubro, data(5, 10), padrao).faixa)
        val passou = comGastos(Gasto(50_000, data(5, 10))).painel(outubro, data(5, 10), padrao)
        assertEquals(10_000, passou.consumoPontosBase)
        assertEquals(38_888 - 50_000L, passou.disponivelDaSemana)
    }

    @Test
    fun `cores da barra seguem os percentuais configurados dos alertas`() {
        // decisão do dono: com atenção em 60%, 60% de 388,88 = 233,328 já é amarelo
        val config = Configuracoes(percentualAtencao = 6000, percentualCritico = 8000)
        assertEquals(Faixa.AMARELA, comGastos(Gasto(23_333, data(5, 10))).painel(outubro, data(5, 10), config).faixa)
        assertEquals(Faixa.VERMELHA, comGastos(Gasto(31_111, data(5, 10))).painel(outubro, data(5, 10), config).faixa)
    }

    @Test
    fun `CA14 com limite zero a barra fica cheia e vermelha`() {
        // decisão do dono: semana de 05/10 com a reserva invadida (gasto de 1600 em 02/10)
        val painel = comGastos(Gasto(160_000, data(2, 10))).painel(outubro, data(5, 10), padrao)
        assertEquals(0, painel.semana.valorCentavos)
        assertEquals(Faixa.VERMELHA, painel.faixa)
        assertEquals(10_000, painel.consumoPontosBase)
        assertEquals(10_000, painel.resumoDoCiclo.reservaInvadida)
    }

    @Test
    fun `consumo em pontos base para desenhar a barra`() {
        // 194,44 de 388,88 = 50%
        assertEquals(5_000, comGastos(Gasto(19_444, data(5, 10))).painel(outubro, data(5, 10), padrao).consumoPontosBase)
    }

    @Test
    fun `RN12 limite manual aparece no painel com o aviso de ritmo`() {
        val painel = CenarioBase.orcamento.painel(outubro, data(1, 10), Configuracoes(limiteSemanalManual = 50_000))
        assertEquals(28_571, painel.semana.valorCentavos)
        assertTrue(painel.semana.ritmoNaoFechaCiclo)
    }
}
