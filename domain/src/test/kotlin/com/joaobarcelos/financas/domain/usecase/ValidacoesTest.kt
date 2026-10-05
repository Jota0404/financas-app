package com.joaobarcelos.financas.domain.usecase

import com.joaobarcelos.financas.domain.data
import com.joaobarcelos.financas.domain.model.Ciclo
import com.joaobarcelos.financas.domain.model.Gasto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidacoesTest {
    private val outubro = Ciclo(data(1, 10), data(31, 10))
    private val hoje = data(15, 10)

    private fun validar(valorCentavos: Long, dia: Int, mes: Int, ano: Int = 2026) =
        validarGasto(Gasto(valorCentavos, data(dia, mes, ano)), outubro, hoje)

    @Test
    fun `RN14 gasto retroativo dentro do ciclo atual e permitido`() {
        assertNull(validar(5_000, 1, 10))
        assertNull(validar(5_000, 14, 10))
        assertNull(validar(5_000, 15, 10))
    }

    @Test
    fun `RN14 gasto em ciclo fechado e bloqueado`() {
        assertEquals(ErroGasto.DATA_EM_CICLO_FECHADO, validar(5_000, 30, 9))
        assertEquals(ErroGasto.DATA_EM_CICLO_FECHADO, validar(5_000, 31, 12, 2025))
    }

    @Test
    fun `RN14 gasto com data futura e bloqueado`() {
        assertEquals(ErroGasto.DATA_FUTURA, validar(5_000, 16, 10))
        assertEquals(ErroGasto.DATA_FUTURA, validar(5_000, 1, 11))
    }

    @Test
    fun `RN15 gasto de valor zero ou negativo e bloqueado`() {
        assertEquals(ErroGasto.VALOR_ZERO_OU_NEGATIVO, validar(0, 10, 10))
        assertEquals(ErroGasto.VALOR_ZERO_OU_NEGATIVO, validar(-100, 10, 10))
        assertNull(validar(1, 10, 10))
    }

    @Test
    fun `RN15 valor zero ou negativo e invalido em qualquer cadastro`() {
        assertFalse(valorValido(0))
        assertFalse(valorValido(-1))
        assertFalse(valorValido(Long.MIN_VALUE))
        assertTrue(valorValido(1))
    }
}
