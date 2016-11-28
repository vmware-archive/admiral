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

package com.vmware.admiral.closures.services.images;

import java.util.concurrent.TimeUnit;

import com.vmware.admiral.closures.drivers.DriverRegistry;
import com.vmware.admiral.closures.drivers.ExecutionDriver;
import com.vmware.admiral.closures.util.ClosureProps;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

public class DockerImageService extends StatefulService {

    private final transient DriverRegistry driverRegistry;

    public DockerImageService(DriverRegistry driverRegistry) {
        super(DockerImage.class);

        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);

        super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);

        super.setMaintenanceIntervalMicros(TimeUnit.SECONDS.toMicros(ClosureProps.MAINTENANCE_TIMEOUT_SECONDS));

        this.driverRegistry = driverRegistry;
    }

    @Override
    public void handleMaintenance(Operation post) {
        if (getProcessingStage() != ProcessingStage.AVAILABLE) {
            logWarning("Skipping maintenance since service is not available: %s ", getUri());
            return;
        }

        sendRequest(Operation
                .createGet(getUri())
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        logWarning("Failed to fetch image requests state. Reason: " + ex.getMessage());
                        post.fail(new Exception("Unable to fetch image requests state."));
                    } else {
                        DockerImage imageRequest = op.getBody(DockerImage.class);
                        if (imageRequest != null) {
                            if (isBuildImageExpired(imageRequest)) {
                                logInfo("Image is expired: %s", imageRequest.documentSelfLink);
                                cleanImage(imageRequest);
                                imageRequest.documentExpirationTimeMicros = TimeUnit.MICROSECONDS.toMicros(1);
                                sendSelfPatch(imageRequest);
                            } else {
                                checkDockerHost(imageRequest);
                            }
                        }
                        post.complete();
                    }
                }));
    }

    @Override
    public void handleStart(Operation put) {
        DockerImage newImageState = put.getBody(DockerImage.class);
        newImageState.lastAccessedTimeMillis = System.currentTimeMillis();

        setState(put, newImageState);
        put.complete();
    }

    @Override
    public void handleDelete(Operation delete) {
        DockerImage currentImageState = this.getState(delete);
        logInfo("Handle delete of: %s ", currentImageState.documentSelfLink);
        cleanImage(currentImageState);

        delete.complete();
    }

    @Override
    public void handlePatch(Operation patch) {
        DockerImage requestedImageState = patch.getBody(DockerImage.class);
        DockerImage currentImageState = this.getState(patch);
        if (requestedImageState.taskInfo != null) {
            if (!(TaskState.isFailed(requestedImageState.taskInfo) && TaskState.isFinished(currentImageState
                    .taskInfo))) {
                currentImageState.taskInfo = requestedImageState.taskInfo;
            } else {
                logInfo("Requested state not allowed!");
            }
        }
        if (TaskState.isFailed(currentImageState.taskInfo)) {
            currentImageState.documentExpirationTimeMicros = ServiceUtils.getExpirationTimeFromNowInMicros(TimeUnit
                    .SECONDS.toMicros(ClosureProps.KEEP_FAILED_BUILDS_TIMEOUT_SECONDS));
        } else {
            currentImageState.lastAccessedTimeMillis = System.currentTimeMillis();
            currentImageState.documentExpirationTimeMicros = requestedImageState.documentExpirationTimeMicros;
            currentImageState.imageDetails = requestedImageState.imageDetails;
        }

        this.setState(patch, currentImageState);
        patch.complete();
    }

    private void checkDockerHost(DockerImage imageRequest) {
        if (imageRequest.taskInfo == null || TaskState.isInProgress(imageRequest.taskInfo)) {
            return;
        }
        ExecutionDriver executionDriver = driverRegistry.getDriver();

        logInfo("Verifying image: %s link: %s", imageRequest.name, imageRequest.documentSelfLink);
        executionDriver.inspectImage(imageRequest.name, imageRequest.computeStateLink,
                (error) -> logWarning("Unable to check docker image: %s on host: %s", imageRequest.name,
                        imageRequest.computeStateLink));
    }

    private void cleanImage(DockerImage imageRequest) {
        ExecutionDriver executionDriver = driverRegistry.getDriver();
        executionDriver.cleanImage(imageRequest.name, imageRequest.computeStateLink,
                (error) -> logWarning("Unable to clean docker image: %s on host: %s", imageRequest.name,
                        imageRequest.computeStateLink));
    }

    private boolean isBuildImageExpired(DockerImage imageRequest) {
        if (imageRequest == null || imageRequest.lastAccessedTimeMillis == null) {
            return false;
        }

        if (TaskState.isInProgress(imageRequest.taskInfo)) {
            return false;
        }

        long timeout = TimeUnit.SECONDS.toMillis(ClosureProps.BUILD_IMAGE_EXPIRE_TIMEOUT_SECONDS);
        long timeElapsed = System.currentTimeMillis() - imageRequest.lastAccessedTimeMillis;
        if (timeElapsed >= timeout) {
            logInfo("Timeout elapsed = %s ms, timeout = %s ms of imageRequest = %s", timeElapsed, timeout,
                    imageRequest.documentSelfLink);
            return true;
        }

        return false;
    }

    private void sendSelfPatch(DockerImage body) {
        sendRequest(Operation
                .createPatch(getUri())
                .setBody(body)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logWarning("Self patch failed: %s", Utils.toString(ex));
                    }
                }));
    }

}
