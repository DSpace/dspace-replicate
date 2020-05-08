/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit.xml;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Common interface for deserialization of xml in BagIt AIPs.
 *
 * @author mikejritter
 */
public interface ElementDeserializer <T extends Element> {

    /**
     * Read an {@link Element} from an {@link XMLStreamReader}
     *
     * If there are any issues when reading the xml, an {@link XMLStreamException} is thrown
     *
     * @param reader the {@link XMLStreamReader} to read xml from
     * @return the {@link Element}
     * @throws XMLStreamException if there are any problems when reading from the {@link XMLStreamReader}
     */
    T readElement(XMLStreamReader reader) throws XMLStreamException;

}
