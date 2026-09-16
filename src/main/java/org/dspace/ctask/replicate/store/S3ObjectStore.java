/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.replicate.store;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.ctask.replicate.ObjectStore;
import org.dspace.curate.Utils;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.HttpStatusCode;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;

/**
 * Implementation of {@link ObjectStore} with Amazon S3.
 *
 * @author Stefano Maffei (stefano.maffei at 4science.com)
 */
public class S3ObjectStore implements ObjectStore {

    private static final Logger log = LogManager.getLogger(S3ObjectStore.class);

    private final ConfigurationService configurationService = DSpaceServicesFactory.getInstance()
                                                                                   .getConfigurationService();

    private S3Client s3Client;
    private S3TransferManager transferManager;
    private String bucketName;

    @Override
    public void init() throws IOException {
        log.debug("Initializing S3ObjectStore");

        if (StringUtils.isBlank(configurationService.getProperty("replicate.s3.bucket-name"))) {
            log.warn("S3 bucket name not configured - S3ObjectStore will not be initialized");
            return;
        }

        log.debug("Starting S3 service initialization");
        s3Client = initializeS3Client();
        log.debug("S3 synchronous client successfully initialized");

        // Initialize TransferManager for optimal upload/download performance
        // It's now required as primary method for uploads without size limitations
        try {
            S3AsyncClient s3AsyncClient = initializeS3AsyncClient();
            log.debug("S3 asynchronous client successfully initialized");
            transferManager = S3TransferManager.builder().s3Client(s3AsyncClient).build();
            log.debug("S3TransferManager created successfully - uploads will support any file size");
        } catch (Exception e) {
            log.warn("TransferManager initialization failed - uploads will be limited to 5GB: {}",
                    e.getMessage());
            log.debug("S3TransferManager initialization error details", e);
            throw new RuntimeException("Failed to initialize S3TransferManager,", e);
        }

        bucketName = configurationService.getProperty("replicate.s3.bucket-name");
        log.debug("Using S3 bucket: {}", bucketName);

        if (!bucketExists(bucketName)) {
            log.warn("Bucket {} does not exist, creating it", bucketName);
            createBucket(bucketName);
            log.debug("Bucket {} created successfully", bucketName);
        } else {
            log.debug("Bucket {} already exists", bucketName);
        }

        log.debug("S3ObjectStore initialization completed successfully (TransferManager available: {})",
                transferManager != null);
    }

