# Andamento do projeto

Atualizado em 06/10/2026.

**Etapa atual:** 4 de 7 (Cadastros) concluída e entregue ao QA. Aguarda a revisão do QA e a
aprovação do dono antes da Etapa 5.

A especificação completa está em [`briefing.md`](briefing.md). Este arquivo só registra até onde o projeto chegou.

## Roteiro

| Etapa | Situação |
| --- | --- |
| 1. Setup | ✅ Concluída e aprovada em 05/10/2026 |
| 2. Domínio (só Kotlin puro, RN01 a RN15) | ✅ Concluída e aprovada em 05/10/2026 |
| 3. Persistência (Room, DAOs, DataStore) | ✅ Concluída e aprovada em 06/10/2026 |
| 4. Cadastros | 🔍 Concluída em 06/10/2026, aguardando QA e aprovação |
| 5. Gastos e Início | ⏳ Não iniciada |
| 6. Alertas e fechamento de ciclo | ⏳ Não iniciada |
| 7. Onboarding, configurações e acabamento | ⏳ Não iniciada |

## O que já existe (Etapa 1)

- Projeto Android em Kotlin + Jetpack Compose (Material 3), minSdk 26.
- Hilt configurado (`FinancasApplication` e `MainActivity`).
- Dois módulos Gradle:
  - `:app`: o aplicativo Android (telas, dados, alertas, Hilt).
  - `:domain`: as regras de negócio, em Kotlin puro. Ele não enxerga o Android, então o
    compilador recusa qualquer `import android.*` nele. O `:app` depende do `:domain`,
    nunca o contrário.
- Estrutura de pastas da arquitetura criada, ainda vazia (ver README).
- Backup automático do Android ligado (`allowBackup`), conforme a RN16, com um teste no aparelho
  (`BackupTest`) que confere isso.
- GitHub Actions roda, a cada push na `main`, os testes unitários de todos os módulos, gera o APK
  de teste e roda o lint (`./gradlew test assembleDebug lintDebug`).
- Sobras do modelo do Android Studio removidas: itens 1 a 12 da primeira revisão do QA e, no
  começo da Etapa 2, o teste de exemplo, as cores roxas e as bibliotecas de pré-visualização
  (B1, B2 e itens 2, 4 e 5 do Ponytail). O tema usa as cores padrão do Material 3 até a Etapa 7.
- Agentes `qa` e `coder` em `.claude/agents/`, e skills `/verificar`, `/testar-emulador` e
  `/cobertura-rn-ca` em `.claude/skills/`, usadas pelos dois.

**Resultado verificado:** o app compila e abre uma tela vazia num celular físico (Galaxy A56),
seguindo o tema claro ou escuro do aparelho, sem erros.

## O que já existe (Etapa 2)

Os cálculos do app, no módulo `:domain`, sem banco e sem tela (ainda não aparecem no app):

| Arquivo | Calcula | Regras |
| --- | --- | --- |
| `model/Modelos.kt` | Ciclo, Entrada, ContaFixa, MetaReserva e Gasto | — |
| `calculadora/Ciclos.kt` | Ciclo de cada data e mudança do dia do pagamento | RN01, RN02 |
| `calculadora/Orcamento.kt` | Contas ativas, reserva, disponível, quanto falta para as metas e reserva invadida | RN03 a RN09, RN13 |
| `calculadora/LimiteSemanal.kt` | Limite da semana, automático e manual, e o aviso de ritmo | RN10 a RN13 |
| `usecase/Validacoes.kt` | Bloqueio de gasto por data e valor; valor positivo em qualquer cadastro | RN14, RN15 |

## O que já existe (Etapa 3)

Os dados do app passam a ser guardados no celular (ainda sem tela para cadastrar):

| Onde | O que faz |
| --- | --- |
| `domain/.../repository/Repositorios.kt` | O que o domínio precisa dos dados: orçamento, histórico e configurações (P11) |
| `data/local/` | Banco Room com as 7 tabelas do briefing; nasce com as 5 categorias padrão |
| `data/repository/RoomRepositorios.kt` | Salva, exclui e avisa a tela a cada mudança (Flow) |
| `data/datastore/` | Configurações: dia do pagamento, ciclo irregular (P14), limite manual, alertas |
| `di/DadosModule.kt` | Hilt: liga as interfaces do domínio às implementações |
| `app/schemas/1.json` | Esquema da versão 1 do banco, para as migrações futuras |

Os modelos do domínio ganharam os campos que faltavam (id, descrição, nome, vencimento e categoria).
A regra da P14 (qual é o ciclo atual depois de mudar o dia do pagamento) ficou no domínio.

