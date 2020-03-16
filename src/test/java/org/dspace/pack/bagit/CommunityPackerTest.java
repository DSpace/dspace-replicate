package org.dspace.pack.bagit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.common.collect.ImmutableList;
import org.dspace.content.Community;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CommunityService;
import org.junit.Test;

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

}