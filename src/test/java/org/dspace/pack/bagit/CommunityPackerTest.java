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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.content.Community;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CommunityService;
import org.dspace.core.Context;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
import org.junit.Test;

/**
 * Tests for {@link CommunityPacker}
 *
 * @author mikejritter
 */
public class CommunityPackerTest extends BagItPackerTest {

    private final ImmutableList<String> fields = ImmutableList.of("name", "short_description", "introductory_text",
                                                                  "copyright_text", "side_bar_text");

    @Test
    public void testPack() throws Exception {
        // setup output
        final URL resources = CollectionPackerTest.class.getClassLoader().getResource("");
        assertNotNull(resources);
        final Path output = Paths.get(resources.toURI().resolve("community-packer-test"));

        // setup Community entity
        final Community community = initDSO(Community.class);
        assertNotNull(community);
        assertNotNull(community.getID());

        // handle mocks
        final CommunityService communityService = ContentServiceFactory.getInstance().getCommunityService();
        for (String field : fields) {
            when(communityService.getMetadata(community, field)).thenReturn(field);
        }

        // and pack
        final CommunityPacker packer = new CommunityPacker(mockContext, community, archFmt);
        final File packedOutput = packer.pack(output.toFile());

        for (String field : fields) {
            verify(communityService, times(1)).getMetadata(community, field);
        }

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

        final Path archive = Paths.get(resources.toURI()).resolve("COMMUNITY@123456789-1.zip");
        final Path openArchive = Paths.get(resources.toURI()).resolve("COMMUNITY@123456789-1");

        final Community community = initDSO(Community.class);
        assertNotNull(community);

        final CommunityService communityService = ContentServiceFactory.getInstance().getCommunityService();
        final CommunityPacker packer = new CommunityPacker(mockContext, community, archFmt);
        packer.unpack(archive.toFile());

        // name="export-test"
        verify(communityService, times(1)).setMetadataSingleValue(any(Context.class), eq(community),
                eq("dc"), eq("title"), isNull(), isNull(), eq("export-test"));
        // short_description="test for export tool"
        verify(communityService, times(1)).setMetadataSingleValue(any(Context.class), eq(community),
                eq("dc"), eq("description"), eq("abstract"), isNull(), eq("test for export tool"));
        // introductory_text=""
        verify(communityService, times(1)).setMetadataSingleValue(any(Context.class), eq(community),
                eq("dc"), eq("description"), isNull(), isNull(), eq(""));
        // copyright_text=""
        verify(communityService, times(1)).setMetadataSingleValue(any(Context.class), eq(community),
                eq("dc"), eq("rights"), isNull(), isNull(), eq(""));
        // side_bar_text=""
        verify(communityService, times(1)).setMetadataSingleValue(any(Context.class), eq(community),
                eq("dc"), eq("description"), eq("tableofcontents"), isNull(), eq(""));

        verify(communityService, never()).setLogo(any(Context.class), eq(community), any(InputStream.class));
        verify(communityService, times(1)).update(any(Context.class), eq(community));

        // since our policy.xml is empty, verify that we never fetched anything and still used the authorize service
        // as expected
        final List<ResourcePolicy> empty = new ArrayList<>();
        verify(resourcePolicyService, never()).create(any(Context.class));
        verify(groupService, never()).findByName(any(Context.class), anyString());
        verify(ePersonService, never()).findByEmail(any(Context.class), anyString());
        verify(authorizeService, times(1)).removeAllPolicies(any(Context.class), eq(community));
        verify(authorizeService, times(1)).addPolicies(any(Context.class), eq(empty), eq(community));

        assertThat(openArchive).doesNotExist();
    }

}
