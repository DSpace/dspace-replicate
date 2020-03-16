package org.dspace.pack.bagit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.common.collect.ImmutableList;
import org.dspace.content.Collection;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CollectionService;
import org.junit.Test;

/**
 * Tests for {@link CollectionPacker}
 */
public class CollectionPackerTest extends BagItPackerTest {

    // same as from CollectionPacker -- might be easier to make that public rather than duplicate
    private final ImmutableList<String> fields = ImmutableList.of("name", "short_description", "introductory_text",
                                                                  "provenance_description", "license", "copyright_text",
                                                                  "side_bar_text");

    @Test
    public void testPack() throws Exception {
        // get the output location
        final URL resources = CollectionPackerTest.class.getClassLoader().getResource("");
        assertNotNull(resources);
        final Path output = Paths.get(resources.toURI().resolve("collection-packer-test"));

        // init test Collection
        final Collection collection = initDSO(Collection.class);
        assertNotNull(collection);
        assertNotNull(collection.getID());
        assertNotNull(collection.getCommunities());
        assertTrue(collection.getCommunities().isEmpty());

        // grab our mocks
        final CollectionService collectionService = ContentServiceFactory.getInstance().getCollectionService();
        for (String field : fields) {
            when(collectionService.getMetadata(eq(collection), eq(field))).thenReturn(field);
        }

        final CollectionPacker collectionPacker = new CollectionPacker(collection, archFmt);
        final File packedOutput = collectionPacker.pack(output.toFile());

        for (String field : fields) {
            verify(collectionService, times(1)).getMetadata(eq(collection), eq(field));
        }

        assertThat(packedOutput).exists();
        assertThat(packedOutput).isFile();

        packedOutput.delete();
    }

}