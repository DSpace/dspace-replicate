package org.dspace.pack.bagit.xml;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Read a {@link Metadata} from an {@link XMLStreamReader}
 *
 * @author mikejritter
 */
public class MetadataDeserializer implements ElementDeserializer<Metadata> {
    @Override
    public Metadata readElement(XMLStreamReader reader) throws XMLStreamException {
        final Metadata metadata = new Metadata();
        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT &&
                reader.getLocalName().equalsIgnoreCase(metadata.getLocalName())) {

                // search for value stanzas
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT &&
                        reader.getLocalName().equalsIgnoreCase(Value.LOCAL_NAME)) {
                        final ValueDeserializer valueDeserializer = new ValueDeserializer();
                        final Element element = valueDeserializer.readElement(reader);
                        if (element != null) {
                            metadata.addChild(element);
                        }
                    }
                }
            }
        }

        return metadata;
    }
}
