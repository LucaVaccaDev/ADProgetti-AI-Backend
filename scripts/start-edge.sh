#!/usr/bin/env bash
# Starts the Edge locally. Needs .env (EDGE_DB_*, EDGE_HUB_URL) and PostgreSQL with pgvector running.
# The Hub credential and public key must be in the keychain: see scripts/edge-set-secret.sh.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$ROOT/scripts/env.sh"
set -a; source "$ROOT/.env"; set +a
cd "$ROOT/be"
mvn -q -pl edge -am package -DskipTests
exec java -jar edge/target/edge-0.0.1-SNAPSHOT.jar "$@"
