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

package com.vmware.admiral.closures.services.closuredescription;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import com.vmware.admiral.closures.drivers.DriverConstants;
import com.vmware.admiral.closures.util.ClosureProps;
import com.vmware.admiral.closures.util.ClosureUtils;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatefulService;

/**
 * Represents closure definition service.
 */
@SuppressWarnings("ALL")
public class ClosureDescriptionService extends StatefulService {

    public ClosureDescriptionService() {
        super(ClosureDescription.class);

        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleStart(Operation post) {
        logInfo("Handle post....");
        if (isBodyEmpty(post)) {
            return;
        }

        ClosureDescription body = post.getBody(ClosureDescription.class);
        logInfo("Closure source: %s, Closure source URL: %s, language: %s", body.source,
                body.sourceURL,
                body.runtime);

        if (!isValid(post, body)) {
            return;
        }

        verifyResourceConstraints(body);

        formatDependencies(body);

        if (body.outputNames == null) {
            body.outputNames = new ArrayList<>(0);
        }
        if (body.inputs == null) {
            body.inputs = new HashMap<>(0);
        }

        this.setState(post, body);
        post.setBody(body).complete();
    }

    private void formatDependencies(ClosureDescription body) {
        if (!ClosureUtils.isEmpty(body.dependencies) && body.runtime
                .equalsIgnoreCase(DriverConstants.RUNTIME_NODEJS_4)) {
            JsonParser parser = new JsonParser();
            JsonElement jsElement = parser.parse(body.dependencies);
            body.dependencies = jsElement.toString();
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        if (isBodyEmpty(patch)) {
            return;
        }

        ClosureDescription currentState = getState(patch);
        ClosureDescription patchedState = patch.getBody(ClosureDescription.class);
        logInfo("Closure source: %s Closure source URL: %s, language: %s", patchedState.source,
                patchedState.sourceURL, patchedState.runtime);

        if (patchedState.logConfiguration != null && !patchedState.logConfiguration.isJsonNull()) {
            currentState.logConfiguration = patchedState.logConfiguration;
        }

        patchedState.logConfiguration = null;
        PropertyUtils.mergeServiceDocuments(currentState, patchedState);

        if (!isValid(patch, currentState)) {
            return;
        }

        verifyResourceConstraints(currentState);

        formatDependencies(currentState);

        patch.setBody(currentState).complete();
    }

    @Override
    public void handleDelete(Operation delete) {

        logInfo("Deleting item: " + delete.getUri());

        delete.complete();

    }

    // PRIVATE METHODS

    private void verifyResourceConstraints(ClosureDescription body) {
        if (body.resources == null) {
            body.resources = createDefaultConstraints(body);
        } else {
            // Validate Memory & CPU resource constraints
            if (body.resources.ramMB < ClosureProps.MIN_MEMORY_MB_RES_CONSTRAINT) {
                logWarning(
                        "Closure definition memory is below allowed min: %s. Setting to min allowed: %s",
                        body
                                .resources.ramMB, ClosureProps.MIN_MEMORY_MB_RES_CONSTRAINT);
                body.resources.ramMB = ClosureProps.MIN_MEMORY_MB_RES_CONSTRAINT;
            } else if (body.resources.ramMB > ClosureProps.MAX_MEMORY_MB_RES_CONSTRAINT) {
                logWarning(
                        "Closure definition memory is above allowed max: %s. Setting to max allowed: %s",
                        body
                                .resources.ramMB, ClosureProps.MAX_MEMORY_MB_RES_CONSTRAINT);
                body.resources.ramMB = ClosureProps.MAX_MEMORY_MB_RES_CONSTRAINT;
            }

            // Calculate CPU shares based on memory
            body.resources.cpuShares = calculateCpuShares(body.resources.ramMB);
            logInfo("Calculated CPU shares: %s for memory used: %s", body.resources.ramMB,
                    body.resources
                            .cpuShares);

            // Validate execution Timeout
            if (body.resources.timeoutSeconds < ClosureProps.MIN_EXEC_TIMEOUT_SECONDS) {
                logWarning(
                        "Closure definition timeout is below allowed min: %s. Setting to min allowed: %s",
                        body
                                .resources.timeoutSeconds,
                        ClosureProps.MIN_EXEC_TIMEOUT_SECONDS);
                body.resources.timeoutSeconds = ClosureProps.MIN_EXEC_TIMEOUT_SECONDS;
            } else if (body.resources.timeoutSeconds
                    > ClosureProps.MAX_EXEC_TIMEOUT_SECONDS) {
                logWarning(
                        "Closure definition timeout is above the allowed max: %s. Setting to max allowed: %s",
                        body
                                .resources.timeoutSeconds,
                        ClosureProps.MAX_EXEC_TIMEOUT_SECONDS);
                body.resources.timeoutSeconds = ClosureProps.MAX_EXEC_TIMEOUT_SECONDS;
            }
        }

    }

    /**
     * Calculate CPU shares proportionally based on memory reservation.
     */
    private Integer calculateCpuShares(Integer ramMB) {
        double memPercent = (float) ramMB / ClosureProps.MAX_MEMORY_MB_RES_CONSTRAINT;

        int calculatedShares = (int) Math.round(memPercent * ClosureProps.DEFAULT_CPU_SHARES);

        if (calculatedShares < ClosureProps.MIN_CPU_SHARES) {
            calculatedShares = ClosureProps.MIN_CPU_SHARES;
        }

        return calculatedShares;
    }

    private ResourceConstraints createDefaultConstraints(ClosureDescription body) {
        return new ResourceConstraints();
    }

    private boolean isValid(Operation op, ClosureDescription body) {
        if (isRuntimeNotSupported(body)) {
            String errorMsg = String.format("Runtime '%s' is not supported!", body.runtime);
            logWarning(errorMsg);
            op.setStatusCode(Operation.STATUS_CODE_BAD_REQUEST);
            op.fail(new IllegalArgumentException(errorMsg));
            return false;
        }

        if (ClosureUtils.isEmpty(body.sourceURL) && ClosureUtils.isEmpty(body.source)) {
            String errorMsg = "Closure source or closure source URL is required";
            logWarning(errorMsg);
            op.setStatusCode(Operation.STATUS_CODE_BAD_REQUEST);
            op.fail(new IllegalArgumentException(errorMsg));
            return false;
        }

        if (ClosureUtils.isEmpty(body.name)) {
            String errorMsg = "Closure name is required.";
            logWarning(errorMsg);
            op.setStatusCode(Operation.STATUS_CODE_BAD_REQUEST);
            op.fail(new IllegalArgumentException("Closure name is required."));
            return false;
        }

        if (!ClosureUtils.isEmpty(body.entrypoint)) {
            if (body.entrypoint.indexOf('.') < 0) {
                String errorMsg = "Invalid format of Closure entrypoint provided. Valid format: module_name.handler_name";
                logWarning(errorMsg);
                op.setStatusCode(Operation.STATUS_CODE_BAD_REQUEST);
                op.fail(new IllegalArgumentException(errorMsg));
                return false;
            }
        }

        if (DriverConstants.RUNTIME_NODEJS_4.equalsIgnoreCase(body.runtime)
                && !ClosureUtils.isEmpty(body.dependencies)) {
            JsonParser parser = new JsonParser();
            try {
                parser.parse(body.dependencies);
            } catch (JsonSyntaxException ex) {
                logWarning("Invalid dependencies format: ", ex);
                op.setStatusCode(Operation.STATUS_CODE_BAD_REQUEST);
                op.fail(new IllegalArgumentException("Invalid JSON format: " + ex.getMessage()));
            }
        }

        return true;
    }

    private boolean isRuntimeNotSupported(ClosureDescription body) {
        if (Objects.equals(body.runtime, DriverConstants.RUNTIME_NODEJS_4)) {
            return false;
        } else if (Objects.equals(body.runtime, DriverConstants.RUNTIME_PYTHON_3)) {
            return false;
        } else if (Objects.equals(body.runtime, DriverConstants.RUNTIME_NASHORN)) {
            return false;
        }

        return true;
    }

    private boolean isBodyEmpty(Operation op) {
        if (!op.hasBody()) {
            op.setStatusCode(Operation.STATUS_CODE_BAD_REQUEST);
            op.fail(new IllegalArgumentException("Empty body is provided."));
            return true;
        }
        return false;
    }

}
