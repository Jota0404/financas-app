package com.joaobarcelos.financas.ui.cadastros

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.joaobarcelos.financas.domain.formato.formatarPercentual
import com.joaobarcelos.financas.domain.formato.lerCentavos
import com.joaobarcelos.financas.domain.formato.lerPontosBase
import com.joaobarcelos.financas.domain.model.Ciclo
import com.joaobarcelos.financas.domain.model.ContaFixa
import com.joaobarcelos.financas.domain.model.Entrada
import com.joaobarcelos.financas.domain.model.MetaReserva
import com.joaobarcelos.financas.domain.model.TipoEntrada
import com.joaobarcelos.financas.domain.model.TipoMeta
import com.joaobarcelos.financas.domain.usecase.inicioPelaParcela
import kotlinx.coroutines.launch
import java.time.LocalDate

// Texto que não é número vira 0, e o domínio bloqueia com a mensagem da RN15.

@Composable
private fun LinhaComChave(texto: String, ligado: Boolean, aoMudar: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(texto, modifier = Modifier.weight(1f))
        Switch(checked = ligado, onCheckedChange = aoMudar)
    }
}

@Composable
private fun <T> Escolha(opcoes: List<Pair<T, String>>, escolhida: T, aoEscolher: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        opcoes.forEachIndexed { indice, (valor, texto) ->
            SegmentedButton(
                selected = valor == escolhida,
                onClick = { aoEscolher(valor) },
                shape = SegmentedButtonDefaults.itemShape(indice, opcoes.size),
            ) { Text(texto) }
        }
    }
}

