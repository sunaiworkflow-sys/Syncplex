# AWS S3 Setup Guide

This guide walks you through setting up Amazon S3 (Simple Storage Service) for the JD-Resume Matching Engine to store resume files.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Create an AWS Account](#step-1-create-an-aws-account)
3. [Create an S3 Bucket](#step-2-create-an-s3-bucket)
4. [Configure Bucket Settings](#step-3-configure-bucket-settings)
5. [Create IAM User & Access Keys](#step-4-create-iam-user--access-keys)
6. [Configure Environment Variables](#step-5-configure-environment-variables)
7. [Verify Connection](#step-6-verify-connection)
8. [Troubleshooting](#troubleshooting)

---

## Prerequisites

- An email address for AWS account registration
- Credit/Debit card for AWS account verification (Free Tier available)
- Basic understanding of cloud storage concepts

---

## Step 1: Create an AWS Account

1. Go to [AWS Console](https://aws.amazon.com/)
2. Click **"Create an AWS Account"**
3. Enter your email address and choose an account name
4. Verify your email address
5. Enter your credit card information (required for verification, Free Tier available)
6. Choose the **Basic Support - Free** plan
7. Complete the account setup

> **Note:** New accounts get **5GB of S3 storage free** for 12 months under the AWS Free Tier.

---

## Step 2: Create an S3 Bucket

1. Log in to the [AWS Console](https://console.aws.amazon.com/)
2. Search for **"S3"** in the search bar and click on it
3. Click **"Create bucket"**

### Bucket Configuration:

| Setting | Value |
|---------|-------|
| **Bucket name** | Choose a unique name (e.g., `jd-resume-storage-yourname`) |
| **AWS Region** | Select a region close to your users (e.g., `eu-north-1`, `us-east-1`) |

> **Important:** Bucket names must be globally unique and follow DNS naming rules:
> - 3-63 characters long
> - Only lowercase letters, numbers, and hyphens
> - Must start and end with a letter or number

---

## Step 3: Configure Bucket Settings

### 3.1 Object Ownership
- Select **"ACLs disabled (recommended)"**

### 3.2 Block Public Access Settings
For production, keep these **enabled** (blocks public access):
- ✅ Block all public access

> **Reason:** Resume files contain personal data and should be accessed only through your application using signed URLs or IAM credentials.

### 3.3 Bucket Versioning
- **Disable** versioning (unless you need to keep file history)

### 3.4 Default Encryption
- **Enable** Server-side encryption with Amazon S3 managed keys (SSE-S3)

### 3.5 Tags (Optional)
Add tags for cost tracking:
```
Key: Project
Value: JD-Resume-Matching
```

4. Click **"Create bucket"**

---

## Step 4: Create IAM User & Access Keys

Your application needs credentials to access S3 programmatically.

### 4.1 Create an IAM User

1. Go to [IAM Console](https://console.aws.amazon.com/iam/)
2. Click **"Users"** in the left sidebar
3. Click **"Create user"**

| Setting | Value |
|---------|-------|
| **User name** | `jd-resume-app-user` |
| **Access type** | ✅ Programmatic access (Access key) |

### 4.2 Set Permissions

1. Click **"Attach policies directly"**
2. Search for and select **`AmazonS3FullAccess`**

> **For Production (More Secure):** Create a custom policy with minimal permissions:

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "s3:PutObject",
                "s3:GetObject",
                "s3:DeleteObject",
                "s3:ListBucket"
            ],
            "Resource": [
                "arn:aws:s3:::your-bucket-name",
                "arn:aws:s3:::your-bucket-name/*"
            ]
        }
    ]
}
```

### 4.3 Create Access Keys

1. After creating the user, click on the user name
2. Go to **"Security credentials"** tab
3. Under **"Access keys"**, click **"Create access key"**
4. Select **"Application running outside AWS"**
5. Click **"Create access key"**
6. **IMPORTANT:** Download or copy both keys immediately:
   - **Access Key ID** (starts with `AKIA...`)
   - **Secret Access Key** (long string of characters)

> ⚠️ **Warning:** The Secret Access Key is shown only once. Store it securely!

---

## Step 5: Configure Environment Variables

Add the following to your `.env` file:

```bash
# AWS S3 Configuration
AWS_ACCESS_KEY_ID=AKIA44XUU65EPXXXXXX        # Your Access Key ID
AWS_SECRET_ACCESS_KEY=tSSRJ2P46UBz+4PEXXXX   # Your Secret Access Key
AWS_REGION=eu-north-1                         # Your bucket's region
AWS_BUCKET_NAME=your-bucket-name              # Your S3 bucket name
```

### Region Codes Reference:

| Region | Code |
|--------|------|
| US East (N. Virginia) | `us-east-1` |
| US West (Oregon) | `us-west-2` |
| EU (Ireland) | `eu-west-1` |
| EU (Stockholm) | `eu-north-1` |
| Asia Pacific (Mumbai) | `ap-south-1` |
| Asia Pacific (Singapore) | `ap-southeast-1` |

---

## Step 6: Verify Connection

Start your Spring Boot application and look for these log messages:

```
🔧 Initializing S3 Client...
   Bucket: your-bucket-name
   Region: eu-north-1
✅ S3 Client initialized successfully
```

### Test File Upload:
1. Upload a resume through the application
2. Check the logs for:
```
📤 Uploading to S3: uploads/uuid_filename.pdf (12345 bytes)
✅ File uploaded to S3: https://your-bucket.s3.eu-north-1.amazonaws.com/uploads/...
```

### Verify in AWS Console:
1. Go to your S3 bucket
2. Navigate to the `uploads/` folder
3. You should see the uploaded file

---

## Troubleshooting

### Error: "The specified bucket does not exist"
- Verify bucket name in `.env` matches exactly (case-sensitive)
- Check if the bucket was created in the correct region

### Error: "Access Denied"
- Verify your IAM user has the correct permissions
- Check if the Access Key ID and Secret Key are correct
- Ensure there are no extra spaces in the `.env` file

### Error: "Region is required"
- Make sure `AWS_REGION` is set in your `.env` file
- Use the correct region code (e.g., `eu-north-1`, not `Europe (Stockholm)`)

### Error: "S3 Client not initialized"
- Check for placeholder values in credentials
- Ensure credentials don't start with `placeholder` or `your_`

### File Upload Succeeds but Returns Error URL
```
https://s3.amazonaws.com/error/uploads/...
```
This means the S3 client failed. Check:
- Network connectivity
- AWS credentials validity
- Bucket permissions

---

## Security Best Practices

1. **Never commit credentials** to version control
2. **Use environment variables** or secret managers
3. **Enable bucket versioning** for production (data recovery)
4. **Set up lifecycle rules** to delete old files and save costs
5. **Enable access logging** for audit trails
6. **Use server-side encryption** (enabled by default)
7. **Regularly rotate** access keys

---

## Cost Estimation

| Service | Free Tier | Standard Pricing |
|---------|-----------|------------------|
| Storage | 5 GB/month free for 12 months | ~$0.023/GB/month |
| PUT requests | 2,000 free/month | $0.005/1,000 requests |
| GET requests | 20,000 free/month | $0.0004/1,000 requests |
| Data Transfer Out | 100 GB/month free | ~$0.09/GB after |

> For typical resume storage (< 5GB), you'll likely stay within the free tier.

---

## Application Code Reference

The S3 integration is implemented in:
- `src/main/java/com/jdres/service/S3Service.java`

### Key Methods:
- `uploadBytes(String key, byte[] content, String contentType)` - Upload files
- `uploadFile(String key, File file)` - Upload from file path
- `deleteFile(String key)` - Delete files from S3

---

## Need Help?

- [AWS S3 Documentation](https://docs.aws.amazon.com/s3/)
- [AWS Free Tier FAQ](https://aws.amazon.com/free/faqs/)
- [IAM Best Practices](https://docs.aws.amazon.com/IAM/latest/UserGuide/best-practices.html)
