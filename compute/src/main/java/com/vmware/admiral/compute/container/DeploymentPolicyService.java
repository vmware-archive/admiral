/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute.container;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Help service to add a container host and validate container host address.
 */
public class DeploymentPolicyService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.DEPLOYMENT_POLICIES;

    public static class DeploymentPolicy extends MultiTenantDocument {

        @UsageOption(option = PropertyUsageOption.REQUIRED)
        public String name;
        public String description;
    }

    public DeploymentPolicyService() {
        super(DeploymentPolicy.class);

        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleDelete(Operation delete) {
        Operation[] ops = new Operation[] {
            createReferenceCountOperation(ComputeState.class, QuerySpecification.buildCompositeFieldName(
                    ComputeState.FIELD_NAME_CUSTOM_PROPERTIES, ContainerHostService.CUSTOM_PROPERTY_DEPLOYMENT_POLICY)),
            createReferenceCountOperation(GroupResourcePlacementState.class,
                    GroupResourcePlacementState.FIELD_NAME_DEPLOYMENT_POLICY_LINK)
        };

        OperationJoin.create(ops).setCompletion((os, es) -> {
            if (es != null && !es.isEmpty()) {
                logWarning(Utils.toString(es));
                delete.fail(es.values().iterator().next());
                return;
            }
            for (Operation o : os.values()) {
                ServiceDocumentQueryResult result = o.getBody(QueryTask.class).results;
                if (result.documentCount != 0) {
                    delete.fail(new IllegalStateException("Deployment Policy is in use"));
                    return;
                }
            }
            super.handleDelete(delete);
        }).sendWith(getHost());
    }

    @Override
    public void handlePut(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("DeploymentPolicy body is required"));
            return;
        }

        DeploymentPolicy deploymentPolicy = op.getBody(DeploymentPolicy.class);

        try {
            this.setState(op, deploymentPolicy);
            op.setBody(null).complete();
        } catch (Throwable e) {
            op.fail(e);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        DeploymentPolicy currentState = getState(patch);
        DeploymentPolicy patchBody = patch.getBody(DeploymentPolicy.class);
        if (patchBody.name != null) {
            currentState.name = patchBody.name;
        }
        if (patchBody.description != null) {
            currentState.description = patchBody.description;
        }
        patch.setBody(currentState).complete();
    }

    private Operation createReferenceCountOperation(Class<? extends ServiceDocument> stateClass, String propertyName) {
        QueryTask computeTask = QueryUtil.buildPropertyQuery(stateClass, propertyName, getSelfLink());
        QueryUtil.addCountOption(computeTask);
        return Operation.createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(computeTask).setReferer(getUri());
    }

    @Override
    public void handleStart(Operation start) {
        try {
            validate(start);
        } catch (Throwable t) {
            start.fail(t);
        }
        super.handleStart(start);
    }

    private void validate(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        DeploymentPolicy state = op.getBody(DeploymentPolicy.class);
        Utils.validateState(getStateDescription(), state);
    }
}
