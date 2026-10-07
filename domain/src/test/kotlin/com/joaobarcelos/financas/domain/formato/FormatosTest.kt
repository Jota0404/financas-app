package com.joaobarcelos.financas.domain.formato

import com.joaobarcelos.financas.domain.data
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatosTest {
    @Test
    fun `reais com milhar e centavos`() {
        assertEquals("R$ 1.500,00", formatarReais(150_000))
        assertEquals("R$ 388,88", formatarReais(38_888))
        assertEquals("R$ 0,01", formatarReais(1))
        assertEquals("R$ 0,00", formatarReais(0))
        assertEquals("R$ 1.234.567,89", formatarReais(123_456_789))
        assertEquals("-R$ 100,00", formatarReais(-10_000))
    }

    @Test
    fun `le valores digitados no formato brasileiro`() {
        assertEquals(150_000L, lerCentavos("1.500,00"))
        assertEquals(150_000L, lerCentavos("1500"))
        assertEquals(150_050L, lerCentavos("1500,5"))
        assertEquals(1L, lerCentavos("R$ 0,01"))
        assertEquals(150_000L, lerCentavos("1.500"))
        assertEquals(0L, lerCentavos("0"))
    }

    @Test
    fun `ponto com 1 ou 2 digitos no fim e centavo e com 3 digitos e milhar`() {
        // QA M2: teclados que só têm ponto
        assertEquals(150_050L, lerCentavos("1500.50"))
        assertEquals(1_250L, lerCentavos("12.5"))
        assertEquals(150L, lerCentavos("1.50"))
        assertEquals(150_000L, lerCentavos("1.500"))
        assertEquals(1050L, lerPontosBase("10.5"))
    }

    @Test
    fun `texto que nao e valor nao vira dinheiro`() {
        listOf("", "abc", "1,505", "1.5000", "1.500.5", "-10", "99999999999999999999", "1,2,3", "12a", "1,500.50").forEach {
            assertNull(it, lerCentavos(it))
        }
    }

    @Test
    fun `percentuais em pontos base`() {
        assertEquals("10%", formatarPercentual(1000))
        assertEquals("10,5%", formatarPercentual(1050))
        assertEquals("10,25%", formatarPercentual(1025))
        assertEquals(1000L, lerPontosBase("10"))
        assertEquals(1050L, lerPontosBase("10,5"))
        assertEquals(1025L, lerPontosBase("10,25%"))
        assertNull(lerPontosBase("10,255"))
        assertNull(lerPontosBase("dez"))
    }

    @Test
    fun `data no formato brasileiro`() {
        assertEquals("06/10/2026", formatarData(data(6, 10)))
    }
}
