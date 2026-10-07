package com.joaobarcelos.financas.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.joaobarcelos.financas.domain.calculadora.Resumo
import com.joaobarcelos.financas.domain.model.Categoria
import com.joaobarcelos.financas.domain.model.Ciclo
import com.joaobarcelos.financas.domain.model.CicloFechado
import com.joaobarcelos.financas.domain.model.ContaFixa
import com.joaobarcelos.financas.domain.model.Entrada
import com.joaobarcelos.financas.domain.model.Gasto
import com.joaobarcelos.financas.domain.model.MetaReserva
import com.joaobarcelos.financas.domain.model.TipoEntrada
import com.joaobarcelos.financas.domain.model.TipoMeta
import java.time.Instant
import java.time.LocalDate

// As sete tabelas do modelo de dados do briefing. Dinheiro em centavos (Long), datas como LocalDate.
// Cada tabela tem a conversão de e para o modelo do domínio logo abaixo dela.

@Entity(tableName = "entrada")
data class EntradaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val descricao: String,
    val valorCentavos: Long,
    val tipo: TipoEntrada,
    val dataInicio: LocalDate,
    val dataFim: LocalDate?,
)

fun EntradaEntity.paraDominio() = Entrada(valorCentavos, tipo, dataInicio, dataFim, descricao, id)
fun Entrada.paraEntidade() = EntradaEntity(id, descricao, valorCentavos, tipo, dataInicio, dataFim)

@Entity(tableName = "conta_fixa")
data class ContaFixaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val descricao: String,
    val valorCentavos: Long,
    val diaVencimento: Int,
    val cicloInicio: LocalDate,
    val duracaoMeses: Int?,
    val encerradaEm: LocalDate?,
)

fun ContaFixaEntity.paraDominio() =
    ContaFixa(valorCentavos, cicloInicio, duracaoMeses, encerradaEm, descricao, diaVencimento, id)
fun ContaFixa.paraEntidade() =
    ContaFixaEntity(id, descricao, valorCentavos, diaVencimento, cicloInicio, duracaoMeses, encerradaEm)

@Entity(tableName = "categoria")
data class CategoriaEntity(@PrimaryKey val id: Long, val nome: String, val icone: String)

fun CategoriaEntity.paraDominio() = Categoria(id, nome, icone)

@Entity(
    tableName = "gasto",
    foreignKeys = [ForeignKey(entity = CategoriaEntity::class, parentColumns = ["id"], childColumns = ["categoriaId"])],
    indices = [Index("categoriaId")],
)
data class GastoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val descricao: String,
    val valorCentavos: Long,
    val data: LocalDate,
    val categoriaId: Long,
    val criadoEm: Instant,
)

fun GastoEntity.paraDominio() = Gasto(valorCentavos, data, descricao, categoriaId, id)
fun Gasto.paraEntidade(criadoEm: Instant) = GastoEntity(id, descricao, valorCentavos, data, categoriaId, criadoEm)

/** [valor] em centavos ou em pontos-base, conforme o [tipo]. */
@Entity(tableName = "meta_reserva")
data class MetaReservaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val nome: String,
    val tipo: TipoMeta,
    val valor: Long,
    val ativa: Boolean,
)

fun MetaReservaEntity.paraDominio() = MetaReserva(tipo, valor, ativa, nome, id)
fun MetaReserva.paraEntidade() = MetaReservaEntity(id, nome, tipo, valor, ativa)

/** O índice único faz o banco recusar o mesmo ciclo fechado duas vezes. */
@Entity(tableName = "ciclo_fechado", indices = [Index("inicio", unique = true)])
data class CicloFechadoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val inicio: LocalDate,
    val fim: LocalDate,
    val totalEntradas: Long,
    val totalFixas: Long,
    val totalReserva: Long,
    val totalGastos: Long,
    val reservaInvadidaCentavos: Long,
)

fun CicloFechadoEntity.paraDominio() =
    CicloFechado(Ciclo(inicio, fim), Resumo(totalEntradas, totalFixas, totalReserva, totalGastos), reservaInvadidaCentavos, id)
fun CicloFechado.paraEntidade() = CicloFechadoEntity(
    id, ciclo.inicio, ciclo.fim, resumo.entradas, resumo.fixas, resumo.reserva, resumo.gastos, reservaInvadida,
)

/** O índice único impede o mesmo alerta duas vezes no mesmo período. */
@Entity(tableName = "registro_alerta", indices = [Index("codigo", "periodoRef", unique = true)])
data class RegistroAlertaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val codigo: String,
    val periodoRef: String,
    val disparadoEm: Instant,
)
