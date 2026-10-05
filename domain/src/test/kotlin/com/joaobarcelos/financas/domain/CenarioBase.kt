package com.joaobarcelos.financas.domain

import java.time.LocalDate

/** Data na ordem do briefing: dia, mês e ano (padrão: 2026). */
fun data(dia: Int, mes: Int, ano: Int = 2026): LocalDate = LocalDate.of(ano, mes, dia)
