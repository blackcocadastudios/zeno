#!/usr/bin/env bash
# Full verification used by CI and runnable locally: build, unit and screenshot
# tests, lint. Output is teed to report/build.log for collect_report.py.
set -uo pipefail

mkdir -p report
./gradlew --stacktrace --continue \
  assembleDebug \
  assembleRelease \
  :core:engine:test \
  :core:browser:test \
  testDebugUnitTest \
  :app:lintDebug \
  -Proborazzi.test.record=true \
  "$@" 2>&1 | tee report/build.log
status=${PIPESTATUS[0]}
echo "$status" > report/gradle-exit-code
exit "$status"
