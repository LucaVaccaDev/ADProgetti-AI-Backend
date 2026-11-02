# Usage: source scripts/env.sh  — Java 21, Maven 3.9, Node 24 LTS (installed in ~/tools)
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$HOME/tools/node-v24.22.2-darwin-arm64/bin:$HOME/tools/apache-maven-3.9.16/bin:$PATH"

# Container runtime (Colima) for Testcontainers
export PATH="/opt/homebrew/bin:$PATH"
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
