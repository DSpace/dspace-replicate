package org.dspace.pack.bagit.xml;

import java.util.List;
import java.util.Map;

/**
 * Interface to define common ops which we need in order to serialize pojos as xml
 *
 * @author mikejritter
 */
public interface Element {

    String getLocalName();
    String getBody();
    Boolean hasChildren();
    List<Element> getChildren();
    Map<String, String> getAttributes();

}
