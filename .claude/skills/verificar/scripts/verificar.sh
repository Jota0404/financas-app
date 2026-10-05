#!/usr/bin/env bash
# Compila o app, roda os testes de todos os módulos e mostra o GitHub Actions do commit atual.
# Uso: verificar.sh [tarefas extras do Gradle, ex.: lintDebug assembleRelease]
set -uo pipefail
cd "$(dirname "$0")/../../../.." || exit 2

# Este Mac não tem Java no PATH: usa o Java que vem com o Android Studio.
if ! java -version >/dev/null 2>&1; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi

# Sem resultados antigos, o Gradle roda os testes de novo e a contagem é sempre desta execução.
rm -rf ./*/build/test-results
mkdir -p build
log=build/verificar.log

echo "== Gradle: assembleDebug test $* (log completo em $log)"
# --continue: se um módulo falhar, os testes dos outros rodam mesmo assim.
./gradlew --console=plain --continue assembleDebug test "$@" >"$log" 2>&1
gradle=$?
grep -E "^e: |What went wrong|FAILED|BUILD (SUCCESSFUL|FAILED)" "$log" | head -20

echo
echo "== Testes por módulo"
for modulo in */; do
  modulo=${modulo%/}
  [ -f "$modulo/build.gradle.kts" ] || continue
  xmls=$(find "$modulo/build/test-results" -name '*.xml' 2>/dev/null)
  if [ -z "$xmls" ]; then
    echo "$modulo: 0 testes"
    continue
  fi
  # shellcheck disable=SC2086
  awk -v m="$modulo" '
    /<testsuite / {
      for (i = 1; i <= NF; i++) {
        split($i, kv, "=")
        gsub(/[">]/, "", kv[2])
        if (kv[1] == "tests") t += kv[2]
        if (kv[1] == "failures" || kv[1] == "errors") f += kv[2]
        if (kv[1] == "skipped") s += kv[2]
      }
    }
    /<testcase / { match($0, /name="[^"]*"/); nome = substr($0, RSTART + 6, RLENGTH - 7) }
    /<failure|<error / { falhas = falhas "\n   FALHOU: " nome }
    END { printf "%s: %d testes, %d falharam, %d ignorados%s\n", m, t, f, s, falhas }
  ' $xmls
done

echo
echo "== GitHub Actions"
if ! git rev-parse HEAD >/dev/null 2>&1; then
  echo "Fora de um repositório Git."
else
  sha=$(git rev-parse HEAD)
  git status --porcelain | grep -q . &&
    echo "Atenção: há mudanças não commitadas. O build acima testou o disco; o GitHub testa só o commit."
  if [ -z "$(git branch -r --contains "$sha" 2>/dev/null)" ]; then
    echo "Commit ${sha:0:7} ainda não foi enviado (push): não há GitHub Actions para ele."
  elif ! command -v gh >/dev/null 2>&1; then
    echo "gh não está instalado: confira em https://github.com/Jota0404/financas-app/actions"
  else
    runs=$(gh run list --commit "$sha" --limit 5 \
      --json databaseId,workflowName,status,conclusion,url \
      --jq '.[] | "\(.workflowName): \(.status) \(.conclusion // "") (id \(.databaseId)) \(.url)"' 2>&1)
    echo "${runs:-Nenhum run encontrado para ${sha:0:7}.}"
  fi
fi

exit "$gradle"
