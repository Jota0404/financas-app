package com.joaobarcelos.financas.domain.formato

import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Valores no formato brasileiro, sem Double: dinheiro em centavos e percentuais em pontos-base.

/** 150000 vira "R$ 1.500,00"; -10000 vira "-R$ 100,00". */
fun formatarReais(centavos: Long): String {
    val sinal = if (centavos < 0) "-" else ""
    val positivo = Math.abs(centavos)
    val reais = Math.floorDiv(positivo, 100L).toString().reversed().chunked(3).joinToString(".").reversed()
    return "${sinal}R$ $reais,${Math.floorMod(positivo, 100L).toString().padStart(2, '0')}"
}

private val formatoReais = Regex("""\d{1,3}(\.\d{3}){0,4}(,\d{1,2})?|\d{1,13}(,\d{1,2})?""")

/** Ponto seguido de 1 ou 2 dígitos no fim é centavo ("1500.50"); com 3 dígitos é milhar ("1.500"). */
private val centavosComPonto = Regex("""\d{1,13}\.\d{1,2}""")

/** Lê "1.500,00", "1500", "1500,5", "1500.50" ou "R$ 0,01" em centavos; null quando o texto não é um valor. */
fun lerCentavos(texto: String): Long? {
    val limpo = texto.replace("R$", "").trim().let { if (centavosComPonto.matches(it)) it.replace('.', ',') else it }
    if (!formatoReais.matches(limpo)) return null
    val partes = limpo.replace(".", "").split(",")
    return partes[0].toLong() * 100 + partes.getOrElse(1) { "" }.padEnd(2, '0').toLong()
}

/** 1000 vira "10%"; 1050 vira "10,5%"; 1025 vira "10,25%". */
fun formatarPercentual(pontosBase: Long): String {
    val inteiro = Math.floorDiv(pontosBase, 100L)
    val fracao = Math.floorMod(pontosBase, 100L).toString().padStart(2, '0').trimEnd('0')
    return if (fracao.isEmpty()) "$inteiro%" else "$inteiro,$fracao%"
}

/** Lê "10", "10,5", "10.5" ou "10,25%" em pontos-base; null quando o texto não é um percentual. */
fun lerPontosBase(texto: String): Long? {
    val limpo = texto.replace("%", "").trim().replace('.', ',')
    if (!Regex("""\d{1,3}(,\d{1,2})?""").matches(limpo)) return null
    val partes = limpo.split(",")
    return partes[0].toLong() * 100 + partes.getOrElse(1) { "" }.padEnd(2, '0').toLong()
}

private val formatoData = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** 2026-10-06 vira "06/10/2026". */
fun formatarData(data: LocalDate): String = data.format(formatoData)
