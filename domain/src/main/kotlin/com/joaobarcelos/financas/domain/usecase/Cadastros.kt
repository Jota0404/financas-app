package com.joaobarcelos.financas.domain.usecase

import com.joaobarcelos.financas.domain.calculadora.Orcamento
import com.joaobarcelos.financas.domain.calculadora.cicloAtual
import com.joaobarcelos.financas.domain.model.Ciclo
import com.joaobarcelos.financas.domain.model.ContaFixa
import com.joaobarcelos.financas.domain.model.Entrada
import com.joaobarcelos.financas.domain.model.MetaReserva
import com.joaobarcelos.financas.domain.model.TipoEntrada
import com.joaobarcelos.financas.domain.model.TipoMeta
import com.joaobarcelos.financas.domain.repository.ConfiguracoesRepository
import com.joaobarcelos.financas.domain.repository.OrcamentoRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate

enum class ErroCadastro {
    DESCRICAO_VAZIA,
    VALOR_ZERO_OU_NEGATIVO,
    PERCENTUAL_ACIMA_DE_100,
    DIA_VENCIMENTO_INVALIDO,
    DURACAO_INVALIDA,
    PARCELA_INVALIDA,
    DATA_EM_CICLO_FECHADO,
    DATA_FUTURA,
    FIM_ANTES_DO_INICIO,
    METAS_NAO_CABEM,
    CONTA_JA_DESCONTADA,
}

/** O que fazer com um cadastro. A tela só aplica a decisão. */
sealed interface Decisao {
    data object Permitido : Decisao

    /** RN08: salva, mas avisa quanto falta para as metas caberem em Entradas − Contas fixas. */
    data class PermitidoComAviso(val faltaParaMetas: Long) : Decisao

    /** [faltaParaMetas] só é preenchido no erro METAS_NAO_CABEM (RN08). */
    data class Bloqueado(val erro: ErroCadastro, val faltaParaMetas: Long = 0) : Decisao
}

/** Troca o item de mesmo id, ou acrescenta quando ele é novo (id 0). */
private fun <T> List<T>.comItem(item: T, id: (T) -> Long) =
    filterNot { id(item) != 0L && id(it) == id(item) } + item

/** RN08: depois de salvar uma conta ou entrada, avisa se as metas deixaram de caber. Sem meta ativa, não avisa. */
private fun avisoDeMetas(depois: Orcamento, ciclo: Ciclo): Decisao {
    if (depois.metas.none { it.ativa }) return Decisao.Permitido
    val falta = depois.resumo(ciclo).faltaParaMetas
    return if (falta > 0) Decisao.PermitidoComAviso(falta) else Decisao.Permitido
}

/** Primeiro dia do ciclo em que começa uma conta que está na [parcela] no [cicloAtual]. */
fun inicioPelaParcela(cicloAtual: Ciclo, parcela: Int): LocalDate = cicloAtual.inicio.minusMonths(parcela - 1L)

/** RN15, RN03 e RN08 para salvar uma conta fixa. */
fun decidirConta(conta: ContaFixa, orcamento: Orcamento, cicloAtual: Ciclo): Decisao {
    val duracao = conta.duracaoMeses
    val erro = when {
        conta.descricao.isBlank() -> ErroCadastro.DESCRICAO_VAZIA
        !valorValido(conta.valorCentavos) -> ErroCadastro.VALOR_ZERO_OU_NEGATIVO
        conta.diaVencimento !in 1..31 -> ErroCadastro.DIA_VENCIMENTO_INVALIDO
        duracao != null && duracao < 1 -> ErroCadastro.DURACAO_INVALIDA
        // conta nova: a parcela do ciclo atual vai de 1 até a duração
        conta.id == 0L && duracao != null &&
            conta.cicloInicio !in inicioPelaParcela(cicloAtual, duracao)..cicloAtual.inicio -> ErroCadastro.PARCELA_INVALIDA
        else -> null
    }
    return erro?.let { Decisao.Bloqueado(it) }
        ?: avisoDeMetas(orcamento.copy(contas = orcamento.contas.comItem(conta) { it.id }), cicloAtual)
}

