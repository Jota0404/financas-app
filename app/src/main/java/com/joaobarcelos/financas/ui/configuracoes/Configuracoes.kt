package com.joaobarcelos.financas.ui.configuracoes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.joaobarcelos.financas.domain.calculadora.cicloAtual
import com.joaobarcelos.financas.domain.calculadora.comDiaPagamento
import com.joaobarcelos.financas.domain.formato.formatarData
import com.joaobarcelos.financas.domain.formato.formatarPercentual
import com.joaobarcelos.financas.domain.formato.lerCentavos
import com.joaobarcelos.financas.domain.formato.lerPontosBase
import com.joaobarcelos.financas.domain.model.Configuracoes
import com.joaobarcelos.financas.domain.repository.ConfiguracoesRepository
import com.joaobarcelos.financas.domain.usecase.Configurar
import com.joaobarcelos.financas.domain.usecase.Decisao
import com.joaobarcelos.financas.ui.CampoTexto
import com.joaobarcelos.financas.ui.textoDoBloqueio
import com.joaobarcelos.financas.ui.textoDoValor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class ConfiguracoesViewModel @Inject constructor(
    private val configurar: Configurar,
    configuracoes: ConfiguracoesRepository,
    private val relogio: Clock,
) : ViewModel() {
    /** Nulo enquanto carrega. */
    val configuracoes: StateFlow<Configuracoes?> =
        configuracoes.configuracoes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun hoje(): LocalDate = LocalDate.now(relogio)

    // Termina de salvar mesmo se a tela for refeita no meio (ex.: girar o celular)
    suspend fun salvar(nova: Configuracoes): Decisao = viewModelScope.async { configurar.salvar(nova, hoje()) }.await()
}

/** "O ciclo atual passa a ir de ... a ..." ao mudar o dia do pagamento (RN01, P14). */
fun efeitoDoNovoDia(atual: Configuracoes, novoDia: Int, hoje: LocalDate): String {
    val mudanca = atual.comDiaPagamento(novoDia, hoje)
    val ciclo = mudanca.configuracoes.cicloAtual(hoje)
    val fechado = mudanca.fechado?.let { "O ciclo de ${formatarData(it.inicio)} a ${formatarData(it.fim)} fecha agora. " }.orEmpty()
    return "${fechado}O ciclo atual passa a ir de ${formatarData(ciclo.inicio)} a ${formatarData(ciclo.fim)}."
}

/** Tela 8: dia do pagamento, limite manual, percentuais e horário dos alertas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracoesTela(vm: ConfiguracoesViewModel = hiltViewModel()) {
    val atual = vm.configuracoes.collectAsStateWithLifecycle().value ?: return
    val escopo = rememberCoroutineScope()
    // Os campos voltam ao que está salvo sempre que as configurações mudam
    var dia by rememberSaveable(atual) { mutableStateOf(atual.diaPagamento.toString()) }
    var limite by rememberSaveable(atual) { mutableStateOf(atual.limiteSemanalManual?.let(::textoDoValor).orEmpty()) }
    var atencao by rememberSaveable(atual) { mutableStateOf(formatarPercentual(atual.percentualAtencao.toLong()).removeSuffix("%")) }
    var critico by rememberSaveable(atual) { mutableStateOf(formatarPercentual(atual.percentualCritico.toLong()).removeSuffix("%")) }
    var horario by rememberSaveable(atual) { mutableStateOf(atual.horaResumo) }
    var mensagem by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmarDia by rememberSaveable { mutableStateOf<String?>(null) }
    var escolherHorario by rememberSaveable { mutableStateOf(false) }

    fun nova(): Configuracoes? {
        val novoLimite = if (limite.isBlank()) null else lerCentavos(limite)
        val novaAtencao = lerPontosBase(atencao)
        val novoCritico = lerPontosBase(critico)
        if ((limite.isNotBlank() && novoLimite == null) || novaAtencao == null || novoCritico == null) {
            mensagem = "Digite os valores assim: limite 500,00 e percentuais 70 ou 70,5."
            return null
        }
        return atual.copy(
            diaPagamento = dia.toIntOrNull() ?: 0,
            limiteSemanalManual = novoLimite,
            percentualAtencao = novaAtencao.toInt(),
            percentualCritico = novoCritico.toInt(),
            horaResumo = horario,
        )
    }

    fun salvar(config: Configuracoes) = escopo.launch {
        mensagem = when (val decisao = vm.salvar(config)) {
            is Decisao.Bloqueado -> textoDoBloqueio(decisao)
            else -> "Configurações salvas."
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Configurações", style = MaterialTheme.typography.headlineSmall)
        CampoTexto("Dia do pagamento", dia, { dia = it }, teclado = KeyboardType.Number)
        CampoTexto("Limite manual da semana (R$)", limite, { limite = it }, teclado = KeyboardType.Decimal)
        Text(
            "Vazio: o app calcula o limite da semana sozinho. Preenchido: vale para uma semana cheia e é proporcional nas semanas partidas.",
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider()
        Text("Alertas", style = MaterialTheme.typography.titleMedium)
        CampoTexto("Atenção (%)", atencao, { atencao = it }, teclado = KeyboardType.Decimal)
        CampoTexto("Crítico (%)", critico, { critico = it }, teclado = KeyboardType.Decimal)
        OutlinedButton(onClick = { escolherHorario = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Horário dos resumos: $horario")
        }
        Notificacoes()
        mensagem?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Button(
            onClick = {
                val config = nova() ?: return@Button
                if (config.diaPagamento != atual.diaPagamento && config.diaPagamento in 1..31) {
                    confirmarDia = efeitoDoNovoDia(atual, config.diaPagamento, vm.hoje())
                } else {
                    salvar(config)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Salvar") }
    }

    confirmarDia?.let { efeito ->
        AlertDialog(
            onDismissRequest = { confirmarDia = null },
            title = { Text("Mudar o dia do pagamento?") },
            text = { Text(efeito) },
            confirmButton = { TextButton(onClick = { confirmarDia = null; nova()?.let(::salvar) }) { Text("Mudar") } },
            dismissButton = { TextButton(onClick = { confirmarDia = null }) { Text("Cancelar") } },
        )
    }

    if (escolherHorario) {
        val relogio = rememberTimePickerState(horario.hour, horario.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { escolherHorario = false },
            title = { Text("Horário dos resumos") },
            text = { TimePicker(state = relogio) },
            confirmButton = {
                TextButton(onClick = { horario = LocalTime.of(relogio.hour, relogio.minute); escolherHorario = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { escolherHorario = false }) { Text("Cancelar") } },
        )
    }
}

/** Mostra se as notificações estão permitidas e leva às configurações do Android para mudar. */
@Composable
private fun Notificacoes() {
    val contexto = LocalContext.current
    val permitidas = remember {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    Text(
        if (permitidas) "Notificações: permitidas." else "Notificações: desligadas. Os alertas não aparecem.",
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedButton(
        onClick = {
            contexto.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, contexto.packageName),
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Ajustar notificações no Android") }
}
