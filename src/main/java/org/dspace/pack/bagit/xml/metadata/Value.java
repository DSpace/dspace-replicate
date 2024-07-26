/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit.xml.metadata;

import static org.dspace.content.service.DSpaceObjectService.MD_COPYRIGHT_TEXT;
import static org.dspace.content.service.DSpaceObjectService.MD_INTRODUCTORY_TEXT;
import static org.dspace.content.service.DSpaceObjectService.MD_LICENSE;
import static org.dspace.content.service.DSpaceObjectService.MD_NAME;
import static org.dspace.content.service.DSpaceObjectService.MD_PROVENANCE_DESCRIPTION;
import static org.dspace.content.service.DSpaceObjectService.MD_SHORT_DESCRIPTION;
import static org.dspace.content.service.DSpaceObjectService.MD_SIDEBAR_TEXT;
import static org.dspace.content.service.DSpaceObjectService.MD_SOURCE;
import static org.dspace.content.service.DSpaceObjectService.MD_USER_FORMAT_DESCRIPTION;
import static org.dspace.eperson.service.EPersonService.MD_FIRSTNAME;
import static org.dspace.eperson.service.EPersonService.MD_LANGUAGE;
import static org.dspace.eperson.service.EPersonService.MD_LASTNAME;
import static org.dspace.eperson.service.EPersonService.MD_PHONE;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlValue;

import org.dspace.content.MetadataFieldName;
import org.dspace.content.MetadataValue;

/**
 * Pojo to contain information from {@link org.dspace.content.MetadataValue}. Should be populated by each
 * {@link org.dspace.pack.Packer} implementation. Setters are used by JAXB and set to private so that only the two
 * constructors are used depending on the workflow.
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

    /**
     * Default constructor for JAXB
     */
    protected Value() {
    }

    /**
     * Construct a Value from a {@link org.dspace.content.MetadataValue} using only a value for the body of the xml tag
     * and a name of the MetadataValue (equivalent the the schema name on the MetadataValue)
     *
     * @param body the body of the Value
     * @param name the name of the MetadataValue
     */
    public Value(final String body, final String name) {
        this.body = body;
        this.name = name;
    }

    /**
     * Gets the {@code MetadataFieldName} for this value.
     *
     * @return the appropriate MetadataFieldName for the given field.
     * @throws IllegalStateException if this value does not have a valid MetadataFieldName.
     */
    public MetadataFieldName getMetadataField() {
        if (schema != null) {
            return new MetadataFieldName(schema, element, qualifier);
        }
        switch (name) {
            case "introductory_text":
                return MD_INTRODUCTORY_TEXT;
            case "short_description":
                return MD_SHORT_DESCRIPTION;
            case "side_bar_text":
                return MD_SIDEBAR_TEXT;
            case "copyright_text":
                return MD_COPYRIGHT_TEXT;
            case "name":
                return MD_NAME;
            case "provenance_description":
                return MD_PROVENANCE_DESCRIPTION;
            case "license":
                return MD_LICENSE;
            case "user_format_description":
                return MD_USER_FORMAT_DESCRIPTION;
            case "source":
                return MD_SOURCE;
            case "firstname":
                return MD_FIRSTNAME;
            case "lastname":
                return MD_LASTNAME;
            case "phone":
                return MD_PHONE;
            case "language":
                return MD_LANGUAGE;
            default:
                throw new IllegalStateException("Value does not have a field recognized by DSpace: " + name);
        }
    }

    /**
     * Construct a full qualified Value from a {@link MetadataValue}.
     *
     * @param metadataValue the MetadataValue to use as a source of information
     */
    public Value(final MetadataValue metadataValue) {
        this.body = metadataValue.getValue();
        this.language = metadataValue.getLanguage();
        this.element = metadataValue.getMetadataField().getElement();
        this.qualifier = metadataValue.getMetadataField().getQualifier();
        this.schema = metadataValue.getMetadataField().getMetadataSchema().getName();
    }

    @XmlAttribute
    public String getName() {
        return name;
    }

    private void setName(String name) {
        this.name = name;
    }

    @XmlAttribute
    public String getSchema() {
        return schema;
    }

    private void setSchema(String schema) {
        this.schema = schema;
    }

    @XmlAttribute
    public String getElement() {
        return element;
    }

    private void setElement(String element) {
        this.element = element;
    }

    @XmlAttribute
    public String getLanguage() {
        return language;
    }

    private void setLanguage(String language) {
        this.language = language;
    }

    @XmlAttribute
    public String getQualifier() {
        return qualifier;
    }

    private void setQualifier(String qualifier) {
        this.qualifier = qualifier;
    }

    @XmlValue
    public String getBody() {
        return body;
    }

    private void setBody(String body) {
        this.body = body;
    }
}
