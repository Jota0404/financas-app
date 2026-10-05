package com.joaobarcelos.financas.domain.calculadora

import com.joaobarcelos.financas.domain.data
import com.joaobarcelos.financas.domain.model.Ciclo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CiclosTest {
    private val outubro = Ciclo(data(1, 10), data(31, 10))

    @Test
    fun `RN01 ciclo vai do dia do pagamento ate a vespera do proximo`() {
        assertEquals(outubro, cicloDe(data(1, 10), 1))
        assertEquals(outubro, cicloDe(data(31, 10), 1))
        assertEquals(Ciclo(data(15, 10), data(14, 11)), cicloDe(data(20, 10), 15))
    }

    @Test
    fun `RN01 data antes do dia do pagamento pertence ao ciclo do mes anterior`() {
        assertEquals(Ciclo(data(15, 9), data(14, 10)), cicloDe(data(14, 10), 15))
    }

    @Test
    fun `RN01 primeiro e ultimo dia pertencem ao ciclo`() {
        assertTrue(data(1, 10) in outubro)
        assertTrue(data(31, 10) in outubro)
        assertFalse(data(30, 9) in outubro)
        assertFalse(data(1, 11) in outubro)
    }

    @Test
    fun `RN01 ciclo atravessa a virada do ano`() {
        assertEquals(Ciclo(data(15, 12), data(14, 1, 2027)), cicloDe(data(10, 1, 2027), 15))
        assertEquals(Ciclo(data(1, 12), data(31, 12)), cicloDe(data(31, 12), 1))
        assertEquals(Ciclo(data(1, 1, 2027), data(31, 1, 2027)), cicloDe(data(1, 1, 2027), 1))
    }

    @Test
    fun `RN01 mudar o dia de 1 para 15 em 10-10 termina o ciclo em 14-10`() {
        assertEquals(Ciclo(data(1, 10), data(14, 10)), mudarDiaPagamento(outubro, 15, hoje = data(10, 10)))
    }

    @Test
    fun `RN01 depois da mudanca os ciclos seguem o novo dia`() {
        val curto = mudarDiaPagamento(outubro, 15, hoje = data(10, 10))
        assertEquals(Ciclo(data(15, 10), data(14, 11)), cicloDe(curto.fim.plusDays(1), 15))
    }

    @Test
    fun `RN01 mudar para um dia que ja passou no mes estica o ciclo ate o mes seguinte`() {
        // dia 15 -> 5 em 20/10: a próxima ocorrência do dia 5 é 05/11
        val atual = Ciclo(data(15, 10), data(14, 11))
        assertEquals(Ciclo(data(15, 10), data(4, 11)), mudarDiaPagamento(atual, 5, hoje = data(20, 10)))
    }

    @Test
    fun `RN01 mudar para o dia de hoje fecha o ciclo ontem e abre um novo hoje`() {
        // decisão do dono: 1 -> 15 em 15/10 fecha outubro em 14/10 e abre 15/10 a 14/11
        assertEquals(Ciclo(data(15, 10), data(14, 11)), mudarDiaPagamento(outubro, 15, hoje = data(15, 10)))
    }

    @Test
    fun `RN01 mudar no primeiro dia do ciclo para o mesmo dia nao muda nada`() {
        assertEquals(outubro, mudarDiaPagamento(outubro, 1, hoje = data(1, 10)))
        assertEquals(outubro, mudarDiaPagamento(outubro, 1, hoje = data(10, 10)))
    }

    @Test
    fun `RN01 mudar para 31 num mes de 30 dias usa o ultimo dia do mes`() {
        // 1 -> 31 em 10/11: novembro não tem dia 31, então a próxima ocorrência é 30/11 (RN02)
        val novembro = Ciclo(data(1, 11), data(30, 11))
        assertEquals(Ciclo(data(1, 11), data(29, 11)), mudarDiaPagamento(novembro, 31, hoje = data(10, 11)))
    }

    @Test
    fun `RN01 mudanca com data fora do ciclo atual e recusada`() {
        assertThrows(IllegalArgumentException::class.java) {
            mudarDiaPagamento(outubro, 15, hoje = data(1, 11))
        }
    }

    @Test
    fun `RN02 dia 31 em abril comeca no dia 30`() {
        assertEquals(Ciclo(data(30, 4, 2027), data(30, 5, 2027)), cicloDe(data(15, 5, 2027), 31))
    }

    @Test
    fun `RN02 depois do mes curto o ciclo volta ao dia configurado`() {
        assertEquals(Ciclo(data(31, 5, 2027), data(29, 6, 2027)), cicloDe(data(31, 5, 2027), 31))
        assertEquals(Ciclo(data(30, 6, 2027), data(30, 7, 2027)), cicloDe(data(30, 6, 2027), 31))
    }

    @Test
    fun `RN02 fevereiro sem ano bissexto termina no dia 28`() {
        assertEquals(Ciclo(data(30, 1, 2027), data(27, 2, 2027)), cicloDe(data(27, 2, 2027), 30))
        assertEquals(Ciclo(data(28, 2, 2027), data(29, 3, 2027)), cicloDe(data(28, 2, 2027), 30))
        assertEquals(Ciclo(data(28, 2, 2027), data(28, 3, 2027)), cicloDe(data(28, 2, 2027), 29))
    }

    @Test
    fun `RN02 fevereiro em ano bissexto usa o dia 29`() {
        assertEquals(Ciclo(data(29, 2, 2028), data(28, 3, 2028)), cicloDe(data(29, 2, 2028), 29))
        assertEquals(Ciclo(data(29, 2, 2028), data(29, 3, 2028)), cicloDe(data(29, 2, 2028), 30))
        assertEquals(Ciclo(data(29, 2, 2028), data(30, 3, 2028)), cicloDe(data(29, 2, 2028), 31))
    }

    @Test
    fun `dia do pagamento fora de 1 a 31 e recusado`() {
        assertThrows(IllegalArgumentException::class.java) { cicloDe(data(1, 10), 0) }
        assertThrows(IllegalArgumentException::class.java) { cicloDe(data(1, 10), 32) }
    }
}
