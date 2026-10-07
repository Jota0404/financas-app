package com.joaobarcelos.financas.domain.usecase

import com.joaobarcelos.financas.domain.calculadora.Orcamento
import com.joaobarcelos.financas.domain.model.Configuracoes
import com.joaobarcelos.financas.domain.repository.ConfiguracoesRepository
import com.joaobarcelos.financas.domain.repository.OrcamentoRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime

/**
 * Tela 8: dia de 1 a 31, limite manual positivo ou vazio (RN15), atenção de 1% a 99%, crítico de 2% a
 * 100% e atenção menor que crítico (decisão do dono).
 */
fun decidirConfiguracoes(configuracoes: Configuracoes): Decisao {
    val limite = configuracoes.limiteSemanalManual
    val erro = when {
        configuracoes.diaPagamento !in 1..31 -> ErroCadastro.DIA_PAGAMENTO_INVALIDO
        limite != null && !valorValido(limite) -> ErroCadastro.VALOR_ZERO_OU_NEGATIVO
        configuracoes.percentualAtencao !in 100..9_900 || configuracoes.percentualCritico !in 200..10_000 ->
            ErroCadastro.PERCENTUAL_FORA_DA_FAIXA
        configuracoes.percentualAtencao >= configuracoes.percentualCritico -> ErroCadastro.ATENCAO_NAO_MENOR_QUE_CRITICO
        else -> null
    }
    return erro?.let { Decisao.Bloqueado(it) } ?: Decisao.Permitido
}

/** Tela 1: o assistente aparece até ser concluído; quem já tem entradas cadastradas não o vê. */
fun mostrarPrimeiroUso(configuracoes: Configuracoes, orcamento: Orcamento): Boolean =
    !configuracoes.primeiroUsoConcluido && orcamento.entradas.isEmpty()

/** Quem agenda a rotina diária dos alertas. No app, é o WorkManager. */
fun interface Agendador {
    fun reagendar(horario: LocalTime)
}

/** Salva as configurações aplicando as decisões acima e as consequências de cada mudança. */
class Configurar(
    private val orcamento: OrcamentoRepository,
    private val configuracoes: ConfiguracoesRepository,
    private val alertas: Alertas,
    private val agendador: Agendador,
) {
    suspend fun salvar(nova: Configuracoes, hoje: LocalDate): Decisao {
        val decisao = decidirConfiguracoes(nova)
        if (decisao != Decisao.Permitido) return decisao
        val atual = configuracoes.configuracoes().first()
        // RN01 e P14: mudar o dia fecha o ciclo que termina ontem e guarda o ciclo irregular
        if (nova.diaPagamento != atual.diaPagamento) alertas.mudarDiaPagamento(nova.diaPagamento, hoje)
        configuracoes.atualizar {
            it.copy(
                limiteSemanalManual = nova.limiteSemanalManual,
                percentualAtencao = nova.percentualAtencao,
                percentualCritico = nova.percentualCritico,
                horaResumo = nova.horaResumo,
            )
        }
        if (nova.horaResumo != atual.horaResumo) agendador.reagendar(nova.horaResumo)
        return decisao
    }

    /**
     * Dia do pagamento escolhido no primeiro uso. Não é uma mudança (RN01): ainda não há ciclos, então
     * o ciclo atual já é o normal do dia escolhido, sem ciclo irregular.
     */
    suspend fun definirDiaNoPrimeiroUso(dia: Int): Decisao {
        val decisao = decidirConfiguracoes(Configuracoes(diaPagamento = dia))
        if (decisao == Decisao.Permitido) configuracoes.atualizar { it.copy(diaPagamento = dia, cicloIrregular = null) }
        return decisao
    }

    /** Fim do assistente de primeiro uso, concluído ou pulado. */
    suspend fun concluirPrimeiroUso() = configuracoes.atualizar { it.copy(primeiroUsoConcluido = true) }

    /** Se o assistente deve aparecer agora. */
    suspend fun mostrarPrimeiroUso() = mostrarPrimeiroUso(configuracoes.configuracoes().first(), orcamento.orcamento().first())
}
