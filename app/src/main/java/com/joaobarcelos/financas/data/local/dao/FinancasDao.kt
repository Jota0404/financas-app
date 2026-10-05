package com.joaobarcelos.financas.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.joaobarcelos.financas.data.local.entity.CategoriaEntity
import com.joaobarcelos.financas.data.local.entity.CicloFechadoEntity
import com.joaobarcelos.financas.data.local.entity.ContaFixaEntity
import com.joaobarcelos.financas.data.local.entity.EntradaEntity
import com.joaobarcelos.financas.data.local.entity.GastoEntity
import com.joaobarcelos.financas.data.local.entity.MetaReservaEntity
import com.joaobarcelos.financas.data.local.entity.RegistroAlertaEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Consultas do banco. As que devolvem Flow emitem de novo a cada mudança na tabela. */
@Dao
interface FinancasDao {
    @Query("SELECT * FROM entrada ORDER BY dataInicio")
    fun entradas(): Flow<List<EntradaEntity>>

    @Query("SELECT * FROM conta_fixa ORDER BY descricao")
    fun contas(): Flow<List<ContaFixaEntity>>

    @Query("SELECT * FROM meta_reserva ORDER BY nome")
    fun metas(): Flow<List<MetaReservaEntity>>

    // ponytail: carrega os gastos de todos os ciclos; filtrar por data na consulta se o histórico pesar
    @Query("SELECT * FROM gasto ORDER BY data DESC, criadoEm DESC")
    fun gastos(): Flow<List<GastoEntity>>

    @Query("SELECT * FROM categoria ORDER BY id")
    fun categorias(): Flow<List<CategoriaEntity>>

    @Query("SELECT * FROM ciclo_fechado ORDER BY inicio DESC")
    fun ciclosFechados(): Flow<List<CicloFechadoEntity>>

    /** Upsert: cria quando o id é 0 e atualiza nos outros casos. Devolve -1 quando atualiza. */
    @Upsert
    suspend fun salvar(entrada: EntradaEntity): Long

    @Upsert
    suspend fun salvar(conta: ContaFixaEntity): Long

    @Upsert
    suspend fun salvar(meta: MetaReservaEntity): Long

    @Insert
    suspend fun inserir(gasto: GastoEntity): Long

    /** Atualiza o gasto sem mexer no criadoEm. */
    @Query(
        "UPDATE gasto SET descricao = :descricao, valorCentavos = :valorCentavos, data = :data, " +
            "categoriaId = :categoriaId WHERE id = :id",
    )
    suspend fun atualizarGasto(id: Long, descricao: String, valorCentavos: Long, data: LocalDate, categoriaId: Long)

    @Insert
    suspend fun inserir(cicloFechado: CicloFechadoEntity): Long

    /** Devolve -1 quando o alerta já está registrado no mesmo período. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun registrar(alerta: RegistroAlertaEntity): Long

    @Query("DELETE FROM entrada WHERE id = :id")
    suspend fun excluirEntrada(id: Long)

    @Query("DELETE FROM conta_fixa WHERE id = :id")
    suspend fun excluirConta(id: Long)

    @Query("DELETE FROM meta_reserva WHERE id = :id")
    suspend fun excluirMeta(id: Long)

    @Query("DELETE FROM gasto WHERE id = :id")
    suspend fun excluirGasto(id: Long)
}
