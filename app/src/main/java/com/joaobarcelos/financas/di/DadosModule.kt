package com.joaobarcelos.financas.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.joaobarcelos.financas.data.datastore.DataStoreConfiguracoesRepository
import com.joaobarcelos.financas.data.local.FinancasDatabase
import com.joaobarcelos.financas.data.repository.RoomHistoricoRepository
import com.joaobarcelos.financas.data.repository.RoomOrcamentoRepository
import com.joaobarcelos.financas.domain.repository.ConfiguracoesRepository
import com.joaobarcelos.financas.domain.repository.HistoricoRepository
import com.joaobarcelos.financas.domain.repository.OrcamentoRepository
import com.joaobarcelos.financas.domain.usecase.Cadastros
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/** Liga as interfaces do domínio às implementações com Room e DataStore. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DadosModule {
    @Binds abstract fun orcamento(repositorio: RoomOrcamentoRepository): OrcamentoRepository
    @Binds abstract fun historico(repositorio: RoomHistoricoRepository): HistoricoRepository
    @Binds abstract fun configuracoes(repositorio: DataStoreConfiguracoesRepository): ConfiguracoesRepository

    companion object {
        @Provides
        @Singleton
        fun banco(@ApplicationContext context: Context): FinancasDatabase =
            Room.databaseBuilder(context, FinancasDatabase::class.java, "financas.db")
                .addCallback(FinancasDatabase.CriarCategoriasPadrao)
                .build()

        @Provides
        fun dao(banco: FinancasDatabase) = banco.dao()

        @Provides
        @Singleton
        fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            DataStoreConfiguracoesRepository.criarDataStore { context.preferencesDataStoreFile("configuracoes") }

        @Provides
        fun relogio(): Clock = Clock.systemDefaultZone()

        @Provides
        fun cadastros(orcamento: OrcamentoRepository, configuracoes: ConfiguracoesRepository) =
            Cadastros(orcamento, configuracoes)
    }
}
