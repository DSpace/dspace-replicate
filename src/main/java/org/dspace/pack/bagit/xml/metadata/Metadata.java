package org.dspace.pack.bagit.xml.metadata;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "metadata")
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
