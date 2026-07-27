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
 *
 */
public class S3ObjectStore implements ObjectStore {

    private static final Logger log = LogManager.getLogger(S3ObjectStore.class);

    /**
     * Specific logger for S3 operations - can be configured independently
     * Use logger name: org.dspace.ctask.replicate.store.S3ObjectStore.operations
     */
    private static final Logger s3Log = LogManager.getLogger(S3ObjectStore.class.getName() + ".operations");

    private final ConfigurationService configurationService =
            DSpaceServicesFactory.getInstance().getConfigurationService();

    private S3Client s3Client;
    private S3TransferManager transferManager;
    private String bucketName;

    @Override
    public void init() throws IOException {
        s3Log.info("Initializing S3ObjectStore");

        if (StringUtils.isBlank(configurationService.getProperty("replicate.s3.bucket-name"))) {
            s3Log.warn("S3 bucket name not configured - S3ObjectStore will not be initialized");
            return;
        }

        s3Log.info("Starting S3 service initialization");
        s3Client = initializeS3Client();
        s3Log.info("S3 synchronous client successfully initialized");

        // Initialize TransferManager for optimal upload/download performance
        // It's now required as primary method for uploads without size limitations
        try {
            S3AsyncClient s3AsyncClient = initializeS3AsyncClient();
            s3Log.info("S3 asynchronous client successfully initialized");

            s3Log.info("Creating S3TransferManager (required for unrestricted upload sizes)");
            transferManager = S3TransferManager.builder()
                    .s3Client(s3AsyncClient)
                    .build();
            s3Log.info("S3TransferManager created successfully - uploads will support any file size");
        } catch (Exception e) {
            s3Log.warn("TransferManager initialization failed - uploads will be limited to 5GB: {}",
                    e.getMessage());
            s3Log.debug("S3TransferManager initialization error details", e);
            throw new RuntimeException("Failed to initialize S3TransferManager,", e);
        }

        bucketName = configurationService.getProperty("replicate.s3.bucket-name");
        s3Log.info("Using S3 bucket: {}", bucketName);

        if (!bucketExists(bucketName)) {
            s3Log.warn("Bucket {} does not exist, creating it", bucketName);
            createBucket(bucketName);
            s3Log.info("Bucket {} created successfully", bucketName);
        } else {
            s3Log.info("Bucket {} already exists", bucketName);
        }

        s3Log.info("S3ObjectStore initialization completed successfully (TransferManager available: {})",
                transferManager != null);
    }

