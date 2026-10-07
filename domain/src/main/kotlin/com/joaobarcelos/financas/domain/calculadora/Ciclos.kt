package com.joaobarcelos.financas.domain.calculadora

import com.joaobarcelos.financas.domain.model.Ciclo
import com.joaobarcelos.financas.domain.model.Configuracoes
import java.time.LocalDate
import java.time.YearMonth

/** Data do pagamento em [mes]. Se o dia não existe no mês, é o último dia dele (RN02). */
fun dataDePagamento(mes: YearMonth, diaPagamento: Int): LocalDate {
    require(diaPagamento in 1..31) { "Dia do pagamento fora de 1 a 31: $diaPagamento" }
    return mes.atDay(minOf(diaPagamento, mes.lengthOfMonth()))
}

/** Ciclo que contém [data]: começa no dia do pagamento e termina na véspera do próximo (RN01, RN02). */
fun cicloDe(data: LocalDate, diaPagamento: Int): Ciclo {
    val mesDaData = YearMonth.from(data)
    val mes = if (dataDePagamento(mesDaData, diaPagamento) <= data) mesDaData else mesDaData.minusMonths(1)
    return Ciclo(
        inicio = dataDePagamento(mes, diaPagamento),
        fim = dataDePagamento(mes.plusMonths(1), diaPagamento).minusDays(1),
    )
}

/** Resultado de uma mudança do dia do pagamento: o ciclo que contém hoje e, se houver, o que fecha ontem. */
data class MudancaDiaPagamento(val atual: Ciclo, val fechado: Ciclo?)

/**
 * RN01: o dia do pagamento muda para [novoDia] em [hoje]. Em geral, o ciclo [atual] passa a terminar
 * na véspera da próxima ocorrência do novo dia. Se o novo dia é hoje, o [atual] fecha ontem e um
 * ciclo novo começa hoje; se o [atual] também começou hoje, não há o que fechar.
 */
fun mudarDiaPagamento(atual: Ciclo, novoDia: Int, hoje: LocalDate): MudancaDiaPagamento {
    require(hoje in atual) { "$hoje fora do ciclo atual $atual" }
    val novo = cicloDe(hoje, novoDia)
    return when {
        novo.inicio != hoje -> MudancaDiaPagamento(Ciclo(atual.inicio, novo.fim), fechado = null)
        atual.inicio == hoje -> MudancaDiaPagamento(novo, fechado = null)
        else -> MudancaDiaPagamento(novo, fechado = Ciclo(atual.inicio, hoje.minusDays(1)))
    }
}

/** Ciclo que contém [hoje]: o irregular guardado depois de uma mudança do dia (P14) ou o normal. */
fun Configuracoes.cicloAtual(hoje: LocalDate): Ciclo =
    cicloIrregular?.takeIf { hoje in it } ?: cicloDe(hoje, diaPagamento)

/** Configurações depois de mudar o dia do pagamento e o ciclo que fecha ontem, se houver. */
data class NovoDiaPagamento(val configuracoes: Configuracoes, val fechado: Ciclo?)

/**
 * RN01 e P14: muda o dia do pagamento para [novoDia] em [hoje]. O ciclo atual fica guardado enquanto
 * for diferente do normal do novo dia, e o ciclo que fecha vem junto (QA Etapa 3, B2).
 */
fun Configuracoes.comDiaPagamento(novoDia: Int, hoje: LocalDate): NovoDiaPagamento {
    val mudanca = mudarDiaPagamento(cicloAtual(hoje), novoDia, hoje)
    val config = copy(diaPagamento = novoDia, cicloIrregular = mudanca.atual.takeIf { it != cicloDe(hoje, novoDia) })
    return NovoDiaPagamento(config, mudanca.fechado)
}
