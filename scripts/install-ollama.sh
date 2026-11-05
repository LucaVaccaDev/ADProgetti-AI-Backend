#!/usr/bin/env bash
# Installs Ollama for the Edge user: verified official binary in ~/tools/ollama, models on the
# external SSD, launchd service listening on 127.0.0.1 only. Idempotent. Does NOT download models.
# Usage: bash scripts/install-ollama.sh [version]   (default: version below)
set -euo pipefail

VERSION="${1:-0.40.14}"
SHA256="${OLLAMA_SHA256:-f3bf9a528e908da0da5a2b3a4ad3252a369d62186501dc4880261e8d9023bff5}"  # ollama-darwin.tgz v0.40.14
MODELS_DIR="${OLLAMA_MODELS_DIR:-/Volumes/AI/ollama/models}"
BIN_DIR="$HOME/tools/ollama"
LOG_DIR="$HOME/Library/Logs"
LABEL="it.adprogetti.zio.ollama"
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

[ "$(uname -m)" = "arm64" ] || { echo "Apple Silicon richiesto"; exit 1; }
[ -d "$(dirname "$(dirname "$MODELS_DIR")")" ] || { echo "SSD non montato: $(dirname "$(dirname "$MODELS_DIR")")"; exit 1; }

if [ ! -x "$BIN_DIR/ollama" ] || [ "$("$BIN_DIR/ollama" --version 2>&1 | sed -n 's/.*client version is //p')" != "$VERSION" ]; then
  TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
  curl -fsSL -o "$TMP/ollama.tgz" "https://github.com/ollama/ollama/releases/download/v$VERSION/ollama-darwin.tgz"
  [ "$(shasum -a 256 "$TMP/ollama.tgz" | cut -d' ' -f1)" = "$SHA256" ] || { echo "Hash non corrispondente"; exit 1; }
  mkdir -p "$BIN_DIR"; tar xzf "$TMP/ollama.tgz" -C "$BIN_DIR"
  xattr -dr com.apple.quarantine "$BIN_DIR" 2>/dev/null || true
fi

mkdir -p "$MODELS_DIR" "$LOG_DIR" "$HOME/Library/LaunchAgents"
sed -e "s|@OLLAMA_BIN@|$BIN_DIR/ollama|" -e "s|@MODELS_DIR@|$MODELS_DIR|" -e "s|@LOG_DIR@|$LOG_DIR|" \
  "$ROOT/infra/launchd/$LABEL.plist.template" > "$PLIST"
chmod 644 "$PLIST"

launchctl bootout "gui/$(id -u)/$LABEL" 2>/dev/null || true
launchctl bootstrap "gui/$(id -u)" "$PLIST"
for _ in $(seq 1 20); do curl -fs http://127.0.0.1:11434/api/version >/dev/null 2>&1 && break || sleep 1; done
curl -fs http://127.0.0.1:11434/api/version && echo
echo "Ollama pronto. Modelli in: $MODELS_DIR (nessun modello scaricato)"
