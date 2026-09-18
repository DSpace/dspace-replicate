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
import java.util.concurrent.CompletionException;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.ctask.replicate.ObjectStore;
import org.dspace.curate.Utils;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.HttpStatusCode;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3CrtAsyncClientBuilder;
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

/**
 * Implementation of {@link ObjectStore} with Amazon S3.
 *
 * <p>This implementation relies exclusively on the AWS SDK for Java v2 CRT-based asynchronous S3
 * client ({@link S3AsyncClient#crtBuilder()}), the same client used by dspace-api's
 * {@code S3BitStoreService}. The CRT client transparently performs multipart uploads/downloads, so
 * there is no 5&nbsp;GB single-request limitation and no separate transfer manager is required.</p>
 *
 * <p>A synchronous {@code S3Client} is intentionally <b>not</b> used: dspace-api excludes every
 * {@code SdkHttpClient} implementation (apache-client, apache5-client, netty-nio-client) and only
 * ships the {@code aws-crt} native library. Building a synchronous client would therefore fail at
 * runtime with "Unable to load an HTTP implementation from any provider in the chain". For the same
 * reason IAM role assumption is not supported here: it would require the AWS SDK {@code sts}
 * module, which is not part of the DSpace distribution either.</p>
 *
 * @author Stefano Maffei (stefano.maffei at 4science.com)
 */
public class S3ObjectStore implements ObjectStore {

    private static final Logger log = LogManager.getLogger(S3ObjectStore.class);

    private final ConfigurationService configurationService = DSpaceServicesFactory.getInstance()
                                                                                  .getConfigurationService();

    private S3AsyncClient s3AsyncClient;
    private String bucketName;

