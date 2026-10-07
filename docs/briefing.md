# Briefing — Fôlego, app de finanças pessoais (Android)

Versão de 05/10/2026, com as decisões do dono sobre a revisão de QA da Etapa 1 (`docs/qa/2026-10-05-etapa-1.md`, P1 a P10) sobre o limite manual em semana partida (RN12) sobre as dúvidas de cálculo do início da Etapa 2 (RN01, RN03 e RN04) e sobre a revisão de QA da Etapa 2 (`docs/qa/2026-10-05-etapa-2.md`, P12 a P16) e sobre a revisão de QA da Etapa 3 (`docs/qa/2026-10-06-etapa-3.md`, P17) e sobre as dúvidas de tela do início da Etapa 4 (cadastro de parcelas, data das entradas quando excluir uma conta fixa e o aviso da RN08 sem metas) e sobre a revisão de QA da Etapa 4 (`docs/qa/2026-10-06-etapa-4.md`, P18) e sobre as dúvidas de tela do início da Etapa 5 (editar gastos, barra com limite zero, aviso de reserva invadida, cores da barra e o que conta como toque no CA12) e sobre os alertas e o fechamento de ciclo do início da Etapa 6.

## Visão geral

O app se chama **Fôlego**: o fôlego que sobra até o próximo pagamento. Ele responde uma pergunta, toda semana: **quanto posso gastar sem comprometer as contas fixas e a reserva?** É um controle financeiro pessoal para Android, offline, que registra entradas, contas fixas com duração, gastos do dia a dia e metas de reserva, e avisa quando o limite se aproxima.

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

- **RN01** — O ciclo começa no **dia do pagamento**, configurável (padrão: dia 1), e termina na véspera do próximo. Se o dia do pagamento mudar, o ciclo atual termina na véspera da próxima ocorrência do novo dia depois de hoje (ex.: mudar de 1 para 15 em 10/10 faz o ciclo atual terminar em 14/10). Se o novo dia for o próprio dia de hoje, o ciclo atual termina ontem e um ciclo novo começa hoje (ex.: mudar de 1 para 15 em 15/10 fecha o ciclo em 14/10 e abre outro em 15/10).
- **RN02** — Se o dia configurado não existe no mês (ex.: 31 em abril), o ciclo começa no último dia daquele mês.

**Contas fixas**

- **RN03** — Uma conta fixa com duração de N meses está ativa nos N ciclos a partir do ciclo de início, inclusive. Depois disso, é encerrada automaticamente e para de ser descontada. Os ciclos são contados pelo mês em que começam: com 3 meses a partir de outubro, a conta vale em todo ciclo que começa em outubro, novembro ou dezembro. Se uma mudança do dia do pagamento (RN01) criar dois ciclos no mesmo mês, a conta é descontada nos dois, assim como o salário entra nos dois. Limitação conhecida e aceita: uma mudança do fim para o começo do mês pode pular um mês (ex.: de 31 para 1 em 05/02/2027 gera os ciclos 31/01–28/02 e 01/03–31/03, e nenhum começa em fevereiro), e aí a conta é descontada uma vez a menos.
- **RN04** — Conta com duração "sem fim" fica ativa até o usuário encerrá-la manualmente. Uma conta encerrada no meio de um ciclo ainda é descontada nele e sai a partir do ciclo seguinte, a favor da segurança, porque a conta daquele ciclo pode já ter sido paga. Na tela, uma conta fixa que já foi descontada em algum ciclo é **encerrada**, não excluída; excluir só serve para conta cadastrada por engano, ainda sem nenhum ciclo descontado, ou seja, uma conta que começa no ciclo atual ou depois. Uma conta que começou num ciclo anterior só pode ser encerrada.
- **RN05** — Editar o valor de uma conta afeta o ciclo atual e os futuros, nunca ciclos já fechados.

**Reserva e meta de investimento**

