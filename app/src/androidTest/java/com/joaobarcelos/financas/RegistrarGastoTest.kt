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
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** CA12 no aparelho, com o app de verdade. */
@RunWith(AndroidJUnit4::class)
class RegistrarGastoTest {
    @get:Rule
    val tela = createAndroidComposeRule<MainActivity>()

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
        assertTrue("foram $toques toques", toques <= 3)

        // Conferência, fora da contagem: o gasto está no Histórico, em Outros.
        // Se o aparelho já tiver gastos, pode aparecer o aviso de reserva invadida.
        tela.waitUntil(5_000) { !existe("Novo gasto") || existe("Entendi") }
        if (existe("Entendi")) tela.onNodeWithText("Entendi").performClick()
        tela.onNodeWithText("Histórico").performClick()
        tela.waitUntil(5_000) { existe("R$ 12,34") }
        assertTrue(existe("Outros"))
    }
}
