#!/bin/sh
set -eu
cd "$(dirname "$0")"
meson setup build backend --buildtype=debug -Dwerror=true "$@"
meson compile -C build
meson test -C build --print-errorlogs
