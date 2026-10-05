# Briefing — App de Finanças Pessoais (Android)

Versão de 05/10/2026

## Visão geral

O app responde uma pergunta, toda semana: **quanto posso gastar sem comprometer as contas fixas e a reserva?** É um controle financeiro pessoal para Android, offline, que registra entradas, contas fixas com duração, gastos do dia a dia e metas de reserva, e avisa quando o limite se aproxima.

- **Usuário:** uso pessoal (um único usuário). O MVP é estruturado para poder virar produto depois, sem retrabalho na base.
- **Objetivo paralelo:** servir de peça de portfólio, então stack, arquitetura e testes seguem o padrão que o mercado cobra.

| Papel | Faz | Não faz |
| --- | --- | --- |
| Dono do produto (Jota) | Decide escopo, aprova cada etapa, testa no celular, leva cada entrega ao QA | — |
| Briefing (Claude) | Escopo, regras de negócio, modelo de dados, arquitetura, etapas, critérios de aceite | Escrever código |
| Dev (Claude Code) | Implementação e testes unitários das regras de negócio, explicando cada decisão | Mudar regra de negócio sem atualizar o briefing |
| QA (agente) | Revisa cada entrega contra este briefing, aponta bugs e riscos de viabilidade | Reescrever features inteiras |

Este documento é o contrato entre todos os papéis. Se uma regra mudar durante o desenvolvimento, ela muda aqui primeiro.

## Stack recomendada

**Kotlin + Jetpack Compose**, a stack nativa oficial do Android. Para quem mira só Android e quer portfólio, é o que vagas de dev Android pedem por padrão, e cada peça abaixo resolve um requisito real do app.

| Camada | Tecnologia | Por que aqui |
| --- | --- | --- |
| Linguagem | Kotlin | Linguagem oficial do Android |
| Interface | Jetpack Compose + Material 3 | UI declarativa, padrão atual do mercado |
| Banco local | Room (SQLite) | Dados offline com consultas tipadas e migrações |
| Preferências | DataStore | Configurações simples (dia do pagamento, frequência de alerta) |
| Alertas agendados | WorkManager | Executa a checagem semanal/mensal mesmo com o app fechado |
| Assincronia | Coroutines + Flow | Tela reage sozinha quando um dado muda |
| Injeção de dependência | Hilt | Facilita testes e é cobrado em entrevistas |
| Testes | JUnit + Turbine + testes de Room | Valida as regras de cálculo, que são o coração do app |

**Regras técnicas obrigatórias:**

- Dinheiro é sempre armazenado em **centavos, tipo Long**. Nunca Double ou Float, por causa de erro de arredondamento.
- minSdk 26 (Android 8). No Android 13+, pedir a permissão de notificações em tempo de execução.
- Projeto versionado no GitHub desde o primeiro commit, com README. Isso é parte do portfólio.

Alternativa considerada: Flutter, que seria melhor se iOS entrasse no plano. Como o alvo é só Android, Kotlin nativo dá mais profundidade no ecossistema que o mercado Android contrata.

## Escopo do MVP

O MVP entrega o ciclo completo: cadastrar, calcular o disponível e alertar. Tudo o que não serve a esse ciclo fica para depois.

**Dentro do MVP**

- Cadastro, edição e exclusão de **entradas recorrentes** (salário) e **entradas avulsas** (um freela, por exemplo).
- Cadastro de **contas fixas** com valor, dia de vencimento e duração em meses (ou "sem fim").
- Registro de **gastos do dia a dia**, com valor, data, descrição e categoria.
- **Meta de reserva** (reserva de segurança e/ou meta de investimento) descontada antes de qualquer gasto.
- **Tela inicial** com o disponível do mês e da semana, e o percentual já consumido.
- **Alertas** por notificação: resumo periódico e aviso de limite.
- Histórico do mês atual e dos anteriores.

**Fora do MVP (versões futuras)**

- Login, nuvem própria do app e sincronização entre aparelhos. O backup do próprio Android (RN16) não conta como nuvem do app e é permitido.
- Integração com bancos (Open Finance) ou leitura de SMS/notificação bancária.
- Gráficos avançados e relatórios exportados.
- Cartão de crédito com fatura e fechamento (entra como gasto comum no MVP).
- Múltiplos usuários ou contas compartilhadas.
- Versão iOS.

## Regras de negócio

