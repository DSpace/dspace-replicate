/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.replicate.store;

import static org.duracloud.common.retry.Retrier.DEFAULT_MAX_RETRIES;
import static org.duracloud.common.retry.Retrier.DEFAULT_WAIT_BETWEEN_RETRIES;
import static org.duracloud.common.retry.Retrier.DEFAULT_WAIT_MULTIPLIER;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.google.common.annotations.VisibleForTesting;
import org.dspace.ctask.replicate.ObjectStore;
import org.dspace.curate.Utils;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.duracloud.client.ContentStore;
import org.duracloud.client.ContentStoreManager;
import org.duracloud.client.ContentStoreManagerImpl;
import org.duracloud.common.model.Credential;
import org.duracloud.common.retry.Retriable;
import org.duracloud.common.retry.Retrier;
import org.duracloud.domain.Content;
import org.duracloud.error.ContentStoreException;
import org.duracloud.error.NotFoundException;

/**
 * DuraCloudReplicaStore invokes the DuraCloud RESTful web service API,
 * (using a java client library) rather than using the rsync tool.
 *
 * @author richardrodgers
 */
public class DuraCloudObjectStore implements ObjectStore {

    // DuraCloud store
    private ContentStore dcStore = null;

    // properties for retrying uploads
    private int maxRetries = DEFAULT_MAX_RETRIES;
    private int defaultWait = DEFAULT_WAIT_BETWEEN_RETRIES;
    private int waitMultiplier = DEFAULT_WAIT_MULTIPLIER;

    public DuraCloudObjectStore() {
    }

    @VisibleForTesting
    protected void setContentStore(final ContentStore contentStore) {
        this.dcStore = contentStore;
    }

    @Override
    public void init() throws IOException {
        final ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

        // locate & login to Duracloud store
        ContentStoreManager storeManager =
            new ContentStoreManagerImpl(configurationService.getProperty("duracloud.host"),
                                        configurationService.getProperty("duracloud.port"),
                                        configurationService.getProperty("duracloud.context"));
        Credential credential =
            new Credential(configurationService.getProperty("duracloud.username"),
                           configurationService.getProperty("duracloud.password"));
        storeManager.login(credential);

        maxRetries = configurationService.getIntProperty("duracloud.retry.max", DEFAULT_MAX_RETRIES);
        defaultWait = configurationService.getIntProperty("duracloud.retry.wait", DEFAULT_WAIT_BETWEEN_RETRIES);
        waitMultiplier = configurationService.getIntProperty("duracloud.retry.multiplier", DEFAULT_WAIT_MULTIPLIER);

        // Attempt to get Content Store from a parameter in the configuration
        String storeId = configurationService.getProperty("duracloud.store-id", "0");
        try {
            dcStore = storeManager.getContentStore(storeId);
        } catch (ContentStoreException csE) {
            throw new IOException("Unable to connect to the DuraCloud Content Store. " +
                "Please check the DuraCloud connection/authentication settings " +
                "and the store-id setting in your 'duracloud.cfg' file.", csE);
        }
    }

    @Override
    public long fetchObject(final String group, final String id, final File file) throws IOException {
        long size = 0L;
        try {
            Content content = dcStore.getContent(getSpaceID(group), getContentPrefix(group) + id);

            // Attempt to get size from request header
            String contentSizeHeader = content.getProperties().get(ContentStore.CONTENT_SIZE);
            try {
                size = Long.parseLong(contentSizeHeader);
            } catch (NumberFormatException nfe) {
                // ignore - header was missing or not a valid Long. We will determine size below
            }

            // Open local file and download content into it
            try (FileOutputStream out = new FileOutputStream(file);
                 InputStream in = content.getStream()) {
                Utils.copy(in, out);
            }

            // If size could not be previously determined from request header, determine it from the downloaded file
            if (size == 0L) {
                size = file.length();
            }
        } catch (NotFoundException nfE) {
            // no object - no-op
        } catch (ContentStoreException csE) {
            throw new IOException(csE);
        }
        return size;
    }

    @Override
    public boolean objectExists(String group, String id) throws IOException {
        try {
            return dcStore.getContentProperties(getSpaceID(group), getContentPrefix(group) + id) != null;
        } catch (NotFoundException nfE) {
            return false;
        } catch (ContentStoreException csE) {
            throw new IOException(csE);
        }
    }

    @Override
    public long removeObject(String group, String id) throws IOException {
        // get metadata before blowing away
        long size = 0L;
        try {
            Map<String, String> attrs = dcStore.getContentProperties(getSpaceID(group), getContentPrefix(group) + id);
            size = Long.parseLong(attrs.get(ContentStore.CONTENT_SIZE));
            dcStore.deleteContent(getSpaceID(group), getContentPrefix(group) + id);
        } catch (NotFoundException nfE) {
            // no replica - no-op
        } catch (ContentStoreException csE) {
            throw new IOException(csE);
        }
        return size;
    }

