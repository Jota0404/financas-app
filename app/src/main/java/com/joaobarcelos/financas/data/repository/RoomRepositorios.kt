package com.joaobarcelos.financas.data.repository

import com.joaobarcelos.financas.data.local.dao.FinancasDao
import com.joaobarcelos.financas.data.local.entity.RegistroAlertaEntity
import com.joaobarcelos.financas.data.local.entity.paraDominio
import com.joaobarcelos.financas.data.local.entity.paraEntidade
import com.joaobarcelos.financas.domain.calculadora.Orcamento
import com.joaobarcelos.financas.domain.model.CicloFechado
import com.joaobarcelos.financas.domain.model.ContaFixa
import com.joaobarcelos.financas.domain.model.Entrada
import com.joaobarcelos.financas.domain.model.Gasto
import com.joaobarcelos.financas.domain.model.MetaReserva
import com.joaobarcelos.financas.domain.repository.HistoricoRepository
import com.joaobarcelos.financas.domain.repository.OrcamentoRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/** O @Upsert devolve -1 quando atualiza; nesse caso, o id é o que já existia. */
private fun idSalvo(resultado: Long, id: Long) = if (resultado == -1L) id else resultado

class RoomOrcamentoRepository @Inject constructor(
    private val dao: FinancasDao,
    private val relogio: Clock,
) : OrcamentoRepository {
    override fun orcamento() = combine(dao.entradas(), dao.contas(), dao.metas(), dao.gastos()) { e, c, m, g ->
        Orcamento(e.map { it.paraDominio() }, c.map { it.paraDominio() }, m.map { it.paraDominio() }, g.map { it.paraDominio() })
    }

    override fun categorias() = dao.categorias().map { lista -> lista.map { it.paraDominio() } }

    override suspend fun salvar(entrada: Entrada) = idSalvo(dao.salvar(entrada.paraEntidade()), entrada.id)
    override suspend fun salvar(conta: ContaFixa) = idSalvo(dao.salvar(conta.paraEntidade()), conta.id)
    override suspend fun salvar(meta: MetaReserva) = idSalvo(dao.salvar(meta.paraEntidade()), meta.id)

    override suspend fun salvar(gasto: Gasto): Long {
        if (gasto.id == 0L) return dao.inserir(gasto.paraEntidade(criadoEm = relogio.instant()))
        dao.atualizarGasto(gasto.id, gasto.descricao, gasto.valorCentavos, gasto.data, gasto.categoriaId)
        return gasto.id
    }

    override suspend fun excluir(entrada: Entrada) = dao.excluirEntrada(entrada.id)
    override suspend fun excluir(conta: ContaFixa) = dao.excluirConta(conta.id)
    override suspend fun excluir(meta: MetaReserva) = dao.excluirMeta(meta.id)
    override suspend fun excluir(gasto: Gasto) = dao.excluirGasto(gasto.id)
}

class RoomHistoricoRepository @Inject constructor(private val dao: FinancasDao) : HistoricoRepository {
    override fun ciclosFechados() = dao.ciclosFechados().map { lista -> lista.map { it.paraDominio() } }

    override suspend fun salvar(cicloFechado: CicloFechado) = dao.inserir(cicloFechado.paraEntidade())

    override suspend fun registrarAlerta(codigo: String, periodoRef: String, disparadoEm: Instant) =
        dao.registrar(RegistroAlertaEntity(0, codigo, periodoRef, disparadoEm)) != -1L
}
