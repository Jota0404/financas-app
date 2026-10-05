#!/usr/bin/env bash
# Instala o app no emulador, abre no tema claro e no escuro, tira prints e procura travamentos.
# Usa só emulador: nunca instala no celular físico do dono.
set -uo pipefail
cd "$(dirname "$0")/../../../.." || exit 2

pacote=com.joaobarcelos.financas
atividade=$pacote/.MainActivity
saida=build/emulador
sdk=$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null)
sdk=${sdk:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}
adb="$sdk/platform-tools/adb"

emulador_ligado() { "$adb" devices | awk '/^emulator-[0-9]+\tdevice$/ { print $1; exit }'; }

serial=$(emulador_ligado)
if [ -z "$serial" ]; then
  avd=$("$sdk/emulator/emulator" -list-avds 2>/dev/null | grep -E '^[A-Za-z0-9._-]+$' | head -1)
  if [ -z "$avd" ]; then
    echo "Nenhum emulador criado. Crie um no Android Studio, em Device Manager."
    exit 2
  fi
  echo "== Ligando o emulador $avd (pode levar 1 a 2 minutos)"
  nohup "$sdk/emulator/emulator" -avd "$avd" >/dev/null 2>&1 &
  for _ in $(seq 90); do
    serial=$(emulador_ligado)
    [ -n "$serial" ] && [ "$("$adb" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && break
    sleep 2
  done
  [ -n "$serial" ] || { echo "O emulador não terminou de ligar."; exit 2; }
fi
echo "== Emulador: $serial (Android $("$adb" -s "$serial" shell getprop ro.build.version.release | tr -d '\r'))"

if ! java -version >/dev/null 2>&1; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi
mkdir -p "$saida"
echo "== Gerando o APK de teste"
./gradlew --console=plain assembleDebug >"$saida/build.log" 2>&1 || {
  echo "O build falhou. Veja $saida/build.log"
  exit 1
}
"$adb" -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk | tail -1

# Guarda o modo noturno atual e devolve no final, mesmo se o script parar no meio.
original=$("$adb" -s "$serial" shell cmd uimode night | tr -d '\r' | awk '{ print $3 }')
trap '"$adb" -s "$serial" shell cmd uimode night "${original:-no}" >/dev/null' EXIT

"$adb" -s "$serial" logcat -c
problema=0
for modo in no yes; do
  nome=claro
  [ "$modo" = yes ] && nome=escuro
  "$adb" -s "$serial" shell cmd uimode night "$modo" >/dev/null
  "$adb" -s "$serial" shell am force-stop "$pacote"
  "$adb" -s "$serial" shell am start -W -n "$atividade" >/dev/null
  sleep 3
  "$adb" -s "$serial" exec-out screencap -p >"$saida/$nome.png"
  if "$adb" -s "$serial" shell dumpsys activity activities | grep -m1 topResumedActivity | grep -q "$pacote"; then
    echo "Tema $nome: app aberto, print em $saida/$nome.png"
  else
    echo "Tema $nome: o app NÃO está na frente da tela (print em $saida/$nome.png)"
    problema=1
  fi
done

travamentos=$("$adb" -s "$serial" logcat -d -b crash)
if [ -n "$travamentos" ]; then
  echo "== TRAVAMENTO encontrado:"
  echo "$travamentos" | head -40
  problema=1
else
  echo "== Nenhum travamento no log."
fi
exit "$problema"
