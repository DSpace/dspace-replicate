/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.replicate;

import static org.dspace.event.Event.CREATE;
import static org.dspace.event.Event.DELETE;
import static org.dspace.event.Event.INSTALL;
import static org.dspace.event.Event.MODIFY;
import static org.dspace.event.Event.MODIFY_METADATA;
import static org.dspace.event.Event.REMOVE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.CommunityService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.factory.CoreServiceFactory;
import org.dspace.core.service.PluginService;
import org.dspace.curate.Curator;
import org.dspace.curate.TaskQueue;
import org.dspace.curate.TaskQueueEntry;
import org.dspace.eperson.EPerson;
import org.dspace.event.Consumer;
import org.dspace.event.Event;
import org.dspace.pack.Packer;
import org.dspace.pack.bagit.CatalogPacker;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;

/**
 * BagItReplicateConsumer is an event consumer that tracks events relevant to
 * replication synchronization. In response to deletions, it creates and
 * transmits a catalog of deleted objects (so they may be restored if
 * deletion was an error). For new or changed objects, it queues a request
 * to perform the configured curation tasks, or directly performs the task
 * if so indicated.
 *
 * @author richardrodgers
 */
public class BagItReplicateConsumer implements Consumer {

    private Logger log = LogManager.getLogger();

    private ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
    private PluginService pluginService = CoreServiceFactory.getInstance().getPluginService();
    private CommunityService communityService = ContentServiceFactory.getInstance().getCommunityService();
    private CollectionService collectionService = ContentServiceFactory.getInstance().getCollectionService();
    private ItemService itemService = ContentServiceFactory.getInstance().getItemService();

    private ReplicaManager repMan = null;
    private TaskQueue taskQueue = null;
    private String queueName = null;
    // list and sense for id filtering
    private List<String> idFilter = null;
    private boolean idExclude = true;
    // map of task names to id sets
    private Map<String, Set<String>> taskQMap = null;
    private Map<String, Set<String>> taskPMap = null;
    private String delObjId = null;
    private String delOwnerId = null;
    private List<String> delMemIds = null;
    // tasks to queue upon add events
    private List<String> addQTasks = null;
    // tasks to perform immediately upon add events
    private List<String> addPTasks = null;
    // tasks to queue upon modify events
    private List<String> modQTasks = null;
    // tasks to perform immediately upon modify events
    private List<String> modPTasks = null;
    // tasks to queue upon delete events
    private List<String> delTasks = null;
    // create deletion catalogs?
    private boolean catalogDeletes = false;
    // Group where all AIPs are stored
    private final String storeGroupName = configurationService.getProperty("replicate.group.aip.name");
    // Group where object deletion catalog/records are stored
    private final String deleteGroupName = configurationService.getProperty("replicate.group.delete.name");
    private final String archFmt = configurationService.getProperty("replicate.packer.archfmt");

    @Override
    public void initialize() throws Exception {
        try {
            repMan = ReplicaManager.instance();
        } catch (IOException ioE) {
            // The ReplicaManager attempts to initialize the ObjectStore specified in the configuration.
            log.error("Unable to initialize the ReplicaManager. ", ioE);
        }

        taskQueue = (TaskQueue) pluginService.getSinglePlugin(TaskQueue.class);
        queueName = configurationService.getProperty("replicate.consumer.queue");

        // look for and load any idFilter files - excludes trump includes
        // An "idFilter" is an actual textual file named "exclude" or "include"
        // which contains a list of handles to filter from the Consumer
        if (!loadIdFilter("exclude")) {
            if (loadIdFilter("include")) {
                idExclude = false;
            }
        }

        taskQMap = new HashMap<String, Set<String>>();
        taskPMap = new HashMap<String, Set<String>>();

        parseTasks("add");
        parseTasks("mod");

        delMemIds = new ArrayList<String>();
        parseTasks("del");
    }

