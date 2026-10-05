# Fôlego

App de finanças pessoais para Android, offline, que responde toda semana: quanto posso gastar sem comprometer as contas fixas e a reserva?

Repositório: https://github.com/Jota0404/financas-app

## Stack

- **Kotlin** — linguagem
- **Jetpack Compose** (Material 3) — interface
- **Room** — banco de dados local
- **DataStore** — configurações
- **WorkManager** — alertas agendados
- **Hilt** — injeção de dependência

A especificação completa está em [`docs/briefing.md`](docs/briefing.md).
O andamento atual do projeto está em [`docs/andamento.md`](docs/andamento.md).

## Estrutura de pastas

```text
financas-app/
├── docs/
│   ├── briefing.md            especificação oficial do app (regras RN, critérios CA)
│   ├── andamento.md           em que etapa o projeto está e o que já foi feito
│   └── qa/                    relatórios de revisão do QA, um por entrega
├── .claude/                   configuração do assistente de desenvolvimento (Claude Code)
│   ├── agents/                agentes coder (desenvolvedor) e qa (revisor)
│   └── skills/                /verificar, /testar-emulador e /cobertura-rn-ca
├── app/src/main/java/com/joaobarcelos/financas/   módulo :app (Android)
│   ├── ui/                    telas e ViewModels, uma pasta por funcionalidade
│   │   ├── inicio/            tela inicial (disponível da semana e do ciclo)
│   │   ├── gastos/            registro de novo gasto
│   │   ├── cadastros/         contas fixas, entradas e metas de reserva
│   │   ├── historico/         gastos do ciclo e ciclos fechados
│   │   ├── configuracoes/     dia do pagamento, limites e alertas
│   │   ├── onboarding/        assistente de primeiro uso
│   │   └── theme/             cores, fontes e tema do app
│   ├── data/                  acesso a dados
│   │   ├── local/entity/      tabelas do Room
│   │   ├── local/dao/         consultas ao banco (DAOs)
│   │   ├── datastore/         configurações salvas
│   │   └── repository/        ponte entre o domínio e o banco/configurações
│   ├── worker/                agendamento e disparo dos alertas (WorkManager)
│   └── di/                    módulos do Hilt
├── app/src/test/java/com/joaobarcelos/financas/
│   └── data/                  testes de persistência (mesma estrutura do main)
├── app/src/androidTest/java/com/joaobarcelos/financas/
│   └── BackupTest.kt          teste no aparelho: backup automático ligado (RN16)
├── domain/                    módulo :domain, regras de negócio em Kotlin puro (sem Android)
│   ├── src/main/kotlin/com/joaobarcelos/financas/domain/
│   │   ├── model/             modelos do domínio (Entrada, ContaFixa, Gasto...)
│   │   ├── usecase/           casos de uso (ações do app)
│   │   └── calculadora/       cálculos de ciclo, reserva, disponível e limite semanal
│   └── src/test/kotlin/...    testes unitários das regras (mesma estrutura do main)
├── CLAUDE.md                  regras de trabalho para o assistente de desenvolvimento
└── README.md
```
