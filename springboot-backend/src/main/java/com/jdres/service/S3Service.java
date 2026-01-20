package com.jdres.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;

@Service
public class S3Service {

    private final S3Client s3Client;
    private final String bucketName;

    public S3Service(
            @Value("${aws.accessKeyId}") String accessKey,
            @Value("${aws.secretKey}") String secretKey,
            @Value("${aws.region}") String region,
            @Value("${aws.s3.bucketName}") String bucketName) {

        this.bucketName = bucketName;

        // Initialize S3 client
        S3Client tempClient = null;

        // Check if placeholder values
        if (accessKey.startsWith("placeholder") || accessKey.startsWith("your_")) {
            System.err.println("⚠️  AWS Credentials not configured properly in .env");
        } else {
            try {
                System.out.println("🔧 Initializing S3 Client...");
                System.out.println("   Bucket: " + bucketName);
                System.out.println("   Region: " + region);

                tempClient = S3Client.builder()
                        .region(Region.of(region))
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)))
                        .build();

                System.out.println("✅ S3 Client initialized successfully");
            } catch (Exception e) {
                System.err.println("❌ Failed to initialize S3 Client: " + e.getMessage());
                e.printStackTrace();
            }
        }

        this.s3Client = tempClient;
    }

    public String uploadFile(String key, File file) {
        if (s3Client == null) {
            System.err.println("⚠️  S3 Client not initialized - returning placeholder URL");
            return "https://s3.amazonaws.com/placeholder/" + key;
        }

        try {
            PutObjectRequest putOb = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.putObject(putOb, RequestBody.fromFile(file));
            String url = s3Client.utilities().getUrl(builder -> builder.bucket(bucketName).key(key)).toExternalForm();
            System.out.println("✅ File uploaded to S3: " + url);
            return url;
        } catch (Exception e) {
            System.err.println("❌ S3 Upload failed: " + e.getMessage());
            e.printStackTrace();
            return "https://s3.amazonaws.com/error/" + key;
        }
    }

    public String uploadBytes(String key, byte[] content, String contentType) {
        if (s3Client == null) {
            System.err.println("⚠️  S3 Client not initialized - returning placeholder URL");
            return "https://s3.amazonaws.com/placeholder/" + key;
        }

        try {
            System.out.println("📤 Uploading to S3: " + key + " (" + content.length + " bytes)");

            PutObjectRequest putOb = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putOb, RequestBody.fromBytes(content));
            String url = s3Client.utilities().getUrl(builder -> builder.bucket(bucketName).key(key)).toExternalForm();
            System.out.println("✅ File uploaded to S3: " + url);
            return url;
        } catch (Exception e) {
            System.err.println("❌ S3 Upload failed: " + e.getMessage());
            System.err.println("   Bucket: " + bucketName);
            System.err.println("   Key: " + key);
            e.printStackTrace();
            return "https://s3.amazonaws.com/error/" + key;
        }
    }

    public boolean deleteFile(String key) {
        if (s3Client == null) {
            System.err.println("⚠️  S3 Client not initialized - cannot delete file");
            return false;
        }

        if (key == null || key.isEmpty()) {
            System.err.println("⚠️  Cannot delete S3 file: key is null or empty");
            return false;
        }

        try {
            System.out.println("🗑️ Deleting from S3: " + key);

            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteRequest);
            System.out.println("✅ File deleted from S3: " + key);
            return true;
        } catch (Exception e) {
            System.err.println("❌ S3 Delete failed: " + e.getMessage());
            System.err.println("   Bucket: " + bucketName);
            System.err.println("   Key: " + key);
            e.printStackTrace();
            return false;
        }
    }
}
