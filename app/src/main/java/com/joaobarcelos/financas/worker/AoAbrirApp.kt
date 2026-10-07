package com.joaobarcelos.financas.worker

import android.util.Log
import com.joaobarcelos.financas.domain.repository.ConfiguracoesRepository
import com.joaobarcelos.financas.domain.usecase.Alertas
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

/**
 * Ao abrir o app: agenda a rotina diária e fecha os ciclos que terminaram (RN05). Agenda primeiro e
 * protege o fechamento, para que um erro nele não feche o app nem deixe a rotina sem agendar
 * (QA Etapa 6, M1).
 */
suspend fun aoAbrirApp(alertas: Alertas, configuracoes: ConfiguracoesRepository, relogio: Clock, agendar: (LocalTime) -> Unit) {
    runCatching { agendar(configuracoes.configuracoes().first().horaResumo) }
        .onFailure { Log.e("AoAbrirApp", "Falha ao agendar a rotina diária", it) }
    runCatching { alertas.fecharCiclos(LocalDate.now(relogio)) }
        .onFailure { Log.e("AoAbrirApp", "Falha ao fechar os ciclos", it) }
}
