package com.joaobarcelos.financas.ui.historico

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.joaobarcelos.financas.domain.formato.formatarData
import com.joaobarcelos.financas.domain.formato.formatarDiaMes
import com.joaobarcelos.financas.domain.formato.formatarReais
import com.joaobarcelos.financas.domain.model.Categoria
import com.joaobarcelos.financas.domain.model.CicloFechado
import com.joaobarcelos.financas.domain.model.Entrada
import com.joaobarcelos.financas.domain.model.Gasto
import com.joaobarcelos.financas.domain.model.TipoEntrada
import com.joaobarcelos.financas.domain.repository.ConfiguracoesRepository
import com.joaobarcelos.financas.domain.repository.HistoricoRepository
import com.joaobarcelos.financas.domain.repository.OrcamentoRepository
import com.joaobarcelos.financas.ui.TituloDeSecao
import com.joaobarcelos.financas.ui.gastos.FormularioGasto
import com.joaobarcelos.financas.ui.gastos.GastoViewModel
import com.joaobarcelos.financas.ui.momento
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class DiaDeGastos(val data: LocalDate, val total: Long, val gastos: List<Gasto>)

data class EstadoHistorico(
    val hoje: LocalDate,
    val dias: List<DiaDeGastos>,
    val categorias: Map<Long, Categoria>,
    val avulsasFechadas: List<Entrada>,
    val ciclosFechados: List<CicloFechado>,
)

@HiltViewModel
class HistoricoViewModel @Inject constructor(
    orcamento: OrcamentoRepository,
    configuracoes: ConfiguracoesRepository,
    historico: HistoricoRepository,
    relogio: Clock,
) : ViewModel() {
    /** Nulo enquanto carrega. */
    val estado: StateFlow<EstadoHistorico?> = combine(
        momento(orcamento, configuracoes, relogio),
        orcamento.categorias(),
        historico.ciclosFechados(),
    ) { agora, categorias, fechados ->
        val ciclo = agora.ciclo
        EstadoHistorico(
            hoje = agora.hoje,
            // Gastos do ciclo atual, do dia mais recente para o mais antigo (briefing, tela 7)
            dias = agora.dados.gastos.filter { it.data in ciclo }.groupBy { it.data }.toSortedMap(reverseOrder())
                .map { (dia, gastos) -> DiaDeGastos(dia, gastos.sumOf { it.valorCentavos }, gastos) },
            categorias = categorias.associateBy { it.id },
            // P18: avulsas de ciclos fechados aparecem aqui, só para consulta
            avulsasFechadas = agora.dados.entradas
                .filter { it.tipo == TipoEntrada.AVULSA && it.dataInicio < ciclo.inicio }
                .sortedByDescending { it.dataInicio },
            ciclosFechados = fechados,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private val brasil = Locale.forLanguageTag("pt-BR")

/** "Hoje", "Ontem" ou "seg., 05/10". */
fun nomeDoDia(dia: LocalDate, hoje: LocalDate): String = when (dia) {
    hoje -> "Hoje"
    hoje.minusDays(1) -> "Ontem"
    else -> "${dia.dayOfWeek.getDisplayName(TextStyle.SHORT, brasil)}, ${formatarDiaMes(dia)}"
}

/** Tela 7: gastos do ciclo atual por dia, avulsas e ciclos fechados. Tocar num gasto corrige ou exclui. */
@Composable
fun HistoricoTela(vm: HistoricoViewModel = hiltViewModel(), vmGasto: GastoViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    var abertoId by rememberSaveable { mutableStateOf<Long?>(null) }
    val atual = estado ?: return
    LazyColumn(Modifier.fillMaxSize()) {
        if (atual.dias.isEmpty()) {
            item {
                Text(
                    "Nenhum gasto neste ciclo ainda. Toque em + no Início para registrar.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        atual.dias.forEach { dia ->
            item(key = "dia-${dia.data}") {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
                    Text(nomeDoDia(dia.data, atual.hoje), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(formatarReais(dia.total), style = MaterialTheme.typography.titleSmall)
                }
            }
            items(dia.gastos, key = { "gasto-${it.id}" }) { gasto ->
                val categoria = atual.categorias[gasto.categoriaId]
                ListItem(
                    headlineContent = { Text(gasto.descricao.ifBlank { categoria?.nome.orEmpty() }) },
                    supportingContent = { Text("${categoria?.icone.orEmpty()} ${categoria?.nome.orEmpty()}") },
                    trailingContent = { Text(formatarReais(gasto.valorCentavos)) },
                    modifier = Modifier.clickable { abertoId = gasto.id },
                )
            }
        }
        if (atual.avulsasFechadas.isNotEmpty()) {
            item { TituloDeSecao("Entradas avulsas de ciclos fechados") }
            items(atual.avulsasFechadas, key = { "avulsa-${it.id}" }) {
                ListItem(
                    headlineContent = { Text(it.descricao) },
                    supportingContent = { Text("${formatarReais(it.valorCentavos)} · ${formatarData(it.dataInicio)}") },
                )
            }
        }
        item { TituloDeSecao("Ciclos fechados") }
        if (atual.ciclosFechados.isEmpty()) {
            item {
                Text(
                    "Os ciclos aparecem aqui depois que o primeiro ciclo terminar.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        items(atual.ciclosFechados, key = { "ciclo-${it.id}" }) { fechado ->
            val resumo = fechado.resumo
            ListItem(
                headlineContent = { Text("${formatarData(fechado.ciclo.inicio)} a ${formatarData(fechado.ciclo.fim)}") },
                supportingContent = {
                    Text(
                        "Entradas ${formatarReais(resumo.entradas)} · Gastos ${formatarReais(resumo.gastos)} · " +
                            "Sobrou ${formatarReais(resumo.disponivel)}" +
                            if (resumo.reservaInvadida > 0) "\nReserva invadida em ${formatarReais(resumo.reservaInvadida)}" else "",
                    )
                },
            )
        }
    }
    atual.dias.flatMap { it.gastos }.find { it.id == abertoId }?.let { FormularioGasto(it, aoFechar = { abertoId = null }, vm = vmGasto) }
}