**Backup (RN16), checagem manual no emulador:** com arquivos de teste nas pastas do banco e das
configurações, o backup do Android (`bmgr backupnow`) funcionou, e depois de desinstalar e
reinstalar o app os dois arquivos voltaram com o conteúdo original. Observação: o Android só faz
backup de um app que já foi aberto pelo menos uma vez depois de instalado.

## O que já existe (Etapa 4)

As primeiras telas. O app abre com a navegação de baixo nas quatro abas do briefing; Início,
Histórico e Configurações ainda mostram em que etapa chegam. A aba **Cadastros** tem três telas:

| Tela | O que faz |
| --- | --- |
| Contas fixas | Lista com valor, vencimento e situação ("Parcela 3 de 10", "Sem fim", "Terminou", "Encerrada"). Cadastrar, editar, encerrar (sempre) e excluir (só a conta que começa no ciclo atual, RN04/P17) |
| Entradas | Recorrentes e avulsas separadas. A recorrente vem com o início do ciclo atual; a avulsa, com hoje, e só aceita datas do ciclo atual até hoje |
| Metas de reserva | Valor efetivo no ciclo atual (ex.: "10% das entradas · R$ 300,00 neste ciclo"). Pausar com a chave "Ativa" |

As decisões saem prontas do domínio (`usecase/Cadastros.kt`), e a tela só aplica:
- RN15 em todo cadastro, mais nome obrigatório, vencimento de 1 a 31, duração e parcela válidas;
- RN08: a meta que não cabe é bloqueada com quanto falta (CA04: "faltam R$ 500,00"); a conta ou
  entrada que deixa as metas sem caber é salva com aviso; sem meta ativa, não há aviso.

Valores em reais e percentuais são lidos e mostrados no formato brasileiro, sem `Double`
(`domain/.../formato/Formatos.kt`). Toda lista vazia orienta: "Nenhuma conta fixa ainda. Toque
em + para adicionar."

**Visto no emulador (Android 17), nos temas claro e escuro:** cenário base cadastrado pela tela
(aluguel, celular de 3 meses, salário e reserva de 10%); a meta de R$ 2.000,00 foi bloqueada com
"faltam R$ 500,00"; nenhum travamento no log.

## Versões principais

| Item | Versão |
| --- | --- |
| Android Gradle Plugin | 9.4.1 |
| Gradle | 9.8.0 |
| Kotlin | 2.4.20 |
| Compose BOM | 2026.09.00 |
| Hilt | 2.60.1 |
| core-ktx | 1.19.1 |
| activity-compose | 1.13.0 |
| Room | 2.8.5 |
| DataStore | 1.2.1 |
| Lifecycle (Compose) | 2.11.0 |
| Hilt para Compose | 1.4.0 |
| Robolectric (testes) | 4.17 |
| compileSdk / targetSdk | 37 |

## Testes

- **Domínio:** 106 testes unitários, todos passando, em `domain/src/test/kotlin/.../domain/`. Cada
  RN de RN01 a RN15 tem teste com o código no nome, e os critérios CA01 a CA07, CA09, CA10 e
  CA13 a CA16 estão em `CriteriosDeAceiteTest.kt`, com o cenário base do briefing
  (`CenarioBase.kt`) e os valores exatos em centavos.
- **App:** 26 testes em `app/src/test/`: 17 de persistência (banco, repositórios e DataStore) e 9
  de tela da aba Cadastros (estados vazios, parcela, CA04 bloqueado na tela, aviso da RN08,
  encerrar e excluir conta, RN15). Rodam no computador com Robolectric, sem aparelho, e por isso
  também no GitHub Actions.
- **No aparelho:** 1 teste instrumentado (`BackupTest`, RN16). Ele roda no emulador, com
  `./gradlew connectedDebugAndroidTest`, e não roda no GitHub Actions, porque precisa de um aparelho.
- **Ainda sem teste, como previsto:** CA08 e CA11 (Etapa 6, alertas e fechamento de ciclo) e CA12
  (Etapa 5, telas).

## Pendências e observações

- Revisão completa do QA ([`qa/2026-10-05-etapa-1.md`](qa/2026-10-05-etapa-1.md)): Etapa 1
  aprovada com ressalvas, sem itens Críticos ou Altos. M1 e M2 já resolvidos (Git configurado e
  agente de QA com acesso às skills). B4 resolvido: o app passou a se chamar **Fôlego**.
  B1, B2, B5 a B8 e os itens 2, 4 e 5 do Ponytail foram resolvidos no começo da Etapa 2.
  B3 (clarão branco ao abrir no modo escuro) e o item 3 do Ponytail (ícones `.webp` sem uso)
  ficam para a Etapa 7.
