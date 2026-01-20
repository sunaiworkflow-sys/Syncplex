# JD-Resume Matching Engine

AI-powered job description and resume matching system with skill-based ranking, Google Drive integration, and AWS S3 storage.

## ✨ Features

- **Smart Resume Matching**: AI-powered skill extraction and matching using OpenAI GPT-4o-mini
- **Google Drive Import**: Bulk import resumes from shared Google Drive folders
- **AWS S3 Storage**: Secure cloud storage for resume files
- **Skill-Based Ranking**: Ranks candidates by actual skill matches, not text similarity
- **MongoDB Atlas**: Scalable NoSQL database for resume and job description storage
- **Firebase Authentication**: Secure user login with Google Auth support
- **Modern UI**: Clean, responsive interface with dark mode support

## 🏗️ Architecture

```
├── springboot-backend/     # Spring Boot REST API
│   ├── src/main/java/
│   │   └── com/jdres/
│   │       ├── controller/     # REST endpoints
│   │       ├── service/        # Business logic
│   │       ├── model/          # MongoDB documents
│   │       └── repository/     # Data access layer
│   └── src/main/resources/
│       ├── static/             # Frontend assets
│       └── application.properties
├── frontend/               # Standalone frontend (backup)
├── docs/                   # Documentation
├── .env                    # Environment variables (not tracked)
└── .env.example            # Template for .env
```

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Maven 3.6+
- MongoDB Atlas account
- AWS S3 bucket
- OpenAI API key
- Google Cloud OAuth credentials (for Drive import)
- Firebase project (for authentication)

### 1. Clone Repository
```bash
git clone https://github.com/YOUR_USERNAME/JD-resume-matching-engine.git
cd JD-resume-matching-engine
```

### 2. Configure Environment
```bash
# Copy the example environment file
cp .env.example .env

# Edit .env with your actual credentials
# Required: MONGO_URI, OPENAI_API_KEY, AWS credentials
# Optional: Firebase, Google Drive, Gemini
```

### 3. Run the Application
```bash
cd springboot-backend
./mvnw spring-boot:run
```

### 4. Access Application
Open browser: `http://localhost:8080`

## ⚙️ Configuration

### Required Environment Variables

| Variable | Description |
|----------|-------------|
| `MONGO_URI` | MongoDB Atlas connection string |
| `OPENAI_API_KEY` | OpenAI API key for skill extraction |
| `AWS_ACCESS_KEY_ID` | AWS access key |
| `AWS_SECRET_ACCESS_KEY` | AWS secret key |
| `AWS_REGION` | AWS region (e.g., `eu-north-1`) |
| `AWS_BUCKET_NAME` | S3 bucket name for resume storage |

### Optional Environment Variables

| Variable | Description |
|----------|-------------|
| `GEMINI_API_KEY` | Fallback AI when OpenAI fails |
| `GOOGLE_DRIVE_CLIENT_ID` | OAuth client ID for Drive import |
| `GOOGLE_DRIVE_CLIENT_SECRET` | OAuth client secret |
| `FIREBASE_*` | Firebase configuration for auth |

## 📖 API Endpoints

### Resumes
- `POST /api/resumes/upload` - Upload resume
- `GET /api/resumes` - List all resumes
- `GET /api/resumes/{id}` - Get resume by ID
- `DELETE /api/resumes/{id}` - Delete resume

### Job Descriptions
- `POST /api/jd/upload` - Upload job description
- `GET /api/jd` - List all JDs
- `GET /api/jd/{id}/matches` - Get matched resumes

### Skills & Matching
- `POST /api/extract-skills` - Extract skills from text
- `GET /api/health` - Health check

### Google Drive
- `POST /api/drive/import` - Import resumes from Drive link

## 🔒 Security

- **Never commit `.env`** - It's in `.gitignore`
- **Firebase config** - Use templates, actual configs are ignored
- **OAuth tokens** - Stored locally, not in repo
- **API Keys** - Use environment variables for all secrets

## 🛠️ Development

### Build
```bash
cd springboot-backend
./mvnw clean package
```

### Run Tests
```bash
./mvnw test
```

### Production JAR
```bash
./mvnw package -DskipTests
java -jar target/jd-resume-matching-*.jar
```

## 📚 Documentation

See the `/docs` folder for detailed setup guides:
- [AWS S3 Setup](docs/AWS_S3_SETUP.md)

## 🤝 Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

## 📄 License

MIT License - see LICENSE file for details

## 💬 Support

For issues and questions, please open a GitHub issue.