package com.joaobarcelos.financas.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.joaobarcelos.financas.domain.model.Categoria
import com.joaobarcelos.financas.domain.model.ContaFixa
import com.joaobarcelos.financas.domain.model.Entrada
import com.joaobarcelos.financas.domain.model.Gasto
import com.joaobarcelos.financas.domain.model.MetaReserva
import com.joaobarcelos.financas.domain.model.TipoEntrada
import com.joaobarcelos.financas.domain.model.TipoMeta
import com.joaobarcelos.financas.domain.usecase.Alertas
import com.joaobarcelos.financas.domain.usecase.Cadastros
import com.joaobarcelos.financas.ui.gastos.GastoViewModel
import com.joaobarcelos.financas.ui.historico.HistoricoTela
import com.joaobarcelos.financas.ui.historico.HistoricoViewModel
import com.joaobarcelos.financas.ui.inicio.InicioTela
import com.joaobarcelos.financas.ui.inicio.InicioViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/** Telas Início, Novo gasto e Histórico com o banco de verdade, em terça, 06/10/2026, ao meio-dia. */
@RunWith(RobolectricTestRunner::class)
class GastosTelaTest {
    @get:Rule val tela = createComposeRule()
    @get:Rule val pasta = TemporaryFolder()

    private val banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FinancasDatabase::class.java)
        .addCallback(FinancasDatabase.CriarCategoriasPadrao)
        .build()
    private val escopo = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val relogio = Clock.fixed(LocalDate.of(2026, 10, 6).atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val orcamento = RoomOrcamentoRepository(banco.dao(), relogio)
    private val configuracoes by lazy {
        DataStoreConfiguracoesRepository(DataStoreConfiguracoesRepository.criarDataStore(escopo) { File(pasta.root, "c.preferences_pb") })
    }
    private val cadastros by lazy {
        Cadastros(orcamento, configuracoes, Alertas(orcamento, configuracoes, RoomHistoricoRepository(banco.dao())) {})
    }
    private val vmInicio by lazy { InicioViewModel(orcamento, configuracoes, relogio) }
    private val vmGasto by lazy { GastoViewModel(cadastros, orcamento, relogio) }
    private val vmHistorico by lazy { HistoricoViewModel(orcamento, configuracoes, RoomHistoricoRepository(banco.dao()), relogio) }

    @After
    fun fechar() {
        escopo.cancel()
        banco.close()
    }

    private fun salvarCenarioBase() = runBlocking {
        orcamento.salvar(Entrada(300_000, TipoEntrada.RECORRENTE, LocalDate.of(2026, 10, 1), descricao = "Salário"))
        orcamento.salvar(ContaFixa(100_000, LocalDate.of(2026, 10, 1), descricao = "Aluguel", diaVencimento = 5))
        orcamento.salvar(ContaFixa(20_000, LocalDate.of(2026, 10, 1), duracaoMeses = 3, descricao = "Celular", diaVencimento = 10))
        orcamento.salvar(MetaReserva(TipoMeta.PERCENTUAL, 1000, nome = "Reserva de segurança"))
    }

    private fun gastos() = runBlocking { orcamento.orcamento().first().gastos }

    private fun esperar(texto: String): SemanticsNodeInteraction {
        tela.waitUntil(5_000) { tela.onAllNodes(hasText(texto, substring = true)).fetchSemanticsNodes().isNotEmpty() }
        return tela.onNodeWithText(texto, substring = true)
    }

    private fun inicio() = tela.setContent { InicioTela(aoIrParaCadastros = {}, vm = vmInicio, vmGasto = vmGasto) }

    @Test
    fun `inicio mostra o disponivel da semana do ciclo e a reserva do cenario base`() {
        salvarCenarioBase()
        inicio()
        esperar("Disponível da semana")
        esperar("R$ 388,88") // CA05: semana de 05/10
        esperar("de 05/10 a 11/10")
        esperar("R$ 1.500,00") // CA01
        esperar("R$ 300,00")
        esperar("0% do limite usado")
    }

    @Test
    fun `inicio sem entradas orienta a cadastrar e nao mostra a barra vermelha`() {
        inicio()
        esperar("Comece cadastrando o seu salário")
        tela.onNodeWithText("Sem limite nesta semana", substring = true).assertDoesNotExist()
        tela.onNodeWithText("Disponível da semana").assertDoesNotExist()
    }

    @Test
    fun `CA12 registrar gasto em ate 3 toques`() {
        // decisão do dono: digitar o valor não conta como toque
        salvarCenarioBase()
        inicio()
        esperar("R$ 388,88")
        var toques = 0
        fun tocar(no: SemanticsNodeInteraction) = no.performClick().also { toques++ }

        tocar(tela.onNodeWithContentDescription("Novo gasto"))
        // o teclado abre direto no valor: o campo já está com o foco
        val valorComFoco = isFocused() and hasSetTextAction()
        tela.waitUntil(5_000) { tela.onAllNodes(valorComFoco).fetchSemanticsNodes().isNotEmpty() }
        tela.onNode(valorComFoco).assert(hasText("Valor (R$)"))
        tela.onNode(valorComFoco).performTextInput("45,00")
        tocar(tela.onNodeWithText("Salvar"))

        esperar("R$ 343,88")
        assertTrue("foram $toques toques", toques <= 3)
        val gasto = gastos().single()
        assertEquals(4_500L, gasto.valorCentavos)
        assertEquals(Categoria.OUTROS, gasto.categoriaId)
        assertEquals(LocalDate.of(2026, 10, 6), gasto.data)
    }

    @Test
    fun `CA03 gasto que invade a reserva e salvo e avisa na hora`() {
        salvarCenarioBase()
        inicio()
        esperar("R$ 388,88")
        tela.onNodeWithContentDescription("Novo gasto").performClick()
        tela.onNodeWithText("Valor (R$)").performTextInput("1.600,00")
        tela.onNodeWithText("Salvar").performClick()
        esperar("A reserva deste ciclo está invadida em R$ 100,00.")
        tela.onNodeWithText("Entendi").performClick()
        esperar("Reserva invadida em R$ 100,00")
        assertEquals(1, gastos().size)
    }

    @Test
    fun `CA14 com limite zero a barra explica que o disponivel acabou`() {
        salvarCenarioBase()
        runBlocking { orcamento.salvar(Gasto(160_000, LocalDate.of(2026, 10, 2))) }
        inicio()
        esperar("Sem limite nesta semana: o disponível do ciclo acabou")
        esperar("R$ 0,00")
    }

    @Test
    fun `RN15 gasto de valor zero e bloqueado na tela`() {
        inicio()
        tela.onNodeWithContentDescription("Novo gasto").performClick()
        tela.onNodeWithText("Valor (R$)").performTextInput("0")
        tela.onNodeWithText("Salvar").performClick()
        esperar("O valor precisa ser maior que zero")
        assertTrue(gastos().isEmpty())
    }

    @Test
    fun `historico agrupa os gastos do ciclo por dia com o total`() {
        runBlocking {
            orcamento.salvar(Gasto(4_500, LocalDate.of(2026, 10, 6), descricao = "Almoço", categoriaId = 1))
            orcamento.salvar(Gasto(1_000, LocalDate.of(2026, 10, 6), descricao = "Café", categoriaId = 1))
            orcamento.salvar(Gasto(450, LocalDate.of(2026, 10, 5), descricao = "Ônibus", categoriaId = 2))
            orcamento.salvar(Gasto(9_900, LocalDate.of(2026, 9, 28), descricao = "Setembro"))
        }
        tela.setContent { HistoricoTela(vmHistorico, vmGasto) }
        esperar("Hoje")
        esperar("R$ 55,00")
        esperar("Ontem")
        esperar("Ônibus")
        tela.onNodeWithText("Setembro").assertDoesNotExist()
    }

    @Test
    fun `corrigir um gasto pelo historico`() {
        // decisão do dono: tocar num gasto do ciclo atual abre o formulário para corrigir
        runBlocking { orcamento.salvar(Gasto(4_500, LocalDate.of(2026, 10, 6), descricao = "Almoço", categoriaId = 1)) }
        tela.setContent { HistoricoTela(vmHistorico, vmGasto) }
        esperar("Almoço").performClick()
        esperar("Corrigir gasto")
        tela.onNodeWithText("45,00").performTextReplacement("54,00")
        tela.onNodeWithText("Salvar").performClick()
        esperar("R$ 54,00")
        assertEquals(listOf(5_400L), gastos().map { it.valorCentavos })
    }

    @Test
    fun `RN15 vale tambem ao corrigir um gasto`() {
        runBlocking { orcamento.salvar(Gasto(4_500, LocalDate.of(2026, 10, 6), descricao = "Almoço", categoriaId = 1)) }
        tela.setContent { HistoricoTela(vmHistorico, vmGasto) }
        esperar("Almoço").performClick()
        tela.onNodeWithText("45,00").performTextReplacement("0")
        tela.onNodeWithText("Salvar").performClick()
        esperar("O valor precisa ser maior que zero")
        assertEquals(listOf(4_500L), gastos().map { it.valorCentavos })
    }

    @Test
    fun `excluir um gasto pelo historico`() {
        runBlocking { orcamento.salvar(Gasto(4_500, LocalDate.of(2026, 10, 6), descricao = "Almoço", categoriaId = 1)) }
        tela.setContent { HistoricoTela(vmHistorico, vmGasto) }
        esperar("Almoço").performClick()
        esperar("Excluir gasto").performScrollTo().performClick()
        tela.onNodeWithText("Excluir").performClick()
        esperar("Nenhum gasto neste ciclo ainda.")
        assertTrue(gastos().isEmpty())
    }

    @Test
    fun `P18 avulsa de ciclo fechado aparece no historico so para consulta`() {
        runBlocking { orcamento.salvar(Entrada(50_000, TipoEntrada.AVULSA, LocalDate.of(2026, 9, 20), descricao = "Freela de setembro")) }
        tela.setContent { HistoricoTela(vmHistorico, vmGasto) }
        esperar("Entradas avulsas de ciclos fechados")
        esperar("Freela de setembro")
        esperar("R$ 500,00 · 20/09/2026")
    }
}
