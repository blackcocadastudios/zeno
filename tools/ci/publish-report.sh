#!/usr/bin/env bash
# Publishes a directory of CI results (logs, test reports, screenshots) to the
# orphan branch `ci-reports`, replacing its previous contents.
#
# Used during development so results can be read from environments that can
# reach git but not GitHub's artifact storage. It is never needed to build the app.
#
# usage: publish-report.sh <dir> <label>
set -euo pipefail

src_dir="$1"
label="$2"

[ -d "$src_dir" ] || { echo "no report dir: $src_dir"; exit 0; }

work="$(mktemp -d)"
cp -R "$src_dir"/. "$work"/
cat > "$work/meta.txt" <<EOF
label=$label
sha=${GITHUB_SHA:-unknown}
ref=${GITHUB_REF:-unknown}
run_id=${GITHUB_RUN_ID:-unknown}
run_attempt=${GITHUB_RUN_ATTEMPT:-unknown}
date=$(date -u +%Y-%m-%dT%H:%M:%SZ)
EOF

cd "$work"
git init -q -b ci-reports
git config user.name "zeno-ci"
git config user.email "zeno-ci@users.noreply.github.com"
git add -A
git commit -q -m "CI report: $label @ ${GITHUB_SHA:-unknown}"
git push -q -f "https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" ci-reports
echo "report published to ci-reports ($label)"
