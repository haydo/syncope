/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.syncope.core.provisioning.java.data;

import java.util.stream.Collectors;
import org.apache.syncope.core.provisioning.api.data.TaskDataBinder;
import org.apache.commons.lang3.StringUtils;
import org.apache.syncope.common.lib.SyncopeClientException;
import org.apache.syncope.common.lib.to.AbstractProvisioningTaskTO;
import org.apache.syncope.common.lib.to.AbstractTaskTO;
import org.apache.syncope.common.lib.to.PropagationTaskTO;
import org.apache.syncope.common.lib.to.PushTaskTO;
import org.apache.syncope.common.lib.to.SchedTaskTO;
import org.apache.syncope.common.lib.to.PullTaskTO;
import org.apache.syncope.common.lib.to.ExecTO;
import org.apache.syncope.common.lib.to.NotificationTaskTO;
import org.apache.syncope.common.lib.types.ClientExceptionType;
import org.apache.syncope.common.lib.types.ImplementationEngine;
import org.apache.syncope.common.lib.types.ImplementationType;
import org.apache.syncope.common.lib.types.JobType;
import org.apache.syncope.common.lib.types.MatchingRule;
import org.apache.syncope.common.lib.types.TaskType;
import org.apache.syncope.common.lib.types.UnmatchingRule;
import org.apache.syncope.core.provisioning.java.utils.TemplateUtils;
import org.apache.syncope.core.persistence.api.dao.ExternalResourceDAO;
import org.apache.syncope.core.persistence.api.dao.NotFoundException;
import org.apache.syncope.core.persistence.api.dao.TaskExecDAO;
import org.apache.syncope.core.persistence.api.entity.task.NotificationTask;
import org.apache.syncope.core.persistence.api.entity.task.PropagationTask;
import org.apache.syncope.core.persistence.api.entity.task.ProvisioningTask;
import org.apache.syncope.core.persistence.api.entity.task.PushTask;
import org.apache.syncope.core.persistence.api.entity.task.SchedTask;
import org.apache.syncope.core.persistence.api.entity.task.Task;
import org.apache.syncope.core.persistence.api.entity.task.TaskExec;
import org.apache.syncope.core.persistence.api.entity.task.TaskUtils;
import org.apache.syncope.core.provisioning.api.job.JobNamer;
import org.apache.syncope.core.spring.BeanUtils;
import org.apache.syncope.core.persistence.api.dao.AnyTypeDAO;
import org.apache.syncope.core.persistence.api.dao.ImplementationDAO;
import org.apache.syncope.core.persistence.api.dao.RealmDAO;
import org.apache.syncope.core.persistence.api.entity.AnyType;
import org.apache.syncope.core.persistence.api.entity.Entity;
import org.apache.syncope.core.persistence.api.entity.EntityFactory;
import org.apache.syncope.core.persistence.api.entity.Implementation;
import org.apache.syncope.core.persistence.api.entity.resource.ExternalResource;
import org.apache.syncope.core.persistence.api.entity.task.AnyTemplatePullTask;
import org.apache.syncope.core.persistence.api.entity.task.PullTask;
import org.apache.syncope.core.provisioning.java.pushpull.PushJobDelegate;
import org.apache.syncope.core.provisioning.java.pushpull.PullJobDelegate;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Component;
import org.apache.syncope.core.persistence.api.entity.task.PushTaskAnyFilter;
import org.apache.syncope.core.persistence.api.entity.task.TaskUtilsFactory;

@Component
public class TaskDataBinderImpl implements TaskDataBinder {

    private static final Logger LOG = LoggerFactory.getLogger(TaskDataBinder.class);

    private static final String[] IGNORE_TASK_PROPERTIES = {
        "destinationRealm", "templates", "filters", "executions", "resource", "matchingRule", "unmatchingRule",
        "notification", "jobDelegate", "actions" };

    private static final String[] IGNORE_TASK_EXECUTION_PROPERTIES = { "key", "task" };

    @Autowired
    private RealmDAO realmDAO;

    @Autowired
    private ExternalResourceDAO resourceDAO;

