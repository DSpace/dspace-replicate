/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.replicate;

import java.sql.SQLException;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.Site;
import org.dspace.core.Context;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.PluginManager;
import org.dspace.curate.Curator;
import org.dspace.curate.TaskQueue;
import org.dspace.curate.TaskQueueEntry;
import org.dspace.eperson.EPerson;
import org.dspace.event.Consumer;
import org.dspace.event.Event;
import org.dspace.pack.Packer;
import org.dspace.pack.bagit.CatalogPacker;

// for readability
import static org.dspace.event.Event.*;

/**
 * METSReplicateConsumer is an event consumer that tracks events relevant to
 * replication synchronization when using METS AIPs. In response to deletions,
 * it creates and transmits a catalog of deleted objects (so they may be restored if
 * deletion was an error). For new or changed objects, it queues a request
 * to perform the configured curation tasks, or directly performs the task
 * if so indicated.
 * <P>
 * This replicate consumer performs the following special actions:
 * <ul>
 * <li>If a Group/Eperson is changed/added/removed, this is considered a modification of the SITE object
 * <li>When a child object is added/removed, this is also considered a modification of its parent object
 * <li>Similar to other ReplicateConsumers, it also just performs the configured tasks on any object
 * that is modified/added/removed
 * </ul>
 * <P>
 * This Consumer should be used with the settings similar to the following in your
 * dspace.cfg file:
 * <P>
 * # consumer to manage content replication (Replication Task Suite add-on)
 * event.consumer.replicate.class = org.dspace.ctask.replicate.METSReplicateConsumer
 * event.consumer.replicate.filters = Community|Collection|Item|Group|EPerson+All
 *
 * @author tdonohue
 * @author richardrodgers
 */
public class METSReplicateConsumer implements Consumer {

    private Logger log = Logger.getLogger(METSReplicateConsumer.class);

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
    private final String storeGroupName = ConfigurationManager.getProperty("replicate", "group.aip.name");
    // Group where object deletion catalog/records are stored
    private final String deleteGroupName = ConfigurationManager.getProperty("replicate", "group.delete.name");

