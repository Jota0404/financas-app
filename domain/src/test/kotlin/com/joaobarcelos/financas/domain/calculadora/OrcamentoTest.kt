package com.joaobarcelos.financas.domain.calculadora

import com.joaobarcelos.financas.domain.CenarioBase
import com.joaobarcelos.financas.domain.CenarioBase.aluguel
import com.joaobarcelos.financas.domain.CenarioBase.celular
import com.joaobarcelos.financas.domain.data
import com.joaobarcelos.financas.domain.model.Ciclo
import com.joaobarcelos.financas.domain.model.ContaFixa
import com.joaobarcelos.financas.domain.model.Entrada
import com.joaobarcelos.financas.domain.model.Gasto
import com.joaobarcelos.financas.domain.model.MetaReserva
import com.joaobarcelos.financas.domain.model.TipoEntrada
import com.joaobarcelos.financas.domain.model.TipoMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrcamentoTest {
    private val base = CenarioBase.orcamento
    private val outubro = Ciclo(data(1, 10), data(31, 10))

    private fun ciclo(mes: Int, ano: Int = 2026) = cicloDe(data(1, mes, ano), CenarioBase.DIA_PAGAMENTO)

    private fun comGastos(vararg gastos: Gasto) = base.copy(gastos = gastos.toList())

    @Test
    fun `RN03 conta de 3 meses vale nos 3 ciclos a partir do inicio`() {
        assertFalse(celular.ativaEm(ciclo(9)))
        assertTrue(celular.ativaEm(ciclo(10)))
        assertTrue(celular.ativaEm(ciclo(11)))
        assertTrue(celular.ativaEm(ciclo(12)))
        assertFalse(celular.ativaEm(ciclo(1, 2027)))
    }

    @Test
    fun `RN03 conta de 1 mes so vale no ciclo de inicio`() {
        val conta = ContaFixa(5_000, cicloInicio = data(1, 10), duracaoMeses = 1)
        assertTrue(conta.ativaEm(ciclo(10)))
        assertFalse(conta.ativaEm(ciclo(11)))
    }

    @Test
    fun `RN03 duracao com dia do pagamento 31 conta os ciclos de fevereiro e abril`() {
        // ciclos de 31/01, 28/02 e 31/03/2027
        val conta = ContaFixa(10_000, cicloInicio = data(31, 1, 2027), duracaoMeses = 2)
        assertTrue(conta.ativaEm(cicloDe(data(31, 1, 2027), 31)))
        assertTrue(conta.ativaEm(cicloDe(data(28, 2, 2027), 31)))
        assertFalse(conta.ativaEm(cicloDe(data(31, 3, 2027), 31)))
    }

    @Test
    fun `RN03 mudanca do dia que cria dois ciclos no mes desconta a conta nos dois`() {
        // decisão do dono: 1 -> 15 em 10/10 cria 01-14/10 e 15/10-14/11, os dois "de outubro"
        val curto = mudarDiaPagamento(outubro, 15, hoje = data(10, 10)).atual
        val ciclos = generateSequence(curto) { cicloDe(it.fim.plusDays(1), 15) }.take(5).toList()
        assertEquals(data(15, 1, 2027), ciclos.last().inicio)
        assertEquals(listOf(true, true, true, true, false), ciclos.map { celular.ativaEm(it) })
    }

    @Test
    fun `RN04 conta sem fim continua ativa anos depois`() {
        assertTrue(aluguel.ativaEm(ciclo(10, 2036)))
    }

    @Test
    fun `RN04 conta encerrada no meio do ciclo ainda desconta nele e sai no seguinte`() {
        // decisão do dono: encerrado em 10/10, o aluguel ainda conta em outubro
        val encerrado = aluguel.copy(encerradaEm = data(10, 10))
        assertTrue(encerrado.ativaEm(ciclo(10)))
        assertFalse(encerrado.ativaEm(ciclo(11)))
        assertEquals(150_000, base.copy(contas = listOf(encerrado, celular)).resumo(outubro).disponivel)
    }

    @Test
    fun `RN04 conta encerrada no ultimo ou no primeiro dia do ciclo`() {
        assertTrue(aluguel.copy(encerradaEm = data(31, 10)).ativaEm(ciclo(10)))
        assertFalse(aluguel.copy(encerradaEm = data(31, 10)).ativaEm(ciclo(11)))
        assertTrue(aluguel.copy(encerradaEm = data(1, 11)).ativaEm(ciclo(11)))
    }

    @Test
    fun `RN04 conta com duracao encerrada antes do fim sai no ciclo seguinte`() {
        val encerrado = celular.copy(encerradaEm = data(15, 11))
        assertTrue(encerrado.ativaEm(ciclo(11)))
        assertFalse(encerrado.ativaEm(ciclo(12)))
    }

    @Test
    fun `RN05 editar o valor da conta muda o ciclo atual e os futuros`() {
        val editado = base.copy(contas = listOf(aluguel.copy(valorCentavos = 110_000), celular))
        assertEquals(150_000, base.resumo(outubro).disponivel)
        assertEquals(140_000, editado.resumo(outubro).disponivel)
        assertEquals(140_000, editado.resumo(ciclo(11)).disponivel)
        assertEquals(160_000, editado.resumo(ciclo(1, 2027)).disponivel)
    }

    @Test
    fun `RN06 metas de valor fixo e percentual se somam`() {
        val duasMetas = base.copy(metas = listOf(CenarioBase.reserva, MetaReserva(TipoMeta.VALOR, 50_000)))
        assertEquals(80_000, duasMetas.resumo(outubro).reserva)
        assertEquals(100_000, duasMetas.resumo(outubro).disponivel)
    }

    @Test
    fun `RN06 meta inativa nao reserva nada`() {
        val inativa = base.copy(metas = listOf(CenarioBase.reserva.copy(ativa = false)))
        assertEquals(0, inativa.resumo(outubro).reserva)
        assertEquals(180_000, inativa.resumo(outubro).disponivel)
    }

    @Test
    fun `RN06 meta percentual incide sobre todas as entradas do ciclo`() {
        val freela = Entrada(100_000, TipoEntrada.AVULSA, dataInicio = data(20, 10))
        assertEquals(40_000, base.copy(entradas = base.entradas + freela).resumo(outubro).reserva)
    }

    @Test
    fun `RN07 reserva sai antes dos gastos e nao entra no disponivel`() {
        // entradas 3000 - fixas 1200 = 1800, mas só 1500 ficam disponíveis: os 300 da reserva ficam de fora
        val resumo = comGastos(Gasto(50_000, data(5, 10))).resumo(outubro)
        assertEquals(Resumo(entradas = 300_000, fixas = 120_000, reserva = 30_000, gastos = 50_000), resumo)
        assertEquals(100_000, resumo.disponivel)
    }

    @Test
    fun `RN07 so os gastos do ciclo entram no disponivel`() {
        val resumo = comGastos(Gasto(1_000, data(30, 9)), Gasto(2_000, data(31, 10)), Gasto(4_000, data(1, 11)))
            .resumo(outubro)
        assertEquals(2_000, resumo.gastos)
    }

    @Test
    fun `RN07 entrada avulsa so entra no ciclo da sua data`() {
        val freela = Entrada(50_000, TipoEntrada.AVULSA, dataInicio = data(31, 10))
        assertTrue(freela.entraEm(ciclo(10)))
        assertFalse(freela.entraEm(ciclo(11)))
        assertFalse(freela.entraEm(ciclo(9)))
    }

    @Test
    fun `RN07 entrada recorrente vale do ciclo de inicio ate o ciclo de fim`() {
        val salario = Entrada(300_000, TipoEntrada.RECORRENTE, dataInicio = data(20, 10), dataFim = data(5, 12))
        assertFalse(salario.entraEm(ciclo(9)))
        assertTrue(salario.entraEm(ciclo(10)))
        assertTrue(salario.entraEm(ciclo(12)))
        assertFalse(salario.entraEm(ciclo(1, 2027)))
    }

    @Test
    fun `RN08 metas iguais a entradas menos fixas cabem`() {
        // 1800 de folga = 300 (10%) + 1500
        val cabe = base.copy(metas = base.metas + MetaReserva(TipoMeta.VALOR, 150_000))
        assertEquals(0, cabe.resumo(outubro).faltaParaMetas)
        val umCentavoAMais = base.copy(metas = base.metas + MetaReserva(TipoMeta.VALOR, 150_001))
        assertEquals(1, umCentavoAMais.resumo(outubro).faltaParaMetas)
    }

    @Test
    fun `RN08 conta fixa nova que deixa as metas maiores mostra quanto falta`() {
        // entradas 3000 - fixas 2900 = 100, e a reserva de 10% é 300
        val contaNova = ContaFixa(170_000, cicloInicio = data(1, 10))
        assertEquals(20_000, base.copy(contas = base.contas + contaNova).resumo(outubro).faltaParaMetas)
    }

    @Test
    fun `RN08 meta inativa nao entra na soma`() {
        val inativa = MetaReserva(TipoMeta.VALOR, 500_000, ativa = false)
        assertEquals(0, base.copy(metas = base.metas + inativa).resumo(outubro).faltaParaMetas)
    }

    @Test
    fun `RN09 disponivel zerado nao invade a reserva e um centavo a mais invade`() {
        assertEquals(0, comGastos(Gasto(150_000, data(5, 10))).resumo(outubro).reservaInvadida)
        assertEquals(1, comGastos(Gasto(150_001, data(5, 10))).resumo(outubro).reservaInvadida)
    }

    @Test
    fun `RN09 valor invadido pode passar do total da reserva`() {
        val resumo = comGastos(Gasto(200_000, data(5, 10))).resumo(outubro)
        assertEquals(30_000, resumo.reserva)
        assertEquals(50_000, resumo.reservaInvadida)
    }

    @Test
    fun `RN09 varios gastos somam o valor invadido`() {
        val resumo = comGastos(Gasto(100_000, data(2, 10)), Gasto(70_000, data(20, 10))).resumo(outubro)
        assertEquals(-20_000, resumo.disponivel)
        assertEquals(20_000, resumo.reservaInvadida)
    }

    @Test
    fun `RN13 meta percentual exata nao arredonda`() {
        assertEquals(30_000, CenarioBase.reserva.valorNoCiclo(300_000))
    }

    @Test
    fun `RN13 meta percentual arredonda qualquer fracao para cima`() {
        assertEquals(1, CenarioBase.reserva.valorNoCiclo(1))
        assertEquals(30_001, CenarioBase.reserva.valorNoCiclo(300_001))
        assertEquals(0, CenarioBase.reserva.valorNoCiclo(0))
    }
}
