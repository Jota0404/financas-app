package com.joaobarcelos.financas.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.joaobarcelos.financas.data.datastore.DataStoreConfiguracoesRepository
import com.joaobarcelos.financas.data.local.FinancasDatabase
import com.joaobarcelos.financas.data.repository.RoomHistoricoRepository
import com.joaobarcelos.financas.data.repository.RoomOrcamentoRepository
import com.joaobarcelos.financas.domain.calculadora.cicloAtual
import com.joaobarcelos.financas.domain.model.Ciclo
import com.joaobarcelos.financas.domain.model.Entrada
import com.joaobarcelos.financas.domain.model.TipoEntrada
import com.joaobarcelos.financas.domain.usecase.Alertas
import com.joaobarcelos.financas.domain.usecase.Cadastros
import com.joaobarcelos.financas.domain.usecase.Configurar
import com.joaobarcelos.financas.ui.cadastros.ContasViewModel
import com.joaobarcelos.financas.ui.cadastros.MetasViewModel
import com.joaobarcelos.financas.ui.configuracoes.ConfiguracoesTela
import com.joaobarcelos.financas.ui.configuracoes.ConfiguracoesViewModel
import com.joaobarcelos.financas.ui.onboarding.PrimeiroUsoTela
import com.joaobarcelos.financas.ui.onboarding.PrimeiroUsoViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Telas 1 (primeiro uso) e 8 (Configurações), com o banco de verdade, em quarta, 07/10/2026. */
@RunWith(RobolectricTestRunner::class)
class PrimeiroUsoEConfiguracoesTelaTest {
    @get:Rule val tela = createComposeRule()
    @get:Rule val pasta = TemporaryFolder()

