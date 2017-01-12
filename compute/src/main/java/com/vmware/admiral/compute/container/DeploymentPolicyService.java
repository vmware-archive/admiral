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

import java.util.concurrent.atomic.AtomicInteger;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

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
        QueryTask computeTask = QueryUtil.buildPropertyQuery(ComputeState.class,
                QuerySpecification.buildCompositeFieldName(
                        ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        ContainerHostService.CUSTOM_PROPERTY_DEPLOYMENT_POLICY),
                getSelfLink());
        QueryUtil.addCountOption(computeTask);

        QueryTask groupResourcePlacementTask = QueryUtil.buildPropertyQuery(
                GroupResourcePlacementState.class,
                GroupResourcePlacementState.FIELD_NAME_DEPLOYMENT_POLICY_LINK, getSelfLink());
        QueryUtil.addCountOption(computeTask);

        AtomicInteger ac = new AtomicInteger(2);
        new ServiceDocumentQuery<>(
                getHost(), ComputeState.class).query(computeTask,
                        (r) -> {
                            if (r.hasException()) {
                                logWarning(Utils.toString(r.getException()));
                                delete.fail(r.getException());
                                return;
                            } else if (r.hasResult() && r.getCount() != 0) {
                                delete.fail(
                                        new LocalizableValidationException("Deployment Policy is in use",
                                                "compute.deployment-policy.in.use"));

                            } else {
                                if (ac.decrementAndGet() <= 0) {
                                    super.handleDelete(delete);
                                }
                            }
                        });

        new ServiceDocumentQuery<>(
                getHost(), GroupResourcePlacementState.class).query(groupResourcePlacementTask,
                        (r) -> {
                            if (r.hasException()) {
                                logWarning(Utils.toString(r.getException()));
                                delete.fail(r.getException());
                                return;
                            } else if (r.hasResult() && r.getCount() != 0) {
                                delete.fail(
                                        new LocalizableValidationException("Deployment Policy is in use",
                                                "compute.deployment-policy.in.use"));
                            } else {
                                if (ac.decrementAndGet() <= 0) {
                                    super.handleDelete(delete);
                                }
                            }
                        });
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

    @Override
    public void handleCreate(Operation start) {
        validate(start);
        start.complete();
    }

    private void validate(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        DeploymentPolicy state = op.getBody(DeploymentPolicy.class);
        Utils.validateState(getStateDescription(), state);
    }
}
