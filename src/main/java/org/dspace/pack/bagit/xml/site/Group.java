package org.dspace.pack.bagit.xml.site;

import java.util.Map;

import com.google.common.collect.ImmutableMap;
import org.dspace.pack.bagit.xml.Parent;

public class Group extends Parent {

    private final Long id;
    private final String name;

    public Group(final Long id, final String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String getLocalName() {
        return "Groups";
    }

    @Override
    public Map<String, String> getAttributes() {
        return ImmutableMap.of("ID", String.valueOf(id), "Name", name);
    }
}