    @Autowired
    private TaskExecDAO taskExecDAO;

    @Autowired
    private AnyTypeDAO anyTypeDAO;

    @Autowired
    private ImplementationDAO implementationDAO;

    @Autowired
    private EntityFactory entityFactory;

    @Autowired
    private TemplateUtils templateUtils;

    @Autowired
    private SchedulerFactoryBean scheduler;

    @Autowired
    private TaskUtilsFactory taskUtilsFactory;

    private void fill(final ProvisioningTask task, final AbstractProvisioningTaskTO taskTO) {
        if (task instanceof PushTask && taskTO instanceof PushTaskTO) {
            PushTask pushTask = (PushTask) task;
            PushTaskTO pushTaskTO = (PushTaskTO) taskTO;

            Implementation jobDelegate = pushTaskTO.getJobDelegate() == null
                    ? implementationDAO.find(ImplementationType.TASKJOB_DELEGATE).stream().
                            filter(impl -> PushJobDelegate.class.getName().equals(impl.getBody())).
                            findFirst().orElse(null)
                    : implementationDAO.find(pushTaskTO.getJobDelegate());
            if (jobDelegate == null) {
                jobDelegate = entityFactory.newEntity(Implementation.class);
                jobDelegate.setKey(PushJobDelegate.class.getSimpleName());
                jobDelegate.setEngine(ImplementationEngine.JAVA);
                jobDelegate.setType(ImplementationType.TASKJOB_DELEGATE);
                jobDelegate.setBody(PushJobDelegate.class.getName());
                jobDelegate = implementationDAO.save(jobDelegate);
            }
            pushTask.setJobDelegate(jobDelegate);

            pushTask.setSourceRealm(realmDAO.findByFullPath(pushTaskTO.getSourceRealm()));

            pushTask.setMatchingRule(pushTaskTO.getMatchingRule() == null
                    ? MatchingRule.LINK : pushTaskTO.getMatchingRule());
            pushTask.setUnmatchingRule(pushTaskTO.getUnmatchingRule() == null
                    ? UnmatchingRule.ASSIGN : pushTaskTO.getUnmatchingRule());

            pushTaskTO.getFilters().entrySet().forEach(entry -> {
                AnyType type = anyTypeDAO.find(entry.getKey());
                if (type == null) {
                    LOG.debug("Invalid AnyType {} specified, ignoring...", entry.getKey());
                } else {
                    PushTaskAnyFilter filter = pushTask.getFilter(type).orElse(null);
                    if (filter == null) {
                        filter = entityFactory.newEntity(PushTaskAnyFilter.class);
                        filter.setAnyType(anyTypeDAO.find(entry.getKey()));
                        filter.setPushTask(pushTask);
                        pushTask.add(filter);
                    }
                    filter.setFIQLCond(entry.getValue());
                }
            });
            // remove all filters not contained in the TO
            pushTask.getFilters().removeAll(
                    pushTask.getFilters().stream().filter(anyFilter
                            -> !pushTaskTO.getFilters().containsKey(anyFilter.getAnyType().getKey())).
                            collect(Collectors.toList()));
        } else if (task instanceof PullTask && taskTO instanceof PullTaskTO) {
            PullTask pullTask = (PullTask) task;
            PullTaskTO pullTaskTO = (PullTaskTO) taskTO;

            Implementation jobDelegate = pullTaskTO.getJobDelegate() == null
                    ? implementationDAO.find(ImplementationType.TASKJOB_DELEGATE).stream().
                            filter(impl -> PullJobDelegate.class.getName().equals(impl.getBody())).
                            findFirst().orElse(null)
                    : implementationDAO.find(pullTaskTO.getJobDelegate());
            if (jobDelegate == null) {
                jobDelegate = entityFactory.newEntity(Implementation.class);
                jobDelegate.setKey(PullJobDelegate.class.getSimpleName());
                jobDelegate.setEngine(ImplementationEngine.JAVA);
                jobDelegate.setType(ImplementationType.TASKJOB_DELEGATE);
                jobDelegate.setBody(PullJobDelegate.class.getName());
                jobDelegate = implementationDAO.save(jobDelegate);
            }
            pullTask.setJobDelegate(jobDelegate);

            pullTask.setPullMode(pullTaskTO.getPullMode());

            if (pullTaskTO.getReconFilterBuilder() == null) {
                pullTask.setReconFilterBuilder(null);
            } else {
                Implementation reconFilterBuilder = implementationDAO.find(pullTaskTO.getReconFilterBuilder());
                if (reconFilterBuilder == null) {
                    LOG.debug("Invalid " + Implementation.class.getSimpleName() + " {}, ignoring...",
                            pullTaskTO.getReconFilterBuilder());
                } else {
                    pullTask.setReconFilterBuilder(reconFilterBuilder);
                }
            }

            pullTask.setDestinationRealm(realmDAO.findByFullPath(pullTaskTO.getDestinationRealm()));

            pullTask.setMatchingRule(pullTaskTO.getMatchingRule() == null
                    ? MatchingRule.UPDATE : pullTaskTO.getMatchingRule());
            pullTask.setUnmatchingRule(pullTaskTO.getUnmatchingRule() == null
                    ? UnmatchingRule.PROVISION : pullTaskTO.getUnmatchingRule());

            // validate JEXL expressions from templates and proceed if fine
            templateUtils.check(pullTaskTO.getTemplates(), ClientExceptionType.InvalidPullTask);
            pullTaskTO.getTemplates().entrySet().forEach(entry -> {
                AnyType type = anyTypeDAO.find(entry.getKey());
                if (type == null) {
                    LOG.debug("Invalid AnyType {} specified, ignoring...", entry.getKey());
                } else {
                    AnyTemplatePullTask anyTemplate = pullTask.getTemplate(type).orElse(null);
                    if (anyTemplate == null) {
                        anyTemplate = entityFactory.newEntity(AnyTemplatePullTask.class);
                        anyTemplate.setAnyType(type);
                        anyTemplate.setPullTask(pullTask);

                        pullTask.add(anyTemplate);
                    }
                    anyTemplate.set(entry.getValue());
                }
            });
            // remove all templates not contained in the TO
            pullTask.getTemplates().removeAll(
                    pullTask.getTemplates().stream().filter(anyTemplate
                            -> !pullTaskTO.getTemplates().containsKey(anyTemplate.getAnyType().getKey())).
                            collect(Collectors.toList()));
        }

        // 3. fill the remaining fields
        task.setPerformCreate(taskTO.isPerformCreate());
        task.setPerformUpdate(taskTO.isPerformUpdate());
        task.setPerformDelete(taskTO.isPerformDelete());
        task.setSyncStatus(taskTO.isSyncStatus());

        taskTO.getActions().forEach(action -> {
            Implementation implementation = implementationDAO.find(action);
            if (implementation == null) {
                LOG.debug("Invalid " + Implementation.class.getSimpleName() + " {}, ignoring...", action);
            } else {
                task.add(implementation);
            }
        });
        // remove all implementations not contained in the TO
        task.getActions().removeAll(task.getActions().stream().
                filter(implementation -> !taskTO.getActions().contains(implementation.getKey())).
                collect(Collectors.toList()));
    }

