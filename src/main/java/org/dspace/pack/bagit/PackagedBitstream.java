/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import java.nio.file.Path;

import org.dspace.pack.bagit.xml.metadata.Metadata;
import org.dspace.pack.bagit.xml.policy.Policies;

/**
 * Information about a {@link org.dspace.content.Bitstream} packaged in an aip
 *
 * @author mikejritter
 * @since 2020-03-19
 */
public class PackagedBitstream {

    private final String bundle;
    private final Path bitstream;
    private final Policies policies;
    private final Metadata metadata;

    /**
     * Constructor for bitstreams packaged in a BagIt AIP
     *
     * @param bundle the name of the bundle for the bitstream
     * @param bitstream the path to the bitstream data
     * @param metadata the metadata for the bitstream, as a {@link Metadata} pojo
     * @param policies the policy for the bitstream
     */
    public PackagedBitstream(final String bundle, final Path bitstream, final Metadata metadata,
                             final Policies policies) {
        this.bundle = bundle;
        this.bitstream = bitstream;
        this.metadata = metadata;
        this.policies = policies;
    }

    /**
     * @return the bundle name
     */
    public String getBundle() {
        return bundle;
    }

    /**
     * @return the {@link Path} to the bitstream
     */
    public Path getBitstream() {
        return bitstream;
    }

    /**
     * @return the metadata for the bitstream
     */
    public Metadata getMetadata() {
        return metadata;
    }

    /**
     * @return the ResourcePolicies for the bitstream
     */
    public Policies getPolicies() {
        return policies;
    }
}
