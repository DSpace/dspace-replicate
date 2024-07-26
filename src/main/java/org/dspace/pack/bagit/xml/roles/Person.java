/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit.xml.roles;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

import org.dspace.eperson.EPerson;

/**
 * A Person tag for the {@link DSpaceRoles} schema
 *
 * @author mikejritter
 */
public class Person {

    private String id;
    private String email;
    private String netId;
    private String firstName;
    private String lastName;
    private String language;
    private String canLogin;
    private String selfRegistered;
    private String requiredCertificate;

    /**
     * Default constructor for JAXB
     */
    protected Person() {
    }

    /**
     * Create a {@link Person} from an {@link EPerson}
     *
     * @param ePerson the {@link EPerson} to use
     */
    public Person(final EPerson ePerson) {
        this.id = ePerson.getID().toString();
        this.email = ePerson.getEmail();
        this.netId = ePerson.getNetid();
        this.firstName = ePerson.getFirstName();
        this.lastName = ePerson.getLastName();
        this.language = ePerson.getLanguage();
        this.canLogin = ePerson.canLogIn() ? "" : null;
        this.selfRegistered = ePerson.getSelfRegistered() ? "" : null;
        this.requiredCertificate = ePerson.getRequireCertificate() ? "" : null;
    }

    /**
     * @return the id, equivalent to {@link EPerson#getID()}
     */
    @XmlAttribute(name = "ID")
    public String getId() {
        return id;
    }

    /**
     * @return the email of the person, equivalent to {@link EPerson#getEmail()}
     */
    @XmlElement(name = "Email")
    public String getEmail() {
        return email;
    }

    /**
     * @return the netid of the person, equivalent to {@link EPerson#getNetid()}
     */
    @XmlElement(name = "Netid")
    public String getNetId() {
        return netId;
    }

    /**
     * @return the first name of the person, equivalent to {@link EPerson#getFirstName()}
     */
    @XmlElement(name = "FirstName")
    public String getFirstName() {
        return firstName;
    }

    /**
     * @return the last name of the person, equivalent to {@link EPerson#getLastName()}
     */
    @XmlElement(name = "LastName")
    public String getLastName() {
        return lastName;
    }

    /**
     * @return the language of the person, equivalent to {@link EPerson#getLanguage()}
     */
    @XmlElement(name = "Language")
    public String getLanguage() {
        return language;
    }

    /**
     * For the xml, a tag is omitted without a body so an empty String is stored if the person can log in.
     *
     * @return a non empty String if the person can log in ({@link EPerson#canLogIn()} is true)
     */
    @XmlElement(name = "CanLogin")
    public String getCanLogin() {
        return canLogin;
    }

    /**
     * For the xml, a tag is omitted without a body so an empty String is stored if the person is self registered.
     *
     * @return a non empty String if the person can log in ({@link EPerson#getSelfRegistered()} is true)
     */
    @XmlElement(name = "SelfRegistered")
    public String getSelfRegistered() {
        return selfRegistered;
    }

    /**
     * For the xml, a tag is omitted without a body so an empty String is stored if the person is requires a
     * certificate.
     *
     * @return a non empty String if the person can log in ({@link EPerson#getRequireCertificate()} is true)
     */
    @XmlElement(name = "RequiredCertificate")
    public String getRequiredCertificate() {
        return requiredCertificate;
    }

}