    @Override
    public SchedTask createSchedTask(final SchedTaskTO taskTO, final TaskUtils taskUtils) {
        Class<? extends AbstractTaskTO> taskTOClass = taskUtils.taskTOClass();
        if (taskTOClass == null || !taskTOClass.equals(taskTO.getClass())) {
            throw new IllegalArgumentException(String.format("Expected %s, found %s", taskTOClass, taskTO.getClass()));
        }

        SchedTask task = taskUtils.newTask();
        task.setStartAt(taskTO.getStartAt());
        task.setCronExpression(taskTO.getCronExpression());
        task.setName(taskTO.getName());
        task.setDescription(taskTO.getDescription());
        task.setActive(taskTO.isActive());

        if (taskUtils.getType() == TaskType.SCHEDULED) {
            Implementation implementation = implementationDAO.find(taskTO.getJobDelegate());
            if (implementation == null) {
                throw new NotFoundException("Implementation " + taskTO.getJobDelegate());
            }
            task.setJobDelegate(implementation);
        } else if (taskTO instanceof AbstractProvisioningTaskTO) {
            AbstractProvisioningTaskTO provisioningTaskTO = (AbstractProvisioningTaskTO) taskTO;

            ExternalResource resource = resourceDAO.find(provisioningTaskTO.getResource());
            if (resource == null) {
                throw new NotFoundException("Resource " + provisioningTaskTO.getResource());
            }
            ((ProvisioningTask) task).setResource(resource);

            fill((ProvisioningTask) task, provisioningTaskTO);
        }

        return task;
    }

