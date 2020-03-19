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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.common.collect.ImmutableList;
import org.dspace.content.Community;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CommunityService;
import org.dspace.core.Context;
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
        final CommunityPacker packer = new CommunityPacker(community, archFmt);
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
        // push to setup
        final URL resources = CollectionPackerTest.class.getClassLoader().getResource("unpack");
        assertNotNull(resources);

        final Path archive = Paths.get(resources.toURI()).resolve("COMMUNITY@123456789-1.zip");
        final Path openArchive = Paths.get(resources.toURI()).resolve("COMMUNITY@123456789-1");

        final Community community = initDSO(Community.class);
        assertNotNull(community);

        final CommunityService communityService = ContentServiceFactory.getInstance().getCommunityService();
        final CommunityPacker packer = new CommunityPacker(community, archFmt);
        packer.unpack(archive.toFile());

        verify(communityService, times(5)).setMetadata(any(Context.class), eq(community), anyString(), anyString());
        verify(communityService, never()).setLogo(any(Context.class), eq(community), any(InputStream.class));
        verify(communityService, times(1)).update(any(Context.class), eq(community));

        assertThat(openArchive).doesNotExist();
    }

}