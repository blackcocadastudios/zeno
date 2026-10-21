#!/usr/bin/env bash
# Emulator smoke test: installs the release APK and uses Zeno the way a person
# would (home, a link from another app, searching, tabs, menu, private mode,
# settings, installing uBlock Origin, a site asking for the location).
# Records screenshots, cold start time, memory and crashes in smoke-report/.
# Every step is attempted; the job fails at the end if Zeno crashed or a step
# did not work. Probes record what is worth knowing without failing the job.
#
# usage: smoke.sh <dir-with-apks>
set -uo pipefail

apk_dir="$1"
out="smoke-report"
mkdir -p "$out/screenshots"
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

probe() {
  # probe <name> <command...>: like step, but only informs.
  local name="$1"
  shift
  if "$@" >> "$out/steps.log" 2>&1; then
    echo "- ✓ $name (probe)" >> "$steps"
  else
    echo "- ○ $name (probe: no)" >> "$steps"
  fi
}

absent() {
  # absent <regex> <seconds>: the text does not show up in that time.
  ! $ui wait "$1" "$2"
}

shot() {
  adb exec-out screencap -p > "$out/screenshots/$1.png"
}

# The release build is what people install: R8-shrunk, with the startup profile.
# Testing it also proves R8 kept everything the app needs.
apk="$(find "$apk_dir" -name '*x86_64*release*.apk' | head -1)"
pkg="com.blackcocadastudios.zeno"
if [ -z "$apk" ]; then
  apk="$(find "$apk_dir" -name '*x86_64*debug*.apk' | head -1)"
  pkg="com.blackcocadastudios.zeno.debug"
fi
if [ -z "$apk" ]; then
  echo "- ✗ no x86_64 APK in $apk_dir" >> "$steps"
  exit 1
fi
activity="$pkg/com.blackcocadastudios.zeno.MainActivity"

adb wait-for-device
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
# The emulator's own apps may freeze on a slow CI machine; their "not responding"
# dialogs would cover Zeno. Hide them (ui.py also answers any that still show).
adb shell settings put global hide_error_dialogs 1 || true
adb shell settings put secure anr_show_background 0 || true
# What Gecko answers about each touch (scrollable directions, overscroll), for the logs.
adb shell setprop log.tag.ZenoTouch DEBUG || true
step "install $(basename "$apk")" adb install -r -g "$apk"
adb logcat -c

# 1. First launch, then a measured cold start (the first one also pays for installing).
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$activity" > "$out/start-first.txt"
sleep 8
adb shell am force-stop "$pkg"
sleep 2
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
# Reading down: the bar slides away; scrolling back up brings it back.
adb shell input swipe 540 1700 540 700 400
sleep 2
shot 07b-scrolled-down
adb shell input swipe 540 900 540 1500 400
sleep 2
shot 07c-scrolled-up
step "the bar comes back when scrolling up" $ui wait "abas? abertas?" 10
# Back to the top, then pull the page down: the ensō draws itself; letting go reloads.
adb shell input swipe 540 700 540 2000 200
adb shell input swipe 540 700 540 2000 200
sleep 1
# A picture mid-pull (best effort: the finger is held with separate commands).
adb shell "input motionevent DOWN 540 1300; input motionevent MOVE 540 1450; input motionevent MOVE 540 1600; input motionevent MOVE 540 1750; input motionevent MOVE 540 1900"
shot 07d-pulling
adb shell input motionevent UP 540 1900
sleep 2
# A slow held finger can read as a long press: close the link menu if it opened.
if $ui wait "^Copiar link$" 2 >> "$out/steps.log" 2>&1; then adb shell input keyevent KEYCODE_BACK; sleep 1; fi
# The real check: one smooth pull reloads the page (Gecko logs every page start).
adb logcat -d > "$out/logcat-before-pull.txt"
adb logcat -c
adb shell input swipe 540 900 540 1900 900
sleep 4
shot 07e-reloaded
step "pulling the page down reloads it" grep -q "GeckoView:PageStart uri=https://pt.wikipedia.org/wiki/Zen" <(adb logcat -d)
step "the bar is there after the pull" $ui wait "abas? abertas?" 10
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