    private val banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FinancasDatabase::class.java)
        .addCallback(FinancasDatabase.CriarCategoriasPadrao)
        .build()
    private val escopo = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val relogio = Clock.fixed(LocalDate.of(2026, 10, 7).atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val orcamento = RoomOrcamentoRepository(banco.dao(), relogio)
    private val historico = RoomHistoricoRepository(banco.dao())
    private val configuracoes by lazy {
        DataStoreConfiguracoesRepository(DataStoreConfiguracoesRepository.criarDataStore(escopo) { File(pasta.root, "c.preferences_pb") })
    }
    private val alertas by lazy { Alertas(orcamento, configuracoes, historico) {} }
    private val cadastros by lazy { Cadastros(orcamento, configuracoes, alertas) }
    private val reagendamentos = mutableListOf<LocalTime>()
    private val configurar by lazy { Configurar(orcamento, configuracoes, alertas) { reagendamentos += it } }
    private val vmPrimeiroUso by lazy { PrimeiroUsoViewModel(configurar, cadastros, configuracoes, relogio) }
    private val vmContas by lazy { ContasViewModel(cadastros, orcamento, configuracoes, relogio) }
    private val vmMetas by lazy { MetasViewModel(cadastros, orcamento, configuracoes, relogio) }
    private val vmConfiguracoes by lazy { ConfiguracoesViewModel(configurar, configuracoes, relogio) }

    @After
    fun fechar() {
        escopo.cancel()
        banco.close()
    }

    private fun esperar(texto: String): SemanticsNodeInteraction {
        tela.waitUntil(5_000) { tela.onAllNodes(hasText(texto, substring = true)).fetchSemanticsNodes().isNotEmpty() }
        return tela.onNodeWithText(texto, substring = true)
    }

    private fun config() = runBlocking { configuracoes.configuracoes().first() }

    /** Espera algo gravado em segundo plano: deixa a tela trabalhar e confere de novo, por até 5 s. */
    private fun esperarAte(condicao: () -> Boolean) {
        repeat(100) {
            tela.waitForIdle()
            if (condicao()) return
            Thread.sleep(50)
        }
        throw AssertionError("A condição não foi atingida em 5 s")
    }

    private fun primeiroUso() = tela.setContent { PrimeiroUsoTela(vmPrimeiroUso, vmContas, vmMetas) }

    @Test
    fun `primeiro uso aparece num app novo e nao aparece para quem ja tem entradas`() {
        tela.setContent { PrimeiroUsoTela(vmPrimeiroUso, vmContas, vmMetas) }
        esperarAte { vmPrimeiroUso.mostrar.value != null }
        assertEquals(true, vmPrimeiroUso.mostrar.value)
        runBlocking { orcamento.salvar(Entrada(300_000, TipoEntrada.RECORRENTE, LocalDate.of(2026, 10, 1), descricao = "Salário")) }
        val outro = PrimeiroUsoViewModel(configurar, cadastros, configuracoes, relogio)
        esperarAte { outro.mostrar.value != null }
        assertEquals(false, outro.mostrar.value)
    }

    @Test
    fun `primeiro uso com dia e salario e o resto pulado`() {
        primeiroUso()
        esperar("Passo 1 de 5")
        tela.onNodeWithText("1").performTextReplacement("5")
        tela.onNodeWithText("Continuar").performClick()
        esperar("Passo 2 de 5")
        tela.onNodeWithText("Salário (R$)").performTextInput("3.000,00")
        tela.onNodeWithText("Continuar").performClick()
        esperar("Passo 3 de 5")
        tela.onNodeWithText("Pular").performClick()
        esperar("Passo 4 de 5")
        tela.onNodeWithText("Pular").performClick()
        esperar("Passo 5 de 5")
        tela.onNodeWithText("Agora não").performClick()

        esperarAte { config().primeiroUsoConcluido }
        esperarAte { vmPrimeiroUso.mostrar.value == false }
        // decisão do dono: dia 5 escolhido em 07/10 dá o ciclo normal de 05/10 a 04/11
        assertEquals(Ciclo(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 4)), config().cicloAtual(LocalDate.of(2026, 10, 7)))
        assertNull(config().cicloIrregular)
        // P13: o salário entra a partir do início do ciclo atual
        val salario = runBlocking { orcamento.orcamento().first().entradas.single() }
        assertEquals(300_000L, salario.valorCentavos)
        assertEquals(LocalDate.of(2026, 10, 5), salario.dataInicio)
    }

    @Test
    fun `primeiro uso todo pulado so grava o dia padrao`() {
        primeiroUso()
        esperar("Passo 1 de 5")
        tela.onNodeWithText("Continuar").performClick()
        repeat(3) { esperar("Pular").performClick() }
        esperar("Agora não").performClick()
        esperarAte { config().primeiroUsoConcluido }
        assertEquals(1, config().diaPagamento)
        assertTrue(runBlocking { orcamento.orcamento().first().entradas.isEmpty() })
    }

    @Test
    fun `dia invalido no primeiro uso mostra o motivo`() {
        primeiroUso()
        esperar("Passo 1 de 5")
        tela.onNodeWithText("1").performTextReplacement("40")
        tela.onNodeWithText("Continuar").performClick()
        esperar("O dia do pagamento vai de 1 a 31.")
        esperar("Passo 1 de 5")
    }

    @Test
    fun `configuracoes salvam limite e percentuais`() {
        tela.setContent { ConfiguracoesTela(vmConfiguracoes) }
        esperar("Dia do pagamento")
        tela.onNodeWithText("Limite manual da semana (R$)").performTextInput("500,00")
        tela.onNodeWithText("70").performTextReplacement("60")
        esperar("Salvar").performScrollTo().performClick()
        esperar("Configurações salvas.")
        assertEquals(50_000L, config().limiteSemanalManual)
        assertEquals(6_000, config().percentualAtencao)
        assertFalse(reagendamentos.isNotEmpty())
    }

    @Test
    fun `atencao maior que critico e bloqueado na tela`() {
        tela.setContent { ConfiguracoesTela(vmConfiguracoes) }
        esperar("Dia do pagamento")
        tela.onNodeWithText("70").performTextReplacement("95")
        esperar("Salvar").performScrollTo().performClick()
        esperar("O percentual de atenção precisa ser menor que o crítico.")
        assertEquals(7_000, config().percentualAtencao)
    }

    @Test
    fun `RN01 mudar o dia nas configuracoes pede confirmacao com o efeito no ciclo`() {
        // P14: 1 -> 15 em 07/10 deixa o ciclo atual de 01/10 a 14/10
        tela.setContent { ConfiguracoesTela(vmConfiguracoes) }
        esperar("Dia do pagamento")
        tela.onNodeWithText("1").performTextReplacement("15")
        esperar("Salvar").performScrollTo().performClick()
        esperar("O ciclo atual passa a ir de 01/10/2026 a 14/10/2026.")
        tela.onNodeWithText("Mudar").performClick()
        esperarAte { config().diaPagamento == 15 }
        esperar("Configurações salvas.")
        assertEquals(Ciclo(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 14)), config().cicloIrregular)
    }
}
