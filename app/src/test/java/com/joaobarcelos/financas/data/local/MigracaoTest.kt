package com.joaobarcelos.financas.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Migração do banco da versão 1 (Etapa 3) para a 2 (Etapa 6): os dados continuam lá. */
@RunWith(RobolectricTestRunner::class)
class MigracaoTest {
    @get:Rule
    val ajudante = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FinancasDatabase::class.java)

    @Test
    fun `migracao 1 para 2 mantem os dados e passa a recusar ciclo fechado repetido`() {
        ajudante.createDatabase("migracao.db", 1).use { banco ->
            banco.execSQL("INSERT INTO categoria (id, nome, icone) VALUES (5, 'Outros', '📦')")
            banco.execSQL("INSERT INTO gasto (id, descricao, valorCentavos, data, categoriaId, criadoEm) VALUES (1, 'Café', 500, '2026-10-05', 5, 0)")
            banco.execSQL(
                "INSERT INTO ciclo_fechado (id, inicio, fim, totalEntradas, totalFixas, totalReserva, totalGastos, reservaInvadidaCentavos) " +
                    "VALUES (1, '2026-09-01', '2026-09-30', 300000, 120000, 30000, 1000, 0)",
            )
        }
        val banco = ajudante.runMigrationsAndValidate("migracao.db", 2, true)
        banco.query("SELECT valorCentavos FROM gasto").use { it.moveToFirst(); assertEquals(500L, it.getLong(0)) }
        banco.query("SELECT COUNT(*) FROM ciclo_fechado").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        assertThrows(Exception::class.java) {
            banco.execSQL(
                "INSERT INTO ciclo_fechado (inicio, fim, totalEntradas, totalFixas, totalReserva, totalGastos, reservaInvadidaCentavos) " +
                    "VALUES ('2026-09-01', '2026-09-30', 0, 0, 0, 0, 0)",
            )
        }
    }
}
