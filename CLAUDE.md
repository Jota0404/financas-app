# CLAUDE.md — Regras do projeto

App de Finanças Pessoais para Android (Kotlin + Jetpack Compose).
Pacote base: `com.joaobarcelos.financas`.

## Comunicação

- Responder sempre em **português**.
- O dono do projeto não programa: explicar em linguagem simples **o que foi feito** e
  **como testar** (passo a passo, no Android Studio ou no celular).

## Fonte da verdade

- Ler `docs/briefing.md` **antes de qualquer tarefa**. Ele é a especificação oficial do app.
- Em caso de dúvida ou conflito entre o pedido e o briefing, perguntar antes de seguir.
- **Nunca mudar uma regra de negócio** sem avisar o dono e sem atualizar o `docs/briefing.md`
  na mesma entrega.

## Forma de trabalhar

- Trabalhar **uma etapa do "Roteiro de construção" por vez**.
- Ao final de cada etapa, **parar** e esperar a aprovação do dono antes de começar a próxima.
- Não avançar de etapa com bug crítico aberto pelo QA na etapa anterior.

## Regras técnicas obrigatórias

- **Dinheiro sempre em centavos, tipo `Long`.** Nunca `Double` ou `Float`.
- Percentuais em pontos-base (`Int`, 1000 = 10%), conforme o briefing.
- `domain/` é **Kotlin puro**: nenhum `import android.*` (nem bibliotecas Android).
- Respeitar a direção das camadas: ui → domain → data. Tela nunca acessa banco direto.

## Testes

- Cada regra de negócio (**RN**) e cada critério de aceite (**CA**) do briefing deve ter
  **teste unitário**, com o código no nome do teste.
  Exemplo: `` `RN13 divisao em centavos arredonda para baixo`() ``,
  `` `CA06 limite de 1000 reais em 3 semanas e 333,33`() ``.
- Testes do domínio ficam em `app/src/test/java/com/joaobarcelos/financas/domain/`.
