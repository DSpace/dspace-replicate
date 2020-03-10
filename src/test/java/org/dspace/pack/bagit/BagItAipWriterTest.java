package org.dspace.pack.bagit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.ImmutableMap;
import jdk.nashorn.internal.ir.annotations.Ignore;
import org.dspace.content.Bitstream;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Context;
import org.dspace.pack.PackerFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

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

    private List<XmlElement> metadata;
    private List<BagBitstream> bitstreams;
    private Map<String, List<String>> properties;

    private BitstreamService bitstreamService;

    @Before
    public void setup() throws SQLException {
        super.setup();
        final String objectTypeLine = PackerFactory.OBJECT_TYPE + objectType;
        properties = ImmutableMap.of(PackerFactory.OBJFILE, Collections.singletonList(objectTypeLine));
        XmlElement xmlElement = new XmlElement(xmlBody, ImmutableMap.of(xmlAttrName, xmlAttr));
        metadata = Collections.singletonList(xmlElement);

        // todo: test using bitstreams
        final Bitstream bitstream = Mockito.mock(Bitstream.class);
        final BagBitstream bagBits = new BagBitstream(bitstream, bundleName, metadata);
        bitstreams = Collections.singletonList(bagBits);

        bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
    }

    @Test
    public void testWriteAip() throws Exception {
        final String bagName = "test-write-aip";
        final URL resources = this.getClass().getClassLoader().getResource("");
        final Path root = Paths.get(Objects.requireNonNull(resources).toURI());

        final Bitstream logo = null;
        final File directory = root.resolve(bagName).toFile();

        final BagItAipWriter writer = new BagItAipWriter(directory, archFmt, logo, properties, metadata, bitstreams);

        when(bitstreamService.retrieve(any(Context.class), any(Bitstream.class)))
            .thenReturn(new ByteArrayInputStream("hello".getBytes()));

        final File packagedAip = writer.packageAip();

        verify(bitstreamService).retrieve(any(Context.class), any(Bitstream.class));

        assertThat(packagedAip).exists();
        assertThat(packagedAip).isFile();

        // todo additional verification that the zip contains all expected entries
        Files.delete(packagedAip.toPath());
    }

    @Test
    @Ignore
    public void testWriteAipExists() {
    }

}