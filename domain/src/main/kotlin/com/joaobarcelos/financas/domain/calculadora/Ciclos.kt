package com.joaobarcelos.financas.domain.calculadora

import com.joaobarcelos.financas.domain.model.Ciclo
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

/**
 * RN01: o dia do pagamento muda para [novoDia] em [hoje]. Devolve o ciclo que contém hoje:
 * - em geral, o [atual] passa a terminar na véspera da próxima ocorrência do novo dia;
 * - se o novo dia é hoje, o [atual] termina ontem, e o ciclo devolvido começa hoje.
 */
fun mudarDiaPagamento(atual: Ciclo, novoDia: Int, hoje: LocalDate): Ciclo {
    require(hoje in atual) { "$hoje fora do ciclo atual $atual" }
    val novo = cicloDe(hoje, novoDia)
    return if (novo.inicio == hoje) novo else Ciclo(atual.inicio, novo.fim)
}
