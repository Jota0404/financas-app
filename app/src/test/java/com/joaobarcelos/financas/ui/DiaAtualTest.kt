package com.joaobarcelos.financas.ui

import app.cash.turbine.test
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class DiaAtualTest {
    /** Relógio que anda junto com o tempo virtual do teste, a partir de [inicio]. */
    private fun TestScope.relogio(inicio: LocalDateTime) = object : Clock() {
        override fun instant() = inicio.toInstant(ZoneOffset.UTC).plusMillis(testScheduler.currentTime)
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId) = this
    }

    @Test
    fun `a data de hoje muda sozinha na virada do dia`() = runTest {
        // QA Etapa 4, B2: com o app aberto às 23:59 de 06/10, a tela passa para 07/10 à meia-noite
        diaAtual(relogio(LocalDateTime.of(2026, 10, 6, 23, 59, 30))).test {
            assertEquals(LocalDate.of(2026, 10, 6), awaitItem())
            advanceTimeBy(29_000)
            expectNoEvents()
            advanceTimeBy(2_000)
            assertEquals(LocalDate.of(2026, 10, 7), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a virada do ciclo tambem chega sozinha`() = runTest {
        // 31/10 às 23:59 -> 01/11, primeiro dia do ciclo de novembro
        diaAtual(relogio(LocalDateTime.of(2026, 10, 31, 23, 59))).test {
            assertEquals(LocalDate.of(2026, 10, 31), awaitItem())
            advanceTimeBy(61_000)
            assertEquals(LocalDate.of(2026, 11, 1), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
