#!/usr/bin/env bash
#
# Boots a throwaway local Paper server with the freshly-built CivPressure plugin
# so you can test in-game with a normal Minecraft client (connect to localhost).
#
# Usage:
#   scripts/run-test-server.sh            # uses Paper for MC 26.3
#   scripts/run-test-server.sh 26.3       # explicit version
#   MEM=4G scripts/run-test-server.sh     # more RAM
#
# Nothing here touches your live Apex server. The server files live in ./run/
# (gitignored). Delete that folder any time to start fresh.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

MC_VERSION="${1:-26.3}"
RUN_DIR="$ROOT/run"
MEM="${MEM:-2G}"
USER_AGENT="CivPressure-test-server/26.3 (https://github.com/dhayer200/CivPressure)"

# --- 1. Find a Java 25 runtime (Paper 26.3 requires it) --------------------
find_java25() {
  # Prefer a JDK 25 Gradle already provisioned, then common install locations.
  local candidates=()
  candidates+=( "$HOME"/.gradle/jdks/*/jdk-25*/Contents/Home/bin/java )
  candidates+=( /Library/Java/JavaVirtualMachines/*25*/Contents/Home/bin/java )
  candidates+=( /opt/homebrew/opt/openjdk@25/bin/java )
  local j
  for j in "${candidates[@]}"; do
    if [ -x "$j" ] && "$j" -version 2>&1 | head -1 | grep -q '"25'; then
      echo "$j"; return 0
    fi
  done
  # Fall back to whatever `java` is on PATH if it happens to be 25.
  if command -v java >/dev/null && java -version 2>&1 | head -1 | grep -q '"25'; then
    command -v java; return 0
  fi
  return 1
}

JAVA_BIN="$(find_java25 || true)"
if [ -z "${JAVA_BIN:-}" ]; then
  echo "ERROR: No Java 25 found. Paper $MC_VERSION needs Java 25." >&2
  echo "Install it with:  brew install --cask temurin@25" >&2
  exit 1
fi
echo ">> Using Java: $JAVA_BIN"

# --- 2. Build the plugin ---------------------------------------------------
echo ">> Building CivPressure.jar ..."
./gradlew build -q
PLUGIN_JAR="$ROOT/build/libs/CivPressure.jar"
[ -f "$PLUGIN_JAR" ] || { echo "ERROR: build/libs/CivPressure.jar not found" >&2; exit 1; }

# --- 3. Download a Paper server jar (once) ---------------------------------
mkdir -p "$RUN_DIR/plugins"
PAPER_JAR="$RUN_DIR/paper-$MC_VERSION.jar"
if [ ! -f "$PAPER_JAR" ]; then
  echo ">> Fetching latest Paper build for $MC_VERSION ..."
  API="https://fill.papermc.io/v3/projects/paper/versions/${MC_VERSION}/builds"
  URL="$(curl -fsSL -A "$USER_AGENT" "$API" | python3 -c '
import json, sys
builds = json.load(sys.stdin)
if not isinstance(builds, list) or not builds:
    sys.exit("No Paper builds returned for this version")
stable = [b for b in builds if b.get("channel") == "STABLE"]
chosen = stable[0] if stable else builds[0]
download = (chosen.get("downloads") or {}).get("server:default") or {}
url = download.get("url")
if not url:
    sys.exit("Paper build is missing a server:default download URL")
print(url)
print(">> Using Paper " + str(chosen.get("channel", "UNKNOWN")) + " build " + str(chosen.get("id")), file=sys.stderr)
')"
  echo ">> Downloading $URL ..."
  curl -fsSL -A "$USER_AGENT" -o "$PAPER_JAR" "$URL"
fi

# --- 4. First-run server config (offline mode = no auth hassle) ------------
echo "eula=true" > "$RUN_DIR/eula.txt"
if [ ! -f "$RUN_DIR/server.properties" ]; then
  cat > "$RUN_DIR/server.properties" <<'EOF'
online-mode=false
spawn-protection=0
view-distance=8
simulation-distance=6
motd=CivPressure Test Server
max-players=5
allow-nether=true
level-type=minecraft:normal
EOF
fi

# Keep the freshly built plugin in sync every launch.
cp -f "$PLUGIN_JAR" "$RUN_DIR/plugins/CivPressure.jar"

# --- 5. Launch -------------------------------------------------------------
echo ">> Starting server. Connect your client to:  localhost"
echo ">>   In this console, run:  op <yourname>   then test with /civ ..."
echo ">>   Stop the server with:  stop"
cd "$RUN_DIR"
exec "$JAVA_BIN" -Xms"$MEM" -Xmx"$MEM" -jar "$PAPER_JAR" --nogui
