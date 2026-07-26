#!/bin/sh
#
# Copyright 2026 the Small CRM authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Starts Small CRM. Everything it needs is in this folder.

set -eu

# Everything is resolved from this script's own folder, so it works whatever the
# current directory is — double-clicked, from a terminal, or from a shortcut.
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$HERE"

PORT="${SMALLCRM_PORT:-8080}"
URL="http://localhost:$PORT"

export SMALLCRM_DATA_DIR="${SMALLCRM_DATA_DIR:-$HERE/data}"
export SMALLCRM_BACKUP_DIR="${SMALLCRM_BACKUP_DIR:-$HERE/backup}"
export SMALLCRM_LOG_FILE="${SMALLCRM_LOG_FILE:-$HERE/logs/small-crm.log}"
export QUARKUS_HTTP_PORT="$PORT"

if [ ! -x "$HERE/runtime/bin/java" ]; then
  echo "The runtime folder is missing or damaged. Unpack the whole archive again." >&2
  exit 1
fi

printf '\nSmall CRM is starting. It will open at %s\n' "$URL"
printf 'Leave this window open while you use it; close it to stop.\n\n'

# Opens the browser once the application actually answers, rather than immediately
# onto a connection error. Gives up quietly after a minute; the address is printed
# above either way.
open_when_ready() {
  i=0
  while [ "$i" -lt 120 ]; do
    if curl -fsS -o /dev/null "$URL/q/health/ready" 2>/dev/null; then
      if command -v open >/dev/null 2>&1; then
        open "$URL"
      elif command -v xdg-open >/dev/null 2>&1; then
        xdg-open "$URL" >/dev/null 2>&1
      fi
      return
    fi
    i=$((i + 1))
    sleep 1
  done
}

if [ "${SMALLCRM_NO_BROWSER:-}" = "" ] && command -v curl >/dev/null 2>&1; then
  open_when_ready &
fi

# exec so that Ctrl+C and a window close reach Java itself, which shuts down cleanly
# and writes any backup that was still waiting.
exec "$HERE/runtime/bin/java" -jar "$HERE/app/quarkus-run.jar" "$@"
