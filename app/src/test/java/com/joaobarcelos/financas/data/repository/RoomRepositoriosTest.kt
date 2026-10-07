package com.joaobarcelos.financas.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.joaobarcelos.financas.data.local.FinancasDatabase
import com.joaobarcelos.financas.domain.calculadora.Resumo
import com.joaobarcelos.financas.domain.model.Configuracoes
import com.joaobarcelos.financas.domain.repository.ConfiguracoesRepository
import com.joaobarcelos.financas.domain.usecase.Alertas
import kotlinx.coroutines.flow.flowOf
import com.joaobarcelos.financas.domain.calculadora.cicloDe
import com.joaobarcelos.financas.domain.model.CicloFechado
import com.joaobarcelos.financas.domain.model.ContaFixa
import com.joaobarcelos.financas.domain.model.Entrada
import com.joaobarcelos.financas.domain.model.Gasto
import com.joaobarcelos.financas.domain.model.MetaReserva
import com.joaobarcelos.financas.domain.model.TipoEntrada
import com.joaobarcelos.financas.domain.model.TipoMeta
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class RoomRepositoriosTest {
    private val banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FinancasDatabase::class.java)
        .addCallback(FinancasDatabase.CriarCategoriasPadrao)
        .allowMainThreadQueries()
        .build()
    private var agora = Instant.parse("2026-10-05T12:00:00Z")
    private val relogio = object : Clock() {
        override fun instant() = agora
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId) = this
    }
    private val orcamento = RoomOrcamentoRepository(banco.dao(), relogio)
    private val historico = RoomHistoricoRepository(banco.dao())
    private val outubro = cicloDe(LocalDate.of(2026, 10, 1), 1)

    // Cenário base do briefing, com todos os campos preenchidos
    private val salario = Entrada(300_000, TipoEntrada.RECORRENTE, LocalDate.of(2026, 10, 1), descricao = "Salário")
    private val aluguel = ContaFixa(100_000, LocalDate.of(2026, 10, 1), descricao = "Aluguel", diaVencimento = 5)
    private val celular = ContaFixa(20_000, LocalDate.of(2026, 10, 1), duracaoMeses = 3, descricao = "Celular", diaVencimento = 10)
    private val reserva = MetaReserva(TipoMeta.PERCENTUAL, 1000, nome = "Reserva de segurança")

    private suspend fun salvarCenarioBase() {
        orcamento.salvar(salario)
        orcamento.salvar(aluguel)
        orcamento.salvar(celular)
        orcamento.salvar(reserva)
    }

    @After
    fun fechar() = banco.close()

    @Test
    fun `cenario base salvo e lido do banco da o disponivel do CA01`() = runTest {
        salvarCenarioBase()
        assertEquals(150_000, orcamento.orcamento().first().resumo(outubro).disponivel)
    }

    @Test
    fun `todos os campos voltam iguais do banco`() = runTest {
        val entrada = salario.copy(dataFim = LocalDate.of(2027, 3, 31))
        val conta = celular.copy(encerradaEm = LocalDate.of(2026, 11, 15))
        val meta = MetaReserva(TipoMeta.VALOR, 50_000, ativa = false, nome = "Investimento")
        val gasto = Gasto(1_250, LocalDate.of(2026, 10, 5), descricao = "Padaria", categoriaId = 1)
        val ids = listOf(orcamento.salvar(entrada), orcamento.salvar(conta), orcamento.salvar(meta), orcamento.salvar(gasto))
        val lido = orcamento.orcamento().first()
        assertEquals(listOf(entrada.copy(id = ids[0])), lido.entradas)
        assertEquals(listOf(conta.copy(id = ids[1])), lido.contas)
        assertEquals(listOf(meta.copy(id = ids[2])), lido.metas)
        assertEquals(listOf(gasto.copy(id = ids[3])), lido.gastos)
    }

    @Test
    fun `salvar com id existente atualiza e excluir remove`() = runTest {
        val id = orcamento.salvar(aluguel)
        assertEquals(id, orcamento.salvar(aluguel.copy(id = id, valorCentavos = 110_000)))
        assertEquals(listOf(110_000L), orcamento.orcamento().first().contas.map { it.valorCentavos })
        orcamento.excluir(aluguel.copy(id = id))
        assertTrue(orcamento.orcamento().first().contas.isEmpty())
    }

    @Test
    fun `editar um gasto mantem a ordem de criacao`() = runTest {
        val dia = LocalDate.of(2026, 10, 5)
        val cafe = orcamento.salvar(Gasto(500, dia, descricao = "café"))
        agora = agora.plusSeconds(60)
        orcamento.salvar(Gasto(3_000, dia, descricao = "almoço"))
        agora = agora.plusSeconds(60)
        orcamento.salvar(Gasto(600, dia, descricao = "café com pão", id = cafe))
        assertEquals(listOf("almoço", "café com pão"), orcamento.orcamento().first().gastos.map { it.descricao })
    }

    @Test
    fun `orcamento avisa sozinho quando um gasto e salvo`() = runTest {
        salvarCenarioBase()
        orcamento.orcamento().test {
            assertEquals(150_000, awaitItem().resumo(outubro).disponivel)
            orcamento.salvar(Gasto(160_000, LocalDate.of(2026, 10, 10)))
            assertEquals(10_000, awaitItem().resumo(outubro).reservaInvadida)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `RN05 editar a conta depois de fechar o ciclo nao muda o ciclo fechado`() = runTest {
        salvarCenarioBase()
        val resumoOutubro = orcamento.orcamento().first().resumo(outubro)
        historico.salvar(CicloFechado(outubro, resumoOutubro))
        val aluguelSalvo = orcamento.orcamento().first().contas.single { it.descricao == "Aluguel" }
        orcamento.salvar(aluguelSalvo.copy(valorCentavos = 110_000))

        val fechado = historico.ciclosFechados().first().single()
        assertEquals(outubro, fechado.ciclo)
        assertEquals(120_000, fechado.resumo.fixas)
        assertEquals(130_000, orcamento.orcamento().first().resumo(outubro).fixas)
    }

    @Test
    fun `ciclo fechado mostra a reserva invadida gravada e nao a recalculada`() = runTest {
        // QA Etapa 3, B1: se a regra da RN09 mudar, o retrato antigo não muda
        val resumo = Resumo(300_000, 120_000, 30_000, 160_000)
        historico.salvar(CicloFechado(outubro, resumo, reservaInvadida = 7_777))
        assertEquals(10_000, resumo.reservaInvadida)
        assertEquals(7_777, historico.ciclosFechados().first().single().reservaInvadida)
    }

    @Test
    fun `o banco recusa fechar o mesmo ciclo duas vezes`() = runTest {
        salvarCenarioBase()
        val resumo = orcamento.orcamento().first().resumo(outubro)
        assertTrue(historico.salvar(CicloFechado(outubro, resumo)) > 0)
        assertEquals(-1L, historico.salvar(CicloFechado(outubro, resumo)))
        assertEquals(1, historico.ciclosFechados().first().size)
    }

    @Test
    fun `CA11 ciclo fechado de outubro continua com 1000 reais de aluguel depois da edicao no banco`() = runTest {
        // Fechamento pelo caso de uso, com o banco de verdade
        salvarCenarioBase()
        orcamento.salvar(Gasto(1_000, LocalDate.of(2026, 10, 10)))
        val configuracoes = object : ConfiguracoesRepository {
            override fun configuracoes() = flowOf(Configuracoes())
            override suspend fun atualizar(mudanca: (Configuracoes) -> Configuracoes) {}
        }
        val alertas = Alertas(orcamento, configuracoes, historico) {}
        alertas.fecharCiclos(LocalDate.of(2026, 11, 1))
        // aluguel editado para 1.100,00 em novembro
        val aluguelSalvo = orcamento.orcamento().first().contas.single { it.descricao == "Aluguel" }
        orcamento.salvar(aluguelSalvo.copy(valorCentavos = 110_000))

        val fechado = historico.ciclosFechados().first().single()
        assertEquals(outubro, fechado.ciclo)
        assertEquals(120_000, fechado.resumo.fixas) // aluguel 1.000 + celular 200
        assertEquals(130_000, orcamento.orcamento().first().resumo(cicloDe(LocalDate.of(2026, 11, 1), 1)).fixas)
    }

    @Test
    fun `alerta registrado nao se repete no mesmo periodo`() = runTest {
        assertTrue(historico.registrarAlerta("A1", "2026-10-05", agora))
        assertFalse(historico.registrarAlerta("A1", "2026-10-05", agora))
        assertTrue(historico.registrarAlerta("A1", "2026-10-12", agora))
    }
}
