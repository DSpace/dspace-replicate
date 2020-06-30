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
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.isNull;
import static org.mockito.Matchers.matches;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.assertj.core.util.Files;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchema;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.BundleService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
import org.dspace.handle.Handle;
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
    private BitstreamFormat bitstreamFormat;
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

            bitstreamFormat = initReloadable(BitstreamFormat.class);
            bitstreamFormat.setExtensions(Collections.<String>emptyList());

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

        // Item entity
        final Item item = initDSO(Item.class);
        item.getBundles().add(bundle);

        // Related collections
        final Collection owning = createCollection("owning");
        final Collection linked = createCollection("linked");

        final Set<Collection> collections = new HashSet<>();
        collections.add(owning);
        collections.add(linked);
        final Field collectionsField = Item.class.getDeclaredField("collections");
        collectionsField.setAccessible(true);
        collectionsField.set(item, collections);

        // all the interactions we expect to occur
        when(itemService.isOwningCollection(eq(item), eq(owning))).thenReturn(true);
        when(itemService.isOwningCollection(eq(item), eq(linked))).thenReturn(false);
        when(itemService.getMetadata(eq(item), eq(Item.ANY), eq(Item.ANY), eq(Item.ANY), eq(Item.ANY)))
            .thenReturn(Collections.singletonList(metadataValue));

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

        when(bitstreamService.getFormat(any(Context.class), any(Bitstream.class))).thenReturn(bitstreamFormat);

        when(bundleService.getMetadataFirstValue(eq(bundle), eq(DC_SCHEMA), eq(bitstreamTitle), isNull(String.class),
                                                 eq(Item.ANY))).thenReturn(BUNDLE_NAME);

        // and perform the packaging
        final ItemPacker packer = new ItemPacker(item, archFmt);
        final File packedOutput = packer.pack(output.toFile());

        // verify all the interactions we outlined above
        verify(itemService, times(1)).isOwningCollection(eq(item), eq(owning));
        verify(itemService, times(1)).isOwningCollection(eq(item), eq(linked));
        verify(bundleService, times(1)).getMetadataFirstValue(eq(bundle), eq(DC_SCHEMA), eq(bitstreamTitle),
                                                              isNull(String.class), eq(Item.ANY));
        verify(bitstreamService, times(3)).getMetadataFirstValue(eq(primaryBitstream), eq(DC_SCHEMA),
                                                                 matches(bitstreamRegex),
                                                                 isNull(String.class), eq(Item.ANY));
        verify(bitstreamService, times(3)).getMetadataFirstValue(eq(licenseBitstream), eq(DC_SCHEMA),
                                                                 matches(bitstreamRegex),
                                                                  isNull(String.class), eq(Item.ANY));
        verify(bitstreamService, times(2)).getFormat(any(Context.class), any(Bitstream.class));
        verify(bitstreamService, times(1)).retrieve(any(Context.class), eq(primaryBitstream));
        verify(bitstreamService, times(1)).retrieve(any(Context.class), eq(licenseBitstream));
        verify(itemService, times(1)).getMetadata(eq(item), eq(Item.ANY), eq(Item.ANY), eq(Item.ANY), eq(Item.ANY));

        assertThat(packedOutput).exists();
        assertThat(packedOutput).isFile();

        packedOutput.delete();
    }

    @Test
    public void testFetchThrowsException() throws Exception {
        final String bitstreamTitle = "title";
        final String bitstreamRegex = "title|source|description";

        // setup output
        final URL resources = CollectionPackerTest.class.getClassLoader().getResource("");
        assertNotNull(resources);
        final Path output = Paths.get(resources.toURI().resolve("item-packer-with-fetch"));

        // Item entity
        final Item item = initDSO(Item.class);
        item.getBundles().add(bundle);

        // all the interactions we expect to occur
        when(itemService.getMetadata(eq(item), eq(Item.ANY), eq(Item.ANY), eq(Item.ANY), eq(Item.ANY)))
            .thenReturn(Collections.singletonList(metadataValue));

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
        licenseBitstream.setSizeBytes(1L);
        primaryBitstream.setSizeBytes(1L);
        final ItemPacker packer = new ItemPacker(item, archFmt);
        packer.setReferenceFilter(BUNDLE_NAME + " 1 https://localhost/fetch");
        try {
            packer.pack(output.toFile());
        } catch (UnsupportedOperationException e) {
            assertNotNull(e);
        }

        // verify all the interactions we outlined above
        // 1 bundle.getName when looping the bundle, 2 from the ReferenceFilter
        verify(bundleService, times(3)).getMetadataFirstValue(eq(bundle), eq(DC_SCHEMA), eq(bitstreamTitle),
                                                              isNull(String.class), eq(Item.ANY));
        verify(bitstreamService, times(3)).getMetadataFirstValue(eq(primaryBitstream), eq(DC_SCHEMA),
                                                                 matches(bitstreamRegex),
                                                                 isNull(String.class), eq(Item.ANY));
        verify(bitstreamService, times(3)).getMetadataFirstValue(eq(licenseBitstream), eq(DC_SCHEMA),
                                                                 matches(bitstreamRegex),
                                                                 isNull(String.class), eq(Item.ANY));
        verify(itemService, times(1)).getMetadata(eq(item), eq(Item.ANY), eq(Item.ANY), eq(Item.ANY), eq(Item.ANY));

        Files.delete(output.toFile());
    }

    /**
     * Create a {@link Collection} with a given {@link Handle}
     *
     * @param handle the string value of the handle
     * @return the collection
     * @throws ReflectiveOperationException if the collection or handle cannot be created
     */
    private Collection createCollection(final String handle) throws ReflectiveOperationException {
        final Handle handleEntity = initReloadable(Handle.class);
        handleEntity.setHandle(handle);

        final Collection owning = initDSO(Collection.class);
        owning.addHandle(handleEntity);
        return owning;
    }

    @Test
    public void testUnpack() throws Exception {
        final GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
        final EPersonService ePersonService = EPersonServiceFactory.getInstance().getEPersonService();
        final AuthorizeService authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
        final ResourcePolicyService resourcePolicyService = AuthorizeServiceFactory.getInstance()
                                                                                   .getResourcePolicyService();

        final String license = LICENSE_NAME.toUpperCase();
        final String original = "ORIGINAL";
        final String metadataRegex = "title|source|description";
        final String bundlesRegex = license + "|" + original;
        // push to setup
        final URL resources = CollectionPackerTest.class.getClassLoader().getResource("unpack");
        assertNotNull(resources);

        final Path archive = Paths.get(resources.toURI()).resolve("ITEM@123456789-3.zip");
        final Path openArchive = Paths.get(resources.toURI()).resolve("ITEM@123456789-3");

        // might be good to use the defined bundles/bitstreams from the setup
        final Item item = initDSO(Item.class);
        final Bundle licenseBundle = initDSO(Bundle.class);
        final Bundle originalBundle = initDSO(Bundle.class);
        final Bitstream licenseBitstream = initDSO(Bitstream.class);
        final Bitstream originalBitstream = initDSO(Bitstream.class);

        when(bundleService.create(any(Context.class), eq(item), eq(license))).thenReturn(licenseBundle);
        when(bundleService.create(any(Context.class), eq(item), eq(original))).thenReturn(originalBundle);
        when(bitstreamService.create(any(Context.class), eq(licenseBundle), any(InputStream.class)))
            .thenReturn(licenseBitstream);
        when(bitstreamService.create(any(Context.class), eq(originalBundle), any(InputStream.class)))
            .thenReturn(originalBitstream);

        final ItemPacker packer = new ItemPacker(item, archFmt);
        packer.unpack(archive.toFile());

        verify(itemService, times(8)).addMetadata(any(Context.class), eq(item), nullable(String.class),
                                                  nullable(String.class), nullable(String.class),
                                                  nullable(String.class), nullable(String.class));
        verify(itemService, times(2)).getBundles(eq(item), anyString());
        verify(bundleService, times(2)).create(any(Context.class), eq(item), matches(bundlesRegex));
        verify(bitstreamService, times(1)).create(any(Context.class), eq(licenseBundle), any(InputStream.class));
        verify(bitstreamService, times(1)).create(any(Context.class), eq(originalBundle), any(InputStream.class));

        verify(bitstreamService, times(2)).setMetadataSingleValue(any(Context.class), eq(licenseBitstream),
                                                                  eq(DC_SCHEMA), matches(metadataRegex),
                                                                  isNull(String.class), isNull(String.class),
                                                                  anyString());

        verify(bitstreamService, times(3)).setMetadataSingleValue(any(Context.class), eq(originalBitstream),
                                                                  eq(DC_SCHEMA), matches(metadataRegex),
                                                                  isNull(String.class), isNull(String.class),
                                                                  anyString());

        verify(bitstreamService, times(1)).update(any(Context.class), eq(licenseBitstream));
        verify(bitstreamService, times(1)).update(any(Context.class), eq(originalBitstream));

        // since our policy.xml is empty, verify that we never fetched anything and still used the authorize service
        // as expected
        final List<ResourcePolicy> empty = new ArrayList<>();
        verify(resourcePolicyService, never()).create(any(Context.class));
        verify(groupService, never()).findByName(any(Context.class), anyString());
        verify(ePersonService, never()).findByEmail(any(Context.class), anyString());
        verify(authorizeService, times(1)).removeAllPolicies(any(Context.class), eq(item));
        verify(authorizeService, times(1)).addPolicies(any(Context.class), eq(empty), eq(item));

        assertThat(openArchive).doesNotExist();
    }

}