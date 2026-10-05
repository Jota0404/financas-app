package com.joaobarcelos.financas.data.local.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.joaobarcelos.financas.data.local.FinancasDatabase
import com.joaobarcelos.financas.data.local.entity.EntradaEntity
import com.joaobarcelos.financas.data.local.entity.GastoEntity
import com.joaobarcelos.financas.data.local.entity.RegistroAlertaEntity
import com.joaobarcelos.financas.domain.model.Categoria
import com.joaobarcelos.financas.domain.model.TipoEntrada
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class FinancasDaoTest {
    private val banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FinancasDatabase::class.java)
        .addCallback(FinancasDatabase.CriarCategoriasPadrao)
        .allowMainThreadQueries()
        .build()
    private val dao = banco.dao()
    private val agora = Instant.parse("2026-10-05T12:00:00Z")

    @After
    fun fechar() = banco.close()

    @Test
    fun `banco novo ja vem com as 5 categorias padrao e Outros no id 5`() = runTest {
        val categorias = dao.categorias().first()
        assertEquals(listOf("Alimentação", "Transporte", "Lazer", "Saúde", "Outros"), categorias.map { it.nome })
        assertEquals("Outros", categorias.single { it.id == Categoria.OUTROS }.nome)
    }

    @Test
    fun `gasto com categoria que nao existe e recusado`() = runTest {
        assertThrows(SQLiteConstraintException::class.java) {
            kotlinx.coroutines.runBlocking { dao.inserir(GastoEntity(0, "", 1_000, LocalDate.of(2026, 10, 5), 99, agora)) }
        }
    }

    @Test
    fun `upsert cria com id novo e atualiza sem duplicar`() = runTest {
        val id = dao.salvar(EntradaEntity(0, "Salário", 300_000, TipoEntrada.RECORRENTE, LocalDate.of(2026, 10, 1), null))
        assertEquals(-1L, dao.salvar(EntradaEntity(id, "Salário", 310_000, TipoEntrada.RECORRENTE, LocalDate.of(2026, 10, 1), null)))
        assertEquals(listOf(310_000L), dao.entradas().first().map { it.valorCentavos })
    }

    @Test
    fun `gastos vem do dia mais recente e na ordem em que foram criados`() = runTest {
        val dia5 = LocalDate.of(2026, 10, 5)
        dao.inserir(GastoEntity(0, "café", 500, dia5, Categoria.OUTROS, agora))
        dao.inserir(GastoEntity(0, "ônibus", 450, LocalDate.of(2026, 10, 4), Categoria.OUTROS, agora.plusSeconds(60)))
        dao.inserir(GastoEntity(0, "almoço", 3_000, dia5, Categoria.OUTROS, agora.plusSeconds(120)))
        assertEquals(listOf("almoço", "café", "ônibus"), dao.gastos().first().map { it.descricao })
    }

    @Test
    fun `mesmo alerta no mesmo periodo e registrado uma vez so`() = runTest {
        assertEquals(1L, dao.registrar(RegistroAlertaEntity(0, "A2", "2026-10-05", agora)))
        assertEquals(-1L, dao.registrar(RegistroAlertaEntity(0, "A2", "2026-10-05", agora.plusSeconds(60))))
        assertEquals(3L, dao.registrar(RegistroAlertaEntity(0, "A2", "2026-10-12", agora)))
        assertEquals(4L, dao.registrar(RegistroAlertaEntity(0, "A3", "2026-10-05", agora)))
    }
}
