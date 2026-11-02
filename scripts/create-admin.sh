#!/usr/bin/env bash
# Creates the first administrator. Usage: scripts/create-admin.sh [username] [display name]
# The password is typed at the prompt; it is not stored in files or shell history.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$ROOT/scripts/env.sh"
set -a; source "$ROOT/.env"; set +a
cd "$ROOT/be"
mvn -q -pl hub -am package -DskipTests
exec java -jar hub/target/hub-0.0.1-SNAPSHOT.jar \
  --spring.main.web-application-type=none \
  --hub.bootstrap-admin.enabled=true \
  --hub.bootstrap-admin.username="${1:-admin}" \
  --hub.bootstrap-admin.display-name="${2:-Amministratore}"
