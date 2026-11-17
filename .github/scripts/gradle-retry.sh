#!/usr/bin/env bash
# Runs a Gradle command and re-runs it only when it failed because a plugin or dependency could not be
# fetched (a transient Gradle plugin portal / Maven miss, issue #215). Real build or test failures fail at once.
# Usage: .github/scripts/gradle-retry.sh ./gradlew <args...>
set -uo pipefail

attempts=3
log="$(mktemp)"
transient='Plugin \[id: .*\] was not found in any of the following sources|Plugin .* was not found|Could not resolve all (files|dependencies) for|Could not (GET|HEAD) |Could not download |Read timed out|Connection reset|Connect timed out|Remote host terminated the handshake|Premature end of Content-Length|Received status code 5[0-9][0-9]|Gateway Time-?out|Service Unavailable'

for ((i = 1; i <= attempts; i++)); do
  "$@" 2>&1 | tee "$log"
  status=${PIPESTATUS[0]}
  if [[ $status -eq 0 ]]; then
    exit 0
  fi
  if [[ $i -lt $attempts ]] && grep -Eq "$transient" "$log"; then
    echo "::warning::Gradle failed on a download or plugin-resolution error (attempt $i of $attempts); retrying in $((i * 20)) s"
    sleep $((i * 20))
    continue
  fi
  exit "$status"
done
