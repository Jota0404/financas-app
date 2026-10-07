package com.joaobarcelos.financas.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.joaobarcelos.financas.domain.calculadora.cicloAtual
import com.joaobarcelos.financas.domain.formato.formatarPercentual
import com.joaobarcelos.financas.domain.formato.formatarReais
import com.joaobarcelos.financas.domain.formato.lerCentavos
import com.joaobarcelos.financas.domain.model.Entrada
import com.joaobarcelos.financas.domain.model.TipoEntrada
import com.joaobarcelos.financas.domain.model.TipoMeta
import com.joaobarcelos.financas.domain.repository.ConfiguracoesRepository
import com.joaobarcelos.financas.domain.usecase.Cadastros
import com.joaobarcelos.financas.domain.usecase.Configurar
import com.joaobarcelos.financas.domain.usecase.Decisao
import com.joaobarcelos.financas.ui.CampoTexto
import com.joaobarcelos.financas.ui.cadastros.ContasViewModel
import com.joaobarcelos.financas.ui.cadastros.FormularioConta
import com.joaobarcelos.financas.ui.cadastros.FormularioMeta
import com.joaobarcelos.financas.ui.cadastros.MetasViewModel
import com.joaobarcelos.financas.ui.textoDoBloqueio
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Decide uma vez, ao abrir o app, se o assistente aparece; ele só some ao ser concluído ou pulado. */
@HiltViewModel
class PrimeiroUsoViewModel @Inject constructor(
    private val configurar: Configurar,
    private val cadastros: Cadastros,
    private val configuracoes: ConfiguracoesRepository,
    private val relogio: Clock,
) : ViewModel() {
    private val _mostrar = MutableStateFlow<Boolean?>(null)

    /** Nulo enquanto carrega. Não muda no meio do assistente, mesmo depois de cadastrar o salário. */
    val mostrar: StateFlow<Boolean?> = _mostrar

    init {
        // Só vale se o assistente ainda não foi concluído enquanto esta leitura acontecia
        viewModelScope.launch { _mostrar.compareAndSet(null, configurar.mostrarPrimeiroUso()) }
    }

    private fun hoje() = LocalDate.now(relogio)

    suspend fun definirDia(dia: Int): Decisao = viewModelScope.async { configurar.definirDiaNoPrimeiroUso(dia) }.await()

    /** P13: o salário entra a partir do início do ciclo atual. */
    suspend fun salvarSalario(centavos: Long): Decisao = viewModelScope.async {
        val inicio = configuracoes.configuracoes().first().cicloAtual(hoje()).inicio
        cadastros.salvar(Entrada(centavos, TipoEntrada.RECORRENTE, inicio, descricao = "Salário"), hoje())
    }.await()

    fun concluir() = viewModelScope.launch {
        configurar.concluirPrimeiroUso()
        _mostrar.value = false
    }
}

private const val PASSOS = 5

/** Tela 1: dia do pagamento, salário, contas fixas, metas e notificações. Passos 2 a 5 podem ser pulados. */
@Composable
fun PrimeiroUsoTela(
    vm: PrimeiroUsoViewModel,
    vmContas: ContasViewModel = hiltViewModel(),
    vmMetas: MetasViewModel = hiltViewModel(),
) {
    val escopo = rememberCoroutineScope()
    var passo by rememberSaveable { mutableIntStateOf(0) }
    var dia by rememberSaveable { mutableStateOf("1") }
    var salario by rememberSaveable { mutableStateOf("") }
    var erro by rememberSaveable { mutableStateOf<String?>(null) }
    var formulario by rememberSaveable { mutableStateOf(false) }
    val contas by vmContas.estado.collectAsStateWithLifecycle()
    val metas by vmMetas.estado.collectAsStateWithLifecycle()
    val pedirNotificacoes = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.concluir() }

    fun proximo() {
        erro = null
        if (passo == PASSOS - 1) vm.concluir() else passo++
    }

    fun aplicar(decisao: Decisao) = if (decisao is Decisao.Bloqueado) erro = textoDoBloqueio(decisao) else proximo()

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Passo ${passo + 1} de $PASSOS", style = MaterialTheme.typography.labelLarge)
            when (passo) {
                0 -> {
                    Titulo("Em que dia você recebe o salário?", "O ciclo do Fôlego vai de um pagamento até a véspera do próximo.")
                    CampoTexto("Dia do pagamento", dia, { dia = it }, teclado = KeyboardType.Number)
                }
                1 -> {
                    Titulo("Quanto você recebe por mês?", "Entra como salário a partir deste ciclo. Freelas e outras entradas ficam em Cadastros.")
                    CampoTexto("Salário (R$)", salario, { salario = it }, teclado = KeyboardType.Decimal)
                }
                2 -> {
                    Titulo("Quais contas fixas você paga?", "Aluguel, internet, financiamentos... Elas saem do disponível todo ciclo.")
                    contas?.itens?.forEach { Text("${it.conta.descricao}: ${formatarReais(it.conta.valorCentavos)}") }
                    OutlinedButton(onClick = { formulario = true }, modifier = Modifier.fillMaxWidth()) { Text("Adicionar conta fixa") }
                }
                3 -> {
                    Titulo("Quanto você quer guardar?", "A reserva sai antes de qualquer gasto e não aparece como disponível.")
                    metas?.itens?.forEach {
                        val valor = if (it.meta.tipo == TipoMeta.PERCENTUAL) formatarPercentual(it.meta.valor) else formatarReais(it.meta.valor)
                        Text("${it.meta.nome}: $valor")
                    }
                    OutlinedButton(onClick = { formulario = true }, modifier = Modifier.fillMaxWidth()) { Text("Adicionar meta de reserva") }
                }
                else -> Titulo(
                    "Quer receber os avisos?",
                    "O Fôlego avisa o resumo da semana, quando os gastos chegam perto do limite, se a reserva for invadida e quando o ciclo fecha.",
                )
            }
            erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (passo in 1 until PASSOS) TextButton(onClick = ::proximo) { Text(if (passo == PASSOS - 1) "Agora não" else "Pular") }
                Button(onClick = {
                    when (passo) {
                        0 -> escopo.launch { aplicar(vm.definirDia(dia.toIntOrNull() ?: 0)) }
                        1 -> {
                            val centavos = lerCentavos(salario)
                            if (centavos == null) erro = "Digite o valor assim: 3.000,00 ou 3000.00." else escopo.launch { aplicar(vm.salvarSalario(centavos)) }
                        }
                        PASSOS - 1 ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                pedirNotificacoes.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                vm.concluir()
                            }
                        else -> proximo()
                    }
                }) { Text(if (passo == PASSOS - 1) "Permitir avisos" else "Continuar") }
            }
        }
    }

    // Os mesmos formulários da aba Cadastros
    if (formulario && passo == 2) contas?.let { FormularioConta(null, it.ciclo, podeExcluir = false, vm = vmContas, aoFechar = { formulario = false }) }
    if (formulario && passo == 3) FormularioMeta(null, vmMetas, aoFechar = { formulario = false })
}

@Composable
private fun Titulo(titulo: String, texto: String) {
    Text(titulo, style = MaterialTheme.typography.headlineSmall)
    Text(texto, style = MaterialTheme.typography.bodyLarge)
}
