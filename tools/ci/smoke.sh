#!/usr/bin/env bash
# Emulator smoke test: installs the debug APK and uses Zeno the way a person
# would (home, a link from another app, searching, tabs, menu, private mode,
# settings, installing uBlock Origin). Records screenshots, cold start time,
# memory and crashes in smoke-report/. Every step is attempted; the job fails
# at the end if Zeno crashed or a step did not work.
#
# usage: smoke.sh <dir-with-apks>
set -uo pipefail

apk_dir="$1"
out="smoke-report"
mkdir -p "$out/screenshots"
pkg="com.blackcocadastudios.zeno.debug"
activity="$pkg/com.blackcocadastudios.zeno.MainActivity"
ui="python3 tools/ci/ui.py"
failures=0
steps="$out/steps.md"
echo "# Smoke test" > "$steps"
echo >> "$steps"

step() {
  # step <name> <command...>: runs the command, records ✓ or ✗.
  local name="$1"
  shift
  if "$@" >> "$out/steps.log" 2>&1; then
    echo "- ✓ $name" >> "$steps"
  else
    echo "- ✗ $name" >> "$steps"
    failures=$((failures + 1))
  fi
}

shot() {
  adb exec-out screencap -p > "$out/screenshots/$1.png"
}

apk="$(find "$apk_dir" -name '*x86_64*debug*.apk' | head -1)"
if [ -z "$apk" ]; then
  echo "- ✗ no x86_64 debug APK in $apk_dir" >> "$steps"
  exit 1
fi

adb wait-for-device
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
step "install $(basename "$apk")" adb install -r -g "$apk"
adb logcat -c

# 1. Cold start to the home page.
adb shell am force-stop "$pkg"
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$activity" > "$out/start-cold.txt"
step "home page shows the wordmark and the uBlock card" $ui wait "Sem anúncios" 20
sleep 2
shot 01-home

# 2. uBlock Origin from the home page card: permissions dialog, install.
step "tap Instalar on the uBlock card" $ui tap "^Instalar$" 10
step "install dialog asks for permissions" $ui wait "Instalar uBlock Origin" 60
shot 02-ublock-dialog
step "approve the install" $ui tap "^Instalar$" 10
# The confirmation message is brief; the menu check below proves the install.
sleep 20
shot 03-ublock-installed

# 3. A link from another app.
adb shell am start -W -a android.intent.action.VIEW -d "https://example.com/" -n "$activity" > "$out/start-view.txt"
step "example.com from another app" $ui wait "^example\\.com$" 30
sleep 6
shot 04-example

# 4. Search from the address bar.
step "tap the address" $ui tap "^example\\.com$" 10
sleep 1
adb shell input text "jardim%szen%skyoto"
sleep 2
shot 05-typing
adb shell input keyevent KEYCODE_ENTER
step "search results show the terms" $ui wait "^jardim zen kyoto$" 30
sleep 6
shot 06-search

# 5. Another site, then the tab grid.
adb shell am start -a android.intent.action.VIEW -d "https://pt.wikipedia.org/wiki/Zen" -n "$activity"
step "wikipedia opens" $ui wait "wikipedia\\.org" 30
sleep 8
shot 07-wikipedia
step "open the tab grid" $ui tap "abas? abertas?" 10
sleep 2
shot 08-tabs
adb shell input keyevent KEYCODE_BACK
sleep 1

# 6. The menu.
step "open the menu" $ui tap "^Menu$" 10
sleep 2
shot 09-menu
step "menu lists uBlock Origin" $ui wait "uBlock Origin" 5

# 7. Private mode from the menu.
step "new private tab" $ui tap "^Nova aba privada$" 10
sleep 2
shot 10-private-editing
# Back hides the keyboard, back again leaves the address editor.
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_BACK
sleep 2
shot 11-private-home

# 8. Settings.
step "open the menu again" $ui tap "^Menu$" 10
sleep 1
step "open settings" $ui tap "^Configurações$" 10
sleep 2
shot 12-settings
adb shell input keyevent KEYCODE_BACK
sleep 1

# 9. Background and back: the session survives.
adb shell input keyevent KEYCODE_HOME
sleep 3
adb shell am start -W -n "$activity" > "$out/start-warm.txt"
sleep 3
shot 13-back

# Measurements.
adb shell dumpsys meminfo "$pkg" > "$out/meminfo-main.txt" 2>&1
adb shell dumpsys meminfo > "$out/meminfo-all.txt" 2>&1
adb logcat -d > "$out/logcat.txt"
grep -E "FATAL EXCEPTION|ANR in|Fatal signal" "$out/logcat.txt" > "$out/crashes.txt" || true

{
  echo
  echo "## Measurements"
  echo
  echo '```'
  grep -E "TotalTime|WaitTime" "$out/start-cold.txt" | sed 's/^/cold start: /'
  grep -E "TotalTime" "$out/start-warm.txt" | sed 's/^/warm start: /'
  grep -E "TOTAL PSS|TOTAL RSS|TOTAL:" "$out/meminfo-main.txt" | head -3 | sed 's/^/main process: /'
  echo "Zeno processes (PSS):"
  grep -E "$pkg|org\\.mozilla" "$out/meminfo-all.txt" | head -12
  echo '```'
  echo
  if [ -s "$out/crashes.txt" ]; then
    echo "## Crashes"
    echo
    echo '```'
    head -60 "$out/crashes.txt"
    echo '```'
  else
    echo "No crashes in logcat."
  fi
} >> "$steps"

if [ -s "$out/crashes.txt" ]; then failures=$((failures + 1)); fi
echo "failures=$failures" >> "$out/steps.log"
[ "$failures" -eq 0 ]
