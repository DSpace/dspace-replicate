/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit.xml.metadata;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlValue;

/**
 * Pojo to contain information from {@link org.dspace.content.MetadataValue}. Should be populated by each
 * {@link org.dspace.pack.Packer} implementation
 *
 * @author mikejritter
 */
public class Value {

    private String body;

    // attributes
    private String name;
    private String schema;
    private String element;
    private String language;
    private String qualifier;

    @XmlAttribute
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlAttribute
    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    @XmlAttribute
    public String getElement() {
        return element;
    }

    public void setElement(String element) {
        this.element = element;
    }

    @XmlAttribute
    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    @XmlAttribute
    public String getQualifier() {
        return qualifier;
    }

    public void setQualifier(String qualifier) {
        this.qualifier = qualifier;
    }

    @XmlValue
    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
