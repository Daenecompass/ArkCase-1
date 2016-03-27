package com.armedia.acm.plugins.task.service.impl;


import com.armedia.acm.activiti.AcmTaskEvent;
import com.armedia.acm.core.exceptions.AcmCreateObjectFailedException;
import com.armedia.acm.data.AuditPropertyEntityAdapter;
import com.armedia.acm.plugins.ecm.dao.AcmContainerDao;
import com.armedia.acm.plugins.ecm.dao.EcmFileDao;
import com.armedia.acm.plugins.ecm.model.AcmContainer;
import com.armedia.acm.plugins.ecm.model.AcmFolder;
import com.armedia.acm.plugins.ecm.model.EcmFile;
import com.armedia.acm.plugins.ecm.model.EcmFileConstants;
import com.armedia.acm.plugins.ecm.service.EcmFileService;
import com.armedia.acm.plugins.task.exception.AcmTaskException;
import com.armedia.acm.plugins.task.model.AcmApplicationTaskEvent;
import com.armedia.acm.plugins.task.model.AcmTask;
import com.armedia.acm.plugins.task.model.NumberOfDays;
import com.armedia.acm.plugins.task.model.TaskConstants;
import com.armedia.acm.plugins.task.model.TaskOutcome;
import com.armedia.acm.plugins.task.model.WorkflowHistoryInstance;
import com.armedia.acm.plugins.task.service.TaskDao;
import com.armedia.acm.plugins.task.service.TaskEventPublisher;
import com.armedia.acm.services.dataaccess.service.impl.DataAccessPrivilegeListener;
import com.armedia.acm.services.participants.dao.AcmParticipantDao;
import com.armedia.acm.services.participants.model.AcmParticipant;
import com.armedia.acm.services.participants.model.ParticipantTypes;
import com.armedia.acm.services.users.dao.ldap.UserDao;
import com.armedia.acm.services.users.model.AcmUser;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.FlowElement;
import org.activiti.bpmn.model.FormProperty;
import org.activiti.bpmn.model.FormValue;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.ActivitiException;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.history.HistoricTaskInstanceQuery;
import org.activiti.engine.history.HistoricVariableInstance;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.IdentityLink;
import org.activiti.engine.task.Task;
import org.apache.commons.lang.WordUtils;
import org.apache.commons.lang.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class ActivitiTaskDao implements TaskDao
{
    private RuntimeService activitiRuntimeService;
    private TaskService activitiTaskService;
    private RepositoryService activitiRepositoryService;
    private Logger log = LoggerFactory.getLogger(getClass());
    private HistoryService activitiHistoryService;
    private Map<String, Integer> priorityLevelToNumberMap;
    private Map<String, List<String>> requiredFieldsPerOutcomeMap;
    private UserDao userDao;
    private AcmParticipantDao participantDao;
    private DataAccessPrivilegeListener dataAccessPrivilegeListener;
    private ExtractAcmTaskFromEvent taskExtractor;
    private TaskBusinessRule taskBusinessRule;
    private EcmFileService fileService;
    private EcmFileDao fileDao;
    private AcmContainerDao containerFolderDao;
    private AuditPropertyEntityAdapter auditPropertyEntityAdapter;
    private TaskEventPublisher taskEventPublisher;

    @Override
    @Transactional
    public AcmTask createAdHocTask(AcmTask in) throws AcmTaskException
    {
        Task activitiTask = getActivitiTaskService().newTask();

        return updateExistingActivitiTask(in, activitiTask);
    }

    @Override
    @Transactional
    public AcmTask save(AcmTask in) throws AcmTaskException
    {
        Task activitiTask = getActivitiTaskService().createTaskQuery().taskId(in.getTaskId().toString()).singleResult();
        if (activitiTask != null)
        {
            return updateExistingActivitiTask(in, activitiTask);
        }

        // task must have been completed.  Try finding the historic task; but historical tasks can't be updated, so
        // even if we find it we have to throw an exception
        {
            HistoricTaskInstance hti = getActivitiHistoryService().
                    createHistoricTaskInstanceQuery().
                    taskId(in.getTaskId().toString()).
                    singleResult();

            if (hti == null)
            {
                // no such task!
                throw new AcmTaskException("No such task with id '" + in.getTaskId() + "'");
            } else
            {
                throw new AcmTaskException("Task with id '" + in.getTaskId() + "' has already been completed and so " +
                        "it cannot be updated.");
            }
        }


    }

    private AcmTask updateExistingActivitiTask(AcmTask in, Task activitiTask) throws AcmTaskException
    {
        activitiTask.setAssignee(in.getAssignee());
        activitiTask.setOwner(in.getOwner());
        Integer activitiPriority = activitiPriorityFromAcmPriority(in.getPriority());
        activitiTask.setPriority(activitiPriority);
        activitiTask.setDueDate(in.getDueDate());
        activitiTask.setName(in.getTitle());

        try
        {
            getActivitiTaskService().saveTask(activitiTask);

            // If start date is not provided, set start date as creation date
            if (in.getTaskStartDate() == null)
            {
                in.setTaskStartDate(activitiTask.getCreateTime());
            }

            getActivitiTaskService().setVariableLocal(activitiTask.getId(), TaskConstants.VARIABLE_NAME_OBJECT_TYPE, in.getAttachedToObjectType());
            getActivitiTaskService().setVariableLocal(activitiTask.getId(), TaskConstants.VARIABLE_NAME_OBJECT_ID, in.getAttachedToObjectId());
            if (in.getAttachedToObjectType() != null)
            {
                getActivitiTaskService().setVariableLocal(activitiTask.getId(), in.getAttachedToObjectType(), in.getAttachedToObjectId());
            }

            getActivitiTaskService().setVariableLocal(activitiTask.getId(), TaskConstants.VARIABLE_NAME_OBJECT_NAME, in.getAttachedToObjectName());
            getActivitiTaskService().setVariableLocal(activitiTask.getId(), TaskConstants.VARIABLE_NAME_START_DATE, in.getTaskStartDate());
            getActivitiTaskService().setVariableLocal(activitiTask.getId(), TaskConstants.VARIABLE_NAME_PERCENT_COMPLETE, in.getPercentComplete());
            getActivitiTaskService().setVariableLocal(activitiTask.getId(), TaskConstants.VARIABLE_NAME_DETAILS, in.getDetails());

            getActivitiTaskService().setVariableLocal(activitiTask.getId(), TaskConstants.VARIABLE_NAME_PARENT_OBJECT_ID, in.getParentObjectId());
            getActivitiTaskService().setVariableLocal(activitiTask.getId(), TaskConstants.VARIABLE_NAME_PARENT_OBJECT_TYPE, in.getParentObjectType());
            getActivitiTaskService().setVariableLocal(activitiTask.getId(), TaskConstants.VARIABLE_NAME_PARENT_OBJECT_TITLE, in.getParentObjectTitle());

            getActivitiTaskService().setVariableLocal(activitiTask.getId(), TaskConstants.VARIABLE_NAME_REWORK_INSTRUCTIONS, in.getReworkInstructions());

            if (in.getTaskOutcome() != null)
            {
                if (in.getTaskOutcome().getName() != null && in.getTaskOutcome().getName().equals("SEND_FOR_REWORK"))
                {
                    getActivitiRuntimeService().setVariable(activitiTask.getProcessInstanceId(), TaskConstants.VARIABLE_NAME_REWORK_INSTRUCTIONS, in.getReworkInstructions());
                } else if (in.getTaskOutcome().getName() != null && !in.getTaskOutcome().getName().equals("SEND_FOR_REWORK"))
                {
                    getActivitiRuntimeService().setVariable(activitiTask.getProcessInstanceId(), TaskConstants.VARIABLE_NAME_REWORK_INSTRUCTIONS, null);
                }
                getActivitiTaskService().setVariableLocal(activitiTask.getId(), TaskConstants.VARIABLE_NAME_OUTCOME, in.getTaskOutcome().getName());
            }

            in.setTaskId(Long.valueOf(activitiTask.getId()));
            in.setCreateDate(activitiTask.getCreateTime());

            // AFDP-1876 save the assignee for the next task in the process to process-level variables.  The next
            // assignee is to support business processes where the current assignee of a task can select the assignee
            // for the next task.  This feature was added originally for the DoD Joint Staff EDTRM project.
            getActivitiTaskService().setVariable(activitiTask.getId(), TaskConstants.VARIABLE_NAME_NEXT_ASSIGNEE, in.getNextAssignee());

            // make sure an assignee participant is there, so the right data access can be set on the assignee...
            // activiti has to control the assignee, not the assignment rules.
            ensureCorrectAssigneeInParticipants(in);

            // to ensure that the participants for new and updated ad-hoc tasks are visible to the client right away, we
            // have to apply the assignment and data access control rules right here, inline with the save operation.
            // Tasks generated or updated by the Activiti engine will have participants set by a specialized
            // Mule flow.
            getDataAccessPrivilegeListener().applyAssignmentAndAccessRules(in);

            // Now we have to check the assignee again, to be sure the Activiti task assignee is the "assignee"
            // participant.  I know we're calling the same method twice!, to overwrite any changes the rules make to the
            // assignee... In short, Activiti controls the task assignee, not the assignment rules.
            ensureCorrectAssigneeInParticipants(in);   // there's a good reason we call this again, see above

            // the rules (or the user) may have removed some participants.  We want to delete all participants other
            // than the ones we just now validated.
            getParticipantDao().removeAllOtherParticipantsForObject(TaskConstants.OBJECT_TYPE, in.getTaskId(), in.getParticipants());
            in.setParticipants(getParticipantDao().saveParticipants(in.getParticipants()));

            return in;
        } catch (ActivitiException e)
        {
            throw new AcmTaskException(e.getMessage(), e);
        }
    }

    @Override
    public void ensureCorrectAssigneeInParticipants(AcmTask in)
    {
        boolean assigneeFound = false;

        if (in.getParticipants() != null)
        {
            for (AcmParticipant ap : in.getParticipants())
            {
                if (ParticipantTypes.ASSIGNEE.equals(ap.getParticipantType()))
                {
                    if (in.getAssignee() == null)
                    {
                        // task has no assignee so we need to remove this participant
                        in.getParticipants().remove(ap);
                    } else
                    {
                        assigneeFound = true;
                        if (ap.getParticipantLdapId() == null || !ap.getParticipantLdapId().equalsIgnoreCase(in.getAssignee()))
                        {
                            ap.setParticipantLdapId(in.getAssignee());
                            break;
                        }
                    }

                }
            }
        }

        if (!assigneeFound && in.getAssignee() != null)
        {
            AcmParticipant assignee = new AcmParticipant();
            assignee.setParticipantLdapId(in.getAssignee());
            assignee.setParticipantType(ParticipantTypes.ASSIGNEE);
            assignee.setObjectId(in.getTaskId());
            assignee.setObjectType(TaskConstants.OBJECT_TYPE);

            if (in.getParticipants() == null)
            {
                in.setParticipants(new ArrayList<>());
            }
            in.getParticipants().add(assignee);
        }
    }

    @Override
    @Transactional
    public AcmTask completeTask(Principal userThatCompletedTheTask, Long taskId) throws AcmTaskException
    {
        return completeTask(userThatCompletedTheTask, taskId, null, null);
    }

    @Override
    public AcmTask completeTask(Principal userThatCompletedTheTask, Long taskId, String outcomePropertyName, String outcomeId)
            throws AcmTaskException
    {

        verifyCompleteTaskArgs(userThatCompletedTheTask, taskId);

        String user = userThatCompletedTheTask.getName();

        if (log.isInfoEnabled())
        {
            log.info("Completing task '" + taskId + "' for user '" + user + "'");
        }

        String strTaskId = String.valueOf(taskId);

        Task existingTask = getActivitiTaskService().
                createTaskQuery().
                includeProcessVariables().
                includeTaskLocalVariables().
                taskId(strTaskId).
                singleResult();

        verifyTaskExists(taskId, existingTask);

        verifyUserIsTheAssignee(taskId, user, existingTask);

        AcmTask retval = acmTaskFromActivitiTask(existingTask);
        retval = completeTask(retval, user, outcomePropertyName, outcomeId);
        return retval;
    }


    @Override
    @Transactional
    public AcmTask deleteTask(Principal userThatCompletedTheTask, Long taskId) throws AcmTaskException
    {
        verifyCompleteTaskArgs(userThatCompletedTheTask, taskId);

        String user = userThatCompletedTheTask.getName();

        if (log.isInfoEnabled())
        {
            log.info("Deleting task '" + taskId + "' for user '" + user + "'");
        }

        String strTaskId = String.valueOf(taskId);

        Task existingTask = getActivitiTaskService().
                createTaskQuery().
                includeProcessVariables().
                includeTaskLocalVariables().
                taskId(strTaskId).
                singleResult();

        verifyTaskExists(taskId, existingTask);

        verifyUserIsTheAssignee(taskId, user, existingTask);

        AcmTask retval = acmTaskFromActivitiTask(existingTask);
        retval = deleteTask(retval, user, null);
        return retval;
    }

    protected void verifyCompleteTaskArgs(Principal userThatCompletedTheTask, Long taskId)
    {
        if (userThatCompletedTheTask == null || taskId == null)
        {
            throw new IllegalArgumentException("userThatCompletedTheTask and taskId must be specified");
        }
    }

    protected void verifyUserIsTheAssignee(Long taskId, String user, Task existingTask) throws AcmTaskException
    {
        if (existingTask.getAssignee() == null || !existingTask.getAssignee().equals(user))
        {
            throw new AcmTaskException("Task '" + taskId + "' can only be closed by the assignee.");
        }
    }

    protected void verifyTaskExists(Long taskId, Task existingTask) throws AcmTaskException
    {
        if (existingTask == null)
        {
            throw new AcmTaskException("Task '" + taskId + "' does not exist or has already been completed.");
        }
    }

    @Override
    public List<AcmTask> tasksForUser(String user)
    {
        if (log.isInfoEnabled())
        {
            log.info("Finding all tasks for user '" + user + "'");
        }

        List<AcmTask> retval = new ArrayList<>();

        List<Task> activitiTasks = getActivitiTaskService().
                createTaskQuery().
                taskAssignee(user).
                includeProcessVariables().
                includeTaskLocalVariables().
                orderByDueDate().desc().
                list();

        if (activitiTasks != null)
        {
            if (log.isDebugEnabled())
            {
                log.debug("Found '" + activitiTasks.size() + "' tasks for user '" + user + "'");
            }

            for (Task activitiTask : activitiTasks)
            {
                AcmTask acmTask = acmTaskFromActivitiTask(activitiTask);

                retval.add(acmTask);
            }
        }


        return retval;
    }

    @Override
    public List<AcmTask> allTasks()
    {
        if (log.isInfoEnabled())
        {
            log.info("Finding all tasks for all users '");
        }

        List<AcmTask> retval = new ArrayList<>();

        List<Task> activitiTasks = getActivitiTaskService().
                createTaskQuery().
                includeProcessVariables().
                includeTaskLocalVariables().
                orderByDueDate().desc().
                list();

        if (activitiTasks != null)
        {
            if (log.isDebugEnabled())
            {
                log.debug("Found '" + activitiTasks.size() + "' tasks for all users");
            }

            for (Task activitiTask : activitiTasks)
            {
                AcmTask acmTask = acmTaskFromActivitiTask(activitiTask);

                retval.add(acmTask);
            }
        }

        return retval;
    }

    @Override
    public List<AcmTask> pastDueTasks()
    {
        if (log.isInfoEnabled())
        {
            log.info("Finding all tasks for all users that due date was before today");
        }

        List<AcmTask> retval = new ArrayList<>();

        List<Task> activitiTasks = getActivitiTaskService().
                createTaskQuery().
                includeProcessVariables().
                includeTaskLocalVariables().
                dueBefore(new Date()).
                list();

        if (activitiTasks != null)
        {
            if (log.isDebugEnabled())
            {
                log.debug("Found '" + activitiTasks.size() + "' tasks for all users with past due date");
            }

            for (Task activitiTask : activitiTasks)
            {
                AcmTask acmTask = acmTaskFromActivitiTask(activitiTask);

                retval.add(acmTask);
            }
        }

        return retval;
    }

    @Override
    public AcmTask claimTask(Long taskId, String userId) throws AcmTaskException
    {
        if (taskId != null && userId != null)
        {
            try
            {
                getActivitiTaskService().claim(String.valueOf(taskId), userId);
                Task existingTask = getActivitiTaskService().
                        createTaskQuery().
                        includeProcessVariables().
                        includeTaskLocalVariables().
                        taskId(String.valueOf(taskId)).
                        singleResult();
                return acmTaskFromActivitiTask(existingTask);

            } catch (ActivitiException e)
            {
                log.info("Claiming task failed for task with ID: [{}]", taskId);
                throw new AcmTaskException(e.getMessage(), e);
            }
        }
        throw new AcmTaskException("Task with ID '" + taskId + "' does not exist. Verify the supplied arguments (taskId: " + taskId + " and userId: " + userId + ")");
    }

    @Override
    public AcmTask unclaimTask(Long taskId) throws AcmTaskException
    {
        if (taskId != null)
        {
            try
            {
                getActivitiTaskService().unclaim(String.valueOf(taskId));
                Task existingTask = getActivitiTaskService().
                        createTaskQuery().
                        includeProcessVariables().
                        includeTaskLocalVariables().
                        taskId(String.valueOf(taskId)).
                        singleResult();
                return acmTaskFromActivitiTask(existingTask);
            } catch (ActivitiException e)
            {
                log.info("Unclaiming task failed for task with ID: [{}]", taskId);
                throw new AcmTaskException(e.getMessage(), e);
            }
        }
        throw new AcmTaskException("Task with ID '" + taskId + "' does not exist. Verify the supplied arguments (taskId: " + taskId + ")");
    }

    @Override
    @Transactional
    public void deleteProcessInstance(String parentId, String processId, String deleteReason, Authentication authentication, String ipAddress) throws AcmTaskException
    {
        if (processId != null)
        {
            try
            {
                // get the process instance
                ProcessInstance processInstance =
                        getActivitiRuntimeService().createProcessInstanceQuery()
                                .processInstanceId(processId)
                                .singleResult();

                if (processInstance != null)
                {
                    //validate against provided parentId
                    Long objectId = (Long) getActivitiRuntimeService().getVariable(processId, TaskConstants.VARIABLE_NAME_OBJECT_ID);
                    if (objectId == null)
                    {
                        throw new AcmTaskException("No parent object ID is associated with the process instance ID " + processId);
                    }
                    if (objectId != null && objectId.toString().equals(parentId))
                    {
                        log.info("provided ID [{}] and object ID from process instance match [{}]", objectId, parentId);

                        //EDTRM-670	- delete the process instance, all tasks should be marked "TERMINATED" instead of "CLOSED"
                        // set deleteReason to "TERMINATED" so that we can utilize it to set the status of tasks belonging to a "TERMINATED"
                        //process as "TERMINATED" from historic task instance

                        deleteReason = TaskConstants.STATE_TERMINATED;
                        getActivitiRuntimeService().deleteProcessInstance(processId, deleteReason);

                        //retrieve historic task instances
                        //update the status of completed task to "TERMINATED"
                        List<HistoricTaskInstance> htis =
                                getActivitiHistoryService().createHistoricTaskInstanceQuery()
                                        .processInstanceId(processId)
                                        .includeProcessVariables()
                                        .includeTaskLocalVariables().list();

                        for (HistoricTaskInstance hti : htis)
                        {
                            AcmTask acmTask = acmTaskFromHistoricActivitiTask(hti);
                            if (acmTask != null)
                            {
                                log.info("Task with id [{}] TERMINATED due to deletion of process instance with ID [{}]", acmTask.getId(), processId);
                                AcmApplicationTaskEvent event = new AcmApplicationTaskEvent(acmTask, "terminate", authentication.getName(), true, ipAddress);
                                getTaskEventPublisher().publishTaskEvent(event);
                            }
                        }
                    } else
                    {
                        throw new AcmTaskException("Process cannot be deleted as supplied parentId : " + parentId + "doesn't match with process instance object Id : " + objectId);
                    }
                }
            } catch (ActivitiException e)
            {
                log.info("Deleting process instance failed for process ID: [{}]", processId);
                throw new AcmTaskException(e.getMessage(), e);
            }
        }
    }

    @Override
    public List<AcmTask> dueSpecificDateTasks(NumberOfDays numberOfDaysFromToday)
    {
        if (log.isInfoEnabled())
        {
            log.info(String.format("Finding all tasks for all users which due date is until %s from today", numberOfDaysFromToday.getnDays()));
        }

        List<AcmTask> retval = new ArrayList<>();

        List<Task> activitiTasks = getActivitiTaskService().
                createTaskQuery().
                includeProcessVariables().
                includeTaskLocalVariables().
                dueAfter(new Date()).
                dueBefore(shiftDateFromToday(numberOfDaysFromToday.getNumOfDays())).
                list();

        if (activitiTasks != null)
        {
            if (log.isDebugEnabled())
            {
                log.debug("Found '" + activitiTasks.size() + "' tasks for all users which due date is between today and " + numberOfDaysFromToday.getnDays() + " from today");
            }

            for (Task activitiTask : activitiTasks)
            {
                AcmTask acmTask = acmTaskFromActivitiTask(activitiTask);
                retval.add(acmTask);
            }
        }

        return retval;
    }

    @Override
    public AcmTask findById(Long taskId) throws AcmTaskException
    {
        if (log.isInfoEnabled())
        {
            log.info("Finding task with ID '" + taskId + "'");
        }
        AcmTask retval;

        Task activitiTask = getActivitiTaskService().
                createTaskQuery().
                taskId(String.valueOf(taskId)).
                includeProcessVariables().
                includeTaskLocalVariables().
                singleResult();
        if (activitiTask != null)
        {
            retval = acmTaskFromActivitiTask(activitiTask);
            return retval;
        } else
        {
            HistoricTaskInstance hti = getActivitiHistoryService().
                    createHistoricTaskInstanceQuery().
                    taskId(String.valueOf(taskId)).
                    includeProcessVariables().
                    includeTaskLocalVariables().
                    singleResult();

            if (hti != null)
            {
                retval = acmTaskFromHistoricActivitiTask(hti);

                return retval;
            }
        }

        throw new AcmTaskException("Task with ID '" + taskId + "' does not exist.");

    }

    @Override
    public List<WorkflowHistoryInstance> getWorkflowHistory(String id, boolean adhoc)
    {

        List<WorkflowHistoryInstance> retval = new ArrayList<>();

        HistoricTaskInstanceQuery query;

        // due to an Activiti issue, we have to retrieve the task local issues separately for each task instance.
        // if we ask for them at the query level (via "includeTaskLocalVariables") Activiti basically returns one big
        // map, instead of one map per historic task instance.  Obviously the one big map will contain correct values
        // only for the last task retrieved.
        if (!adhoc)
        {
            query = getActivitiHistoryService().createHistoricTaskInstanceQuery().processInstanceId(id).includeTaskLocalVariables().orderByHistoricTaskInstanceEndTime().asc();
        } else
        {
            query = getActivitiHistoryService().createHistoricTaskInstanceQuery().taskId(id).includeTaskLocalVariables().orderByHistoricTaskInstanceEndTime().asc();
        }

        if (null != query)
        {
            List<HistoricTaskInstance> historicTaskInstances = query.list();

            if (null != historicTaskInstances && !historicTaskInstances.isEmpty())
            {
                for (HistoricTaskInstance historicTaskInstance : historicTaskInstances)
                {

                    String taskId = historicTaskInstance.getId();

                    // TODO: For now Role is empty. This is agreed with Dave. Once we have that information, we should add it here.
                    String role = "";
                    Date startDate = historicTaskInstance.getStartTime();
                    Date endDate = historicTaskInstance.getEndTime();

                    String status = findTaskStatus(historicTaskInstance);

                    // for purposes of the workflow history, the task outcome should override the status.
                    Map<String, Object> localVariables = historicTaskInstance.getTaskLocalVariables();

                    if (null != localVariables && localVariables.containsKey("outcome"))
                    {
                        String outcome = (String) localVariables.get(TaskConstants.VARIABLE_NAME_OUTCOME);
                        status = WordUtils.capitalizeFully(outcome.replaceAll("_", " "));
                    }

                    WorkflowHistoryInstance workflowHistoryInstance = new WorkflowHistoryInstance();

                    workflowHistoryInstance.setId(taskId);

                    workflowHistoryInstance.setRole(role);
                    workflowHistoryInstance.setStatus(status);
                    workflowHistoryInstance.setStartDate(startDate);
                    workflowHistoryInstance.setEndDate(endDate);

                    if (historicTaskInstance.getAssignee() != null)
                    {
                        AcmUser user = getUserDao().findByUserId(historicTaskInstance.getAssignee());
                        if (user != null)
                        {
                            String participant = user.getFullName();
                            workflowHistoryInstance.setParticipant(participant);
                        } else
                        {
                            workflowHistoryInstance.setParticipant("[unknown]");
                        }
                    }

                    retval.add(workflowHistoryInstance);
                }
            }
        }

        return retval;
    }

    private String findTaskStatus(HistoricTaskInstance historicTaskInstance)
    {
        if (isTaskTerminated(historicTaskInstance))
        {
            return TaskConstants.STATE_TERMINATED;
        }
        return historicTaskInstance.getEndTime() == null ? TaskConstants.STATE_ACTIVE : TaskConstants.STATE_CLOSED;
    }

    private boolean isTaskTerminated(HistoricTaskInstance historicTaskInstance)
    {
        //EDTRM-670	- All tasks should be marked "TERMINATED" instead of "CLOSED"
        //tasks belonging to a "TERMINATED" process will have delete reason set to "TERMINATED"
        if (historicTaskInstance.getDeleteReason() != null && historicTaskInstance.getEndTime() != null
                && historicTaskInstance.getDeleteReason().equals(TaskConstants.STATE_TERMINATED))
        {
            //make a check if the task is ad-hoc or not
            if (historicTaskInstance.getProcessInstanceId() != null)
            {
                HistoricProcessInstance historicProcessInstance =
                        getActivitiHistoryService().createHistoricProcessInstanceQuery()
                                .processInstanceId(historicTaskInstance.getProcessInstanceId())
                                .singleResult();

                //deleted process instance endTime matches terminated tasks endTime to second offset
                if (historicProcessInstance.getEndTime() != null)
                {
                    Date processTerminatedDateTime = DateUtils.round(historicProcessInstance.getEndTime(), Calendar.SECOND);
                    Date taskTerminatedDateTime = DateUtils.round(historicTaskInstance.getEndTime(), Calendar.SECOND);
                    if (processTerminatedDateTime.equals(taskTerminatedDateTime))
                    {
                        return true;
                    }
                }
            }
            return true;
        }
        return false;
    }

    private String findTaskStatus(HistoricTaskInstance historicTaskInstance, Boolean deleted)
    {
        if (isTaskTerminated(historicTaskInstance))
        {
            return TaskConstants.STATE_TERMINATED;
        }
        return historicTaskInstance.getEndTime() == null ? TaskConstants.STATE_ACTIVE : TaskConstants.STATE_DELETED;
    }

    private String findTaskStatus(Task task)
    {
        // tasks in ACT_RU_TASK table (where Task objects come from) are active by definition
        // tasks have status unclaimed if assignee is null
        if (task.getAssignee() == null)
        {
            return TaskConstants.STATE_UNCLAIMED;
        }
        return TaskConstants.STATE_ACTIVE;
    }

    @Override
    public List<AcmTask> getTasksModifiedSince(Date lastModified, int start, int pageSize)
    {
        List<AcmTask> retval = new ArrayList<>();

        List<HistoricTaskInstance> tasks = getActivitiHistoryService().
                createHistoricTaskInstanceQuery().
                includeProcessVariables().
                includeTaskLocalVariables().
                taskCreatedAfter(lastModified).
                orderByTaskId().
                asc().listPage(start, pageSize);

        if (tasks != null)
        {
            for (HistoricTaskInstance task : tasks)
            {
                AcmTask active = acmTaskFromHistoricActivitiTask(task);
                retval.add(active);
            }
        }

        return retval;

    }

    protected AcmTask completeTask(AcmTask acmTask, String user, String outcomePropertyName, String outcomeId) throws AcmTaskException
    {
        String strTaskId = String.valueOf(acmTask.getId());
        try
        {
            if (outcomePropertyName != null && outcomeId != null)
            {
                getActivitiTaskService().setVariable(acmTask.getId().toString(), outcomePropertyName, outcomeId);
                getActivitiTaskService().setVariableLocal(strTaskId, TaskConstants.VARIABLE_NAME_OUTCOME, outcomeId);
            }

            getActivitiTaskService().complete(strTaskId);

            HistoricTaskInstance hti =
                    getActivitiHistoryService().createHistoricTaskInstanceQuery().taskId(strTaskId).singleResult();

            acmTask.setTaskStartDate(hti.getStartTime());
            acmTask.setTaskFinishedDate(hti.getEndTime());
            acmTask.setTaskDurationInMillis(hti.getDurationInMillis());
            acmTask.setCompleted(true);
            String status = findTaskStatus(hti);
            acmTask.setStatus(status);

            return acmTask;
        } catch (ActivitiException e)
        {
            log.error("Could not close task '" + strTaskId + "' for user '" + user + "': " + e.getMessage(), e);
            throw new AcmTaskException(e);
        }
    }

    protected AcmTask deleteTask(AcmTask acmTask, String user, String deleteReason) throws AcmTaskException
    {
        String strTaskId = String.valueOf(acmTask.getId());
        try
        {
            getActivitiTaskService().deleteTask(strTaskId, deleteReason);

            HistoricTaskInstance hti =
                    getActivitiHistoryService().createHistoricTaskInstanceQuery().taskId(strTaskId).singleResult();

            acmTask.setTaskStartDate(hti.getStartTime());
            acmTask.setTaskFinishedDate(hti.getEndTime());
            acmTask.setTaskDurationInMillis(hti.getDurationInMillis());
            acmTask.setCompleted(true);
            String status = findTaskStatus(hti, true);
            acmTask.setStatus(status);

            return acmTask;
        } catch (ActivitiException e)
        {
            log.error("Could not close task '" + strTaskId + "' for user '" + user + "': " + e.getMessage(), e);
            throw new AcmTaskException(e);
        }
    }

    protected AcmTask acmTaskFromHistoricActivitiTask(HistoricTaskInstance hti)
    {
        if (hti == null)
        {
            return null;
        }

        // even active tasks have an entry in the historic task table, so this HistoricTaskInstance may
        // represent an active task
        AcmTask retval;
        retval = new AcmTask();
        retval.setTaskStartDate(hti.getStartTime());
        retval.setTaskFinishedDate(hti.getEndTime());
        retval.setTaskDurationInMillis(hti.getDurationInMillis());
        retval.setCompleted(hti.getEndTime() != null);

        retval.setTaskId(Long.valueOf(hti.getId()));
        retval.setDueDate(hti.getDueDate());
        String taskPriority = acmPriorityFromActivitiPriority(hti.getPriority());
        retval.setPriority(taskPriority);
        retval.setTitle(hti.getName());
        retval.setAssignee(hti.getAssignee());

        if (hti.getProcessVariables() != null)
        {
            retval.setAttachedToObjectId((Long) hti.getProcessVariables().get(TaskConstants.VARIABLE_NAME_OBJECT_ID));
            retval.setAttachedToObjectType((String) hti.getProcessVariables().get(TaskConstants.VARIABLE_NAME_OBJECT_TYPE));
            retval.setAttachedToObjectName((String) hti.getProcessVariables().get(TaskConstants.VARIABLE_NAME_OBJECT_NAME));
            retval.setWorkflowRequestId((Long) hti.getProcessVariables().get(TaskConstants.VARIABLE_NAME_REQUEST_ID));
            retval.setWorkflowRequestType((String) hti.getProcessVariables().get(TaskConstants.VARIABLE_NAME_REQUEST_TYPE));
            retval.setReviewDocumentPdfRenditionId((Long) hti.getProcessVariables().get(TaskConstants.VARIABLE_NAME_PDF_RENDITION_ID));
            retval.setReviewDocumentFormXmlId((Long) hti.getProcessVariables().get(TaskConstants.VARIABLE_NAME_XML_RENDITION_ID));

            Long parentObjectId = (Long) hti.getProcessVariables().get(TaskConstants.VARIABLE_NAME_PARENT_OBJECT_ID);
            parentObjectId = parentObjectId == null ? retval.getAttachedToObjectId() : parentObjectId;
            retval.setParentObjectId(parentObjectId);

            String parentObjectType = (String) hti.getProcessVariables().get(TaskConstants.VARIABLE_NAME_PARENT_OBJECT_TYPE);
            parentObjectType = parentObjectType == null ? retval.getAttachedToObjectType() : parentObjectType;
            retval.setParentObjectType(parentObjectType);

            retval.setParentObjectTitle((String) hti.getProcessVariables().get(TaskConstants.VARIABLE_NAME_PARENT_OBJECT_TITLE));

        }

        if (hti.getTaskLocalVariables() != null)
        {
            Map<String, Object> taskLocal = hti.getTaskLocalVariables();

            extractTaskLocalVariables(retval, taskLocal);

            // There seems to be an inconsistency when retrieving details from task local variables map
            // especially for tasks inside a subprocces, for e.g. when the subprocess ends, the API seems
            // to retrieve only details from the most recently completed task outside of the subprocess
            // Using HistoricVariableInstance solves this issue and we'll use this until we find any
            // better solution for this issue

            HistoricVariableInstance historicVariableInstance = getActivitiHistoryService().
                    createHistoricVariableInstanceQuery().
                    taskId(retval.getId().toString()).
                    variableName(TaskConstants.VARIABLE_NAME_DETAILS).
                    singleResult();
            if (historicVariableInstance != null)
            {
                retval.setDetails((String) historicVariableInstance.getValue());
            }

            historicVariableInstance = getActivitiHistoryService().
                    createHistoricVariableInstanceQuery().
                    taskId(retval.getId().toString()).
                    variableName(TaskConstants.VARIABLE_NAME_REWORK_INSTRUCTIONS).
                    singleResult();
            if (historicVariableInstance != null)
            {
                retval.setReworkInstructions((String) historicVariableInstance.getValue());
            }
        }

        String status = findTaskStatus(hti);
        retval.setStatus(status);

        String pid = hti.getProcessDefinitionId();
        String processInstanceId = hti.getProcessInstanceId();
        String taskDefinitionKey = hti.getTaskDefinitionKey();
        if (pid != null)
        {
            findProcessNameAndTaskOutcomes(retval, pid, processInstanceId, taskDefinitionKey);
            findSelectedTaskOutcome(hti, retval);
        } else
        {
            retval.setAdhocTask(true);
        }

        List<AcmParticipant> participants = getParticipantDao().findParticipantsForObject("TASK", retval.getTaskId());
        retval.setParticipants(participants);

        if (log.isTraceEnabled())
        {
            log.trace("Activiti task id '" + retval.getTaskId() + "' for object type '" +
                    retval.getAttachedToObjectType() + "'" +
                    ", object id '" + retval.getAttachedToObjectId() + "' found for user '" + retval.getAssignee()
                    + "'");
        }

        return retval;
    }

    @Override
    public void createFolderForTaskEvent(AcmTaskEvent event) throws AcmTaskException, AcmCreateObjectFailedException
    {
        log.info("Creating folder for task with ID: " + event.getObjectId());

        getAuditPropertyEntityAdapter().setUserId(event.getUserId());

        AcmTask task = getTaskExtractor().fromEvent(event);

        createFolderForTaskEvent(task);
    }

    @Override
    public void createFolderForTaskEvent(AcmTask task) throws AcmTaskException, AcmCreateObjectFailedException
    {
        log.info("Creating folder for task with ID: " + task.getId());

        if (task.getContainer() != null && task.getContainer().getFolder() != null)
            return;

        task = getTaskBusinessRule().applyRules(task);

        // if the task doesn't have a container folder, the rules will set the EcmFolderPath.  If it does have
        // one, the rules will leave EcmFolderPath null.  So only create a folder if EcmFolderPath is not null.

        if (task.getEcmFolderPath() != null)
        {
            String folderId = getFileService().createFolder(task.getEcmFolderPath());

            AcmContainer container = new AcmContainer();
            AcmFolder folder = new AcmFolder();
            folder.setCmisFolderId(folderId);
            folder.setName(EcmFileConstants.CONTAINER_FOLDER_NAME);
            container.setFolder(folder);
            container.setContainerObjectType(task.getObjectType());
            container.setContainerObjectId(task.getId());
            container.setContainerObjectTitle(task.getTitle());

            container = getContainerFolderDao().save(container);
            task.setContainer(container);

            log.info("Created folder id '" + folderId + "' for task with ID " + task.getTaskId());
        }

    }

    private void findSelectedTaskOutcome(HistoricTaskInstance hti, AcmTask retval)
    {
        // check for selected task outcome
        String outcomeId = (String) hti.getTaskLocalVariables().get("outcome");
        if (outcomeId != null)
        {
            for (TaskOutcome availableOutcome : retval.getAvailableOutcomes())
            {
                if (outcomeId.equals(availableOutcome.getName()))
                {
                    retval.setTaskOutcome(availableOutcome);
                    break;
                }
            }
        }
    }

    private void findProcessNameAndTaskOutcomes(
            AcmTask retval,
            String processDefinitionId,
            String processInstanceId,
            String taskDefinitionKey)
    {
        ProcessDefinition pd =
                getActivitiRepositoryService().createProcessDefinitionQuery().processDefinitionId(processDefinitionId).singleResult();
        retval.setBusinessProcessName(pd.getName());
        retval.setAdhocTask(false);
        retval.setBusinessProcessId(
                processInstanceId == null ? null : Long.valueOf(processInstanceId));

        List<FormProperty> formProperties = findFormPropertiesForTask(processDefinitionId, taskDefinitionKey);
        if (formProperties != null)
        {
            for (FormProperty fp : formProperties)
            {
                log.debug("form property name: " + fp.getName() + "; id: " + fp.getId());
                if (fp.getId() != null && fp.getId().endsWith("Outcome"))
                {
                    retval.setOutcomeName(fp.getId());
                    for (FormValue fv : fp.getFormValues())
                    {
                        log.debug(fv.getId() + " = " + fv.getName());
                        TaskOutcome outcome = new TaskOutcome();
                        outcome.setName(fv.getId());
                        outcome.setDescription(fv.getName());
                        outcome.setFieldsRequiredWhenOutcomeIsChosen(getRequiredFieldsPerOutcomeMap().get(fv.getId()));
                        retval.getAvailableOutcomes().add(outcome);
                    }
                }
            }
        }
    }

    private List<FormProperty> findFormPropertiesForTask(String processDefinitionId, String taskDefinitionKey)
    {
        BpmnModel model = getActivitiRepositoryService().getBpmnModel(processDefinitionId);

        List<Process> processes = model.getProcesses();

        Process p = processes.get(0);

        FlowElement taskFlowElement = p.getFlowElementRecursive(taskDefinitionKey);

        log.debug("task flow type: " + taskFlowElement.getClass().getName());

        if (taskFlowElement instanceof UserTask)
        {
            UserTask ut = (UserTask) taskFlowElement;

            List<FormProperty> formProperties = ut.getFormProperties();

            return formProperties;
        }
        return Collections.emptyList();

    }

    private void extractTaskLocalVariables(AcmTask acmTask, Map<String, Object> taskLocal)
    {
        if (acmTask.getAttachedToObjectId() == null)
        {
            Long objectId = (Long) taskLocal.get(TaskConstants.VARIABLE_NAME_OBJECT_ID);
            acmTask.setAttachedToObjectId(objectId);
        }
        if (acmTask.getAttachedToObjectType() == null)
        {
            String objectType = (String) taskLocal.get(TaskConstants.VARIABLE_NAME_OBJECT_TYPE);
            acmTask.setAttachedToObjectType(objectType);
        }
        if (acmTask.getAttachedToObjectName() == null)
        {
            String objectName = (String) taskLocal.get(TaskConstants.VARIABLE_NAME_OBJECT_NAME);
            acmTask.setAttachedToObjectName(objectName);
        }
        if (acmTask.getParentObjectId() == null)
        {
            Long parentObjectId = (Long) taskLocal.get(TaskConstants.VARIABLE_NAME_PARENT_OBJECT_ID);
            acmTask.setParentObjectId(parentObjectId);
        }
        if (acmTask.getParentObjectType() == null)
        {
            String parentObjectType = (String) taskLocal.get(TaskConstants.VARIABLE_NAME_PARENT_OBJECT_TYPE);
            acmTask.setParentObjectType(parentObjectType);
        }
        if (acmTask.getParentObjectTitle() == null)
        {
            String parentObjectTitle = (String) taskLocal.get(TaskConstants.VARIABLE_NAME_PARENT_OBJECT_TITLE);
            acmTask.setParentObjectType(parentObjectTitle);
        }
        Date startDate = (Date) taskLocal.get(TaskConstants.VARIABLE_NAME_START_DATE);
        acmTask.setTaskStartDate(startDate);

        Integer percentComplete = (Integer) taskLocal.get(TaskConstants.VARIABLE_NAME_PERCENT_COMPLETE);
        acmTask.setPercentComplete(percentComplete);

        // AFDP-1876 Task next assignee field: for ad-hoc tasks (not part of a business process) the next assignee
        // will be stored here in task local variables.  It's hard to imagine why a "next assignee" is needed for an
        // ad-hoc task - where you can just change the assignee directly - but we have this code here for
        // consistency and to avoid surprises.  This way, every task can have a next assignee.
        //
        // Note, if the task already has nextAssignee set, then the process-level variables had the next assignee,
        // and we don't want to overwrite it here.  So only check the task local variables if the nextAssignee is null.
        if (acmTask.getNextAssignee() == null)
        {
            String nextAssignee = (String) taskLocal.get(TaskConstants.VARIABLE_NAME_NEXT_ASSIGNEE);
            acmTask.setNextAssignee(nextAssignee);
        }

    }

    private String acmPriorityFromActivitiPriority(int priority)
    {
        String defaultPriority = TaskConstants.DEFAULT_PRIORITY_WORD;

        for (Map.Entry<String, Integer> acmToActiviti : getPriorityLevelToNumberMap().entrySet())
        {
            if (acmToActiviti.getValue().equals(priority))
            {
                return acmToActiviti.getKey();
            }
        }

        return defaultPriority;
    }

    private Integer activitiPriorityFromAcmPriority(String acmPriority)
    {
        for (Map.Entry<String, Integer> acmToActiviti : getPriorityLevelToNumberMap().entrySet())
        {
            if (acmToActiviti.getKey().equals(acmPriority))
            {
                return acmToActiviti.getValue();
            }
        }

        return TaskConstants.DEFAULT_PRIORITY;
    }

    protected AcmTask acmTaskFromActivitiTask(Task activitiTask)
    {
        if (activitiTask == null)
        {
            return null;
        }

        AcmTask acmTask = new AcmTask();

        acmTask.setTaskId(Long.valueOf(activitiTask.getId()));
        acmTask.setDueDate(activitiTask.getDueDate());
        String taskPriority = acmPriorityFromActivitiPriority(activitiTask.getPriority());
        acmTask.setPriority(taskPriority);
        acmTask.setTitle(activitiTask.getName());
        acmTask.setAssignee(activitiTask.getAssignee());
        acmTask.setCreateDate(activitiTask.getCreateTime());
        acmTask.setOwner(activitiTask.getOwner());

        extractProcessVariables(activitiTask, acmTask);

        if (activitiTask.getTaskLocalVariables() != null)
        {
            extractTaskLocalVariables(acmTask, activitiTask.getTaskLocalVariables());

            String details = (String) activitiTask.getTaskLocalVariables().get(TaskConstants.VARIABLE_NAME_DETAILS);
            acmTask.setDetails(details);

            //only on rework task, first time rework instructions will be fetched from process variables
            //otherwise, rework instruction will be fetched via task local variable
            String reworkInstructions = (String) activitiTask.getTaskLocalVariables().get(TaskConstants.VARIABLE_NAME_REWORK_INSTRUCTIONS);
            if (reworkInstructions != null)
            {
                acmTask.setReworkInstructions(reworkInstructions);
            }

        }

        // If start date is not provided, set start date as creation date
        if (acmTask.getTaskStartDate() == null)
        {
            acmTask.setTaskStartDate(activitiTask.getCreateTime());
        }

        String status = findTaskStatus(activitiTask);
        acmTask.setStatus(status);

        String pid = activitiTask.getProcessDefinitionId();
        String processInstanceId = activitiTask.getProcessInstanceId();
        String taskDefinitionKey = activitiTask.getTaskDefinitionKey();

        if (pid != null)
        {
            findProcessNameAndTaskOutcomes(acmTask, pid, processInstanceId, taskDefinitionKey);
        } else
        {
            acmTask.setAdhocTask(true);
        }

        // only business process tasks can have a candidate group, so only check if the task is from a process.
        // also, if the task already has an assignee, we don't care about the candidate group.  So, only
        // lookup candidate groups for business process tasks with no assignee.
        if (pid != null && acmTask.getAssignee() == null)
        {
            List<String> candidateGroups = findCandidateGroups(activitiTask.getId());
            acmTask.setCandidateGroups(candidateGroups);
        }


        if (log.isTraceEnabled())
        {
            log.trace("Activiti task id '" + acmTask.getTaskId() + "' for object type '" +
                    acmTask.getAttachedToObjectType() + "'" +
                    ", object id '" + acmTask.getAttachedToObjectId() + ", object number '" + acmTask.getAttachedToObjectName() + "' found for user '" + acmTask.getAssignee()
                    + "'");
        }

        List<AcmParticipant> participants = getParticipantDao().findParticipantsForObject("TASK", acmTask.getTaskId());
        acmTask.setParticipants(participants);

        return acmTask;
    }

    private List<String> findCandidateGroups(String taskId)
    {
        List<IdentityLink> candidates = getActivitiTaskService().getIdentityLinksForTask(taskId);

        if (candidates != null)
        {
            List<String> retval = candidates.stream().filter(il -> TaskConstants.IDENTITY_LINK_TYPE_CANDIDATE.equals(il.getType()))
                    .filter(il -> il.getGroupId() != null)
                    .map(IdentityLink::getGroupId)
                    .collect(Collectors.toList());
            return retval;
        }

        return null;
    }


    private Date shiftDateFromToday(int daysFromToday)
    {
        Date nextDate;
        Date today = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(today);
        cal.add(Calendar.DATE, daysFromToday);
        nextDate = cal.getTime();
        return nextDate;
    }

    protected void extractProcessVariables(Task activitiTask, AcmTask acmTask)
    {
        if (activitiTask.getProcessVariables() != null)
        {
            acmTask.setAttachedToObjectId((Long) activitiTask.getProcessVariables().get(TaskConstants.VARIABLE_NAME_OBJECT_ID));
            acmTask.setAttachedToObjectType((String) activitiTask.getProcessVariables().get(TaskConstants.VARIABLE_NAME_OBJECT_TYPE));
            acmTask.setAttachedToObjectName((String) activitiTask.getProcessVariables().get(TaskConstants.VARIABLE_NAME_OBJECT_NAME));

            Long parentObjectId = (Long) activitiTask.getProcessVariables().get(TaskConstants.VARIABLE_NAME_PARENT_OBJECT_ID);
            parentObjectId = parentObjectId == null ? acmTask.getAttachedToObjectId() : parentObjectId;
            acmTask.setParentObjectId(parentObjectId);

            String parentObjectType = (String) activitiTask.getProcessVariables().get(TaskConstants.VARIABLE_NAME_PARENT_OBJECT_TYPE);
            parentObjectType = parentObjectType == null ? acmTask.getAttachedToObjectType() : parentObjectType;
            acmTask.setParentObjectType(parentObjectType);

            acmTask.setParentObjectTitle((String) activitiTask.getProcessVariables().get(TaskConstants.VARIABLE_NAME_PARENT_OBJECT_TITLE));

            acmTask.setWorkflowRequestId((Long) activitiTask.getProcessVariables().get(TaskConstants.VARIABLE_NAME_REQUEST_ID));
            acmTask.setWorkflowRequestType((String) activitiTask.getProcessVariables().get(TaskConstants.VARIABLE_NAME_REQUEST_TYPE));
            acmTask.setReviewDocumentPdfRenditionId((Long) activitiTask.getProcessVariables().get(TaskConstants.VARIABLE_NAME_PDF_RENDITION_ID));
            acmTask.setReviewDocumentFormXmlId((Long) activitiTask.getProcessVariables().get(TaskConstants.VARIABLE_NAME_XML_RENDITION_ID));
            acmTask.setReworkInstructions((String) activitiTask.getProcessVariables().get(TaskConstants.VARIABLE_NAME_REWORK_INSTRUCTIONS));
            acmTask.setTaskStartDate((Date) activitiTask.getProcessVariables().get(TaskConstants.VARIABLE_NAME_START_DATE));

            if (acmTask.getReviewDocumentPdfRenditionId() != null && acmTask.getReviewDocumentPdfRenditionId() > 0)
            {
                EcmFile docUnderReview = getFileDao().find(acmTask.getReviewDocumentPdfRenditionId());
                acmTask.setDocumentUnderReview(docUnderReview);
            }

            // AFDP-1876 if the task is part of a business process, the next assignee will be stored in process variables.
            acmTask.setNextAssignee((String) activitiTask.getProcessVariables().get(TaskConstants.VARIABLE_NAME_NEXT_ASSIGNEE));
        }
    }

    public RuntimeService getActivitiRuntimeService()
    {
        return activitiRuntimeService;
    }

    public void setActivitiRuntimeService(RuntimeService activitiRuntimeService)
    {
        this.activitiRuntimeService = activitiRuntimeService;
    }

    public TaskService getActivitiTaskService()
    {
        return activitiTaskService;
    }

    public void setActivitiTaskService(TaskService activitiTaskService)
    {
        this.activitiTaskService = activitiTaskService;
    }

    public RepositoryService getActivitiRepositoryService()
    {
        return activitiRepositoryService;
    }

    public void setActivitiRepositoryService(RepositoryService activitiRepositoryService)
    {
        this.activitiRepositoryService = activitiRepositoryService;
    }

    public void setActivitiHistoryService(HistoryService activitiHistoryService)
    {
        this.activitiHistoryService = activitiHistoryService;
    }

    public HistoryService getActivitiHistoryService()
    {
        return activitiHistoryService;
    }

    public Map<String, Integer> getPriorityLevelToNumberMap()
    {
        return priorityLevelToNumberMap;
    }

    public void setPriorityLevelToNumberMap(Map<String, Integer> priorityLevelToNumberMap)
    {
        this.priorityLevelToNumberMap = priorityLevelToNumberMap;
    }

    public Map<String, List<String>> getRequiredFieldsPerOutcomeMap()
    {
        return requiredFieldsPerOutcomeMap;
    }

    public void setRequiredFieldsPerOutcomeMap(Map<String, List<String>> requiredFieldsPerOutcomeMap)
    {
        this.requiredFieldsPerOutcomeMap = requiredFieldsPerOutcomeMap;
    }

    public UserDao getUserDao()
    {
        return userDao;
    }

    public void setUserDao(UserDao userDao)
    {
        this.userDao = userDao;
    }

    public AcmParticipantDao getParticipantDao()
    {
        return participantDao;
    }

    public void setParticipantDao(AcmParticipantDao participantDao)
    {
        this.participantDao = participantDao;
    }

    public DataAccessPrivilegeListener getDataAccessPrivilegeListener()
    {
        return dataAccessPrivilegeListener;
    }

    public void setDataAccessPrivilegeListener(DataAccessPrivilegeListener dataAccessPrivilegeListener)
    {
        this.dataAccessPrivilegeListener = dataAccessPrivilegeListener;
    }

    public ExtractAcmTaskFromEvent getTaskExtractor()
    {
        return taskExtractor;
    }

    public void setTaskExtractor(ExtractAcmTaskFromEvent taskExtractor)
    {
        this.taskExtractor = taskExtractor;
    }

    public TaskBusinessRule getTaskBusinessRule()
    {
        return taskBusinessRule;
    }

    public void setTaskBusinessRule(TaskBusinessRule taskBusinessRule)
    {
        this.taskBusinessRule = taskBusinessRule;
    }

    public EcmFileService getFileService()
    {
        return fileService;
    }

    public void setFileService(EcmFileService fileService)
    {
        this.fileService = fileService;
    }

    public AcmContainerDao getContainerFolderDao()
    {
        return containerFolderDao;
    }

    public void setContainerFolderDao(AcmContainerDao containerFolderDao)
    {
        this.containerFolderDao = containerFolderDao;
    }

    public EcmFileDao getFileDao()
    {
        return fileDao;
    }

    public void setFileDao(EcmFileDao fileDao)
    {
        this.fileDao = fileDao;
    }

    public AuditPropertyEntityAdapter getAuditPropertyEntityAdapter()
    {
        return auditPropertyEntityAdapter;
    }

    public void setAuditPropertyEntityAdapter(AuditPropertyEntityAdapter auditPropertyEntityAdapter)
    {
        this.auditPropertyEntityAdapter = auditPropertyEntityAdapter;
    }

    public TaskEventPublisher getTaskEventPublisher()
    {
        return taskEventPublisher;
    }

    public void setTaskEventPublisher(TaskEventPublisher taskEventPublisher)
    {
        this.taskEventPublisher = taskEventPublisher;
    }
}
