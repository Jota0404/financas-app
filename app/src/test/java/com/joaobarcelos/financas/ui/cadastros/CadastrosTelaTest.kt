package com.joaobarcelos.financas.ui.cadastros

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
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
import com.joaobarcelos.financas.data.repository.RoomOrcamentoRepository
import com.joaobarcelos.financas.domain.model.ContaFixa
import com.joaobarcelos.financas.domain.model.Entrada
import com.joaobarcelos.financas.domain.model.MetaReserva
import com.joaobarcelos.financas.domain.model.TipoEntrada
import com.joaobarcelos.financas.domain.model.TipoMeta
import com.joaobarcelos.financas.domain.usecase.Cadastros
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/** Telas da aba Cadastros com o banco e as configurações de verdade, em 06/10/2026. */
@RunWith(RobolectricTestRunner::class)
class CadastrosTelaTest {
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
    private val cadastros by lazy { Cadastros(orcamento, configuracoes) }

    // Criados fora da tela, como pede o lint; o by lazy cria só no primeiro uso
    private val vmContas by lazy { ContasViewModel(cadastros, orcamento, configuracoes, relogio) }
    private val vmEntradas by lazy { EntradasViewModel(cadastros, orcamento, configuracoes, relogio) }
    private val vmMetas by lazy { MetasViewModel(cadastros, orcamento, configuracoes, relogio) }

    @After
    fun fechar() {
        escopo.cancel()
        banco.close()
    }

    /** Cenário base do briefing. */
    private fun salvarCenarioBase() = runBlocking {
        orcamento.salvar(Entrada(300_000, TipoEntrada.RECORRENTE, LocalDate.of(2026, 10, 1), descricao = "Salário"))
        orcamento.salvar(ContaFixa(100_000, LocalDate.of(2026, 10, 1), descricao = "Aluguel", diaVencimento = 5))
        orcamento.salvar(ContaFixa(20_000, LocalDate.of(2026, 10, 1), duracaoMeses = 3, descricao = "Celular", diaVencimento = 10))
        orcamento.salvar(MetaReserva(TipoMeta.PERCENTUAL, 1000, nome = "Reserva de segurança"))
    }

    private fun esperar(texto: String, parcial: Boolean = true): SemanticsNodeInteraction {
        tela.waitUntil(5_000) { tela.onAllNodes(hasText(texto, substring = parcial)).fetchSemanticsNodes().isNotEmpty() }
        return tela.onNodeWithText(texto, substring = parcial)
    }

    private fun campo(rotulo: String) = tela.onNodeWithText(rotulo)

    @Test
    fun `listas vazias orientam a tocar em mais`() {
        tela.setContent { ContasTela(vmContas) }
        esperar("Nenhuma conta fixa ainda. Toque em + para adicionar.")
    }

    @Test
    fun `entradas e metas vazias tambem orientam`() {
        tela.setContent {
            EntradasTela(vmEntradas)
            MetasTela(vmMetas)
        }
        esperar("Nenhuma entrada ainda.")
        esperar("Nenhuma meta de reserva ainda.")
    }

    @Test
    fun `cadastrar financiamento na parcela 3 de 10 pelo formulario`() {
        tela.setContent { ContasTela(vmContas) }
        esperar("Nenhuma conta fixa ainda.")
        tela.onNodeWithContentDescription("Adicionar").performClick()
        campo("Nome (ex.: Aluguel)").performTextInput("Financiamento")
        campo("Valor (R$)").performTextInput("300")
        campo("Dia do vencimento").performTextInput("15")
        tela.onNode(isToggleable()).performClick()
        campo("Duração (meses)").performTextInput("10")
        campo("Parcela deste ciclo").performTextReplacement("3")
        tela.onNodeWithText("Salvar").performClick()
        esperar("Parcela 3 de 10")
        esperar("R$ 300,00 · vence dia 15")
    }

