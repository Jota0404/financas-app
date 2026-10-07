package com.joaobarcelos.financas

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.joaobarcelos.financas.data.datastore.DataStoreConfiguracoesRepository
import com.joaobarcelos.financas.data.local.FinancasDatabase
import com.joaobarcelos.financas.di.ArmazenamentoModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import javax.inject.Singleton

/** Nos testes no aparelho, o banco fica em memória e as configurações num arquivo temporário. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ArmazenamentoModule::class])
object ArmazenamentoDeTeste {
    @Provides
    @Singleton
    fun banco(@ApplicationContext context: Context): FinancasDatabase =
        Room.inMemoryDatabaseBuilder(context, FinancasDatabase::class.java)
            .addCallback(FinancasDatabase.CriarCategoriasPadrao)
            .build()

    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        DataStoreConfiguracoesRepository.criarDataStore {
            File.createTempFile("configuracoes", ".preferences_pb", context.cacheDir).also { it.delete() }
        }
}
