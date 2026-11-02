#!/usr/bin/env bash
set -euo pipefail

PORT="${1:-5000}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

cd "$SCRIPT_DIR"
mkdir -p out

echo "Compiling server..."
if command -v javac >/dev/null 2>&1; then
    javac -encoding UTF-8 -source 8 -target 8 -d out src/Main.java
elif [ -f out/Main.class ]; then
    echo "javac not found, using existing compiled files in out/"
elif [ -f src/Main.class ]; then
    echo "javac not found, using existing compiled files in src/"
    exec java -cp src Main "$PORT"
else
    echo "javac command not found."
    echo "Please install JDK or upload compiled Main.class files."
    exit 1
fi

echo "Starting chat server on port ${PORT}"
echo "Press Ctrl+C to stop."
exec java -cp out Main "$PORT"


#开启服务
#./start_server.sh 5000
#停止服务
#pkill -f "java -cp out Main 5000"