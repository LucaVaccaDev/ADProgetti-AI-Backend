#!/usr/bin/env bash
# Stores an Edge secret in the macOS keychain. The value is typed at a hidden prompt, so it never
# appears in shell history or in the process list.
#   hub-edge-key             credential shown once by the Hub (POST /api/integrations/edge/credential)
#   hub-command-public-key   Hub public key (GET /api/integrations/edge/public-key)
#   freellmapi-key           unified FreeLLMAPI key (freellmapi-...), from the Keys page of its dashboard
# `security -i` reads its command from stdin, so the value is not on any command line. (Piping the value to
# `security add-generic-password -w` does not work in a real Terminal: it then asks for the password on the tty.)
set -euo pipefail
NAME="${1:-}"
case "$NAME" in
  hub-edge-key|hub-command-public-key|freellmapi-key) ;;
  *) echo "Uso: $0 hub-edge-key|hub-command-public-key|freellmapi-key"; exit 1 ;;
esac
SERVICE=it.adprogetti.zio.edge
read -r -s -p "Valore per $NAME: " VALUE; echo
[ -n "$VALUE" ] || { echo "Valore vuoto"; exit 1; }
case "$VALUE" in
  *[!A-Za-z0-9._~+/=-]*) echo "Il valore contiene caratteri inattesi (spazi, virgolette?): controlla di aver copiato solo la chiave"; exit 1 ;;
esac
printf 'add-generic-password -U -s %s -a %s -w "%s"\n' "$SERVICE" "$NAME" "$VALUE" | /usr/bin/security -i >/dev/null
[ "$(/usr/bin/security find-generic-password -s "$SERVICE" -a "$NAME" -w | wc -c | tr -d ' ')" -gt "$(( ${#VALUE} ))" ] \
  || { echo "Salvataggio non riuscito"; exit 1; }
unset VALUE
echo "Salvato nel portachiavi: $NAME"
