package org.dspace.pack.bagit;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.commons.io.input.XmlStreamReaderException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.DSpaceObject;
import org.dspace.content.packager.PackageException;
import org.dspace.content.packager.PackageUtils;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.pack.bagit.xml.Policy;
import org.dspace.pack.bagit.xml.Value;

/**
 * Operations for {@link ResourcePolicy} objects in BagIt bags
 *
 * Generates a {@link Policy} pojo for {@link DSpaceObject}s which can be easily serialized to XML
 *
 * @author mikejritter
 */
public class BagItPolicyUtil {

    // ResourcePolicy XML Attributes
    private static final String RP_NAME = "rp-name";
    private static final String RP_ACTION = "rp-action";
    private static final String RP_CONTEXT = "rp-context";
    private static final String RP_END_DATE = "rp-end-date";
    private static final String RP_START_DATE = "rp-start-date";
    private static final String RP_IN_EFFECT = "rp-in-effect";
    private static final String RP_DESCRIPTION = "rp-description";

    // from METSRightsCrosswalk, determine if a RP is for a custom group or eperson
    private static final String MANAGED_GROUP = "MANAGED GROUP";
    private static final String ACADEMIC_USER = "ACADEMIC USER";

    /**
     * Create a {@link Policy} for a {@link DSpaceObject}
     *
     * @param dso The {@link DSpaceObject} to get the {@link Policy} for
     * @return the {@link Policy}
     */
    public Policy getPolicy(Context context, DSpaceObject dso) {
        final Policy policy = new Policy();

        for (ResourcePolicy resourcePolicy : dso.getResourcePolicies()) {
            final Map<String, String> attributes = new HashMap<>();
            String username = null;

            // name and description
            attributes.put(RP_NAME, resourcePolicy.getRpName());
            attributes.put(RP_DESCRIPTION, resourcePolicy.getRpDescription());

            // in-effect = true by default, then needs checks on start + end date
            boolean inEffect = true;
            final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            final Date endDate = resourcePolicy.getEndDate();
            final Date startDate = resourcePolicy.getStartDate();
            final Date now = new Date();
            if (startDate != null) {
                attributes.put(RP_START_DATE, dateFormat.format(startDate));
                if (startDate.after(now)) {
                    inEffect = false;
                }
            }
            if (endDate != null) {
                attributes.put(RP_END_DATE, dateFormat.format(endDate));
                if (endDate.before(now)) {
                    inEffect = false;
                }
            }
            attributes.put(RP_IN_EFFECT, Boolean.toString(inEffect));

            // attributes for determining if adding policies on a group + what type of group or policies for a user
            final Group group = resourcePolicy.getGroup();
            final EPerson ePerson = resourcePolicy.getEPerson();
            if (group != null) {
                final String groupName = group.getName();
                if (groupName.equals(Group.ANONYMOUS)) {
                    attributes.put(RP_CONTEXT, Group.ANONYMOUS);
                    username = groupName;
                } else if (groupName.equals(Group.ADMIN)) {
                    attributes.put(RP_CONTEXT, Group.ADMIN);
                    username = groupName;
                } else {
                    attributes.put(RP_CONTEXT, MANAGED_GROUP);
                    try {
                        username = PackageUtils.translateGroupNameForExport(context, groupName);
                    } catch (PackageException ignored) {
                        // todo: throw an exception here
                    }
                }
            } else if (ePerson != null) {
                attributes.put(RP_CONTEXT, ACADEMIC_USER);
                username = ePerson.getEmail();
            } // todo: log warning if no group or user is on the policy

            final String action = identifyAction(resourcePolicy.getAction());
            attributes.put(RP_ACTION, action);

            policy.addChild(new Value(username, attributes));
        }

        return policy;
    }

    private String identifyAction(final int action) {
        switch (action) {
            case Constants.ADD: return "ADD";
            case Constants.READ: return "READ";
            case Constants.ADMIN: return "ADMIN";
            case Constants.WRITE: return "WRITE";
            case Constants.DELETE: return "DELETE";
            case Constants.REMOVE: return "REMOVE";
            case Constants.DEFAULT_ITEM_READ: return "READ_ITEM";
            case Constants.DEFAULT_BITSTREAM_READ: return "READ_BITSTREAM";
        }

        return null;
    }

    public Policy readXml(final InputStream inputStream) throws IOException {
        final XMLStreamReader reader;
        final XMLInputFactory factory = XMLInputFactory.newFactory();
        try {
            reader = factory.createXMLStreamReader(inputStream);
        } catch (XMLStreamException e) {
            throw new IOException(e.getMessage(), e);
        }

        final Policy policy = new Policy();
        // search for metadata stanza
        try {
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT &&
                    reader.getLocalName().equalsIgnoreCase(policy.getLocalName())) {

                    // search for value stanzas
                    while (reader.hasNext()) {
                        if (reader.next() == XMLStreamConstants.START_ELEMENT &&
                            reader.getLocalName().equalsIgnoreCase(Value.LOCAL_NAME)) {
                            Value value = readValue(reader);
                            if (value != null) {
                                policy.addChild(value);
                            }
                        }
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw new IOException(e.getMessage(), e);
        }

        return new Policy();
    }

    private Value readValue(XMLStreamReader reader) throws XMLStreamException {
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
