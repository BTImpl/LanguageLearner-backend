#!/usr/bin/env bash
# Checks or fixes the formatting of the Java sources (Spotless, google-java-format).
#
#   tool/format.sh check   exit non-zero if any file is not formatted (default)
#   tool/format.sh fix     format the files in place
#
# Used by the CI workflow and by the pre-commit hook, so both always check the
# same thing.
set -euo pipefail

cd "$(dirname "$0")/.."

mode="${1:-check}"

case "$mode" in
  check) mvn -B -q spotless:check ;;
  fix) mvn -B -q spotless:apply ;;
  *)
    echo "usage: tool/format.sh [check|fix]" >&2
    exit 2
    ;;
esac
