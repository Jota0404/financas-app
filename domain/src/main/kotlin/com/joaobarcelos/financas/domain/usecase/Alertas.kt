package com.joaobarcelos.financas.domain.usecase

import com.joaobarcelos.financas.domain.calculadora.Faixa
import com.joaobarcelos.financas.domain.calculadora.Orcamento
import com.joaobarcelos.financas.domain.calculadora.Painel
import com.joaobarcelos.financas.domain.calculadora.Resumo
import com.joaobarcelos.financas.domain.calculadora.cicloAtual
import com.joaobarcelos.financas.domain.calculadora.ciclosParaFechar
import com.joaobarcelos.financas.domain.calculadora.comDiaPagamento
import com.joaobarcelos.financas.domain.calculadora.fechar
import com.joaobarcelos.financas.domain.calculadora.painel
import com.joaobarcelos.financas.domain.formato.formatarDiaMes
import com.joaobarcelos.financas.domain.formato.formatarPercentual
import com.joaobarcelos.financas.domain.formato.formatarReais
import com.joaobarcelos.financas.domain.model.CicloFechado
import com.joaobarcelos.financas.domain.model.Gasto
import com.joaobarcelos.financas.domain.repository.ConfiguracoesRepository
import com.joaobarcelos.financas.domain.repository.HistoricoRepository
import com.joaobarcelos.financas.domain.repository.OrcamentoRepository
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class CodigoAlerta { A1, A2, A3, A4, A5 }

/** Um alerta da tabela do briefing. O mesmo [codigo] só dispara uma vez por [periodoRef]. */
sealed interface Alerta {
    val codigo: CodigoAlerta
    val periodoRef: String

    /** false: só registra o alerta, sem avisar (o A2 quando o A3 dispara junto). */
    val avisar: Boolean get() = true

    /** A1: resumo da semana do calendário que começa na segunda [segunda]. */
    data class ResumoSemanal(val segunda: LocalDate, val painel: Painel) : Alerta {
        override val codigo = CodigoAlerta.A1
        override val periodoRef = segunda.toString()
    }

    /** A2 ou A3: os gastos da semana atingiram [percentual] do limite. Cada parte da semana é um período. */
    data class LimiteDaSemana(
        override val codigo: CodigoAlerta,
        val percentual: Int,
        val painel: Painel,
        override val avisar: Boolean = true,
    ) : Alerta {
        override val periodoRef = painel.semana.inicio.toString()
    }

    /** A4: o gasto [gastoId] invadiu a reserva, que está invadida em [reservaInvadida] (um por gasto). */
    data class ReservaInvadida(val gastoId: Long, val reservaInvadida: Long) : Alerta {
        override val codigo = CodigoAlerta.A4
        override val periodoRef = "gasto-$gastoId"
    }

    /** A5: resumo do ciclo que fechou. */
    data class ResumoDoCiclo(val fechado: CicloFechado) : Alerta {
        override val codigo = CodigoAlerta.A5
        override val periodoRef = fechado.ciclo.inicio.toString()
    }
}

/** Quem mostra o alerta ao usuário. No app, é a notificação do Android. */
fun interface Notificador {
    fun avisar(alerta: Alerta)
}

/**
 * A2 e A3 da semana do [painel]: o A2 quando os gastos atingem o percentual de atenção e o A3 no
 * crítico, os mesmos pontos da barra. Decisões do dono: no salto direto para o A3, o A2 só é
 * registrado, sem aviso; com limite zero, nenhum dos dois.
 */
fun alertasDeLimite(painel: Painel, percentualAtencao: Int, percentualCritico: Int): List<Alerta> {
    if (painel.semana.valorCentavos <= 0) return emptyList()
    return when (painel.faixa) {
        Faixa.VERDE -> emptyList()
        Faixa.AMARELA -> listOf(Alerta.LimiteDaSemana(CodigoAlerta.A2, percentualAtencao, painel))
        Faixa.VERMELHA -> listOf(
            Alerta.LimiteDaSemana(CodigoAlerta.A2, percentualAtencao, painel, avisar = false),
            Alerta.LimiteDaSemana(CodigoAlerta.A3, percentualCritico, painel),
        )
    }
}

/** A4 (RN09): o gasto invadiu a reserva se ela ficou mais invadida depois dele, novo ou corrigido. */
fun alertaDeInvasao(antes: Resumo, depois: Resumo, gastoId: Long): Alerta? =
    if (depois.reservaInvadida > antes.reservaInvadida) Alerta.ReservaInvadida(gastoId, depois.reservaInvadida) else null

/** Título e texto da notificação. */
data class Mensagem(val titulo: String, val texto: String)