    /**
     * Consume a content event. At a high level, 2 sorts of actions are
     * performed: first, for all new or modified objects, the object handle
     * is added to a set of objects to be processed. When a Curator batch
     * next runs, this list will be read and whatever tasks are configured to
     * be performed will be. Typically, a new AIP will be generated and
     * uploaded to the replication service. Second, for deletions, the event
     * stream is parsed to construct a 'delete catalog' containing an enumeration
     * of the objects that are being deleted. This also is uploaded to the
     * replication service, and can be used either to recover from mistaken
     * deletions, or purge the replica store when desired.
     *
     * @param ctx Context
     * @param event Event
     * @throws Exception if error
     */
    @Override
    public void consume(Context ctx, Event event) throws Exception {
        int evType = event.getEventType();
        int subjType = event.getSubjectType();

        // This is the Handle of the object on which an event occured
        String id = event.getDetail();

        // System.out.println("got event type: " + evType + " for subject type: " + subjType);
        switch (evType) {
            case CREATE: //CREATE = Create a new object.
            case INSTALL: //INSTALL = Install an object (exits workflow/workspace). Only used for Items.
                // if NOT (Item & Create)
                // (i.e. We don't want to replicate items UNTIL they are Installed)
                if (subjType != Constants.ITEM || evType != CREATE) {
                    if (acceptId(id, event, ctx)) {
                        // add it to the master lists of added/new objects
                        // for which we need to perform tasks
                        mapId(taskQMap, addQTasks, id);
                        mapId(taskPMap, addPTasks, id);
                    }
                }
                break;
            case MODIFY: // MODIFY = modify an object
            case MODIFY_METADATA: // MODIFY_METADATA = just modify an object's metadata
                // If subject of event is null, this means the object was likely deleted
                if (event.getSubject(ctx) == null) {
                    log.warn(event.getEventTypeAsString() + " event, could not get object for "
                            + event.getSubjectTypeAsString() + " id="
                            + String.valueOf(event.getSubjectID())
                            + ", perhaps it has been deleted.");
                    break;
                }

                // For MODIFY events, the Handle of modified object needs to be obtained from the Subject
                id = event.getSubject(ctx).getHandle();
                // make sure handle resolves - these could be events
                // for a newly created item that hasn't been assigned a handle
                if (id != null) {
                    // make sure we are supposed to process this object
                    if (acceptId(id, event, ctx)) {
                        // add it to the master lists of modified objects
                        // for which we need to perform tasks
                        mapId(taskQMap, modQTasks, id);
                        mapId(taskPMap, modPTasks, id);
                    }
                }
                break;
            case REMOVE: // REMOVE = Remove an object from a container or group
            case DELETE: // DELETE = Delete an object (actually destroy it)
                // make sure we are supposed to process this object
                if (acceptId(id, event, ctx)) {   // analyze & process the deletion/removal event
                    deleteEvent(ctx, id, event);
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void end(Context ctx) throws Exception {
        // if there are any pending objectIds, pass them to the curation
        // system to queue for later processing, or perform immediately
        EPerson ep = ctx.getCurrentUser();
        String name = (ep != null) ? ep.getName() : "unknown";
        long stamp = System.currentTimeMillis();

        // first the queueables
        Set<TaskQueueEntry> entrySet = new HashSet<TaskQueueEntry>();
        if (!taskQMap.isEmpty()) {
            List<String> taskList = new ArrayList<String>();
            for (String task : taskQMap.keySet()) {
                taskList.add(task);
                for (String id : taskQMap.get(task)) {
                    entrySet.add(new TaskQueueEntry(name, stamp, taskList, id));
                }
                taskList.clear();
            }
            taskQMap.clear();
        }

        // now the performables
        if (!taskPMap.isEmpty()) {
            Curator curator = new Curator();
            for (String task : taskPMap.keySet()) {
                curator.addTask(task);
                for (String id : taskQMap.get(task)) {
                    curator.curate(ctx, id);
                }
                curator.clear();
            }
            taskPMap.clear();
        }

        // if there any uncommitted deletions, record them now
        if (delObjId != null) {
            if (delTasks != null) {
                entrySet.add(new TaskQueueEntry(name, stamp, delTasks, delObjId));
            }
            processDelete(ctx);
        }

        if (!entrySet.isEmpty()) {
            taskQueue.enqueue(queueName, entrySet);
        }
    }

    @Override
    public void finish(Context ctx) throws Exception {
        // no-op
    }

    /**
     * Check to see if an object ID (Handle) is allowed to be processed by
     * this consumer. Individual Objects may be filtered out of consumer
     * processing by using a filter file (a textual file with a list of
     * handles to either include or exclude).
     *
     * @param id Object ID to check
     * @param event Event that was performed on the Object
     * @param ctx Current DSpace Context
     * @return true if this consumer should process this object event, false if it should not
     * @throws SQLException if database error occurs
     */
    private boolean acceptId(String id, Event event, Context ctx) throws SQLException {
        // always accept if not filtering
        if (idFilter == null) {
            return true;
        }

        // filter supports only container ids - so if id is for an item,
        // find its owning collection
        String id2check = id;
        if (event.getSubjectType() == Constants.ITEM) {
            // NB: Item should be available form context cache - should
            // not incur a performance hit here
            Item item = itemService.find(ctx, event.getSubjectID());
            Collection coll = item.getOwningCollection();
            if (coll != null) {
                id2check = coll.getHandle();
            }
        }

        boolean onList = idFilter.contains(id2check);
        return idExclude ? ! onList : onList;
    }

    /**
     * Process a DELETE (destroy object) or REMOVE (remove object from container) event.
     * For a DELETE, record all objects that were deleted (parent & possible child objects)
     * For a REMOVE, if this was preceded by deletion of a parent, record a deletion catalog
     * @param ctx current DSpace Context
     * @param id Object on which the delete/remove event was triggered
     * @param event event that was triggered
     * @throws Exception
     */
    private void deleteEvent(Context ctx, String id, Event event) throws Exception {
        int type = event.getEventType();
        if (DELETE == type) {
            // either marks start of new deletion or a member of enclosing one
            if (delObjId == null) {
                //Start of a new deletion
                delObjId = id;
            } else {
                // just add to list of deleted members
                delMemIds.add(id);
            }
        } else if (REMOVE == type) {
            // either marks end of current deletion or is member of
            // enclosing one: ignore if latter
            if (delObjId.equals(id)) {
                // determine owner and write out deletion catalog
                if (Constants.COLLECTION == event.getSubjectType()) {
                    // my owner is a collection
                    Collection ownColl = collectionService.find(ctx, event.getSubjectID());
                    delOwnerId = ownColl.getHandle();
                } else if (Constants.COMMUNITY == event.getSubjectType()) {
                    // my owner is a community
                    Community comm = communityService.find(ctx, event.getSubjectID());
                    delOwnerId = comm.getHandle();
                }

                processDelete(ctx);
            }
        }
    }

    /*
     * Process a deletion event by recording a deletion catalog if configured
     */
    private void processDelete(Context context) throws IOException {
        if (repMan == null) {
            log.error("The ReplicaManager failed to initialize earlier. Check the logs above.");
            return;
        }

        // write out deletion catalog if defined
        if (catalogDeletes) {
            // First, check if this object has an AIP in storage
            try {
                final String storageId = repMan.storageId(context, delObjId, archFmt);
                boolean found = repMan.objectExists(storeGroupName, storageId);

                // If the object has an AIP, then create a deletion catalog
                // If there's no AIP, then there's no need for a deletion
                // catalog as the object isn't backed up & cannot be restored!
                if (found) {
                    Packer packer = new CatalogPacker(context, delObjId, delOwnerId, delMemIds);
                    // Create a new deletion catalog (with default file extension / format)
                    // and store it in the deletion group store
                    String catID = repMan.deletionCatalogId(delObjId, null);
                    File packDir = repMan.stage(context, deleteGroupName, catID);
                    File archive = packer.pack(packDir);
                    // System.out.println("delcat about to transfer");
                    repMan.transferObject(deleteGroupName, archive);
                }
            } catch (AuthorizeException | SQLException e) {
                throw new IOException(e);
            }
        }
        // reset for next events
        delObjId = delOwnerId = null;
        delMemIds.clear();
    }

    /**
     * Load the ID filter file of the given name.  This is a textual file in
     * the base directory which contains a list of handles to include/exclude
     * from this consumer
     * @param filterName the name of the textual filter file
     * @return true if filter file was loaded successfully, false otherwise
     */
    private boolean loadIdFilter(String filterName) {
        File filterFile = new File(configurationService.getProperty("replicate.base.dir"), filterName);
        if (filterFile.exists()) {
            idFilter = new ArrayList<String>();
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(filterFile));
                String id = null;
                while ((id = reader.readLine()) != null) {
                    idFilter.add(id);
                }
                return true;
            } catch (IOException ioE) {
                // log.error("Unable to read filter file '" + filterName + "'");
                idFilter = null;
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException ioE) {
                        // ignore exception
                    }
                }
            }
        }
        return false;
    }

    /**
     * Record the given object tasklist in the given "map".  This is essentially
     * providing a master list (map) of tasks to perform for particular objects.
     * NOTE: if this object and task already exist in the master list, it will
     * NOT be duplicated.
     * @param map Master task list to add to (String task, Set<String> ids)
     * @param tasks Tasks to be performed
     * @param id Object for which the tasks should be performed.
     */
    private void mapId(Map<String, Set<String>> map, List<String> tasks, String id) {
        if (tasks != null) {
            for (String task : tasks) {
                Set<String> ids = map.get(task);
                if (ids == null) {
                    ids = new HashSet<String>();
                    map.put(task, ids);
                }
                ids.add(id);
            }
        }
    }

    /**
     * Parse the list of Consumer tasks to perform.  This list of tasks
     * is in the 'replicate.cfg' file.
     * @param propName property name
     */
    private void parseTasks(String propName) {
        String taskStr = configurationService.getProperty("replicate.consumer.tasks." + propName);
        if (taskStr == null || taskStr.isEmpty()) {
            return;
        }
        for (String task : taskStr.split(",")) {
            task = task.trim();
            // If the task in question does NOT end in "+p",
            // then it should be queued for later processing
            if (! task.endsWith("+p")) {
                if ("add".equals(propName)) {
                    if (addQTasks == null) {
                        addQTasks = new ArrayList<String>();
                    }
                    addQTasks.add(task);
                } else if ("mod".equals(propName)) {
                    if (modQTasks == null) {
                        modQTasks = new ArrayList<String>();
                    }
                    modQTasks.add(task);
                } else if ("del".equals(propName)) {
                    if (delTasks == null) {
                        delTasks = new ArrayList<String>();
                    }
                    delTasks.add(task);
                }
            } else {
                // Otherwise (if the task ends in "+p"),
                //  it should be added to the list of tasks to perform immediately

                String sTask = task.substring(0, task.lastIndexOf("+p"));
                if ("add".equals(propName)) {
                    if (addPTasks == null) {
                        addPTasks = new ArrayList<String>();
                    }
                    addPTasks.add(sTask);
                } else if ("mod".equals(propName)) {
                    if (modPTasks == null) {
                        modPTasks = new ArrayList<String>();
                    }
                    addPTasks.add(sTask);
                } else if ("del".equals(propName)) {
                    // just test for special case of deletion catalogs.
                    if ("catalog".equals(sTask)) {
                        catalogDeletes = true;
                    }
                }
            }
        }
    }
}
