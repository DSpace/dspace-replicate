/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.replicate;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.curate.FileTaskQueue;
import org.dspace.curate.TaskQueueEntry;

/**
 * An extension of the default FileTaskQueue which ensures that duplicate entries
 * in the queue are filtered out during the "dequeue()" process.
 * <P>
 * In other words, if multiple entries in the queue include the exact same
 * task listing and the exact same object, only the first entry will be processed.
 * <P>
 * For example, assuming a FileTaskQueue with the following contents:<br>
 * user1@myu.edu|123456789|transmitaip|10673/0 <br>
 * user1@myu.edu|123456790|transmitaip|10673/1 <br>
 * user2@myu.edu|123456791|transmitaip|10673/0 <br>
 * user1@myu.edu|123456792|transmitaip|10673/0 <br>
 * Only the first TWO entries will be returned by "dequeue()", as entries #3
 * and #4 would be considered duplicates of entry #1.
 * <P>
 * This FilteredFileTaskQueue is extremely useful to Replication Tasks, as it
 * ensures that AIPs are not (re-)generated multiple times when the queue
 * is actually processed. (NOTE however that some of the Replication store plugins
 * avoid duplicate transfers by ensuring Checksums differ before transferring)
 *
 * @author Tim Donohue
 */
public class FilteredFileTaskQueue extends FileTaskQueue {
    private static Logger log = LogManager.getLogger();

    /**
     * Returns the set of UNIQUE task entries from the named queue. Any duplicate
     * task entries in the queue are ignored. The operation locks
     * the queue from any further enqueue or dequeue operations until a
     * <code>release</code> is called. The ticket may be any number, but a
     * timestamp should guarantee sufficient uniqueness.
     *
     * @param queueName
     *        the name of the queue to read
     * @param ticket
     *        a token which must be presented to release the queue
     * @return set
     *        the current set of queued unique task entries
     * @throws IOException if I/O error
     */
    @Override
    public synchronized Set<TaskQueueEntry> dequeue(String queueName, long ticket)
           throws IOException {
        // Dequeue our list of tasks (which may include duplicates)
        Set<TaskQueueEntry> entrySet = super.dequeue(queueName, ticket);

        // Filter out any duplicate entries in the task list
        filterDuplicates(entrySet);

        return entrySet;
    }

    /**
     * Filter out any duplicate entries in a set of TaskQueueEntry objects.
     * A duplicate entry is one that references the same object and the same task(s)
     * @param entries initial set of entries
     */
    private void filterDuplicates(Set<TaskQueueEntry> entries) {
        // create filteredSet as a LinkedHashSet in order to maintain existing order of queue
        Set<UniqueTaskQueueEntry> filteredSet = new LinkedHashSet<UniqueTaskQueueEntry>();

        // Add all our TaskQueueEntries to our "filteredSet".
        // This will filter out any duplicates automatically
        // (see UniqueTaskQueueEntry.equals() below).
        Iterator<TaskQueueEntry> entryIter = entries.iterator();
        while (entryIter.hasNext()) {
            filteredSet.add(new UniqueTaskQueueEntry(entryIter.next()));
        }

        // Now overwrite our initial entry set with the filtered list of entries
        entries.clear();
        Iterator<UniqueTaskQueueEntry> filterIter = filteredSet.iterator();
        while (filterIter.hasNext()) {
            entries.add(filterIter.next().getTaskQueueEntry());
        }
    }


    /**
     * An object that represents a unique TaskQueueEntry
     * @TODO This is not really ideal, as it'd be better to just *extend*
     * TaskQueueEntry (not allowed, as it's "final"), or have a similar
     * TaskQueueEntry.equals() method.
     */
    private class UniqueTaskQueueEntry {
        private final TaskQueueEntry entry;
        private final String tasks;
        private final String objId;

        public UniqueTaskQueueEntry(TaskQueueEntry entry) {
            this.entry = entry;
            List<String> taskNames = entry.getTaskNames();
            StringBuilder sb = new StringBuilder();
            for (String tName : taskNames) {
                sb.append(tName).append(",");
            }
            this.tasks = sb.substring(0, sb.length() - 1);
            this.objId = entry.getObjectId();
        }

        /**
         * Get the TaskQueueEntry object that was used to generate the TaskQueueEntryFilter
         * @return TaskQueueEntry object
         */
        public TaskQueueEntry getTaskQueueEntry() {
            return entry;
        }

        /**
        * Return true if this object equals obj, false otherwise.
        *
        * @param obj object to check against
        * @return true if TaskQueueEntryFilter objects are equal
        */
        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }

            if (getClass() != obj.getClass()) {
                return false;
            }

            final UniqueTaskQueueEntry other = (UniqueTaskQueueEntry) obj;
            // two task entries are considered "equal" if they refer to the
            // same object and same list of tasks
            if (this.tasks.equalsIgnoreCase(other.tasks) &&
                this.objId.equalsIgnoreCase(other.objId)) {
                return true;
            }

            return false;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 59 * hash + (this.tasks != null ? this.tasks.hashCode() : 0);
            hash = 59 * hash + (this.objId != null ? this.objId.hashCode() : 0);
            return hash;
        }
    }
}
