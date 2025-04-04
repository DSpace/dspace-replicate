/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit.xml.policy;

import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * Root element for policy.xml. Contains only a list of {@link Policy} objects.
 *
 * @author mikejritter
 */
@XmlRootElement
public class Policies {

    private List<Policy> policies = new ArrayList<>();

    @XmlElement(name = "policy")
    public List<Policy> getPolicies() {
        return policies;
    }

    public void addPolicy(Policy policy) {
        policies.add(policy);
    }

    public void setPolicies(List<Policy> policies) {
        this.policies = policies;
    }
}
