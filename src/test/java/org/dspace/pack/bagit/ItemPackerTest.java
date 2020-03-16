package org.dspace.pack.bagit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.dspace.content.MetadataSchema.DC_SCHEMA;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.isNull;
import static org.mockito.Matchers.matches;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.google.common.collect.ImmutableList;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchema;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.BundleService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.curate.Curator;
import org.junit.Before;
import org.junit.Test;

public class ItemPackerTest extends BagItPackerTest {

    private static final String PRIMARY_NAME = "primary";
    private static final String LICENSE_NAME = "license";
    private static final String BUNDLE_NAME = "bundle";

    // mocked values
    private MetadataValue metadataValue;
    private MetadataField metadataField;
    private MetadataSchema metadataSchema;
    private ItemService itemService;
    private BundleService bundleService;
    private BitstreamService bitstreamService;


    @Before
    public void setup() throws Exception {
        // Mocks
        metadataValue = mock(MetadataValue.class);
        metadataField = mock(MetadataField.class);
        metadataSchema = mock(MetadataSchema.class);
        itemService = ContentServiceFactory.getInstance().getItemService();
        bundleService = ContentServiceFactory.getInstance().getBundleService();
        bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
    }

    @Test
    public void testPack() throws Exception {
        // setup output
        final URL resources = CollectionPackerTest.class.getClassLoader().getResource("");
        assertNotNull(resources);
        final Path output = Paths.get(resources.toURI().resolve("item-packer-test"));

        final String mdSchemaName = "metadataSchema";
        final String mdFieldElement = "metadataFieldElement";
        final String mdFieldQualifier = "metadataFieldQualifier";
        final String mdValue = "metadataValue";

        // Grab the context so we can init the jpa entities
        final Context context = Curator.curationContext();

        // Item entity, todo linked collections, bundles, bitstreams
        final Bitstream primaryBitstream = initJpa(Bitstream.class);
        primaryBitstream.setName(context, PRIMARY_NAME);
        primaryBitstream.setSource(context, PRIMARY_NAME);
        primaryBitstream.setDescription(context, PRIMARY_NAME);
        primaryBitstream.setSequenceID(0);

        final Bitstream licenseBitstream = initJpa(Bitstream.class);
        licenseBitstream.setName(context, LICENSE_NAME);
        licenseBitstream.setSource(context, LICENSE_NAME);
        licenseBitstream.setDescription(context, LICENSE_NAME);
        licenseBitstream.setSequenceID(1);

        final Bundle bundle = initJpa(Bundle.class);
        // kind of hacky... more reflection to add the bitstreams to the bundle
        final Field bitstreamsField = Bundle.class.getDeclaredField("bitstreams");
        bitstreamsField.setAccessible(true);
        List<Bitstream> bitstreams = (List<Bitstream>) bitstreamsField.get(bundle);
        bitstreams.add(licenseBitstream);
        bitstreams.add(primaryBitstream);
        bundle.setPrimaryBitstreamID(primaryBitstream);

        final Item item = initJpa(Item.class);
        item.getBundles().add(bundle);

        when(itemService.getMetadata(eq(item), eq(Item.ANY), eq(Item.ANY), eq(Item.ANY), eq(Item.ANY)))
            .thenReturn(ImmutableList.of(metadataValue));
        when(metadataValue.getMetadataField()).thenReturn(metadataField);
        when(metadataField.getMetadataSchema()).thenReturn(metadataSchema);
        when(metadataSchema.getName()).thenReturn(mdSchemaName);
        when(metadataField.getElement()).thenReturn(mdFieldElement);
        when(metadataField.getQualifier()).thenReturn(mdFieldQualifier);
        when(metadataValue.getValue()).thenReturn(mdValue);

        when(bitstreamService.retrieve(any(Context.class), eq(primaryBitstream)))
            .thenReturn(new ByteArrayInputStream(PRIMARY_NAME.getBytes()));
        when(bitstreamService.retrieve(any(Context.class), eq(licenseBitstream)))
            .thenReturn(new ByteArrayInputStream(LICENSE_NAME.getBytes()));

        when(bundleService.getMetadataFirstValue(eq(bundle), eq(DC_SCHEMA), eq("title"), isNull(String.class),
                                                 eq(Item.ANY))).thenReturn(BUNDLE_NAME);

        final ItemPacker packer = new ItemPacker(item, archFmt);
        final File packedOutput = packer.pack(output.toFile());

        verify(bundleService, times(3)).getMetadataFirstValue(eq(bundle), eq(DC_SCHEMA), eq("title"),
                                                              isNull(String.class), eq(Item.ANY));
        verify(bitstreamService, times(3)).setMetadataSingleValue(any(Context.class), eq(primaryBitstream),
                                                                  eq(DC_SCHEMA), matches("title|source|description"),
                                                                  isNull(String.class), isNull(String.class),
                                                                  eq(PRIMARY_NAME));
        verify(bitstreamService, times(3)).setMetadataSingleValue(any(Context.class), eq(licenseBitstream),
                                                                  eq(DC_SCHEMA), matches("title|source|description"),
                                                                  isNull(String.class), isNull(String.class),
                                                                  eq(LICENSE_NAME));
        verify(bitstreamService, times(1)).retrieve(any(Context.class), eq(primaryBitstream));
        verify(bitstreamService, times(1)).retrieve(any(Context.class), eq(licenseBitstream));
        verify(itemService, times(1)).getMetadata(eq(item), eq(Item.ANY), eq(Item.ANY), eq(Item.ANY), eq(Item.ANY));

        assertThat(packedOutput).exists();
        assertThat(packedOutput).isFile();

        packedOutput.delete();
    }

    private void verifyBundleService() {
    }

    private void verifyBitstreamService() {
    }

    private void verifyItemService() {
    }

}