    @Override
    public void updateSchedTask(final SchedTask task, final SchedTaskTO taskTO, final TaskUtils taskUtils) {
        Class<? extends AbstractTaskTO> taskTOClass = taskUtils.taskTOClass();
        if (taskTOClass == null || !taskTOClass.equals(taskTO.getClass())) {
            throw new IllegalArgumentException(String.format("Expected %s, found %s", taskTOClass, taskTO.getClass()));
        }

        if (StringUtils.isBlank(taskTO.getName())) {
            SyncopeClientException sce = SyncopeClientException.build(ClientExceptionType.RequiredValuesMissing);
            sce.getElements().add("name");
            throw sce;
        }
        task.setName(taskTO.getName());
        task.setDescription(taskTO.getDescription());
        task.setCronExpression(taskTO.getCronExpression());
        task.setActive(taskTO.isActive());

        if (task instanceof ProvisioningTask) {
            fill((ProvisioningTask) task, (AbstractProvisioningTaskTO) taskTO);
        }
    }

    @Override
    public String buildRefDesc(final Task task) {
        return taskUtilsFactory.getInstance(task).getType().name() + " "
                + "Task "
                + task.getKey() + " "
                + (task instanceof SchedTask
                        ? SchedTask.class.cast(task).getName()
                        : task instanceof PropagationTask
                                ? PropagationTask.class.cast(task).getConnObjectKey()
                                : StringUtils.EMPTY);
    }

    @Override
    public ExecTO getExecTO(final TaskExec execution) {
        ExecTO execTO = new ExecTO();
        BeanUtils.copyProperties(execution, execTO, IGNORE_TASK_EXECUTION_PROPERTIES);

        if (execution.getKey() != null) {
            execTO.setKey(execution.getKey());
        }

        if (execution.getTask() != null && execution.getTask().getKey() != null) {
            execTO.setJobType(JobType.TASK);
            execTO.setRefKey(execution.getTask().getKey());
            execTO.setRefDesc(buildRefDesc(execution.getTask()));
        }

        return execTO;
    }

    private void setExecTime(final SchedTaskTO taskTO, final Task task) {
        taskTO.setLastExec(taskTO.getStart());

        String triggerName = JobNamer.getTriggerName(JobNamer.getJobKey(task).getName());
        try {
            Trigger trigger = scheduler.getScheduler().getTrigger(new TriggerKey(triggerName, Scheduler.DEFAULT_GROUP));

            if (trigger != null) {
                taskTO.setLastExec(trigger.getPreviousFireTime());
                taskTO.setNextExec(trigger.getNextFireTime());
            }
        } catch (SchedulerException e) {
            LOG.warn("While trying to get to " + triggerName, e);
        }
    }

