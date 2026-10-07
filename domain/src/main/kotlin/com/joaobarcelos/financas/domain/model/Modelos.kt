package com.joaobarcelos.financas.domain.model

import com.joaobarcelos.financas.domain.calculadora.Resumo
import java.time.LocalDate
import java.time.LocalTime

// Nos modelos, id 0 quer dizer "ainda não foi salvo".

/** Período entre dois pagamentos, de [inicio] a [fim], os dois inclusive (RN01). */
data class Ciclo(val inicio: LocalDate, val fim: LocalDate) {
    operator fun contains(data: LocalDate) = data >= inicio && data <= fim
}

enum class TipoEntrada { RECORRENTE, AVULSA }

/**
 * Recorrente entra nos ciclos cujo primeiro dia cai entre [dataInicio] e [dataFim] (nulo = sem fim).
 * Avulsa entra só no ciclo de [dataInicio].
 */
data class Entrada(
    val valorCentavos: Long,
    val tipo: TipoEntrada,
    val dataInicio: LocalDate,
    val dataFim: LocalDate? = null,
    val descricao: String = "",
    val id: Long = 0,
)

/**
 * [cicloInicio] é o primeiro dia do primeiro ciclo da conta. [duracaoMeses] nulo é "sem fim" (RN04).
 * [encerradaEm] é o dia em que o usuário encerrou a conta. [diaVencimento] só é informativo: não entra
 * nos cálculos.
 */
data class ContaFixa(
    val valorCentavos: Long,
    val cicloInicio: LocalDate,
    val duracaoMeses: Int? = null,
    val encerradaEm: LocalDate? = null,
    val descricao: String = "",
    val diaVencimento: Int = 1,
    val id: Long = 0,
)

enum class TipoMeta { VALOR, PERCENTUAL }

/** [valor] em centavos (VALOR) ou em pontos-base, 1000 = 10% (PERCENTUAL) (RN06). */
data class MetaReserva(
    val tipo: TipoMeta,
    val valor: Long,
    val ativa: Boolean = true,
    val nome: String = "",
    val id: Long = 0,
)

/** A descrição é opcional, e a categoria padrão é Outros, para caber em 3 toques (CA12). */
data class Gasto(
    val valorCentavos: Long,
    val data: LocalDate,
    val descricao: String = "",
    val categoriaId: Long = Categoria.OUTROS,
    val id: Long = 0,
)

/** [icone] é um emoji, que o Android desenha sem biblioteca de ícones. */
data class Categoria(val id: Long, val nome: String, val icone: String) {
    companion object {
        /** Categorias que o app já traz, com ids fixos. */
        val PADRAO = listOf(
            Categoria(1, "Alimentação", "🍽️"),
            Categoria(2, "Transporte", "🚌"),
            Categoria(3, "Lazer", "🎉"),
            Categoria(4, "Saúde", "💊"),
            Categoria(5, "Outros", "📦"),
        )
        const val OUTROS = 5L
    }
}

/**
 * Retrato de um ciclo no fechamento: os totais ficam guardados, e editar um cadastro não os muda (RN05).
 * [reservaInvadida] é o valor gravado no fechamento, não recalculado (QA Etapa 3, B1).
 */
data class CicloFechado(
    val ciclo: Ciclo,
    val resumo: Resumo,
    val reservaInvadida: Long = resumo.reservaInvadida,
    val id: Long = 0,
)

/**
 * Configurações do usuário. [cicloIrregular] guarda o ciclo atual quando uma mudança do dia do
 * pagamento o deixou diferente do normal (P14); passado o fim dele, o ciclo volta a sair só de
 * [diaPagamento]. Percentuais em pontos-base: 7000 = 70%.
 */
data class Configuracoes(
    val diaPagamento: Int = 1,
    val cicloIrregular: Ciclo? = null,
    val limiteSemanalManual: Long? = null,
    val percentualAtencao: Int = 7000,
    val percentualCritico: Int = 9000,
    val horaResumo: LocalTime = LocalTime.of(8, 0),
    /** O assistente de primeiro uso foi concluído (tela 1). */
    val primeiroUsoConcluido: Boolean = false,
)
