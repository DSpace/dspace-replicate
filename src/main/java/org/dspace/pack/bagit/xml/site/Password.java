package org.dspace.pack.bagit.xml.site;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlValue;

public class Password {

    private String hash;
    private String salt;
    private String algorithm;

    @XmlValue
    public String getHash() {
        return hash;
    }

    public Password setHash(String hash) {
        this.hash = hash;
        return this;
    }

    @XmlAttribute(name = "salt")
    public String getSalt() {
        return salt;
    }

    public Password setSalt(String salt) {
        this.salt = salt;
        return this;
    }

    @XmlAttribute(name = "digest")
    public String getAlgorithm() {
        return algorithm;
    }

    public Password setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
        return this;
    }
}
