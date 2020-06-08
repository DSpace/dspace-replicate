/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit.xml.roles;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlValue;

/**
 * The Password tag for a {@link Member}
 *
 * @author mikejritter
 */
public class Password {

    private String hash;
    private String salt;
    private String algorithm;

    /**
     * @return the hashed password
     */
    @XmlValue
    public String getHash() {
        return hash;
    }

    /**
     * @param hash the hashed version of the password to use
     */
    public void setHash(String hash) {
        this.hash = hash;
    }

    /**
     * @return the salt for the password
     */
    @XmlAttribute(name = "salt")
    public String getSalt() {
        return salt;
    }

    /**
     * @param salt the salt of the password
     */
    public void setSalt(String salt) {
        this.salt = salt;
    }

    /**
     * @return the message digest of the password
     */
    @XmlAttribute(name = "digest")
    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * @param algorithm the algorithm to set for the password
     */
    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }
}
