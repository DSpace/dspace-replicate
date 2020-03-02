package org.dspace.pack.bagit;

import java.util.List;

import com.google.common.base.Preconditions;
import org.dspace.content.Bitstream;

public class BagBitstream {
    private String fetchUrl;
    private Bitstream bitstream;

    private final String bundle;
    private final List<XmlElement> xml;

    public BagBitstream(final String fetchUrl, final String bundle, final List<XmlElement> xml) {
        this.xml = Preconditions.checkNotNull(xml);
        this.bundle = Preconditions.checkNotNull(bundle);
        this.fetchUrl = Preconditions.checkNotNull(fetchUrl);
    }

    public BagBitstream(final Bitstream bitstream, final String bundle, final List<XmlElement> xml) {
        this.xml = Preconditions.checkNotNull(xml);
        this.bundle = Preconditions.checkNotNull(bundle);
        this.bitstream = Preconditions.checkNotNull(bitstream);
    }

    public String getFetchUrl() {
        return fetchUrl;
    }

    public Bitstream getBitstream() {
        return bitstream;
    }

    public List<XmlElement> getXml() {
        return xml;
    }

    public String getBundle() {
        return bundle;
    }
}
