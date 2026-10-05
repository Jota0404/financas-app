---
name: verificar
description: Compila o app Android (assembleDebug), roda os testes unitários de todos os módulos (:app e :domain), conta quantos passaram ou falharam e mostra o resultado do GitHub Actions do commit atual. Use antes de todo commit ou push, ao terminar uma tarefa de código, ao entregar uma etapa ao QA, nos passos 2 e 3 da revisão do QA, e sempre que alguém perguntar se "está compilando", se "os testes passam" ou se "o GitHub ficou verde".
argument-hint: "[tarefas extras do Gradle, ex.: lintDebug]"
allowed-tools: Bash(${CLAUDE_SKILL_DIR}/scripts/verificar.sh) Bash(${CLAUDE_SKILL_DIR}/scripts/verificar.sh *) Bash(gh run watch *) Bash(gh run view *)
---

# Verificar build, testes e GitHub Actions

Rode:

```bash
${CLAUDE_SKILL_DIR}/scripts/verificar.sh $ARGUMENTS
```

Tarefas extras do Gradle entram como argumentos: `lintDebug` roda o lint, e `assembleRelease`
gera a versão de publicação (com R8).

O script cuida de três armadilhas deste projeto, para você não precisar lembrar delas:

- **Java:** este Mac não tem Java no PATH, então o script usa o Java do Android Studio.
- **Contagem velha:** ele apaga os resultados de teste anteriores antes de rodar. Assim, o
  número de testes é sempre desta execução, e não de uma rodada antiga que o Gradle reaproveitou.
- **Falha que esconde falha:** com `--continue`, um módulo quebrado não impede os testes dos outros.

O log completo fica em `build/verificar.log`. O script termina com código diferente de 0 se o
build ou algum teste falhar.

## Como ler o resultado

- **"0 testes"** num módulo quer dizer que ele não tem testes. Na Etapa 1 isso é normal. Da Etapa 2
  em diante, o `:domain` com 0 testes é um problema.
- **Falha de compilação:** procure `e:` (erro do Kotlin) ou `What went wrong` em `build/verificar.log`.
- **GitHub Actions `in_progress`:** acompanhe com `gh run watch <id> --exit-status`. Quando aparece
  "ainda não foi enviado", é porque não houve push e por isso não existe run.
- **Mudanças não commitadas:** o build local testa os arquivos do disco, enquanto o GitHub testa o
  último commit. Os dois resultados podem ser diferentes até o próximo push.

## Ao relatar

O dono não programa. Diga em uma frase se compilou, quantos testes passaram e se o GitHub ficou
verde. Se algo falhou, diga qual teste falhou e o que isso significa para o app. O agente de QA
copia os números exatos para o relatório.
