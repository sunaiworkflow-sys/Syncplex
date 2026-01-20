package com.jdres.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.jdres.model.Resume;
import com.jdres.repository.ResumeRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GoogleDriveService {

    private static final String APPLICATION_NAME = "JD Resume Matching Engine";
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE_READONLY);
    private static final String CREDENTIALS_FILE_PATH = "/client_secret.json";

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private S3Service s3Service; // Injected (Future use)

    @Autowired
    private com.jdres.service.SkillExtractorService skillExtractorService;

    @Autowired
    private com.jdres.service.MatchingService matchingService;

    private Drive driveService;

    public GoogleDriveService() {
        // Init happens on demand or in post-construct if needed
    }

    private Drive getDriveService() throws IOException, GeneralSecurityException {
        if (driveService != null)
            return driveService;

        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        InputStream in = GoogleDriveService.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new IOException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        // This will open browser on local machine
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

        driveService = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
        return driveService;
    }

    public List<Resume> importFromLink(String link, List<String> excludeIds) throws Exception {
        Drive service = getDriveService();
        String resourceId = extractIdFromLink(link);
        if (resourceId == null) {
            throw new IllegalArgumentException("Invalid Drive Link");
        }

        // Get File Metadata to check if it's a folder or file
        File metadata = service.files().get(resourceId).setFields("mimeType").execute();
        List<File> filesToProcess = new ArrayList<>();

        if ("application/vnd.google-apps.folder".equals(metadata.getMimeType())) {
            // It's a folder, list children
            String query = String.format("'%s' in parents and mimeType = 'application/pdf' and trashed = false",
                    resourceId);
            FileList result = service.files().list()
                    .setQ(query)
                    .setFields("files(id, name, webViewLink, webContentLink)")
                    .execute();
            filesToProcess.addAll(result.getFiles());
        } else if ("application/pdf".equals(metadata.getMimeType())) {
            // It's a single file
            File f = service.files().get(resourceId).setFields("id, name, webViewLink, webContentLink").execute();
            filesToProcess.add(f);
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + metadata.getMimeType());
        }

        List<Resume> savedResumes = new ArrayList<>();

        for (File f : filesToProcess) {
            if (excludeIds != null && excludeIds.contains(f.getId())) {
                continue; // Skip existing
            }
            if (resumeRepository.findByFileId(f.getId()).isPresent()) {
                continue; // Double check DB
            }

            // Download and Extract
            try {
                // 1. Download Content
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                service.files().get(f.getId()).executeMediaAndDownloadTo(outputStream);
                byte[] content = outputStream.toByteArray();

                // 2. Upload to S3
                // Uploading with fileId creates unique path in bucket
                String s3Key = "resumes/" + f.getId() + ".pdf";
                String s3Url = s3Service.uploadBytes(s3Key, content, "application/pdf");

                // 3. Extract Text (Using PDFBox from memory)
                String text = "";
                try (PDDocument document = Loader.loadPDF(content)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    text = stripper.getText(document);
                }

                // 4. Skip Embedding Generation (Using Skill-Based Matching!)
                List<Double> vector = Collections.emptyList();

                // 5. Extract Structured Details (One-time, Persistent)
                Map<String, Object> parsedDetails = skillExtractorService.extractResumeDetails(text);

                // Flatten skills
                List<String> skills;
                if (parsedDetails != null && !parsedDetails.isEmpty()) {
                    skills = skillExtractorService.flattenSkills(parsedDetails);
                    if (skills.isEmpty()) {
                        skills = skillExtractorService.extractSkills(text);
                    }
                } else {
                    skills = skillExtractorService.extractSkills(text);
                }

                Resume resume = new Resume();
                resume.setParsedDetails(parsedDetails);
                resume.setFileId(f.getId());
                resume.setName(f.getName());
                resume.setViewLink(f.getWebViewLink()); // Keep Drive link as view link for now
                resume.setDownloadLink(f.getWebContentLink());
                resume.setText(text);
                resume.setSource("google_drive");
                resume.setImportedAt(LocalDateTime.now());

                // Extract candidate info
                if (parsedDetails != null && parsedDetails.containsKey("candidate_profile")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> profile = (Map<String, Object>) parsedDetails.get("candidate_profile");
                    if (profile != null) {
                        resume.setCandidateName((String) profile.get("name"));
                    }
                }
                if (parsedDetails != null && parsedDetails.containsKey("total_experience_years")) {
                    Object exp = parsedDetails.get("total_experience_years");
                    if (exp instanceof Number) {
                        resume.setCandidateExperience(((Number) exp).intValue());
                    }
                }

                // Set New Fields.
                resume.setS3Key(s3Key);
                resume.setS3Url(s3Url);
                resume.setEmbedding(vector);
                resume.setSkills(skills); // Saved to DB

                Resume savedResume = resumeRepository.save(resume);
                savedResumes.add(savedResume);

                // Trigger matching against all JDs
                matchingService.matchNewResume(savedResume.getFileId());
            } catch (Exception e) {
                System.err.println("Failed to process file: " + f.getName() + " - " + e.getMessage());
                e.printStackTrace();
            }
        }

        return savedResumes;
    }

    private String extractIdFromLink(String url) {
        // Logic similar to Python script
        if (url == null)
            return null;

        // Match IDs (approx 33 chars, logic from Python regex)
        // Python regex: ([-a-zA-Z0-9_]{25,})
        Pattern pattern = Pattern.compile("([-a-zA-Z0-9_]{25,})");
        Matcher matcher = pattern.matcher(url);

        // Logic: usually the ID is the longest alphanumeric string in fragment or path
        String id = null;
        while (matcher.find()) {
            String group = matcher.group(1);
            if (!group.contains("drive") && !group.contains("google") && !group.contains("folders")
                    && !group.contains("file")) {
                id = group;
                // In many cases the last match or specific position matters, but simplistic
                // "find id" works for now
                // Ideally we use specific patterns like /folders/{id} or /d/{id}
            }
        }

        // Standard patterns
        if (url.contains("/folders/")) {
            int start = url.indexOf("/folders/") + 9;
            int end = url.indexOf("?", start);
            if (end == -1)
                end = url.length();
            return url.substring(start, end).split("/")[0];
        }
        if (url.contains("/d/")) {
            int start = url.indexOf("/d/") + 3;
            int end = url.indexOf("/", start);
            if (end == -1) {
                end = url.indexOf("?", start);
                if (end == -1)
                    end = url.length();
            }
            return url.substring(start, end);
        }
        if (url.contains("id=")) {
            int start = url.indexOf("id=") + 3;
            int end = url.indexOf("&", start);
            if (end == -1)
                end = url.length();
            return url.substring(start, end);
        }

        return id;
    }
}
