package com.joaobarcelos.financas.worker

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.joaobarcelos.financas.data.datastore.DataStoreConfiguracoesRepository
import com.joaobarcelos.financas.data.local.FinancasDatabase
import com.joaobarcelos.financas.data.repository.RoomHistoricoRepository
import com.joaobarcelos.financas.data.repository.RoomOrcamentoRepository
import com.joaobarcelos.financas.domain.calculadora.Orcamento
import com.joaobarcelos.financas.domain.model.ContaFixa
import com.joaobarcelos.financas.domain.repository.OrcamentoRepository
import com.joaobarcelos.financas.domain.model.Entrada
import com.joaobarcelos.financas.domain.model.Gasto
import com.joaobarcelos.financas.domain.model.MetaReserva
import com.joaobarcelos.financas.domain.model.TipoEntrada
import com.joaobarcelos.financas.domain.model.TipoMeta
import com.joaobarcelos.financas.domain.usecase.Alerta
import com.joaobarcelos.financas.domain.usecase.Alertas
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Notificações e rotina diária com o banco de verdade, em segunda, 02/11/2026, às 08:00. */
@RunWith(RobolectricTestRunner::class)
class AlertasAndroidTest {
    @get:Rule val pasta = TemporaryFolder()

    private val contexto: Context = ApplicationProvider.getApplicationContext()
    private val banco = Room.inMemoryDatabaseBuilder(contexto, FinancasDatabase::class.java)
        .addCallback(FinancasDatabase.CriarCategoriasPadrao)
        .allowMainThreadQueries()
        .build()
    private val escopo = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val relogio = Clock.fixed(LocalDate.of(2026, 11, 2).atTime(8, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val orcamento = RoomOrcamentoRepository(banco.dao(), relogio)
    private val historico = RoomHistoricoRepository(banco.dao())
    private val configuracoes = DataStoreConfiguracoesRepository(
        DataStoreConfiguracoesRepository.criarDataStore(escopo) { File(pasta.root, "c.preferences_pb") },
    )
    private val notificacoes = NotificacoesAndroid(contexto)
    private val alertas = Alertas(orcamento, configuracoes, historico, notificacoes)
    private val gerenciador = contexto.getSystemService(NotificationManager::class.java)

    init {
        shadowOf(contexto as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificacoesAndroid.criarCanal(contexto)
    }

    @After
    fun fechar() {
        escopo.cancel()
        banco.close()
    }

    private fun notificacoes() = shadowOf(gerenciador).allNotifications.map {
        it.extras.getString("android.title") to it.extras.getCharSequence("android.text").toString()
    }

    private fun cenarioBaseComGastoDeOutubro() = runBlocking {
        orcamento.salvar(Entrada(300_000, TipoEntrada.RECORRENTE, LocalDate.of(2026, 10, 1), descricao = "Salário"))
        orcamento.salvar(ContaFixa(100_000, LocalDate.of(2026, 10, 1), descricao = "Aluguel", diaVencimento = 5))
        orcamento.salvar(ContaFixa(20_000, LocalDate.of(2026, 10, 1), duracaoMeses = 3, descricao = "Celular", diaVencimento = 10))
        orcamento.salvar(MetaReserva(TipoMeta.PERCENTUAL, 1000, nome = "Reserva"))
        orcamento.salvar(Gasto(50_000, LocalDate.of(2026, 10, 10)))
    }

    @Test
    fun `o alerta aparece como notificacao com o texto do dominio`() {
        notificacoes.avisar(Alerta.ReservaInvadida(gastoId = 1, reservaInvadida = 10_000))
        assertEquals(
            listOf("Reserva invadida" to "Um gasto invadiu a reserva. A reserva deste ciclo está invadida em R$ 100,00."),
            notificacoes(),
        )
    }

    @Test
    fun `sem a permissao de notificacao nada aparece e o app nao fecha`() {
        shadowOf(contexto as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notificacoes.avisar(Alerta.ReservaInvadida(gastoId = 1, reservaInvadida = 10_000))
        assertTrue(notificacoes().isEmpty())
    }

    @Test
    fun `rotina diaria fecha outubro manda o A5 e o A1 e agenda a proxima`() {
        cenarioBaseComGastoDeOutubro()
        WorkManagerTestInitHelper.initializeTestWorkManager(contexto)
        val rotina = TestListenableWorkerBuilder<RotinaDiariaWorker>(contexto)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    RotinaDiariaWorker(appContext, workerParameters, alertas, configuracoes, relogio)
            })
            .build()

        assertEquals(ListenableWorker.Result.success(), runBlocking { rotina.doWork() })

        assertEquals(1, runBlocking { historico.ciclosFechados().first().size })
        val titulos = notificacoes().map { it.first }
        assertTrue(titulos.toString(), "Ciclo de 01/10 a 31/10 fechado" in titulos)
        assertTrue(titulos.toString(), "Resumo da semana" in titulos)
        val proxima = WorkManager.getInstance(contexto).getWorkInfosForUniqueWork(ROTINA_DIARIA).get()
        assertTrue(proxima.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED })
    }

    @Test
    fun `erro no fechamento ao abrir o app nao fecha o app e a rotina e agendada`() = runBlocking {
        // QA Etapa 6, M1: um banco que falha ao ler os dados
        val quebrado = object : OrcamentoRepository by orcamento {
            override fun orcamento() = flow<Orcamento> {
                throw IllegalStateException("dado inesperado no banco")
            }
        }
        var agendada: LocalTime? = null
        aoAbrirApp(Alertas(quebrado, configuracoes, historico, notificacoes), configuracoes, relogio) { agendada = it }
        assertEquals(LocalTime.of(8, 0), agendada)
    }
}