/** RN15, data da avulsa como nos gastos (RN14) e aviso da RN08 para salvar uma entrada. */
fun decidirEntrada(entrada: Entrada, orcamento: Orcamento, cicloAtual: Ciclo, hoje: LocalDate): Decisao {
    val fim = entrada.dataFim
    val erro = when {
        entrada.descricao.isBlank() -> ErroCadastro.DESCRICAO_VAZIA
        !valorValido(entrada.valorCentavos) -> ErroCadastro.VALOR_ZERO_OU_NEGATIVO
        entrada.tipo == TipoEntrada.AVULSA && entrada.dataInicio < cicloAtual.inicio -> ErroCadastro.DATA_EM_CICLO_FECHADO
        entrada.tipo == TipoEntrada.AVULSA && entrada.dataInicio > hoje -> ErroCadastro.DATA_FUTURA
        fim != null && fim < entrada.dataInicio -> ErroCadastro.FIM_ANTES_DO_INICIO
        else -> null
    }
    return erro?.let { Decisao.Bloqueado(it) }
        ?: avisoDeMetas(orcamento.copy(entradas = orcamento.entradas.comItem(entrada) { it.id }), cicloAtual)
}

/** RN15 e RN08 para salvar uma meta: se as metas ativas não cabem, bloqueia e diz quanto falta. */
fun decidirMeta(meta: MetaReserva, orcamento: Orcamento, cicloAtual: Ciclo): Decisao {
    val erro = when {
        meta.nome.isBlank() -> ErroCadastro.DESCRICAO_VAZIA
        !valorValido(meta.valor) -> ErroCadastro.VALOR_ZERO_OU_NEGATIVO
        meta.tipo == TipoMeta.PERCENTUAL && meta.valor > 10_000 -> ErroCadastro.PERCENTUAL_ACIMA_DE_100
        else -> null
    }
    if (erro != null) return Decisao.Bloqueado(erro)
    // Uma meta pausada não entra na soma, então pausar sempre é permitido
    if (!meta.ativa) return Decisao.Permitido
    val falta = orcamento.copy(metas = orcamento.metas.comItem(meta) { it.id }).resumo(cicloAtual).faltaParaMetas
    return if (falta > 0) Decisao.Bloqueado(ErroCadastro.METAS_NAO_CABEM, falta) else Decisao.Permitido
}

/** RN04 e P17: só exclui conta que ainda não foi descontada, ou seja, que começa no ciclo atual ou depois. */
fun decidirExclusao(conta: ContaFixa, cicloAtual: Ciclo): Decisao =
    if (conta.cicloInicio >= cicloAtual.inicio) Decisao.Permitido else Decisao.Bloqueado(ErroCadastro.CONTA_JA_DESCONTADA)

/**
 * Salva e exclui cadastros aplicando as decisões acima: o que é bloqueado não chega ao banco.
 * [hoje] vem de quem chama, porque o domínio não lê o relógio.
 */
class Cadastros(
    private val orcamento: OrcamentoRepository,
    private val configuracoes: ConfiguracoesRepository,
) {
    private suspend fun dados() = orcamento.orcamento().first()
    private suspend fun ciclo(hoje: LocalDate) = configuracoes.configuracoes().first().cicloAtual(hoje)

    suspend fun salvar(conta: ContaFixa, hoje: LocalDate): Decisao =
        decidirConta(conta, dados(), ciclo(hoje)).also { if (it !is Decisao.Bloqueado) orcamento.salvar(conta) }

    suspend fun salvar(entrada: Entrada, hoje: LocalDate): Decisao =
        decidirEntrada(entrada, dados(), ciclo(hoje), hoje).also { if (it !is Decisao.Bloqueado) orcamento.salvar(entrada) }

    suspend fun salvar(meta: MetaReserva, hoje: LocalDate): Decisao =
        decidirMeta(meta, dados(), ciclo(hoje)).also { if (it !is Decisao.Bloqueado) orcamento.salvar(meta) }

    suspend fun excluir(conta: ContaFixa, hoje: LocalDate): Decisao =
        decidirExclusao(conta, ciclo(hoje)).also { if (it is Decisao.Permitido) orcamento.excluir(conta) }

    /** RN04: a conta ainda conta no ciclo de hoje e sai a partir do seguinte. */
    suspend fun encerrar(conta: ContaFixa, hoje: LocalDate) {
        if (conta.encerradaEm == null) orcamento.salvar(conta.copy(encerradaEm = hoje))
    }

    suspend fun excluir(entrada: Entrada) = orcamento.excluir(entrada)
    suspend fun excluir(meta: MetaReserva) = orcamento.excluir(meta)
}
