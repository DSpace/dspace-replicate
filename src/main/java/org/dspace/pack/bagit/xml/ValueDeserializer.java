/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit.xml;

import java.util.HashMap;
import java.util.Map;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Create a {@link Value} from a {@link XMLStreamReader}
 *
 * @author mikejritter
 */
public class ValueDeserializer implements ElementDeserializer<Value> {
    @Override
    public Value readElement(XMLStreamReader reader) throws XMLStreamException {
        // we begin on a start element so initialize the attributes first
        final Map<String, String> attributes = new HashMap<>();
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            attributes.put(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
        }

        // now iterate to find the body and end element
        String body = null;
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.CHARACTERS:
                    body = reader.getText();
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    return new Value(body, attributes);
                default:
                    break;
            }
        }

        return null;
    }
}
