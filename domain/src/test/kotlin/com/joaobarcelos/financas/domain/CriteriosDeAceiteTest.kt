package com.joaobarcelos.financas.domain

import com.joaobarcelos.financas.domain.CenarioBase.DIA_PAGAMENTO
import com.joaobarcelos.financas.domain.calculadora.ativaEm
import com.joaobarcelos.financas.domain.calculadora.cicloDe
import com.joaobarcelos.financas.domain.calculadora.limiteSemanal
import com.joaobarcelos.financas.domain.model.Gasto
import com.joaobarcelos.financas.domain.model.MetaReserva
import com.joaobarcelos.financas.domain.model.TipoMeta
import com.joaobarcelos.financas.domain.usecase.Decisao
import com.joaobarcelos.financas.domain.usecase.ErroCadastro
import com.joaobarcelos.financas.domain.usecase.ErroGasto
import com.joaobarcelos.financas.domain.usecase.decidirMeta
import com.joaobarcelos.financas.domain.usecase.validarGasto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Critérios de aceite de cálculo do briefing, com o cenário base. CA08 e CA11: Etapa 6; CA12: Etapa 5. */
class CriteriosDeAceiteTest {
    private val base = CenarioBase.orcamento
    private val outubro = cicloDe(data(1, 10), DIA_PAGAMENTO)

    @Test
    fun `CA01 outubro sem gastos tem 1500 reais disponiveis`() {
        assertEquals(150_000, base.resumo(outubro).disponivel)
    }

    @Test
    fun `CA02 em janeiro de 2027 o celular acabou e sobram 1700 reais`() {
        val janeiro = cicloDe(data(1, 1, 2027), DIA_PAGAMENTO)
        assertFalse(CenarioBase.celular.ativaEm(janeiro))
        assertEquals(170_000, base.resumo(janeiro).disponivel)
    }

    @Test
    fun `CA03 gasto de 1600 reais em outubro invade a reserva em 100 reais`() {
        val gasto = Gasto(160_000, data(10, 10))
        assertNull(validarGasto(gasto, outubro, hoje = data(10, 10)))
        val resumo = base.copy(gastos = listOf(gasto)).resumo(outubro)
        assertEquals(-10_000, resumo.disponivel)
        assertEquals(10_000, resumo.reservaInvadida)
    }

    @Test
    fun `CA04 meta de 2000 reais em outubro e bloqueada porque faltam 500 reais`() {
        val metaNova = MetaReserva(TipoMeta.VALOR, 200_000, nome = "Viagem")
        assertEquals(Decisao.Bloqueado(ErroCadastro.METAS_NAO_CABEM, 50_000), decidirMeta(metaNova, base, outubro))
    }

    @Test
    fun `CA05 segunda 05-10 sem gastos tem limite semanal de 388,88`() {
        val limite = base.limiteSemanal(outubro, data(5, 10))
        assertEquals(data(11, 10), limite.fim)
        assertEquals(38_888, limite.valorCentavos)
    }

    @Test
    fun `CA06 limite de 1000 reais numa segunda com 21 dias restantes e 333,33`() {
        // segunda 11/01/2027: faltam 21 dias no ciclo de janeiro; 1700 disponíveis - 700 de gastos = 1000
        val janeiro = cicloDe(data(11, 1, 2027), DIA_PAGAMENTO)
        val orcamento = base.copy(gastos = listOf(Gasto(70_000, data(4, 1, 2027))))
        assertEquals(100_000, orcamento.resumo(janeiro, gastosAntesDe = data(11, 1, 2027)).disponivel)
        assertEquals(33_333, orcamento.limiteSemanal(janeiro, data(11, 1, 2027)).valorCentavos)
    }

    @Test
    fun `CA07 dia do pagamento 31 faz o ciclo de abril comecar em 30-04`() {
        assertEquals(data(30, 4, 2027), cicloDe(data(30, 4, 2027), 31).inicio)
        assertEquals(data(30, 4, 2027), cicloDe(data(15, 5, 2027), 31).inicio)
    }

    @Test
    fun `CA09 gasto de 0 reais e bloqueado`() {
        assertEquals(ErroGasto.VALOR_ZERO_OU_NEGATIVO, validarGasto(Gasto(0, data(5, 10)), outubro, hoje = data(5, 10)))
    }

    @Test
    fun `CA10 gasto com data em ciclo ja fechado e bloqueado`() {
        assertEquals(
            ErroGasto.DATA_EM_CICLO_FECHADO,
            validarGasto(Gasto(5_000, data(30, 9)), outubro, hoje = data(5, 10)),
        )
    }

    @Test
    fun `CA13 quinta 01-10 sem gastos tem limite de 193,54 ate domingo 04-10`() {
        val limite = base.limiteSemanal(outubro, data(1, 10))
        assertEquals(data(1, 10), limite.inicio)
        assertEquals(data(4, 10), limite.fim)
        assertEquals(19_354, limite.valorCentavos)
    }

    @Test
    fun `CA14 segunda 05-10 com a reserva invadida tem limite zero`() {
        val invadido = base.copy(gastos = listOf(Gasto(160_000, data(2, 10))))
        assertEquals(0, invadido.limiteSemanal(outubro, data(5, 10)).valorCentavos)
    }

    @Test
    fun `CA15 salario de 3000,05 com meta de 10 por cento reserva 300,01`() {
        val salario = CenarioBase.salario.copy(valorCentavos = 300_005)
        assertEquals(30_001, base.copy(entradas = listOf(salario)).resumo(outubro).reserva)
    }

    @Test
    fun `CA16 limite manual de 500 reais vira 285,71 de 01 a 04-10 e avisa do ritmo`() {
        val limite = base.limiteSemanal(outubro, data(1, 10), limiteManual = 50_000)
        assertEquals(28_571, limite.valorCentavos)
        assertTrue(limite.ritmoNaoFechaCiclo)
        assertEquals(19_354, base.limiteSemanal(outubro, data(1, 10)).valorCentavos)
    }
}
