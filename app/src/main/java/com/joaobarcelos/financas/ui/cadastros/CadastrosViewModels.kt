package com.joaobarcelos.financas.ui.cadastros

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaobarcelos.financas.domain.calculadora.Orcamento
import com.joaobarcelos.financas.domain.calculadora.ativaEm
import com.joaobarcelos.financas.domain.calculadora.parcelaEm
import com.joaobarcelos.financas.domain.calculadora.valorNoCiclo
import com.joaobarcelos.financas.domain.model.Ciclo
import com.joaobarcelos.financas.domain.model.ContaFixa
import com.joaobarcelos.financas.domain.model.Entrada
import com.joaobarcelos.financas.domain.model.MetaReserva
import com.joaobarcelos.financas.domain.model.TipoEntrada
import com.joaobarcelos.financas.domain.repository.ConfiguracoesRepository
import com.joaobarcelos.financas.domain.repository.OrcamentoRepository
import com.joaobarcelos.financas.domain.usecase.Cadastros
import com.joaobarcelos.financas.domain.usecase.Decisao
import com.joaobarcelos.financas.domain.usecase.decidirExclusao
import com.joaobarcelos.financas.ui.momento
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Base dos três ViewModels: o ciclo atual e o que foi cadastrado, atualizados a cada mudança. */
abstract class CadastroViewModel<E>(
    protected val cadastros: Cadastros,
    orcamento: OrcamentoRepository,
    configuracoes: ConfiguracoesRepository,
    private val relogio: Clock,
    paraEstado: (ciclo: Ciclo, hoje: LocalDate, dados: Orcamento) -> E,
) : ViewModel() {
    fun hoje(): LocalDate = LocalDate.now(relogio)

    /** Nulo enquanto carrega. Recalcula na virada do dia (QA Etapa 4, B2). */
    val estado: StateFlow<E?> = momento(orcamento, configuracoes, relogio)
        .map { paraEstado(it.ciclo, it.hoje, it.dados) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Termina de salvar mesmo se a tela for refeita no meio (ex.: girar o celular). */
    protected suspend fun <T> ateOFim(acao: suspend () -> T): T = viewModelScope.async { acao() }.await()
}

data class ItemConta(val conta: ContaFixa, val ativa: Boolean, val parcela: Int?, val podeExcluir: Boolean)

data class EstadoContas(val ciclo: Ciclo, val itens: List<ItemConta>)

@HiltViewModel
class ContasViewModel @Inject constructor(
    cadastros: Cadastros,
    orcamento: OrcamentoRepository,
    configuracoes: ConfiguracoesRepository,
    relogio: Clock,
) : CadastroViewModel<EstadoContas>(cadastros, orcamento, configuracoes, relogio, { ciclo, _, dados ->
    EstadoContas(
        ciclo,
        dados.contas
            .map { ItemConta(it, it.ativaEm(ciclo), it.parcelaEm(ciclo), decidirExclusao(it, ciclo) == Decisao.Permitido) }
            .sortedWith(compareByDescending<ItemConta> { it.ativa }.thenBy { it.conta.descricao.lowercase() }),
    )
}) {
    suspend fun salvar(conta: ContaFixa): Decisao = ateOFim { cadastros.salvar(conta, hoje()) }
    suspend fun excluir(conta: ContaFixa): Decisao = ateOFim { cadastros.excluir(conta, hoje()) }
    suspend fun encerrar(conta: ContaFixa) = ateOFim { cadastros.encerrar(conta, hoje()) }
}

data class EstadoEntradas(val ciclo: Ciclo, val hoje: LocalDate, val recorrentes: List<Entrada>, val avulsas: List<Entrada>)

@HiltViewModel
class EntradasViewModel @Inject constructor(
    cadastros: Cadastros,
    orcamento: OrcamentoRepository,
    configuracoes: ConfiguracoesRepository,
    relogio: Clock,
) : CadastroViewModel<EstadoEntradas>(cadastros, orcamento, configuracoes, relogio, { ciclo, hoje, dados ->
    val (recorrentes, avulsas) = dados.entradas.partition { it.tipo == TipoEntrada.RECORRENTE }
    // P18: avulsas de ciclos fechados ficam só no Histórico
    EstadoEntradas(ciclo, hoje, recorrentes.sortedBy { it.dataInicio }, avulsas.filter { it.dataInicio in ciclo }.sortedByDescending { it.dataInicio })
}) {
    suspend fun salvar(entrada: Entrada): Decisao = ateOFim { cadastros.salvar(entrada, hoje()) }
    suspend fun excluir(entrada: Entrada): Decisao = ateOFim { cadastros.excluir(entrada, hoje()) }
}

data class ItemMeta(val meta: MetaReserva, val valorNoCiclo: Long)

data class EstadoMetas(val ciclo: Ciclo, val itens: List<ItemMeta>)

@HiltViewModel
class MetasViewModel @Inject constructor(
    cadastros: Cadastros,
    orcamento: OrcamentoRepository,
    configuracoes: ConfiguracoesRepository,
    relogio: Clock,
) : CadastroViewModel<EstadoMetas>(cadastros, orcamento, configuracoes, relogio, { ciclo, _, dados ->
    val entradas = dados.resumo(ciclo).entradas
    EstadoMetas(ciclo, dados.metas.map { ItemMeta(it, it.valorNoCiclo(entradas)) })
}) {
    suspend fun salvar(meta: MetaReserva): Decisao = ateOFim { cadastros.salvar(meta, hoje()) }
    suspend fun excluir(meta: MetaReserva) = ateOFim { cadastros.excluir(meta) }
}
