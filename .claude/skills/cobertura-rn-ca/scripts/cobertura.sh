#!/usr/bin/env bash
# Para cada RN e CA do docs/briefing.md, conta os testes que têm o código no nome.
# Reconhece `RN13 divisao...`() (src/test) e RN16_backup...() (src/androidTest).
set -uo pipefail
cd "$(dirname "$0")/../../../.." || exit 2

pastas=""
for p in domain/src/test app/src/test app/src/androidTest; do
  [ -d "$p" ] && pastas="$pastas $p"
done

# Só onde cada código é definido: RN em negrito ("**RN01**") e CA na linha da tabela ("| CA01 |").
# Uma simples menção no texto não conta, para um briefing cortado não passar despercebido.
rns=$(grep -oE '\*\*RN[0-9]{2}\*\*' docs/briefing.md | tr -d '*' | sort -u)
cas=$(grep -oE '^\| CA[0-9]{2} \|' docs/briefing.md | grep -oE 'CA[0-9]{2}' | sort -u)
n_rn=$(printf '%s' "$rns" | grep -c . || true)
n_ca=$(printf '%s' "$cas" | grep -c . || true)
echo "Lidos do briefing: $n_rn RN e $n_ca CA."
# Briefing cortado (como na Etapa 6) não pode virar "Sem teste: nenhum"
if [ "$n_rn" -eq 0 ] || [ "$n_ca" -eq 0 ]; then
  echo "ATENÇÃO: o briefing não tem RN ou CA. Ele pode ter sido cortado: confira com git diff --stat docs/briefing.md."
  exit 1
fi
codigos="$rns $cas"
sem_teste=""
printf '%-7s %-7s %s\n' "Código" "Testes" "Arquivos"
for codigo in $codigos; do
  # shellcheck disable=SC2086
  achados=$(grep -rnE "fun \`?${codigo}[ _]" $pastas --include='*.kt' 2>/dev/null || true)
  if [ -z "$achados" ]; then
    printf '%-7s %-7s %s\n' "$codigo" 0 "-"
    sem_teste="$sem_teste $codigo"
  else
    n=$(printf '%s\n' "$achados" | wc -l | tr -d ' ')
    arquivos=$(printf '%s\n' "$achados" | cut -d: -f1 | xargs -n1 basename | sort -u | tr '\n' ' ')
    printf '%-7s %-7s %s\n' "$codigo" "$n" "$arquivos"
  fi
done
echo
echo "Sem teste:${sem_teste:- nenhum}"
