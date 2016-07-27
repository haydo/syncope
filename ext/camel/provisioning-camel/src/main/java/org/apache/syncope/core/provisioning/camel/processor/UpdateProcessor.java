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
package org.apache.syncope.core.provisioning.camel.processor;

import java.util.List;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.syncope.common.lib.patch.AnyObjectPatch;
import org.apache.syncope.common.lib.patch.AnyPatch;
import org.apache.syncope.common.lib.patch.UserPatch;
import org.apache.syncope.common.lib.types.AnyTypeKind;
import org.apache.syncope.core.persistence.api.entity.task.PropagationTask;
import org.apache.syncope.core.provisioning.api.VirAttrHandler;
import org.apache.syncope.core.provisioning.api.WorkflowResult;
import org.apache.syncope.core.provisioning.api.propagation.PropagationManager;
import org.apache.syncope.core.provisioning.api.propagation.PropagationReporter;
import org.apache.syncope.core.provisioning.api.propagation.PropagationTaskExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UpdateProcessor implements Processor {

    @Autowired
    protected PropagationManager propagationManager;

    @Autowired
    protected PropagationTaskExecutor taskExecutor;

    @Autowired
    protected VirAttrHandler virtAttrHandler;

    @Override
    @SuppressWarnings("unchecked")
    public void process(final Exchange exchange) {
        if ((exchange.getIn().getBody() instanceof WorkflowResult)) {
            Object actual = exchange.getProperty("actual");
            Boolean nullPriorityAsync = exchange.getProperty("nullPriorityAsync", Boolean.class);
            Set<String> excludedResources = exchange.getProperty("excludedResources", Set.class);
            
            if (actual instanceof UserPatch) {
                WorkflowResult<Pair<UserPatch, Boolean>> updated =
                        (WorkflowResult<Pair<UserPatch, Boolean>>) exchange.getIn().getBody();
        
                List<PropagationTask> tasks = propagationManager.getUserUpdateTasks(updated);
                PropagationReporter propagationReporter = taskExecutor.execute(tasks, nullPriorityAsync);

                exchange.getOut().setBody(new ImmutablePair<>(
                        updated.getResult().getKey().getKey(), propagationReporter.getStatuses()));
            } else if (actual instanceof AnyPatch) {
                WorkflowResult<String> updated = (WorkflowResult<String>) exchange.getIn().getBody();

                AnyTypeKind anyTypeKind = AnyTypeKind.GROUP;
                if (actual instanceof AnyObjectPatch) {
                    anyTypeKind = AnyTypeKind.ANY_OBJECT;
                }

                List<PropagationTask> tasks = propagationManager.getUpdateTasks(
                        anyTypeKind,
                        updated.getResult(),
                        false,
                        null,
                        updated.getPropByRes(),
                        ((AnyPatch) actual).getVirAttrs(),
                        excludedResources);
                PropagationReporter propagationReporter = taskExecutor.execute(tasks, nullPriorityAsync);

                exchange.getOut().setBody(new ImmutablePair<>(updated.getResult(), propagationReporter.getStatuses()));
            }
        }
    }
}
