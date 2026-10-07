package com.joaobarcelos.financas.domain

import com.joaobarcelos.financas.domain.calculadora.Orcamento
import com.joaobarcelos.financas.domain.model.Categoria
import com.joaobarcelos.financas.domain.model.CicloFechado
import com.joaobarcelos.financas.domain.model.Configuracoes
import com.joaobarcelos.financas.domain.model.ContaFixa
import com.joaobarcelos.financas.domain.model.Entrada
import com.joaobarcelos.financas.domain.model.Gasto
import com.joaobarcelos.financas.domain.model.MetaReserva
import com.joaobarcelos.financas.domain.repository.ConfiguracoesRepository
import com.joaobarcelos.financas.domain.repository.HistoricoRepository
import com.joaobarcelos.financas.domain.repository.OrcamentoRepository
import com.joaobarcelos.financas.domain.usecase.Alerta
import com.joaobarcelos.financas.domain.usecase.Alertas
import com.joaobarcelos.financas.domain.usecase.Cadastros
import com.joaobarcelos.financas.domain.usecase.Notificador
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant

// Repositórios em memória para os testes do domínio, com o mesmo comportamento dos de verdade.

class OrcamentoEmMemoria(inicial: Orcamento = Orcamento()) : OrcamentoRepository {
    val dados = MutableStateFlow(inicial)
    private var ultimoId = 100L

    /** Troca o item de mesmo id ou acrescenta com um id novo, como o banco. */
    private fun <T> List<T>.salvo(item: T, id: Long, comId: (Long) -> T): Pair<List<T>, Long> {
        val novoId = if (id == 0L) ++ultimoId else id
        return (filterNot { idDe(it) == novoId } + comId(novoId)) to novoId
    }
    private fun idDe(item: Any?) = when (item) {
        is Entrada -> item.id; is ContaFixa -> item.id; is MetaReserva -> item.id; is Gasto -> item.id; else -> -1
    }

    override fun orcamento() = dados
    override fun categorias() = flowOf(Categoria.PADRAO)
    override suspend fun salvar(entrada: Entrada) =
        dados.value.entradas.salvo(entrada, entrada.id) { entrada.copy(id = it) }.let { (l, id) -> dados.value = dados.value.copy(entradas = l); id }
    override suspend fun salvar(conta: ContaFixa) =
        dados.value.contas.salvo(conta, conta.id) { conta.copy(id = it) }.let { (l, id) -> dados.value = dados.value.copy(contas = l); id }
    override suspend fun salvar(meta: MetaReserva) =
        dados.value.metas.salvo(meta, meta.id) { meta.copy(id = it) }.let { (l, id) -> dados.value = dados.value.copy(metas = l); id }
    override suspend fun salvar(gasto: Gasto) =
        dados.value.gastos.salvo(gasto, gasto.id) { gasto.copy(id = it) }.let { (l, id) -> dados.value = dados.value.copy(gastos = l); id }
    override suspend fun excluir(entrada: Entrada) { dados.value = dados.value.copy(entradas = dados.value.entradas.filterNot { it.id == entrada.id }) }
    override suspend fun excluir(conta: ContaFixa) { dados.value = dados.value.copy(contas = dados.value.contas.filterNot { it.id == conta.id }) }
    override suspend fun excluir(meta: MetaReserva) { dados.value = dados.value.copy(metas = dados.value.metas.filterNot { it.id == meta.id }) }
    override suspend fun excluir(gasto: Gasto) { dados.value = dados.value.copy(gastos = dados.value.gastos.filterNot { it.id == gasto.id }) }
}

class ConfiguracoesEmMemoria(inicial: Configuracoes = Configuracoes()) : ConfiguracoesRepository {
    val dados = MutableStateFlow(inicial)
    override fun configuracoes() = dados
    override suspend fun atualizar(mudanca: (Configuracoes) -> Configuracoes) { dados.value = mudanca(dados.value) }
}

/** Como o banco: um ciclo só é fechado uma vez, e um alerta só é registrado uma vez por período. */
class HistoricoEmMemoria : HistoricoRepository {
    val fechados = MutableStateFlow(emptyList<CicloFechado>())
    val alertas = mutableSetOf<Pair<String, String>>()
    override fun ciclosFechados() = fechados
    override suspend fun salvar(cicloFechado: CicloFechado): Long {
        if (fechados.value.any { it.ciclo.inicio == cicloFechado.ciclo.inicio }) return -1
        fechados.value = fechados.value + cicloFechado
        return fechados.value.size.toLong()
    }
    override suspend fun registrarAlerta(codigo: String, periodoRef: String, disparadoEm: Instant) = alertas.add(codigo to periodoRef)
}

/** Guarda os avisos que chegariam ao usuário. */
class NotificadorDeTeste : Notificador {
    val avisos = mutableListOf<Alerta>()
    override fun avisar(alerta: Alerta) { avisos += alerta }
}

/** Tudo o que os casos de uso precisam, em memória. */
class Ambiente(orcamento: Orcamento = Orcamento(), configuracoes: Configuracoes = Configuracoes()) {
    val orcamento = OrcamentoEmMemoria(orcamento)
    val configuracoes = ConfiguracoesEmMemoria(configuracoes)
    val historico = HistoricoEmMemoria()
    val notificador = NotificadorDeTeste()
    val alertas = Alertas(this.orcamento, this.configuracoes, historico, notificador)
    val cadastros = Cadastros(this.orcamento, this.configuracoes, alertas)
}
