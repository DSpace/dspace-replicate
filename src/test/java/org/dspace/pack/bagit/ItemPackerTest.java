package org.dspace.pack.bagit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.common.collect.ImmutableList;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchema;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.junit.Test;

public class ItemPackerTest extends BagItPackerTest {

    @Test
    public void testPack() throws Exception {
        // setup output
        final URL resources = CollectionPackerTest.class.getClassLoader().getResource("");
        assertNotNull(resources);
        final Path output = Paths.get(resources.toURI().resolve("item-packer-test"));

        // Item entity, todo linked collections, bundles, bitstreams
        final Item item = initJpa(Item.class);

        // Mocks
        final MetadataValue metadataValue = mock(MetadataValue.class);
        final MetadataField metadataField = mock(MetadataField.class);
        final MetadataSchema metadataSchema = mock(MetadataSchema.class);
        final ItemService itemService = ContentServiceFactory.getInstance().getItemService();

        when(itemService.getMetadata(eq(item), eq(Item.ANY), eq(Item.ANY), eq(Item.ANY), eq(Item.ANY)))
            .thenReturn(ImmutableList.of(metadataValue));
        when(metadataValue.getMetadataField()).thenReturn(metadataField);
        when(metadataField.getMetadataSchema()).thenReturn(metadataSchema);
        when(metadataSchema.getName()).thenReturn("metadataSchema");
        when(metadataField.getElement()).thenReturn("metadataFieldElement");
        when(metadataField.getQualifier()).thenReturn("metadataFieldQualifier");
        when(metadataValue.getValue()).thenReturn("metadataValue");

        final ItemPacker packer = new ItemPacker(item, archFmt);
        final File packedOutput = packer.pack(output.toFile());

        verify(itemService, times(1)).getMetadata(eq(item), eq(Item.ANY), eq(Item.ANY), eq(Item.ANY), eq(Item.ANY));

        assertThat(packedOutput).exists();
        assertThat(packedOutput).isFile();

        packedOutput.delete();
    }

}