# 🚀 SyncPlex JD-Resume Matching Engine

<div align="center">

![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)
![Java](https://img.shields.io/badge/Java-17+-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)
![MongoDB](https://img.shields.io/badge/MongoDB-Atlas-green.svg)
![License](https://img.shields.io/badge/license-MIT-blue.svg)

**An AI-powered Resume-to-Job Description matching platform that intelligently ranks candidates based on skills, experience, projects, and certifications.**

[Features](#-features) • [Tech Stack](#-tech-stack) • [Installation](#-installation) • [Configuration](#-configuration) • [API Reference](#-api-reference) • [Deployment](#-deployment)

</div>

---

## 📋 Table of Contents

- [Overview](#-overview)
- [Features](#-features)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Project Structure](#-project-structure)
- [Installation](#-installation)
- [Configuration](#-configuration)
- [Environment Variables](#-environment-variables)
- [Running Locally](#-running-locally)
- [API Reference](#-api-reference)
- [Data Models](#-data-models)
- [Matching Algorithm](#-matching-algorithm)
- [Services](#-services)
- [Frontend](#-frontend)
- [Deployment](#-deployment)
- [Contributing](#-contributing)

---

## 🎯 Overview

SyncPlex JD-Resume Matching Engine is a comprehensive platform designed for recruiters and job seekers. It uses AI-powered skill extraction and intelligent matching algorithms to rank candidates against job descriptions accurately.

### Key Capabilities

- **AI-Powered Skill Extraction**: Uses OpenAI GPT-4o-mini with Gemini fallback for extracting skills, experience, projects, and certifications from resumes and JDs
- **Intelligent Matching**: Multi-factor scoring algorithm considering skills, experience, projects, certifications, and employment gaps
- **Cloud Storage**: AWS S3 integration for secure resume storage
- **Multi-tenant Support**: Firebase authentication for user management
- **Google Drive Integration**: Import resumes directly from Google Drive
- **Real-time Rankings**: Dynamic candidate ranking and status management

---

## ✨ Features

### For Recruiters
| Feature | Description |
|---------|-------------|
| 📝 **Job Description Management** | Create, edit, and manage multiple job descriptions |
| 📊 **Candidate Ranking** | AI-powered ranking of candidates against JD requirements |
| 🔍 **Skill Gap Analysis** | See matched vs. missing skills for each candidate |
| ✅ **Status Management** | Accept, review, or reject candidates |
| 📁 **Bulk Import** | Import multiple resumes from Google Drive |
| 📄 **Resume Viewing** | View stored resumes directly in the browser |
| 🗑️ **Resume Deletion** | Delete resumes and associated data |

### For Job Seekers
| Feature | Description |
|---------|-------------|
| 📤 **Resume Upload** | Upload resume (PDF/DOCX) for analysis |
| 🎯 **JD Comparison** | Compare resume against any job description |
| 📈 **Match Score** | See detailed match breakdown |
| 💡 **Skill Gaps** | Identify skills to improve |

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                           CLIENT LAYER                               │
├─────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐ │
│  │  Landing    │  │   Login     │  │  Recruiter  │  │ Job Seeker  │ │
│  │   Page      │  │   Page      │  │  Dashboard  │  │   Page      │ │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        SPRING BOOT BACKEND                           │
├─────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                      CONTROLLERS                             │    │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────┐ │    │
│  │  │ ResumeCtrl   │ │ JDController │ │ DriveImportController│ │    │
│  │  └──────────────┘ └──────────────┘ └──────────────────────┘ │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                       SERVICES                               │    │
│  │  ┌────────────────┐ ┌─────────────────┐ ┌────────────────┐  │    │
│  │  │SkillExtractor  │ │ MatchingService │ │  S3Service     │  │    │
│  │  │   Service      │ │                 │ │                │  │    │
│  │  └────────────────┘ └─────────────────┘ └────────────────┘  │    │
│  │  ┌────────────────┐ ┌─────────────────┐ ┌────────────────┐  │    │
│  │  │TextExtractor   │ │EmbeddingService │ │GoogleDrive     │  │    │
│  │  │   Service      │ │                 │ │   Service      │  │    │
│  │  └────────────────┘ └─────────────────┘ └────────────────┘  │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                     REPOSITORIES                             │    │
│  │  ┌──────────────┐ ┌──────────────┐ ┌────────────────────┐   │    │
│  │  │ResumeRepo    │ │JDRepo        │ │MatchResultRepo     │   │    │
│  │  └──────────────┘ └──────────────┘ └────────────────────┘   │    │
│  └─────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
            ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
            │  MongoDB     │ │   AWS S3     │ │   OpenAI     │
            │   Atlas      │ │   Bucket     │ │    API       │
            └──────────────┘ └──────────────┘ └──────────────┘
```

---

## 🛠️ Tech Stack

### Backend
| Technology | Purpose |
|------------|---------|
| **Java 17+** | Core programming language |
| **Spring Boot 3.2.0** | Application framework |
| **Spring Data MongoDB** | Database access layer |
| **Spring WebFlux** | Reactive web client for API calls |
| **Apache PDFBox 3.0** | PDF text extraction |
| **Apache POI 5.2.5** | DOCX text extraction |
| **AWS SDK 2.21** | S3 file storage |
| **Google API Client** | Google Drive integration |
| **Jackson** | JSON processing |
| **Lombok** | Boilerplate code reduction |

### Frontend
| Technology | Purpose |
|------------|---------|
| **HTML5** | Page structure |
| **CSS3** | Styling with dark mode support |
| **JavaScript (ES6+)** | Client-side logic |
| **Firebase SDK** | Authentication |

### Cloud Services
| Service | Purpose |
|---------|---------|
| **MongoDB Atlas** | Cloud database |
| **AWS S3** | Resume file storage |
| **OpenAI API** | Primary AI for skill extraction |
| **Google Gemini API** | Fallback AI service |
| **Firebase** | User authentication |
| **Google Drive API** | Resume import |

---

## 📁 Project Structure

```
JD-resume-matching-engine/
├── 📄 .env                          # Environment variables (create from .env.example)
├── 📄 .env.example                  # Template for environment variables
├── 📄 .gitignore                    # Git ignore rules
├── 🐳 Dockerfile                    # Docker containerization
├── 📄 Procfile                      # Heroku deployment config
├── 📄 railway.toml                  # Railway.app deployment config
├── 📄 system.properties             # Java version specification
├── 📄 README.md                     # This file
├── 📄 package.json                  # Node.js dependencies (for MongoDB driver)
│
├── 📁 docs/                         # Documentation
│   ├── AWS_S3_SETUP.md              # AWS S3 configuration guide
│   ├── DEPLOYMENT.md                # Deployment instructions
│   └── MONGODB_ATLAS_SETUP.md       # MongoDB Atlas setup guide
│
├── 📁 frontend/                     # Legacy frontend (if any)
│
└── 📁 springboot-backend/           # Main Spring Boot application
    ├── 📄 pom.xml                   # Maven dependencies
    ├── 📄 run.sh                    # Local development startup script
    ├── 📄 error.log                 # Application error logs
    │
    └── 📁 src/main/
        ├── 📁 java/com/jdres/
        │   ├── 📄 MatchingApplication.java    # Main Spring Boot entry point
        │   │
        │   ├── 📁 config/
        │   │   └── 📄 WebConfig.java          # CORS & static resource config
        │   │
        │   ├── 📁 controller/
        │   │   ├── 📄 ApiController.java      # Health check & utility APIs
        │   │   ├── 📄 DriveImportController.java  # Google Drive import
        │   │   ├── 📄 JobDescriptionController.java # JD CRUD & matching
        │   │   └── 📄 ResumeController.java   # Resume upload & management
        │   │
        │   ├── 📁 model/
        │   │   ├── 📄 JobDescription.java     # JD entity
        │   │   ├── 📄 MatchResult.java        # Match result entity
        │   │   └── 📄 Resume.java             # Resume entity
        │   │
        │   ├── 📁 repository/
        │   │   ├── 📄 JobDescriptionRepository.java
        │   │   ├── 📄 MatchResultRepository.java
        │   │   └── 📄 ResumeRepository.java
        │   │
        │   └── 📁 service/
        │       ├── 📄 EmbeddingService.java       # OpenAI embeddings
        │       ├── 📄 FaissClientService.java     # Vector search (optional)
        │       ├── 📄 GoogleDriveService.java     # Google Drive integration
        │       ├── 📄 MatchCalculatorService.java # Score calculations
        │       ├── 📄 MatchingService.java        # Core matching logic
        │       ├── 📄 S3Service.java              # AWS S3 operations
        │       ├── 📄 SkillExtractorService.java  # AI skill extraction
        │       └── 📄 TextExtractorService.java   # PDF/DOCX text extraction
        │
        └── 📁 resources/
            ├── 📄 application.properties     # Spring Boot configuration
            └── 📁 static/                    # Frontend files
                ├── 📄 index.html             # Recruiter dashboard
                ├── 📄 landing.html           # Landing page
                ├── 📄 login.html             # Login page
                ├── 📄 signup.html            # Registration page
                ├── 📄 job-seeker.html        # Job seeker interface
                ├── 📄 app-v3.js              # Main JavaScript application
                ├── 📄 styles.css             # Custom styles
                └── 📄 firebase-config.js     # Firebase configuration
```

---

## 🚀 Installation

### Prerequisites

- **Java 17+** (JDK)
- **Maven 3.6+** (or use included Maven wrapper)
- **MongoDB** (local) or **MongoDB Atlas** (cloud)
- **Node.js 16+** (optional, for testing MongoDB connection)

### Clone Repository

```bash
git clone https://github.com/yourusername/JD-resume-matching-engine.git
cd JD-resume-matching-engine
```

### Install Dependencies

```bash
cd springboot-backend
./mvnw clean install -DskipTests
```

---

## ⚙️ Configuration

### 1. Create Environment File

```bash
cp .env.example .env
```

### 2. Configure Required Services

Edit `.env` with your actual values:

```env
# Server
SERVER_PORT=8080

# MongoDB Atlas
MONGO_URI=mongodb+srv://<username>:<password>@<cluster>.mongodb.net/jd_resume_db?retryWrites=true&w=majority

# OpenAI (Primary AI)
OPENAI_API_KEY=sk-your-openai-api-key
OPENAI_MODEL=gpt-4o-mini

# Gemini (Fallback AI)
GEMINI_API_KEY=your-gemini-api-key

# AWS S3
AWS_ACCESS_KEY_ID=your-access-key
AWS_SECRET_ACCESS_KEY=your-secret-key
AWS_REGION=us-east-2
AWS_BUCKET_NAME=your-bucket-name

# Firebase (Authentication)
FIREBASE_API_KEY=your-firebase-api-key
FIREBASE_AUTH_DOMAIN=your-project.firebaseapp.com
FIREBASE_PROJECT_ID=your-project-id

# Google Drive (Optional)
GOOGLE_DRIVE_CLIENT_ID=your-client-id
GOOGLE_DRIVE_CLIENT_SECRET=your-client-secret
```

---

## 🔐 Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `SERVER_PORT` | Yes | Server port (default: 8080) |
| `MONGO_URI` | Yes | MongoDB connection string |
| `OPENAI_API_KEY` | Yes | OpenAI API key for skill extraction |
| `OPENAI_MODEL` | No | OpenAI model (default: gpt-4o-mini) |
| `GEMINI_API_KEY` | No | Gemini API key (fallback) |
| `AWS_ACCESS_KEY_ID` | Yes | AWS access key |
| `AWS_SECRET_ACCESS_KEY` | Yes | AWS secret key |
| `AWS_REGION` | Yes | AWS region (e.g., us-east-2) |
| `AWS_BUCKET_NAME` | Yes | S3 bucket name |
| `FIREBASE_API_KEY` | Yes | Firebase API key |
| `FIREBASE_AUTH_DOMAIN` | Yes | Firebase auth domain |
| `FIREBASE_PROJECT_ID` | Yes | Firebase project ID |
| `GOOGLE_DRIVE_CLIENT_ID` | No | Google Drive OAuth client ID |
| `GOOGLE_DRIVE_CLIENT_SECRET` | No | Google Drive OAuth secret |

---

## 🏃 Running Locally

### Option 1: Using the Run Script (Recommended)

```bash
cd springboot-backend
chmod +x run.sh
./run.sh
```

This script will:
1. Load environment variables from `.env`
2. Check/start MongoDB
3. Start Spring Boot on port 8080
4. Open the browser automatically

### Option 2: Using Maven Directly

```bash
cd springboot-backend
source ../.env
mvn spring-boot:run
```

### Access the Application

| URL | Description |
|-----|-------------|
| `http://localhost:8080` | Landing page |
| `http://localhost:8080/index.html` | Recruiter dashboard |
| `http://localhost:8080/job-seeker.html` | Job seeker interface |
| `http://localhost:8080/login.html` | Login page |

---

## 📡 API Reference

### Health Check

```http
GET /api/health
```

**Response:**
```json
{
  "status": "healthy",
  "timestamp": "2026-01-21T10:00:00"
}
```

---

### Resume Endpoints

#### Upload Resume

```http
POST /api/resumes/upload
Content-Type: multipart/form-data
X-User-Id: <firebase-uid>

file: <resume.pdf>
```

**Response:**
```json
{
  "id": "resume123",
  "fileId": "unique-file-id",
  "name": "John_Doe_Resume.pdf",
  "s3Url": "https://s3.aws.com/bucket/resume.pdf",
  "skills": ["Java", "Spring Boot", "AWS"],
  "candidateName": "John Doe",
  "candidateExperience": 5
}
```

#### Delete Resume

```http
DELETE /api/resumes/{fileId}
X-User-Id: <firebase-uid>
```

---

### Job Description Endpoints

#### Create Job Description

```http
POST /api/jd
Content-Type: application/json
X-User-Id: <firebase-uid>

{
  "title": "Senior Java Developer",
  "text": "We are looking for a Java developer with 5+ years experience..."
}
```

**Response:**
```json
{
  "id": "jd123",
  "jdId": "unique-jd-id",
  "title": "Senior Java Developer",
  "requiredSkills": ["Java", "Spring Boot", "AWS"],
  "preferredSkills": ["Kubernetes", "React"],
  "minExperience": 5
}
```

#### Get All Job Descriptions

```http
GET /api/jd
X-User-Id: <firebase-uid>
```

#### Delete Job Description

```http
DELETE /api/jd/{jdId}
```

---

### Matching Endpoints

#### Get Matches for JD

```http
GET /api/jd/{jdId}/matches?limit=50
```

**Response:**
```json
{
  "jdId": "jd123",
  "matches": [
    {
      "resumeId": "resume123",
      "candidateName": "John Doe",
      "finalScore": 85.5,
      "skillMatchScore": 90,
      "experienceScore": 80,
      "matchedSkillsList": ["Java", "Spring Boot"],
      "missingSkillsList": ["Kubernetes"],
      "candidateStatus": "review"
    }
  ]
}
```

#### Update Candidate Status

```http
PUT /api/jd/{jdId}/matches/{resumeId}
Content-Type: application/json

{
  "status": "accepted"
}
```

---

### Google Drive Endpoints

#### Start OAuth Flow

```http
GET /api/drive/auth
```

#### Import Resumes from Folder

```http
POST /api/drive/import
Content-Type: application/json
X-User-Id: <firebase-uid>

{
  "folderId": "google-drive-folder-id",
  "accessToken": "oauth-access-token"
}
```

---

## 📊 Data Models

### Resume

```java
@Document(collection = "resumes")
public class Resume {
    String id;                    // MongoDB ObjectId
    String fileId;                // Unique file identifier
    String name;                  // Original filename
    String text;                  // Extracted text content
    String s3Key;                 // S3 storage key
    String s3Url;                 // S3 public URL
    List<String> skills;          // Extracted skills
    String candidateName;         // Parsed candidate name
    int candidateExperience;      // Years of experience
    Map<String, Object> parsedDetails;  // Full parsed data
    List<Double> embedding;       // Vector embedding (optional)
    String recruiterId;           // Owner's Firebase UID
    LocalDateTime importedAt;     // Import timestamp
}
```

### JobDescription

```java
@Document(collection = "job_descriptions")
public class JobDescription {
    String id;                    // MongoDB ObjectId
    String jdId;                  // Unique JD identifier
    String title;                 // Job title
    String text;                  // Raw JD text
    List<String> requiredSkills;  // Must-have skills
    List<String> preferredSkills; // Nice-to-have skills
    int minExperience;            // Minimum years required
    Map<String, Object> parsedDetails;  // Full parsed data
    List<Double> embedding;       // Vector embedding (optional)
    String recruiterId;           // Owner's Firebase UID
    LocalDateTime createdAt;      // Creation timestamp
}
```

### MatchResult

```java
@Document(collection = "match_results")
public class MatchResult {
    String id;                    // MongoDB ObjectId
    String jdId;                  // Reference to JD
    String resumeId;              // Reference to Resume
    
    // Score Components
    double skillMatchScore;       // Skill matching score (0-100)
    double experienceScore;       // Experience score (0-100)
    double projectsCertificationsScore;  // Projects & certs score
    double gapPenalty;            // Employment gap penalty
    double finalScore;            // Weighted final score
    
    // Display Metadata
    String candidateName;
    String candidateStatus;       // "accepted", "review", "rejected"
    List<String> matchedSkillsList;
    List<String> missingSkillsList;
    List<String> relevantProjects;
    
    LocalDateTime matchedAt;
}
```

---

## 🧮 Matching Algorithm

The matching engine uses a **multi-factor weighted scoring algorithm**:

```
Final Score = (Skill Score × 40%) + 
              (Experience Score × 25%) + 
              (Project Score × 20%) + 
              (Certification Score × 10%) + 
              (Domain Match × 5%) - 
              (Gap Penalty)
```

### Score Components

| Component | Weight | Calculation |
|-----------|--------|-------------|
| **Skill Match** | 40% | `(matched_required / total_required) × 100` |
| **Experience** | 25% | Based on candidate vs. required experience |
| **Projects** | 20% | Relevant projects using required skills |
| **Certifications** | 10% | Industry-relevant certifications |
| **Domain Match** | 5% | Same industry/domain experience |
| **Gap Penalty** | Deduction | `-2 points per month of employment gap` |

### Experience Scoring

| Candidate Experience | Score |
|---------------------|-------|
| Exceeds requirement by 2+ years | 100% |
| Meets requirement | 80% |
| Within 1 year of requirement | 60% |
| 2+ years below requirement | 30% |

### Skill Matching

Skills are matched using:
1. **Exact match**: `"Java" == "Java"`
2. **Case-insensitive**: `"java" == "JAVA"`
3. **Fuzzy matching**: `"JavaScript" ≈ "JS"`
4. **Alias recognition**: `"PostgreSQL" ≈ "Postgres"`

---

## 🔧 Services

### SkillExtractorService

Extracts structured data from resumes and JDs using AI.

**Primary**: OpenAI GPT-4o-mini  
**Fallback**: Google Gemini

**Extracted Fields:**
- Candidate name
- Total experience (years)
- Skills (required/preferred)
- Work history with dates
- Projects with tech stack
- Certifications
- Education
- Employment gaps

### MatchingService

Implements the core matching algorithm.

**Methods:**
- `matchNewJobDescription(jdId)` - Match JD against all resumes
- `matchNewResume(resumeId)` - Match resume against all JDs
- `computeSkillBasedMatch(jd, resume)` - Calculate match score

### S3Service

Handles AWS S3 operations.

**Methods:**
- `uploadFile(key, file)` - Upload file to S3
- `uploadBytes(key, bytes, contentType)` - Upload bytes
- `deleteFile(key)` - Delete file from S3

### GoogleDriveService

Imports resumes from Google Drive.

**Features:**
- OAuth 2.0 authentication
- Folder browsing
- Batch PDF/DOCX import
- Automatic text extraction

### TextExtractorService

Extracts text from documents.

**Supported Formats:**
- PDF (via Apache PDFBox)
- DOCX (via Apache POI)
- TXT (plain text)

---

## 🎨 Frontend

### Pages

| Page | File | Description |
|------|------|-------------|
| **Landing** | `landing.html` | Welcome page with role selection |
| **Login** | `login.html` | Email/password + Google/Apple login |
| **Signup** | `signup.html` | User registration |
| **Recruiter Dashboard** | `index.html` | Full JD and resume management |
| **Job Seeker** | `job-seeker.html` | Resume vs JD comparison |

### Features

- **Dark Mode**: Modern dark theme UI
- **Responsive Design**: Works on desktop and mobile
- **Real-time Updates**: Dynamic table updates
- **Drag & Drop**: File upload support
- **Progress Indicators**: Loading states for all actions

### JavaScript Application (app-v3.js)

Main functionality:
- Firebase authentication handling
- JD CRUD operations
- Resume upload and management
- Candidate ranking display
- Status management (accept/reject)
- Google Drive OAuth flow
- Skill extraction and display

---

## 🚀 Deployment

### Railway.app (Recommended)

1. Connect your GitHub repository to Railway
2. Set environment variables in Railway dashboard
3. Railway auto-deploys on push

Configuration is in `railway.toml`.

### Docker

```bash
# Build image
docker build -t jd-resume-engine .

# Run container
docker run -p 8080:8080 \
  -e MONGO_URI=your-mongodb-uri \
  -e OPENAI_API_KEY=your-openai-key \
  -e AWS_ACCESS_KEY_ID=your-aws-key \
  ... \
  jd-resume-engine
```

### Heroku

```bash
heroku create your-app-name
heroku config:set MONGO_URI=your-mongodb-uri
heroku config:set OPENAI_API_KEY=your-openai-key
# ... set other variables
git push heroku main
```

### Manual Deployment

```bash
# Build JAR
cd springboot-backend
./mvnw clean package -DskipTests

# Run JAR
java -jar target/jd-resume-matching-1.0.0.jar
```

---

## 📚 Additional Documentation

| Document | Description |
|----------|-------------|
| [MongoDB Atlas Setup](docs/MONGODB_ATLAS_SETUP.md) | Detailed MongoDB Atlas configuration |
| [AWS S3 Setup](docs/AWS_S3_SETUP.md) | S3 bucket setup and IAM permissions |
| [Deployment Guide](docs/DEPLOYMENT.md) | Full deployment instructions |

---

## 🔒 Security Considerations

1. **Never commit `.env`** - Use `.env.example` as template
2. **API Keys** - Rotate keys regularly
3. **S3 Bucket** - Configure proper bucket policies
4. **Firebase Rules** - Set appropriate security rules
5. **CORS** - Configure allowed origins in production
6. **HTTPS** - Always use HTTPS in production

---

## 🧪 Testing

```bash
# Run unit tests
cd springboot-backend
./mvnw test

# Run with specific profile
./mvnw test -Dspring.profiles.active=test
```

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- [OpenAI](https://openai.com/) for GPT-4o-mini API
- [Google](https://cloud.google.com/) for Gemini API and Drive API
- [MongoDB](https://www.mongodb.com/) for Atlas cloud database
- [AWS](https://aws.amazon.com/) for S3 storage
- [Spring Boot](https://spring.io/projects/spring-boot) for the amazing framework

---

<div align="center">

**Built with ❤️ by SyncPlex Team**

</div>