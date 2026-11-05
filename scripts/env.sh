# Usage: source scripts/env.sh  — Java 21, Maven 3.9, Node 24 LTS (installed in ~/tools)
if [ -d "$HOME/tools/jdk-21.0.13+9/Contents/Home" ]; then export JAVA_HOME="$HOME/tools/jdk-21.0.13+9/Contents/Home"; else export JAVA_HOME=$(/usr/libexec/java_home -v 21); fi
export PATH="$JAVA_HOME/bin:$PATH"
export PATH="$HOME/tools/node-v24.22.2-darwin-arm64/bin:$HOME/tools/apache-maven-3.9.16/bin:$PATH"

# Container runtime (Colima) for Testcontainers
export PATH="/opt/homebrew/bin:$PATH"
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
