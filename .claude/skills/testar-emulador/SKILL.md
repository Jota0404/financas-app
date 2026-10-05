---
name: testar-emulador
description: Instala a versão de teste do app no emulador Android, abre o app no tema claro e no escuro, tira um print de cada tema e procura travamentos no log. Use para ver uma mudança funcionando no app de verdade (não só nos testes), na entrega de cada etapa ("o app abre?", "fecha sozinho?"), quando o dono pedir para ver uma tela ou conferir o modo escuro, e na revisão do QA. Só usa emulador, nunca o celular físico do dono.
allowed-tools: Bash(${CLAUDE_SKILL_DIR}/scripts/emulador.sh) Bash(${CLAUDE_SKILL_DIR}/scripts/emulador.sh *)
---

# Testar o app no emulador

Rode:

```bash
${CLAUDE_SKILL_DIR}/scripts/emulador.sh
```

O script:

1. Usa o emulador que estiver ligado. Se nenhum estiver, liga o primeiro emulador criado no
   Android Studio, o que pode levar 1 ou 2 minutos.
2. Gera o APK de teste e instala.
3. Abre o app no tema claro e no escuro e salva `build/emulador/claro.png` e `build/emulador/escuro.png`.
4. Confere se o app ficou na frente da tela e se o log de travamentos (`logcat -b crash`) está vazio.
5. Devolve o modo noturno do emulador ao que estava antes, mesmo se parar no meio.

O script termina com código diferente de 0 se o app não abrir ou se houver travamento.

## Depois de rodar

- **Olhe os dois prints** com a ferramenta de leitura de arquivos. O script só sabe se o app
  abriu; ele não sabe se a tela está certa. Compare com o que o briefing pede para a tela.
- **Travamento é Crítico** pela tabela de severidades do briefing (app fechando). Mostre o trecho
  do erro e o arquivo e a linha do código, se aparecerem.
- **Fluxos com vários toques** (como o CA12, registrar gasto em até 3 toques): depois do script,
  use `adb -s <serial> shell input tap X Y` e `adb -s <serial> exec-out screencap -p > passo.png`
  entre um passo e outro, e conte os toques.

## Por que só emulador

O celular do dono tem dados pessoais e a versão que ele usa no dia a dia. Uma instalação de teste
pode substituir essa versão. Instale no celular físico só se o dono pedir, e nesse caso escolha o
aparelho pelo serial (`adb devices`).
