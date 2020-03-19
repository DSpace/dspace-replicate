package org.dspace.pack.bagit;

import java.nio.file.Path;
import java.util.List;

/**
 * Information about a {@link org.dspace.content.Bitstream} packaged in an aip
 *
 * @author mikejritter
 * @since 2020-03-19
 */
public class PackagedBitstream {

    private final String bundle;
    private final Path bitstream;
    private final List<XmlElement> metadata;

    /**
     * Init buddy
     *
     * @param bundle
     * @param bitstream
     * @param metadata
     */
    public PackagedBitstream(final String bundle, final Path bitstream, final List<XmlElement> metadata) {
        this.bundle = bundle;
        this.bitstream = bitstream;
        this.metadata = metadata;
    }


    public String getBundle() {
        return bundle;
    }

    public Path getBitstream() {
        return bitstream;
    }

    public List<XmlElement> getMetadata() {
        return metadata;
    }
}
