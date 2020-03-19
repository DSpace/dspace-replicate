package org.dspace.pack.bagit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.After;
import org.junit.Test;

/**
 * Small set of tests to cover flows the unpack tests might not hit
 *
 * @author mikejritter
 * @since 2020-03-19
 */
public class BagItAipReaderTest extends BagItPackerTest {

    @Test(expected = IOException.class)
    public void failIfArchiveIsNull() throws IOException {
        final BagItAipReader reader = new BagItAipReader(null);
    }

    @Test(expected = IOException.class)
    public void failIfArchiveDoesNotExist() throws URISyntaxException, IOException {
        final URL resource = this.getClass().getClassLoader().getResource("");
        assertNotNull(resource);

        final Path path = Paths.get(resource.toURI()).resolve("does-not-exist");
        final BagItAipReader reader = new BagItAipReader(path);
    }

    @Test
    public void readFileNotExists() throws URISyntaxException, IOException {
        final URL resource = this.getClass().getClassLoader().getResource("");
        assertNotNull(resource);
        final Path path = Paths.get(resource.toURI()).resolve("unpack/catalog.zip");

        final BagItAipReader reader = new BagItAipReader(path);
        final List<String> permissions = reader.readFile("permissions");
        assertThat(permissions).isNotNull();
        assertThat(permissions).isEmpty();
        reader.clean();
    }

    @After
    public void verifyMocks() {
    }

}