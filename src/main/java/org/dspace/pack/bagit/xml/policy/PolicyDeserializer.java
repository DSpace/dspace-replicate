/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit.xml.policy;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.dspace.pack.bagit.xml.Element;
import org.dspace.pack.bagit.xml.ElementDeserializer;
import org.dspace.pack.bagit.xml.ValueDeserializer;

/**
 * Create a {@link Policy} pojo from an {@link XMLStreamReader}
 *
 * @author mikejritter
 */
public class PolicyDeserializer  { //implements ElementDeserializer<Policy> {

    public Policies readElement(XMLStreamReader reader) throws XMLStreamException {
        final Policies policy = new Policies();
        /*
        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT &&
                reader.getLocalName().equalsIgnoreCase(policy.getLocalName())) {

                // search for value stanzas
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT &&
                        reader.getLocalName().equalsIgnoreCase(Policy.CHILD_LOCAL_NAME)) {
                        final ValueDeserializer valueDeserializer = new ValueDeserializer();
                        final Element element = valueDeserializer.readElement(reader);
                        if (element != null) {
                            policy.addChild(element);
                        }
                    }
                }
            }
        }
         */

        return policy;
    }
}
