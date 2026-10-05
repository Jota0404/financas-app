# Andamento do projeto

Atualizado em 05/10/2026.

**Etapa atual:** 2 de 7 (Domínio) concluída e entregue ao QA. Aguarda a revisão do QA e a aprovação
do dono antes da Etapa 3.

A especificação completa está em [`briefing.md`](briefing.md). Este arquivo só registra até onde o projeto chegou.

## Roteiro

| Etapa | Situação |
| --- | --- |
| 1. Setup | ✅ Concluída e aprovada em 05/10/2026 |
| 2. Domínio (só Kotlin puro, RN01 a RN15) | 🔍 Concluída em 05/10/2026, aguardando QA e aprovação |
| 3. Persistência (Room, DAOs, DataStore) | ⏳ Não iniciada |
| 4. Cadastros | ⏳ Não iniciada |
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
| compileSdk / targetSdk | 37 |

## Testes

- **Domínio:** 74 testes unitários, todos passando, em `domain/src/test/kotlin/.../domain/`. Cada
  RN de RN01 a RN15 tem teste com o código no nome, e os critérios CA01 a CA07, CA09, CA10 e
  CA13 a CA16 estão em `CriteriosDeAceiteTest.kt`, com o cenário base do briefing
  (`CenarioBase.kt`) e os valores exatos em centavos.
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
- Atalho do Ponytail registrado no código: os modelos do domínio só têm os campos usados nos
  cálculos. Os demais campos do modelo de dados (id, descrição, categoria, nome da meta, dia de
  vencimento) entram na Etapa 3.
- O GitHub avisou que o runner `ubuntu-latest` passa para o Ubuntu 26 a partir de 19/10/2026.
  Não exige ação agora: vale conferir se os testes continuam passando depois dessa data.

## Próximo passo

Revisão do QA da Etapa 2 e aprovação do dono. Depois, a Etapa 3 (Persistência): tabelas do Room,
DAOs, repositórios e DataStore, com testes de DAO e de repositório.
