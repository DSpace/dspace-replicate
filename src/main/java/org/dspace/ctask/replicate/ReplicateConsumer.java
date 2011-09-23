/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://dspace.org/license/
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

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
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
 * ReplicateConsumer is an event consumer that tracks events relevant to
 * replication synchronization. In response to deletions, it creates and
 * transmits a catalog of deleted objects (so they may be restored if 
 * deletion was an error). For new or changed objects, it queues a request
 * to perform the configured curation tasks, or directly performs the task
 * if so indicated.
 * 
 * @author richardrodgers
 */
public class ReplicateConsumer implements Consumer {

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
    // Group/catalog where all AIPs are temporarily moved when deleted
    private final String deleteGroupName = ConfigurationManager.getProperty("replicate", "group.delete.name");

    @Override
    public void initialize() throws Exception
    {
        repMan = ReplicaManager.instance();
        taskQueue = (TaskQueue)PluginManager.getSinglePlugin("curate", TaskQueue.class);
        queueName = localProperty("consumer.queue");
        // look for and load any idFilter files - excludes trump includes
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
        String id = event.getDetail();
        System.out.println("got event type: " + evType + " for subject type: " + subjType);
        switch (evType)
        {
            case CREATE:
            case INSTALL:
                if (subjType != Constants.ITEM || evType != CREATE)
                {
                    if (acceptId(id, event, ctx))
                    {
                        mapId(taskQMap, addQTasks, id);
                        mapId(taskPMap, addPTasks, id);
                    }
                }
                break;
            case MODIFY:
            case MODIFY_METADATA:
                id = event.getSubject(ctx).getHandle();
                // make sure handle resolves - these could be events
                // for a newly created item that hasn't been assigned a handle
                if (id != null)
                {
                    if (acceptId(id, event, ctx))
                    {
                        mapId(taskQMap, modQTasks, id);
                        mapId(taskPMap, modPTasks, id);
                    }
                }
                break;
            case REMOVE:
            case DELETE:
                // analyze event
                if (acceptId(id, event, ctx)) deleteEvent(ctx, id, event);
                break;
            default:
                break;
        }
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

    private void deleteEvent(Context ctx, String id, Event event) throws Exception
    {
        int type = event.getEventType();
        if (DELETE == type)
        {
            // either marks start of new deletion or a member of enclosing one
            if (delObjId == null)
            {
                //System.out.println("ReplicateConsumer assigning id");
                delObjId = id;
            }
            else
            {
                // just add to list of deleted members
                //System.out.println("ReplicateConsumer adding id to list");
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
                processDelete();
             }
        }
    }

    private void processDelete() throws IOException
    {
        // write out deletion catalog if defined
        if (catalogDeletes)
        {
            Packer packer = new CatalogPacker(delObjId, delOwnerId, delMemIds);
            try
            {
                File packDir = repMan.stage(deleteGroupName, delObjId);
                File archive = packer.pack(packDir);
                //System.out.println("delcat about to transfer");
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
        // reset for next events
        delObjId = delOwnerId = null;
        delMemIds.clear();
    }
    
    private boolean loadIdFilter(String filterName)
    {
        //String baseDir = localProperty("base.dir");
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
    
    private String localProperty(String propName)
    {
        return ConfigurationManager.getProperty("replicate", propName);
    }

}