A reserva é descontada antes de tudo: o disponível nunca inclui dinheiro reservado. Cada regra tem um código (RN) para o QA referenciar.

```latex
\text{Disponível} = \text{Entradas} - \text{Contas fixas ativas} - \text{Reserva} - \text{Gastos variáveis}
```

Todos os termos são do ciclo atual, em centavos.

**Ciclo**

- **RN01** — O ciclo começa no **dia do pagamento**, configurável (padrão: dia 1), e termina na véspera do próximo.
- **RN02** — Se o dia configurado não existe no mês (ex.: 31 em abril), o ciclo começa no último dia daquele mês.

**Contas fixas**

- **RN03** — Uma conta fixa com duração de N meses está ativa nos N ciclos a partir do ciclo de início, inclusive. Depois disso, é encerrada automaticamente e para de ser descontada.
- **RN04** — Conta com duração "sem fim" fica ativa até o usuário encerrá-la manualmente.
- **RN05** — Editar o valor de uma conta afeta o ciclo atual e os futuros, nunca ciclos já fechados.

**Reserva e meta de investimento**

- **RN06** — Cada meta de reserva tem nome, tipo (valor fixo em R$ ou percentual das entradas do ciclo) e valor. Pode haver mais de uma (ex.: "Reserva de segurança" e "Investimento").
- **RN07** — A reserva é descontada **antes** dos gastos variáveis e não aparece como disponível em nenhuma tela.
- **RN08** — Ao salvar uma meta, se Entradas − Contas fixas < Reserva, o app bloqueia o salvamento e mostra quanto falta para a meta ser viável.
- **RN09** — Um gasto que deixa o disponível negativo é registrado mesmo assim (o dinheiro já saiu), mas o ciclo fica marcado como **reserva invadida**, com o valor invadido, e dispara o alerta A4.

**Limite semanal**

- **RN10** — A semana vai de segunda a domingo.
- **RN11** — Limite automático = disponível no início da semana ÷ semanas restantes no ciclo (contando a atual, arredondando para cima). O valor é calculado na segunda às 00:00 e fica fixo até domingo.
- **RN12** — O usuário pode definir um limite manual. Se ele for maior que o automático, o app avisa que o ritmo não fecha o ciclo, mas aceita.
- **RN13** — Divisões em centavos sempre arredondam **para baixo**, a favor da segurança.

**Gastos**

- **RN14** — Gasto com data retroativa é permitido dentro do ciclo atual e recalcula tudo. Data em ciclo fechado ou futura é bloqueada.
- **RN15** — Valor zero ou negativo é bloqueado em qualquer cadastro.

**Backup**

- **RN16** — Os dados do app (banco Room e DataStore) entram no backup automático do Android e são restaurados ao reinstalar o app ou trocar de celular. O backup não é obrigatório: quem liga ou desliga é o usuário, na opção de backup do Google nas configurações do celular. O app funciona igual nos dois casos.

**Alertas**

| Código | Quando dispara | Frequência máxima |
| --- | --- | --- |
| A1 | Resumo semanal: disponível da semana e do ciclo, toda segunda às 08:00 | 1 por semana |
| A2 | Gastos da semana atingem 70% do limite semanal | 1 por semana |
| A3 | Gastos da semana atingem 90% do limite semanal | 1 por semana |
| A4 | Reserva invadida (RN09) | 1 por gasto que invade |
| A5 | Resumo do ciclo no dia do pagamento: quanto sobrou e se a reserva foi cumprida | 1 por ciclo |

Os percentuais de A2 e A3 são configuráveis. O mesmo alerta nunca dispara duas vezes no mesmo período.

## Modelo de dados

Sete tabelas no Room e um arquivo de configurações no DataStore. Valores em centavos (Long), percentuais em pontos-base (Int, 1000 = 10%), datas como LocalDate.

| Entidade | Campos | Observações |
| --- | --- | --- |
| Entrada | id, descricao, valorCentavos, tipo (RECORRENTE / AVULSA), dataInicio, dataFim? | Recorrente entra em todo ciclo a partir de dataInicio; avulsa só no ciclo da sua data |
| ContaFixa | id, descricao, valorCentavos, diaVencimento, cicloInicio, duracaoMeses?, encerradaEm? | duracaoMeses nulo = sem fim (RN04) |
| Gasto | id, descricao, valorCentavos, data, categoriaId, criadoEm | categoriaId é chave estrangeira |
| Categoria | id, nome, icone | Vem com categorias padrão (Alimentação, Transporte, Lazer, Saúde, Outros) |
| MetaReserva | id, nome, tipo (VALOR / PERCENTUAL), valor, ativa | valor em centavos ou pontos-base, conforme o tipo |
| CicloFechado | id, inicio, fim, totalEntradas, totalFixas, totalReserva, totalGastos, reservaInvadidaCentavos | Retrato do ciclo no fechamento; garante a RN05 |
| RegistroAlerta | id, codigo, periodoRef, disparadoEm | Impede alerta duplicado no mesmo período |

