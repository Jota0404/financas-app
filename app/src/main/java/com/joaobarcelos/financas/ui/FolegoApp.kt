package com.joaobarcelos.financas.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaobarcelos.financas.ui.cadastros.CadastrosTela
import com.joaobarcelos.financas.ui.configuracoes.ConfiguracoesTela
import com.joaobarcelos.financas.ui.onboarding.PrimeiroUsoTela
import com.joaobarcelos.financas.ui.onboarding.PrimeiroUsoViewModel
import com.joaobarcelos.financas.ui.historico.HistoricoTela
import com.joaobarcelos.financas.ui.inicio.InicioTela

enum class Aba(val titulo: String, val icone: ImageVector) {
    INICIO("Início", Icons.Filled.Home),
    HISTORICO("Histórico", Icons.Filled.DateRange),
    CADASTROS("Cadastros", Icons.Filled.Edit),
    CONFIGURACOES("Configurações", Icons.Filled.Settings),
}

/** O assistente de primeiro uso (tela 1) ou as quatro abas da navegação inferior do briefing. */
@Composable
fun FolegoApp(primeiroUso: PrimeiroUsoViewModel = hiltViewModel()) {
    when (primeiroUso.mostrar.collectAsStateWithLifecycle().value) {
        null -> return // carregando
        true -> return PrimeiroUsoTela(primeiroUso)
        false -> Unit
    }
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
                Aba.INICIO -> InicioTela(aoIrParaCadastros = { aba = Aba.CADASTROS })
                Aba.HISTORICO -> HistoricoTela()
                Aba.CADASTROS -> CadastrosTela()
                Aba.CONFIGURACOES -> ConfiguracoesTela()
            }
        }
    }
}
