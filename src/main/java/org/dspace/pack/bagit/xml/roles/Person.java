/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit.xml.roles;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

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
    private Password password;

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
    public Person(EPerson ePerson) {
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

    /**
     * The Password tag for a Person
     *
     * @return the {@link Password} of the Person
     */
    public Password getPassword() {
        return password;
    }

    /**
     * @param id the id to set
     * @return the Person
     */
    public Person setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * @param email the email to set
     * @return the Person
     */
    public Person setEmail(String email) {
        this.email = email;
        return this;
    }

    /**
     * @param netId the netId to set
     * @return the Person
     */
    public Person setNetId(String netId) {
        this.netId = netId;
        return this;
    }

    /**
     * @param firstName the firstName to set
     * @return the Person
     */
    public Person setFirstName(String firstName) {
        this.firstName = firstName;
        return this;
    }

    /**
     * @param lastName the lastName to set
     * @return the Person
     */
    public Person setLastName(String lastName) {
        this.lastName = lastName;
        return this;
    }

    /**
     * @param language the language to set
     * @return the Person
     */
    public Person setLanguage(String language) {
        this.language = language;
        return this;
    }

    /**
     * Set that a {@link EPerson} has the can login flag
     *
     * @return the Person
     */
    public Person canLogin() {
        this.canLogin = "";
        return this;
    }

    /**
     * Set that a {@link EPerson} has the self registered flag
     *
     * @return the Person
     */
    public Person selfRegistered() {
        this.selfRegistered = "";
        return this;
    }

    /**
     * Set that a {@link EPerson} has the required certificate flag
     *
     * @return the Person
     */
    public Person requiredCertificate() {
        this.requiredCertificate = "";
        return this;
    }

    /**
     * @param password the {@link Password} to set
     * @return the Person
     */
    public Person setPassword(Password password) {
        this.password = password;
        return this;
    }

}
