/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit.xml.policy;

import jakarta.xml.bind.annotation.XmlAttribute;

/**
 * Pojo for {@link org.dspace.authorize.ResourcePolicy} objects
 *
 * @author mikejritter
 */
public class Policy {

    private String name;
    private String type;
    private String group;
    private String action;
    private String eperson;
    private String endDate;
    private String startDate;
    private String description;

    @XmlAttribute
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlAttribute
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @XmlAttribute
    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    @XmlAttribute
    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    @XmlAttribute
    public String getEperson() {
        return eperson;
    }

    public void setEperson(String eperson) {
        this.eperson = eperson;
    }

    @XmlAttribute(name = "end-date")
    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    @XmlAttribute(name = "start-date")
    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    @XmlAttribute
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