fun Alerta.mensagem(): Mensagem = when (this) {
    is Alerta.ResumoSemanal -> Mensagem(
        "Resumo da semana",
        "Você pode gastar ${formatarReais(painel.disponivelDaSemana)} nesta semana " +
            "(limite de ${formatarReais(painel.semana.valorCentavos)}). No ciclo: ${formatarReais(painel.resumoDoCiclo.disponivel)}.",
    )
    is Alerta.LimiteDaSemana -> Mensagem(
        "${formatarPercentual(percentual.toLong())} do limite da semana",
        "Você já gastou ${formatarReais(painel.gastosDaSemana)} de ${formatarReais(painel.semana.valorCentavos)} " +
            "nesta semana. Restam ${formatarReais(maxOf(0, painel.disponivelDaSemana))}.",
    )
    is Alerta.ReservaInvadida -> Mensagem(
        "Reserva invadida",
        "Um gasto invadiu a reserva. A reserva deste ciclo está invadida em ${formatarReais(reservaInvadida)}.",
    )
    is Alerta.ResumoDoCiclo -> {
        val resumo = fechado.resumo
        val guardado = maxOf(0, resumo.reserva - fechado.reservaInvadida)
        // decisão do dono: diz quanto sobrou e quanto da reserva foi guardado
        val reserva = when {
            resumo.reserva == 0L -> "Sem meta de reserva."
            fechado.reservaInvadida == 0L -> "Reserva cumprida: ${formatarReais(resumo.reserva)} guardados."
            else -> "Reserva não cumprida: guardou ${formatarReais(guardado)} de ${formatarReais(resumo.reserva)}."
        }
        Mensagem(
            "Ciclo de ${formatarDiaMes(fechado.ciclo.inicio)} a ${formatarDiaMes(fechado.ciclo.fim)} fechado",
            "Sobrou ${formatarReais(maxOf(0, resumo.disponivel))}. $reserva",
        )
    }
}

/**
 * Fecha ciclos (RN05) e dispara os alertas A1 a A5, cada um no máximo uma vez por período
 * (RegistroAlerta). [hoje] e [agora] vêm de quem chama, porque o domínio não lê o relógio.
 */
class Alertas(
    private val orcamento: OrcamentoRepository,
    private val configuracoes: ConfiguracoesRepository,
    private val historico: HistoricoRepository,
    private val notificador: Notificador,
) {
    /** Fecha os ciclos que já terminaram e ainda não têm retrato. Devolve os que fechou agora. */
    suspend fun fecharCiclos(hoje: LocalDate): List<CicloFechado> {
        val dados = orcamento.orcamento().first()
        val ultimo = historico.ciclosFechados().first().maxByOrNull { it.ciclo.fim }
        val pendentes = ciclosParaFechar(ultimo?.ciclo, dados.gastos.minOfOrNull { it.data }, configuracoes.configuracoes().first(), hoje)
        // O banco ignora um ciclo repetido, caso dois fechamentos rodem juntos
        return pendentes.map { dados.fechar(it) }.filter { historico.salvar(it) != -1L }
    }

    /** RN01: muda o dia do pagamento e fecha, junto, o ciclo que termina ontem (se houver). */
    suspend fun mudarDiaPagamento(novoDia: Int, hoje: LocalDate) {
        fecharCiclos(hoje)
        val novo = configuracoes.configuracoes().first().comDiaPagamento(novoDia, hoje)
        novo.fechado?.let { historico.salvar(orcamento.orcamento().first().fechar(it)) }
        configuracoes.atualizar { novo.configuracoes }
    }

    /** A2, A3 e A4 depois de salvar um gasto. [antes] são os dados de antes de salvar. */
    suspend fun depoisDoGasto(antes: Orcamento, gastoId: Long, hoje: LocalDate, agora: Instant) {
        val config = configuracoes.configuracoes().first()
        val ciclo = config.cicloAtual(hoje)
        val depois = orcamento.orcamento().first()
        val painel = depois.painel(ciclo, hoje, config)
        disparar(
            alertasDeLimite(painel, config.percentualAtencao, config.percentualCritico) +
                listOfNotNull(alertaDeInvasao(antes.resumo(ciclo), depois.resumo(ciclo), gastoId)),
            agora,
        )
    }

    /**
     * Rotina de todo dia, por volta do horário do resumo: fecha os ciclos que terminaram, manda o A5
     * do ciclo fechado mais recente (um só, mesmo atrasado), o A1 da semana, se ainda não foi, e o A2
     * ou o A3, se um cadastro novo baixou o limite e os gastos já passaram do ponto (QA Etapa 6, B3).
     */
    suspend fun rotinaDiaria(hoje: LocalDate, agora: Instant) {
        fecharCiclos(hoje)
        val maisRecente = historico.ciclosFechados().first().maxByOrNull { it.ciclo.fim }
        val config = configuracoes.configuracoes().first()
        val painel = orcamento.orcamento().first().painel(config.cicloAtual(hoje), hoje, config)
        val segunda = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        disparar(
            listOfNotNull(maisRecente?.let { Alerta.ResumoDoCiclo(it) }, Alerta.ResumoSemanal(segunda, painel)) +
                alertasDeLimite(painel, config.percentualAtencao, config.percentualCritico),
            agora,
        )
    }

    private suspend fun disparar(alertas: List<Alerta>, agora: Instant) {
        alertas.forEach { if (historico.registrarAlerta(it.codigo.name, it.periodoRef, agora) && it.avisar) notificador.avisar(it) }
    }
}