@Composable
fun FormularioConta(conta: ContaFixa?, ciclo: Ciclo, podeExcluir: Boolean, vm: ContasViewModel, aoFechar: () -> Unit) {
    val escopo = rememberCoroutineScope()
    val estado = remember { EstadoFormulario(aoFechar) }
    var descricao by remember { mutableStateOf(conta?.descricao.orEmpty()) }
    var valor by remember { mutableStateOf(conta?.let { textoDoValor(it.valorCentavos) }.orEmpty()) }
    var vencimento by remember { mutableStateOf(conta?.diaVencimento?.toString().orEmpty()) }
    var semFim by remember { mutableStateOf(conta?.duracaoMeses == null) }
    var duracao by remember { mutableStateOf(conta?.duracaoMeses?.toString().orEmpty()) }
    var parcela by remember { mutableStateOf("1") }
    var confirmar by remember { mutableStateOf<String?>(null) }

    Formulario(
        titulo = if (conta == null) "Nova conta fixa" else "Editar conta fixa",
        estado = estado,
        aoSalvar = {
            val meses = if (semFim) null else duracao.toIntOrNull() ?: 0
            val inicio = when {
                conta != null -> conta.cicloInicio
                meses == null -> ciclo.inicio
                // decisão do dono: a parcela deste ciclo diz em que ciclo a conta começou
                else -> inicioPelaParcela(ciclo, parcela.toIntOrNull() ?: 0)
            }
            val nova = ContaFixa(
                valorCentavos = lerCentavos(valor) ?: 0,
                cicloInicio = inicio,
                duracaoMeses = meses,
                encerradaEm = conta?.encerradaEm,
                descricao = descricao.trim(),
                diaVencimento = vencimento.toIntOrNull() ?: 0,
                id = conta?.id ?: 0,
            )
            escopo.launch { estado.aplicar(vm.salvar(nova)) }
        },
        acoes = {
            if (conta != null && conta.encerradaEm == null) {
                OutlinedButton(onClick = { confirmar = "encerrar" }, modifier = Modifier.fillMaxWidth()) { Text("Encerrar conta") }
            }
            if (conta != null && podeExcluir) {
                TextButton(onClick = { confirmar = "excluir" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Excluir conta", color = MaterialTheme.colorScheme.error)
                }
            }
        },
    ) {
        CampoTexto("Nome (ex.: Aluguel)", descricao, { descricao = it })
        CampoTexto("Valor (R$)", valor, { valor = it }, KeyboardType.Decimal)
        CampoTexto("Dia do vencimento", vencimento, { vencimento = it }, KeyboardType.Number)
        LinhaComChave("Sem fim", semFim) { semFim = it }
        if (!semFim) {
            CampoTexto("Duração (meses)", duracao, { duracao = it }, KeyboardType.Number)
            if (conta == null) CampoTexto("Parcela deste ciclo", parcela, { parcela = it }, KeyboardType.Number)
        }
    }

    when (confirmar) {
        "encerrar" -> Confirmacao(
            titulo = "Encerrar ${conta?.descricao}?",
            texto = "A conta ainda é descontada neste ciclo e sai a partir do próximo.",
            botao = "Encerrar",
            aoConfirmar = { escopo.launch { vm.encerrar(conta!!); aoFechar() } },
            aoCancelar = { confirmar = null },
        )
        "excluir" -> Confirmacao(
            titulo = "Excluir ${conta?.descricao}?",
            texto = "Use só para uma conta cadastrada por engano. Ela some também do ciclo atual.",
            botao = "Excluir",
            aoConfirmar = { escopo.launch { confirmar = null; estado.aplicar(vm.excluir(conta!!)) } },
            aoCancelar = { confirmar = null },
        )
    }
}

@Composable
fun FormularioEntrada(entrada: Entrada?, ciclo: Ciclo, hoje: LocalDate, vm: EntradasViewModel, aoFechar: () -> Unit) {
    val escopo = rememberCoroutineScope()
    val estado = remember { EstadoFormulario(aoFechar) }
    var descricao by remember { mutableStateOf(entrada?.descricao.orEmpty()) }
    var valor by remember { mutableStateOf(entrada?.let { textoDoValor(it.valorCentavos) }.orEmpty()) }
    var tipo by remember { mutableStateOf(entrada?.tipo ?: TipoEntrada.RECORRENTE) }
    // decisão do dono: recorrente começa no início do ciclo atual; avulsa, hoje
    var inicio by remember { mutableStateOf(entrada?.dataInicio ?: dataPadrao(tipo, ciclo.inicio, hoje)) }
    var fim by remember { mutableStateOf(entrada?.dataFim) }
    var confirmarExclusao by remember { mutableStateOf(false) }

    Formulario(
        titulo = if (entrada == null) "Nova entrada" else "Editar entrada",
        estado = estado,
        aoSalvar = {
            val nova = Entrada(
                valorCentavos = lerCentavos(valor) ?: 0,
                tipo = tipo,
                dataInicio = inicio,
                dataFim = if (tipo == TipoEntrada.RECORRENTE) fim else null,
                descricao = descricao.trim(),
                id = entrada?.id ?: 0,
            )
            escopo.launch { estado.aplicar(vm.salvar(nova)) }
        },
        acoes = {
            if (entrada != null) {
                TextButton(onClick = { confirmarExclusao = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Excluir entrada", color = MaterialTheme.colorScheme.error)
                }
            }
        },
    ) {
        Escolha(listOf(TipoEntrada.RECORRENTE to "Recorrente", TipoEntrada.AVULSA to "Avulsa"), tipo) {
            if (entrada == null) inicio = dataPadrao(it, ciclo.inicio, hoje)
            tipo = it
        }
        CampoTexto(if (tipo == TipoEntrada.RECORRENTE) "Nome (ex.: Salário)" else "Nome (ex.: Freela)", descricao, { descricao = it })
        CampoTexto("Valor (R$)", valor, { valor = it }, KeyboardType.Decimal)
        if (tipo == TipoEntrada.RECORRENTE) {
            CampoData("Entra a partir de", inicio, { inicio = it })
            CampoData("Até", fim, { fim = it }, aoLimpar = { fim = null })
        } else {
            CampoData("Data", inicio, { inicio = it })
        }
    }

    if (confirmarExclusao && entrada != null) {
        Confirmacao(
            titulo = "Excluir ${entrada.descricao}?",
            texto = "A entrada sai do ciclo atual e dos próximos. Os ciclos já fechados não mudam.",
            botao = "Excluir",
            aoConfirmar = { escopo.launch { vm.excluir(entrada); aoFechar() } },
            aoCancelar = { confirmarExclusao = false },
        )
    }
}

@Composable
fun FormularioMeta(meta: MetaReserva?, vm: MetasViewModel, aoFechar: () -> Unit) {
    val escopo = rememberCoroutineScope()
    val estado = remember { EstadoFormulario(aoFechar) }
    var nome by remember { mutableStateOf(meta?.nome.orEmpty()) }
    var tipo by remember { mutableStateOf(meta?.tipo ?: TipoMeta.PERCENTUAL) }
    var valor by remember {
        mutableStateOf(
            when (meta?.tipo) {
                null -> ""
                TipoMeta.PERCENTUAL -> formatarPercentual(meta.valor).removeSuffix("%")
                TipoMeta.VALOR -> textoDoValor(meta.valor)
            },
        )
    }
    var ativa by remember { mutableStateOf(meta?.ativa ?: true) }
    var confirmarExclusao by remember { mutableStateOf(false) }

    Formulario(
        titulo = if (meta == null) "Nova meta de reserva" else "Editar meta de reserva",
        estado = estado,
        aoSalvar = {
            val numero = if (tipo == TipoMeta.PERCENTUAL) lerPontosBase(valor) else lerCentavos(valor)
            val nova = MetaReserva(tipo, numero ?: 0, ativa, nome.trim(), meta?.id ?: 0)
            escopo.launch { estado.aplicar(vm.salvar(nova)) }
        },
        acoes = {
            if (meta != null) {
                TextButton(onClick = { confirmarExclusao = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Excluir meta", color = MaterialTheme.colorScheme.error)
                }
            }
        },
    ) {
        CampoTexto("Nome (ex.: Reserva de segurança)", nome, { nome = it })
        Escolha(listOf(TipoMeta.PERCENTUAL to "% das entradas", TipoMeta.VALOR to "Valor fixo"), tipo) { tipo = it }
        CampoTexto(if (tipo == TipoMeta.PERCENTUAL) "Percentual (%)" else "Valor (R$)", valor, { valor = it }, KeyboardType.Decimal)
        LinhaComChave("Ativa", ativa) { ativa = it }
    }

    if (confirmarExclusao && meta != null) {
        Confirmacao(
            titulo = "Excluir ${meta.nome}?",
            texto = "A meta deixa de ser reservada no ciclo atual e nos próximos. Para parar só por um tempo, desligue \"Ativa\".",
            botao = "Excluir",
            aoConfirmar = { escopo.launch { vm.excluir(meta); aoFechar() } },
            aoCancelar = { confirmarExclusao = false },
        )
    }
}
