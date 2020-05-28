package org.dspace.pack.bagit.xml.policy;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

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
