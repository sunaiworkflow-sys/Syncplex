#!/bin/bash

# Generate firebase-config.js from environment variables
# This script should be run during deployment or before starting the application

echo "📝 Generating firebase-config.js from environment variables..."

# Target file location
TARGET_FILE="src/main/resources/static/firebase-config.js"

# Check if Firebase environment variables are set
if [ -z "$FIREBASE_API_KEY" ]; then
    echo "⚠️  Warning: FIREBASE_API_KEY not set. Firebase authentication may not work."
    echo "   Using template file instead."
    exit 0
fi

# Generate the firebase-config.js file
cat > "$TARGET_FILE" << EOF
// Firebase Configuration
// This file is auto-generated from environment variables
// DO NOT EDIT MANUALLY - Changes will be overwritten

import { initializeApp } from "https://www.gstatic.com/firebasejs/10.7.1/firebase-app.js";
import { getAuth } from "https://www.gstatic.com/firebasejs/10.7.1/firebase-auth.js";
import { getAnalytics } from "https://www.gstatic.com/firebasejs/10.7.1/firebase-analytics.js";

const firebaseConfig = {
    apiKey: "${FIREBASE_API_KEY}",
    authDomain: "${FIREBASE_AUTH_DOMAIN}",
    projectId: "${FIREBASE_PROJECT_ID}",
    storageBucket: "${FIREBASE_STORAGE_BUCKET}",
    messagingSenderId: "${FIREBASE_MESSAGING_SENDER_ID}",
    appId: "${FIREBASE_APP_ID}",
    measurementId: "${FIREBASE_MEASUREMENT_ID}"
};

const app = initializeApp(firebaseConfig);
const analytics = getAnalytics(app);
export const auth = getAuth(app);
export default app;
EOF

echo "✅ firebase-config.js generated successfully at $TARGET_FILE"
