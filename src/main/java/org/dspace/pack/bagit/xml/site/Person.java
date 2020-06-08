package org.dspace.pack.bagit.xml.site;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

import org.dspace.eperson.EPerson;

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

    protected Person() {
    }

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

    @XmlAttribute(name = "ID")
    public String getId() {
        return id;
    }

    @XmlElement(name = "Email")
    public String getEmail() {
        return email;
    }

    @XmlElement(name = "Netid")
    public String getNetId() {
        return netId;
    }

    @XmlElement(name = "FirstName")
    public String getFirstName() {
        return firstName;
    }

    @XmlElement(name = "LastName")
    public String getLastName() {
        return lastName;
    }

    @XmlElement(name = "Language")
    public String getLanguage() {
        return language;
    }

    @XmlElement(name = "CanLogin")
    public String getCanLogin() {
        return canLogin;
    }

    @XmlElement(name = "SelfRegistered")
    public String getSelfRegistered() {
        return selfRegistered;
    }

    @XmlElement(name = "RequiredCertificate")
    public String getRequiredCertificate() {
        return requiredCertificate;
    }

    public Password getPassword() {
        return password;
    }

    public Person setId(String id) {
        this.id = id;
        return this;
    }

    public Person setEmail(String email) {
        this.email = email;
        return this;
    }

    public Person setNetId(String netId) {
        this.netId = netId;
        return this;
    }

    public Person setFirstName(String firstName) {
        this.firstName = firstName;
        return this;
    }

    public Person setLastName(String lastName) {
        this.lastName = lastName;
        return this;
    }

    public Person setLanguage(String language) {
        this.language = language;
        return this;
    }

    public Person canLogin() {
        this.canLogin = "";
        return this;
    }

    public Person selfRegistered() {
        this.selfRegistered = "";
        return this;
    }

    public Person requiredCertificate() {
        this.requiredCertificate = "";
        return this;
    }

    public Person setPassword(Password password) {
        this.password = password;
        return this;
    }

}
