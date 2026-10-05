package com.joaobarcelos.financas.domain.repository

import com.joaobarcelos.financas.domain.calculadora.Orcamento
import com.joaobarcelos.financas.domain.model.Categoria
import com.joaobarcelos.financas.domain.model.CicloFechado
import com.joaobarcelos.financas.domain.model.Configuracoes
import com.joaobarcelos.financas.domain.model.ContaFixa
import com.joaobarcelos.financas.domain.model.Entrada
import com.joaobarcelos.financas.domain.model.Gasto
import com.joaobarcelos.financas.domain.model.MetaReserva
import kotlinx.coroutines.flow.Flow
import java.time.Instant

// O domínio só conhece estas interfaces; as implementações ficam no :app, em data/ (P11).
// "salvar" cria quando o id é 0 e atualiza nos outros casos, devolvendo o id.

/** Cadastros e gastos. Os Flows emitem de novo a cada mudança, para a tela se atualizar sozinha. */
interface OrcamentoRepository {
    fun orcamento(): Flow<Orcamento>
    fun categorias(): Flow<List<Categoria>>
    suspend fun salvar(entrada: Entrada): Long
    suspend fun salvar(conta: ContaFixa): Long
    suspend fun salvar(meta: MetaReserva): Long
    suspend fun salvar(gasto: Gasto): Long
    suspend fun excluir(entrada: Entrada)
    suspend fun excluir(conta: ContaFixa)
    suspend fun excluir(meta: MetaReserva)
    suspend fun excluir(gasto: Gasto)
}

/** Ciclos fechados e registro de alertas já disparados. */
interface HistoricoRepository {
    fun ciclosFechados(): Flow<List<CicloFechado>>
    suspend fun salvar(cicloFechado: CicloFechado): Long

    /** Registra o alerta e devolve true, ou devolve false se ele já disparou nesse período. */
    suspend fun registrarAlerta(codigo: String, periodoRef: String, disparadoEm: Instant): Boolean
}

interface ConfiguracoesRepository {
    fun configuracoes(): Flow<Configuracoes>

    /** Altera as configurações de uma vez, ex.: `atualizar { it.comDiaPagamento(15, hoje) }`. */
    suspend fun atualizar(mudanca: (Configuracoes) -> Configuracoes)
}
