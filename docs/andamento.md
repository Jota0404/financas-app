# Andamento do projeto

Atualizado em 05/10/2026.

**Etapa atual:** 1 de 7 concluída e aprovada pelo dono. A próxima é a Etapa 2 (Domínio).

A especificação completa está em [`briefing.md`](briefing.md). Este arquivo só registra até onde o projeto chegou.

## Roteiro

| Etapa | Situação |
| --- | --- |
| 1. Setup | ✅ Concluída e aprovada em 05/10/2026 |
| 2. Domínio (só Kotlin puro, RN01 a RN15) | ⏭️ Próxima |
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
- Backup automático do Android ligado (`allowBackup`), conforme a RN16.
- GitHub Actions roda os testes unitários de todos os módulos a cada push na `main`.
- Sobras do modelo do Android Studio removidas (primeira revisão do QA, itens 1 a 12). A revisão
  completa achou mais algumas (B1 e B2), que serão limpas no começo da Etapa 2.
- Agentes `qa` e `coder` em `.claude/agents/`, e skills `/verificar`, `/testar-emulador` e
  `/cobertura-rn-ca` em `.claude/skills/`, usadas pelos dois.

**Resultado verificado:** o app compila e abre uma tela vazia num celular físico (Galaxy A56),
seguindo o tema claro ou escuro do aparelho, sem erros.

## Versões principais

| Item | Versão |
| --- | --- |
| Android Gradle Plugin | 9.4.1 |
| Kotlin | 2.2.10 |
| Compose BOM | 2026.02.01 |
| Hilt | 2.60.1 |
| core-ktx | 1.19.1 |
| activity-compose | 1.13.0 |
| compileSdk / targetSdk | 37 |

## Testes

Ainda não há testes do domínio: eles começam na Etapa 2, um para cada RN e CA do briefing.
Por enquanto, o GitHub Actions só confirma que o projeto monta sem erro.

## Pendências e observações

- Revisão completa do QA ([`qa/2026-10-05-etapa-1.md`](qa/2026-10-05-etapa-1.md)): Etapa 1
  aprovada com ressalvas, sem itens Críticos ou Altos. M1 e M2 já resolvidos (Git configurado e
  agente de QA com acesso às skills). B1, B2 e B4 a B8 ficam para o começo da Etapa 2; B3, para a
  Etapa 7. O nome do app foi confirmado como "Finanças".
- Mudança no briefing nesta etapa: criada a **RN16** (backup do Android permitido, não conta como
  nuvem do app), e o domínio passou a ser o módulo `:domain`.
- Decisões do dono sobre as propostas do QA (P1 a P10), registradas no briefing: a RN08 soma todas
  as metas (CA04 agora diz R$ 500,00); o limite semanal passa a ser proporcional aos dias que
  faltam no ciclo (RN11; CA05 agora R$ 388,88 e CA08 R$ 272,22); novos CA13 a CA15. A P11 (onde
  ficam as interfaces dos repositórios) fica a critério do desenvolvedor na Etapa 3.
- O GitHub avisou que o runner `ubuntu-latest` passa para o Ubuntu 26 a partir de 19/10/2026.
  Não exige ação agora: vale conferir se os testes continuam passando depois dessa data.

## Próximo passo

Etapa 2: primeiro, um commit de limpeza com os itens B do QA. Depois, criar no `:domain` as
classes que calculam ciclo, contas ativas, reserva, disponível e limite semanal (RN01 a RN15),
com testes unitários para cada regra e para os critérios de aceite (CA) que dependem só de cálculo.
