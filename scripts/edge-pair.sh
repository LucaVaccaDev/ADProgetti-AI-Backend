#!/usr/bin/env bash
# Pairs the Edge with a running Hub: logs in as administrator, rotates the Edge credential, reads the
# Hub public key and stores both in the macOS keychain. Neither secret is printed or put in a file.
# Usage: scripts/edge-pair.sh [username] [hub-url]     (the password is typed at a hidden prompt)
set -euo pipefail
USERNAME="${1:-admin}"
HUB="${2:-http://localhost:8080}"
SERVICE=it.adprogetti.zio.edge

read -r -s -p "Password di $USERNAME: " PASSWORD; echo
TOKEN=$(jq -n --arg u "$USERNAME" --arg p "$PASSWORD" '{username:$u,password:$p}' \
  | curl -fsS -X POST "$HUB/api/auth/login" -H 'Content-Type: application/json' -d @- \
  | jq -r .accessToken) || { echo "Login non riuscito"; exit 1; }
unset PASSWORD

store() { # name value: `security -i` reads the command from stdin, so the value is not in argv or ps
  printf 'add-generic-password -U -s %s -a %s -w "%s"\n' "$SERVICE" "$1" "$2" | /usr/bin/security -i >/dev/null
  [ "$(/usr/bin/security find-generic-password -s "$SERVICE" -a "$1" -w | wc -c | tr -d ' ')" -gt 11 ] \
    || { echo "Salvataggio di $1 non riuscito"; exit 1; }
  echo "Salvato nel portachiavi: $1"
}

CREDENTIAL=$(curl -fsS -X POST "$HUB/api/integrations/edge/credential" -H "Authorization: Bearer $TOKEN" | jq -r .credential)
PUBLIC_KEY=$(curl -fsS "$HUB/api/integrations/edge/public-key" -H "Authorization: Bearer $TOKEN" | jq -r .publicKey)
[ -n "$CREDENTIAL" ] && [ "$CREDENTIAL" != null ] && [ -n "$PUBLIC_KEY" ] && [ "$PUBLIC_KEY" != null ] \
  || { echo "Risposta inattesa dall'Hub"; exit 1; }
store hub-edge-key "$CREDENTIAL"
store hub-command-public-key "$PUBLIC_KEY"
echo "Accoppiamento completato. Ora avvia l'Edge: bash scripts/start-edge.sh"
