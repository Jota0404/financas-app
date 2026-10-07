package com.joaobarcelos.financas.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joaobarcelos.financas.domain.formato.formatarData
import com.joaobarcelos.financas.domain.formato.formatarReais
import com.joaobarcelos.financas.domain.usecase.Decisao
import com.joaobarcelos.financas.domain.usecase.ErroCadastro
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Texto da tela para cada bloqueio do domínio. */
fun textoDoBloqueio(bloqueio: Decisao.Bloqueado): String = when (bloqueio.erro) {
    ErroCadastro.DESCRICAO_VAZIA -> "Dê um nome."
    ErroCadastro.VALOR_ZERO_OU_NEGATIVO -> "O valor precisa ser maior que zero, como 1.500,00."
    ErroCadastro.PERCENTUAL_ACIMA_DE_100 -> "O percentual vai até 100%."
    ErroCadastro.DIA_VENCIMENTO_INVALIDO -> "O vencimento é um dia de 1 a 31."
    ErroCadastro.DURACAO_INVALIDA -> "A duração é de pelo menos 1 mês."
    ErroCadastro.PARCELA_INVALIDA -> "A parcela deste ciclo vai de 1 até a duração."
    ErroCadastro.DATA_EM_CICLO_FECHADO -> "Essa data é de um ciclo que já fechou."
    ErroCadastro.DATA_FUTURA -> "A data não pode ser depois de hoje."
    ErroCadastro.FIM_ANTES_DO_INICIO -> "O fim não pode ser antes do início."
    ErroCadastro.METAS_NAO_CABEM ->
        "Esta meta não cabe: faltam ${formatarReais(bloqueio.faltaParaMetas)} para ela ser viável."
    ErroCadastro.CONTA_SAIRIA_DO_CICLO -> "Para tirar a conta deste ciclo, encerre."
    ErroCadastro.CONTA_JA_DESCONTADA ->
        "Esta conta já foi descontada em ciclos anteriores. Encerre a conta em vez de excluir."
}

/** Aplica a decisão do domínio: fecha, mostra o aviso da RN08 ou mostra o motivo do bloqueio. */
class EstadoFormulario(private val aoFechar: () -> Unit) {
    var erro by mutableStateOf<String?>(null)
    var aviso by mutableStateOf<String?>(null)

    fun aplicar(decisao: Decisao) {
        when (decisao) {
            Decisao.Permitido -> aoFechar()
            is Decisao.PermitidoComAviso -> aviso =
                "Suas metas de reserva passam do que sobra das entradas depois das contas fixas. " +
                "Faltam ${formatarReais(decisao.faltaParaMetas)}."
            is Decisao.PermitidoComReservaInvadida -> aviso =
                "Este gasto invadiu a reserva. A reserva deste ciclo está invadida em ${formatarReais(decisao.reservaInvadida)}."
            is Decisao.Bloqueado -> erro = textoDoBloqueio(decisao)
        }
    }

    fun fechar() = aoFechar()

    /** O texto do valor não é um número (ex.: "abc" ou vazio). */
    fun valorIlegivel(percentual: Boolean = false) {
        erro = if (percentual) "Digite o percentual assim: 10 ou 10,5." else "Digite o valor assim: 1.500,00 ou 1500.50."
    }
}

/** Formulário em tela cheia, com Salvar no alto. [acoes] fica embaixo (encerrar, excluir). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Formulario(
    titulo: String,
    estado: EstadoFormulario,
    aoSalvar: () -> Unit,
    acoes: @Composable ColumnScope.() -> Unit = {},
    campos: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = estado::fechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(titulo) },
                    navigationIcon = {
                        IconButton(onClick = estado::fechar) { Icon(Icons.Filled.Close, contentDescription = "Fechar") }
                    },
                    actions = { TextButton(onClick = aoSalvar) { Text("Salvar") } },
                )
            },
        ) { espaco ->
            Column(
                Modifier.padding(espaco).padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                campos()
                estado.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                acoes()
            }
        }
    }
    estado.aviso?.let { aviso ->
        AlertDialog(
            onDismissRequest = estado::fechar,
            title = { Text("Salvo, mas atenção") },
            text = { Text(aviso) },
            confirmButton = { TextButton(onClick = estado::fechar) { Text("Entendi") } },
        )
    }
}

@Composable
fun CampoTexto(
    rotulo: String,
    valor: String,
    aoMudar: (String) -> Unit,
    modifier: Modifier = Modifier,
    teclado: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text(rotulo) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Botão que mostra a data e abre o calendário. Com [aoLimpar], a data pode ficar vazia. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoData(rotulo: String, data: LocalDate?, aoMudar: (LocalDate) -> Unit, aoLimpar: (() -> Unit)? = null) {
    var aberto by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { aberto = true }, modifier = Modifier.weight(1f)) {
            Text("$rotulo: ${data?.let(::formatarData) ?: "sem data"}")
        }
        if (aoLimpar != null && data != null) TextButton(onClick = aoLimpar) { Text("Limpar") }
    }
    if (aberto) {
        // O calendário trabalha com meia-noite em UTC
        val calendario = rememberDatePickerState(
            initialSelectedDateMillis = data?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { aberto = false },
            confirmButton = {
                TextButton(onClick = {
                    calendario.selectedDateMillis?.let { aoMudar(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    aberto = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { aberto = false }) { Text("Cancelar") } },
        ) { DatePicker(state = calendario) }
    }
}

/** Pede confirmação antes de uma ação que não dá para desfazer. */
@Composable
fun Confirmacao(titulo: String, texto: String, botao: String, aoConfirmar: () -> Unit, aoCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = aoCancelar,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = { TextButton(onClick = aoConfirmar) { Text(botao) } },
        dismissButton = { TextButton(onClick = aoCancelar) { Text("Cancelar") } },
    )
}

/** Lista com o botão "+" e o estado vazio com orientação. Nada aparece enquanto carrega. */
@Composable
fun ListaDeCadastro(
    carregada: Boolean,
    vazia: Boolean,
    textoVazio: String,
    textoDoBotao: String,
    aoAdicionar: () -> Unit,
    itens: LazyListScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        when {
            !carregada -> Unit
            vazia -> Text(
                textoVazio,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp), content = itens)
        }
        FloatingActionButton(onClick = aoAdicionar, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Filled.Add, contentDescription = textoDoBotao)
        }
    }
}

@Composable
fun TituloDeSecao(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** "1.500,00" para preencher o campo de valor ao editar. */
fun textoDoValor(centavos: Long) = formatarReais(centavos).removePrefix("R$ ")
