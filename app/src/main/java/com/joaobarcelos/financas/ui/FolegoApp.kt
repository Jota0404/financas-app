package com.joaobarcelos.financas.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joaobarcelos.financas.ui.cadastros.CadastrosTela

enum class Aba(val titulo: String, val icone: ImageVector) {
    INICIO("Início", Icons.Filled.Home),
    HISTORICO("Histórico", Icons.Filled.DateRange),
    CADASTROS("Cadastros", Icons.Filled.Edit),
    CONFIGURACOES("Configurações", Icons.Filled.Settings),
}

/** As quatro abas da navegação inferior do briefing. */
@Composable
fun FolegoApp() {
    var aba by rememberSaveable { mutableStateOf(Aba.INICIO) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Aba.entries.forEach {
                    NavigationBarItem(
                        selected = aba == it,
                        onClick = { aba = it },
                        icon = { Icon(it.icone, contentDescription = null) },
                        label = { Text(it.titulo) },
                    )
                }
            }
        },
    ) { espaco ->
        Box(Modifier.padding(espaco)) {
            when (aba) {
                Aba.INICIO -> EmBreve("O disponível da semana chega na Etapa 5. Comece pelos seus cadastros.") {
                    Button(onClick = { aba = Aba.CADASTROS }) { Text("Ir para Cadastros") }
                }
                Aba.HISTORICO -> EmBreve("O histórico de gastos e de ciclos chega na Etapa 5.")
                Aba.CADASTROS -> CadastrosTela()
                Aba.CONFIGURACOES -> EmBreve("As configurações chegam na Etapa 7.")
            }
        }
    }
}

// ponytail: aviso de "em breve" no lugar das abas que ainda não existem; sai quando cada tela chegar
@Composable
private fun EmBreve(texto: String, acao: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(texto, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        acao()
    }
}