**Configurações (DataStore):** diaPagamento, limiteSemanalManual?, percentualAtencao, percentualCritico, horaResumo.

Campos com **?** são opcionais (nuláveis).

## Arquitetura

MVVM em camadas, com as regras de negócio isoladas num domínio em Kotlin puro. Assim o QA testa os cálculos sem abrir tela nem banco, e trocar o Room por nuvem no futuro não mexe nas regras.

```text
Telas (Compose) -> ViewModels -> Domínio (casos de uso + calculadoras, RN01-RN15) -> Repositórios -> Room / DataStore
WorkManager -> Domínio (calcula) ; WorkManager -> Notificações (A1-A5)
```

Cada seta só aponta para baixo: tela nunca acessa banco direto, e o domínio não conhece nada do Android.

O domínio fica num módulo Gradle separado, **`:domain`**, em Kotlin puro (JVM, sem plugin Android). O módulo `:app` depende do `:domain`, nunca o contrário. Como o `:domain` não enxerga as bibliotecas do Android, o próprio compilador recusa um `import android.*` ali. O WorkManager usa o mesmo domínio que as telas, então o alerta e a tela inicial nunca mostram números diferentes.

```text
app/src/main/java/.../financas/       módulo :app (Android)
├── ui/          telas e ViewModels, uma pasta por funcionalidade
├── data/        entidades Room, DAOs, DataStore e repositórios
├── worker/      agendamento e disparo dos alertas
└── di/          módulos do Hilt

domain/src/main/kotlin/.../financas/domain/   módulo :domain (Kotlin puro)
├── model/       modelos do domínio
├── usecase/     casos de uso
└── calculadora/ cálculos de ciclo, reserva, disponível e limite semanal
```

Os testes do domínio ficam em `domain/src/test/kotlin/.../financas/domain/`.

## Telas e fluxos

Oito telas, com navegação inferior em quatro abas: Início, Histórico, Cadastros e Configurações. O fluxo mais usado, registrar um gasto, precisa caber em até 3 toques a partir da tela inicial.

1. **Primeiro uso (onboarding)** — assistente em passos: dia do pagamento, salário, contas fixas, metas de reserva e pedido de permissão de notificação. Só aparece uma vez.
2. **Início** — em destaque, o disponível da semana. Abaixo, uma barra de consumo (verde até 70%, amarela até 90%, vermelha acima), o disponível do ciclo e o valor protegido na reserva. Botão flutuante "+" para novo gasto.
3. **Novo gasto** — valor, descrição, categoria e data (padrão: hoje). O teclado numérico abre direto no campo valor.
4. **Contas fixas** — lista com valor, vencimento e progresso ("parcela 3 de 10" ou "sem fim").
5. **Entradas** — recorrentes e avulsas, separadas.
6. **Metas de reserva** — lista de metas com o valor efetivo no ciclo atual.
7. **Histórico** — gastos do ciclo atual agrupados por dia e, abaixo, os ciclos fechados com seus totais e a marca de reserva invadida.
8. **Configurações** — dia do pagamento, limite manual, percentuais e horário dos alertas.

As telas 4, 5 e 6 ficam dentro da aba Cadastros. Toda tela de lista tem estado vazio com orientação ("Nenhuma conta fixa ainda. Toque em + para adicionar").

## Roteiro de construção

Sete etapas, e cada uma termina numa entrega ao QA. A regra de ouro: **os cálculos são construídos e testados antes de qualquer tela**, porque um bug no cálculo contamina tudo o que vem depois.

1. **Setup** — projeto Android com Compose, Hilt e estrutura de pastas da arquitetura; repositório no GitHub com README inicial; módulo :domain separado; testes rodando no GitHub Actions a cada push.
   - Entrega ao QA: projeto compila e abre uma tela vazia.
