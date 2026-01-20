# Deployment Guide

This guide covers deploying the JD-Resume Matching Engine to various cloud platforms.

## Quick Comparison

| Platform | Difficulty | Free Tier | Best For |
|----------|------------|-----------|----------|
| **Railway** | ⭐ Easy | $5 credit/month | Quick deployment, auto-scaling |
| **Render** | ⭐ Easy | 750 hours/month | Simple web services |
| **Heroku** | ⭐⭐ Medium | Limited | Established platform |
| **AWS EC2** | ⭐⭐⭐ Advanced | 1 year free | Full control |
| **Docker** | ⭐⭐ Medium | N/A | Any container platform |

---

## Option 1: Railway (Recommended - Easiest)

Railway offers the simplest deployment experience with automatic builds.

### Steps:

1. **Go to [railway.app](https://railway.app)** and sign up with GitHub

2. **Create New Project** → **Deploy from GitHub repo**
   - Select: `sunaiworkflow-sys/Syncplex`

3. **Configure Environment Variables** in Railway dashboard:
   ```
   MONGO_URI=mongodb+srv://...
   OPENAI_API_KEY=sk-...
   AWS_ACCESS_KEY_ID=...
   AWS_SECRET_ACCESS_KEY=...
   AWS_REGION=eu-north-1
   AWS_BUCKET_NAME=syncplex
   GEMINI_API_KEY=...
   FIREBASE_API_KEY=...
   FIREBASE_AUTH_DOMAIN=...
   FIREBASE_PROJECT_ID=...
   ```

4. **Deploy** - Railway will automatically:
   - Detect Java/Maven project
   - Run `mvn package`
   - Start the application

5. **Get your URL**: Railway provides a `*.railway.app` URL

---

## Option 2: Render

### Steps:

1. **Go to [render.com](https://render.com)** and connect GitHub

2. **New Web Service** → Select your repo

3. **Configure:**
   - **Build Command**: `cd springboot-backend && ./mvnw clean package -DskipTests`
   - **Start Command**: `java -jar springboot-backend/target/jd-resume-matching-1.0.0.jar`
   - **Environment**: Add all env variables

4. **Deploy**

---

## Option 3: Docker (Any Platform)

Use this for AWS ECS, Google Cloud Run, DigitalOcean, etc.

### Build & Run Locally:
```bash
# Build the Docker image
docker build -t syncplex .

# Run with environment variables
docker run -p 8080:8080 \
  -e MONGO_URI="mongodb+srv://..." \
  -e OPENAI_API_KEY="sk-..." \
  -e AWS_ACCESS_KEY_ID="..." \
  -e AWS_SECRET_ACCESS_KEY="..." \
  -e AWS_REGION="eu-north-1" \
  -e AWS_BUCKET_NAME="syncplex" \
  syncplex
```

### Push to Docker Hub:
```bash
docker tag syncplex yourusername/syncplex:latest
docker push yourusername/syncplex:latest
```

---

## Option 4: Manual JAR Deployment (VPS/EC2)

For any Linux server (AWS EC2, DigitalOcean Droplet, etc.)

### 1. Build the JAR locally:
```bash
cd springboot-backend
./mvnw clean package -DskipTests
```

### 2. Copy to server:
```bash
scp target/jd-resume-matching-1.0.0.jar user@your-server:/opt/syncplex/
```

### 3. Create systemd service on server:
```bash
sudo nano /etc/systemd/system/syncplex.service
```

```ini
[Unit]
Description=SyncPlex JD-Resume Matching Engine
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/opt/syncplex
ExecStart=/usr/bin/java -jar jd-resume-matching-1.0.0.jar
Restart=always
RestartSec=10

# Environment Variables
Environment="MONGO_URI=mongodb+srv://..."
Environment="OPENAI_API_KEY=sk-..."
Environment="AWS_ACCESS_KEY_ID=..."
Environment="AWS_SECRET_ACCESS_KEY=..."
Environment="AWS_REGION=eu-north-1"
Environment="AWS_BUCKET_NAME=syncplex"

[Install]
WantedBy=multi-user.target
```

### 4. Start the service:
```bash
sudo systemctl daemon-reload
sudo systemctl start syncplex
sudo systemctl enable syncplex  # Auto-start on boot
```

### 5. Check status:
```bash
sudo systemctl status syncplex
sudo journalctl -u syncplex -f  # View logs
```

---

## Environment Variables Reference

All these must be set in your deployment platform:

| Variable | Required | Description |
|----------|----------|-------------|
| `MONGO_URI` | ✅ | MongoDB Atlas connection string |
| `OPENAI_API_KEY` | ✅ | OpenAI API key |
| `AWS_ACCESS_KEY_ID` | ✅ | AWS access key |
| `AWS_SECRET_ACCESS_KEY` | ✅ | AWS secret key |
| `AWS_REGION` | ✅ | AWS region (e.g., `eu-north-1`) |
| `AWS_BUCKET_NAME` | ✅ | S3 bucket name |
| `GEMINI_API_KEY` | ⚪ | Fallback AI (optional) |
| `FIREBASE_API_KEY` | ⚪ | Firebase auth (optional) |
| `FIREBASE_AUTH_DOMAIN` | ⚪ | Firebase domain |
| `FIREBASE_PROJECT_ID` | ⚪ | Firebase project |
| `SERVER_PORT` | ⚪ | Default: 8080 (platforms override) |

---

## Troubleshooting

### Application won't start
- Check logs: `railway logs` or platform dashboard
- Verify all required env variables are set
- Ensure MongoDB Atlas whitelist includes `0.0.0.0/0` for cloud access

### MongoDB connection fails
- Whitelist IP: MongoDB Atlas → Network Access → Add `0.0.0.0/0`
- Check connection string format

### S3 upload fails
- Verify IAM permissions
- Check bucket region matches `AWS_REGION`

---

## Post-Deployment Checklist

- [ ] Application accessible at deployed URL
- [ ] Health check passes: `GET /api/health`
- [ ] MongoDB connected (check logs)
- [ ] S3 uploads working
- [ ] Firebase auth working (if enabled)
