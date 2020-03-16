/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.dspace.content.MetadataSchema.DC_SCHEMA;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.isNull;
import static org.mockito.Matchers.matches;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
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
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link ItemPacker}
 *
 * @author mikejritter
 */
public class ItemPackerTest extends BagItPackerTest {

    private static final String SCHEMA_NAME = "metadataSchema";
    private static final String FIELD_ELEMENT = "metadataFieldElement";
    private static final String FIELD_QUALIFIER = "metadataFieldQualifier";
    private static final String METADATA_VALUE = "metadataValue";

    private static final String PRIMARY_NAME = "primary";
    private static final String LICENSE_NAME = "license";
    private static final String BUNDLE_NAME = "bundle";

    // mocked values
    private ItemService itemService;
    private BundleService bundleService;
    private BitstreamService bitstreamService;

    // entities which the item will use
    private Bundle bundle;
    private Bitstream primaryBitstream;
    private Bitstream licenseBitstream;
    private MetadataValue metadataValue;
    private MetadataField metadataField;
    private MetadataSchema metadataSchema;

    @Before
    public void setup() throws SQLException {
        super.setup();

        try {
            // Create some metadata for the item
            metadataSchema = initReloadable(MetadataSchema.class);
            metadataSchema.setName(SCHEMA_NAME);

            metadataField = initReloadable(MetadataField.class);
            metadataField.setMetadataSchema(metadataSchema);
            metadataField.setElement(FIELD_ELEMENT);
            metadataField.setQualifier(FIELD_QUALIFIER);

            metadataValue = initReloadable(MetadataValue.class);
            metadataValue.setMetadataField(metadataField);
            metadataValue.setValue(METADATA_VALUE);

            // create bitstreams and bundle
            primaryBitstream = initDSO(Bitstream.class);
            primaryBitstream.setSequenceID(0);

            licenseBitstream = initDSO(Bitstream.class);
            licenseBitstream.setSequenceID(1);

            bundle = initDSO(Bundle.class);
            // kind of hacky... more reflection to add the bitstreams to the bundle
            final Field bitstreamsField = Bundle.class.getDeclaredField("bitstreams");
            bitstreamsField.setAccessible(true);
            List<Bitstream> bitstreams = (List<Bitstream>) bitstreamsField.get(bundle);
            bitstreams.add(licenseBitstream);
            bitstreams.add(primaryBitstream);
            bundle.setPrimaryBitstreamID(primaryBitstream);
        } catch (ReflectiveOperationException e) {
            fail("Unable to initiate Metadata entities");
        }

        // mocks for service interactions
        itemService = ContentServiceFactory.getInstance().getItemService();
        bundleService = ContentServiceFactory.getInstance().getBundleService();
        bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
    }

    @Test
    public void testPack() throws Exception {
        final String bitstreamTitle = "title";
        final String bitstreamRegex = "title|source|description";

        // setup output
        final URL resources = CollectionPackerTest.class.getClassLoader().getResource("");
        assertNotNull(resources);
        final Path output = Paths.get(resources.toURI().resolve("item-packer-test"));

        // Item entity, todo linked collections
        final Item item = initDSO(Item.class);
        item.getBundles().add(bundle);

        // all the interactions we expect to occur
        when(itemService.getMetadata(eq(item), eq(Item.ANY), eq(Item.ANY), eq(Item.ANY), eq(Item.ANY)))
            .thenReturn(ImmutableList.of(metadataValue));

        when(bitstreamService.getMetadataFirstValue(eq(primaryBitstream), eq(DC_SCHEMA),
                                                    matches(bitstreamRegex), isNull(String.class),
                                                    eq(Item.ANY))).thenReturn(PRIMARY_NAME);

        when(bitstreamService.getMetadataFirstValue(eq(licenseBitstream), eq(DC_SCHEMA),
                                                    matches(bitstreamRegex), isNull(String.class),
                                                    eq(Item.ANY))).thenReturn(LICENSE_NAME);

        when(bitstreamService.retrieve(any(Context.class), eq(primaryBitstream)))
            .thenReturn(new ByteArrayInputStream(PRIMARY_NAME.getBytes()));
        when(bitstreamService.retrieve(any(Context.class), eq(licenseBitstream)))
            .thenReturn(new ByteArrayInputStream(LICENSE_NAME.getBytes()));

        when(bundleService.getMetadataFirstValue(eq(bundle), eq(DC_SCHEMA), eq(bitstreamTitle), isNull(String.class),
                                                 eq(Item.ANY))).thenReturn(BUNDLE_NAME);

        // and perform the packaging
        final ItemPacker packer = new ItemPacker(item, archFmt);
        final File packedOutput = packer.pack(output.toFile());

        // verify all the interactions we outlined above
        verify(bundleService, times(1)).getMetadataFirstValue(eq(bundle), eq(DC_SCHEMA), eq(bitstreamTitle),
                                                              isNull(String.class), eq(Item.ANY));
        verify(bitstreamService, times(3)).getMetadataFirstValue(eq(primaryBitstream), eq(DC_SCHEMA),
                                                                 matches(bitstreamRegex),
                                                                 isNull(String.class), eq(Item.ANY));
        verify(bitstreamService, times(3)).getMetadataFirstValue(eq(licenseBitstream), eq(DC_SCHEMA),
                                                                 matches(bitstreamRegex),
                                                                  isNull(String.class), eq(Item.ANY));
        verify(bitstreamService, times(1)).retrieve(any(Context.class), eq(primaryBitstream));
        verify(bitstreamService, times(1)).retrieve(any(Context.class), eq(licenseBitstream));
        verify(itemService, times(1)).getMetadata(eq(item), eq(Item.ANY), eq(Item.ANY), eq(Item.ANY), eq(Item.ANY));

        assertThat(packedOutput).exists();
        assertThat(packedOutput).isFile();

        packedOutput.delete();
    }

}