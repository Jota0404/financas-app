package com.joaobarcelos.financas.domain.usecase

import com.joaobarcelos.financas.domain.CenarioBase
import com.joaobarcelos.financas.domain.calculadora.Orcamento
import com.joaobarcelos.financas.domain.calculadora.parcelaEm
import com.joaobarcelos.financas.domain.data
import com.joaobarcelos.financas.domain.model.Categoria
import com.joaobarcelos.financas.domain.model.Ciclo
import com.joaobarcelos.financas.domain.model.Configuracoes
import com.joaobarcelos.financas.domain.model.ContaFixa
import com.joaobarcelos.financas.domain.model.Entrada
import com.joaobarcelos.financas.domain.model.Gasto
import com.joaobarcelos.financas.domain.model.MetaReserva
import com.joaobarcelos.financas.domain.model.TipoEntrada
import com.joaobarcelos.financas.domain.model.TipoMeta
import com.joaobarcelos.financas.domain.repository.ConfiguracoesRepository
import com.joaobarcelos.financas.domain.repository.OrcamentoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CadastrosTest {
    private val base = CenarioBase.orcamento.let {
        // cenário base com ids, como viria do banco
        it.copy(
            entradas = listOf(CenarioBase.salario.copy(id = 1, descricao = "Salário")),
            contas = listOf(CenarioBase.aluguel.copy(id = 1, descricao = "Aluguel"), CenarioBase.celular.copy(id = 2, descricao = "Celular")),
            metas = listOf(CenarioBase.reserva.copy(id = 1, nome = "Reserva")),
        )
    }
    private val outubro = Ciclo(data(1, 10), data(31, 10))
    private val hoje = data(6, 10)
    private val conta = ContaFixa(30_000, cicloInicio = outubro.inicio, duracaoMeses = 10, descricao = "Financiamento", diaVencimento = 15)
    private val freela = Entrada(50_000, TipoEntrada.AVULSA, dataInicio = hoje, descricao = "Freela")
    private val meta = MetaReserva(TipoMeta.VALOR, 10_000, nome = "Viagem")

    private fun bloqueio(erro: ErroCadastro) = Decisao.Bloqueado(erro)

    @Test
    fun `RN15 valor zero ou negativo bloqueia conta entrada e meta`() {
        listOf(0L, -1L).forEach { valor ->
            assertEquals(bloqueio(ErroCadastro.VALOR_ZERO_OU_NEGATIVO), decidirConta(conta.copy(valorCentavos = valor), base, outubro))
            assertEquals(bloqueio(ErroCadastro.VALOR_ZERO_OU_NEGATIVO), decidirEntrada(freela.copy(valorCentavos = valor), base, outubro, hoje))
            assertEquals(bloqueio(ErroCadastro.VALOR_ZERO_OU_NEGATIVO), decidirMeta(meta.copy(valor = valor), base, outubro))
            assertEquals(
                bloqueio(ErroCadastro.VALOR_ZERO_OU_NEGATIVO),
                decidirMeta(MetaReserva(TipoMeta.PERCENTUAL, valor, nome = "Investimento"), base, outubro),
            )
        }
    }

    @Test
    fun `cadastro sem descricao e bloqueado`() {
        assertEquals(bloqueio(ErroCadastro.DESCRICAO_VAZIA), decidirConta(conta.copy(descricao = " "), base, outubro))
        assertEquals(bloqueio(ErroCadastro.DESCRICAO_VAZIA), decidirEntrada(freela.copy(descricao = ""), base, outubro, hoje))
        assertEquals(bloqueio(ErroCadastro.DESCRICAO_VAZIA), decidirMeta(meta.copy(nome = ""), base, outubro))
    }

    @Test
    fun `conta com vencimento duracao ou parcela fora do lugar e bloqueada`() {
        assertEquals(bloqueio(ErroCadastro.DIA_VENCIMENTO_INVALIDO), decidirConta(conta.copy(diaVencimento = 0), base, outubro))
        assertEquals(bloqueio(ErroCadastro.DIA_VENCIMENTO_INVALIDO), decidirConta(conta.copy(diaVencimento = 32), base, outubro))
        assertEquals(bloqueio(ErroCadastro.DURACAO_INVALIDA), decidirConta(conta.copy(duracaoMeses = 0), base, outubro))
        // parcela 11 de 10 e conta que começaria num ciclo futuro
        assertEquals(bloqueio(ErroCadastro.PARCELA_INVALIDA), decidirConta(conta.copy(cicloInicio = inicioPelaParcela(outubro, 11)), base, outubro))
        assertEquals(bloqueio(ErroCadastro.PARCELA_INVALIDA), decidirConta(conta.copy(cicloInicio = data(1, 11)), base, outubro))
    }

    @Test
    fun `RN04 editar conta antiga para duracao menor que a parcela atual e bloqueado`() {
        // QA M1: na parcela 3 de 10, encurtar para 1 ou 2 meses tiraria a conta de outubro, como excluir
        val antiga = conta.copy(id = 7, cicloInicio = inicioPelaParcela(outubro, 3))
        val comAntiga = base.copy(contas = base.contas + antiga)
        assertEquals(bloqueio(ErroCadastro.CONTA_SAIRIA_DO_CICLO), decidirConta(antiga.copy(duracaoMeses = 1), comAntiga, outubro))
        assertEquals(bloqueio(ErroCadastro.CONTA_SAIRIA_DO_CICLO), decidirConta(antiga.copy(duracaoMeses = 2), comAntiga, outubro))
        // encurtar até a parcela atual (3) mantém a conta em outubro e é permitido
        assertEquals(Decisao.Permitido, decidirConta(antiga.copy(duracaoMeses = 3), comAntiga, outubro))
    }

    @Test
    fun `RN04 editar conta que ja terminou continua permitido`() {
        // a conta de 2 meses que começou em agosto não vale mais em outubro; renomear não muda isso
        val terminada = conta.copy(id = 8, duracaoMeses = 2, cicloInicio = data(1, 8))
        val comTerminada = base.copy(contas = base.contas + terminada)
        assertEquals(Decisao.Permitido, decidirConta(terminada.copy(descricao = "Curso"), comTerminada, outubro))
    }

    @Test
    fun `P18 avulsa de ciclo fechado nao pode ser excluida`() {
        assertEquals(bloqueio(ErroCadastro.DATA_EM_CICLO_FECHADO), decidirExclusao(freela.copy(dataInicio = data(30, 9)), outubro))
        assertEquals(Decisao.Permitido, decidirExclusao(freela, outubro))
        assertEquals(Decisao.Permitido, decidirExclusao(base.entradas[0].copy(dataInicio = data(1, 1, 2020)), outubro))
    }

    @Test
    fun `RN03 conta na parcela 3 de 10 comeca 2 ciclos antes`() {
        // decisão do dono: financiamento já na 3ª parcela em outubro começou no ciclo de agosto
        val naTerceira = conta.copy(cicloInicio = inicioPelaParcela(outubro, 3))
        assertEquals(data(1, 8), naTerceira.cicloInicio)
        assertEquals(3, naTerceira.parcelaEm(outubro))
        assertEquals(10, naTerceira.parcelaEm(Ciclo(data(1, 5, 2027), data(31, 5, 2027))))
        assertEquals(Decisao.Permitido, decidirConta(naTerceira, base, outubro))
        assertNull(CenarioBase.aluguel.parcelaEm(outubro))
    }

    @Test
    fun `RN08 conta que deixa as metas sem caber salva com aviso`() {
        // 3000 - (1200 + 1700) = 100, e a reserva de 10% é 300: faltam 200
        val cara = conta.copy(valorCentavos = 170_000, duracaoMeses = null)
        assertEquals(Decisao.PermitidoComAviso(20_000), decidirConta(cara, base, outubro))
        assertEquals(Decisao.Permitido, decidirConta(conta, base, outubro))
    }

    @Test
    fun `RN08 editar a conta conta o valor novo e nao soma duas vezes`() {
        val aluguelMaisCaro = base.contas[0].copy(valorCentavos = 250_000)
        // 3000 - (2500 + 200) = 300 = reserva: cabe exatamente
        assertEquals(Decisao.Permitido, decidirConta(aluguelMaisCaro, base, outubro))
        assertEquals(Decisao.PermitidoComAviso(1), decidirConta(aluguelMaisCaro.copy(valorCentavos = 250_001), base, outubro))
    }

    @Test
    fun `RN08 sem meta ativa a conta e salva sem aviso`() {
        // decisão do dono: aluguel cadastrado antes do salário e de qualquer meta não gera aviso
        assertEquals(Decisao.Permitido, decidirConta(conta.copy(duracaoMeses = null), Orcamento(), outubro))
        val soPausada = Orcamento(metas = listOf(meta.copy(ativa = false)))
        assertEquals(Decisao.Permitido, decidirConta(conta.copy(duracaoMeses = null), soPausada, outubro))
        // com uma meta ativa de 0,01 e sem entradas, o aviso volta: 0,01 - (0 - 300) = 300,01
        val umCentavo = Orcamento(metas = listOf(meta.copy(valor = 1)))
        assertEquals(Decisao.PermitidoComAviso(30_001), decidirConta(conta.copy(duracaoMeses = null), umCentavo, outubro))
    }

    @Test
    fun `RN08 entrada que deixa as metas sem caber salva com aviso`() {
        // salário editado para 1500: 1500 - 1200 = 300 e a reserva cai para 150, então cabe
        assertEquals(Decisao.Permitido, decidirEntrada(base.entradas[0].copy(valorCentavos = 150_000), base, outubro, hoje))
        // salário de 1300: 1300 - 1200 = 100 e a reserva é 130: faltam 30
        assertEquals(Decisao.PermitidoComAviso(3_000), decidirEntrada(base.entradas[0].copy(valorCentavos = 130_000), base, outubro, hoje))
    }

    @Test
    fun `RN08 meta que nao cabe e bloqueada com quanto falta`() {
        // 1800 de folga: 300 (10%) + 1500 cabe; 1500,01 não cabe
        assertEquals(Decisao.Permitido, decidirMeta(meta.copy(valor = 150_000), base, outubro))
        assertEquals(Decisao.Bloqueado(ErroCadastro.METAS_NAO_CABEM, 1), decidirMeta(meta.copy(valor = 150_001), base, outubro))
    }

    @Test
    fun `RN08 meta pausada nao entra na soma e pode ser salva`() {
        assertEquals(Decisao.Permitido, decidirMeta(meta.copy(valor = 500_000, ativa = false), base, outubro))
    }

    @Test
    fun `RN08 editar a meta troca o valor antigo em vez de somar`() {
        // reserva editada de 10% para 60%: 1800 cabe exatamente; 61% (1830) faltam 30
        val reserva = base.metas[0]
        assertEquals(Decisao.Permitido, decidirMeta(reserva.copy(valor = 6000), base, outubro))
        assertEquals(Decisao.Bloqueado(ErroCadastro.METAS_NAO_CABEM, 3_000), decidirMeta(reserva.copy(valor = 6100), base, outubro))
    }

    @Test
    fun `meta percentual acima de 100 por cento e bloqueada`() {
        assertEquals(
            bloqueio(ErroCadastro.PERCENTUAL_ACIMA_DE_100),
            decidirMeta(MetaReserva(TipoMeta.PERCENTUAL, 10_001, nome = "Tudo"), Orcamento(), outubro),
        )
    }

    @Test
    fun `RN14 entrada avulsa so vale do inicio do ciclo ate hoje`() {
        // decisão do dono: mesma regra de data dos gastos
        assertEquals(Decisao.Permitido, decidirEntrada(freela.copy(dataInicio = data(1, 10)), base, outubro, hoje))
        assertEquals(Decisao.Permitido, decidirEntrada(freela.copy(dataInicio = hoje), base, outubro, hoje))
        assertEquals(bloqueio(ErroCadastro.DATA_EM_CICLO_FECHADO), decidirEntrada(freela.copy(dataInicio = data(30, 9)), base, outubro, hoje))
        assertEquals(bloqueio(ErroCadastro.DATA_FUTURA), decidirEntrada(freela.copy(dataInicio = data(7, 10)), base, outubro, hoje))
    }

    @Test
    fun `entrada recorrente aceita inicio no passado ou no futuro mas nao fim antes do inicio`() {
        val salario = Entrada(300_000, TipoEntrada.RECORRENTE, dataInicio = data(1, 11), descricao = "Emprego novo")
        assertEquals(Decisao.Permitido, decidirEntrada(salario, base, outubro, hoje))
        assertEquals(Decisao.Permitido, decidirEntrada(salario.copy(dataInicio = data(1, 1, 2020)), base, outubro, hoje))
        assertEquals(
            bloqueio(ErroCadastro.FIM_ANTES_DO_INICIO),
            decidirEntrada(salario.copy(dataFim = data(31, 10)), base, outubro, hoje),
        )
    }

    @Test
    fun `RN04 conta que comeca no ciclo atual pode ser excluida`() {
        assertEquals(Decisao.Permitido, decidirExclusao(conta, outubro))
    }

    @Test
    fun `RN04 conta ja descontada em ciclo anterior so pode ser encerrada`() {
        // P17: o financiamento na parcela 3 começou em agosto
        val antiga = conta.copy(cicloInicio = inicioPelaParcela(outubro, 3))
        assertEquals(bloqueio(ErroCadastro.CONTA_JA_DESCONTADA), decidirExclusao(antiga, outubro))
    }

    // Repositórios em memória, para testar que o bloqueado não chega ao banco
    private class OrcamentoEmMemoria(inicial: Orcamento) : OrcamentoRepository {
        val dados = MutableStateFlow(inicial)
        override fun orcamento() = dados
        override fun categorias() = flowOf(Categoria.PADRAO)
        override suspend fun salvar(entrada: Entrada) = 1L.also { dados.value = dados.value.copy(entradas = dados.value.entradas + entrada) }
        override suspend fun salvar(conta: ContaFixa) = 1L.also { dados.value = dados.value.copy(contas = dados.value.contas.filterNot { it.id == conta.id } + conta) }
        override suspend fun salvar(meta: MetaReserva) = 1L.also { dados.value = dados.value.copy(metas = dados.value.metas + meta) }
        override suspend fun salvar(gasto: Gasto) = 1L
        override suspend fun excluir(entrada: Entrada) {}
        override suspend fun excluir(conta: ContaFixa) { dados.value = dados.value.copy(contas = dados.value.contas - conta) }
        override suspend fun excluir(meta: MetaReserva) {}
        override suspend fun excluir(gasto: Gasto) {}
    }

    private class ConfiguracoesFixas : ConfiguracoesRepository {
        override fun configuracoes() = flowOf(Configuracoes())
        override suspend fun atualizar(mudanca: (Configuracoes) -> Configuracoes) {}
    }

    private val repositorio = OrcamentoEmMemoria(base)
    private val cadastros = Cadastros(repositorio, ConfiguracoesFixas())

    @Test
    fun `meta bloqueada nao e salva e meta que cabe e salva`() = runBlocking {
        assertEquals(Decisao.Bloqueado(ErroCadastro.METAS_NAO_CABEM, 50_000), cadastros.salvar(meta.copy(valor = 200_000), hoje))
        assertEquals(1, repositorio.orcamento().first().metas.size)
        assertEquals(Decisao.Permitido, cadastros.salvar(meta, hoje))
        assertEquals(2, repositorio.orcamento().first().metas.size)
    }

    @Test
    fun `conta com aviso e salva mesmo assim`() = runBlocking {
        val cara = conta.copy(valorCentavos = 170_000, duracaoMeses = null)
        assertEquals(Decisao.PermitidoComAviso(20_000), cadastros.salvar(cara, hoje))
        assertEquals(3, repositorio.orcamento().first().contas.size)
    }

    @Test
    fun `RN04 encerrar a conta guarda o dia e ela continua no ciclo atual`() = runBlocking {
        val aluguel = base.contas[0]
        assertEquals(bloqueio(ErroCadastro.CONTA_JA_DESCONTADA), cadastros.excluir(aluguel.copy(cicloInicio = data(1, 9)), hoje))
        cadastros.encerrar(aluguel, hoje)
        val encerrado = repositorio.orcamento().first().contas.single { it.id == aluguel.id }
        assertEquals(hoje, encerrado.encerradaEm)
        assertEquals(150_000, repositorio.orcamento().first().resumo(outubro).disponivel)
        // encerrar de novo não muda a data
        cadastros.encerrar(encerrado, data(20, 10))
        assertEquals(hoje, repositorio.orcamento().first().contas.single { it.id == aluguel.id }.encerradaEm)
    }
}
