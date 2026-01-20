# JD-Resume Matching Engine - Spring Boot Backend

## Prerequisites

- **Java 17+** 
- **Maven 3.6+** 

### Install Maven on macOS

```bash
brew install maven
```

Or download manually from https://maven.apache.org/download.cgi

## Project Structure

```
springboot-backend/
├── pom.xml                           # Maven dependencies
├── src/main/java/com/jdres/
│   ├── MatchingApplication.java      # Entry point
│   ├── controller/
│   │   └── ApiController.java        # REST endpoints
│   ├── service/
│   │   ├── TextExtractorService.java # PDF/DOCX/TXT parsing
│   │   ├── SkillExtractorService.java# OpenAI integration
│   │   ├── MatchCalculatorService.java# Fuzzy matching
│   │   └── FaissClientService.java   # Python microservice client
│   └── config/
│       └── WebConfig.java            # CORS & serves ../frontend/
└── src/main/resources/
    └── application.properties        # Configuration
```

> **Note:** Frontend files are served from `../frontend/` (external folder), not duplicated.

## Configuration

Set your OpenAI API key as an environment variable before running:

```bash
export OPENAI_API_KEY=your_api_key_here
```

Copy `src/main/resources/application.properties.example` to `src/main/resources/application.properties` and add your API key:
 
 ```bash
 cp src/main/resources/application.properties.example src/main/resources/application.properties
 ```
 
 Then modify `src/main/resources/application.properties`:

```properties
openai.api-key=your_api_key_here
```

## Running the Application

```bash
cd springboot-backend
mvn spring-boot:run
```

The application will start on **http://localhost:3000**

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/extract-text` | Extract text from uploaded file |
| POST | `/api/extract-multiple-texts` | Extract text from multiple files |
| POST | `/api/extract-skills` | Extract skills using OpenAI |
| POST | `/api/match-skills` | Calculate skill match percentage |
| POST | `/api/rank-resumes` | Rank resumes (requires Python service) |
| GET | `/api/health` | Health check |

## Notes

- The frontend is served from the same Spring Boot application
- All API endpoints match the original Express.js implementation
- The matching logic is identical to the JavaScript version
