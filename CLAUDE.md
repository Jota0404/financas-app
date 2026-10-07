# CLAUDE.md — Regras do projeto

Fôlego, app de finanças pessoais para Android (Kotlin + Jetpack Compose).
Pacote base: `com.joaobarcelos.financas`.

## Comunicação

- Responder sempre em **português**.
- O dono do projeto não programa: explicar em linguagem simples **o que foi feito** e
  **como testar** (passo a passo, no Android Studio ou no celular).

## Fonte da verdade

- Ler `docs/briefing.md` **antes de qualquer tarefa**. Ele é a especificação oficial do app.
- Em caso de dúvida ou conflito entre o pedido e o briefing, perguntar antes de seguir.
- Regra ambígua no briefing: perguntar ao dono **antes de implementar** e registrar a decisão no
  briefing. Não deixar a escolha só como "dúvida" na mensagem de entrega.
- **Nunca mudar uma regra de negócio** sem avisar o dono e sem atualizar o `docs/briefing.md`
  na mesma entrega.
- Antes de começar uma etapa, ler o relatório mais recente do QA em `docs/qa/`.

## Forma de trabalhar

- Trabalhar **uma etapa do "Roteiro de construção" por vez**.
- Ao final de cada etapa, **parar** e esperar a aprovação do dono antes de começar a próxima.
- Não avançar de etapa com item **Crítico** ou **Alto** aberto no último relatório do QA
  (severidades no "Protocolo de entrega ao QA" do briefing).
- Ao concluir uma etapa ou corrigir itens do QA, atualizar `docs/andamento.md` no mesmo push.

## Regras técnicas obrigatórias

- **Dinheiro sempre em centavos, tipo `Long`.** Nunca `Double` ou `Float`.
- Percentuais em pontos-base (`Int`, 1000 = 10%), conforme o briefing.
- Divisão de centavos com `Math.floorDiv` (RN13): o `/` do Kotlin arredonda negativos para
  cima (`-10000 / 3` dá `-3333`, não `-3334`).
- Todo o domínio fica no módulo **`:domain`**, que **não pode depender do Android**
  (nenhum `import android.*`, nenhuma biblioteca Android, nenhuma dependência de `:app`).
- O `:domain` nunca chama `LocalDate.now()` nem lê o relógio do sistema: a data de hoje
  chega como parâmetro.
- Respeitar a direção das camadas: ui → domain → data. Tela nunca acessa banco direto.
- Toda decisão de regra de negócio (bloquear, avisar, fechar ciclo) sai pronta do `:domain`, com
  teste. Tela e repositórios só aplicam o resultado, sem "quem chama precisa lembrar de...".
- Regra que bloqueia criar ou excluir vale também para **editar**: teste o caminho de edição.
- Campos de formulário e "qual formulário está aberto" usam `rememberSaveable`, para não se
  perderem ao girar o celular.

## Testes

- Cada regra de negócio (**RN**) e cada critério de aceite (**CA**) do briefing deve ter
  **teste automatizado**, com o código no nome do teste: unitário para cálculo; instrumentado
  ou de interface só quando a regra depende do Android (hoje: RN16 e CA12).
  Exemplo: `` `RN13 divisao em centavos arredonda para baixo`() ``,
  `` `CA06 limite de 1000 reais em 3 semanas e 333,33`() ``.
- Teste de caso de erro (arquivo estragado, dado inválido) precisa provocar o erro de verdade:
  confira que ele **falha** quando a proteção é retirada.
- Testes do domínio ficam em `domain/src/test/kotlin/com/joaobarcelos/financas/domain/`.
- Em `app/src/androidTest`, nome de teste **sem espaço** (com minSdk 26 o build quebra):
  `` `CA12_registrar_gasto_em_ate_3_toques`() ``.
- Testes no aparelho (`connectedDebugAndroidTest`) **desinstalam o app no fim e apagam os dados
  dele**: rodar só no emulador, com `ANDROID_SERIAL=emulator-5554`, nunca com o celular do dono
  conectado. E esses testes usam banco em memória, nunca o banco real do app.
- Antes de cada push, rodar `/verificar` (build, testes e GitHub Actions). Gradle à mão neste Mac
  precisa de `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

## Skills do projeto

Ficam em `.claude/skills/` e servem ao desenvolvedor e ao agente de QA:

- `/verificar`: compila, roda os testes de todos os módulos e mostra o GitHub Actions do commit.
- `/testar-emulador`: abre o app no emulador (tema claro e escuro), tira prints e procura travamentos.
- `/cobertura-rn-ca`: lista quais RN e CA do briefing já têm teste e os casos de borda que faltam.

## Plugin Ponytail

- Usar o plugin Ponytail ao implementar, sempre que couber: a solução mais simples que
  funciona, com biblioteca padrão e recursos nativos antes de código próprio ou dependência nova.
- Todo atalho do Ponytail fica marcado no código com um comentário `ponytail:` dizendo o que
  foi simplificado e quando revisar. Ex.: `// ponytail: busca linear; usar índice se passar de 1.000 gastos`.
  O `/ponytail-debt` lista todos.
- **O briefing vence o Ponytail:** nunca cortar testes de RN e CA, validações, tratamento de
  erro ou requisitos do briefing para reduzir código.
