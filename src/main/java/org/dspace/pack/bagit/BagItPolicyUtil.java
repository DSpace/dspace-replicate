package org.dspace.pack.bagit;

import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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
 * @author mikejritter
 */
public class BagItPolicyUtil {

    /**
     * Create a {@link Policy} for a {@link DSpaceObject}
     *
     * @param dso
     * @return
     */
    public Policy getPolicy(Context context, DSpaceObject dso) throws SQLException {
        final Policy policy = new Policy();

        for (ResourcePolicy resourcePolicy : dso.getResourcePolicies()) {
            final Map<String, String> attributes = new HashMap<>();
            String username = null;

            // name and description
            attributes.put("rpName", resourcePolicy.getRpName());
            attributes.put("rpDescription", resourcePolicy.getRpDescription());

            // in-effect = true by default, then needs checks on start + end date
            boolean inEffect = true;
            final DateFormat dateFormat = new SimpleDateFormat("YYYY-MM-dd");
            final Date endDate = resourcePolicy.getEndDate();
            final Date startDate = resourcePolicy.getStartDate();
            final Date now = new Date();
            if (startDate != null) {
                attributes.put("start-date", dateFormat.format(startDate));
                if (startDate.after(now)) {
                    inEffect = false;
                }
            }
            if (endDate != null) {
                attributes.put("end-date", dateFormat.format(startDate));
                if (endDate.before(now)) {
                    inEffect = false;
                }
            }
            attributes.put("in-effect", Boolean.toString(inEffect));

            // attributes for determining if adding policies on a group + what type of group or policies for a user
            final Group group = resourcePolicy.getGroup();
            final EPerson ePerson = resourcePolicy.getEPerson();
            if (group != null) {
                final String groupName = group.getName();
                if (groupName.equals(Group.ANONYMOUS)) {
                    attributes.put("context", Group.ANONYMOUS);
                    username = groupName;
                } else if (groupName.equals(Group.ADMIN)) {
                    attributes.put("context", Group.ADMIN);
                    username = groupName;
                } else {
                    attributes.put("context", "Managed");
                    try {
                        final String exportName = PackageUtils.translateGroupNameForExport(context, groupName);
                        username = exportName;
                    } catch (PackageException ignored) {
                        // todo: throw an exception here
                    }
                }
            } else if (ePerson != null) {
                attributes.put("context", "Individual");
                username = ePerson.getEmail();
            } // todo: log warning if no group or user is on the policy

            final String action = identifyAction(resourcePolicy.getAction());
            attributes.put("action", action);

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

}
