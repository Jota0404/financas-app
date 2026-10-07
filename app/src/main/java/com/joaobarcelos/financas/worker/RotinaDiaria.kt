package com.joaobarcelos.financas.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.joaobarcelos.financas.domain.repository.ConfiguracoesRepository
import com.joaobarcelos.financas.domain.usecase.Alertas
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Roda uma vez por dia, por volta do horário do resumo (decisão do dono): fecha os ciclos que
 * terminaram, manda o A5 e o A1, e agenda a próxima vez.
 */
@HiltWorker
class RotinaDiariaWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parametros: WorkerParameters,
    private val alertas: Alertas,
    private val configuracoes: ConfiguracoesRepository,
    private val relogio: Clock,
) : CoroutineWorker(context, parametros) {
    override suspend fun doWork(): Result {
        // Uma falha hoje não pode parar as rotinas dos próximos dias
        runCatching { alertas.rotinaDiaria(LocalDate.now(relogio), relogio.instant()) }
            .onFailure { Log.e("RotinaDiaria", "Falha na rotina diária", it) }
        agendarRotinaDiaria(applicationContext, configuracoes.configuracoes().first().horaResumo, relogio, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}

const val ROTINA_DIARIA = "rotina-diaria"

/**
 * Agenda a rotina para o próximo [horario]. Ao abrir o app (KEEP), não mexe numa rotina já
 * agendada; de dentro da rotina (APPEND_OR_REPLACE), a próxima espera esta terminar.
 */
fun agendarRotinaDiaria(context: Context, horario: LocalTime, relogio: Clock, politica: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
    val agora = LocalDateTime.now(relogio)
    val hoje = agora.toLocalDate().atTime(horario)
    val proxima = if (hoje.isAfter(agora)) hoje else hoje.plusDays(1)
    val pedido = OneTimeWorkRequestBuilder<RotinaDiariaWorker>().setInitialDelay(Duration.between(agora, proxima)).build()
    WorkManager.getInstance(context).enqueueUniqueWork(ROTINA_DIARIA, politica, pedido)
}
