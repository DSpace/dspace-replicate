package org.dspace.pack.bagit;

import java.util.Map;

public class XmlElement {
    private final String body;
    private final Map<String, String> attributes;

    public XmlElement(String body, Map<String, String> attributes) {
        this.body = body;
        this.attributes = attributes;
    }

    public String getBody() {
        return body;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }
}
