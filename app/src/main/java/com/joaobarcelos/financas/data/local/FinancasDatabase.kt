package com.joaobarcelos.financas.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.joaobarcelos.financas.data.local.dao.FinancasDao
import com.joaobarcelos.financas.data.local.entity.CategoriaEntity
import com.joaobarcelos.financas.data.local.entity.CicloFechadoEntity
import com.joaobarcelos.financas.data.local.entity.ContaFixaEntity
import com.joaobarcelos.financas.data.local.entity.EntradaEntity
import com.joaobarcelos.financas.data.local.entity.GastoEntity
import com.joaobarcelos.financas.data.local.entity.MetaReservaEntity
import com.joaobarcelos.financas.data.local.entity.RegistroAlertaEntity
import com.joaobarcelos.financas.domain.model.Categoria
import java.time.Instant
import java.time.LocalDate

@Database(
    entities = [
        EntradaEntity::class, ContaFixaEntity::class, CategoriaEntity::class, GastoEntity::class,
        MetaReservaEntity::class, CicloFechadoEntity::class, RegistroAlertaEntity::class,
    ],
    version = 1,
)
@TypeConverters(Conversores::class)
abstract class FinancasDatabase : RoomDatabase() {
    abstract fun dao(): FinancasDao

    /** Na primeira abertura do banco, cria as categorias padrão (Alimentação ... Outros). */
    object CriarCategoriasPadrao : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            Categoria.PADRAO.forEach {
                db.execSQL("INSERT INTO categoria (id, nome, icone) VALUES (?, ?, ?)", arrayOf<Any>(it.id, it.nome, it.icone))
            }
        }
    }
}

/** Datas como texto ISO (2026-10-05), legível no banco; instantes em milissegundos. */
class Conversores {
    @TypeConverter fun data(texto: String?): LocalDate? = texto?.let(LocalDate::parse)
    @TypeConverter fun texto(data: LocalDate?): String? = data?.toString()
    @TypeConverter fun instante(milis: Long?): Instant? = milis?.let(Instant::ofEpochMilli)
    @TypeConverter fun milis(instante: Instant?): Long? = instante?.toEpochMilli()
}