    @Override
    public void initialize() throws Exception
    {
        repMan = ReplicaManager.instance();
        taskQueue = (TaskQueue)PluginManager.getSinglePlugin("curate", TaskQueue.class);
        queueName = localProperty("consumer.queue");
        // look for and load any idFilter files - excludes trump includes
        // An "idFilter" is an actual textual file named "exclude" or "include"
        // which contains a list of handles to filter from the Consumer
        if (! loadIdFilter("exclude"))
        {
            if (loadIdFilter("include"))
            {
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
     * @param ctx
     * @param event
     * @throws Exception
     */
    @Override
    public void consume(Context ctx, Event event) throws Exception
    {
        int evType = event.getEventType();
        int subjType = event.getSubjectType();
        //In this situation the "id" is actually the Object Handle
        String id = null;

        //Special processing specific to Group & EPerson events
        if(subjType==Constants.GROUP || subjType==Constants.EPERSON)
        {
            // ANY changes to a Group/EPerson are essentially modifications
            // to the DSpace System (Site), as they are site-wide changes
            id = Site.getSiteHandle();
            // make sure we are supposed to process this object
            if (acceptId(id, event, ctx))
            {
                // add it to the master lists of modified objects
                // for which we need to perform tasks
                mapId(taskQMap, modQTasks, id);
                mapId(taskPMap, modPTasks, id);
            }
        }
        else // process all other object types
        {
            switch (evType)
            {
                //ADD = Adding an object to a container or group
                case ADD:
                    //If mapping/adding an Item to a Collection
                    if(subjType==Constants.COLLECTION)
                    {
                        //First, get Handle of collection that was modified
                        id = event.getSubject(ctx).getHandle();

                        // make sure we are supposed to process this Collection
                        if (acceptId(id, event, ctx))
                        {
                            // add Collection to the master lists of modified objects
                            // for which we need to perform tasks
                            mapId(taskQMap, modQTasks, id);
                            mapId(taskPMap, modPTasks, id);

                            //now, get Handle of Item that was mapped/added
                            id = event.getDetail();

                            // add Item to the master lists of modified objects
                            // for which we need to perform tasks
                            mapId(taskQMap, modQTasks, id);
                            mapId(taskPMap, modPTasks, id);
                        }
                    }
                    //IGNORE all other "ADD" events. Currently it's not possible to map
                    //Collections or SubCommunities to multiple parents.
                    break;

                case CREATE: //CREATE = Create a new object.
                case INSTALL: //INSTALL = Install an object (exits workflow/workspace). Only used for Items.
                    // For CREATE & INSTALL, the Handle of object being created is found in Event Detail
                    id = event.getDetail();

                    // if NOT (Create & Item)
                    // (i.e. We don't want to replicate items UNTIL they are Installed)
                    if (!(subjType == Constants.ITEM && evType == CREATE))
                    {
                        if (acceptId(id, event, ctx))
                        {
                            // add it to the master lists of added/new objects
                            // for which we need to perform tasks
                            mapId(taskQMap, addQTasks, id);
                            mapId(taskPMap, addPTasks, id);
                        }

                        // get parent of this newly created object & mark it as modified
                        DSpaceObject parent = event.getSubject(ctx).getParentObject();
                        if(parent!=null)
                        {
                            id = parent.getHandle();
                            if(id != null)
                            {
                                if (acceptId(id, event, ctx))
                                {
                                    // add it to the master lists of modified objects
                                    // for which we need to perform tasks
                                    mapId(taskQMap, modQTasks, id);
                                    mapId(taskPMap, modPTasks, id);
                                }
                            }
                        }
                    }
                    break;

                case MODIFY: //MODIFY = modify an object
                case MODIFY_METADATA: //MODIFY_METADATA = just modify an object's metadata
                    // If subject of event is null, this means the object was likely deleted
                    if (event.getSubject(ctx)==null)
                    {
                        log.warn(event.getEventTypeAsString() + " event, could not get object for "
                                + event.getSubjectTypeAsString() + " id="
                                + String.valueOf(event.getSubjectID())
                                + ", perhaps it has been deleted.");
                        break;
                    }

                    //For MODIFY events, the Handle of modified object needs to be obtained from the Subject
                    id = event.getSubject(ctx).getHandle();

                    // make sure handle resolves - these could be events
                    // for a newly created item that hasn't been assigned a handle
                    if (id != null)
                    {
                        // make sure we are supposed to process this object
                        if (acceptId(id, event, ctx))
                        {
                            // add it to the master lists of modified objects
                            // for which we need to perform tasks
                            mapId(taskQMap, modQTasks, id);
                            mapId(taskPMap, modPTasks, id);
                        }
                    }
                    break;

                case REMOVE: //REMOVE = Remove an object from a container or group
                case DELETE: //DELETE = Delete an object (actually destroy it)
                    // For REMOVE & DELETE, the Handle of object being deleted is found in Event Detail
                    id = event.getDetail();

                    // make sure we are supposed to process this object
                    if (acceptId(id, event, ctx))
                    {   // analyze & process the deletion/removal event
                        deleteEvent(ctx, id, event);
                    }

                    break;
                default:
                    break;
            }//end switch
        }//end if
    }

    @Override
    public void end(Context ctx) throws Exception
    {
        // if there are any pending objectIds, pass them to the curation
        // system to queue for later processing, or perform immediately
        EPerson ep = ctx.getCurrentUser();
        String name = (ep != null) ? ep.getName() : "unknown";
        long stamp = System.currentTimeMillis();
        // first the queueables
        Set<TaskQueueEntry> entrySet = new HashSet<TaskQueueEntry>();
        if (taskQMap.size() > 0)
        {
            List<String> taskList = new ArrayList<String>();
            for (String task : taskQMap.keySet())
            {
                taskList.add(task);
                for (String id : taskQMap.get(task))
                {
                    entrySet.add(new TaskQueueEntry(name, stamp, taskList, id));
                }
                taskList.clear();
            }
            taskQMap.clear();
        }
        // now the performables
        if (taskPMap.size() > 0)
        {
            Curator curator = new Curator();
            for (String task : taskPMap.keySet())
            {
                curator.addTask(task);
                for (String id : taskQMap.get(task))
                {
                    curator.curate(ctx, id);
                }
                curator.clear();
            }
            taskPMap.clear();
        }

        // if there any uncommitted deletions, record them now
        if (delObjId != null)
        {
            if (delTasks != null)
            {
                entrySet.add(new TaskQueueEntry(name, stamp, delTasks, delObjId));
            }
            processDelete();
        }
        if (entrySet.size() > 0)
        {
            taskQueue.enqueue(queueName, entrySet);
        }
    }

    @Override
    public void finish(Context ctx) throws Exception
    {
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
    private boolean acceptId(String id, Event event, Context ctx) throws SQLException
    {
        // always accept if not filtering
        if (idFilter == null)
        {
            return true;
        }
        // filter supports only container ids - so if id is for an item,
        // find its owning collection
        String id2check = id;
        if (event.getSubjectType() == Constants.ITEM)
        {
            // NB: Item should be available form context cache - should
            // not incur a performance hit here
            Item item = Item.find(ctx, event.getSubjectID());
            Collection coll = item.getOwningCollection();
            if (coll != null)
            {
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
    private void deleteEvent(Context ctx, String id, Event event) throws Exception
    {
        int type = event.getEventType();
        if (DELETE == type)
        {
            // either marks start of new deletion or a member of enclosing one
            if (delObjId == null)
            {
                //Start of a new deletion
                delObjId = id;
            }
            else
            {
                // just add to list of deleted members
                delMemIds.add(id);
            }
        }
        else if (REMOVE == type)
        {
            // either marks end of current deletion or is member of
            // enclosing one: ignore if latter
            if (delObjId.equals(id))
            {
                // determine owner and write out deletion catalog
                if (Constants.COLLECTION == event.getSubjectType())
                {
                    // my owner is a collection
                    Collection ownColl = Collection.find(ctx, event.getSubjectID());
                    delOwnerId = ownColl.getHandle();
                }
                else if (Constants.COMMUNITY == event.getSubjectType())
                {
                    // my owner is a community
                    Community comm = Community.find(ctx, event.getSubjectID());
                    delOwnerId = comm.getHandle();
                }

                // If the parent/owner was found, mark that parent as having been modified
                // (This ensures that a fresh AIP will be generated for the parent object)
                if(delOwnerId != null)
                {
                    if (acceptId(delOwnerId, event, ctx))
                    {
                        // add parent to the master lists of modified objects
                        // for which we need to perform tasks
                        mapId(taskQMap, modQTasks, delOwnerId);
                        mapId(taskPMap, modPTasks, delOwnerId);
                    }
                }

                //Record the deletion catalog for the deleted object (as needed)
                processDelete();
             }
        }
    }

    /*
     * Process a deletion event by recording a deletion catalog if configured
     */
    private void processDelete() throws IOException
    {
        // write out deletion catalog if defined
        if (catalogDeletes)
        {
            //First, check if this object has an AIP in storage
            boolean found = repMan.objectExists(storeGroupName, delObjId);

            // If the object has an AIP, then create a deletion catalog
            // If there's no AIP, then there's no need for a deletion
            // catalog as the object isn't backed up & cannot be restored!
            if(found)
            {
                //Create a deletion catalog (in BagIt format) of all deleted objects
                Packer packer = new CatalogPacker(delObjId, delOwnerId, delMemIds);
                try
                {
                    // Create a new deletion catalog (with default file extension / format)
                    // and store it in the deletion group store
                    String catID = repMan.deletionCatalogId(delObjId, null);
                    File packDir = repMan.stage(deleteGroupName, catID);
                    File archive = packer.pack(packDir);
                    // Create a deletion catalog in deletion archive location.
                    repMan.transferObject(deleteGroupName, archive);
                }
                catch (AuthorizeException authE)
                {
                    throw new IOException(authE);
                }
                catch (SQLException sqlE)
                {
                    throw new IOException(sqlE);
                }
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
    private boolean loadIdFilter(String filterName)
    {
        File filterFile = new File(localProperty("base.dir"), filterName);
        if (filterFile.exists())
        {
            idFilter = new ArrayList<String>();
            BufferedReader reader = null;
            try
            {
                reader = new BufferedReader(new FileReader(filterFile));
                String id = null;
                while((id = reader.readLine()) != null)
                {
                    idFilter.add(id);
                }
                return true;
            }
            catch (IOException ioE)
            {
                //log.error("Unable to read filter file '" + filterName + "'");
                idFilter = null;
            }
            finally
            {
                if (reader != null) {
                    try
                    {
                        reader.close();
                    }
                    catch (IOException ioE)
                    {
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
    private void mapId(Map<String, Set<String>> map, List<String> tasks, String id)
    {
        if (tasks != null)
        {
            for (String task : tasks)
            {
                Set<String> ids = map.get(task);
                if (ids == null)
                {
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
    private void parseTasks(String propName)
    {
        String taskStr = localProperty("consumer.tasks." + propName);
        if (taskStr == null || taskStr.length() == 0)
        {
            return;
        }
        for (String task : taskStr.split(","))
        {
            task = task.trim();
            //If the task in question does NOT end in "+p",
            // then it should be queued for later processing
            if (! task.endsWith("+p"))
            {
                if ("add".equals(propName))
                {
                    if (addQTasks == null)
                    {
                        addQTasks = new ArrayList<String>();
                    }
                    addQTasks.add(task);
                }
                else if ("mod".equals(propName))
                {
                    if (modQTasks == null)
                    {
                        modQTasks = new ArrayList<String>();
                    }
                    modQTasks.add(task);   
                }
                else if ("del".equals(propName))
                {
                    if (delTasks == null)
                    {
                        delTasks = new ArrayList<String>();
                    }
                    delTasks.add(task);   
                }
            }
            //Otherwise (if the task ends in "+p"),
            //  it should be added to the list of tasks to perform immediately
            else 
            {
                String sTask = task.substring(0, task.lastIndexOf("+p"));
                if ("add".equals(propName))
                {
                    if (addPTasks == null)
                    {
                        addPTasks = new ArrayList<String>();
                    }
                    addPTasks.add(sTask);
                }
                else if ("mod".equals(propName))
                {
                    if (modPTasks == null)
                    {
                        modPTasks = new ArrayList<String>();
                    }
                    addPTasks.add(sTask);
                }
                else if ("del".equals(propName))
                {
                    // just test for special case of deletion catalogs.
                    if ("catalog".equals(sTask))
                    {
                        catalogDeletes = true;
                    } 
                }
            }
        }
    }

    /**
     * Load a single property value from the "replicate.cfg" configuration file
     * @param propName property name
     * @return property value
     */
    private String localProperty(String propName)
    {
        return ConfigurationManager.getProperty("replicate", propName);
    }

}