    @Override
    public boolean objectExists(String group, String id) throws IOException {
        String key = getKey(id, group);
        log.debug("Checking if object exists: key={}", key);

        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.headObject(request);
            log.debug("Object existence check result: key={}, exists=true", key);
            return true;
        } catch (NoSuchKeyException exception) {
            log.debug("Object existence check result: key={}, exists=false", key);
            return false;
        } catch (S3Exception ex) {
            if (ex.statusCode() == HttpStatusCode.NOT_FOUND) {
                log.debug("Object existence check result: key={}, exists=false", key);
                return false;
            }
            log.error("Error checking object existence: key={}, status={}", key, ex.statusCode(), ex);
            throw new IOException("Failed to check S3 object existence for key: " + key, ex);
        }
    }

    @Override
    public String objectAttribute(String group, String id, String attrName) throws IOException {
        String key = getKey(id, group);
        log.debug("Getting object attribute: key={}, attrName={}", key, attrName);

        if (StringUtils.isBlank(attrName) || !objectExists(group, id)) {
            log.debug("Object attribute request failed: key={}, attrName={}, reason={}",
                    key, attrName, StringUtils.isBlank(attrName) ? "blank attribute name" : "object does not exist");
            return null;
        }

        if ("checksum".equals(attrName)) {
            log.debug("Calculating checksum for object: key={}", key);
            String checksum = calculateChecksum(group, id);
            log.debug("Checksum calculated: key={}, checksum={}", key, checksum);
            return checksum;
        }

        if (!"sizebytes".equals(attrName)) {
            log.debug("Unknown attribute requested: key={}, attrName={}", key, attrName);
            return null;
        }

        log.debug("Getting object size: key={}", key);
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            HeadObjectResponse response = s3Client.headObject(request);
            String size = String.valueOf(response.contentLength());
            log.debug("Object size retrieved: key={}, size={}", key, size);
            return size;
        } catch (Exception e) {
            log.warn("Failed to get object size: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    @Override
    public long fetchObject(String group, String id, File file) throws IOException {
        String key = getKey(id, group);
        log.debug("Starting fetch object: key={}, targetFile={}", key, file.getAbsolutePath());

        if (transferManager != null) {
            // Use TransferManager for optimal download performance
            DownloadFileRequest request = DownloadFileRequest.builder()
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .build())
                    .destination(file.toPath())
                    .build();

            try {
                log.debug("Waiting for download completion via TransferManager: key={}", key);
                transferManager.downloadFile(request).completionFuture().join();
                long fileSize = file.length();
                log.debug("Object fetch completed successfully via TransferManager: key={}," +
                        " size={} bytes", key, fileSize);
                return fileSize;
            } catch (Exception e) {
                log.error("Failed to fetch object via TransferManager: key={}, error={}", key, e.getMessage(), e);
                throw new IOException("Failed to fetch object: " + key, e);
            }
        } else {
            // Fallback to synchronous download
            log.warn("TransferManager not available, using synchronous download: key={}", key);
            try {
                GetObjectRequest request = GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build();
                s3Client.getObject(request, file.toPath());
                long fileSize = file.length();
                log.debug("Object fetch completed successfully via synchronous download: key={}," +
                        " size={} bytes", key, fileSize);
                return fileSize;
            } catch (Exception e) {
                log.error("Failed to fetch object via synchronous download: key={}, error={}",
                        key, e.getMessage(), e);
                throw new IOException("Failed to fetch object: " + key, e);
            }
        }
    }

    @Override
    public long transferObject(String group, File file) throws IOException {
        String key = getKey(file.getName(), group);
        long fileSize = file.length();

        log.debug("Starting transfer object to S3: file={}, key={}, size={} bytes",
                file.getAbsolutePath(), key, fileSize);

        try {
            // Use TransferManager as primary method - handles multipart upload automatically for large files
            if (transferManager != null) {
                log.debug("Using TransferManager for upload" +
                                " (automatically handles multipart for large files): key={}, size={} bytes",
                        key, fileSize);

                // Note: PutObjectRequest here is just metadata configuration (bucket, key, headers)
                // TransferManager automatically decides whether to use simple PUT or multipart upload
                // based on file size, removing the 5GB limitation of direct putObject() calls
                UploadFileRequest request = UploadFileRequest.builder()
                        .putObjectRequest(PutObjectRequest.builder()
                                .bucket(bucketName)
                                .key(key)
                                .build())
                        .source(file.toPath())
                        .build();

                transferManager.uploadFile(request).completionFuture().join();
                log.debug("Object transfer completed successfully via TransferManager: key={}, size={} bytes",
                        key, fileSize);
                return fileSize;
            } else {
                throw new IOException("TransferManager is not available - cannot transfer object larger than 5GB: "
                        + key);
            }
        } catch (Exception e) {
            log.error("Failed to transfer object: key={}, file={}, error={}",
                        key, file.getAbsolutePath(), e.getMessage(), e);
            throw new IOException("Failed to transfer object: " + key, e);
        } finally {
            if (file.exists() && !file.delete()) {
                log.warn("Failed to delete temporary file after transfer: {}", file.getAbsolutePath());
            }
        }
    }

    @Override
    public long removeObject(String group, String id) throws IOException {
        String key = getKey(id, group);
        log.debug("Starting remove object: key={}", key);

        long size = getFileSize(group, id);
        log.debug("Object size before removal: key={}, size={} bytes", key, size);

        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.deleteObject(request);
            log.debug("Object removed successfully: key={}, size={} bytes", key, size);
            return size;
        } catch (Exception e) {
            log.error("Failed to remove object: key={}, error={}", key, e.getMessage(), e);
            throw new IOException("Failed to remove object: " + key, e);
        }
    }

    @Override
    public long moveObject(String srcGroup, String destGroup, String id) throws IOException {
        String srcKey = getKey(id, srcGroup);
        String destKey = getKey(id, destGroup);
        log.debug("Starting move object: srcKey={}, destKey={}", srcKey, destKey);

        long fileSize = getFileSize(srcGroup, id);
        log.debug("Object size: key={}, size={} bytes", srcKey, fileSize);

        try {
            CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                    .sourceBucket(bucketName)
                    .sourceKey(srcKey)
                    .destinationBucket(bucketName)
                    .destinationKey(destKey)
                    .build();
            s3Client.copyObject(copyRequest);

            // After successful copy, delete the source object
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(srcKey)
                    .build();
            s3Client.deleteObject(deleteRequest);

            log.debug("Object moved successfully: srcKey={}, destKey={}, size={} bytes", srcKey, destKey, fileSize);
            return fileSize;
        } catch (Exception e) {
            log.error("Failed to move object: srcKey={}, destKey={}, error={}", srcKey, destKey, e.getMessage(), e);
            throw new IOException("Failed to move object: " + srcKey + " to " + destKey, e);
        }
    }

    private String calculateChecksum(String group, String id) throws IOException {
        String key = getKey(id, group);
        log.debug("Starting checksum calculation: key={}", key);

        File tempFile = File.createTempFile("s3-checksum-", "tmp");
        tempFile.deleteOnExit();

        try {
            fetchObject(group, id, tempFile);
            String checksum = Utils.checksum(tempFile, "MD5");
            log.debug("Checksum calculation completed: key={}, checksum={}", key, checksum);
            return checksum;
        } finally {
            if (tempFile.exists() && !tempFile.delete()) {
                log.warn("Failed to delete temporary checksum file: {}", tempFile.getAbsolutePath());
            }
        }
    }

    private long getFileSize(String group, String id) throws IOException {
        String size = objectAttribute(group, id, "sizebytes");
        try {
            return size != null ? Long.parseLong(size) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String getKey(String id, String group) {
        return isNotBlank(group) ? group + "/" + id : id;
    }

    /**
     * Supports:
     * 1) Static credentials (legacy)
     * 2) AssumeRole (cross-account)
     * 3) Default IAM role
     */
    private S3Client initializeS3Client() {
        Region region = Region.US_EAST_1;
        String regionName = configurationService.getProperty("replicate.s3.region-name");

        if (StringUtils.isNotBlank(regionName)) {
            try {
                region = Region.of(regionName);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid AWS region: {}, using default", regionName);
            }
        }

        // 1️⃣ Legacy static credentials (unchanged behavior)
        String accessKey = configurationService.getProperty("replicate.s3.access-key");
        String secretKey = configurationService.getProperty("replicate.s3.secret-key");

        if (isNotBlank(accessKey) && isNotBlank(secretKey)) {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
            return S3Client.builder()
                    .region(region)
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .build();
        }

        // 2️⃣ AssumeRole (cross-account)
        String roleArn = configurationService.getProperty("replicate.s3.assume-role-arn");

        if (isNotBlank(roleArn)) {
            String externalId = configurationService.getProperty("replicate.s3.assume-role-external-id");

            AssumeRoleRequest.Builder assumeRoleBuilder = AssumeRoleRequest.builder()
                    .roleArn(roleArn)
                    .roleSessionName("dspace-s3-replication-session");

            if (isNotBlank(externalId)) {
                assumeRoleBuilder.externalId(externalId);
                log.info("AssumeRole configured with external ID for enhanced security");
            } else {
                log.warn("AssumeRole configured without external ID - " +
                        "consider adding replicate.s3.assume-role-external-id for better security");
            }

            StsClient stsClient = StsClient.builder().region(region).build();

            AwsCredentialsProvider credentialsProvider = StsAssumeRoleCredentialsProvider.builder()
                    .stsClient(stsClient)
                    .refreshRequest(assumeRoleBuilder.build())
                    .asyncCredentialUpdateEnabled(true)
                    .build();

            return S3Client.builder()
                    .region(region)
                    .credentialsProvider(credentialsProvider)
                    .build();
        }

        // 3️⃣ Default IAM role (EC2 / ECS / EKS)
        return S3Client.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }

    private S3AsyncClient initializeS3AsyncClient() {
        Region region = Region.US_EAST_1;
        String regionName = configurationService.getProperty("replicate.s3.region-name");

        if (StringUtils.isNotBlank(regionName)) {
            try {
                region = Region.of(regionName);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid AWS region: {}, using default", regionName);
            }
        }

        // 1️⃣ Legacy static credentials (unchanged behavior)
        String accessKey = configurationService.getProperty("replicate.s3.access-key");
        String secretKey = configurationService.getProperty("replicate.s3.secret-key");

        if (isNotBlank(accessKey) && isNotBlank(secretKey)) {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
            return S3AsyncClient.crtBuilder()
                    .region(region)
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .build();
        }

        String roleArn = configurationService.getProperty("replicate.s3.assume-role-arn");

        if (isNotBlank(roleArn)) {
            String externalId = configurationService.getProperty("replicate.s3.assume-role-external-id");

            AssumeRoleRequest.Builder assumeRoleBuilder = AssumeRoleRequest.builder()
                    .roleArn(roleArn)
                    .roleSessionName("dspace-s3-replication-session");

            if (isNotBlank(externalId)) {
                assumeRoleBuilder.externalId(externalId);
                log.info("AssumeRole configured with external ID for enhanced security");
            } else {
                log.warn("AssumeRole configured without external ID - " +
                        "consider adding replicate.s3.assume-role-external-id for better security");
            }

            StsClient stsClient = StsClient.builder().region(region).build();

            AwsCredentialsProvider credentialsProvider = StsAssumeRoleCredentialsProvider.builder()
                    .stsClient(stsClient)
                    .refreshRequest(assumeRoleBuilder.build())
                    .asyncCredentialUpdateEnabled(true)
                    .build();

            return S3AsyncClient.crtBuilder()
                    .region(region)
                    .credentialsProvider(credentialsProvider)
                    .build();
        }

        return S3AsyncClient.crtBuilder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }

    private boolean bucketExists(String bucketName) throws IOException {
        try {
            HeadBucketRequest request = HeadBucketRequest.builder().bucket(bucketName).build();
            s3Client.headBucket(request);
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == HttpStatusCode.NOT_FOUND) {
                return false;
            }
            log.error("Error checking bucket existence: bucket={}, status={}", bucketName, e.statusCode(), e);
            throw new IOException("Failed to check S3 bucket existence: " + bucketName, e);
        }
    }

    private void createBucket(String bucketName) {
        try {
            CreateBucketRequest request = CreateBucketRequest.builder().bucket(bucketName).build();
            s3Client.createBucket(request);
        } catch (Exception e) {
            log.error("Failed to create bucket: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create S3 bucket: " + bucketName, e);
        }
    }

}

