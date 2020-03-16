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
import org.junit.Test;

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
        final CatalogPacker packer = new CatalogPacker("object-id", "owner-id", ImmutableList.of("member"));
        final File packedOutput = packer.pack(output.toFile());

        assertThat(packedOutput).exists();
        assertThat(packedOutput).isFile();

        packedOutput.delete();
    }
}