# App de Finanças Pessoais

App Android offline que responde, toda semana: quanto posso gastar sem comprometer as contas fixas e a reserva?

Repositório: https://github.com/Jota0404/financas-app

## Stack

- **Kotlin** — linguagem
- **Jetpack Compose** (Material 3) — interface
- **Room** — banco de dados local
- **DataStore** — configurações
- **WorkManager** — alertas agendados
- **Hilt** — injeção de dependência

A especificação completa está em [`docs/briefing.md`](docs/briefing.md).

## Estrutura de pastas

```text
financas-app/
├── docs/
│   └── briefing.md            especificação oficial do app (regras RN, critérios CA)
├── app/src/main/java/com/joaobarcelos/financas/
│   ├── ui/                    telas e ViewModels, uma pasta por funcionalidade
│   │   ├── inicio/            tela inicial (disponível da semana e do ciclo)
│   │   ├── gastos/            registro de novo gasto
│   │   ├── cadastros/         contas fixas, entradas e metas de reserva
│   │   ├── historico/         gastos do ciclo e ciclos fechados
│   │   ├── configuracoes/     dia do pagamento, limites e alertas
│   │   ├── onboarding/        assistente de primeiro uso
│   │   └── theme/             cores, fontes e tema do app
│   ├── domain/                regras de negócio em Kotlin puro (sem Android)
│   │   ├── model/             modelos do domínio (Entrada, ContaFixa, Gasto...)
│   │   ├── usecase/           casos de uso (ações do app)
│   │   └── calculadora/       cálculos de ciclo, reserva, disponível e limite semanal
│   ├── data/                  acesso a dados
│   │   ├── local/entity/      tabelas do Room
│   │   ├── local/dao/         consultas ao banco (DAOs)
│   │   ├── datastore/         configurações salvas
│   │   └── repository/        ponte entre o domínio e o banco/configurações
│   ├── worker/                agendamento e disparo dos alertas (WorkManager)
│   └── di/                    módulos do Hilt
├── app/src/test/java/com/joaobarcelos/financas/
│   ├── domain/                testes unitários das regras (mesma estrutura do main)
│   └── data/                  testes de persistência (mesma estrutura do main)
├── CLAUDE.md                  regras de trabalho para o assistente de desenvolvimento
└── README.md
```
