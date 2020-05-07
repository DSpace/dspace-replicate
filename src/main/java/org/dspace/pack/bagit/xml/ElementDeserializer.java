package org.dspace.pack.bagit.xml;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Common interface for deserialization of xml in BagIt AIPs.
 *
 * @author mikejritter
 */
public interface ElementDeserializer <T extends Element> {

    T readElement(XMLStreamReader reader) throws XMLStreamException;

}
