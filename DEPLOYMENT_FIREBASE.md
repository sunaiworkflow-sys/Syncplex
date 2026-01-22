# Deployment Guide - Firebase Configuration

## Problem
The `firebase-config.js` file contains sensitive credentials and is excluded from Git. During deployment, we need to generate it from environment variables.

## Solution
The deployment build process now automatically generates `firebase-config.js` from environment variables **before** the Maven build.

## How It Works

### Build Process Flow:
1. **Generate Firebase Config**: Run `generate-firebase-config.sh` 
2. **Build Application**: Run Maven package
3. **Deploy**: Start the Spring Boot application

### Configuration Files Updated:
- ✅ `nixpacks.toml` - For Railway/Render deployments
- ✅ `railway.toml` - For Railway-specific deployments  
- ✅ `Dockerfile` - For Docker-based deployments
- ✅ `run.sh` - For local development

## Deployment Instructions

### Step 1: Set Environment Variables on Your Platform

**Required Firebase Variables:**
```
FIREBASE_API_KEY=AIzaSyCr0LUhwl5MdDCe7RBw6XPb6t0_-RmOzmw
FIREBASE_AUTH_DOMAIN=syncplex-ca5c8.firebaseapp.com
FIREBASE_PROJECT_ID=syncplex-ca5c8
FIREBASE_STORAGE_BUCKET=syncplex-ca5c8.firebasestorage.app
FIREBASE_MESSAGING_SENDER_ID=972241272858
FIREBASE_APP_ID=1:972241272858:web:ebdb48134de438ba21d1a3
FIREBASE_MEASUREMENT_ID=G-RS4F4WL5CP
```

### Step 2: Platform-Specific Setup

#### Railway
```bash
# Using Railway CLI
railway variables set FIREBASE_API_KEY="your_key_here"
railway variables set FIREBASE_AUTH_DOMAIN="your_domain_here"
# ... repeat for all Firebase variables

# Or use the Railway Dashboard:
# 1. Go to your project
# 2. Variables tab
# 3. Add all Firebase variables
# 4. Redeploy
```

#### Heroku
```bash
heroku config:set FIREBASE_API_KEY="your_key_here"
heroku config:set FIREBASE_AUTH_DOMAIN="your_domain_here"
# ... repeat for all Firebase variables
```

#### Render
```
1. Dashboard → Environment
2. Add each Firebase variable
3. Save & Deploy
```

#### Docker Deployment
```bash
# Option 1: Use .env file
docker-compose --env-file .env up

# Option 2: Pass variables directly
docker run -e FIREBASE_API_KEY="..." -e FIREBASE_AUTH_DOMAIN="..." yourimage
```

### Step 3: Enable Google Sign-In in Firebase

1. Go to [Firebase Console](https://console.firebase.google.com)
2. Select your project: **syncplex-ca5c8**
3. Authentication → Sign-in method
4. Enable **Google** provider
5. Under **Authorized domains**, add your deployment domain:
   - `localhost` (already there)
   - `yourapp.railway.app`
   - `yourapp.onrender.com`
   - Or your custom domain

### Step 4: Deploy

Push your code and the build process will:
1. Load environment variables
2. Run `generate-firebase-config.sh` to create `firebase-config.js`
3. Build the application with Maven
4. Package everything into the JAR

## Verification

After deployment, check:

1. **Build logs** should show:
   ```
   📝 Generating firebase-config.js from environment variables...
   ✅ firebase-config.js generated successfully
   ```

2. **Test Google Login**:
   - Go to your deployed app
   - Click "Login with Google"
   - Should redirect to Google authentication

## Troubleshooting

### Error: "Firebase config not generated"
**Cause**: Environment variables not set during build
**Solution**: Verify all Firebase variables are set in your platform's environment settings

### Error: "Module not found: firebase-config.js"
**Cause**: The script didn't run during build
**Solution**: Check build logs for errors in `generate-firebase-config.sh`

### Google Login popup blocked
**Cause**: Domain not authorized in Firebase
**Solution**: Add your domain to Firebase Console → Authentication → Authorized domains

### Error: "auth/unauthorized-domain"
**Cause**: Your deployment domain is not in Firebase authorized domains
**Solution**: Add the domain in Firebase Console

## Files Reference

| File | Purpose | Environment |
|------|---------|-------------|
| `generate-firebase-config.sh` | Script to create config from env vars | All environments |
| `firebase-config.js` | **Generated file** (not in Git) | Created at build time |
| `firebase-config.template.js` | Template showing structure | Reference only |
| `nixpacks.toml` | Railway/Render build config | Cloud deployment |
| `railway.toml` | Railway-specific config | Railway deployment |
| `Dockerfile` | Docker build config | Docker deployment |

## Security Notes

✅ **Good**: 
- `firebase-config.js` is in `.gitignore`
- Credentials stored as environment variables
- File generated at build time

❌ **Bad**:
- Never commit `firebase-config.js`
- Never hardcode credentials in code
- Don't expose Firebase API keys in client-side code (they're meant to be public but domain-restricted)
