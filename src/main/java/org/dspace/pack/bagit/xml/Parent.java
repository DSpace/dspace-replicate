/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit.xml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

/**
 * Super class for parent xml nodes. These do not have any body associated with them and can have any number of
 * children.
 *
 * @author mikejritter
 */
public abstract class Parent implements Element {

    private final List<Element> children = new ArrayList<>();

    public abstract String getLocalName();

    @Override
    public String getBody() {
        return null;
    }

    @Override
    public Boolean hasChildren() {
        return true;
    }

    public void addChild(final Element value) {
        children.add(value);
    }

    public void addAll(final List<Element> children) {
        this.children.addAll(children);
    }

    @Override
    public List<Element> getChildren() {
        return children;
    }

    @Override
    public Map<String, String> getAttributes() {
        return ImmutableMap.of();
    }

}