# 9. A site asks for the location; the answer is remembered and shown in the site panel.
# Test pages are served from this machine and reach the emulator through adb reverse.
python3 -m http.server 8765 --bind 127.0.0.1 --directory tools/ci/pages > "$out/http.log" 2>&1 &
http_pid=$!
adb reverse tcp:8765 tcp:8765
sleep 1
adb shell am start -a android.intent.action.VIEW -d "http://localhost:8765/geo.html" -n "$activity"
step "a site asks for the location" $ui wait "quer saber sua localização" 30
shot 13-geo-question
step "allow, remembering for the site" $ui tap "^Permitir$" 10
probe "page text is visible to UI Automator" $ui wait "geo: (granted|error)" 20
step "reload the page" $ui tap "^Recarregar$" 10
step "the kept answer is used: no second question" absent "quer saber sua localização" 8
step "the lock opens the site panel" $ui tap "Informações do site" 10
step "the panel shows tracking protection" $ui wait "^Proteção contra rastreamento" 10
# Title and subtitle of a row are separate texts for UI Automator.
step "the panel lists the location" $ui wait "^Localização$" 5
step "…as allowed" $ui wait "^Permitido$" 5
shot 14-site-panel
adb shell input keyevent KEYCODE_BACK
sleep 1
step "open the menu for settings" $ui tap "^Menu$" 10
sleep 1
step "open settings again" $ui tap "^Configurações$" 10
sleep 1
# The row is in the privacy section, below the first screenful.
adb shell input swipe 540 1700 540 900 300
sleep 1
step "open site permissions" $ui tap "^Permissões dos sites" 10
# The browser stays composed under the settings: look for the permission row, not the host.
step "settings list the site's location permission" $ui wait "^Localização$" 10
step "…as allowed, under the site" $ui wait "^Permitido$" 5
shot 15-site-permissions
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_BACK
sleep 1

# 10. Site notifications: off by default, so a site asking is refused without a question; then on.
adb shell am start -a android.intent.action.VIEW -d "http://localhost:8765/notify.html" -n "$activity"
sleep 5
# The page is one big button; a request for notifications needs a tap.
adb shell input tap 540 1000
step "notifications off: refused quietly, with a way to the settings" $ui wait "Notificações de sites estão desligadas" 10
shot 16-notifications-off
sleep 4
step "open the menu to turn them on" $ui tap "^Menu$" 10
sleep 1
step "open settings for notifications" $ui tap "^Configurações$" 10
sleep 1
adb shell input swipe 540 1700 540 500 300
adb shell input swipe 540 1700 540 500 300
sleep 1
step "turn site notifications on" $ui tap "^Notificações de sites(?! estão)" 10
sleep 1
shot 17-notifications-setting
adb shell input keyevent KEYCODE_BACK
sleep 2
adb shell input tap 540 1000
step "notifications on: the site asks" $ui wait "quer mostrar notificações" 15
shot 18-notifications-question
step "allow notifications" $ui tap "^Permitir$" 10
sleep 3
step "the site's notification is on Android's list" grep -q "Olá do site de teste" <(adb shell dumpsys notification --noredact)
adb shell cmd statusbar expand-notifications
sleep 2
shot 19-notification-shade
step "tap the site's notification" $ui tap "Olá do site de teste" 10
sleep 2
step "Zeno comes back on the site's tab" $ui wait "^localhost$" 10
adb shell cmd statusbar collapse || true

# 11. Reader mode on a local article: the article alone, then back to the page.
adb shell am start -a android.intent.action.VIEW -d "http://localhost:8765/article.html" -n "$activity"
step "the article page opens" $ui wait "Menu do site de teste" 20
step "open the menu for reader mode" $ui tap "^Menu$" 10
sleep 1
step "enter reader mode" $ui tap "^Modo leitura$" 10
step "reader mode shows the article" $ui wait "min de leitura" 20
step "reader mode leaves the site's menu out" absent "Menu do site de teste" 3
step "the address keeps the site, with a book" $ui wait "Modo leitura. Voltar para a página" 5
shot 21-reader
step "the book goes back to the page" $ui tap "Modo leitura. Voltar para a página" 10
step "the page is back" $ui wait "Menu do site de teste" 15
kill "$http_pid" 2>/dev/null || true

# 12. Background and back: the session survives.
adb shell input keyevent KEYCODE_HOME
sleep 3
adb shell am start -W -n "$activity" > "$out/start-warm.txt"
sleep 3
shot 22-back

# Measurements.
adb shell dumpsys meminfo "$pkg" > "$out/meminfo-main.txt" 2>&1
adb shell dumpsys meminfo > "$out/meminfo-all.txt" 2>&1
adb logcat -d > "$out/logcat.txt"
cat "$out"/logcat-before-pull.txt "$out/logcat.txt" 2>/dev/null | grep -E "FATAL EXCEPTION|ANR in|Fatal signal" > "$out/crashes.txt" || true

{
  echo
  echo "## Measurements"
  echo
  echo '```'
  echo "apk: $(basename "$apk")"
  grep -E "TotalTime" "$out/start-first.txt" | sed 's/^/first launch: /'
  grep -E "TotalTime|WaitTime" "$out/start-cold.txt" | sed 's/^/cold start: /'
  grep -E "TotalTime" "$out/start-warm.txt" | sed 's/^/warm start: /'
  grep -E "TOTAL PSS|TOTAL RSS|TOTAL:" "$out/meminfo-main.txt" | head -3 | sed 's/^/main process: /'
  echo "Zeno processes (PSS, KB):"
  awk '/Total PSS by process/{on=1; next} on && /^$/{exit} on' "$out/meminfo-all.txt" | grep -F "$pkg" | head -12
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
