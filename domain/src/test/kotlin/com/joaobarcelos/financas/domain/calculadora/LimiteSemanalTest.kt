package com.joaobarcelos.financas.domain.calculadora

import com.joaobarcelos.financas.domain.CenarioBase
import com.joaobarcelos.financas.domain.data
import com.joaobarcelos.financas.domain.model.Ciclo
import com.joaobarcelos.financas.domain.model.Entrada
import com.joaobarcelos.financas.domain.model.Gasto
import com.joaobarcelos.financas.domain.model.TipoEntrada
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LimiteSemanalTest {
    private val base = CenarioBase.orcamento
    private val outubro = Ciclo(data(1, 10), data(31, 10))

    private fun cicloDoDia(dia: Int, mes: Int, ano: Int = 2026) = cicloDe(data(dia, mes, ano), CenarioBase.DIA_PAGAMENTO)

    @Test
    fun `RN10 semana vai de segunda a domingo`() {
        val quarta = base.limiteSemanal(outubro, data(7, 10))
        assertEquals(data(5, 10), quarta.inicio)
        assertEquals(data(11, 10), quarta.fim)
        assertEquals(quarta, base.limiteSemanal(outubro, data(11, 10)))
        assertEquals(data(12, 10), base.limiteSemanal(outubro, data(12, 10)).inicio)
    }

    @Test
    fun `RN10 semana que cruza o inicio do ciclo comeca no primeiro dia do ciclo`() {
        val sabado = base.limiteSemanal(outubro, data(3, 10))
        assertEquals(data(1, 10), sabado.inicio)
        assertEquals(data(4, 10), sabado.fim)
    }

    @Test
    fun `RN11 ultima semana do ciclo libera todo o disponivel`() {
        // 26 a 31/10: 6 dias na semana e 6 dias restantes no ciclo
        val limite = base.limiteSemanal(outubro, data(28, 10))
        assertEquals(data(26, 10), limite.inicio)
        assertEquals(data(31, 10), limite.fim)
        assertEquals(150_000, limite.valorCentavos)
    }

    @Test
    fun `RN11 semana que cruza a virada do ciclo tem um limite em cada ciclo`() {
        // domingo 01/11 é o primeiro dia do ciclo de novembro: 1500 x 1 / 30
        val novembro = cicloDoDia(1, 11)
        val limite = base.limiteSemanal(novembro, data(1, 11))
        assertEquals(data(1, 11), limite.inicio)
        assertEquals(data(1, 11), limite.fim)
        assertEquals(5_000, limite.valorCentavos)
    }

    @Test
    fun `RN11 semana que cruza a virada do ano`() {
        // segunda 28/12/2026 a domingo 03/01/2027
        val dezembro = base.limiteSemanal(cicloDoDia(28, 12), data(28, 12))
        assertEquals(data(31, 12), dezembro.fim)
        assertEquals(150_000, dezembro.valorCentavos)
        // janeiro, sem o celular: 1700 x 3 / 31 = 164,516...
        val janeiro = base.limiteSemanal(cicloDoDia(1, 1, 2027), data(3, 1, 2027))
        assertEquals(data(1, 1, 2027), janeiro.inicio)
        assertEquals(16_451, janeiro.valorCentavos)
    }

    @Test
    fun `RN11 gastos da propria semana nao mudam o limite`() {
        val comGastos = base.copy(gastos = listOf(Gasto(10_000, data(5, 10)), Gasto(20_000, data(6, 10))))
        assertEquals(38_888, comGastos.limiteSemanal(outubro, data(7, 10)).valorCentavos)
    }

    @Test
    fun `RN11 disponivel zerado da limite zero`() {
        val semSobra = base.copy(gastos = listOf(Gasto(150_000, data(2, 10))))
        assertEquals(0, semSobra.limiteSemanal(outubro, data(5, 10)).valorCentavos)
    }

    @Test
    fun `RN11 limite deriva dos cadastros e sobe com uma entrada nova na semana`() {
        // freela de 270 em 07/10: entradas 3270, reserva 327, disponível 1743; 1743 x 7 / 27 = 451,88...
        val freela = Entrada(27_000, TipoEntrada.AVULSA, dataInicio = data(7, 10))
        assertEquals(45_188, base.copy(entradas = base.entradas + freela).limiteSemanal(outubro, data(7, 10)).valorCentavos)
    }

    @Test
    fun `RN11 data fora do ciclo e recusada`() {
        assertThrows(IllegalArgumentException::class.java) { base.limiteSemanal(outubro, data(1, 11)) }
    }

    @Test
    fun `RN12 limite manual vale inteiro na semana cheia e avisa quando passa do automatico`() {
        val limite = base.limiteSemanal(outubro, data(5, 10), limiteManual = 50_000)
        assertEquals(50_000, limite.valorCentavos)
        assertTrue(limite.ritmoNaoFechaCiclo)
    }

    @Test
    fun `RN12 limite manual menor ou igual ao automatico nao avisa`() {
        val menor = base.limiteSemanal(outubro, data(5, 10), limiteManual = 30_000)
        assertEquals(30_000, menor.valorCentavos)
        assertFalse(menor.ritmoNaoFechaCiclo)
        assertFalse(base.limiteSemanal(outubro, data(5, 10), limiteManual = 38_888).ritmoNaoFechaCiclo)
    }

    @Test
    fun `RN12 limite manual e proporcional na semana partida do fim do ciclo`() {
        // 26 a 31/10: 500 x 6 / 7 = 428,57...
        val limite = base.limiteSemanal(outubro, data(28, 10), limiteManual = 50_000)
        assertEquals(42_857, limite.valorCentavos)
        assertFalse(limite.ritmoNaoFechaCiclo)
    }

    @Test
    fun `RN12 limite manual numa semana de um dia so`() {
        // domingo 01/11: 500 x 1 / 7 = 71,42...; o automático é 50,00
        val limite = base.limiteSemanal(cicloDoDia(1, 11), data(1, 11), limiteManual = 50_000)
        assertEquals(7_142, limite.valorCentavos)
        assertTrue(limite.ritmoNaoFechaCiclo)
    }

    @Test
    fun `RN12 limite manual vale mesmo com a reserva invadida e avisa`() {
        val invadido = base.copy(gastos = listOf(Gasto(160_000, data(2, 10))))
        val limite = invadido.limiteSemanal(outubro, data(5, 10), limiteManual = 50_000)
        assertEquals(50_000, limite.valorCentavos)
        assertTrue(limite.ritmoNaoFechaCiclo)
    }

    @Test
    fun `RN13 divisao do limite arredonda para baixo`() {
        // disponível de 1,00 numa segunda com 27 dias restantes: 1,00 x 7 / 27 = 0,259...
        val umReal = Orcamento(entradas = listOf(Entrada(100, TipoEntrada.AVULSA, dataInicio = data(2, 10))))
        assertEquals(25, umReal.limiteSemanal(outubro, data(5, 10)).valorCentavos)
        // limite manual de 0,01 em 4 dias: 0,01 x 4 / 7 = 0,005...
        assertEquals(0, base.limiteSemanal(outubro, data(1, 10), limiteManual = 1).valorCentavos)
    }

    @Test
    fun `RN14 gasto retroativo de semana anterior recalcula o limite da semana atual`() {
        assertEquals(38_888, base.limiteSemanal(outubro, data(7, 10)).valorCentavos)
        // gasto de 150 lançado na quarta 07/10 com data de 02/10: 1350 x 7 / 27 = 350,00
        val retroativo = base.copy(gastos = listOf(Gasto(15_000, data(2, 10))))
        assertEquals(35_000, retroativo.limiteSemanal(outubro, data(7, 10)).valorCentavos)
    }
}