    @Test
    fun `CA04 meta de 2000 reais e bloqueada na tela porque faltam 500 reais`() {
        salvarCenarioBase()
        tela.setContent { MetasTela(vmMetas) }
        esperar("R$ 300,00 neste ciclo")
        tela.onNodeWithContentDescription("Adicionar").performClick()
        campo("Nome (ex.: Reserva de segurança)").performTextInput("Viagem")
        tela.onNodeWithText("Valor fixo").performClick()
        campo("Valor (R$)").performTextInput("2.000,00")
        tela.onNodeWithText("Salvar").performClick()
        esperar("faltam R$ 500,00")
        assertEquals(1, runBlocking { orcamento.orcamento().first().metas.size })
    }

    @Test
    fun `RN08 conta que deixa as metas sem caber e salva com aviso`() {
        salvarCenarioBase()
        tela.setContent { ContasTela(vmContas) }
        esperar("Aluguel")
        tela.onNodeWithContentDescription("Adicionar").performClick()
        campo("Nome (ex.: Aluguel)").performTextInput("Escola")
        campo("Valor (R$)").performTextInput("1.700,00")
        campo("Dia do vencimento").performTextInput("10")
        tela.onNodeWithText("Salvar").performClick()
        esperar("Salvo, mas atenção")
        esperar("Faltam R$ 200,00")
        assertEquals(3, runBlocking { orcamento.orcamento().first().contas.size })
    }

    @Test
    fun `RN04 conta ja descontada oferece encerrar e nao excluir`() {
        runBlocking { orcamento.salvar(ContaFixa(100_000, LocalDate.of(2026, 9, 1), descricao = "Aluguel antigo", diaVencimento = 5)) }
        tela.setContent { ContasTela(vmContas) }
        esperar("Aluguel antigo").performClick()
        esperar("Encerrar conta")
        tela.onNodeWithText("Excluir conta").assertDoesNotExist()
        tela.onNodeWithText("Encerrar conta").performClick()
        tela.onNodeWithText("Encerrar").performClick()
        esperar("Encerrada em 06/10/2026: ainda conta neste ciclo e sai no próximo")
    }

    @Test
    fun `RN04 conta que comeca neste ciclo pode ser excluida`() {
        runBlocking { orcamento.salvar(ContaFixa(5_000, LocalDate.of(2026, 10, 1), descricao = "Engano", diaVencimento = 5)) }
        tela.setContent { ContasTela(vmContas) }
        esperar("Engano").performClick()
        esperar("Excluir conta").performScrollTo().performClick()
        tela.onNodeWithText("Excluir").performClick()
        esperar("Nenhuma conta fixa ainda.")
    }

    @Test
    fun `RN15 entrada de valor zero e bloqueada na tela`() {
        tela.setContent { EntradasTela(vmEntradas) }
        esperar("Nenhuma entrada ainda.")
        tela.onNodeWithContentDescription("Adicionar").performClick()
        campo("Nome (ex.: Salário)").performTextInput("Salário")
        campo("Valor (R$)").performTextInput("0")
        tela.onNodeWithText("Salvar").performClick()
        esperar("O valor precisa ser maior que zero")
        assertEquals(0, runBlocking { orcamento.orcamento().first().entradas.size })
    }

    @Test
    fun `salario novo vem com o inicio do ciclo e aparece em recorrentes`() {
        tela.setContent { EntradasTela(vmEntradas) }
        esperar("Nenhuma entrada ainda.")
        tela.onNodeWithContentDescription("Adicionar").performClick()
        esperar("Entra a partir de: 01/10/2026")
        campo("Nome (ex.: Salário)").performTextInput("Salário")
        campo("Valor (R$)").performTextInput("3.000,00")
        tela.onNodeWithText("Salvar").performClick()
        esperar("Recorrentes")
        esperar("R$ 3.000,00 · desde 01/10/2026")
    }
}
