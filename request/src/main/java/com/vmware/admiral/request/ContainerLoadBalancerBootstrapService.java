/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request;

import java.util.logging.Level;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.request.ClusteringTaskService.ClusteringTaskState;
import com.vmware.admiral.service.common.ExtensibilitySubscriptionService;
import com.vmware.admiral.service.common.ExtensibilitySubscriptionService.ExtensibilitySubscription;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ContainerLoadBalancerBootstrapService extends StatefulService {

    public static final String FACTORY_LINK = ManagementUriParts.CONFIG +
            "/container-load-balancer-bootstrap";

    public ContainerLoadBalancerBootstrapService() {
        super(ServiceDocument.class);
        toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    public static FactoryService createFactory() {
        return FactoryService.create(ContainerLoadBalancerBootstrapService.class);
    }

    public static CompletionHandler startTask(ServiceHost host) {
        return (o, e) -> {
            if (e != null) {
                host.log(Level.SEVERE, Utils.toString(e));
                return;
            }

            ServiceDocument doc = new ServiceDocument();
            doc.documentSelfLink = "container-load-balancer-preparation-task";
            Operation.createPost(host, ContainerLoadBalancerBootstrapService.FACTORY_LINK)
                    .setBody(doc)
                    .setReferer(host.getUri())
                    .setCompletion((oo, ee) -> {
                        if (ee != null) {
                            host.log(Level.SEVERE, Utils.toString(ee));
                            return;
                        }
                        host.log(Level.INFO, "container-load-balancer-preparation-task triggered");
                    })
                    .sendWith(host);
        };
    }

    @Override
    public void handleStart(Operation post) {
        if (!ServiceHost.isServiceCreate(post)) {
            post.complete();
            return;
        }

        ExtensibilitySubscription state = new ExtensibilitySubscription();
        state.task = ClusteringTaskState.class.getSimpleName();
        state.stage = TaskStage.STARTED.name();
        state.substage = ClusteringTaskState.SubStage.COMPLETED.name();
        state.callbackReference = UriUtils
                .buildUri(getHost(), ContainerLoadBalancerReconfigureTaskService.FACTORY_LINK);
        state.blocking = false;

        Operation.createPost(getHost(), ExtensibilitySubscriptionService.FACTORY_LINK).setBody
                (state).setReferer(getHost().getUri()).setCompletion((o, e) -> {
                    if (e != null) {
                        post.fail(e);
                    } else {
                        post.complete();
                    }
                }).sendWith(getHost());
    }

    @Override
    public void handlePut(Operation put) {
        if (put.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_POST_TO_PUT)) {
            logInfo("Task has already started. Ignoring converted PUT.");
            put.complete();
            return;
        }
        put.fail(Operation.STATUS_CODE_BAD_METHOD);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        return template;
    }
}
