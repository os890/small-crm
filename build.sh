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
# Builds the Small CRM packages on a machine with nothing installed.
#
#   ./build.sh                 all three platforms
#   ./build.sh linux-x64       just one
#
# No Java, no Maven, no Node, no pnpm needed beforehand. This script fetches a Node — the one
# thing it cannot do without, and the only step that has to be written in shell — and
# packaging/bootstrap-build.mjs then fetches the rest. Everything lands in .build-tools/ inside
# this folder; nothing outside it is written and nothing already installed is used or changed.
# Delete .build-tools/ and it is as if this never ran.
#
# What it does expect, because every macOS and Linux install has them: a shell, curl, tar, and
# either shasum or sha256sum.

set -eu

HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$HERE"

TOOLS="$HERE/.build-tools"
CACHE="$TOOLS/cache"
NODE_DIR="$TOOLS/node"

# Node 22 is the current long-term release and the one the frontend is built against. Corepack
# comes with it, which is how pnpm arrives at the pinned version without installing anything.
NODE_MAJOR=22
DIST="https://nodejs.org/dist/latest-v$NODE_MAJOR.x"

die() {
  printf '\n%s\n' "$*" >&2
  exit 1
}

require() {
  command -v "$1" >/dev/null 2>&1 ||
    die "This needs $1, which is not on the PATH. $2"
}

sha256_of() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | cut -d' ' -f1
  else
    sha256sum "$1" | cut -d' ' -f1
  fi
}

require curl "Install it, or fetch Node yourself and run: node packaging/bootstrap-build.mjs"
require tar "Install it and try again."
if ! command -v shasum >/dev/null 2>&1 && ! command -v sha256sum >/dev/null 2>&1; then
  die "This needs shasum or sha256sum to check what it downloads, and has neither."
fi

case "$(uname -s)" in
Darwin) os=darwin ;;
Linux) os=linux ;;
*) die "Unsupported build host: $(uname -s). Use macOS, Linux, or build.cmd on Windows." ;;
esac

case "$(uname -m)" in
arm64 | aarch64) arch=arm64 ;;
x86_64 | amd64) arch=x64 ;;
*) die "Unsupported architecture: $(uname -m)." ;;
esac

# Already bootstrapped: go straight on. This is the common case on every run after the first.
if [ ! -x "$NODE_DIR/bin/node" ]; then
  printf 'Fetching Node %s for %s-%s\n' "$NODE_MAJOR" "$os" "$arch"

  # One plain text file carries both the current version and its checksum, so there is no
  # version pinned here to go stale and no JSON to parse in shell.
  sums=$(curl -fsSL "$DIST/SHASUMS256.txt") ||
    die "Could not reach $DIST. Check the network and try again."
  line=$(printf '%s\n' "$sums" | grep -- "-$os-$arch\.tar\.gz\$" | head -1) ||
    die "nodejs.org lists no $NODE_MAJOR.x build for $os-$arch."
  [ -n "$line" ] ||
    die "nodejs.org lists no $NODE_MAJOR.x build for $os-$arch."

  expected=$(printf '%s' "$line" | awk '{print $1}')
  name=$(printf '%s' "$line" | awk '{print $2}')

  mkdir -p "$CACHE"
  archive="$CACHE/$name"
  if [ ! -f "$archive" ] || [ "$(sha256_of "$archive")" != "$expected" ]; then
    printf '  downloading %s\n' "$name"
    curl -fsSL -o "$archive.part" "$DIST/$name" ||
      die "Downloading $name failed."
    mv "$archive.part" "$archive"
  fi

  actual=$(sha256_of "$archive")
  if [ "$actual" != "$expected" ]; then
    rm -f "$archive"
    die "$name does not match the checksum nodejs.org published; it was not used."
  fi

  # Unpacked to a fixed folder rather than a versioned one, so a Node that moves on does not
  # leave copies behind. --strip-components drops the node-vX-os-arch wrapper directory.
  rm -rf "$NODE_DIR"
  mkdir -p "$NODE_DIR"
  tar -xzf "$archive" -C "$NODE_DIR" --strip-components=1
  printf '  %s\n\n' "$("$NODE_DIR/bin/node" --version)"
fi

exec "$NODE_DIR/bin/node" "$HERE/packaging/bootstrap-build.mjs" "$@"
