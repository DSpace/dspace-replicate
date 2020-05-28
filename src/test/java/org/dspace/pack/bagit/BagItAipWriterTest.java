/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Context;
import org.dspace.pack.PackerFactory;
import org.dspace.pack.bagit.xml.metadata.Metadata;
import org.dspace.pack.bagit.xml.metadata.Value;
import org.dspace.pack.bagit.xml.policy.Policies;
import org.dspace.pack.bagit.xml.policy.Policy;
import org.elasticsearch.common.recycler.Recycler;
import org.junit.Before;
import org.junit.Test;

/**
 * @author mikejritter
 * @since 2020-03-09
 */
public class BagItAipWriterTest extends BagItPackerTest {

    private final String archFmt = "zip";
    private final String objectType = "test-bag";
    private final String bundleName = "test-bundle";
    private final String xmlBody = "test-xml-body";
    private final String xmlAttr = "test-xml-attr";

    private Policies policies;
    private Metadata metadata;
    private List<BagBitstream> bitstreams;
    private Map<String, List<String>> properties;

    private BitstreamService bitstreamService;

    @Before
    public void setup() throws SQLException {
        super.setup();
        final String objectTypeLine = PackerFactory.OBJECT_TYPE + objectType;
        properties = ImmutableMap.of(PackerFactory.OBJFILE, Collections.singletonList(objectTypeLine));
        metadata = new Metadata();
        Value value = new Value();
        value.setName(xmlAttr);
        value.setBody(xmlBody);
        metadata.addValue(value);

        policies = new Policies();
        Policy policy = new Policy();
        policy.setType("READ");
        policies.addPolicy(policy);

        bitstreams = new ArrayList<>();

        bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
    }

    @Test
    public void testWriteAip() throws Exception {
        final String bagName = "test-write-aip";
        final URL resources = this.getClass().getClassLoader().getResource("");
        final Path root = Paths.get(Objects.requireNonNull(resources).toURI());

        final Bitstream logo = initDSO(Bitstream.class);
        final Bitstream bitstream = initDSO(Bitstream.class);
        bitstreams.add(new BagBitstream(bitstream, bundleName, null, null));
        final File directory = root.resolve(bagName).toFile();
        final BitstreamFormat bitstreamFormat = initReloadable(BitstreamFormat.class);
        bitstreamFormat.setExtensions(Collections.singletonList("txt"));

        final BagItAipWriter writer = new BagItAipWriter(directory, archFmt, properties)
            .withLogo(logo)
            .withMetadata(metadata)
            .withPolicies(policies)
            .withBitstreams(bitstreams);

        when(bitstreamService.retrieve(any(Context.class), eq(logo)))
            .thenReturn(new ByteArrayInputStream("logo".getBytes()));
        when(bitstreamService.retrieve(any(Context.class), eq(bitstream)))
            .thenReturn(new ByteArrayInputStream("hello".getBytes()));
        when(bitstreamService.getFormat(any(Context.class), any(Bitstream.class)))
            .thenReturn(bitstreamFormat);

        final File packagedAip = writer.packageAip();

        verify(bitstreamService, times(2)).retrieve(any(Context.class), any(Bitstream.class));
        verify(bitstreamService, times(2)).getFormat(any(Context.class), any(Bitstream.class));

        assertThat(packagedAip).exists();
        assertThat(packagedAip).isFile();

        // read the manifests to get the entries written to the bag
        final Map<String, List<String>> contents = new HashMap<>();
        try (InputStream is = Files.newInputStream(packagedAip.toPath());
             ZipArchiveInputStream zis = new ZipArchiveInputStream(is)) {
            ArchiveEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                final String entryName = entry.getName();
                if (entryName.endsWith("manifest-md5.txt")) {
                    final List<String> lines = IOUtils.readLines(zis);
                    contents.put(entryName, lines);
                }
            }
        }

        final String manifestKey = "test-write-aip/manifest-md5.txt";
        final String tagManifestKey = "test-write-aip/tagmanifest-md5.txt";
        assertThat(contents).containsKeys(manifestKey, tagManifestKey);

        // it would be nice to test the file names in the captured lines as well
        final List<String> manifestLines = contents.get(manifestKey);
        final List<String> tagManifestLines = contents.get(tagManifestKey);
        assertThat(manifestLines).hasSize(5);
        assertThat(tagManifestLines).hasSize(3);

        Files.delete(packagedAip.toPath());
    }

    @Test(expected = IllegalStateException.class)
    public void testWriteAipExists() throws Exception {
        final String bagName = "existing-bagit-aip";
        final URL resources = this.getClass().getClassLoader().getResource("");
        final Path root = Paths.get(Objects.requireNonNull(resources).toURI());

        final Bitstream logo = null;
        final File directory = root.resolve(bagName).toFile();

        final BagItAipWriter writer = new BagItAipWriter(directory, archFmt, properties)
            .withLogo(logo)
            .withMetadata(metadata)
            .withBitstreams(bitstreams);
        writer.packageAip();
    }

}