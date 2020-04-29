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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Context;
import org.dspace.pack.PackerFactory;
import org.dspace.pack.bagit.xml.Metadata;
import org.dspace.pack.bagit.xml.Value;
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
    private final String xmlAttrName = "name";

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
        metadata.addChild(new Value(xmlBody, ImmutableMap.of(xmlAttrName, xmlAttr)));

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
        bitstreams.add(new BagBitstream(bitstream, bundleName, null));
        final File directory = root.resolve(bagName).toFile();
        final BitstreamFormat bitstreamFormat = initReloadable(BitstreamFormat.class);
        bitstreamFormat.setExtensions(Collections.singletonList("txt"));

        final BagItAipWriter writer = new BagItAipWriter(directory, archFmt, logo, properties, metadata, bitstreams);

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

        // todo additional verification that the zip contains all expected entries
        Files.delete(packagedAip.toPath());
    }

    @Test(expected = IllegalStateException.class)
    public void testWriteAipExists() throws Exception {
        final String bagName = "existing-bagit-aip";
        final URL resources = this.getClass().getClassLoader().getResource("");
        final Path root = Paths.get(Objects.requireNonNull(resources).toURI());

        final Bitstream logo = null;
        final File directory = root.resolve(bagName).toFile();

        final BagItAipWriter writer = new BagItAipWriter(directory, archFmt, logo, properties, metadata, bitstreams);
        writer.packageAip();
    }

}