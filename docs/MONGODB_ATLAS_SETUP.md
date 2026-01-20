# MongoDB Atlas Setup Guide

This guide walks you through setting up MongoDB Atlas (cloud-hosted MongoDB) for the JD-Resume Matching Engine.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Create MongoDB Atlas Account](#step-1-create-mongodb-atlas-account)
3. [Create a Cluster](#step-2-create-a-cluster)
4. [Configure Database Access](#step-3-configure-database-access)
5. [Configure Network Access](#step-4-configure-network-access)
6. [Get Connection String](#step-5-get-connection-string)
7. [Configure Environment Variables](#step-6-configure-environment-variables)
8. [Verify Connection](#step-7-verify-connection)
9. [Data Migration (Optional)](#step-8-data-migration-optional)
10. [Troubleshooting](#troubleshooting)

---

## Prerequisites

- An email address for MongoDB Atlas account
- Basic understanding of database concepts
- Your application's IP address (or enable access from anywhere for development)

---

## Step 1: Create MongoDB Atlas Account

1. Go to [MongoDB Atlas](https://www.mongodb.com/cloud/atlas)
2. Click **"Try Free"** or **"Get Started Free"**
3. Sign up with:
   - Email and password, OR
   - Google account, OR
   - GitHub account
4. Complete email verification
5. Fill out the onboarding survey (optional)

---

## Step 2: Create a Cluster

### 2.1 Choose a Plan

| Plan | Cost | Best For |
|------|------|----------|
| **M0 Sandbox (Free)** | $0/month | Development, learning, small projects |
| **M2 Shared** | ~$9/month | Small production apps |
| **M10+ Dedicated** | $57+/month | Production with scaling needs |

> **Recommendation:** Start with **M0 (Free Tier)** for development.

### 2.2 Cluster Configuration

1. Click **"Build a Database"** or **"Create"**
2. Select **"M0 FREE"** tier
3. **Choose a Cloud Provider:**
   - AWS (recommended for AWS integration)
   - Google Cloud
   - Azure

4. **Select a Region** (choose one close to your deployment):

   | Provider | Recommended Regions |
   |----------|---------------------|
   | AWS | `eu-north-1` (Stockholm), `us-east-1` (Virginia), `ap-south-1` (Mumbai) |
   | GCP | `europe-north1`, `us-central1`, `asia-south1` |
   | Azure | `westeurope`, `eastus`, `centralindia` |

5. **Cluster Name:** `jd-resume-cluster` (or your preferred name)

6. Click **"Create Cluster"**

> ⏳ Cluster creation takes 1-3 minutes.

---

## Step 3: Configure Database Access

Create a database user for your application.

### 3.1 Create Database User

1. Go to **"Database Access"** in the left sidebar
2. Click **"Add New Database User"**

### 3.2 Authentication Method

Select **"Password"** authentication:

| Setting | Value |
|---------|-------|
| **Username** | `jd-resume-app` (or your choice) |
| **Password** | Click "Autogenerate Secure Password" and **SAVE IT!** |

> ⚠️ **Important:** Copy and save the password immediately. You'll need it for the connection string.

### 3.3 Database User Privileges

Select one of:
- **Atlas Admin** (full access - good for development)
- **Read and write to any database** (recommended for production)

### 3.4 Restrict to Specific Database (Optional - More Secure)

1. Click **"Specific Privileges"**
2. Add:
   - Database: `jd_resume_db`
   - Role: `readWrite`

3. Click **"Add User"**

---

## Step 4: Configure Network Access

MongoDB Atlas blocks all IPs by default. You must whitelist your IP addresses.

### 4.1 Add IP Address

1. Go to **"Network Access"** in the left sidebar
2. Click **"Add IP Address"**

### 4.2 Choose Access Level

| Option | Use Case |
|--------|----------|
| **Add My Current IP** | Development on your machine |
| **Allow Access from Anywhere (0.0.0.0/0)** | Cloud deployment, dynamic IPs |
| **Add Specific IP** | Production server with static IP |

> **For Development:** Click "Add My Current IP Address"
> 
> **For Cloud Deployment (Heroku, Render, etc.):** Click "Allow Access from Anywhere"

3. Add a comment (e.g., "Development Machine")
4. Click **"Confirm"**

### 4.3 Multiple IPs (Production Setup)

For production, add all necessary IPs:
- Your development machine
- CI/CD servers
- Production servers
- Team members' IPs (if applicable)

---

## Step 5: Get Connection String

### 5.1 Navigate to Connection

1. Go to **"Database"** in the left sidebar
2. Find your cluster and click **"Connect"**
3. Select **"Connect your application"**

### 5.2 Choose Driver

| Setting | Value |
|---------|-------|
| **Driver** | Java |
| **Version** | 4.3 or later |

### 5.3 Copy Connection String

You'll see a connection string like:

```
mongodb+srv://<username>:<password>@jd-resume-cluster.xxxxx.mongodb.net/?retryWrites=true&w=majority
```

### 5.4 Customize the Connection String

Replace placeholders:
- `<username>` → Your database username (e.g., `jd-resume-app`)
- `<password>` → Your database password (URL-encode special characters!)
- Add database name before `?` → `jd_resume_db`

**Final format:**
```
mongodb+srv://jd-resume-app:YourPassword123@jd-resume-cluster.xxxxx.mongodb.net/jd_resume_db?retryWrites=true&w=majority
```

### 5.5 URL-Encode Special Characters

If your password contains special characters, encode them:

| Character | Encoded |
|-----------|---------|
| `@` | `%40` |
| `:` | `%3A` |
| `/` | `%2F` |
| `#` | `%23` |
| `?` | `%3F` |
| `&` | `%26` |
| `=` | `%3D` |
| `+` | `%2B` |
| `%` | `%25` |
| ` ` (space) | `%20` |

**Example:** Password `MyP@ss+word!` becomes `MyP%40ss%2Bword!`

---

## Step 6: Configure Environment Variables

Update your `.env` file:

```bash
# MongoDB Atlas Configuration
# Format: mongodb+srv://<username>:<password>@<cluster>.mongodb.net/<database>?retryWrites=true&w=majority
MONGO_URI=mongodb+srv://jd-resume-app:YourEncodedPassword@jd-resume-cluster.xxxxx.mongodb.net/jd_resume_db?retryWrites=true&w=majority
```

### Important Notes:

1. **No quotes** around the value
2. **URL-encode** special characters in password
3. **Include database name** (`jd_resume_db`) in the URL
4. Keep `?retryWrites=true&w=majority` for reliability

---

## Step 7: Verify Connection

### 7.1 Start Your Application

```bash
cd springboot-backend
./mvnw spring-boot:run
```

### 7.2 Check Logs for Success

Look for:
```
... MongoDB connection successful
... Connected to: jd-resume-cluster.xxxxx.mongodb.net
```

### 7.3 Test Database Operations

1. Upload a resume through the application
2. Check MongoDB Atlas:
   - Go to **"Database"** → **"Browse Collections"**
   - You should see:
     - `jd_resume_db` database
     - `resumes` collection
     - Your uploaded document

---

## Step 8: Data Migration (Optional)

If you have existing data in a local MongoDB instance, migrate it to Atlas.

### 8.1 Export from Local MongoDB

```bash
# Export all collections
mongodump --db jd_resume_db --out ./backup

# Or export specific collection
mongoexport --db jd_resume_db --collection resumes --out resumes.json
```

### 8.2 Import to Atlas

```bash
# Using mongorestore (recommended)
mongorestore --uri "mongodb+srv://username:password@cluster.mongodb.net" ./backup

# Or using mongoimport for JSON
mongoimport --uri "mongodb+srv://username:password@cluster.mongodb.net/jd_resume_db" \
  --collection resumes --file resumes.json
```

### 8.3 Using MongoDB Compass (GUI)

1. Download [MongoDB Compass](https://www.mongodb.com/products/compass)
2. Connect to local MongoDB: `mongodb://localhost:27017`
3. Export collections to JSON
4. Connect to Atlas using your connection string
5. Import the JSON files

---

## Troubleshooting

### Error: "connection timed out"

**Cause:** Your IP is not whitelisted.

**Solution:**
1. Go to Atlas → Network Access
2. Add your current IP or `0.0.0.0/0` for testing
3. Wait 1-2 minutes for changes to propagate

### Error: "Authentication failed"

**Cause:** Wrong username or password.

**Solution:**
1. Verify username/password in Database Access
2. URL-encode special characters in password
3. Try creating a new database user

### Error: "not authorized on jd_resume_db"

**Cause:** User doesn't have permissions for the database.

**Solution:**
1. Go to Database Access
2. Edit the user
3. Add `readWrite` role for `jd_resume_db`

### Error: "Invalid connection string"

**Cause:** Malformed URI.

**Solution:**
1. Check for typos in the connection string
2. Ensure password is URL-encoded
3. Verify the format: `mongodb+srv://user:pass@cluster/database?options`

### Error: "DNS resolution failed"

**Cause:** Network or DNS issues.

**Solution:**
1. Check internet connectivity
2. Try: `ping jd-resume-cluster.xxxxx.mongodb.net`
3. Some networks block MongoDB ports; try a different network

### Slow Connection

**Cause:** Cluster region is far from your location/deployment.

**Solution:**
1. Create a new cluster in a closer region
2. Consider upgrading from M0 if performance is critical

---

## MongoDB Atlas Best Practices

### Security
1. **Never use `0.0.0.0/0` in production** - whitelist specific IPs
2. **Use strong, unique passwords** for database users
3. **Enable "Require TLS/SSL"** for connections
4. **Set up audit logging** for compliance
5. **Regular password rotation** for database users

### Performance
1. **Create indexes** for frequently queried fields
2. **Use connection pooling** (Spring Boot does this automatically)
3. **Monitor with Atlas Performance Advisor**
4. **Consider dedicated clusters** for production

### Backup & Recovery
1. **Enable continuous backups** (M10+ tiers)
2. **Test restore procedures** regularly
3. **Use point-in-time recovery** for critical data

---

## Collections in This Application

The JD-Resume Matching Engine uses these collections:

| Collection | Purpose |
|------------|---------|
| `resumes` | Stores resume data, skills, S3 URLs |
| `job_descriptions` | Stores JD text, required skills |
| `match_results` | Stores resume-JD match scores |

---

## Monitoring Your Cluster

### Atlas Dashboard Features:
- **Real-time Metrics:** Connections, operations, storage
- **Performance Advisor:** Index suggestions
- **Alerts:** Configure email/SMS notifications
- **Query Profiler:** Identify slow queries

### Key Metrics to Watch:
- Disk Usage (M0 has 512 MB limit)
- Connections (M0 has 500 connection limit)
- Operations per second
- Query targeting ratio

---

## Cost Considerations

### M0 Free Tier Limits:
- 512 MB storage
- Shared RAM/CPU
- 500 connections max
- No backups
- Limited to 1 M0 cluster per project

### When to Upgrade:
- Storage exceeds 400 MB
- Need automatic backups
- Require dedicated resources
- Production workloads

---

## Useful Links

- [MongoDB Atlas Documentation](https://docs.atlas.mongodb.com/)
- [Connection String URI Format](https://docs.mongodb.com/manual/reference/connection-string/)
- [MongoDB Compass (GUI)](https://www.mongodb.com/products/compass)
- [Spring Data MongoDB](https://spring.io/projects/spring-data-mongodb)
- [Atlas Free Tier FAQ](https://www.mongodb.com/docs/atlas/tutorial/deploy-free-tier-cluster/)
