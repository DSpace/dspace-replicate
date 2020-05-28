package org.dspace.pack.bagit.xml.metadata;


import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.junit.Test;

public class MetadataBTest {

    @Test
    public void marshal() throws JAXBException {
        Value valueA = new Value();
        valueA.setBody("test-body-2");
        valueA.setSchema("dc");
        valueA.setElement("name");
        valueA.setQualifier("unkwone");
        Value valueB = new Value();
        valueB.setBody("test-body");
        valueB.setSchema("dc");
        valueB.setElement("name");
        valueB.setQualifier("koala");
        Metadata metadataB = new Metadata();
        metadataB.addValue(valueA);
        metadataB.addValue(valueB);

        JAXBContext context = JAXBContext.newInstance(Metadata.class);

        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(metadataB, System.out);
    }

}