    @Override
    public void init() throws IOException {
        log.debug("Initializing S3ObjectStore");

        if (StringUtils.isBlank(configurationService.getProperty("replicate.s3.bucket-name"))) {
            log.warn("S3 bucket name not configured - S3ObjectStore will not be initialized");
            return;
        }

        log.debug("Starting S3 service initialization");
        s3AsyncClient = initializeS3AsyncClient();
        log.debug("S3 CRT asynchronous client successfully initialized");

        bucketName = configurationService.getProperty("replicate.s3.bucket-name");
        log.debug("Using S3 bucket: {}", bucketName);

        if (!bucketExists(bucketName)) {
            log.warn("Bucket {} does not exist, creating it", bucketName);
            createBucket(bucketName);
            log.debug("Bucket {} created successfully", bucketName);
        } else {
            log.debug("Bucket {} already exists", bucketName);
        }

        log.debug("S3ObjectStore initialization completed successfully");
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
            s3AsyncClient.headObject(request).join();
            log.debug("Object existence check result: key={}, exists=true", key);
            return true;
        } catch (CompletionException ce) {
            if (isNotFound(ce)) {
                log.debug("Object existence check result: key={}, exists=false", key);
                return false;
            }
            Throwable cause = unwrap(ce);
            log.error("Error checking object existence: key={}", key, cause);
            throw new IOException("Failed to check S3 object existence for key: " + key, cause);
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
            HeadObjectResponse response = s3AsyncClient.headObject(request).join();
            String size = String.valueOf(response.contentLength());
            log.debug("Object size retrieved: key={}, size={}", key, size);
            return size;
        } catch (Exception e) {
            log.warn("Failed to get object size: key={}, error={}", key, unwrap(e).getMessage());
            return null;
        }
    }

    @Override
    public long fetchObject(String group, String id, File file) throws IOException {
        String key = getKey(id, group);
        log.debug("Starting fetch object: key={}, targetFile={}", key, file.getAbsolutePath());

        // getObject(..., Path) refuses to overwrite an existing destination file, so remove it first.
        if (file.exists() && !file.delete()) {
            throw new IOException("Unable to overwrite existing destination file: " + file.getAbsolutePath());
        }

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            // The CRT client automatically handles multipart download for large objects.
            s3AsyncClient.getObject(request, file.toPath()).join();
            long fileSize = file.length();
            log.debug("Object fetch completed successfully: key={}, size={} bytes", key, fileSize);
            return fileSize;
        } catch (Exception e) {
            Throwable cause = unwrap(e);
            log.error("Failed to fetch object: key={}, error={}", key, cause.getMessage(), cause);
            throw new IOException("Failed to fetch object: " + key, cause);
        }
    }

    @Override
    public long transferObject(String group, File file) throws IOException {
        String key = getKey(file.getName(), group);
        long fileSize = file.length();

        log.debug("Starting transfer object to S3: file={}, key={}, size={} bytes",
                file.getAbsolutePath(), key, fileSize);

        try {
            // The CRT client automatically performs a multipart upload for large files,
            // so there is no 5GB single-PutObject limitation.
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3AsyncClient.putObject(request, file.toPath()).join();
            log.debug("Object transfer completed successfully: key={}, size={} bytes", key, fileSize);
            return fileSize;
        } catch (Exception e) {
            Throwable cause = unwrap(e);
            log.error("Failed to transfer object: key={}, file={}, error={}",
                    key, file.getAbsolutePath(), cause.getMessage(), cause);
            throw new IOException("Failed to transfer object: " + key, cause);
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
            s3AsyncClient.deleteObject(request).join();
            log.debug("Object removed successfully: key={}, size={} bytes", key, size);
            return size;
        } catch (Exception e) {
            Throwable cause = unwrap(e);
            log.error("Failed to remove object: key={}, error={}", key, cause.getMessage(), cause);
            throw new IOException("Failed to remove object: " + key, cause);
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
            s3AsyncClient.copyObject(copyRequest).join();

            // After successful copy, delete the source object
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(srcKey)
                    .build();
            s3AsyncClient.deleteObject(deleteRequest).join();

            log.debug("Object moved successfully: srcKey={}, destKey={}, size={} bytes", srcKey, destKey, fileSize);
            return fileSize;
        } catch (Exception e) {
            Throwable cause = unwrap(e);
            log.error("Failed to move object: srcKey={}, destKey={}, error={}",
                    srcKey, destKey, cause.getMessage(), cause);
            throw new IOException("Failed to move object: " + srcKey + " to " + destKey, cause);
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
     * Builds the CRT-based asynchronous S3 client.
     *
     * <p>Credential resolution mirrors dspace-api's {@code S3BitStoreService}:</p>
     * <ol>
     *   <li>If static credentials are configured
     *       ({@code replicate.s3.access-key} / {@code replicate.s3.secret-key}) they are used.
     *       Kept only for backwards compatibility with legacy buckets.</li>
     *   <li>Otherwise no explicit credentials provider is set, so the CRT client resolves
     *       credentials through its native default chain (IAM role attached to the
     *       instance/task/pod, environment variables or a shared config profile).</li>
     * </ol>
     */
    private S3AsyncClient initializeS3AsyncClient() {
        S3CrtAsyncClientBuilder builder = S3AsyncClient.crtBuilder().region(resolveRegion());

        String accessKey = configurationService.getProperty("replicate.s3.access-key");
        String secretKey = configurationService.getProperty("replicate.s3.secret-key");

        if (isNotBlank(accessKey) && isNotBlank(secretKey)) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)));
            log.debug("Using static S3 credentials (legacy)");
        } else {
            log.debug("Using default AWS credential provider chain (IAM role / environment)");
        }

        return builder.build();
    }

    private Region resolveRegion() {
        Region region = Region.US_EAST_1;
        String regionName = configurationService.getProperty("replicate.s3.region-name");

        if (StringUtils.isNotBlank(regionName)) {
            try {
                region = Region.of(regionName);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid AWS region: {}, using default", regionName);
            }
        }
        return region;
    }

    private boolean bucketExists(String bucketName) throws IOException {
        try {
            HeadBucketRequest request = HeadBucketRequest.builder().bucket(bucketName).build();
            s3AsyncClient.headBucket(request).join();
            return true;
        } catch (CompletionException ce) {
            if (isNotFound(ce)) {
                return false;
            }
            Throwable cause = unwrap(ce);
            log.error("Error checking bucket existence: bucket={}", bucketName, cause);
            throw new IOException("Failed to check S3 bucket existence: " + bucketName, cause);
        }
    }

    private void createBucket(String bucketName) {
        try {
            CreateBucketRequest request = CreateBucketRequest.builder().bucket(bucketName).build();
            s3AsyncClient.createBucket(request).join();
        } catch (Exception e) {
            Throwable cause = unwrap(e);
            log.error("Failed to create bucket: {}", cause.getMessage(), cause);
            throw new RuntimeException("Failed to create S3 bucket: " + bucketName, cause);
        }
    }

    /**
     * Unwraps the actual cause from the {@link CompletionException} thrown by the asynchronous
     * client's {@code join()} calls, so error messages and rethrown causes are meaningful.
     */
    private Throwable unwrap(Throwable t) {
        if (t instanceof CompletionException && t.getCause() != null) {
            return t.getCause();
        }
        return t;
    }

    /**
     * Tells whether the given failure means "the requested object or bucket does not exist".
     *
     * <p>A HEAD request carries no response body, so the SDK often cannot map the error to a typed
     * exception and reports a generic {@link S3Exception} with a 404 status code instead of
     * {@link NoSuchKeyException} / {@link NoSuchBucketException}. Both forms are handled here.</p>
     */
    private boolean isNotFound(Throwable t) {
        Throwable cause = unwrap(t);
        if (cause instanceof NoSuchKeyException || cause instanceof NoSuchBucketException) {
            return true;
        }
        return cause instanceof S3Exception && ((S3Exception) cause).statusCode() == HttpStatusCode.NOT_FOUND;
    }
}