    @Override
    public <T extends AbstractTaskTO> T getTaskTO(final Task task, final TaskUtils taskUtils, final boolean details) {
        T taskTO = taskUtils.newTaskTO();
        BeanUtils.copyProperties(task, taskTO, IGNORE_TASK_PROPERTIES);

        TaskExec latestExec = taskExecDAO.findLatestStarted(task);
        if (latestExec == null) {
            taskTO.setLatestExecStatus(StringUtils.EMPTY);
        } else {
            taskTO.setLatestExecStatus(latestExec.getStatus());
            taskTO.setStart(latestExec.getStart());
            taskTO.setEnd(latestExec.getEnd());
        }

        if (details) {
            task.getExecs().stream().
                    filter(execution -> execution != null).
                    forEachOrdered(execution -> taskTO.getExecutions().add(getExecTO(execution)));
        }

        switch (taskUtils.getType()) {
            case PROPAGATION:
                PropagationTask propagationTask = (PropagationTask) task;
                PropagationTaskTO propagationTaskTO = (PropagationTaskTO) taskTO;

                propagationTaskTO.setAnyTypeKind(propagationTask.getAnyTypeKind());
                propagationTaskTO.setEntityKey(propagationTask.getEntityKey());
                propagationTaskTO.setResource(propagationTask.getResource().getKey());
                propagationTaskTO.setAttributes(propagationTask.getSerializedAttributes());
                break;

            case SCHEDULED:
                SchedTask schedTask = (SchedTask) task;
                SchedTaskTO schedTaskTO = (SchedTaskTO) taskTO;

                setExecTime(schedTaskTO, task);

                if (schedTask.getJobDelegate() != null) {
                    schedTaskTO.setJobDelegate(schedTask.getJobDelegate().getKey());
                }
                break;

            case PULL:
                PullTask pullTask = (PullTask) task;
                PullTaskTO pullTaskTO = (PullTaskTO) taskTO;

                setExecTime(pullTaskTO, task);

                pullTaskTO.setDestinationRealm(pullTask.getDestinatioRealm().getFullPath());
                pullTaskTO.setResource(pullTask.getResource().getKey());
                pullTaskTO.setMatchingRule(pullTask.getMatchingRule() == null
                        ? MatchingRule.UPDATE : pullTask.getMatchingRule());
                pullTaskTO.setUnmatchingRule(pullTask.getUnmatchingRule() == null
                        ? UnmatchingRule.PROVISION : pullTask.getUnmatchingRule());

                if (pullTask.getReconFilterBuilder() != null) {
                    pullTaskTO.setReconFilterBuilder(pullTask.getReconFilterBuilder().getKey());
                }

                pullTaskTO.getActions().addAll(
                        pullTask.getActions().stream().map(Entity::getKey).collect(Collectors.toList()));

                pullTask.getTemplates().forEach(template -> {
                    pullTaskTO.getTemplates().put(template.getAnyType().getKey(), template.get());
                });
                break;

            case PUSH:
                PushTask pushTask = (PushTask) task;
                PushTaskTO pushTaskTO = (PushTaskTO) taskTO;

                setExecTime(pushTaskTO, task);

                pushTaskTO.setSourceRealm(pushTask.getSourceRealm().getFullPath());
                pushTaskTO.setResource(pushTask.getResource().getKey());
                pushTaskTO.setMatchingRule(pushTask.getMatchingRule() == null
                        ? MatchingRule.LINK : pushTask.getMatchingRule());
                pushTaskTO.setUnmatchingRule(pushTask.getUnmatchingRule() == null
                        ? UnmatchingRule.ASSIGN : pushTask.getUnmatchingRule());

                pushTaskTO.getActions().addAll(
                        pushTask.getActions().stream().map(Entity::getKey).collect(Collectors.toList()));

                pushTask.getFilters().forEach(filter -> {
                    pushTaskTO.getFilters().put(filter.getAnyType().getKey(), filter.getFIQLCond());
                });
                break;

            case NOTIFICATION:
                NotificationTask notificationTask = (NotificationTask) task;
                NotificationTaskTO notificationTaskTO = (NotificationTaskTO) taskTO;

                notificationTaskTO.setNotification(notificationTask.getNotification().getKey());
                notificationTaskTO.setAnyTypeKind(notificationTask.getAnyTypeKind());
                notificationTaskTO.setEntityKey(notificationTask.getEntityKey());
                if (notificationTask.isExecuted() && StringUtils.isBlank(taskTO.getLatestExecStatus())) {
                    taskTO.setLatestExecStatus("[EXECUTED]");
                }
                break;

            default:
        }

        return taskTO;
    }
}
