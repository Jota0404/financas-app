package com.joaobarcelos.financas

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.joaobarcelos.financas.domain.model.Categoria
import com.joaobarcelos.financas.domain.model.ContaFixa
import com.joaobarcelos.financas.domain.model.Entrada
import com.joaobarcelos.financas.domain.model.MetaReserva
import com.joaobarcelos.financas.domain.model.TipoEntrada
import com.joaobarcelos.financas.domain.model.TipoMeta
import com.joaobarcelos.financas.domain.repository.OrcamentoRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import javax.inject.Inject

/**
 * CA12 no aparelho, com o app de verdade e o banco em memória (ArmazenamentoDeTeste): o teste não lê
 * nem grava os dados reais. Rodar só no emulador, com ANDROID_SERIAL=emulator-5554.
 */
@HiltAndroidTest
class RegistrarGastoTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val tela = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var orcamento: OrcamentoRepository

    /** Cenário base do briefing, num banco vazio. */
    @Before
    fun cenarioBase() = runBlocking {
        hilt.inject()
        val inicio = LocalDate.now().withDayOfMonth(1)
        orcamento.salvar(Entrada(300_000, TipoEntrada.RECORRENTE, inicio, descricao = "Salário"))
        orcamento.salvar(ContaFixa(100_000, inicio, descricao = "Aluguel", diaVencimento = 5))
        orcamento.salvar(MetaReserva(TipoMeta.PERCENTUAL, 1000, nome = "Reserva"))
        Unit
    }

    private fun existe(texto: String) = tela.onAllNodes(hasText(texto, substring = true)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `CA12_registrar_gasto_em_ate_3_toques`() {
        var toques = 0
        fun tocar(no: SemanticsNodeInteraction) = no.performClick().also { toques++ }

        tela.waitUntil(5_000) { existe("Disponível da semana") }
        tocar(tela.onNodeWithContentDescription("Novo gasto"))
        // o teclado abre direto no valor; digitar o valor não conta como toque (decisão do dono)
        val valorComFoco = isFocused() and hasSetTextAction()
        tela.waitUntil(5_000) { tela.onAllNodes(valorComFoco).fetchSemanticsNodes().isNotEmpty() }
        tela.onNode(valorComFoco).performTextInput("12,34")
        tocar(tela.onNodeWithText("Salvar"))

        tela.waitUntil(5_000) { runBlocking { orcamento.orcamento().first().gastos.isNotEmpty() } }
        assertTrue("foram $toques toques", toques <= 3)
        val gasto = runBlocking { orcamento.orcamento().first().gastos.single() }
        assertEquals(1_234L, gasto.valorCentavos)
        assertEquals(Categoria.OUTROS, gasto.categoriaId)
        assertEquals(LocalDate.now(), gasto.data)
    }
}
