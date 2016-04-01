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
package org.apache.syncope.client.console.approvals;

import java.util.List;
import org.apache.syncope.client.console.panels.MultilevelPanel;
import org.apache.syncope.client.console.rest.AnyTypeRestClient;
import org.apache.syncope.client.console.rest.UserRestClient;
import org.apache.syncope.client.console.wizards.AjaxWizard;
import org.apache.syncope.client.console.wizards.any.AnyHandler;
import org.apache.syncope.client.console.wizards.any.UserWizardBuilder;
import org.apache.syncope.common.lib.to.UserTO;
import org.apache.syncope.common.lib.to.WorkflowFormTO;
import org.apache.syncope.common.lib.types.AnyTypeKind;
import org.apache.wicket.PageReference;

public class ApprovalDetails extends MultilevelPanel.SecondLevel {

    private static final long serialVersionUID = -8847854414429745216L;

    public ApprovalDetails(final PageReference pageRef, final WorkflowFormTO formTO) {
        super(MultilevelPanel.SECOND_LEVEL_ID);

        final UserTO userTO = new UserRestClient().read(formTO.getUserKey());
        final List<String> anyTypeClasses = new AnyTypeRestClient().get(AnyTypeKind.USER.name()).getClasses();

        final AjaxWizard<AnyHandler<UserTO>> wizard
                = new UserWizardBuilder("wizard", userTO, anyTypeClasses, pageRef).build(AjaxWizard.Mode.READONLY);

        add(wizard);
    }
}