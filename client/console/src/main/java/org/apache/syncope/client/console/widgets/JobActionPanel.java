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
package org.apache.syncope.client.console.widgets;

import java.io.Serializable;
import org.apache.commons.lang3.StringUtils;
import org.apache.syncope.client.console.SyncopeConsoleSession;
import org.apache.syncope.client.console.commons.Constants;
import org.apache.syncope.client.console.pages.BasePage;
import org.apache.syncope.client.console.reports.ReportWizardBuilder;
import org.apache.syncope.client.console.rest.ReportRestClient;
import org.apache.syncope.client.console.rest.TaskRestClient;
import org.apache.syncope.client.console.tasks.SchedTaskWizardBuilder;
import org.apache.syncope.client.console.wicket.ajax.markup.html.IndicatorAjaxLink;
import org.apache.syncope.client.console.wicket.markup.html.bootstrap.dialog.BaseModal;
import org.apache.syncope.client.console.wizards.AjaxWizard;
import org.apache.syncope.client.console.wizards.WizardMgtPanel;
import org.apache.syncope.common.lib.to.JobTO;
import org.apache.syncope.common.lib.to.ReportTO;
import org.apache.syncope.common.lib.to.SchedTaskTO;
import org.apache.syncope.common.lib.types.JobAction;
import org.apache.syncope.common.lib.types.JobType;
import org.apache.syncope.common.rest.api.service.NotificationService;
import org.apache.syncope.common.rest.api.service.ReportService;
import org.apache.syncope.common.rest.api.service.TaskService;
import org.apache.wicket.PageReference;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.event.Broadcast;
import org.apache.wicket.event.IEvent;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.StringResourceModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobActionPanel extends WizardMgtPanel<Serializable> {

    private static final long serialVersionUID = 6645135178773151224L;

    private static final Logger LOG = LoggerFactory.getLogger(JobActionPanel.class);

    private final BaseModal<Serializable> jobModal;

    public JobActionPanel(final String id, final JobTO jobTO, final JobWidget widget,
            final BaseModal<Serializable> jobModal, final PageReference pageRef) {
        super(id, true);
        this.jobModal = jobModal;
        setOutputMarkupId(true);
        setWindowClosedReloadCallback(modal);

        IndicatorAjaxLink<Void> link = new IndicatorAjaxLink<Void>("edit") {

            private static final long serialVersionUID = -7978723352517770644L;

            @Override
            public void onClick(final AjaxRequestTarget target) {

                if (null != jobTO.getType()) {
                    switch (jobTO.getType()) {

                        case NOTIFICATION:
                            break;

                        case REPORT:

                            final ReportTO reportTO = new ReportRestClient().read(jobTO.getRefKey());

                            final ReportWizardBuilder rwb = new ReportWizardBuilder(reportTO, pageRef);
                            rwb.setEventSink(JobActionPanel.this);

                            target.add(jobModal.setContent(rwb.build(BaseModal.CONTENT_ID, AjaxWizard.Mode.EDIT)));

                            jobModal.header(new StringResourceModel(
                                    "any.edit",
                                    this,
                                    new Model<>(reportTO)));

                            jobModal.show(true);

                            break;

                        case TASK:

                            final SchedTaskTO schedTaskTO = new TaskRestClient().
                                    readSchedTask(SchedTaskTO.class, jobTO.getRefKey());

                            final SchedTaskWizardBuilder swb = new SchedTaskWizardBuilder(schedTaskTO, pageRef);
                            swb.setEventSink(JobActionPanel.this);

                            target.add(jobModal.setContent(swb.build(BaseModal.CONTENT_ID, AjaxWizard.Mode.EDIT)));

                            jobModal.header(new StringResourceModel(
                                    "any.edit",
                                    this,
                                    new Model<>(schedTaskTO)));

                            jobModal.show(true);

                            break;

                        default:
                            break;
                    }
                }
            }
        };
        link.setOutputMarkupPlaceholderTag(true);
        link.setVisible(!(null != jobTO.getType() && JobType.NOTIFICATION.equals(jobTO.getType())));
        addInnerObject(link);

        Fragment controls;
        if (jobTO.isRunning()) {
            controls = new Fragment("controls", "runningFragment", this);
            controls.add(new IndicatorAjaxLink<Void>("stop") {

                private static final long serialVersionUID = -7978723352517770644L;

                @Override
                public void onClick(final AjaxRequestTarget target) {
                    try {
                        switch (jobTO.getType()) {
                            case NOTIFICATION:
                                SyncopeConsoleSession.get().getService(NotificationService.class).
                                        actionJob(JobAction.STOP);
                                break;

                            case REPORT:
                                SyncopeConsoleSession.get().getService(ReportService.class).
                                        actionJob(jobTO.getRefKey(), JobAction.STOP);
                                break;

                            case TASK:
                                SyncopeConsoleSession.get().getService(TaskService.class).
                                        actionJob(jobTO.getRefKey(), JobAction.STOP);
                                break;

                            default:
                        }
                        SyncopeConsoleSession.get().info(getString(Constants.OPERATION_SUCCEEDED));
                        send(widget, Broadcast.EXACT, new JobActionPayload(target));
                    } catch (Exception e) {
                        LOG.error("While stopping {}", jobTO.getRefDesc(), e);
                        SyncopeConsoleSession.get().error(StringUtils.isBlank(e.getMessage()) ? e.getClass().getName()
                                : e.getMessage());
                    }
                    ((BasePage) getPage()).getNotificationPanel().refresh(target);
                }
            });
        } else {
            controls = new Fragment("controls", "notRunningFragment", this);
            controls.add(new IndicatorAjaxLink<Void>("start") {

                private static final long serialVersionUID = -7978723352517770644L;

                @Override
                public void onClick(final AjaxRequestTarget target) {
                    try {
                        switch (jobTO.getType()) {
                            case NOTIFICATION:
                                SyncopeConsoleSession.get().getService(NotificationService.class).
                                        actionJob(JobAction.START);
                                break;

                            case REPORT:
                                SyncopeConsoleSession.get().getService(ReportService.class).
                                        actionJob(jobTO.getRefKey(), JobAction.START);
                                break;

                            case TASK:
                                SyncopeConsoleSession.get().getService(TaskService.class).
                                        actionJob(jobTO.getRefKey(), JobAction.START);
                                break;

                            default:
                        }
                        SyncopeConsoleSession.get().info(getString(Constants.OPERATION_SUCCEEDED));
                        send(widget, Broadcast.EXACT, new JobActionPayload(target));
                    } catch (Exception e) {
                        LOG.error("While starting {}", jobTO.getRefDesc(), e);
                        SyncopeConsoleSession.get().error(StringUtils.isBlank(e.getMessage()) ? e.getClass().getName()
                                : e.getMessage());
                    }
                    ((BasePage) getPage()).getNotificationPanel().refresh(target);
                }
            });
        }
        addInnerObject(controls);
    }

    public static class JobActionPayload implements Serializable {

        private static final long serialVersionUID = -6798174303329212126L;

        private final AjaxRequestTarget target;

        public JobActionPayload(final AjaxRequestTarget target) {
            this.target = target;
        }

        public AjaxRequestTarget getTarget() {
            return target;
        }

    }

    @Override
    @SuppressWarnings("unchecked")
    public void onEvent(final IEvent<?> event) {
        if (event.getPayload() instanceof AjaxWizard.NewItemEvent) {
            final AjaxRequestTarget target = AjaxWizard.NewItemEvent.class.cast(event.getPayload()).getTarget();

            if (event.getPayload() instanceof AjaxWizard.NewItemCancelEvent
                    || event.getPayload() instanceof AjaxWizard.NewItemFinishEvent) {
                jobModal.close(target);
            }
        }

        super.onEvent(event);
    }
}
