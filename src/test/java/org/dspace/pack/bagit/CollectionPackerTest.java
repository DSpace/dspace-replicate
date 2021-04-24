/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.ImmutableList;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
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

        // init test DSOs
        final Item template = initDSO(Item.class);
        final MetadataValue templateMetadata = createMetadataValue();

        // also spy the collection so we can get the Item during getTemplateItem
        final Collection collection = spy(initDSO(Collection.class));
        assertNotNull(collection);
        assertNotNull(collection.getID());
        assertNotNull(collection.getCommunities());
        assertTrue(collection.getCommunities().isEmpty());

        // grab our mocks
        final ItemService itemService = ContentServiceFactory.getInstance().getItemService();
        final CollectionService collectionService = ContentServiceFactory.getInstance().getCollectionService();
        for (String field : fields) {
            when(collectionService.getMetadata(eq(collection), eq(field))).thenReturn(field);
        }

        when(collection.getTemplateItem()).thenReturn(template);
        when(itemService.getMetadata(eq(template), eq(Item.ANY), eq(Item.ANY), eq(Item.ANY), eq(Item.ANY)))
            .thenReturn(Collections.singletonList(templateMetadata));

        final CollectionPacker collectionPacker = new CollectionPacker(mockContext, collection, archFmt);
        final File packedOutput = collectionPacker.pack(output.toFile());

        for (String field : fields) {
            verify(collectionService, times(1)).getMetadata(eq(collection), eq(field));
        }

        verify(collection, times(1)).getTemplateItem();
        verify(itemService, times(1)).getMetadata(eq(template), eq(Item.ANY), eq(Item.ANY), eq(Item.ANY), eq(Item.ANY));

        assertThat(packedOutput).exists();
        assertThat(packedOutput).isFile();

        packedOutput.delete();
    }

    @Test
    public void testUnpack() throws Exception {
        final GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
        final EPersonService ePersonService = EPersonServiceFactory.getInstance().getEPersonService();
        final AuthorizeService authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
        final ResourcePolicyService resourcePolicyService = AuthorizeServiceFactory.getInstance()
                                                                                   .getResourcePolicyService();

        // push to setup
        final URL resources = CollectionPackerTest.class.getClassLoader().getResource("unpack");
        assertNotNull(resources);

        final Path archive = Paths.get(resources.toURI()).resolve("COLLECTION@123456789-2.zip");
        final Path openArchive = Paths.get(resources.toURI()).resolve("COLLECTION@123456789-2");

        final Item template = initDSO(Item.class);
        final Collection collection = initDSO(Collection.class);
        assertNotNull(collection);

        final ItemService itemService = ContentServiceFactory.getInstance().getItemService();
        final CollectionService collectionService = ContentServiceFactory.getInstance().getCollectionService();
        when(itemService.createTemplateItem(any(Context.class), eq(collection))).thenReturn(template);

        final CollectionPacker packer = new CollectionPacker(mockContext, collection, archFmt);
        packer.unpack(archive.toFile());

        verify(collectionService, times(7)).setMetadataSingleValue(any(Context.class), eq(collection), anyString(),
                anyString(), anyString(), anyString(), anyString());
        verify(collectionService, times(1)).setLogo(any(Context.class), eq(collection), any(InputStream.class));
        verify(collectionService, times(1)).update(any(Context.class), eq(collection));
        verify(itemService, times(1)).createTemplateItem(any(Context.class), eq(collection));
        verify(itemService, times(1)).addMetadata(any(Context.class), any(Item.class), eq("dc"), eq("contributor"),
                                                  eq("illustrator"), eq(""), eq("illustrator jones"));
        verify(itemService, times(1)).update(any(Context.class), eq(template));

        // since our policy.xml is empty, verify that we never fetched anything and still used the authorize service
        // as expected
        final List<ResourcePolicy> empty = new ArrayList<>();
        verify(resourcePolicyService, never()).create(any(Context.class));
        verify(groupService, never()).findByName(any(Context.class), anyString());
        verify(ePersonService, never()).findByEmail(any(Context.class), anyString());
        verify(authorizeService, times(1)).removeAllPolicies(any(Context.class), eq(collection));
        verify(authorizeService, times(1)).addPolicies(any(Context.class), eq(empty), eq(collection));

        assertThat(openArchive).doesNotExist();
    }

}
