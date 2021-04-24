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

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.common.collect.ImmutableList;
import org.dspace.core.Context;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Test the {@link CatalogPacker}
 *
 * @author mikejritter
 */
public class CatalogPackerTest extends BagItPackerTest {

    @Test
    public void testPack() throws Exception {
        // get the output location
        final URL resources = CollectionPackerTest.class.getClassLoader().getResource("");
        assertNotNull(resources);
        final Path output = Paths.get(resources.toURI().resolve("catalog-packer-test"));

        // setup the packer
        final CatalogPacker packer = new CatalogPacker(mockContext, "object-id", "owner-id", ImmutableList.of("member"));
        final File packedOutput = packer.pack(output.toFile());

        assertThat(packedOutput).exists();
        assertThat(packedOutput).isFile();

        packedOutput.delete();
    }

    @Test
    public void testUnpack() throws Exception {
        final URL resources = CollectionPackerTest.class.getClassLoader().getResource("");
        assertNotNull(resources);
        final Path archive = Paths.get(resources.toURI().resolve("unpack/catalog.zip"));
        final Path openArchive = Paths.get(resources.toURI().resolve("unpack/catalog"));

        final Context mockContext = Mockito.mock(Context.class);
        final CatalogPacker packer = new CatalogPacker(mockContext, "object-id", "owner-id",
                ImmutableList.of("member"));
        packer.unpack(archive.toFile());

        assertThat(openArchive).doesNotExist();
        assertThat(packer.getMembers()).hasSize(2);
        assertThat(packer.getMembers()).contains("admin", "test-user");
        assertThat(packer.getOwnerId()).isEqualTo("123456789/1");
    }
}
