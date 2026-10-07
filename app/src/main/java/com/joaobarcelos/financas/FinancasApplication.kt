package com.joaobarcelos.financas

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.joaobarcelos.financas.domain.repository.ConfiguracoesRepository
import com.joaobarcelos.financas.domain.usecase.Alertas
import com.joaobarcelos.financas.worker.NotificacoesAndroid
import com.joaobarcelos.financas.worker.agendarRotinaDiaria
import com.joaobarcelos.financas.worker.aoAbrirApp
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

@HiltAndroidApp
class FinancasApplication : Application(), Configuration.Provider {
    @Inject lateinit var fabricaDeWorkers: HiltWorkerFactory
    @Inject lateinit var alertas: Alertas
    @Inject lateinit var configuracoes: ConfiguracoesRepository
    @Inject lateinit var relogio: Clock

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(fabricaDeWorkers).build()

    override fun onCreate() {
        super.onCreate()
        NotificacoesAndroid.criarCanal(this)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            aoAbrirApp(alertas, configuracoes, relogio) { agendarRotinaDiaria(this@FinancasApplication, it, relogio) }
        }
    }
}
