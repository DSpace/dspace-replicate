/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit.xml.metadata;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Root tag for metadata.xml. Contains only a list of metadata {@link Value}s.
 *
 * @author mikejritter
 */
@XmlRootElement
public class Metadata {

    private List<Value> values = new ArrayList<>();

    @XmlElement(name = "value")
    public List<Value> getValues() {
        return values;
    }

    public void addValue(Value valueB) {
        values.add(valueB);
    }

    public void setValues(List<Value> values) {
        this.values = values;
    }
}