- **RN06** — Cada meta de reserva tem nome, tipo (valor fixo em R$ ou percentual das entradas do ciclo) e valor. Pode haver mais de uma (ex.: "Reserva de segurança" e "Investimento").
- **RN07** — A reserva é descontada **antes** dos gastos variáveis e não aparece como disponível em nenhuma tela.
- **RN08** — Ao salvar uma meta, se Entradas − Contas fixas < soma de todas as metas ativas (incluindo a que está sendo salva), o app bloqueia o salvamento e mostra quanto falta para a meta ser viável. Ao salvar uma conta fixa ou uma entrada que deixe as metas maiores que Entradas − Contas fixas, o app salva, mas avisa quanto falta. Sem nenhuma meta ativa não há reserva em risco, e o app salva sem aviso.
- **RN09** — Um gasto que deixa o disponível negativo é registrado mesmo assim (o dinheiro já saiu), mas o ciclo fica marcado como **reserva invadida**, com o valor invadido (quanto o disponível ficou negativo, mesmo que passe do total da reserva), e dispara o alerta A4. O valor invadido é o quanto os gastos tiraram da reserva: o menor entre o total de gastos do ciclo e o quanto o disponível ficou negativo. Sem gastos, não há reserva invadida; a falta causada por metas que não cabem é o aviso da RN08.

**Limite semanal**

- **RN10** — A semana vai de segunda a domingo.
- **RN11** — Limite automático da semana = disponível no momento do cálculo × dias da semana dentro do ciclo ÷ dias que faltam no ciclo, contando o dia do cálculo. A multiplicação vem antes da divisão, e a divisão arredonda para baixo (RN13). O cálculo acontece na segunda às 00:00, ou no primeiro dia do ciclo quando ele começa no meio da semana, e o valor fica fixo até o fim da semana ou do ciclo, o que vier antes. Se o disponível for zero ou negativo, o limite é R$ 0,00. Exemplo: ciclo de 01 a 31/10/2026 com R$ 1.500,00 disponíveis; de quinta 01/10 a domingo 04/10, o limite é R$ 1.500,00 × 4 ÷ 31 = R$ 193,54. "Fixo" quer dizer que os gastos da própria semana não reduzem o limite. Mudanças nos cadastros (entradas, contas fixas e metas) e gastos retroativos de semanas anteriores (RN14) recalculam o limite.
- **RN12** — O usuário pode definir um limite manual, que vale para uma semana cheia (7 dias). Quando só parte da semana está dentro do ciclo (no começo ou no fim dele), o limite manual é proporcional aos dias dentro do ciclo: valor × dias ÷ 7, com a divisão arredondando para baixo (RN13). Se o limite manual da semana for maior que o automático da mesma semana, o app avisa que o ritmo não fecha o ciclo, mas aceita. Exemplo: limite manual de R$ 500,00; de quinta 01/10 a domingo 04/10/2026, o limite é R$ 500,00 × 4 ÷ 7 = R$ 285,71.
- **RN13** — Divisões em centavos sempre arredondam **para baixo**, a favor da segurança. Exceção: o valor de uma meta em percentual arredonda **para cima**, para nunca reservar menos do que o percentual.

**Gastos**

- **RN14** — Gasto com data retroativa é permitido dentro do ciclo atual e recalcula tudo, inclusive o limite da semana atual quando a data é de uma semana anterior. Data em ciclo fechado ou futura é bloqueada.
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

Os percentuais de A2 e A3 são configuráveis. O mesmo alerta nunca dispara duas vezes no mesmo período. Na semana que cruza a virada do ciclo, cada parte tem seu próprio limite (RN11) e conta como um período separado para A2 e A3.

Decisões do dono sobre os alertas (início da Etapa 6):

- A permissão de notificação (Android 13+) é pedida na primeira abertura do app; na Etapa 7, o pedido passa para o passo do onboarding.
- A1 e A5 chegam por volta do horário do resumo (padrão 08:00), em geral em até 15 minutos, e mais tarde se o celular estiver economizando bateria. O A5 chega no dia do pagamento, no mesmo horário.
- A5 diz quanto sobrou do disponível e quanto foi guardado: "Reserva cumprida: R$ 300,00 guardados" ou, com invasão, "Reserva não cumprida: guardou R$ 200,00 de R$ 300,00" (reserva menos o valor invadido).
- Se o app não rodar na virada do ciclo (celular desligado), o fechamento acontece quando ele voltar: fecha todos os ciclos que terminaram, a partir do primeiro ciclo com gastos registrados, e manda um A5 só, do ciclo mais recente. Um A1 atrasado só chega se ainda for a mesma semana.
- Um gasto que pula do A2 direto para o A3 dispara só o A3, e o A2 conta como enviado naquele período.
- Com limite da semana em R$ 0,00, o A2 e o A3 não disparam; a tela Início e o A4 já avisam.
- Corrigir um gasto que passa a invadir a reserva também dispara o A4, uma vez por gasto.