2. **Domínio (só Kotlin puro)** — classes que calculam ciclo, contas ativas, reserva, disponível e limite semanal (RN01 a RN15), sem banco e sem tela.
   - Entrega ao QA: testes unitários passando para cada RN, incluindo os casos de borda.
3. **Persistência** — entidades do Room, DAOs, repositórios e DataStore.
   - Entrega ao QA: testes de DAO e repositório.
4. **Cadastros** — telas de contas fixas, entradas e metas de reserva, com seus ViewModels.
5. **Gastos e Início** — tela inicial, novo gasto e histórico.
6. **Alertas e fechamento de ciclo** — WorkManager, notificações A1 a A5, RegistroAlerta e geração do CicloFechado.
7. **Onboarding, configurações e acabamento** — primeiro uso, estados vazios, ícone e README final com prints e APK de release.

Não avance de etapa com bug crítico aberto pelo QA na etapa anterior.

## Critérios de aceite

Todos os casos usam o mesmo cenário base, e cada um vira um teste automatizado na etapa 2 ou 6.

**Cenário base:** salário recorrente de R$ 3.000,00; dia do pagamento 1; aluguel R$ 1.000,00 sem fim; celular R$ 200,00 por 3 meses a partir do ciclo de outubro/2026; reserva de 10% das entradas.

| Código | Regra | Situação | Resultado esperado |
| --- | --- | --- | --- |
| CA01 | RN03, RN07 | Ciclo de outubro, sem gastos | Disponível = R$ 1.500,00 |
| CA02 | RN03 | Ciclo de janeiro/2027, sem gastos | Celular encerrado; disponível = R$ 1.700,00 |
| CA03 | RN09 | Gasto de R$ 1.600,00 em outubro | Gasto salvo; disponível = −R$ 100,00; reserva invadida em R$ 100,00; alerta A4 |
| CA04 | RN08 | Criar meta de R$ 2.000,00 (valor fixo) em outubro | Salvamento bloqueado; mensagem informa que faltam R$ 200,00 |
| CA05 | RN11 | Início de outubro, 4 semanas restantes, sem gastos | Limite semanal = R$ 375,00 |
| CA06 | RN13 | Disponível de R$ 1.000,00 com 3 semanas restantes | Limite = R$ 333,33 (nunca 333,34) |
| CA07 | RN02 | Dia do pagamento 31, ciclo de abril | Ciclo começa em 30/04 |
| CA08 | A2 | Gastos da semana chegam a R$ 262,50 (70% de R$ 375,00) e depois a R$ 290,00 | A2 dispara uma única vez |
| CA09 | RN15 | Gasto de R$ 0,00 | Salvamento bloqueado |
| CA10 | RN14 | Gasto com data em ciclo já fechado | Salvamento bloqueado |
| CA11 | RN05 | Aluguel editado para R$ 1.100,00 em novembro | CicloFechado de outubro continua com R$ 1.000,00 de aluguel |
| CA12 | Telas | Registrar gasto a partir do Início | No máximo 3 toques (categoria padrão: Outros) |

Critérios gerais: o app não fecha sozinho em nenhum fluxo, funciona sem internet, e os dados sobrevivem a fechar o app e reiniciar o celular.

## Protocolo de entrega ao QA

Toda entrega ao agente de QA segue o mesmo pacote, e todo retorno do QA cita a regra (RN) ou o critério (CA) violado.

**O que você envia**

- O número da etapa do roteiro.
- O código: link do repositório e branch, ou os arquivos alterados.
- A saída dos testes (passou ou falhou, e quais).
- Um resumo do que mudou e qualquer dúvida ou desvio do briefing.

**O que o QA devolve**

| Severidade | Significado | Ação |
| --- | --- | --- |
| Crítico | Cálculo errado, perda de dados ou app fechando | Bloqueia a próxima etapa |
| Alto | Regra de negócio descumprida | Corrigir antes de avançar |
| Médio | Caso de borda sem tratamento ou teste faltando | Corrigir na etapa atual ou na seguinte |
| Baixo | Legibilidade, nomenclatura, boas práticas | Melhoria opcional |

**Modelo de mensagem para o QA**

```markdown
Etapa: [número e nome]
Briefing: [link deste documento]
Código: [link do repo/branch ou arquivos]
Testes: [resultado]
O que mudou: [resumo]
Dúvidas/desvios: [se houver]

Revise contra as RN e CA do briefing. Classifique cada problema por severidade e cite a regra afetada.
```
