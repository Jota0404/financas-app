package com.joaobarcelos.financas.domain.usecase

import com.joaobarcelos.financas.domain.Ambiente
import com.joaobarcelos.financas.domain.CenarioBase
import com.joaobarcelos.financas.domain.calculadora.Orcamento
import com.joaobarcelos.financas.domain.calculadora.cicloAtual
import com.joaobarcelos.financas.domain.data
import com.joaobarcelos.financas.domain.model.Ciclo
import com.joaobarcelos.financas.domain.model.Configuracoes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class ConfigurarTest {
    private val padrao = Configuracoes()
    private fun bloqueio(erro: ErroCadastro) = Decisao.Bloqueado(erro)

    private val ambiente = Ambiente(CenarioBase.orcamento)
    private val reagendamentos = mutableListOf<LocalTime>()
    private val configurar = Configurar(ambiente.orcamento, ambiente.configuracoes, ambiente.alertas) { reagendamentos += it }

    @Test
    fun `padroes do briefing sao validos`() {
        assertEquals(Decisao.Permitido, decidirConfiguracoes(padrao))
    }

    @Test
    fun `dia do pagamento fora de 1 a 31 e bloqueado`() {
        assertEquals(bloqueio(ErroCadastro.DIA_PAGAMENTO_INVALIDO), decidirConfiguracoes(padrao.copy(diaPagamento = 0)))
        assertEquals(bloqueio(ErroCadastro.DIA_PAGAMENTO_INVALIDO), decidirConfiguracoes(padrao.copy(diaPagamento = 32)))
        assertEquals(Decisao.Permitido, decidirConfiguracoes(padrao.copy(diaPagamento = 31)))
    }

    @Test
    fun `RN15 limite manual zero ou negativo e bloqueado e vazio volta ao automatico`() {
        assertEquals(bloqueio(ErroCadastro.VALOR_ZERO_OU_NEGATIVO), decidirConfiguracoes(padrao.copy(limiteSemanalManual = 0)))
        assertEquals(bloqueio(ErroCadastro.VALOR_ZERO_OU_NEGATIVO), decidirConfiguracoes(padrao.copy(limiteSemanalManual = -1)))
        assertEquals(Decisao.Permitido, decidirConfiguracoes(padrao.copy(limiteSemanalManual = 50_000)))
        assertEquals(Decisao.Permitido, decidirConfiguracoes(padrao.copy(limiteSemanalManual = null)))
    }

    @Test
    fun `percentuais nas faixas e atencao menor que critico`() {
        // decisão do dono: atenção de 1% a 99%, crítico de 2% a 100%, atenção menor que crítico
        assertEquals(Decisao.Permitido, decidirConfiguracoes(padrao.copy(percentualAtencao = 100, percentualCritico = 200)))
        assertEquals(Decisao.Permitido, decidirConfiguracoes(padrao.copy(percentualAtencao = 9_900, percentualCritico = 10_000)))
        assertEquals(bloqueio(ErroCadastro.PERCENTUAL_FORA_DA_FAIXA), decidirConfiguracoes(padrao.copy(percentualAtencao = 0)))
        assertEquals(bloqueio(ErroCadastro.PERCENTUAL_FORA_DA_FAIXA), decidirConfiguracoes(padrao.copy(percentualCritico = 10_001)))
        assertEquals(
            bloqueio(ErroCadastro.ATENCAO_NAO_MENOR_QUE_CRITICO),
            decidirConfiguracoes(padrao.copy(percentualAtencao = 9_000, percentualCritico = 7_000)),
        )
        assertEquals(
            bloqueio(ErroCadastro.ATENCAO_NAO_MENOR_QUE_CRITICO),
            decidirConfiguracoes(padrao.copy(percentualAtencao = 8_000, percentualCritico = 8_000)),
        )
    }

    @Test
    fun `primeiro uso aparece ate ser concluido e nao aparece para quem ja tem entradas`() {
        // decisão do dono
        assertTrue(mostrarPrimeiroUso(padrao, Orcamento()))
        assertFalse(mostrarPrimeiroUso(padrao.copy(primeiroUsoConcluido = true), Orcamento()))
        assertFalse(mostrarPrimeiroUso(padrao, CenarioBase.orcamento))
    }

    @Test
    fun `salvar grava as configuracoes e o horario novo reagenda os alertas`() = runBlocking {
        val nova = padrao.copy(limiteSemanalManual = 50_000, percentualAtencao = 6_000, percentualCritico = 8_500, horaResumo = LocalTime.of(7, 30))
        assertEquals(Decisao.Permitido, configurar.salvar(nova, data(6, 10)))
        assertEquals(nova, ambiente.configuracoes.dados.value)
        assertEquals(listOf(LocalTime.of(7, 30)), reagendamentos)
        // sem mudar o horário, não reagenda de novo
        configurar.salvar(nova.copy(limiteSemanalManual = null), data(6, 10))
        assertEquals(1, reagendamentos.size)
        assertNull(ambiente.configuracoes.dados.value.limiteSemanalManual)
    }

    @Test
    fun `configuracao bloqueada nao e gravada`() = runBlocking {
        assertEquals(
            bloqueio(ErroCadastro.ATENCAO_NAO_MENOR_QUE_CRITICO),
            configurar.salvar(padrao.copy(percentualAtencao = 9_500), data(6, 10)),
        )
        assertEquals(padrao, ambiente.configuracoes.dados.value)
        assertTrue(reagendamentos.isEmpty())
    }

    @Test
    fun `RN01 mudar o dia do pagamento nas configuracoes fecha o ciclo e guarda o irregular`() = runBlocking {
        // P14: 1 -> 15 em 20/10 deixa o ciclo atual de 01/10 a 14/11; em 15/10, fecha 01/10 a 14/10
        configurar.salvar(padrao.copy(diaPagamento = 15), data(20, 10))
        val config = ambiente.configuracoes.dados.value
        assertEquals(15, config.diaPagamento)
        assertEquals(Ciclo(data(1, 10), data(14, 11)), config.cicloAtual(data(25, 10)))

        val outro = Ambiente(CenarioBase.orcamento)
        Configurar(outro.orcamento, outro.configuracoes, outro.alertas) {}.salvar(padrao.copy(diaPagamento = 15), data(15, 10))
        assertEquals(listOf(Ciclo(data(1, 10), data(14, 10))), outro.historico.fechados.value.map { it.ciclo })
    }

    @Test
    fun `concluir o primeiro uso grava que ele nao aparece mais`() = runBlocking {
        val vazio = Ambiente()
        val configurarVazio = Configurar(vazio.orcamento, vazio.configuracoes, vazio.alertas) {}
        assertTrue(configurarVazio.mostrarPrimeiroUso())
        configurarVazio.concluirPrimeiroUso()
        assertFalse(configurarVazio.mostrarPrimeiroUso())
    }
}
