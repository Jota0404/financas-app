package com.joaobarcelos.financas.ui.cadastros

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaobarcelos.financas.domain.formato.formatarData
import com.joaobarcelos.financas.domain.formato.formatarPercentual
import com.joaobarcelos.financas.domain.formato.formatarReais
import com.joaobarcelos.financas.domain.model.Entrada
import com.joaobarcelos.financas.domain.model.TipoEntrada
import com.joaobarcelos.financas.domain.model.TipoMeta
import java.time.LocalDate

/** Aba Cadastros: contas fixas, entradas e metas de reserva (telas 4, 5 e 6 do briefing). */
@Composable
fun CadastrosTela() {
    var aba by rememberSaveable { mutableIntStateOf(0) }
    Column {
        PrimaryTabRow(selectedTabIndex = aba) {
            listOf("Contas fixas", "Entradas", "Metas").forEachIndexed { indice, titulo ->
                Tab(selected = aba == indice, onClick = { aba = indice }, text = { Text(titulo) })
            }
        }
        when (aba) {
            0 -> ContasTela()
            1 -> EntradasTela()
            else -> MetasTela()
        }
    }
}

/** Situação da conta: "Parcela 3 de 10", "Sem fim", "Terminou" ou encerrada (RN03, RN04). */
fun situacaoDaConta(item: ItemConta): String {
    val conta = item.conta
    val duracao = conta.duracaoMeses
    return when {
        conta.encerradaEm != null && item.ativa ->
            "Encerrada em ${formatarData(conta.encerradaEm!!)}: ainda conta neste ciclo e sai no próximo"
        conta.encerradaEm != null -> "Encerrada em ${formatarData(conta.encerradaEm!!)}"
        duracao == null -> "Sem fim"
        !item.ativa && (item.parcela ?: 0) > duracao -> "Terminou ($duracao de $duracao)"
        !item.ativa -> "Começa no ciclo de ${formatarData(conta.cicloInicio)}"
        else -> "Parcela ${item.parcela} de $duracao"
    }
}

@Composable
fun ContasTela(vm: ContasViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    var aberta by remember { mutableStateOf<ItemConta?>(null) }
    var nova by remember { mutableStateOf(false) }
    ListaDeCadastro(
        carregada = estado != null,
        vazia = estado?.itens.isNullOrEmpty(),
        textoVazio = "Nenhuma conta fixa ainda. Toque em + para adicionar.",
        aoAdicionar = { nova = true },
    ) {
        items(estado?.itens.orEmpty(), key = { it.conta.id }) { item ->
            ListItem(
                headlineContent = { Text(item.conta.descricao) },
                supportingContent = {
                    Text("${formatarReais(item.conta.valorCentavos)} · vence dia ${item.conta.diaVencimento}\n${situacaoDaConta(item)}")
                },
                modifier = Modifier.clickable { aberta = item },
            )
        }
    }
    val ciclo = estado?.ciclo ?: return
    if (nova) FormularioConta(null, ciclo, podeExcluir = false, vm = vm, aoFechar = { nova = false })
    aberta?.let { FormularioConta(it.conta, ciclo, it.podeExcluir, vm, aoFechar = { aberta = null }) }
}

@Composable
fun EntradasTela(vm: EntradasViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    var aberta by remember { mutableStateOf<Entrada?>(null) }
    var nova by remember { mutableStateOf(false) }
    val atual = estado
    ListaDeCadastro(
        carregada = atual != null,
        vazia = atual != null && atual.recorrentes.isEmpty() && atual.avulsas.isEmpty(),
        textoVazio = "Nenhuma entrada ainda. Toque em + para adicionar o seu salário ou um freela.",
        aoAdicionar = { nova = true },
    ) {
        if (atual == null) return@ListaDeCadastro
        if (atual.recorrentes.isNotEmpty()) item { TituloDeSecao("Recorrentes") }
        items(atual.recorrentes, key = { it.id }) { entrada ->
            val periodo = "desde ${formatarData(entrada.dataInicio)}" + (entrada.dataFim?.let { " até ${formatarData(it)}" } ?: "")
            ListItem(
                headlineContent = { Text(entrada.descricao) },
                supportingContent = { Text("${formatarReais(entrada.valorCentavos)} · $periodo") },
                modifier = Modifier.clickable { aberta = entrada },
            )
        }
        if (atual.avulsas.isNotEmpty()) item { TituloDeSecao("Avulsas") }
        items(atual.avulsas, key = { it.id }) { entrada ->
            ListItem(
                headlineContent = { Text(entrada.descricao) },
                supportingContent = { Text("${formatarReais(entrada.valorCentavos)} · ${formatarData(entrada.dataInicio)}") },
                modifier = Modifier.clickable { aberta = entrada },
            )
        }
    }
    if (atual == null) return
    if (nova) FormularioEntrada(null, atual.ciclo, atual.hoje, vm, aoFechar = { nova = false })
    aberta?.let { FormularioEntrada(it, atual.ciclo, atual.hoje, vm, aoFechar = { aberta = null }) }
}

@Composable
fun MetasTela(vm: MetasViewModel = hiltViewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    var aberta by remember { mutableStateOf<ItemMeta?>(null) }
    var nova by remember { mutableStateOf(false) }
    ListaDeCadastro(
        carregada = estado != null,
        vazia = estado?.itens.isNullOrEmpty(),
        textoVazio = "Nenhuma meta de reserva ainda. Toque em + para adicionar.",
        aoAdicionar = { nova = true },
    ) {
        items(estado?.itens.orEmpty(), key = { it.meta.id }) { item ->
            val meta = item.meta
            val tipo = if (meta.tipo == TipoMeta.PERCENTUAL) "${formatarPercentual(meta.valor)} das entradas" else "Valor fixo"
            ListItem(
                headlineContent = { Text(meta.nome + if (meta.ativa) "" else " (pausada)") },
                supportingContent = {
                    Text(if (meta.ativa) "$tipo · ${formatarReais(item.valorNoCiclo)} neste ciclo" else "$tipo · não reserva nada enquanto pausada")
                },
                modifier = Modifier.clickable { aberta = item },
            )
        }
    }
    if (nova) FormularioMeta(null, vm, aoFechar = { nova = false })
    aberta?.let { FormularioMeta(it.meta, vm, aoFechar = { aberta = null }) }
}

/** Entrada nova começa com a data padrão do tipo: recorrente no início do ciclo, avulsa hoje. */
fun dataPadrao(tipo: TipoEntrada, cicloInicio: LocalDate, hoje: LocalDate) =
    if (tipo == TipoEntrada.RECORRENTE) cicloInicio else hoje
