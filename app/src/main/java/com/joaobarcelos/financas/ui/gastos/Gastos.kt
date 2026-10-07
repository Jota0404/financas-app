package com.joaobarcelos.financas.ui.gastos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.joaobarcelos.financas.domain.formato.lerCentavos
import com.joaobarcelos.financas.domain.model.Categoria
import com.joaobarcelos.financas.domain.model.Gasto
import com.joaobarcelos.financas.domain.repository.OrcamentoRepository
import com.joaobarcelos.financas.domain.usecase.Cadastros
import com.joaobarcelos.financas.domain.usecase.Decisao
import com.joaobarcelos.financas.ui.CampoData
import com.joaobarcelos.financas.ui.CampoTexto
import com.joaobarcelos.financas.ui.Confirmacao
import com.joaobarcelos.financas.ui.EstadoFormulario
import com.joaobarcelos.financas.ui.Formulario
import com.joaobarcelos.financas.ui.textoDoValor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Registra, corrige e exclui gastos. As regras (RN09, RN14, RN15) vêm do domínio. */
@HiltViewModel
class GastoViewModel @Inject constructor(
    private val cadastros: Cadastros,
    orcamento: OrcamentoRepository,
    private val relogio: Clock,
) : ViewModel() {
    val categorias: StateFlow<List<Categoria>> =
        orcamento.categorias().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Categoria.PADRAO)

    fun hoje(): LocalDate = LocalDate.now(relogio)

    // Termina de salvar mesmo se a tela for refeita no meio (ex.: girar o celular)
    suspend fun salvar(gasto: Gasto): Decisao = viewModelScope.async { cadastros.salvar(gasto, hoje()) }.await()
    suspend fun excluir(gasto: Gasto): Decisao = viewModelScope.async { cadastros.excluir(gasto, hoje()) }.await()
}

/** Tela 3, novo gasto, e a correção de um gasto do ciclo atual (decisão do dono, pelo Histórico). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormularioGasto(gasto: Gasto?, aoFechar: () -> Unit, vm: GastoViewModel = hiltViewModel()) {
    val escopo = rememberCoroutineScope()
    val estado = remember { EstadoFormulario(aoFechar) }
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    var valor by rememberSaveable { mutableStateOf(gasto?.let { textoDoValor(it.valorCentavos) }.orEmpty()) }
    var descricao by rememberSaveable { mutableStateOf(gasto?.descricao.orEmpty()) }
    var categoria by rememberSaveable { mutableLongStateOf(gasto?.categoriaId ?: Categoria.OUTROS) }
    var data by rememberSaveable { mutableStateOf(gasto?.data ?: vm.hoje()) }
    var confirmarExclusao by rememberSaveable { mutableStateOf(false) }

    // O teclado numérico abre direto no valor (briefing, tela 3)
    val focoNoValor = remember { FocusRequester() }
    val teclado = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        if (gasto == null) {
            delay(100) // espera a janela do formulário aparecer
            focoNoValor.requestFocus()
            teclado?.show()
        }
    }

    Formulario(
        titulo = if (gasto == null) "Novo gasto" else "Corrigir gasto",
        estado = estado,
        aoSalvar = salvar@{
            val centavos = lerCentavos(valor) ?: return@salvar estado.valorIlegivel()
            val novo = Gasto(centavos, data, descricao.trim(), categoria, gasto?.id ?: 0)
            escopo.launch { estado.aplicar(vm.salvar(novo)) }
        },
        acoes = {
            if (gasto != null) {
                TextButton(onClick = { confirmarExclusao = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Excluir gasto", color = MaterialTheme.colorScheme.error)
                }
            }
        },
    ) {
        CampoTexto("Valor (R$)", valor, { valor = it }, KeyboardType.Decimal, Modifier.focusRequester(focoNoValor))
        CampoTexto("Descrição (opcional)", descricao, { descricao = it })
        Text("Categoria", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categorias.forEach {
                FilterChip(selected = it.id == categoria, onClick = { categoria = it.id }, label = { Text("${it.icone} ${it.nome}") })
            }
        }
        CampoData("Data", data, { data = it })
    }

    if (confirmarExclusao && gasto != null) {
        Confirmacao(
            titulo = "Excluir este gasto?",
            texto = "O valor volta para o disponível da semana e do ciclo.",
            botao = "Excluir",
            aoConfirmar = { escopo.launch { confirmarExclusao = false; estado.aplicar(vm.excluir(gasto)) } },
            aoCancelar = { confirmarExclusao = false },
        )
    }
}
