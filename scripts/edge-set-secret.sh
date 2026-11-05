#!/usr/bin/env bash
# Stores an Edge secret in the macOS keychain. The value is typed at a hidden prompt, so it never
# appears in shell history or in the process list.
#   hub-edge-key             credential shown once by the Hub (POST /api/integrations/edge/credential)
#   hub-command-public-key   Hub public key (GET /api/integrations/edge/public-key)
set -euo pipefail
NAME="${1:-}"
case "$NAME" in
  hub-edge-key|hub-command-public-key) ;;
  *) echo "Uso: $0 hub-edge-key|hub-command-public-key"; exit 1 ;;
esac
read -r -s -p "Valore per $NAME: " VALUE; echo
[ -n "$VALUE" ] || { echo "Valore vuoto"; exit 1; }
# -w as the last option makes `security` ask for the password itself; feed it twice (it asks to retype)
printf '%s\n%s\n' "$VALUE" "$VALUE" | /usr/bin/security add-generic-password -U -s it.adprogetti.zio.edge -a "$NAME" -w
echo "Salvato nel portachiavi: $NAME"
