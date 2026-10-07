package com.joaobarcelos.financas.ui.inicio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.joaobarcelos.financas.domain.calculadora.Faixa
import com.joaobarcelos.financas.domain.calculadora.Painel
import com.joaobarcelos.financas.domain.calculadora.painel
import com.joaobarcelos.financas.domain.formato.formatarDiaMes
import com.joaobarcelos.financas.domain.formato.formatarPercentual
import com.joaobarcelos.financas.domain.formato.formatarReais
import com.joaobarcelos.financas.domain.repository.ConfiguracoesRepository
import com.joaobarcelos.financas.domain.repository.OrcamentoRepository
import com.joaobarcelos.financas.ui.gastos.FormularioGasto
import com.joaobarcelos.financas.ui.gastos.GastoViewModel
import com.joaobarcelos.financas.ui.momento
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import javax.inject.Inject

/** [semEntradas]: nada cadastrado ainda, então o painel orienta a começar pelos cadastros. */
data class EstadoInicio(val painel: Painel, val semEntradas: Boolean)

@HiltViewModel
class InicioViewModel @Inject constructor(
    orcamento: OrcamentoRepository,
    configuracoes: ConfiguracoesRepository,
    relogio: Clock,
) : ViewModel() {
    /** Nulo enquanto carrega. Recalcula a cada gasto ou cadastro e na virada do dia. */
    val estado: StateFlow<EstadoInicio?> = momento(orcamento, configuracoes, relogio)
        .map { EstadoInicio(it.dados.painel(it.ciclo, it.hoje, it.configuracoes), it.dados.entradas.isEmpty()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Cor da barra de consumo (briefing, tela 2). */
fun corDaFaixa(faixa: Faixa): Color = when (faixa) {
    Faixa.VERDE -> Color(0xFF43A047)
    Faixa.AMARELA -> Color(0xFFF9A825)
    Faixa.VERMELHA -> Color(0xFFE53935)
}

/** Tela 2: disponível da semana em destaque, barra de consumo, disponível do ciclo e reserva. */
@Composable
fun InicioTela(
    aoIrParaCadastros: () -> Unit,
    vm: InicioViewModel = hiltViewModel(),
    vmGasto: GastoViewModel = hiltViewModel(),
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    var novoGasto by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        // No primeiro uso o banco leva alguns segundos para ser criado
        estado?.let { Painel(it, aoIrParaCadastros) } ?: CircularProgressIndicator(Modifier.align(Alignment.Center))
        FloatingActionButton(onClick = { novoGasto = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Novo gasto")
        }
    }
    if (novoGasto) FormularioGasto(null, aoFechar = { novoGasto = false }, vm = vmGasto)
}

@Composable
private fun Painel(estado: EstadoInicio, aoIrParaCadastros: () -> Unit) {
    val painel = estado.painel
    val semana = painel.semana
    val resumo = painel.resumoDoCiclo
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (estado.semEntradas) {
            // Sem nada cadastrado, só o convite: a barra vermelha de limite zero assustaria (QA Etapa 5, B1)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Comece cadastrando o seu salário, as contas fixas e a reserva.")
                    Button(onClick = aoIrParaCadastros) { Text("Ir para Cadastros") }
                }
            }
            return@Column
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Disponível da semana", style = MaterialTheme.typography.titleMedium)
            Text(
                formatarReais(painel.disponivelDaSemana),
                style = MaterialTheme.typography.displaySmall,
                color = if (painel.disponivelDaSemana < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Limite de ${formatarReais(semana.valorCentavos)} de ${formatarDiaMes(semana.inicio)} a ${formatarDiaMes(semana.fim)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            val consumo = "${formatarPercentual(painel.consumoPontosBase)} do limite usado"
            LinearProgressIndicator(
                progress = { painel.consumoPontosBase / 10_000f }, // só para desenhar a barra
                color = corDaFaixa(painel.faixa),
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().height(12.dp).semantics { contentDescription = consumo },
            )
            Text(
                if (semana.valorCentavos <= 0) "Sem limite nesta semana: o disponível do ciclo acabou" else consumo,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (semana.ritmoNaoFechaCiclo) {
                Text("Seu limite manual passa do ritmo que fecha o ciclo.", color = MaterialTheme.colorScheme.error)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Linha("Disponível do ciclo", formatarReais(resumo.disponivel))
                Linha("Protegido na reserva", formatarReais(resumo.reserva))
                if (resumo.reservaInvadida > 0) {
                    Text("Reserva invadida em ${formatarReais(resumo.reservaInvadida)}", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun Linha(rotulo: String, valor: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(rotulo, modifier = Modifier.weight(1f))
        Text(valor, style = MaterialTheme.typography.titleMedium)
    }
}
