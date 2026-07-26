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
# Runs the application that was just built here.
#
#   ./build.sh && ./start.sh
#   SMALLCRM_PORT=9000 ./start.sh
#
# The Java it uses is the one ./build.sh fetched, so this works on a machine that has no Java
# installed and needs no unpacking of a distribution archive first. A JAVA_HOME or a java on the
# PATH is used if there is no fetched one, so this also works after a plain mvn package.
#
# This is the counterpart of packaging/templates/start.sh, which goes inside the distributions
# and starts the copy of Java that travels with them. Kept separate because the two resolve
# everything differently: that one knows exactly where its runtime is, this one has to look.

set -eu

HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$HERE"

JAR="$HERE/target/quarkus-app/quarkus-run.jar"

PORT="${SMALLCRM_PORT:-8080}"
URL="http://localhost:$PORT"
export QUARKUS_HTTP_PORT="$PORT"

# Deliberately no SMALLCRM_DATA_DIR or SMALLCRM_BACKUP_DIR here: the defaults already put data/,
# backup/ and logs/ beside the project, which is what the distribution does inside its own folder
# and what "java -jar target/..." would do. Setting them would only make this script's runs land
# somewhere other than an ordinary run's.

if [ ! -f "$JAR" ]; then
  echo "Nothing built yet: $JAR is not there." >&2
  echo "Run ./build.sh first, which fetches what it needs and builds it." >&2
  exit 1
fi

# The JDK ./build.sh fetched comes first, so a Java that happens to be installed cannot change
# what this runs. macOS buries the runtime one level deeper, inside the bundle layout.
java_command=""
for candidate in \
  "$HERE"/.build-tools/jdk/*/Contents/Home/bin/java \
  "$HERE"/.build-tools/jdk/*/bin/java; do
  if [ -x "$candidate" ]; then
    java_command="$candidate"
    break
  fi
done

if [ -z "$java_command" ] && [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  java_command="$JAVA_HOME/bin/java"
fi

if [ -z "$java_command" ] && command -v java >/dev/null 2>&1; then
  java_command=java
fi

if [ -z "$java_command" ]; then
  echo "No Java found: none fetched in .build-tools/, no JAVA_HOME, none on the PATH." >&2
  echo "Run ./build.sh, which fetches one." >&2
  exit 1
fi

printf '\nSmall CRM is starting. It will open at %s\n' "$URL"
printf 'Leave this window open while you use it; press Ctrl+C to stop.\n\n'

# Opens the browser once the application actually answers, rather than immediately onto a
# connection error. Gives up quietly after two minutes; the address is printed above either way.
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

# exec so that Ctrl+C reaches Java itself, which shuts down cleanly and writes any backup that
# was still waiting.
exec "$java_command" -jar "$JAR" "$@"
