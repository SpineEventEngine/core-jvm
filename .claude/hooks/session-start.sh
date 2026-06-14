#!/usr/bin/env bash
#
# SessionStart hook: pre-warm the Gradle dependency cache.
#
# Claude Code on the web starts every session from a fresh clone, so the first
# build in a web session has to download all plugins and dependencies. This hook
# downloads them up front (and lets the environment cache the result) so that
# builds run quickly and without network surprises.
#
# Behavior:
#   * Web-only. On a local machine the caches already exist, so the hook is a
#     no-op (see CLAUDE_CODE_REMOTE below).
#   * Non-blocking. It never fails the session. If the warm-up cannot complete
#     - most often because the environment's network allowlist does not yet
#     include this project's custom Maven repositories - the session still
#     starts normally.
#   * Idempotent. Gradle's own up-to-date checks make repeat runs cheap.
#
# This project resolves Spine artifacts from custom Maven repositories. For the
# warm-up to succeed, the cloud environment's "Network access" must be set to
# "Custom" with these hosts on the "Allowed domains" list (keep the default
# package-manager list enabled as well):
#
#   europe-maven.pkg.dev
#   spine.mycloudrepo.io
#   cache-redirector.jetbrains.com
#   www.jetbrains.com
#   oss.sonatype.org

# Run only in Claude Code on the web; do nothing on local machines.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

project_dir="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
cd "$project_dir" || exit 0

echo "[session-start] Pre-warming the Gradle cache for a web session..."

if ./gradlew --console=plain assemble testClasses; then
  echo "[session-start] Gradle cache pre-warmed."
else
  echo "[session-start] Gradle pre-warm did not complete; continuing session startup."
  echo "[session-start] If the cause was a 403/Forbidden response, add this project's"
  echo "[session-start] custom Maven hosts to the environment's Network access allowlist"
  echo "[session-start] (Custom -> Allowed domains) and start a new session:"
  echo "[session-start]   europe-maven.pkg.dev, spine.mycloudrepo.io,"
  echo "[session-start]   cache-redirector.jetbrains.com, www.jetbrains.com, oss.sonatype.org"
fi

# Always succeed: a pre-warm failure must never block the session from starting.
exit 0
