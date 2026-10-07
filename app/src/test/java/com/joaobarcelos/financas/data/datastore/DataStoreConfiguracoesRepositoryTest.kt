package com.joaobarcelos.financas.data.datastore

import com.joaobarcelos.financas.domain.calculadora.cicloAtual
import com.joaobarcelos.financas.domain.calculadora.comDiaPagamento
import com.joaobarcelos.financas.domain.model.Ciclo
import com.joaobarcelos.financas.domain.model.Configuracoes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

class DataStoreConfiguracoesRepositoryTest {
    @get:Rule
    val pasta = TemporaryFolder()

    private val arquivo by lazy { File(pasta.root, "configuracoes.preferences_pb") }
    private val escopos = mutableListOf<CoroutineScope>()

    /** Abre o arquivo como o app faria ao iniciar. Só um DataStore pode ficar aberto por arquivo. */
    private suspend fun abrir(): DataStoreConfiguracoesRepository {
        // espera o anterior fechar de vez; só cancelar deixa o arquivo preso por um instante
        escopos.forEach { it.coroutineContext[Job]!!.cancelAndJoin() }
        val escopo = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { escopos += it }
        return DataStoreConfiguracoesRepository(DataStoreConfiguracoesRepository.criarDataStore(escopo) { arquivo })
    }

    @After
    fun fechar() = escopos.forEach { it.cancel() }

    @Test
    fun `sem nada gravado valem os padroes do briefing`() = runTest {
        val config = abrir().configuracoes().first()
        assertEquals(Configuracoes(), config)
        assertEquals(1, config.diaPagamento)
        assertEquals(7000, config.percentualAtencao)
        assertEquals(9000, config.percentualCritico)
        assertEquals(LocalTime.of(8, 0), config.horaResumo)
    }

    @Test
    fun `todas as configuracoes sobrevivem a reabrir o app`() = runTest {
        val gravada = Configuracoes(
            diaPagamento = 31,
            cicloIrregular = Ciclo(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 14)),
            limiteSemanalManual = 50_000,
            percentualAtencao = 6000,
            percentualCritico = 8500,
            horaResumo = LocalTime.of(7, 30),
        )
        abrir().atualizar { gravada }
        assertEquals(gravada, abrir().configuracoes().first())
    }

    @Test
    fun `apagar o limite manual volta ao limite automatico`() = runTest {
        abrir().atualizar { it.copy(limiteSemanalManual = 50_000) }
        val repositorio = abrir()
        repositorio.atualizar { it.copy(limiteSemanalManual = null) }
        assertNull(repositorio.configuracoes().first().limiteSemanalManual)
    }

    @Test
    fun `RN01 ciclo irregular depois de mudar o dia fica guardado ate o fim dele`() = runTest {
        // P14: mudar de 1 para 15 em 20/10 deixa o ciclo atual de 01/10 a 14/11
        abrir().atualizar { it.comDiaPagamento(15, hoje = LocalDate.of(2026, 10, 20)).configuracoes }
        val config = abrir().configuracoes().first()
        assertEquals(Ciclo(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 14)), config.cicloAtual(LocalDate.of(2026, 10, 25)))
        assertEquals(Ciclo(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 12, 14)), config.cicloAtual(LocalDate.of(2026, 11, 15)))
    }

    @Test
    fun `arquivo de configuracoes estragado usa os padroes e salva sem fechar o app`() = runTest {
        // bytes que o DataStore não consegue ler (CorruptionException sem a proteção)
        arquivo.writeBytes(byteArrayOf(0x0A, 0x7F, 0x01))
        val repositorio = abrir()
        assertEquals(Configuracoes(), repositorio.configuracoes().first())
        repositorio.atualizar { it.copy(diaPagamento = 15) }
        assertEquals(15, abrir().configuracoes().first().diaPagamento)
    }
}
