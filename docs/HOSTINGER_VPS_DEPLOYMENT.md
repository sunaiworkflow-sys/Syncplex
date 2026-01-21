# Hostinger VPS Deployment Guide

This guide provides step-by-step instructions for deploying the JD-Resume Matching Engine on a **Hostinger VPS**.

## Prerequisites

- Hostinger VPS with Ubuntu 22.04+ (recommended)
- SSH access to your VPS
- Domain name pointed to your VPS IP (optional, for HTTPS)
- Your `.env` file with all required environment variables

---

## Step 1: Connect to Your Hostinger VPS

```bash
ssh root@your-vps-ip
# Or if you set up a different user:
ssh your-username@your-vps-ip
```

---

## Step 2: Install Docker & Docker Compose

```bash
# Update system packages
sudo apt update && sudo apt upgrade -y

# Install required packages
sudo apt install -y apt-transport-https ca-certificates curl software-properties-common

# Add Docker's official GPG key
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg

# Add Docker repository
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io

# Install Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Add your user to docker group (if not root)
sudo usermod -aG docker $USER

# Verify installation
docker --version
docker-compose --version
```

---

## Step 3: Clone Your Repository

```bash
# Create application directory
sudo mkdir -p /opt/syncplex
cd /opt/syncplex

# Clone your repository
git clone https://github.com/sunaiworkflow-sys/Syncplex.git .

# Or if using SSH:
git clone git@github.com:sunaiworkflow-sys/Syncplex.git .
```

---

## Step 4: Configure Environment Variables

```bash
# Copy the example environment file
cp .env.example .env

# Edit with your actual values
nano .env
```

**Required variables to set:**
```env
# MongoDB Atlas
MONGO_URI=mongodb+srv://your-user:your-password@cluster.mongodb.net/jd_resume_db

# OpenAI
OPENAI_API_KEY=sk-your-openai-key

# AWS S3
AWS_ACCESS_KEY_ID=your-aws-key
AWS_SECRET_ACCESS_KEY=your-aws-secret
AWS_REGION=eu-north-1
AWS_BUCKET_NAME=syncplex

# Gemini (fallback)
GEMINI_API_KEY=your-gemini-key

# Firebase (if using authentication)
FIREBASE_API_KEY=your-firebase-key
FIREBASE_AUTH_DOMAIN=your-project.firebaseapp.com
FIREBASE_PROJECT_ID=your-project-id
```

---

## Step 5: Deploy the Application

### Option A: Basic Deployment (HTTP only)

```bash
# Build and start the application
docker-compose up -d --build

# Check if container is running
docker-compose ps

# View logs
docker-compose logs -f app
```

Your app will be accessible at: `http://your-vps-ip:8080`

### Option B: Production Deployment with SSL/HTTPS

First, update the `.env` file with your domain:

```env
# Add these to your .env file
DOMAIN=yourdomain.com
ACME_EMAIL=your-email@example.com
```

Then deploy with the production compose file:

```bash
# Build and start with SSL
docker-compose -f docker-compose.prod.yml up -d --build

# Check status
docker-compose -f docker-compose.prod.yml ps

# View logs
docker-compose -f docker-compose.prod.yml logs -f
```

Your app will be accessible at: `https://yourdomain.com`

---

## Step 6: Configure Hostinger Firewall

In your Hostinger VPS control panel or via command line:

```bash
# Allow SSH (should already be open)
sudo ufw allow 22/tcp

# Allow HTTP
sudo ufw allow 80/tcp

# Allow HTTPS
sudo ufw allow 443/tcp

# If using basic deployment without Traefik, also allow 8080
sudo ufw allow 8080/tcp

# Enable firewall
sudo ufw enable

# Check status
sudo ufw status
```

---

## Step 7: Verify Deployment

```bash
# Check container status
docker-compose ps

# Test health endpoint
curl http://localhost:8080/api/health

# View application logs
docker-compose logs -f app

# Check container resource usage
docker stats
```

---

## Common Commands

### Start/Stop/Restart

```bash
# Start
docker-compose up -d

# Stop
docker-compose down

# Restart
docker-compose restart

# Rebuild and restart (after code changes)
docker-compose up -d --build
```

### View Logs

```bash
# All logs
docker-compose logs -f

# App logs only
docker-compose logs -f app

# Last 100 lines
docker-compose logs --tail=100 app
```

### Update Application

```bash
# Pull latest code
cd /opt/syncplex
git pull origin main

# Rebuild and restart
docker-compose up -d --build
```

---

## Troubleshooting

### Container won't start

```bash
# Check logs for errors
docker-compose logs app

# Check if port is in use
sudo lsof -i :8080

# Verify environment variables
docker-compose config
```

### MongoDB connection fails

1. Go to MongoDB Atlas → Network Access
2. Add your VPS IP address OR `0.0.0.0/0` (allow all)
3. Verify connection string format in `.env`

### Out of memory

```bash
# Check memory usage
free -h

# Increase swap (if needed)
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile

# Make permanent
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### Application health check failing

```bash
# Wait for app to fully start (up to 60 seconds)
sleep 60

# Check health manually
curl -v http://localhost:8080/api/health

# Check container health
docker inspect --format='{{.State.Health.Status}}' syncplex-app
```

---

## Backup & Maintenance

### Backup Docker Volumes

```bash
# Backup uploads
docker run --rm -v syncplex-uploads:/data -v $(pwd):/backup alpine tar czf /backup/uploads-backup.tar.gz -C /data .

# Backup logs
docker run --rm -v syncplex-logs:/data -v $(pwd):/backup alpine tar czf /backup/logs-backup.tar.gz -C /data .
```

### Automatic Updates with Watchtower

If using `docker-compose.prod.yml`, Watchtower is included and will automatically check for updates every 24 hours.

---

## Quick Reference

| Action | Command |
|--------|---------|
| Start | `docker-compose up -d` |
| Stop | `docker-compose down` |
| Restart | `docker-compose restart` |
| View logs | `docker-compose logs -f` |
| Rebuild | `docker-compose up -d --build` |
| Status | `docker-compose ps` |
| Health check | `curl http://localhost:8080/api/health` |

---

## Support

If you encounter issues:
1. Check logs: `docker-compose logs -f app`
2. Verify environment variables: `docker-compose config`
3. Check MongoDB Atlas network access settings
4. Ensure all required ports are open in Hostinger firewall
