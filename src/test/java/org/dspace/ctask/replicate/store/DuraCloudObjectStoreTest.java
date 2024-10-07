/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.replicate.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;

import org.duracloud.client.ContentStore;
import org.duracloud.common.retry.Retrier;
import org.duracloud.error.ContentStoreException;
import org.duracloud.error.NotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

/**
 * Tests for {@link DuraCloudObjectStore}. This creates a mock for the {@link ContentStore} so that we do not need to
 * create outgoing requests to DuraCloud
 */
public class DuraCloudObjectStoreTest {
    private final String group = "dc-object-store-test";
    private final String mimeType = "application/zip";

    private final ContentStore contentStore = mock(ContentStore.class);
    private DuraCloudObjectStore objectStore;

    @Before
    public void setup() {
        objectStore = new DuraCloudObjectStore();
        objectStore.setContentStore(contentStore);
    }

    @Test
    public void testUploadRetry() throws Exception {
        final URL root = DuraCloudObjectStoreTest.class.getClassLoader().getResource("unpack");

        // copy the file because the ObjectStore will delete it after transfer
        final Path catalog = Paths.get(root.toURI()).resolve("catalog.zip");
        final Path zipFile = Paths.get(root.toURI()).resolve("dc-upload.zip");
        Files.copy(catalog, zipFile, StandardCopyOption.REPLACE_EXISTING);

        assertThat(zipFile).exists();

        // trigger uploadReplica
        when(contentStore.getContentProperties(anyString(), anyString())).thenThrow(new NotFoundException("not found"));
        // fail once then pass
        when(contentStore.addContent(anyString(), anyString(), ArgumentMatchers.<InputStream>any(), anyLong(),
                                     eq(mimeType), anyString(), ArgumentMatchers.<String, String>anyMap()))
            .thenThrow(new ContentStoreException("first try fails"))
            .thenReturn("second try succeeds");

        objectStore.transferObject(group, zipFile.toFile());
        assertThat(zipFile).doesNotExist();
    }

    @Test
    public void testUploadFailure() throws Exception {
        final URL root = DuraCloudObjectStoreTest.class.getClassLoader().getResource("unpack");
        // copy the file because the ObjectStore will delete it after transfer
        final Path catalog = Paths.get(root.toURI()).resolve("catalog.zip");
        final Path zipFile = Paths.get(root.toURI()).resolve("dc-upload.zip");
        Files.copy(catalog, zipFile, StandardCopyOption.REPLACE_EXISTING);

        assertThat(zipFile).exists();

        // trigger uploadReplica
        when(contentStore.getContentProperties(anyString(), anyString())).thenReturn(new HashMap<String, String>());
        // fail
        when(contentStore.addContent(anyString(), anyString(), ArgumentMatchers.<InputStream>any(), anyLong(),
                                     eq(mimeType), anyString(), ArgumentMatchers.<String, String>anyMap()))
            .thenThrow(new ContentStoreException("exception"));

        try {
            objectStore.transferObject(group, zipFile.toFile());
            fail("Expected transfer to fail");
        } catch (IOException ignored) {
            // ignore exception
        }

        // verify that the retry process was attempted
        final int numTries = 1 + Retrier.DEFAULT_MAX_RETRIES;
        verify(contentStore, times(numTries)).addContent(anyString(), anyString(), ArgumentMatchers.<InputStream>any(),
                                                         anyLong(), eq(mimeType), anyString(),
                                                         ArgumentMatchers.<String, String>anyMap());

        // we failed to upload, so the file should still exist
        assertThat(zipFile).exists();
        // and finish cleaning up
        Files.delete(zipFile);
    }
}