    @Override
    public boolean objectExists(String group, String id) {
        String key = getKey(id, group);
        s3Log.info("Checking if object exists: key={}", key);

        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.headObject(request);
            s3Log.info("Object existence check result: key={}, exists=true", key);
            return true;
        } catch (NoSuchKeyException exception) {
            s3Log.info("Object existence check result: key={}, exists=false", key);
            return false;
        } catch (S3Exception exception) {
            s3Log.warn("Error checking object existence: key={}, error={}," +
                    " considering file as existing", key, exception.getMessage(), exception);
            return true;
        }
    }

    @Override
    public String objectAttribute(String group, String id, String attrName) throws IOException {
        String key = getKey(id, group);
        s3Log.info("Getting object attribute: key={}, attrName={}", key, attrName);

        if (StringUtils.isBlank(attrName) || !objectExists(group, id)) {
            s3Log.info("Object attribute request failed: key={}, attrName={}, reason={}",
                    key, attrName, StringUtils.isBlank(attrName) ? "blank attribute name" : "object does not exist");
            return null;
        }

        if ("checksum".equals(attrName)) {
            s3Log.info("Calculating checksum for object: key={}", key);
            String checksum = calculateChecksum(group, id);
            s3Log.info("Checksum calculated: key={}, checksum={}", key, checksum);
            return checksum;
        }

        if (!"sizebytes".equals(attrName)) {
            s3Log.info("Unknown attribute requested: key={}, attrName={}", key, attrName);
            return null;
        }

        s3Log.info("Getting object size: key={}", key);
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            HeadObjectResponse response = s3Client.headObject(request);
            String size = String.valueOf(response.contentLength());
            s3Log.info("Object size retrieved: key={}, size={}", key, size);
            return size;
        } catch (Exception e) {
            s3Log.warn("Failed to get object size: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    @Override
    public long fetchObject(String group, String id, File file) throws IOException {
        String key = getKey(id, group);
        s3Log.info("Starting fetch object: key={}, targetFile={}", key, file.getAbsolutePath());

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
                s3Log.info("Waiting for download completion via TransferManager: key={}", key);
                transferManager.downloadFile(request).completionFuture().join();
                long fileSize = file.length();
                s3Log.info("Object fetch completed successfully via TransferManager: key={}," +
                        " size={} bytes", key, fileSize);
                return fileSize;
            } catch (Exception e) {
                s3Log.error("Failed to fetch object via TransferManager: key={}, error={}", key, e.getMessage(), e);
                throw new IOException("Failed to fetch object: " + key, e);
            }
        } else {
            // Fallback to synchronous download
            s3Log.warn("TransferManager not available, using synchronous download: key={}", key);
            try {
                GetObjectRequest request = GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build();
                s3Client.getObject(request, file.toPath());
                long fileSize = file.length();
                s3Log.info("Object fetch completed successfully via synchronous download: key={}," +
                        " size={} bytes", key, fileSize);
                return fileSize;
            } catch (Exception e) {
                s3Log.error("Failed to fetch object via synchronous download: key={}, error={}",
                        key, e.getMessage(), e);
                throw new IOException("Failed to fetch object: " + key, e);
            }
        }
    }

    @Override
    public long transferObject(String group, File file) throws IOException {
        String key = getKey(file.getName(), group);
        long fileSize = file.length();

        s3Log.info("Starting transfer object to S3: file={}, key={}, size={} bytes",
                file.getAbsolutePath(), key, fileSize);

        try {
            // Use TransferManager as primary method - handles multipart upload automatically for large files
            if (transferManager != null) {
                s3Log.info("Using TransferManager for upload" +
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
                s3Log.info("Object transfer completed successfully via TransferManager: key={}, size={} bytes",
                        key, fileSize);
                return fileSize;
            } else {
                throw new IOException("TransferManager is not available - cannot transfer object larger than 5GB: "
                        + key);
            }
        } catch (Exception e) {
            s3Log.error("Failed to transfer object: key={}, file={}, error={}",
                    key, file.getAbsolutePath(), e.getMessage(), e);
            throw new IOException("Failed to transfer object: " + key, e);
        } finally {
            if (file.exists() && !file.delete()) {
                s3Log.warn("Failed to delete temporary file after transfer: {}", file.getAbsolutePath());
            }
            System.gc(); // Suggest garbage collection to help release file handles
            // not guaranteed but can help in some environments
        }
    }

    /**
     * Primary method for synchronous upload to S3
     * Uses simple upload for files <= 5GB, multipart upload for larger files
     *
     * @param key the S3 object key
     * @param file the file to upload
     * @param fileSize the size of the file
     * @throws IOException if upload fails
     * @author Stefano Maffei (stefano.maffei at 4science.com)
     */
    private void performSyncUpload(String key, File file, long fileSize) throws IOException {
        // AWS S3 putObject limit is 5GB - use multipart upload for larger files
        final long fiveGB = 5L * 1024 * 1024 * 1024;

        if (fileSize <= fiveGB) {
            s3Log.info("Using simple upload for key: {}, size: {} bytes", key, fileSize);
            performSimpleUpload(key, file, fileSize);
        } else {
            s3Log.info("File size exceeds 5GB limit, using multipart upload for key: {}, size: {} bytes",
                    key, fileSize);
            performMultipartUpload(key, file, fileSize);
        }
    }

    /**
     * Performs simple S3 upload for files <= 5GB
     *
     * @param key the S3 object key
     * @param file the file to upload
     * @param fileSize the size of the file
     * @throws IOException if upload fails
     * @author Stefano Maffei (stefano.maffei at 4science.com)
     */
    private void performSimpleUpload(String key, File file, long fileSize) throws IOException {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.putObject(putRequest, file.toPath());
            s3Log.info("Simple upload completed successfully: key={}, size={} bytes", key, fileSize);
        } catch (Exception e) {
            s3Log.error("Simple upload failed: key={}, error={}", key, e.getMessage(), e);
            throw new IOException("Simple upload failed for key: " + key, e);
        }
    }

    /**
     * Performs multipart upload for files > 5GB using TransferManager
     *
     * @param key the S3 object key
     * @param file the file to upload
     * @param fileSize the size of the file
     * @throws IOException if upload fails
     * @author Stefano Maffei (stefano.maffei at 4science.com)
     */
    private void performMultipartUpload(String key, File file, long fileSize) throws IOException {
        if (transferManager == null) {
            s3Log.error("Large file upload requires TransferManager but it's not available: key={}, size={} bytes",
                    key, fileSize);
            throw new IOException("Cannot upload file larger than 5GB without TransferManager: " + key);
        }

        try {
            UploadFileRequest uploadRequest = UploadFileRequest.builder()
                    .putObjectRequest(PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .build())
                    .source(file.toPath())
                    .build();

            transferManager.uploadFile(uploadRequest).completionFuture().join();
            s3Log.info("Multipart upload completed successfully: key={}, size={} bytes", key, fileSize);
        } catch (Exception e) {
            s3Log.error("Multipart upload failed: key={}, error={}", key, e.getMessage(), e);
            throw new IOException("Multipart upload failed for key: " + key, e);
        }
    }

    @Override
    public long removeObject(String group, String id) throws IOException {
        String key = getKey(id, group);
        s3Log.info("Starting remove object: key={}", key);

        long size = getFileSize(group, id);
        s3Log.info("Object size before removal: key={}, size={} bytes", key, size);

        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.deleteObject(request);
            s3Log.info("Object removed successfully: key={}, size={} bytes", key, size);
            return size;
        } catch (Exception e) {
            s3Log.error("Failed to remove object: key={}, error={}", key, e.getMessage(), e);
            throw new IOException("Failed to remove object: " + key, e);
        }
    }

    @Override
    public long moveObject(String srcGroup, String destGroup, String id) throws IOException {
        String srcKey = getKey(id, srcGroup);
        String destKey = getKey(id, destGroup);
        s3Log.info("Starting move object: srcKey={}, destKey={}", srcKey, destKey);

        long fileSize = getFileSize(srcGroup, id);
        s3Log.info("Object size: key={}, size={} bytes", srcKey, fileSize);

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

            s3Log.info("Object moved successfully: srcKey={}, destKey={}, size={} bytes", srcKey, destKey, fileSize);
            return fileSize;
        } catch (Exception e) {
            s3Log.error("Failed to move object: srcKey={}, destKey={}, error={}", srcKey, destKey, e.getMessage(), e);
            throw new IOException("Failed to move object: " + srcKey + " to " + destKey, e);
        }
    }

    private String calculateChecksum(String group, String id) throws IOException {
        String key = getKey(id, group);
        s3Log.info("Starting checksum calculation: key={}", key);

        File tempFile = File.createTempFile("s3-checksum-", "tmp");
        tempFile.deleteOnExit();

        try {
            fetchObject(group, id, tempFile);
            String checksum = Utils.checksum(tempFile, "MD5");
            s3Log.info("Checksum calculation completed: key={}, checksum={}", key, checksum);
            return checksum;
        } finally {
            if (tempFile.exists() && !tempFile.delete()) {
                s3Log.warn("Failed to delete temporary checksum file: {}", tempFile.getAbsolutePath());
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

            StsClient stsClient = StsClient.builder()
                    .region(region)
                    .build();

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

            StsClient stsClient = StsClient.builder()
                    .region(region)
                    .build();

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

        // 3️⃣ Default IAM role (EC2 / ECS / EKS)
        return S3AsyncClient.crtBuilder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }

    private boolean bucketExists(String bucketName) {
        try {
            HeadBucketRequest request = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            s3Client.headBucket(request);
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        } catch (Exception e) {
            log.warn("Error checking bucket existence: {}", e.getMessage());
            return true; // Assume it exists to avoid creating it
        }
    }

    private void createBucket(String bucketName) {
        try {
            CreateBucketRequest request = CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            s3Client.createBucket(request);
        } catch (Exception e) {
            log.error("Failed to create bucket: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create S3 bucket: " + bucketName, e);
        }
    }

    /**
     * Clean up AWS resources when the ObjectStore is being destroyed
     *
     * @author Stefano Maffei (stefano.maffei at 4science.com)
     */
    public void destroy() {
        s3Log.info("Cleaning up S3ObjectStore resources");

        try {
            if (transferManager != null) {
                transferManager.close();
                s3Log.info("S3TransferManager closed successfully");
            }
        } catch (Exception e) {
            s3Log.warn("Error closing S3TransferManager: {}", e.getMessage());
        }

        try {
            if (s3Client != null) {
                s3Client.close();
                s3Log.info("S3Client closed successfully");
            }
        } catch (Exception e) {
            s3Log.warn("Error closing S3Client: {}", e.getMessage());
        }

        s3Log.info("S3ObjectStore resources cleanup completed");
    }
}

