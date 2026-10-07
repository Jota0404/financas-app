---
name: cobertura-rn-ca
description: Mostra quais regras de negócio (RN) e critérios de aceite (CA) do docs/briefing.md já têm teste com o código no nome, e lista os casos de borda que costumam faltar (dia 31, fevereiro, virada de ano, semana que começa no meio do ciclo, valores zero ou negativos). Use ao escrever ou revisar testes do domínio, antes de entregar uma etapa, no passo 5 da revisão do QA, e sempre que alguém perguntar "quais regras estão testadas?" ou "falta teste de alguma RN?".
allowed-tools: Bash(${CLAUDE_SKILL_DIR}/scripts/cobertura.sh)
---

# Cobertura de testes das RN e CA

Rode:

```bash
${CLAUDE_SKILL_DIR}/scripts/cobertura.sh
```

O script lê os códigos direto do `docs/briefing.md`, então acompanha o briefing quando ele muda.
Ele conta só onde cada código é definido (RN em negrito, CA na tabela de critérios), mostra quantos
leu e para com aviso se não achar nenhum RN ou CA, porque isso indica um briefing cortado.
Depois procura testes com o código no nome em `domain/src/test`, `app/src/test` e
`app/src/androidTest`. Ele reconhece os dois formatos de nome de teste do CLAUDE.md:
`` `RN13 divisao ...`() `` e `RN16_backup_...()`.

## O que é esperado em cada etapa

Use o "Roteiro de construção" e os "Critérios de aceite" do briefing para decidir quais códigos já
deveriam ter teste. Um código sem teste só é problema se a etapa atual ou uma anterior já devia
cobri-lo. Por exemplo, a Etapa 2 cobre as RN de cálculo e os CA que dependem só de cálculo, e o
CA12 depende das telas da Etapa 5. Quando o briefing não deixar claro em que etapa um código entra,
pergunte em vez de supor.

## Ter o código no nome não basta

O script só confere nomes. Para cada teste encontrado, abra o arquivo e compare com o texto da regra
no briefing: o teste usa os valores do briefing? Confere o resultado exato, em centavos? Um teste com
o nome certo e a conta errada é pior do que nenhum teste, porque dá falsa segurança.

Os CA usam o mesmo "cenário base" do briefing. Monte esse cenário uma vez, num arquivo compartilhado
dos testes do domínio, em vez de repetir os valores em cada teste.

## Casos de borda que costumam faltar

Uma regra com um teste só, quase sempre, testa só o caminho feliz. Confira:

- **Datas:** dia do pagamento 29, 30 e 31 em meses curtos; fevereiro com e sem ano bissexto (2028
  é bissexto); virada de ano (ciclo de dezembro para janeiro); ciclo que começa no meio da semana;
  primeiro e último dia do ciclo.
- **Dinheiro:** valor zero e negativo; divisão com resto (arredonda para baixo, RN13); disponível
  negativo (o `/` do Kotlin arredonda negativos para cima, então use `Math.floorDiv`).
- **Contas fixas:** primeiro e último ciclo da duração; conta "sem fim"; conta encerrada à mão.
- **Alertas:** valor exatamente no limite (70% cravado) e segunda vez no mesmo período.

## Ao relatar

Diga quantos códigos esperados para a etapa têm teste, liste os que faltam e aponte os casos de borda
sem teste. No relatório do QA, teste faltando é severidade Médio.
