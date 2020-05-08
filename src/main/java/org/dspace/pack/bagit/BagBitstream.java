/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import com.google.common.base.Preconditions;
import org.dspace.content.Bitstream;
import org.dspace.pack.bagit.xml.Metadata;
import org.dspace.pack.bagit.xml.Policy;

/**
 * Hold information about a {@link Bitstream} and its metadata.
 *
 * @author mikejritter
 * @since 2020-03-12
 */
public class BagBitstream {
    private String fetchUrl;

    private final String bundle;
    private final Policy policy;
    private final Metadata metadata;
    private final Bitstream bitstream;

    /**
     * A {@link Bitstream} which is to be fetched rather than added to a BagIt bag
     *
     * @param fetchUrl the url to fetch the {@link Bitstream} from
     * @param bitstream the {@link Bitstream} being packaged
     * @param bundle the name of the {@link org.dspace.content.Bundle} the {@link Bitstream} belongs to
     * @param policy the {@link Policy} metadata associated with the {@link Bitstream}
     * @param metadata the metadata associated with the {@link Bitstream}
     */
    public BagBitstream(final String fetchUrl, final Bitstream bitstream, final String bundle, final Policy policy,
                        final Metadata metadata) {
        this.policy = policy;
        this.metadata = metadata;
        this.bundle = Preconditions.checkNotNull(bundle);
        this.fetchUrl = Preconditions.checkNotNull(fetchUrl);
        this.bitstream = Preconditions.checkNotNull(bitstream);
    }

    /**
     * Information for a {@link Bitstream} which is to be packaged directly in a BagIt bag
     *
     * @param bitstream the {@link Bitstream} being packaged
     * @param bundle the name of the {@link org.dspace.content.Bundle} the {@link Bitstream} belongs to
     * @param policy the {@link Policy} metadata associated with the {@link Bitstream}
     * @param metadata the metadata associated with the {@link Bitstream}
     */
    public BagBitstream(final Bitstream bitstream, final String bundle, final Policy policy, final Metadata metadata) {
        this.policy = policy;
        this.metadata = metadata;
        this.bundle = Preconditions.checkNotNull(bundle);
        this.bitstream = Preconditions.checkNotNull(bitstream);
    }

    /**
     * Get the url to fetch the {@link Bitstream} with, or null if it does not exist
     *
     * @return the fetchUrl
     */
    public String getFetchUrl() {
        return fetchUrl;
    }

    /**
     * Get the {@link Bitstream}, or null if it does not exist
     *
     * @return the bitstream
     */
    public Bitstream getBitstream() {
        return bitstream;
    }

    /**
     * Get the policy for a {@link Bitstream}
     *
     * @return the {@link Policy}
     */
    public Policy getPolicy() {
        return policy;
    }

    /**
     * Get the metadata associated with the {@link Bitstream}
     *
     * @return the {@link Metadata}
     */
    public Metadata getMetadata() {
        return metadata;
    }

    /**
     * Get the name of the {@link org.dspace.content.Bundle} associated with the {@link Bitstream}
     *
     * @return the name of the bundle
     */
    public String getBundle() {
        return bundle;
    }
}