    @Override
    public long transferObject(String group, File file) throws IOException {
        long size = 0L;
        String chkSum = Utils.checksum(file, "MD5");

        // make sure this is a different file from what replica store has
        // to avoid network I/O tax
        try {
            Map<String, String> attrs = dcStore.getContentProperties(getSpaceID(group),
                                                                     getContentPrefix(group) + file.getName());
            if (! chkSum.equals(attrs.get(ContentStore.CONTENT_CHECKSUM))) {
                size = uploadReplica(group, file, chkSum);
            }
        } catch (NotFoundException nfE) {
            // no extant replica - proceed
            size = uploadReplica(group, file, chkSum);
        } catch (ContentStoreException csE) {
            throw new IOException(csE);
        }

        // delete staging file
        file.delete();
        return size;
    }

    private long uploadReplica(final String group, final File file, final String chkSum) throws IOException {
        try (final FileInputStream fileInputStream = new FileInputStream(file)) {
            // get the mime type for DuraCloud
            final String mimeType;
            final String filename = file.getName();
            if (filename.endsWith(".zip")) {
                mimeType = "application/zip";
            } else if (filename.endsWith(".tgz") || filename.endsWith(".gzip")) {
                mimeType = "application/x-gzip";
            } else if (filename.endsWith(".txt")) {
                mimeType = "text/plain";
            } else if (filename.endsWith(".tar")) {
                mimeType = "application/tar";
            } else {
                mimeType = "application/octet-stream";
            }

            new Retrier(maxRetries, defaultWait, waitMultiplier).execute(new Retriable() {
                @Override
                public String retry() throws Exception {
                    return dcStore.addContent(getSpaceID(group), getContentPrefix(group) + filename,
                                       fileInputStream, file.length(),
                                       mimeType, chkSum,
                                       new HashMap<String, String>());
                }
            });

            return file.length();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public long moveObject(String srcGroup, String destGroup, String id) throws IOException {
        // get file-size metadata before moving the content
        long size = 0L;
        try {
            Map<String, String> attrs = dcStore.getContentProperties(getSpaceID(srcGroup),
                                                                     getContentPrefix(srcGroup) + id);
            size = Long.parseLong(attrs.get(ContentStore.CONTENT_SIZE));
            dcStore.moveContent(getSpaceID(srcGroup), getContentPrefix(srcGroup) + id,
                                getSpaceID(destGroup), getContentPrefix(destGroup) + id);
        } catch (NotFoundException nfE) {
            // no replica - no-op
        } catch (ContentStoreException csE) {
            throw new IOException(csE);
        }
        return size;
    }

    @Override
    public String objectAttribute(String group, String id, String attrName) throws IOException {
        try {
            Map<String, String> attrs = dcStore.getContentProperties(getSpaceID(group), getContentPrefix(group) + id);

            if ("checksum".equals(attrName)) {
                return attrs.get(ContentStore.CONTENT_CHECKSUM);
            } else if ("sizebytes".equals(attrName)) {
                return attrs.get(ContentStore.CONTENT_SIZE);
            } else if ("modified".equals(attrName)) {
                return attrs.get(ContentStore.CONTENT_MODIFIED);
            }
            return null;
        } catch (NotFoundException nfE) {
            return null;
        } catch (ContentStoreException csE) {
            throw new IOException(csE);
        }
    }

    /**
     * Returns the Space ID where content should be stored in DuraCloud,
     * based on the passed in Group.
     * <P>
     * If the group contains a forward slash ('/'), then the substring
     * before the first slash is assumed to be the Space ID.
     * Otherwise, the entire group name is assumed to be the Space ID.
     * @param group name
     * @return DuraCloud Space ID
     */
    private String getSpaceID(String group) {
        // If group contains a forward or backslash, then the
        // Space ID is whatever is before that slash
        if (group != null && group.contains("/")) {
            return group.substring(0, group.indexOf("/"));
        } else {
            // otherwise, the passed in group is the Space ID
            return group;
        }
    }

    /**
     * Returns the Content prefix that should be used when saving a file
     * to a DuraCloud space.
     * <P>
     * If the group contains a forward slash ('/'), then the substring
     * after the first slash is assumed to be the content naming prefix.
     * Otherwise, there is no content naming prefix.
     * @param group name
     * @return content prefix (ending with a forward slash)
     */
    private String getContentPrefix(String group) {
        // If group contains a forward or backslash, then the
        // content prefix is whatever is after that slash
        if (group != null && group.contains("/")) {
            return group.substring(group.indexOf("/") + 1) + "/";
        } else {
            // otherwise, no content prefix is specified
            return "";
        }
    }
}
