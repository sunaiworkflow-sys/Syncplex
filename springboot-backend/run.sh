#!/bin/bash

# Function to kill background processes on exit
cleanup() {
    echo ""
    echo "🛑 Stopping servers..."
    kill $BOOT_PID 2>/dev/null
    echo "✅ Done. Bye!"
    exit
}

# Trap Ctrl+C (SIGINT)
trap cleanup SIGINT

echo "=================================================="
echo "   🚀 Starting SyncPlex JD-Resume Engine"
echo "=================================================="

# 0. Load Env from Parent (Project Root)
if [ -f ../.env ]; then
  echo "📄 Loading environment variables from ../.env"
  export $(grep -v '^#' ../.env | grep -v '^$' | xargs)
fi

# 1. Check MongoDB
echo "🔍 Checking MongoDB..."
if ! pgrep -x "mongod" > /dev/null
then
    echo "⚠️  MongoDB is NOT running."
    echo "   Attempting to start via Homebrew..."
    brew services start mongodb-community@7.0 2>/dev/null || brew services start mongodb-community 2>/dev/null
    sleep 2
    if ! pgrep -x "mongod" > /dev/null; then
        echo "❌ Failed to start MongoDB. Please run 'mongod' manually in another tab."
    else
        echo "✅ MongoDB started."
    fi
else
    echo "✅ MongoDB is running."
fi

# 2. Start Spring Boot Backend
echo "--------------------------------------------------"
echo "🌱 Starting Spring Boot Backend (Port 8080)..."
echo "   This also serves the Frontend at http://localhost:8080"
echo "   Logs redirected to error.log"

mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8080 > error.log 2>&1 &
BOOT_PID=$!
echo "   Backend Process ID: $BOOT_PID"

# 4. Wait for Backend (optimistic)
echo "--------------------------------------------------"
echo "⏳ Waiting 5 seconds for Spring Boot to warm up..."
# Progress bar animation
for i in {1..5}; do
    printf "▓"
    sleep 1
done
echo " Ready!"

# 5. Open Browser
echo "--------------------------------------------------"
echo "🌐 Opening http://localhost:8080/index.html"
open "http://localhost:8080/index.html"

echo "=================================================="
echo "   🎉 SYSTEM IS LIVE"
echo "   URL: http://localhost:8080"
echo ""
echo "   👉 Press Ctrl+C to stop the servers."
echo "=================================================="

# Keep running
wait
