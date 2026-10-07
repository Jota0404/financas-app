package com.joaobarcelos.financas.domain.usecase

import com.joaobarcelos.financas.domain.Ambiente
import com.joaobarcelos.financas.domain.CenarioBase
import com.joaobarcelos.financas.domain.data
import com.joaobarcelos.financas.domain.model.Ciclo
import com.joaobarcelos.financas.domain.model.Gasto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AlertasTest {
    private val ambiente = Ambiente(CenarioBase.orcamento)
    private val agora = Instant.parse("2026-10-05T12:00:00Z")
    private val segunda = data(5, 10)

    private fun gastar(centavos: Long, dia: LocalDate = segunda, hoje: LocalDate = dia, id: Long = 0) =
        runBlocking { ambiente.cadastros.salvar(Gasto(centavos, dia, id = id), hoje, agora) }

    private fun codigos() = ambiente.notificador.avisos.map { it.codigo }

    @Test
    fun `CA08 A2 dispara uma unica vez na semana`() {
        // semana de 05/10, limite 388,88: 272,21 ainda não atinge 70%; 272,22 atinge; depois 300,00
        gastar(27_221)
        assertTrue(codigos().isEmpty())
        gastar(1)
        assertEquals(listOf(CodigoAlerta.A2), codigos())
        gastar(2_778)
        assertEquals(30_000, runBlocking { ambiente.orcamento.orcamento().first().gastos.sumOf { it.valorCentavos } })
        assertEquals(listOf(CodigoAlerta.A2), codigos())
        val mensagem = ambiente.notificador.avisos.single().mensagem()
        assertEquals("70% do limite da semana", mensagem.titulo)
        assertEquals("Você já gastou R$ 272,22 de R$ 388,88 nesta semana. Restam R$ 116,66.", mensagem.texto)
    }

    @Test
    fun `A3 dispara ao atingir 90 por cento depois do A2`() {
        gastar(27_222)
        gastar(7_778) // 350,00 de 388,88: 90% é 349,992
        assertEquals(listOf(CodigoAlerta.A2, CodigoAlerta.A3), codigos())
        gastar(1_000)
        assertEquals(listOf(CodigoAlerta.A2, CodigoAlerta.A3), codigos())
    }

    @Test
    fun `no salto direto para 90 por cento so o A3 chega e o A2 conta como enviado`() {
        // decisão do dono
        gastar(37_000)
        assertEquals(listOf(CodigoAlerta.A3), codigos())
        assertTrue(ambiente.historico.alertas.contains("A2" to "2026-10-05"))
    }

    @Test
    fun `cada parte da semana partida e um periodo separado para A2 e A3`() {
        // 01 a 04/10: limite 193,54; 70% = 135,478
        gastar(13_548, dia = data(2, 10))
        assertEquals(listOf(CodigoAlerta.A2), codigos())
        // semana de 05/10: limite (1500 - 135,48) x 7 / 27 = 353,75; 70% = 247,62 dispara outro A2
        gastar(25_000, dia = segunda)
        assertEquals(listOf(CodigoAlerta.A2, CodigoAlerta.A2), codigos())
        assertEquals(listOf("2026-10-01", "2026-10-05"), ambiente.notificador.avisos.map { it.periodoRef })
    }

    @Test
    fun `com limite zero A2 e A3 nao disparam`() {
        // decisão do dono: CA14, o disponível acabou em 02/10
        gastar(160_000, dia = data(2, 10), hoje = data(2, 10))
        ambiente.notificador.avisos.clear()
        gastar(1_000)
        assertEquals(listOf(CodigoAlerta.A4), codigos())
    }

    @Test
    fun `CA03 gasto que invade a reserva dispara o A4 uma vez`() {
        gastar(160_000, dia = data(10, 10), hoje = data(10, 10))
        val a4 = ambiente.notificador.avisos.filterIsInstance<Alerta.ReservaInvadida>().single()
        assertEquals(10_000, a4.reservaInvadida)
        assertEquals("Um gasto invadiu a reserva. A reserva deste ciclo está invadida em R$ 100,00.", a4.mensagem().texto)
    }

    @Test
    fun `A4 chega uma vez por gasto e tambem ao corrigir um gasto`() {
        // decisão do dono: corrigir 45,00 para 1.600,00 invade a reserva e dispara o A4
        gastar(4_500, dia = data(2, 10), hoje = data(2, 10))
        val id = runBlocking { ambiente.orcamento.orcamento().first().gastos.single().id }
        gastar(160_000, dia = data(2, 10), hoje = data(2, 10), id = id)
        assertEquals(1, ambiente.notificador.avisos.count { it.codigo == CodigoAlerta.A4 })
        // corrigir de novo o mesmo gasto não manda outro A4
        gastar(170_000, dia = data(2, 10), hoje = data(2, 10), id = id)
        assertEquals(1, ambiente.notificador.avisos.count { it.codigo == CodigoAlerta.A4 })
        // um gasto novo que aprofunda a invasão manda outro
        gastar(1_000, dia = data(3, 10), hoje = data(3, 10))
        assertEquals(2, ambiente.notificador.avisos.count { it.codigo == CodigoAlerta.A4 })
    }

    @Test
    fun `A1 chega uma vez por semana com o disponivel da semana e do ciclo`() = runBlocking {
        ambiente.alertas.rotinaDiaria(segunda, agora)
        ambiente.alertas.rotinaDiaria(data(6, 10), agora)
        val a1 = ambiente.notificador.avisos.filterIsInstance<Alerta.ResumoSemanal>().single()
        assertEquals(
            "Você pode gastar R$ 388,88 nesta semana (limite de R$ 388,88). No ciclo: R$ 1.500,00.",
            a1.mensagem().texto,
        )
        ambiente.alertas.rotinaDiaria(data(12, 10), agora)
        assertEquals(2, ambiente.notificador.avisos.count { it.codigo == CodigoAlerta.A1 })
    }

    @Test
    fun `A1 atrasado so chega se ainda for a mesma semana`() = runBlocking {
        // a rotina de segunda não rodou; na quarta ainda chega o A1 dessa semana, uma vez
        ambiente.alertas.rotinaDiaria(data(7, 10), agora)
        assertEquals(listOf("2026-10-05"), ambiente.notificador.avisos.filterIsInstance<Alerta.ResumoSemanal>().map { it.periodoRef })
    }

    @Test
    fun `A5 fecha o ciclo no dia do pagamento e diz quanto sobrou e guardou`() = runBlocking {
        gastar(160_000, dia = data(10, 10), hoje = data(10, 10)) // CA03: reserva invadida em 100
        ambiente.notificador.avisos.clear()
        ambiente.alertas.rotinaDiaria(data(1, 11), agora)
        val a5 = ambiente.notificador.avisos.filterIsInstance<Alerta.ResumoDoCiclo>().single()
        assertEquals(Ciclo(data(1, 10), data(31, 10)), a5.fechado.ciclo)
        assertEquals("Ciclo de 01/10 a 31/10 fechado", a5.mensagem().titulo)
        assertEquals("Sobrou R$ 0,00. Reserva não cumprida: guardou R$ 200,00 de R$ 300,00.", a5.mensagem().texto)
        // a rotina do dia seguinte não repete
        ambiente.alertas.rotinaDiaria(data(2, 11), agora)
        assertEquals(1, ambiente.notificador.avisos.count { it.codigo == CodigoAlerta.A5 })
    }

    @Test
    fun `A5 com a reserva cumprida`() = runBlocking {
        gastar(50_000, dia = data(10, 10), hoje = data(10, 10))
        ambiente.alertas.rotinaDiaria(data(1, 11), agora)
        val a5 = ambiente.notificador.avisos.filterIsInstance<Alerta.ResumoDoCiclo>().single()
        assertEquals("Sobrou R$ 1.000,00. Reserva cumprida: R$ 300,00 guardados.", a5.mensagem().texto)
    }

    @Test
    fun `com o app parado na virada fecha tudo e manda um A5 so do ciclo mais recente`() = runBlocking {
        // decisão do dono: primeiro gasto em outubro, app volta a rodar em 03/12
        gastar(1_000, dia = data(10, 10), hoje = data(10, 10))
        ambiente.alertas.rotinaDiaria(data(3, 12), agora)
        assertEquals(2, ambiente.historico.fechados.value.size)
        val a5 = ambiente.notificador.avisos.filterIsInstance<Alerta.ResumoDoCiclo>().single()
        assertEquals(Ciclo(data(1, 11), data(30, 11)), a5.fechado.ciclo)
    }

    @Test
    fun `CA11 ciclo fechado de outubro continua com 1000 reais de aluguel depois da edicao`() = runBlocking {
        gastar(1_000, dia = data(10, 10), hoje = data(10, 10))
        ambiente.alertas.fecharCiclos(data(1, 11))
        // aluguel editado para 1.100,00 em novembro
        val aluguel = ambiente.orcamento.orcamento().first().contas.single { it.valorCentavos == 100_000L }
        ambiente.cadastros.salvar(aluguel.copy(valorCentavos = 110_000), data(5, 11))
        val outubro = ambiente.historico.fechados.value.single()
        assertEquals(120_000, outubro.resumo.fixas) // aluguel 1000 + celular 200
        assertEquals(150_000, outubro.resumo.disponivel + outubro.resumo.gastos)
    }

    @Test
    fun `fechar duas vezes nao duplica o ciclo`() = runBlocking {
        gastar(1_000, dia = data(10, 10), hoje = data(10, 10))
        ambiente.alertas.fecharCiclos(data(1, 11))
        assertTrue(ambiente.alertas.fecharCiclos(data(1, 11)).isEmpty())
        assertEquals(1, ambiente.historico.fechados.value.size)
    }

    @Test
    fun `RN01 mudar o dia do pagamento fecha o ciclo que termina ontem`() = runBlocking {
        // QA Etapa 3, B2: 1 -> 15 em 15/10 fecha 01/10 a 14/10 e grava o dia novo
        ambiente.alertas.mudarDiaPagamento(15, data(15, 10))
        assertEquals(listOf(Ciclo(data(1, 10), data(14, 10))), ambiente.historico.fechados.value.map { it.ciclo })
        assertEquals(15, ambiente.configuracoes.dados.value.diaPagamento)
    }
}