- Mudança no briefing nesta etapa: criada a **RN16** (backup do Android permitido, não conta como
  nuvem do app), e o domínio passou a ser o módulo `:domain`.
- Decisões do dono sobre as propostas do QA (P1 a P10), registradas no briefing: a RN08 soma todas
  as metas (CA04 agora diz R$ 500,00); o limite semanal passa a ser proporcional aos dias que
  faltam no ciclo (RN11; CA05 agora R$ 388,88 e CA08 R$ 272,22); novos CA13 a CA15. Depois, o
  limite manual (RN12) também passou a ser proporcional nas semanas partidas do começo e do fim
  do ciclo (novo CA16: R$ 500,00 viram R$ 285,71 de 01 a 04/10). A P11 (onde
  ficam as interfaces dos repositórios) fica a critério do desenvolvedor na Etapa 3.
- Decisões do dono no começo da Etapa 2, registradas no briefing: mudar o dia do pagamento para o
  próprio dia de hoje fecha o ciclo ontem e abre um novo hoje (RN01); a duração de uma conta fixa
  conta os ciclos pelo mês em que começam (RN03); uma conta encerrada no meio do ciclo ainda é
  descontada nele e sai a partir do seguinte (RN04).
- A RN05 está testada só na parte de cálculo: editar o valor de uma conta muda o ciclo atual e os
  futuros. A parte "nunca muda ciclos já fechados" depende do retrato do ciclo (CicloFechado), que
  é criado na Etapa 6 e testado pelo CA11.
- Revisão do QA da Etapa 2 ([`qa/2026-10-05-etapa-2.md`](qa/2026-10-05-etapa-2.md)): aprovada com
  ressalvas, sem itens Críticos ou Altos. Corrigidos M1 (mudança do dia do pagamento no primeiro
  dia do ciclo agora diz qual ciclo fecha), M2 e M3 (conforme as decisões P12 e P13) e B3. O B1
  (bloquear ou só avisar ao salvar meta, no domínio) fica para a Etapa 4.
- Decisões do dono sobre P12 a P16, registradas no briefing: reserva invadida só por gasto (RN09);
  salário recorrente a partir do primeiro pagamento; `inicioCicloAtual` e `fimCicloAtual` nas
  configurações depois de uma mudança do dia do pagamento (o dono escolheu guardar também o fim,
  porque só o início não identifica o ciclo); limitação aceita da mudança que pula um mês (RN03);
  o que "fixo" quer dizer no limite semanal (RN11).
- Revisão do QA da Etapa 3 ([`qa/2026-10-06-etapa-3.md`](qa/2026-10-06-etapa-3.md)): aprovada com
  ressalvas, sem itens Críticos ou Altos. O M1 (salvar configurações com o arquivo do DataStore
  corrompido) foi resolvido no começo da Etapa 4; B1 e B2 ficam para a Etapa 6. Decisão do dono sobre a P17,
  registrada no briefing (RN04): conta fixa que já foi descontada é encerrada, não excluída;
  excluir só serve para conta cadastrada por engano.
- O B1 da Etapa 2 (o domínio decide se bloqueia ou só avisa ao salvar meta) foi feito na Etapa 4,
  e o CA04 passou a conferir o bloqueio.
- Decisões do dono no começo da Etapa 4, registradas no briefing: conta com duração informa a
  parcela do ciclo atual; entrada recorrente vem com o início do ciclo atual; entrada avulsa só
  aceita datas do ciclo atual até hoje (como a RN14); só se exclui conta que começa no ciclo atual;
  sem meta ativa, salvar conta ou entrada não avisa (RN08).
- Atalhos do Ponytail: `FinancasDao.kt:30` carrega os gastos de todos os ciclos (filtrar por data
  se o histórico pesar); `FolegoApp.kt:70` mostra "em breve" nas abas que ainda não existem (sai
  quando cada tela chegar).
- O GitHub avisou que o runner `ubuntu-latest` passa para o Ubuntu 26 a partir de 19/10/2026.
  Não exige ação agora: vale conferir se os testes continuam passando depois dessa data.

## Próximo passo

Revisão do QA da Etapa 4 e aprovação do dono. Depois, a Etapa 5 (Gastos e Início): tela inicial
com o disponível da semana, novo gasto em até 3 toques (CA12) e histórico.
