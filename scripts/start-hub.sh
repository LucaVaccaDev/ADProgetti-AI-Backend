#!/usr/bin/env bash
# Starts the Hub locally. Needs .env (copy from .env.example) and PostgreSQL running.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$ROOT/scripts/env.sh"
set -a; source "$ROOT/.env"; set +a
cd "$ROOT/be"
mvn -q -pl hub -am package -DskipTests
exec java -jar hub/target/hub-0.0.1-SNAPSHOT.jar "$@"
