package org.dspace.pack.bagit.xml;

import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * Pojo for an xml node which has a body + attributes
 *
 * @author mikejritter
 */
public class Value implements Element {
    private final String LOCAL_NAME = "value";

    private final String body;
    private final Map<String, String> attributes;

    public Value(String body, Map<String, String> attributes) {
        this.body = Preconditions.checkNotNull(body);
        this.attributes = attributes;
    }

    @Override
    public String getLocalName() {
        return LOCAL_NAME;
    }

    @Override
    public String getBody() {
        return body;
    }

    @Override
    public Boolean hasChildren() {
        return false;
    }

    @Override
    public List<Element> getChildren() {
        return ImmutableList.of();
    }

    @Override
    public Map<String, String> getAttributes() {
        return attributes;
    }
}
