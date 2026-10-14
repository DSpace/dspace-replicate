/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.replicate;

import org.dspace.event.DetailType;
import org.dspace.event.Event;
import org.dspace.event.EventDetail;

/**
 * Helper methods shared by the replication {@link org.dspace.event.Consumer} implementations.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public final class ReplicateEventUtils {

    private ReplicateEventUtils() {
    }

    /**
     * Resolve the Handle carried by the given event. The Handle is looked up among all the details of
     * the event by {@link DetailType#HANDLE}, as an event may carry details of several types.
     *
     * @param event the event to inspect
     * @return the Handle carried by the event, or {@code null} if the event carries no Handle detail
     */
    public static String resolveHandle(Event event) {
        if (event == null || event.getDetailList() == null) {
            return null;
        }

        for (EventDetail detail : event.getDetailList()) {
            if (detail != null && DetailType.HANDLE == detail.getDetailType() && detail.getDetailObject() != null) {
                return detail.getDetailObject().toString();
            }
        }

        return null;
    }
}
